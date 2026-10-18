package com.orba.app.ui

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.orba.app.playback.PlaybackService

/** Se conecta al servicio de reproducción; el reproductor vive allá, no en la pantalla. */
@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(context))
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

/** Estado observable del reproductor para que la interfaz se actualice sola. */
@Stable
class PlayerUiState(private val player: Player) : Player.Listener {
    var mediaId by mutableStateOf(player.currentMediaItem?.mediaId)
        private set
    var isPlaying by mutableStateOf(player.isPlaying)
        private set
    var playbackState by mutableIntStateOf(player.playbackState)
        private set
    var playWhenReady by mutableStateOf(player.playWhenReady)
        private set
    /** Video cuyo primer cuadro ya se dibujó (para quitar la miniatura justo a tiempo, sin parpadeos). */
    var firstFrameMediaId by mutableStateOf<String?>(null)
        private set

    /**
     * Proporción real (ancho / alto) del video que suena, y de cuál es. La interfaz arma el
     * cuadro con esta medida, así un video nunca se ve estirado aunque venga del anterior.
     */
    var videoAspect by mutableStateOf<Float?>(null)
        private set
    var videoAspectMediaId by mutableStateOf<String?>(null)
        private set

    init {
        player.addListener(this)
        readVideoSize()
    }

    private fun readVideoSize() {
        val v = player.videoSize
        if (v.width > 0 && v.height > 0) {
            val ratio = v.width * (if (v.pixelWidthHeightRatio > 0f) v.pixelWidthHeightRatio else 1f) / v.height
            videoAspect = ratio
            videoAspectMediaId = player.currentMediaItem?.mediaId
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        mediaId = player.currentMediaItem?.mediaId
        isPlaying = player.isPlaying
        playbackState = player.playbackState
        playWhenReady = player.playWhenReady
        if (events.contains(Player.EVENT_VIDEO_SIZE_CHANGED) || events.contains(Player.EVENT_RENDERED_FIRST_FRAME)) readVideoSize()
    }

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            firstFrameMediaId = null
            videoAspect = null
            videoAspectMediaId = null
        }
    }

    override fun onRenderedFirstFrame() {
        firstFrameMediaId = player.currentMediaItem?.mediaId
        readVideoSize()
    }

    fun release() = player.removeListener(this)
}

@Composable
fun rememberPlayerUiState(player: Player?): PlayerUiState? {
    val state = remember(player) { player?.let { PlayerUiState(it) } }
    DisposableEffect(state) { onDispose { state?.release() } }
    return state
}
