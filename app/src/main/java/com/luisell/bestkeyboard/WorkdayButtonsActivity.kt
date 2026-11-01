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
        title.text = tr("Mi jornada", "My workday")
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
        // ---- Ícono de Mi jornada
        card(tr("Ícono", "Icon"), Icons.CLOCK)
        text(tr("El dibujito de Mi jornada en la barra del teclado, la notificación y los ajustes rápidos.",
            "The Mi jornada picture on the keyboard bar, the notification and Quick Settings."), 12f, faded = true)
        iconRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(iconRow)
        col.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        paintIcons()

        // ---- Horas extra
        card(tr("Horas extra", "Overtime"), Icons.STOPWATCH)
        segmented(tr("Horas normales por día", "Regular hours per day"), Prefs.OVERTIME_DAY, "8",
            listOf("0" to tr("Sin aviso", "Off"), "8" to "8 h", "9" to "9 h", "10" to "10 h")) { WorkDay.config(prefs); WorkdayCenter.refresh(this) }
        text(tr("Al pasar esas horas te llega un aviso y Mi jornada te muestra las horas extra. En la semana, lo que pase de 40 horas.",
            "When you pass those hours you get an alert and My workday shows the overtime. For the week, anything over 40 hours."), 12f, faded = true)

        // ---- Almuerzo
        card(tr("Almuerzo", "Lunch"), Icons.CLOCK)
        text(tr("Mientras trabajas sale el botón «🍔 Salí a almorzar» y después «↩️ Regresé», para anotar cuánto tomaste.",
            "While you work you'll see «🍔 Lunch break» and then «↩️ Back from lunch», to log how long you took."), 12f, faded = true)
        segmented(tr("Tiempo que te descuentan", "Time deducted"), Prefs.LUNCH_AUTO, "30",
            listOf("15" to "15 min", "30" to "30 min", "45" to "45 min", "60" to tr("1 hora", "1 hour"))) { WorkDay.config(prefs); WorkdayCenter.refresh(this); recreate() }
        // Cualquier otro tiempo (20 min, 1 h 30…), escrito por ti
        val curMin = (prefs.getString(Prefs.LUNCH_AUTO, "30") ?: "30").toIntOrNull() ?: 30
        val custom = curMin !in listOf(15, 30, 45, 60)
        button(if (custom) tr("✓ Otro tiempo: ", "✓ Other time: ") + minText(curMin) + tr("  (toca para cambiar)", "  (tap to change)")
            else tr("✏️ Otro tiempo…", "✏️ Other time…")) { askLunchMinutes(curMin) }
        segmented(tr("Tipo de almuerzo", "Lunch type"), Prefs.LUNCH_PAID + "_s", if (prefs.getBoolean(Prefs.LUNCH_PAID, false)) "paid" else "unpaid",
            listOf("paid" to tr("Pagado", "Paid"), "unpaid" to tr("No pagado", "Unpaid"))) {
            prefs.edit().putBoolean(Prefs.LUNCH_PAID, prefs.getString(Prefs.LUNCH_PAID + "_s", "unpaid") != "unpaid").apply()
            WorkDay.config(prefs); WorkdayCenter.refresh(this)
        }
        segmented(tr("Cómo te descuentan", "How it's deducted"), Prefs.LUNCH_RULE, "always",
            listOf("always" to tr("Siempre el fijo", "Always fixed"), "real" to tr("Lo que marque", "What I log"))) { WorkDay.config(prefs); WorkdayCenter.refresh(this) }
        text(tr("Siempre el fijo: cada día que trabajas se descuenta el tiempo fijo, aunque sea un día corto o tomes más o menos.\n" +
            "Lo que marque: se descuenta lo que marcaste con 🍔 y ↩️ (si no marcas, el tiempo fijo en días de 5 horas o más).\nPagado: no se descuenta nada, solo se anota.",
            "Always fixed: every day you work the fixed time is deducted, even on short days or if you take more or less.\n" +
            "What I log: what you logged with 🍔 and ↩️ is deducted (if you don't, the fixed time on 5+ hour days).\nPaid: nothing is deducted, it's just logged."), 12f, faded = true)

        card(tr("Tus botones", "Your buttons"), Icons.TRUCK)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list, LinearLayout.LayoutParams(-1, -2))
        button(tr("Restaurar los de fábrica", "Restore defaults")) {
            enabled.clear(); enabled.addAll(WorkDay.ALL_KINDS); names.clear(); save(); refresh()
        }
        setContentView(sv)
        refresh()
    }

    private lateinit var iconRow: LinearLayout

    private fun paintIcons() {
        iconRow.removeAllViews()
        val cur = prefs.getString(Prefs.WORKDAY_ICON, "truck") ?: "truck"
        val names = mapOf(
            "truck" to tr("Camioneta", "Truck"), "clock" to tr("Reloj", "Clock"), "stopwatch" to tr("Cronómetro", "Stopwatch"),
            "briefcase" to tr("Maletín", "Briefcase"), "calendar" to tr("Calendario", "Calendar"), "hardhat" to tr("Casco", "Hard hat"),
            "building" to tr("Edificio", "Building"),
        )
        for (id in WorkDay.ICONS) {
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            val sel = id == cur
            val iv = IconView(this, WorkDay.iconKind(id), 11f)
            iv.isClickable = false
            iv.color = if (sel) 0xFF0B1020.toInt() else TEXT
            iv.bgColor = if (sel) CYAN else CARD
            iv.background = if (sel) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(CYAN, VIOLET)).apply { shape = GradientDrawable.OVAL }
                else GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(BG); setStroke(dp(1), LINE) }
            box.addView(iv, LinearLayout.LayoutParams(dp(54), dp(54)))
            val t = TextView(this)
            t.text = names[id]
            t.textSize = 11f
            t.gravity = Gravity.CENTER
            t.setTextColor(if (sel) TEXT else SUB)
            box.addView(t, LinearLayout.LayoutParams(dp(70), -2).apply { topMargin = dp(4) })
            box.setOnClickListener {
                prefs.edit().putString(Prefs.WORKDAY_ICON, id).apply()
                WorkDay.config(prefs)
                WorkdayCenter.refresh(this)
                paintIcons()
            }
            iconRow.addView(box, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        }
    }

    private fun minText(m: Int): String = when {
        m == 0 -> tr("nada", "none")
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60} h"
        else -> "${m / 60} h ${m % 60} min"
    }

    /** Escribes cuántos minutos te descuentan de almuerzo. */
    private fun askLunchMinutes(cur: Int) {
        val box = EditText(this).apply {
            setText(cur.toString())
            setSelection(text.length)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            textSize = 18f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            hint = tr("Minutos (ej. 20)", "Minutes (e.g. 20)")
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
        }
        val wrap = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(box, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(this)
            .setTitle(tr("¿Cuántos minutos te descuentan?", "How many minutes are deducted?"))
            .setMessage(tr("Escribe los minutos de almuerzo (0 = nada, 90 = 1 hora y media).", "Type the lunch minutes (0 = none, 90 = an hour and a half)."))
            .setView(wrap)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val m = box.text.toString().trim().toIntOrNull()
                if (m == null || m < 0 || m > 240) { toast("Escribe un número de 0 a 240.", "Type a number from 0 to 240."); return@setPositiveButton }
                prefs.edit().putString(Prefs.LUNCH_AUTO, m.toString()).apply()
                WorkDay.config(prefs)
                WorkdayCenter.refresh(this)
                recreate()
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
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
