package com.orba.app.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cola de canciones de la sección Escuchar. La interfaz la arma al tocar una canción
 * (con la lista visible en ese momento) y el servicio la usa para pasar a la siguiente,
 * también desde la notificación o con la pantalla apagada.
 */
object AudioQueue {
    private val _order = MutableStateFlow<List<Long>>(emptyList())
    val order: StateFlow<List<Long>> = _order.asStateFlow()

    /** Lista original (sin mezclar), para poder quitar el aleatorio sin perder el orden. */
    private var original: List<Long> = emptyList()
    private var isShuffled = false

    @Synchronized
    fun set(ids: List<Long>, startId: Long, shuffle: Boolean) {
        original = ids
        isShuffled = shuffle
        _order.value = arrange(ids, startId, shuffle)
    }

    /** Al activar o desactivar el aleatorio, se reordena dejando primero la canción actual. */
    @Synchronized
    fun reshuffle(currentId: Long?, shuffle: Boolean) {
        if (original.isEmpty() || shuffle == isShuffled) return
        isShuffled = shuffle
        val start = currentId?.takeIf { it in original } ?: original.first()
        _order.value = arrange(original, start, shuffle)
    }

    private fun arrange(ids: List<Long>, startId: Long, shuffle: Boolean): List<Long> =
        if (shuffle) listOf(startId) + ids.filter { it != startId }.shuffled() else ids

    fun contains(id: Long): Boolean = id in _order.value

    fun next(current: Long, wrap: Boolean): Long? {
        val list = _order.value
        val i = list.indexOf(current)
        if (i < 0) return null
        return when {
            i + 1 < list.size -> list[i + 1]
            wrap && list.isNotEmpty() -> list.first()
            else -> null
        }
    }

    fun previous(current: Long, wrap: Boolean): Long? {
        val list = _order.value
        val i = list.indexOf(current)
        if (i < 0) return null
        return when {
            i > 0 -> list[i - 1]
            wrap && list.isNotEmpty() -> list.last()
            else -> null
        }
    }
}
