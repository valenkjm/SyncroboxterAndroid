package com.syncroboxter.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var tvLog: TextView
    private lateinit var btnSync: Button
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvLastSync: TextView

    private var connection: SyncConnection? = null
    private var syncing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        supportActionBar?.title = "Syncroboxter"

        prefs = PrefsManager(this)

        tvLog       = findViewById(R.id.tvLog)
        btnSync     = findViewById(R.id.btnSync)
        btnConnect  = findViewById(R.id.btnConnect)
        tvStatus    = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)
        tvProgress  = findViewById(R.id.tvProgress)
        tvLastSync  = findViewById(R.id.tvLastSync)

        updateLastSync()
        updateStatus("Desconectado")

        btnConnect.setOnClickListener {
            if (prefs.host.isEmpty()) {
                startActivity(Intent(this, ConnectionActivity::class.java))
            } else {
                toggleConnection()
            }
        }

        btnSync.setOnClickListener {
            if (connection == null || !connection!!.isConnected) {
                toast("Conectate primero al servidor")
                return@setOnClickListener
            }
            if (prefs.localFolder.isEmpty()) {
                toast("Configurá la carpeta local primero")
                startActivity(Intent(this, ConnectionActivity::class.java))
                return@setOnClickListener
            }
            startSync()
        }

        // Si ya hay datos guardados, intentar conectar automáticamente
        if (prefs.host.isNotEmpty()) {
            btnConnect.text = "Conectar a ${prefs.host}"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, ConnectionActivity::class.java))
                true
            }
            R.id.action_files -> {
                startActivity(Intent(this, FileBrowserActivity::class.java))
                true
            }
            R.id.action_clear_log -> {
                tvLog.text = ""
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ── Conexión ──────────────────────────────────────────────────────────
    private fun toggleConnection() {
        if (connection?.isConnected == true) {
            connection?.quit()
            connection?.close()
            connection = null
            btnConnect.text = "Conectar"
            updateStatus("Desconectado")
            log("🔌 Desconectado del servidor")
            btnSync.isEnabled = false
        } else {
            connectToServer()
        }
    }

    private fun connectToServer() {
        if (prefs.host.isEmpty()) {
            toast("Configurá el servidor primero")
            startActivity(Intent(this, ConnectionActivity::class.java))
            return
        }

        updateStatus("Conectando a ${prefs.host}...")
        btnConnect.isEnabled = false
        log("☁ Conectando a ${prefs.host}:${prefs.port}...")

        lifecycleScope.launch(Dispatchers.IO) {
            val conn = SyncConnection(prefs.host, prefs.port, prefs.secret)
            val result = conn.connect()

            withContext(Dispatchers.Main) {
                btnConnect.isEnabled = true
                result.fold(
                    onSuccess = { info ->
                        connection = conn
                        btnConnect.text     = "Desconectar"
                        btnSync.isEnabled   = true
                        updateStatus("✔ Conectado — Syncroboxter PC v${info.version}")
                        log("✔ Conectado al servidor")
                        log("  PC base: ${info.pc_base}")
                        log("  Carpetas: ${info.subfolders.joinToString(", ")}")
                    },
                    onFailure = { e ->
                        updateStatus("Error de conexión")
                        log("✖ Error: ${e.message}")
                        toast("No se pudo conectar: ${e.message}")
                    }
                )
            }
        }
    }

    // ── Sincronización ────────────────────────────────────────────────────
    private fun startSync() {
        if (syncing) return
        syncing = true
        btnSync.isEnabled   = false
        progressBar.progress = 0
        progressBar.visibility = android.view.View.VISIBLE
        tvProgress.visibility  = android.view.View.VISIBLE
        log("\n▶ Iniciando sincronización...")

        lifecycleScope.launch(Dispatchers.IO) {
            val localBase = File(prefs.localFolder)

            val engine = SyncEngine(
                conn      = connection!!,
                localBase = localBase,
                onLog     = { msg -> runOnUiThread { log(msg) } },
                onProgress = { cur, total ->
                    runOnUiThread {
                        val pct = if (total > 0) (cur * 100 / total) else 0
                        progressBar.progress = pct
                        tvProgress.text      = "$pct%"
                    }
                }
            )

            val result = engine.syncAll()

            withContext(Dispatchers.Main) {
                syncing           = false
                btnSync.isEnabled = true
                progressBar.visibility = android.view.View.GONE
                tvProgress.visibility  = android.view.View.GONE

                prefs.lastSyncTime = System.currentTimeMillis()
                updateLastSync()

                if (result.errors > 0) {
                    updateStatus("Sync con ${result.errors} error(es)")
                    toast("Sync completado con errores")
                } else {
                    updateStatus("✔ Sync completado — ${result.copied} archivo(s)")
                    toast("Sincronización completada")
                }
            }
        }
    }

    // ── Helpers UI ────────────────────────────────────────────────────────
    private fun log(msg: String) {
        val ts  = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val cur = tvLog.text.toString()
        val max = 200  // max líneas
        val lines = cur.lines().takeLast(max).toMutableList()
        lines.add("[$ts] $msg")
        tvLog.text = lines.joinToString("\n")
        // Auto-scroll
        val scrollView = findViewById<ScrollView>(R.id.scrollLog)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun updateStatus(msg: String) {
        tvStatus.text = msg
    }

    private fun updateLastSync() {
        val t = prefs.lastSyncTime
        tvLastSync.text = if (t == 0L) "Último sync: nunca"
        else "Último sync: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(t))}"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        // Actualizar botón si se configuró el host
        if (prefs.host.isNotEmpty() && connection?.isConnected != true) {
            btnConnect.text = "Conectar a ${prefs.host}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        connection?.quit()
        connection?.close()
    }
}
