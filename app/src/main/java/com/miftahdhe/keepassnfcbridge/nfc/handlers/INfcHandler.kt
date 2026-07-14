package com.miftahdhe.keepassnfcbridge.nfc.handlers

import android.nfc.Tag
import com.miftahdhe.keepassnfcbridge.nfc.NfcKeyData

interface INfcHandler {
    val handlerName: String
    fun isSupported(tag: Tag): Boolean
    fun getCardType(tag: Tag): String
    fun readKeyFile(tag: Tag): NfcKeyData
    fun writeKeyFile(tag: Tag, key: ByteArray, overwrite: Boolean = false)
    fun eraseCard(tag: Tag)
}
