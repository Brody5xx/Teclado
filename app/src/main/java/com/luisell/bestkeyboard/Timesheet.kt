package com.luisell.bestkeyboard

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 📄 Hoja de horas de la semana en PDF (para mandarla por WhatsApp o correo). */
object Timesheet {
    private const val DIR = "share"

    fun shareDir(ctx: Context) = File(ctx.cacheDir, DIR).apply { mkdirs() }

    fun uriOf(name: String): Uri = Uri.parse("content://" + ClipStore.AUTHORITY + "/" + name)

    /** Crea el PDF de la semana que empieza en [weekStart] (lunes 0:00). Devuelve el nombre del archivo. */
    fun build(ctx: Context, weekStart: Long, english: Boolean): String {
        val p = Prefs.get(ctx)
        WorkDay.config(p)
        val loc = if (english) Locale.US else Locale("es")
        fun t(es: String, en: String) = if (english) en else es
        val doc = PdfDocument()
        val W = 792; val H = 612 // carta horizontal
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val c = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = 0xFF111827.toInt() }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = 0xFF4B5563.toInt() }
        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); color = 0xFFFFFFFF.toInt() }
        val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = 0xFF111827.toInt() }
        val bold = Paint(cell).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD1D5DB.toInt(); strokeWidth = 0.7f }

        val df = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val end = weekStart + 6L * 24 * 3600 * 1000
        c.drawText(t("Hoja de horas", "Timesheet"), 36f, 52f, title)
        c.drawText(t("Semana del ", "Week of ") + df.format(weekStart) + t(" al ", " to ") + df.format(end), 36f, 70f, sub)

        val cols = listOf(
            t("Día", "Day") to 92f, t("Comencé", "Start") to 54f, t("Llegué", "Arrival") to 54f, t("Salí", "Departure") to 58f,
            t("Casa", "Home") to 54f, t("Sitio(s)", "Site(s)") to 170f, t("Almuerzo", "Lunch") to 58f,
            t("Total", "Total") to 58f, t("En sitio", "On site") to 58f, t("Extra", "OT") to 64f,
        )
        val x0 = 36f
        var y = 92f
        val rowH = 30f
        fill.color = 0xFF1F2937.toInt()
        c.drawRect(x0, y, x0 + cols.sumOf { it.second.toDouble() }.toFloat(), y + 22f, fill)
        var x = x0
        for ((name, w) in cols) { c.drawText(name, x + 5f, y + 15f, head); x += w }
        y += 22f

        val byDay = WorkDay.byDay(p).toMap()
        val dayFmt = SimpleDateFormat("EEE MM/dd", loc)
        var tot = 0L; var site = 0L; var lunch = 0L
        val now = System.currentTimeMillis()
        for (d in 0 until 7) {
            val cal = Calendar.getInstance().apply { timeInMillis = weekStart; add(Calendar.DAY_OF_YEAR, d) }
            val day = cal.timeInMillis
            val ev = byDay[day] ?: emptyList()
            if (d % 2 == 1) { fill.color = 0xFFF3F4F6.toInt(); c.drawRect(x0, y, x0 + cols.sumOf { it.second.toDouble() }.toFloat(), y + rowH, fill) }
            val vals = ArrayList<String>()
            vals.add(dayFmt.format(day).replaceFirstChar { it.uppercase() })
            if (ev.isEmpty()) {
                repeat(cols.size - 1) { vals.add(if (it == 4) "—" else "") }
            } else {
                val s = WorkDay.summary(ev, if (WorkStore.sameDay(day, now)) now else ev.last().time)
                tot += s.total; site += s.site; lunch += s.deducted
                fun first(k: String) = ev.firstOrNull { it.kind == k }?.let { WorkStore.time(it.time) } ?: ""
                fun last(k: String) = ev.lastOrNull { it.kind == k }?.let { WorkStore.time(it.time) } ?: ""
                vals.add(first(WorkDay.START)); vals.add(first(WorkDay.ARRIVE)); vals.add(last(WorkDay.LEAVE)); vals.add(last(WorkDay.HOME))
                vals.add(ev.map { it.site }.filter { it.isNotEmpty() }.distinct().joinToString(", "))
                vals.add(if (s.deducted > 0) "-" + WorkDay.dur(s.deducted) else if (s.lunch > 0) WorkDay.dur(s.lunch) else "")
                vals.add(WorkDay.dur(s.total)); vals.add(WorkDay.dur(s.site))
                vals.add(WorkDay.overtime(s.total).let { if (it > 0) WorkDay.dur(it) else "" })
            }
            x = x0
            for ((i, v) in vals.withIndex()) {
                val w = cols[i].second
                c.drawText(fit(v, cell, w - 8f), x + 5f, y + 19f, if (i == 0 || i == 7) bold else cell)
                x += w
            }
            y += rowH
            c.drawLine(x0, y, x0 + cols.sumOf { it.second.toDouble() }.toFloat(), y, line)
        }
        // Totales
        y += 8f
        fill.color = 0xFFE0F2FE.toInt()
        c.drawRect(x0, y, x0 + cols.sumOf { it.second.toDouble() }.toFloat(), y + 26f, fill)
        val wkOt = WorkDay.weekOvertime(tot)
        val totals = t("Total de la semana: ", "Week total: ") + WorkDay.dur(tot) +
            "    " + t("En el sitio: ", "On site: ") + WorkDay.dur(site) +
            (if (lunch > 0) "    " + t("Almuerzo descontado: ", "Lunch deducted: ") + WorkDay.dur(lunch) else "") +
            (if (wkOt > 0) "    " + t("Horas extra (más de 40): ", "Overtime (over 40): ") + WorkDay.dur(wkOt) else "")
        c.drawText(totals, x0 + 8f, y + 17f, bold)
        y += 60f
        c.drawText(t("Firma: ______________________________", "Signature: ______________________________"), x0, y, cell)
        c.drawText(t("Hecho con Best Keyboard · ", "Made with Best Keyboard · ") + SimpleDateFormat("MM/dd/yyyy h:mm a", Locale.US).format(now),
            x0, H - 24f, sub)
        doc.finishPage(page)

        // Se borran las hojas viejas y se guarda la nueva
        val dir = shareDir(ctx)
        dir.listFiles()?.filter { it.name.startsWith("timesheet") }?.forEach { it.delete() }
        val name = "timesheet_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(weekStart) + ".pdf"
        File(dir, name).outputStream().use { doc.writeTo(it) }
        doc.close()
        return name
    }

    private fun fit(s: String, p: Paint, w: Float): String {
        if (p.measureText(s) <= w) return s
        var t = s
        while (t.length > 1 && p.measureText("$t…") > w) t = t.dropLast(1)
        return "$t…"
    }

    /** Abre "Compartir" con el PDF (WhatsApp, correo, Drive…). */
    fun share(ctx: Context, name: String, english: Boolean) {
        val i = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uriOf(name))
            .putExtra(Intent.EXTRA_SUBJECT, if (english) "Timesheet" else "Hoja de horas")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(i, if (english) "Send timesheet" else "Enviar hoja de horas")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { ctx.startActivity(chooser) } catch (_: Exception) { }
    }
}
