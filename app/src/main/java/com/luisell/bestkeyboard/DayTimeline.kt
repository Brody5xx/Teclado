package com.luisell.bestkeyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * 🕒 La línea de tiempo del día: cada tramo (🛻 manejando, 📍 en un sitio, 🍽️ almuerzo) con su hora de inicio
 * y fin, cuánto duró y cuánto ganaste en él. Usa la misma cuenta que «Tu cheque» (el almuerzo que te descuentan
 * sale del tramo más largo en un sitio), así que los totales cuadran con el cheque.
 */
object DayTimeline {
    const val DRIVE = "drive"
    const val SITE = "site"
    const val LUNCH = "lunch"

    /**
     * Un tramo. [paidMs] = lo que se paga (sin el almuerzo descontado); [cutMs] = el almuerzo que se le quitó.
     * [startEv] / [endEv] = la hora del evento que lo empieza y lo termina (para cambiarla con un toque).
     */
    class Seg(val kind: String, val start: Long, val end: Long, val site: String, var paidMs: Long, var cutMs: Long = 0L,
              val rate: Double = 0.0, val startEv: Long = -1L, val endEv: Long = -1L) {
        val ms get() = maxOf(0L, end - start)
        val money get() = paidMs / 3_600_000.0 * rate
    }

    class Day(val segs: List<Seg>, val driveMs: Long, val siteMs: Long, val cutMs: Long, val lunchAuto: Boolean, val money: Double, val running: Boolean)

    fun day(p: SharedPreferences, ev: List<WorkDay.Event>, now: Long = System.currentTimeMillis()): Day? {
        if (ev.isEmpty()) return null
        val cfg = Pay.config(p)
        val rates = SiteStore.all(p).associate { it.name to it.rate }
        val running = WorkDay.isRunning(ev)
        val end = if (running) now else WorkDay.lastMain(ev)?.time ?: ev.last().time
        val main = ev.filter { !WorkDay.isBreak(it.kind) }
        val segs = ArrayList<Seg>()
        // Los tramos: entre cada evento principal, manejando o en un sitio
        var curStart = main.firstOrNull()?.time ?: return null
        var curEv = main.first().time
        var atSite: String? = null
        var atRate = cfg.site
        // Si el día empezó llegando a un sitio
        main.first().let { f -> if (f.kind == WorkDay.ARRIVE) { atSite = f.site.ifEmpty { L.t("Sitio", "Site") }; atRate = rates[f.site]?.takeIf { it > 0 } ?: cfg.site } }
        for (e in main.drop(1) + listOfNotNull(if (running) WorkDay.Event(end, "_now") else null)) {
            if (e.time > curStart) {
                if (atSite != null) segs.add(Seg(SITE, curStart, e.time, atSite, e.time - curStart, rate = atRate, startEv = curEv, endEv = if (e.kind == "_now") -1L else e.time))
                else segs.add(Seg(DRIVE, curStart, e.time, "", e.time - curStart, rate = cfg.drive, startEv = curEv, endEv = if (e.kind == "_now") -1L else e.time))
            }
            when (e.kind) {
                WorkDay.ARRIVE -> { if (atSite == null) { atSite = e.site.ifEmpty { L.t("Sitio", "Site") }; atRate = rates[e.site]?.takeIf { it > 0 } ?: cfg.site } }
                WorkDay.LEAVE -> atSite = null
            }
            curStart = e.time; curEv = e.time
        }
        // 🍽️ El almuerzo marcado: se muestra como su propio tramo (encima del que tocaba)
        val lunches = ArrayList<Seg>()
        var lunchAt = -1L
        for (e in ev) when (e.kind) {
            WorkDay.LUNCH -> if (lunchAt < 0) lunchAt = e.time
            WorkDay.BACK -> if (lunchAt >= 0) { lunches.add(Seg(LUNCH, lunchAt, e.time, "", 0L, startEv = lunchAt, endEv = e.time)); lunchAt = -1 }
        }
        if (lunchAt >= 0) lunches.add(Seg(LUNCH, lunchAt, end, "", 0L, startEv = lunchAt))
        // Lo que se descuenta (igual que el cheque): del tramo más largo en un sitio; si no hubo sitio, del manejo
        val sum = WorkDay.summary(ev, now)
        var cut = sum.deducted
        val siteSegs = segs.filter { it.kind == SITE }
        if (cut > 0 && siteSegs.isNotEmpty()) {
            val s = siteSegs.maxByOrNull { it.ms }!!
            val take = minOf(cut, s.paidMs)
            s.paidMs -= take; s.cutMs += take; cut -= take
        }
        if (cut > 0) for (s in segs.filter { it.kind == DRIVE }.sortedByDescending { it.ms }) {
            val take = minOf(cut, s.paidMs); s.paidMs -= take; s.cutMs += take; cut -= take
            if (cut <= 0) break
        }
        val all = (segs + lunches).sortedBy { it.start }
        return Day(all, segs.filter { it.kind == DRIVE }.sumOf { it.paidMs }, segs.filter { it.kind == SITE }.sumOf { it.paidMs },
            sum.deducted, sum.lunchAuto, segs.sumOf { it.money }, running)
    }

