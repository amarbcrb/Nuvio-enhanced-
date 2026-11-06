package com.nuvio.app.features.home

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.cloudstream.CloudStreamPluginItem
import com.nuvio.app.features.streams.StreamBadgeSettingsStorage
import com.nuvio.app.features.streams.cloudStreamAddonId
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.streams.BingeGroupCacheStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock

/** An addon or CloudStream extension whose catalogs can fill the Home tab. */
data class CatalogSourceOption(
    val id: String,
    val name: String,
)

internal fun addonCatalogSourceId(manifestUrl: String): String = "addon:$manifestUrl"

/**
 * Remembers the sources the Home tab shows catalogs from. An empty choice shows every
 * source. Search and stream searches are not affected. Sources that are no longer installed are ignored, and
 * when none of the chosen sources is left every source is shown again.
 */
object CatalogSourceRepository {
    private const val ENTRY_SEPARATOR = "\n"
    private const val FIELD_SEPARATOR = "\u001F"

    private val _selected = MutableStateFlow<List<CatalogSourceOption>>(emptyList())
    val selected: StateFlow<List<CatalogSourceOption>> = _selected.asStateFlow()

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

    fun setSelected(options: List<CatalogSourceOption>) {
        ensureLoaded()
        val distinct = options.distinctBy { it.id }
        if (distinct == _selected.value) return
        _selected.value = distinct
        StreamBadgeSettingsStorage.saveCatalogSource(
            distinct.joinToString(ENTRY_SEPARATOR) { "${it.id}$FIELD_SEPARATOR${it.name}" },
        )
    }

    /** Adds or removes [option] from the chosen sources. */
    fun toggle(option: CatalogSourceOption) {
        ensureLoaded()
        val current = _selected.value
        setSelected(
            if (current.any { it.id == option.id }) current.filterNot { it.id == option.id } else current + option,
        )
    }

    fun showAll() = setSelected(emptyList())

    /** Records the sources that currently offer catalogs, for the source choice. */
    internal fun updateAvailable(options: List<CatalogSourceOption>) {
        if (options.isNotEmpty()) _available.value = options
        SourceAddedOrder.record(options.map { it.id })
    }

    /** Ids of the sources to show among [options], or null to show all of them. */
    internal fun resolveSelectedIds(options: List<CatalogSourceOption>): Set<String>? {
        ensureLoaded()
        val availableIds = options.map { it.id }.toSet()
        return _selected.value.map { it.id }.filter { it in availableIds }.toSet().takeIf { it.isNotEmpty() }
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val stored = StreamBadgeSettingsStorage.loadCatalogSource()?.takeIf { it.isNotBlank() }
        _selected.value = stored?.let(::decode).orEmpty()
    }

    private fun decode(raw: String): List<CatalogSourceOption> {
        // Older versions kept a single source as "id\nname".
        if (FIELD_SEPARATOR !in raw) {
            val id = raw.substringBefore(ENTRY_SEPARATOR).trim()
            val name = raw.substringAfter(ENTRY_SEPARATOR, missingDelimiterValue = id).trim()
            return listOfNotNull(id.takeIf { it.isNotEmpty() }?.let { CatalogSourceOption(it, name.ifEmpty { it }) })
        }
        return raw.split(ENTRY_SEPARATOR).mapNotNull { entry ->
            val id = entry.substringBefore(FIELD_SEPARATOR).trim()
            val name = entry.substringAfter(FIELD_SEPARATOR, missingDelimiterValue = id).trim()
            id.takeIf { it.isNotEmpty() }?.let { CatalogSourceOption(it, name.ifEmpty { it }) }
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

/**
 * When each source (addon or CloudStream extension) was first seen, for the « last added » order of
 * the source pickers. Sources already installed when this started are dated in their list order.
 */
internal object SourceAddedOrder {
    private const val STORAGE_KEY = "catalog_source_first_seen_v1"
    private const val FIELD_SEPARATOR = "\u001F"
    private const val ENTRY_SEPARATOR = "\n"

    private var loadedProfileId: Int? = null
    private var firstSeen: Map<String, Long> = emptyMap()

    private fun ensureLoaded() {
        val profileId = ProfileRepository.activeProfileId
        if (loadedProfileId == profileId) return
        loadedProfileId = profileId
        firstSeen = BingeGroupCacheStorage.load(STORAGE_KEY).orEmpty()
            .split(ENTRY_SEPARATOR)
            .mapNotNull { entry ->
                val id = entry.substringBefore(FIELD_SEPARATOR).takeIf(String::isNotBlank) ?: return@mapNotNull null
                val time = entry.substringAfter(FIELD_SEPARATOR, "").toLongOrNull() ?: return@mapNotNull null
                id to time
            }
            .toMap()
    }

    /** Dates the [ids] not seen before; the later in the list, the more recent. */
    fun record(ids: List<String>) {
        ensureLoaded()
        val unseen = ids.distinct().filterNot { it in firstSeen }
        if (unseen.isEmpty()) return
        val now = Clock.System.now().toEpochMilliseconds()
        firstSeen = firstSeen + unseen.mapIndexed { index, id -> id to now + index }
        BingeGroupCacheStorage.save(
            STORAGE_KEY,
            firstSeen.entries.joinToString(ENTRY_SEPARATOR) { (id, time) -> "$id$FIELD_SEPARATOR$time" },
        )
    }

    /** When [id] was first seen, or null if never. */
    fun firstSeen(id: String): Long? {
        ensureLoaded()
        return firstSeen[id]
    }
}
