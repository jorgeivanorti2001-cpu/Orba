package com.orba.app.ui.listen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.orba.app.playback.AudioLevels
import com.orba.app.ui.theme.OrbaBlue
import com.orba.app.ui.theme.OrbaCyan
import com.orba.app.ui.theme.OrbaMagenta
import com.orba.app.ui.theme.OrbaViolet
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Una onda que nació con un golpe de la canción. */
internal class Wave(val born: Float, val strength: Float, val color: Int)

/**
 * El pulso de la canción, cuadro a cuadro: volumen, graves y golpes (bombo, bajo).
 * Lo leen la onda de la carátula y la carátula misma, que late con los graves.
 */
@Stable
class AudioPulse {
    var time by mutableFloatStateOf(0f)
        internal set
    /** Volumen suavizado, 0 a 1. */
    var level by mutableFloatStateOf(0f)
        internal set
    /** Graves suavizados, 0 a 1 (para que la carátula "lata"). */
    var bass by mutableFloatStateOf(0f)
        internal set
    /** Se incrementa en cada cuadro para redibujar. */
    var frame by mutableIntStateOf(0)
        internal set

    internal val waves = ArrayList<Wave>()
    internal var bassAverage = 0.02f
    internal var lastBeat = -10f
    internal var lastAudio = -10f
    internal var nextColor = 0
    var energy by mutableFloatStateOf(0f)
        internal set
}

@Composable
fun rememberAudioPulse(playing: Boolean): AudioPulse {
    val pulse = remember { AudioPulse() }
    LaunchedEffect(playing) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
                pulse.step(dt, playing)
            }
            if (!playing && pulse.energy < 0.01f && pulse.waves.isEmpty()) break
        }
    }
    return pulse
}

private fun AudioPulse.step(dt: Float, playing: Boolean) {
    time += dt
    energy += ((if (playing) 1f else 0f) - energy) * min(1f, dt * 3f)
    val sample = if (playing) AudioLevels.now() else null
    if (sample != null) {
        lastAudio = time
        // El volumen de una canción normal ronda 0.1–0.4: se lleva a 0–1
        val lv = min(1f, sample[0] * 3.2f)
        val bs = sample[1]
        level += (lv - level) * min(1f, dt * 18f)
        val bassNorm = min(1f, bs * 4.5f)
        // Sube rápido con cada golpe y baja despacio, como un latido
        bass = if (bassNorm > bass) bass + (bassNorm - bass) * min(1f, dt * 30f) else bass + (bassNorm - bass) * min(1f, dt * 6f)
        // Golpe: los graves saltan por encima de su promedio reciente
        bassAverage += (bs - bassAverage) * min(1f, dt * 2.5f)
        if (bs > bassAverage * 1.35f && bs > 0.015f && time - lastBeat > 0.22f) {
            lastBeat = time
            waves += Wave(time, min(1f, 0.45f + (bs / max(bassAverage, 0.001f) - 1f)), nextColor++)
        }
    } else {
        level += (0f - level) * min(1f, dt * 4f)
        bass += (0f - bass) * min(1f, dt * 4f)
        // Si no llega audio (por ejemplo, otra salida de sonido), un movimiento suave de respaldo
        if (playing && time - lastAudio > 1.5f && time - lastBeat > 0.9f) {
            lastBeat = time
            waves += Wave(time, 0.5f, nextColor++)
        }
    }
    waves.removeAll { time - it.born > WAVE_LIFE }
    frame++
}

private const val WAVE_LIFE = 1.6f

/**
 * Detrás de la carátula: ondas con la misma forma de la carátula que nacen con cada golpe
 * de la canción (bombo, bajo) y se desvanecen hacia afuera, un borde que vibra con el volumen,
 * un resplandor y notas musicales que flotan. No usa el micrófono ni pide permisos.
 *
 * [coverFraction] es el ancho de la carátula respecto al área; [cornerFraction] su redondeo.
 */
