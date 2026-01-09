package com.tailsync.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tailsync.app.ui.components.TailSyncSnackbarHost
import com.tailsync.app.ui.components.rememberSnackbarState
import com.tailsync.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverUrl: String,
    serverPort: Int,
    autoConnect: Boolean,
    isServiceRunning: Boolean,
    onSaveSettings: (String, Int) -> Unit,
    onAutoConnectChange: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbarState = rememberSnackbarState()
    
    var urlInput by remember(serverUrl) { mutableStateOf(serverUrl) }
    var portInput by remember(serverPort) { mutableStateOf(serverPort.toString()) }
    var isSaving by remember { mutableStateOf(false) }
    
    // Battery optimization status
    var isBatteryOptimized by remember { mutableStateOf(isBatteryOptimizationEnabled(context)) }
    
    // Refresh battery status on resume
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
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
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
                    .padding(horizontal = 20.dp)
            ) {
                // Server Section
                SectionHeader(title = "SERVER")
                
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("Tailscale IP Address") },
                            placeholder = { Text("e.g., 100.64.0.1") },
                            leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TailSyncPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = TailSyncPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.all { it.isDigit() }) {
                                    portInput = input
                                }
                            },
                            label = { Text("Port") },
                            placeholder = { Text("8765") },
                            leadingIcon = { Icon(Icons.Rounded.Tag, contentDescription = null) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TailSyncPrimary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = TailSyncPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch {
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
                                        // Save both settings at once
                                        onSaveSettings(urlInput, port)
                                        snackbarState.showSuccess("Configuration saved!")
                                    } catch (e: Exception) {
                                        snackbarState.showError("Failed to save", e.message ?: e.toString())
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving,
                            shape = RoundedCornerShape(12.dp)
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
                                Icon(Icons.Rounded.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Configuration")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Behavior Section
                SectionHeader(title = "BEHAVIOR")
                
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
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
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAutoConnectChange(enabled)
                                snackbarState.showSuccess(
                                    if (enabled) "Auto-connect enabled" else "Auto-connect disabled"
                                )
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Battery Section
                SectionHeader(title = "BATTERY")
                
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
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
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (!isBatteryOptimized) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (!isBatteryOptimized) StatusConnected else StatusDisconnected
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (!isBatteryOptimized) "Unrestricted" else "Restricted",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (!isBatteryOptimized) StatusConnected else StatusDisconnected
                                )
                            }
                            
                            if (isBatteryOptimized) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Disable to keep service running",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isBatteryOptimized) {
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    openBatteryOptimizationSettings(context)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Configure")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // About Section
                SectionHeader(title = "ABOUT")
                
                GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        InfoRow(label = "App Version", value = "1.2.3")
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoRow(label = "Package", value = "com.tailsync.app")
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Snackbar
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun isBatteryOptimizationEnabled(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return !powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBatteryOptimizationSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        })
    } catch (e: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e2: Exception) {
            context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
    }
}
