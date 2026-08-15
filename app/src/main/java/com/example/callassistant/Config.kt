package com.example.callassistant

object Config {
    /**
     * مفتاح مجاني من https://console.groq.com (سجل بإيميلك، مجاني تمامًا،
     * مفيش كارت ائتمان مطلوب، وسريع جدًا في الرد). لصقه هنا بين
     * علامتي التنصيص.
     *
     * لو سبته فاضي، البرنامج هيشتغل بالتحليل المحلي بس (بدون AI) وهيفضل
     * يشتغل عادي، بس هيكون أضعف في فهم الجمل الغريبة أو الأسماء المعقدة.
     */
    const val GROQ_API_KEY = ""

    // موديل مجاني سريع من Groq، مناسب لفهم أوامر قصيرة
    const val GROQ_MODEL = "llama-3.1-8b-instant"

    val AI_ENABLED: Boolean get() = GROQ_API_KEY.isNotBlank()
}
