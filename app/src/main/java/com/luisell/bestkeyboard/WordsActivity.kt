package com.luisell.bestkeyboard

import android.app.AlertDialog
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * "Mis palabras": las palabras que el teclado aprendió de ti. Puedes buscarlas, cambiarlas,
 * borrarlas una por una y agregar nuevas.
 */
class WordsActivity : UiActivity() {

    private lateinit var list: LinearLayout
    private lateinit var count: TextView
    private var filter = ""

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
        title.text = tr("Mis palabras", "My words")
        title.textSize = 28f
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

        count = text("", 13f, faded = true)
        button(tr("+  Agregar palabra", "+  Add word"), primary = true) { editDialog(null) }

        val search = EditText(this).apply {
            hint = tr("Buscar…", "Search…")
            isSingleLine = true
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(22f); setStroke(dp(1), LINE) }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { filter = s?.toString()?.trim()?.lowercase() ?: ""; refresh() }
        })
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(22f); setStroke(dp(1), LINE) }
        list.setPadding(dp(14), dp(4), dp(6), dp(4))
        root.addView(list, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        blockedHead = text("", 13f, faded = true)
        blockedList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        blockedList.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(22f); setStroke(dp(1), LINE) }
        blockedList.setPadding(dp(14), dp(4), dp(6), dp(4))
        root.addView(blockedList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        fixHead = text("", 13f, faded = true)
        fixList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fixList.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(22f); setStroke(dp(1), LINE) }
        fixList.setPadding(dp(14), dp(4), dp(6), dp(4))
        root.addView(fixList, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        setContentView(sv)

        count.text = tr("Cargando…", "Loading…")
        WordStore.load(this) { refresh() }
    }

    override fun onPause() {
        super.onPause()
        WordStore.saveNow()
    }

    private fun user() = WordStore.engine?.user

    private lateinit var fixHead: TextView
    private lateinit var fixList: LinearLayout

    /** Tus arreglos aprendidos ("clmo" → "como"). */
    private fun refreshFixes() {
        val u = user() ?: return
        val all = u.fixes.entries.sortedBy { it.key }
        fixList.removeAllViews()
        fixHead.text = if (all.isEmpty()) "" else tr("\nTus arreglos (${all.size}): lo de la izquierda se cambia solo por lo de la derecha. Toca ✕ para quitar uno.",
            "\nYour fixes (${all.size}): the left one changes by itself into the right one. Tap ✕ to remove one.")
        fixHead.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        fixList.visibility = fixHead.visibility
        for ((i, e) in all.take(300).withIndex()) {
            if (i > 0) fixList.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(6), 0, dp(6))
            val w = TextView(this)
            w.text = e.key + "  →  " + e.value.first
            w.textSize = 16f
            w.setTextColor(TEXT)
            row.addView(w, LinearLayout.LayoutParams(0, -2, 1f))
            val del = IconView(this, Icons.CLOSE, 6f)
            del.color = SUB
            val key = e.key
            del.setOnClickListener {
                u.fixes.remove(key); u.dirty = true
                WordStore.scheduleSave()
                refreshFixes()
            }
            row.addView(del, LinearLayout.LayoutParams(dp(40), dp(40)))
            fixList.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private lateinit var blockedHead: TextView
    private lateinit var blockedList: LinearLayout

    /** Las que borraste de las sugerencias: puedes devolverlas. */
    private fun refreshBlocked() {
        val u = user() ?: return
        val all = u.blocked.sorted()
        blockedList.removeAllViews()
        blockedHead.text = if (all.isEmpty()) "" else tr("\nBorradas de las sugerencias (${all.size}). No vuelven a salir; toca ↶ para devolver una.",
            "\nDeleted from suggestions (${all.size}). They won't show again; tap ↶ to bring one back.")
        blockedHead.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
        blockedList.visibility = blockedHead.visibility
        for ((i, x) in all.take(300).withIndex()) {
            if (i > 0) blockedList.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(6), 0, dp(6))
            val w = TextView(this)
            w.text = x
            w.textSize = 16f
            w.setTextColor(SUB)
            row.addView(w, LinearLayout.LayoutParams(0, -2, 1f))
            val back = IconView(this, Icons.UNDO, 7f)
            back.color = CYAN
            back.setOnClickListener {
                u.unblock(x)
                WordStore.scheduleSave()
                toast("«$x» puede volver a salir", "«$x» can show up again")
                refreshBlocked()
            }
            row.addView(back, LinearLayout.LayoutParams(dp(40), dp(40)))
            blockedList.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** Dibuja la lista (en orden alfabético, con la búsqueda). */
    private fun refresh() {
        val u = user() ?: return
        refreshBlocked()
        refreshFixes()
        val all = u.words.values.filter { it.known }.sortedBy { it.display.lowercase() }
        val shown = if (filter.isEmpty()) all else all.filter { it.display.lowercase().contains(filter) }
        count.text = tr("${all.size} palabras aprendidas. Toca ✎ para cambiar una o ✕ para borrarla.",
            "${all.size} learned words. Tap ✎ to change one or ✕ to delete it.")
        list.removeAllViews()
        if (shown.isEmpty()) {
            val t = TextView(this)
            t.text = if (all.isEmpty()) tr("Todavía no hay palabras aprendidas.", "No learned words yet.") else tr("No hay resultados.", "No results.")
            t.textSize = 14f
            t.setTextColor(SUB)
            t.setPadding(0, dp(14), 0, dp(14))
            list.addView(t)
            return
        }
        // Para no hacer la pantalla lenta con miles de palabras: las primeras 400 (usa la búsqueda)
        for ((i, e) in shown.take(400).withIndex()) {
            if (i > 0) list.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(6), 0, dp(6))
            val texts = LinearLayout(this)
            texts.orientation = LinearLayout.VERTICAL
            val w = TextView(this)
            w.text = e.display
            w.textSize = 16f
            w.setTextColor(TEXT)
            texts.addView(w)
            val info = TextView(this)
            info.text = if (e.sure) tr("Confirmada por ti", "Confirmed by you") else tr("Escrita ${e.count} veces", "Typed ${e.count} times")
            info.textSize = 11.5f
            info.setTextColor(SUB)
            texts.addView(info)
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            val edit = IconView(this, Icons.PENCIL, 7f)
            edit.color = CYAN
            edit.setOnClickListener { editDialog(e.display) }
            row.addView(edit, LinearLayout.LayoutParams(dp(40), dp(40)))
            val del = IconView(this, Icons.CLOSE, 6f)
            del.color = SUB
            del.setOnClickListener {
                user()?.block(e.display)
                WordStore.scheduleSave()
                toast("«${e.display}» borrada, ya no saldrá", "«${e.display}» deleted, it won't show up")
                refresh()
            }
            row.addView(del, LinearLayout.LayoutParams(dp(40), dp(40)))
            list.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** Agregar una palabra nueva ([old] = null) o cambiar una que ya existe. */
    private fun editDialog(old: String?) {
        val box = EditText(this).apply {
            hint = tr("Palabra", "Word")
            setText(old ?: "")
            isSingleLine = true
            textSize = 16f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
            setSelection(text.length)
        }
        val wrap = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(box, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(this)
            .setTitle(if (old == null) tr("Agregar palabra", "Add word") else tr("Cambiar palabra", "Change word"))
            .setView(wrap)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val w = box.text.toString().trim()
                if (w.isEmpty() || w.contains(' ')) {
                    toast("Escribe una sola palabra, sin espacios.", "Type a single word, no spaces.")
                    return@setPositiveButton
                }
                val u = user() ?: return@setPositiveButton
                if (old != null) u.forget(old.lowercase())
                u.learn(w)
                WordStore.scheduleSave()
                refresh()
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }
}
