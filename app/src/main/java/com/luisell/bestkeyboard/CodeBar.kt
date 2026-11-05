package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Barra del "Modo código" (arriba del teclado): Explicar · Escribir código · Arreglar error · Pista,
 * el lenguaje elegido (Python, JavaScript…) y el lugar donde sale la respuesta de la IA.
 */
@SuppressLint("ViewConstructor")
class CodeBar(context: Context) : LinearLayout(context) {

    interface Actions {
        fun onCodeClose()
        fun onCodeLang()
        fun onCodeExplain()
        fun onCodeWrite()
        fun onCodeFix()
        fun onCodeHint()
        /** Escribiste la descripción y tocaste "Crear" (o Enter). */
        fun onCodeAsk(description: String)
        fun onCodeCancelAsk()
        fun onCodeInsert(text: String)
        fun onCodeCopy(text: String)
        fun onCodeAddKey()
        fun onCodeFeedback()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val icon = IconView(context, Icons.CODE, 8f)
    private val title = TextView(context)
    private val langBtn = TextView(context)
    private val close = IconView(context, Icons.CLOSE, 7f)
    private val actionsRow = LinearLayout(context)
    private val actionsScroll = HorizontalScrollView(context)
    private val pills = ArrayList<TextView>()

    // Caja para describir el código que quieres (el teclado escribe aquí)
    private val askRow = LinearLayout(context)
    val box = CursorBox(context)
    private val askGo = TextView(context)
    private val askCancel = IconView(context, Icons.CLOSE, 6f)
    val inputInfo = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        imeOptions = EditorInfo.IME_ACTION_NONE
    }
    val connection: InputConnection

