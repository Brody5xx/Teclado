package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Panel "Frases" dentro del teclado: tocas una y se escribe completa. El botón ES/EN cambia el idioma
 * de las frases (por ejemplo, para escribir el reporte en inglés aunque el teclado esté en español).
 */
@SuppressLint("ViewConstructor")
class PhrasePanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onPhrase(text: String)
        fun onPhraseBack()
        fun onPhraseEdit()
        fun onPhraseFeedback()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val back = IconView(context, Icons.BACK, 8f)
    private val title = TextView(context)
    private val langBtn = TextView(context)
    private val editBtn = IconView(context, Icons.PENCIL, 8f)
    private val list = LinearLayout(context)
    private var english = false
    private val rows = ArrayList<TextView>()

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(8f), d(4f), d(8f), d(6f))
        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        back.setOnClickListener { actions?.onPhraseFeedback(); actions?.onPhraseBack() }
        head.addView(back, LinearLayout.LayoutParams(d(44f), d(40f)))
        L.bind(title, "Frases", "Phrases")
        title.textSize = 18f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(12f), 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        langBtn.textSize = 14f
        langBtn.gravity = Gravity.CENTER
        langBtn.setPadding(d(14f), 0, d(14f), 0)
        langBtn.setOnClickListener { actions?.onPhraseFeedback(); english = !english; rebuild() }
        head.addView(langBtn, LinearLayout.LayoutParams(-2, d(36f)).apply { rightMargin = d(6f) })
        editBtn.setOnClickListener { actions?.onPhraseFeedback(); actions?.onPhraseEdit() }
        head.addView(editBtn, LinearLayout.LayoutParams(d(52f), d(36f)))
        body.addView(head, LinearLayout.LayoutParams(-1, d(46f)))

        list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(context)
        sv.addView(list)
        body.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(body, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    /** Se abre en el idioma del teclado. */
    fun open() {
        english = L.english
        rebuild()
    }

    private fun rebuild() {
        langBtn.text = if (english) "EN ⇄" else "ES ⇄"
        list.removeAllViews()
        rows.clear()
        val items = Phrases.all(Prefs.get(context), english)
        if (items.isEmpty()) {
            val t = TextView(context)
            t.text = L.t("No hay frases. Toca ✎ para agregar las tuyas.", "No phrases. Tap ✎ to add yours.")
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setPadding(d(16f), d(24f), d(16f), d(24f))
            t.setTextColor(palette.hint)
            list.addView(t)
            return
        }
        for (p in items) {
            val t = TextView(context)
            t.text = p
            t.textSize = 14.5f
            t.maxLines = 3
            t.setPadding(d(16f), d(11f), d(16f), d(11f))
            t.setOnClickListener { actions?.onPhraseFeedback(); actions?.onPhrase(p) }
            rows.add(t)
            list.addView(t, LinearLayout.LayoutParams(-1, -2).apply { setMargins(d(4f), d(4f), d(4f), d(4f)) })
        }
        paintRows()
    }

    private fun paintRows() {
        for (t in rows) {
            t.setTextColor(palette.text)
            t.background = PanelUi.pressable(palette.key, palette.keyPressed, d(18f).toFloat())
        }
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        back.color = p.text
        back.background = PanelUi.pressable(p.special, p.keyPressed, d(20f).toFloat())
        editBtn.color = p.text
        editBtn.background = PanelUi.pressable(p.special, p.keyPressed, d(18f).toFloat())
        langBtn.setTextColor(p.onAccent)
        langBtn.background = PanelUi.pressable(p.accent, p.accentPressed, d(18f).toFloat())
        paintRows()
    }
}
