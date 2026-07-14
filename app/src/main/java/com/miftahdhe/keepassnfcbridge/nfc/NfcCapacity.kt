package com.miftahdhe.keepassnfcbridge.nfc

import android.nfc.tech.MifareUltralight

object NfcCapacity {
    private fun getVersion(mifare: MifareUltralight): ByteArray? {
        return try {
            mifare.transceive(byteArrayOf(0x60.toByte()))
        } catch (_: Exception) {
            null
        }
    }
    
    fun detect(mifare: MifareUltralight): NfcMemoryLayout {
        val version = getVersion(mifare)
        if (version != null && version.size >= 8) {
            val storage = version[6].toInt() and 0xFF
            return when (storage) {
                0x0F -> NfcLayouts.NTAG213
                0x11 -> NfcLayouts.NTAG215
                0x13 -> NfcLayouts.NTAG216
                0x2A -> NfcLayouts.NTAG424_DNA
                0x2B -> NfcLayouts.NTAG424_DNA_EV2
                0x2C -> NfcLayouts.NTAG424_DNA_EV3
                else -> NfcLayouts.UNKNOWN
            }
        }
        return when (mifare.type) {
            MifareUltralight.TYPE_ULTRALIGHT -> NfcLayouts.ULTRALIGHT
            MifareUltralight.TYPE_ULTRALIGHT_C -> NfcLayouts.ULTRALIGHT
            else -> NfcLayouts.UNKNOWN
        }
    }
}
