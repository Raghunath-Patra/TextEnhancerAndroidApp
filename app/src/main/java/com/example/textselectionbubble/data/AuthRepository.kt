// 7. AuthRepository.kt
package com.example.textselectionbubble.data

object AuthRepository {
    fun signUp(email: String, password: String): String? {
        return if (email.isNotEmpty() && password.length >= 4) "fake_signup_token" else null
    }

    fun signIn(email: String, password: String): String? {
        return if (email == "test@example.com" && password == "password") "fake_signin_token" else null
    }
}