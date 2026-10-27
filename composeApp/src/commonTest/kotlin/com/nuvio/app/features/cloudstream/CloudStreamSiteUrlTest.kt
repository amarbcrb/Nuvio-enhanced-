package com.nuvio.app.features.cloudstream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CloudStreamSiteUrlTest {
    @Test
    fun addsSchemeAndDropsTrailingSlash() {
        assertEquals("https://example.org", normalizeCloudStreamSiteUrl(" example.org/ "))
        assertEquals("http://example.org/films", normalizeCloudStreamSiteUrl("http://example.org/films/"))
    }

    @Test
    fun rejectsTextThatIsNotAWebAddress() {
        assertNull(normalizeCloudStreamSiteUrl(""))
        assertNull(normalizeCloudStreamSiteUrl("not a site"))
        assertNull(normalizeCloudStreamSiteUrl("ftp://example.org"))
        assertNull(normalizeCloudStreamSiteUrl("example"))
    }
}
