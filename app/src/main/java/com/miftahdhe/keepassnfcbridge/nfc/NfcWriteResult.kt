package com.miftahdhe.keepassnfcbridge.nfc

sealed class NfcWriteResult {
    object Success : NfcWriteResult()
    object CardFull : NfcWriteResult()
    object CardLocked : NfcWriteResult()
    object VerificationFailed : NfcWriteResult()
    object SameData : NfcWriteResult()
    object EraseSuccess : NfcWriteResult()
    data class Failed(val reason: String) : NfcWriteResult()
}
