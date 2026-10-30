package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Barra de "Revisar ortografía": te lleva palabra por palabra por los errores del texto.
 * Cada error se marca en la app y aquí eliges la corrección, lo saltas o lo agregas a tus palabras.
 */
@SuppressLint("ViewConstructor")
class SpellBar(context: Context) : LinearLayout(context) {

    interface Actions {
        fun onSpellPick(word: String)
        fun onSpellSkip()
        fun onSpellLearn()
        fun onSpellClose()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val close = TextView(context)
    private val title = TextView(context)
    private val skip = TextView(context)
    private val learn = TextView(context)
    private val chips = LinearLayout(context)
    private val scroll = HorizontalScrollView(context)

    init {
        orientation = VERTICAL
        setPadding(d(6f), d(6f), d(6f), d(4f))

        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        close.text = "✕"
        close.textSize = 17f
        close.gravity = Gravity.CENTER
        close.setOnClickListener { actions?.onSpellClose() }
        row.addView(close, LayoutParams(d(40f), d(36f)))
        title.textSize = 15f
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        title.setPadding(d(8f), 0, d(8f), 0)
        row.addView(title, LayoutParams(0, -2, 1f))
        learn.text = "+ Mi palabra"
        learn.textSize = 13f
        learn.gravity = Gravity.CENTER
        learn.setPadding(d(10f), 0, d(10f), 0)
        learn.setOnClickListener { actions?.onSpellLearn() }
        row.addView(learn, LayoutParams(-2, d(34f)).apply { rightMargin = d(6f) })
        skip.text = "Saltar ›"
        skip.textSize = 13f
        skip.gravity = Gravity.CENTER
        skip.setPadding(d(12f), 0, d(12f), 0)
        skip.setOnClickListener { actions?.onSpellSkip() }
        row.addView(skip, LayoutParams(-2, d(34f)))
        addView(row, LayoutParams(-1, -2))

        chips.orientation = HORIZONTAL
        chips.setPadding(0, d(6f), 0, d(2f))
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(chips)
        addView(scroll, LayoutParams(-1, -2))
        applyPalette(palette)
    }

    /** Muestra el error número [n] de [total], con sus correcciones. */
    fun show(n: Int, total: Int, word: String, suggestions: List<String>, maybe: Boolean) {
        title.text = if (maybe) "$n de $total · ¿Quisiste decir…? «$word»" else "$n de $total · Mal escrita: «$word»"
        chips.removeAllViews()
        if (suggestions.isEmpty()) {
            val t = TextView(context)
            t.text = "Sin sugerencias: corrígela a mano, sáltala o agrégala"
            t.textSize = 13f
            t.setTextColor(palette.hint)
            t.setPadding(d(8f), d(8f), d(8f), d(8f))
            chips.addView(t)
        }
        for ((i, s) in suggestions.withIndex()) {
            val c = TextView(context)
            c.text = s
            c.textSize = 16f
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
            c.setOnClickListener { actions?.onSpellPick(s) }
            chips.addView(c, LayoutParams(-2, -2).apply { rightMargin = d(8f) })
        }
        scroll.scrollTo(0, 0)
    }

    /** Mensaje final (por ejemplo "Listo, sin errores"). */
    fun message(text: String) {
        title.text = text
        chips.removeAllViews()
        skip.visibility = GONE
        learn.visibility = GONE
    }

    fun resetButtons() {
        skip.visibility = VISIBLE
        learn.visibility = VISIBLE
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        val r = d(17f).toFloat()
        close.setTextColor(p.text)
        close.background = PanelUi.pressable(p.special, p.keyPressed, r)
        skip.setTextColor(p.text)
        skip.background = PanelUi.pressable(p.special, p.keyPressed, r)
        learn.setTextColor(p.accent)
        learn.background = PanelUi.pressable(p.key, p.keyPressed, r)
    }
}
