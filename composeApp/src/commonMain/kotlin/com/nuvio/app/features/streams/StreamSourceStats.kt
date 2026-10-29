package com.nuvio.app.features.streams

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Remembers which sources answered with links lately, so the next searches ask them first and
 * the sources that keep failing last. Each answer moves a source's score by one, within bounds.
 */
internal object StreamSourceStats {
    private const val KEY = "stream_source_scores"
    private const val MIN_SCORE = -3
    private const val MAX_SCORE = 5

    private val scores = MutableStateFlow<Map<String, Int>?>(null)

    fun score(addonId: String): Int = loaded()[addonId] ?: 0

    fun record(addonId: String, hadLinks: Boolean) {
        if (addonId.isBlank()) return
        val before = loaded()
        scores.update { current ->
            val map = current ?: before
            val next = ((map[addonId] ?: 0) + if (hadLinks) 1 else -1).coerceIn(MIN_SCORE, MAX_SCORE)
            if (map[addonId] == next) map else map + (addonId to next)
        }
        val after = scores.value ?: return
        if (after != before) {
            BingeGroupCacheStorage.save(KEY, after.entries.joinToString("\n") { (id, value) -> "$id=$value" })
        }
    }

    private fun loaded(): Map<String, Int> {
        scores.value?.let { return it }
        val parsed = BingeGroupCacheStorage.load(KEY).orEmpty()
            .lineSequence()
            .mapNotNull { line ->
                val id = line.substringBeforeLast('=', missingDelimiterValue = "").trim()
                val value = line.substringAfterLast('=').trim().toIntOrNull()
                if (id.isEmpty() || value == null) null else id to value
            }
            .toMap()
        scores.compareAndSet(null, parsed)
        return scores.value ?: parsed
    }
}

/** Sources that answered with links lately first, those that keep failing last; ties keep their order. */
internal fun <T> List<T>.bySourceTrackRecord(addonIdOf: (T) -> String): List<T> =
    sortedByDescending { item -> StreamSourceStats.score(addonIdOf(item)) }
