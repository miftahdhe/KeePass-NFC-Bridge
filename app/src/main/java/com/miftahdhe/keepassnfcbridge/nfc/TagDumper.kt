package com.miftahdhe.keepassnfcbridge.nfc

import android.nfc.Tag
import android.nfc.tech.MifareUltralight

data class DumpResult(
    val success: Boolean,
    val pages: List<String> = emptyList(),
    val error: String? = null
)

object TagDumper {
    fun dump(tag: Tag): DumpResult {
        val ultralight = MifareUltralight.get(tag) ?: return DumpResult(false, error = "Not Mifare Ultralight")
        try {
            ultralight.connect()
            val layout = NfcCapacity.detect(ultralight)
            val dump = mutableListOf<String>()
            
            for (page in layout.firstWritablePage..minOf(layout.lastWritablePage, layout.firstWritablePage + 10)) {
                val data = ultralight.readPages(page).copyOf(4)
                val hex = data.joinToString(" ") { "%02X".format(it) }
                val ascii = data.map { if (it in 32..126) it.toChar() else '.' }.joinToString("")
                dump.add("Page %02d: %s | %s".format(page, hex, ascii))
            }
            ultralight.close()
            return DumpResult(true, dump)
        } catch (e: Exception) {
            return DumpResult(false, error = e.message)
        }
    }
}
