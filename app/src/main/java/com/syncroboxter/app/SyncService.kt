package com.syncroboxter.app

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class SyncService : Service() {

    private val scope       = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connection: SyncConnection? = null
    private var syncJob: Job? = null

    companion object {
        const val CHANNEL_ID    = "syncroboxter_channel"
        const val NOTIF_ID      = 1
        const val ACTION_SYNC   = "com.syncroboxter.SYNC"
        const val ACTION_STOP   = "com.syncroboxter.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_SYNC -> startForegroundAndSync()
            else        -> startForegroundAndSync()
        }
        return START_STICKY
    }

    private fun startForegroundAndSync() {
        startForeground(NOTIF_ID, buildNotification("Sincronizando..."))
        val prefs = PrefsManager(this)

        syncJob = scope.launch {
            try {
                val conn = SyncConnection(prefs.host, prefs.port, prefs.secret)
                conn.connect().getOrThrow()
                connection = conn

                val engine = SyncEngine(
                    conn      = conn,
                    localBase = File(prefs.localFolder),
                    onLog     = { /* silencioso en background */ }
                )
                val result = engine.syncAll()
                prefs.lastSyncTime = System.currentTimeMillis()

                updateNotification("✔ Sync: ${result.copied} archivo(s)")
                conn.quit(); conn.close()

                delay(3000)
                stopSelf()
            } catch (e: Exception) {
                updateNotification("✖ Error: ${e.message}")
                delay(3000)
                stopSelf()
            }
        }
    }

    private fun buildNotification(msg: String): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SyncService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Syncroboxter")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .addAction(android.R.drawable.ic_delete, "Detener", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(msg: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(msg))
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID, "Syncroboxter",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Sincronización en segundo plano" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        syncJob?.cancel()
        connection?.close()
        scope.cancel()
    }
}
