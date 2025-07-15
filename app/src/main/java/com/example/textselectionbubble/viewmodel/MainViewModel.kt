// 10. MainViewModel.kt
package com.example.textselectionbubble.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.example.textselectionbubble.TextSelectionService
import com.example.textselectionbubble.data.UserSessionManager

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val context = application.applicationContext
    private val sessionManager = UserSessionManager(context)

    fun startService() {
        val intent = Intent(context, TextSelectionService::class.java)
        context.startService(intent)
    }

    fun stopService() {
        val intent = Intent(context, TextSelectionService::class.java)
        context.stopService(intent)
    }

    fun logout() {
        sessionManager.clearSession()
    }
}
