package com.syncroboxter.app

import android.content.Context
import android.content.SharedPreferences

// ══════════════════════════════════════════════════════
//  CONFIGURACIÓN PERSISTENTE
// ══════════════════════════════════════════════════════
class PrefsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("syncroboxter", Context.MODE_PRIVATE)

    var host: String
        get()      = prefs.getString("host", "")!!
        set(value) = prefs.edit().putString("host", value).apply()

    var port: Int
        get()      = prefs.getInt("port", Protocol.DEFAULT_PORT)
        set(value) = prefs.edit().putInt("port", value).apply()

    var secret: String
        get()      = prefs.getString("secret", Protocol.DEFAULT_SECRET)!!
        set(value) = prefs.edit().putString("secret", value).apply()

    var localFolder: String
        get()      = prefs.getString("local_folder", "")!!
        set(value) = prefs.edit().putString("local_folder", value).apply()

    var autoSync: Boolean
        get()      = prefs.getBoolean("auto_sync", false)
        set(value) = prefs.edit().putBoolean("auto_sync", value).apply()

    var autoSyncSeconds: Int
        get()      = prefs.getInt("auto_sync_seconds", 30)
        set(value) = prefs.edit().putInt("auto_sync_seconds", value).apply()

    var lastSyncTime: Long
        get()      = prefs.getLong("last_sync", 0L)
        set(value) = prefs.edit().putLong("last_sync", value).apply()

    var connectionMode: String   // "wifi" | "bluetooth" | "internet"
        get()      = prefs.getString("connection_mode", "wifi")!!
        set(value) = prefs.edit().putString("connection_mode", value).apply()
}
