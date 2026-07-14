package com.miftahdhe.keepassnfcbridge.keepass

import android.content.Context
import android.content.Intent

object KeePassIntegration {

    fun sendKeyToKeePass(context: Context): Boolean {
        // Just launch KeePassDX
        val intent = context.packageManager.getLaunchIntentForPackage("com.kunzisoft.keepass.free") 
            ?: context.packageManager.getLaunchIntentForPackage("com.kunzisoft.keepass.pro")
            ?: context.packageManager.getLaunchIntentForPackage("com.kunzisoft.keepass.libre")
        
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return true
        }
        return false
    }
    
    fun cleanup(context: Context) {
        // No longer needed, as we use a RAM pipe
    }
}
