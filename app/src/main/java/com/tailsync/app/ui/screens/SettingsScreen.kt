package com.tailsync.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tailsync.app.ui.components.SnackbarState
import com.tailsync.app.ui.components.TailSyncSnackbarHost
import com.tailsync.app.ui.components.rememberSnackbarState
import com.tailsync.app.ui.theme.StatusConnected
import com.tailsync.app.ui.theme.StatusDisconnected
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverUrl: String,
    serverPort: Int,
    autoConnect: Boolean,
    isServiceRunning: Boolean,
    onServerUrlChange: (String) -> Unit,
    onServerPortChange: (Int) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarState = rememberSnackbarState()
    
    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }
    var portInput by remember(serverPort) { mutableStateOf(serverPort.toString()) }
    var isSaving by remember { mutableStateOf(false) }
    var isTogglingService by remember { mutableStateOf(false) }
    
    // Check battery optimization status - refresh on each recomposition
    var isBatteryOptimized by remember { mutableStateOf(isBatteryOptimizationEnabled(context)) }
    
    // Refresh battery status when screen is visible (using DisposableEffect as a workaround)
    DisposableEffect(Unit) {
        isBatteryOptimized = isBatteryOptimizationEnabled(context)
        onDispose { }
    }
    
    // Also refresh on window focus changes
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isBatteryOptimized = isBatteryOptimizationEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Top App Bar
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                // Server Configuration Section
                Text(
                    text = "SERVER",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        // Server URL
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Tailscale IP Address") },
                            placeholder = { Text("e.g., 100.64.0.1") },
                            leadingIcon = {
                                Icon(Icons.Default.Dns, contentDescription = null)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Port
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    portInput = input
                                }
                            },
                            label = { Text("Port") },
                            placeholder = { Text("8765") },
                            leadingIcon = {
                                Icon(Icons.Default.Tag, contentDescription = null)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Save Button with feedback
                        Button(
                            onClick = {
                                scope.launch {
                                    // Validate inputs
                                    if (urlInput.isBlank()) {
                                        snackbarState.showError("Server URL cannot be empty")
                                        return@launch
                                    }
                                    
                                    val port = portInput.toIntOrNull()
                                    if (port == null || port < 1 || port > 65535) {
                                        snackbarState.showError("Port must be between 1 and 65535")
                                        return@launch
                                    }
                                    
                                    isSaving = true
                                    try {
                                        onServerUrlChange(urlInput)
                                        onServerPortChange(port)
                                        delay(300) // Brief delay for visual feedback
                                        snackbarState.showSuccess("Configuration saved!")
                                    } catch (e: Exception) {
                                        snackbarState.showError(
                                            "Failed to save configuration",
                                            e.message ?: e.toString()
                                        )
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving...")
                            } else {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Configuration")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Behavior Section
                Text(
                    text = "BEHAVIOR",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        // Auto-connect Toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Auto-connect on Boot",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Start service when device boots",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoConnect,
                                onCheckedChange = { enabled ->
                                    onAutoConnectChange(enabled)
                                    snackbarState.showSuccess(
                                        if (enabled) "Auto-connect enabled" else "Auto-connect disabled"
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Battery Optimization Section
                Text(
                    text = "BATTERY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Battery Optimization",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (!isBatteryOptimized) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (!isBatteryOptimized) StatusConnected else StatusDisconnected
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (!isBatteryOptimized) "Unrestricted" else "Restricted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (!isBatteryOptimized) StatusConnected else StatusDisconnected
                                )
                            }
                            Text(
                                text = if (isBatteryOptimized) "Disable to prevent Android from killing the service" else "App is unrestricted from battery optimization",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Only show Configure button if battery is restricted
                        if (isBatteryOptimized) {
                            FilledTonalButton(
                                onClick = {
                                    openBatteryOptimizationSettings(context)
                                }
                            ) {
                                Text("Configure")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Info Section
                Text(
                    text = "ABOUT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        InfoRow(label = "App Version", value = "1.0.0")
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoRow(label = "Package", value = "com.tailsync.app")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // Snackbar at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            TailSyncSnackbarHost(snackbarState = snackbarState)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun isBatteryOptimizationEnabled(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBatteryOptimizationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to general battery settings
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (e2: Exception) {
            // Final fallback
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            context.startActivity(intent)
        }
    }
}
