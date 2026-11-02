package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ContentCategory(
    @Json(name = "id") val id: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "sort_order") val sortOrder: Int = 0,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CategoryInfo(
    @Json(name = "id") val id: String? = null,
    @Json(name = "slug") val slug: String? = null,
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class SeriesCard(
    @Json(name = "id") val id: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "category") val category: CategoryInfo? = null,
    @Json(name = "genres") val genres: List<String> = emptyList(),
    @Json(name = "languages") val languages: List<String> = emptyList(),
    @Json(name = "rating") val rating: Double? = null,
    @Json(name = "avg_user_rating") val avgUserRating: Double = 0.0,
    @Json(name = "ratings_count") val ratingsCount: Int = 0,
    @Json(name = "status") val status: String? = "ongoing",
    @Json(name = "release_year") val releaseYear: Int? = null,
    @Json(name = "age_rating") val ageRating: String? = null,
    @Json(name = "is_mature") val isMature: Boolean = false,
    @Json(name = "episodes_count") val episodesCount: Int = 0,
    @Json(name = "last_episode_at") val lastEpisodeAt: String? = null,
    @Json(name = "views_count") val viewsCount: Long = 0,
    @Json(name = "likes_count") val likesCount: Int = 0,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Series(
    @Json(name = "id") val id: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "original_title") val originalTitle: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "banner_url") val bannerUrl: String? = null,
    @Json(name = "trailer_url") val trailerUrl: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "genres") val genres: List<String> = emptyList(),
    @Json(name = "languages") val languages: List<String> = emptyList(),
    @Json(name = "subtitle_languages") val subtitleLanguages: List<String> = emptyList(),
    @Json(name = "rating") val rating: Double? = null,
    @Json(name = "age_rating") val ageRating: String? = null,
    @Json(name = "is_mature") val isMature: Boolean = false,
    @Json(name = "status") val status: String = "ongoing",
    @Json(name = "release_year") val releaseYear: Int? = null,
    @Json(name = "studio") val studio: String? = null,
    @Json(name = "total_episodes") val totalEpisodes: Int? = null,
    @Json(name = "is_published") val isPublished: Boolean = false,
    @Json(name = "is_featured") val isFeatured: Boolean = false,
    @Json(name = "is_blocked") val isBlocked: Boolean = false,
    @Json(name = "blocked_reason") val blockedReason: String? = null,
    @Json(name = "created_by") val createdBy: String? = null,
    @Json(name = "avg_user_rating") val avgUserRating: Double = 0.0,
    @Json(name = "ratings_count") val ratingsCount: Int = 0,
    @Json(name = "likes_count") val likesCount: Int = 0,
    @Json(name = "comments_count") val commentsCount: Int = 0,
    @Json(name = "views_count") val viewsCount: Long = 0,
    @Json(name = "episodes_count") val episodesCount: Int = 0,
    @Json(name = "last_episode_at") val lastEpisodeAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SeriesSeason(
    @Json(name = "id") val id: String = "",
    @Json(name = "series_id") val seriesId: String = "",
    @Json(name = "season_number") val seasonNumber: Int = 1,
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "poster_url") val posterUrl: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Episode(
    @Json(name = "id") val id: String = "",
    @Json(name = "series_id") val seriesId: String = "",
    @Json(name = "season_number") val seasonNumber: Int = 1,
    @Json(name = "episode_number") val episodeNumber: Int = 1,
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "duration_seconds") val durationSeconds: Int? = null,
    @Json(name = "air_date") val airDate: String? = null,
    @Json(name = "intro_start_sec") val introStartSec: Int? = null,
    @Json(name = "intro_end_sec") val introEndSec: Int? = null,
    @Json(name = "is_published") val isPublished: Boolean = true,
    @Json(name = "likes_count") val likesCount: Int = 0,
    @Json(name = "comments_count") val commentsCount: Int = 0,
    @Json(name = "views_count") val viewsCount: Long = 0,
    @Json(name = "created_by") val createdBy: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class EpisodeSource(
    @Json(name = "id") val id: String = "",
    @Json(name = "episode_id") val episodeId: String = "",
    @Json(name = "label") val label: String = "Server 1",
    @Json(name = "url") val url: String = "",
    @Json(name = "platform") val platform: String? = null,
    @Json(name = "language") val language: String? = null,
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "priority") val priority: Int = 0,
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "fail_count") val failCount: Int = 0,
    @Json(name = "last_ok_at") val lastOkAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SourceReport(
    @Json(name = "id") val id: String? = null,
    @Json(name = "source_id") val sourceId: String = "",
    @Json(name = "user_id") val userId: String = "",
    @Json(name = "reason") val reason: String = "broken", // 'broken','wrong_episode','copyright','other'
    @Json(name = "details") val details: String? = null,
    @Json(name = "status") val status: String = "open"
)

@JsonClass(generateAdapter = true)
data class ShelfInfo(
    @Json(name = "id") val id: String = "",
    @Json(name = "slug") val slug: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "type") val type: String = "manual", // 'manual','genre','category','auto_trending','latest_episodes','top_rated','hero'
    @Json(name = "layout") val layout: String = "row", // 'row','hero','top10','grid'
    @Json(name = "position") val position: Int = 0
)

@JsonClass(generateAdapter = true)
data class HomeShelfItem(
    @Json(name = "shelf") val shelf: ShelfInfo,
    @Json(name = "items") val items: List<SeriesCard> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ContinueEpisodeInfo(
    @Json(name = "id") val id: String = "",
    @Json(name = "season_number") val seasonNumber: Int = 1,
    @Json(name = "episode_number") val episodeNumber: Int = 1,
    @Json(name = "title") val title: String? = null,
    @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,
    @Json(name = "duration_seconds") val durationSeconds: Int? = null
)

@JsonClass(generateAdapter = true)
data class ContinueWatchingItem(
    @Json(name = "series") val series: SeriesCard,
    @Json(name = "episode") val episode: ContinueEpisodeInfo,
    @Json(name = "position_sec") val positionSec: Int = 0,
    @Json(name = "duration_sec") val durationSec: Int? = null,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class WatchProgress(
    @Json(name = "user_id") val userId: String = "",
    @Json(name = "episode_id") val episodeId: String = "",
    @Json(name = "series_id") val seriesId: String = "",
    @Json(name = "position_sec") val positionSec: Int = 0,
    @Json(name = "duration_sec") val durationSec: Int? = null,
    @Json(name = "completed") val completed: Boolean = false,
    @Json(name = "updated_at") val updatedAt: String? = null
)

@JsonClass(generateAdapter = true)
data class Profile(
    @Json(name = "id") val id: String = "",
    @Json(name = "username") val username: String = "",
    @Json(name = "display_name") val displayName: String = "",
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "role") val role: String = "user"
)

@JsonClass(generateAdapter = true)
data class CommentAuthor(
    @Json(name = "username") val username: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class SeriesComment(
    @Json(name = "id") val id: String = "",
    @Json(name = "series_id") val seriesId: String = "",
    @Json(name = "episode_id") val episodeId: String? = null,
    @Json(name = "user_id") val userId: String = "",
    @Json(name = "parent_id") val parentId: String? = null,
    @Json(name = "content") val content: String = "",
    @Json(name = "likes_count") val likesCount: Int = 0,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "profiles") val profiles: CommentAuthor? = null,
    @Json(name = "is_liked_by_me") val isLikedByMe: Boolean = false
)

@JsonClass(generateAdapter = true)
data class WatchlistItem(
    @Json(name = "user_id") val userId: String = "",
    @Json(name = "series_id") val seriesId: String = "",
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "series") val series: SeriesCard? = null
)
