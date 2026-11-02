package com.example.ui.screens.watch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Episode
import com.example.data.model.EpisodeSource
import com.example.data.model.Series
import com.example.data.model.SeriesSeason
import com.example.data.repository.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WatchUiState(
    val isLoading: Boolean = true,
    val series: Series? = null,
    val seasons: List<SeriesSeason> = emptyList(),
    val selectedSeasonNumber: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val currentEpisode: Episode? = null,
    val sources: List<EpisodeSource> = emptyList(),
    val currentSource: EpisodeSource? = null,
    val currentSourceIndex: Int = 0,
    val watchedEpisodeIds: Set<String> = emptySet(),
    val isCompleted: Boolean = false,
    val railsExpanded: Boolean = true,
    val allowPopups: Boolean = false,
    val showServersSheet: Boolean = false,
    val showReportDialog: Boolean = false,
    val webViewProgress: Int = 0,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

class WatchViewModel(
    private val seriesId: String,
    private val initialEpisodeId: String?,
    private val seriesRepository: SeriesRepository,
    private val episodeRepository: EpisodeRepository,
    private val sourceRepository: SourceRepository,
    private val userActionsRepository: UserActionsRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchUiState())
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    private var progressTrackingJob: Job? = null
    private var secondsWatched: Int = 6

    init {
        observeSettings()
        loadEpisodeData()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.railsExpanded.collect { expanded ->
                _uiState.update { it.copy(railsExpanded = expanded) }
            }
        }
        viewModelScope.launch {
            settingsRepository.allowPopups.collect { allowed ->
                _uiState.update { it.copy(allowPopups = allowed) }
            }
        }
    }

    private fun loadEpisodeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            try {
                // Load series
                val seasons = episodeRepository.getSeasons(seriesId).first()
                val episodes = episodeRepository.getEpisodes(seriesId).first()

                val targetEpisode = if (!initialEpisodeId.isNullOrBlank()) {
                    episodes.find { it.id == initialEpisodeId } ?: episodes.firstOrNull()
                } else {
                    episodes.firstOrNull()
                }

                _uiState.update {
                    it.copy(
                        seasons = seasons,
                        selectedSeasonNumber = targetEpisode?.seasonNumber ?: 1,
                        episodes = episodes,
                        currentEpisode = targetEpisode
                    )
                }

                if (targetEpisode != null) {
                    loadSourcesForEpisode(targetEpisode)
                    recordEpisodeView(targetEpisode.id)
                } else {
                    _uiState.update { it.copy(isLoading = false, hasError = true, errorMessage = "No episodes found") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, hasError = true, errorMessage = e.message ?: "Failed to load watch screen") }
            }
        }
    }

    fun loadSourcesForEpisode(episode: Episode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            try {
                val sources = sourceRepository.getEpisodeSources(episode.id).first()
                val firstSource = sources.firstOrNull()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentEpisode = episode,
                        selectedSeasonNumber = episode.seasonNumber,
                        sources = sources,
                        currentSource = firstSource,
                        currentSourceIndex = 0,
                        hasError = firstSource == null,
                        errorMessage = if (firstSource == null) "No sources available yet" else null
                    )
                }

                startProgressTracking(episode)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, hasError = true, errorMessage = e.message) }
            }
        }
    }

    fun selectSource(source: EpisodeSource) {
        val index = _uiState.value.sources.indexOf(source)
        _uiState.update {
            it.copy(
                currentSource = source,
                currentSourceIndex = if (index >= 0) index else 0,
                hasError = false,
                errorMessage = null,
                showServersSheet = false
            )
        }
    }

    fun tryNextServer() {
        val sources = _uiState.value.sources
        val nextIndex = _uiState.value.currentSourceIndex + 1
        if (nextIndex < sources.size) {
            val nextSource = sources[nextIndex]
            _uiState.update {
                it.copy(
                    currentSource = nextSource,
                    currentSourceIndex = nextIndex,
                    hasError = false,
                    errorMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    hasError = true,
                    errorMessage = "All servers failed. Please report this issue or try again later."
                )
            }
        }
    }

    fun selectEpisode(episode: Episode) {
        if (_uiState.value.currentEpisode?.id == episode.id) return
        saveCurrentProgress()
        secondsWatched = 6
        loadSourcesForEpisode(episode)
        recordEpisodeView(episode.id)
    }

    fun nextEpisode() {
        val episodes = _uiState.value.episodes
        val currentEp = _uiState.value.currentEpisode ?: return
        val next = episodes.find {
            it.seasonNumber == currentEp.seasonNumber && it.episodeNumber == currentEp.episodeNumber + 1
        } ?: episodes.find {
            it.seasonNumber == currentEp.seasonNumber + 1 && it.episodeNumber == 1
        }
        if (next != null) {
            selectEpisode(next)
        }
    }

    fun toggleRailsExpanded() {
        val current = _uiState.value.railsExpanded
        viewModelScope.launch {
            settingsRepository.setRailsExpanded(!current)
        }
    }

    fun setWebViewProgress(progress: Int) {
        _uiState.update { it.copy(webViewProgress = progress) }
    }

    fun onWebPageError(error: String) {
        _uiState.update { it.copy(hasError = true, errorMessage = error) }
    }

    fun toggleServersSheet(show: Boolean) {
        _uiState.update { it.copy(showServersSheet = show) }
    }

    fun toggleReportDialog(show: Boolean) {
        _uiState.update { it.copy(showReportDialog = show, showServersSheet = false) }
    }

    fun reportCurrentSource(reason: String, details: String?) {
        val source = _uiState.value.currentSource ?: return
        viewModelScope.launch {
            sourceRepository.reportSource(source.id, reason, details).first()
            _uiState.update { it.copy(showReportDialog = false) }
        }
    }

    fun markCurrentEpisodeAsWatched() {
        val currentEp = _uiState.value.currentEpisode ?: return
        _uiState.update {
            it.copy(
                watchedEpisodeIds = it.watchedEpisodeIds + currentEp.id,
                isCompleted = true,
                showServersSheet = false
            )
        }
        viewModelScope.launch {
            userActionsRepository.upsertWatchProgress(
                seriesId = seriesId,
                episodeId = currentEp.id,
                positionSec = currentEp.durationSeconds ?: 1440,
                durationSec = currentEp.durationSeconds,
                completed = true
            )
        }
    }

    private fun recordEpisodeView(episodeId: String) {
        viewModelScope.launch {
            userActionsRepository.recordView(seriesId, episodeId)
        }
    }

    private fun startProgressTracking(episode: Episode) {
        progressTrackingJob?.cancel()
        progressTrackingJob = viewModelScope.launch {
            // Documented: WebView cannot report internal HTML5 player time, so estimate is used
            while (true) {
                delay(30_000) // update every 30 seconds
                secondsWatched += 30
                val duration = episode.durationSeconds ?: 1440
                val isCompleted = secondsWatched >= (duration * 0.85)
                if (isCompleted) {
                    _uiState.update {
                        it.copy(
                            isCompleted = true,
                            watchedEpisodeIds = it.watchedEpisodeIds + episode.id
                        )
                    }
                }
                userActionsRepository.upsertWatchProgress(
                    seriesId = seriesId,
                    episodeId = episode.id,
                    positionSec = secondsWatched,
                    durationSec = episode.durationSeconds,
                    completed = isCompleted
                )
            }
        }
    }

    fun saveCurrentProgress() {
        val ep = _uiState.value.currentEpisode ?: return
        viewModelScope.launch {
            val duration = ep.durationSeconds ?: 1440
            val isCompleted = secondsWatched >= (duration * 0.85) || _uiState.value.isCompleted
            userActionsRepository.upsertWatchProgress(
                seriesId = seriesId,
                episodeId = ep.id,
                positionSec = secondsWatched,
                durationSec = ep.durationSeconds,
                completed = isCompleted
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        progressTrackingJob?.cancel()
    }

    companion object {
        fun provideFactory(
            seriesId: String,
            initialEpisodeId: String?,
            seriesRepo: SeriesRepository,
            episodeRepo: EpisodeRepository,
            sourceRepo: SourceRepository,
            userActionsRepo: UserActionsRepository,
            settingsRepo: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WatchViewModel(seriesId, initialEpisodeId, seriesRepo, episodeRepo, sourceRepo, userActionsRepo, settingsRepo) as T
            }
        }
    }
}
