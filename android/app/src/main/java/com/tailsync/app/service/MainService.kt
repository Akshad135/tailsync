package com.tailsync.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
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
    
    // Message state for snackbar (non-error feedback)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var isUpdatingClipboard = false
    private var lastSentContent: String = ""
    private var lastReceivedContent: String = ""

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
        if (!isForegroundStarted) {
            startForegroundService()
        }
        
        when (intent?.action) {
            ACTION_START -> { }
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
                // Small delay to let DataStore initialize
                delay(500)
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
    
    fun clearMessage() {
        _message.value = null
    }

    private fun setupClipboardListener() {
        clipboardHelper.addClipboardListener {
            if (!isUpdatingClipboard) {
                val content = clipboardHelper.readClipboard()
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
        if (payload.plainText.isBlank()) return
        
        isUpdatingClipboard = true
        lastReceivedContent = payload.plainText
        lastSentContent = payload.plainText
        try {
            clipboardHelper.writeClipboard(payload.plainText, payload.htmlText)
            updateLastSynced(payload.plainText, "server")
        } finally {
            scope.launch {
                delay(1000)
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

    /**
     * Connect using URL/port from DataStore
     */
    fun connect() {
        scope.launch {
            val url = settingsRepository.serverUrl.first()
            val port = settingsRepository.serverPort.first()
            connectWithUrl(url, port)
        }
    }
    
    /**
     * Connect with explicit URL and port (bypasses DataStore read)
     * This is used after saving settings to ensure we use the new values
     */
    fun connectWithUrl(url: String, port: Int) {
        if (url.isEmpty()) {
            // No URL configured - DashboardScreen will show appropriate feedback
            _connectionState.value = ConnectionState.DISCONNECTED
            updateNotification()
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        updateNotification()
        
        webSocketManager.configure(url, port)
        webSocketManager.resetReconnectAttempts()
        webSocketManager.connect()
    }

    fun disconnect() {
        webSocketManager.disconnect()
    }

    fun syncClipboardNow() {
        scope.launch {
            if (_connectionState.value != ConnectionState.CONNECTED) {
                val url = settingsRepository.serverUrl.first()
                val port = settingsRepository.serverPort.first()
                
                if (url.isEmpty()) {
                    _message.value = "Server not configured"
                    return@launch
                }
                
                webSocketManager.configure(url, port)
                webSocketManager.resetReconnectAttempts()
                webSocketManager.connect()
                
                var waited = 0
                while (_connectionState.value != ConnectionState.CONNECTED && waited < 3000) {
                    delay(100)
                    waited += 100
                }
                
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    _message.value = "Could not connect to server"
                    return@launch
                }
            }
            
            val content = clipboardHelper.readClipboard()
            if (content != null && content.plainText.isNotBlank()) {
                lastSentContent = content.plainText
                webSocketManager.sendClipboard(content.plainText, content.htmlText)
                updateLastSynced(content.plainText, "phone")
                _message.value = "Clipboard synced!"
            } else {
                _message.value = "Clipboard is empty"
            }
        }
    }

    private fun syncClipboardContent(plainText: String, htmlText: String?) {
        scope.launch {
            if (_connectionState.value != ConnectionState.CONNECTED) {
                val url = settingsRepository.serverUrl.first()
                val port = settingsRepository.serverPort.first()
                
                if (url.isEmpty()) {
                    _message.value = "Server not configured"
                    return@launch
                }
                
                webSocketManager.configure(url, port)
                webSocketManager.resetReconnectAttempts()
                webSocketManager.connect()
                
                var waited = 0
                while (_connectionState.value != ConnectionState.CONNECTED && waited < 3000) {
                    delay(100)
                    waited += 100
                }
                
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    _message.value = "Could not connect"
                    return@launch
                }
            }
            
            lastSentContent = plainText
            lastReceivedContent = plainText
            webSocketManager.sendClipboard(plainText, htmlText)
            updateLastSynced(plainText, "phone")
            
            _message.value = "Clipboard synced!"
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
