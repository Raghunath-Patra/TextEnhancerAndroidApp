// ui/screens/AuthScreen.kt (Updated with connectivity)
package com.example.textselectionbubble.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.textselectionbubble.ui.components.ConnectivityStatusBar
import com.example.textselectionbubble.ui.components.ConnectivityIndicator
import com.example.textselectionbubble.ui.components.NoInternetDialog
import com.example.textselectionbubble.ui.components.rememberConnectivityState
import com.example.textselectionbubble.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(navController: NavController, viewModel: AuthViewModel = viewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val connectivityState = rememberConnectivityState()
    var passwordVisible by remember { mutableStateOf(false) }
    var showNoInternetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSuccess && !uiState.isSignUp) {
        if (uiState.isSuccess && !uiState.isSignUp) {
            navController.navigate("main") {
                popUpTo("auth") { inclusive = true }
            }
        }
    }

    // Show no internet dialog when trying to authenticate without connection
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App Title
            Text(
                text = "Text Selection Bubble",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Enhance your text with AI",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ConnectivityIndicator(showLabel = false)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Internet requirement notice
            if (!connectivityState.isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "⚠️ Internet connection required for authentication",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Auth Mode Title
            Text(
                text = if (uiState.isSignUp) "Create Account" else "Welcome Back",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Email Field
            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                enabled = !uiState.isLoading && connectivityState.isConnected
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password Field
            OutlinedTextField(
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                enabled = !uiState.isLoading && connectivityState.isConnected
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Action Button
            Button(
                onClick = {
                    if (!connectivityState.isConnected) {
                        showNoInternetDialog = true
                    } else {
                        viewModel.authenticate {
                            navController.navigate("main") {
                                popUpTo("auth") { inclusive = true }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isLoading && connectivityState.isConnected
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (!connectivityState.isConnected) "No Internet Connection"
                    else if (uiState.isSignUp) "Create Account"
                    else "Sign In",
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Toggle Auth Mode Button
            TextButton(
                onClick = viewModel::toggleMode,
                enabled = !uiState.isLoading && connectivityState.isConnected
            ) {
                Text(
                    text = if (uiState.isSignUp)
                        "Already have an account? Sign in"
                    else
                        "Don't have an account? Sign up",
                    color = if (connectivityState.isConnected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Email Verification Status
            if (uiState.needsEmailVerification) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Email Verification Required",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "We've sent a verification link to your email. Please click the link to activate your account, then return here to sign in.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = {
                                if (!connectivityState.isConnected) {
                                    showNoInternetDialog = true
                                } else {
                                    viewModel.resendVerificationEmail()
                                }
                            },
                            enabled = !uiState.isLoading && connectivityState.isConnected
                        ) {
                            Text(
                                text = if (connectivityState.isConnected)
                                    "Resend Verification Email"
                                else
                                    "No Internet Connection"
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
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Success Message for Sign Up
            if (uiState.isSuccess && uiState.errorMessage.isNotEmpty() && uiState.errorMessage.contains("successfully")) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = uiState.errorMessage,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Forgot Password Option
            if (!uiState.isSignUp && connectivityState.isConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.forgotPassword() },
                    enabled = !uiState.isLoading && uiState.email.isNotEmpty()
                ) {
                    Text(
                        text = "Forgot Password?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

    // No Internet Dialog
    if (showNoInternetDialog) {
        NoInternetDialog(
            onDismiss = { showNoInternetDialog = false },
            onRetry = {
                showNoInternetDialog = false
                if (connectivityState.isConnected) {
                    viewModel.authenticate {
                        navController.navigate("main") {
                            popUpTo("auth") { inclusive = true }
                        }
                    }
                }
            }
        )
    }
}