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

    private var isUpdatingClipboard = false
    private var lastSentContent: String = ""

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
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_STOP -> stopSelf()
            ACTION_SYNC -> syncClipboardNow()
            ACTION_CONNECT -> connect()
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    private fun startForegroundService() {
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

        val disconnectIntent = Intent(this, MainService::class.java).apply {
            action = ACTION_STOP
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stateText = when (_connectionState.value) {
            ConnectionState.CONNECTED -> "Connected"
            ConnectionState.CONNECTING -> "Connecting..."
            ConnectionState.RECONNECTING -> "Reconnecting..."
            ConnectionState.DISCONNECTED -> "Disconnected"
        }

        return NotificationCompat.Builder(this, TailSyncApp.CHANNEL_ID)
            .setContentTitle("TailSync")
            .setContentText(stateText)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_close, "Stop", disconnectPendingIntent)
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
    }

    private fun setupClipboardListener() {
        clipboardHelper.addClipboardListener {
            if (!isUpdatingClipboard) {
                val content = clipboardHelper.readClipboard()
                if (content != null && content.plainText != lastSentContent) {
                    lastSentContent = content.plainText
                    webSocketManager.sendClipboard(content.plainText, content.htmlText)
                    updateLastSynced(content.plainText)
                }
            }
        }
    }

    private fun handleIncomingClipboard(payload: ClipboardPayload) {
        isUpdatingClipboard = true
        try {
            clipboardHelper.writeClipboard(payload.plainText, payload.htmlText)
            updateLastSynced(payload.plainText)
        } finally {
            // Delay resetting flag to avoid catching our own clipboard change
            scope.launch {
                delay(500)
                isUpdatingClipboard = false
            }
        }
    }

    private fun updateLastSynced(text: String) {
        val time = System.currentTimeMillis()
        _lastSyncedText.value = text
        _lastSyncedTime.value = time

        scope.launch {
            settingsRepository.setLastSynced(text, time)
        }
    }

    fun connect() {
        scope.launch {
            val url = settingsRepository.serverUrl.first()
            val port = settingsRepository.serverPort.first()
            
            if (url.isNotEmpty()) {
                webSocketManager.configure(url, port)
                webSocketManager.resetReconnectAttempts()
                webSocketManager.connect()
            }
        }
    }

    fun disconnect() {
        webSocketManager.disconnect()
    }

    fun syncClipboardNow() {
        val content = clipboardHelper.readClipboard()
        if (content != null) {
            lastSentContent = content.plainText
            webSocketManager.sendClipboard(content.plainText, content.htmlText)
            updateLastSynced(content.plainText)
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
        const val ACTION_CONNECT = "com.tailsync.app.action.CONNECT"
        const val ACTION_DISCONNECT = "com.tailsync.app.action.DISCONNECT"
        const val NOTIFICATION_ID = 1001
    }
}
