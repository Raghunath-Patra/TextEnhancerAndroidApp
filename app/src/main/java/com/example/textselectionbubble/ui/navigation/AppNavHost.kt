// 2. AppNavHost.kt (in ui/navigation/)
package com.example.textselectionbubble.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.textselectionbubble.ui.screens.AuthScreen
import com.example.textselectionbubble.ui.screens.MainScreen
import com.example.textselectionbubble.ui.screens.SplashScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController) }
        composable("auth") { AuthScreen(navController) }
        composable("main") { MainScreen(navController) }
    }
}