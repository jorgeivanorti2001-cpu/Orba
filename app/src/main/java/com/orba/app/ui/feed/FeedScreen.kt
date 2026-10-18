package com.orba.app.ui.feed

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.StayCurrentLandscape
import androidx.compose.material.icons.rounded.StayCurrentPortrait
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.orba.app.data.Category
import com.orba.app.data.Note
import com.orba.app.data.Orientation
import com.orba.app.data.Video
import com.orba.app.data.isDownloaded
import com.orba.app.data.showsVertical
import com.orba.app.data.DownloadState
import com.orba.app.engine.Engine
import com.orba.app.engine.StreamResolver
import com.orba.app.ui.MainViewModel
import com.orba.app.ui.PlayerUiState
import com.orba.app.ui.components.ActionButton
import com.orba.app.ui.components.CategoryChips
import com.orba.app.ui.components.NotePreview
import com.orba.app.ui.components.Pill
import com.orba.app.ui.components.downloadVisual
import com.orba.app.ui.formatDuration
import com.orba.app.ui.rememberPlayerUiState
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.BrandGradient
import com.orba.app.ui.theme.Mint
import androidx.compose.material.icons.rounded.CloudDone
import com.orba.app.ui.theme.NavClearance
import com.orba.app.ui.theme.Rose
import com.orba.app.ui.theme.darkGlass
import com.orba.app.ui.theme.glass
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Los videos de hasta este largo siempre empiezan desde el inicio (como un reel). */
private const val SHORT_VIDEO_SEC = 90

