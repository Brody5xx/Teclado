package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Teclado chiquito para buscar dentro de los paneles (emojis y GIFs),
 * así no tienes que salir del panel para escribir lo que buscas.
 */
@SuppressLint("ViewConstructor")
class MiniKeys(context: Context) : LinearLayout(context) {

    interface Actions {
        fun onMiniText(s: String)
        fun onMiniDelete()
        fun onMiniEnter()
        fun onMiniFeedback()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)
    /** (vista, tipo: 0 letra, 1 especial, 2 azul) */
    private val keys = ArrayList<Pair<TextView, Int>>()
    private var del: IconView? = null
    private var enter: TextView? = null

    init {
        orientation = VERTICAL
        setPadding(d(3f), d(2f), d(3f), d(4f))
        for (row in listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")) {
            val r = LinearLayout(context)
            r.orientation = HORIZONTAL
            r.gravity = Gravity.CENTER
            if (row.length < 10) r.addView(android.view.View(context), LayoutParams(0, -1, 0.5f))
            for (ch in row) addKey(r, ch.toString(), 0, 1f) { actions?.onMiniText(ch.toString()) }
            if (row.length < 10) {
                val dl = IconView(context, Icons.DELETE, 8f)
                val pl = object : PanelListener {
                    override fun onPanelText(text: String) {}
                    override fun onPanelDelete() { actions?.onMiniDelete() }
                    override fun onPanelBack() {}
                    override fun onPanelFeedback() { actions?.onMiniFeedback() }
                }
                PanelUi.makeRepeatingDelete(dl) { pl }
                del = dl
                r.addView(dl, LayoutParams(0, -1, 2.5f).apply { setMargins(d(2f), d(3f), d(2f), d(3f)) })
            }
            addView(r, LayoutParams(-1, 0, 1f))
        }
        val bottom = LinearLayout(context)
        bottom.orientation = HORIZONTAL
        addKey(bottom, ",", 1, 1f) { actions?.onMiniText(",") }
        val space = addKey(bottom, "", 0, 6f) { actions?.onMiniText(" ") }
        L.bind(space, "espacio", "space")
        space.textSize = 13f
        val en = addKey(bottom, "", 2, 2.5f) { actions?.onMiniEnter() }
        L.bind(en, "Buscar", "Search")
        en.textSize = 14f
        en.setTypeface(en.typeface, Typeface.BOLD)
        enter = en
        addView(bottom, LayoutParams(-1, 0, 1f))
        applyPalette(palette)
    }

    private fun addKey(row: LinearLayout, label: String, type: Int, weight: Float, onTap: () -> Unit): TextView {
        val t = TextView(context)
        t.text = label
        t.textSize = 18f
        t.gravity = Gravity.CENTER
        t.setOnClickListener { actions?.onMiniFeedback(); onTap() }
        keys.add(t to type)
        row.addView(t, LayoutParams(0, -1, weight).apply { setMargins(d(2f), d(3f), d(2f), d(3f)) })
        return t
    }

    fun applyPalette(p: Palette) {
        palette = p
        val r = d(7f).toFloat()
        for ((t, type) in keys) {
            when (type) {
                2 -> { t.setTextColor(p.onAccent); t.background = PanelUi.pressable(p.accent, p.accentPressed, r) }
                1 -> { t.setTextColor(p.text); t.background = PanelUi.pressable(p.special, p.keyPressed, r) }
                else -> { t.setTextColor(p.text); t.background = PanelUi.pressable(p.key, p.keyPressed, r) }
            }
        }
        del?.let { it.color = p.text; it.bgColor = p.special; it.background = PanelUi.pressable(p.special, p.keyPressed, r) }
    }
}
