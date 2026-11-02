package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ContentCategory
import com.example.data.model.ContinueWatchingItem
import com.example.data.model.HomeShelfItem
import com.example.data.repository.HomeRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val categories: List<ContentCategory> = emptyList(),
    val selectedCategorySlug: String = "all",
    val shelves: List<HomeShelfItem> = emptyList(),
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val currentTheme: ThemeMode = ThemeMode.SYSTEM,
    val error: String? = null
)

class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadData()
        observeTheme()
    }

    private fun observeTheme() {
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(currentTheme = mode) }
            }
        }
    }

    fun loadData(category: String? = _uiState.value.selectedCategorySlug) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Load categories
                val cats = homeRepository.getCategories().first()

                // Load shelves
                val shelves = homeRepository.getSeriesHome(category).first()

                // Load continue watching
                val cw = homeRepository.getContinueWatching().first()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = cats,
                        shelves = shelves,
                        continueWatching = cw,
                        selectedCategorySlug = category ?: "all"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load catalog") }
            }
        }
    }

    fun selectCategory(slug: String) {
        if (_uiState.value.selectedCategorySlug == slug) return
        _uiState.update { it.copy(selectedCategorySlug = slug) }
        loadData(slug)
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val nextMode = when (_uiState.value.currentTheme) {
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.LIGHT
                ThemeMode.SYSTEM -> ThemeMode.DARK
            }
            settingsRepository.setThemeMode(nextMode)
        }
    }

    companion object {
        fun provideFactory(
            homeRepo: HomeRepository,
            settingsRepo: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(homeRepo, settingsRepo) as T
            }
        }
    }
}
