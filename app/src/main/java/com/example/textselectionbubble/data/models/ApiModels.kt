// data/models/ApiModels.kt
package com.example.textselectionbubble.data.models

import com.google.gson.annotations.SerializedName

// Authentication Request Models
data class SignInRequest(
    val email: String,
    val password: String
)

data class SignUpRequest(
    val email: String,
    val password: String
)

// Authentication Response Models
data class SignInResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: User
)

data class SignUpResponse(
    val message: String,
    val user: UserBasic,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val requiresEmailVerification: Boolean? = null
)

data class User(
    val id: String,
    val email: String,
    @SerializedName("tokens_used_today") val tokensUsedToday: Int,
    @SerializedName("daily_token_limit") val dailyTokenLimit: Int,
    @SerializedName("plan_name") val planName: String,
    @SerializedName("last_usage_date") val lastUsageDate: String?
)

data class UserBasic(
    val id: String,
    val email: String,
    @SerializedName("email_confirmed") val emailConfirmed: Boolean = false
)

// Text Enhancement Request/Response Models
data class EnhanceTextRequest(
    val text: String,
    @SerializedName("enhancement_type") val enhancementType: String = "general"
)

data class EnhanceTextResponse(
    @SerializedName("enhanced_text") val enhancedText: String,
    @SerializedName("original_text") val originalText: String,
    @SerializedName("enhancement_type") val enhancementType: String,
    @SerializedName("tokens_used_this_request") val tokensUsedThisRequest: Int,
    @SerializedName("tokens_used_today") val tokensUsedToday: Int,
    @SerializedName("tokens_remaining_today") val tokensRemainingToday: Int,
    @SerializedName("daily_limit") val dailyLimit: Int,
    @SerializedName("resets_at") val resetsAt: String
)

// Error Response Model
data class ErrorResponse(
    val error: String
)

// Token Limit Error Response
data class TokenLimitErrorResponse(
    val error: String,
    @SerializedName("tokens_used_today") val tokensUsedToday: Int?,
    @SerializedName("daily_limit") val dailyLimit: Int?,
    val plan: String?,
    @SerializedName("resets_at") val resetsAt: String?,
    @SerializedName("tokens_remaining") val tokensRemaining: Int?
)

// Additional Request Models
data class RefreshTokenRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

data class ResendVerificationRequest(
    val email: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class SignOutRequest(
    @SerializedName("access_token") val accessToken: String
)

// Additional Response Models
data class RefreshTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: User
)

data class ResendVerificationResponse(
    val message: String
)

data class ForgotPasswordResponse(
    val message: String
)

data class SignOutResponse(
    val message: String
)

data class UserUsageResponse(
    @SerializedName("tokens_used_today") val tokensUsedToday: Int,
    @SerializedName("tokens_remaining_today") val tokensRemainingToday: Int,
    @SerializedName("daily_limit") val dailyLimit: Int,
    @SerializedName("plan_name") val planName: String,
    @SerializedName("resets_at") val resetsAt: String
)

// Enhancement Types Enum
enum class EnhancementType(val value: String) {
    GENERAL("general"),
    PROFESSIONAL("professional"),
    CASUAL("casual"),
    CONCISE("concise"),
    DETAILED("detailed")
}