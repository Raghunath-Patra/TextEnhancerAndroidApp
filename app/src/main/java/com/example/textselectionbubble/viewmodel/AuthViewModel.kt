// 9. AuthViewModel.kt
package com.example.textselectionbubble.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.textselectionbubble.data.AuthRepository
import com.example.textselectionbubble.data.UserSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionManager = UserSessionManager(application.applicationContext)

    data class UiState(
        val email: String = "",
        val password: String = "",
        val isSignUp: Boolean = true,
        val errorMessage: String = ""
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = value)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(isSignUp = !_uiState.value.isSignUp, errorMessage = "")
    }

    fun authenticate(onSuccess: () -> Unit) {
        val state = _uiState.value
        val token = if (state.isSignUp) AuthRepository.signUp(state.email, state.password)
        else AuthRepository.signIn(state.email, state.password)

        if (token != null) {
            sessionManager.saveAuthToken(token)
            onSuccess()
        } else {
            _uiState.value = state.copy(errorMessage = "Authentication failed")
        }
    }
}