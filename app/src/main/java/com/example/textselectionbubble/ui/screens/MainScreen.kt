// ui/screens/MainScreen.kt - Fixed to properly show permission controls
package com.example.textselectionbubble.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.textselectionbubble.data.models.EnhancementType
import com.example.textselectionbubble.ui.components.ConnectivityStatusBar
import com.example.textselectionbubble.ui.components.ConnectivityIndicator
import com.example.textselectionbubble.ui.components.NoInternetDialog
import com.example.textselectionbubble.ui.components.rememberConnectivityState
import com.example.textselectionbubble.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()
    val connectivityState = rememberConnectivityState()

    var textToEnhance by remember { mutableStateOf("") }
    var selectedEnhancementType by remember { mutableStateOf(EnhancementType.GENERAL) }
    var showPermissionDetails by remember { mutableStateOf(false) }
    var showNoInternetDialog by remember { mutableStateOf(false) }

    // Check permissions
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(checkAccessibilityPermission(context)) }

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        // Check permissions when screen loads
        hasOverlayPermission = checkOverlayPermission(context)
        hasAccessibilityPermission = checkAccessibilityPermission(context)
        viewModel.refreshServiceState()
    }

    // Refresh permissions when user returns from settings
    LaunchedEffect(hasAccessibilityPermission, hasOverlayPermission) {
        viewModel.refreshServiceState()
    }

    // Show no internet dialog when trying to enhance without connection
    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage.contains("network", ignoreCase = true) ||
            uiState.errorMessage.contains("connection", ignoreCase = true)) {
            showNoInternetDialog = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Connectivity Status Bar
        ConnectivityStatusBar()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Header with user info and logout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Text Selection Bubble",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    uiState.user?.let { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = user.email,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ConnectivityIndicator(showLabel = false)
                        }
                    }
                }

                IconButton(
                    onClick = {
                        viewModel.logout()
                        navController.navigate("auth") {
                            popUpTo("main") { inclusive = true }
                        }
                    }
                ) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Token Usage Card
            uiState.user?.let { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Daily Usage",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = user.tokensUsedToday.toFloat() / user.dailyTokenLimit.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${user.tokensUsedToday} / ${user.dailyTokenLimit} tokens",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${user.planName.uppercase()} plan",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Service Status Card - Always visible
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isMonitoringEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Text Selection Service",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (uiState.isMonitoringEnabled)
                                    "✅ Active - Select text in any app to enhance it"
                                else
                                    "❌ Stopped - Enable to use text selection bubble",
                                fontSize = 12.sp,
                                color = if (uiState.isMonitoringEnabled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }

                        IconButton(
                            onClick = { showPermissionDetails = !showPermissionDetails }
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Service Control Button
                    val allPermissionsGranted = hasOverlayPermission && hasAccessibilityPermission

                    if (!uiState.isMonitoringEnabled) {
                        Button(
                            onClick = {
                                if (allPermissionsGranted) {
                                    viewModel.startService()
                                } else {
                                    showPermissionDetails = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = allPermissionsGranted
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (allPermissionsGranted) "Start Service" else "Enable Permissions First")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.stopService() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop Service")
                        }
                    }

                    if (!allPermissionsGranted) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "⚠️ Missing permissions - tap Settings above to enable",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Permission Details Section
            if (showPermissionDetails) {
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Required Permissions",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Permission Status
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (hasAccessibilityPermission) "✅" else "❌",
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Accessibility Service - to detect text selection",
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (hasOverlayPermission) "✅" else "❌",
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Display over other apps - to show bubble",
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Permission Buttons
                        if (!hasAccessibilityPermission) {
                            Button(
                                onClick = {
                                    openAccessibilitySettings(context)
                                    // Refresh permissions after a delay
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        kotlinx.coroutines.delay(1000)
                                        hasAccessibilityPermission = checkAccessibilityPermission(context)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable Accessibility Service")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (!hasOverlayPermission) {
                            Button(
                                onClick = {
                                    requestOverlayPermission(context)
                                    // Refresh permissions after a delay
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                        kotlinx.coroutines.delay(1000)
                                        hasOverlayPermission = checkOverlayPermission(context)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable Overlay Permission")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // How it works
                        Text(
                            text = "How it works:",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Enable both permissions above\n" +
                                    "2. Start the service\n" +
                                    "3. Select text in any app\n" +
                                    "4. Enhancement bubble appears automatically\n" +
                                    "5. Service works even when this app is closed",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Text Enhancement Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manual Text Enhancement",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                ConnectivityIndicator()
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Internet requirement notice
            if (!connectivityState.isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠️ Internet connection required for text enhancement",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Enhancement Type Selector
            Text(
                text = "Enhancement Type:",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhancementType.values().take(3).forEach { type ->
                    FilterChip(
                        onClick = { selectedEnhancementType = type },
                        label = {
                            Text(
                                text = type.value.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp
                            )
                        },
                        selected = selectedEnhancementType == type,
                        modifier = Modifier.weight(1f),
                        enabled = connectivityState.isConnected
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhancementType.values().drop(3).forEach { type ->
                    FilterChip(
                        onClick = { selectedEnhancementType = type },
                        label = {
                            Text(
                                text = type.value.replaceFirstChar { it.uppercase() },
                                fontSize = 12.sp
                            )
                        },
                        selected = selectedEnhancementType == type,
                        modifier = Modifier.weight(1f),
                        enabled = connectivityState.isConnected
                    )
                }
                // Add empty space if needed
                if (EnhancementType.values().size < 6) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Text Input
            OutlinedTextField(
                value = textToEnhance,
                onValueChange = { textToEnhance = it },
                label = { Text("Enter text to enhance") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                enabled = !uiState.isEnhancing && connectivityState.isConnected
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enhance Button
            Button(
                onClick = {
                    if (!connectivityState.isConnected) {
                        showNoInternetDialog = true
                    } else {
                        viewModel.enhanceText(textToEnhance, selectedEnhancementType)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isEnhancing && textToEnhance.isNotBlank() && connectivityState.isConnected
            ) {
                if (uiState.isEnhancing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    if (!connectivityState.isConnected) "No Internet Connection"
                    else "Enhance Text"
                )
            }

            // Enhancement Result
            if (uiState.showEnhancementResult) {
                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enhanced Text",
                                fontWeight = FontWeight.Medium,
                                fontSize = 16.sp
                            )

                            Row {
                                Text(
                                    text = "${uiState.tokensUsedThisRequest} tokens used",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(uiState.enhancedText))
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        SelectionContainer {
                            Text(
                                text = uiState.enhancedText,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Error Message
            if (uiState.errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.errorMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // No Internet Dialog
    if (showNoInternetDialog) {
        NoInternetDialog(
            onDismiss = { showNoInternetDialog = false },
            onRetry = {
                showNoInternetDialog = false
                if (connectivityState.isConnected && textToEnhance.isNotBlank()) {
                    viewModel.enhanceText(textToEnhance, selectedEnhancementType)
                }
            }
        )
    }
}

// Helper functions
private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}

private fun checkAccessibilityPermission(context: Context): Boolean {
    val accessibilityServiceName = "${context.packageName}/${com.example.textselectionbubble.TextSelectionService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return enabledServices?.contains(accessibilityServiceName) == true
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}

private fun requestOverlayPermission(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
}