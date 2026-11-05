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
        val f = fill(p)
        val b = border(p)
        val a = if (glass) glassA else 0xFF
        val top = withAlpha(lighter(f, if (p.isDark) 0.10f else 0.0f), a)
        val bottom = withAlpha(f, if (glass) (glassA * 0.75f).toInt() else 0xFF)
        val line = (density * lineMul).toInt()
        val normal = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = radiusPx
            if (line > 0) setStroke(line, withAlpha(b, if (glass) 0x88 else 0x66))
        }
        val pressed = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(b, 0x66), withAlpha(b, 0x33))).apply {
            cornerRadius = radiusPx
            setStroke(maxOf(1, (density * 1.5f).toInt()), b)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    /** Los colores del tema que está usando el teclado (para que todo lo gris se vea bonito). */
    @JvmStatic var pal: Palette? = null
    @JvmStatic var density = 2.75f

    // ✨ Tu estilo de paneles y botones (Estilo → Paneles y botones). null = como el tema.
    private var borderC: Int? = null
    private var fillC: Int? = null
    private var iconC: Int? = null
    /** Qué tan sólidos son con tu fondo propio (0..255). */
    private var glassA = 0x66
    /** Grosor del borde (0 = sin borde). */
    private var lineMul = 1f

    /** Lee tus colores de paneles. Se llama cada vez que el teclado aplica el tema. */
    fun loadStyle(prefs: android.content.SharedPreferences) {
        fun c(k: String) = prefs.getString(k, "")?.takeIf { it.isNotEmpty() }?.toLongOrNull(16)?.toInt()
        borderC = c(Prefs.PANEL_BORDER)
        fillC = c(Prefs.PANEL_FILL)
        iconC = c(Prefs.PANEL_ICON)
        glassA = when (prefs.getString(Prefs.PANEL_GLASS, "normal")) { "clear" -> 0x30; "solid" -> 0xC8; else -> 0x66 }
        lineMul = when (prefs.getString(Prefs.PANEL_LINE, "thin")) { "none" -> 0f; "thick" -> 2.2f; else -> 1f }
    }

    /** El color del borde y la luz. */
    fun border(p: Palette) = borderC ?: p.accent
    /** El color de los botones (en vez del gris de las teclas). */
    fun fill(p: Palette) = fillC ?: p.key
    /** El color de los íconos de los paneles. */
    fun icon(p: Palette) = iconC ?: borderC ?: p.accent
    /** Transparencia del vidrio (con fondo propio). */
    fun glassAlpha() = glassA
    fun lineWidth(base: Float) = base * lineMul

    /**
     * Todo lo que antes era gris liso (tarjetas y botones con el color de las teclas) ahora se ve
     * como las teclas: brillo suave arriba, borde fino del color del tema y, con fondo propio, de vidrio.
     * Al tocar, se ilumina con el color del tema.
     */
    fun rounded(color: Int, radiusPx: Float): GradientDrawable {
        val p = pal
        if (p != null && (color == p.key || color == p.special)) {
            val strong = color == p.special
            val base = fillC ?: color
            val a = if (glass) (if (strong) minOf(255, glassA + 0x11) else glassA) else 0xFF
            val top = withAlpha(lighter(base, if (p.isDark) 0.09f else 0f), a)
            val bottom = withAlpha(base, if (glass) (glassA * 0.75f).toInt() else 0xFF)
            val line = (density * lineMul).toInt()
            return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
                cornerRadius = radiusPx
                if (line > 0) setStroke(line, withAlpha(border(p), if (glass) 0x88 else 0x55))
            }
        }
        if (p != null && color == p.keyPressed) {
            val b = border(p)
            return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(withAlpha(b, 0x66), withAlpha(b, 0x33))).apply {
                cornerRadius = radiusPx
                setStroke(maxOf(1, (density * 1.5f).toInt()), b)
            }
        }
        if (p != null && color == p.accent) {
            // Botones principales: el color del tema con un brillo arriba
            return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(lighter(p.accent, 0.22f), p.accent)).apply { cornerRadius = radiusPx }
        }
        return GradientDrawable().apply { setColor(color); cornerRadius = radiusPx }
    }

    /** 🌈 Fondo con borde de colores (azul → violeta → rosa) y relleno de vidrio: para la barra de búsqueda. */
    fun rainbowField(ctx: Context, p: Palette, radiusPx: Float): android.graphics.drawable.Drawable {
        val line = maxOf(1, (density * 1.6f).toInt())
        val edge = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, LightFx.COLORS).apply { cornerRadius = radiusPx }
        val f = fill(p)
        val a = if (glass) glassA + 0x22 else 0xFF
        val inner = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(withAlpha(lighter(f, if (p.isDark) 0.12f else 0f), minOf(255, a)), withAlpha(f, minOf(255, a)))).apply { cornerRadius = maxOf(0f, radiusPx - line) }
        // El relleno tapa el centro del borde de colores (con fondo de vidrio, se mezcla un poquito con el color)
        val solid = GradientDrawable().apply { setColor(p.bg or 0xFF000000.toInt()); cornerRadius = maxOf(0f, radiusPx - line) }
        return android.graphics.drawable.LayerDrawable(arrayOf(edge, solid, inner)).apply {
            setLayerInset(1, line, line, line, line)
            setLayerInset(2, line, line, line, line)
        }
    }

    /** Para tarjetas hechas a mano: les pone el borde fino del color del tema. */
    fun decorate(g: GradientDrawable) {
        val p = pal ?: return
        val line = (density * lineMul).toInt()
        if (line > 0) g.setStroke(line, withAlpha(border(p), if (glass) 0x88 else 0x55))
    }

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
