// ui/screens/MainScreen.kt
package com.example.textselectionbubble.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.textselectionbubble.ui.components.rememberConnectivityState
import com.example.textselectionbubble.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController, viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uiState by viewModel.uiState.collectAsState()
    val connectivityState = rememberConnectivityState()

    var textToEnhance by remember { mutableStateOf("") }
    var selectedEnhancementType by remember { mutableStateOf(EnhancementType.GENERAL) }
    var showOfflineDialog by remember { mutableStateOf(false) }

    // Check permissions
    var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission(context)) }
    var hasAccessibilityPermission by remember { mutableStateOf(checkAccessibilityPermission(context)) }

    val scrollState = rememberScrollState()

    // Refresh permission states periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val newOverlay = checkOverlayPermission(context)
            val newAccessibility = checkAccessibilityPermission(context)

            if (newOverlay != hasOverlayPermission || newAccessibility != hasAccessibilityPermission) {
                hasOverlayPermission = newOverlay
                hasAccessibilityPermission = newAccessibility
                viewModel.refreshServiceState()
            }
        }
    }

    // Show offline dialog when trying to enhance without connection
    LaunchedEffect(uiState.errorMessage) {
        if (!connectivityState.isConnected &&
            (uiState.errorMessage.contains("network", ignoreCase = true) ||
                    uiState.errorMessage.contains("connection", ignoreCase = true))) {
            showOfflineDialog = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Offline notification
        ConnectivityStatusBar()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Text Selection Bubble",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    uiState.user?.let { user ->
                        Text(
                            text = user.email,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = {
                    viewModel.logout()
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                    }
                }) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Token Usage
            uiState.user?.let { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Daily Usage",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${user.tokensUsedToday} / ${user.dailyTokenLimit}",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = user.tokensUsedToday.toFloat() / user.dailyTokenLimit.toFloat(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = user.planName.uppercase(),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Service Control
            ServiceControlCard(
                isMonitoringEnabled = uiState.isMonitoringEnabled,
                hasOverlayPermission = hasOverlayPermission,
                hasAccessibilityPermission = hasAccessibilityPermission,
                onStartService = { viewModel.startService() },
                onStopService = { viewModel.stopService() },
                onRequestOverlay = {
                    requestOverlayPermission(context)
                },
                onRequestAccessibility = {
                    openAccessibilitySettings(context)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Text Enhancement Section
            Text(
                text = "Manual Enhancement",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enhancement Type Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EnhancementType.values().take(3).forEach { type ->
                    FilterChip(
                        onClick = { selectedEnhancementType = type },
                        label = { Text(type.value.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                        selected = selectedEnhancementType == type,
                        modifier = Modifier.weight(1f)
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
                        label = { Text(type.value.replaceFirstChar { it.uppercase() }, fontSize = 12.sp) },
                        selected = selectedEnhancementType == type,
                        modifier = Modifier.weight(1f)
                    )
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
                enabled = !uiState.isEnhancing
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Enhance Button
            Button(
                onClick = {
                    if (!connectivityState.isConnected) {
                        showOfflineDialog = true
                    } else {
                        viewModel.enhanceText(textToEnhance, selectedEnhancementType)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isEnhancing && textToEnhance.isNotBlank()
            ) {
                if (uiState.isEnhancing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Text("Enhance Text", fontSize = 16.sp)
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enhanced Result",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )

                            Row {
                                Text(
                                    text = "${uiState.tokensUsedThisRequest} tokens",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(uiState.enhancedText))
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
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

    // Offline Dialog
    if (showOfflineDialog) {
        AlertDialog(
            onDismissRequest = { showOfflineDialog = false },
            icon = { Icon(Icons.Default.WifiOff, contentDescription = null) },
            title = { Text("No Internet Connection") },
            text = { Text("Please check your connection and try again.") },
            confirmButton = {
                TextButton(onClick = { showOfflineDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun ServiceControlCard(
    isMonitoringEnabled: Boolean,
    hasOverlayPermission: Boolean,
    hasAccessibilityPermission: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    val allPermissionsGranted = hasOverlayPermission && hasAccessibilityPermission

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isMonitoringEnabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Text Selection Service",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Icon(
                    imageVector = if (isMonitoringEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isMonitoringEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permission Status
            if (!allPermissionsGranted) {
                if (!hasAccessibilityPermission) {
                    PermissionItem(
                        text = "Accessibility Service",
                        isGranted = false,
                        onClick = onRequestAccessibility
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!hasOverlayPermission) {
                    PermissionItem(
                        text = "Display Over Apps",
                        isGranted = false,
                        onClick = onRequestOverlay
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Service Control Button
            Button(
                onClick = {
                    if (isMonitoringEnabled) {
                        onStopService()
                    } else if (allPermissionsGranted) {
                        onStartService()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = allPermissionsGranted,
                colors = if (isMonitoringEnabled)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else
                    ButtonDefaults.buttonColors()
            ) {
                Icon(
                    imageVector = if (isMonitoringEnabled) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (!allPermissionsGranted) "Enable Permissions"
                    else if (isMonitoringEnabled) "Stop Service"
                    else "Start Service"
                )
            }

            if (isMonitoringEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select text anywhere to see enhancement bubble",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PermissionItem(
    text: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Enable $text")
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
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}