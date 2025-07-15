// data/network/ApiResult.kt
package com.example.textselectionbubble.data.network

import com.google.gson.Gson

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