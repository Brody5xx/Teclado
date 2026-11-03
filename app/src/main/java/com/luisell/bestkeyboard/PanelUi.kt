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

    /** true cuando el teclado tiene tu foto o video de fondo (los botones se vuelven de vidrio). */
    @JvmStatic var glass = false

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)
    private fun lighter(c: Int, f: Float): Int {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return (0xFF shl 24) or ((r + (255 - r) * f).toInt() shl 16) or ((g + (255 - g) * f).toInt() shl 8) or (b + (255 - b) * f).toInt()
    }

    /**
     * Botón bonito como las teclas: un brillo suave arriba, borde fino del color de acento
     * y, al tocarlo, se ilumina con el acento. Con fondo propio es de vidrio (se ve tu foto o video).
     */
    fun glassButton(ctx: Context, p: Palette, radiusPx: Float): StateListDrawable {
        val a = if (glass) 0x66 else 0xFF
        val top = withAlpha(lighter(p.key, if (p.isDark) 0.10f else 0.0f), a)
        val bottom = withAlpha(p.key, if (glass) 0x4D else 0xFF)
        val line = dp(ctx, 1f).coerceAtLeast(1)
        val normal = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = radiusPx
            setStroke(line, withAlpha(p.accent, if (glass) 0x88 else 0x55))
        }
        val pressed = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(p.accent, 0x66), withAlpha(p.accent, 0x33))).apply {
            cornerRadius = radiusPx
            setStroke(dp(ctx, 1.5f).coerceAtLeast(1), p.accent)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

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

    /**
     * Barra de scroll fina y moderna: un destello de luz blanca que aparece al deslizar y se apaga sola.
     * No se puede agarrar (así no estorba al tocar los emojis de la orilla).
     */
    /** El color de la luz de las barras de scroll (el acento del tema del teclado). */
    var glowAccent: Int = 0xFF22D3EE.toInt()

    fun glowScrollbar(v: View) {
        v.isVerticalScrollBarEnabled = true
        v.isScrollbarFadingEnabled = true
        v.scrollBarFadeDuration = 400
        v.scrollBarDefaultDelayBeforeFade = 500
        v.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            // Ancha para que quepa el brillo; la línea que se ve es fina y corta
            v.scrollBarSize = dp(v.context, 14f)
            v.verticalScrollbarThumbDrawable = GlowThumb(v.resources.displayMetrics.density)
            v.verticalScrollbarTrackDrawable = null
        } else v.scrollBarSize = dp(v.context, 3f)
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

/** Botón que repite su acción mientras lo mantienes presionado (flechas, borrar). */
@SuppressLint("ClickableViewAccessibility")
fun makeRepeating(view: View, feedback: () -> Unit, action: () -> Unit) {
    val h = Handler(Looper.getMainLooper())
    var count = 0
    val repeat = object : Runnable {
        override fun run() {
            action()
            count++
            h.postDelayed(this, if (count > 10) 35L else 70L)
        }
    }
    view.setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.isPressed = true
                feedback()
                action()
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
