package com.example.callassistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.callassistant.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.READ_CALL_LOG,
        android.Manifest.permission.CALL_PHONE,
        android.Manifest.permission.ANSWER_PHONE_CALLS,
        android.Manifest.permission.READ_CONTACTS,
        android.Manifest.permission.WRITE_CONTACTS,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            updateStatus("الصلاحيات اتاخدت ✅ - اضغط 'اجعله تطبيق الاتصال الافتراضي'")
        } else {
            updateStatus("لازم توافق على كل الصلاحيات عشان البرنامج يشتغل")
        }
    }

    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkDefaultDialerAndUpdate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantPermissions.setOnClickListener { requestAllPermissions() }
        binding.btnDefaultDialer.setOnClickListener { requestDefaultDialer() }
        binding.btnStartService.setOnClickListener { startVoiceService() }
        binding.btnStopService.setOnClickListener { stopVoiceService() }

        requestAllPermissions()
        checkDefaultDialerAndUpdate()
    }

    private fun requestAllPermissions() {
        val missing = requiredPermissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            updateStatus("الصلاحيات موجودة ✅")
        }
    }

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            ) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                defaultDialerLauncher.launch(intent)
            } else if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                updateStatus("هو أصلاً تطبيق الاتصال الافتراضي ✅")
            } else {
                // بعض الأجهزة (شاومي، سامسونج بعض النسخ) بتخفي أو تعطل الـ
                // Role ده، فبنفتح إعدادات Default Apps يدويًا كحل بديل
                openManualDefaultAppsSettings()
            }
        } else {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            defaultDialerLauncher.launch(intent)
        }
    }

    private fun openManualDefaultAppsSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
            updateStatus("افتح 'Phone app' أو 'تطبيق الهاتف' من الشاشة اللي فتحت واختار مساعد المكالمات")
        } catch (e: Exception) {
            updateStatus("الجهاز ده مش بيسمح بتغيير تطبيق الهاتف الافتراضي من التطبيق - جرب من إعدادات الموبايل مباشرة: Settings > Apps > Default apps")
        }
    }

    private fun checkDefaultDialerAndUpdate() {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val isDefault = telecomManager.defaultDialerPackage == packageName
        binding.tvDialerStatus.text =
            if (isDefault) "التطبيق مسجل كتطبيق الاتصال الافتراضي ✅"
            else "لسه مش تطبيق الاتصال الافتراضي ❌ (لازم عشان الرد/الرفض الصوتي يشتغل)"
    }

    private fun startVoiceService() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
        updateStatus("المساعد الصوتي شغال - قول \"يا مساعد\" وبعدين الأمر")
    }

    private fun stopVoiceService() {
        stopService(Intent(this, VoiceAssistantService::class.java))
        updateStatus("المساعد الصوتي متوقف")
    }

    private fun updateStatus(text: String) {
        binding.tvStatus.text = text
    }
}
