// viewmodel/AuthViewModel.kt
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
        val isSuccess: Boolean = false,
        val needsEmailVerification: Boolean = false
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
            password = "",
            needsEmailVerification = false
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
                    handleSignUp(state.email, state.password)
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

    private suspend fun handleSignUp(email: String, password: String) {
        when (val result = authRepository.signUp(email, password)) {
            is ApiResult.Success -> {
                // Check if email verification is required
                if (result.data.requiresEmailVerification == true) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Please check your email and click the confirmation link to complete registration. Then try signing in.",
                        isSignUp = false, // Switch back to sign-in mode
                        password = ""
                    )
                } else {
                    // Normal signup completion
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

    fun resendVerificationEmail() {
        val currentEmail = _uiState.value.email

        if (currentEmail.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Email is required")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = "")

        viewModelScope.launch {
            try {
                when (val result = authRepository.resendVerificationEmail(currentEmail)) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = result.data.message
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to resend verification email: ${e.localizedMessage}"
                )
            }
        }
    }

    fun forgotPassword() {
        val currentEmail = _uiState.value.email

        if (currentEmail.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Email is required")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = "")

        viewModelScope.launch {
            try {
                when (val result = authRepository.forgotPassword(currentEmail)) {
                    is ApiResult.Success -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = result.data.message
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
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to send reset email: ${e.localizedMessage}"
                )
            }
        }
    }
}