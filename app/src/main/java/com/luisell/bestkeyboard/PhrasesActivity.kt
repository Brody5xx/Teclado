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

/** "Frases": agrega, cambia y borra tus frases rápidas (salen primero en el teclado). */
class PhrasesActivity : UiActivity() {

    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val sv = ScrollView(this)
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
        title.text = tr("Frases", "Phrases")
        title.textSize = 28f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(TEXT)
        title.setPadding(dp(6), 0, 0, 0)
        title.post {
            title.paint.shader = LinearGradient(0f, 0f, title.width.toFloat(), 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            title.invalidate()
        }
        head.addView(title)
        root.addView(head)

        text(tr("Escribe ___ (tres guiones bajos) donde tengas que llenar algo: al insertar la frase queda seleccionado para escribir encima.",
            "Type ___ (three underscores) where you need to fill something in: when the phrase is inserted it gets selected so you can type over it."), 13f, faded = true)
        button(tr("+  Agregar frase", "+  Add phrase"), primary = true) { editDialog(-1) }
        card(tr("Mis frases", "My phrases"), Icons.BUBBLE)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list, LinearLayout.LayoutParams(-1, -2))
        card(tr("Opciones", "Options"), Icons.SETTINGS)
        switch(tr("Mostrar frases de ejemplo del trabajo", "Show example work phrases"), Prefs.PHRASES_EXAMPLES, true,
            tr("Reportes de control de acceso, cámaras, cableado y fibra, en español e inglés.",
                "Access control, cameras, cabling and fiber reports, in Spanish and English."))
        setContentView(sv)
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        val mine = Phrases.mine(prefs)
        if (mine.isEmpty()) {
            val t = TextView(this)
            t.text = tr("Todavía no agregaste frases.", "You haven't added phrases yet.")
            t.textSize = 13f
            t.setTextColor(SUB)
            t.setPadding(0, dp(12), 0, dp(6))
            list.addView(t)
            return
        }
        for ((i, p) in mine.withIndex()) {
            if (i > 0) list.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(8), 0, dp(8))
            val t = TextView(this)
            t.text = p
            t.textSize = 15f
            t.setTextColor(TEXT)
            row.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            val edit = IconView(this, Icons.PENCIL, 7f)
            edit.color = CYAN
            edit.setOnClickListener { editDialog(i) }
            row.addView(edit, LinearLayout.LayoutParams(dp(40), dp(40)))
            val del = IconView(this, Icons.CLOSE, 6f)
            del.color = SUB
            del.setOnClickListener {
                Phrases.saveMine(prefs, mine.filterIndexed { j, _ -> j != i })
                refresh()
            }
            row.addView(del, LinearLayout.LayoutParams(dp(40), dp(40)))
            list.addView(row)
        }
    }

    /** Agregar ([index] = -1) o cambiar una frase. */
    private fun editDialog(index: Int) {
        val mine = Phrases.mine(prefs)
        val box = EditText(this).apply {
            hint = tr("Tu frase…", "Your phrase…")
            setText(mine.getOrNull(index) ?: "")
            minLines = 2
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
        }
        val wrap = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(box, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(this)
            .setTitle(if (index < 0) tr("Agregar frase", "Add phrase") else tr("Cambiar frase", "Change phrase"))
            .setView(wrap)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val p = box.text.toString().trim()
                if (p.isEmpty()) return@setPositiveButton
                val l = mine.toMutableList()
                if (index in l.indices) l[index] = p else l.add(0, p)
                Phrases.saveMine(prefs, l)
                refresh()
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }
}
