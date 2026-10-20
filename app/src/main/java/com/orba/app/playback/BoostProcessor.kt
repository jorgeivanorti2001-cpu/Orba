package com.orba.app.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/**
 * Sube el volumen de lo que suena (videos y música por igual), dentro del propio reproductor.
 * Tiene un limitador: cuando la canción ya viene fuerte, baja solo lo necesario para que
 * nunca se rompa el sonido. El cambio de volumen es gradual, sin chasquidos.
 */
@UnstableApi
class BoostProcessor : BaseAudioProcessor() {
    /** Refuerzo deseado, en decibeles (0 = normal). */
    @Volatile
    var targetDb: Int = 0

    private var gain = 1f
    private var envelope = 0f
    private var release = 0.0002f
    private var ramp = 0.0005f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        val sr = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        release = (1.0 - exp(-1.0 / (0.25 * sr))).toFloat() // vuelve en unos 250 ms
        ramp = (1.0 - exp(-1.0 / (0.08 * sr))).toFloat()    // cambio de volumen en unos 80 ms
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val target = 10f.pow(targetDb.coerceIn(0, 30) / 20f)
        if (target == 1f && abs(gain - 1f) < 0.001f) {
            gain = 1f
            out.put(inputBuffer)
            out.flip()
            return
        }
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val ch = inputAudioFormat.channelCount.coerceAtLeast(1)
        val bytesPerSample = if (isFloat) 4 else 2
        val frames = size / (bytesPerSample * ch)
        val samples = FloatArray(ch)
        val ceiling = 0.97f
        repeat(frames) {
            gain += (target - gain) * ramp
            var peak = 0f
            for (c in 0 until ch) {
                val s = if (isFloat) inputBuffer.float else inputBuffer.short / 32768f
                samples[c] = s
                val a = abs(s * gain)
                if (a > peak) peak = a
            }
            // Limitador: sube al instante con los picos y se relaja despacio
            envelope = if (peak > envelope) peak else envelope + (peak - envelope) * release
            val limit = if (envelope > ceiling) ceiling / envelope else 1f
            val g = gain * limit
            for (c in 0 until ch) {
                val y = (samples[c] * g).coerceIn(-1f, 1f)
                if (isFloat) out.putFloat(y) else out.putShort((y * 32767f).toInt().toShort())
            }
        }
        // Bytes sueltos que no completan un cuadro (no debería pasar): se copian tal cual
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    override fun onFlush() {
        envelope = 0f
    }
}
