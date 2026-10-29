package com.nuvio.app.features.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PinnedStreamSourcesRepository {
    private const val SEPARATOR = "\n"

    private val _pinnedSourceIds = MutableStateFlow<List<String>>(emptyList())
    val pinnedSourceIds: StateFlow<List<String>> = _pinnedSourceIds.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun sourceKeyFor(addonId: String, sourceName: String?): String {
        val source = sourceName?.trim()?.takeIf { it.isNotEmpty() }
        return if (source == null) addonId else "$addonId|$source"
    }

    fun sourceKeyFor(stream: StreamItem): String =
        sourceKeyFor(addonId = stream.addonId, sourceName = stream.sourceName)

    fun isPinned(sourceKey: String): Boolean = sourceKey in _pinnedSourceIds.value

    fun setPinned(sourceKey: String, pinned: Boolean) {
        val normalized = sourceKey.trim()
        if (normalized.isEmpty()) return
        ensureLoaded()
        val current = _pinnedSourceIds.value
        val updated = when {
            pinned && normalized in current -> return
            pinned -> current + normalized
            else -> current.filterNot { it == normalized }
        }
        if (updated == current) return
        _pinnedSourceIds.value = updated
        persist(updated)
    }

    fun toggle(sourceKey: String) {
        setPinned(sourceKey, !isPinned(sourceKey))
    }

    /** Whether the source group [addonId], or one of its sources, is a favorite. */
    fun isFavoriteGroup(addonId: String): Boolean {
        ensureLoaded()
        val prefix = "$addonId|"
        return _pinnedSourceIds.value.any { key -> key == addonId || key.startsWith(prefix) }
    }

    private fun loadFromDisk() {
        hasLoaded = true
        _pinnedSourceIds.value = StreamBadgeSettingsStorage.loadPinnedStreamSources()
            ?.split(SEPARATOR)
            ?.map(String::trim)
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            .orEmpty()
    }

    private fun persist(ids: List<String>) {
        StreamBadgeSettingsStorage.savePinnedStreamSources(ids.joinToString(SEPARATOR))
    }
}

/** Favorite sources first, the order being otherwise kept, so they are queried and picked first. */
internal fun <T> List<T>.favoriteSourcesFirst(addonIdOf: (T) -> String): List<T> =
    sortedBy { item -> if (PinnedStreamSourcesRepository.isFavoriteGroup(addonIdOf(item))) 0 else 1 }
