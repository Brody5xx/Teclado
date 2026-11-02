package com.luisell.bestkeyboard

import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 📅 Calendario del mes: cuántas horas trabajaste cada día. Toca un día para ver el detalle. */
class MonthActivity : UiActivity() {

    private val month: Calendar = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    private lateinit var title: TextView
    private lateinit var totals: TextView
    private lateinit var grid: GridLayout
    private lateinit var detail: LinearLayout
    /** El día que estás viendo (hoy, al abrir). */
    private var chosen = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        WorkDay.config(prefs)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        val sv = ScrollView(this)
        PanelUi.glowScrollbar(sv)
        sv.setBackgroundColor(BG)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(48))
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
        val prev = TextView(this).apply { text = "‹"; textSize = 28f; setTextColor(TEXT); gravity = Gravity.CENTER }
        val next = TextView(this).apply { text = "›"; textSize = 28f; setTextColor(TEXT); gravity = Gravity.CENTER }
        title = TextView(this).apply {
            textSize = 22f; gravity = Gravity.CENTER; setTextColor(TEXT)
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        }
        prev.setOnClickListener { month.add(Calendar.MONTH, -1); chosen = -1; paint() }
        next.setOnClickListener { month.add(Calendar.MONTH, 1); chosen = -1; paint() }
        head.addView(prev, LinearLayout.LayoutParams(dp(44), dp(44)))
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(next, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(head)
        totals = text("", 13f, faded = true)
        totals.gravity = Gravity.CENTER

        // Nombres de los días (lunes primero)
        val names = GridLayout(this).apply { columnCount = 7 }
        val cell = (resources.displayMetrics.widthPixels - dp(28)) / 7
        val dn = if (en) listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") else listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        for (n in dn) names.addView(TextView(this).apply { text = n; textSize = 11f; setTextColor(SUB); gravity = Gravity.CENTER },
            GridLayout.LayoutParams().apply { width = cell; height = dp(24) })
        root.addView(names, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        grid = GridLayout(this).apply { columnCount = 7 }
        root.addView(grid)
        detail = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        setContentView(sv)
        paint()
    }

    // Al volver de cambiar una hora, se vuelve a pintar
    override fun onResume() {
        super.onResume()
        if (::grid.isInitialized) paint()
    }

    private fun paint() {
        title.text = SimpleDateFormat("MMMM yyyy", if (en) Locale.US else Locale("es")).format(month.time).replaceFirstChar { it.uppercase() }
        grid.removeAllViews()
        val cell = (resources.displayMetrics.widthPixels - dp(28)) / 7
        val byDay = WorkDay.byDay(prefs).toMap()
        val now = System.currentTimeMillis()
        val c = month.clone() as Calendar
        val lead = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7 // lunes = 0
        repeat(lead) { grid.addView(View(this), GridLayout.LayoutParams().apply { width = cell; height = cell }) }
        val days = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        var monthMs = 0L
        var worked = 0
        for (d in 1..days) {
            c.set(Calendar.DAY_OF_MONTH, d)
            val day = c.timeInMillis
            val ev = byDay[day] ?: emptyList()
            val ms = if (ev.isEmpty()) 0L else WorkDay.summary(ev, if (WorkStore.sameDay(day, now)) now else ev.last().time).total
            if (ms > 0) { monthMs += ms; worked++ }
            val box = LinearLayout(this)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            val num = TextView(this).apply { text = d.toString(); textSize = 14f; gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD) }
            val hrs = TextView(this).apply { textSize = 10f; gravity = Gravity.CENTER }
            box.addView(num)
            box.addView(hrs)
            val h = ms / 3_600_000.0
            hrs.text = if (ms > 0) String.format(Locale.US, "%.1fh", h) else ""
            // Más horas = color más fuerte
            val strength = (h / 10.0).coerceIn(0.0, 1.0).toFloat()
            val bg = GradientDrawable().apply {
                cornerRadius = dpf(10f)
                if (ms > 0) setColor(mix(CARD, CYAN, 0.18f + 0.6f * strength)) else setColor(CARD)
                if (day == chosen) setStroke(dp(2), VIOLET) else if (WorkStore.sameDay(day, now)) setStroke(dp(1), CYAN)
            }
            box.background = bg
            val dark = ms > 0 && strength > 0.45f
            num.setTextColor(if (dark) 0xFF0B1020.toInt() else TEXT)
            hrs.setTextColor(if (dark) 0xFF0B1020.toInt() else SUB)
            box.setOnClickListener { chosen = day; paint() }
            grid.addView(box, GridLayout.LayoutParams().apply { width = cell - dp(4); height = cell - dp(4); setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        totals.text = tr("Este mes: ", "This month: ") + WorkDay.dur(monthMs) + "  ·  " + worked + tr(" día(s) trabajado(s)", " day(s) worked")
        showDay(byDay)
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        fun ch(x: Int, s: Int) = (x shr s) and 0xFF
        val r = (ch(a, 16) + (ch(b, 16) - ch(a, 16)) * f).toInt()
        val g = (ch(a, 8) + (ch(b, 8) - ch(a, 8)) * f).toInt()
        val bl = (ch(a, 0) + (ch(b, 0) - ch(a, 0)) * f).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun showDay(byDay: Map<Long, List<WorkDay.Event>>) {
        detail.removeAllViews()
        if (chosen < 0) {
            detail.addView(TextView(this).apply {
                text = tr("Toca un día para ver a qué hora llegaste, saliste y dónde estuviste.", "Tap a day to see when you arrived, left and where you were.")
                textSize = 13f; setTextColor(SUB)
            })
            return
        }
        val ev = byDay[chosen] ?: emptyList()
        val head = TextView(this).apply {
            text = SimpleDateFormat("EEEE, MMMM d", if (en) Locale.US else Locale("es")).format(chosen).replaceFirstChar { it.uppercase() }
            textSize = 17f; setTextColor(TEXT); setTypeface(typeface, Typeface.BOLD)
        }
        detail.addView(head)
        if (ev.isEmpty()) {
            detail.addView(TextView(this).apply { text = tr("No trabajaste (o no lo marcaste).", "No work logged."); textSize = 13f; setTextColor(SUB) })
            return
        }
        // Cada cosa del día: tócala para cambiar la hora, o ✕ para borrarla
        for (e in ev) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(12), dp(10), dp(4), dp(10))
            row.background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(12f) }
            val t = TextView(this).apply { text = WorkStore.time(e.time); textSize = 15f; setTextColor(CYAN); setTypeface(typeface, Typeface.BOLD) }
            val n = TextView(this).apply { text = WorkDay.labelOf(e); textSize = 14f; setTextColor(TEXT); setPadding(dp(12), 0, 0, 0) }
            row.addView(t, LinearLayout.LayoutParams(dp(78), -2))
            row.addView(n, LinearLayout.LayoutParams(0, -2, 1f))
            val del = TextView(this).apply { text = "✕"; textSize = 16f; setTextColor(SUB); gravity = Gravity.CENTER }
            del.setOnClickListener {
                android.app.AlertDialog.Builder(this)
                    .setMessage(tr("¿Borrar «${WorkDay.labelOf(e)} · ${WorkStore.time(e.time)}»?", "Delete «${WorkDay.labelOf(e)} · ${WorkStore.time(e.time)}»?"))
                    .setPositiveButton(tr("Borrar", "Delete")) { _, _ -> WorkDay.remove(prefs, e.time); WorkdayCenter.refresh(this); paint() }
                    .setNegativeButton(tr("Cancelar", "Cancel"), null).show()
            }
            row.addView(del, LinearLayout.LayoutParams(dp(40), dp(36)))
            row.setOnClickListener { TimeSetActivity.open(this, eventTime = e.time) }
            detail.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        detail.addView(TextView(this).apply {
            text = tr("Toca una hora para cambiarla.", "Tap a time to change it.")
            textSize = 12f; setTextColor(SUB); setPadding(0, dp(6), 0, 0)
        })
        val now = System.currentTimeMillis()
        detail.addView(TextView(this).apply {
            text = WorkDay.text(ev, en).let { if (WorkStore.sameDay(chosen, now)) it else it }
            textSize = 14f; setTextColor(TEXT)
            setLineSpacing(0f, 1.25f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }
}
