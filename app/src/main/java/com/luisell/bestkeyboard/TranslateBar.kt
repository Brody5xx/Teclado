package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Barra de traducción (como Gboard): arriba del teclado escribes en tu idioma
 * y la traducción va apareciendo en la app donde estás escribiendo.
 */
@SuppressLint("ViewConstructor")
class TranslateBar(context: Context) : LinearLayout(context) {

    interface Actions {
        fun onTranslateClose()
        fun onTranslateSwap()
        fun onTranslateSource(text: String)
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val close = TextView(context)
    private val from = TextView(context)
    private val swap = TextView(context)
    private val to = TextView(context)
    val box = EditText(context)
    private val status = TextView(context)
    private var silent = false

    /** El teclado escribe aquí (no en la app) mientras traduces. */
    val inputInfo = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        imeOptions = EditorInfo.IME_ACTION_SEND
    }
    val connection: InputConnection

    init {
        orientation = VERTICAL
        setPadding(d(6f), d(6f), d(6f), d(2f))

        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        close.text = "←"
        close.textSize = 20f
        close.gravity = Gravity.CENTER
        close.setOnClickListener { actions?.onTranslateClose() }
        row.addView(close, LayoutParams(d(44f), d(38f)))
        for (t in listOf(from, to)) {
            t.textSize = 15f
            t.gravity = Gravity.CENTER
            t.setOnClickListener { actions?.onTranslateSwap() }
        }
        swap.text = "⇄"
        swap.textSize = 20f
        swap.gravity = Gravity.CENTER
        swap.setOnClickListener { actions?.onTranslateSwap() }
        row.addView(from, LayoutParams(0, d(36f), 1f).apply { leftMargin = d(8f) })
        row.addView(swap, LayoutParams(d(48f), d(36f)))
        row.addView(to, LayoutParams(0, d(36f), 1f).apply { rightMargin = d(8f) })
        addView(row, LayoutParams(-1, -2))

        box.hint = "Escribe aquí para traducir"
        box.textSize = 16f
        box.isSingleLine = false
        box.maxLines = 3
        box.inputType = inputInfo.inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        box.showSoftInputOnFocus = false
        box.isFocusable = false
        box.setPadding(d(14f), d(8f), d(14f), d(8f))
        addView(box, LayoutParams(-1, -2).apply { topMargin = d(6f) })

        status.textSize = 12f
        status.setPadding(d(14f), d(2f), d(14f), d(2f))
        addView(status, LayoutParams(-1, -2))

        connection = box.onCreateInputConnection(inputInfo)
            ?: android.view.inputmethod.BaseInputConnection(box, true)
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!silent) actions?.onTranslateSource(s?.toString() ?: "")
            }
        })
        applyPalette(palette)
    }

    fun setLangs(src: String, dst: String) {
        from.text = if (src == "es") "Español" else "Inglés"
        to.text = if (dst == "es") "Español" else "Inglés"
    }

    fun setStatus(text: String) {
        status.text = text
        status.visibility = if (text.isEmpty()) GONE else VISIBLE
    }

    /** Borrar hacia atrás dentro de la caja (una letra, o un emoji completo). */
    fun deleteBack() {
        val e = box.text ?: return
        var start = Selection.getSelectionStart(e)
        var end = Selection.getSelectionEnd(e)
        if (start < 0 || end < 0) { start = e.length; end = e.length }
        if (start > end) { val t = start; start = end; end = t }
        if (start != end) {
            e.delete(start, end)
            return
        }
        if (start == 0) return
        val bi = android.icu.text.BreakIterator.getCharacterInstance()
        bi.setText(e.toString())
        val prev = bi.preceding(start)
        e.delete(if (prev == android.icu.text.BreakIterator.DONE) start - 1 else prev, start)
    }

    /** Mover el cursor dentro de la caja (deslizar sobre la barra espaciadora). */
    fun moveCursor(steps: Int) {
        val e = box.text ?: return
        var pos = Selection.getSelectionEnd(e)
        if (pos < 0) pos = e.length
        Selection.setSelection(e, (pos + steps).coerceIn(0, e.length))
    }

    /** Vacía la caja sin traducir. */
    fun clear() {
        silent = true
        box.text?.clear()
        silent = false
        box.text?.let { Selection.setSelection(it, 0) }
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        val r = d(18f).toFloat()
        close.setTextColor(p.text)
        close.background = PanelUi.pressable(p.special, p.keyPressed, r)
        swap.setTextColor(p.text)
        for (t in listOf(from, to)) {
            t.setTextColor(p.onAccent)
            t.background = PanelUi.pressable(p.accent, p.accentPressed, r)
        }
        box.setTextColor(p.text)
        box.setHintTextColor(p.hint)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.setColor(p.key)
        bg.cornerRadius = d(20f).toFloat()
        bg.setStroke(d(1.5f), p.accent)
        box.background = bg
        status.setTextColor(p.hint)
    }
}
