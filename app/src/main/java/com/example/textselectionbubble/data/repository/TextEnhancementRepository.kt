// data/repository/TextEnhancementRepository.kt
package com.example.textselectionbubble.data.repository

import com.example.textselectionbubble.data.models.*
import com.example.textselectionbubble.data.network.ApiResult
import com.example.textselectionbubble.data.network.ApiService
import com.example.textselectionbubble.data.network.safeApiCall

class TextEnhancementRepository(private val apiService: ApiService) {
    
    suspend fun enhanceText(
        accessToken: String,
        text: String,
        enhancementType: EnhancementType = EnhancementType.GENERAL
    ): ApiResult<EnhanceTextResponse> {
        return safeApiCall {
            apiService.enhanceText(
                authorization = "Bearer $accessToken",
                request = EnhanceTextRequest(text, enhancementType.value)
            )
        }
    }
}