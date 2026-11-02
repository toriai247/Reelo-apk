package com.example.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SeriesDetailsUiState(
    val isLoading: Boolean = true,
    val series: Series? = null,
    val seasons: List<SeriesSeason> = emptyList(),
    val selectedSeasonNumber: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val relatedSeries: List<SeriesCard> = emptyList(),
    val comments: List<SeriesComment> = emptyList(),
    val isInWatchlist: Boolean = false,
    val isLiked: Boolean = false,
    val userRating: Int? = null,
    val isLoggedIn: Boolean = false,
    val showMatureWarning: Boolean = false,
    val matureWarningDismissed: Boolean = false,
    val showRatingDialog: Boolean = false,
    val showCommentsSheet: Boolean = false,
    val error: String? = null
)

class SeriesDetailsViewModel(
    private val slug: String,
    private val seriesRepository: SeriesRepository,
    private val episodeRepository: EpisodeRepository,
    private val userActionsRepository: UserActionsRepository,
    private val commentRepository: CommentRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SeriesDetailsUiState())
    val uiState: StateFlow<SeriesDetailsUiState> = _uiState.asStateFlow()

    init {
        observeAuth()
        loadSeries()
    }

    private fun observeAuth() {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
                if (loggedIn) {
                    checkWatchlistStatus()
                }
            }
        }
    }

    fun loadSeries() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val series = seriesRepository.getSeriesBySlug(slug).first()
                if (series == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Series not found") }
                    return@launch
                }

                // Check mature status
                val needsWarning = series.isMature && !_uiState.value.matureWarningDismissed

                // Load seasons
                val seasons = episodeRepository.getSeasons(series.id).first()

                // Load episodes
                val episodes = episodeRepository.getEpisodes(series.id).first()

                // Load related
                val related = seriesRepository.getRelatedSeries(series.id).first()

                // Load comments
                val comments = commentRepository.getComments(series.id).first()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        series = series,
                        seasons = seasons,
                        episodes = episodes,
                        relatedSeries = related,
                        comments = comments,
                        showMatureWarning = needsWarning
                    )
                }

                checkWatchlistStatus()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load details") }
            }
        }
    }

    fun dismissMatureWarning(confirmed: Boolean) {
        _uiState.update {
            it.copy(
                showMatureWarning = false,
                matureWarningDismissed = confirmed
            )
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber) }
    }

    private fun checkWatchlistStatus() {
        viewModelScope.launch {
            val seriesId = _uiState.value.series?.id ?: return@launch
            userActionsRepository.getWatchlist().collect { list ->
                val inList = list.any { it.seriesId == seriesId }
                _uiState.update { it.copy(isInWatchlist = inList) }
            }
        }
    }

    fun toggleWatchlist(onRequireLogin: () -> Unit) {
        if (!_uiState.value.isLoggedIn) {
            onRequireLogin()
            return
        }
        val series = _uiState.value.series ?: return
        val current = _uiState.value.isInWatchlist
        // Optimistic UI
        _uiState.update { it.copy(isInWatchlist = !current) }
        viewModelScope.launch {
            val success = if (!current) {
                userActionsRepository.addToWatchlist(series.id).first()
            } else {
                userActionsRepository.removeFromWatchlist(series.id).first()
            }
            if (!success) {
                _uiState.update { it.copy(isInWatchlist = current) } // rollback
            }
        }
    }

    fun toggleLike(onRequireLogin: () -> Unit) {
        if (!_uiState.value.isLoggedIn) {
            onRequireLogin()
            return
        }
        val series = _uiState.value.series ?: return
        val current = _uiState.value.isLiked
        _uiState.update { it.copy(isLiked = !current) }
        viewModelScope.launch {
            val success = userActionsRepository.toggleSeriesLike(series.id, !current).first()
            if (!success) {
                _uiState.update { it.copy(isLiked = current) }
            }
        }
    }

    fun openRatingDialog(onRequireLogin: () -> Unit) {
        if (!_uiState.value.isLoggedIn) {
            onRequireLogin()
            return
        }
        _uiState.update { it.copy(showRatingDialog = true) }
    }

    fun closeRatingDialog() {
        _uiState.update { it.copy(showRatingDialog = false) }
    }

    fun submitRating(score: Int) {
        val series = _uiState.value.series ?: return
        _uiState.update { it.copy(showRatingDialog = false, userRating = score) }
        viewModelScope.launch {
            userActionsRepository.rateSeries(series.id, score).first()
        }
    }

    fun toggleCommentsSheet(show: Boolean) {
        _uiState.update { it.copy(showCommentsSheet = show) }
    }

    fun postComment(content: String) {
        val series = _uiState.value.series ?: return
        viewModelScope.launch {
            val success = commentRepository.postComment(series.id, null, null, content).first()
            if (success) {
                // Refresh comments
                val updated = commentRepository.getComments(series.id).first()
                _uiState.update { it.copy(comments = updated) }
            }
        }
    }

    fun likeComment(comment: SeriesComment) {
        val series = _uiState.value.series ?: return
        val newLiked = !comment.isLikedByMe
        viewModelScope.launch {
            val success = commentRepository.likeComment(comment.id, newLiked).first()
            if (success) {
                val updated = commentRepository.getComments(series.id).first()
                _uiState.update { it.copy(comments = updated) }
            }
        }
    }

    companion object {
        fun provideFactory(
            slug: String,
            seriesRepo: SeriesRepository,
            episodeRepo: EpisodeRepository,
            userActionsRepo: UserActionsRepository,
            commentRepo: CommentRepository,
            authRepo: AuthRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SeriesDetailsViewModel(slug, seriesRepo, episodeRepo, userActionsRepo, commentRepo, authRepo) as T
            }
        }
    }
}
