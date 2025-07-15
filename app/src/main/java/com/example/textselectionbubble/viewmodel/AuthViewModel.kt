package com.example.textselectionbubble.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.textselectionbubble.data.UserSessionManager
import com.example.textselectionbubble.data.network.ApiResult
import com.example.textselectionbubble.data.network.NetworkModule
import com.example.textselectionbubble.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = UserSessionManager(application.applicationContext)
    private val authRepository = AuthRepository(NetworkModule.apiService)

    data class UiState(
        val email: String = "",
        val password: String = "",
        val isSignUp: Boolean = false,
        val isLoading: Boolean = false,
        val errorMessage: String = "",
        val isSuccess: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value, errorMessage = "")
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, errorMessage = "")
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isSignUp = !_uiState.value.isSignUp,
            errorMessage = "",
            email = "",
            password = ""
        )
    }

    fun authenticate(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.email.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Email is required")
            return
        }

        if (state.password.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Password is required")
            return
        }

        if (state.password.length < 6) {
            _uiState.value = state.copy(errorMessage = "Password must be at least 6 characters")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.value = state.copy(errorMessage = "Please enter a valid email address")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = "")

        viewModelScope.launch {
            try {
                if (state.isSignUp) {
                    handleSignUp(state.email, state.password, onSuccess)
                } else {
                    handleSignIn(state.email, state.password, onSuccess)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "An unexpected error occurred: ${e.localizedMessage}"
                )
            }
        }
    }

    private suspend fun handleSignIn(email: String, password: String, onSuccess: () -> Unit) {
        when (val result = authRepository.signIn(email, password)) {
            is ApiResult.Success -> {
                sessionManager.saveUserSession(
                    accessToken = result.data.accessToken,
                    refreshToken = result.data.refreshToken,
                    user = result.data.user
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSuccess = true
                )

                onSuccess()
            }

            is ApiResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message
                )
            }

            is ApiResult.Loading -> {
                // Already handled above
            }
        }
    }

    private suspend fun handleSignUp(email: String, password: String, onSuccess: () -> Unit) {
        when (val result = authRepository.signUp(email, password)) {
            is ApiResult.Success -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSuccess = true,
                    errorMessage = "Account created successfully! Please sign in.",
                    isSignUp = false,
                    password = ""
                )
            }

            is ApiResult.Error -> {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.message
                )
            }

            is ApiResult.Loading -> {
                // Already handled above
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = "")
    }
}