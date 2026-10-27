package com.nuvio.app.features.streams

/** Resolution, release type and HDR format of a stream, read from its name, description and filename. */
data class StreamQualityInfo(
    val resolution: String? = null,
    val release: String? = null,
    val hdr: String? = null,
) {
    /** Badge text such as "4K · BluRay · HDR" or "CAM", or null when nothing is known. */
    val label: String?
        get() = listOfNotNull(resolution, release, hdr).joinToString(" · ").takeIf { it.isNotEmpty() }

    /** Theatre recordings (CAM, TS, TC, screeners) have poor picture and sound. */
    val isLowQualityRelease: Boolean
        get() = release in lowQualityReleases

    private companion object {
        val lowQualityReleases = setOf("CAM", "TS", "TC", "SCREENER")
    }
}

object StreamQualityDetector {
    private fun pattern(body: String) = Regex("(?<![A-Z0-9])(?:$body)(?![A-Z0-9])")

    /** Checked in order; the first match names the release type. */
    private val releases: List<Pair<Regex, String>> = listOf(
        pattern("REMUX") to "REMUX",
        pattern("BLU[ ._-]?RAY|BDRIP|BRRIP|BDREMUX|BD25|BD50") to "BluRay",
        pattern("WEB[ ._-]?DL") to "WEB-DL",
        pattern("WEB[ ._-]?RIP") to "WEBRip",
        pattern("HDTV|PDTV") to "HDTV",
        pattern("DVD[ ._-]?SCR|SCREENER|SCR") to "SCREENER",
        pattern("DVD[ ._-]?RIP|DVD5|DVD9|DVD") to "DVD",
        pattern("HD[ ._-]?RIP") to "HDRip",
        pattern("HD[ ._-]?CAM|CAM[ ._-]?RIP|CAM") to "CAM",
        pattern("HD[ ._-]?TS|TELESYNC|TS") to "TS",
        pattern("TELECINE|TC") to "TC",
        pattern("WEB") to "WEB",
    )

    private val resolutions: List<Pair<Regex, String>> = listOf(
        pattern("2160P|4K|UHD") to "4K",
        pattern("1440P|2K") to "1440p",
        pattern("1080P|1080I|FHD") to "1080p",
        pattern("720P") to "720p",
        pattern("576P|480P|SD") to "480p",
    )

    private val hdrFormats: List<Pair<Regex, String>> = listOf(
        pattern("DV|DOVI|DOLBY[ ._-]?VISION") to "DV",
        pattern("HDR10\\+?|HDR") to "HDR",
    )

    fun detect(stream: StreamItem): StreamQualityInfo =
        detect(
            listOfNotNull(
                stream.name,
                stream.title,
                stream.description,
                stream.behaviorHints.filename,
            ),
        )

    fun detect(texts: List<String>): StreamQualityInfo {
        val text = texts.joinToString(" ").uppercase()
        fun first(candidates: List<Pair<Regex, String>>): String? =
            candidates.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second
        return StreamQualityInfo(
            resolution = first(resolutions),
            release = first(releases),
            hdr = first(hdrFormats),
        )
    }
}
