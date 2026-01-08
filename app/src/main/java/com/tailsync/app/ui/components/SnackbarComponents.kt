package com.tailsync.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Snackbar types for different feedback scenarios
 */
enum class SnackbarType {
    SUCCESS,
    ERROR,
    INFO,
    WARNING
}

data class SnackbarMessage(
    val message: String,
    val type: SnackbarType = SnackbarType.INFO,
    val errorDetails: String? = null,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null
)

/**
 * State holder for snackbar management
 */
class SnackbarState {
    private val _currentMessage = mutableStateOf<SnackbarMessage?>(null)
    val currentMessage: State<SnackbarMessage?> = _currentMessage

    fun showSuccess(message: String) {
        _currentMessage.value = SnackbarMessage(message, SnackbarType.SUCCESS)
    }

    fun showError(message: String, errorDetails: String? = null) {
        _currentMessage.value = SnackbarMessage(
            message = message,
            type = SnackbarType.ERROR,
            errorDetails = errorDetails,
            actionLabel = if (errorDetails != null) "Copy Error" else null
        )
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

/**
 * Custom snackbar host with styled snackbars
 */
@Composable
fun TailSyncSnackbarHost(
    snackbarState: SnackbarState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val message = snackbarState.currentMessage.value

    // Auto-dismiss after delay
    LaunchedEffect(message) {
        if (message != null) {
            val duration = when (message.type) {
                SnackbarType.ERROR -> 5000L
                else -> 3000L
            }
            delay(duration)
            snackbarState.dismiss()
        }
    }

    if (message != null) {
        Snackbar(
            modifier = modifier.padding(16.dp),
            containerColor = when (message.type) {
                SnackbarType.SUCCESS -> MaterialTheme.colorScheme.tertiary
                SnackbarType.ERROR -> MaterialTheme.colorScheme.error
                SnackbarType.WARNING -> MaterialTheme.colorScheme.secondary
                SnackbarType.INFO -> MaterialTheme.colorScheme.primary
            },
            contentColor = when (message.type) {
                SnackbarType.SUCCESS -> MaterialTheme.colorScheme.onTertiary
                SnackbarType.ERROR -> MaterialTheme.colorScheme.onError
                SnackbarType.WARNING -> MaterialTheme.colorScheme.onSecondary
                SnackbarType.INFO -> MaterialTheme.colorScheme.onPrimary
            },
            action = {
                if (message.errorDetails != null) {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Error Details", message.errorDetails)
                            clipboard.setPrimaryClip(clip)
                            snackbarState.showInfo("Error copied to clipboard")
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }
                }
            },
            dismissAction = {
                IconButton(onClick = { snackbarState.dismiss() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = when (message.type) {
                            SnackbarType.SUCCESS -> MaterialTheme.colorScheme.onTertiary
                            SnackbarType.ERROR -> MaterialTheme.colorScheme.onError
                            SnackbarType.WARNING -> MaterialTheme.colorScheme.onSecondary
                            SnackbarType.INFO -> MaterialTheme.colorScheme.onPrimary
                        }
                    )
                }
            }
        ) {
            Text(message.message)
        }
    }
}

/**
 * Loading indicator overlay
 */
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    message: String = "Processing...",
    modifier: Modifier = Modifier
) {
    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/**
 * Error dialog with copy button for detailed error information
 */
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
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = "An error occurred. You can copy the details below for debugging:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
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
                    val clip = ClipData.newPlainText("Error Details", errorDetails)
                    clipboard.setPrimaryClip(clip)
                    copied = true
                }
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (copied) "Copied!" else "Copy Error")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
