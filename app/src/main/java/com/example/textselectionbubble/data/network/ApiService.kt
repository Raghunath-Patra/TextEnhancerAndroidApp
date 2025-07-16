// data/network/ApiService.kt
package com.example.textselectionbubble.data.network

import com.example.textselectionbubble.data.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Authentication endpoints
    @POST("auth/signin")
    suspend fun signIn(@Body request: SignInRequest): Response<SignInResponse>

    @POST("auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<SignUpResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): Response<RefreshTokenResponse>

    @POST("auth/resend-verification")
    suspend fun resendVerificationEmail(@Body request: ResendVerificationRequest): Response<ResendVerificationResponse>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ForgotPasswordResponse>

    @POST("auth/signout")
    suspend fun signOut(@Body request: SignOutRequest): Response<SignOutResponse>

    // Text enhancement endpoints
    @POST("enhance")
    suspend fun enhanceText(
        @Header("Authorization") authorization: String,
        @Body request: EnhanceTextRequest
    ): Response<EnhanceTextResponse>

    @GET("user/usage")
    suspend fun getUserUsage(
        @Header("Authorization") authorization: String
    ): Response<UserUsageResponse>

    @GET("user/profile")
    suspend fun getUserProfile(
        @Header("Authorization") authorization: String
    ): Response<User>
}