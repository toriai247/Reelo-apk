package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.data.model.Profile
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reelo_prefs")

class PreferencesManager(private val context: Context) {

    private val KEY_THEME = stringPreferencesKey("theme_mode")
    private val KEY_ALLOW_POPUPS = booleanPreferencesKey("allow_popups")
    private val KEY_VIEWER_KEY = stringPreferencesKey("viewer_key")
    private val KEY_RAILS_EXPANDED = booleanPreferencesKey("rails_expanded")
    private val KEY_RECENT_SEARCHES = stringPreferencesKey("recent_searches")
    private val KEY_AUTH_TOKEN = stringPreferencesKey("auth_token")
    private val KEY_USER_ID = stringPreferencesKey("user_id")
    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
    private val KEY_AVATAR_URL = stringPreferencesKey("avatar_url")
    private val KEY_CUSTOM_SUPABASE_URL = stringPreferencesKey("custom_supabase_url")
    private val KEY_CUSTOM_SUPABASE_ANON_KEY = stringPreferencesKey("custom_supabase_anon_key")

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        val mode = prefs[KEY_THEME] ?: "SYSTEM"
        try {
            ThemeMode.valueOf(mode)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME] = mode.name }
    }

    private val KEY_USE_SECURE_DNS = booleanPreferencesKey("use_secure_dns")
    private val KEY_STAY_ON_ORIGINAL_SITE = booleanPreferencesKey("stay_on_original_site")
    private val KEY_AD_CLEANER_ENABLED = booleanPreferencesKey("ad_cleaner_enabled")
    private val KEY_POPUP_GATED_PLAYERS = booleanPreferencesKey("popup_gated_players")
    private val KEY_BRAVE_DONT_SHOW_AGAIN = booleanPreferencesKey("brave_dont_show_again")
    private val KEY_BRAVE_LAST_SHOWN_TIME = androidx.datastore.preferences.core.longPreferencesKey("brave_last_shown_time")
    private val KEY_DISABLED_SHIELD_HOSTS = stringPreferencesKey("disabled_shield_hosts")

    val allowPopupsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALLOW_POPUPS] ?: false }

    suspend fun setAllowPopups(allow: Boolean) {
        context.dataStore.edit { it[KEY_ALLOW_POPUPS] = allow }
    }

    // Default to false to avoid ERR_TIMED_OUT with local proxy
    val useSecureDnsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_USE_SECURE_DNS] ?: false }

    suspend fun setUseSecureDns(enabled: Boolean) {
        context.dataStore.edit { it[KEY_USE_SECURE_DNS] = enabled }
    }

    val stayOnOriginalSiteFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_STAY_ON_ORIGINAL_SITE] ?: true }

    suspend fun setStayOnOriginalSite(stay: Boolean) {
        context.dataStore.edit { it[KEY_STAY_ON_ORIGINAL_SITE] = stay }
    }

    val adCleanerEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AD_CLEANER_ENABLED] ?: true }

    suspend fun setAdCleanerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AD_CLEANER_ENABLED] = enabled }
    }

    val popupGatedPlayersFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_POPUP_GATED_PLAYERS] ?: false }

    suspend fun setPopupGatedPlayers(enabled: Boolean) {
        context.dataStore.edit { it[KEY_POPUP_GATED_PLAYERS] = enabled }
    }

    val braveDontShowAgainFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_BRAVE_DONT_SHOW_AGAIN] ?: false }

    suspend fun setBraveDontShowAgain(dontShow: Boolean) {
        context.dataStore.edit { it[KEY_BRAVE_DONT_SHOW_AGAIN] = dontShow }
    }

    suspend fun getBraveLastShownTime(): Long {
        return context.dataStore.data.first()[KEY_BRAVE_LAST_SHOWN_TIME] ?: 0L
    }

    suspend fun updateBraveLastShownTime(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[KEY_BRAVE_LAST_SHOWN_TIME] = timestamp }
    }

    val disabledShieldHostsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_DISABLED_SHIELD_HOSTS] ?: ""
        if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun toggleShieldForHost(host: String): Boolean {
        var disabled = false
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_DISABLED_SHIELD_HOSTS] ?: ""
            val hosts = if (raw.isBlank()) mutableSetOf() else raw.split(",").filter { it.isNotBlank() }.toMutableSet()
            if (hosts.contains(host)) {
                hosts.remove(host)
                disabled = false
            } else {
                hosts.add(host)
                disabled = true
            }
            prefs[KEY_DISABLED_SHIELD_HOSTS] = hosts.joinToString(",")
        }
        return disabled
    }

    suspend fun saveEpisodePlayback(episodeId: String, seriesId: String, positionSec: Int, durationSec: Int) {
        val key = stringPreferencesKey("playback_$episodeId")
        val seriesKey = stringPreferencesKey("series_last_ep_$seriesId")
        val value = "$positionSec:$durationSec:$seriesId:${System.currentTimeMillis()}"
        context.dataStore.edit { prefs ->
            prefs[key] = value
            prefs[seriesKey] = "$episodeId:$positionSec:$durationSec:${System.currentTimeMillis()}"
        }
    }

    suspend fun getEpisodePlayback(episodeId: String): Pair<Int, Int>? {
        val key = stringPreferencesKey("playback_$episodeId")
        val raw = context.dataStore.data.first()[key] ?: return null
        val parts = raw.split(":")
        if (parts.size >= 2) {
            val pos = parts[0].toIntOrNull() ?: 0
            val dur = parts[1].toIntOrNull() ?: 0
            return Pair(pos, dur)
        }
        return null
    }

    fun getSeriesLastPlaybackFlow(seriesId: String): Flow<Triple<String, Int, Int>?> {
        val seriesKey = stringPreferencesKey("series_last_ep_$seriesId")
        return context.dataStore.data.map { prefs ->
            val raw = prefs[seriesKey] ?: return@map null
            val parts = raw.split(":")
            if (parts.size >= 3) {
                val epId = parts[0]
                val pos = parts[1].toIntOrNull() ?: 0
                val dur = parts[2].toIntOrNull() ?: 0
                Triple(epId, pos, dur)
            } else null
        }
    }

    val railsExpandedFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_RAILS_EXPANDED] ?: true }

    suspend fun setRailsExpanded(expanded: Boolean) {
        context.dataStore.edit { it[KEY_RAILS_EXPANDED] = expanded }
    }

    suspend fun getOrCreateViewerKey(): String {
        val current = context.dataStore.data.first()[KEY_VIEWER_KEY]
        if (!current.isNullOrBlank()) return current
        val newKey = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_VIEWER_KEY] = newKey }
        return newKey
    }

    val recentSearchesFlow: Flow<List<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_RECENT_SEARCHES] ?: ""
        if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        val current = context.dataStore.data.first()[KEY_RECENT_SEARCHES] ?: ""
        val list = current.split("\n").filter { it.isNotBlank() && !it.equals(trimmed, ignoreCase = true) }.toMutableList()
        list.add(0, trimmed)
        val trimmedList = list.take(10)
        context.dataStore.edit { it[KEY_RECENT_SEARCHES] = trimmedList.joinToString("\n") }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it.remove(KEY_RECENT_SEARCHES) }
    }

    val authTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_AUTH_TOKEN] }
    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }

    val userProfileFlow: Flow<Profile?> = context.dataStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID] ?: return@map null
        Profile(
            id = id,
            username = prefs[KEY_USERNAME] ?: "",
            displayName = prefs[KEY_DISPLAY_NAME] ?: "",
            avatarUrl = prefs[KEY_AVATAR_URL]
        )
    }

    suspend fun saveAuthSession(token: String, userId: String, username: String, displayName: String, avatarUrl: String?) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTH_TOKEN] = token
            prefs[KEY_USER_ID] = userId
            prefs[KEY_USERNAME] = username
            prefs[KEY_DISPLAY_NAME] = displayName
            if (avatarUrl != null) prefs[KEY_AVATAR_URL] = avatarUrl else prefs.remove(KEY_AVATAR_URL)
        }
    }

    suspend fun clearAuthSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_AUTH_TOKEN)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USERNAME)
            prefs.remove(KEY_DISPLAY_NAME)
            prefs.remove(KEY_AVATAR_URL)
        }
    }

    private val KEY_WATCHLIST_IDS = stringPreferencesKey("local_watchlist_ids")

    val localWatchlistIdsFlow: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_WATCHLIST_IDS] ?: ""
        if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun toggleLocalWatchlist(seriesId: String): Boolean {
        var isBookmarked = false
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_WATCHLIST_IDS] ?: ""
            val currentSet = if (raw.isBlank()) mutableSetOf() else raw.split(",").filter { it.isNotBlank() }.toMutableSet()
            if (currentSet.contains(seriesId)) {
                currentSet.remove(seriesId)
                isBookmarked = false
            } else {
                currentSet.add(seriesId)
                isBookmarked = true
            }
            prefs[KEY_WATCHLIST_IDS] = currentSet.joinToString(",")
        }
        return isBookmarked
    }

    suspend fun removeFromLocalWatchlist(seriesId: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[KEY_WATCHLIST_IDS] ?: ""
            val currentSet = if (raw.isBlank()) mutableSetOf() else raw.split(",").filter { it.isNotBlank() }.toMutableSet()
            currentSet.remove(seriesId)
            prefs[KEY_WATCHLIST_IDS] = currentSet.joinToString(",")
        }
    }

    fun getSupabaseUrl(): String {
        return BuildConfig.SUPABASE_URL.ifBlank { "https://ffbmozwsasbhevtehbit.supabase.co" }
    }

    fun getSupabaseAnonKey(): String {
        return BuildConfig.SUPABASE_ANON_KEY
    }
}
