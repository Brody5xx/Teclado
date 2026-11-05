package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
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
        party.visibility = GONE
        Emo.apply(title, heading, PanelUi.icon(palette), 17f) // sin emojis: ícono pequeño
        // Los emojis al principio de cada renglón (los nuestros) se quitan; lo demás queda igual
        body.text = if (keepEmojis) text else text.lines().joinToString("\n") { ln -> Emo.split(ln).second.let { r -> if (r.isEmpty()) ln else r } }
        // Si está esperando (sin botones y el texto termina en «…»), sale el diamante con su luz
        loader.visibility = if (buttons.isEmpty() && text.trim().endsWith("…")) VISIBLE else GONE
        if (loader.visibility == VISIBLE) loader.invalidate()
        scroll.visibility = if (text.isEmpty()) GONE else VISIBLE
        scroll.scrollTo(0, 0)
        chips.removeAllViews()
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
