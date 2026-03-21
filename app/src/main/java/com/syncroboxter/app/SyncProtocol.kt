package com.syncroboxter.app

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ══════════════════════════════════════════════════════
//  PROTOCOLO DE COMUNICACIÓN PC ↔ ANDROID
// ══════════════════════════════════════════════════════
object Protocol {
    const val DEFAULT_PORT   = 54321
    const val DEFAULT_SECRET = "SYNCROBOXTER_ANDROID_KEY"
    const val BUFFER_SIZE    = 65536
    val gson = Gson()
}

data class ServerInfo(
    val version: String,
    val subfolders: List<String>,
    val pc_base: String
)

data class FileEntry(
    val path: String,
    val size: Long,
    val mtime: Double
)

data class DirNode(
    val files: Map<String, FileEntry> = emptyMap(),
    val dirs: Map<String, DirNode>    = emptyMap()
)

data class CmdPacket(val cmd: String, val path: String? = null, val size: Long? = null)

data class ServerResponse(
    val ok: Boolean?    = null,
    val error: String?  = null,
    val size: Long?     = null,
    val mtime: Double?  = null
)

// Aplana el árbol en lista de FileEntry para fácil iteración
fun DirNode.flatten(prefix: String = ""): List<FileEntry> {
    val result = mutableListOf<FileEntry>()
    files.forEach { (_, f) -> result.add(f) }
    dirs.forEach  { (name, sub) -> result.addAll(sub.flatten("$prefix$name/")) }
    return result
}
