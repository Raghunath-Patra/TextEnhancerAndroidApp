// 5. MainScreen.kt
package com.example.textselectionbubble.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.textselectionbubble.TextSelectionService
import com.example.textselectionbubble.viewmodel.MainViewModel

@Composable
fun MainScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val context = viewModel.context

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Welcome to Text Selection Bubble")

        Button(onClick = { viewModel.startService() }) {
            Text("Start Bubble Service")
        }

        Button(onClick = { viewModel.stopService() }) {
            Text("Stop Bubble Service")
        }

        TextButton(onClick = {
            viewModel.logout()
            navController.navigate("auth") {
                popUpTo("main") { inclusive = true }
            }
        }) {
            Text("Logout")
        }
    }
}