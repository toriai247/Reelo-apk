package com.example.di

import android.content.Context
import com.example.data.local.PreferencesManager
import com.example.data.remote.SupabaseApiClient
import com.example.data.repository.*

class AppContainer(context: Context) {
    val preferencesManager = PreferencesManager(context)
    val apiClient = SupabaseApiClient(preferencesManager)

    val homeRepository = HomeRepository(apiClient)
    val seriesRepository = SeriesRepository(apiClient)
    val episodeRepository = EpisodeRepository(apiClient)
    val sourceRepository = SourceRepository(apiClient, preferencesManager)
    val userActionsRepository = UserActionsRepository(apiClient, preferencesManager)
    val commentRepository = CommentRepository(apiClient, preferencesManager)
    val authRepository = AuthRepository(apiClient, preferencesManager)
    val settingsRepository = SettingsRepository(preferencesManager)
}
