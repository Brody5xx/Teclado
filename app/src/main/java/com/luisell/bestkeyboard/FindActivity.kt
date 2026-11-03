package com.luisell.bestkeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 🔍 Buscar una palabra en tus tickets, la libreta y el portapapeles. Tocas un resultado y queda copiado. */
class FindActivity : UiActivity() {

    private lateinit var results: LinearLayout
    private lateinit var count: TextView

    private class Hit(val kind: String, val title: String, val text: String, val time: Long)

    private val all = ArrayList<Hit>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val sv = ScrollView(this)
        PanelUi.glowScrollbar(sv)
        sv.setBackgroundColor(BG)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(48))
        }
        col = root
        sv.addView(root)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = IconView(this, Icons.BACK, 9f)
        back.color = TEXT
        back.setOnClickListener { finish() }
        head.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val title = TextView(this)
        title.text = tr("Buscar", "Search")
        title.textSize = 24f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(TEXT)
        title.setPadding(dp(6), 0, 0, 0)
        title.post {
            title.paint.shader = LinearGradient(0f, 0f, title.width.toFloat(), 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            title.invalidate()
        }
        head.addView(title)
        head.addView(DiamondView(this), LinearLayout.LayoutParams(dp(26), dp(26)).apply { leftMargin = dp(10) }) // 💎 con su luz
        root.addView(head)

        val box = EditText(this).apply {
            hint = tr("Escribe una palabra (ej. puerta, cable)", "Type a word (e.g. door, cable)")
            isSingleLine = true
            textSize = 16f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(24f); setStroke(dp(1), CYAN) }
        }
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        count = text("", 12f, faded = true)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(results, LinearLayout.LayoutParams(-1, -2))
        setContentView(sv)

        // Todo lo que se puede buscar
        for (t in WorkStore.tickets(prefs)) all.add(Hit("🗂️", tr("Ticket ", "Ticket ") + t.num + " · " + WorkStore.shortDate(t.time, en), t.text, t.time))
        for (n in WorkStore.notes(prefs)) all.add(Hit("📒", tr("Libreta", "Notebook") + " · " + WorkStore.shortDate(n.time, en), n.text, n.time))
        ClipStore.load(this) {
            runOnUiThread {
                for (c in ClipStore.ordered()) {
                    val tx = c.text ?: continue
                    all.add(Hit("📋", tr("Portapapeles", "Clipboard") + " · " + WorkStore.shortDate(c.time, en), tx, c.time))
                }
                show(box.text.toString())
            }
        }
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { show(s?.toString() ?: "") }
        })
        box.requestFocus()
        show("")
    }

    /** "instalacion" encuentra "instalación". */
    private fun plain(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private fun show(q: String) {
        results.removeAllViews()
        val words = plain(q).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) {
            count.text = tr("Busca en tus tickets, la libreta del día y el portapapeles.", "Searches your tickets, the day notebook and the clipboard.")
            return
        }
        val found = all.filter { h -> val t = plain(h.text); words.all { it in t } }.sortedByDescending { it.time }.take(60)
        count.text = if (found.isEmpty()) tr("No encontré nada con eso.", "Nothing found.") else
            tr("${found.size} resultado(s) · toca uno para copiarlo", "${found.size} result(s) · tap one to copy it")
        for ((i, h) in found.withIndex()) {
            if (i > 0) results.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.setPadding(0, dp(10), 0, dp(10))
            val t = TextView(this).apply { text = h.kind + "  " + h.title; textSize = 12f; setTextColor(CYAN) }
            c.addView(t)
            val body = TextView(this).apply { textSize = 14f; setTextColor(TEXT); maxLines = 5; text = highlight(h.text, words) }
            c.addView(body)
            c.setOnClickListener {
                try {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Best Keyboard", h.text))
                } catch (_: Exception) { }
                toast("Copiado. Mantén presionado donde escribes y toca Pegar.", "Copied. Long-press where you type and tap Paste.")
                finish()
            }
            results.addView(c)
        }
    }

    /** Pinta las palabras que buscaste. */
    private fun highlight(text: String, words: List<String>): CharSequence {
        val s = SpannableString(text)
        val p = plain(text)
        // Si al quitar los acentos cambia el largo, no se pinta (para no marcar mal)
        if (p.length != text.length) return text
        for (w in words) {
            var i = p.indexOf(w)
            while (i >= 0) {
                s.setSpan(BackgroundColorSpan(0x5522D3EE), i, i + w.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(ForegroundColorSpan(0xFFFFFFFF.toInt()), i, i + w.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i = p.indexOf(w, i + w.length)
            }
        }
        return s
    }
}
