package com.orba.app.ui.listen

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.orba.app.data.Prefs
import com.orba.app.data.Video
import com.orba.app.data.thumbModel
import com.orba.app.playback.AudioLevels
import com.orba.app.ui.theme.Coral
import com.orba.app.ui.theme.OrbaBlue
import com.orba.app.ui.theme.OrbaCyan
import com.orba.app.ui.theme.OrbaMagenta
import com.orba.app.ui.theme.OrbaViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Una onda que nació con un golpe de la canción. */
internal class Wave(val born: Float, val strength: Float, val color: Int)

/**
 * El pulso de la canción, cuadro a cuadro: volumen, graves, golpes (bombo, bajo) y espectro.
 * Lo leen las animaciones de la pantalla de la canción.
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
    /** 1 mientras suena, baja a 0 al pausar. */
    var energy by mutableFloatStateOf(0f)
        internal set

    /** Espectro suavizado (graves → agudos), 0 a 1. */
    val bands = FloatArray(AudioLevels.BANDS)
    internal val raw = FloatArray(AudioLevels.BANDS)
    internal val waves = ArrayList<Wave>()
    internal var bassAverage = 0.02f
    internal var lastBeat = -10f
    internal var lastAudio = -10f
    internal var nextColor = 0
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
            if (!playing && pulse.energy < 0.01f && pulse.waves.isEmpty() && pulse.bands.all { it < 0.01f }) break
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
        val lv = min(1f, sample[0] * 3.2f)
        val bs = sample[1]
        level += (lv - level) * min(1f, dt * 18f)
        val bassNorm = min(1f, bs * 4.5f)
        // Sube rápido con cada golpe y baja despacio, como un latido
        bass += (bassNorm - bass) * min(1f, dt * (if (bassNorm > bass) 30f else 6f))
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
    val hasSpectrum = playing && AudioLevels.spectrumNow(raw)
    for (i in bands.indices) {
        val target = if (hasSpectrum) raw[i] else 0f
        val speed = if (target > bands[i]) 28f else 7f
        bands[i] += (target - bands[i]) * min(1f, dt * speed)
    }
    waves.removeAll { time - it.born > WAVE_LIFE }
    frame++
}

private const val WAVE_LIFE = 1.6f

// ---------------- Colores ----------------

private val AppColors = listOf(OrbaCyan, OrbaBlue, OrbaViolet, OrbaMagenta)
private val VizBlack = Color(0xFF0B0C12)
private val VizGray = Color(0xFFD4D6DD)

/** Colores del visualizador según lo elegido (los de la app, los de la carátula, blanco, negro o gris claro). */
fun vizPalette(option: Int, cover: List<Color>?): List<Color> = when (option) {
    Prefs.VIZ_COLOR_COVER -> cover?.takeIf { it.isNotEmpty() } ?: AppColors
    Prefs.VIZ_COLOR_WHITE -> listOf(Color.White)
    Prefs.VIZ_COLOR_BLACK -> listOf(VizBlack)
    Prefs.VIZ_COLOR_GRAY -> listOf(VizGray)
    else -> AppColors
}

/** Color en una posición del degradado (0 a 1). */
private fun List<Color>.at(f: Float): Color {
    if (size == 1) return first()
    val x = f.coerceIn(0f, 1f) * (size - 1)
    val i = x.toInt().coerceAtMost(size - 2)
    return lerp(this[i], this[i + 1], x - i)
}

/** Borde que separa el dibujo de lo que hay detrás: claro si el color es oscuro y al revés. */
private fun contrastFor(c: Color): Color =
    if (c.luminance() < 0.35f) Color.White.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.42f)

