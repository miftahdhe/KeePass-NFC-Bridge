package com.miftahdhe.keepassnfcbridge

import com.miftahdhe.keepassnfcbridge.nfc.NfcWriteResult
import com.miftahdhe.keepassnfcbridge.nfc.NfcWriter
import com.miftahdhe.keepassnfcbridge.nfc.NfcKeyReader
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import com.miftahdhe.keepassnfcbridge.nfc.NfcCapacity
import android.provider.OpenableColumns
import android.app.AlertDialog

import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

import com.miftahdhe.keepassnfcbridge.databinding.ActivityMainBinding
import com.miftahdhe.keepassnfcbridge.key.KeyFileImporter
import com.miftahdhe.keepassnfcbridge.nfc.NfcReader
import com.miftahdhe.keepassnfcbridge.nfc.TagDumper
import com.miftahdhe.keepassnfcbridge.crypto.KeyFileGenerator

class MainActivity : AppCompatActivity() {
    private fun getFileName(uri: android.net.Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        return result ?: "keyfile.bin"
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        val prefs = newBase.getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
        val lang = prefs.getString("language", "en")?.replace("in", "id") ?: "en"
        val localeContext = com.miftahdhe.keepassnfcbridge.LocaleHelper.setLocale(newBase, lang)
        super.attachBaseContext(localeContext)
    }


    private lateinit var binding: ActivityMainBinding
    
    private var nfcAdapter: NfcAdapter? = null

    private lateinit var pendingIntent: PendingIntent

    private lateinit var intentFilters: Array<IntentFilter>
    
    private var importedKey: ByteArray? = null
    
    private var pendingWrite = false
    private var pendingErase = false
    private var pendingOverwrite = false
    private var pendingKey: ByteArray? = null
    
    private val importLauncher =
    registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->

        if (uri == null) return@registerForActivityResult

