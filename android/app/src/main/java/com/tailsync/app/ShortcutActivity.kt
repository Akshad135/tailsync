package com.tailsync.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Transparent activity that handles app shortcut intents.
 * Immediately launches ClipboardSyncActivity for foreground clipboard access.
 */
class ShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch ClipboardSyncActivity to read clipboard in foreground
        val syncIntent = Intent(this, ClipboardSyncActivity::class.java)
        startActivity(syncIntent)
        
        finish()
    }
}
