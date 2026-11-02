package com.example.ui.screens.watch

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Episode
import com.example.data.model.EpisodeSource
import com.example.data.model.Series
import com.example.data.model.SeriesSeason
import com.example.data.proxy.DohResolver
import com.example.data.proxy.LocalDohProxyServer
import com.example.data.repository.*
import com.example.ui.screens.watch.helper.DnsHelper
import com.example.ui.screens.watch.helper.PrivateDnsStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.ArrayDeque

enum class UserAgentMode(val label: String, val value: String) {
    MOBILE("Mobile", "Mozilla/5.0 (Linux; Android 14; Mobile; rv:128.0) Gecko/128.0 Firefox/128.0 Chrome/124.0.0.0 Mobile Safari/537.36"),
    DESKTOP("Desktop", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
}

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
    val useSecureDns: Boolean = false, // Default false to prevent ERR_TIMED_OUT with local proxy
    val isProxyActive: Boolean = false,
    val proxyPort: Int = 0,
    val autoRetryAttempted: Boolean = false,
    val showServersSheet: Boolean = false,
    val showReportDialog: Boolean = false,
    val webViewProgress: Int = 0,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val isLandscapeMode: Boolean = false, // Default to 9:16 portrait vertical reel
    val userAgentMode: UserAgentMode = UserAgentMode.MOBILE,
    val isLiked: Boolean = false,
    val heartAnimationCoordinates: Pair<Float, Float>? = null,
    val comments: List<com.example.data.model.SeriesComment> = emptyList(),
    val showCommentsSheet: Boolean = false,
    val isUserLoggedIn: Boolean = false,
    val reportSubmittedToast: String? = null,

    // Feature 1: Open in Browser
    val showOpenInBrowserSheet: Boolean = false,
    val currentPageUrl: String? = null,

    // Feature 2: Brave Recommendation
    val showBraveDialog: Boolean = false,

    // Feature 3: Guided Secure DNS
    val showSecureDnsSheet: Boolean = false,
    val dnsStatus: PrivateDnsStatus = PrivateDnsStatus(isActive = false, serverName = null, isGoogleDns = false),

    // Feature 4: Stay on Original Site (Auto-Return)
    val stayOnOriginalSite: Boolean = true,
    val blockedRedirectsCount: Int = 0,
    val blockedRedirectToast: String? = null,
    val allowOnceActive: Boolean = false,
    val showShieldSheet: Boolean = false,
    val disabledShieldHosts: Set<String> = emptySet(),

    // Feature 5: Ad Overlay Cleaner
    val adCleanerEnabled: Boolean = true,

    // Feature 6: Popup Handling
    val popupGatedPlayers: Boolean = false,

    // Feature 7: Real Playback Position Resume
    val resumeChipText: String? = null,
    val showResumeChip: Boolean = false,
    val targetSeekSeconds: Int? = null,
    val seekCommandId: Long = 0L
)

