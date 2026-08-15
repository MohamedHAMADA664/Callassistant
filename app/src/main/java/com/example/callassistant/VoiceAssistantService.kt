package com.example.callassistant

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * خدمة تعمل باستمرار في الخلفية:
 * 1) تستمع لكلمة التنبيه (Wake Word) "يا مساعد"
 * 2) بعد سماعها، تستمع للأمر وتنفذه (اتصال، حفظ رقم، رد، رفض)
 */
class VoiceAssistantService : Service() {

    companion object {
        private const val TAG = "VoiceAssistantService"
        private const val WAKE_WORD = "يا مساعد"
        private const val CHANNEL_ID = "voice_assistant_channel"
        private const val NOTIF_ID = 1001
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var listeningForCommand = false
    private var consecutiveErrors = 0
    private val commandTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val restartHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()

        // مهم على أندرويد 14+: لازم RECORD_AUDIO يكون ممنوح فعلاً قبل
        // startForeground بنوع microphone، وإلا SecurityException وكراش فوري
        val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasMicPermission) {
            Log.e(TAG, "RECORD_AUDIO not granted - stopping service")
            stopSelf()
            return
        }

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("المساعد الصوتي شغال..."))
        TTSHelper.init(this)
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        commandTimeoutHandler.removeCallbacksAndMessages(null)
        restartHandler.removeCallbacksAndMessages(null)
    }

    // ---------- الاستماع المستمر ----------

    private fun startListening() {
        if (speechRecognizer != null) {
            speechRecognizer?.destroy()
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(recognitionListener)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("ar"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        muteSystemBeepTemporarily()
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening error: ${e.message}")
        }
    }

    /**
     * SpeechRecognizer بيشغل beep نظام مع كل جلسة استماع جديدة، وده مصدر
     * صوت "فتح/قفل المايك" المتكرر اللي بيضايق مع الاستماع المستمر.
     * مفيش API رسمي لإيقافه، لكن الحل الشائع إننا نكتم STREAM_SYSTEM
     * لحظة بدء الجلسة فقط، وده بيمنع الصوت من غير ما يأثر على باقي
     * أصوات الموبايل (زي المكالمات نفسها اللي على stream مختلف).
     */
    private fun muteSystemBeepTemporarily() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            audioManager.adjustStreamVolume(
                android.media.AudioManager.STREAM_SYSTEM,
                android.media.AudioManager.ADJUST_MUTE,
                0
            )
            android.os.Handler(mainLooper).postDelayed({
                audioManager.adjustStreamVolume(
                    android.media.AudioManager.STREAM_SYSTEM,
                    android.media.AudioManager.ADJUST_UNMUTE,
                    0
                )
            }, 500)
        } catch (e: Exception) {
            // بعض الأجهزة بترفض كتم STREAM_SYSTEM بدون صلاحية إضافية -
            // مش مشكلة خطيرة، هيفضل الصوت شغال بس البرنامج يكمل عادي
            Log.w(TAG, "Could not mute system beep: ${e.message}")
        }
    }

    private fun restartListening() {
        // backoff بسيط: كل ما الأخطاء المتتالية تزيد، نستنى أكتر قبل
        // إعادة المحاولة - ده بيوفر بطارية لما مفيش صوت حوالين الموبايل خالص
        // بدل ما نضرب SpeechRecognizer.startListening كل نص ثانية بلا فايدة
        val delay = when {
            consecutiveErrors > 10 -> 3000L
            consecutiveErrors > 3 -> 1000L
            else -> 300L
        }
        restartHandler.removeCallbacksAndMessages(null)
        restartHandler.postDelayed({ startListening() }, delay)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            // لو حصل خطأ أو صمت، نعيد الاستماع تلقائيًا (استماع دائم)
            consecutiveErrors++
            restartListening()
        }

        override fun onResults(results: Bundle?) {
            consecutiveErrors = 0
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()?.trim() ?: ""
            Log.d(TAG, "Heard: $text")
            handleRecognizedText(text)
            restartListening()
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // ---------- معالجة الكلام ----------

    private fun handleRecognizedText(text: String) {
        if (text.isEmpty()) return

        if (!listeningForCommand) {
            // بنستنى كلمة التنبيه الأول
            if (text.contains(WAKE_WORD) || text.contains("مساعد")) {
                listeningForCommand = true
                TTSHelper.speak("قولي")
                // لو المستخدم قال "يا مساعد" وسكت، نرجع لوضع الانتظار
                // تلقائيًا بعد 6 ثواني بدل ما نفضل عالقين في وضع الأمر
                commandTimeoutHandler.removeCallbacksAndMessages(null)
                commandTimeoutHandler.postDelayed({ listeningForCommand = false }, 6000)
            }
            return
        }

        // دلوقتي هو بيقول الأمر
        commandTimeoutHandler.removeCallbacksAndMessages(null)
        listeningForCommand = false
        executeCommand(text)
    }

    private fun executeCommand(command: String) {
        val c = command.trim()

        when {
            // "اتصل على/ب <اسم أو رقم>"
            c.startsWith("اتصل") -> {
                // مهم: لازم نشيل بادئة "على" أو "ب" من أول الكلام بس،
                // مش أي حرف "ب" في أي حتة من الجملة - وإلا أسماء زي
                // "عبدالله" أو "بسمة" هتتقطع لأن فيها حرف الباء جواها
                var target = c.removePrefix("اتصل").trim()
                target = when {
                    target.startsWith("على ") -> target.removePrefix("على ")
                    target.startsWith("ب") -> target.removePrefix("ب")
                    else -> target
                }.trim()
                callByNameOrNumber(target)
            }

            // "رد" أو "رد على المكالمة"
            c.contains("رد") -> {
                InCallServiceImpl.answerCurrentCall()
                TTSHelper.speak("تم الرد")
            }

            // "ارفض" أو "ارفض المكالمة"
            c.contains("ارفض") || c.contains("رفض") -> {
                InCallServiceImpl.rejectCurrentCall()
                TTSHelper.speak("تم الرفض")
            }

            // "سجل الرقم ده باسم <اسم>" - محتاج نكون عارفين آخر رقم دخل مكالمة
            c.contains("سجل") -> {
                val name = c.substringAfter("باسم").trim()
                val number = InCallServiceImpl.currentCall?.details?.handle?.schemeSpecificPart
                if (!number.isNullOrEmpty() && name.isNotEmpty()) {
                    val saved = ContactsHelper.saveContact(this, name, number)
                    TTSHelper.speak(if (saved) "تم حفظ الرقم باسم $name" else "حصل خطأ في الحفظ")
                } else {
                    TTSHelper.speak("مش لاقي رقم لحفظه")
                }
            }

            // مفيش نمط محلي اتطابق - نجرب AI لو مفعّل، وإلا نعتذر
            else -> {
                tryAiFallback(c)
            }
        }
    }

    /**
     * لما التحليل المحلي (regex) مايلاقيش أي نمط معروف، بنبعت الجملة
     * لـ AI (لو مفعّل) كخطة بديلة - مفيد لما الجملة متقالة بصياغة
     * مختلفة أو Speech-to-Text سمعها مش مضبوطة 100%
     */
    private fun tryAiFallback(originalText: String) {
        if (!Config.AI_ENABLED) {
            TTSHelper.speak("لم أفهم الأمر")
            return
        }
        AiCommandParser.parse(originalText) { result ->
            android.os.Handler(mainLooper).post {
                if (result == null) {
                    TTSHelper.speak("لم أفهم الأمر")
                    return@post
                }
                when (result.action) {
                    "call" -> callByNameOrNumber(result.target)
                    "answer" -> {
                        InCallServiceImpl.answerCurrentCall()
                        TTSHelper.speak("تم الرد")
                    }
                    "reject" -> {
                        InCallServiceImpl.rejectCurrentCall()
                        TTSHelper.speak("تم الرفض")
                    }
                    "save_contact" -> {
                        val number = InCallServiceImpl.currentCall?.details?.handle?.schemeSpecificPart
                        if (!number.isNullOrEmpty() && result.target.isNotEmpty()) {
                            val saved = ContactsHelper.saveContact(this, result.target, number)
                            TTSHelper.speak(if (saved) "تم حفظ الرقم باسم ${result.target}" else "حصل خطأ في الحفظ")
                        } else {
                            TTSHelper.speak("مش لاقي رقم لحفظه")
                        }
                    }
                    else -> TTSHelper.speak("لم أفهم الأمر")
                }
            }
        }
    }

    private fun callByNameOrNumber(target: String) {
        if (target.isEmpty()) {
            TTSHelper.speak("قولي اسم أو رقم مين أتصل عليه")
            return
        }
        // لو المدخل رقم بالفعل
        val isNumber = target.all { it.isDigit() || it == '+' || it == ' ' }
        val numberToCall = if (isNumber) {
            target.replace(" ", "")
        } else {
            ContactsHelper.findNumberByName(this, target)
        }

        if (numberToCall.isNullOrEmpty()) {
            TTSHelper.speak("مش لاقي حد اسمه $target")
            return
        }

        TTSHelper.speak("جاري الاتصال ب $target")
        ContactsHelper.callNumber(this, numberToCall)
    }

    // ---------- الإشعار (مطلوب لأي Foreground Service) ----------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "المساعد الصوتي", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("مساعد المكالمات")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
}
