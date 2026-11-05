package com.nuvio.app.features.search

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.cloudstream.CloudStreamPluginItem
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.BingeGroupCacheStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the Search screen shows, chosen in Settings like the Home screen catalogs:
 * the Discover block when the search bar is empty, and which search sources (addon catalogs,
 * CloudStream extensions) give result rows, in which order.
 */
@Serializable
internal data class SearchScreenSettings(
    val showDiscover: Boolean = true,
    val hiddenSources: Set<String> = emptySet(),
    val sourceOrder: List<String> = emptyList(),
)

/** One row of search results that can be shown, hidden or moved. */
internal data class SearchSourceOption(
    val key: String,
    val title: String,
    val subtitle: String,
)

internal object SearchScreenSettingsRepository {
    private const val STORAGE_KEY = "search_screen_settings_v1"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(SearchScreenSettings())
    val state: StateFlow<SearchScreenSettings> = _state.asStateFlow()

    private var loadedProfileId: Int? = null

    fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        _state.value = BingeGroupCacheStorage.load(STORAGE_KEY)?.let(::decode) ?: SearchScreenSettings()
    }

    fun snapshot(): SearchScreenSettings {
        ensureLoaded()
        return _state.value
    }

    fun setShowDiscover(show: Boolean) = update { it.copy(showDiscover = show) }

    fun setSourceVisible(key: String, visible: Boolean) = update { current ->
        current.copy(hiddenSources = if (visible) current.hiddenSources - key else current.hiddenSources + key)
    }

    /** Moves [key] by [offset] places among [availableKeys], shown in the user's order. */
    fun moveSource(availableKeys: List<String>, key: String, offset: Int) = update { current ->
        val ordered = availableKeys.orderedBy(current.sourceOrder).toMutableList()
        val from = ordered.indexOf(key)
        val to = from + offset
        if (from < 0 || to !in ordered.indices) return@update current
        ordered.add(to, ordered.removeAt(from))
        // Keep the place of sources that are not installed right now (an addon turned off for a while).
        val missing = current.sourceOrder.filterNot { it in ordered }
        current.copy(sourceOrder = ordered + missing)
    }

    fun reset() = update { SearchScreenSettings() }

    fun exportPayload(): String {
        ensureLoaded()
        return json.encodeToString(SearchScreenSettings.serializer(), _state.value)
    }

    fun importPayload(payload: String) {
        val decoded = decode(payload) ?: error("Invalid search screen settings")
        update { decoded }
    }

    private fun decode(raw: String): SearchScreenSettings? =
        runCatching { json.decodeFromString(SearchScreenSettings.serializer(), raw) }.getOrNull()

    private fun update(transform: (SearchScreenSettings) -> SearchScreenSettings) {
        ensureLoaded()
        val updated = transform(_state.value)
        if (updated == _state.value) return
        _state.value = updated
        BingeGroupCacheStorage.save(STORAGE_KEY, json.encodeToString(SearchScreenSettings.serializer(), updated))
    }
}

/** Sorts [this] by [order]; entries missing from [order] keep their relative place, after the others. */
internal fun List<String>.orderedBy(order: List<String>): List<String> {
    if (order.isEmpty()) return this
    val rank = order.withIndex().associate { (index, key) -> key to index }
    return withIndex()
        .sortedWith(compareBy<IndexedValue<String>>({ rank[it.value] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
}

/** Key of an addon search catalog in [SearchScreenSettings]. */
internal fun addonSearchSourceKey(manifestUrl: String, type: String, catalogId: String): String =
    "addon|$manifestUrl|$type|$catalogId"

/** Key of a CloudStream extension in [SearchScreenSettings]. */
internal fun CloudStreamPluginItem.searchSourceKey(): String = "cloudstream|${metadata.id.value}"

/** Every search source of the installed addons and runnable CloudStream extensions, in the user's order. */
internal fun searchSourceOptions(
    addons: List<ManagedAddon>,
    cloudPlugins: List<CloudStreamPluginItem>,
    settings: SearchScreenSettings,
): List<SearchSourceOption> {
    val addonOptions = addons.enabledAddons().flatMap { addon ->
        val manifest = addon.manifest ?: return@flatMap emptyList()
        manifest.catalogs
            .filter { catalog -> catalog.supportsSearch() }
            .map { catalog ->
                SearchSourceOption(
                    key = addonSearchSourceKey(addon.manifestUrl, catalog.type, catalog.id),
                    title = "${catalog.name} · ${catalog.type.replaceFirstChar { it.uppercase() }}",
                    subtitle = addon.displayTitle,
                )
            }
    }
    val cloudOptions = cloudPlugins.filter(CloudStreamPluginItem::isRunnable).map { plugin ->
        SearchSourceOption(
            key = plugin.searchSourceKey(),
            title = plugin.metadata.name,
            subtitle = "CloudStream",
        )
    }
    val options = (addonOptions + cloudOptions).distinctBy { it.key }
    val byKey = options.associateBy { it.key }
    return options.map { it.key }.orderedBy(settings.sourceOrder).mapNotNull(byKey::get)
}
