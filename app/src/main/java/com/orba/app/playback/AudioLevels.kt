package com.orba.app.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lo que está sonando, convertido en números por cada instante: volumen general, graves
 * y un espectro de [BANDS] bandas (de graves a agudos). Lo alimenta el reproductor (mismo
 * proceso, sin micrófono ni permisos) y lo leen las animaciones de la pantalla de la canción.
 */
object AudioLevels {
    /** Cantidad de bandas del espectro (graves a la izquierda, agudos a la derecha). */
    const val BANDS = 32

    /** El audio entra aquí un poco antes de sonar (búfer del sistema): se compensa con este retraso. */
    private const val LATENCY_NS = 260_000_000L
    private const val SIZE = 256
    private const val FFT = 1024

    private val times = LongArray(SIZE)
    private val levels = FloatArray(SIZE)
    private val basses = FloatArray(SIZE)
    private val spectra = Array(SIZE) { FloatArray(BANDS) }
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

    // Espectro: últimas FFT muestras (mono), ventana de Hann y límites de cada banda
    private val ring = FloatArray(FFT)
    private var ringPos = 0
    private val re = FloatArray(FFT)
    private val im = FloatArray(FFT)
    private val hann = FloatArray(FFT) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FFT - 1))).toFloat() }
    private var bandEdges = IntArray(BANDS + 1)
    private var bandTilt = FloatArray(BANDS)
    private val bandsOut = FloatArray(BANDS)

    init { computeBands() }

    private fun computeBands() {
        // Bandas en escala logarítmica de 40 Hz a 14 kHz (como se oye)
        val lo = 40.0
        val hi = min(14_000.0, sampleRate / 2.0 - 100)
        val binHz = sampleRate.toDouble() / FFT
        val edges = IntArray(BANDS + 1)
        for (b in 0..BANDS) {
            val f = lo * (hi / lo).pow(b.toDouble() / BANDS)
            edges[b] = (f / binHz).toInt().coerceIn(1, FFT / 2 - 1)
        }
        for (b in 1..BANDS) if (edges[b] <= edges[b - 1]) edges[b] = min(FFT / 2, edges[b - 1] + 1)
        val tilt = FloatArray(BANDS) { b ->
            // La música tiene menos energía en los agudos: se compensa (+3 dB por octava)
            val fc = lo * (hi / lo).pow((b + 0.5) / BANDS)
            (3.0 * ln(fc / 150.0) / ln(2.0)).toFloat().coerceIn(-3f, 18f)
        }
        bandEdges = edges
        bandTilt = tilt
    }

    /** Llega un formato nuevo (cambio de canción o salto en la canción). */
    fun onFlush(sampleRateHz: Int, channelCount: Int, isFloat: Boolean, isPcm16: Boolean) {
        sampleRate = sampleRateHz.coerceAtLeast(8_000)
        channels = channelCount.coerceAtLeast(1)
        floatPcm = isFloat
        supported = isFloat || isPcm16
        alpha = (1.0 - exp(-2.0 * PI * 140.0 / sampleRate)).toFloat()
        window = sampleRate / 60 // unos 16 ms, un cuadro de pantalla
        lowPass = 0f; sumSq = 0.0; bassSq = 0.0; inWindow = 0
        nextTimeNs = 0L
        ring.fill(0f); ringPos = 0
        computeBands()
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
            ring[ringPos] = s
            ringPos = (ringPos + 1) % FFT
            lowPass += alpha * (s - lowPass)
            sumSq += (s * s).toDouble()
            bassSq += (lowPass * lowPass).toDouble()
            if (++inWindow >= window) {
                val level = sqrt(sumSq / inWindow).toFloat()
                val bass = sqrt(bassSq / inWindow).toFloat()
                spectrum()
                push(nextTimeNs, level, bass, bandsOut)
                nextTimeNs += windowNs
                sumSq = 0.0; bassSq = 0.0; inWindow = 0
            }
        }
    }

    /** FFT de las últimas [FFT] muestras → [bandsOut] (0 a 1). */
    private fun spectrum() {
        for (i in 0 until FFT) {
            re[i] = ring[(ringPos + i) % FFT] * hann[i]
            im[i] = 0f
        }
        fft(re, im)
        val norm = FFT / 4f
        for (band in 0 until BANDS) {
            val a = bandEdges[band]
            val z = max(a + 1, bandEdges[band + 1])
            var peak = 0f
            for (k in a until z) {
                val m = sqrt(re[k] * re[k] + im[k] * im[k]) / norm
                if (m > peak) peak = m
            }
            val db = 20f * log10(max(peak, 1e-6f)) + bandTilt[band]
            // -58 dB → 0, -8 dB → 1
            bandsOut[band] = ((db + 58f) / 50f).coerceIn(0f, 1f)
        }
    }

    /** FFT radix 2 en el lugar. */
    private fun fft(x: FloatArray, y: FloatArray) {
        val n = x.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tx = x[i]; x[i] = x[j]; x[j] = tx
                val ty = y[i]; y[i] = y[j]; y[j] = ty
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val ur = x[i + k]; val ui = y[i + k]
                    val vr = x[i + k + len / 2] * cr - y[i + k + len / 2] * ci
                    val vi = x[i + k + len / 2] * ci + y[i + k + len / 2] * cr
                    x[i + k] = ur + vr; y[i + k] = ui + vi
                    x[i + k + len / 2] = ur - vr; y[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun push(t: Long, level: Float, bass: Float, bands: FloatArray) {
        synchronized(lock) {
            times[head] = t; levels[head] = level; basses[head] = bass
            System.arraycopy(bands, 0, spectra[head], 0, BANDS)
            head = (head + 1) % SIZE
            if (count < SIZE) count++
        }
    }

    private fun current(nowNs: Long): Int {
        if (count == 0) return -1
        for (k in 0 until count) {
            val i = (head - 1 - k + SIZE) % SIZE
            if (times[i] <= nowNs) return if (nowNs - times[i] > 250_000_000L) -1 else i
        }
        return -1
    }

    /**
     * Volumen y graves que se están oyendo ahora mismo (0 a 1 aprox.), o null si no llega
     * audio (pausa, o una canción que el sistema reproduce por otra vía).
     */
    fun now(nowNs: Long = System.nanoTime()): FloatArray? {
        synchronized(lock) {
            val i = current(nowNs)
            if (i < 0) return null
            return floatArrayOf(levels[i], basses[i])
        }
    }

    /** Copia en [out] el espectro que se está oyendo ahora. Devuelve false si no llega audio. */
    fun spectrumNow(out: FloatArray, nowNs: Long = System.nanoTime()): Boolean {
        synchronized(lock) {
            val i = current(nowNs)
            if (i < 0) return false
            System.arraycopy(spectra[i], 0, out, 0, min(out.size, BANDS))
            return true
        }
    }
}
