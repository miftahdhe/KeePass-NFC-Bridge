package com.miftahdhe.keepassnfcbridge.nfc.handlers

import android.nfc.Tag

object NfcHandlerManager {
    private val handlers = listOf(
        NTAGDNAHandler(),
        DESFireHandler(),
        MIFAREHandler(),
        NTAGHandler()
    )

    fun getHandler(tag: Tag): INfcHandler? {
        return handlers.firstOrNull { it.isSupported(tag) }
    }
}
