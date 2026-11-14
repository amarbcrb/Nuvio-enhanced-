package com.nuvio.app.features.streams

/**
 * Drops the links an addon lists more than once (Torrentio often returns the same torrent or the same
 * file several times): same torrent and file, same URL, or the same file name with the same size.
 * The first occurrence is kept, so the addon's own order (best first) is preserved.
 */
internal fun List<StreamItem>.withoutDuplicateLinks(): List<StreamItem> {
    if (size < 2) return this
    val seen = HashSet<String>(size * 2)
    return filter { stream ->
        val keys = stream.duplicateKeys()
        if (keys.isEmpty()) return@filter true
        val duplicate = keys.any { it in seen }
        seen += keys
        !duplicate
    }
}

private fun StreamItem.duplicateKeys(): List<String> = buildList {
    infoHash?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { add("hash:$it:${fileIdx ?: -1}") }
    url?.trim()?.takeIf { it.isNotEmpty() }?.let { add("url:$it") }
    val fileName = behaviorHints.filename?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    val size = behaviorHints.videoSize?.takeIf { it > 0 }
    if (fileName != null && size != null) add("file:$fileName:$size")
}
