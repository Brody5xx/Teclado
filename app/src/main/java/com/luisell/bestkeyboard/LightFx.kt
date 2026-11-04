package com.luisell.bestkeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View

/**
 * ✨ La luz del logo, para usarla en cualquier parte: una línea de luz que traza un borde,
 * el brillo de todo el borde y el destello de estrella. Colores del diamante: azul → violeta → rosa.
 */
object LightFx {
    val COLORS = intArrayOf(0xFF67E8F9.toInt(), 0xFFA78BFA.toInt(), 0xFFF0ABFC.toInt())

    fun ease(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - Math.pow((-2.0 * t + 2), 3.0).toFloat() / 2

    private val part = Path()

    /** Dibuja el tramo [from]..[to] del borde con halo de color y centro blanco; [a] = brillo 0..1. */
    fun glow(c: Canvas, m: PathMeasure, from: Float, to: Float, a: Float, d: Float, shader: Shader?, line: Paint, scale: Float = 1f) {
        if (to - from < 0.5f || a <= 0.01f) return
        part.reset()
        m.getSegment(from, to, part, true)
        line.style = Paint.Style.STROKE
        line.strokeCap = Paint.Cap.ROUND
        line.strokeJoin = Paint.Join.ROUND
        line.shader = shader
        if (shader == null) line.color = COLORS[0]
        val widths = floatArrayOf(6f, 3.6f, 2.2f)
        val alphas = floatArrayOf(0.12f, 0.25f, 0.55f)
        for (i in widths.indices) {
            line.strokeWidth = widths[i] * d * scale
            line.alpha = (255 * alphas[i] * a).toInt().coerceIn(0, 255)
            c.drawPath(part, line)
        }
        line.shader = null
        line.color = 0xFFFFFFFF.toInt()
        line.alpha = (240 * a).toInt().coerceIn(0, 255)
        line.strokeWidth = 1.1f * d * scale
        c.drawPath(part, line)
    }

    /** La bolita brillante en la punta de la línea. */
    fun head(c: Canvas, x: Float, y: Float, r: Float, fill: Paint) {
        val rr = maxOf(1f, r)
        fill.shader = RadialGradient(x, y, rr, intArrayOf(0xEEFFFFFF.toInt(), 0x5567E8F9, 0x00000000), floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, rr, fill)
        fill.shader = null
    }

    /** ✨ El destello de estrella (crece rápido y se apaga). [f] = 0..1 del destello. */
    fun star(c: Canvas, x: Float, y: Float, size: Float, f: Float, fill: Paint, line: Paint, d: Float, maxUp: Float = Float.MAX_VALUE, bold: Boolean = false) {
        val grow = if (f < 0.25f) f / 0.25f else 1f
        val fade = (if (f < 0.25f) 1f else 1f - (f - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val big = maxOf(1f, size * grow)
        fill.shader = RadialGradient(x, y, big, intArrayOf(((fade * 255).toInt() shl 24) or 0xFFFFFF,
            ((fade * 130).toInt() shl 24) or 0x67E8F9, 0x00000000), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, big, fill)
        fill.shader = null
        line.shader = null
        line.color = 0xFFFFFFFF.toInt()
        line.alpha = (fade * 255).toInt().coerceIn(0, 255)
        line.strokeWidth = 1.1f * d
        val arm = big * 1.45f
        val up = minOf(arm, maxUp)
        // Rayos largos a los lados (como un destello de lente) y cortos arriba/abajo
        if (bold) {
            // ⭐ Estrella gorda: cuatro puntas rellenas (como un diamante de luz) con halo de color
            val sp = Path()
            val wx = arm * 1.6f; val wy = arm * 1.25f; val th = big * 0.22f
            sp.moveTo(x, y - wy); sp.quadTo(x + th * 0.35f, y - th * 0.35f, x + wx, y)
            sp.quadTo(x + th * 0.35f, y + th * 0.35f, x, y + wy)
            sp.quadTo(x - th * 0.35f, y + th * 0.35f, x - wx, y)
            sp.quadTo(x - th * 0.35f, y - th * 0.35f, x, y - wy); sp.close()
            fill.shader = RadialGradient(x, y, maxOf(1f, wx), intArrayOf(((fade * 255).toInt() shl 24) or 0xFFFFFF,
                ((fade * 230).toInt() shl 24) or 0xE0F7FF, ((fade * 120).toInt() shl 24) or 0xA78BFA), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(sp, fill)
            fill.shader = null
            line.strokeWidth = 2.4f * d
            line.alpha = (fade * 200).toInt().coerceIn(0, 255)
            c.drawLine(x - wx * 1.35f, y, x + wx * 1.35f, y, line)
        } else {
            line.strokeWidth = 1.6f * d
            c.drawLine(x - arm * 1.5f, y, x + arm * 1.5f, y, line)
            c.drawLine(x, y - up, x, y + up, line)
        }
        line.strokeWidth = if (bold) 1.6f * d else 0.9f * d
        line.alpha = (fade * 150).toInt().coerceIn(0, 255)
        val dg = minOf(arm * 0.5f, maxUp)
        c.drawLine(x - dg, y - dg, x + dg, y + dg, line)
        c.drawLine(x - dg, y + dg, x + dg, y - dg, line)
        // Centro blanco fuerte
        fill.color = 0xFFFFFFFF.toInt()
        fill.alpha = (fade * 255).toInt().coerceIn(0, 255)
        c.drawCircle(x, y, maxOf(1f, big * (if (bold) 0.26f else 0.16f)), fill)
        fill.alpha = 255
    }

    /** ✅ Hace que la luz dé la vuelta al borde de [v] y destelle (para confirmar algo). */
    fun flash(v: View, radiusPx: Float) {
        try {
            if (v.width <= 0 || v.height <= 0) { v.post { flash(v, radiusPx) }; return }
            val dr = TraceFlash(radiusPx, v.resources.displayMetrics.density)
            dr.setBounds(0, 0, v.width, v.height)
            v.overlay.add(dr)
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = TraceFlash.TOTAL
                addUpdateListener { dr.t = it.animatedValue as Float; dr.invalidateSelf(); v.invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) { try { v.overlay.remove(dr) } catch (_: Exception) { } }
                })
                start()
            }
        } catch (_: Throwable) { }
    }
}

/** La luz que da una vuelta a un rectángulo redondeado y destella (se pone encima de un botón). */
class TraceFlash(private val radius: Float, private val d: Float) : Drawable() {
    companion object { const val TOTAL = 1100L }
    var t = 0f
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val m = PathMeasure()
    private val pt = FloatArray(2)
    private val r = RectF()

