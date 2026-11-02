package com.example.data.repository

import com.example.data.local.PreferencesManager
import com.example.data.model.*
import com.example.data.remote.SupabaseApiClient
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class HomeRepository(
    private val apiClient: SupabaseApiClient
) {
    fun getCategories(): Flow<List<ContentCategory>> = flow {
        emit(apiClient.getCategories())
    }

    fun getSeriesHome(category: String?): Flow<List<HomeShelfItem>> = flow {
        emit(apiClient.getSeriesHome(category))
    }

    fun getContinueWatching(): Flow<List<ContinueWatchingItem>> = flow {
        emit(apiClient.getContinueWatching(12))
    }
}

class SeriesRepository(
    private val apiClient: SupabaseApiClient
) {
    fun getSeriesBySlug(slug: String): Flow<Series?> = flow {
        emit(apiClient.getSeriesBySlug(slug))
    }

    fun getRelatedSeries(seriesId: String): Flow<List<SeriesCard>> = flow {
        emit(apiClient.getRelatedSeries(seriesId))
    }

    fun searchSeries(query: String, category: String?, limit: Int = 20, offset: Int = 0): Flow<List<SeriesCard>> = flow {
        emit(apiClient.searchSeries(query, category, limit, offset))
    }
}

class EpisodeRepository(
    private val apiClient: SupabaseApiClient
) {
    fun getSeasons(seriesId: String): Flow<List<SeriesSeason>> = flow {
        emit(apiClient.getSeasons(seriesId))
    }

    fun getEpisodes(seriesId: String): Flow<List<Episode>> = flow {
        emit(apiClient.getEpisodes(seriesId))
    }
}

class SourceRepository(
    private val apiClient: SupabaseApiClient,
    private val prefs: PreferencesManager
) {
    fun getEpisodeSources(episodeId: String): Flow<List<EpisodeSource>> = flow {
        emit(apiClient.getEpisodeSources(episodeId))
    }

    fun reportSource(sourceId: String, reason: String, details: String?): Flow<Boolean> = flow {
        val userId = prefs.userIdFlow.first() ?: prefs.getOrCreateViewerKey()
        emit(apiClient.reportSource(sourceId, userId, reason, details))
    }
}

class UserActionsRepository(
    private val apiClient: SupabaseApiClient,
    private val prefs: PreferencesManager
) {
    suspend fun recordView(seriesId: String, episodeId: String?) {
        apiClient.recordView(seriesId, episodeId)
    }

    suspend fun upsertWatchProgress(seriesId: String, episodeId: String, positionSec: Int, durationSec: Int?, completed: Boolean) {
        val userId = prefs.userIdFlow.first() ?: return
        val progress = WatchProgress(
            userId = userId,
            episodeId = episodeId,
            seriesId = seriesId,
            positionSec = positionSec,
            durationSec = durationSec,
            completed = completed
        )
        apiClient.upsertWatchProgress(progress)
    }

    val localWatchlistIdsFlow: Flow<Set<String>> = prefs.localWatchlistIdsFlow

    fun getWatchlist(): Flow<List<WatchlistItem>> = flow {
        val localIds = prefs.localWatchlistIdsFlow.first()
        val userId = prefs.userIdFlow.first()
        val remoteItems = if (!userId.isNullOrBlank()) {
            runCatching { apiClient.getWatchlist(userId) }.getOrDefault(emptyList())
        } else emptyList()

        val remoteIds = remoteItems.map { it.seriesId }.toSet()
        val combinedIds = localIds + remoteIds

        val items = combinedIds.mapNotNull { sId ->
            val remoteItem = remoteItems.find { it.seriesId == sId }
            if (remoteItem != null) {
                remoteItem
            } else {
                val seriesCard = runCatching { apiClient.getSeriesCardById(sId) }.getOrNull()
                seriesCard?.let { WatchlistItem(userId = userId ?: "local", seriesId = sId, series = it) }
            }
        }
        emit(items)
    }

    fun toggleWatchlist(seriesId: String): Flow<Boolean> = flow {
        val isBookmarked = prefs.toggleLocalWatchlist(seriesId)
        val userId = prefs.userIdFlow.first()
        if (!userId.isNullOrBlank()) {
            runCatching {
                if (isBookmarked) apiClient.addToWatchlist(userId, seriesId)
                else apiClient.removeFromWatchlist(userId, seriesId)
            }
        }
        emit(isBookmarked)
    }

    fun addToWatchlist(seriesId: String): Flow<Boolean> = flow {
        prefs.toggleLocalWatchlist(seriesId)
        val userId = prefs.userIdFlow.first()
        if (!userId.isNullOrBlank()) {
            runCatching { apiClient.addToWatchlist(userId, seriesId) }
        }
        emit(true)
    }

    fun removeFromWatchlist(seriesId: String): Flow<Boolean> = flow {
        prefs.removeFromLocalWatchlist(seriesId)
        val userId = prefs.userIdFlow.first()
        if (!userId.isNullOrBlank()) {
            runCatching { apiClient.removeFromWatchlist(userId, seriesId) }
        }
        emit(true)
    }

    fun rateSeries(seriesId: String, rating: Int): Flow<Boolean> = flow {
        val userId = prefs.userIdFlow.first()
        if (userId.isNullOrBlank()) {
            emit(false)
        } else {
            emit(apiClient.rateSeries(seriesId, userId, rating))
        }
    }

    fun toggleSeriesLike(seriesId: String, isLiked: Boolean): Flow<Boolean> = flow {
        val userId = prefs.userIdFlow.first()
        if (userId.isNullOrBlank()) {
            emit(false)
        } else {
            emit(apiClient.toggleSeriesLike(seriesId, userId, isLiked))
        }
    }
}

