package com.tailsync.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
    onSyncNow: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarState = rememberSnackbarState()
    
    var isSyncing by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    // Track connection state changes for feedback
    var previousState by remember { mutableStateOf(connectionState) }
    LaunchedEffect(connectionState) {
        if (previousState != connectionState) {
            when (connectionState) {
                ConnectionState.CONNECTED -> {
                    snackbarState.showSuccess("Connected to server")
                    isConnecting = false
                }
                ConnectionState.DISCONNECTED -> {
                    if (previousState == ConnectionState.CONNECTED) {
                        snackbarState.showWarning("Disconnected from server")
                    }
                    isConnecting = false
                }
                ConnectionState.CONNECTING -> {
                    // No snackbar, status indicator shows this
                }
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
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
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

                Row {
                    IconButton(onClick = onNavigateToSetup) {
                        Icon(
                            Icons.Outlined.Help,
                            contentDescription = "Setup Guide",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Connection Status Card
            ConnectionStatusCard(
                connectionState = connectionState,
                isConnecting = isConnecting,
                onConnect = {
                    isConnecting = true
                    snackbarState.showInfo("Connecting...")
                    onConnect()
                },
                onDisconnect = {
                    onDisconnect()
                    snackbarState.showInfo("Disconnecting...")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Clipboard History Section (scrollable, takes remaining space)
            ClipboardHistorySection(
                history = clipboardHistory,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Sync Now Button (fixed at bottom)
            SyncNowButton(
                isConnected = connectionState == ConnectionState.CONNECTED,
                isSyncing = isSyncing,
                onClick = {
                    if (connectionState != ConnectionState.CONNECTED) {
                        snackbarState.showWarning("Not connected to server")
                        return@SyncNowButton
                    }
                    
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        isSyncing = true
                        try {
                            onSyncNow()
                            delay(500)
                            snackbarState.showSuccess("Clipboard synced!")
                        } catch (e: Exception) {
                            snackbarState.showError(
                                "Sync failed",
                                e.message ?: e.toString()
                            )
                        } finally {
                            isSyncing = false
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
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
private fun ClipboardHistorySection(
    history: List<ClipboardHistoryItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Clipboard History",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (history.isEmpty()) {
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No clipboard history yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { item ->
                    ClipboardHistoryItemCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun ClipboardHistoryItemCard(
    item: ClipboardHistoryItem
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    
    // Reset copied state after a delay
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
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
                        if (item.source == "server") Icons.Default.Computer else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (item.source == "server") TailSyncPrimary else TailSyncSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (item.source == "server") "From Laptop" else "From Phone",
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.text.take(150) + if (item.text.length > 150) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            FilledTonalButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("TailSync", item.text)
                    clipboard.setPrimaryClip(clip)
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(
                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
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
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = when (connectionState) {
            ConnectionState.CONNECTED -> StatusConnected
            ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> StatusConnecting
            ConnectionState.DISCONNECTED -> StatusDisconnected
        },
        animationSpec = tween(300),
        label = "statusColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isConnectingState = connectionState == ConnectionState.CONNECTING || 
                            connectionState == ConnectionState.RECONNECTING

    GlassmorphicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnectingState) statusColor.copy(alpha = pulseAlpha)
                        else statusColor
                    )
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "Connected"
                        ConnectionState.CONNECTING -> "Connecting..."
                        ConnectionState.RECONNECTING -> "Reconnecting..."
                        ConnectionState.DISCONNECTED -> "Disconnected"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "Tailscale server active"
                        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> "Establishing connection..."
                        ConnectionState.DISCONNECTED -> "Tap Connect to start"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledTonalButton(
                onClick = if (connectionState == ConnectionState.CONNECTED) onDisconnect else onConnect,
                enabled = !isConnecting && !isConnectingState,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (connectionState == ConnectionState.CONNECTED)
                        StatusDisconnected.copy(alpha = 0.2f)
                    else
                        StatusConnected.copy(alpha = 0.2f)
                )
            ) {
                if (isConnecting || isConnectingState) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        if (connectionState == ConnectionState.CONNECTED) "Disconnect" else "Connect"
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncNowButton(
    isConnected: Boolean,
    isSyncing: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed || isSyncing) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )

    val gradient = Brush.linearGradient(
        colors = if (isConnected && !isSyncing) {
            listOf(TailSyncPrimary, TailSyncSecondary)
        } else {
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant
            )
        }
    )

    Button(
        onClick = onClick,
        enabled = isConnected && !isSyncing,
        modifier = Modifier
            .size(140.dp)
            .scale(scale),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient, CircleShape)
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassBorder,
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = "Sync",
                        modifier = Modifier.size(40.dp),
                        tint = if (isConnected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isSyncing) "SYNCING" else "SYNC",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isConnected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
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
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = GlassBorder,
                shape = RoundedCornerShape(20.dp)
            ),
        color = GlassBackground,
        shape = RoundedCornerShape(20.dp)
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
