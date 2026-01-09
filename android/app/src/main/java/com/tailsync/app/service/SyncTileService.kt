package com.tailsync.app.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tailsync.app.ClipboardSyncActivity
import kotlinx.coroutines.*

/**
 * Quick Settings tile that syncs clipboard when tapped.
 * Launches ClipboardSyncActivity to read clipboard in foreground context.
 */
class SyncTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        
        // Launch ClipboardSyncActivity to read clipboard in foreground
        val syncIntent = Intent(this, ClipboardSyncActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivityAndCollapse(syncIntent)

        // Visual feedback
        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Syncing..."
            tile.updateTile()
        }

        // Reset tile state after brief delay
        scope.launch {
            delay(1500)
            updateTileState()
        }
    }

    private fun updateTileState() {
        qsTile?.let { tile ->
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Sync Clipboard"
            tile.contentDescription = "Tap to sync clipboard with server"
            tile.updateTile()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
