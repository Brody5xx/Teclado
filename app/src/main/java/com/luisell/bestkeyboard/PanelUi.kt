package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

/** Acciones que los paneles (emojis, portapapeles) le piden al teclado. */
interface PanelListener {
    fun onPanelText(text: String)
    fun onPanelDelete()
    fun onPanelBack()
    fun onPanelFeedback()
}

/** Herramientas pequeñas para dibujar los paneles con los colores del tema. */
object PanelUi {
    fun dp(ctx: Context, v: Float) = (v * ctx.resources.displayMetrics.density).toInt()

    fun rounded(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radiusPx }

    /** Fondo que cambia de color al tocar. */
    fun pressable(normal: Int, pressed: Int, radiusPx: Float): StateListDrawable =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed, radiusPx))
            addState(intArrayOf(), rounded(normal, radiusPx))
        }

    fun barButton(ctx: Context, label: String, p: Palette, special: Boolean = true): TextView =
        TextView(ctx).apply {
            text = label
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(p.text)
            setTypeface(typeface, Typeface.BOLD)
            val r = dp(ctx, 7f).toFloat()
            background = pressable(if (special) p.special else p.key, p.keyPressed, r)
        }

    /** Tecla de borrar que repite mientras la mantienes presionada. */
    @SuppressLint("ClickableViewAccessibility")
    fun makeRepeatingDelete(view: View, listener: () -> PanelListener?) {
        val h = Handler(Looper.getMainLooper())
        var count = 0
        val repeat = object : Runnable {
            override fun run() {
                listener()?.onPanelDelete()
                count++
                h.postDelayed(this, if (count > 12) 30L else 60L)
            }
        }
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    listener()?.onPanelFeedback()
                    listener()?.onPanelDelete()
                    count = 0
                    h.postDelayed(repeat, 400L)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    h.removeCallbacks(repeat)
                }
            }
            true
        }
    }
}

/**
 * Contenedor del teclado: las teclas mandan la altura, y los paneles (emojis, portapapeles)
 * usan EXACTAMENTE esa misma altura, con scroll por dentro.
 */
class PanelBox(context: Context, private val keys: View) : android.widget.FrameLayout(context) {
    /** Panel que necesita más altura (el escáner): mientras se ve, el teclado crece. */
    var tallPanel: View? = null

    init {
        addView(keys, LayoutParams(-1, -2))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        keys.measure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        var h = keys.measuredHeight
        val tall = tallPanel
        if (tall != null && tall.visibility == VISIBLE) {
            h = maxOf(h, (resources.displayMetrics.heightPixels * 0.58f).toInt())
        }
        val exactW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val exactH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c !== keys) c.measure(exactW, exactH)
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.layout(0, 0, c.measuredWidth, c.measuredHeight)
        }
    }
}

/**
 * Capa que se dibuja encima de un panel (menú o tonos de piel), dentro del mismo teclado.
 * Tocar fuera la cierra.
 */
@SuppressLint("ViewConstructor")
class PanelOverlay(context: Context, scrim: Int) : android.widget.FrameLayout(context) {
    init {
        setBackgroundColor(scrim)
        isClickable = true
        visibility = GONE
        setOnClickListener { hide() }
    }

    fun show(content: View, lp: LayoutParams) {
        removeAllViews()
        // Que los toques sobre el contenido no cierren la capa
        content.isClickable = true
        addView(content, lp)
        visibility = VISIBLE
        bringToFront()
    }

    fun hide() {
        removeAllViews()
        visibility = GONE
    }

    val isShowing get() = visibility == VISIBLE
}
