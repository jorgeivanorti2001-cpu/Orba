package com.orba.app.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Lo que está sonando, convertido en dos números por cada instante: volumen general y graves.
 * Lo alimenta el reproductor (mismo proceso, sin micrófono ni permisos) y lo lee la onda
 * alrededor de la carátula, para que se mueva al ritmo de la canción.
 */
object AudioLevels {
    /** El audio entra aquí un poco antes de sonar (búfer del sistema): se compensa con este retraso. */
    private const val LATENCY_NS = 260_000_000L
    private const val SIZE = 512

    private val times = LongArray(SIZE)
    private val levels = FloatArray(SIZE)
    private val basses = FloatArray(SIZE)
    private var head = 0
    private var count = 0
    private val lock = Any()

    // Formato actual
    @Volatile private var sampleRate = 44_100
    @Volatile private var channels = 2
    @Volatile private var floatPcm = false
    @Volatile private var supported = true

    // Estado del análisis (solo lo toca el hilo de audio)
    private var lowPass = 0f
    private var alpha = 0.02f
    private var sumSq = 0.0
    private var bassSq = 0.0
    private var inWindow = 0
    private var window = 735
    private var nextTimeNs = 0L

    /** Llega un formato nuevo (cambio de canción o salto en la canción). */
    fun onFlush(sampleRateHz: Int, channelCount: Int, isFloat: Boolean, isPcm16: Boolean) {
        sampleRate = sampleRateHz.coerceAtLeast(8_000)
        channels = channelCount.coerceAtLeast(1)
        floatPcm = isFloat
        supported = isFloat || isPcm16
        alpha = (1.0 - exp(-2.0 * Math.PI * 140.0 / sampleRate)).toFloat()
        window = sampleRate / 60 // unos 16 ms, un cuadro de pantalla
        lowPass = 0f; sumSq = 0.0; bassSq = 0.0; inWindow = 0
        nextTimeNs = 0L
        synchronized(lock) { count = 0 }
    }

    fun onBuffer(buffer: ByteBuffer) {
        if (!supported) return
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        val bytesPerSample = if (floatPcm) 4 else 2
        val ch = channels
        val frames = b.remaining() / (bytesPerSample * ch)
        var pos = b.position()
        val now = System.nanoTime()
        if (nextTimeNs < now + LATENCY_NS - 50_000_000L || nextTimeNs > now + 1_500_000_000L) nextTimeNs = now + LATENCY_NS
        val windowNs = 1_000_000_000L * window / sampleRate
        for (f in 0 until frames) {
            var s = 0f
            for (c in 0 until ch) {
                s += if (floatPcm) b.getFloat(pos) else b.getShort(pos) / 32768f
                pos += bytesPerSample
            }
            s /= ch
            lowPass += alpha * (s - lowPass)
            sumSq += (s * s).toDouble()
            bassSq += (lowPass * lowPass).toDouble()
            if (++inWindow >= window) {
                val level = sqrt(sumSq / inWindow).toFloat()
                val bass = sqrt(bassSq / inWindow).toFloat()
                push(nextTimeNs, level, bass)
                nextTimeNs += windowNs
                sumSq = 0.0; bassSq = 0.0; inWindow = 0
            }
        }
    }

    private fun push(t: Long, level: Float, bass: Float) {
        synchronized(lock) {
            times[head] = t; levels[head] = level; basses[head] = bass
            head = (head + 1) % SIZE
            if (count < SIZE) count++
        }
    }

    /**
     * Volumen y graves que se están oyendo ahora mismo (0 a 1 aprox.), o null si no llega
     * audio (pausa, o una canción que el sistema reproduce por otra vía).
     */
    fun now(nowNs: Long = System.nanoTime()): FloatArray? {
        synchronized(lock) {
            if (count == 0) return null
            var best = -1
            for (k in 0 until count) {
                val i = (head - 1 - k + SIZE) % SIZE
                if (times[i] <= nowNs) { best = i; break }
            }
            if (best < 0) return null
            if (nowNs - times[best] > 250_000_000L) return null
            return floatArrayOf(levels[best], basses[best])
        }
    }
}
