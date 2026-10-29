package com.nuvio.app.features.streams

/** The source a series was last played from. */
data class RememberedSeriesSource(
    val addonId: String,
    val sourceName: String?,
)

/**
 * Remembers, per series, the source its last episode played from, so the next episodes play
 * straight from it instead of opening the stream list each time.
 */
object SeriesSourceMemoryRepository {
    private const val SEPARATOR = "\u001F"

    fun save(seriesId: String, addonId: String?, sourceName: String?) {
        val id = seriesId.trim()
        val addon = addonId?.trim().orEmpty()
        if (id.isEmpty() || addon.isEmpty() || addon == "embedded") return
        val source = sourceName?.trim().orEmpty()
        BingeGroupCacheStorage.save(key(id), "$addon$SEPARATOR$source")
    }

    fun get(seriesId: String): RememberedSeriesSource? {
        val id = seriesId.trim().takeIf { it.isNotEmpty() } ?: return null
        val raw = BingeGroupCacheStorage.load(key(id))?.takeIf { it.isNotBlank() } ?: return null
        val addonId = raw.substringBefore(SEPARATOR).trim().takeIf { it.isNotEmpty() } ?: return null
        val sourceName = raw.substringAfter(SEPARATOR, missingDelimiterValue = "").trim().takeIf { it.isNotEmpty() }
        return RememberedSeriesSource(addonId = addonId, sourceName = sourceName)
    }

    fun forget(seriesId: String) {
        val id = seriesId.trim().takeIf { it.isNotEmpty() } ?: return
        BingeGroupCacheStorage.remove(key(id))
    }

    private fun key(seriesId: String): String {
        val hash = seriesId.fold(0L) { acc, c -> acc * 31 + c.code }.toULong()
        return "series_source_$hash"
    }
}

/**
 * The links to try from the remembered source among [streams] (in display order): the playable
 * links of the same source first, then the other playable links of the same addon or extension.
 * Empty when the source has none for this episode.
 */
internal fun RememberedSeriesSource.candidateStreams(
    streams: List<StreamItem>,
    debridEnabled: Boolean,
): List<StreamItem> {
    val fromAddon = streams.filter { stream ->
        stream.addonId == addonId && stream.isSelectableForPlayback(debridEnabled)
    }
    val name = sourceName ?: return fromAddon
    val (sameSource, others) = fromAddon.partition { stream ->
        stream.sourceName?.trim().equals(name, ignoreCase = true)
    }
    return sameSource + others
}
