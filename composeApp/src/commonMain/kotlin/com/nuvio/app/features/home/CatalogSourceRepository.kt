package com.nuvio.app.features.home

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.cloudstream.CloudStreamPluginItem
import com.nuvio.app.features.streams.StreamBadgeSettingsStorage
import com.nuvio.app.features.streams.cloudStreamAddonId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An addon or CloudStream extension whose catalogs can fill the Home and Search tabs. */
data class CatalogSourceOption(
    val id: String,
    val name: String,
)

internal fun addonCatalogSourceId(manifestUrl: String): String = "addon:$manifestUrl"

/**
 * Remembers the single source the Home and Search tabs show catalogs from. `null` shows every
 * source. Stream searches are not affected. When the chosen source is no longer installed, every
 * source is shown again.
 */
object CatalogSourceRepository {
    private const val SEPARATOR = "\n"

    private val _selected = MutableStateFlow<CatalogSourceOption?>(null)
    val selected: StateFlow<CatalogSourceOption?> = _selected.asStateFlow()

    private val _available = MutableStateFlow<List<CatalogSourceOption>>(emptyList())
    val available: StateFlow<List<CatalogSourceOption>> = _available.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    fun select(option: CatalogSourceOption?) {
        ensureLoaded()
        if (option == _selected.value) return
        _selected.value = option
        StreamBadgeSettingsStorage.saveCatalogSource(option?.let { "${it.id}$SEPARATOR${it.name}" }.orEmpty())
    }

    /** Records the sources that currently offer catalogs, for the settings choice. */
    internal fun updateAvailable(options: List<CatalogSourceOption>) {
        if (options.isNotEmpty()) _available.value = options
    }

    /** Id of the source to show alone among [options], or null to show all of them. */
    internal fun resolveSelectedId(options: List<CatalogSourceOption>): String? {
        ensureLoaded()
        val id = _selected.value?.id ?: return null
        return id.takeIf { options.any { option -> option.id == id } }
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val stored = StreamBadgeSettingsStorage.loadCatalogSource()?.takeIf { it.isNotBlank() }
        _selected.value = stored?.let { raw ->
            val id = raw.substringBefore(SEPARATOR).trim()
            val name = raw.substringAfter(SEPARATOR, missingDelimiterValue = id).trim()
            id.takeIf { it.isNotEmpty() }?.let { CatalogSourceOption(id = it, name = name.ifEmpty { it }) }
        }
    }
}

/** Addons with catalogs, then CloudStream extensions, as catalog source choices. */
internal fun buildCatalogSourceOptions(
    addons: List<ManagedAddon>,
    cloudPlugins: List<CloudStreamPluginItem>,
): List<CatalogSourceOption> =
    (
        addons
            .filter { addon -> addon.manifest?.catalogs?.isNotEmpty() == true }
            .map { addon -> CatalogSourceOption(addonCatalogSourceId(addon.manifestUrl), addon.displayTitle) } +
            cloudPlugins.map { plugin ->
                CatalogSourceOption(
                    id = cloudStreamAddonId(plugin.metadata.id.value),
                    name = plugin.metadata.name.ifBlank { plugin.metadata.internalName },
                )
            }
        ).distinctBy { it.id }
