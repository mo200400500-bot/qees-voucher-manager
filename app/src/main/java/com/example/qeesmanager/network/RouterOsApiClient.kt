package com.example.qeesmanager.network

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest

class RouterOsApiClient(
    private val host: String,
    private val port: Int,
    private val timeoutMs: Int = 7000
) {

    data class ApiResult(
        val success: Boolean,
        val message: String,
        val rawSentences: List> = emptyList()
    )

    private fun cleanHost(raw: String): String {
        return raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .substringBefore(":")
    }

    private fun writeLength(out: DataOutputStream, length: Int) {
        when {
            length < 0x80 -> out.write(length)
            length < 0x4000 -> {
                val v = length or 0x8000
                out.write((v ushr 8) and 0xFF)
                out.write(v and 0xFF)
            }
            length < 0x200000 -> {
                val v = length or 0xC00000
                out.write((v ushr 16) and 0xFF)
                out.write((v ushr 8) and 0xFF)
                out.write(v and 0xFF)
            }
            length < 0x10000000 -> {
                val v = length or 0xE0000000.toInt()
                out.write((v ushr 24) and 0xFF)
                out.write((v ushr 16) and 0xFF)
                out.write((v ushr 8) and 0xFF)
                out.write(v and 0xFF)
            }
            else -> {
                out.write(0xF0)
                out.write((length ushr 24) and 0xFF)
                out.write((length ushr 16) and 0xFF)
                out.write((length ushr 8) and 0xFF)
                out.write(length and 0xFF)
            }
        }
    }

    private fun readLength(input: DataInputStream): Int {
        val b1 = input.read()
        if (b1 < 0) return -1
        return when {
            (b1 and 0x80) == 0 -> b1
            (b1 and 0xC0) == 0x80 -> {
                val b2 = input.read()
                if (b2 < 0) -1 else (((b1 and 0x3F) shl 8) or b2)
            }
            (b1 and 0xE0) == 0xC0 -> {
                val b2 = input.read()
                val b3 = input.read()
                if (b2 < 0 || b3 < 0) -1 else (((b1 and 0x1F) shl 16) or (b2 shl 8) or b3)
            }
            (b1 and 0xF0) == 0xE0 -> {
                val b2 = input.read()
                val b3 = input.read()
                val b4 = input.read()
                if (b2 < 0 || b3 < 0 || b4 < 0) -1 else (((b1 and 0x0F) shl 24) or (b2 shl 16) or (b3 shl 8) or b4)
            }
            else -> input.readInt()
        }
    }

    private fun sendWord(out: DataOutputStream, word: String) {
        val bytes = word.toByteArray(Charsets.UTF_8)
        writeLength(out, bytes.size)
        out.write(bytes)
    }

    private fun sendSentence(out: DataOutputStream, words: List<String>) {
        for (w in words) { sendWord(out, w) }
        out.write(0)
        out.flush()
    }

    private fun readSentence(input: DataInputStream): List<String>? {
        val words = mutableListOf<String>()
        while (true) {
            val len = readLength(input)
            if (len < 0) return if (words.isEmpty()) null else words
            if (len == 0) {
                if (words.isNotEmpty()) return words
                continue
            }
            val buf = ByteArrayOutputStream(len)
            var remaining = len
            val chunk = ByteArray(1024)
            while (remaining > 0) {
                val read = input.read(chunk, 0, minOf(remaining, chunk.size))
                if (read < 0) return null
                buf.write(chunk, 0, read)
                remaining -= read
            }
            words.add(buf.toString(Charsets.UTF_8.name()))
        }
    }

    private fun md5Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val result = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            result[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
            i += 2
        }
        return result
    }

    fun executeCommand(
        username: String,
        password: String,
        commandWords: List<String>
    ): ApiResult {
        val targetHost = cleanHost(host)
        if (targetHost.isBlank()) {
            return ApiResult(false, "Router host/IP is empty.")
        }
        val socket = Socket()
        return try {
            socket.soTimeout = timeoutMs
            socket.connect(InetSocketAddress(targetHost, port), timeoutMs)
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            sendSentence(output, listOf("/login", "=name=$username", "=password=$password"))
            val firstLoginReply = readSentence(input) ?: return ApiResult(false, "Connection closed during login.")
            var authenticated = false
            var challengeHex: String? = null
            for (word in firstLoginReply) {
                if (word == "!done") {
                    authenticated = true
                } else if (word.startsWith("=ret=")) {
                    challengeHex = word.removePrefix("=ret=")
                } else if (word == "!trap") {
                    val reason = firstLoginReply.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=")
                    if (reason != null && challengeHex == null) {
                        return ApiResult(false, "RouterOS login failed: $reason")
                    }
                }
            }
            if (!authenticated && challengeHex != null) {
                val md = MessageDigest.getInstance("MD5")
                md.update(0.toByte())
                md.update(password.toByteArray(Charsets.ISO_8859_1))
                md.update(hexToBytes(challengeHex))
                val responseHex = "00" + md5Hex(md.digest())
                sendSentence(output, listOf("/login", "=name=$username", "=response=$responseHex"))
                val secondLoginReply = readSentence(input) ?: return ApiResult(false, "Challenge response failed.")
                if (!secondLoginReply.contains("!done")) {
                    val trapMsg = secondLoginReply.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "Invalid credentials"
                    return ApiResult(false, "Login failed: $trapMsg")
                }
                authenticated = true
            }
            if (!authenticated) {
                return ApiResult(false, "Authentication failed on port $port.")
            }
            sendSentence(output, commandWords)
            val rows = mutableListOf>()
            var errorMessage: String? = null
            while (true) {
                val sentence = readSentence(input) ?: break
                if (sentence.contains("!done")) break
                if (sentence.contains("!trap") || sentence.contains("!fatal")) {
                    errorMessage = sentence.firstOrNull { it.startsWith("=message=") }?.removePrefix("=message=") ?: "Command rejected by router"
                    break
                }
                if (sentence.contains("!re")) {
                    val map = linkedMapOf<String, String>()
                    for (w in sentence) {
                        if (w.startsWith("=") && w.indexOf("=", 1) > 1) {
                            val eqIndex = w.indexOf("=", 1)
                            val key = w.substring(1, eqIndex)
                            val valStr = w.substring(eqIndex + 1)
                            map[key] = valStr
                        }
                    }
                    if (map.isNotEmpty()) rows.add(map)
                }
            }
            if (errorMessage != null) ApiResult(false, "RouterOS error: $errorMessage", rows)
            else ApiResult(true, "Connected and executed on $targetHost:$port", rows)
        } catch (e: Exception) {
            val detail = e.localizedMessage ?: e.javaClass.simpleName
            ApiResult(false, "Failed to connect to $targetHost:$port - $detail")
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
