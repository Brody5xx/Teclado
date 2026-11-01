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

/**
 * Botones de "Mi jornada" a tu gusto: quita los que no uses (por ejemplo «Comencé» si no vas en camioneta)
 * y ponles el nombre que tú quieras («Entrada», «Clock in», «Llegué a la oficina»…).
 */
class WorkdayButtonsActivity : UiActivity() {

    private lateinit var list: LinearLayout
    private val enabled = HashSet<String>()
    private val names = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        WorkDay.config(prefs)
        for (k in WorkDay.ALL_KINDS) {
            if (WorkDay.isOn(k)) enabled.add(k)
            WorkDay.customName(k).takeIf { it.isNotEmpty() }?.let { names[k] = it }
        }

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
        title.text = tr("Botones de Mi jornada", "My workday buttons")
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
        text(tr("Prende solo los botones que usas y cámbiales el nombre como tú quieras (puedes poner un emoji al principio). " +
            "Se cambian en el teclado, el widget, la pantalla de bloqueo y los ajustes rápidos.",
            "Turn on only the buttons you use and rename them however you like (you can put an emoji first). " +
                "They change on the keyboard, the widget, the lock screen and Quick Settings."), 13f, faded = true)
        card(tr("Tus botones", "Your buttons"), Icons.TRUCK)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list, LinearLayout.LayoutParams(-1, -2))
        button(tr("Restaurar los de fábrica", "Restore defaults")) {
            enabled.clear(); enabled.addAll(WorkDay.ALL_KINDS); names.clear(); save(); refresh()
        }
        setContentView(sv)
        refresh()
    }

    private fun save() {
        WorkDay.saveConfig(prefs, enabled, names)
        WorkdayCenter.refresh(this)
    }

    private fun refresh() {
        list.removeAllViews()
        for ((i, k) in WorkDay.ALL_KINDS.withIndex()) {
            if (i > 0) list.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(10), 0, dp(10))
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            val on = k in enabled
            val n = TextView(this)
            n.text = names[k] ?: WorkDay.defaultLabel(k)
            n.textSize = 17f
            n.setTextColor(if (on) TEXT else SUB)
            c.addView(n)
            val sub = TextView(this)
            sub.text = (if (on) tr("Se ve", "Visible") else tr("Escondido", "Hidden")) + "  ·  " + tr("toca para cambiar el nombre", "tap to rename")
            sub.textSize = 12f
            sub.setTextColor(SUB)
            c.addView(sub)
            c.setOnClickListener { rename(k) }
            row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
            // Interruptor: prender o quitar el botón
            val sw = TextView(this)
            sw.text = if (on) tr("Quitar", "Hide") else tr("Poner", "Show")
            sw.textSize = 14f
            sw.gravity = Gravity.CENTER
            sw.setPadding(dp(16), 0, dp(16), 0)
            sw.setTextColor(if (on) TEXT else 0xFF0B1020.toInt())
            sw.background = if (on) GradientDrawable().apply { cornerRadius = dpf(18f); setStroke(dp(1), LINE) }
                else GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(18f) }
            sw.setOnClickListener {
                if (on && enabled.size == 1) { toast("Deja al menos un botón.", "Keep at least one button."); return@setOnClickListener }
                if (on) enabled.remove(k) else enabled.add(k)
                save(); refresh()
            }
            row.addView(sw, LinearLayout.LayoutParams(-2, dp(36)))
            list.addView(row)
        }
    }

    private fun rename(k: String) {
        val box = EditText(this).apply {
            setText(names[k] ?: WorkDay.defaultLabel(k))
            setSelection(text.length)
            isSingleLine = true
            textSize = 16f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            hint = WorkDay.defaultLabel(k)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
        }
        val wrap = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(box, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(this)
            .setTitle(tr("Nombre del botón", "Button name"))
            .setView(wrap)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val v = box.text.toString().trim()
                if (v.isEmpty() || v == WorkDay.defaultLabel(k)) names.remove(k) else names[k] = v.take(30)
                save(); refresh()
            }
            .setNeutralButton(tr("De fábrica", "Default")) { _, _ -> names.remove(k); save(); refresh() }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }
}
