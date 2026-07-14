package com.miftahdhe.keepassnfcbridge.nfc.handlers

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import com.miftahdhe.keepassnfcbridge.nfc.NfcKeyData

class MIFAREHandler : INfcHandler {
    override val handlerName: String = "MIFARE Handler"

    override fun isSupported(tag: Tag): Boolean {
        return MifareClassic.get(tag) != null
    }

    override fun getCardType(tag: Tag): String {
        return "MIFARE Classic (1K/4K)"
    }

    override fun readKeyFile(tag: Tag): NfcKeyData {
        throw Exception("MIFARE Classic handler belum tersedia.")
    }

    override fun writeKeyFile(tag: Tag, key: ByteArray, overwrite: Boolean) {
        throw Exception("MIFARE Classic handler belum tersedia.")
    }

    override fun eraseCard(tag: Tag) {
        throw Exception("MIFARE Classic handler belum tersedia.")
    }
}
