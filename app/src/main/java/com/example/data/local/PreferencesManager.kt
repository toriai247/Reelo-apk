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

    val allowPopupsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_ALLOW_POPUPS] ?: false }

    suspend fun setAllowPopups(allow: Boolean) {
        context.dataStore.edit { it[KEY_ALLOW_POPUPS] = allow }
    }

    val useSecureDnsFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_USE_SECURE_DNS] ?: true }

    suspend fun setUseSecureDns(enabled: Boolean) {
        context.dataStore.edit { it[KEY_USE_SECURE_DNS] = enabled }
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

    fun getSupabaseUrl(): String {
        return BuildConfig.SUPABASE_URL.ifBlank { "https://ffbmozwsasbhevtehbit.supabase.co" }
    }

    fun getSupabaseAnonKey(): String {
        return BuildConfig.SUPABASE_ANON_KEY
    }
}
