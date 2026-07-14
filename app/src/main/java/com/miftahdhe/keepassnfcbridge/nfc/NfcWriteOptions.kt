package com.miftahdhe.keepassnfcbridge.nfc

data class NfcWriteOptions(
    val overwrite: Boolean = false,
    val backup: Boolean = true
)