@Composable
fun MusicVisualizer(pulse: AudioPulse, coverFraction: Float, modifier: Modifier = Modifier, cornerFraction: Float = 0.11f) {
    val measurer = rememberTextMeasurer()
    val notes = remember { listOf("♪", "♫", "♬", "♩") }
    val waveColors = remember { listOf(OrbaCyan, OrbaBlue, OrbaViolet, OrbaMagenta) }

    Canvas(modifier) {
        val tick = pulse.frame // leerlo hace que se redibuje en cada cuadro
        if (tick < 0) return@Canvas
        val time = pulse.time
        val energy = pulse.energy
        val level = pulse.level
        val bass = pulse.bass
        val c = Offset(size.width / 2f, size.height / 2f)
        val cover = size.minDimension * coverFraction
        val room = (size.minDimension - cover) / 2f

        // Resplandor que respira con el volumen
        if (energy > 0.01f) {
            drawRect(
                Brush.radialGradient(
                    listOf(
                        OrbaViolet.copy(alpha = (0.18f + 0.40f * level) * energy),
                        OrbaBlue.copy(alpha = (0.06f + 0.14f * bass) * energy),
                        Color.Transparent,
                    ),
                    center = c,
                    radius = cover * 0.5f + room * (0.7f + 0.3f * bass),
                ),
            )
        }

        // Borde pegado a la carátula: más grueso y brillante cuando la canción sube
        if (energy > 0.01f) {
            val side = cover + room * 0.10f * (0.4f + bass)
            drawRoundRect(
                color = OrbaCyan.copy(alpha = (0.25f + 0.55f * level) * energy),
                topLeft = Offset(c.x - side / 2f, c.y - side / 2f),
                size = Size(side, side),
                cornerRadius = CornerRadius(side * cornerFraction),
                style = Stroke(width = (1.5f + 4f * bass) * density),
            )
        }

        // Ondas que salen con cada golpe
        pulse.waves.forEach { w ->
            val phase = ((time - w.born) / WAVE_LIFE).coerceIn(0f, 1f)
            val eased = 1f - (1f - phase) * (1f - phase)
            val grow = room * eased
            val side = cover + grow * 2f
            val alpha = (1f - phase) * (1f - phase) * (0.35f + 0.55f * w.strength) * energy
            if (alpha < 0.01f) return@forEach
            drawRoundRect(
                color = waveColors[w.color % waveColors.size].copy(alpha = alpha.coerceAtMost(1f)),
                topLeft = Offset(c.x - side / 2f, c.y - side / 2f),
                size = Size(side, side),
                cornerRadius = CornerRadius(side * cornerFraction),
                style = Stroke(width = (1.2f + 3.2f * w.strength * (1f - phase)) * density),
            )
        }

        // Notas musicales que suben alrededor de la carátula (más rápido si la canción va fuerte)
        if (energy > 0.05f) {
            for (n in 0 until 8) {
                val life = ((time * (0.12f + 0.10f * level) + n * 0.125f) % 1f)
                val sideSign = if (n % 2 == 0) -1f else 1f
                val spread = cover / 2f + room * (0.25f + 0.6f * (((n * 37) % 10) / 10f))
                val x = c.x + sideSign * spread + sin(time * 1.2f + n) * 10f * density
                val y = c.y + cover * 0.45f - life * cover * 1.1f
                val alpha = (sin(life * PI.toFloat()) * (0.55f + 0.4f * level) * energy).coerceIn(0f, 1f)
                if (x < 0f || y < 0f || x > size.width - 40f || y > size.height - 40f) continue
                drawText(
                    textMeasurer = measurer,
                    text = notes[n % notes.size],
                    topLeft = Offset(x - 10f, y - 14f),
                    style = TextStyle(color = waveColors[n % waveColors.size].copy(alpha = alpha), fontSize = (15 + (n % 3) * 5).sp),
                )
            }
        }
    }
}
