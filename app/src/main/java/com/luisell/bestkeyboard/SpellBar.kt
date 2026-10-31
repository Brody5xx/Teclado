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
    /** Se muestra "Reescribir formal" (cuando revisas un texto seleccionado). */
    var rewriteOn = false
        set(v) { field = v; rewrite.visibility = if (v) VISIBLE else GONE }
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
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        title.setPadding(d(10f), 0, d(8f), 0)
        row.addView(title, LayoutParams(0, -2, 1f))
        L.bind(fixAll, "Corregir todo", "Fix all")
        fixAll.textSize = 14f
        fixAll.gravity = Gravity.CENTER
        fixAll.setPadding(d(14f), 0, d(14f), 0)
        fixAll.setOnClickListener { actions?.onSpellFixAll() }
        row.addView(fixAll, LayoutParams(-2, d(34f)))
        L.bind(rewrite, "Reescribir formal", "Make formal")
        rewrite.textSize = 14f
        rewrite.gravity = Gravity.CENTER
        rewrite.setPadding(d(14f), 0, d(14f), 0)
        rewrite.visibility = GONE
        rewrite.setOnClickListener { actions?.onRewrite() }
        row.addView(rewrite, LayoutParams(-2, d(34f)).apply { leftMargin = d(6f) })
        addView(row, LayoutParams(-1, -2))

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
        rewrite.visibility = if (rewriteOn) VISIBLE else GONE
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

    /** Muestra la versión formal [index] de [versions], con botones para usarla. */
    fun showRewrite(versions: List<String>, index: Int) {
        title.text = L.t("Versión formal", "Formal version")
        fixAll.visibility = GONE
        rewrite.visibility = GONE
        previewScroll.visibility = VISIBLE
        preview.text = versions[index]
        previewScroll.scrollTo(0, 0)
        chips.removeAllViews()
        chip(L.t("Usar este texto", "Use this text"), true) { actions?.onRewriteUse(index) }
        if (versions.size > 1) {
            chip(L.t("Otra versión (${index + 1}/${versions.size})", "Another version (${index + 1}/${versions.size})"), false) {
                showRewrite(versions, (index + 1) % versions.size)
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
        rewrite.visibility = if (showRewrite) VISIBLE else GONE
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
        rewrite.setTextColor(p.accent)
        rewrite.background = PanelUi.pressable(p.key, p.keyPressed, r)
        preview.setTextColor(p.text)
        preview.background = PanelUi.rounded(p.key, d(14f).toFloat())
    }
}
