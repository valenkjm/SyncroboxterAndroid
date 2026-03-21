package com.syncroboxter.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class ConnectionActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etSecret: EditText
    private lateinit var etLocalFolder: EditText
    private lateinit var btnPickFolder: Button
    private lateinit var btnTest: Button
    private lateinit var btnSave: Button
    private lateinit var tvTestResult: TextView
    private lateinit var rgMode: RadioGroup

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            etLocalFolder.setText(it.toString())
            prefs.localFolder = it.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection)
        supportActionBar?.title = "Configuración"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = PrefsManager(this)

        etHost        = findViewById(R.id.etHost)
        etPort        = findViewById(R.id.etPort)
        etSecret      = findViewById(R.id.etSecret)
        etLocalFolder = findViewById(R.id.etLocalFolder)
        btnPickFolder = findViewById(R.id.btnPickFolder)
        btnTest       = findViewById(R.id.btnTest)
        btnSave       = findViewById(R.id.btnSave)
        tvTestResult  = findViewById(R.id.tvTestResult)
        rgMode        = findViewById(R.id.rgMode)

        // Cargar valores guardados
        etHost.setText(prefs.host)
        etPort.setText(prefs.port.toString())
        etSecret.setText(prefs.secret)
        etLocalFolder.setText(prefs.localFolder)

        // Modo de conexión
        when (prefs.connectionMode) {
            "wifi"      -> rgMode.check(R.id.rbWifi)
            "bluetooth" -> rgMode.check(R.id.rbBluetooth)
            "internet"  -> rgMode.check(R.id.rbInternet)
        }

        rgMode.setOnCheckedChangeListener { _, id ->
            prefs.connectionMode = when (id) {
                R.id.rbWifi      -> "wifi"
                R.id.rbBluetooth -> "bluetooth"
                R.id.rbInternet  -> "internet"
                else             -> "wifi"
            }
            updateHint()
        }
        updateHint()

        btnPickFolder.setOnClickListener {
            folderPicker.launch(null)
        }

        btnTest.setOnClickListener {
            testConnection()
        }

        btnSave.setOnClickListener {
            saveAndClose()
        }
    }

    private fun updateHint() {
        val hint = when (prefs.connectionMode) {
            "wifi"      -> "IP local (ej: 192.168.1.100)"
            "bluetooth" -> "Dirección MAC o nombre del dispositivo"
            "internet"  -> "IP pública o dominio (ej: mi-pc.ddns.net)"
            else        -> "Host / IP"
        }
        etHost.hint = hint
    }

    private fun testConnection() {
        val host   = etHost.text.toString().trim()
        val port   = etPort.text.toString().trim().toIntOrNull() ?: Protocol.DEFAULT_PORT
        val secret = etSecret.text.toString().trim()

        if (host.isEmpty()) {
            tvTestResult.text = "✖ Ingresá el host/IP"
            return
        }

        tvTestResult.text = "Probando conexión..."
        btnTest.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val conn   = SyncConnection(host, port, secret)
            val result = conn.connect()
            conn.quit()
            conn.close()

            withContext(Dispatchers.Main) {
                btnTest.isEnabled = true
                result.fold(
                    onSuccess = { info ->
                        tvTestResult.text =
                            "✔ Conectado — PC v${info.version}\n" +
                            "  Carpetas: ${info.subfolders.size}"
                    },
                    onFailure = { e ->
                        tvTestResult.text = "✖ Error: ${e.message}"
                    }
                )
            }
        }
    }

    private fun saveAndClose() {
        prefs.host   = etHost.text.toString().trim()
        prefs.port   = etPort.text.toString().trim().toIntOrNull() ?: Protocol.DEFAULT_PORT
        prefs.secret = etSecret.text.toString().trim()
        // localFolder ya se guarda en el picker

        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
