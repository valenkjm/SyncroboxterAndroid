package com.syncroboxter.app

import android.content.Context
import android.os.Environment
import java.io.File

// ══════════════════════════════════════════════════════
//  MOTOR DE SINCRONIZACIÓN ANDROID ↔ PC
// ══════════════════════════════════════════════════════
data class SyncResult(
    val copied:  Int = 0,
    val deleted: Int = 0,
    val errors:  Int = 0,
    val messages: List<String> = emptyList()
)

class SyncEngine(
    private val conn: SyncConnection,
    private val localBase: File,
    private val onLog: (String) -> Unit = {},
    private val onProgress: (Int, Int) -> Unit = { _, _ -> }  // current, total
) {
    // ── Sincronización completa ───────────────────────────────────────────
    suspend fun syncAll(): SyncResult {
        var copied = 0; var deleted = 0; var errors = 0

        onLog("▶ Obteniendo lista de archivos del servidor...")
        val remoteTree: Map<String, DirNode> = try {
            conn.listFiles()
        } catch (e: Exception) {
            onLog("✖ Error obteniendo lista: ${e.message}")
            return SyncResult(errors = 1)
        }

        // Aplanar árbol remoto
        val remoteFiles = mutableMapOf<String, FileEntry>()
        remoteTree.forEach { (sub, node) ->
            node.flatten(sub).forEach { remoteFiles[it.path] = it }
        }

        // Aplanar árbol local
        val localFiles = mutableMapOf<String, LocalFileEntry>()
        remoteTree.keys.forEach { sub ->
            val subDir = File(localBase, sub)
            scanLocalDir(subDir, sub, localFiles)
        }

        val allPaths = (remoteFiles.keys + localFiles.keys).toSet()
        val total    = allPaths.size
        var current  = 0

        for (path in allPaths.sorted()) {
            current++
            onProgress(current, total)

            val remote = remoteFiles[path]
            val local  = localFiles[path]
            val localFile = File(localBase, path.replace("/", File.separator))

            when {
                // Existe en remoto pero no en local → descargar
                remote != null && local == null -> {
                    onLog("  ↓ Descargando: $path")
                    conn.downloadFile(path, localFile).fold(
                        onSuccess = { copied++ },
                        onFailure = { onLog("  ✖ Error: ${it.message}"); errors++ }
                    )
                }

                // Existe en local pero no en remoto → subir
                remote == null && local != null -> {
                    onLog("  ↑ Subiendo: $path")
                    conn.uploadFile(path, local.file).fold(
                        onSuccess = { copied++ },
                        onFailure = { onLog("  ✖ Error: ${it.message}"); errors++ }
                    )
                }

                // Existe en ambos → comparar fechas
                remote != null && local != null -> {
                    val diff = kotlin.math.abs(remote.mtime - local.mtime)
                    if (diff > 2.0) {  // más de 2 segundos de diferencia
                        if (remote.mtime > local.mtime) {
                            onLog("  ↓ Actualizando (remoto más nuevo): $path")
                            conn.downloadFile(path, localFile).fold(
                                onSuccess = { copied++ },
                                onFailure = { onLog("  ✖ Error: ${it.message}"); errors++ }
                            )
                        } else {
                            onLog("  ↑ Actualizando (local más nuevo): $path")
                            conn.uploadFile(path, local.file).fold(
                                onSuccess = { copied++ },
                                onFailure = { onLog("  ✖ Error: ${it.message}"); errors++ }
                            )
                        }
                    }
                }
            }
        }

        onLog("\n✅ Sync completado — copiados: $copied, errores: $errors")
        return SyncResult(copied = copied, deleted = deleted, errors = errors)
    }

    // ── Escaneo local recursivo ───────────────────────────────────────────
    private fun scanLocalDir(
        dir: File,
        relBase: String,
        result: MutableMap<String, LocalFileEntry>
    ) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { f ->
            val relPath = "$relBase/${f.name}"
            if (f.isFile) {
                result[relPath] = LocalFileEntry(
                    file  = f,
                    path  = relPath,
                    mtime = f.lastModified() / 1000.0
                )
            } else if (f.isDirectory) {
                scanLocalDir(f, relPath, result)
            }
        }
    }
}

data class LocalFileEntry(val file: File, val path: String, val mtime: Double)
