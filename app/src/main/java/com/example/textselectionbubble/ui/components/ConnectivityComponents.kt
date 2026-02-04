// ui/components/ConnectivityComponents.kt
package com.example.textselectionbubble.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.textselectionbubble.utils.NetworkConnectivityManager
import kotlinx.coroutines.delay

@Composable
fun ConnectivityStatusBar() {
    val context = LocalContext.current
    val connectivityManager = remember { NetworkConnectivityManager(context) }
    var isConnected by remember { mutableStateOf(connectivityManager.isConnected()) }
    var showBar by remember { mutableStateOf(!isConnected) }

    // Observe connectivity changes
    LaunchedEffect(Unit) {
        connectivityManager.observeConnectivity().collect { connected ->
            isConnected = connected

            if (!connected) {
                showBar = true
            } else {
                // Auto-hide when connected
                delay(2000)
                showBar = false
            }
        }
    }

    // Only show when offline
    AnimatedVisibility(
        visible = showBar && !isConnected,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it })
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF44336))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "No internet connection",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun rememberConnectivityState(): ConnectivityState {
    val context = LocalContext.current
    val connectivityManager = remember { NetworkConnectivityManager(context) }
    var isConnected by remember { mutableStateOf(connectivityManager.isConnected()) }

    LaunchedEffect(Unit) {
        connectivityManager.observeConnectivity().collect { connected ->
            isConnected = connected
        }
    }

    return remember(isConnected) {
        ConnectivityState(isConnected = isConnected)
    }
}

data class ConnectivityState(
    val isConnected: Boolean
)