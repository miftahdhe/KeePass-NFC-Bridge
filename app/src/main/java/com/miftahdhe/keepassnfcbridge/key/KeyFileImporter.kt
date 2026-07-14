package com.miftahdhe.keepassnfcbridge.key

import java.io.InputStream
import java.security.MessageDigest

data class ImportedKey(
    val bytes: ByteArray,
    val sha256: String,
    val hex: String
)

object KeyFileImporter {
    fun import(stream: InputStream): ImportedKey {
        val bytes = stream.readBytes()
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(bytes)
        val sha256Hex = hash.joinToString("") { "%02X".format(it) }
        val hex = bytes.joinToString("") { "%02X".format(it) }
        
        return ImportedKey(bytes, sha256Hex, hex)
    }
}
