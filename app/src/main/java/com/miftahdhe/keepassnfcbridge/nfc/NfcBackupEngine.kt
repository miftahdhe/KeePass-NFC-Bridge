package com.miftahdhe.keepassnfcbridge.nfc

import android.nfc.tech.MifareUltralight

object NfcBackupEngine {
    fun readUserMemory(mifare: MifareUltralight, layout: NfcMemoryLayout): ByteArray {
        val totalBytes = layout.userBytes
        val result = ByteArray(totalBytes)
        var offset = 0
        var page = layout.firstWritablePage
        
        while (page <= layout.lastWritablePage && offset < totalBytes) {
            // readPages reads 4 pages (16 bytes)
            val pageData = try {
                mifare.readPages(page)
            } catch (e: Exception) {
                // If reading fails, fill with zeros
                ByteArray(16)
            }
            
            // We only need the bytes up to the end of user memory or the next 4 pages
            val bytesToCopy = minOf(16, totalBytes - offset)
            // But we must be careful not to read past the lastWritablePage
            // Actually, readPages(page) reads page, page+1, page+2, page+3.
            // If page+3 is past lastWritablePage, it's fine for the card usually, but we only copy what we need.
            
            System.arraycopy(pageData, 0, result, offset, bytesToCopy)
            offset += bytesToCopy
            page += 4
        }
        return result
    }
}
