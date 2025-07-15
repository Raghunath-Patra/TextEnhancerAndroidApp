// 4. AuthScreen.kt
package com.example.textselectionbubble.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.textselectionbubble.viewmodel.AuthViewModel

@Composable
fun AuthScreen(navController: NavController, viewModel: AuthViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = if (uiState.isSignUp) "Sign Up" else "Sign In")
        OutlinedTextField(value = uiState.email, onValueChange = viewModel::onEmailChange, label = { Text("Email") })
        OutlinedTextField(value = uiState.password, onValueChange = viewModel::onPasswordChange, label = { Text("Password") })

        Button(onClick = {
            viewModel.authenticate(onSuccess = {
                navController.navigate("main") {
                    popUpTo("auth") { inclusive = true }
                }
            })
        }) {
            Text(if (uiState.isSignUp) "Create Account" else "Log In")
        }

        TextButton(onClick = { viewModel.toggleMode() }) {
            Text(if (uiState.isSignUp) "Already have an account? Log in" else "Don't have an account? Sign up")
        }

        if (uiState.errorMessage.isNotEmpty()) {
            Text(text = uiState.errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}
