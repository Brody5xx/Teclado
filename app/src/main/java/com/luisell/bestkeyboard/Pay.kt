package com.luisell.bestkeyboard

import android.content.SharedPreferences
import org.json.JSONObject
import java.util.Locale

/**
 * 💵 Cuánto vas a cobrar (un cálculo aproximado, solo en tu teléfono):
 *  - Manejando (camioneta) se paga a una tarifa y en el sitio a otra.
 *  - Algunos sitios pagan más (tarifa especial en "Mis sitios").
 *  - Horas extra (más de 40 en la semana) a tiempo y medio sobre el promedio de lo que ganas por hora.
 *  - Se descuentan el 401(k) %, los taxes % y el seguro de vida (cantidad fija por semana).
 */
object Pay {
    class Config(
        val drive: Double, val site: Double, val otMult: Double,
        val taxPct: Double, val k401Pct: Double, val lifeWeek: Double, val otherWeek: Double,
    ) {
        val isSet get() = drive > 0 || site > 0
    }

    class Result(
        val driveH: Double, val siteH: Double, val specialH: Double, val otH: Double,
        val straight: Double, val otPay: Double, val gross: Double,
        val k401: Double, val tax: Double, val life: Double, val other: Double, val net: Double,
    )

    fun config(p: SharedPreferences): Config {
        val o = try { JSONObject(p.getString(Prefs.PAY, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        return Config(o.optDouble("drive", 0.0), o.optDouble("site", 0.0), o.optDouble("ot", 1.5),
            o.optDouble("tax", 0.0), o.optDouble("k401", 0.0), o.optDouble("life", 0.0), o.optDouble("other", 0.0))
    }

    fun set(p: SharedPreferences, key: String, v: Double) {
        val o = try { JSONObject(p.getString(Prefs.PAY, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        o.put(key, v)
        p.edit().putString(Prefs.PAY, o.toString()).apply()
    }

    /** Horas pagadas de un día: (manejando, en el sitio por tarifa). */
    private fun dayHours(ev: List<WorkDay.Event>, now: Long, rates: Map<String, Double>, cfg: Config): Pair<Double, List<Pair<Double, Double>>> {
        if (ev.isEmpty()) return 0.0 to emptyList()
        val first = ev.first().time
        val end = if (WorkDay.isRunning(ev)) now else WorkDay.lastMain(ev)?.time ?: ev.last().time
        val site = ArrayList<Pair<Long, Double>>() // (ms, tarifa)
        var at = -1L
        var atRate = cfg.site
        for (e in ev) when (e.kind) {
            WorkDay.ARRIVE -> if (at < 0) { at = e.time; atRate = rates[e.site]?.takeIf { it > 0 } ?: cfg.site }
            WorkDay.LEAVE -> if (at >= 0) { site.add((e.time - at) to atRate); at = -1 }
        }
        if (at >= 0) site.add(maxOf(0, end - at) to atRate)
        val total = maxOf(0L, end - first)
        var siteMs = site.sumOf { it.first }
        var driveMs = maxOf(0L, total - siteMs)
        // El almuerzo que te descuentan sale del tiempo en el sitio (del tramo más largo); si no hubo sitio, del manejo
        var cut = WorkDay.summary(ev, now).deducted
        val sites = site.toMutableList()
        if (cut > 0 && sites.isNotEmpty()) {
            val i = sites.indices.maxByOrNull { sites[it].first }!!
            val take = minOf(cut, sites[i].first)
            sites[i] = (sites[i].first - take) to sites[i].second
            cut -= take
        }
        if (cut > 0) driveMs = maxOf(0L, driveMs - cut)
        return (driveMs / 3_600_000.0) to sites.map { (it.first / 3_600_000.0) to it.second }
    }

    /** El pago de la semana que empieza en [weekStart]. */
    fun week(p: SharedPreferences, weekStart: Long): Result {
        val cfg = config(p)
        val rates = SiteStore.all(p).associate { it.name to it.rate }
        val now = System.currentTimeMillis()
        val weekEnd = weekStart + 7L * 24 * 3600 * 1000
        var driveH = 0.0; var siteH = 0.0; var specialH = 0.0; var straight = 0.0
        for ((day, ev) in WorkDay.byDay(p)) {
            if (day < weekStart || day >= weekEnd) continue
            val (dh, sites) = dayHours(ev, now, rates, cfg)
            driveH += dh
            straight += dh * cfg.drive
            for ((h, r) in sites) {
                if (r != cfg.site) specialH += h else siteH += h
                straight += h * r
            }
        }
        val hours = driveH + siteH + specialH
        val otH = maxOf(0.0, hours - WorkDay.WEEK_HOURS)
        // Horas extra: ya se pagaron a la tarifa normal; se suma el extra (0.5) sobre el promedio por hora
        val regular = if (hours > 0) straight / hours else 0.0
        val otPay = otH * regular * (cfg.otMult - 1.0)
        val gross = straight + otPay
        val k401 = gross * cfg.k401Pct / 100.0
        val tax = (gross - k401) * cfg.taxPct / 100.0
        val life = if (gross > 0) cfg.lifeWeek else 0.0
        val other = if (gross > 0) cfg.otherWeek else 0.0
        val net = gross - k401 - tax - life - other
        return Result(driveH, siteH, specialH, otH, straight, otPay, gross, k401, tax, life, other, net)
    }

    fun money(v: Double): String = "$" + String.format(Locale.US, "%,.2f", v)
    fun hrs(h: Double): String = String.format(Locale.US, "%.2f h", h)

    /** El desglose para leerlo en el teclado. */
    fun text(r: Result, cfg: Config): String {
        val sb = StringBuilder()
        if (r.driveH > 0) sb.append(L.t("🛻 Manejando: ", "🛻 Driving: ")).append(hrs(r.driveH)).append(" × ").append(money(cfg.drive)).append("\n")
        if (r.siteH > 0) sb.append(L.t("📍 En el sitio: ", "📍 On site: ")).append(hrs(r.siteH)).append(" × ").append(money(cfg.site)).append("\n")
        if (r.specialH > 0) sb.append(L.t("⭐ Sitios con pago especial: ", "⭐ Special-rate sites: ")).append(hrs(r.specialH)).append("\n")
        if (r.otH > 0) sb.append(L.t("⏰ Horas extra: ", "⏰ Overtime: ")).append(hrs(r.otH)).append(" (+").append(money(r.otPay)).append(")\n")
        sb.append(L.t("Bruto: ", "Gross: ")).append(money(r.gross)).append("\n")
        if (r.k401 > 0) sb.append("401(k) ").append(fmtPct(cfg.k401Pct)).append(": −").append(money(r.k401)).append("\n")
        if (r.tax > 0) sb.append(L.t("Taxes ", "Taxes ")).append(fmtPct(cfg.taxPct)).append(": −").append(money(r.tax)).append("\n")
        if (r.life > 0) sb.append(L.t("Seguro de vida: −", "Life insurance: −")).append(money(r.life)).append("\n")
        if (r.other > 0) sb.append(L.t("Otros descuentos: −", "Other deductions: −")).append(money(r.other)).append("\n")
        sb.append(L.t("💵 Te quedan aprox.: ", "💵 Take-home approx.: ")).append(money(r.net))
        return sb.toString()
    }

    private fun fmtPct(v: Double) = (if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.US, "%.2f", v)) + "%"
}
