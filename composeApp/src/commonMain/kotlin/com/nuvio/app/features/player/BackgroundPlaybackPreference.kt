package com.nuvio.app.features.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Keep playing when the screen is locked": the player is not paused when the app goes to the
 * background (screen off, phone locked); the playback notification keeps it running.
 */
object BackgroundPlaybackPreference {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var persist: ((Boolean) -> Unit)? = null

    fun initialize(stored: Boolean, persist: (Boolean) -> Unit) {
        this.persist = persist
        _enabled.value = stored
    }

    fun setEnabled(enabled: Boolean) {
        if (_enabled.value == enabled) return
        _enabled.value = enabled
        runCatching { persist?.invoke(enabled) }
    }
}

/**
 * "Play during a call": the player no longer takes the audio focus, so a phone call does not stop
 * or block playback. The film sound during the call is decided by Android (muted, low or mixed).
 */
object CallPlaybackPreference {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var persist: ((Boolean) -> Unit)? = null

    fun initialize(stored: Boolean, persist: (Boolean) -> Unit) {
        this.persist = persist
        _enabled.value = stored
    }

    fun setEnabled(enabled: Boolean) {
        if (_enabled.value == enabled) return
        _enabled.value = enabled
        runCatching { persist?.invoke(enabled) }
    }
}
