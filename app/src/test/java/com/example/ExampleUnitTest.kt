package com.example

import com.example.data.model.ContentCategory
import com.example.data.model.SeriesCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `verify supabase build config keys are configured with live project`() {
        assertEquals("https://ffbmozwsasbhevtehbit.supabase.co", BuildConfig.SUPABASE_URL)
        assertEquals("sb_publishable_kzXQl0X6eNtzFrAuY-3EFw_pYh6H-qr", BuildConfig.SUPABASE_ANON_KEY)
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
}
