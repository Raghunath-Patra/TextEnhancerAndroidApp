// ui/components/ConnectivityComponents.kt
package com.example.textselectionbubble.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.SignalCellularOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.textselectionbubble.utils.NetworkConnectivityManager
import kotlinx.coroutines.delay

@Composable
fun ConnectivityStatusBar() {
    val context = LocalContext.current
    val connectivityManager = remember { NetworkConnectivityManager(context) }
    var isConnected by remember { mutableStateOf(connectivityManager.isConnected()) }
    var connectionType by remember { mutableStateOf(connectivityManager.getConnectionType()) }
    var showBar by remember { mutableStateOf(!isConnected) }

    // Observe connectivity changes
    LaunchedEffect(Unit) {
        connectivityManager.observeConnectivity().collect { connected ->
            isConnected = connected
            connectionType = connectivityManager.getConnectionType()

            if (!connected) {
                showBar = true
            } else {
                // Show reconnected message briefly, then hide
                showBar = true
                delay(2000)
                showBar = false
            }
        }
    }

    AnimatedVisibility(
        visible = showBar,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it })
    ) {
        ConnectivityBanner(
            isConnected = isConnected,
            connectionType = connectionType
        )
    }
}

@Composable
fun ConnectivityBanner(
    isConnected: Boolean,
    connectionType: String
) {
    val backgroundColor = if (isConnected) {
        Color(0xFF4CAF50) // Green
    } else {
        Color(0xFFF44336) // Red
    }

    val icon = when {
        !isConnected -> Icons.Default.WifiOff
        connectionType.contains("WiFi") -> Icons.Default.Wifi
        connectionType.contains("Mobile") -> Icons.Default.SignalCellularOff
        else -> Icons.Default.CloudOff
    }

    val message = if (isConnected) {
        "Connected via $connectionType"
    } else {
        "No internet connection"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun NoInternetDialog(
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text("No Internet Connection")
            }
        },
        text = {
            Column {
                Text(
                    text = "This app requires an internet connection to enhance text with AI. Please check your connection and try again.",
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Make sure you have:",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• WiFi or mobile data enabled\n• Strong signal strength\n• No firewall blocking the app",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun ConnectivityIndicator(
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val context = LocalContext.current
    val connectivityManager = remember { NetworkConnectivityManager(context) }
    var isConnected by remember { mutableStateOf(connectivityManager.isConnected()) }
    var connectionType by remember { mutableStateOf(connectivityManager.getConnectionType()) }

    LaunchedEffect(Unit) {
        connectivityManager.observeConnectivity().collect { connected ->
            isConnected = connected
            connectionType = connectivityManager.getConnectionType()
        }
    }

    val color = if (isConnected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    val icon = when {
        !isConnected -> Icons.Default.WifiOff
        connectionType.contains("WiFi") -> Icons.Default.Wifi
        else -> Icons.Default.SignalCellularOff
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Connection status",
            tint = color,
            modifier = Modifier.size(16.dp)
        )

        if (showLabel) {
            Text(
                text = if (isConnected) connectionType else "Offline",
                fontSize = 12.sp,
                color = color
            )
        }
    }
}

@Composable
fun rememberConnectivityState(): ConnectivityState {
    val context = LocalContext.current
    val connectivityManager = remember { NetworkConnectivityManager(context) }
    var isConnected by remember { mutableStateOf(connectivityManager.isConnected()) }
    var connectionType by remember { mutableStateOf(connectivityManager.getConnectionType()) }

    LaunchedEffect(Unit) {
        connectivityManager.observeConnectivity().collect { connected ->
            isConnected = connected
            connectionType = connectivityManager.getConnectionType()
        }
    }

    return remember(isConnected, connectionType) {
        ConnectivityState(
            isConnected = isConnected,
            connectionType = connectionType
        )
    }
}

data class ConnectivityState(
    val isConnected: Boolean,
    val connectionType: String
)