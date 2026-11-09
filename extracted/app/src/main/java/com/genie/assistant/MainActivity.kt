package com.genie.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private var askedMic = false
    private var askedOverlay = false
    private var askedAcc = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("genie", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("logged_in", false)) showLogin()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("logged_in", false)) proceed()
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun showLogin() {
        val dp = resources.displayMetrics.density
        val pad = (32 * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#1B1F3B"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val title = TextView(this).apply {
            text = "المارد"
            textSize = 36f
            setTextColor(Color.parseColor("#FFC107"))
            gravity = Gravity.CENTER
        }
        val user = EditText(this).apply {
            hint = "اسم المستخدم"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        val pass = EditText(this).apply {
            hint = "كلمة المرور"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
        }
        val btn = Button(this).apply { text = "دخول" }

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (16 * dp).toInt() }

        root.addView(title)
        root.addView(user, lp)
        root.addView(pass, lp)
        root.addView(btn, lp)
        setContentView(root)

        btn.setOnClickListener {
            val u = user.text.toString().trim()
            val p = pass.text.toString()
            if (u.isEmpty() || p.length < 4) {
                Toast.makeText(this, "اكتب اسم المستخدم وكلمة مرور (4 أحرف على الأقل)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val savedUser = prefs.getString("user", null)
            val savedHash = prefs.getString("hash", null)
            if (savedUser == null || savedHash == null) {
                prefs.edit().putString("user", u).putString("hash", hash(p))
                    .putBoolean("logged_in", true).apply()
                Toast.makeText(this, "تم إنشاء حسابك", Toast.LENGTH_SHORT).show()
                proceed()
            } else if (savedUser == u && savedHash == hash(p)) {
                prefs.edit().putBoolean("logged_in", true).apply()
                proceed()
            } else {
                Toast.makeText(this, "اسم المستخدم أو كلمة المرور غلط", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun accEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.contains(packageName)
    }

    private fun proceed() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (askedMic) {
                Toast.makeText(this, "لازم إذن المايك عشان المارد يسمعك", Toast.LENGTH_LONG).show()
                finish(); return
            }
            askedMic = true
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            requestPermissions(perms.toTypedArray(), 1)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            if (askedOverlay) {
                Toast.makeText(this, "فعّل «العرض فوق التطبيقات» وافتح المارد مرة ثانية", Toast.LENGTH_LONG).show()
                finish(); return
            }
            askedOverlay = true
            Toast.makeText(this, "فعّل «العرض فوق التطبيقات» للمارد", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (!accEnabled()) {
            if (askedAcc) {
                Toast.makeText(this, "فعّل خدمة «المارد» في إمكانية الوصول وافتح التطبيق مرة ثانية", Toast.LENGTH_LONG).show()
                finish(); return
            }
            askedAcc = true
            Toast.makeText(this, "فعّل خدمة «المارد» من إمكانية الوصول", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        finish()
    }
}
