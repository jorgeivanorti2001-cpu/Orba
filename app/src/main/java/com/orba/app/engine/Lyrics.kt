package com.orba.app.engine

import android.content.Context
import com.orba.app.data.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Una línea de la letra: tiempo (si es sincronizada), texto original y traducción al español. */
data class LyricLine(val timeMs: Long?, val text: String, val translation: String?)

data class LyricsResult(
    val lines: List<LyricLine>,
    val synced: Boolean,
    /** Idioma detectado de la letra ("es" si ya está en español: no se muestra traducción). */
    val language: String?,
)

/**
 * Letras de canciones desde LRCLIB (gratis, sin cuenta) y traducción al español.
 * Todo se guarda en el teléfono la primera vez: después se ve sin conexión.
 */
object Lyrics {

    suspend fun load(context: Context, song: Video): LyricsResult? = withContext(Dispatchers.IO) {
        val cache = File(File(context.filesDir, "lyrics").apply { mkdirs() }, "${song.id}.json")
        if (cache.exists()) {
            runCatching { return@withContext fromJson(JSONObject(cache.readText())) }
        }
        val (artist, track) = guessArtistAndTrack(song)
        val raw = findLyrics(artist, track, song.durationSec) ?: return@withContext null
        val synced = raw.first
        val lines = if (synced != null) parseLrc(synced) else raw.second.orEmpty().lines().map { LyricLine(null, it.trim(), null) }
        if (lines.none { it.text.isNotBlank() }) return@withContext null

        // Traducción en un solo pedido (solo las líneas con texto), luego se reparte línea por línea
        val withText = lines.withIndex().filter { it.value.text.isNotBlank() }
        val (translated, lang) = translate(withText.map { it.value.text })
        val withTranslation = if (lang != "es" && translated != null && translated.size == withText.size) {
            val byIndex = withText.mapIndexed { k, iv -> iv.index to translated[k] }.toMap()
            lines.mapIndexed { i, l ->
                l.copy(translation = byIndex[i]?.takeIf { t -> t.isNotBlank() && !t.equals(l.text, true) })
            }
        } else lines
        val result = LyricsResult(withTranslation, synced != null, lang)
        runCatching { cache.writeText(toJson(result).toString()) }
        result
    }

    fun clearCache(context: Context, id: Long) {
        File(context.filesDir, "lyrics/$id.json").delete()
    }

    // ---------- Buscar artista y canción a partir del título ----------

    private val noise = Regex(
        """\s*[\(\[][^)\]]*(official|oficial|video|lyric|letra|audio|visualizer|hd|4k|live|en vivo|remaster)[^)\]]*[\)\]]""",
        RegexOption.IGNORE_CASE,
    )

    fun guessArtistAndTrack(song: Video): Pair<String?, String> {
        val title = song.title.replace(noise, "").replace(Regex("""\s+(ft\.?|feat\.?)\s+.*$""", RegexOption.IGNORE_CASE), "").trim()
        val parts = title.split(" - ", " – ", " — ", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
            return parts[0].trim() to parts[1].trim()
        }
        val artist = song.uploader?.removeSuffix(" - Topic")?.removeSuffix("VEVO")?.trim()
        return artist to title
    }

    /**
     * Devuelve (letra sincronizada LRC, letra simple). Busca primero en LRCLIB (con tiempos);
     * si no está, en Letras.com y por último en Lyrics.ovh.
     */
    private fun findLyrics(artist: String?, track: String, durationSec: Int): Pair<String?, String?>? =
        findOnLrclib(artist, track, durationSec)
            ?: findOnLetras(artist, track)?.let { null to it }
            ?: findOnLyricsOvh(artist, track)?.let { null to it }

    private fun findOnLrclib(artist: String?, track: String, durationSec: Int): Pair<String?, String?>? {
        // 1) Búsqueda exacta por artista + canción
        if (artist != null) {
            val q = "artist_name=${enc(artist)}&track_name=${enc(track)}" + if (durationSec > 0) "&duration=$durationSec" else ""
            httpGet("https://lrclib.net/api/get?$q")?.let { body ->
                runCatching { JSONObject(body) }.getOrNull()?.let { o -> pick(o)?.let { return it } }
            }
        }
        // 2) Búsqueda libre
        val q = listOfNotNull(artist, track).joinToString(" ")
        httpGet("https://lrclib.net/api/search?q=${enc(q)}")?.let { body ->
            val arr = runCatching { JSONArray(body) }.getOrNull() ?: return null
            for (i in 0 until minOf(arr.length(), 8)) {
                pick(arr.getJSONObject(i))?.let { return it }
            }
        }
        return null
    }

    // ---------- Letras.com ----------

    /** Buscador de Letras.com: devuelve las canciones encontradas (artista, enlace, géneros). */
    fun letrasSearch(query: String): JSONArray? {
        val body = httpGet("https://solr.sscdn.co/letras/m1/?q=${enc(query)}&wt=json&callback=LetrasSug") ?: return null
        val json = body.substringAfter('(').substringBeforeLast(')')
        return runCatching { JSONObject(json).optJSONObject("response")?.optJSONArray("docs") }.getOrNull()
    }

