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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Reelo",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 2.dp, top = 6.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ReeloYellow)
                        )
                    }
                },
                actions = {
                    // Quick Sun / Moon Theme Toggle
                    IconButton(
                        onClick = { viewModel.toggleTheme() },
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        val isDark = uiState.currentTheme == ThemeMode.DARK
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDark) ReeloYellow else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Search Button
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier.testTag("home_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Category Chips Row
            val categoryNames = listOf("All") + uiState.categories.map { it.name }
            val currentSelectedName = if (uiState.selectedCategorySlug.equals("all", ignoreCase = true)) {
                "All"
            } else {
                uiState.categories.find { it.slug.equals(uiState.selectedCategorySlug, ignoreCase = true) }?.name ?: "All"
            }

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

            // Content Area
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

                    // Render Shelves according to layout: 'hero', 'row', 'top10', 'grid'
                    uiState.shelves.forEach { shelfItem ->
                        val shelf = shelfItem.shelf
                        val items = shelfItem.items

                        when (shelf.layout) {
                            "hero" -> {
                                item {
                                    ShelfHeroCarousel(
                                        items = items,
                                        onWatchClick = { series ->
                                            onWatchClick(series.id, null)
                                        },
                                        onDetailsClick = onSeriesClick
                                    )
                                }
                            }
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

                                        // 3-column row flow
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
                                                // Fill empty slots if last row has less than 3
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
        // Hero placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(shimmer)
        )

        // Row title
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmer)
        )

        // Poster row placeholders
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
