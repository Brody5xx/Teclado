package com.luisell.bestkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.view.View

/**
 * ☄️ El destello del logo: un cometa de luz que recorre el borde del diamante
 * (como el arco de luz del castillo de Disney) y termina con un brillo en la punta de arriba.
 * Se dibuja encima del logo (mismo tamaño que la imagen, viewport de 108).
 */
class CometView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val seg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var measure: PathMeasure? = null
    private var len = 0f
    private var scale = 1f
    private val start = SystemClock.uptimeMillis() + 700 // espera un poquito al abrir

    // Tiempos de cada vuelta (ms)
    private val RUN = 2600L   // el cometa da vuelta y media
    private val BURST = 900L  // el destello en la punta
    private val REST = 3200L  // descanso antes de repetir
    private val CYCLE = RUN + BURST + REST

    private val pt = FloatArray(2)
    private val sp = Path()

    init { setLayerType(LAYER_TYPE_HARDWARE, null) }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        scale = minOf(w, h) / 108f
        val ox = (w - 108f * scale) / 2f
        val oy = (h - 108f * scale) / 2f
        fun x(v: Float) = ox + v * scale
        fun y(v: Float) = oy + v * scale
        // Empieza abajo, sube por la izquierda, pasa la punta, baja por la derecha… y vuelve a la punta
        path.reset()
        path.moveTo(x(54f), y(63.5f))
        path.lineTo(x(38f), y(42f))
        path.lineTo(x(54f), y(20f))
        path.lineTo(x(70f), y(42f))
        path.lineTo(x(54f), y(63.5f))
        path.lineTo(x(38f), y(42f))
        path.lineTo(x(54f), y(20f))
        measure = PathMeasure(path, false)
        len = measure!!.length
    }

    private fun ease(t: Float): Float = if (t < 0.5f) 4 * t * t * t else 1 - Math.pow((-2.0 * t + 2), 3.0).toFloat() / 2

    /** Color del cometa según dónde va: azul → violeta → rosa (como el diamante). */
    private fun hue(f: Float): Int {
        val a = intArrayOf(0x67E8F9, 0xA78BFA, 0xF0ABFC, 0x67E8F9)
        val p = (f.coerceIn(0f, 1f) * 3f)
        val i = p.toInt().coerceAtMost(2)
        val k = p - i
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        val c1 = a[i]; val c2 = a[i + 1]
        val r = (ch(c1, 16) + (ch(c2, 16) - ch(c1, 16)) * k).toInt()
        val g = (ch(c1, 8) + (ch(c2, 8) - ch(c1, 8)) * k).toInt()
        val b = (ch(c1, 0) + (ch(c2, 0) - ch(c1, 0)) * k).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    override fun onDraw(c: Canvas) {
        val m = measure ?: return
        val now = SystemClock.uptimeMillis()
        if (now < start) { postInvalidateDelayed(start - now); return }
        val t = (now - start) % CYCLE
        if (t < RUN) {
            val f = ease(t / RUN.toFloat())
            val head = f * len
            // La cola: se alarga al principio y se recoge al final
            val tailLen = len * 0.42f * minOf(1f, f * 4f) * minOf(1f, (1f - f) * 6f + 0.25f)
            val n = 46
            for (i in 0 until n) {
                val a0 = head - tailLen * i / n
                val a1 = head - tailLen * (i + 1) / n
                if (a1 < 0f) break
                val k = 1f - i / n.toFloat()
                seg.strokeWidth = (0.6f + 2.8f * k * k) * scale * 0.55f + 0.4f * d
                val col = hue(a0 / len)
                seg.color = ((k * k * 235).toInt() shl 24) or (if (i < 3) 0xFFFFFF else col)
                sp.reset()
                m.getSegment(a1, a0, sp, true)
                c.drawPath(sp, seg)
            }
            // La cabeza: una bolita de luz blanca con brillo de color
            m.getPosTan(head, pt, null)
            val r = 7f * scale * 0.5f + 4f * d
            glow.shader = RadialGradient(pt[0], pt[1], r, intArrayOf(0xFFFFFFFF.toInt(), (0xAA shl 24) or hue(head / len), 0x00000000),
                floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(pt[0], pt[1], r, glow)
            postInvalidateOnAnimation()
        } else if (t < RUN + BURST) {
            // ✨ El destello en la punta de arriba (la estrellita de 4 puntas que crece y se apaga)
            val f = (t - RUN) / BURST.toFloat()
            m.getPosTan(len, pt, null)
            val grow = if (f < 0.3f) f / 0.3f else 1f
            val fade = if (f < 0.3f) 1f else 1f - (f - 0.3f) / 0.7f
            val big = (14f * scale * 0.5f + 10f * d) * grow
            glow.shader = RadialGradient(pt[0], pt[1], big, intArrayOf(((fade * 255).toInt() shl 24) or 0xFFFFFF,
                ((fade * 140).toInt() shl 24) or 0x67E8F9, 0x00000000), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(pt[0], pt[1], big, glow)
            seg.color = ((fade * 255).toInt() shl 24) or 0xFFFFFF
            seg.strokeWidth = 1.4f * d
            val arm = big * 1.6f
            c.drawLine(pt[0] - arm, pt[1], pt[0] + arm, pt[1], seg)
            c.drawLine(pt[0], pt[1] - arm, pt[0], pt[1] + arm, seg)
            val arm2 = arm * 0.45f
            c.save(); c.rotate(45f, pt[0], pt[1])
            c.drawLine(pt[0] - arm2, pt[1], pt[0] + arm2, pt[1], seg)
            c.drawLine(pt[0], pt[1] - arm2, pt[0], pt[1] + arm2, seg)
            c.restore()
            postInvalidateOnAnimation()
        } else {
            postInvalidateDelayed(CYCLE - t)
        }
    }
}
