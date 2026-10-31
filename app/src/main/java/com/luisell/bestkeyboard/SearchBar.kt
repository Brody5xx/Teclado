package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Barra de búsqueda arriba del teclado (para emojis y GIFs), como Gboard:
 * escribes con las teclas normales y los resultados salen aquí mismo.
 */
@SuppressLint("ViewConstructor")
class SearchBar(context: Context) : LinearLayout(context) {

    interface Actions {
        fun onSearchText(text: String)
        fun onSearchClose()
        fun onSearchPick(emoji: String)
        fun onSearchFeedback()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val back = IconView(context, Icons.BACK, 8f)
    private val glass = IconView(context, Icons.SEARCH, 7f)
    val box = CursorBox(context)
    private val clear = IconView(context, Icons.CLOSE, 6f)
    private val results = LinearLayout(context)
    private val resultsScroll = HorizontalScrollView(context)
    private val hint = TextView(context)
    private val field = LinearLayout(context)
    private var silent = false
    /** true = GIFs (resultados al tocar Buscar); false = emojis (mientras escribes). */
    var gifs = false
        private set

    val inputInfo = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT
        imeOptions = EditorInfo.IME_ACTION_SEARCH
    }
    val connection: InputConnection

    init {
        orientation = VERTICAL
        setPadding(d(6f), d(6f), d(6f), d(2f))

        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        back.setOnClickListener { actions?.onSearchFeedback(); actions?.onSearchClose() }
        row.addView(back, LayoutParams(d(44f), d(40f)))

        field.orientation = HORIZONTAL
        field.gravity = Gravity.CENTER_VERTICAL
        field.setPadding(d(10f), 0, d(4f), 0)
        field.addView(glass, LayoutParams(d(28f), d(28f)))
        box.textSize = 16f
        box.isSingleLine = true
        box.inputType = inputInfo.inputType
        box.showSoftInputOnFocus = false
        box.isFocusable = false
        box.background = null
        box.setPadding(d(6f), d(6f), d(6f), d(6f))
        field.addView(box, LayoutParams(0, -2, 1f))
        clear.setOnClickListener { actions?.onSearchFeedback(); setText("") }
        field.addView(clear, LayoutParams(d(36f), d(36f)))
        row.addView(field, LayoutParams(0, d(42f), 1f).apply { leftMargin = d(6f) })
        addView(row, LayoutParams(-1, -2))

        results.orientation = HORIZONTAL
        resultsScroll.isHorizontalScrollBarEnabled = false
        resultsScroll.addView(results)
        addView(resultsScroll, LayoutParams(-1, d(48f)).apply { topMargin = d(4f) })
        hint.textSize = 13f
        hint.gravity = Gravity.CENTER_VERTICAL
        hint.setPadding(d(14f), 0, d(14f), 0)
        addView(hint, LayoutParams(-1, d(36f)))

        connection = box.onCreateInputConnection(inputInfo)
            ?: android.view.inputmethod.BaseInputConnection(box, true)
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clear.visibility = if (s.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
                if (!silent) actions?.onSearchText(s?.toString() ?: "")
            }
        })
        clear.visibility = View.INVISIBLE
        applyPalette(palette)
    }

    /** Abre la búsqueda de emojis ([gif] = false) o de GIFs. */
    fun start(gif: Boolean) {
        gifs = gif
        if (gif) L.bindHint(box, "Buscar GIFs", "Search GIFs") else L.bindHint(box, "Buscar emojis", "Search emojis")
        setText("")
        box.text?.let { Selection.setSelection(it, 0) }
        box.resetBlink()
        showResults(emptyList())
    }

    fun text(): String = box.text?.toString() ?: ""

    fun setText(t: String) {
        silent = true
        box.setText(t)
        box.text?.let { Selection.setSelection(it, it.length) }
        silent = false
        actions?.onSearchText(t)
    }

    fun deleteBack() = box.deleteBack()
    fun moveCursor(steps: Int) = box.moveCursor(steps)

    /** Emojis encontrados (en GIFs: un aviso para tocar Buscar). */
    fun showResults(list: List<String>) {
        results.removeAllViews()
        if (gifs) {
            resultsScroll.visibility = GONE
            hint.visibility = VISIBLE
            hint.text = L.t("Escribe y toca 🔍 (la tecla de Enter) para ver los GIFs.", "Type and tap 🔍 (the Enter key) to see GIFs.")
            return
        }
        val empty = list.isEmpty()
        resultsScroll.visibility = if (empty) GONE else VISIBLE
        hint.visibility = if (empty) VISIBLE else GONE
        hint.text = if (text().isBlank()) L.t("Escribe: casa, feliz, perro, pizza, corazón…", "Type: house, happy, dog, pizza, heart…")
        else L.t("No encontré emojis con esa palabra.", "No emojis for that word.")
        for (e in list) {
            val t = TextView(context)
            t.text = e
            t.textSize = 26f
            t.gravity = Gravity.CENTER
            t.background = PanelUi.pressable(0, palette.keyPressed, d(10f).toFloat())
            t.setOnClickListener { actions?.onSearchFeedback(); actions?.onSearchPick(e) }
            results.addView(t, LayoutParams(d(46f), d(46f)))
        }
        resultsScroll.scrollTo(0, 0)
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        back.color = p.text
        back.background = PanelUi.pressable(p.special, p.keyPressed, d(20f).toFloat())
        glass.color = p.hint
        clear.color = p.hint
        box.setTextColor(p.text)
        box.cursorColor = p.accent
        box.setHintTextColor(p.hint)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.setColor(p.key)
        bg.cornerRadius = d(21f).toFloat()
        bg.setStroke(d(1.5f), p.accent)
        field.background = bg
        hint.setTextColor(p.hint)
    }
}
