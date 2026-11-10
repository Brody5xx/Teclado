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
    /** 💵 Tu cheque de la semana (aprox.), debajo del calendario. */
    private lateinit var payBox: LinearLayout
    private lateinit var statsBox: LinearLayout
    /** ⏱️ Horas por sitio: false = esta semana, true = este mes. */
    private var statsMonth = false
    private var payWeeksAgo = 0
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
        head.addView(DiamondView(this), LinearLayout.LayoutParams(dp(24), dp(24))) // 💎 con su luz
        head.addView(next, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(head)
        totals = text("", 13f, faded = true)
        totals.gravity = Gravity.CENTER

        // Nombres de los días (lunes primero)
        val names = GridLayout(this).apply { columnCount = 7 }
        val cell = (resources.displayMetrics.widthPixels - dp(28)) / 7
        // Los días en el idioma de la pantalla (del propio Android: chino 周一, francés lun.…)
        val dn = if (uiCode == "es") listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom") else {
            val ab = android.icu.text.DateFormatSymbols(java.util.Locale(uiCode)).getWeekdays(android.icu.text.DateFormatSymbols.FORMAT, android.icu.text.DateFormatSymbols.ABBREVIATED)
            listOf(2, 3, 4, 5, 6, 7, 1).map { ab[it].trimEnd('.').replaceFirstChar { c -> c.uppercase() } }
        }
        for (n in dn) names.addView(TextView(this).apply { text = n; textSize = 11f; setTextColor(SUB); gravity = Gravity.CENTER },
            GridLayout.LayoutParams().apply { width = cell; height = dp(24) })
        root.addView(names, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(10) })
        grid = GridLayout(this).apply { columnCount = 7 }
        root.addView(grid)
        payBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(22f); setStroke(dp(1), 0x804FD1FF.toInt()) }
        }
        root.addView(payBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        statsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = android.graphics.drawable.GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(22f); setStroke(dp(1), LINE) }
        }
        root.addView(statsBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
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

    private fun paintPay() {
        payBox.removeAllViews()
        val keep = col
        col = payBox
        val cfg = Pay.config(prefs)
        if (!cfg.isSet) {
            text(tr("💵 Tu cheque de la semana", "💵 Your weekly paycheck"), 16f, bold = true)
            text(tr("Pon cuánto ganas por hora (manejando y en el sitio) y aquí te calcula cuánto te queda en el cheque.",
                "Enter how much you make per hour (driving and on site) and it works out your take-home pay here."), 13f, faded = true)
            button(tr("Poner mi pago por hora", "Set my hourly pay"), primary = true) {
                startActivity(android.content.Intent(this, WorkdayButtonsActivity::class.java))
            }
        } else {
            val r = Pay.week(prefs, WorkDay.weekStart(payWeeksAgo))
            text((if (payWeeksAgo == 0) tr("💵 Tu cheque de esta semana (aprox.)", "💵 This week's paycheck (approx.)")
                else tr("💵 Tu cheque de la semana pasada (aprox.)", "💵 Last week's paycheck (approx.)")), 16f, bold = true)
            text(tr("Te quedan aprox. ", "Take-home approx. ") + Pay.money(r.net), 22f, bold = true)
            text(Pay.text(r, cfg), 13f, faded = true)
            button(if (payWeeksAgo == 0) tr("Ver la semana pasada", "See last week") else tr("Ver esta semana", "See this week")) {
                payWeeksAgo = if (payWeeksAgo == 0) 1 else 0; paintPay()
            }
            // 🧾 Comparar con el cheque de verdad
            val ws = WorkDay.weekStart(payWeeksAgo)
            val real = prefs.getFloat("real_check_$ws", -1f)
            if (real >= 0f) {
                val diff = real - r.net
                text(tr("Tu cheque real: ", "Your real paycheck: ") + Pay.money(real.toDouble()) + "  ·  " +
                    (if (Math.abs(diff) < 1.0) tr("cuadra con lo que calculé.", "matches what I calculated.")
                    else tr("diferencia: ", "difference: ") + (if (diff > 0) "+" else "−") + Pay.money(Math.abs(diff))), 13f, bold = true)
                if (Math.abs(diff) >= 5.0) checkHints(ws, diff, cfg)
            }
            button(if (real >= 0f) tr("Cambiar lo que me pagaron", "Change what I was paid") else tr("Comparar con mi cheque real", "Compare with my real paycheck")) { askRealCheck(ws, r.net) }
            button(tr("Cambiar mi pago o descuentos", "Change my pay or deductions")) {
                startActivity(android.content.Intent(this, WorkdayButtonsActivity::class.java))
            }
        }
        col = keep
    }

    /** 🧾 Escribe cuánto te pagaron de verdad (lo que te quedó, el neto) para compararlo. */
    private fun askRealCheck(ws: Long, calc: Double) {
        val f = android.widget.EditText(this).apply {
            hint = tr("Lo que te quedó en el cheque (ej. 850.25)", "Your take-home on the paycheck (e.g. 850.25)")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 16f; setTextColor(TEXT); setHintTextColor(SUB)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
            prefs.getFloat("real_check_$ws", -1f).takeIf { it >= 0f }?.let { setText(String.format(Locale.US, "%.2f", it)) }
        }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(f, LinearLayout.LayoutParams(-1, -2)) }
        panelDialog(tr("Tu cheque real", "Your real paycheck"),
            tr("La app calculó ", "The app calculated ") + Pay.money(calc) + tr(". Escribe lo que te pagaron de verdad y te digo la diferencia (y qué día puede estar mal anotado).",
                ". Type what you were really paid and I'll tell you the difference (and which day may be logged wrong)."), box, listOf(
            PanelBtn(tr("Borrar", "Delete"), 2) { prefs.edit().remove("real_check_$ws").apply(); paintPay(); true },
            PanelBtn(tr("Cancelar", "Cancel"), 1) { true },
            PanelBtn(tr("Guardar", "Save"), 0) {
                val v = f.text.toString().replace(",", "").replace("$", "").trim().toFloatOrNull()
                if (v == null || v < 0f) { toast("Escribe una cantidad, por ejemplo 850.25", "Type an amount, for example 850.25"); return@PanelBtn false }
                prefs.edit().putFloat("real_check_$ws", v).apply(); paintPay(); true
            },
        ))
    }

    /** Si no cuadra: cuántas horas son más o menos, y qué días de esa semana pueden estar mal anotados. */
    private fun checkHints(ws: Long, diff: Double, cfg: Pay.Config) {
        val rate = if (cfg.site > 0) cfg.site else cfg.drive
        val sb = StringBuilder()
        if (rate > 0) {
            // Lo que llega al bolsillo de cada hora (después de 401k y taxes)
            val keep = (1 - cfg.k401Pct / 100.0) * (1 - cfg.taxPct / 100.0)
            val h = Math.abs(diff) / (rate * keep).coerceAtLeast(0.01)
            sb.append(if (diff > 0) tr("Te pagaron más: son como ", "You were paid more: that's about ") else tr("Te pagaron menos: son como ", "You were paid less: that's about "))
                .append(String.format(Locale.US, "%.1f", h)).append(tr(" horas en el sitio.", " hours on site."))
        }
        val weekEnd = ws + 7L * 24 * 3600 * 1000
        val fmt = SimpleDateFormat("EEE d", uiLocale)
        for ((day, ev) in WorkDay.byDay(prefs)) {
            if (day < ws || day >= weekEnd) continue
            val why = ArrayList<String>()
            val sum = WorkDay.summary(ev, ev.last().time)
            if (WorkDay.lastMain(ev)?.kind != WorkDay.finalKind() && !WorkStore.sameDay(day, System.currentTimeMillis())) why.add(tr("no se marcó el fin", "the end wasn't logged"))
            if (ev.count { it.kind == WorkDay.ARRIVE } != ev.count { it.kind == WorkDay.LEAVE } && WorkDay.lastMain(ev)?.kind != WorkDay.ARRIVE) why.add(tr("una llegada sin salida", "an arrival without departure"))
            if (sum.total > 13 * 3600_000L) why.add(tr("más de 13 horas", "more than 13 hours"))
            if (sum.total in 1 until 3 * 3600_000L) why.add(tr("menos de 3 horas", "less than 3 hours"))
            if (ev.none { it.kind == WorkDay.ARRIVE }) why.add(tr("sin tiempo en un sitio (todo cuenta como manejando)", "no time on a site (all counts as driving)"))
            if (why.isNotEmpty()) sb.append("\n• ").append(fmt.format(java.util.Date(day)).replaceFirstChar { it.uppercase() }).append(": ").append(why.joinToString(", "))
        }
        if (sb.isNotEmpty()) text(sb.toString().trim() + "\n" + tr("Toca ese día en el calendario para revisarlo.", "Tap that day on the calendar to check it."), 12.5f, faded = true)
    }

    /** ⏱️ Horas por sitio (esta semana o este mes): cuánto en cada sitio y cuánto manejando, con el dinero. */
    private fun paintStats() {
        statsBox.removeAllViews()
        val keep = col
        col = statsBox
        val cal = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val from = if (statsMonth) cal.timeInMillis else WorkDay.weekStart(0)
        val to = if (statsMonth) cal.apply { add(Calendar.MONTH, 1) }.timeInMillis else from + 7L * 24 * 3600 * 1000
        val t = DayTimeline.totals(prefs, from, to)
        text(tr("Horas por sitio", "Hours by site"), 16f, bold = true)
        // Semana / Mes
        val seg = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, lb) in listOf(tr("Esta semana", "This week"), tr("Este mes", "This month")).withIndex()) {
            val on = (i == 1) == statsMonth
            seg.addView(TextView(this).apply {
                text = lb; textSize = 13.5f; gravity = Gravity.CENTER; setPadding(dp(10), dp(8), dp(10), dp(8))
                setTextColor(if (on) 0xFF0B1020.toInt() else TEXT)
                background = if (on) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(16f) }
                    else GradientDrawable().apply { cornerRadius = dpf(16f); setColor(CARD2); setStroke(dp(1), LINE) }
                setOnClickListener { statsMonth = i == 1; paintStats() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i > 0) leftMargin = dp(8) })
        }
        statsBox.addView(seg, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(6) })
        if (t.days == 0) {
            text(tr("Todavía no hay días trabajados en este tiempo.", "No workdays in this period yet."), 13f, faded = true)
        } else {
            val totalMs = t.bySite.sumOf { it.second } + t.driveMs
            fun row(color: Int, name: String, ms: Long, money: Double) {
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(2)) }
                r.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } }, LinearLayout.LayoutParams(dp(10), dp(10)))
                r.addView(TextView(this).apply { text = name; textSize = 14f; setTextColor(TEXT); setPadding(dp(10), 0, dp(8), 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(TextView(this).apply { text = WorkDay.dur(ms) + (if (money > 0) "  ·  " + Pay.money(money) else ""); textSize = 13.5f; setTextColor(SUB) })
                statsBox.addView(r)
                // La barrita de qué parte del total fue
                statsBox.addView(object : View(this) {
                    override fun onDraw(c: android.graphics.Canvas) {
                        val pp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                        pp.color = 0xFF1A2236.toInt(); c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), height / 2f, height / 2f, pp)
                        pp.color = color; c.drawRoundRect(0f, 0f, width * (ms.toFloat() / maxOf(1L, totalMs)), height.toFloat(), height / 2f, height / 2f, pp)
                    }
                }, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(5); leftMargin = dp(20) })
            }
            row(DayTimeline.colorOf(DayTimeline.DRIVE), tr("Manejando", "Driving"), t.driveMs, t.driveMoney)
            for ((site, ms, money) in t.bySite) row(DayTimeline.colorOf(DayTimeline.SITE), site, ms, money)
            text(tr("Total: ", "Total: ") + WorkDay.dur(totalMs) + tr(" en ${t.days} días (sin el almuerzo descontado).", " over ${t.days} days (lunch deducted)."), 12.5f, faded = true)
        }
        col = keep
    }

    /**
     * 🕒 La línea de tiempo del día: la barra de colores y cada tramo con sus horas, lo que duró y lo que ganaste.
     * Tocas un tramo para cambiar su hora de inicio o de fin.
     */
    private fun timeline(ev: List<WorkDay.Event>): View? {
        val d = DayTimeline.day(prefs, ev) ?: return null
        if (d.segs.isEmpty()) return null
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(16f); setStroke(dp(1), LINE) }
        }
        box.addView(TextView(this).apply { text = tr("Línea de tiempo", "Timeline"); textSize = 15f; setTextColor(TEXT); setTypeface(typeface, Typeface.BOLD) })
        box.addView(DayTimeline.Bar(this, d.segs), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        // La leyenda
        val legend = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        for ((k, lb) in listOf(DayTimeline.DRIVE to tr("Manejando", "Driving"), DayTimeline.SITE to tr("En el sitio", "On site"), DayTimeline.LUNCH to tr("Almuerzo", "Lunch"))) {
            legend.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(DayTimeline.colorOf(k)) } }, LinearLayout.LayoutParams(dp(9), dp(9)).apply { leftMargin = dp(2) })
            legend.addView(TextView(this).apply { text = lb; textSize = 11.5f; setTextColor(SUB); setPadding(dp(5), 0, dp(12), 0) })
        }
        box.addView(legend, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) })
        val payOn = Pay.config(prefs).isSet
        for (sg in d.segs) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = GradientDrawable().apply { setColor(CARD2); cornerRadius = dpf(12f) }
                isClickable = true
            }
            r.addView(View(this).apply { background = GradientDrawable().apply { cornerRadius = dpf(3f); setColor(DayTimeline.colorOf(sg.kind)) } }, LinearLayout.LayoutParams(dp(5), dp(34)))
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, dp(6), 0) }
            val name = when (sg.kind) { DayTimeline.DRIVE -> tr("Manejando", "Driving"); DayTimeline.LUNCH -> tr("Almuerzo", "Lunch"); else -> sg.site }
            texts.addView(TextView(this).apply { text = name; textSize = 14.5f; setTextColor(TEXT); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            texts.addView(TextView(this).apply {
                text = WorkStore.time(sg.start) + " – " + (if (sg.endEv < 0 && sg.kind != DayTimeline.LUNCH && d.running) tr("ahora", "now") else WorkStore.time(sg.end)) +
                    "  ·  " + WorkDay.dur(sg.ms) + (if (sg.cutMs > 0) tr("  (−${WorkDay.dur(sg.cutMs)} de almuerzo)", "  (−${WorkDay.dur(sg.cutMs)} lunch)") else "")
                textSize = 12.5f; setTextColor(SUB)
            })
            r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            if (payOn && sg.kind != DayTimeline.LUNCH) r.addView(TextView(this).apply { text = Pay.money(sg.money); textSize = 14f; setTextColor(CYAN); setTypeface(typeface, Typeface.BOLD) })
            r.setOnClickListener {
                val opts = ArrayList<Pair<String, () -> Unit>>()
                if (sg.startEv > 0) opts.add(tr("Cambiar la hora de inicio (", "Change the start time (") + WorkStore.time(sg.start) + ")" to { TimeSetActivity.open(this, eventTime = sg.startEv) })
                if (sg.endEv > 0) opts.add(tr("Cambiar la hora de fin (", "Change the end time (") + WorkStore.time(sg.end) + ")" to { TimeSetActivity.open(this, eventTime = sg.endEv) })
                if (opts.isEmpty()) return@setOnClickListener
                choiceDialog(name, opts.map { it.first to null }) { w -> opts.getOrNull(w)?.second?.invoke() }
            }
            box.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        // El total del día (igual que el cheque)
        box.addView(TextView(this).apply {
            text = tr("Pagado: ", "Paid: ") + WorkDay.dur(d.driveMs + d.siteMs) + "  (" + tr("manejando ", "driving ") + WorkDay.dur(d.driveMs) + " · " + tr("sitio ", "site ") + WorkDay.dur(d.siteMs) + ")" +
                (if (payOn) "  ·  " + Pay.money(d.money) + tr(" antes de descuentos", " before deductions") else "") +
                (if (d.cutMs > 0) "\n" + tr("Almuerzo descontado: ", "Lunch deducted: ") + WorkDay.dur(d.cutMs) + (if (d.lunchAuto) tr(" (no lo marcaste: se cuenta el tiempo fijo)", " (not logged: the fixed time is counted)") else "") else "")
            textSize = 12.5f; setTextColor(SUB); setLineSpacing(0f, 1.2f); setPadding(dp(2), dp(10), dp(2), 0)
        })
        return box
    }

    private fun paint() {
        try { paintPay() } catch (_: Exception) { }
        try { paintStats() } catch (_: Exception) { }
        title.text = uiDate("MMMMyyyy", month.time)
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

    /**
     * 🗺️ Revisión del día: el mapa con los puntos donde el GPS te ubicó y cada llegada y salida con su hora.
     * Así ves de un vistazo si algo salió mal (y lo corriges tocando la hora abajo).
     */
    private fun dayMap(day: Long, ev: List<WorkDay.Event>): View? {
        val trail = Trail.day(this, day)
        val marks = ArrayList<MapPreview.Mark>()
        for (e in ev) {
            val site = if (e.site.isNotEmpty()) SiteStore.find(prefs, e.site)?.takeIf { !(it.lat == 0.0 && it.lon == 0.0) } else null
            val pos: DoubleArray? = when {
                site != null -> doubleArrayOf(site.lat, site.lon)
                e.kind == WorkDay.HOME -> SiteStore.home(prefs)?.let { doubleArrayOf(it.first, it.second) }
                // Sin sitio: el punto del GPS más cerca de esa hora (máximo 15 minutos)
                else -> trail.minByOrNull { Math.abs(it[3] - e.time) }?.takeIf { Math.abs(it[3] - e.time) < 15 * 60_000.0 }
            }
            if (pos == null) continue
            val color = when (e.kind) { WorkDay.ARRIVE -> CYAN; WorkDay.LEAVE -> VIOLET; else -> 0xFFFBBF24.toInt() }
            marks.add(MapPreview.Mark(pos[0], pos[1], WorkStore.time(e.time), color, e.time, WorkDay.labelOf(e)))
        }
        if (trail.isEmpty() && marks.isEmpty()) return null
        val first = marks.firstOrNull()?.let { doubleArrayOf(it.lat, it.lon) } ?: trail.first()
        val map = MapPreview(this, first[0], first[1])
        // 🧹 Sin el zigzag de cuando estás quieto
        map.path = Trail.simplify(trail)
        map.marks = marks
        // 🔷 Los sitios donde estuviste (y tu casa), para ver si la llegada cayó adentro
        val visited = ev.map { it.site }.filter { it.isNotEmpty() }.distinct().mapNotNull { SiteStore.find(prefs, it) }.filter { !(it.lat == 0.0 && it.lon == 0.0) }
        val homeArea = if (ev.any { it.kind == WorkDay.HOME }) SiteStore.homeSite(prefs) else null
        map.areas = (visited + listOfNotNull(homeArea)).map { MapPreview.Area(it.lat, it.lon, SiteStore.radiusOf(prefs, it), it.shape) }
        // Tocar una marca: qué fue, a qué hora, y cambiarla
        map.onMarkTap = { m ->
            choiceDialog(m.info.ifEmpty { m.label } + " · " + m.label, listOf(tr("Cambiar la hora", "Change the time") to null, tr("Borrar", "Delete") to null)) { w ->
                if (w == 0) TimeSetActivity.open(this, eventTime = m.time)
                else android.app.AlertDialog.Builder(this)
                    .setMessage(tr("¿Borrar «${m.info} · ${m.label}»?", "Delete «${m.info} · ${m.label}»?"))
                    .setPositiveButton(tr("Borrar", "Delete")) { _, _ -> WorkDay.remove(prefs, m.time); WorkdayCenter.refresh(this); paint() }
                    .setNegativeButton(tr("Cancelar", "Cancel"), null).show()
            }
        }
        map.mapLabel = tr("Mapa", "Map"); map.satLabel = tr("Satélite", "Satellite")
        map.clipToOutline = true
        map.background = GradientDrawable().apply { cornerRadius = dpf(16f); setColor(0xFF1B2230.toInt()) }
        return map
    }

    private fun showDay(byDay: Map<Long, List<WorkDay.Event>>) {
        detail.removeAllViews()
        // 📊 Qué tan bien se anotó solo esta semana (y la pasada)
        (SiteLearn.weekReport(prefs) ?: SiteLearn.weekReport(prefs, 1))?.let { r ->
            detail.addView(TextView(this).apply {
                text = r; textSize = 12f; setTextColor(SUB); setLineSpacing(0f, 1.2f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(12f); setStroke(dp(1), LINE) }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        if (chosen < 0) {
            detail.addView(TextView(this).apply {
                text = tr("Toca un día para ver a qué hora llegaste, saliste y dónde estuviste.", "Tap a day to see when you arrived, left and where you were.")
                textSize = 13f; setTextColor(SUB)
            })
            return
        }
        val ev = byDay[chosen] ?: emptyList()
        val head = TextView(this).apply {
            text = if (uiCode == "es" || uiCode == "en") SimpleDateFormat("EEEE, MMMM d", uiLocale).format(chosen).replaceFirstChar { it.uppercase() } else uiDate("EEEEMMMMd", java.util.Date(chosen))
            textSize = 17f; setTextColor(TEXT); setTypeface(typeface, Typeface.BOLD)
        }
        detail.addView(head)
        dayMap(chosen, ev)?.let { detail.addView(it, LinearLayout.LayoutParams(-1, dp(280)).apply { topMargin = dp(8) }) }
        timeline(ev)?.let { detail.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }) }
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
