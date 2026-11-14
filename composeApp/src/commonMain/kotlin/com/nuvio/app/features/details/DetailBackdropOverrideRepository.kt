package com.nuvio.app.features.details

import com.nuvio.app.features.streams.BingeGroupCacheStorage

/** Background picked by the user for a title, kept per profile ("type:id" -> image URL). */
internal object DetailBackdropOverrideRepository {
    private const val KEY = "detail_backdrop_override_v1"
    private var loaded = false
    private var map: Map<String, String> = emptyMap()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        map = BingeGroupCacheStorage.load(KEY)
            ?.lineSequence()
            ?.mapNotNull { line ->
                val key = line.substringBefore('\t', "").takeIf { it.isNotBlank() }
                val url = line.substringAfter('\t', "").takeIf { it.isNotBlank() }
                if (key != null && url != null) key to url else null
            }
            ?.toMap()
            .orEmpty()
    }

    fun reload() {
        loaded = false
        ensureLoaded()
    }

    fun get(type: String, id: String): String? {
        ensureLoaded()
        return map["$type:$id"]
    }

    /** Pass null to go back to the automatic background. */
    fun set(type: String, id: String, url: String?) {
        ensureLoaded()
        val key = "$type:$id"
        map = if (url == null) map - key else map + (key to url)
        BingeGroupCacheStorage.save(KEY, map.entries.joinToString("\n") { "${it.key}\t${it.value}" })
    }
}
