package com.example.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ContentCategory
import com.example.data.model.SeriesCard
import com.example.data.repository.HomeRepository
import com.example.data.repository.SeriesRepository
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val selectedCategorySlug: String = "all",
    val categories: List<ContentCategory> = emptyList(),
    val results: List<SeriesCard> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null
)

class SearchViewModel(
    private val seriesRepository: SeriesRepository,
    private val homeRepository: HomeRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchDebounceJob: Job? = null

    init {
        loadCategoriesAndRecents()
    }

    private fun loadCategoriesAndRecents() {
        viewModelScope.launch {
            homeRepository.getCategories().collect { cats ->
                _uiState.update { it.copy(categories = cats) }
            }
        }
        viewModelScope.launch {
            settingsRepository.recentSearches.collect { recents ->
                _uiState.update { it.copy(recentSearches = recents) }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }

        searchDebounceJob?.cancel()
        if (newQuery.trim().length >= 2) {
            searchDebounceJob = viewModelScope.launch {
                delay(400) // 400ms debounce
                executeSearch(newQuery.trim(), _uiState.value.selectedCategorySlug)
            }
        } else {
            _uiState.update { it.copy(results = emptyList(), hasSearched = false, isLoading = false) }
        }
    }

    fun selectCategory(slug: String) {
        _uiState.update { it.copy(selectedCategorySlug = slug) }
        val query = _uiState.value.query.trim()
        if (query.length >= 2) {
            executeSearch(query, slug)
        }
    }

    fun executeSearch(query: String, categorySlug: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, hasSearched = true, error = null) }
            try {
                settingsRepository.addRecentSearch(query)
                val catParam = if (categorySlug == "all") null else categorySlug
                val results = seriesRepository.searchSeries(query, catParam, limit = 20, offset = 0).first()
                _uiState.update { it.copy(isLoading = false, results = results) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            settingsRepository.clearRecentSearches()
        }
    }

    companion object {
        fun provideFactory(
            seriesRepo: SeriesRepository,
            homeRepo: HomeRepository,
            settingsRepo: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(seriesRepo, homeRepo, settingsRepo) as T
            }
        }
    }
}
