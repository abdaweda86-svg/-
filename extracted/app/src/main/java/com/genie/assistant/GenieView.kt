package com.genie.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.sin

class GenieView(ctx: Context) : View(ctx) {

    companion object {
        const val IDLE = 0
        const val LISTENING = 1
        const val THINKING = 2
        const val SPEAKING = 3
    }

    var state = IDLE
        set(v) { field = v; invalidate() }

    /** 0 = مارد، 1 = ذكر، 2 = بنت */
    var persona = 0
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 6.2831855f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    private fun fill(color: Long) {
        p.style = Paint.Style.FILL
        p.color = color.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val bob = sin(t) * h * 0.025f

        // حالة الاستماع: حلقة خضراء نابضة
        if (state == LISTENING) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * 0.03f
            p.color = 0xFF4CD964.toInt()
            canvas.drawCircle(cx, h * 0.5f, w * 0.42f + sin(t * 3) * w * 0.03f, p)
        }

        canvas.save()
        canvas.translate(0f, bob)
        val headY = h * 0.32f
        val r = w * 0.17f

        when (persona) {
            0 -> { // مارد
                fill(0xFF3FA9F5)
                val tail = Path().apply {
                    moveTo(cx - w * 0.2f, h * 0.6f)
                    quadTo(cx - w * 0.15f, h * 0.82f, cx + w * 0.12f, h * 0.95f)
                    quadTo(cx + w * 0.05f, h * 0.8f, cx + w * 0.2f, h * 0.6f)
                    close()
                }
                canvas.drawPath(tail, p)
                canvas.drawOval(RectF(cx - w * 0.2f, h * 0.42f, cx + w * 0.2f, h * 0.68f), p)
                canvas.drawOval(RectF(cx - w * 0.31f, h * 0.48f, cx - w * 0.16f, h * 0.62f), p)
                canvas.drawOval(RectF(cx + w * 0.16f, h * 0.48f, cx + w * 0.31f, h * 0.62f), p)
                fill(0xFF5BB8F8)
                canvas.drawCircle(cx, headY, r, p)
                fill(0xFF7E57C2)
                canvas.drawOval(RectF(cx - w * 0.17f, h * 0.1f, cx + w * 0.17f, h * 0.24f), p)
                fill(0xFFFFC107)
                canvas.drawCircle(cx, h * 0.17f, w * 0.03f, p)
                fill(0xFF2E86C1)
                canvas.drawOval(RectF(cx - w * 0.035f, headY + r * 0.65f, cx + w * 0.035f, headY + r * 1.1f), p)
            }
            1 -> { // ذكر
                fill(0xFF43A047)
                canvas.drawRoundRect(RectF(cx - w * 0.2f, h * 0.48f, cx + w * 0.2f, h * 0.78f), w * 0.06f, w * 0.06f, p)
                fill(0xFF37474F)
                canvas.drawRect(RectF(cx - w * 0.15f, h * 0.78f, cx - w * 0.02f, h * 0.95f), p)
                canvas.drawRect(RectF(cx + w * 0.02f, h * 0.78f, cx + w * 0.15f, h * 0.95f), p)
                fill(0xFFFFCC99)
                canvas.drawCircle(cx, headY, r, p)
                fill(0xFF3E2723)
                canvas.drawArc(RectF(cx - r, headY - r * 1.05f, cx + r, headY + r * 0.95f), 190f, 160f, true, p)
            }
            else -> { // بنت
                fill(0xFF6D4C41)
                canvas.drawOval(RectF(cx - w * 0.21f, h * 0.12f, cx + w * 0.21f, h * 0.62f), p)
                fill(0xFFEC407A)
                val dress = Path().apply {
                    moveTo(cx - w * 0.1f, h * 0.48f)
                    lineTo(cx + w * 0.1f, h * 0.48f)
                    lineTo(cx + w * 0.25f, h * 0.88f)
                    lineTo(cx - w * 0.25f, h * 0.88f)
                    close()
                }
                canvas.drawPath(dress, p)
                fill(0xFFFFCC99)
                canvas.drawRect(RectF(cx - w * 0.09f, h * 0.88f, cx - w * 0.03f, h * 0.96f), p)
                canvas.drawRect(RectF(cx + w * 0.03f, h * 0.88f, cx + w * 0.09f, h * 0.96f), p)
                canvas.drawCircle(cx, headY, r, p)
                fill(0xFF6D4C41)
                canvas.drawArc(RectF(cx - r, headY - r * 1.05f, cx + r, headY + r * 0.95f), 190f, 160f, true, p)
                fill(0xFFFF80AB)
                canvas.drawCircle(cx + w * 0.13f, h * 0.15f, w * 0.045f, p)
                canvas.drawCircle(cx + w * 0.2f, h * 0.15f, w * 0.045f, p)
            }
        }

        // الوجه
        fill(0xFF222222)
        canvas.drawCircle(cx - w * 0.06f, headY - h * 0.01f, w * 0.018f, p)
        canvas.drawCircle(cx + w * 0.06f, headY - h * 0.01f, w * 0.018f, p)
        if (state == SPEAKING) {
            val open = abs(sin(t * 6)) * h * 0.04f + h * 0.01f
            fill(0xFF7B1F1F)
            canvas.drawOval(RectF(cx - w * 0.04f, headY + h * 0.04f, cx + w * 0.04f, headY + h * 0.04f + open), p)
        } else {
            p.style = Paint.Style.STROKE
            p.strokeWidth = w * 0.015f
            p.color = 0xFF222222.toInt()
            canvas.drawArc(RectF(cx - w * 0.05f, headY + h * 0.01f, cx + w * 0.05f, headY + h * 0.07f), 20f, 140f, false, p)
        }
        canvas.restore()

        // حالة التفكير: ثلاث نقاط تقفز
        if (state == THINKING) {
            fill(0xFFFFC107)
            for (i in 0..2) {
                val dy = sin(t * 3 + i * 1.2f) * h * 0.03f
                canvas.drawCircle(cx + (i - 1) * w * 0.1f, h * 0.05f + dy, w * 0.025f, p)
            }
        }
    }
}