    // Respuesta de la IA
    private val status = TextView(context)
    private val result = TextView(context)
    private val resultScroll = object : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(d(190f), MeasureSpec.AT_MOST))
        }
    }
    private val chips = LinearLayout(context)
    private val chipsScroll = HorizontalScrollView(context)

    init {
        PanelUi.glowScrollbar(resultScroll)
        orientation = VERTICAL
        setPadding(d(6f), d(6f), d(6f), d(4f))

        // Fila 1: </> Modo código · Python ▾ · ✕
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        icon.isClickable = false
        row.addView(icon, LayoutParams(d(30f), d(30f)).apply { leftMargin = d(4f) })
        L.bind(title, "Modo código", "Code mode")
        title.textSize = 15f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(8f), 0, d(8f), 0)
        row.addView(title, LayoutParams(0, -2, 1f))
        langBtn.textSize = 14f
        langBtn.gravity = Gravity.CENTER
        langBtn.setPadding(d(14f), 0, d(14f), 0)
        langBtn.setOnClickListener { actions?.onCodeFeedback(); actions?.onCodeLang() }
        row.addView(langBtn, LayoutParams(-2, d(32f)).apply { rightMargin = d(6f) })
        close.setOnClickListener { actions?.onCodeFeedback(); actions?.onCodeClose() }
        row.addView(close, LayoutParams(d(38f), d(34f)))
        addView(row, LayoutParams(-1, -2))

        // Fila 2: botones de la IA
        actionsRow.orientation = HORIZONTAL
        fun pill(es: String, en: String, onClick: () -> Unit) {
            val t = TextView(context)
            L.bind(t, es, en)
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setPadding(d(14f), 0, d(14f), 0)
            t.setOnClickListener { actions?.onCodeFeedback(); onClick() }
            pills.add(t)
            actionsRow.addView(t, LayoutParams(-2, d(34f)).apply { rightMargin = d(8f) })
        }
        pill("Explicar", "Explain") { actions?.onCodeExplain() }
        pill("Escribir código", "Write code") { actions?.onCodeWrite() }
        pill("Arreglar error", "Fix error") { actions?.onCodeFix() }
        pill("Pista", "Hint") { actions?.onCodeHint() }
        actionsScroll.isHorizontalScrollBarEnabled = false
        actionsScroll.addView(actionsRow)
        addView(actionsScroll, LayoutParams(-1, -2).apply { topMargin = d(6f) })

        // Caja "¿Qué quieres que haga el código?"
        askRow.orientation = HORIZONTAL
        askRow.gravity = Gravity.CENTER_VERTICAL
        L.bindHint(box, "Describe qué quieres que haga el código…", "Describe what the code should do…")
        box.textSize = 15f
        box.isSingleLine = false
        box.maxLines = 3
        box.inputType = inputInfo.inputType
        box.showSoftInputOnFocus = false
        box.isFocusable = false
        box.setPadding(d(14f), d(8f), d(14f), d(8f))
        askRow.addView(box, LayoutParams(0, -2, 1f))
        L.bind(askGo, "Crear", "Create")
        askGo.textSize = 14f
        askGo.gravity = Gravity.CENTER
        askGo.setPadding(d(14f), 0, d(14f), 0)
        askGo.setOnClickListener { actions?.onCodeFeedback(); actions?.onCodeAsk(box.text?.toString() ?: "") }
        askRow.addView(askGo, LayoutParams(-2, d(38f)).apply { leftMargin = d(6f) })
        askCancel.setOnClickListener { actions?.onCodeFeedback(); actions?.onCodeCancelAsk() }
        askRow.addView(askCancel, LayoutParams(d(34f), d(38f)))
        askRow.visibility = GONE
        addView(askRow, LayoutParams(-1, -2).apply { topMargin = d(6f) })
        connection = box.onCreateInputConnection(inputInfo) ?: android.view.inputmethod.BaseInputConnection(box, true)

        status.textSize = 13f
        status.setPadding(d(10f), d(6f), d(10f), d(2f))
        status.visibility = GONE
        addView(status, LayoutParams(-1, -2))

        result.textSize = 14f
        result.setLineSpacing(d(2f).toFloat(), 1f)
        result.setPadding(d(14f), d(10f), d(14f), d(10f))
        result.setTextIsSelectable(false)
        resultScroll.addView(result, LayoutParams(-1, -2))
        resultScroll.visibility = GONE
        addView(resultScroll, LayoutParams(-1, -2).apply { topMargin = d(4f) })

        chips.orientation = HORIZONTAL
        chips.setPadding(0, d(6f), 0, d(2f))
        chipsScroll.isHorizontalScrollBarEnabled = false
        chipsScroll.addView(chips)
        chipsScroll.visibility = GONE
        addView(chipsScroll, LayoutParams(-1, -2))
        applyPalette(palette)
    }

    fun setLang(lang: String) { langBtn.text = "$lang ▾" }

    /** Muestra la caja para describir el código. */
    fun showAsk() {
        hideResult()
        box.text?.clear()
        askRow.visibility = VISIBLE
        actionsScroll.visibility = GONE
        box.text?.let { android.text.Selection.setSelection(it, 0) }
        box.resetBlink()
    }

    fun hideAsk() {
        askRow.visibility = GONE
        actionsScroll.visibility = VISIBLE
    }

    val asking get() = askRow.visibility == VISIBLE

    /** "Pensando…", errores, etc. */
    fun showStatus(text: String) {
        status.text = Emo.strip(text)
        status.visibility = VISIBLE
        resultScroll.visibility = GONE
        chipsScroll.visibility = GONE
        chips.removeAllViews()
    }

    /** Error con un botón para arreglarlo (falta la clave de Gemini). */
    fun showNeedKey() {
        showStatus(L.t("El Modo código usa Google Gemini: necesita tu clave GRATIS (una sola vez).",
            "Code mode uses Google Gemini: it needs your FREE key (just once)."))
        chipsScroll.visibility = VISIBLE
        chip(L.t("Poner mi clave", "Add my key"), true) { actions?.onCodeAddKey() }
        chip(L.t("Cerrar", "Close"), false) { hideResult() }
    }

    /**
     * Muestra la respuesta. [code] = el código para "Insertar" (si hay); [isCode] = mostrar en letra de código.
     */
    fun showResult(heading: String, text: String, code: String?, isCode: Boolean) {
        status.text = heading
        status.visibility = VISIBLE
        result.text = text
        result.typeface = if (isCode) Typeface.MONOSPACE else Typeface.DEFAULT
        resultScroll.visibility = VISIBLE
        resultScroll.scrollTo(0, 0)
        chips.removeAllViews()
        chipsScroll.visibility = VISIBLE
        if (code != null) chip(L.t("Insertar código", "Insert code"), true) { actions?.onCodeInsert(code) }
        chip(L.t("Copiar", "Copy"), code == null) { actions?.onCodeCopy(code ?: text) }
        chip(L.t("Cerrar", "Close"), false) { hideResult() }
        chipsScroll.scrollTo(0, 0)
    }

    fun hideResult() {
        status.visibility = GONE
        resultScroll.visibility = GONE
        chipsScroll.visibility = GONE
        chips.removeAllViews()
    }

    private fun chip(label: String, main: Boolean, onClick: () -> Unit) {
        val c = TextView(context)
        c.text = label
        c.textSize = 14f
        c.gravity = Gravity.CENTER
        c.setPadding(d(16f), d(8f), d(16f), d(8f))
        val p = palette
        if (main) {
            c.setTypeface(c.typeface, Typeface.BOLD)
            c.setTextColor(p.onAccent)
            c.background = PanelUi.pressable(p.accent, p.accentPressed, d(18f).toFloat())
        } else {
            c.setTextColor(p.text)
            c.background = PanelUi.pressable(p.key, p.keyPressed, d(18f).toFloat())
        }
        c.maxLines = 1
        c.ellipsize = TextUtils.TruncateAt.END
        c.setOnClickListener { actions?.onCodeFeedback(); onClick() }
        chips.addView(c, LayoutParams(-2, -2).apply { rightMargin = d(8f) })
    }

    fun applyPalette(p: Palette) {
        palette = p
        background = PanelUi.barBackground(context, p) // 🎨 ya no gris
        icon.color = p.accent
        icon.bgColor = p.bg
        title.setTextColor(p.text)
        val r = d(17f).toFloat()
        langBtn.setTextColor(p.onAccent)
        langBtn.background = PanelUi.pressable(p.accent, p.accentPressed, r)
        close.color = p.text
        close.background = PanelUi.pressable(p.special, p.keyPressed, r)
        for (t in pills) {
            t.setTextColor(p.accent)
            t.background = PanelUi.pressable(p.key, p.keyPressed, r)
        }
        box.setTextColor(p.text)
        box.cursorColor = p.accent
        box.setHintTextColor(p.hint)
        box.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(p.key); cornerRadius = d(20f).toFloat(); setStroke(d(1.5f), p.accent)
        }
        askGo.setTextColor(p.onAccent)
        askGo.background = PanelUi.pressable(p.accent, p.accentPressed, r)
        askCancel.color = p.hint
        status.setTextColor(p.hint)
        result.setTextColor(p.text)
        result.background = PanelUi.rounded(p.key, d(14f).toFloat())
    }
}
