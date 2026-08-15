package com.example.callassistant

import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.callassistant.databinding.ActivityCallBinding

/**
 * شاشة موحدة للمكالمة: بتتغير حسب حالة المكالمة.
 * - RINGING  -> زرار رد كبير + زرار رفض كبير
 * - ACTIVE   -> اسم المتصل + وقت المكالمة + ميوت/سبيكر/إنهاء + حفظ كجهة اتصال
 * بتفتح فوق الشاشة المقفولة زي أي تطبيق اتصال حقيقي.
 */
class CallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallBinding
    private var isMuted = false
    private var isSpeakerOn = false
    private var currentCallRef: Call? = null

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            runOnUiThread { renderForState(state) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        binding = ActivityCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentCallRef = InCallServiceImpl.currentCall
        currentCallRef?.registerCallback(callCallback)

        val number = currentCallRef?.details?.handle?.schemeSpecificPart ?: ""
        val name = ContactsHelper.getNameForNumber(this, number)
        binding.tvCallerName.text = name
        binding.tvCallerNumber.text = number

        binding.btnAnswer.setOnClickListener {
            InCallServiceImpl.answerCurrentCall()
        }
        binding.btnReject.setOnClickListener {
            InCallServiceImpl.rejectCurrentCall()
            finish()
        }
        binding.btnEndCall.setOnClickListener {
            InCallServiceImpl.endCurrentCall()
            finish()
        }
        binding.btnMute.setOnClickListener { toggleMute() }
        binding.btnSpeaker.setOnClickListener { toggleSpeaker() }
        binding.btnSaveContact.setOnClickListener { showSaveContactDialog(number) }

        renderForState(currentCallRef?.state ?: Call.STATE_RINGING)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentCallRef?.unregisterCallback(callCallback)
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun renderForState(state: Int) {
        when (state) {
            Call.STATE_RINGING -> {
                binding.ringingControls.visibility = android.view.View.VISIBLE
                binding.activeControls.visibility = android.view.View.GONE
                binding.tvCallStatus.text = "مكالمة واردة..."
            }
            Call.STATE_ACTIVE -> {
                binding.ringingControls.visibility = android.view.View.GONE
                binding.activeControls.visibility = android.view.View.VISIBLE
                binding.tvCallStatus.text = "المكالمة جارية"
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                binding.ringingControls.visibility = android.view.View.GONE
                binding.activeControls.visibility = android.view.View.VISIBLE
                binding.tvCallStatus.text = "جاري الاتصال..."
            }
            Call.STATE_DISCONNECTED -> {
                finish()
            }
            else -> {
                binding.tvCallStatus.text = "..."
            }
        }
    }

    private fun toggleMute() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        isMuted = !isMuted
        audioManager.isMicrophoneMute = isMuted
        binding.btnMute.text = if (isMuted) "إلغاء الكتم" else "كتم الصوت"
    }

    private fun toggleSpeaker() {
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        isSpeakerOn = !isSpeakerOn
        audioManager.isSpeakerphoneOn = isSpeakerOn
        binding.btnSpeaker.text = if (isSpeakerOn) "إيقاف السماعة" else "مكبر الصوت"
    }

    private fun showSaveContactDialog(number: String) {
        val input = android.widget.EditText(this)
        input.hint = "اسم جهة الاتصال"
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("حفظ الرقم")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    ContactsHelper.saveContact(this, name, number)
                    TTSHelper.speak("تم حفظ الرقم باسم $name")
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }
}
