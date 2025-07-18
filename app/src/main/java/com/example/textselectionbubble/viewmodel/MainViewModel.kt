// viewmodel/MainViewModel.kt - Updated with proper service control
package com.example.textselectionbubble.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.textselectionbubble.TextSelectionService
import com.example.textselectionbubble.data.UserSessionManager
import com.example.textselectionbubble.data.models.EnhancementType
import com.example.textselectionbubble.data.models.User
import com.example.textselectionbubble.data.network.ApiResult
import com.example.textselectionbubble.data.network.NetworkModule
import com.example.textselectionbubble.data.repository.TextEnhancementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val context = application.applicationContext
    private val sessionManager = UserSessionManager(context)
    private val textEnhancementRepository = TextEnhancementRepository(NetworkModule.apiService)

    // SharedPreferences for service control
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("TextSelectionBubble", Context.MODE_PRIVATE)

    data class UiState(
        val user: User? = null,
        val isLoading: Boolean = false,
        val errorMessage: String = "",
        val enhancedText: String = "",
        val originalText: String = "",
        val isEnhancing: Boolean = false,
        val enhancementType: EnhancementType = EnhancementType.GENERAL,
        val tokensUsedThisRequest: Int = 0,
        val showEnhancementResult: Boolean = false,
        val isServiceRunning: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        loadUserData()
        loadServiceState()
        // Refresh service state when app starts
        refreshServiceState()
    }

    private fun loadUserData() {
        viewModelScope.launch {
            sessionManager.getUser().collect { user ->
                _uiState.value = _uiState.value.copy(user = user)
            }
        }
    }

    private fun loadServiceState() {
        val isServiceActive = sharedPrefs.getBoolean("service_active", false)
        val isMonitoringEnabled = sharedPrefs.getBoolean("monitoring_enabled", false)

        _uiState.value = _uiState.value.copy(
            isServiceRunning = isServiceActive,
            isMonitoringEnabled = isMonitoringEnabled
        )
    }

    fun enhanceText(text: String, enhancementType: EnhancementType = EnhancementType.GENERAL) {
        if (text.trim().isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Please enter some text to enhance")
            return
        }

        if (text.length > 5000) {
            _uiState.value = _uiState.value.copy(errorMessage = "Text is too long (max 5000 characters)")
            return
        }

        _uiState.value = _uiState.value.copy(
            isEnhancing = true,
            errorMessage = "",
            originalText = text,
            enhancementType = enhancementType
        )

        viewModelScope.launch {
            try {
                val accessToken = sessionManager.getAccessToken().first()

                if (accessToken == null) {
                    _uiState.value = _uiState.value.copy(
                        isEnhancing = false,
                        errorMessage = "Please log in again"
                    )
                    return@launch
                }

                when (val result = textEnhancementRepository.enhanceText(accessToken, text, enhancementType)) {
                    is ApiResult.Success -> {
                        // Update user token usage
                        sessionManager.updateUserUsage(
                            tokensUsedToday = result.data.tokensUsedToday,
                            tokensRemaining = result.data.tokensRemainingToday,
                            lastUsageDate = null
                        )

                        _uiState.value = _uiState.value.copy(
                            isEnhancing = false,
                            enhancedText = result.data.enhancedText,
                            tokensUsedThisRequest = result.data.tokensUsedThisRequest,
                            showEnhancementResult = true,
                            user = _uiState.value.user?.copy(
                                tokensUsedToday = result.data.tokensUsedToday
                            )
                        )
                    }

                    is ApiResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isEnhancing = false,
                            errorMessage = result.message
                        )
                    }

                    is ApiResult.Loading -> {
                        // Already handled above
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isEnhancing = false,
                    errorMessage = "An unexpected error occurred: ${e.localizedMessage}"
                )
            }
        }
    }

    fun clearEnhancementResult() {
        _uiState.value = _uiState.value.copy(
            showEnhancementResult = false,
            enhancedText = "",
            originalText = "",
            tokensUsedThisRequest = 0
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = "")
    }

    fun setEnhancementType(type: EnhancementType) {
        _uiState.value = _uiState.value.copy(enhancementType = type)
    }

    // Updated service control methods
    fun startService() {
        val intent = Intent(context, TextSelectionService::class.java)
        context.startService(intent)

        // Enable monitoring
        sharedPrefs.edit()
            .putBoolean("service_active", true)
            .putBoolean("monitoring_enabled", true)
            .apply()

        _uiState.value = _uiState.value.copy(
            isServiceRunning = true,
            isMonitoringEnabled = true
        )
    }

    fun stopService() {
        // Disable monitoring but keep service running
        sharedPrefs.edit()
            .putBoolean("monitoring_enabled", false)
            .apply()

        _uiState.value = _uiState.value.copy(
            isMonitoringEnabled = false
        )
    }

    fun stopServiceCompletely() {
        val intent = Intent(context, TextSelectionService::class.java)
        context.stopService(intent)

        // Disable everything
        sharedPrefs.edit()
            .putBoolean("service_active", false)
            .putBoolean("monitoring_enabled", false)
            .apply()

        _uiState.value = _uiState.value.copy(
            isServiceRunning = false,
            isMonitoringEnabled = false
        )
    }

    fun toggleMonitoring() {
        val newMonitoringState = !_uiState.value.isMonitoringEnabled

        sharedPrefs.edit()
            .putBoolean("monitoring_enabled", newMonitoringState)
            .apply()

        _uiState.value = _uiState.value.copy(
            isMonitoringEnabled = newMonitoringState
        )
    }

    fun logout() {
        viewModelScope.launch {
            // Stop service when logging out
            stopService()
            sessionManager.clearSession()
        }
    }