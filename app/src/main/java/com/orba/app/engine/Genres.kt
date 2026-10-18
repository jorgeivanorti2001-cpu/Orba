package com.orba.app.engine

import com.orba.app.data.Category
import com.orba.app.data.Video
import com.orba.app.ui.searchable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Sugiere el género de una canción buscándola en el catálogo de música de Apple
 * (gratis y sin cuenta) y lo acomoda a tus propias categorías cuando ya existe una parecida.
 */
object Genres {

    /** Resultado para una canción: el nombre de categoría propuesto (o null si no se encontró). */
    data class Suggestion(val songId: Long, val genre: String?, val existingCategoryId: Long?)

    /** Grupos de nombres que significan lo mismo. El primero es el nombre que se usa al crear. */
    private val groups: List<List<String>> = listOf(
        listOf("Metal", "heavy metal", "hard rock", "metalcore", "thrash", "death metal", "black metal", "nu metal"),
        listOf("Rock", "rock clasico", "classic rock", "soft rock", "rock and roll"),
        listOf("Alternativo", "alternative", "indie", "indie rock", "indie pop", "grunge"),
        listOf("Punk", "punk rock", "pop punk", "hardcore"),
        listOf("Pop", "pop latino", "latin pop", "dance pop", "teen pop"),
        listOf("K-Pop", "kpop", "k pop"),
        listOf("J-Pop", "jpop", "j pop"),
        listOf("Anime", "soundtrack anime", "ost anime"),
        listOf("Reguetón", "reggaeton", "regueton", "urbano latino", "latin urban", "urbano", "latin urbano", "trap latino"),
        listOf("Hip-Hop / Rap", "hip hop", "hip-hop", "rap", "hip-hop/rap", "trap"),
        listOf("R&B / Soul", "r&b", "rnb", "soul", "r&b/soul", "funk"),
        listOf("Electrónica", "electronic", "electronica", "dance", "edm", "house", "techno", "trance", "dubstep"),
        listOf("Salsa", "salsa y tropical", "tropical", "salsa tropical"),
        listOf("Vallenato"),
        listOf("Merengue"),
        listOf("Bachata"),
        listOf("Cumbia"),
        listOf("Música llanera", "llanera", "joropo", "musica llanera"),
        listOf("Regional mexicano", "regional mexican", "musica mexicana", "mariachi", "ranchera", "norteño", "corridos", "banda"),
        listOf("Música popular", "popular", "despecho", "musica popular"),
        listOf("Latina", "latin", "latino", "musica latina"),
        listOf("Baladas", "balada", "ballad", "romantica", "romántica", "boleros", "bolero"),
        listOf("Cristiana", "christian", "christian & gospel", "gospel", "cristiana y gospel", "worship"),
        listOf("Clásica", "classical", "clasica", "opera", "orquesta"),
        listOf("Jazz", "smooth jazz"),
        listOf("Blues"),
        listOf("Country"),
        listOf("Reggae", "dancehall", "ska"),
        listOf("Folk", "folklore", "folclor", "singer/songwriter", "cantautor", "cantautores"),
        listOf("Bandas sonoras", "soundtrack", "banda sonora", "original score", "film score"),
        listOf("Instrumental", "new age", "ambient", "lofi", "lo-fi"),
        listOf("Infantil", "children's music", "kids", "ninos"),
        listOf("Música del mundo", "world", "musica del mundo"),
        listOf("Flamenco"),
        listOf("Tango"),
    )

    /** Llave común para comparar nombres ("Hip-Hop/Rap" y "rap" dan lo mismo). */
    private fun norm(s: String) = s.searchable().replace(Regex("[^a-z0-9&]+"), " ").trim()

    private val index: Map<String, Int> = buildMap {
        groups.forEachIndexed { i, g -> g.forEach { put(norm(it), i) } }
    }

    /** Grupo al que pertenece un nombre, si se reconoce. */
    private fun groupOf(name: String): Int? {
        val n = norm(name)
        index[n]?.let { return it }
        // "Rock en español", "Metal sinfónico"… se reconoce por la palabra principal
        return index.entries.filter { (k, _) -> k.length >= 3 && Regex("\\b${Regex.escape(k)}\\b").containsMatchIn(n) }
            .maxByOrNull { it.key.length }?.value
    }

    /** Nombre en español para un género de Apple (o el mismo si no está en la tabla). */
    fun spanishName(appleGenre: String): String = groupOf(appleGenre)?.let { groups[it].first() } ?: appleGenre

    /** Busca entre tus categorías una que sea del mismo género. */
    fun matchCategory(genre: String, categories: List<Category>): Category? {
        val n = norm(genre)
        categories.firstOrNull { norm(it.name) == n }?.let { return it }
        val g = groupOf(genre) ?: return null
        return categories.firstOrNull { groupOf(it.name) == g }
    }

    /** Pide el género al catálogo de Apple. Respeta el límite de pedidos (unos 20 por minuto). */
    suspend fun lookup(song: Video): String? = withContext(Dispatchers.IO) {
        val (artist, track) = Lyrics.guessArtistAndTrack(song)
        val term = listOfNotNull(artist, track).joinToString(" ").take(120)
        if (term.isBlank()) return@withContext null
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=5&country=US&term=" +
            URLEncoder.encode(term, "UTF-8")
        var body = get(url)
        if (body == RATE_LIMITED) {
            delay(15_000)
            body = get(url)
        }
        if (body == null || body == RATE_LIMITED) return@withContext null
        runCatching {
            val results = JSONObject(body).optJSONArray("results") ?: return@runCatching null
            // Se toma el género más repetido entre los primeros resultados
            val genres = (0 until results.length()).mapNotNull { i ->
                results.optJSONObject(i)?.optString("primaryGenreName")?.takeIf { it.isNotBlank() && it != "Music" }
            }
            genres.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        }.getOrNull()
    }

    /** Segunda opción: el género que muestra Letras.com para esa canción. */
    private suspend fun lookupLetras(song: Video): String? = withContext(Dispatchers.IO) {
        val (artist, track) = Lyrics.guessArtistAndTrack(song)
        val docs = Lyrics.letrasSearch(listOfNotNull(artist, track).joinToString(" ")) ?: return@withContext null
        (0 until docs.length()).asSequence()
            .mapNotNull { docs.optJSONObject(it) }
            .firstOrNull { it.optString("t") == "2" && it.optString("g").isNotBlank() }
            ?.optString("g")?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun suggest(song: Video, categories: List<Category>): Suggestion {
        val apple = lookup(song) ?: lookupLetras(song) ?: return Suggestion(song.id, null, null)
        val match = matchCategory(apple, categories)
        return Suggestion(song.id, match?.name ?: spanishName(apple), match?.id)
    }

    private const val RATE_LIMITED = "\u0000limit"

    private fun get(url: String): String? = runCatching {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            when (c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }
                403, 429 -> RATE_LIMITED
                else -> null
            }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
