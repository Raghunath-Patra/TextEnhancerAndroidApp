// 3. SplashScreen.kt
package com.example.textselectionbubble.ui.screens

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import com.example.textselectionbubble.data.UserSessionManager
import androidx.compose.ui.platform.LocalContext

@Composable
fun SplashScreen(navController: NavController) {
    val context = LocalContext.current
    val sessionManager = UserSessionManager(context)

    LaunchedEffect(Unit) {
        delay(1500)
        val destination = if (sessionManager.isLoggedIn()) "main" else "auth"
        navController.navigate(destination) {
            popUpTo("splash") { inclusive = true }
        }
    }
}