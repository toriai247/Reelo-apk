package com.example

import com.example.data.model.ContentCategory
import com.example.data.model.SeriesCard
import com.example.ui.screens.watch.helper.BrowserHelper
import com.example.ui.screens.watch.helper.DnsHelper
import com.example.ui.screens.watch.helper.WatchScripts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `verify supabase build config keys are non blank`() {
        assertTrue("SUPABASE_URL should not be blank", BuildConfig.SUPABASE_URL.isNotBlank())
        assertTrue("SUPABASE_ANON_KEY should not be blank", BuildConfig.SUPABASE_ANON_KEY.isNotBlank())
        assertTrue("SUPABASE_URL should start with http", BuildConfig.SUPABASE_URL.startsWith("http"))
    }

    @Test
    fun `verify content category model fields`() {
        val cat = ContentCategory(
            id = "cat-uuid",
            slug = "movie",
            name = "Movie",
            sortOrder = 1,
            isActive = true
        )
        assertEquals("movie", cat.slug)
        assertEquals("Movie", cat.name)
        assertTrue(cat.isActive)
    }

    @Test
    fun `verify series card model defaults and rating`() {
        val card = SeriesCard(
            id = "series-uuid",
            slug = "solo-leveling",
            title = "Solo Leveling",
            avgUserRating = 9.5,
            ratingsCount = 100,
            isMature = false
        )
        assertEquals(9.5, card.avgUserRating, 0.01)
        assertEquals(100, card.ratingsCount)
        assertFalse(card.isMature)
    }

    @Test
    fun `verify browser helper brave package constant`() {
        assertEquals("com.brave.browser", BrowserHelper.BRAVE_PACKAGE)
    }

    @Test
    fun `verify dns helper google hostname constant`() {
        assertEquals("dns.google", DnsHelper.GOOGLE_DNS_HOSTNAME)
    }

    @Test
    fun `verify watch scripts contain expected security signatures`() {
        assertTrue(WatchScripts.AD_CLEANER_JS.contains("cleanOverlays"))
        assertTrue(WatchScripts.AD_CLEANER_JS.contains("__reeloCleanerInjected"))
        assertTrue(WatchScripts.VIDEO_RESUME_BRIDGE_JS.contains("ReeloBridge"))
        assertTrue(WatchScripts.VIDEO_RESUME_BRIDGE_JS.contains("__reeloExecuteCommand"))
    }
}
