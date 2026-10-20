package com.orba.app.engine

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.orba.app.playback.Preloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Direcciones de reproducción de los videos en línea.
 *
 * Leer un enlace con yt-dlp tarda unos segundos, así que:
 *  - Lo ya leído se guarda (en memoria y en el teléfono) hasta que la plataforma lo caduca:
 *    volver a un video, o abrir la app más tarde, arranca al instante.
 *  - El feed avisa cuál video estás viendo y cuáles siguen ([focus]). Se preparan los
 *    siguientes por adelantado, pero nunca compiten con el que estás viendo.
 *  - Lo que ya no está cerca (pasaste de largo) se cancela y su proceso se mata enseguida.
 */
object StreamResolver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class Work(val deferred: Deferred<Engine.Streams>, val background: Boolean)

    /** Lecturas en curso. */
    private val running = ConcurrentHashMap<String, Work>()

    /** Lecturas terminadas, con su vencimiento. */
    private data class Ready(val streams: Engine.Streams, val expiresAt: Long)
    private val ready = ConcurrentHashMap<String, Ready>()

    /** Cuántas lecturas "de primer plano" (el video que estás viendo) hay en curso. */
    private val foreground = AtomicInteger(0)

    /** Espera (máximo 8 s) a que no haya un video o canción abriéndose. Lo usan las descargas. */
    suspend fun awaitForegroundIdle() {
        var waited = 0
        while (foreground.get() > 0 && waited < 8_000) {
            delay(150)
            waited += 150
        }
    }

    /** Máximo dos lecturas por adelantado a la vez. */
    private val backgroundGate = Semaphore(2)

    @Volatile private var diskFile: File? = null
    @Volatile private var diskLoaded = false

    private fun key(url: String, maxHeight: Int) = "$url|$maxHeight|${Engine.preferSpanish}"

    private fun cached(key: String): Engine.Streams? {
        val r = ready[key] ?: return null
        if (System.currentTimeMillis() < r.expiresAt) return r.streams
        ready.remove(key)
        return null
    }

    /** Lo que necesita el reproductor ya. Si está listo vuelve al instante. */
    suspend fun get(context: Context, url: String, maxHeight: Int): Engine.Streams {
        loadDisk(context)
        val k = key(url, maxHeight)
        cached(k)?.let { return it }
        val existing = running[k]
        // Si estaba en la fila de "por adelantado" sin empezar, pasa al frente
        val job = if (existing != null && existing.deferred.isActive && !(existing.background && !started.contains(k))) {
            existing
        } else {
            existing?.deferred?.cancel()
            start(context, url, maxHeight, k, background = false)
        }
        return job.deferred.await()
    }

    /** Claves de lecturas "por adelantado" que ya están corriendo (no solo esperando turno). */
    private val started = ConcurrentHashMap.newKeySet<String>()

    private fun start(context: Context, url: String, maxHeight: Int, k: String, background: Boolean): Work {
        val app = context.applicationContext
        val deferred = scope.async {
            val result = if (background) {
                backgroundGate.withPermit {
                    // Espera a que el video que estás viendo ya esté listo
                    while (foreground.get() > 0) delay(120)
                    started.add(k)
                    try {
                        Engine.resolveStreams(app, url, maxHeight)
                    } finally {
                        started.remove(k)
                    }
                }
            } else {
                foreground.incrementAndGet()
                try {
                    Engine.resolveStreams(app, url, maxHeight)
                } finally {
                    foreground.decrementAndGet()
                }
            }
            ready[k] = Ready(result, expiryOf(result))
            saveDisk()
            result
        }
        val job = Work(deferred, background)
        running[k] = job
        deferred.invokeOnCompletion { if (running[k] === job) running.remove(k) }
        return job
    }

    /**
     * El feed avisa qué videos están cerca, en orden de importancia: el actual primero,
     * luego los siguientes. Se preparan los que falten y se cancela lo que ya quedó lejos.
     */
    @OptIn(UnstableApi::class)
    @Synchronized
    fun focus(context: Context, current: String?, ahead: List<String>, maxHeight: Int, warmNext: String? = null) {
        loadDisk(context)
        val keys = (listOfNotNull(current) + ahead).map { key(it, maxHeight) }.toSet()
        running.forEach { (k, job) ->
            if (k !in keys && job.deferred.isActive) {
                job.deferred.cancel()
                running.remove(k)
            }
        }
        // El video al que llegas empieza a leerse ya, antes de que la página termine de acomodarse
        if (current != null) {
            val k = key(current, maxHeight)
            val existing = running[k]
            if (cached(k) == null && (existing == null || !existing.deferred.isActive || (existing.background && !started.contains(k)))) {
                existing?.deferred?.cancel()
                start(context, current, maxHeight, k, background = false)
            }
        }
        ahead.forEach { url ->
            val k = key(url, maxHeight)
            if (cached(k) == null && running[k]?.deferred?.isActive != true) start(context, url, maxHeight, k, background = true)
        }
        // El siguiente video además descarga sus primeros segundos, para que arranque sin espera
        if (warmNext != null) {
            val app = context.applicationContext
            val k = key(warmNext, maxHeight)
            scope.launch {
                runCatching {
                    val streams = cached(k) ?: running[k]?.deferred?.await() ?: return@launch
                    Preloader.warm(app, streams)
                }
            }
        }
    }

    /** Prepara un enlace suelto (inicio, siguiente canción). */
    @OptIn(UnstableApi::class)
    fun prefetch(context: Context, url: String, maxHeight: Int, warm: Boolean = false) {
        loadDisk(context)
        val app = context.applicationContext
        val k = key(url, maxHeight)
        if (cached(k) == null && running[k]?.deferred?.isActive != true) start(app, url, maxHeight, k, background = true)
        if (warm) scope.launch {
            runCatching {
                val streams = cached(k) ?: running[k]?.deferred?.await() ?: return@launch
                Preloader.warm(app, streams)
            }
        }
    }

    fun invalidate(url: String) {
        ready.keys.removeAll { it.startsWith("$url|") }
        running.keys.filter { it.startsWith("$url|") }.forEach { running.remove(it)?.deferred?.cancel() }
        saveDisk()
    }

    // ---------- Vencimiento ----------

    /** YouTube indica en la dirección cuándo vence ("expire="). Se usa con margen; si no, 1 hora. */
    private fun expiryOf(s: Engine.Streams): Long {
        val now = System.currentTimeMillis()
        val expire = Regex("[?&/]expire[=/](\\d{9,11})").find(s.videoUrl)?.groupValues?.get(1)?.toLongOrNull()
        return if (expire != null) {
            (expire * 1000 - 20 * 60 * 1000).coerceIn(now, now + 5 * 60 * 60 * 1000)
        } else {
            now + 60 * 60 * 1000
        }
    }

    // ---------- Guardado en el teléfono ----------

    private fun loadDisk(context: Context) {
        if (diskLoaded) return
        synchronized(this) {
            if (diskLoaded) return
            val f = File(context.applicationContext.cacheDir, "streams.json")
            diskFile = f
            runCatching {
                if (f.exists()) {
                    val o = JSONObject(f.readText())
                    val now = System.currentTimeMillis()
                    o.keys().forEach { k ->
                        val e = o.getJSONObject(k)
                        val exp = e.optLong("exp")
                        if (exp > now) {
                            val h = e.optJSONObject("h")
                            val headers = h?.keys()?.asSequence()?.associateWith { h.optString(it) }.orEmpty()
                            ready[k] = Ready(
                                Engine.Streams(e.getString("v"), e.optString("a").takeIf { it.isNotBlank() }, headers),
                                exp,
                            )
                        }
                    }
                }
            }
            diskLoaded = true
        }
    }

    private fun saveDisk() {
        val f = diskFile ?: return
        scope.launch {
            runCatching {
                synchronized(this@StreamResolver) {
                    val now = System.currentTimeMillis()
                    val o = JSONObject()
                    ready.entries.filter { it.value.expiresAt > now }.takeLast(300).forEach { (k, r) ->
                        o.put(k, JSONObject().apply {
                            put("v", r.streams.videoUrl)
                            put("a", r.streams.audioUrl ?: "")
                            put("h", JSONObject(r.streams.headers))
                            put("exp", r.expiresAt)
                        })
                    }
                    f.writeText(o.toString())
                }
            }
        }
    }
}
