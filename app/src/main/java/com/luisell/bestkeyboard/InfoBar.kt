package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Barra arriba del teclado que muestra un resultado (por ejemplo, un mensaje traducido) con botones. */
@SuppressLint("ViewConstructor")
class InfoBar(context: Context) : LinearLayout(context) {

    var onClose: (() -> Unit)? = null
    var onFeedback: (() -> Unit)? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val close = IconView(context, Icons.CLOSE, 7f)
    private val title = TextView(context)
    private val body = TextView(context)
    private val scroll = object : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(d(170f), MeasureSpec.AT_MOST))
        }
    }
    private val chips = LinearLayout(context)
    /** 🗂 El menú de un grupo: botones grandes con ícono, de dos en dos. */
    private val menu = LinearLayout(context)
    /** Los dos idiomas que se pueden tocar para cambiarlos (Traducir captura). */
    private val langRow = LinearLayout(context)
    /** 💎 El diamante con la luz dando vueltas mientras espera (traduciendo, la IA pensando…). */
    private val loader = DiamondView(context, loader = true)
    /** 🏁 El diamante grande de las celebraciones (terminaste tu día, llegaste a 40 horas). */
    private val party = DiamondView(context)

    init {
        PanelUi.glowScrollbar(scroll)
        orientation = VERTICAL
        setPadding(d(6f), d(6f), d(6f), d(4f))
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        close.setOnClickListener { onFeedback?.invoke(); onClose?.invoke() }
        row.addView(close, LayoutParams(d(40f), d(36f)))
        title.textSize = 15f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(10f), 0, d(8f), 0)
        row.addView(title, LayoutParams(0, -2, 1f))
        langRow.orientation = HORIZONTAL
        langRow.gravity = Gravity.CENTER_VERTICAL
        langRow.visibility = GONE
        langRow.setPadding(d(8f), 0, 0, 0)
        row.addView(langRow, LayoutParams(0, -2, 1f))
        loader.visibility = GONE
        row.addView(loader, LayoutParams(d(30f), d(30f)).apply { rightMargin = d(8f) })
        addView(row, LayoutParams(-1, -2))
        party.visibility = GONE
        addView(party, LayoutParams(d(84f), d(84f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = d(4f) })
        body.textSize = 15f
        body.setLineSpacing(d(2f).toFloat(), 1f)
        body.setPadding(d(14f), d(10f), d(14f), d(10f))
        body.setTextIsSelectable(false)
        scroll.addView(body, LayoutParams(-1, -2))
        addView(scroll, LayoutParams(-1, -2).apply { topMargin = d(6f) })
        menu.orientation = VERTICAL
        menu.visibility = GONE
        addView(menu, LayoutParams(-1, -2).apply { topMargin = d(6f) })
        chips.orientation = HORIZONTAL
        chips.setPadding(0, d(6f), 0, d(2f))
        val hs = HorizontalScrollView(context)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chips)
        addView(hs, LayoutParams(-1, -2))
        applyPalette(palette)
    }

    /** Muestra [text] con su título y botones ([buttons] = nombre → acción; el primero se pinta azul). */
    /** 🏁 Como [show], pero con el diamante grande y su luz (para celebrar algo). */
    fun celebrate(heading: String, text: String, buttons: List<Pair<String, () -> Unit>>) {
        show(heading, text, buttons)
        party.visibility = VISIBLE
        party.restart(250L)
    }

    fun show(heading: String, text: String, buttons: List<Pair<String, () -> Unit>>) {
        shineAnim?.cancel(); shineAnim = null
        menu.visibility = GONE
        body.animate().cancel(); body.alpha = 1f; body.translationY = 0f
        party.visibility = GONE
        langRow.visibility = GONE
        title.visibility = VISIBLE
        Emo.apply(title, heading, PanelUi.icon(palette), 17f) // sin emojis: ícono pequeño
        // Los emojis al principio de cada renglón (los nuestros) se quitan; lo demás queda igual
        body.text = if (keepEmojis) text else text.lines().joinToString("\n") { ln -> Emo.split(ln).second.let { r -> if (r.isEmpty()) ln else r } }
        // Si está esperando (sin botones y el texto termina en «…»), sale el diamante con su luz
        loader.visibility = if (buttons.size <= 1 && text.trim().endsWith("…")) VISIBLE else GONE // también con «Cancelar»
        if (loader.visibility == VISIBLE) loader.invalidate()
        scroll.visibility = if (text.isEmpty()) GONE else VISIBLE
        scroll.scrollTo(0, 0)
        chips.removeAllViews()
        chips.visibility = VISIBLE
        for ((i, b) in buttons.withIndex()) {
            val c = TextView(context)
            c.textSize = 14f
            c.gravity = Gravity.CENTER
            c.setPadding(d(16f), d(8f), d(16f), d(8f))
            if (i == 0) {
                c.setTypeface(c.typeface, Typeface.BOLD)
                c.setTextColor(palette.onAccent)
                c.background = PanelUi.pressable(palette.accent, palette.accentPressed, d(18f).toFloat())
            } else {
                c.setTextColor(palette.text)
                c.background = PanelUi.pressable(palette.key, palette.keyPressed, d(18f).toFloat())
            }
            Emo.apply(c, b.first, if (i == 0) palette.onAccent else PanelUi.icon(palette), 15f)
            c.setOnClickListener { onFeedback?.invoke(); b.second() }
            chips.addView(c, LayoutParams(-2, -2).apply { rightMargin = d(8f) })
        }
        visibility = VISIBLE
    }

    /**
     * En vez del título, dos idiomas que se tocan para cambiarlos: [Chino ▾] ⇄ [Español ▾].
     * Se llama después de [show].
     */
    fun showLangs(from: String, to: String, onFrom: () -> Unit, onTo: () -> Unit, onSwap: (() -> Unit)?) {
        langRow.removeAllViews()
        fun pill(label: String, act: () -> Unit) = TextView(context).apply {
            text = label + "  ▾"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(d(12f), d(7f), d(10f), d(7f))
            background = PanelUi.pressable(palette.key, palette.keyPressed, d(16f).toFloat())
            setOnClickListener { onFeedback?.invoke(); act() }
        }
        langRow.addView(pill(from, onFrom), LayoutParams(0, -2, 1f))
        val sw = TextView(context).apply {
            text = "⇄"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(if (onSwap != null) palette.text else PanelUi.icon(palette))
            if (onSwap != null) {
                background = PanelUi.pressable(palette.special, palette.keyPressed, d(16f).toFloat())
                setOnClickListener { v -> onFeedback?.invoke(); v.animate().rotationBy(180f).setDuration(260L).withEndAction { onSwap() }.start() }
            }
        }
        langRow.addView(sw, LayoutParams(d(36f), d(32f)).apply { leftMargin = d(6f); rightMargin = d(6f) })
        langRow.addView(pill(to, onTo), LayoutParams(0, -2, 1f))
        title.visibility = GONE
        langRow.visibility = VISIBLE
    }

    /**
     * ✨ El destello de la traducción: el texto aparece suave y un brillo del color de tu tema
     * pasa por encima de las letras, de izquierda a derecha, una sola vez (sin líneas encima de la tarjeta).
     */
    fun shine() {
        shineAnim?.cancel()
        body.alpha = 0f
        body.animate().alpha(1f).setDuration(260L).start()
        val base = palette.text
        // El brillo: el color de tu tema mezclado con blanco
        val glow = blend(palette.accent, android.graphics.Color.WHITE, 0.45f)
        val a = android.animation.ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1250L
        a.startDelay = 120L
        a.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        a.addUpdateListener {
            val w = body.width.toFloat().coerceAtLeast(1f)
            val h = body.height.toFloat().coerceAtLeast(1f)
            val band = w * 0.45f
            val x = -band + (w + band * 2f) * (it.animatedValue as Float)
            body.paint.shader = android.graphics.LinearGradient(x - band, 0f, x + band, h * 0.5f,
                intArrayOf(base, base, glow, base, base), floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), android.graphics.Shader.TileMode.CLAMP)
            body.invalidate()
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: android.animation.Animator) { body.paint.shader = null; body.invalidate() }
            override fun onAnimationCancel(an: android.animation.Animator) { body.paint.shader = null; body.invalidate() }
        })
        shineAnim = a
        a.start()
    }
    private var shineAnim: android.animation.ValueAnimator? = null

    private fun blend(a: Int, b: Int, k: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * k).toInt()
        return android.graphics.Color.rgb(ch(android.graphics.Color.red(a), android.graphics.Color.red(b)),
            ch(android.graphics.Color.green(a), android.graphics.Color.green(b)), ch(android.graphics.Color.blue(a), android.graphics.Color.blue(b)))
    }

    /**
     * 🗂 Muestra las opciones de un grupo como botones grandes (ícono + nombre), de dos en dos.
     * [items] = (ícono, nombre, prendida, acción).
     */
    fun showMenu(heading: String, items: List<MenuItem>) {
        show(heading, "", emptyList())
        chips.visibility = GONE
        menu.removeAllViews()
        var row: LinearLayout? = null
        for ((i, it) in items.withIndex()) {
            if (i % 2 == 0) {
                row = LinearLayout(context).apply { orientation = HORIZONTAL }
                menu.addView(row, LayoutParams(-1, -2).apply { if (i > 0) topMargin = d(8f) })
            }
            val cell = LinearLayout(context)
            cell.orientation = HORIZONTAL
            cell.gravity = Gravity.CENTER_VERTICAL
            cell.setPadding(d(10f), d(10f), d(10f), d(10f))
            cell.background = if (it.on) PanelUi.pressable(palette.accent, palette.accentPressed, d(16f).toFloat())
                else PanelUi.pressable(palette.key, palette.keyPressed, d(16f).toFloat())
            val ic = IconView(context, it.icon, 6.5f)
            ic.color = if (it.on) palette.onAccent else PanelUi.icon(palette)
            cell.addView(ic, LayoutParams(d(28f), d(28f)))
            val tv = TextView(context)
            tv.text = it.label
            tv.textSize = 14f
            tv.maxLines = 2
            tv.setTextColor(if (it.on) palette.onAccent else palette.text)
            tv.setPadding(d(8f), 0, 0, 0)
            cell.addView(tv, LayoutParams(0, -2, 1f))
            cell.setOnClickListener { _ -> onFeedback?.invoke(); it.action() }
            row!!.addView(cell, LayoutParams(0, -1, 1f).apply { if (i % 2 == 1) leftMargin = d(8f) })
        }
        if (items.size % 2 == 1) row?.addView(View(context), LayoutParams(0, 1, 1f).apply { leftMargin = d(8f) })
        menu.visibility = VISIBLE
        // Entran suave, uno detrás del otro
        for (r in 0 until menu.childCount) {
            val v = menu.getChildAt(r)
            v.alpha = 0f; v.translationY = d(8f).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(40L * r).setDuration(220L).start()
        }
    }

    class MenuItem(val icon: Int, val label: String, val on: Boolean, val action: () -> Unit)

    /** Lo que muestran como resultado (traducciones, lo copiado…): eso no se toca, puede tener tus emojis. */
    var keepEmojis = false

    fun applyPalette(p: Palette) {
        palette = p
        background = PanelUi.barBackground(context, p) // 🎨 ya no gris
        title.setTextColor(p.text)
        close.color = p.text
        close.background = PanelUi.pressable(p.special, p.keyPressed, d(17f).toFloat())
        body.setTextColor(p.text)
        body.background = PanelUi.rounded(p.key, d(14f).toFloat())
    }
}