class CommentRepository(
    private val apiClient: SupabaseApiClient,
    private val prefs: PreferencesManager
) {
    fun getComments(seriesId: String, episodeId: String? = null): Flow<List<SeriesComment>> = flow {
        emit(apiClient.getComments(seriesId, episodeId))
    }

    fun postComment(seriesId: String, episodeId: String?, parentId: String?, content: String): Flow<Boolean> = flow {
        val userId = prefs.userIdFlow.first()
        if (userId.isNullOrBlank()) {
            emit(false)
        } else {
            emit(apiClient.postComment(seriesId, episodeId, userId, parentId, content))
        }
    }

    fun likeComment(commentId: String, isLiked: Boolean): Flow<Boolean> = flow {
        val userId = prefs.userIdFlow.first()
        if (userId.isNullOrBlank()) {
            emit(false)
        } else {
            emit(apiClient.likeComment(commentId, userId, isLiked))
        }
    }
}

class AuthRepository(
    private val apiClient: SupabaseApiClient,
    private val prefs: PreferencesManager
) {
    val currentUserProfile: Flow<Profile?> = prefs.userProfileFlow
    val isLoggedIn: Flow<Boolean> = prefs.authTokenFlow.map { !it.isNullOrBlank() }

    suspend fun signIn(email: String, pass: String): Result<Profile> {
        return apiClient.signIn(email, pass)
    }

    suspend fun signUp(email: String, pass: String, username: String, displayName: String): Result<Profile> {
        return apiClient.signUp(email, pass, username, displayName)
    }

    suspend fun signOut() {
        apiClient.signOut()
    }
}

class SettingsRepository(
    private val prefs: PreferencesManager
) {
    val themeMode: Flow<ThemeMode> = prefs.themeModeFlow
    suspend fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)

    val allowPopups: Flow<Boolean> = prefs.allowPopupsFlow
    suspend fun setAllowPopups(allow: Boolean) = prefs.setAllowPopups(allow)

    val useSecureDns: Flow<Boolean> = prefs.useSecureDnsFlow
    suspend fun setUseSecureDns(enabled: Boolean) = prefs.setUseSecureDns(enabled)

    val stayOnOriginalSite: Flow<Boolean> = prefs.stayOnOriginalSiteFlow
    suspend fun setStayOnOriginalSite(stay: Boolean) = prefs.setStayOnOriginalSite(stay)

    val adCleanerEnabled: Flow<Boolean> = prefs.adCleanerEnabledFlow
    suspend fun setAdCleanerEnabled(enabled: Boolean) = prefs.setAdCleanerEnabled(enabled)

    val popupGatedPlayers: Flow<Boolean> = prefs.popupGatedPlayersFlow
    suspend fun setPopupGatedPlayers(enabled: Boolean) = prefs.setPopupGatedPlayers(enabled)

    val braveDontShowAgain: Flow<Boolean> = prefs.braveDontShowAgainFlow
    suspend fun setBraveDontShowAgain(dontShow: Boolean) = prefs.setBraveDontShowAgain(dontShow)

    suspend fun getBraveLastShownTime(): Long = prefs.getBraveLastShownTime()
    suspend fun updateBraveLastShownTime(timestamp: Long = System.currentTimeMillis()) = prefs.updateBraveLastShownTime(timestamp)

    val disabledShieldHosts: Flow<Set<String>> = prefs.disabledShieldHostsFlow
    suspend fun toggleShieldForHost(host: String): Boolean = prefs.toggleShieldForHost(host)

    suspend fun saveEpisodePlayback(episodeId: String, seriesId: String, positionSec: Int, durationSec: Int) =
        prefs.saveEpisodePlayback(episodeId, seriesId, positionSec, durationSec)

    suspend fun getEpisodePlayback(episodeId: String): Pair<Int, Int>? =
        prefs.getEpisodePlayback(episodeId)

    fun getSeriesLastPlaybackFlow(seriesId: String): Flow<Triple<String, Int, Int>?> =
        prefs.getSeriesLastPlaybackFlow(seriesId)

    val railsExpanded: Flow<Boolean> = prefs.railsExpandedFlow
    suspend fun setRailsExpanded(expanded: Boolean) = prefs.setRailsExpanded(expanded)

    val recentSearches: Flow<List<String>> = prefs.recentSearchesFlow
    suspend fun addRecentSearch(query: String) = prefs.addRecentSearch(query)
    suspend fun clearRecentSearches() = prefs.clearRecentSearches()
}
