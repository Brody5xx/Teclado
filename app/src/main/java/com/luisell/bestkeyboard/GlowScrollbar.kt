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
            // Línea corta de luz (igual que la de la barra de arriba), que baja según dónde vas
            val h = height.toFloat()
            val len = GlowLine.LEN_DP * dens
            val pad = 8f * dens
            val cy = pad + len / 2f + (h - 2 * pad - len) * pos
            GlowLine.draw(c, width - 6f * dens, cy, len, dens, PanelUi.glowAccent, level)
        }
        if (level != target) postInvalidateOnAnimation()
    }
}

/**
 * La luz de las barras de scroll: una línea fina vertical, blanca en el centro, del color del tema
 * hacia las puntas y transparente en los extremos, con un brillo suave alrededor (como la luz de la barra de arriba).
 */
object GlowLine {
    const val LEN_DP = 30f
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var cacheKey = 0L
    private var coreShader: Shader? = null
    private var haloShader: Shader? = null

    fun draw(c: Canvas, cx: Float, cy: Float, len: Float, dens: Float, accent: Int, level: Float) {
        val key = (accent.toLong() shl 20) xor len.toLong()
        if (key != cacheKey || coreShader == null) {
            cacheKey = key
            val a = accent and 0x00FFFFFF
            coreShader = LinearGradient(0f, -len / 2f, 0f, len / 2f,
                intArrayOf(a, a or 0xD9000000.toInt(), 0xFFFFFFFF.toInt(), a or 0xD9000000.toInt(), a),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), Shader.TileMode.CLAMP)
            haloShader = RadialGradient(0f, 0f, 1f, intArrayOf(a or 0x40000000, a), null, Shader.TileMode.CLAMP)
        }
        val alpha = (level.coerceIn(0f, 1f) * 255).toInt()
        c.save()
        c.translate(cx, cy)
        // Brillo alrededor (óvalo alto y angosto)
        halo.shader = haloShader
        halo.alpha = alpha
        c.save()
        c.scale(7f * dens, len * 0.8f)
        c.drawCircle(0f, 0f, 1f, halo)
        c.restore()
        // La línea
        core.shader = coreShader
        core.alpha = alpha
        val w = 0.9f * dens
        r.set(-w, -len / 2f, w, len / 2f)
        c.drawRoundRect(r, w, w, core)
        c.restore()
    }
}

/** La misma luz para las barras de scroll normales de Android (listas, ajustes…). */
class GlowThumb(private val dens: Float) : android.graphics.drawable.Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        if (b.height() <= 0) return
        val len = minOf(GlowLine.LEN_DP * dens, b.height().toFloat())
        GlowLine.draw(c, b.exactCenterX(), b.exactCenterY(), len, dens, PanelUi.glowAccent, alpha / 255f)
    }
    private var a = 255
    override fun setAlpha(alpha: Int) { a = alpha; invalidateSelf() }
    override fun getAlpha(): Int = a
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
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
