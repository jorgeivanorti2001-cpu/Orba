package com.orba.app.playback

/**
 * Atajo directo (mismo proceso) desde la interfaz al servicio para "anterior / siguiente".
 * Así los botones funcionan siempre, sin depender de lo que el reproductor anuncie.
 */
object PlaybackControl {
    @Volatile
    var onNext: (() -> Unit)? = null

    @Volatile
    var onPrevious: (() -> Unit)? = null

    /** Cambia el refuerzo de volumen (en dB) si ese video o canción es el que suena. */
    @Volatile
    var onBoost: ((Long, Int) -> Unit)? = null

    fun next() { onNext?.invoke() }
    fun previous() { onPrevious?.invoke() }
    fun boost(id: Long, db: Int) { onBoost?.invoke(id, db) }
}
