package com.luisell.bestkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.os.SystemClock
import android.view.View

/**
 * ☄️ La luz del logo: una línea de luz que va trazando el borde del diamante
 * (como el arco del castillo de Disney). Cuando termina la vuelta, todo el borde brilla,
 * sale un destello en la punta de arriba y se apaga despacio. Luego descansa y se repite.
 */
class CometView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val part = Path()
    private var measure: PathMeasure? = null
    private var len = 0f
    private var scale = 1f
    private var cx = 0f
    private var cy = 0f
    private var sweep: Shader? = null
    private val pt = FloatArray(2)
    private val top = FloatArray(2)
    private val start = SystemClock.uptimeMillis() + 700 // espera un poquito al abrir

    // Tiempos (ms)
    private val TRACE = 2200L  // la línea dibuja la vuelta completa
    private val SHINE = 500L   // todo el borde brilla + destello
    private val FADE = 1100L   // se apaga despacio
    private val REST = 3000L   // descanso
    private val CYCLE = TRACE + SHINE + FADE + REST

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        scale = minOf(w, h) / 108f
        val ox = (w - 108f * scale) / 2f
        val oy = (h - 108f * scale) / 2f
        fun x(v: Float) = ox + v * scale
        fun y(v: Float) = oy + v * scale
        // Empieza y termina en la punta de arriba, dando la vuelta por la derecha
        path.reset()
        path.moveTo(x(54f), y(20f))
        path.lineTo(x(70f), y(42f))
        path.lineTo(x(54f), y(63.5f))
        path.lineTo(x(38f), y(42f))
        path.close()
        measure = PathMeasure(path, true)
        len = measure!!.length
        top[0] = x(54f); top[1] = y(20f)
        cx = x(54f); cy = y(42f)
        // Los colores del diamante alrededor: azul → violeta → rosa → azul
        sweep = SweepGradient(cx, cy, intArrayOf(0xFF67E8F9.toInt(), 0xFFA78BFA.toInt(), 0xFFF0ABFC.toInt(), 0xFF67E8F9.toInt()), null).apply {
            setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cy) })
        }
    }

    private fun ease(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - Math.pow((-2.0 * t + 2), 3.0).toFloat() / 2

    override fun onDraw(c: Canvas) {
        try { drawLight(c) } catch (_: Throwable) { postInvalidateDelayed(1000) }
    }

    /** Dibuja el borde iluminado hasta [upTo] con brillo [a] (0..1): un halo ancho suave y una línea fina brillante. */
    private fun stroke(c: Canvas, m: PathMeasure, from: Float, upTo: Float, a: Float) {
        if (upTo - from < 0.5f || a <= 0.01f) return
        part.reset()
        m.getSegment(from, upTo, part, true)
        line.shader = sweep
        // Halo (3 capas, de ancha y tenue a fina y fuerte)
        val widths = floatArrayOf(7f, 4.2f, 2.4f)
        val alphas = floatArrayOf(0.10f, 0.22f, 0.5f)
        for (i in widths.indices) {
            line.strokeWidth = widths[i] * d
            line.alpha = (255 * alphas[i] * a).toInt().coerceIn(0, 255)
            c.drawPath(part, line)
        }
        // Centro blanco
        line.shader = null
        line.color = 0xFFFFFFFF.toInt()
        line.alpha = (255 * 0.85f * a).toInt().coerceIn(0, 255)
        line.strokeWidth = 1.1f * d
        c.drawPath(part, line)
    }

    private fun drawLight(c: Canvas) {
        val m = measure ?: return
        if (len <= 0f || scale <= 0f) return
        val now = SystemClock.uptimeMillis()
        if (now < start) { postInvalidateDelayed(start - now); return }
        val t = (now - start) % CYCLE
        when {
            t < TRACE -> {
                // ✍️ La línea va trazando el borde; lo ya trazado se queda encendido (un poco más suave)
                val head = ease(t / TRACE.toFloat()) * len
                stroke(c, m, 0f, head, 0.55f)
                // La punta de la línea, más brillante
                stroke(c, m, maxOf(0f, head - len * 0.12f), head, 1f)
                m.getPosTan(head, pt, null)
                val r = maxOf(1f, 6f * d)
                glow.shader = RadialGradient(pt[0], pt[1], r, intArrayOf(0xEEFFFFFF.toInt(), 0x5567E8F9, 0x00000000),
                    floatArrayOf(0f, 0.4f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(pt[0], pt[1], r, glow)
                postInvalidateOnAnimation()
            }
            t < TRACE + SHINE + FADE -> {
                // ✨ Todo el borde brilla, destello en la punta y luego se apaga
                val k = t - TRACE
                val a = if (k < SHINE) 0.55f + 0.45f * (k / SHINE.toFloat()) else 1f - (k - SHINE) / FADE.toFloat()
                stroke(c, m, 0f, len, a)
                val f = k / (SHINE + FADE).toFloat()
                val grow = if (f < 0.25f) f / 0.25f else 1f
                val fade = if (f < 0.25f) 1f else 1f - (f - 0.25f) / 0.75f
                val big = maxOf(1f, (9f * d + 5f * scale) * grow)
                glow.shader = RadialGradient(top[0], top[1], big, intArrayOf(((fade * 255).toInt() shl 24) or 0xFFFFFF,
                    ((fade * 130).toInt() shl 24) or 0x67E8F9, 0x00000000), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
                c.drawCircle(top[0], top[1], big, glow)
                line.shader = null
                line.color = 0xFFFFFFFF.toInt()
                line.alpha = (fade * 255).toInt().coerceIn(0, 255)
                line.strokeWidth = 1.3f * d
                val arm = big * 1.5f
                c.drawLine(top[0] - arm, top[1], top[0] + arm, top[1], line)
                c.drawLine(top[0], top[1] - arm, top[0], top[1] + arm, line)
                postInvalidateOnAnimation()
            }
            else -> postInvalidateDelayed(CYCLE - t)
        }
    }
}
