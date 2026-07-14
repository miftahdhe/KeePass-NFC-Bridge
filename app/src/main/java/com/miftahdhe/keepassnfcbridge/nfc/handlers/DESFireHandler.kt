package com.miftahdhe.keepassnfcbridge.nfc.handlers

import android.nfc.Tag
import com.miftahdhe.keepassnfcbridge.nfc.NfcKeyData

class DESFireHandler : INfcHandler {
    override val handlerName: String = "DESFire Handler"

    override fun isSupported(tag: Tag): Boolean {
        val techs = tag.techList.map { it.split(".").last() }
        // Simple heuristic for DESFire, often just IsoDep, but we differentiate from NTAG DNA
        return techs.contains("IsoDep") && !techs.contains("NfcA")
    }

    override fun getCardType(tag: Tag): String {
        return "MIFARE DESFire"
    }

    override fun readKeyFile(tag: Tag): NfcKeyData {
        throw Exception("DESFire secure handler belum tersedia.")
    }

    override fun writeKeyFile(tag: Tag, key: ByteArray, overwrite: Boolean) {
        throw Exception("DESFire secure handler belum tersedia.")
    }

    override fun eraseCard(tag: Tag) {
        throw Exception("DESFire secure handler belum tersedia.")
    }
}
