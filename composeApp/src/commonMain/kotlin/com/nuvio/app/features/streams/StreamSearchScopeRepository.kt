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
    private const val FAVORITES_ONLY_KEY = "stream_favorites_only_v1"

    private val _scope = MutableStateFlow<StreamSourceOption?>(null)
    val scope: StateFlow<StreamSourceOption?> = _scope.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)

    /** Only the favorite (starred) sources are searched, when one of them can serve the title. */
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

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

    fun setFavoritesOnly(enabled: Boolean) {
        ensureLoaded()
        if (enabled == _favoritesOnly.value) return
        _favoritesOnly.value = enabled
        BingeGroupCacheStorage.save(FAVORITES_ONLY_KEY, enabled.toString())
    }

    /** Part of the stream request key: changes when the favorites-only search or the favorites change. */
    fun favoritesSignature(): String {
        ensureLoaded()
        if (!_favoritesOnly.value) return "all"
        PinnedStreamSourcesRepository.ensureLoaded()
        return "fav:" + PinnedStreamSourcesRepository.pinnedSourceIds.value.sorted().joinToString(",")
    }

    /**
     * Which sources (by id) a stream search covers among [available]: the single chosen source, else the
     * favorites when only favorites are searched and one of them can serve the title, else every source.
     */
    fun searchFilter(available: List<StreamSourceOption>): (String) -> Boolean {
        val scopeId = resolveScopeId(available)
        if (scopeId != null) return { id -> id == scopeId }
        if (_favoritesOnly.value && available.any { PinnedStreamSourcesRepository.isFavoriteGroup(it.id) }) {
            return { id -> PinnedStreamSourcesRepository.isFavoriteGroup(id) }
        }
        return { true }
    }

    /** Id of the source to search alone among [available], or null to search all of them. */
    fun resolveScopeId(available: List<StreamSourceOption>): String? {
        ensureLoaded()
        val scopeId = _scope.value?.id ?: return null
        return scopeId.takeIf { id -> available.any { it.id == id } }
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _favoritesOnly.value = BingeGroupCacheStorage.load(FAVORITES_ONLY_KEY) == "true"
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
