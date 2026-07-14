package com.miftahdhe.keepassnfcbridge.crypto

import android.os.Handler
import android.os.Looper
import android.content.Context
import com.miftahdhe.keepassnfcbridge.keepass.KeePassIntegration

object KeySessionManager {
    private var activeKey: ByteArray? = null
    private var timeoutRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var onSessionTimeout: (() -> Unit)? = null
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun startSession(key: ByteArray, timeoutMs: Long = 60000, autoClear: Boolean = true, onTimeout: () -> Unit) {
        clearSession()
        activeKey = key.copyOf()
        onSessionTimeout = onTimeout
        
        if (autoClear) {
            timeoutRunnable = Runnable {
                clearSession()
                onSessionTimeout?.invoke()
            }
            handler.postDelayed(timeoutRunnable!!, timeoutMs)
        }
    }

    fun clearSession() {
        activeKey?.let {
            for (i in it.indices) {
                it[i] = 0
            }
        }
        activeKey = null
        appContext?.let { KeePassIntegration.cleanup(it) }
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    fun getActiveKey(): ByteArray? {
        return activeKey?.copyOf()
    }
    
    fun isSessionActive(): Boolean {
        return activeKey != null
    }
}
