package com.miftahdhe.keepassnfcbridge.nfc.handlers

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.miftahdhe.keepassnfcbridge.nfc.NfcKeyData
import java.security.MessageDigest

class NTAGDNAHandler : INfcHandler {
    override val handlerName: String = "NTAG DNA Handler"

    override fun isSupported(tag: Tag): Boolean {
        val techs = tag.techList.map { it.split(".").last() }
        // IsoDep is required for NTAG DNA / DESFire. We do not want to intercept NTAG216 (which is MifareUltralight + Ndef)
        return techs.contains("IsoDep")
    }

    override fun getCardType(tag: Tag): String {
        return "NTAG424 DNA / DESFire\n(Secure Handler aktif sebagian - Mode NDEF)"
    }

    override fun readKeyFile(tag: Tag): NfcKeyData {
        val ndef = Ndef.get(tag) ?: throw Exception("Kartu tidak mendukung NDEF. Mode secure belum lengkap.")
        ndef.connect()
        try {
            val msg = ndef.ndefMessage ?: throw Exception("EMPTY_CARD")
            
            if (msg.records.isEmpty() || msg.records.all { it.tnf == NdefRecord.TNF_EMPTY || it.payload.isEmpty() }) {
                throw Exception("EMPTY_CARD")
            }

            for (record in msg.records) {
                if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                    val mimeType = String(record.type)
                    if (mimeType == "application/vnd.kpnfc01") {
                        val payload = record.payload
                        try {
                            val container = com.miftahdhe.keepassnfcbridge.nfc.NfcContainerFormat.unpack(payload)
                            return NfcKeyData(
                                bytes = container.keyData,
                                sha256 = sha256(container.keyData),
                                containerVersion = container.version,
                                containerSize = container.keyLength,
                                isKpnfc01 = true
                            )
                        } catch (e: Exception) {
                            return NfcKeyData(
                                bytes = payload,
                                sha256 = sha256(payload),
                                isKpnfc01 = false
                            )
                        }
                    }
                }
            }
            throw Exception("Key File (KPNFC01) tidak ditemukan dalam record NDEF.")
        } finally {
            ndef.close()
        }
    }

    override fun writeKeyFile(tag: Tag, key: ByteArray, overwrite: Boolean) {
        val payload = com.miftahdhe.keepassnfcbridge.nfc.NfcContainerFormat.pack(key)
        val record = NdefRecord.createMime("application/vnd.kpnfc01", payload)
        val message = NdefMessage(arrayOf(record))
        
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                if (!ndef.isWritable) {
                    throw Exception("Kartu Read-Only atau terkunci.")
                }
                if (ndef.maxSize < message.toByteArray().size) {
                    throw Exception("Key File terlalu besar.\nFile: ${message.toByteArray().size} bytes\nKapasitas NFC: ${ndef.maxSize} bytes")
                }
                // Check existing data
                if (!overwrite) {
                    val msg = ndef.ndefMessage
                    if (msg != null && msg.records.isNotEmpty()) {
                        val isTrulyEmpty = msg.records.all { it.tnf == NdefRecord.TNF_EMPTY || it.payload.isEmpty() }
                        if (!isTrulyEmpty) {
                            throw Exception("Card Contains Other Data")
                        }
                    }
                }
                ndef.writeNdefMessage(message)
            } finally {
                ndef.close()
            }
        } else {
            val formatable = NdefFormatable.get(tag)
            if (formatable != null) {
                formatable.connect()
                try {
                    formatable.format(message)
                } finally {
                    formatable.close()
                }
            } else {
                throw Exception("Kartu tidak mendukung NDEF.")
            }
        }
    }

    override fun eraseCard(tag: Tag) {
        val record = NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)
        val message = NdefMessage(arrayOf(record))
        
        val ndef = Ndef.get(tag)
        if (ndef != null) {
            ndef.connect()
            try {
                if (!ndef.isWritable) throw Exception("Kartu Read-Only")
                ndef.writeNdefMessage(message)
            } finally {
                ndef.close()
            }
        } else {
            val formatable = NdefFormatable.get(tag)
            if (formatable != null) {
                formatable.connect()
                try {
                    formatable.format(message)
                } finally {
                    formatable.close()
                }
            } else {
                throw Exception("Kartu tidak mendukung NDEF.")
            }
        }
    }
    
    private fun sha256(data: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02X".format(it) }
    }
}
