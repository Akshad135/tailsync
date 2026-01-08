package com.tailsync.app.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tailsync.app.MainActivity
import com.tailsync.app.network.ConnectionState
import kotlinx.coroutines.*

class SyncTileService : TileService() {

    private var mainService: MainService? = null
    private var isBound = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? MainService.LocalBinder
            mainService = binder?.getService()
            isBound = true
            updateTileState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mainService = null
            isBound = false
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        bindToService()
    }

    override fun onStopListening() {
        super.onStopListening()
        unbindFromService()
    }

    override fun onClick() {
        super.onClick()

        if (mainService != null && mainService!!.isConnected()) {
            // Sync clipboard now
            mainService?.syncClipboardNow()

            // Visual feedback - briefly change tile state
            qsTile?.let { tile ->
                tile.state = Tile.STATE_ACTIVE
                tile.updateTile()
            }

            // Reset tile state after brief delay
            scope.launch {
                delay(500)
                updateTileState()
            }
        } else {
            // Start service if not running
            startMainService()
        }
    }

    /**
     * Long press opens the app settings.
     * Note: This requires the tile to be registered with android:meta-data
     * and the user must have added the tile to their quick settings panel.
     */
    fun onLongClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("open_settings", true)
        }
        startActivityAndCollapse(intent)
    }

    private fun bindToService() {
        val intent = Intent(this, MainService::class.java)
        try {
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            // Service might not be running yet
        }
    }

    private fun unbindFromService() {
        if (isBound) {
            try {
                unbindService(serviceConnection)
            } catch (e: Exception) {
                // Ignore unbind errors
            }
            isBound = false
        }
    }

    private fun startMainService() {
        val intent = Intent(this, MainService::class.java).apply {
            action = MainService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Bind after starting
        scope.launch {
            delay(500)
            bindToService()
        }
    }

    private fun updateTileState() {
        qsTile?.let { tile ->
            val isConnected = mainService?.isConnected() == true

            tile.state = if (isConnected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (isConnected) "TailSync" else "TailSync Off"
            tile.contentDescription = if (isConnected) {
                "Connected. Tap to sync clipboard."
            } else {
                "Disconnected. Tap to connect."
            }
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        unbindFromService()
    }
}
