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
