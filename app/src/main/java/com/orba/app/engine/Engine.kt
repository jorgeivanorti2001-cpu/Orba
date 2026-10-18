package com.orba.app.engine

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Envoltorio de yt-dlp. Lee la información de un enlace (título, miniatura, formato
 * vertical u horizontal) y obtiene las direcciones reales de video y audio para reproducir.
 */
object Engine {
    private const val TAG = "Engine"
    private val initLock = Mutex()
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    suspend fun ensureInit(context: Context) {
        loadMemory(context)
        if (_ready.value) return
        initLock.withLock {
            if (_ready.value) return
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
            }
            _ready.value = true
        }
    }

    // ---------- Enlaces ----------

    private val urlRegex = Regex("""https?://[^\s"'<>]+""")

    private fun cleanUrl(raw: String) = raw.trimEnd('.', ',', ')', ']', ';', '!', '?')

    /** Saca el primer enlace de un texto (al compartir desde otras apps llega con texto extra). */
    fun extractUrl(text: String): String? = urlRegex.find(text.trim())?.value?.let { cleanUrl(it) }

    /** Todos los enlaces de un texto, sin repetir y en orden. */
    fun extractUrls(text: String): List<String> =
        urlRegex.findAll(text).map { cleanUrl(it.value) }.distinct().toList()

    fun detectPlatform(url: String): String {
        val u = url.lowercase()
        return when {
            "youtube.com" in u || "youtu.be" in u -> PLATFORM_YOUTUBE
            "facebook.com" in u || "fb.watch" in u || "fb.com" in u -> PLATFORM_FACEBOOK
            "instagram.com" in u || "instagr.am" in u -> "instagram"
            "tiktok.com" in u -> "tiktok"
            else -> "web"
        }
    }

    private val youtubeId = Regex("""(?:youtu\.be/|[?&]v=|/shorts/|/live/|/embed/|/v/)([A-Za-z0-9_-]{11})""")

    /** Clave para reconocer el mismo video aunque el enlace venga escrito distinto (solo YouTube). */
    fun sameVideoKey(url: String): String? {
        if (detectPlatform(url) != PLATFORM_YOUTUBE) return null
        return youtubeId.find(url)?.groupValues?.get(1)?.let { "yt:$it" }
    }

    /** Pistas en el enlace: shorts de YouTube, reels de Facebook/Instagram (incluido el "compartir" /share/r/). */
    private fun looksVertical(url: String?): Boolean {
        val u = url?.lowercase() ?: return false
        return "/shorts/" in u || "/reel" in u || "/share/r/" in u || "tiktok.com" in u || "instagram.com" in u
    }

    // ---------- Selección de formatos ----------

    /** Preferir la pista de audio en español cuando exista (la fija Ajustes). */
    @Volatile
    var preferSpanish: Boolean = true

    /** Prioriza H.264 (decodificación por hardware en casi todos los celulares) y audio AAC. */
    fun formatSelector(maxHeight: Int): String {
        if (maxHeight <= 0) {
            val low = maxHeight == AUDIO_LOW
            val base = if (low) "ba[abr<=72][ext=m4a]/ba[abr<=72]/wa/ba/b" else "ba[ext=m4a]/ba/b"
            if (!preferSpanish) return base
            return (if (low) "ba[language^=es][abr<=72]/" else "ba[language^=es][ext=m4a]/ba[language^=es]/") + base
        }
        val h = maxHeight
        val spanish = if (preferSpanish) {
            // Doblajes (p. ej. los automáticos de YouTube): primero la pista en español si existe
            "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a][language^=es]/bv*[height<=$h]+ba[language^=es]/"
        } else ""
        return spanish +
            "bv*[height<=$h][vcodec^=avc1]+ba[ext=m4a]/" +
            "bv*[height<=$h][ext=mp4]+ba[ext=m4a]/" +
            "b[height<=$h][ext=mp4]/" +
            "bv*[height<=$h]+ba/b[height<=$h]/b"
    }

    /** Formato de respaldo: un solo archivo con audio y video juntos (más compatible). */
    private const val FALLBACK_SELECTOR = "18/b[ext=mp4]/b"

    /** Para audios se usa "altura 0" como marca de "solo audio" (y -1 para calidad de ahorro). */
    const val AUDIO_HEIGHT = 0
    const val AUDIO_LOW = -1

    fun audioHeight(quality: Int): Int = if (quality == 1) AUDIO_LOW else AUDIO_HEIGHT

    // ---------- Información del video ----------

    data class Metadata(
        val url: String,
        val title: String,
        val uploader: String?,
        val thumbnail: String?,
        val durationSec: Int,
        val width: Int,
        val height: Int,
        val vertical: Boolean,
        /** Dirección "oficial" del video según la plataforma (sirve para detectar repetidos). */
        val canonicalUrl: String?,
    )

    // ---------- Reintentos cuando la plataforma pide "iniciar sesión" ----------

    /**
     * YouTube a veces responde "inicia sesión para confirmar que no eres un bot". No es un
     * problema de tu cuenta: depende de cómo se pide el video. Ante ese error se actualiza
     * el motor y se prueba con otros "clientes" (TV, móvil, iOS…), que suelen funcionar.
     */
    fun extractorFallbacks(url: String): List<String?> =
        if (detectPlatform(url) == PLATFORM_YOUTUBE) {
            val all = listOf(
                null,
                "youtube:player_client=default,tv,mweb",
                "youtube:player_client=tv_simply,web_safari",
                "youtube:player_client=ios,android",
                "youtube:player_client=web_embedded,mweb",
            )
            // Primero la forma que funcionó la última vez: así no se pierden segundos probando las que fallan
            val best = bestYoutubeArgs
            if (best == null) all else listOf(best.ifEmpty { null }) + all.filter { it != best.ifEmpty { null } }
        } else {
            listOf(null, null) // otras plataformas: un segundo intento tras actualizar el motor
        }

    /** La forma de pedir los videos de YouTube que funcionó la última vez ("" = la normal). */
    @Volatile private var bestYoutubeArgs: String? = null
    @Volatile private var memory: android.content.SharedPreferences? = null

    private fun rememberWinner(context: Context, url: String, args: String?) {
        if (detectPlatform(url) != PLATFORM_YOUTUBE) return
        val value = args ?: ""
        if (value == bestYoutubeArgs) return
        bestYoutubeArgs = value
        (memory ?: context.applicationContext.getSharedPreferences("engine", Context.MODE_PRIVATE).also { memory = it })
            .edit().putString("ytArgs", value).apply()
    }

    private fun loadMemory(context: Context) {
        if (memory != null) return
        val sp = context.applicationContext.getSharedPreferences("engine", Context.MODE_PRIVATE)
        memory = sp
        bestYoutubeArgs = sp.getString("ytArgs", null)
    }

    /** Errores que suelen arreglarse reintentando de otra forma o con el motor actualizado. */
    fun isRetryable(t: Throwable): Boolean {
        val m = (t.message ?: "").lowercase()
        if (looksRemoved(t)) return false
        return listOf(
            "sign in", "not a bot", "confirm you", "log in", "login", "cookies", "po token",
            "http error 403", "forbidden", "requested format is not available", "unable to extract",
            "nsig", "signature", "player response", "precondition",
        ).any { it in m }
    }

    private val updateLock = Mutex()
    @Volatile private var updatedThisSession = false

    private val updateScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    @Volatile private var updateJob: kotlinx.coroutines.Job? = null

    /**
     * Actualiza yt-dlp en segundo plano (una vez por sesión) sin hacer esperar al video:
     * mientras tanto se siguen probando las otras formas de leer el enlace.
     */
    fun updateInBackground(context: Context) {
        if (updatedThisSession || updateJob?.isActive == true) return
        val app = context.applicationContext
        updateJob = updateScope.launch { updateOnce(app) }
    }

    /** Si hay una actualización en curso, espera a que termine. */
    private suspend fun awaitUpdate() { updateJob?.join() }

    /** Actualiza yt-dlp como mucho una vez por sesión cuando algo falla. */
    suspend fun updateOnce(context: Context) {
        if (updatedThisSession) return
        updateLock.withLock {
            if (updatedThisSession) return
            withContext(Dispatchers.IO) {
                runCatching { YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext) }
            }
            updatedThisSession = true
        }
    }

    /** Lee la información de un enlace, reintentando con otras formas si la plataforma se pone difícil. */
    private suspend fun robustInfo(
        context: Context,
        url: String,
        configure: YoutubeDLRequest.() -> Unit,
    ): com.yausername.youtubedl_android.mapper.VideoInfo {
        var last: Throwable? = null
        for ((i, args) in extractorFallbacks(url).withIndex()) {
            try {
                val request = YoutubeDLRequest(url).apply {
                    configure()
                    if (args != null) addOption("--extractor-args", args)
                }
                val info = YoutubeDL.getInstance().getInfo(request)
                rememberWinner(context, url, args)
                return info
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                last = t
                if (!isRetryable(t)) throw t
                if (i == 0) updateInBackground(context)
            }
        }
        // Último intento, ya con el motor actualizado
        if (updateJob != null) {
            awaitUpdate()
            try {
                return YoutubeDL.getInstance().getInfo(YoutubeDLRequest(url).apply { configure() })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("No se pudo leer el enlace")
    }

    suspend fun fetchMetadata(context: Context, url: String, audio: Boolean = false): Metadata = withContext(Dispatchers.IO) {
        ensureInit(context)
        val info = robustInfo(context, url) {
            addOption("--no-playlist")
            addOption("-f", formatSelector(if (audio) AUDIO_HEIGHT else 720))
        }
        var w = info.width
        var h = info.height
        if (w <= 0 || h <= 0) {
            info.requestedFormats?.firstOrNull { it.width > 0 && it.height > 0 }?.let {
                w = it.width; h = it.height
            }
        }
        if (w <= 0 || h <= 0) {
            info.formats?.filter { it.width > 0 && it.height > 0 }?.maxByOrNull { it.height }?.let {
                w = it.width; h = it.height
            }
        }
        val canonical = info.webpageUrl?.takeIf { it.startsWith("http") }
        // Shorts y reels se tratan como verticales aunque la plataforma no informe el tamaño.
        // Si el tamaño real resulta distinto, el reproductor lo corrige solo al reproducir.
        val hinted = looksVertical(url) || looksVertical(canonical)
        val vertical = hinted || (w > 0 && h > 0 && h > w)
        Metadata(
            url = url,
            title = info.title?.takeIf { it.isNotBlank() } ?: info.fulltitle ?: "Video sin título",
            uploader = info.uploader,
            thumbnail = info.thumbnail,
            durationSec = info.duration,
            width = w,
            height = h,
            vertical = vertical,
            canonicalUrl = canonical,
        )
    }

    // ---------- Direcciones para reproducir en línea ----------

    data class Streams(
        val videoUrl: String,
        val audioUrl: String?,
        val headers: Map<String, String>,
        val resolvedAt: Long = System.currentTimeMillis(),
    )

    suspend fun resolveStreams(
        context: Context,
        url: String,
        maxHeight: Int,
        fallback: Boolean = false,
    ): Streams = withContext(Dispatchers.IO) {
        ensureInit(context)
        var last: Throwable? = null
        for ((i, args) in extractorFallbacks(url).withIndex()) {
            try {
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--dump-json")
                    addOption("--no-warnings")
                    addOption("--socket-timeout", "10")
                    addOption("-f", if (fallback && maxHeight > 0) FALLBACK_SELECTOR else formatSelector(maxHeight))
                    when {
                        args != null -> addOption("--extractor-args", args)
                        // Primer intento en YouTube: no pide listas HLS/DASH que no se usan (responde más rápido)
                        detectPlatform(url) == PLATFORM_YOUTUBE -> addOption("--extractor-args", "youtube:skip=hls,dash,translated_subs")
                    }
                }
                val streams = parseStreams(runCancellable(request))
                rememberWinner(context, url, args)
                return@withContext streams
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                last = t
                if (!isRetryable(t)) throw t
                if (i == 0) updateInBackground(context)
            }
        }
        if (updateJob != null) {
            awaitUpdate()
            try {
                val request = YoutubeDLRequest(url).apply {
                    addOption("--no-playlist")
                    addOption("--dump-json")
                    addOption("--no-warnings")
                    addOption("--socket-timeout", "10")
                    addOption("-f", if (fallback && maxHeight > 0) FALLBACK_SELECTOR else formatSelector(maxHeight))
                }
                return@withContext parseStreams(runCancellable(request))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                last = t
            }
        }
        throw last ?: IllegalStateException("No se pudo leer el enlace")
    }

    private val processCounter = java.util.concurrent.atomic.AtomicLong()

    /**
     * Ejecuta yt-dlp y devuelve lo que imprime. Si ya no hace falta (pasaste de video),
     * el proceso se mata al instante en vez de seguir gastando el procesador.
     */
    private suspend fun runCancellable(request: YoutubeDLRequest): String = coroutineScope {
        val pid = "s" + processCounter.incrementAndGet()
        val killer = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                runCatching { YoutubeDL.getInstance().destroyProcessById(pid) }
            }
        }
        try {
            runInterruptible(Dispatchers.IO) { YoutubeDL.getInstance().execute(request, pid, false, null).out }
        } finally {
            killer.cancel()
        }
    }

    private fun parseStreams(out: String): Streams {
        val line = out.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") }
            ?: error("No se encontró una dirección reproducible")
        val info = org.json.JSONObject(line)
        fun headers(o: org.json.JSONObject?): Map<String, String> {
            val h = o?.optJSONObject("http_headers") ?: return emptyMap()
            return h.keys().asSequence().associateWith { h.optString(it) }
        }
        fun has(o: org.json.JSONObject, key: String) = o.optString(key).let { it.isNotBlank() && it != "none" && it != "null" }
        val requested = info.optJSONArray("requested_formats")
        var videoPart: org.json.JSONObject? = null
        var audioPart: org.json.JSONObject? = null
        if (requested != null) for (i in 0 until requested.length()) {
            val f = requested.optJSONObject(i) ?: continue
            if (!has(f, "url")) continue
            if (has(f, "vcodec")) { if (videoPart == null) videoPart = f } else if (has(f, "acodec") && audioPart == null) audioPart = f
        }
        val v = videoPart
        val a = audioPart
        return if (v != null && a != null) {
            Streams(v.getString("url"), a.getString("url"), headers(v).ifEmpty { headers(info) })
        } else {
            val single = info.optString("url").takeIf { it.startsWith("http") }
                ?: v?.optString("url")?.takeIf { it.startsWith("http") }
                ?: a?.optString("url")?.takeIf { it.startsWith("http") }
                ?: error("No se encontró una dirección reproducible")
            Streams(single, null, headers(info).ifEmpty { headers(v ?: a) })
        }
    }

    // ---------- Buscar en internet (YouTube) ----------

    data class SearchResult(
        val url: String,
        val title: String,
        val uploader: String?,
        val thumbnail: String?,
        val durationSec: Int,
        val vertical: Boolean,
    )

    /** Busca en YouTube sin abrir cada video (rápido): título, canal, duración y miniatura. */
    suspend fun search(context: Context, query: String, limit: Int = 15): List<SearchResult> = withContext(Dispatchers.IO) {
        ensureInit(context)
        val q = query.trim().replace("\n", " ")
        if (q.isEmpty()) return@withContext emptyList()
        val request = YoutubeDLRequest("ytsearch$limit:$q").apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--no-warnings")
        }
        val response = try {
            YoutubeDL.getInstance().execute(request)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (!isRetryable(t)) throw t
            updateOnce(context)
            YoutubeDL.getInstance().execute(request)
        }
        val root = org.json.JSONObject(response.out.trim().lines().last { it.startsWith("{") })
        val entries = root.optJSONArray("entries") ?: return@withContext emptyList()
        (0 until entries.length()).mapNotNull { i ->
            val e = entries.optJSONObject(i) ?: return@mapNotNull null
            val id = e.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = e.optString("url").takeIf { it.startsWith("http") } ?: "https://www.youtube.com/watch?v=$id"
            SearchResult(
                url = url,
                title = e.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: "Sin título",
                uploader = (e.optString("channel").takeIf { it.isNotBlank() && it != "null" }
                    ?: e.optString("uploader").takeIf { it.isNotBlank() && it != "null" }),
                thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                durationSec = e.optDouble("duration", 0.0).let { if (it.isNaN()) 0 else it.toInt() },
                vertical = "/shorts/" in url,
            )
        }
    }

    // ---------- Mensajes de error comprensibles ----------

    fun friendlyError(t: Throwable): String {
        val msg = (t.message ?: "").lowercase()
        Log.w(TAG, "Error del motor", t)
        return when {
            looksRemoved(t) -> "El video ya no está disponible en la plataforma"
            "unsupported url" in msg -> "Ese enlace no es compatible"
            "age-restricted" in msg || "confirm your age" in msg || "age restricted" in msg ->
                "Este video tiene restricción de edad y no se puede leer sin cuenta"
            "private" in msg -> "Es un video privado"
            "sign in" in msg || "not a bot" in msg || "login" in msg || "log in" in msg || "cookies" in msg || "rate-limit" in msg ->
                "La plataforma está limitando las descargas en este momento. Espera unos minutos y vuelve a intentarlo"
            "unable to download" in msg || "network" in msg || "timed out" in msg || "resolve host" in msg ->
                "Sin conexión o la red está fallando"
            else -> "No se pudo leer el video. Prueba actualizar el motor en Ajustes"
        }
    }

    fun looksRemoved(t: Throwable): Boolean {
        val msg = (t.message ?: "").lowercase()
        return listOf(
            "video unavailable", "has been removed", "private video", "no longer available",
            "account associated with this video has been terminated", "content isn't available",
            "this video is not available", "copyright",
        ).any { it in msg }
    }

    const val PLATFORM_YOUTUBE = "youtube"
    const val PLATFORM_FACEBOOK = "facebook"
    /** Audios agregados desde la memoria del teléfono (no tienen enlace). */
    const val PLATFORM_LOCAL = "local"

    fun platformLabel(p: String): String = when (p) {
        PLATFORM_YOUTUBE -> "YouTube"
        PLATFORM_FACEBOOK -> "Facebook"
        "instagram" -> "Instagram"
        "tiktok" -> "TikTok"
        PLATFORM_LOCAL -> "Mi teléfono"
        else -> "Web"
    }
}
