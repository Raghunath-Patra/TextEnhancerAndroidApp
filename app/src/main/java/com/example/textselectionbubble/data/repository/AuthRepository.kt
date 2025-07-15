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
}
