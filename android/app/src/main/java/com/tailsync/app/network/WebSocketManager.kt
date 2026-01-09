package com.tailsync.app.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class ClipboardPayload(
    val plainText: String,
    val htmlText: String?,
    val timestamp: Long,
    val source: String
)

class WebSocketManager {

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var serverUrl: String = ""
    private var serverPort: Int = 8765
    private var useSecureConnection: Boolean = false
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3  // Only retry 3 times
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onClipboardReceived: ((ClipboardPayload) -> Unit)? = null
    var onConnectionChanged: ((ConnectionState) -> Unit)? = null
    var onError: ((String, String) -> Unit)? = null  // title, details

    fun configure(url: String, port: Int, secureConnection: Boolean = false) {
        serverUrl = url.trim()
        serverPort = port
        useSecureConnection = secureConnection
    }

    fun connect() {
        if (serverUrl.isEmpty()) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        // Close existing connection if any (prevent multiple connections)
        webSocket?.close(1000, "Reconnecting")
        webSocket = null
        reconnectJob?.cancel()

        isManualDisconnect = false  // Reset when user initiates connection
        _connectionState.value = ConnectionState.CONNECTING
        onConnectionChanged?.invoke(ConnectionState.CONNECTING)

        try {
            val wsUrl = buildWebSocketUrl()
            val request = Request.Builder()
                .url(wsUrl)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    reconnectAttempts = 0
                    _connectionState.value = ConnectionState.CONNECTED
                    onConnectionChanged?.invoke(ConnectionState.CONNECTED)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = JSONObject(text)
                        val payload = ClipboardPayload(
                            plainText = json.optString("plain_text", ""),
                            htmlText = json.optString("html_text", null),
                            timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                            source = json.optString("source", "server")
                        )
                        onClipboardReceived?.invoke(payload)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    onConnectionChanged?.invoke(ConnectionState.DISCONNECTED)
                    // Only reconnect if not manually disconnected
                    if (!isManualDisconnect) {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val errorMessage = t.message ?: t.toString()
                    val errorDetails = buildString {
                        appendLine("URL: ${buildWebSocketUrl()}")
                        appendLine("Error: $errorMessage")
                        response?.let {
                            appendLine("Response Code: ${it.code}")
                            appendLine("Response Message: ${it.message}")
                        }
                        appendLine("Stack Trace:")
                        appendLine(t.stackTraceToString().take(500))
                    }
                    
                    _connectionState.value = ConnectionState.DISCONNECTED
                    onConnectionChanged?.invoke(ConnectionState.DISCONNECTED)
                    
                    // Only auto-reconnect if not manually disconnected
                    if (!isManualDisconnect) {
                        scheduleReconnect(errorDetails)
                    }
                }
            })
        } catch (e: Exception) {
            // Handle URL parsing errors or other exceptions
            val errorDetails = buildString {
                appendLine("Failed to create WebSocket connection")
                appendLine("URL: ${serverUrl}:${serverPort}")
                appendLine("Error: ${e.message ?: e.toString()}")
                appendLine("Stack Trace:")
                appendLine(e.stackTraceToString().take(500))
            }
            
            _connectionState.value = ConnectionState.DISCONNECTED
            onConnectionChanged?.invoke(ConnectionState.DISCONNECTED)
            onError?.invoke("Connection Failed", errorDetails)
        }
    }

    private var isManualDisconnect = false

    fun disconnect() {
        isManualDisconnect = true
        reconnectJob?.cancel()
        reconnectAttempts = maxReconnectAttempts // Prevent auto-reconnect
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        onConnectionChanged?.invoke(ConnectionState.DISCONNECTED)
    }

    fun sendClipboard(plainText: String, htmlText: String?) {
        val json = JSONObject().apply {
            put("plain_text", plainText)
            put("html_text", htmlText ?: JSONObject.NULL)
            put("timestamp", System.currentTimeMillis())
            put("source", "android")
        }
        webSocket?.send(json.toString())
    }

    private fun buildWebSocketUrl(): String {
        // Aggressively clean the URL - remove any invisible characters, BOM, zero-width spaces, etc.
        val cleanUrl = serverUrl
            .trim()
            .replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F\\u200B-\\u200D\\uFEFF\\u2028\\u2029]"), "") // Remove control chars and zero-width
            .replace(Regex("\\s+"), "") // Remove all whitespace
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")
            .trimEnd('/')
            .trim()
        val protocol = if (useSecureConnection) "wss" else "ws"
        return "$protocol://$cleanUrl:$serverPort/ws"
    }

    private var lastErrorDetails: String = ""

    private fun scheduleReconnect(errorDetails: String? = null) {
        if (errorDetails != null) {
            lastErrorDetails = errorDetails
        }
        
        if (reconnectAttempts >= maxReconnectAttempts) {
            // Max attempts reached, report error
            onError?.invoke(
                "Connection Failed",
                "Failed to connect after $maxReconnectAttempts attempts.\n\n$lastErrorDetails"
            )
            return
        }
        if (serverUrl.isEmpty()) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            onConnectionChanged?.invoke(ConnectionState.RECONNECTING)

            // Exponential backoff: 1s, 2s, 4s
            val delay = minOf(1000L * (1 shl reconnectAttempts), 4000L)
            reconnectAttempts++
            delay(delay)

            if (isActive) {
                connect()
            }
        }
    }

    fun resetReconnectAttempts() {
        reconnectAttempts = 0
    }

    fun destroy() {
        scope.cancel()
        disconnect()
        client.dispatcher.executorService.shutdown()
    }
}