@Composable
fun FeedScreen(
    vm: MainViewModel,
    player: Player?,
    onAdd: () -> Unit,
    onOptions: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val feed by vm.feed.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val noteCounts by vm.noteCounts.collectAsStateWithLifecycle()
    val audioOnly by vm.prefs.audioOnly.collectAsStateWithLifecycle()
    val maxHeight by vm.prefs.maxHeight.collectAsStateWithLifecycle()
    val jumpTo by vm.jumpTo.collectAsStateWithLifecycle()
    val fullscreen by vm.fullscreen.collectAsStateWithLifecycle()
    val allVideos by vm.videos.collectAsStateWithLifecycle()
    val offline by vm.feedOffline.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    val playerState = rememberPlayerUiState(player)

    val pagerState = rememberPagerState { feed.size }
    var positioned by remember { mutableStateOf(false) }

    // Al entrar: ir al video pedido, o al que ya está sonando, o al primero
    LaunchedEffect(feed.isNotEmpty()) {
        if (feed.isEmpty() || positioned) return@LaunchedEffect
        val target = vm.jumpTo.value ?: player?.currentMediaItem?.mediaId?.toLongOrNull()
        val index = target?.let { id -> feed.indexOfFirst { it.id == id } } ?: -1
        if (index > 0) pagerState.scrollToPage(index)
        vm.jumpTo.value = null
        positioned = true
    }

    // Saltos posteriores (p. ej. al terminar de agregar videos)
    LaunchedEffect(jumpTo, feed, positioned) {
        if (!positioned) return@LaunchedEffect
        val id = jumpTo ?: return@LaunchedEffect
        val index = feed.indexOfFirst { it.id == id }
        if (index >= 0) {
            pagerState.scrollToPage(index)
            vm.jumpTo.value = null
        }
    }

    // Al cambiar de categoría estando aquí, vuelve al primero
    var lastFilter by remember { mutableStateOf(filter) }
    LaunchedEffect(filter) {
        if (filter != lastFilter) {
            lastFilter = filter
            if (vm.jumpTo.value == null && pagerState.pageCount > 0) pagerState.scrollToPage(0)
        }
    }

    // El video de la página visible es el que suena; los vecinos se preparan por adelantado
    val settledVideo = feed.getOrNull(pagerState.settledPage)
    LaunchedEffect(player, settledVideo?.id, positioned) {
        if (!positioned) return@LaunchedEffect
        val p = player ?: return@LaunchedEffect
        val v = settledVideo ?: return@LaunchedEffect
        if (p.currentMediaItem?.mediaId != v.id.toString()) {
            val fromStart = v.durationSec in 1..SHORT_VIDEO_SEC ||
                (v.durationSec > 0 && v.lastPositionMs > (v.durationSec - 3) * 1000L)
            p.setMediaItem(MediaItem.Builder().setMediaId(v.id.toString()).build(), if (fromStart) 0 else v.lastPositionMs)
            p.prepare()
            p.play()
        } else if (!p.playWhenReady && p.playbackState != Player.STATE_ENDED) {
            p.play()
        }
    }

    // Ventana de preparación: apenas el dedo pasa la mitad de la pantalla ya se sabe cuál
    // video viene, y se preparan ese y los siguientes. Lo que quedó lejos se cancela.
    val nearPage = pagerState.currentPage
    val currentUrl = feed.getOrNull(nearPage)?.takeIf { !it.isDownloaded() }?.sourceUrl
    val aheadUrls = listOf(nearPage + 1, nearPage + 2, nearPage - 1)
        .mapNotNull { feed.getOrNull(it) }
        .filter { !it.isDownloaded() }
        .map { it.sourceUrl }
    LaunchedEffect(currentUrl, aheadUrls, maxHeight, positioned) {
        if (!positioned) return@LaunchedEffect
        StreamResolver.focus(context, currentUrl, aheadUrls, maxHeight, warmNext = aheadUrls.firstOrNull())
    }

    // Si un video no se pudo ver en línea y se terminó de descargar, arranca solo desde el teléfono
    LaunchedEffect(settledVideo?.id, settledVideo?.downloadState) {
        val p = player ?: return@LaunchedEffect
        val v = settledVideo ?: return@LaunchedEffect
        if (v.downloadState == DownloadState.DONE && p.currentMediaItem?.mediaId == v.id.toString() &&
            p.playbackState == Player.STATE_IDLE
        ) {
            p.setMediaItem(MediaItem.Builder().setMediaId(v.id.toString()).build(), 0)
            p.prepare()
            p.play()
        }
    }

    LaunchedEffect(feed.isEmpty(), player) {
        if (feed.isEmpty()) player?.let { it.stop(); it.clearMediaItems() }
    }

    val restart: () -> Unit = {
        player?.let { it.seekTo(0); it.play() }
    }
    val positionOf: (Video) -> Long? = { v ->
        player?.takeIf { it.currentMediaItem?.mediaId == v.id.toString() }?.currentPosition
    }
    val seekTo: (Video, Long) -> Unit = { v, ms ->
        player?.takeIf { it.currentMediaItem?.mediaId == v.id.toString() }?.let { it.seekTo(ms); it.play() }
    }

    if (fullscreen && settledVideo != null) {
        FullscreenPlayer(settledVideo, player, playerState, audioOnly, restart) { vm.fullscreen.value = false }
        return
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (feed.isEmpty()) {
            EmptyFeed(hasAny = allVideos.isNotEmpty(), onAdd = onAdd)
        } else {
            VerticalPager(
                state = pagerState,
                key = { index -> feed.getOrNull(index)?.id ?: index },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val video = feed.getOrNull(page) ?: return@VerticalPager
                val active = page == pagerState.settledPage
                val category = categories.firstOrNull { it.id == video.categoryId }
                val actions = PageActions(
                    onDownload = {
                        when (video.downloadState) {
                            DownloadState.NONE, DownloadState.FAILED -> vm.download(video.id)
                            else -> onOptions(video)
                        }
                    },
                    onAudio = { vm.prefs.setAudioOnly(!audioOnly) },
                    onRestart = restart,
                    onNotes = { vm.openNotes(video.id, focus = false) },
                    onWriteNote = { vm.openNotes(video.id, focus = true) },
                    onOrientation = { vm.setOrientation(video.id, it) },
                    onOptions = { onOptions(video) },
                    onSeek = { seekTo(video, it) },
                )
                if (video.showsVertical()) {
                    VerticalPage(video, active, player, playerState, audioOnly, category, noteCounts[video.id] ?: 0, actions)
                } else {
                    HorizontalPage(
                        vm, video, active, player, playerState, audioOnly, category, noteCounts[video.id] ?: 0, actions,
                        onFullscreen = { vm.fullscreen.value = true },
                    )
                }
            }
        }

        // Barra superior translúcida con las categorías
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (offline) {
                // Modo Guardados (sin conexión): tocar para volver a ver todo
                Row(
                    Modifier
                        .padding(start = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Mint.copy(alpha = 0.85f))
                        .clickable { vm.exitOfflineFeed() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CloudDone, null, tint = Color(0xFF06231B), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Guardados  ✕", style = MaterialTheme.typography.labelLarge, color = Color(0xFF06231B))
                }
            }
            CategoryChips(categories, filter, { vm.filter.value = it }, Modifier.weight(1f), onDark = true)
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(BrandGradient))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, "Agregar videos", tint = Color.White)
                if (adding > 0) androidx.compose.material3.CircularProgressIndicator(
                    Modifier.size(40.dp), strokeWidth = 2.dp, color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

private class PageActions(
    val onDownload: () -> Unit,
    val onAudio: () -> Unit,
    val onRestart: () -> Unit,
    val onNotes: () -> Unit,
    val onWriteNote: () -> Unit,
    val onOrientation: (Int) -> Unit,
    val onOptions: () -> Unit,
    val onSeek: (Long) -> Unit,
)

// ---------------- Página vertical (shorts / reels) ----------------

@Composable
private fun VerticalPage(
    video: Video,
    active: Boolean,
    player: Player?,
    playerState: PlayerUiState?,
    audioOnly: Boolean,
    category: Category?,
    noteCount: Int,
    actions: PageActions,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(
            video, active, player, playerState, audioOnly, vertical = true, fill = video.isVertical,
            onFullscreen = null, onRestart = actions.onRestart, modifier = Modifier.fillMaxSize(),
        )

        // Toque: pausa / reanuda. Doble toque a los lados: ±10 s.
        if (active && player != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(player) {
                        detectTapGestures(
                            onTap = {
                                if (player.playbackState == Player.STATE_ENDED) actions.onRestart()
                                else if (player.playWhenReady) player.pause() else player.play()
                            },
                            onDoubleTap = { offset ->
                                if (offset.x < size.width / 2f) player.seekBack() else player.seekForward()
                            },
                        )
                    }
            )
            val isThis = playerState?.mediaId == video.id.toString()
            if (playerState != null && isThis && !playerState.playWhenReady && playerState.playbackState == Player.STATE_READY) {
                Box(
                    Modifier.align(Alignment.Center).size(76.dp).darkGlass(CircleShape, alpha = 0.40f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(320.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 72.dp, bottom = NavClearance + 26.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MetaPills(video, category)
            Text(
                video.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            video.uploader?.let {
                Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 4.dp, bottom = NavClearance + 22.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val dl = downloadVisual(video)
            ActionButton(dl.icon, dl.label, actions.onDownload, tint = dl.tint, progress = dl.progress, onDark = true, compact = true)
            ActionButton(Icons.Rounded.Headphones, "Audio", actions.onAudio, active = audioOnly, onDark = true, compact = true)
            ActionButton(Icons.Rounded.Replay, "Reiniciar", actions.onRestart, onDark = true, compact = true)
            ActionButton(Icons.AutoMirrored.Rounded.Notes, "Notas", actions.onNotes, onDark = true, badge = noteCount, compact = true)
            ActionButton(Icons.Rounded.StayCurrentLandscape, "Horizontal", { actions.onOrientation(Orientation.HORIZONTAL) }, onDark = true, compact = true)
            ActionButton(Icons.Rounded.MoreHoriz, "Más", actions.onOptions, onDark = true, compact = true)
        }

        if (active && player != null) {
            SeekBar(
                player, video.id,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = NavClearance - 8.dp)
                    .fillMaxWidth(),
                hairline = true,
            )
        }
    }
}

// ---------------- Página horizontal (videos normales) ----------------

@Composable
private fun HorizontalPage(
    vm: MainViewModel,
    video: Video,
    active: Boolean,
    player: Player?,
    playerState: PlayerUiState?,
    audioOnly: Boolean,
    category: Category?,
    noteCount: Int,
    actions: PageActions,
    onFullscreen: () -> Unit,
) {
    val ratio = if (video.width > 0 && video.height > 0) (video.width.toFloat() / video.height).coerceIn(1f, 2.4f) else 16f / 9f
    Column(Modifier.fillMaxSize().background(Color(0xFF0A0B0F))) {
        Spacer(Modifier.statusBarsPadding().height(64.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(ratio)) {
            VideoSurface(
                video, active, player, playerState, audioOnly, vertical = false,
                onFullscreen = onFullscreen, onRestart = actions.onRestart,
                modifier = Modifier.fillMaxSize(),
                useController = false,
            )
            if (active && player != null) {
                HorizontalOverlay(player, playerState, video.id, onFullscreen, actions.onRestart)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
                .navigationBarsPadding()
                .padding(bottom = NavClearance),
        ) {
            Text(video.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            MetaPills(video, category, light = true)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val dl = downloadVisual(video)
                ActionButton(dl.icon, dl.label, actions.onDownload, tint = dl.tint, progress = dl.progress)
                ActionButton(Icons.Rounded.Headphones, "Audio", actions.onAudio, active = audioOnly)
                ActionButton(Icons.Rounded.Replay, "Reiniciar", actions.onRestart)
                ActionButton(Icons.AutoMirrored.Rounded.Notes, "Notas", actions.onNotes, badge = noteCount)
                ActionButton(Icons.Rounded.StayCurrentPortrait, "Vertical", { actions.onOrientation(Orientation.AUTO) })
                ActionButton(Icons.Rounded.MoreHoriz, "Más", actions.onOptions)
            }
            Spacer(Modifier.height(16.dp))

            // Notas debajo del video (al tocar se abre la pantalla de notas, con el teclado sin tapar nada)
            Row(
                Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(18.dp))
                    .clickable(onClick = actions.onWriteNote)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.EditNote, null, tint = Coral)
                Spacer(Modifier.width(10.dp))
                Text("Escribe una nota…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (active) {
                val notes by remember(video.id) { vm.notesFor(video.id) }.collectAsStateWithLifecycle(emptyList<Note>())
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notes.takeLast(3).forEach { n -> NotePreview(n, onSeek = actions.onSeek, onOpen = actions.onNotes) }
                    }
                    if (notes.size > 3) {
                        TextButton(onClick = actions.onNotes) { Text("Ver las ${notes.size} notas") }
                    }
                }
            }
        }
    }
}

/**
 * Controles del video horizontal: la barra finita con el tiempo va siempre sobre el borde
 * inferior. Un toque muestra −10 s, pausa/reanudar y +10 s (se ocultan solos en 3 s).
 */
@Composable
private fun BoxScope.HorizontalOverlay(
    player: Player,
    playerState: PlayerUiState?,
    videoId: Long,
    onFullscreen: () -> Unit,
    onRestart: () -> Unit,
) {
    var visible by remember(videoId) { mutableStateOf(false) }
    var touchedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(visible, touchedAt) {
        if (visible) {
            delay(3_000)
            visible = false
        }
    }
    val bump = { touchedAt = System.currentTimeMillis() }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(player) {
                detectTapGestures(
                    onTap = { visible = !visible; bump() },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2f) player.seekBack() else player.seekForward()
                        visible = true; bump()
                    },
                )
            }
    )

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.fadeOut(),
        modifier = Modifier.matchParentSize(),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ControlCircle(Icons.Rounded.Replay10, "Atrás 10 segundos", 46.dp) { player.seekBack(); bump() }
                val ended = playerState?.playbackState == Player.STATE_ENDED
                val playing = playerState?.playWhenReady == true && !ended
                ControlCircle(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Reproducir o pausar", 60.dp) {
                    when {
                        ended -> onRestart()
                        player.playWhenReady -> player.pause()
                        else -> player.play()
                    }
                    bump()
                }
                ControlCircle(Icons.Rounded.Forward10, "Adelantar 10 segundos", 46.dp) { player.seekForward(); bump() }
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .darkGlass(CircleShape, alpha = 0.40f)
                    .clickable(onClick = onFullscreen),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Fullscreen, "Pantalla completa", tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
    }

    SeekBar(
        player, videoId,
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        hairline = true,
    )
}

