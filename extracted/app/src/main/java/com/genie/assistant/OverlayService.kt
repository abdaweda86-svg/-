package com.genie.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

class OverlayService : Service() {

    private var wm: WindowManager? = null
    private var view: GenieView? = null
    private lateinit var params: WindowManager.LayoutParams
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())
    private var busy = false
    private var persona = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        if (view == null) setup()
        return START_STICKY
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("genie", "المارد", NotificationManager.IMPORTANCE_LOW)
        )
        val n = Notification.Builder(this, "genie")
            .setContentTitle("المارد شغّال")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, n)
        }
    }

    private fun setup() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = manager
        val prefs = getSharedPreferences("genie", MODE_PRIVATE)
        persona = prefs.getInt("persona", 0)
        val dp = resources.displayMetrics.density

        val v = GenieView(this)
        v.persona = persona
        view = v

        val size = (110 * dp).toInt()
        params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = (16 * dp).toInt()
        params.y = (300 * dp).toInt()

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        v.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f
            var sy = 0f
            var ox = 0
            var oy = 0
            var moved = false
            var longFired = false
            val longRun = Runnable {
                longFired = true
                cyclePersona()
            }

            override fun onTouch(view: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX; sy = e.rawY
                        ox = params.x; oy = params.y
                        moved = false; longFired = false
                        main.postDelayed(longRun, 700)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - sx
                        val dy = e.rawY - sy
                        if (!moved && (abs(dx) > slop || abs(dy) > slop)) {
                            moved = true
                            main.removeCallbacks(longRun)
                        }
                        if (moved) {
                            params.x = (ox + dx).toInt()
                            params.y = (oy + dy).toInt()
                            manager.updateViewLayout(view, params)
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        main.removeCallbacks(longRun)
                        if (e.action == MotionEvent.ACTION_UP && !moved && !longFired) onTap()
                    }
                }
                return true
            }
        })
        manager.addView(v, params)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ar")
                applyVoice()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) { main.post { view?.state = GenieView.IDLE } }
                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) { main.post { view?.state = GenieView.IDLE } }
                })
                main.post { speak(greeting()) }
            }
        }
    }

    private fun greeting() = when (persona) {
        0 -> "شبيك لبيك، عبدك بين إيديك"
        1 -> "هلا، أنا جاهز"
        else -> "هلا، أنا جاهزة"
    }

    private fun personaDesc() = when (persona) {
        0 -> "مارد ظريف يتكلم بصيغة المذكر ويقول «شبيك لبيك» أحياناً"
        1 -> "شاب ودود يتكلم بصيغة المذكر"
        else -> "بنت ودودة تتكلم بصيغة المؤنث"
    }

    private fun applyVoice() {
        when (persona) {
            0 -> { tts?.setPitch(0.55f); tts?.setSpeechRate(0.9f) }
            1 -> { tts?.setPitch(0.85f); tts?.setSpeechRate(1.0f) }
            else -> { tts?.setPitch(1.35f); tts?.setSpeechRate(1.05f) }
        }
    }

    private fun setPersona(n: Int) {
        persona = n
        view?.persona = n
        getSharedPreferences("genie", MODE_PRIVATE).edit().putInt("persona", n).apply()
        applyVoice()
    }

    private fun cyclePersona() {
        setPersona((persona + 1) % 3)
        speak(greeting())
    }

    private fun speak(text: String) {
        view?.state = GenieView.SPEAKING
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "u" + System.nanoTime())
    }

    private fun onTap() {
        if (busy) return
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("خدمة التعرف على الصوت غير متوفرة")
            return
        }
        tts?.stop()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-JO")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        view?.state = GenieView.LISTENING
        recognizer?.startListening(i)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            view?.state = GenieView.IDLE
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> speak("ما سمعتك، جرّب مرة ثانية")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> speak("ما عندي إذن المايك")
                else -> {}
            }
        }

        override fun onResults(results: Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (t.isNullOrBlank()) view?.state = GenieView.IDLE else handle(t)
        }
    }

    private fun handle(text: String) {
        busy = true
        view?.state = GenieView.THINKING
        Thread {
            try {
                runAgent(text)
            } catch (e: Exception) {
                main.post { speak("صار خطأ، " + (e.message ?: "")) }
            } finally {
                main.post {
                    busy = false
                    if (view?.state == GenieView.THINKING) view?.state = GenieView.IDLE
                }
            }
        }.start()
    }

    private fun launchables(): List<ResolveInfo> =
        packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )

    private fun screen(): String =
        AssistantAccessibilityService.instance?.dump() ?: "(خدمة إمكانية الوصول غير مفعّلة)"

    private fun runAgent(text: String) {
        val msgs = JSONArray()
        val apps = launchables().map { it.loadLabel(packageManager).toString() }
            .distinct().take(150).joinToString("، ")
        var content = "الأمر: $text\n\nالتطبيقات المثبتة: $apps\n\nالشاشة:\n" + screen()

        for (step in 0 until 6) {
            msgs.put(JSONObject().put("role", "user").put("content", content))
            val r = Brain.ask(msgs, personaDesc())
            msgs.put(JSONObject().put("role", "assistant").put("content", r.raw))

            val say = r.json.optString("say", "")
            if (say.isNotBlank()) main.post { speak(say) }

            val actions = r.json.optJSONArray("actions") ?: JSONArray()
            for (i in 0 until actions.length()) {
                exec(actions.getJSONObject(i))
                Thread.sleep(700)
            }
            if (!r.json.optBoolean("continue", false)) return
            Thread.sleep(800)
            content = "الشاشة بعد التنفيذ:\n" + screen()
        }
    }

    private fun exec(a: JSONObject) {
        val svc = AssistantAccessibilityService.instance
        when (a.optString("type")) {
            "open_app" -> openApp(a.optString("app"))
            "click" -> svc?.clickNode(a.optInt("id", -1))
            "type" -> svc?.typeText(a.optString("text"))
            "scroll" -> svc?.scroll(a.optString("dir") != "up")
            "back" -> svc?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> svc?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> svc?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            "persona" -> main.post {
                when (a.optString("value")) {
                    "male" -> setPersona(1)
                    "female" -> setPersona(2)
                    else -> setPersona(0)
                }
            }
            "quit" -> main.postDelayed({ stopSelf() }, 3000)
        }
    }

    private fun openApp(name: String) {
        val q = name.trim().lowercase()
        if (q.isEmpty()) return
        val pm = packageManager
        val apps = launchables()
        val match = apps.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
            ?: apps.firstOrNull {
                val l = it.loadLabel(pm).toString().lowercase()
                l.contains(q) || q.contains(l)
            } ?: return
        val i = pm.getLaunchIntentForPackage(match.activityInfo.packageName) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    override fun onDestroy() {
        recognizer?.destroy()
        tts?.shutdown()
        view?.let { v -> try { wm?.removeView(v) } catch (_: Exception) {} }
        view = null
        super.onDestroy()
    }
}
