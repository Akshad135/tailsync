package com.tailsync.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.tailsync.app.ClipboardSyncActivity
import com.tailsync.app.MainActivity
import com.tailsync.app.R
import com.tailsync.app.TailSyncApp
import com.tailsync.app.data.SettingsRepository
import com.tailsync.app.network.ClipboardPayload
import com.tailsync.app.network.ConnectionState
import com.tailsync.app.network.WebSocketManager
import com.tailsync.app.util.ClipboardHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

class MainService : Service() {

    private val binder = LocalBinder()
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var clipboardHelper: ClipboardHelper
    private lateinit var settingsRepository: SettingsRepository
    private var wakeLock: PowerManager.WakeLock? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _lastSyncedText = MutableStateFlow("")
    val lastSyncedText: StateFlow<String> = _lastSyncedText

    private val _lastSyncedTime = MutableStateFlow(0L)
    val lastSyncedTime: StateFlow<Long> = _lastSyncedTime

    // Error state for dialog
    private val _errorTitle = MutableStateFlow<String?>(null)
    val errorTitle: StateFlow<String?> = _errorTitle
    
    private val _errorDetails = MutableStateFlow<String?>(null)
    val errorDetails: StateFlow<String?> = _errorDetails

    private var isUpdatingClipboard = false
    private var lastSentContent: String = ""
    private var lastReceivedContent: String = ""  // Track received content to prevent echo

