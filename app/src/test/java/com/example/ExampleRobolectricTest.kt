package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ContentCategory
import com.example.data.model.Series
import com.example.data.model.SeriesCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ExampleRobolectricTest {

    @Test
    fun `verify app name string resource`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Reelo", appName)
    }

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
