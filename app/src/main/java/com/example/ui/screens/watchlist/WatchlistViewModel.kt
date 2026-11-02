package com.example.ui.screens.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.WatchlistItem
import com.example.data.repository.AuthRepository
import com.example.data.repository.UserActionsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WatchlistUiState(
    val isLoading: Boolean = true,
    val items: List<WatchlistItem> = emptyList(),
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

class WatchlistViewModel(
    private val userActionsRepository: UserActionsRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        observeWatchlist()
    }

    private fun observeWatchlist() {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
            }
        }
        viewModelScope.launch {
            userActionsRepository.localWatchlistIdsFlow.collect {
                loadWatchlist()
            }
        }
    }

    fun loadWatchlist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                userActionsRepository.getWatchlist().collect { list ->
                    _uiState.update { it.copy(isLoading = false, items = list) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun removeItem(seriesId: String) {
        viewModelScope.launch {
            val current = _uiState.value.items
            _uiState.update { it.copy(items = current.filter { item -> item.seriesId != seriesId }) }
            userActionsRepository.removeFromWatchlist(seriesId).first()
        }
    }

    companion object {
        fun provideFactory(
            userActionsRepo: UserActionsRepository,
            authRepo: AuthRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return WatchlistViewModel(userActionsRepo, authRepo) as T
            }
        }
    }
}
