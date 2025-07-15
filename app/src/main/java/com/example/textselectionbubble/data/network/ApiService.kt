// data/network/ApiService.kt
package com.example.textselectionbubble.data.network

import com.example.textselectionbubble.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    
    @POST("api/auth/signin")
    suspend fun signIn(@Body request: SignInRequest): Response<SignInResponse>
    
    @POST("api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<SignUpResponse>
    
    @POST("api/enhance-text")
    suspend fun enhanceText(
        @Header("Authorization") authorization: String,
        @Body request: EnhanceTextRequest
    ): Response<EnhanceTextResponse>
}
