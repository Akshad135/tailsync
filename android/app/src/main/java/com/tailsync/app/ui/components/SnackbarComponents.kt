package com.tailsync.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tailsync.app.ui.theme.*
import kotlinx.coroutines.delay

enum class SnackbarType {
    SUCCESS, ERROR, INFO, WARNING
}

data class SnackbarMessage(
    val message: String,
    val type: SnackbarType = SnackbarType.INFO,
    val errorDetails: String? = null
)

class SnackbarState {
    private val _currentMessage = mutableStateOf<SnackbarMessage?>(null)
    val currentMessage: State<SnackbarMessage?> = _currentMessage

    fun showSuccess(message: String) {
        _currentMessage.value = SnackbarMessage(message, SnackbarType.SUCCESS)
    }

    fun showError(message: String, errorDetails: String? = null) {
        _currentMessage.value = SnackbarMessage(message, SnackbarType.ERROR, errorDetails)
    }

    fun showInfo(message: String) {
        _currentMessage.value = SnackbarMessage(message, SnackbarType.INFO)
    }

    fun showWarning(message: String) {
        _currentMessage.value = SnackbarMessage(message, SnackbarType.WARNING)
    }

    fun dismiss() {
        _currentMessage.value = null
    }
}

@Composable
fun rememberSnackbarState(): SnackbarState {
    return remember { SnackbarState() }
}

@Composable
fun TailSyncSnackbarHost(
    snackbarState: SnackbarState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val message = snackbarState.currentMessage.value

    // Auto-dismiss
    LaunchedEffect(message) {
        if (message != null) {
            val duration = if (message.type == SnackbarType.ERROR) 5000L else 3000L
            delay(duration)
            snackbarState.dismiss()
        }
    }

    // Simple fade animation only
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        if (message != null) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                shape = RoundedCornerShape(14.dp),
                containerColor = when (message.type) {
                    SnackbarType.SUCCESS -> StatusConnected
                    SnackbarType.ERROR -> StatusDisconnected
                    SnackbarType.WARNING -> StatusConnecting
                    SnackbarType.INFO -> TailSyncPrimary
                },
                contentColor = DarkBackground,
                action = {
                    if (message.errorDetails != null) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Error", message.errorDetails)
                                clipboard.setPrimaryClip(clip)
                                snackbarState.showInfo("Error copied")
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = DarkBackground)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }
                },
                dismissAction = {
                    IconButton(onClick = { snackbarState.dismiss() }) {
                        Icon(
                            Icons.Rounded.Close,
                            "Dismiss",
                            tint = DarkBackground.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            ) {
                Text(message.message, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun ErrorDialog(
    title: String,
    errorDetails: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = StatusDisconnected)
        },
        text = {
            Column {
                Text(
                    "An error occurred. Copy the details below for debugging:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Surface(color = DarkSurfaceElevated, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = errorDetails.take(500) + if (errorDetails.length > 500) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Error", errorDetails))
                    copied = true
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied!" else "Copy Error")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                Text("Close")
            }
        }
    )
}
