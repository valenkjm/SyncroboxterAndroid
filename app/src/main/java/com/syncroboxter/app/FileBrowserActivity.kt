package com.syncroboxter.app

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class FileBrowserActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var lvFiles: ListView
    private lateinit var tvPath: TextView
    private lateinit var btnUp: Button

    private var currentPath: File? = null
    private val pathStack = ArrayDeque<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_browser)
        supportActionBar?.title = "Archivos sincronizados"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs   = PrefsManager(this)
        lvFiles = findViewById(R.id.lvFiles)
        tvPath  = findViewById(R.id.tvPath)
        btnUp   = findViewById(R.id.btnUp)

        val localFolder = prefs.localFolder
        if (localFolder.isEmpty()) {
            tvPath.text = "Sin carpeta configurada"
            return
        }

        // Intentar abrir como ruta directa o como URI
        currentPath = File(localFolder).takeIf { it.exists() }
                   ?: run {
                       tvPath.text = "Ruta URI — usá el explorador del sistema"
                       null
                   }

        currentPath?.let { loadDir(it) }

        btnUp.setOnClickListener {
            if (pathStack.isNotEmpty()) {
                currentPath = pathStack.removeLast()
                loadDir(currentPath!!)
            }
        }

        lvFiles.setOnItemClickListener { _, _, pos, _ ->
            val item = (lvFiles.adapter as ArrayAdapter<String>).getItem(pos) ?: return@setOnItemClickListener
            val name = item.removePrefix("📁 ").removePrefix("📄 ").trim()
            val file = File(currentPath, name)
            if (file.isDirectory) {
                pathStack.addLast(currentPath!!)
                currentPath = file
                loadDir(file)
            }
        }
    }

    private fun loadDir(dir: File) {
        tvPath.text = dir.absolutePath
        btnUp.isEnabled = pathStack.isNotEmpty()

        val items = mutableListOf<String>()
        dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?.forEach { f ->
                val icon = if (f.isDirectory) "📁" else "📄"
                val size = if (f.isFile) "  (${formatSize(f.length())})" else ""
                items.add("$icon ${f.name}$size")
            }

        if (items.isEmpty()) items.add("(carpeta vacía)")

        lvFiles.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024        -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else                -> "${bytes / (1024 * 1024)} MB"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
