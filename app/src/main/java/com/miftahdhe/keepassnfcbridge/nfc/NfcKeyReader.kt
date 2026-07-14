package com.miftahdhe.keepassnfcbridge.nfc

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import java.security.MessageDigest

data class NfcKeyData(
    val bytes: ByteArray,
    val sha256: String,
    val containerVersion: Byte? = null,
    val containerSize: Int? = null,
    val isKpnfc01: Boolean = false
)

object NfcKeyReader {
    fun readKeyFile(
        tag: Tag
    ): NfcKeyData {
        val mifare = MifareUltralight.get(tag)
            ?: throw Exception("Tag bukan MIFARE Ultralight")
            
        mifare.connect()
        try {
            val layout = NfcCapacity.detect(mifare)
            val allData = NfcBackupEngine.readUserMemory(mifare, layout)
            
            val nonZeroBytes = allData.count { it != 0.toByte() }
            if (nonZeroBytes == 0) {
                throw Exception("EMPTY_CARD")
            }
            
            try {
                // Try reading as KPNFC01 container
                val container = NfcContainerFormat.unpack(allData)
                return NfcKeyData(
                    bytes = container.keyData,
                    sha256 = sha256(container.keyData),
                    containerVersion = container.version,
                    containerSize = container.keyLength,
                    isKpnfc01 = true
                )
            } catch (e: ContainerException) {
                // Fallback to legacy behavior (just return the first 128 bytes if it looks like a keyfile or not)
                val key = allData.copyOf(128)
                return NfcKeyData(
                    bytes = key,
                    sha256 = sha256(key),
                    isKpnfc01 = false
                )
            }
        } finally {
            mifare.close()
        }
    }

    private fun sha256(
        data: ByteArray
    ): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(data)
            .joinToString("") {
                "%02X".format(it)
            }
    }
}
