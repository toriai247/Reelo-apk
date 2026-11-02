package com.example.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContinueWatchingItem
import com.example.data.model.SeriesCard
import com.example.ui.components.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSeriesClick: (SeriesCard) -> Unit,
    onWatchClick: (seriesId: String, episodeId: String?) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val categoryNames = listOf("All") + uiState.categories.map { it.name }
    val currentSelectedName = if (uiState.selectedCategorySlug.equals("all", ignoreCase = true)) {
        "All"
    } else {
        uiState.categories.find { it.slug.equals(uiState.selectedCategorySlug, ignoreCase = true) }?.name ?: "All"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Content Layer
        if (uiState.isLoading) {
            HomeLoadingSkeleton()
        } else if (uiState.error != null && uiState.shelves.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Could not load catalog",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.error ?: "Network error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.loadData() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ReeloYellow,
                            contentColor = ReeloBlack
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Render Shelves according to layout: 'hero', 'row', 'top10', 'grid'
                val heroShelf = uiState.shelves.find { it.shelf.layout == "hero" }
                val nonHeroShelves = uiState.shelves.filter { it.shelf.layout != "hero" }

                // Full-bleed Hero Banner Carousel at top of LazyColumn
                if (heroShelf != null) {
                    item {
                        ShelfHeroCarousel(
                            items = heroShelf.items,
                            onWatchClick = { series ->
                                onWatchClick(series.id, null)
                            },
                            onDetailsClick = onSeriesClick
                        )
                    }
                }

                // Category Chips Selector Row
                item {
                    CategoryChipsRow(
                        categories = categoryNames,
                        selectedCategory = currentSelectedName,
                        onCategorySelected = { name ->
                            val slug = if (name.equals("All", ignoreCase = true)) {
                                "all"
                            } else {
                                uiState.categories.find { it.name.equals(name, ignoreCase = true) }?.slug ?: name.lowercase()
                            }
                            viewModel.selectCategory(slug)
                        }
                    )
                }

                // Continue Watching Shelf (if any)
                if (uiState.continueWatching.isNotEmpty()) {
                    item {
                        ContinueWatchingRow(
                            items = uiState.continueWatching,
                            onItemClick = { item ->
                                onWatchClick(item.series.id, item.episode.id)
                            }
                        )
                    }
                }

                // Other Shelves
                nonHeroShelves.forEach { shelfItem ->
                    val shelf = shelfItem.shelf
                    val items = shelfItem.items

                    when (shelf.layout) {
                        "top10" -> {
                            item {
                                ShelfTop10Row(
                                    title = shelf.title,
                                    items = items,
                                    onSeriesClick = onSeriesClick
                                )
                            }
                        }
                        "grid" -> {
                            item {
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                    Text(
                                        text = shelf.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )

                                    items.chunked(3).forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowItems.forEach { series ->
                                                SeriesPosterCard(
                                                    series = series,
                                                    onClick = { onSeriesClick(series) },
                                                    modifier = Modifier.weight(1f),
                                                    width = 110
                                                )
                                            }
                                            repeat(3 - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> { // Default 'row'
                            item {
                                ShelfPosterRow(
                                    title = shelf.title,
                                    items = items,
                                    onSeriesClick = onSeriesClick
                                )
                            }
                        }
                    }
                }
            }
        }

        // Transparent Floating Top Header Bar (Overlaid over Hero Banner)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Reelo Logo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Reelo",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .padding(start = 2.dp, top = 4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ReeloYellow)
                )
            }

            // Transparent Pill Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.toggleTheme() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .testTag("theme_toggle_button")
                ) {
                    val isDark = uiState.currentTheme == ThemeMode.DARK
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle Theme",
                        tint = if (isDark) ReeloYellow else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                        .testTag("home_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeLoadingSkeleton() {
    val shimmer = ShimmerBrush()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmer)
        )

        Box(
            modifier = Modifier
                .width(140.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmer)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmer)
                )
            }
        }
    }
}
