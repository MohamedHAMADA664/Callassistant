package com.example.callassistant

/**
 * التعرف على الصوت غالبًا بيرجع الاسم بشكل مختلف شوية عن المكتوب في
 * جهات الاتصال (تشكيل زايد، "أ" بدل "ا"، مسافات زيادة...). الدالة دي
 * بتوحد الشكلين قبل المقارنة عشان المطابقة تنجح.
 */
object ArabicNormalizer {

    private val diacritics = Regex("[\u064B-\u065F\u0670]")

    fun normalize(input: String): String {
        var s = input.trim()
        s = diacritics.replace(s, "")
        s = s.replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace('ؤ', 'و')
            .replace('ئ', 'ي')
        s = s.replace(Regex("\\s+"), " ").trim()
        return s.lowercase()
    }
}
