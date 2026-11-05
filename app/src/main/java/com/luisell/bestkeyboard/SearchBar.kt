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
        /** "✓ Guardar" (al escribir o editar algo del portapapeles). */
        fun onSearchSave() {}
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
    private val saveBtn = TextView(context)
    private var fieldRow: LinearLayout? = null
    private var silent = false
    /** true = GIFs (resultados al tocar Buscar); false = emojis (mientras escribes). */
    var gifs = false
        private set
    /** true = apunte para la libreta del día (Enter lo guarda). */
    var note = false
        private set
    /** true = nombre de un material nuevo (Enter lo guarda). */
    var material = false
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
        fieldRow = row
        addView(row, LayoutParams(-1, -2))

        results.orientation = HORIZONTAL
        resultsScroll.isHorizontalScrollBarEnabled = false
        resultsScroll.addView(results)
        addView(resultsScroll, LayoutParams(-1, d(48f)).apply { topMargin = d(4f) })
        hint.textSize = 13f
        hint.gravity = Gravity.CENTER_VERTICAL
        hint.setPadding(d(14f), 0, d(14f), 0)
        val bottom = LinearLayout(context)
        bottom.orientation = HORIZONTAL
        bottom.gravity = Gravity.CENTER_VERTICAL
        bottom.addView(hint, LayoutParams(0, d(36f), 1f))
        saveBtn.textSize = 14f
        saveBtn.gravity = Gravity.CENTER
        saveBtn.setTypeface(saveBtn.typeface, android.graphics.Typeface.BOLD)
        saveBtn.setPadding(d(18f), 0, d(18f), 0)
        saveBtn.setOnClickListener { actions?.onSearchFeedback(); actions?.onSearchSave() }
        saveBtn.visibility = GONE
        bottom.addView(saveBtn, LayoutParams(-2, d(36f)).apply { rightMargin = d(6f) })
        addView(bottom, LayoutParams(-1, -2).apply { topMargin = d(2f); bottomMargin = d(2f) })

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
    /** Escribir o editar algo del portapapeles (Enter lo guarda). */
    var clip = false
        private set

    /** "todo" (pendiente nuevo), "check" (cosa de la checklist) o "convert" (número a convertir). */
    var custom = ""
        private set
    /** Lo que se ve abajo en los modos de arriba (el resultado de la conversión, por ejemplo). */
    private var customInfo = ""

    fun setInfo(text: String) {
        customInfo = text
        hint.visibility = VISIBLE
        hint.text = text
    }

    fun start(gif: Boolean, noteMode: Boolean = false, materialMode: Boolean = false, clipText: String? = null,
              customMode: String = "", customHint: Pair<String, String>? = null, info: String = "") {
        custom = customMode
        customInfo = info
        material = materialMode
        clip = clipText != null
        // Para el portapapeles: una caja grande, con varias líneas, para escribir y leer cómodo
        box.isSingleLine = !clip
        box.inputType = if (clip) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else InputType.TYPE_CLASS_TEXT
        box.maxLines = if (clip) 9 else 1
        box.minLines = if (clip) 6 else 1
        box.gravity = if (clip) Gravity.TOP or Gravity.START else Gravity.CENTER_VERTICAL or Gravity.START
        box.isVerticalScrollBarEnabled = clip
        field.gravity = if (clip) Gravity.TOP else Gravity.CENTER_VERTICAL
        field.setPadding(d(10f), if (clip) d(8f) else 0, d(4f), if (clip) d(8f) else 0)
        field.layoutParams = (field.layoutParams as LayoutParams).apply { height = if (clip) d(200f) else d(42f) }
        fieldRow?.gravity = if (clip) Gravity.TOP else Gravity.CENTER_VERTICAL
        box.layoutParams = (box.layoutParams as LayoutParams).apply { height = if (clip) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT }
        saveBtn.visibility = if (clip) VISIBLE else GONE
        L.bind(saveBtn, "✓ Guardar", "✓ Save")
        gifs = gif
        note = noteMode
        glass.kind = when (customMode) {
            "todo" -> Icons.TODO; "check" -> Icons.TOOLBOX; "convert" -> Icons.RULER
            else -> if (clip) Icons.PENCIL else if (noteMode) Icons.NOTEBOOK else Icons.SEARCH
        }
        when {
            customHint != null -> L.bindHint(box, customHint.first, customHint.second)
            clip -> L.bindHint(box, "Escribe lo que quieres guardar…", "Type what you want to save…")
            materialMode -> L.bindHint(box, "Nombre del material (ej. Cat 6A cable)", "Material name (e.g. Cat 6A cable)")
            noteMode -> L.bindHint(box, "Apunta algo…", "Write a note…")
            gif -> L.bindHint(box, "Buscar GIFs", "Search GIFs")
            else -> L.bindHint(box, "Buscar emojis", "Search emojis")
        }
        setText(clipText ?: "")
        if (clipText == null) box.text?.let { Selection.setSelection(it, 0) }
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
        if (custom.isNotEmpty()) {
            resultsScroll.visibility = GONE
            hint.visibility = VISIBLE
            hint.text = customInfo
            return
        }
        if (clip) {
            resultsScroll.visibility = GONE
            hint.visibility = VISIBLE
            hint.text = L.t("Enter = nueva línea", "Enter = new line")
            return
        }
        if (material) {
            resultsScroll.visibility = GONE
            hint.visibility = VISIBLE
            hint.text = L.t("Escribe el material y toca Enter para agregarlo a tu lista.", "Type the material and tap Enter to add it to your list.")
            return
        }
        if (note) {
            resultsScroll.visibility = GONE
            hint.visibility = VISIBLE
            hint.text = L.t("Escribe tu apunte y toca Enter para guardarlo en la libreta.", "Type your note and tap Enter to save it in the notebook.")
            return
        }
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
        // ✨ Fondo suave en degradado con la línea de luz abajo
        background = android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(p.bg, p.bg2)),
            android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(0x0022D3EE, LightFx.COLORS[0], 0xFFFFFFFF.toInt(), LightFx.COLORS[1], 0x00A78BFA)),
        )).apply { setLayerInset(1, d(30f), 0, d(30f), 0); setLayerGravity(1, Gravity.BOTTOM); setLayerHeight(1, maxOf(1, d(1.2f))) }
        back.color = p.text
        back.background = PanelUi.glassButton(context, p, d(20f).toFloat())
        glass.color = PanelUi.icon(p)
        clear.color = p.hint
        box.setTextColor(p.text)
        box.cursorColor = p.accent
        box.setHintTextColor(p.hint)
        // 🌈 La caja con borde de colores y una luz que le da vueltas
        field.background = PanelUi.rainbowField(context, p, d(21f).toFloat())
        field.foreground = LoopTrace(d(21f).toFloat(), resources.displayMetrics.density)
        hint.setTextColor(p.text and 0x00FFFFFF or 0xB3000000.toInt())
        saveBtn.setTextColor(p.onAccent)
        saveBtn.background = PanelUi.pressable(p.accent, p.accentPressed, d(18f).toFloat())
    }
}