        contentResolver.openInputStream(uri)?.use { stream ->

            val key = KeyFileImporter.import(stream)

            importedKey = key.bytes
            pendingKey = key.bytes
            pendingWrite = true
            pendingOverwrite = false

            val fileName = getFileName(uri)
            val format = if (fileName.uppercase().endsWith(".XML")) "XML" else "BIN"
            val reqSpace = key.bytes.size + 15
            val importMsg = "File Name: $fileName\nFile Size: ${key.bytes.size} Bytes\nFile Format: $format\nRequired NFC Space: ~$reqSpace Bytes\n\nTap NFC Card to Save."
            binding.txtImportStatus.text = importMsg

        }

    }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) {
                val key = com.miftahdhe.keepassnfcbridge.crypto.KeySessionManager.getActiveKey()
                if (key != null) {
                    try {
                        contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(key)
                        }
                        android.widget.Toast.makeText(this, getString(R.string.msg_export_success), android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(this, getString(R.string.msg_export_failed, e.message), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(this, getString(R.string.msg_export_no_key), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    
    fun triggerExport() {
        if (com.miftahdhe.keepassnfcbridge.crypto.KeySessionManager.getActiveKey() != null) {
            exportLauncher.launch("KPNFC_Keyfile_Backup.bin")
        } else {
            android.widget.Toast.makeText(this, getString(R.string.msg_ram_no_key), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("require_unlock", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
        
        com.miftahdhe.keepassnfcbridge.crypto.KeySessionManager.init(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)

        enableEdgeToEdge()
        setContentView(binding.root)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }
        
        updateOpenKeePassButton()
        setupAboutPage()
        

        
        // Setup Bottom Navigation
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
        val workspaceScan = findViewById<android.view.View>(R.id.workspace_scan)
        val workspaceImportErase = findViewById<android.view.View>(R.id.workspace_import_erase)
        val workspaceGenerate = findViewById<android.view.View>(R.id.workspace_generate)
        val workspaceSettings = findViewById<android.view.View>(R.id.workspace_settings)
        val workspaceAbout = findViewById<android.view.View>(R.id.workspace_about)

        fun hideAllWorkspaces() {
            workspaceScan.visibility = android.view.View.GONE
            workspaceImportErase.visibility = android.view.View.GONE
            workspaceGenerate.visibility = android.view.View.GONE
            workspaceSettings.visibility = android.view.View.GONE
            workspaceAbout.visibility = android.view.View.GONE
        }
        
        // Settings Dialog logic inside workspace
        val prefs_s = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val chkAutoClear = findViewById<android.widget.CheckBox>(R.id.chkAutoClear)
        val chkEnableSound = findViewById<android.widget.CheckBox>(R.id.chkEnableSound)
        val spinnerTimeout = findViewById<android.widget.Spinner>(R.id.spinnerTimeout)
        val chkRequireUnlock = findViewById<android.widget.CheckBox>(R.id.chkRequireUnlock)
        val spinnerLanguage = findViewById<android.widget.Spinner>(R.id.spinnerLanguage)
        val checkDeveloper = findViewById<android.widget.CheckBox>(R.id.checkDeveloper)
        val btnExport = findViewById<android.widget.Button>(R.id.btnExport)
        val btnFormatNfc = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFormatNfc)

        chkAutoClear.isChecked = prefs_s.getBoolean("auto_clear", true)
        chkEnableSound.isChecked = prefs_s.getBoolean("enable_sound", true)
        val currentTimeoutMs = prefs_s.getLong("timeout_ms", 60000)
        chkRequireUnlock.isChecked = prefs_s.getBoolean("require_unlock", false)
        val currentLang = prefs_s.getString("language", "en")?.replace("in", "id") ?: "en"
        checkDeveloper.isChecked = prefs_s.getBoolean("developer_mode", false)

                val timeouts = arrayOf(
            Pair(getString(R.string.timeout_30s), 30000L),
            Pair(getString(R.string.timeout_1m), 60000L),
            Pair(getString(R.string.timeout_5m), 300000L),
            Pair(getString(R.string.timeout_10m), 600000L),
            Pair(getString(R.string.timeout_30m), 1800000L),
            Pair(getString(R.string.timeout_1h), 3600000L)
        )
        val adapterT = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, timeouts.map { it.first })
        adapterT.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimeout.adapter = adapterT
        val tIndex = timeouts.indexOfFirst { it.second == currentTimeoutMs }
        if (tIndex >= 0) spinnerTimeout.setSelection(tIndex)
        
        val languages = arrayOf(
            Pair("English", "en"),
            Pair("Bahasa Indonesia", "id"),
            Pair("中文 (Chinese)", "zh"),
            Pair("العربية (Arabic)", "ar"),
            Pair("Español (Spanish)", "es"),
            Pair("Українська (Ukrainian)", "uk")
        )
        val adapterL = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, languages.map { it.first })
        adapterL.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapterL
        val lIndex = languages.indexOfFirst { it.second == currentLang }
        if (lIndex >= 0) spinnerLanguage.setSelection(lIndex)
        
        fun saveSettings() {
            val timeout = timeouts[spinnerTimeout.selectedItemPosition].second
            val newLang = languages[spinnerLanguage.selectedItemPosition].second
            prefs_s.edit()
                .putBoolean("auto_clear", chkAutoClear.isChecked)
                .putBoolean("enable_sound", chkEnableSound.isChecked)
                .putLong("timeout_ms", timeout)
                .putBoolean("require_unlock", chkRequireUnlock.isChecked)
                .putString("language", newLang)
                .putBoolean("developer_mode", checkDeveloper.isChecked)
                .apply()
            
            // Check language change
            if (newLang != currentLang) {
                recreate()
            }
            
            // Toggle Dev Mode
            binding.layoutDeveloper.visibility = if (checkDeveloper.isChecked) android.view.View.VISIBLE else android.view.View.GONE
        }

        chkAutoClear.setOnCheckedChangeListener { _, _ -> saveSettings() }
        chkEnableSound.setOnCheckedChangeListener { _, _ -> saveSettings() }
        chkRequireUnlock.setOnCheckedChangeListener { _, _ -> saveSettings() }
        checkDeveloper.setOnCheckedChangeListener { _, _ -> saveSettings() }
        
        spinnerTimeout.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { saveSettings() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        spinnerLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) { saveSettings() }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        btnExport.setOnClickListener {
            triggerExport()
        }

        btnFormatNfc.setOnClickListener {
            pendingWrite = false
            pendingErase = true
            pendingOverwrite = true
            binding.txtImportStatus.text = getString(R.string.hold_nfc_format)
        }

        bottomNav.setOnItemSelectedListener { item ->
            hideAllWorkspaces()
            pendingWrite = false
            pendingErase = false
            pendingOverwrite = false
            binding.btnForceOverwrite.visibility = android.view.View.GONE
            binding.txtImportStatus.text = getString(R.string.ready_import_erase)
            binding.txtGenerateStatus.text = getString(R.string.generate_ready_msg)
            when (item.itemId) {
                R.id.nav_scan -> {
                    workspaceScan.visibility = android.view.View.VISIBLE
                    true
                }
                R.id.nav_import_erase -> {
                    workspaceImportErase.visibility = android.view.View.VISIBLE
                    true
                }
                R.id.nav_generate -> {
                    workspaceGenerate.visibility = android.view.View.VISIBLE
                    true
                }
                R.id.nav_settings -> {
                    workspaceSettings.visibility = android.view.View.VISIBLE
                    true
                }
                R.id.nav_about -> {
                    workspaceAbout.visibility = android.view.View.VISIBLE
                    true
                }
                else -> false
            }
        }
        
        // Initial dev mode state
        binding.layoutDeveloper.visibility = if (checkDeveloper.isChecked) android.view.View.VISIBLE else android.view.View.GONE

        
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        binding.txtStatus.text =
            if (nfcAdapter == null) {
                getString(R.string.msg_nfc_not_supported)
            } else {
                getString(R.string.status_nfc_ready)
            }

        val intentBase = Intent(this, javaClass)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("foreground_dispatch", true)
            
        pendingIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {

                PendingIntent.getActivity(
                    this,
                    0,
                    intentBase,
                    PendingIntent.FLAG_MUTABLE
                )

            } else {

                PendingIntent.getActivity(
                    this,
                    0,
                    intentBase,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )

            }

        intentFilters = arrayOf(
            IntentFilter(
                NfcAdapter.ACTION_TAG_DISCOVERED
            )
        )

        binding.btnScan.setOnClickListener {
            showScanDialog()
        }

        binding.btnGenerate.setOnClickListener {
            val key = com.miftahdhe.keepassnfcbridge.crypto.KeyFileGenerator.generate(128)
            importedKey = key.bytes
            pendingKey = key.bytes
            pendingWrite = true
            
            binding.txtGenerateStatus.text = getString(R.string.key_generated_msg, key.bytes.size, key.sha256)
        }
        binding.btnImportKey.setOnClickListener {

    importLauncher.launch(
        arrayOf("*/*")
    )

}

        binding.btnOpenKeePass.setOnClickListener {
            if (com.miftahdhe.keepassnfcbridge.crypto.KeySessionManager.isSessionActive()) {
                val success = com.miftahdhe.keepassnfcbridge.keepass.KeePassIntegration.sendKeyToKeePass(this)
                if (success) {
                    binding.txtLog.text = getString(R.string.opening_keepassdx)
                } else {
                    binding.txtLog.text = getString(R.string.keepassdx_not_found)
                }
            } else {
                binding.txtLog.text = getString(R.string.msg_key_not_available)
            }
        }

        binding.btnEraseNfc.setOnClickListener {
            pendingErase = true
            pendingWrite = false
            
            binding.txtImportStatus.text = getString(R.string.waiting_erase)

        }
        
        binding.btnForceOverwrite.setOnClickListener {
            if (pendingKey != null) {
                pendingWrite = true
                pendingOverwrite = true
                pendingErase = false
                binding.btnForceOverwrite.visibility = android.view.View.GONE
                binding.txtImportStatus.text = getString(R.string.force_overwrite_active)
                binding.txtGenerateStatus.text = getString(R.string.force_overwrite_active) 
            }
        }
        




        onNewIntent(intent)

    }
        override fun onResume() {
        super.onResume()

        nfcAdapter?.enableForegroundDispatch(
            this,
            pendingIntent,
            intentFilters,
            null
        )
    }

    override fun onPause() {
        super.onPause()

        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (intent == null) return
        
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        if (tag == null) {
            binding.txtStatus.text = getString(R.string.status_nfc_ready)
            return
        }

        val prefs = getSharedPreferences("Settings", Context.MODE_PRIVATE)
        val requireUnlock = prefs.getBoolean("require_unlock", false)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager

        if (requireUnlock && keyguardManager.isKeyguardLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                keyguardManager.requestDismissKeyguard(this, object : android.app.KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        processNfcTag(tag)
                    }
                    override fun onDismissCancelled() {
                        android.widget.Toast.makeText(this@MainActivity, getString(R.string.msg_unlock_required), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    override fun onDismissError() {
                        android.widget.Toast.makeText(this@MainActivity, getString(R.string.msg_unlock_error), android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
            } else {
                android.widget.Toast.makeText(this, getString(R.string.msg_unlock_first), android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            processNfcTag(tag)
        }
    }

    private fun updateActiveMonitor(message: String) {
        when (findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation).selectedItemId) {
            R.id.nav_scan -> binding.txtLog.text = message
            R.id.nav_import_erase -> binding.txtImportStatus.text = message
            R.id.nav_generate -> binding.txtGenerateStatus.text = message
            else -> binding.txtLog.text = message
        }
    }
    
    private fun processNfcTag(tag: Tag) {
        scanDialog?.dismiss()
        if (pendingErase) {

    pendingErase = false

    try {
        val handler = com.miftahdhe.keepassnfcbridge.nfc.handlers.NfcHandlerManager.getHandler(tag)
        if (handler != null) {
            handler.eraseCard(tag)
            playSuccess()
            binding.txtStatus.text = getString(R.string.msg_erase_success)
            updateActiveMonitor(getString(R.string.msg_erase_success_log))
        } else {
            playError()
            binding.txtStatus.text = getString(R.string.msg_erase_failed)
            updateActiveMonitor(getString(R.string.msg_unsupported_card))
        }
    } catch (e: Exception) {
        playError()
        binding.txtStatus.text = getString(R.string.msg_erase_failed)
        updateActiveMonitor(e.message ?: "Unknown Error")
    }

    return
}
        
        if (pendingWrite && pendingKey != null) {

    writeKeyToNfc(tag, pendingKey!!)

    return
}

        val info = NfcReader.read(tag)
        var readKey: String? = null

        try {
            val handler = com.miftahdhe.keepassnfcbridge.nfc.handlers.NfcHandlerManager.getHandler(tag)
            val key = handler?.readKeyFile(tag) ?: throw Exception(getString(R.string.msg_unsupported_card))
            
playSuccess()
            
            val prefs = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
            val timeoutMs = prefs.getLong("timeout_ms", 60000)
            val autoClear = prefs.getBoolean("auto_clear", true)
            
            com.miftahdhe.keepassnfcbridge.crypto.KeySessionManager.startSession(key.bytes, timeoutMs, autoClear) {
                runOnUiThread {
                    binding.txtStatus.text = getString(R.string.status_nfc_ready)
                    binding.txtLog.text = getString(R.string.msg_session_expired_log)
                    binding.txtSessionStatus.text = getString(R.string.session_expired_dot)
                    binding.txtSessionStatus.setTextColor(android.graphics.Color.parseColor("#FF5252"))
                    binding.txtKeyFile.text = getString(R.string.msg_ram_empty)
                    updateOpenKeePassButton()
                }
            }
            val sessionText = if (autoClear) getString(R.string.session_active_with_time, timeoutMs/1000) else getString(R.string.session_active_no_timeout)
            binding.txtSessionStatus.text = sessionText
            binding.txtSessionStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            binding.txtKeyFile.text = getString(R.string.key_loaded_msg, key.bytes.size, key.sha256)
            updateOpenKeePassButton()
            
            // Auto open KeePassDX feature
            val action = intent.action
            val isForeground = intent.getBooleanExtra("foreground_dispatch", false)
            if (!isForeground && (action == NfcAdapter.ACTION_TAG_DISCOVERED || action == NfcAdapter.ACTION_TECH_DISCOVERED || action == NfcAdapter.ACTION_NDEF_DISCOVERED)) {
                // If the activity was started by tapping a tag from outside, launch KeePassDX immediately
                val launched = com.miftahdhe.keepassnfcbridge.keepass.KeePassIntegration.sendKeyToKeePass(this)
                if (launched) {
                    binding.txtLog.text = getString(R.string.opening_keepassdx_auto)
                }
            }
            
            readKey = if (key.isKpnfc01) {
                """
[ KPNFC01 CONTAINER DETECTED ]
Version    : ${key.containerVersion}
Size       : ${key.containerSize} Bytes
Key SHA-256: ${key.sha256}
Integrity  : Valid Checksum
                """.trimIndent()
            } else {
                """
[ LEGACY KEYFILE DETECTED ]
Size       : ${key.bytes.size} Bytes
Key SHA-256: ${key.sha256}
                """.trimIndent()
            }
        } catch (e: Exception) {
            if (e.message == "EMPTY_CARD") {
                binding.txtStatus.text = getString(R.string.empty_card)
                readKey = "[ KARTU KOSONG ]\nBelum ada Key File pada kartu."
            } else if (e is com.miftahdhe.keepassnfcbridge.nfc.ContainerException) {
                readKey = """
[ KPNFC01 ERROR ]
${e.message}
                """.trimIndent()
            } else if (e.message?.contains("Tag bukan MIFARE Ultralight") == true) {
                binding.txtStatus.text = getString(R.string.unsupported_card)
                readKey = "Kartu ini tidak didukung untuk Key File (Membutuhkan NTAG/MIFARE Ultralight)."
            } else {
                readKey = "Error: ${e.message}"
            }
        }
        val dump = TagDumper.dump(tag)
        binding.txtStatus.text = getString(R.string.card_detected)
        
        val isDev = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE).getBoolean("developer_mode", false)
        if (isDev) {
            binding.layoutDeveloper.visibility = android.view.View.VISIBLE
        } else {
            binding.layoutDeveloper.visibility = android.view.View.GONE
        }
        
        binding.txtUid.text = getString(R.string.uid_format, info.uid)
        binding.txtType.text = getString(R.string.type_format, info.tagType)
        binding.txtTechnology.text = buildString {
            info.technologies.forEach { appendLine("✓ $it") }
            appendLine()
            appendLine("Tech Count: ${info.techCount}")
            appendLine("Memory: ${info.memory}")
        }
        
        updateActiveMonitor(buildString {
            appendLine("NFC Card Detected")
            appendLine("UID: ${info.uid}")
            appendLine("Type: ${info.tagType}")
            appendLine()
            
            if (readKey != null) {
                appendLine(readKey)
                appendLine()
            }
            
            if (isDev) {
                if (dump.success) {
                    appendLine("===== FULL DUMP =====")
                    dump.pages.forEach { page ->
                        appendLine(page)
                    }
                } else {
                    appendLine("Dump Gagal: ${dump.error ?: "Unknown Error"}")
                }
            }
        })
    }

    
    private fun writeKeyToNfc(tag: Tag, key: ByteArray) {
        val techList = tag.techList.joinToString(", ") { it.split(".").last() }
        var capacityStr = "Unknown"
        var availableSpace = -1
        var cardType = "Unknown"
        
        val mifare = MifareUltralight.get(tag)
        val ndef = Ndef.get(tag)
        if (mifare != null) {
            try {
                mifare.connect()
                val layout = NfcCapacity.detect(mifare)
                cardType = layout.name
                availableSpace = layout.userBytes
                capacityStr = "${layout.userBytes} Bytes"
                mifare.close()
            } catch(e:Exception) {}
        } else if (ndef != null) {
            availableSpace = ndef.maxSize
            capacityStr = "${ndef.maxSize} Bytes"
            cardType = "NDEF Compatible"
        }

        val requiredSpace = key.size + 15
        val hasEnoughSpace = availableSpace == -1 || availableSpace >= requiredSpace
        val statusText = if (hasEnoughSpace) "Compatible" else "Not Enough Space"
        
        val msg = StringBuilder()
        msg.append("NFC Card:\n")
        msg.append("- Card Type: $cardType\n")
        msg.append("- NFC Technology: $techList\n")
        msg.append("- Total Capacity: $capacityStr\n")
        msg.append("- Available Space: $capacityStr\n\n")
        
        msg.append("Key File:\n")
        msg.append("- File Size: ${key.size} Bytes\n")
        msg.append("- Required Space: ~$requiredSpace Bytes\n")
        msg.append("- Status: $statusText\n\n")
        
        if (!hasEnoughSpace) {
            msg.append("Please check your NFC card capacity before storing a key file.\n")
            msg.append("Different NFC cards have different storage sizes and security capabilities.\n")
            msg.append("For larger key files and higher security requirements, use NFC hardware with higher capacity and advanced security features.")
        } else {
            msg.append("Proceed with writing?")
        }
        
        if (!hasEnoughSpace) {
            AlertDialog.Builder(this)
                .setTitle("Capacity Warning")
                .setMessage(msg.toString())
                .setPositiveButton("Try Anyway") { _, _ -> performWriteKeyToNfc(tag, key) }
                .setNegativeButton("Cancel") { dialog, _ -> 
                    pendingWrite = false
                    pendingOverwrite = false
                    pendingKey = null
                    binding.btnForceOverwrite.visibility = android.view.View.GONE
                    binding.txtStatus.text = "Write Cancelled"
                    dialog.dismiss() 
                }
                .show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("NFC Information")
                .setMessage(msg.toString())
                .setPositiveButton("Write") { _, _ -> performWriteKeyToNfc(tag, key) }
                .setNegativeButton("Cancel") { dialog, _ -> 
                    pendingWrite = false
                    pendingOverwrite = false
                    pendingKey = null
                    binding.btnForceOverwrite.visibility = android.view.View.GONE
                    binding.txtStatus.text = "Write Cancelled"
                    dialog.dismiss() 
                }
                .show()
        }
    }

    private fun performWriteKeyToNfc(tag: Tag, key: ByteArray) {
        binding.txtStatus.text = getString(R.string.writing_key_file)


    try {
        val handler = com.miftahdhe.keepassnfcbridge.nfc.handlers.NfcHandlerManager.getHandler(tag)
        if (handler != null) {
            handler.writeKeyFile(tag, key, overwrite = pendingOverwrite)
            playSuccess()
            pendingWrite = false
            pendingOverwrite = false
            pendingKey = null

            binding.btnForceOverwrite.visibility = android.view.View.GONE

            binding.txtStatus.text = getString(R.string.msg_write_success)

            updateActiveMonitor(getString(R.string.msg_write_success_log))
        } else {
            playError()
            binding.txtStatus.text = getString(R.string.msg_write_failed)
            updateActiveMonitor(getString(R.string.msg_unsupported_card))
        }
    } catch (e: Exception) {
        val reason = e.message ?: "Unknown Error"
        playError()
        binding.txtStatus.text = getString(R.string.msg_write_failed)
        updateActiveMonitor(reason)
        if (reason == "Card Contains Other Data" || reason == "Card Already Contains Key File") {
            binding.btnForceOverwrite.visibility = android.view.View.VISIBLE
        }
    }
}

    private fun updateOpenKeePassButton() {
        if (com.miftahdhe.keepassnfcbridge.crypto.KeySessionManager.isSessionActive()) {
            binding.btnOpenKeePass.isEnabled = true
            binding.btnOpenKeePass.alpha = 1.0f
        } else {
            binding.btnOpenKeePass.isEnabled = false
            binding.btnOpenKeePass.alpha = 0.5f
        }
    }

    private fun playSuccess() {
        if (!isSoundEnabled()) return
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(100)
            }
        } catch (_: Exception) {}
    }

    private fun playError() {
        if (!isSoundEnabled()) return
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_ALERT, 300)
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(300)
            }
        } catch (_: Exception) {}
    }

    private var scanDialog: android.app.AlertDialog? = null

    private fun showScanDialog() {
        val view = layoutInflater.inflate(com.miftahdhe.keepassnfcbridge.R.layout.dialog_scan_nfc, null)
        val imgGlow = view.findViewById<android.widget.ImageView>(com.miftahdhe.keepassnfcbridge.R.id.imgGlow)
        val imgCard = view.findViewById<android.widget.ImageView>(com.miftahdhe.keepassnfcbridge.R.id.imgCard)
        
        // Wave animation
        val scaleX = android.animation.ObjectAnimator.ofFloat(imgGlow, "scaleX", 0.8f, 1.5f)
        val scaleY = android.animation.ObjectAnimator.ofFloat(imgGlow, "scaleY", 0.8f, 1.5f)
        val alpha = android.animation.ObjectAnimator.ofFloat(imgGlow, "alpha", 0.8f, 0f)
        
        scaleX.repeatCount = android.animation.ValueAnimator.INFINITE
        scaleY.repeatCount = android.animation.ValueAnimator.INFINITE
        alpha.repeatCount = android.animation.ValueAnimator.INFINITE
        
        val waveAnim = android.animation.AnimatorSet()
        waveAnim.playTogether(scaleX, scaleY, alpha)
        waveAnim.duration = 1500
        waveAnim.start()
        
        // Pulse animation
        val pulseX = android.animation.ObjectAnimator.ofFloat(imgCard, "scaleX", 1f, 1.1f, 1f)
        val pulseY = android.animation.ObjectAnimator.ofFloat(imgCard, "scaleY", 1f, 1.1f, 1f)
        pulseX.repeatCount = android.animation.ValueAnimator.INFINITE
        pulseY.repeatCount = android.animation.ValueAnimator.INFINITE
        
        val pulseAnim = android.animation.AnimatorSet()
        pulseAnim.playTogether(pulseX, pulseY)
        pulseAnim.duration = 1000
        pulseAnim.start()

        val builder = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        builder.setView(view)
        scanDialog = builder.create()
        scanDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        scanDialog?.show()
        playScanStart()
    }

    private fun isSoundEnabled(): Boolean {
        val prefs = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean("enable_sound", true)
    }

    private fun playScanStart() {
        if (!isSoundEnabled()) return
        try {
            val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 50)
            toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 50)
        } catch (_: Exception) {}
    }

    private fun setupAboutPage() {
        setupToggleCard(R.id.card1, R.id.desc1)
        setupToggleCard(R.id.card2, R.id.desc2)
        setupToggleCard(R.id.card3, R.id.desc3)
        setupToggleCard(R.id.card4, R.id.desc4)
        setupToggleCard(R.id.card5, R.id.desc5)
        setupToggleCard(R.id.card6, R.id.desc6)
        setupToggleCard(R.id.card7, R.id.desc7)
        setupToggleCard(R.id.card8, R.id.desc8)
        setupToggleCard(R.id.card9, R.id.desc9)

        val btcAddress = "bc1qk59s9y592p7ggc377meyj5zzw7ukky5yg0e08v"
        val btcUri = "bitcoin:$btcAddress"
        val xmrAddress = "4BAi2AfhsUfFXUJTDJnhRq7pt2TdreeYejZUpADE9WdZfkYxkP5YevfQv9kZQ3yfKR1CFDRUwmfnDHsNPNkKJEvZ28AUBqA"
        val xmrUri = "monero:$xmrAddress"

        val imgBtcQr = findViewById<android.widget.ImageView>(R.id.imgBtcQr)
        val imgXmrQr = findViewById<android.widget.ImageView>(R.id.imgXmrQr)

        imgBtcQr.setImageBitmap(generateQrCode(btcUri))
        imgXmrQr.setImageBitmap(generateQrCode(xmrUri))

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCopyBtc).setOnClickListener {
            copyToClipboard("BTC Address", btcAddress)
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCopyXmr).setOnClickListener {
            copyToClipboard("XMR Address", xmrAddress)
        }
        
        val btnBack = findViewById<android.widget.ImageView>(R.id.btnBack)
        if (btnBack != null) {
            btnBack.visibility = android.view.View.GONE
        }
    }

    private fun setupToggleCard(cardId: Int, descId: Int) {
        val card = findViewById<com.google.android.material.card.MaterialCardView>(cardId)
        val desc = findViewById<android.view.View>(descId)
        card.setOnClickListener {
            desc.visibility = if (desc.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun generateQrCode(content: String): android.graphics.Bitmap? {
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(this, "$label copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
    }
}
