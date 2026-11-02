package com.luisell.bestkeyboard

import android.app.AlertDialog
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** ⚡ Atajos de texto: "/voy" + espacio → "Voy en camino, llego en unos minutos." */
class ShortcutsActivity : UiActivity() {

    private lateinit var list: LinearLayout

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
        title.text = tr("Atajos de texto", "Text shortcuts")
        title.textSize = 24f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(TEXT)
        title.setPadding(dp(6), 0, 0, 0)
        title.post {
            title.paint.shader = LinearGradient(0f, 0f, title.width.toFloat(), 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            title.invalidate()
        }
        head.addView(title)
        root.addView(head)
        text(tr("Escribe el atajo (por ejemplo /voy) y toca la barra espaciadora: se cambia solo por tu frase completa.",
            "Type the shortcut (for example /omw) and tap the space bar: it turns into your full phrase."), 13f, faded = true)
        button(tr("➕ Atajo nuevo", "➕ New shortcut"), primary = true) { edit(null) }
        card(tr("Tus atajos", "Your shortcuts"), Icons.BUBBLE)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list, LinearLayout.LayoutParams(-1, -2))
        setContentView(sv)
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        for ((i, s) in Shortcuts.all(prefs).withIndex()) {
            if (i > 0) list.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(10), 0, dp(10))
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            val code = TextView(this)
            code.text = s.code
            code.textSize = 16f
            code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            code.setTextColor(CYAN)
            c.addView(code)
            val t = TextView(this)
            t.text = s.text
            t.textSize = 14f
            t.setTextColor(TEXT)
            c.addView(t)
            c.setOnClickListener { edit(s) }
            row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
            val del = IconView(this, Icons.TRASH, 8f)
            del.color = SUB
            del.setOnClickListener { Shortcuts.remove(prefs, s.code); refresh() }
            row.addView(del, LinearLayout.LayoutParams(dp(40), dp(40)))
            list.addView(row)
        }
    }

    private fun box(hint: String, value: String, multi: Boolean) = EditText(this).apply {
        setText(value)
        setSelection(text.length)
        isSingleLine = !multi
        if (multi) { minLines = 2; maxLines = 6 }
        textSize = 16f
        setTextColor(TEXT)
        setHintTextColor(SUB)
        this.hint = hint
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
    }

    private fun edit(item: Shortcuts.Item?) {
        val code = box(tr("Atajo (ej. /voy)", "Shortcut (e.g. /omw)"), item?.code ?: "/", false)
        val text = box(tr("La frase completa", "The full phrase"), item?.text ?: "", true)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(code, LinearLayout.LayoutParams(-1, -2))
            addView(text, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        }
        AlertDialog.Builder(this)
            .setTitle(if (item == null) tr("Atajo nuevo", "New shortcut") else tr("Editar atajo", "Edit shortcut"))
            .setView(wrap)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val c = Shortcuts.clean(code.text.toString())
                if (c.isEmpty() || text.text.isBlank()) { toast("Escribe el atajo y la frase.", "Type the shortcut and the phrase."); return@setPositiveButton }
                if (item != null && item.code != c) Shortcuts.remove(prefs, item.code)
                Shortcuts.add(prefs, c, text.text.toString())
                refresh()
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }
}
