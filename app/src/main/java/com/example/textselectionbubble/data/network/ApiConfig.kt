// data/network/ApiConfig.kt
package com.example.textselectionbubble.data.network

object ApiConfig {
    // TODO: Replace this with your actual backend API URL
    // For local development, you might use something like:
    // const val BASE_URL = "http://10.0.2.2:3000/" // For Android emulator
    // const val BASE_URL = "http://192.168.1.100:3000/" // For real device (replace with your local IP)
    // For production:
    // const val BASE_URL = "https://your-domain.com/"
    
    const val BASE_URL = "https://your-api-domain.com/"
    
    // Add timeout configurations
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L
}

// Alternative: You can also set this in your build.gradle.kts file
// buildConfigField("String", "API_BASE_URL", "\"https://your-api-domain.com\"")
// Then use BuildConfig.API_BASE_URL instead of ApiConfig.BASE_URL