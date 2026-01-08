package com.tailsync.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.tailsync.app.service.MainService

/**
 * Transparent activity that handles app shortcut intents.
 * This activity immediately performs the action and finishes,
 * making it perfect for Samsung Routines and Edge Panel Tasks.
 */
class ShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent.action) {
            ACTION_SYNC -> {
                syncClipboard()
                Toast.makeText(this, "Syncing clipboard...", Toast.LENGTH_SHORT).show()
            }
            ACTION_SETTINGS -> {
                openSettings()
                return // Don't finish, let MainActivity handle it
            }
            ACTION_TOGGLE -> {
                toggleConnection()
                Toast.makeText(this, "Toggling connection...", Toast.LENGTH_SHORT).show()
            }
        }

        // Finish immediately for action shortcuts
        finish()
    }

    private fun syncClipboard() {
        val intent = Intent(this, MainService::class.java).apply {
            action = MainService.ACTION_SYNC
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_settings", true)
        }
        startActivity(intent)
        finish()
    }

    private fun toggleConnection() {
        // First ensure service is running, then toggle
        val startIntent = Intent(this, MainService::class.java).apply {
            action = MainService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(startIntent)
        } else {
            startService(startIntent)
        }

        // Send connect action (service will handle toggle logic)
        val connectIntent = Intent(this, MainService::class.java).apply {
            action = MainService.ACTION_CONNECT
        }
        startService(connectIntent)
    }

    companion object {
        const val ACTION_SYNC = "com.tailsync.app.action.SHORTCUT_SYNC"
        const val ACTION_SETTINGS = "com.tailsync.app.action.SHORTCUT_SETTINGS"
        const val ACTION_TOGGLE = "com.tailsync.app.action.SHORTCUT_TOGGLE"
    }
}
