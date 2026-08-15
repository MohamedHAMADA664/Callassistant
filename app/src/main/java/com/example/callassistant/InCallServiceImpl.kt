package com.example.callassistant

import android.telecom.Call
import android.telecom.InCallService

/**
 * دي الخدمة اللي بتخلي التطبيق يشتغل كـ "تطبيق اتصال" (Dialer).
 * لازم التطبيق يبقى Default Dialer عشان الرد/الرفض البرمجي يشتغل فعليًا.
 */
class InCallServiceImpl : InCallService() {

    companion object {
        // نحتفظ بالمكالمة الحالية عشان أوامر الصوت (رد/رفض) تقدر توصلها من أي مكان في التطبيق
        var currentCall: Call? = null
            private set

        /** يرد على المكالمة الحالية - يُستدعى من أي مكان في التطبيق */
        fun answerCurrentCall() {
            currentCall?.answer(0)
        }

        /** يرفض المكالمة الحالية - يُستدعى من أي مكان في التطبيق */
        fun rejectCurrentCall() {
            currentCall?.reject(false, null)
        }

        /** ينهي مكالمة جارية */
        fun endCurrentCall() {
            currentCall?.disconnect()
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) {
                if (currentCall == call) currentCall = null
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        call.registerCallback(callCallback)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (currentCall == call) currentCall = null
    }
}