@Composable
private fun ControlCircle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).darkGlass(CircleShape, alpha = 0.45f).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
private fun MetaPills(video: Video, category: Category?, light: Boolean = false) {
    val bg = if (light) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.45f)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(Engine.platformLabel(video.platform), color = bg)
        formatDuration(video.durationSec).takeIf { it.isNotEmpty() }?.let { Pill(it, color = bg) }
        category?.let { Pill(it.name, color = Color(it.color).copy(alpha = 0.40f)) }
        if (video.unavailableOnline) {
            Pill(
                if (video.isDownloaded()) "Ya no está en línea · guardado" else "Ya no está en línea",
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.45f),
                icon = Icons.Rounded.CloudOff,
            )
        }
    }
}

@Composable
private fun EmptyFeed(hasAny: Boolean, onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp).glass(RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.VideoLibrary, null, tint = Coral, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(if (hasAny) "No hay videos en esta categoría" else "Tu lista está vacía", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Agrega enlaces de YouTube, Facebook, Instagram o TikTok con el botón +, o usa «Compartir» desde esas apps.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------- Pantalla completa ----------------

@Composable
private fun FullscreenPlayer(
    video: Video,
    player: Player?,
    playerState: PlayerUiState?,
    audioOnly: Boolean,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    BackHandler(onBack = onExit)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(
            video, active = true, player = player, playerState = playerState, audioOnly = audioOnly,
            vertical = false, onFullscreen = onExit, onRestart = onRestart, modifier = Modifier.fillMaxSize(),
        )
    }
}
