package com.tailsync.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tailsync.app.data.SettingsRepository
import com.tailsync.app.service.MainService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            // Check if auto-connect is enabled
            val settingsRepository = SettingsRepository(context)
            val autoConnect = runBlocking {
                settingsRepository.autoConnect.first()
            }

            if (autoConnect) {
                val serviceIntent = Intent(context, MainService::class.java).apply {
                    action = MainService.ACTION_START
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
