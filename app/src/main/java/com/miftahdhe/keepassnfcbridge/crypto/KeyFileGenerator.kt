package com.miftahdhe.keepassnfcbridge.crypto

import java.security.MessageDigest
import java.security.SecureRandom

data class GeneratedKey(
    val bytes: ByteArray,
    val sha256: String,
    val hex: String
)

object KeyFileGenerator {
    fun generate(size: Int = 128): GeneratedKey {
        val random = SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(bytes)
        val sha256Hex = hash.joinToString("") { "%02X".format(it) }
        val hex = bytes.joinToString("") { "%02X".format(it) }
        
        return GeneratedKey(bytes, sha256Hex, hex)
    }
}
