package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SeriesSourceMemoryTest {
    private fun link(addonId: String, sourceName: String?, name: String) = StreamItem(
        name = name,
        url = "https://example.com/$addonId/$name.mp4",
        sourceName = sourceName,
        addonName = addonId,
        addonId = addonId,
    )

    @Test
    fun `links of the remembered source come first then the rest of its addon`() {
        val streams = listOf(
            link("addon:other", null, "o1"),
            link("cloudstream:fs", "Uqload", "u1"),
            link("cloudstream:fs", "Voe", "v1"),
            link("cloudstream:fs", "Voe", "v2"),
        )
        val remembered = RememberedSeriesSource(addonId = "cloudstream:fs", sourceName = "voe")

        val candidates = remembered.candidateStreams(streams, debridEnabled = false)

        assertEquals(listOf("v1", "v2", "u1"), candidates.map { it.name })
    }

    @Test
    fun `no candidates when the remembered source has nothing for the episode`() {
        val streams = listOf(link("addon:other", null, "o1"))
        val remembered = RememberedSeriesSource(addonId = "cloudstream:fs", sourceName = null)

        assertTrue(remembered.candidateStreams(streams, debridEnabled = false).isEmpty())
    }

    @Test
    fun `the server playing now comes first for the next episode`() {
        val streams = listOf(
            link("cloudstream:movix", null, "Upvid"),
            link("cloudstream:movix", "Uqload", "Uqload"),
            link("cloudstream:movix", "Uqload", "Uqload · 360p"),
        )
        val playing = link("cloudstream:movix", null, "Uqload · 360p")

        assertEquals("uqload", playing.serverKey())
        assertEquals(listOf("Uqload", "Uqload · 360p", "Upvid"), streams.sameServerFirst(playing.serverKey()).map { it.name })
        assertEquals(streams, streams.sameServerFirst(null))
    }
}
