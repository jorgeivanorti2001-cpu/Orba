package com.orba.app.playback

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Canal simple entre el servicio de reproducción y la interfaz (mismo proceso). */
object PlaybackEvents {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    fun message(text: String) { _messages.tryEmit(text) }
}
