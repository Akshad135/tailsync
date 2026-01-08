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
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var onClipboardReceived: ((ClipboardPayload) -> Unit)? = null
    var onConnectionChanged: ((ConnectionState) -> Unit)? = null

    fun configure(url: String, port: Int) {
        serverUrl = url.trim()
        serverPort = port
    }

    fun connect() {
        if (serverUrl.isEmpty()) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        onConnectionChanged?.invoke(ConnectionState.CONNECTING)

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
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
                _connectionState.value = ConnectionState.DISCONNECTED
                onConnectionChanged?.invoke(ConnectionState.DISCONNECTED)
                scheduleReconnect()
            }
        })
    }

    fun disconnect() {
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
        val cleanUrl = serverUrl
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")
            .trimEnd('/')
        return "ws://$cleanUrl:$serverPort/ws"
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) return
        if (serverUrl.isEmpty()) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            onConnectionChanged?.invoke(ConnectionState.RECONNECTING)

            // Exponential backoff: 1s, 2s, 4s, 8s, ... up to 60s
            val delay = minOf(1000L * (1 shl reconnectAttempts), 60000L)
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