    /** ⏱️ Horas por sitio entre [from] y [to]: (sitio → ms pagados) y el manejo, con el dinero. */
    class Totals(val bySite: List<Triple<String, Long, Double>>, val driveMs: Long, val driveMoney: Double, val days: Int)

    fun totals(p: SharedPreferences, from: Long, to: Long): Totals {
        val map = LinkedHashMap<String, Pair<Long, Double>>()
        var drive = 0L; var driveMoney = 0.0; var days = 0
        for ((day, ev) in WorkDay.byDay(p)) {
            if (day < from || day >= to) continue
            val d = day(p, ev) ?: continue
            days++
            for (s in d.segs) when (s.kind) {
                SITE -> { val o = map[s.site] ?: (0L to 0.0); map[s.site] = (o.first + s.paidMs) to (o.second + s.money) }
                DRIVE -> { drive += s.paidMs; driveMoney += s.money }
            }
        }
        return Totals(map.entries.map { Triple(it.key, it.value.first, it.value.second) }.sortedByDescending { it.second }, drive, driveMoney, days)
    }

    fun colorOf(kind: String) = when (kind) { DRIVE -> 0xFF22D3EE.toInt(); SITE -> 0xFF8B5CF6.toInt(); else -> 0xFFFBBF24.toInt() }

    /**
     * La barra de colores del día (de la primera a la última hora), con las horas abajo.
     * Al abrirse se dibuja de izquierda a derecha y después un brillo la recorre.
     */
    class Bar(ctx: Context, private val segs: List<Seg>) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val start = android.os.SystemClock.uptimeMillis()

        override fun onDraw(c: Canvas) {
            if (segs.isEmpty()) return
            val t0 = segs.minOf { it.start }; val t1 = segs.maxOf { it.end }
            val span = maxOf(1L, t1 - t0).toFloat()
            val w = width.toFloat(); val barH = 18f * d; val top = 4f * d
            val rect = RectF(0f, top, w, top + barH)
            val t = (android.os.SystemClock.uptimeMillis() - start).toFloat()
            val grow = (t / 700f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) * (1f - it) }
            // Fondo
            p.style = Paint.Style.FILL; p.color = 0xFF1A2236.toInt()
            c.drawRoundRect(rect, barH / 2, barH / 2, p)
            c.save()
            val clip = android.graphics.Path().apply { addRoundRect(RectF(0f, top, w * grow, top + barH), barH / 2, barH / 2, android.graphics.Path.Direction.CW) }
            c.clipPath(clip)
            // Primero manejando y sitios, encima el almuerzo
            for (pass in 0..1) for (s in segs) {
                if ((s.kind == LUNCH) != (pass == 1)) continue
                val x0 = (s.start - t0) / span * w; val x1 = (s.end - t0) / span * w
                p.color = colorOf(s.kind)
                c.drawRect(x0, if (s.kind == LUNCH) top + barH * 0.2f else top, maxOf(x0 + 2f * d, x1), if (s.kind == LUNCH) top + barH * 0.8f else top + barH, p)
            }
            // El brillo que la recorre cada 3 s
            if (grow >= 1f) {
                val k = ((t - 700f) % 3000f) / 1400f
                if (k < 1f) {
                    val gw = 40f * d; val gx = -gw + (w + 2 * gw) * k
                    p.shader = LinearGradient(gx - gw, 0f, gx + gw, 0f, intArrayOf(0x00FFFFFF, 0x88FFFFFF.toInt(), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                    c.drawRect(0f, top, w, top + barH, p)
                    p.shader = null
                }
            }
            c.restore()
            // Las horas abajo: inicio, fin y cada hora en punto
            p.color = 0xFF94A3B8.toInt(); p.textSize = 10.5f * d
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = t0; set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0); add(java.util.Calendar.HOUR_OF_DAY, 1) }
            val hours = maxOf(1L, (t1 - t0) / 3_600_000L)
            val every = when { hours > 10 -> 3; hours > 5 -> 2; else -> 1 }
            while (cal.timeInMillis < t1) {
                val x = (cal.timeInMillis - t0) / span * w
                p.color = 0x5594A3B8; c.drawRect(x - 0.5f * d, top + barH + 2f * d, x + 0.5f * d, top + barH + 6f * d, p)
                val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
                if (h % every == 0 && x > 36f * d && x < w - 36f * d) {
                    p.color = 0xFF94A3B8.toInt()
                    val lb = (if (h % 12 == 0) 12 else h % 12).toString() + if (h < 12) "a" else "p"
                    c.drawText(lb, x - p.measureText(lb) / 2, top + barH + 18f * d, p)
                }
                cal.add(java.util.Calendar.HOUR_OF_DAY, 1)
            }
            p.color = 0xFFE2E8F0.toInt()
            c.drawText(WorkStore.time(t0), 0f, top + barH + 18f * d, p)
            val et = WorkStore.time(t1)
            c.drawText(et, w - p.measureText(et), top + barH + 18f * d, p)
            postInvalidateOnAnimation()
        }

        override fun onMeasure(wm: Int, hm: Int) = setMeasuredDimension(MeasureSpec.getSize(wm), (48 * d).toInt())
    }
}