    private fun findOnLetras(artist: String?, track: String): String? {
        val docs = letrasSearch(listOfNotNull(artist, track).joinToString(" ")) ?: return null
        var tried = 0
        for (i in 0 until docs.length()) {
            val d = docs.optJSONObject(i) ?: continue
            if (d.optString("t") != "2") continue // 2 = canción
            val dns = d.optString("dns").takeIf { it.isNotBlank() } ?: continue
            val url = d.optString("url").takeIf { it.isNotBlank() } ?: continue
            val page = httpGet("https://www.letras.com/$dns/$url/", browser = true) ?: continue
            extractLetras(page)?.let { return it }
            if (++tried >= 2) break
        }
        return null
    }

    /** Saca el texto de la letra del HTML (el bloque "lyric-original"). */
    private fun extractLetras(html: String): String? {
        val mark = html.indexOf("class=\"lyric-original")
        if (mark < 0) return null
        val openStart = html.lastIndexOf("<div", mark).takeIf { it >= 0 } ?: return null
        val openEnd = html.indexOf('>', mark).takeIf { it >= 0 } ?: return null
        // Busca el cierre del div contando los que se abren dentro
        var depth = 1
        var pos = openEnd + 1
        var close = -1
        val tag = Regex("<(/?)div\\b", RegexOption.IGNORE_CASE)
        while (depth > 0) {
            val m = tag.find(html, pos) ?: break
            if (m.groupValues[1] == "/") depth-- else depth++
            if (depth == 0) close = m.range.first
            pos = m.range.last + 1
        }
        if (close < 0 || openStart > close) return null
        val text = html.substring(openEnd + 1, close)
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), "")
            .let { decodeEntities(it) }
            .lines().joinToString("\n") { it.trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return text.takeIf { it.length > 20 }
    }

    private fun decodeEntities(s: String): String = s
        .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toInt().toChar().toString() }
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&nbsp;", " ").replace("&amp;", "&")

    // ---------- Lyrics.ovh ----------

    private fun findOnLyricsOvh(artist: String?, track: String): String? {
        if (artist.isNullOrBlank()) return null
        val body = httpGet("https://api.lyrics.ovh/v1/${enc(artist).replace("+", "%20")}/${enc(track).replace("+", "%20")}") ?: return null
        return runCatching { JSONObject(body).optString("lyrics") }.getOrNull()
            ?.replace("\r\n", "\n")?.trim()?.takeIf { it.length > 20 }
    }

    private fun pick(o: JSONObject): Pair<String?, String?>? {
        val synced = o.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val plain = o.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }
        return if (synced != null || plain != null) synced to plain else null
    }

    private val lrcLine = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")

    private fun parseLrc(lrc: String): List<LyricLine> =
        lrc.lines().mapNotNull { line ->
            val m = lrcLine.find(line) ?: return@mapNotNull null
            val min = m.groupValues[1].toLong()
            val sec = m.groupValues[2].toLong()
            val frac = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            val text = line.replace(lrcLine, "").trim()
            LyricLine(min * 60_000 + sec * 1_000 + frac, text, null)
        }

    // ---------- Traducción ----------

    /** Traduce las líneas al español. Devuelve (líneas traducidas, idioma detectado). */
    private fun translate(lines: List<String>): Pair<List<String>?, String?> {
        val text = lines.joinToString("\n")
        if (text.isBlank()) return null to null
        // Servicio gratuito de Google Translate (sin cuenta). Si falla, la letra se muestra sin traducir.
        val body = httpPost(
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=es&dt=t",
            "q=" + enc(text),
        ) ?: return null to null
        return runCatching {
            val root = JSONArray(body)
            val sentences = root.getJSONArray(0)
            val sb = StringBuilder()
            for (i in 0 until sentences.length()) {
                val part = sentences.optJSONArray(i) ?: continue
                sb.append(part.optString(0))
            }
            val lang = root.optString(2).takeIf { it.isNotBlank() }
            sb.toString().split("\n").map { it.trim() } to lang
        }.getOrElse { null to null }
    }

    // ---------- Red ----------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(url: String, browser: Boolean = false): String? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty(
                "User-Agent",
                if (browser) "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                else "Orba/3.0 (app personal)",
            )
        }
        try {
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    private fun httpPost(url: String, form: String): String? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        try {
            c.outputStream.use { it.write(form.toByteArray()) }
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()

    // ---------- Guardado local ----------

    private fun toJson(r: LyricsResult): JSONObject = JSONObject().apply {
        put("synced", r.synced)
        put("lang", r.language ?: "")
        put("lines", JSONArray().apply {
            r.lines.forEach { l ->
                put(JSONObject().apply {
                    put("t", l.timeMs ?: -1L)
                    put("x", l.text)
                    put("tr", l.translation ?: "")
                })
            }
        })
    }

    private fun fromJson(o: JSONObject): LyricsResult {
        val arr = o.getJSONArray("lines")
        val lines = (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            LyricLine(
                l.optLong("t", -1L).takeIf { it >= 0 },
                l.optString("x"),
                l.optString("tr").takeIf { it.isNotBlank() },
            )
        }
        return LyricsResult(lines, o.optBoolean("synced"), o.optString("lang").takeIf { it.isNotBlank() })
    }
}
