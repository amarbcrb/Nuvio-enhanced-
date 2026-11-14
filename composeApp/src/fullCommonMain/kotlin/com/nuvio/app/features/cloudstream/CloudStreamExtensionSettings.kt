package com.nuvio.app.features.cloudstream

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What happened when the settings window of an extension was asked for. */
enum class CloudStreamSettingsOpenResult { Opened, NoSettings, NotLoaded, Failed }

/**
 * The extensions' own settings windows (the settings button of CloudStream, e.g. the playlists of
 * M3U Player). The Android runtime installs [opener]; elsewhere it stays null and no button shows.
 */
object CloudStreamExtensionSettings {
    var opener: (suspend (CloudStreamPluginItem) -> CloudStreamSettingsOpenResult)? = null

    private val _withoutSettings = MutableStateFlow<Set<String>>(emptySet())

    /** Extensions found to have no settings window, whose button is hidden for the session. */
    val withoutSettings: StateFlow<Set<String>> = _withoutSettings.asStateFlow()

    fun markWithoutSettings(pluginId: String) {
        _withoutSettings.value = _withoutSettings.value + pluginId
    }
}