    override fun draw(c: Canvas) {
        try {
            val b = bounds
            r.set(b.left + 1.5f * d, b.top + 1.5f * d, b.right - 1.5f * d, b.bottom - 1.5f * d)
            path.reset()
            path.addRoundRect(r, radius, radius, Path.Direction.CW)
            m.setPath(path, true)
            val len = m.length
            if (len <= 0f) return
            val sh = LinearGradient(r.left, r.top, r.right, r.bottom, LightFx.COLORS, null, Shader.TileMode.CLAMP)
            val traceEnd = 0.6f
            if (t < traceEnd) {
                val head = LightFx.ease(t / traceEnd) * len
                LightFx.glow(c, m, 0f, head, 0.45f, d, sh, line, 0.8f)
                LightFx.glow(c, m, maxOf(0f, head - len * 0.12f), head, 1f, d, sh, line, 0.8f)
                m.getPosTan(head, pt, null)
                LightFx.head(c, pt[0], pt[1], 4f * d, fill)
            } else {
                val k = (t - traceEnd) / (1f - traceEnd)
                LightFx.glow(c, m, 0f, len, 1f - k, d, sh, line, 0.8f)
                m.getPosTan(0f, pt, null)
                LightFx.star(c, pt[0], pt[1], 7f * d, k, fill, line, d)
            }
        } catch (_: Throwable) { }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/**
 * 💎 El diamante del logo, chiquito, con su luz. [loader] = true: la luz da vueltas sin parar
 * (para «espera, estoy trabajando»). Si no, pasa la luz de vez en cuando.
 */
class DiamondView(context: Context, private val loader: Boolean = false) : View(context) {
    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outer = Path()
    private val inner = Path()
    private val m = PathMeasure()
    private val pt = FloatArray(2)
    private var shader: Shader? = null
    private var tipX = 0f
    private var tipY = 0f
    private var start = SystemClock.uptimeMillis() + (if (loader) 0L else 900L + (Math.random() * 1500).toLong())

    /** Que la luz empiece ya (para una celebración). */
    fun restart(delay: Long = 150L) { start = SystemClock.uptimeMillis() + delay; invalidate() }
    private val TRACE = if (loader) 1000L else 1400L
    private val SHINE = if (loader) 450L else 900L
    private val CYCLE = if (loader) TRACE + SHINE else 8000L

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        val s = minOf(w, h) / 2f * 0.82f
        val cx = w / 2f
        val cy = h / 2f + s * 0.05f
        tipX = cx; tipY = cy - s * 1.05f
        outer.reset()
        outer.moveTo(cx, cy - s * 1.05f); outer.lineTo(cx + s * 0.72f, cy - s * 0.05f)
        outer.lineTo(cx, cy + s * 1.0f); outer.lineTo(cx - s * 0.72f, cy - s * 0.05f); outer.close()
        inner.reset()
        inner.moveTo(cx, cy - s * 1.05f); inner.lineTo(cx + s * 0.27f, cy - s * 0.05f)
        inner.lineTo(cx, cy + s * 1.0f); inner.lineTo(cx - s * 0.27f, cy - s * 0.05f); inner.close()
        m.setPath(outer, true)
        shader = LinearGradient(cx - s, cy - s, cx + s, cy + s, LightFx.COLORS, null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        try { paint(c) } catch (_: Throwable) { postInvalidateDelayed(1000) }
    }

    private fun paint(c: Canvas) {
        val sh = shader ?: return
        val k = minOf(width, height) / (26f * d) // tamaño relativo (26dp = 1)
        line.shader = sh; line.alpha = 255; line.strokeWidth = maxOf(1.2f * d, 1.7f * d * k)
        c.drawPath(outer, line)
        line.alpha = 180; line.strokeWidth = maxOf(0.8f * d, 0.9f * d * k)
        c.drawPath(inner, line)
        line.shader = null
        fill.shader = null; fill.color = 0xFFFFFFFF.toInt()
        c.drawCircle(tipX, tipY, maxOf(1f, 1.3f * d * k), fill)
        val now = SystemClock.uptimeMillis()
        if (now < start) { postInvalidateDelayed(start - now); return }
        val t = (now - start) % CYCLE
        val len = m.length
        if (len <= 0f) return
        val sc = maxOf(0.55f, minOf(1.4f, k))
        when {
            t < TRACE -> {
                val head = LightFx.ease(t / TRACE.toFloat()) * len
                LightFx.glow(c, m, 0f, head, 0.5f, d, sh, line, sc)
                LightFx.glow(c, m, maxOf(0f, head - len * 0.15f), head, 1f, d, sh, line, sc)
                m.getPosTan(head, pt, null)
                LightFx.head(c, pt[0], pt[1], 3.5f * d * sc, fill)
                postInvalidateOnAnimation()
            }
            t < TRACE + SHINE -> {
                val f = (t - TRACE) / SHINE.toFloat()
                LightFx.glow(c, m, 0f, len, 1f - f, d, sh, line, sc)
                LightFx.star(c, tipX, tipY, 6f * d * sc, f, fill, line, d)
                postInvalidateOnAnimation()
            }
            else -> postInvalidateDelayed(CYCLE - t)
        }
    }
}

/**
 * ✨ El reflejo de vidrio de los botones grandes: una franja de luz blanca que los cruza en diagonal
 * de vez en cuando (cada botón a su propio ritmo).
 */
class Shine(private val radius: Float) : Drawable(), Runnable {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rr = RectF()
    private val offset = (Math.random() * 4000).toLong()
    private val CROSS = 1100L
    private val CYCLE = 6500L

    override fun draw(c: Canvas) {
        try {
            val b = bounds
            if (b.width() <= 0) return
            val now = SystemClock.uptimeMillis() + offset
            val t = now % CYCLE
            if (t < CROSS) {
                val f = LightFx.ease(t / CROSS.toFloat())
                val w = b.width().toFloat()
                val band = b.height() * 1.6f
                val x = b.left - band + (w + band * 2) * f
                rr.set(b)
                clip.reset(); clip.addRoundRect(rr, radius, radius, Path.Direction.CW)
                c.save()
                c.clipPath(clip)
                p.shader = LinearGradient(x - band / 2, b.top.toFloat(), x + band / 2, b.bottom.toFloat(),
                    intArrayOf(0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawRect(rr, p)
                c.restore()
                scheduleSelf(this, SystemClock.uptimeMillis() + 16)
            } else scheduleSelf(this, SystemClock.uptimeMillis() + (CYCLE - t))
        } catch (_: Throwable) { }
    }

    override fun run() { invalidateSelf() }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