    inner class LocalBinder : Binder() {
        fun getService(): MainService = this@MainService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        
        settingsRepository = SettingsRepository(this)
        clipboardHelper = ClipboardHelper(this)
        webSocketManager = WebSocketManager()

        setupWebSocketCallbacks()
        setupClipboardListener()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always ensure foreground mode for any action
        if (!isForegroundStarted) {
            startForegroundService()
        }
        
        when (intent?.action) {
            ACTION_START -> { /* Already handled above */ }
            ACTION_STOP -> {
                disconnect()
                stopSelf()
            }
            ACTION_SYNC -> syncClipboardNow()
            ACTION_SYNC_WITH_CONTENT -> {
                val plainText = intent.getStringExtra("plain_text") ?: ""
                val htmlText = intent.getStringExtra("html_text")
                if (plainText.isNotBlank()) {
                    syncClipboardContent(plainText, htmlText)
                }
            }
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    private var isForegroundStarted = false

    private fun startForegroundService() {
        if (isForegroundStarted) return
        isForegroundStarted = true
        
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        scope.launch {
            val autoConnect = settingsRepository.autoConnect.first()
            if (autoConnect) {
                connect()
            }
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Sync action - launch ClipboardSyncActivity to read clipboard in foreground
        val syncIntent = Intent(this, ClipboardSyncActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val syncPendingIntent = PendingIntent.getActivity(
            this, 2, syncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isConnected = _connectionState.value == ConnectionState.CONNECTED ||
                          _connectionState.value == ConnectionState.CONNECTING ||
                          _connectionState.value == ConnectionState.RECONNECTING

        // Dynamic Connect/Disconnect action
        val toggleIntent = Intent(this, MainService::class.java).apply {
            action = if (isConnected) ACTION_DISCONNECT else ACTION_CONNECT
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stateText = when (_connectionState.value) {
            ConnectionState.CONNECTED -> "Connected"
            ConnectionState.CONNECTING -> "Connecting..."
            ConnectionState.RECONNECTING -> "Reconnecting..."
            ConnectionState.DISCONNECTED -> "Disconnected"
        }

        val toggleLabel = if (isConnected) "Stop" else "Start"
        val toggleIcon = if (isConnected) R.drawable.ic_close else R.drawable.ic_sync

        return NotificationCompat.Builder(this, TailSyncApp.CHANNEL_ID)
            .setContentTitle("TailSync")
            .setContentText(stateText)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_sync, "Sync", syncPendingIntent)
            .addAction(toggleIcon, toggleLabel, togglePendingIntent)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun setupWebSocketCallbacks() {
        webSocketManager.onConnectionChanged = { state ->
            _connectionState.value = state
            updateNotification()
        }

        webSocketManager.onClipboardReceived = { payload ->
            handleIncomingClipboard(payload)
        }
        
        webSocketManager.onError = { title, details ->
            _errorTitle.value = title
            _errorDetails.value = details
        }
    }
    
    fun clearError() {
        _errorTitle.value = null
        _errorDetails.value = null
    }

    private fun setupClipboardListener() {
        clipboardHelper.addClipboardListener {
            if (!isUpdatingClipboard) {
                val content = clipboardHelper.readClipboard()
                // Skip if: null, empty, same as last sent, or same as last received (echo prevention)
                if (content != null && 
                    content.plainText.isNotBlank() && 
                    content.plainText != lastSentContent &&
                    content.plainText != lastReceivedContent) {
                    lastSentContent = content.plainText
                    webSocketManager.sendClipboard(content.plainText, content.htmlText)
                    updateLastSynced(content.plainText, "phone")
                }
            }
        }
    }

    private fun handleIncomingClipboard(payload: ClipboardPayload) {
        // Skip empty payloads
        if (payload.plainText.isBlank()) return
        
        isUpdatingClipboard = true
        lastReceivedContent = payload.plainText  // Track to prevent echo
        lastSentContent = payload.plainText  // Also set as sent to prevent duplicate sends
        try {
            clipboardHelper.writeClipboard(payload.plainText, payload.htmlText)
            updateLastSynced(payload.plainText, "server")
        } finally {
            // Delay resetting flag to avoid catching our own clipboard change
            scope.launch {
                delay(1000)  // Increased delay for safety
                isUpdatingClipboard = false
            }
        }
    }

    private fun updateLastSynced(text: String, source: String = "phone") {
        val time = System.currentTimeMillis()
        _lastSyncedText.value = text
        _lastSyncedTime.value = time

        scope.launch {
            settingsRepository.addToHistory(text, source)
        }
    }

    fun connect() {
        scope.launch {
            val url = settingsRepository.serverUrl.first()
            val port = settingsRepository.serverPort.first()
            
            if (url.isEmpty()) {
                android.widget.Toast.makeText(
                    this@MainService,
                    "Please configure server URL in Settings",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                // Force state update to trigger UI refresh
                _connectionState.value = ConnectionState.DISCONNECTED
                updateNotification()
                return@launch
            }
            
            // Force CONNECTING state to trigger UI updates
            _connectionState.value = ConnectionState.CONNECTING
            updateNotification()
            
            webSocketManager.configure(url, port)
            webSocketManager.resetReconnectAttempts()
            webSocketManager.connect()
        }
    }

    fun disconnect() {
        webSocketManager.disconnect()
    }

    fun syncClipboardNow() {
        scope.launch {
            // If not connected, connect first and wait
            if (_connectionState.value != ConnectionState.CONNECTED) {
                val url = settingsRepository.serverUrl.first()
                val port = settingsRepository.serverPort.first()
                
                if (url.isEmpty()) {
                    android.widget.Toast.makeText(
                        this@MainService,
                        "Server not configured. Open app to set up.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                webSocketManager.configure(url, port)
                webSocketManager.resetReconnectAttempts()
                webSocketManager.connect()
                
                // Wait for connection (max 3 seconds)
                var waited = 0
                while (_connectionState.value != ConnectionState.CONNECTED && waited < 3000) {
                    delay(100)
                    waited += 100
                }
                
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    android.widget.Toast.makeText(
                        this@MainService,
                        "Could not connect to server",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
            }
            
            // Now sync
            val content = clipboardHelper.readClipboard()
            if (content != null && content.plainText.isNotBlank()) {
                lastSentContent = content.plainText
                webSocketManager.sendClipboard(content.plainText, content.htmlText)
                updateLastSynced(content.plainText, "phone")
                android.widget.Toast.makeText(
                    this@MainService,
                    "Clipboard synced!",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                android.widget.Toast.makeText(
                    this@MainService,
                    "Clipboard is empty",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Sync clipboard content that was already read by ClipboardSyncActivity.
     * This bypasses the background clipboard restriction.
     */
    private fun syncClipboardContent(plainText: String, htmlText: String?) {
        scope.launch {
            // Connect if not connected
            if (_connectionState.value != ConnectionState.CONNECTED) {
                val url = settingsRepository.serverUrl.first()
                val port = settingsRepository.serverPort.first()
                
                if (url.isEmpty()) {
                    android.widget.Toast.makeText(
                        this@MainService,
                        "Server not configured",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                
                webSocketManager.configure(url, port)
                webSocketManager.resetReconnectAttempts()
                webSocketManager.connect()
                
                // Wait for connection (max 3 seconds)
                var waited = 0
                while (_connectionState.value != ConnectionState.CONNECTED && waited < 3000) {
                    delay(100)
                    waited += 100
                }
                
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    android.widget.Toast.makeText(
                        this@MainService,
                        "Could not connect",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
            }
            
            // Send the clipboard
            lastSentContent = plainText
            lastReceivedContent = plainText  // Prevent echo
            webSocketManager.sendClipboard(plainText, htmlText)
            updateLastSynced(plainText, "phone")
            
            android.widget.Toast.makeText(
                this@MainService,
                "Clipboard synced!",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "TailSync::WebSocketWakeLock"
        )
        wakeLock?.acquire(Long.MAX_VALUE)
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        webSocketManager.destroy()
        releaseWakeLock()
    }

    companion object {
        const val ACTION_START = "com.tailsync.app.action.START"
        const val ACTION_STOP = "com.tailsync.app.action.STOP"
        const val ACTION_SYNC = "com.tailsync.app.action.SYNC"
        const val ACTION_SYNC_WITH_CONTENT = "com.tailsync.app.action.SYNC_WITH_CONTENT"
        const val ACTION_CONNECT = "com.tailsync.app.action.CONNECT"
        const val ACTION_DISCONNECT = "com.tailsync.app.action.DISCONNECT"
        const val NOTIFICATION_ID = 1001
    }
}
