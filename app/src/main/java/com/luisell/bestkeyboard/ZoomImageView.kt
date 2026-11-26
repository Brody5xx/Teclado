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

    fun setImage(b: Bitmap?) {
        bmp = b
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
    }
}