/** Saca de la carátula hasta 3 colores vivos (para la opción "Carátula"). */
@Composable
fun rememberCoverColors(song: Video): List<Color>? {
    val context = LocalContext.current
    var colors by remember(song.id) { mutableStateOf<List<Color>?>(null) }
    LaunchedEffect(song.id, song.localThumbPath, song.thumbnailUrl) {
        colors = withContext(Dispatchers.IO) {
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(song.thumbModel()).size(48).allowHardware(false).build()
                )
                val bmp = (result as? SuccessResult)?.drawable?.toBitmap(48, 48, Bitmap.Config.ARGB_8888)
                bmp?.let { vividColors(it) }
            }.getOrNull()
        }
    }
    return colors
}

private fun vividColors(bmp: Bitmap): List<Color>? {
    val buckets = 12
    val weight = FloatArray(buckets)
    val r = FloatArray(buckets); val g = FloatArray(buckets); val b = FloatArray(buckets)
    val hsv = FloatArray(3)
    for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
        val p = bmp.getPixel(x, y)
        android.graphics.Color.colorToHSV(p, hsv)
        if (hsv[1] < 0.30f || hsv[2] < 0.30f) continue
        val k = ((hsv[0] / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
        val w = hsv[1] * hsv[2]
        weight[k] += w
        r[k] += android.graphics.Color.red(p) * w
        g[k] += android.graphics.Color.green(p) * w
        b[k] += android.graphics.Color.blue(p) * w
    }
    val top = weight.indices.filter { weight[it] > 0.5f }.sortedByDescending { weight[it] }.take(3)
    if (top.isEmpty()) return null
    return top.map { k ->
        val c = android.graphics.Color.rgb((r[k] / weight[k]).toInt(), (g[k] / weight[k]).toInt(), (b[k] / weight[k]).toInt())
        android.graphics.Color.colorToHSV(c, hsv)
        // Más luminoso, para que se vea sobre el fondo oscuro
        hsv[2] = max(hsv[2], 0.85f)
        hsv[1] = max(hsv[1], 0.45f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
}

// ---------------- Dibujo ----------------

/**
 * Lo que va detrás de la carátula según el estilo elegido:
 *  - Ondas: salen de la carátula con cada golpe de la canción.
 *  - Notas: notas musicales que suben como burbujas (sin seguir el ritmo).
 *  - Barras: un ecualizador pequeño en el espacio de abajo de la carátula.
 *  (El espectro sobre la carátula se dibuja aparte, encima, con [SpectrumOverCover].)
 *
 * [coverFraction] es el ancho de la carátula respecto al área; [cornerFraction] su redondeo.
 */
@Composable
fun MusicVisualizer(
    pulse: AudioPulse,
    mode: Int,
    colors: List<Color>,
    coverFraction: Float,
    modifier: Modifier = Modifier,
    cornerFraction: Float = 0.11f,
) {
    val measurer = rememberTextMeasurer()
    val notes = remember { listOf("♪", "♫", "♬", "♩") }

    Canvas(modifier) {
        val tick = pulse.frame // leerlo hace que se redibuje en cada cuadro
        if (tick < 0) return@Canvas
        val time = pulse.time
        val energy = pulse.energy
        val c = Offset(size.width / 2f, size.height / 2f)
        val cover = size.minDimension * coverFraction
        val room = (size.minDimension - cover) / 2f

        when (mode) {
            Prefs.VIZ_WAVES -> {
                val level = pulse.level
                val bass = pulse.bass
                // Resplandor que respira con el volumen
                if (energy > 0.01f) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(colors.at(0.6f).copy(alpha = (0.14f + 0.36f * level) * energy), Color.Transparent),
                            center = c,
                            radius = cover * 0.5f + room * (0.7f + 0.3f * bass),
                        ),
                    )
                    // Borde pegado a la carátula: más grueso y brillante cuando la canción sube
                    val side = cover + room * 0.10f * (0.4f + bass)
                    drawRoundRect(
                        color = colors.at(0f).copy(alpha = (0.25f + 0.55f * level) * energy),
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
                    val side = cover + room * eased * 2f
                    val alpha = (1f - phase) * (1f - phase) * (0.35f + 0.55f * w.strength) * energy
                    if (alpha < 0.01f) return@forEach
                    val col = colors[w.color % colors.size]
                    drawRoundRect(
                        color = col.copy(alpha = alpha.coerceAtMost(1f)),
                        topLeft = Offset(c.x - side / 2f, c.y - side / 2f),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(side * cornerFraction),
                        style = Stroke(width = (1.2f + 3.2f * w.strength * (1f - phase)) * density),
                    )
                }
            }

            Prefs.VIZ_NOTES -> {
                // Notas que aparecen y desaparecen como burbujas, a su propio paso
                if (energy > 0.05f) for (n in 0 until 10) {
                    val life = ((time * 0.16f + n * 0.1f) % 1f)
                    val sideSign = if (n % 2 == 0) -1f else 1f
                    val spread = cover / 2f + room * (0.20f + 0.65f * (((n * 37) % 10) / 10f))
                    val x = c.x + sideSign * spread + sin(time * 1.2f + n) * 10f * density
                    val y = c.y + cover * 0.45f - life * cover * 1.1f
                    val alpha = (sin(life * PI.toFloat()) * 0.9f * energy).coerceIn(0f, 1f)
                    if (x < 0f || y < 0f || x > size.width - 40f || y > size.height - 40f) continue
                    val col = colors[n % colors.size]
                    drawText(
                        textMeasurer = measurer,
                        text = notes[n % notes.size],
                        topLeft = Offset(x - 10f, y - 14f),
                        style = TextStyle(color = col.copy(alpha = alpha), fontSize = (15 + (n % 3) * 5).sp),
                    )
                }
            }

            Prefs.VIZ_BARS -> drawBarsBelow(pulse, colors, c, cover, room)

            else -> Unit
        }
    }
}

/** Ecualizador de barras por bloques, pequeño, en el espacio libre debajo de la carátula. */
private fun DrawScope.drawBarsBelow(pulse: AudioPulse, colors: List<Color>, c: Offset, cover: Float, room: Float) {
    val count = 26
    val top = c.y + cover / 2f + room * 0.18f
    val bottom = c.y + cover / 2f + room * 0.92f
    val areaH = bottom - top
    if (areaH < 8f) return
    val areaW = cover * 0.92f
    val left = c.x - areaW / 2f
    val slot = areaW / count
    val barW = slot * 0.62f
    val segH = 2.6f * density
    val segGap = 1.6f * density
    val segments = max(3, (areaH / (segH + segGap)).toInt())
    val energy = pulse.energy
    for (i in 0 until count) {
        // Graves a la izquierda, agudos a la derecha (se saltan las bandas más altas, casi vacías)
        val band = (i * (AudioLevels.BANDS - 4) / count).coerceIn(0, AudioLevels.BANDS - 1)
        val v = pulse.bands[band] * energy
        val lit = max(1, (v * segments).toInt())
        val col = colors.at(i / (count - 1f))
        val outline = contrastFor(col)
        val x = left + i * slot + (slot - barW) / 2f
        for (s in 0 until lit) {
            val y = bottom - (s + 1) * (segH + segGap) + segGap
            val a = if (s == lit - 1 && lit > 1) 0.75f else 0.95f
            drawRoundRect(
                color = outline,
                topLeft = Offset(x - 0.6f * density, y - 0.6f * density),
                size = Size(barW + 1.2f * density, segH + 1.2f * density),
                cornerRadius = CornerRadius(segH),
            )
            drawRoundRect(
                color = col.copy(alpha = a * (0.55f + 0.45f * energy)),
                topLeft = Offset(x, y),
                size = Size(barW, segH),
                cornerRadius = CornerRadius(segH / 2f),
            )
        }
    }
}

/**
 * Espectro sobre la carátula: barras redondeadas que crecen hacia arriba y hacia abajo desde
 * el centro, con los graves en el medio. Llevan un borde de contraste para verse sobre
 * cualquier carátula sin perderse entre sus colores.
 */
@Composable
fun SpectrumOverCover(pulse: AudioPulse, colors: List<Color>, coverFraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val tick = pulse.frame
        if (tick < 0) return@Canvas
        val energy = pulse.energy
        val c = Offset(size.width / 2f, size.height / 2f)
        val cover = size.minDimension * coverFraction
        val count = 31 // impar: una barra en el centro
        val mid = count / 2
        val areaW = cover * 0.86f
        val slot = areaW / count
        val barW = slot * 0.55f
        val maxHalf = cover * 0.22f
        val left = c.x - areaW / 2f
        // Velo muy suave detrás, solo para separar las barras de la imagen
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f * energy), Color.Transparent),
                startY = c.y - maxHalf * 1.4f,
                endY = c.y + maxHalf * 1.4f,
            ),
            topLeft = Offset(c.x - cover / 2f, c.y - maxHalf * 1.4f),
            size = Size(cover, maxHalf * 2.8f),
        )
        for (i in 0 until count) {
            val d = abs(i - mid)
            val band = (d * (AudioLevels.BANDS - 6) / mid).coerceIn(0, AudioLevels.BANDS - 1)
            val v = pulse.bands[band] * energy
            val half = max(barW / 2f, v * maxHalf)
            val x = left + i * slot + (slot - barW) / 2f
            val col = colors.at(d / mid.toFloat())
            val outline = contrastFor(col)
            val pad = 1.3f * density
            drawRoundRect(
                color = outline,
                topLeft = Offset(x - pad, c.y - half - pad),
                size = Size(barW + pad * 2, half * 2 + pad * 2),
                cornerRadius = CornerRadius((barW + pad * 2) / 2f),
            )
            drawRoundRect(
                color = col.copy(alpha = 0.55f + 0.45f * energy),
                topLeft = Offset(x, c.y - half),
                size = Size(barW, half * 2),
                cornerRadius = CornerRadius(barW / 2f),
            )
        }
    }
}

