package com.example.ui.screens.details

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Episode
import com.example.data.model.SeriesCard
import com.example.ui.components.RatingDialog
import com.example.ui.components.SeriesPosterCard
import com.example.ui.screens.comments.CommentsBottomSheet
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailsScreen(
    viewModel: SeriesDetailsViewModel,
    onBackClick: () -> Unit,
    onWatchEpisode: (seriesId: String, episodeId: String) -> Unit,
    onWatchTrailer: (trailerUrl: String) -> Unit,
    onRelatedSeriesClick: (SeriesCard) -> Unit,
    onRequireLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    // 18+ Mature Confirmation Dialog
    if (uiState.showMatureWarning) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissMatureWarning(false)
                onBackClick()
            },
            title = {
                Text("Age Verification (18+)", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("This series contains mature content and themes intended for adult audiences. Are you 18 years of age or older?")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissMatureWarning(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReeloYellow,
                        contentColor = ReeloBlack
                    )
                ) {
                    Text("I am 18+", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissMatureWarning(false)
                    onBackClick()
                }) {
                    Text("Go Back")
                }
            }
        )
    }

    // Rating Dialog
    if (uiState.showRatingDialog) {
        RatingDialog(
            initialRating = uiState.userRating ?: 8,
            onDismiss = { viewModel.closeRatingDialog() },
            onSubmit = { rating -> viewModel.submitRating(rating) }
        )
    }

    // Comments Bottom Sheet
    if (uiState.showCommentsSheet) {
        CommentsBottomSheet(
            comments = uiState.comments,
            isLoggedIn = uiState.isLoggedIn,
            onDismiss = { viewModel.toggleCommentsSheet(false) },
            onPostComment = { viewModel.postComment(it) },
            onLikeComment = { viewModel.likeComment(it) },
            onRequireLogin = onRequireLogin
        )
    }

    Scaffold(modifier = modifier) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ReeloYellow)
            }
        } else if (uiState.series == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Series not found")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onBackClick) { Text("Back to Home") }
                }
            }
        } else {
            val series = uiState.series!!
            val filteredEpisodes = uiState.episodes.filter {
                if (uiState.seasons.size > 1) it.seasonNumber == uiState.selectedSeasonNumber else true
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                // Header Banner & Overlay
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        AsyncImage(
                            model = series.bannerUrl ?: series.posterUrl,
                            contentDescription = series.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top & Bottom Gradient
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.7f),
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background
                                        ),
                                        startY = 0f,
                                        endY = 700f
                                    )
                                )
                        )

                        // Top Back Button
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .statusBarsPadding()
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(40.dp)
                                .testTag("details_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Info Section
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Title & Original Title
                        Text(
                            text = series.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (!series.originalTitle.isNullOrBlank()) {
                            Text(
                                text = series.originalTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Badges Row (Year, Status, Age, Mature, Ratings)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            if (series.releaseYear != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "${series.releaseYear}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = series.status.replaceFirstChar { it.uppercase() },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (series.status == "ongoing") SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            if (!series.ageRating.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = series.ageRating,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (series.isMature) {
                                Surface(
                                    color = MatureRed,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "18+",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Rating chip
                            val score = series.avgUserRating.takeIf { it > 0 } ?: series.rating
                            if (score != null) {
                                Surface(
                                    color = ReeloYellow.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = RatingGold,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = String.format("%.1f", score),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        if (series.ratingsCount > 0) {
                                            Text(
                                                text = " (${series.ratingsCount})",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Genres Chips
                        if (series.genres.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                series.genres.forEach { genre ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text(
                                            text = genre.replaceFirstChar { it.uppercase() },
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Big Yellow "▶ Watch" button (supports Continue Watching)
                        Button(
                            onClick = {
                                val targetEpId = uiState.continueEpisodeId ?: filteredEpisodes.firstOrNull()?.id ?: uiState.episodes.firstOrNull()?.id
                                if (targetEpId != null) {
                                    onWatchEpisode(series.id, targetEpId)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ReeloYellow,
                                contentColor = ReeloBlack
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("details_watch_button")
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.continueWatchingText ?: "Watch Episode 1",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Secondary Action Buttons Row: Watchlist, Like, Rate, Share, Trailer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            // Watchlist
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { viewModel.toggleWatchlist(onRequireLogin) }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isInWatchlist) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Watchlist",
                                    tint = if (uiState.isInWatchlist) ReeloYellow else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (uiState.isInWatchlist) "Saved" else "Watchlist",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            // Like
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { viewModel.toggleLike(onRequireLogin) }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Like",
                                    tint = if (uiState.isLiked) MatureRed else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (uiState.isLiked) "Liked" else "Like",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            // Rate
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { viewModel.openRatingDialog(onRequireLogin) }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.userRating != null) Icons.Default.Star else Icons.Outlined.Star,
                                    contentDescription = "Rate",
                                    tint = if (uiState.userRating != null) RatingGold else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = uiState.userRating?.let { "$it★" } ?: "Rate",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }

                            // Share
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Watch ${series.title} on Reelo!")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Share Series"))
                                    }
                                    .padding(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Share", style = MaterialTheme.typography.labelSmall)
                            }

                            // Trailer (if present)
                            if (!series.trailerUrl.isNullOrBlank()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { onWatchTrailer(series.trailerUrl) }
                                        .padding(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Movie, contentDescription = "Trailer", tint = ReeloYellow)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "Trailer", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Description with "Read more" toggle
                        if (!series.description.isNullOrBlank()) {
                            Text(
                                text = series.description,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (series.description.length > 120) {
                                Text(
                                    text = if (isDescriptionExpanded) "Show less" else "Read more",
                                    color = ReeloYellow,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }

                        // Studio & Languages info
                        if (!series.studio.isNullOrBlank() || series.languages.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                if (!series.studio.isNullOrBlank()) {
                                    Text(
                                        text = "Studio: ${series.studio}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (series.languages.isNotEmpty()) {
                                    Text(
                                        text = "Audio: ${series.languages.joinToString()}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Counters Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("👁 ${series.viewsCount} views", style = MaterialTheme.typography.labelMedium)
                            Text("❤ ${series.likesCount} likes", style = MaterialTheme.typography.labelMedium)
                            Text("💬 ${uiState.comments.size} comments", style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Seasons Tab Selector (if multiple seasons)
                        if (uiState.seasons.size > 1) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 8.dp)
                            ) {
                                uiState.seasons.forEach { season ->
                                    val isSelected = season.seasonNumber == uiState.selectedSeasonNumber
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.selectSeason(season.seasonNumber) },
                                        label = { Text("Season ${season.seasonNumber}") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ReeloYellow,
                                            selectedLabelColor = ReeloBlack
                                        )
                                    )
                                }
                            }
                        }

                        // Episodes Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes (${filteredEpisodes.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Episodes List
                items(filteredEpisodes, key = { it.id }) { ep ->
                    EpisodeRowItem(
                        episode = ep,
                        onClick = { onWatchEpisode(uiState.series!!.id, ep.id) }
                    )
                }

                // Comments Section Preview
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Comments (${uiState.comments.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { viewModel.toggleCommentsSheet(true) }) {
                                Text("View All", color = ReeloYellow)
                            }
                        }

                        if (uiState.comments.isNotEmpty()) {
                            val firstComment = uiState.comments.first()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleCommentsSheet(true) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = firstComment.profiles?.displayName ?: "User",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = firstComment.content,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No comments yet. Tap to start the discussion!",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Related Series Row
                if (uiState.relatedSeries.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(bottom = 24.dp)) {
                            Text(
                                text = "More Like This",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                uiState.relatedSeries.forEach { related ->
                                    SeriesPosterCard(
                                        series = related,
                                        onClick = { onRelatedSeriesClick(related) }
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
fun EpisodeRowItem(
    episode: Episode,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick)
            .testTag("episode_item_${episode.episodeNumber}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .width(110.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = ReeloYellow,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Episode info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "E${episode.episodeNumber} • ${episode.title ?: "Episode ${episode.episodeNumber}"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!episode.description.isNullOrBlank()) {
                    Text(
                        text = episode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (episode.durationSeconds != null) {
                    val minutes = episode.durationSeconds / 60
                    Text(
                        text = "$minutes min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
