package com.ubaidd.host.webrtc

import android.os.Environment
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

class FileServer {

    companion object {
        private const val TAG = "UbaidFileServer"
        private const val DEFAULT_CHUNK_SIZE = 32 * 1024 // 32 KB per chunk
    }

    val defaultRootPath: String
        get() = Environment.getExternalStorageDirectory().absolutePath

    fun isStorageAccessible(): Boolean {
        return try {
            val root = Environment.getExternalStorageDirectory()
            root.exists() && root.canRead()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Handles an incoming JSON file request from Salim and returns the response JSON string
     */
    fun handleRequest(requestJsonStr: String): String {
        return try {
            val json = JSONObject(requestJsonStr)
            when (json.optString("type")) {
                "ping" -> handlePing(json)
                "list" -> handleList(json)
                "read" -> handleRead(json)
                "write" -> handleWrite(json)
                else -> JSONObject().apply {
                    put("type", "error")
                    put("message", "Unknown file command: ${json.optString("type")}")
                }.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling file request", e)
            JSONObject().apply {
                put("type", "error")
                put("message", e.message ?: "File request parsing failed")
            }.toString()
        }
    }

    private fun handlePing(json: JSONObject): String {
        val root = Environment.getExternalStorageDirectory()
        return JSONObject().apply {
            put("type", "pong")
            put("timestamp", json.optLong("timestamp", System.currentTimeMillis()))
            put("status", "active")
            put("storageAccessible", isStorageAccessible())
            put("rootPath", defaultRootPath)
            put("freeSpaceBytes", root.freeSpace)
            put("totalSpaceBytes", root.totalSpace)
        }.toString()
    }

    private fun handleList(json: JSONObject): String {
        val targetPath = json.optString("path", defaultRootPath).ifBlank { defaultRootPath }
        val targetDir = File(targetPath)

        val response = JSONObject()
        response.put("type", "list_result")
        response.put("path", targetPath)

        if (!targetDir.exists()) {
            response.put("success", false)
            response.put("error", "Directory does not exist")
            return response.toString()
        }

        if (!targetDir.isDirectory) {
            response.put("success", false)
            response.put("error", "Target is not a directory")
            return response.toString()
        }

        val filesArray = JSONArray()
        val fileList = targetDir.listFiles()
        if (fileList != null) {
            // Sort: directories first, then alphabetical
            fileList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .take(200) // Limit single payload size for responsive channel
                .forEach { file ->
                    val item = JSONObject().apply {
                        put("name", file.name)
                        put("path", file.absolutePath)
                        put("isDir", file.isDirectory)
                        put("size", if (file.isDirectory) 0L else file.length())
                        put("lastModified", file.lastModified())
                    }
                    filesArray.put(item)
                }
        }

        response.put("success", true)
        response.put("files", filesArray)
        return response.toString()
    }

    private fun handleRead(json: JSONObject): String {
        val path = json.optString("path")
        val offset = json.optLong("offset", 0L)
        val length = json.optInt("length", DEFAULT_CHUNK_SIZE).coerceIn(1024, 64 * 1024)

        val file = File(path)
        val response = JSONObject().apply {
            put("type", "read_chunk")
            put("path", path)
            put("offset", offset)
        }

        if (!file.exists() || !file.isFile || !file.canRead()) {
            response.put("success", false)
            response.put("error", "File unreadable or does not exist")
            return response.toString()
        }

        try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLength = raf.length()
                if (offset >= fileLength) {
                    response.put("success", true)
                    response.put("isEof", true)
                    response.put("data", "")
                    response.put("bytesRead", 0)
                    return response.toString()
                }

                raf.seek(offset)
                val buffer = ByteArray(length)
                val bytesRead = raf.read(buffer)
                if (bytesRead <= 0) {
                    response.put("success", true)
                    response.put("isEof", true)
                    response.put("data", "")
                    response.put("bytesRead", 0)
                } else {
                    val base64Data = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
                    val isEof = (offset + bytesRead) >= fileLength
                    response.put("success", true)
                    response.put("isEof", isEof)
                    response.put("data", base64Data)
                    response.put("bytesRead", bytesRead)
                }
            }
        } catch (e: Exception) {
            response.put("success", false)
            response.put("error", e.message ?: "Failed reading chunk")
        }

        return response.toString()
    }

    private fun handleWrite(json: JSONObject): String {
        val path = json.optString("path")
        val offset = json.optLong("offset", 0L)
        val base64Data = json.optString("data", "")
        val isEof = json.optBoolean("isEof", false)

        val response = JSONObject().apply {
            put("type", "write_result")
            put("path", path)
            put("offset", offset)
        }

        try {
            val file = File(path)
            file.parentFile?.mkdirs()

            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(offset)
                raf.write(bytes)
            }

            response.put("success", true)
            response.put("bytesWritten", bytes.size)
            response.put("isEof", isEof)
        } catch (e: Exception) {
            response.put("success", false)
            response.put("error", e.message ?: "Failed writing chunk")
        }

        return response.toString()
    }
}
