package com.genie.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Reply(val raw: String, val json: JSONObject)

object Brain {

    private const val MODEL = "claude-sonnet-5-5"

    private const val SYSTEM = """أنت «المارد»، مساعد صوتي يتحكم بهاتف أندرويد بأوامر المستخدم الصوتية.
ترد دائماً بكائن JSON واحد فقط بدون أي نص قبله أو بعده وبدون علامات markdown، بهذا الشكل:
{"say":"جملة قصيرة تُنطق بصوت عالٍ","actions":[...],"continue":false}

الأفعال المتاحة داخل actions:
{"type":"open_app","app":"اسم التطبيق بالضبط كما في قائمة التطبيقات المثبتة"}
{"type":"click","id":رقم العنصر من قائمة الشاشة}
{"type":"type","text":"النص المراد كتابته في الحقل المحدد أو أول حقل نص"}
{"type":"scroll","dir":"down"} أو {"type":"scroll","dir":"up"}
{"type":"back"} {"type":"home"} {"type":"recents"}
{"type":"persona","value":"genie"} أو "male" أو "female" لتغيير شخصيتك
{"type":"quit"} لإغلاق المارد

القواعد:
- say قصير جداً (جملة أو جملتين) وبلهجة بسيطة مفهومة، لأنه يُنطق صوتياً.
- إذا احتجت أن ترى الشاشة بعد تنفيذ الأفعال لتكمل المهمة ضع continue=true وسيصلك وصف الشاشة الجديدة. عند انتهاء المهمة ضع continue=false.
- استخدم أرقام العناصر من آخر وصف شاشة وصلك فقط.
- لا ترسل رسالة ولا تحذف شيئاً ولا تدفع ولا تشتري إلا إذا طلب المستخدم ذلك صراحة. إذا كان الطلب غامضاً اسأل في say واجعل actions فارغة.
- إذا كان الطلب مجرد سؤال أو دردشة فأجب في say بدون أفعال."""

    fun ask(messages: JSONArray, personaDesc: String): Reply {
        val key = BuildConfig.API_KEY
        if (key.isBlank()) throw Exception("مفتاح الـ API غير موجود في البناء")

        val body = JSONObject()
            .put("model", MODEL)
            .put("max_tokens", 1000)
            .put("system", SYSTEM + "\n\nشخصيتك الحالية: " + personaDesc)
            .put("messages", messages)

        val conn = URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 60000
        conn.doOutput = true
        conn.setRequestProperty("content-type", "application/json")
        conn.setRequestProperty("x-api-key", key)
        conn.setRequestProperty("anthropic-version", "2023-06-01")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val txt = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw Exception("خطأ من الخادم $code")

        val content = JSONObject(txt).getJSONArray("content")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.getString("text"))
        }
        val out = sb.toString()
        val json = try {
            JSONObject(out.substring(out.indexOf('{'), out.lastIndexOf('}') + 1))
        } catch (e: Exception) {
            JSONObject().put("say", out)
        }
        return Reply(out, json)
    }
}
