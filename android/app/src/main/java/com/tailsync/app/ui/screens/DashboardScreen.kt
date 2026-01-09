package com.tailsync.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tailsync.app.data.ClipboardHistoryItem
import com.tailsync.app.network.ConnectionState
import com.tailsync.app.ui.components.TailSyncSnackbarHost
import com.tailsync.app.ui.components.rememberSnackbarState
import com.tailsync.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    connectionState: ConnectionState,
    clipboardHistory: List<ClipboardHistoryItem>,
    serverUrl: String,
    onSyncNow: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClearHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarState = rememberSnackbarState()
    
    var isSyncing by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    
    // Track if user explicitly clicked the Connect button (vs auto-connect on app start)
    var userInitiatedConnect by remember { mutableStateOf(false) }

    // Track connection state changes
    var previousState by remember { mutableStateOf<ConnectionState?>(null) }
    
    LaunchedEffect(connectionState) {
        // Skip the very first state update (initial app state)
        if (previousState == null) {
            previousState = connectionState
            return@LaunchedEffect
        }
        
        // Only show toasts if the state actually changed
        if (previousState != connectionState) {
            when (connectionState) {
                ConnectionState.CONNECTED -> {
                    isConnecting = false
                    // Only show "Connected" toast if user explicitly clicked Connect
                    if (userInitiatedConnect) {
                        snackbarState.showSuccess("Connected to server")
                        userInitiatedConnect = false
                    }
                }
                ConnectionState.DISCONNECTED -> {
                    isConnecting = false
                    // Only show "Disconnected" if we were previously connected (lost connection)
                    if (previousState == ConnectionState.CONNECTED) {
                        snackbarState.showWarning("Disconnected from server")
                    }
                }
                ConnectionState.CONNECTING -> { }
                ConnectionState.RECONNECTING -> {
                    snackbarState.showInfo("Reconnecting...")
                }
            }
            previousState = connectionState
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp)
                .padding(bottom = 100.dp)
        ) {
            // Top Bar
            TopBar(
                onNavigateToSetup = onNavigateToSetup,
                onNavigateToSettings = onNavigateToSettings
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Clipboard History
            ClipboardHistorySection(
                history = clipboardHistory,
                onClear = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClearHistory()
                    snackbarState.showSuccess("History cleared")
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Floating Bottom Bar
        FloatingBottomBar(
            connectionState = connectionState,
            isConnecting = isConnecting,
            isSyncing = isSyncing,
            onConnect = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                
                // Check if server URL is configured before attempting to connect
                if (serverUrl.isBlank()) {
                    snackbarState.showWarning("Please configure server IP in Settings")
                    return@FloatingBottomBar
                }
                
                // Mark this as a user-initiated connection (to show "Connected" toast later)
                userInitiatedConnect = true
                isConnecting = true
                snackbarState.showInfo("Connecting...")
                onConnect()
                
                scope.launch {
                    delay(10000)
                    if (isConnecting && connectionState == ConnectionState.DISCONNECTED) {
                        isConnecting = false
                        userInitiatedConnect = false
                        snackbarState.showError("Connection failed", "Check server settings")
                    }
                }
            },
            onDisconnect = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDisconnect()
                snackbarState.showInfo("Disconnecting...")
            },
            onSync = {
                if (connectionState != ConnectionState.CONNECTED) {
                    snackbarState.showWarning("Not connected to server")
                    return@FloatingBottomBar
                }
                
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    isSyncing = true
                    try {
                        onSyncNow()
                        delay(500)
                        snackbarState.showSuccess("Clipboard synced!")
                    } catch (e: Exception) {
                        snackbarState.showError("Sync failed", e.message ?: e.toString())
                    } finally {
                        isSyncing = false
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )

        // Snackbar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
        ) {
            TailSyncSnackbarHost(snackbarState = snackbarState)
        }
    }
}

@Composable
private fun FloatingBottomBar(
    connectionState: ConnectionState,
    isConnecting: Boolean,
    isSyncing: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnectingState = connectionState == ConnectionState.CONNECTING || 
                            connectionState == ConnectionState.RECONNECTING
    
    val statusColor = when (connectionState) {
        ConnectionState.CONNECTED -> StatusConnected
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> StatusConnecting
        ConnectionState.DISCONNECTED -> StatusDisconnected
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .shadow(8.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = DarkSurface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "Connected"
                        ConnectionState.CONNECTING -> "Connecting..."
                        ConnectionState.RECONNECTING -> "Reconnecting..."
                        ConnectionState.DISCONNECTED -> "Disconnected"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))

            FilledTonalButton(
                onClick = if (isConnected) onDisconnect else onConnect,
                enabled = !isConnecting && !isConnectingState,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkSurfaceVariant),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isConnecting || isConnectingState) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        if (isConnected) "Disconnect" else "Connect",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            FilledTonalButton(
                onClick = onSync,
                enabled = isConnected && !isSyncing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isConnected) TailSyncPrimary else DarkSurfaceVariant,
                    contentColor = if (isConnected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledContainerColor = DarkSurfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.Black
                    )
                } else {
                    Icon(Icons.Rounded.Sync, null, Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    onNavigateToSetup: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TailSync",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onNavigateToSetup) {
                Icon(
                    Icons.Rounded.HelpOutline,
                    contentDescription = "Setup Guide",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ClipboardHistorySection(
    history: List<ClipboardHistoryItem>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Header with Clear button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Clips",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (history.isNotEmpty()) {
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        if (history.isEmpty()) {
            GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No clipboard history yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(history, key = { _, item -> item.timestamp }) { _, item ->
                    ClipboardHistoryItemCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun ClipboardHistoryItemCard(item: ClipboardHistoryItem) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var copied by remember { mutableStateOf(false) }
    
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    GlassmorphicCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (item.source == "server") Icons.Rounded.Computer else Icons.Rounded.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (item.source == "server") TailSyncPrimary else TailSyncSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (item.source == "server") "Laptop" else "Phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = formatTimestamp(item.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = item.text.take(150) + if (item.text.length > 150) "..." else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))
            
            FilledTonalButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("TailSync", item.text)
                    clipboard.setPrimaryClip(clip)
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 10.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (copied) "Copied!" else "Copy",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
        color = GlassBackground,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp
    ) {
        content()
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
