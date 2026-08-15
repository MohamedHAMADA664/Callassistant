package com.example.callassistant

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * دي الطريقة الرسمية الحديثة لمعرفة إن فيه مكالمة واردة ورقمها (بدل
 * BroadcastReceiver لـ PHONE_STATE اللي بقى غير موثوق على أندرويد الحديث
 * بسبب قيود الـ background execution وإخفاء الرقم أحيانًا لتطبيقات مش
 * مسجلة كـ Default Dialer/Call Screening app).
 *
 * النظام بينادي onScreenCall تلقائيًا مع كل مكالمة واردة طالما التطبيق
 * مسجل كـ Default Dialer أو كـ Caller ID app.
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart

        if (!number.isNullOrEmpty()) {
            TTSHelper.init(applicationContext)
            val name = ContactsHelper.getNameForNumber(applicationContext, number)
            TTSHelper.speak("مكالمة واردة من $name")
        }

        // بنسمح لكل المكالمات تعدي عادي حاليًا؛ الرفض بيتم يدويًا بالأمر
        // الصوتي عبر InCallServiceImpl. لاحقًا ممكن نضيف قائمة سوداء هنا.
        val response = CallScreeningService.CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipNotification(false)
            .build()
        respondToCall(callDetails, response)
    }
}
