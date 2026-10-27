package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamLanguageDetectorTest {
    private fun detect(vararg texts: String, providerLanguage: String? = null) =
        StreamLanguageDetector.detect(texts.toList(), providerLanguage)

    @Test
    fun readsFrenchDubAndSubtitledReleases() {
        assertEquals("VF", detect("Movie.2023.TRUEFRENCH.1080p.WEB").label)
        assertEquals(setOf("fr"), detect("Série S01E02 VOSTFR 720p").subtitles)
        assertEquals("VOSTFR", detect("Série S01E02 VOSTFR 720p").label)
        assertTrue(detect("Film MULTi 2160p").multi)
    }

    @Test
    fun readsSubtitleMarkerFollowedByLanguage() {
        val info = detect("Anime - 12 [SUB ENG]")
        assertEquals(setOf("en"), info.subtitles)
        assertTrue(info.audio.isEmpty())
    }

    @Test
    fun ignoresLowerCaseShortWordsInTitles() {
        val info = detect("Un homme en colère", "La casa de papel")
        assertNull(info.label)
    }

    @Test
    fun readsFlagsAndArabicWords() {
        assertEquals(setOf("fr"), detect("1080p 🇫🇷").audio)
        assertEquals(setOf("ar"), detect("مترجم").subtitles)
        assertEquals("AR", detect("Server 1", providerLanguage = "ar").label)
    }

    @Test
    fun filtersByPreferredLanguages() {
        val frenchOnly = StreamLanguagePreferences(linkLanguages = setOf("fr"))
        assertTrue(detect("VFF 1080p").matches(frenchOnly))
        assertTrue(detect("VOSTFR").matches(frenchOnly))
        assertFalse(detect("VOSTFR").matches(frenchOnly.copy(includeSubtitled = false)))
        assertFalse(detect("ENGLISH 1080p").matches(frenchOnly))
        assertTrue(detect("1080p").matches(frenchOnly))
        assertFalse(detect("1080p").matches(frenchOnly.copy(hideUnknown = true)))
        assertTrue(detect("MULTI").matches(frenchOnly.copy(hideUnknown = true)))
    }

    @Test
    fun roundTripsPreferences() {
        val preferences = StreamLanguagePreferences(
            linkLanguages = setOf("fr", "en"),
            includeSubtitled = false,
            hideUnknown = true,
            extensionLanguages = setOf("ar"),
        )
        assertEquals(preferences, StreamLanguagePreferences.decode(preferences.encode()))
    }
}
