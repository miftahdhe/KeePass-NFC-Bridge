package com.miftahdhe.keepassnfcbridge.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.miftahdhe.keepassnfcbridge.R
import com.miftahdhe.keepassnfcbridge.crypto.KeySessionManager
import com.miftahdhe.keepassnfcbridge.MainActivity

object SettingsDialog {
    fun show(activity: MainActivity) {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("⚙️ Settings")
        
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_settings, null)
        builder.setView(view)
        
        val prefs = activity.getSharedPreferences("Settings", Context.MODE_PRIVATE)
        
        val chkAutoClear = view.findViewById<CheckBox>(R.id.chkAutoClear)
        val chkEnableSound = view.findViewById<CheckBox>(R.id.chkEnableSound)
        val spinnerTimeout = view.findViewById<Spinner>(R.id.spinnerTimeout)
        val chkRequireUnlock = view.findViewById<CheckBox>(R.id.chkRequireUnlock)
        val spinnerLanguage = view.findViewById<Spinner>(R.id.spinnerLanguage)
        
        chkAutoClear.isChecked = prefs.getBoolean("auto_clear", true)
        chkEnableSound.isChecked = prefs.getBoolean("enable_sound", true)
        val currentTimeoutMs = prefs.getLong("timeout_ms", 60000)
        chkRequireUnlock.isChecked = prefs.getBoolean("require_unlock", false)
        val currentLang = prefs.getString("language", "en") ?: "en"
        
        val timeoutOptions = arrayOf("1 Minute", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "60 Minutes")
        val timeoutValues = arrayOf(60000L, 300000L, 600000L, 900000L, 1800000L, 3600000L)
        val adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, timeoutOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimeout.adapter = adapter
        
        val index = timeoutValues.indexOf(currentTimeoutMs)
        if (index >= 0) {
            spinnerTimeout.setSelection(index)
        } else {
            spinnerTimeout.setSelection(0)
        }

        val langOptions = arrayOf("English", "Indonesia", "Español", "Українська", "العربية", "中文")
        val langValues = arrayOf("en", "id", "es", "uk", "ar", "zh")
        val langAdapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, langOptions)
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = langAdapter

        val langIndex = langValues.indexOf(currentLang)
        if (langIndex >= 0) {
            spinnerLanguage.setSelection(langIndex)
        }

        val btnExport = view.findViewById<Button>(R.id.btnExport)
        btnExport.setOnClickListener {
            activity.triggerExport()
        }
        
        builder.setPositiveButton("Save") { dialog, _ ->
            val selectedIndex = spinnerTimeout.selectedItemPosition
            val timeout = if (selectedIndex in timeoutValues.indices) timeoutValues[selectedIndex] else 60000L
            val selectedLangIndex = spinnerLanguage.selectedItemPosition
            val newLang = if (selectedLangIndex in langValues.indices) langValues[selectedLangIndex] else "en"
            
            prefs.edit()
                .putBoolean("auto_clear", chkAutoClear.isChecked)
                .putBoolean("enable_sound", chkEnableSound.isChecked)
                .putLong("timeout_ms", timeout)
                .putBoolean("require_unlock", chkRequireUnlock.isChecked)
                .putString("language", newLang)
                .apply()
                
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(newLang))
            
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.show()
    }
}
