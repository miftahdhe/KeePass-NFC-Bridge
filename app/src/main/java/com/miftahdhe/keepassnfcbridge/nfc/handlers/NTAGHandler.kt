package com.miftahdhe.keepassnfcbridge.nfc.handlers

import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import com.miftahdhe.keepassnfcbridge.nfc.NfcKeyData
import com.miftahdhe.keepassnfcbridge.nfc.NfcKeyReader
import com.miftahdhe.keepassnfcbridge.nfc.NfcWriter

class NTAGHandler : INfcHandler {
    override val handlerName: String = "NTAG Handler"

    override fun isSupported(tag: Tag): Boolean {
        val techs = tag.techList.map { it.split(".").last() }
        // Filter out NTAG DNA because it has IsoDep usually, or handle differently
        if (techs.contains("IsoDep")) return false
        return MifareUltralight.get(tag) != null
    }

    override fun getCardType(tag: Tag): String {
        return "NTAG / MIFARE Ultralight"
    }

    override fun readKeyFile(tag: Tag): NfcKeyData {
        return NfcKeyReader.readKeyFile(tag)
    }

    override fun writeKeyFile(tag: Tag, key: ByteArray, overwrite: Boolean) {
        val result = NfcWriter().writeKeyFile(tag, key, com.miftahdhe.keepassnfcbridge.nfc.NfcWriteOptions(overwrite = overwrite))
        when (result) {
            is com.miftahdhe.keepassnfcbridge.nfc.NfcWriteResult.Success -> {
                // Success
            }
            is com.miftahdhe.keepassnfcbridge.nfc.NfcWriteResult.Failed -> {
                throw Exception(result.reason)
            }
            else -> {
                throw Exception("Gagal menulis kartu: $result")
            }
        }
    }

    override fun eraseCard(tag: Tag) {
        val result = NfcWriter().eraseCard(tag)
        if (result is com.miftahdhe.keepassnfcbridge.nfc.NfcWriteResult.Failed) {
            throw Exception(result.reason)
        }
    }
}
