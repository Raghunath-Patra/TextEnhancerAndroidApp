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

// data/network/NetworkModule.kt
package com.example.textselectionbubble.data.network

import com.example.textselectionbubble.BuildConfig
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    
    private val gson: Gson = GsonBuilder()
        .setLenient()
        .create()
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
    
    val apiService: ApiService = retrofit.create(ApiService::class.java)
}

// data/network/ApiResult.kt
package com.example.textselectionbubble.data.network

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
}

// Extension function to handle API responses
suspend fun <T> safeApiCall(apiCall: suspend () -> retrofit2.Response<T>): ApiResult<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful) {
            response.body()?.let { body ->
                ApiResult.Success(body)
            } ?: ApiResult.Error("Empty response body")
        } else {
            val errorBody = response.errorBody()?.string()
            val errorMessage = try {
                val gson = Gson()
                val errorResponse = gson.fromJson(errorBody, com.example.textselectionbubble.data.models.ErrorResponse::class.java)
                errorResponse.error
            } catch (e: Exception) {
                "HTTP ${response.code()}: ${response.message()}"
            }
            ApiResult.Error(errorMessage, response.code())
        }
    } catch (e: Exception) {
        ApiResult.Error("Network error: ${e.localizedMessage}")
    }
}