// 6. UserSessionManager.kt
package com.example.textselectionbubble.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class UserSessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE)

    fun saveAuthToken(token: String) {
        prefs.edit() { putString("auth_token", token) }
    }

    fun getAuthToken(): String? = prefs.getString("auth_token", null)

    fun isLoggedIn(): Boolean = getAuthToken() != null

    fun clearSession() {
        prefs.edit() { clear() }
    }
}
