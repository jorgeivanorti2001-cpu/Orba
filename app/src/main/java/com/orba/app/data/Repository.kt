package com.orba.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.orba.app.engine.Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DuplicateVideoException(val existingId: Long) : Exception("Ese enlace ya está en tu lista")

class Repository private constructor(private val context: Context) {
    private val db = AppDatabase.get(context)
    val videoDao = db.videos()
    private val categoryDao = db.categories()
    private val noteDao = db.notes()

    val videos: Flow<List<Video>> = videoDao.observeAll()
    val categories: Flow<List<Category>> = categoryDao.observeAll()
    val noteCounts: Flow<List<NoteCount>> = noteDao.observeCounts()

    suspend fun getVideo(id: Long) = videoDao.get(id)

    // ---------- Videos ----------

    /**
     * ¿Ya está guardado? Mismo enlace, o el mismo video de YouTube escrito de otra forma
     * (youtu.be, shorts, con "?si=" al final, etc.).
     */
    suspend fun findDuplicate(url: String, kind: Int): Video? = withContext(Dispatchers.IO) {
        videoDao.findByUrl(url, kind)?.let { return@withContext it }
        val key = Engine.sameVideoKey(url) ?: return@withContext null
        videoDao.allOfKind(kind).firstOrNull { Engine.sameVideoKey(it.sourceUrl) == key }
    }

    suspend fun addVideo(rawText: String, categoryId: Long?, kind: Int = Kind.VIDEO): Long {
        val url = Engine.extractUrl(rawText) ?: throw IllegalArgumentException("Eso no parece un enlace válido")
        findDuplicate(url, kind)?.let { throw DuplicateVideoException(it.id) }
        val m = Engine.fetchMetadata(context, url, audio = kind == Kind.AUDIO)
        return insertFromMetadata(url, m, categoryId, kind)
    }

    /** Guarda un enlace ya leído (lo usa la fila de "Agregar" en segundo plano). */
    suspend fun insertFromMetadata(url: String, m: Engine.Metadata, categoryId: Long?, kind: Int): Long {
        findDuplicate(url, kind)?.let { throw DuplicateVideoException(it.id) }
        // Un mismo video puede llegar con enlaces distintos (p. ej. los de "compartir" de Facebook)
        m.canonicalUrl?.takeIf { it != url }?.let { c -> findDuplicate(c, kind)?.let { throw DuplicateVideoException(it.id) } }
        val finalUrl = m.canonicalUrl ?: url
        val id = videoDao.insert(
            Video(
                sourceUrl = finalUrl,
                platform = Engine.detectPlatform(finalUrl),
                title = m.title,
                uploader = m.uploader,
                thumbnailUrl = m.thumbnail,
                durationSec = m.durationSec,
                width = m.width,
                height = m.height,
                isVertical = m.vertical,
                categoryId = categoryId,
                kind = kind,
            )
        )
        // La miniatura se guarda en el teléfono para que siga viéndose aunque borren el video
        m.thumbnail?.let { thumb -> saveThumbnail(id, thumb)?.let { videoDao.setThumb(id, it) } }
        return id
    }

    /** Agrega un resultado del buscador sin volver a leer el enlace (es instantáneo). */
    suspend fun addFromSearch(r: Engine.SearchResult, kind: Int, categoryId: Long?): Long = withContext(Dispatchers.IO) {
        findDuplicate(r.url, kind)?.let { throw DuplicateVideoException(it.id) }
        val id = videoDao.insert(
            Video(
                sourceUrl = r.url,
                platform = Engine.detectPlatform(r.url),
                title = r.title,
                uploader = r.uploader,
                thumbnailUrl = r.thumbnail,
                durationSec = r.durationSec,
                width = 0,
                height = 0,
                isVertical = r.vertical,
                categoryId = categoryId,
                kind = kind,
            )
        )
        r.thumbnail?.let { thumb -> saveThumbnail(id, thumb)?.let { videoDao.setThumb(id, it) } }
        id
    }

