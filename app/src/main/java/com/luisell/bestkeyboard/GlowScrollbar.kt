package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Barra de scroll propia: un destello de luz blanca, fino y suave, con un brillo difuminado alrededor.
 * Aparece al deslizar, se apaga sola despacito y NO recibe toques (así no estorba a los emojis).
 * Se pone encima de la lista y se le avisa con [show].
 */
@SuppressLint("ViewConstructor")
class GlowScrollbar(context: Context) : View(context) {

    private val dens = resources.displayMetrics.density
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var pos = 0f      // 0 = arriba, 1 = abajo
    private var size = 0.1f   // qué parte de la lista se ve
    private var level = 0f    // brillo actual (0 apagado, 1 encendido)
    private var target = 0f
    private var lastFrame = 0L
    private val fadeOut = Runnable { target = 0f; tick() }

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onTouchEvent(event: android.view.MotionEvent?) = false

    /** [fraction] = dónde vas (0..1); [visible] = qué parte de todo se ve en pantalla (0..1). */
    fun show(fraction: Float, visible: Float) {
        if (visible >= 0.999f) return
        pos = fraction.coerceIn(0f, 1f)
        size = visible.coerceIn(0.05f, 1f)
        target = 1f
        removeCallbacks(fadeOut)
        postDelayed(fadeOut, 700)
        tick()
    }

    /** Animación suave: el brillo sube rápido y baja lento. */
    private fun tick() {
        lastFrame = 0L
        postInvalidateOnAnimation()
    }

    override fun onDraw(c: Canvas) {
        val now = android.os.SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16f else (now - lastFrame).coerceIn(1, 64).toFloat()
        lastFrame = now
        val speed = if (target > level) dt / 120f else dt / 420f
        level = if (target > level) minOf(target, level + speed) else maxOf(target, level - speed)
        if (level > 0.001f) {
            val h = height.toFloat()
            val pad = 6f * dens
            val len = maxOf(36f * dens, (h - 2 * pad) * size).coerceAtMost(h * 0.5f)
            val top = pad + (h - 2 * pad - len) * pos
            val cx = width - 5f * dens
            val a = (level * 255).toInt()

            // Brillo difuminado alrededor (como luz)
            val hw = 7f * dens
            halo.shader = RadialGradient(cx, top + len / 2f, maxOf(hw, len / 2f + hw),
                intArrayOf(0x55FFFFFF, 0x18FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            halo.alpha = a
            r.set(cx - hw, top - hw, cx + hw, top + len + hw)
            c.drawOval(r, halo)

            // Línea fina: transparente en las puntas y blanca en el centro
            core.shader = LinearGradient(0f, top, 0f, top + len,
                intArrayOf(0x00FFFFFF, 0xE6FFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xE6FFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f), Shader.TileMode.CLAMP)
            core.alpha = a
            val w = 1.4f * dens
            r.set(cx - w, top, cx + w, top + len)
            c.drawRoundRect(r, w, w, core)

            // Chispa en el centro
            spark.shader = RadialGradient(cx, top + len / 2f, 4f * dens,
                intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            spark.alpha = a
            c.drawCircle(cx, top + len / 2f, 4f * dens, spark)
        }
        if (level != target) postInvalidateOnAnimation()
    }
}

/** Cuadrícula de emojis que dice cuánto se deslizó (para la barra de luz). */
class GlowGrid(context: Context) : android.widget.GridView(context) {
    /** (dónde vas 0..1, qué parte se ve 0..1) */
    fun scrollInfo(): Pair<Float, Float> {
        val range = computeVerticalScrollRange().toFloat()
        val extent = computeVerticalScrollExtent().toFloat()
        val offset = computeVerticalScrollOffset().toFloat()
        if (range <= 0f || range <= extent) return Pair(0f, 1f)
        return Pair(offset / (range - extent), extent / range)
    }
}
