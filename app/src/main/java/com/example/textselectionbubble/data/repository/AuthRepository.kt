// data/repository/AuthRepository.kt
package com.example.textselectionbubble.data.repository

import com.example.textselectionbubble.data.models.*
import com.example.textselectionbubble.data.network.ApiResult
import com.example.textselectionbubble.data.network.ApiService
import com.example.textselectionbubble.data.network.safeApiCall

class AuthRepository(private val apiService: ApiService) {

    suspend fun signIn(email: String, password: String): ApiResult<SignInResponse> {
        return safeApiCall {
            apiService.signIn(SignInRequest(email, password))
        }
    }

    suspend fun signUp(email: String, password: String): ApiResult<SignUpResponse> {
        return safeApiCall {
            apiService.signUp(SignUpRequest(email, password))
        }
    }

    suspend fun refreshToken(refreshToken: String): ApiResult<RefreshTokenResponse> {
        return safeApiCall {
            apiService.refreshToken(RefreshTokenRequest(refreshToken))
        }
    }

    suspend fun resendVerificationEmail(email: String): ApiResult<ResendVerificationResponse> {
        return safeApiCall {
            apiService.resendVerificationEmail(ResendVerificationRequest(email))
        }
    }

    suspend fun forgotPassword(email: String): ApiResult<ForgotPasswordResponse> {
        return safeApiCall {
            apiService.forgotPassword(ForgotPasswordRequest(email))
        }
    }

    suspend fun signOut(accessToken: String): ApiResult<SignOutResponse> {
        return safeApiCall {
            apiService.signOut(SignOutRequest(accessToken))
        }
    }
}