package com.orba.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orba.app.data.Category
import com.orba.app.data.DuplicateVideoException
import com.orba.app.data.Kind
import com.orba.app.data.isDownloaded
import com.orba.app.BuildConfig
import com.orba.app.data.Note
import com.orba.app.data.Prefs
import com.orba.app.data.Repository
import com.orba.app.data.Video
import com.orba.app.download.Downloads
import com.orba.app.engine.Engine
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Filtro de categoría: todas, sin categoría o una en particular. */
sealed interface CategoryFilter {
    data object All : CategoryFilter
    data object None : CategoryFilter
    /** Solo los marcados con "Me encanta". */
    data object Favorites : CategoryFilter
    data class Only(val id: Long) : CategoryFilter
    /** Varias categorías elegidas a la vez (pulsación larga sobre las categorías). */
    data class Many(val ids: Set<Long>) : CategoryFilter
}

object Tab {
    const val HOME = 0
    const val FEED = 1
    const val LIBRARY = 2
    const val SETTINGS = 3
    const val LISTEN = 4
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository.get(app)
    val prefs = Prefs.get(app)

    /** Esta instalación es Orba Music (solo música). */
    val musicOnly: Boolean = BuildConfig.MUSIC_ONLY

    /** Esta instalación es Orba Plus (videos y música juntos). */
    val allInOne: Boolean = BuildConfig.ALL_IN_ONE

    /** Qué secciones existen: Ver en Orba y Orba Plus; Escuchar en Orba Music, Orba Plus, o en Orba si se unifica. */
    val showVideo: Boolean = !musicOnly
    val showMusic: StateFlow<Boolean> = prefs.unified.map { musicOnly || allInOne || it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, musicOnly || allInOne || prefs.unified.value)