    /**
     * Copia un archivo de audio (o video) del teléfono dentro de Orba y lo agrega a Escuchar.
     * Queda marcado como "del teléfono" y funciona sin internet.
     */
    suspend fun importLocal(uri: Uri, categoryId: Long?): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "Audio"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 } ?: "m4a"
        val key = java.util.UUID.randomUUID().toString()
        val dir = File(context.filesDir, "local").apply { mkdirs() }
        val out = File(dir, "$key.$ext")
        (resolver.openInputStream(uri) ?: throw IllegalArgumentException("No se pudo abrir el archivo")).use { input ->
            out.outputStream().use { input.copyTo(it) }
        }

        var title = name.substringBeforeLast('.')
        var artist: String? = null
        var durationSec = 0
        var picture: ByteArray? = null
        runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(out.absolutePath)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it.trim() }
                artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.trim()
                durationSec = ((r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000).toInt()
                picture = r.embeddedPicture
            } finally {
                r.release()
            }
        }
        val id = videoDao.insert(
            Video(
                sourceUrl = "local://$key",
                platform = Engine.PLATFORM_LOCAL,
                title = title,
                uploader = artist,
                thumbnailUrl = null,
                durationSec = durationSec,
                width = 0,
                height = 0,
                isVertical = false,
                categoryId = categoryId,
                kind = Kind.AUDIO,
                localFilePath = out.absolutePath,
                fileSizeBytes = out.length(),
                downloadState = DownloadState.DONE,
                downloadProgress = 1f,
            )
        )
        picture?.let { bytes ->
            runCatching {
                val thumb = File(File(context.filesDir, "thumbs").apply { mkdirs() }, "$id.img")
                thumb.writeBytes(bytes)
                videoDao.setThumb(id, thumb.absolutePath)
            }
        }
        id
    }

    /**
     * Crea una copia del elemento en la otra sección (video → audio o audio → video),
     * sin volver a leer el enlace. Devuelve el id de la copia.
     */
    suspend fun copyToKind(id: Long, kind: Int): Long = withContext(Dispatchers.IO) {
        val v = videoDao.get(id) ?: throw IllegalArgumentException("No existe")
        if (v.isLocal()) throw IllegalArgumentException("Los audios del teléfono solo están en Escuchar")
        videoDao.findByUrl(v.sourceUrl, kind)?.let { throw DuplicateVideoException(it.id) }
        val newId = videoDao.insert(
            v.copy(
                id = 0,
                kind = kind,
                categoryId = null,
                addedAt = System.currentTimeMillis(),
                localThumbPath = null,
                localFilePath = null,
                fileSizeBytes = 0,
                downloadState = DownloadState.NONE,
                downloadProgress = 0f,
                downloadError = null,
                lastPositionMs = 0,
                lastPlayedAt = 0,
                orientation = Orientation.AUTO,
                favorite = false,
            )
        )
        // La miniatura se copia aparte, para que no dependa del original
        v.localThumbPath?.let { File(it) }?.takeIf { it.exists() }?.let { src ->
            runCatching {
                val dst = File(File(context.filesDir, "thumbs").apply { mkdirs() }, "$newId.img")
                src.copyTo(dst, overwrite = true)
                videoDao.setThumb(newId, dst.absolutePath)
            }
        }
        newId
    }

    private suspend fun saveThumbnail(id: Long, url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
            val out = File(dir, "$id.img")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            conn.disconnect()
            out.absolutePath
        }.getOrNull()
    }

    suspend fun deleteVideo(id: Long) = withContext(Dispatchers.IO) {
        val v = videoDao.get(id) ?: return@withContext
        deleteFiles(v)
        File(context.filesDir, "thumbs/$id.img").delete()
        videoDao.delete(id)
    }

    /** Quita varias descargas a la vez (el enlace se conserva). Devuelve los bytes liberados. */
    suspend fun deleteDownloads(ids: Collection<Long>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        ids.forEach { id ->
            // Los del teléfono no se pueden volver a descargar: esos se quitan de la lista completos
            val local = videoDao.get(id)
            if (local != null && local.isLocal()) {
                freed += local.fileSizeBytes
                deleteFiles(local)
                File(context.filesDir, "thumbs/$id.img").delete()
                videoDao.delete(id)
                return@forEach
            }
            videoDao.get(id)?.let { v ->
                freed += v.fileSizeBytes
                deleteFiles(v)
            }
            videoDao.clearDownload(id)
        }
        freed
    }

    suspend fun deleteDownload(id: Long) = withContext(Dispatchers.IO) {
        videoDao.get(id)?.let { deleteFiles(it) }
        videoDao.clearDownload(id)
    }

    private fun deleteFiles(v: Video) {
        v.localFilePath?.let { File(it).delete() }
        File(context.filesDir, "videos").listFiles { f -> f.name.startsWith("${v.id}.") }?.forEach { it.delete() }
    }

    suspend fun setCategory(id: Long, categoryId: Long?) = videoDao.setCategory(id, categoryId)
    suspend fun setCategoryMany(ids: Collection<Long>, categoryId: Long?) = videoDao.setCategoryMany(ids.toList(), categoryId)
    suspend fun rename(id: Long, title: String) = videoDao.rename(id, title)
    suspend fun savePosition(id: Long, pos: Long) = videoDao.savePosition(id, pos)
    suspend fun setUnavailable(id: Long, value: Boolean) = videoDao.setUnavailable(id, value)
    suspend fun setOrientation(id: Long, orientation: Int) = videoDao.setOrientation(id, orientation)
    suspend fun setDimensions(id: Long, width: Int, height: Int) =
        videoDao.setDimensions(id, width, height, height > width)
    suspend fun setFavorite(id: Long, value: Boolean) = videoDao.setFavorite(id, value)
    suspend fun setBoost(id: Long, db: Int) = videoDao.setBoost(id, db)
    suspend fun markPlayed(id: Long) = videoDao.setLastPlayed(id, System.currentTimeMillis())

    // ---------- Categorías ----------

    suspend fun createCategory(name: String, kind: Int = Kind.VIDEO): Long {
        val palette = listOf(0xFFFF7A59, 0xFF4FC3A1, 0xFF6C9CFF, 0xFFFFC857, 0xFFE76FB1, 0xFF9D8CFF, 0xFF5BC0EB)
        return categoryDao.insert(Category(name = name.trim(), color = palette.random(), kind = kind))
    }

    suspend fun renameCategory(id: Long, name: String) = categoryDao.rename(id, name.trim())
    suspend fun deleteCategory(id: Long) = categoryDao.delete(id)

    // ---------- Notas ----------

    /** Cualquier cambio en notas (para el respaldo automático). */
    val allNotes: Flow<List<Note>> = noteDao.observeAll()

    fun notesFor(videoId: Long): Flow<List<Note>> = noteDao.observeFor(videoId)
    suspend fun addNote(videoId: Long, text: String, positionMs: Long?) =
        noteDao.insert(Note(videoId = videoId, text = text.trim(), positionMs = positionMs))

    suspend fun updateNote(id: Long, text: String) = noteDao.updateText(id, text.trim(), System.currentTimeMillis())
    suspend fun deleteNote(id: Long) = noteDao.delete(id)
    suspend fun moveNotes(from: Long, to: Long) = noteDao.moveAll(from, to)

    // ---------- Respaldo (lista completa sin los archivos descargados) ----------

    /** Todo lo agregado: enlaces, categorías, notas y favoritos. Los audios del teléfono no se incluyen. */
    suspend fun exportBackup(): org.json.JSONObject = withContext(Dispatchers.IO) {
        val cats = categoryDao.all()
        val catName = cats.associate { it.id to it.name }
        val notes = noteDao.all().groupBy { it.videoId }
        org.json.JSONObject().apply {
            put("app", "orba")
            put("version", 1)
            put("exportedAt", System.currentTimeMillis())
            put("categories", org.json.JSONArray().apply {
                cats.forEach { c ->
                    put(org.json.JSONObject().put("name", c.name).put("kind", c.kind).put("color", c.color))
                }
            })
            put("items", org.json.JSONArray().apply {
                videoDao.all().filter { !it.isLocal() }.forEach { v ->
                    put(org.json.JSONObject().apply {
                        put("url", v.sourceUrl)
                        put("kind", v.kind)
                        put("platform", v.platform)
                        put("title", v.title)
                        put("uploader", v.uploader ?: "")
                        put("thumb", v.thumbnailUrl ?: "")
                        put("duration", v.durationSec)
                        put("w", v.width)
                        put("h", v.height)
                        put("vertical", v.isVertical)
                        put("orientation", v.orientation)
                        put("category", v.categoryId?.let { catName[it] } ?: "")
                        put("favorite", v.favorite)
                        put("boost", v.boostDb)
                        put("addedAt", v.addedAt)
                        put("position", v.lastPositionMs)
                        put("notes", org.json.JSONArray().apply {
                            notes[v.id].orEmpty().forEach { n ->
                                put(org.json.JSONObject().put("text", n.text).put("pos", n.positionMs ?: -1L).put("at", n.createdAt))
                            }
                        })
                    })
                }
            })
        }
    }

    /** Agrega lo del respaldo que no esté ya en el teléfono. Devuelve cuántos elementos entraron. */
    suspend fun importBackup(o: org.json.JSONObject): Int = withContext(Dispatchers.IO) {
        // Categorías: se reutilizan las que ya existen con el mismo nombre
        val cats = categoryDao.all().toMutableList()
        fun findCat(name: String, kind: Int) = cats.firstOrNull { it.kind == kind && it.name.equals(name, ignoreCase = true) }
        val catArr = o.optJSONArray("categories") ?: org.json.JSONArray()
        for (i in 0 until catArr.length()) {
            val c = catArr.getJSONObject(i)
            val name = c.optString("name").trim()
            val kind = c.optInt("kind")
            if (name.isEmpty() || findCat(name, kind) != null) continue
            val id = categoryDao.insert(Category(name = name, color = c.optLong("color", 0xFFFF7A59), kind = kind))
            cats += Category(id = id, name = name, color = 0, kind = kind)
        }

        var added = 0
        val thumbs = mutableListOf<Pair<Long, String>>()
        val items = o.optJSONArray("items") ?: org.json.JSONArray()
        for (i in 0 until items.length()) {
            val e = items.getJSONObject(i)
            val url = e.optString("url").takeIf { u -> u.startsWith("http") } ?: continue
            val kind = e.optInt("kind")
            if (findDuplicate(url, kind) != null) continue
            val category = e.optString("category").takeIf { c -> c.isNotBlank() }?.let { c -> findCat(c, kind)?.id }
            val thumb = e.optString("thumb").takeIf { t -> t.isNotBlank() }
            val id = runCatching {
                videoDao.insert(
                    Video(
                        sourceUrl = url,
                        platform = e.optString("platform").ifBlank { Engine.detectPlatform(url) },
                        title = e.optString("title").ifBlank { "Sin título" },
                        uploader = e.optString("uploader").takeIf { u -> u.isNotBlank() },
                        thumbnailUrl = thumb,
                        durationSec = e.optInt("duration"),
                        width = e.optInt("w"),
                        height = e.optInt("h"),
                        isVertical = e.optBoolean("vertical"),
                        categoryId = category,
                        addedAt = e.optLong("addedAt", System.currentTimeMillis()),
                        lastPositionMs = e.optLong("position"),
                        orientation = e.optInt("orientation"),
                        kind = kind,
                        favorite = e.optBoolean("favorite"),
                        boostDb = e.optInt("boost"),
                    )
                )
            }.getOrNull() ?: continue
            added++
            val notesArr = e.optJSONArray("notes")
            if (notesArr != null) for (k in 0 until notesArr.length()) {
                val n = notesArr.getJSONObject(k)
                val text = n.optString("text")
                if (text.isBlank()) continue
                val at = n.optLong("at", System.currentTimeMillis())
                noteDao.insert(Note(videoId = id, text = text, positionMs = n.optLong("pos", -1L).takeIf { p -> p >= 0 }, createdAt = at, updatedAt = at))
            }
            if (thumb != null) thumbs += id to thumb
        }
        // Las miniaturas se guardan al final (si falla alguna, se ve la de internet)
        thumbs.forEach { (id, url) -> saveThumbnail(id, url)?.let { path -> videoDao.setThumb(id, path) } }
        added
    }

    companion object {
        @Volatile
        private var instance: Repository? = null
        fun get(context: Context): Repository =
            instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
    }
}
