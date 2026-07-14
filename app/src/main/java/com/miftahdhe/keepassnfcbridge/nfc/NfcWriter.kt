package com.miftahdhe.keepassnfcbridge.nfc

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import java.security.MessageDigest

class NfcWriter {
    fun writeKeyFile(
        tag: Tag,
        rawKeyFile: ByteArray,
        options: NfcWriteOptions = NfcWriteOptions()
    ): NfcWriteResult {
        val mifare = MifareUltralight.get(tag)
            ?: return NfcWriteResult.Failed("Tag bukan MIFARE Ultralight")
            
        try {
            mifare.connect()
            
            //---------------------------------------------------
            // DETECT MEMORY
            //---------------------------------------------------
            val layout = NfcCapacity.detect(mifare)
            val packedData = NfcContainerFormat.pack(rawKeyFile)
            
            println("========== NFC DEBUG ==========")
            println("Card Type      : ${mifare.type}")
            println("Layout         : ${layout.name}")
            println("User Bytes     : ${layout.userBytes}")
            println("First Page     : ${layout.firstWritablePage}")
            println("Last Page      : ${layout.lastWritablePage}")
            println("Key Size       : ${rawKeyFile.size}")
            println("Packed Size    : ${packedData.size}")
            println("================================")
            
            println("CHECK : ${packedData.size} > ${layout.userBytes}")
            if (packedData.size > layout.userBytes) {
                mifare.close()
                return NfcWriteResult.Failed("Key File terlalu besar.\nFile: ${packedData.size} bytes\nKapasitas NFC: ${layout.userBytes} bytes")
            }
            
            //---------------------------------------------------
            // BACKUP
            //---------------------------------------------------
            val existingData = NfcBackupEngine.readUserMemory(mifare, layout)
            
            //---------------------------------------------------
            // CHECK OLD DATA
            //---------------------------------------------------
            val hasData = hasRealUserData(existingData)
            if (hasData && !options.overwrite) {
                mifare.close()
                return if (looksLikeKeyFile(existingData)) {
                    NfcWriteResult.Failed("Card Already Contains Key File")
                } else {
                    NfcWriteResult.Failed("Card Contains Other Data")
                }
            }
            
            //---------------------------------------------------
            // WRITE KEY FILE
            //---------------------------------------------------
            var offset = 0
            var page = layout.firstWritablePage
            while (offset < packedData.size && page <= layout.lastWritablePage) {
                val buffer = ByteArray(4)
                val remain = packedData.size - offset
                val copy = minOf(4, remain)
                System.arraycopy(packedData, offset, buffer, 0, copy)
                mifare.writePage(page, buffer)
                offset += copy
                page++
            }
            
            //---------------------------------------------------
            // FILL REMAINING SPACE
            //---------------------------------------------------
            while (page <= layout.lastWritablePage) {
                mifare.writePage(page, byteArrayOf(0, 0, 0, 0))
                page++
            }
            
            //---------------------------------------------------
            // VERIFY
            //---------------------------------------------------
            val verifyData = NfcBackupEngine.readUserMemory(mifare, layout)
            
            try {
                val container = NfcContainerFormat.unpack(verifyData)
                if (container.keyLength != rawKeyFile.size || !container.keyData.contentEquals(rawKeyFile)) {
                    mifare.close()
                    return NfcWriteResult.Failed("Verifikasi gagal (Data mismatch)")
                }
            } catch (e: Exception) {
                mifare.close()
                return NfcWriteResult.Failed("Verifikasi gagal: ${e.message}")
            }
            
            mifare.close()
            return NfcWriteResult.Success
        } catch (e: Exception) {
            try {
                mifare.close()
            } catch (_: Exception) {}
            return NfcWriteResult.Failed(e.message ?: "Unknown Error")
        }
    }
    
    fun eraseCard(tag: Tag): NfcWriteResult {
        val mifare = MifareUltralight.get(tag)
            ?: return NfcWriteResult.Failed("Tag bukan MIFARE Ultralight")
        try {
            mifare.connect()
            val layout = NfcCapacity.detect(mifare)
            // Hapus seluruh area user
            for (page in layout.firstWritablePage..layout.lastWritablePage) {
                mifare.writePage(page, byteArrayOf(0, 0, 0, 0))
            }
            // Verifikasi
            val verify = NfcBackupEngine.readUserMemory(mifare, layout)
            mifare.close()
            val masihAdaData = hasRealUserData(verify)
            return if (!masihAdaData) {
                NfcWriteResult.EraseSuccess
            } else {
                NfcWriteResult.VerificationFailed
            }
        } catch (e: Exception) {
            try {
                mifare.close()
            } catch (_: Exception) {}
            return NfcWriteResult.Failed(e.message ?: "Erase gagal")
        }
    }
    
    private fun sha256(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(data)
            .joinToString("") { "%02X".format(it) }
    }
    
    private fun looksLikeKeyFile(data: ByteArray): Boolean {
        try {
            NfcContainerFormat.unpack(data)
            return true
        } catch (e: Exception) {
            // Fallback to legacy check
            if (data.size < 128) return false
            var nonZero = 0
            for (i in 0 until 128) {
                if (data[i] != 0.toByte()) nonZero++
            }
            return nonZero > 100
        }
    }
    
    private fun hasRealUserData(data: ByteArray): Boolean {
        if (data.isEmpty()) return false
        var nonZero = 0
        for (b in data) {
            if (b.toInt() != 0) nonZero++
        }
        if (nonZero <= 8) return false
        return true
    }
}