// ---------------- Menú para elegir estilo y color ----------------

@Composable
fun VisualizerMenu(mode: Int, color: Int, coverColors: List<Color>?, onMode: (Int) -> Unit, onColor: (Int) -> Unit) {
    Column(Modifier.width(270.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text("Animación", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        listOf(
            Prefs.VIZ_WAVES to "Ondas que salen de la carátula",
            Prefs.VIZ_NOTES to "Notas musicales",
            Prefs.VIZ_SPECTRUM to "Espectro sobre la carátula",
            Prefs.VIZ_BARS to "Barras debajo de la carátula",
        ).forEach { (m, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onMode(m) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (m == mode) Icon(Icons.Rounded.Check, null, tint = Coral, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, color = if (m == mode) Color.White else Color.White.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(
                Prefs.VIZ_COLOR_APP to AppColors,
                Prefs.VIZ_COLOR_COVER to (coverColors ?: listOf(Color(0xFF777777), Color(0xFFAAAAAA))),
                Prefs.VIZ_COLOR_WHITE to listOf(Color.White),
                Prefs.VIZ_COLOR_BLACK to listOf(VizBlack),
                Prefs.VIZ_COLOR_GRAY to listOf(VizGray),
            ).forEach { (opt, swatch) ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (swatch.size > 1) Brush.linearGradient(swatch) else Brush.linearGradient(listOf(swatch[0], swatch[0])))
                        .border(if (opt == color) 2.5.dp else 1.dp, if (opt == color) Coral else Color.White.copy(alpha = 0.35f), CircleShape)
                        .clickable { onColor(opt) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            when (color) {
                Prefs.VIZ_COLOR_COVER -> "Colores de la carátula"
                Prefs.VIZ_COLOR_WHITE -> "Blanco"
                Prefs.VIZ_COLOR_BLACK -> "Negro"
                Prefs.VIZ_COLOR_GRAY -> "Gris claro"
                else -> "Colores de Orba"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
