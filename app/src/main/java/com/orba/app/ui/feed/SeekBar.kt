package com.orba.app.ui.feed

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.orba.app.ui.formatTime
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.darkGlass
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Barra de reproducción para la vista vertical. Toca un punto para saltar ahí, o
 * mantén presionado y desliza para buscar un momento exacto (se ve el minuto mientras arrastras).
 */
@Composable
fun SeekBar(
    player: Player,
    videoId: Long,
    modifier: Modifier = Modifier,
    /** Barra más delgada (video horizontal). */
    thin: Boolean = false,
    /** Muestra debajo el tiempo actual y el total, en letra pequeña. */
    showTimes: Boolean = false,
    accent: Color = Coral,
    /** Barra finísima para ir encima del video, con el tiempo arriba a la izquierda. */
    hairline: Boolean = false,
) {
    var progress by remember(videoId) { mutableFloatStateOf(0f) }
    var duration by remember(videoId) { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(player, videoId) {
        while (isActive) {
            val isThis = player.currentMediaItem?.mediaId == videoId.toString()
            val d = player.duration
            duration = if (isThis && d > 0) d else 0L
            if (!scrubbing) {
                progress = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            }
            delay(150)
        }
    }

    val barHeight by animateDpAsState(
        when {
            hairline -> if (scrubbing) 4.dp else 1.5.dp
            scrubbing -> if (thin) 4.dp else 6.dp
            else -> if (thin) 2.dp else 3.dp
        },
        label = "barHeight",
    )
    val thumbRadius by animateDpAsState(
        when {
            hairline -> if (scrubbing) 7.dp else 3.dp
            scrubbing -> if (thin) 7.dp else 9.dp
            else -> if (thin) 4.dp else 5.dp
        },
        label = "thumb",
    )
    val shown = if (scrubbing) scrubFraction else progress

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (hairline) {
            // Tiempo que va / total, pequeño y sobre el video
            val current = if (scrubbing) (scrubFraction * duration).toLong() else (progress * duration).toLong()
            Text(
                if (duration > 0) "${formatTime(current)} / ${formatTime(duration)}" else "",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (scrubbing) 13.sp else 10.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 6f),
                ),
                color = if (scrubbing) accent else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.Start).padding(start = 2.dp),
            )
        }
        if (scrubbing && duration > 0 && !showTimes && !hairline) {
            Text(
                "${formatTime((scrubFraction * duration).toLong())} / ${formatTime(duration)}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .darkGlass(RoundedCornerShape(14.dp), alpha = 0.6f)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        // Zona táctil alta (28 dp) aunque la barra se vea fina
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (thin || hairline) 22.dp else 28.dp)
                .pointerInput(player, videoId) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (duration <= 0) return@awaitEachGesture
                        down.consume()
                        scrubbing = true
                        scrubFraction = (down.position.x / size.width).coerceIn(0f, 1f)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            change.consume()
                        }
                        player.seekTo((scrubFraction * duration).toLong())
                        progress = scrubFraction
                        scrubbing = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val h = barHeight.toPx()
                val y = size.height / 2
                val r = CornerRadius(h / 2, h / 2)
                drawRoundRect(Color.White.copy(alpha = if (hairline) 0.28f else 0.22f), Offset(0f, y - h / 2), Size(size.width, h), r)
                drawRoundRect(if (scrubbing || thin) accent else Color.White, Offset(0f, y - h / 2), Size(size.width * shown, h), r)
                drawCircle(if (scrubbing || thin) accent else Color.White, thumbRadius.toPx(), Offset(size.width * shown, y))
            }
        }
        if (showTimes) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                val current = if (scrubbing) (scrubFraction * duration).toLong() else (progress * duration).toLong()
                Text(
                    formatTime(current),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (scrubbing) accent else Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    if (duration > 0) formatTime(duration) else "--:--",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}
