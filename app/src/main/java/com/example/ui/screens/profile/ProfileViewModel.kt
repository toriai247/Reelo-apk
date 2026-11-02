package com.example.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Profile
import com.example.data.repository.AuthRepository
import com.example.data.repository.SettingsRepository
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileUiState(
    val isLoggedIn: Boolean = false,
    val profile: Profile? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val allowPopups: Boolean = false,
    val useSecureDns: Boolean = true,
    val showAuthDialog: Boolean = false,
    val isAuthLoading: Boolean = false,
    val authError: String? = null,
    val message: String? = null
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            authRepository.isLoggedIn.collect { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
            }
        }
        viewModelScope.launch {
            authRepository.currentUserProfile.collect { prof ->
                _uiState.update { it.copy(profile = prof) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
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
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAllowPopups(allow: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAllowPopups(allow)
        }
    }

    fun setUseSecureDns(useSecure: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseSecureDns(useSecure)
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            settingsRepository.clearRecentSearches()
            _uiState.update { it.copy(message = "Search history cleared") }
        }
    }

    fun showAuthDialog(show: Boolean) {
        _uiState.update { it.copy(showAuthDialog = show, authError = null) }
    }

    fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = authRepository.signIn(email, pass)
            result.onSuccess {
                _uiState.update { it.copy(isAuthLoading = false, showAuthDialog = false) }
            }.onFailure { err ->
                _uiState.update { it.copy(isAuthLoading = false, authError = err.message ?: "Sign in failed") }
            }
        }
    }

    fun signUp(email: String, pass: String, username: String, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthLoading = true, authError = null) }
            val result = authRepository.signUp(email, pass, username, displayName)
            result.onSuccess {
                _uiState.update { it.copy(isAuthLoading = false, showAuthDialog = false, message = "Account created!") }
            }.onFailure { err ->
                _uiState.update { it.copy(isAuthLoading = false, authError = err.message ?: "Sign up failed") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { it.copy(message = "Logged out") }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun provideFactory(
            authRepo: AuthRepository,
            settingsRepo: SettingsRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProfileViewModel(authRepo, settingsRepo) as T
            }
        }
    }
}
