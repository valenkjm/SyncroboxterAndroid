package com.syncroboxter.app

import android.util.Log
import com.google.gson.reflect.TypeToken
import java.io.*
import java.net.Socket
import java.net.SocketTimeoutException

// ══════════════════════════════════════════════════════
//  CONEXIÓN TCP CON EL SERVIDOR SYNCROBOXTER PC
// ══════════════════════════════════════════════════════
class SyncConnection(
    private val host: String,
    private val port: Int,
    private val secret: String
) {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var rawOut: OutputStream? = null
    private var rawIn: InputStream? = null

    val isConnected get() = socket?.isConnected == true && socket?.isClosed == false

    // ── Conectar y autenticar ─────────────────────────────────────────────
    fun connect(): Result<ServerInfo> {
        return try {
            socket = Socket(host, port).apply { soTimeout = 15_000 }
            rawIn  = socket!!.getInputStream()
            rawOut = socket!!.getOutputStream()
            reader = BufferedReader(InputStreamReader(rawIn!!))
            writer = PrintWriter(OutputStreamWriter(rawOut!!), true)

            // Autenticación
            writer!!.println(secret)
            val resp = reader!!.readLine()
            if (resp != "OK") {
                close()
                return Result.failure(Exception("Autenticación fallida: $resp"))
            }

            // Pedir info del servidor
            val info = sendCmd<ServerInfo>(CmdPacket("INFO"))
            Result.success(info)
        } catch (e: Exception) {
            close()
            Result.failure(e)
        }
    }

    // ── Comandos ──────────────────────────────────────────────────────────
    fun listFiles(): Map<String, DirNode> {
        val json = sendRaw(CmdPacket("LIST"))
        val type = object : TypeToken<Map<String, DirNode>>() {}.type
        return Protocol.gson.fromJson(json, type)
    }

    fun getFileInfo(path: String): ServerResponse {
        return sendCmd(CmdPacket("GET", path = path))
    }

    // Descarga un archivo del servidor y lo guarda en localFile
    fun downloadFile(
        path: String,
        localFile: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<Unit> {
        return try {
            val meta: ServerResponse = sendCmd(CmdPacket("GET", path = path))
            if (meta.error != null) return Result.failure(Exception(meta.error))
            val size = meta.size ?: 0L

            localFile.parentFile?.mkdirs()
            var received = 0L
            localFile.outputStream().use { out ->
                val buf = ByteArray(Protocol.BUFFER_SIZE)
                while (received < size) {
                    val toRead = minOf(buf.size.toLong(), size - received).toInt()
                    val n = rawIn!!.read(buf, 0, toRead)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    received += n
                    onProgress(received, size)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Sube un archivo local al servidor
    fun uploadFile(
        remotePath: String,
        localFile: File,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<Unit> {
        return try {
            val size = localFile.length()
            val putCmd = CmdPacket("PUT", path = remotePath, size = size)
            writer!!.println(Protocol.gson.toJson(putCmd))

            val ack = reader!!.readLine()
            if (ack != "READY") return Result.failure(Exception("Servidor no listo: $ack"))

            var sent = 0L
            localFile.inputStream().use { inp ->
                val buf = ByteArray(Protocol.BUFFER_SIZE)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    rawOut!!.write(buf, 0, n)
                    sent += n
                    onProgress(sent, size)
                }
                rawOut!!.flush()
            }

            val resp: ServerResponse = sendCmdFromRaw(reader!!.readLine())
            if (resp.error != null) Result.failure(Exception(resp.error))
            else Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deleteFile(path: String): ServerResponse = sendCmd(CmdPacket("DELETE", path = path))

    fun quit() { try { writer?.println("""{"cmd":"QUIT"}""") } catch (_: Exception) {} }

    fun close() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null; reader = null; writer = null; rawIn = null; rawOut = null
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun sendRaw(cmd: CmdPacket): String {
        writer!!.println(Protocol.gson.toJson(cmd))
        return reader!!.readLine() ?: ""
    }

    private inline fun <reified T> sendCmd(cmd: CmdPacket): T {
        val json = sendRaw(cmd)
        return Protocol.gson.fromJson(json, T::class.java)
    }

    private inline fun <reified T> sendCmdFromRaw(json: String): T =
        Protocol.gson.fromJson(json, T::class.java)
}
