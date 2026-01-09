package com.tailsync.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.tailsync.app.service.MainService
import com.tailsync.app.util.ClipboardHelper

/**
 * Invisible/translucent activity that briefly comes to foreground to read clipboard.
 * 
 * This is the KDE Connect approach:
 * - Android 10+ blocks background clipboard access
 * - This activity becomes foreground, reads clipboard, then finishes
 * - Works from notification actions, shortcuts, etc.
 * 
 * IMPORTANT: Clipboard must be read in onWindowFocusChanged(), not onCreate().
 * Android 10+ requires window focus to access clipboard content.
 */
class ClipboardSyncActivity : ComponentActivity() {
    
    private var hasSynced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Don't read clipboard here - wait for window focus
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        
        // Only sync when we gain window focus (Android 10+ requires this for clipboard access)
        if (hasFocus && !hasSynced) {
            performSync()
        }
    }
    
    private fun performSync() {
        // Prevent double sync
        if (hasSynced) return
        hasSynced = true
        
        // Read clipboard while we're in foreground
        val clipboardHelper = ClipboardHelper(this)
        val content = clipboardHelper.readClipboard()
        
        if (content != null && content.plainText.isNotBlank()) {
            // Send to service for syncing
            val syncIntent = Intent(this, MainService::class.java).apply {
                action = MainService.ACTION_SYNC_WITH_CONTENT
                putExtra("plain_text", content.plainText)
                putExtra("html_text", content.htmlText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(syncIntent)
            } else {
                startService(syncIntent)
            }
            Toast.makeText(this, "Syncing clipboard...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
        }
        
        // Finish immediately
        finish()
    }
}
