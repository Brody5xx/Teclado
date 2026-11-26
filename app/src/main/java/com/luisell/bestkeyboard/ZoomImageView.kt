package com.luisell.bestkeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * 🔍 Una foto que se agranda: pellizca con dos dedos para acercar o alejar, arrastra para moverla
 * y toca dos veces para hacer zoom ahí (o volver al tamaño normal). Todo con animación suave.
 */
class ZoomImageView(context: Context) : View(context) {
    private var bmp: Bitmap? = null
    private val m = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var base = 1f   // la escala para que la foto quepa entera
    private var zoom = 1f   // cuánto la agrandaste (1 = normal)
    private var tx = 0f
    private var ty = 0f
    private var anim: ValueAnimator? = null

    /** Se avisa cuando cambia el zoom (para mostrar «2.5x»). */
    var onZoom: ((Float) -> Unit)? = null

    /** 📝 Los renglones de texto de la foto (en coordenadas de la foto): se marcan y se pueden tocar. */
    class Box(val r: android.graphics.RectF, val text: String)
    var boxes: List<Box> = emptyList()
        set(v) { field = v; flashIdx = -1; invalidate() }
    /** Tocaste un renglón. */
    var onBoxTap: ((Box) -> Unit)? = null
    var accent = 0xFF4FD1FF.toInt()
    private var flashIdx = -1
    private var flashT = 0f
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boxLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tmpR = android.graphics.RectF()

    /** Abre la misma foto otra vez: se queda donde la dejaste (zoom y posición). */
    fun sameImage(b: Bitmap?) = b != null && b === bmp

    fun setImage(b: Bitmap?) {
        bmp = b
        boxes = emptyList()
        zoom = 1f
        fit()
        // Entra creciendo un poquito
        if (b != null) {
            scaleX = 0.92f; scaleY = 0.92f; alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(240L).setInterpolator(DecelerateInterpolator()).start()
        }
        invalidate()
    }

    private fun fit() {
        val b = bmp ?: return
        if (width == 0 || height == 0) return
        base = minOf(width / b.width.toFloat(), height / b.height.toFloat())
        tx = (width - b.width * base * zoom) / 2f
        ty = (height - b.height * base * zoom) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { super.onSizeChanged(w, h, ow, oh); fit() }

    /** Que la foto no se salga de la pantalla (y quede centrada si es más chica). */
    private fun clamp() {
        val b = bmp ?: return
        val iw = b.width * base * zoom
        val ih = b.height * base * zoom
        tx = if (iw <= width) (width - iw) / 2f else tx.coerceIn(width - iw, 0f)
        ty = if (ih <= height) (height - ih) / 2f else ty.coerceIn(height - ih, 0f)
    }

    private fun zoomAround(newZoom: Float, fx: Float, fy: Float) {
        val z = newZoom.coerceIn(1f, 8f)
        val k = z / zoom
        tx = fx - (fx - tx) * k
        ty = fy - (fy - ty) * k
        zoom = z
        clamp()
        onZoom?.invoke(zoom)
        invalidate()
    }

    private fun animateTo(target: Float, fx: Float, fy: Float) {
        anim?.cancel()
        val from = zoom
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener { zoomAround(from + (target - from) * (it.animatedValue as Float), fx, fy) }
            start()
        }
    }

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { zoomAround(zoom * d.scaleFactor, d.focusX, d.focusY); return true }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { anim?.cancel(); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (zoom <= 1.01f) return false
            tx -= dx; ty -= dy; clamp(); invalidate(); return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            // ¿Tocaste un renglón? (de la pantalla a la foto)
            val sc = base * zoom
            val x = (e.x - tx) / sc; val y = (e.y - ty) / sc
            val pad = 6f * resources.displayMetrics.density / sc
            val i = boxes.indexOfFirst { b -> x >= b.r.left - pad && x <= b.r.right + pad && y >= b.r.top - pad && y <= b.r.bottom + pad }
            if (i < 0) return false
            flashIdx = i
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 600L
                addUpdateListener { flashT = it.animatedValue as Float; invalidate() }
                start()
            }
            onBoxTap?.invoke(boxes[i])
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            animateTo(if (zoom > 1.5f) 1f else 2.8f, e.x, e.y); return true
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        // Mientras está agrandada, el teclado no se lleva el dedo
        if (zoom > 1.01f || e.pointerCount > 1) parent?.requestDisallowInterceptTouchEvent(true)
        scaler.onTouchEvent(e)
        if (!scaler.isInProgress) gestures.onTouchEvent(e)
        return true
    }

    override fun onDraw(c: Canvas) {
        val b = bmp ?: return
        m.reset()
        m.postScale(base * zoom, base * zoom)
        m.postTranslate(tx, ty)
        c.drawBitmap(b, m, paint)
        if (boxes.isEmpty()) return
        // Cada renglón con su marquito de luz (el que tocaste brilla)
        val sc = base * zoom
        val rad = 4f * resources.displayMetrics.density
        boxLine.strokeWidth = 1.2f * resources.displayMetrics.density
        for ((i, bx) in boxes.withIndex()) {
            tmpR.set(bx.r.left * sc + tx - 3, bx.r.top * sc + ty - 2, bx.r.right * sc + tx + 3, bx.r.bottom * sc + ty + 2)
            val on = i == flashIdx && flashT > 0f
            boxPaint.color = (accent and 0x00FFFFFF) or ((if (on) 0x30 + (0x70 * flashT).toInt() else 0x26) shl 24)
            boxLine.color = (accent and 0x00FFFFFF) or ((if (on) 0xFF else 0x8C) shl 24)
            c.drawRoundRect(tmpR, rad, rad, boxPaint)
            c.drawRoundRect(tmpR, rad, rad, boxLine)
        }
    }
}