    /** Todo lo guardado: videos y audios. */
    val allItems: StateFlow<List<Video>> =
        repo.videos.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val videos: StateFlow<List<Video>> = allItems.map { l -> l.filter { it.kind == Kind.VIDEO } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val audios: StateFlow<List<Video>> = allItems.map { l -> l.filter { it.kind == Kind.AUDIO } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allCategories: StateFlow<List<Category>> =
        repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Categorías de videos (sección Ver). */
    val categories: StateFlow<List<Category>> = allCategories.map { l -> l.filter { it.kind == Kind.VIDEO } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Categorías (playlists) de audio (sección Escuchar). */
    val audioCategories: StateFlow<List<Category>> = allCategories.map { l -> l.filter { it.kind == Kind.AUDIO } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val noteCounts: StateFlow<Map<Long, Int>> =
        repo.noteCounts.map { list -> list.associate { it.videoId to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val filter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)

    /** Modo "Guardados": el feed muestra solo lo descargado (para ver sin conexión), y puede ir en aleatorio. */
    val feedOffline = MutableStateFlow(false)
    val feedShuffleSeed = MutableStateFlow<Long?>(null)

    /** Categorías ocultas: se siguen viendo, pero "Todos" (y reproducir todo) las salta. */
    val hiddenCategories: StateFlow<Set<Long>> = prefs.hiddenCategories

    val feed: StateFlow<List<Video>> = combine(videos, filter, feedOffline, feedShuffleSeed, prefs.hiddenCategories) { list, f, offline, seed, hidden ->
        val filtered = applyFilter(list, f, hidden).let { l -> if (offline) l.filter { it.downloadState == com.orba.app.data.DownloadState.DONE } else l }
        if (seed != null) filtered.shuffled(java.util.Random(seed)) else filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun applyFilter(list: List<Video>, f: CategoryFilter, hidden: Set<Long> = emptySet()): List<Video> = when (f) {
        CategoryFilter.All -> if (hidden.isEmpty()) list else list.filter { it.categoryId == null || it.categoryId !in hidden }
        is CategoryFilter.Many -> list.filter { it.categoryId in f.ids }
        CategoryFilter.None -> list.filter { it.categoryId == null }
        CategoryFilter.Favorites -> list.filter { it.favorite }
        is CategoryFilter.Only -> list.filter { it.categoryId == f.id }
    }

    /** Abre el feed solo con videos descargados (sin conexión), de una categoría y opcionalmente en aleatorio. */
    fun openOfflineVideos(f: CategoryFilter, shuffle: Boolean) {
        filter.value = f
        feedOffline.value = true
        feedShuffleSeed.value = if (shuffle) System.currentTimeMillis() else null
        tab.value = Tab.FEED
    }

    fun exitOfflineFeed() {
        feedOffline.value = false
        feedShuffleSeed.value = null
    }

    /** Pantalla "Guardados" para reproducir lo descargado (desde Inicio). */
    val offlineOpen = MutableStateFlow(false)

    fun toggleFavorite(id: Long, value: Boolean) = viewModelScope.launch { repo.setFavorite(id, value) }

    /** Refuerzo de volumen de un video o canción: se guarda y, si es lo que suena, se aplica ya. */
    fun setBoost(id: Long, db: Int) {
        com.orba.app.playback.PlaybackControl.boost(id, db)
        viewModelScope.launch { repo.setBoost(id, db) }
    }

    private fun autoDownloadFor(kind: Int) =
        if (kind == Kind.AUDIO) prefs.autoDownloadAudio.value else prefs.autoDownload.value

    /** Escuchar: filtro de playlist y lista resultante. */
    val audioFilter = MutableStateFlow<CategoryFilter>(CategoryFilter.All)

    /** Desde el botón + del inicio: abrir el buscador de Escuchar o el de la Biblioteca. */
    val listenSearchRequest = MutableStateFlow(false)
    val librarySearchRequest = MutableStateFlow(false)

    fun openWebSearch(kind: Int) {
        if (kind == Kind.AUDIO) {
            listenSearchRequest.value = true
            tab.value = Tab.LISTEN
        } else {
            libraryKind.value = Kind.VIDEO
            libraryNotesOnly.value = false
            librarySearchRequest.value = true
            tab.value = Tab.LIBRARY
        }
    }

    /** Lo que se escribe en el buscador de Escuchar (busca en todas las canciones). */
    val listenQuery = MutableStateFlow("")

    val listenList: StateFlow<List<Video>> = combine(audios, audioFilter, listenQuery, prefs.hiddenCategories) { list, f, q, hidden ->
        val base = if (q.isBlank()) applyFilter(list, f, hidden) else list.filter { it.matchesQuery(q) }
        // Siempre de la A a la Z, con el índice de letras al costado
        base.sortedAz()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Oculta (o vuelve a mostrar, si ya estaban todas ocultas) las categorías elegidas. */
    fun toggleHidden(ids: Set<Long>) {
        if (ids.isEmpty()) return
        val now = prefs.hiddenCategories.value
        val show = ids.all { it in now }
        prefs.setHiddenCategories(if (show) now - ids else now + ids)
        toast(
            when {
                show -> if (ids.size == 1) "Categoría visible otra vez" else "Categorías visibles otra vez"
                ids.size == 1 -> "Categoría oculta: «Todos» la salta"
                else -> "Categorías ocultas: «Todos» las salta"
            }
        )
    }

    /** Ver solo los videos de varias categorías elegidas. */
    fun playVideoCategories(ids: Set<Long>) {
        if (ids.isEmpty()) return
        exitOfflineFeed()
        filter.value = if (ids.size == 1) CategoryFilter.Only(ids.first()) else CategoryFilter.Many(ids)
        tab.value = Tab.FEED
    }

    /** Canciones de varias categorías elegidas, de la A a la Z (para reproducirlas como cola). */
    fun songsOf(ids: Set<Long>): List<Video> {
        audioFilter.value = if (ids.size == 1) CategoryFilter.Only(ids.first()) else CategoryFilter.Many(ids)
        return audios.value.filter { it.categoryId in ids }.sortedAz()
    }

    /** Pasa varias canciones (o videos) a una categoría de una sola vez. */
    fun moveToCategory(ids: Collection<Long>, categoryId: Long?) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        repo.setCategoryMany(ids, categoryId)
        toast(if (ids.size == 1) "Listo" else "Se movieron ${ids.size}")
    }

    /** Descarga varias de una vez para tenerlas sin conexión (las que ya están guardadas se saltan). */
    fun downloadMany(ids: Collection<Long>) = viewModelScope.launch {
        val pending = allItems.value.filter {
            it.id in ids && !it.isDownloaded() && it.platform != Engine.PLATFORM_LOCAL &&
                it.downloadState != com.orba.app.data.DownloadState.QUEUED && it.downloadState != com.orba.app.data.DownloadState.DOWNLOADING
        }
        pending.forEach { Downloads.enqueue(getApplication(), it.id) }
        toast(
            when (pending.size) {
                0 -> "Ya estaban guardadas"
                1 -> "Descargando 1 para escuchar sin conexión"
                else -> "Descargando ${pending.size} para escuchar sin conexión"
            }
        )
    }

    /** Borra varias de una vez (con sus descargas y notas). */
    fun deleteMany(ids: Collection<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        ids.forEach { id ->
            Downloads.cancel(getApplication(), id)
            repo.deleteVideo(id)
        }
        toast(if (ids.size == 1) "Se eliminó 1" else "Se eliminaron ${ids.size}")
    }

    /** Crea una categoría nueva y le pasa varias canciones. */
    fun moveToNewCategory(ids: Collection<Long>, name: String, kind: Int) = viewModelScope.launch {
        if (name.isBlank() || ids.isEmpty()) return@launch
        val id = repo.createCategory(name, kind)
        repo.setCategoryMany(ids, id)
        toast("Se movieron ${ids.size} a ${name.trim()}")
    }

    /**
     * Aplica los géneros sugeridos: [assign] va de id de canción a nombre de categoría.
     * Las categorías que no existen se crean.
     */
    fun applyGenres(assign: Map<Long, String>) = viewModelScope.launch {
        if (assign.isEmpty()) return@launch
        val existing = audioCategories.value.associateBy { it.name.searchable().trim() }.toMutableMap()
        assign.entries.groupBy({ it.value }, { it.key }).forEach { (name, ids) ->
            val key = name.searchable().trim()
            val catId = existing[key]?.id ?: repo.createCategory(name, Kind.AUDIO).also { newId ->
                existing[key] = Category(id = newId, name = name, color = 0, kind = Kind.AUDIO)
            }
            repo.setCategoryMany(ids, catId)
        }
        toast(if (assign.size == 1) "Género aplicado" else "Se organizaron ${assign.size} canciones")
    }

    // ---------- Audios del teléfono ----------

    val importing = MutableStateFlow(false)

    fun importLocal(uris: List<android.net.Uri>) {
        if (uris.isEmpty() || importing.value) return
        importing.value = true
        viewModelScope.launch {
            val target = (audioFilter.value as? CategoryFilter.Only)?.id
            var ok = 0
            uris.forEach { uri ->
                runCatching { repo.importLocal(uri, target) }.onSuccess { ok++ }
            }
            importing.value = false
            toast(
                when {
                    ok == 0 -> "No se pudo agregar ningún archivo"
                    ok == 1 -> "Se agregó 1 audio del teléfono"
                    else -> "Se agregaron $ok audios del teléfono"
                }
            )
        }
    }

    // ---------- Buscar en internet ----------

    suspend fun searchWeb(query: String): List<Engine.SearchResult> = Engine.search(getApplication(), query)

    /** Agrega un resultado del buscador. Devuelve el id, o null si falló. No reproduce nada. */
    suspend fun addFromSearch(r: Engine.SearchResult, kind: Int): Long? = try {
        val target = if (kind == Kind.AUDIO) (audioFilter.value as? CategoryFilter.Only)?.id
        else (filter.value as? CategoryFilter.Only)?.id
        val id = repo.addFromSearch(r, kind, target)
        if (autoDownloadFor(kind)) Downloads.enqueue(getApplication(), id)
        id
    } catch (e: DuplicateVideoException) {
        toast("Ya estaba en tu lista")
        e.existingId
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        toast("No se pudo agregar")
        null
    }

    // ---------- Navegación ----------

    val tab = MutableStateFlow(Tab.HOME)

    /** Video al que el feed debe saltar al abrirse. */
    val jumpTo = MutableStateFlow<Long?>(null)

    /** Video horizontal en pantalla completa (oculta la barra inferior). */
    val fullscreen = MutableStateFlow(false)

    /** Pantalla de notas abierta para este video (null = cerrada). */
    val notesOpenFor = MutableStateFlow<Long?>(null)
    val notesFocus = MutableStateFlow(false)

    /** Pantalla de "Agregar" abierta, y si agrega videos o audios. */
    val addOpen = MutableStateFlow(false)
    val addKind = MutableStateFlow(if (BuildConfig.MUSIC_ONLY) Kind.AUDIO else Kind.VIDEO)

    /** Pantalla de descargas guardadas (para liberar espacio). */
    val savedOpen = MutableStateFlow(false)

    /** Pantalla grande de la canción que suena. */
    val nowPlayingOpen = MutableStateFlow(false)

    /** Biblioteca: Videos o Audios, y si solo muestra los que tienen notas. */
    val libraryKind = MutableStateFlow(if (BuildConfig.MUSIC_ONLY) Kind.AUDIO else Kind.VIDEO)
    val libraryNotesOnly = MutableStateFlow(false)

    fun openAdd(requested: Int) {
        val kind = if (musicOnly) Kind.AUDIO else requested
        addKind.value = kind
        AddQueue.setKind(getApplication(), kind)
        addOpen.value = true
    }

    fun openLibrary(requested: Int, notesOnly: Boolean = false) {
        val kind = if (musicOnly) Kind.AUDIO else requested
        libraryKind.value = kind
        libraryNotesOnly.value = notesOnly
        if (kind == Kind.VIDEO) filter.value = CategoryFilter.All else audioFilter.value = CategoryFilter.All
        tab.value = Tab.LIBRARY
    }

    fun openVideo(id: Long) {
        exitOfflineFeed()
        filter.value = CategoryFilter.All
        jumpTo.value = id
        tab.value = Tab.FEED
    }

    fun openFeed(f: CategoryFilter = CategoryFilter.All) {
        exitOfflineFeed()
        filter.value = f
        tab.value = Tab.FEED
    }

    fun openNotes(videoId: Long, focus: Boolean) {
        notesFocus.value = focus
        notesOpenFor.value = videoId
    }

    // ---------- Mensajes ----------

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    fun toast(text: String) { _messages.tryEmit(text) }

    // ---------- Agregar varios enlaces (en segundo plano, ver AddQueue) ----------

    val links: StateFlow<List<PendingLink>> = AddQueue.drafts

    /** Cuántos se están agregando ahora mismo (para mostrar un indicador discreto). */
    val adding: StateFlow<Int> = AddQueue.working

    /** Toma todos los enlaces que haya en el texto y los suma a la lista (sin repetir). Devuelve cuántos entraron. */
    fun addLinks(text: String): Int = AddQueue.add(getApplication(), text, addKind.value)

    fun removeLink(url: String) = AddQueue.remove(url)

    fun closeAdd() {
        addOpen.value = false
    }

    /** Se cierra la pantalla enseguida; la lectura y el guardado siguen solos y al final llega un aviso. */
    fun submitLinks(categoryId: Long?) {
        val kind = addKind.value
        val n = AddQueue.submit(getApplication(), categoryId, kind)
        addOpen.value = false
        if (n > 0) {
            val what = if (kind == Kind.AUDIO) (if (n == 1) "1 canción" else "$n canciones") else (if (n == 1) "1 video" else "$n videos")
            toast("Agregando $what en segundo plano. Te aviso cuando termine")
        }
    }

    // ---------- Videos ----------

    fun download(id: Long) = viewModelScope.launch { Downloads.enqueue(getApplication(), id) }
    fun cancelDownload(id: Long) = viewModelScope.launch { Downloads.cancel(getApplication(), id) }
    fun deleteDownload(id: Long) = viewModelScope.launch {
        repo.deleteDownload(id)
        toast("Descarga eliminada. El video sigue en tu lista")
    }

    /** Quita varias descargas a la vez: los enlaces quedan en la app. */
    fun deleteDownloads(ids: Set<Long>) = viewModelScope.launch {
        if (ids.isEmpty()) return@launch
        val freed = repo.deleteDownloads(ids)
        toast("Se liberaron ${com.orba.app.ui.formatSize(freed)}. Los enlaces siguen en tu lista")
    }

    /**
     * Lleva un enlace a la otra sección: [move] = false lo deja en las dos;
     * [move] = true lo quita de donde estaba (sus notas pasan a la copia).
     */
    fun copyToSection(id: Long, kind: Int, move: Boolean) = viewModelScope.launch {
        val where = if (kind == Kind.AUDIO) "Escuchar" else "Ver"
        try {
            val newId = repo.copyToKind(id, kind)
            if (autoDownloadFor(kind)) Downloads.enqueue(getApplication(), newId)
            if (move) {
                repo.moveNotes(id, newId)
                Downloads.cancel(getApplication(), id)
                repo.deleteVideo(id)
                toast("Movido a $where")
            } else {
                toast("Agregado también a $where")
            }
        } catch (e: DuplicateVideoException) {
            toast("Ese enlace ya está en $where")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            toast("No se pudo pasar a $where")
        }
    }

    fun deleteVideo(id: Long) = viewModelScope.launch {
        Downloads.cancel(getApplication(), id)
        repo.deleteVideo(id)
    }

    fun rename(id: Long, title: String) = viewModelScope.launch {
        if (title.isNotBlank()) repo.rename(id, title.trim())
    }

    fun setCategory(videoId: Long, categoryId: Long?) = viewModelScope.launch { repo.setCategory(videoId, categoryId) }

    fun setOrientation(videoId: Long, orientation: Int) = viewModelScope.launch { repo.setOrientation(videoId, orientation) }

    // ---------- Categorías ----------

    fun createCategory(name: String, assignTo: Long? = null, kind: Int = Kind.VIDEO) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        val id = repo.createCategory(name, kind)
        if (assignTo != null) repo.setCategory(assignTo, id)
    }

    fun renameCategory(id: Long, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.renameCategory(id, name)
    }

    fun deleteCategory(id: Long) = viewModelScope.launch {
        repo.deleteCategory(id)
        if (filter.value == CategoryFilter.Only(id)) filter.value = CategoryFilter.All
        if (audioFilter.value == CategoryFilter.Only(id)) audioFilter.value = CategoryFilter.All
    }

    // ---------- Notas ----------

    fun notesFor(videoId: Long): Flow<List<Note>> = repo.notesFor(videoId)
    fun addNote(videoId: Long, text: String, positionMs: Long?) = viewModelScope.launch {
        if (text.isNotBlank()) repo.addNote(videoId, text, positionMs)
    }
    fun updateNote(id: Long, text: String) = viewModelScope.launch {
        if (text.isNotBlank()) repo.updateNote(id, text)
    }
    fun deleteNote(id: Long) = viewModelScope.launch { repo.deleteNote(id) }

    // ---------- Motor ----------

    val engineVersion = MutableStateFlow<String?>(null)
    val engineUpdating = MutableStateFlow(false)

    fun loadEngineVersion() = viewModelScope.launch {
        engineVersion.value = withContext(Dispatchers.IO) {
            runCatching {
                Engine.ensureInit(getApplication())
                YoutubeDL.getInstance().version(getApplication())
            }.getOrNull()
        }
    }

    fun updateEngine() = viewModelScope.launch {
        if (engineUpdating.value) return@launch
        engineUpdating.value = true
        val result = withContext(Dispatchers.IO) {
            runCatching {
                Engine.ensureInit(getApplication())
                YoutubeDL.getInstance().updateYoutubeDL(getApplication())
            }
        }
        engineUpdating.value = false
        result.onSuccess {
            prefs.lastEngineUpdate = System.currentTimeMillis()
            toast(if (it?.toString()?.contains("ALREADY", ignoreCase = true) == true) "El motor ya estaba actualizado" else "Motor actualizado")
            loadEngineVersion()
        }.onFailure { toast("No se pudo actualizar. Revisa tu conexión") }
    }
}
