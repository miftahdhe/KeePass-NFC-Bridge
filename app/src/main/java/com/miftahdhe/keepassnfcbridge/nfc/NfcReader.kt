package com.miftahdhe.keepassnfcbridge.nfc

import android.nfc.Tag
import android.nfc.tech.MifareUltralight

data class NfcTagInfo(
    val uid: String,
    val tagType: String,
    val technologies: List<String>,
    val techCount: Int,
    val memory: String,
    val pages: String,
    val ascii: String
)

object NfcReader {
    fun read(tag: Tag): NfcTagInfo {
        val uid = tag.id.joinToString(":") { "%02X".format(it) }
        val techs = tag.techList.map { it.split(".").last() }
        val ultralight = MifareUltralight.get(tag)
        
        var tagType = "Unknown NFC Type"
        
        val handler = com.miftahdhe.keepassnfcbridge.nfc.handlers.NfcHandlerManager.getHandler(tag)
        if (handler != null) {
            tagType = handler.getCardType(tag)
        } else {
            // Basic heuristics for NFC mapping if no handler found
            if (techs.contains("IsoDep")) {
                tagType = "Detected NTAG424 DNA / DESFire\nHandler belum tersedia."
            } else if (techs.contains("NfcV") || techs.contains("Iso15693")) {
                tagType = "ISO 15693 (ICODE / SLIX)\nHandler belum tersedia."
            }
        }
        
        var memory = "Unknown"
        var pages = "N/A"
        var ascii = "N/A"
        
        if (ultralight != null) {
                // Tag type already set by handler or heuristics
            
            try {
                ultralight.connect()
                val layout = NfcCapacity.detect(ultralight)
                memory = "${layout.userBytes} Bytes (${layout.name})"
                
                // Read first 4 bytes of user area
                val pageData = ultralight.readPages(layout.firstWritablePage)
                val first4 = pageData.copyOf(4)
                pages = first4.joinToString(" ") { "%02X".format(it) }
                ascii = first4.map { if (it in 32..126) it.toChar() else '.' }.joinToString("")
                
                ultralight.close()
            } catch (e: Exception) {
                memory = "Error reading: ${e.message}"
            }
        }
        
        return NfcTagInfo(
            uid = uid,
            tagType = tagType,
            technologies = techs,
            techCount = tag.techList.size,
            memory = memory,
            pages = pages,
            ascii = ascii
        )
    }
}
