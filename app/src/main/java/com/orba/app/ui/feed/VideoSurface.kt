package com.orba.app.ui.feed

import android.view.LayoutInflater
import android.view.View
import com.orba.app.R
import kotlinx.coroutines.delay
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.orba.app.data.Video
import com.orba.app.data.thumbModel
import com.orba.app.ui.PlayerUiState
import com.orba.app.ui.theme.darkGlass

/**
 * Superficie de video. Solo la página activa crea un PlayerView (las demás muestran la
 * miniatura), así el feed se mueve fluido aunque tengas cientos de videos.
 * En vertical, un video vertical llena la pantalla (como en Reels) y uno horizontal se ve completo y centrado.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    video: Video,
    active: Boolean,
    player: Player?,
    playerState: PlayerUiState?,
    audioOnly: Boolean,
    vertical: Boolean,
    onFullscreen: (() -> Unit)?,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    /** En vertical: si el video también es vertical llena la pantalla; si es horizontal se ve completo, centrado. */
    fill: Boolean = vertical,
    /** Controles propios de ExoPlayer (solo en pantalla completa); en el feed se usan los de Orba. */
    useController: Boolean = !vertical,
) {
    val isThis = playerState?.mediaId == video.id.toString()
    val state = playerState?.playbackState
    val ready = active && isThis && (state == Player.STATE_READY || state == Player.STATE_ENDED)
    var started by remember(video.id, active) { mutableStateOf(false) }
    // La miniatura se quita cuando ya se dibujó el primer cuadro de ESTE video (no el del anterior)
    val firstFrame = active && playerState?.firstFrameMediaId == video.id.toString()
    LaunchedEffect(firstFrame) { if (firstFrame) started = true }
    LaunchedEffect(ready) {
        if (ready) {
            delay(400) // respaldo por si el aviso del primer cuadro no llega
            started = true
        }
    }

    // Medida real del video (la que reporta el reproductor, no la guardada): con ella se arma el
    // cuadro exacto. Mientras no se conoce, la miniatura sigue tapando, así nunca se ve estirado.
    val aspect = playerState?.videoAspect?.takeIf { active && playerState?.videoAspectMediaId == video.id.toString() }

    Box(modifier.background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
        if (audioOnly || !active) {
            Thumb(video, vertical && fill, blurred = audioOnly && active)
        }

        if (active && player != null) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.orba_player_view, null) as PlayerView).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        // Los controles solo aparecen si tocas el video, nunca solos al cambiar de video
                        controllerAutoShow = false
                        controllerShowTimeoutMs = 2500
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                    }
                },
                update = { pv ->
                    if (pv.player !== player) pv.player = player
                    pv.useController = useController
                    // En "solo audio" se deja ver la miniatura por detrás
                    pv.setShutterBackgroundColor(if (audioOnly) android.graphics.Color.TRANSPARENT else android.graphics.Color.BLACK)
                    if (!useController) pv.hideController()
                    // Con la medida conocida, el cuadro ya tiene la forma exacta del video: se llena tal cual.
                    pv.resizeMode = when {
                        aspect != null -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        vertical && fill -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                    pv.videoSurfaceView?.visibility = if (audioOnly) View.INVISIBLE else View.VISIBLE
                    if (onFullscreen != null) pv.setFullscreenButtonClickListener { onFullscreen() }
                },
                onRelease = { it.player = null },
                modifier = Modifier.videoFrame(aspect, cover = vertical && (aspect ?: 1f) < 0.7f),
            )
        }

        // La miniatura tapa el negro hasta que aparece el primer cuadro
        AnimatedVisibility(visible = active && !audioOnly && !started, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Thumb(video, vertical && fill, blurred = false)
                CircularProgressIndicator(Modifier.size(38.dp), color = Color.White.copy(alpha = 0.85f), strokeWidth = 3.dp)
            }
        }

        if (audioOnly && active) {
            Column(
                Modifier.align(if (vertical) Alignment.Center else Alignment.TopCenter).padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(if (vertical) 56.dp else 28.dp))
                Text("Solo audio", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        // Si la conexión se pone lenta a mitad del video, un indicador discreto
        if (active && isThis && started && !audioOnly && state == Player.STATE_BUFFERING) {
            CircularProgressIndicator(Modifier.size(32.dp), color = Color.White.copy(alpha = 0.7f), strokeWidth = 2.dp)
        }

        // Al terminar (sin bucle, o tras las 10 repeticiones): botón para verlo otra vez
        if (active && isThis && state == Player.STATE_ENDED) {
            Box(
                Modifier
                    .size(72.dp)
                    .darkGlass(CircleShape, alpha = 0.55f)
                    .clickable(onClick = onRestart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Replay, "Ver de nuevo", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}

/**
 * Cuadro con la proporción exacta del video, centrado. [cover] = llenar toda la pantalla
 * recortando los bordes (shorts y reels); si no, se ve completo con franjas negras.
 * Sin medida conocida ocupa todo el espacio y el reproductor ajusta solo.
 */
private fun Modifier.videoFrame(aspect: Float?, cover: Boolean): Modifier = layout { measurable, c ->
    val pw = c.maxWidth
    val ph = c.maxHeight
    if (!c.hasBoundedWidth || !c.hasBoundedHeight) {
        val p = measurable.measure(c)
        return@layout layout(p.width, p.height) { p.place(0, 0) }
    }
    if (aspect == null || aspect <= 0f || pw == 0 || ph == 0) {
        val p = measurable.measure(Constraints.fixed(pw, ph))
        return@layout layout(pw, ph) { p.place(0, 0) }
    }
    val parent = pw.toFloat() / ph
    val widthBound = (aspect > parent) != cover
    val w = if (widthBound) pw else (ph * aspect).roundToInt()
    val h = if (widthBound) (pw / aspect).roundToInt() else ph
    val p = measurable.measure(Constraints.fixed(w.coerceAtLeast(1), h.coerceAtLeast(1)))
    layout(pw, ph) { p.place((pw - p.width) / 2, (ph - p.height) / 2) }
}

@Composable
private fun Thumb(video: Video, vertical: Boolean, blurred: Boolean) {
    AsyncImage(
        model = video.thumbModel(),
        contentDescription = null,
        contentScale = if (vertical) ContentScale.Crop else ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .then(if (blurred) Modifier.blur(18.dp) else Modifier),
        alpha = if (blurred) 0.6f else 1f,
    )
}
