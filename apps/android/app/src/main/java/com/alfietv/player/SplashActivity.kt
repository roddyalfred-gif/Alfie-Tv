package com.alfietv.player

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import kotlin.math.cos
import kotlin.math.sin

/** Branded, resolution-independent Alfie TV boot experience. */
class SplashActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var splashView: AlfieSplashView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)
        if (!SettingsStore.splashEnabled(this)) {
            openNext()
            return
        }
        splashView = AlfieSplashView(this, SettingsStore.splashStyle(this))
        setContentView(splashView)
        val duration = 10_000L
        handler.postDelayed({ openNext() }, duration)
    }

    private fun openNext() {
        if (isFinishing) return
        val target = if (SessionStore.load(this) != null) HomeActivity::class.java else LoginActivity::class.java
        startActivity(Intent(this, target).apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) })
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        splashView?.stop()
        super.onDestroy()
    }
}

private class AlfieSplashView(context: android.content.Context, private val style: String) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = DecelerateInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
        start()
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        contentDescription = "Alfie TV animated boot logo"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        canvas.drawColor(Color.rgb(4, 7, 18))

        val scale = when (style) {
            "cinematic" -> 0.82f + 0.18f * phase
            else -> 0.92f + 0.08f * sin(phase * Math.PI * 2).toFloat()
        }
        canvas.save()
        canvas.scale(scale, scale, cx, cy)

        val radius = minOf(w, h) * 0.11f
        val pulse = 0.65f + 0.35f * sin(phase * Math.PI * 2).toFloat().coerceIn(-1f, 1f)
        glow.color = Color.rgb(76, 72, 255)
        glow.style = Paint.Style.STROKE
        glow.strokeWidth = radius * 0.12f
        glow.setShadowLayer(radius * (1.2f + pulse), 0f, 0f, Color.rgb(0, 210, 255))
        canvas.drawCircle(cx, cy - radius * 0.45f, radius, glow)

        paint.color = Color.WHITE
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = radius * 0.62f
        paint.setShadowLayer(radius * 0.45f, 0f, 0f, Color.rgb(120, 80, 255))
        canvas.drawText("▶", cx, cy - radius * 0.12f, paint)

        paint.clearShadowLayer()
        paint.textSize = radius * 0.58f
        paint.letterSpacing = 0.08f
        canvas.drawText("ALFIE TV", cx, cy + radius * 1.75f, paint)

        if (style == "orbit") {
            val orbitR = radius * 1.55f
            val angle = phase * Math.PI * 2
            paint.color = Color.rgb(0, 210, 255)
            canvas.drawCircle((cx + cos(angle) * orbitR).toFloat(), (cy - radius * 0.45f + sin(angle) * orbitR).toFloat(), radius * 0.10f, paint)
        } else if (style == "cinematic") {
            paint.color = Color.argb((55 * (1f - phase)).toInt(), 120, 80, 255)
            canvas.drawCircle(cx, cy - radius * 0.45f, radius * (1.3f + phase * 1.8f), paint)
        }
        canvas.restore()
    }

    fun stop() {
        animator.cancel()
    }
}
