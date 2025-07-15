// data/UserSessionManager.kt
package com.example.textselectionbubble.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.textselectionbubble.data.models.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

class UserSessionManager(private val context: Context) {
    
    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
        private val TOKENS_USED_TODAY_KEY = intPreferencesKey("tokens_used_today")
        private val DAILY_TOKEN_LIMIT_KEY = intPreferencesKey("daily_token_limit")
        private val PLAN_NAME_KEY = stringPreferencesKey("plan_name")
        private val LAST_USAGE_DATE_KEY = stringPreferencesKey("last_usage_date")
    }
    
    suspend fun saveUserSession(accessToken: String, refreshToken: String, user: User) {
        context.dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN_KEY] = accessToken
            preferences[REFRESH_TOKEN_KEY] = refreshToken
            preferences[USER_ID_KEY] = user.id
            preferences[USER_EMAIL_KEY] = user.email
            preferences[TOKENS_USED_TODAY_KEY] = user.tokensUsedToday
            preferences[DAILY_TOKEN_LIMIT_KEY] = user.dailyTokenLimit
            preferences[PLAN_NAME_KEY] = user.planName
            user.lastUsageDate?.let { preferences[LAST_USAGE_DATE_KEY] = it }
        }
    }
    
    suspend fun updateUserUsage(tokensUsedToday: Int, tokensRemaining: Int, lastUsageDate: String?) {
        context.dataStore.edit { preferences ->
            preferences[TOKENS_USED_TODAY_KEY] = tokensUsedToday
            lastUsageDate?.let { preferences[LAST_USAGE_DATE_KEY] = it }
        }
    }
    
    fun getAccessToken(): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY]
    }
    
    fun getRefreshToken(): Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[REFRESH_TOKEN_KEY]
    }
    
    fun getUser(): Flow<User?> = context.dataStore.data.map { preferences ->
        val id = preferences[USER_ID_KEY]
        val email = preferences[USER_EMAIL_KEY]
        val tokensUsedToday = preferences[TOKENS_USED_TODAY_KEY]
        val dailyTokenLimit = preferences[DAILY_TOKEN_LIMIT_KEY]
        val planName = preferences[PLAN_NAME_KEY]
        val lastUsageDate = preferences[LAST_USAGE_DATE_KEY]
        
        if (id != null && email != null && tokensUsedToday != null && 
            dailyTokenLimit != null && planName != null) {
            User(
                id = id,
                email = email,
                tokensUsedToday = tokensUsedToday,
                dailyTokenLimit = dailyTokenLimit,
                planName = planName,
                lastUsageDate = lastUsageDate
            )
        } else null
    }
    
    fun isLoggedIn(): Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN_KEY] != null
    }
    
    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    // For immediate access (non-Flow)
    suspend fun getAccessTokenImmediate(): String? {
        var token: String? = null
        getAccessToken().collect { token = it }
        return token
    }
}