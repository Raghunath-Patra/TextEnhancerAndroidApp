// MainActivity.kt - Complete with all imports
package com.example.textselectionbubble

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.textselectionbubble.ui.navigation.AppNavHost
import com.example.textselectionbubble.ui.theme.TextSelectionBubbleTheme

class MainActivity : ComponentActivity() {

    private val permissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request necessary permissions
        requestPermissions()

        setContent {
            TextSelectionBubbleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavHost(navController = navController)
                }
            }
        }
    }

    private fun requestPermissions() {
        // Request overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, permissionRequestCode)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == permissionRequestCode) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    // Permission granted
                    Log.d("MainActivity", "Overlay permission granted")
                } else {
                    // Permission denied
                    Log.d("MainActivity", "Overlay permission denied")
                }
            }
        }
    }
}

//
//package com.example.textselectionbubble
//
//import android.app.Activity
//import android.content.Context
//import android.content.Intent
//import android.net.Uri
//import android.os.Build
//import android.os.Bundle
//import android.provider.Settings
//import android.widget.Toast
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import com.example.textselectionbubble.ui.theme.TextSelectionBubbleTheme
//import kotlinx.coroutines.delay
//
//class MainActivity : ComponentActivity() {
//
//    // Store refresh function to call from launchers
//    private var refreshPermissionsCallback: (() -> Unit)? = null
//
//    private val overlayPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        // User returned from overlay permission settings - refresh state
//        refreshPermissionsCallback?.invoke()
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (Settings.canDrawOverlays(this)) {
//                Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
//            } else {
//                Toast.makeText(this, "Overlay permission denied", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private val accessibilitySettingsLauncher = registerForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        // User returned from accessibility settings - refresh state
//        refreshPermissionsCallback?.invoke()
//        Toast.makeText(this, "Please check if accessibility service is enabled", Toast.LENGTH_SHORT).show()
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            TextSelectionBubbleTheme {
//                Surface(
//                    modifier = Modifier.fillMaxSize(),
//                    color = MaterialTheme.colorScheme.background
//                ) {
//                    MainScreen()
//                }
//            }
//        }
//    }
//
//    @Composable
//    fun MainScreen() {
//        val context = LocalContext.current
//
//        // Load service state from SharedPreferences
//        val sharedPrefs = context.getSharedPreferences("TextSelectionBubble", Context.MODE_PRIVATE)
//        var serviceRunning by remember { mutableStateOf(sharedPrefs.getBoolean("service_running", false)) }
//
//        // Check permissions initially and when user returns from settings
//        var hasOverlayPermission by remember { mutableStateOf(checkOverlayPermission()) }
//        var hasAccessibilityPermission by remember { mutableStateOf(checkAccessibilityPermission()) }
//
//        // Function to refresh all permissions
//        fun refreshPermissions() {
//            hasOverlayPermission = checkOverlayPermission()
//            hasAccessibilityPermission = checkAccessibilityPermission()
//        }
//
//        // Function to save service state
//        fun saveServiceState(running: Boolean) {
//            serviceRunning = running
//            sharedPrefs.edit().putBoolean("service_running", running).apply()
//        }
//
//        // Set callback so launchers can trigger refresh
//        refreshPermissionsCallback = { refreshPermissions() }
//
//        // Only check permissions once when screen loads
//        LaunchedEffect(Unit) {
//            refreshPermissions()
//        }
//
//        val allPermissionsGranted = hasOverlayPermission && hasAccessibilityPermission
//
//        Column(
//            modifier = Modifier
//                .fillMaxSize()
//                .padding(16.dp),
//            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.spacedBy(16.dp)
//        ) {
//            Spacer(modifier = Modifier.height(32.dp))
//
//            Text(
//                text = "Text Selection Bubble",
//                fontSize = 24.sp,
//                fontWeight = FontWeight.Bold,
//                textAlign = TextAlign.Center
//            )
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            Text(
//                text = "This app requires two permissions to work:",
//                fontSize = 16.sp,
//                textAlign = TextAlign.Center
//            )
//
//            Column(
//                modifier = Modifier.padding(16.dp),
//                verticalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    Text(
//                        text = if (hasAccessibilityPermission) "✅" else "❌",
//                        fontSize = 16.sp
//                    )
//                    Text(
//                        text = "Accessibility Service - to detect text selection",
//                        fontSize = 14.sp
//                    )
//                }
//
//                Row(
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    Text(
//                        text = if (hasOverlayPermission) "✅" else "❌",
//                        fontSize = 16.sp
//                    )
//                    Text(
//                        text = "Display over other apps - to show bubble",
//                        fontSize = 14.sp
//                    )
//                }
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            Button(
//                onClick = {
//                    openAccessibilitySettings()
//                    // Permission will be checked when user returns via launcher callback
//                },
//                modifier = Modifier.fillMaxWidth(),
//                colors = if (hasAccessibilityPermission)
//                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
//                else
//                    ButtonDefaults.buttonColors()
//            ) {
//                Text(if (hasAccessibilityPermission) "✅ Accessibility Enabled" else "Enable Accessibility Service")
//            }
//
//            Button(
//                onClick = {
//                    requestOverlayPermission()
//                    // Permission will be checked when user returns via launcher callback
//                },
//                modifier = Modifier.fillMaxWidth(),
//                colors = if (hasOverlayPermission)
//                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
//                else
//                    ButtonDefaults.buttonColors()
//            ) {
//                Text(if (hasOverlayPermission) "✅ Overlay Enabled" else "Enable Overlay Permission")
//            }
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            Spacer(modifier = Modifier.height(8.dp))
//
//            if (!serviceRunning) {
//                Button(
//                    onClick = {
//                        // Refresh permissions one more time before starting
//                        refreshPermissions()
//                        if (allPermissionsGranted) {
//                            startTextSelectionService()
//                            saveServiceState(true)
//                        } else {
//                            val missingPermissions = mutableListOf<String>()
//                            if (!hasAccessibilityPermission) missingPermissions.add("Accessibility Service")
//                            if (!hasOverlayPermission) missingPermissions.add("Overlay Permission")
//
//                            Toast.makeText(
//                                context,
//                                "Missing: ${missingPermissions.joinToString(", ")}",
//                                Toast.LENGTH_LONG
//                            ).show()
//                        }
//                    },
//                    modifier = Modifier.fillMaxWidth(),
//                    enabled = allPermissionsGranted,
//                    colors = ButtonDefaults.buttonColors(
//                        containerColor = if (allPermissionsGranted)
//                            MaterialTheme.colorScheme.primary
//                        else
//                            MaterialTheme.colorScheme.outline
//                    )
//                ) {
//                    Text(
//                        if (allPermissionsGranted) "Start Service" else "Enable All Permissions First",
//                        color = if (allPermissionsGranted)
//                            MaterialTheme.colorScheme.onPrimary
//                        else
//                            MaterialTheme.colorScheme.onSurface
//                    )
//                }
//            } else {
//                Button(
//                    onClick = {
//                        stopTextSelectionService()
//                        saveServiceState(false)
//                    },
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
//                ) {
//                    Text("Stop Service")
//                }
//            }
//
//            // Less prominent refresh button
//            TextButton(
//                onClick = { refreshPermissions() },
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Text(
//                    "🔄 Refresh Permission Status",
//                    fontSize = 14.sp,
//                    color = MaterialTheme.colorScheme.secondary
//                )
//            }
//
//            if (serviceRunning) {
//                Card(
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
//                ) {
//                    Text(
//                        text = "✓ Service is running! Select text in any app to see the bubble.",
//                        fontSize = 14.sp,
//                        modifier = Modifier.padding(12.dp),
//                        textAlign = TextAlign.Center,
//                        color = MaterialTheme.colorScheme.onPrimaryContainer
//                    )
//                }
//            }
//
//            if (!allPermissionsGranted) {
//                Card(
//                    modifier = Modifier.fillMaxWidth(),
//                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
//                ) {
//                    Text(
//                        text = "⚠ Please enable all permissions above before starting the service.",
//                        fontSize = 14.sp,
//                        modifier = Modifier.padding(12.dp),
//                        textAlign = TextAlign.Center,
//                        color = MaterialTheme.colorScheme.onErrorContainer
//                    )
//                }
//            }
//
//            Spacer(modifier = Modifier.height(16.dp))
//
//            Card(
//                modifier = Modifier.fillMaxWidth(),
//                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
//            ) {
//                Column(
//                    modifier = Modifier.padding(12.dp),
//                    verticalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    Text(
//                        text = "Tips:",
//                        fontSize = 14.sp,
//                        fontWeight = FontWeight.Medium
//                    )
//                    Text(
//                        text = "• Long press and drag to move the bubble",
//                        fontSize = 12.sp
//                    )
//                    Text(
//                        text = "• Bubble appears after a short delay to avoid flickering",
//                        fontSize = 12.sp
//                    )
//                    Text(
//                        text = "• Turn off the service when not needed to save battery",
//                        fontSize = 12.sp
//                    )
//                    Text(
//                        text = "• Use refresh button if permissions don't update automatically",
//                        fontSize = 12.sp
//                    )
//                }
//            }
//        }
//    }
//
//    private fun openAccessibilitySettings() {
//        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
//        accessibilitySettingsLauncher.launch(intent)
//    }
//
//    private fun requestOverlayPermission() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (!Settings.canDrawOverlays(this)) {
//                val intent = Intent(
//                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
//                    Uri.parse("package:$packageName")
//                )
//                overlayPermissionLauncher.launch(intent)
//            } else {
//                Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
//            }
//        }
//    }
//
//    private fun checkOverlayPermission(): Boolean {
//        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            Settings.canDrawOverlays(this)
//        } else {
//            true
//        }
//    }
//
//    private fun checkAccessibilityPermission(): Boolean {
//        val accessibilityServiceName = "${packageName}/${TextSelectionService::class.java.name}"
//        val enabledServices = Settings.Secure.getString(
//            contentResolver,
//            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
//        )
//        return enabledServices?.contains(accessibilityServiceName) == true
//    }
//
//    private fun checkPermissions(): Boolean {
//        return checkOverlayPermission() && checkAccessibilityPermission()
//    }
//
//    private fun startTextSelectionService() {
//        val serviceIntent = Intent(this, TextSelectionService::class.java)
//        startService(serviceIntent)
//        Toast.makeText(this, "Text Selection Service Started", Toast.LENGTH_SHORT).show()
//    }
//
//    private fun stopTextSelectionService() {
//        val serviceIntent = Intent(this, TextSelectionService::class.java)
//        stopService(serviceIntent)
//        Toast.makeText(this, "Text Selection Service Stopped", Toast.LENGTH_SHORT).show()
//    }
//}
