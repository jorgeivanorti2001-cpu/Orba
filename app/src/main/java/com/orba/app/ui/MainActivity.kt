package com.orba.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.orba.app.data.Video
import com.orba.app.playback.PlaybackEvents
import com.orba.app.ui.components.AddLinksScreen
import com.orba.app.ui.components.FloatingNav
import com.orba.app.ui.components.NotesScreen
import com.orba.app.ui.components.VideoOptionsSheet
import com.orba.app.ui.feed.FeedScreen
import com.orba.app.ui.home.HomeScreen
import com.orba.app.ui.home.WelcomeScreen
import com.orba.app.ui.library.SavedScreen
import com.orba.app.ui.library.OfflineScreen
import com.orba.app.ui.listen.ListenScreen
import com.orba.app.ui.listen.MiniPlayer
import com.orba.app.ui.listen.MiniPlayerSpace
import com.orba.app.ui.listen.NowPlayingScreen
import com.orba.app.ui.listen.GenreDialog
import com.orba.app.ui.lock.License
import com.orba.app.ui.lock.LockScreen
import com.orba.app.data.Kind
import com.orba.app.data.isAudio
import com.orba.app.data.isDownloaded
import com.orba.app.engine.Engine
import com.orba.app.engine.StreamResolver
import com.orba.app.playback.AudioQueue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orba.app.ui.library.LibraryScreen
import com.orba.app.ui.settings.SettingsScreen
import com.orba.app.ui.theme.NavClearance
import com.orba.app.ui.theme.OrbaTheme
import com.orba.app.ui.theme.appBackground
import kotlinx.coroutines.flow.merge

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        askNotificationPermission()
        setContent {
            OrbaTheme {
                // Sin permiso del dueño, la app no muestra nada más que la pantalla de bloqueo
                var unlocked by remember { mutableStateOf(License.isActivated(this@MainActivity)) }
                if (unlocked) AppRoot(vm)
                else LockScreen(onUnlocked = { unlocked = true; pendingShare?.let { handleIntent(it) }; pendingShare = null })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.foreground = true
    }

    override fun onStop() {
        AppVisibility.foreground = false
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** "Compartir → Orba" desde YouTube o Facebook: los enlaces entran directo a la lista. */
    private var pendingShare: Intent? = null

    private fun handleIntent(intent: Intent?) {
        if (!License.isActivated(this)) { pendingShare = intent; return }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
                // Desde la sección Escuchar, lo compartido se agrega como audio
                val kind = if (vm.musicOnly || vm.tab.value == Tab.LISTEN) Kind.AUDIO else Kind.VIDEO
                if (Engine.extractUrls(text).isNotEmpty() || vm.links.value.isNotEmpty()) {
                    vm.openAdd(kind)
                    vm.addLinks(text)
                }
            }
        }
    }

    /** Android 13+ pide permiso para mostrar los controles en la notificación. */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val player = rememberMediaController()
    val playerState = rememberPlayerUiState(player)
    val snackbar = remember { SnackbarHostState() }
    var optionsFor by remember { mutableStateOf<Video?>(null) }
    var genreSong by remember { mutableStateOf<Video?>(null) }

    val tab by vm.tab.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val audioCategories by vm.audioCategories.collectAsStateWithLifecycle()
    val videos by vm.allItems.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val audioFilter by vm.audioFilter.collectAsStateWithLifecycle()
    val addKind by vm.addKind.collectAsStateWithLifecycle()
    val savedOpen by vm.savedOpen.collectAsStateWithLifecycle()
    val nowPlayingOpen by vm.nowPlayingOpen.collectAsStateWithLifecycle()
    val offlineOpen by vm.offlineOpen.collectAsStateWithLifecycle()
    val userName by vm.prefs.userName.collectAsStateWithLifecycle()
    val maxHeight by vm.prefs.maxHeight.collectAsStateWithLifecycle()
    val showMusic by vm.showMusic.collectAsStateWithLifecycle()

    // Si se desactiva Escuchar en Orba estando en esa pestaña, vuelve al inicio
    LaunchedEffect(showMusic) {
        if (!showMusic && tab == Tab.LISTEN) vm.tab.value = Tab.HOME
    }
    val context = LocalContext.current

    // Canción que suena ahora (si lo que suena es un audio)
    val currentAudio = playerState?.mediaId?.toLongOrNull()?.let { id -> videos.firstOrNull { it.id == id && it.isAudio() } }

    // Mientras suena una canción, se deja lista la siguiente para que no haya pausa entre una y otra
    LaunchedEffect(currentAudio?.id) {
        val id = currentAudio?.id ?: return@LaunchedEffect
        val nextId = AudioQueue.next(id, wrap = true) ?: return@LaunchedEffect
        videos.firstOrNull { it.id == nextId }?.takeIf { !it.isDownloaded() }?.let {
            StreamResolver.prefetch(context, it.sourceUrl, Engine.audioHeight(vm.prefs.audioQuality.value), warm = true)
        }
    }
    val fullscreen by vm.fullscreen.collectAsStateWithLifecycle()
    val addOpen by vm.addOpen.collectAsStateWithLifecycle()
    val links by vm.links.collectAsStateWithLifecycle()
    val notesFor by vm.notesOpenFor.collectAsStateWithLifecycle()
    val notesFocus by vm.notesFocus.collectAsStateWithLifecycle()
    // Se recuerda el último video de las notas para que la animación de cierre no quede vacía
    var lastNotesId by remember { mutableStateOf<Long?>(null) }
    if (notesFor != null) lastNotesId = notesFor

    LaunchedEffect(Unit) {
        merge(vm.messages, PlaybackEvents.messages).collect { snackbar.showSnackbar(it) }
    }

    // Un video solo sigue sonando fuera de "Ver" (otra pestaña, otra app o pantalla apagada)
    // si está activado el modo audio. La música de Escuchar sí sigue siempre.
    val pauseVideo: () -> Unit = {
        val p = player
        val id = p?.currentMediaItem?.mediaId?.toLongOrNull()
        val item = id?.let { i -> vm.allItems.value.firstOrNull { it.id == i } }
        if (p != null && item != null && !item.isAudio() && !vm.prefs.audioOnly.value && p.playWhenReady) p.pause()
    }
    var previousTab by remember { mutableIntStateOf(tab) }
    LaunchedEffect(tab, player) {
        if (previousTab == Tab.FEED && tab != Tab.FEED) pauseVideo()
        previousTab = tab
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) pauseVideo()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Atrás: desde cualquier pestaña vuelve al inicio
    BackHandler(enabled = tab != Tab.HOME && !fullscreen && notesFor == null && !addOpen && !savedOpen && !nowPlayingOpen && !offlineOpen) {
        vm.tab.value = Tab.HOME
    }

    val openAdd = { vm.openAdd(if (vm.musicOnly) Kind.AUDIO else Kind.VIDEO) }

    Box(Modifier.fillMaxSize().appBackground()) {
        Crossfade(targetState = tab, label = "tabs") { current ->
            when (current) {
                Tab.HOME -> HomeScreen(vm, onAdd = openAdd)
                Tab.FEED -> FeedScreen(vm, player, onAdd = openAdd, onOptions = { optionsFor = it })
                Tab.LISTEN -> ListenScreen(vm, player, playerState, onOptions = { optionsFor = it })
                Tab.LIBRARY -> LibraryScreen(vm, player, playerState, onOpen = { vm.openVideo(it.id) }, onOptions = { optionsFor = it })
                else -> SettingsScreen(vm)
            }
        }

        // Mini reproductor de música, encima de la barra (en todas las pestañas menos Ver)
        if (currentAudio != null && player != null && playerState != null && tab != Tab.FEED && !fullscreen) {
            MiniPlayer(
                currentAudio, player, playerState,
                onOpen = { vm.nowPlayingOpen.value = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = NavClearance - 4.dp),
            )
        }

        if (!fullscreen) {
            FloatingNav(
                tab, { vm.tab.value = it }, Modifier.align(Alignment.BottomCenter),
                showVideo = vm.showVideo, showMusic = showMusic,
            )
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = NavClearance + if (currentAudio != null && tab != Tab.FEED) MiniPlayerSpace else 0.dp),
        )

        // Pantallas superpuestas: el teclado empuja su contenido en vez de taparlo
        AnimatedVisibility(
            visible = notesFor != null,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            val id = lastNotesId ?: return@AnimatedVisibility
            val video = videos.firstOrNull { it.id == id }
            val notes by remember(id) { vm.notesFor(id) }.collectAsStateWithLifecycle(emptyList())
            NotesScreen(
                title = video?.title.orEmpty(),
                notes = notes,
                autoFocus = notesFocus,
                currentPositionMs = { player.positionFor(id) },
                onSeek = { ms ->
                    player?.takeIf { it.currentMediaItem?.mediaId == id.toString() }?.let { it.seekTo(ms); it.play() }
                    vm.notesOpenFor.value = null
                },
                onAdd = { text, pos -> vm.addNote(id, text, pos) },
                onUpdate = { noteId, text -> vm.updateNote(noteId, text) },
                onDelete = { vm.deleteNote(it) },
                onClose = { vm.notesOpenFor.value = null },
            )
        }

        AnimatedVisibility(
            visible = addOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            val audio = addKind == Kind.AUDIO
            AddLinksScreen(
                audio = audio,
                links = links,
                categories = if (audio) audioCategories else categories,
                defaultCategory = ((if (audio) audioFilter else filter) as? CategoryFilter.Only)?.id,
                onAddText = { vm.addLinks(it) },
                onRemove = { vm.removeLink(it) },
                onSubmit = { vm.submitLinks(it) },
                onCreateCategory = { vm.createCategory(it, kind = addKind) },
                onClose = { vm.closeAdd() },
            )
        }

        AnimatedVisibility(
            visible = savedOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            SavedScreen(
                items = videos,
                videoCategories = categories,
                audioCategories = audioCategories,
                onDelete = { vm.deleteDownloads(it) },
                onClose = { vm.savedOpen.value = false },
            )
        }

        AnimatedVisibility(
            visible = offlineOpen,
            enter = slideInVertically { it / 3 } + fadeIn(),
            exit = slideOutVertically { it / 3 } + fadeOut(),
        ) {
            OfflineScreen(
                items = videos,
                videoCategories = categories,
                audioCategories = audioCategories,
                showVideo = vm.showVideo,
                showMusic = showMusic,
                player = player,
                currentId = playerState?.mediaId?.toLongOrNull(),
                onPlayVideos = { f, shuffle, startId ->
                    vm.openOfflineVideos(f, shuffle)
                    if (startId != null) vm.jumpTo.value = startId
                    vm.offlineOpen.value = false
                },
                onAudioStarted = {
                    vm.offlineOpen.value = false
                    vm.nowPlayingOpen.value = true
                },
                onFavorite = { id, value -> vm.toggleFavorite(id, value) },
                onClose = { vm.offlineOpen.value = false },
            )
        }

        AnimatedVisibility(
            visible = nowPlayingOpen && currentAudio != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            val song = currentAudio
            if (song != null && player != null && playerState != null) {
                NowPlayingScreen(
                    song, player, playerState, vm.prefs,
                    onFavorite = { vm.toggleFavorite(song.id, it) },
                    onBoost = { vm.setBoost(song.id, it) },
                    onClose = { vm.nowPlayingOpen.value = false },
                )
            }
        }

        // Primera vez: preguntar el nombre para el saludo del inicio
        if (userName == null) {
            WelcomeScreen(onDone = { vm.prefs.setUserName(it) })
        }
    }

    // Siempre se muestra la versión más reciente del video (p. ej. el progreso de descarga)
    optionsFor?.let { stale ->
        val v = videos.firstOrNull { it.id == stale.id } ?: stale
        VideoOptionsSheet(
            video = v,
            categories = if (v.isAudio()) audioCategories else categories,
            onDownload = { vm.download(v.id) },
            onCancelDownload = { vm.cancelDownload(v.id) },
            onDeleteDownload = { vm.deleteDownload(v.id) },
            onSetCategory = { vm.setCategory(v.id, it) },
            onCreateCategory = { vm.createCategory(it, assignTo = v.id, kind = v.kind) },
            onRename = { vm.rename(v.id, it) },
            onSetOrientation = { vm.setOrientation(v.id, it) },
            onDelete = { vm.deleteVideo(v.id) },
            onDismiss = { optionsFor = null },
            transferTarget = when {
                !(vm.showVideo && showMusic) -> null
                v.isAudio() -> "Ver"
                else -> "Escuchar"
            },
            onCopyToOther = { vm.copyToSection(v.id, if (v.isAudio()) Kind.VIDEO else Kind.AUDIO, move = false) },
            onMoveToOther = { vm.copyToSection(v.id, if (v.isAudio()) Kind.VIDEO else Kind.AUDIO, move = true) },
            onSuggestGenre = if (v.isAudio()) ({ genreSong = v }) else null,
            onSetBoost = { vm.setBoost(v.id, it) },
        )
    }
    genreSong?.let { song ->
        GenreDialog(
            songs = listOf(song),
            categories = audioCategories,
            onApply = { vm.applyGenres(it) },
            onDismiss = { genreSong = null },
        )
    }
}

private fun Player?.positionFor(videoId: Long): Long? =
    this?.takeIf { it.currentMediaItem?.mediaId == videoId.toString() }?.currentPosition
