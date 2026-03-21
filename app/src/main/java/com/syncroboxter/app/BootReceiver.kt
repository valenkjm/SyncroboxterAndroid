package com.syncroboxter.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PrefsManager(context)
            if (prefs.autoSync && prefs.host.isNotEmpty()) {
                context.startForegroundService(
                    Intent(context, SyncService::class.java).apply {
                        action = SyncService.ACTION_SYNC
                    }
                )
            }
        }
    }
}
