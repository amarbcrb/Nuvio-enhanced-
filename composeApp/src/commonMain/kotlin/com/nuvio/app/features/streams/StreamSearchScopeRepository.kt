package com.nuvio.app.features.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A stream source that can be searched on its own (addon, plugin provider, CloudStream provider or server). */
data class StreamSourceOption(
    val id: String,
    val name: String,
)

/**
 * Remembers the single source the user wants stream searches limited to. `null` means every source is
 * searched. When the chosen source cannot serve a title, every source is searched for that title instead,
 * so playback never dead-ends on an incompatible choice.
 */
object StreamSearchScopeRepository {
    private const val SEPARATOR = "\n"

    private val _scope = MutableStateFlow<StreamSourceOption?>(null)
    val scope: StateFlow<StreamSourceOption?> = _scope.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun setScope(option: StreamSourceOption?) {
        ensureLoaded()
        if (option == _scope.value) return
        _scope.value = option
        StreamBadgeSettingsStorage.saveStreamSearchScope(
            option?.let { "${it.id}$SEPARATOR${it.name}" }.orEmpty(),
        )
    }

    /** Id of the source to search alone among [available], or null to search all of them. */
    fun resolveScopeId(available: List<StreamSourceOption>): String? {
        ensureLoaded()
        val scopeId = _scope.value?.id ?: return null
        return scopeId.takeIf { id -> available.any { it.id == id } }
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val stored = StreamBadgeSettingsStorage.loadStreamSearchScope()?.takeIf { it.isNotBlank() }
        _scope.value = stored?.let { raw ->
            val id = raw.substringBefore(SEPARATOR).trim()
            val name = raw.substringAfter(SEPARATOR, missingDelimiterValue = id).trim()
            id.takeIf { it.isNotEmpty() }?.let { StreamSourceOption(id = it, name = name.ifEmpty { it }) }
        }
    }
}

internal fun buildStreamSourceOptions(
    streamAddons: List<InstalledStreamAddonTarget>,
    pluginProviderGroups: List<PluginProviderGroup>,
    cloudStreamProviderGroups: List<CloudStreamProviderGroup>,
    serverGroups: List<AddonStreamGroup>,
): List<StreamSourceOption> =
    (
        streamAddons.map { StreamSourceOption(it.addonId, it.addonName) } +
            pluginProviderGroups.map { StreamSourceOption(it.addonId, it.addonName) } +
            cloudStreamProviderGroups.map { StreamSourceOption(it.addonId, it.addonName) } +
            serverGroups.map { StreamSourceOption(it.addonId, it.addonName) }
        ).distinctBy { it.id }
