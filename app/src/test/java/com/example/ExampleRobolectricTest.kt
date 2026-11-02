package com.example

import com.example.data.model.ContentCategory
import com.example.data.model.SeriesCard
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun `verify schema models instantiated properly`() {
        val category = ContentCategory(
            id = "test-uuid",
            slug = "anime",
            name = "Anime",
            sortOrder = 1,
            isActive = true
        )
        assertEquals("anime", category.slug)

        val seriesCard = SeriesCard(
            id = "s-1",
            slug = "test-series",
            title = "Test Series",
            genres = listOf("action", "fantasy"),
            isMature = false
        )
        assertEquals("test-series", seriesCard.slug)
        assertEquals(2, seriesCard.genres.size)
    }
}
