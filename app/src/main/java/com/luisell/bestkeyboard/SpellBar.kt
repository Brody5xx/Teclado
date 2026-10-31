package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Revisión de ortografía (como Google Docs): muestra TODO tu texto con las palabras mal escritas
 * en rojo y subrayadas. Toca una para ver sus correcciones, o "Corregir todo".
 */
@SuppressLint("ViewConstructor")
class SpellBar(context: Context) : LinearLayout(context) {

    interface Actions {
        fun onSpellPick(word: String)
        fun onSpellSkip()
        fun onSpellLearn()
        fun onSpellClose()
        /** Tocaste la palabra mal escrita número [index] en el texto. */
        fun onSpellSelect(index: Int)
        fun onSpellFixAll()
        /** "Reescribir formal" (solo con texto seleccionado). */
        fun onRewrite()
        /** "Corregir gramática" (con internet, LanguageTool). */
        fun onGrammar()
        /** Usar la versión formal número [index]. */
        fun onRewriteUse(index: Int)
    }

    /** Un error dentro del texto que se muestra ([start]..[end] relativos al texto). */
    class Mark(val start: Int, val end: Int, val maybe: Boolean)

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val close = IconView(context, Icons.CLOSE, 7f)
    private val title = TextView(context)
    private val fixAll = TextView(context)
    private val rewrite = TextView(context)
    private val grammar = TextView(context)
    private val actionsRow = LinearLayout(context)
    private val actionsScroll = HorizontalScrollView(context)
    /** Se muestran "Corregir gramática" y "Reescribir formal". */
    var rewriteOn = false
        set(v) { field = v; showActions(true) }
    private val preview = TextView(context)
    private val previewScroll = object : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Máximo ~5 líneas de alto; si el texto es más largo, se desliza
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(d(118f), MeasureSpec.AT_MOST))
        }
    }
    private val chips = LinearLayout(context)
    private val scroll = HorizontalScrollView(context)

    init {
        orientation = VERTICAL
        setPadding(d(6f), d(6f), d(6f), d(4f))

        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        close.setOnClickListener { actions?.onSpellClose() }
        row.addView(close, LayoutParams(d(40f), d(36f)))
        title.textSize = 15f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.maxLines = 3
        title.ellipsize = TextUtils.TruncateAt.END
        title.setPadding(d(10f), 0, d(8f), 0)
        row.addView(title, LayoutParams(0, -2, 1f))
        addView(row, LayoutParams(-1, -2))

        // Botones: Corregir todo · Corregir gramática · Más formal
        actionsRow.orientation = HORIZONTAL
        actionsRow.gravity = Gravity.CENTER_VERTICAL
        L.bind(fixAll, "Corregir todo", "Fix all")
        L.bind(grammar, "Corregir gramática", "Fix grammar")
        L.bind(rewrite, "Más formal", "More formal")
        for (b in listOf(fixAll, grammar, rewrite)) {
            b.textSize = 14f
            b.gravity = Gravity.CENTER
            b.setPadding(d(14f), 0, d(14f), 0)
            actionsRow.addView(b, LayoutParams(-2, d(34f)).apply { rightMargin = d(8f) })
        }
        fixAll.setOnClickListener { actions?.onSpellFixAll() }
        grammar.setOnClickListener { actions?.onGrammar() }
        rewrite.setOnClickListener { actions?.onRewrite() }
        actionsScroll.isHorizontalScrollBarEnabled = false
        actionsScroll.addView(actionsRow)
        addView(actionsScroll, LayoutParams(-1, -2).apply { topMargin = d(6f) })

        // Tu texto, con los errores marcados
        preview.textSize = 16f
        preview.setLineSpacing(d(3f).toFloat(), 1f)
        preview.setPadding(d(14f), d(10f), d(14f), d(10f))
        preview.movementMethod = LinkMovementMethod.getInstance()
        preview.highlightColor = 0
        previewScroll.isVerticalScrollBarEnabled = true
        previewScroll.addView(preview, LayoutParams(-1, -2))
        addView(previewScroll, LayoutParams(-1, -2).apply { topMargin = d(6f) })

        chips.orientation = HORIZONTAL
        chips.gravity = Gravity.CENTER_VERTICAL
        chips.setPadding(0, d(6f), 0, d(2f))
        scroll.isHorizontalScrollBarEnabled = false
        scroll.addView(chips)
        addView(scroll, LayoutParams(-1, -2))
        applyPalette(palette)
    }

    /**
     * Muestra el texto completo con todos los errores; el número [current] está elegido
     * y abajo salen sus correcciones.
     */
    fun showAll(text: String, marks: List<Mark>, current: Int, suggestions: List<String>) {
        val n = marks.size
        title.text = if (n == 1) L.t("1 palabra por revisar", "1 word to check")
        else L.t("$n palabras por revisar", "$n words to check")
        fixAll.visibility = VISIBLE
        showActions(true)
        previewScroll.visibility = VISIBLE

        val red = 0xFFE53935.toInt()
        val sp = SpannableString(text)
        for ((i, m) in marks.withIndex()) {
            if (m.start < 0 || m.end > text.length || m.start >= m.end) continue
            val color = if (m.maybe) 0xFFFB8C00.toInt() else red
            sp.setSpan(ForegroundColorSpan(color), m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(UnderlineSpan(), m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (i == current) sp.setSpan(BackgroundColorSpan(color and 0x40FFFFFF), m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) { actions?.onSpellSelect(i) }
                override fun updateDrawState(ds: TextPaint) { ds.isUnderlineText = true }
            }, m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        preview.text = sp
        // Que se vea la palabra elegida
        val cur = marks.getOrNull(current)
        if (cur != null) preview.post {
            val lay = preview.layout ?: return@post
            val line = lay.getLineForOffset(cur.start.coerceIn(0, text.length))
            val y = lay.getLineTop(line) + preview.paddingTop - d(30f)
            previewScroll.smoothScrollTo(0, maxOf(0, y))
        }

        chips.removeAllViews()
        val word = cur?.let { text.substring(it.start, it.end) } ?: ""
        if (suggestions.isEmpty()) {
            chip(L.t("Sin ideas para «$word»", "No ideas for «$word»"), false, hint = true) {}
        }
        for ((i, s) in suggestions.withIndex()) chip(s, i == 0) { actions?.onSpellPick(s) }
        chip(L.t("+ Mi palabra", "+ My word"), false, accentText = true) { actions?.onSpellLearn() }
        chip(L.t("Saltar ›", "Skip ›"), false) { actions?.onSpellSkip() }
        scroll.scrollTo(0, 0)
    }

    private fun chip(label: String, main: Boolean, accentText: Boolean = false, hint: Boolean = false, onClick: () -> Unit) {
        val c = TextView(context)
        c.text = label
        c.textSize = if (hint) 13f else 15f
        c.gravity = Gravity.CENTER
        c.setPadding(d(14f), d(8f), d(14f), d(8f))
        val p = palette
        when {
            main -> {
                c.setTypeface(c.typeface, Typeface.BOLD)
                c.setTextColor(p.onAccent)
                c.background = PanelUi.pressable(p.accent, p.accentPressed, d(18f).toFloat())
            }
            hint -> c.setTextColor(p.hint)
            else -> {
                c.setTextColor(if (accentText) p.accent else p.text)
                c.background = PanelUi.pressable(p.key, p.keyPressed, d(18f).toFloat())
            }
        }
        if (!hint) c.setOnClickListener { onClick() }
        chips.addView(c, LayoutParams(-2, -2).apply { rightMargin = d(8f) })
    }

    /** Muestra u oculta la fila de botones (Corregir todo / gramática / formal). */
    private fun showActions(on: Boolean) {
        grammar.visibility = if (rewriteOn) VISIBLE else GONE
        rewrite.visibility = if (rewriteOn) VISIBLE else GONE
        actionsScroll.visibility = if (on && (rewriteOn || fixAll.visibility == VISIBLE)) VISIBLE else GONE
    }

    /**
     * Muestra una versión nueva del texto ([index] de [versions]) para usarla o cancelar.
     * [changes] = partes cambiadas que se pintan de verde (para "Corregir gramática").
     */
    fun showRewrite(versions: List<String>, index: Int, heading: String? = null, changes: List<IntRange> = emptyList()) {
        title.text = heading ?: L.t("Versión formal", "Formal version")
        actionsScroll.visibility = GONE
        previewScroll.visibility = VISIBLE
        val sp = SpannableString(versions[index])
        for (r in changes) {
            if (r.first < 0 || r.last >= sp.length || r.first > r.last) continue
            sp.setSpan(ForegroundColorSpan(0xFF2E7D32.toInt().let { if (palette.isDark) 0xFF81C995.toInt() else it }), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(BackgroundColorSpan(0x3334A853), r.first, r.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        preview.text = sp
        previewScroll.scrollTo(0, 0)
        chips.removeAllViews()
        chip(L.t("Usar este texto", "Use this text"), true) { actions?.onRewriteUse(index) }
        if (versions.size > 1) {
            chip(L.t("Otra versión (${index + 1}/${versions.size})", "Another version (${index + 1}/${versions.size})"), false) {
                showRewrite(versions, (index + 1) % versions.size, heading)
            }
        }
        chip(L.t("Cancelar", "Cancel"), false) { actions?.onSpellClose() }
        scroll.scrollTo(0, 0)
    }

    /** Mensaje (por ejemplo "Revisando…" o "Listo, sin errores"). */
    fun message(text: String, showRewrite: Boolean = rewriteOn) {
        title.text = text
        chips.removeAllViews()
        fixAll.visibility = GONE
        showActions(showRewrite)
        previewScroll.visibility = GONE
    }

    fun resetButtons() {
        fixAll.visibility = VISIBLE
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        val r = d(17f).toFloat()
        close.color = p.text
        close.background = PanelUi.pressable(p.special, p.keyPressed, r)
        fixAll.setTextColor(p.onAccent)
        fixAll.background = PanelUi.pressable(p.accent, p.accentPressed, r)
        for (b in listOf(grammar, rewrite)) {
            b.setTextColor(p.accent)
            b.background = PanelUi.pressable(p.key, p.keyPressed, r)
        }
        preview.setTextColor(p.text)
        preview.background = PanelUi.rounded(p.key, d(14f).toFloat())
    }
}
