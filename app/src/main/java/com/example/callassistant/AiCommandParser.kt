package com.example.callassistant

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * بيستخدم Groq API (مجاني، console.groq.com) لفهم الأوامر اللي التحليل
 * المحلي البسيط (regex) فشل يفهمها - مثلاً لو المستخدم قال الجملة
 * بصياغة مختلفة عن "اتصل ب..." المتوقعة، أو لو Speech-to-Text سمع
 * الكلام بشكل مش مضبوط 100%.
 *
 * ده احتياطي، مش أساسي: البرنامج يشتغل بالكامل من غيره لو مفيش مفتاح
 * أو مفيش إنترنت.
 */
object AiCommandParser {

    private const val TAG = "AiCommandParser"
    private val executor = Executors.newSingleThreadExecutor()

    data class ParsedCommand(val action: String, val target: String)

    /**
     * يبعت النص اللي البرنامج سمعه لـ AI ويرجع الأمر منظم.
     * action ممكن تكون: call / save_contact / answer / reject / unknown
     * بيشتغل على thread خلفية ويرجع النتيجة عن طريق callback على نفس الـ thread
     */
    fun parse(text: String, callback: (ParsedCommand?) -> Unit) {
        if (!Config.AI_ENABLED) {
            callback(null)
            return
        }

        executor.execute {
            try {
                val result = callGroq(text)
                callback(result)
            } catch (e: Exception) {
                Log.e(TAG, "AI parse failed: ${e.message}")
                callback(null)
            }
        }
    }

    private fun callGroq(text: String): ParsedCommand? {
        val url = URL("https://api.groq.com/openai/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer ${Config.GROQ_API_KEY}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 5000

        val systemPrompt = """
            أنت محلل أوامر صوتية لتطبيق اتصال. المستخدم بيتكلم عربي (فصحى أو عامية مصرية).
            حوّل الجملة لـ JSON فقط بدون أي شرح، بالشكل ده بالظبط:
            {"action": "call" أو "save_contact" أو "answer" أو "reject" أو "unknown", "target": "الاسم أو الرقم أو الاسم الجديد لو save_contact"}
            لو الجملة "اتصل بمحمد" رجع {"action":"call","target":"محمد"}
            لو الجملة مش مفهومة رجع {"action":"unknown","target":""}
        """.trimIndent()

        val body = JSONObject().apply {
            put("model", Config.GROQ_MODEL)
            put("temperature", 0)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            })
        }

        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        if (responseCode != 200) {
            Log.e(TAG, "Groq API error: $responseCode")
            return null
        }

        val responseText = connection.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(responseText)
        val content = json
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()

        // الموديل ممكن يرجع الـ JSON ملفوف في ```json أحيانًا، بنشيلها لو موجودة
        val cleanJson = content.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val parsed = JSONObject(cleanJson)
        val action = parsed.optString("action", "unknown")
        val target = parsed.optString("target", "")

        return if (action == "unknown") null else ParsedCommand(action, target)
    }
}