class WatchViewModel(
    private val seriesId: String,
    private val initialEpisodeId: String?,
    private val seriesRepository: SeriesRepository,
    private val episodeRepository: EpisodeRepository,
    private val sourceRepository: SourceRepository,
    private val userActionsRepository: UserActionsRepository,
    private val settingsRepository: SettingsRepository,
    private val commentRepository: CommentRepository? = null,
    private val authRepository: AuthRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchUiState())
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    private val dohResolver = DohResolver()
    private val localProxyServer = LocalDohProxyServer(dohResolver)

    private var progressTrackingJob: Job? = null
    private var secondsWatched: Int = 0
    private var lastRemoteUpsertTime: Long = 0L

    // Feature 4: Trusted hosts whitelist tracking
    private val trustedHosts = mutableSetOf<String>()
    private var hasUserTouchedPage = false
    private val redirectBlockTimestamps = ArrayDeque<Long>()

    init {
        observeSettings()
        loadEpisodeData()
        checkInitialBravePrompt()
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
        viewModelScope.launch {
            settingsRepository.useSecureDns.collect { secureDns ->
                _uiState.update { it.copy(useSecureDns = secureDns) }
            }
        }
        viewModelScope.launch {
            settingsRepository.stayOnOriginalSite.collect { stay ->
                _uiState.update { it.copy(stayOnOriginalSite = stay) }
            }
        }
        viewModelScope.launch {
            settingsRepository.adCleanerEnabled.collect { cleaner ->
                _uiState.update { it.copy(adCleanerEnabled = cleaner) }
            }
        }
        viewModelScope.launch {
            settingsRepository.popupGatedPlayers.collect { gated ->
                _uiState.update { it.copy(popupGatedPlayers = gated) }
            }
        }
        viewModelScope.launch {
            settingsRepository.disabledShieldHosts.collect { disabledHosts ->
                _uiState.update { it.copy(disabledShieldHosts = disabledHosts) }
            }
        }
        if (authRepository != null) {
            viewModelScope.launch {
                authRepository.isLoggedIn.collect { loggedIn ->
                    _uiState.update { it.copy(isUserLoggedIn = loggedIn) }
                }
            }
        }
    }

    private fun checkInitialBravePrompt() {
        viewModelScope.launch {
            delay(1500)
            triggerBraveRecommendationIfNeeded("First visit")
        }
    }

    fun startLocalProxy(): Int {
        val assignedPort = localProxyServer.start()
        _uiState.update { it.copy(isProxyActive = assignedPort > 0, proxyPort = assignedPort) }
        return assignedPort
    }

    fun stopLocalProxy() {
        localProxyServer.stop()
        _uiState.update { it.copy(isProxyActive = false, proxyPort = 0) }
    }

    fun handleDnsError() {
        val state = _uiState.value
        if (!state.autoRetryAttempted && !state.isProxyActive) {
            viewModelScope.launch {
                settingsRepository.setUseSecureDns(true)
                _uiState.update { it.copy(autoRetryAttempted = true, hasError = false, errorMessage = null) }
            }
        } else {
            _uiState.update {
                it.copy(
                    hasError = true,
                    errorMessage = "DNS lookup failed (net::ERR_NAME_NOT_RESOLVED). Check internet connection or try next server."
                )
            }
            triggerBraveRecommendationIfNeeded("Load error")
        }
    }

    fun setUseSecureDns(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseSecureDns(enabled)
        }
    }

    fun refreshDnsStatus(context: Context) {
        val status = DnsHelper.getPrivateDnsStatus(context)
        _uiState.update { it.copy(dnsStatus = status) }
    }

    private fun loadEpisodeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasError = false) }
            try {
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

                resetTrustedHosts(firstSource?.url)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentEpisode = episode,
                        selectedSeasonNumber = episode.seasonNumber,
                        sources = sources,
                        currentSource = firstSource,
                        currentSourceIndex = 0,
                        currentPageUrl = firstSource?.url,
                        hasError = firstSource == null,
                        errorMessage = if (firstSource == null) "No sources available yet" else null
                    )
                }

                startFallbackProgressTracking(episode)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, hasError = true, errorMessage = e.message) }
            }
        }
    }

    fun selectSource(source: EpisodeSource) {
        val index = _uiState.value.sources.indexOf(source)
        resetTrustedHosts(source.url)
        _uiState.update {
            it.copy(
                currentSource = source,
                currentSourceIndex = if (index >= 0) index else 0,
                currentPageUrl = source.url,
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
            resetTrustedHosts(nextSource.url)
            _uiState.update {
                it.copy(
                    currentSource = nextSource,
                    currentSourceIndex = nextIndex,
                    currentPageUrl = nextSource.url,
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
            triggerBraveRecommendationIfNeeded("All servers failed")
        }
    }

    fun selectEpisode(episode: Episode) {
        if (_uiState.value.currentEpisode?.id == episode.id) return
        saveCurrentProgress()
        secondsWatched = 0
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

    fun previousEpisode() {
        val episodes = _uiState.value.episodes
        val currentEp = _uiState.value.currentEpisode ?: return
        val prev = episodes.find {
            it.seasonNumber == currentEp.seasonNumber && it.episodeNumber == currentEp.episodeNumber - 1
        }
        if (prev != null) {
            selectEpisode(prev)
        }
    }

    // =========================================================================
    // FEATURE 4: STAY ON ORIGINAL SITE (AUTO-RETURN & REDIRECT BLOCKING)
    // =========================================================================

    private fun resetTrustedHosts(url: String?) {
        trustedHosts.clear()
        hasUserTouchedPage = false
        if (!url.isNullOrBlank()) {
            val host = Uri.parse(url).host?.lowercase()
            if (!host.isNullOrBlank()) {
                trustedHosts.add(host)
                // Also add apex registrable domain (e.g. embed.streamtape.com -> streamtape.com)
                val parts = host.split(".")
                if (parts.size >= 2) {
                    val apex = "${parts[parts.size - 2]}.${parts.last()}"
                    trustedHosts.add(apex)
                }
            }
        }
    }

    fun addInitialRedirectHost(url: String?) {
        if (!hasUserTouchedPage && !url.isNullOrBlank()) {
            val host = Uri.parse(url).host?.lowercase()
            if (!host.isNullOrBlank()) {
                trustedHosts.add(host)
                val parts = host.split(".")
                if (parts.size >= 2) {
                    trustedHosts.add("${parts[parts.size - 2]}.${parts.last()}")
                }
                Log.d("ReeloNav", "Added initial trusted redirect host: $host")
            }
        }
    }

    fun onUserInteractedWithWebView() {
        hasUserTouchedPage = true
    }

    fun isHostTrusted(host: String?): Boolean {
        if (host.isNullOrBlank()) return true
        val cleanHost = host.lowercase().trim()

        // If shield disabled globally or for this specific host, allow
        if (!_uiState.value.stayOnOriginalSite) return true
        if (_uiState.value.disabledShieldHosts.contains(cleanHost)) return true
        if (_uiState.value.allowOnceActive) return true

        if (trustedHosts.contains(cleanHost)) return true

        for (trusted in trustedHosts) {
            if (cleanHost.endsWith(".$trusted") || cleanHost == trusted) {
                return true
            }
        }
        return false
    }

    fun getTrustedHosts(): Set<String> = trustedHosts.toSet()

    fun onAdRedirectBlocked(attemptedUrl: String) {
        val now = System.currentTimeMillis()
        redirectBlockTimestamps.addLast(now)
        // Purge timestamps older than 60 seconds
        while (redirectBlockTimestamps.isNotEmpty() && now - redirectBlockTimestamps.first() > 60_000) {
            redirectBlockTimestamps.removeFirst()
        }

        val totalBlocked = _uiState.value.blockedRedirectsCount + 1
        _uiState.update {
            it.copy(
                blockedRedirectsCount = totalBlocked,
                blockedRedirectToast = "Ad redirect blocked"
            )
        }

        Log.d("ReeloNav", "Blocked ad redirect to: $attemptedUrl (Blocks in 60s: ${redirectBlockTimestamps.size})")

        // Trigger Brave prompt if >= 10 redirects blocked in 60s
        if (redirectBlockTimestamps.size >= 10) {
            triggerBraveRecommendationIfNeeded("Redirect storm (>=10 in 60s)")
        }
    }

    fun triggerAllowOnce() {
        viewModelScope.launch {
            _uiState.update { it.copy(allowOnceActive = true, blockedRedirectToast = "Redirect allowed for 5s") }
            delay(5000)
            _uiState.update { it.copy(allowOnceActive = false) }
        }
    }

    fun dismissBlockedToast() {
        _uiState.update { it.copy(blockedRedirectToast = null) }
    }

    fun resetBlockedCount() {
        _uiState.update { it.copy(blockedRedirectsCount = 0) }
    }

    fun toggleStayOnOriginalSite(stay: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStayOnOriginalSite(stay)
        }
    }

    fun toggleAdCleaner(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAdCleanerEnabled(enabled)
        }
    }

    fun togglePopupGatedPlayers(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPopupGatedPlayers(enabled)
        }
    }

    fun toggleShieldForCurrentHost() {
        val currentHost = Uri.parse(_uiState.value.currentPageUrl ?: _uiState.value.currentSource?.url ?: "").host
        if (!currentHost.isNullOrBlank()) {
            viewModelScope.launch {
                settingsRepository.toggleShieldForHost(currentHost.lowercase())
            }
        }
    }

    // =========================================================================
    // FEATURE 2: RECOMMENDED BROWSER (BRAVE)
    // =========================================================================

    private fun triggerBraveRecommendationIfNeeded(reason: String) {
        viewModelScope.launch {
            val dontShow = settingsRepository.braveDontShowAgain.first()
            if (dontShow) return@launch

            val lastShown = settingsRepository.getBraveLastShownTime()
            val now = System.currentTimeMillis()
            // Max 1 show per 24 hours
            if (now - lastShown >= 24 * 60 * 60 * 1000L) {
                settingsRepository.updateBraveLastShownTime(now)
                _uiState.update { it.copy(showBraveDialog = true) }
                Log.d("ReeloNav", "Brave recommendation triggered: $reason")
            }
        }
    }

    fun setBraveDontShowAgain() {
        viewModelScope.launch {
            settingsRepository.setBraveDontShowAgain(true)
        }
    }

    fun dismissBraveDialog() {
        _uiState.update { it.copy(showBraveDialog = false) }
    }

    // =========================================================================
    // FEATURE 7: REAL HTML5 VIDEO RESUME PLAYBACK
    // =========================================================================

    fun onVideoReady(durationSec: Int) {
        val ep = _uiState.value.currentEpisode ?: return
        viewModelScope.launch {
            val saved = settingsRepository.getEpisodePlayback(ep.id)
            if (saved != null) {
                val (pos, _) = saved
                if (pos > 10 && (durationSec <= 0 || pos < durationSec - 30)) {
                    val minutes = pos / 60
                    val seconds = pos % 60
                    val timeFormatted = "%d:%02d".format(minutes, seconds)

                    _uiState.update {
                        it.copy(
                            resumeChipText = "Resumed from $timeFormatted · Start over",
                            showResumeChip = true,
                            targetSeekSeconds = pos,
                            seekCommandId = System.currentTimeMillis()
                        )
                    }
                    Log.d("ReeloResume", "Handshake: restoring episode ${ep.id} at ${pos}s")

                    // Auto hide resume chip after 5s
                    delay(5000)
                    _uiState.update { it.copy(showResumeChip = false) }
                }
            }
        }
    }

    fun onVideoTimeUpdate(currentTimeSec: Int, durationSec: Int, ended: Boolean) {
        val ep = _uiState.value.currentEpisode ?: return
        secondsWatched = currentTimeSec
        val duration = if (durationSec > 0) durationSec else ep.durationSeconds ?: 1440
        val isCompleted = ended || (duration > 0 && currentTimeSec >= (duration * 0.90))

        if (isCompleted && !_uiState.value.isCompleted) {
            _uiState.update {
                it.copy(
                    isCompleted = true,
                    watchedEpisodeIds = it.watchedEpisodeIds + ep.id
                )
            }
        }

        // Save locally to DataStore every 5s
        viewModelScope.launch {
            settingsRepository.saveEpisodePlayback(ep.id, seriesId, currentTimeSec, duration)
        }

        // Upsert to Supabase every ~30 seconds or if completed
        val now = System.currentTimeMillis()
        if (now - lastRemoteUpsertTime >= 30_000 || isCompleted) {
            lastRemoteUpsertTime = now
            viewModelScope.launch {
                userActionsRepository.upsertWatchProgress(
                    seriesId = seriesId,
                    episodeId = ep.id,
                    positionSec = currentTimeSec,
                    durationSec = duration,
                    completed = isCompleted
                )
            }
        }
    }

    fun onNoVideoFound() {
        Log.d("ReeloResume", "no video element found")
    }

    fun startOverPlayback() {
        val ep = _uiState.value.currentEpisode ?: return
        _uiState.update {
            it.copy(
                showResumeChip = false,
                targetSeekSeconds = 0,
                seekCommandId = System.currentTimeMillis()
            )
        }
        viewModelScope.launch {
            settingsRepository.saveEpisodePlayback(ep.id, seriesId, 0, ep.durationSeconds ?: 0)
        }
    }

    fun dismissResumeChip() {
        _uiState.update { it.copy(showResumeChip = false) }
    }

    fun updateCurrentPageUrl(url: String?) {
        if (!url.isNullOrBlank()) {
            _uiState.update { it.copy(currentPageUrl = url) }
        }
    }

    // =========================================================================
    // FALLBACK TIMED PROGRESS TRACKING
    // =========================================================================

    private fun startFallbackProgressTracking(episode: Episode) {
        progressTrackingJob?.cancel()
        progressTrackingJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (secondsWatched == 0) {
                    // Only increment fallback if real HTML5 video updates haven't taken over
                    val duration = episode.durationSeconds ?: 1440
                    userActionsRepository.upsertWatchProgress(
                        seriesId = seriesId,
                        episodeId = episode.id,
                        positionSec = 30,
                        durationSec = duration,
                        completed = false
                    )
                }
            }
        }
    }

    fun saveCurrentProgress() {
        val ep = _uiState.value.currentEpisode ?: return
        viewModelScope.launch {
            val duration = ep.durationSeconds ?: 1440
            val isCompleted = secondsWatched >= (duration * 0.85) || _uiState.value.isCompleted
            settingsRepository.saveEpisodePlayback(ep.id, seriesId, secondsWatched, duration)
            userActionsRepository.upsertWatchProgress(
                seriesId = seriesId,
                episodeId = ep.id,
                positionSec = secondsWatched,
                durationSec = duration,
                completed = isCompleted
            )
        }
    }

    // =========================================================================
    // UI DIALOG & SHEET TOGGLES
    // =========================================================================

    fun toggleOpenInBrowserSheet(show: Boolean) {
        _uiState.update { it.copy(showOpenInBrowserSheet = show) }
    }

    fun toggleSecureDnsSheet(show: Boolean) {
        _uiState.update { it.copy(showSecureDnsSheet = show) }
    }

    fun toggleShieldSheet(show: Boolean) {
        _uiState.update { it.copy(showShieldSheet = show) }
    }

    fun toggleServersSheet(show: Boolean) {
        _uiState.update { it.copy(showServersSheet = show) }
    }

    fun toggleReportDialog(show: Boolean) {
        _uiState.update { it.copy(showReportDialog = show) }
    }

    fun toggleRailsExpanded() {
        val next = !_uiState.value.railsExpanded
        viewModelScope.launch {
            settingsRepository.setRailsExpanded(next)
        }
    }

    fun toggleUserAgentMode() {
        val next = if (_uiState.value.userAgentMode == UserAgentMode.MOBILE) UserAgentMode.DESKTOP else UserAgentMode.MOBILE
        _uiState.update { it.copy(userAgentMode = next) }
    }

    fun toggleLandscapeMode() {
        _uiState.update { it.copy(isLandscapeMode = !it.isLandscapeMode) }
    }

    fun setLandscapeMode(landscape: Boolean) {
        _uiState.update { it.copy(isLandscapeMode = landscape) }
    }

    fun setWebViewProgress(progress: Int) {
        _uiState.update { it.copy(webViewProgress = progress) }
    }

    fun onWebPageError(error: String) {
        _uiState.update { it.copy(hasError = true, errorMessage = error) }
        triggerBraveRecommendationIfNeeded("Web page error: $error")
    }

    fun toggleLike(x: Float = 0f, y: Float = 0f) {
        val ep = _uiState.value.currentEpisode ?: return
        val currentLiked = _uiState.value.isLiked
        val nextLiked = !currentLiked
        _uiState.update {
            it.copy(
                isLiked = nextLiked,
                heartAnimationCoordinates = if (nextLiked && (x > 0 || y > 0)) Pair(x, y) else null
            )
        }
    }

    fun dismissHeartAnimation() {
        _uiState.update { it.copy(heartAnimationCoordinates = null) }
    }

    fun reportCurrentSource(reason: String, details: String?) {
        val source = _uiState.value.currentSource ?: return
        viewModelScope.launch {
            val success = sourceRepository.reportSource(source.id, reason, details).first()
            _uiState.update {
                it.copy(
                    showReportDialog = false,
                    reportSubmittedToast = if (success) "Report submitted. Thank you!" else "Failed to submit report"
                )
            }
        }
    }

    fun dismissReportToast() {
        _uiState.update { it.copy(reportSubmittedToast = null) }
    }

    fun toggleCommentsSheet(show: Boolean) {
        _uiState.update { it.copy(showCommentsSheet = show) }
        if (show) {
            loadComments()
        }
    }

    fun loadComments() {
        val commentRepo = commentRepository ?: return
        viewModelScope.launch {
            val currentEpId = _uiState.value.currentEpisode?.id
            commentRepo.getComments(seriesId, currentEpId).collect { list ->
                _uiState.update { it.copy(comments = list) }
            }
        }
    }

    fun postComment(content: String) {
        val commentRepo = commentRepository ?: return
        val authRepo = authRepository ?: return
        viewModelScope.launch {
            val profile = authRepo.currentUserProfile.first()
            val author = profile?.let { com.example.data.model.CommentAuthor(username = it.username, displayName = it.displayName, avatarUrl = it.avatarUrl) }
            val newComment = com.example.data.model.SeriesComment(
                id = "temp_${System.currentTimeMillis()}",
                seriesId = seriesId,
                episodeId = _uiState.value.currentEpisode?.id,
                userId = profile?.id ?: "user",
                content = content,
                likesCount = 0,
                isLikedByMe = false,
                profiles = author
            )
            _uiState.update { it.copy(comments = listOf(newComment) + it.comments) }
            commentRepo.postComment(seriesId, _uiState.value.currentEpisode?.id, null, content).first()
        }
    }

    fun toggleCommentLike(comment: com.example.data.model.SeriesComment) {
        val commentRepo = commentRepository ?: return
        val updatedLikedState = !comment.isLikedByMe
        val updatedCount = if (updatedLikedState) comment.likesCount + 1 else (comment.likesCount - 1).coerceAtLeast(0)

        _uiState.update { state ->
            state.copy(
                comments = state.comments.map {
                    if (it.id == comment.id) it.copy(isLikedByMe = updatedLikedState, likesCount = updatedCount) else it
                }
            )
        }

        viewModelScope.launch {
            commentRepo.likeComment(comment.id, updatedLikedState).first()
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

    override fun onCleared() {
        super.onCleared()
        progressTrackingJob?.cancel()
        localProxyServer.stop()
    }

    companion object {
        fun provideFactory(
            seriesId: String,
            initialEpisodeId: String?,
            seriesRepo: SeriesRepository,
            episodeRepo: EpisodeRepository,
            sourceRepo: SourceRepository,
            userActionsRepo: UserActionsRepository,
            settingsRepo: SettingsRepository,
            commentRepo: CommentRepository? = null,
            authRepo: AuthRepository? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WatchViewModel(
                    seriesId,
                    initialEpisodeId,
                    seriesRepo,
                    episodeRepo,
                    sourceRepo,
                    userActionsRepo,
                    settingsRepo,
                    commentRepo,
                    authRepo
                ) as T
            }
        }
    }
}
