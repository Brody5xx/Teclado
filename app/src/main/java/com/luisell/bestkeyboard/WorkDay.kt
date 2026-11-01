package com.luisell.bestkeyboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Mi jornada: 🛻 comencé (en la camioneta) → 📍 llegué al sitio → 🚪 salí del sitio → 🏠 llegué a casa.
 * Calcula las horas totales, en el sitio y manejando, y el total de la semana.
 */
object WorkDay {
    const val START = "start"
    const val ARRIVE = "arrive"
    const val LEAVE = "leave"
    const val HOME = "home"

    /** [site] = el nombre del sitio (de "Mis sitios"), si se sabe. */
    class Event(val time: Long, val kind: String, val site: String = "")
    class Summary(val total: Long, val site: Long, val driving: Long, val running: Boolean)

    fun label(kind: String): String = when (kind) {
        START -> L.t("🛻 Comencé (camioneta)", "🛻 Started (truck)")
        ARRIVE -> L.t("📍 Llegué al sitio", "📍 Arrived on site")
        LEAVE -> L.t("🚪 Salí del sitio", "🚪 Left the site")
        else -> L.t("🏠 Llegué a casa", "🏠 Home")
    }

    /** En inglés, para el ticket. */
    fun ticketLabel(kind: String): String = when (kind) {
        START -> "Start (truck)"
        ARRIVE -> "Arrival Time"
        LEAVE -> "Departure Time"
        else -> "Home"
    }

    private fun all(p: SharedPreferences): MutableList<Event> = try {
        val a = JSONArray(p.getString(Prefs.WORKDAY, "[]"))
        MutableList(a.length()) { val o = a.getJSONObject(it); Event(o.optLong("t"), o.optString("k"), o.optString("s")) }
    } catch (_: Exception) { ArrayList() }

    private fun save(p: SharedPreferences, list: List<Event>) {
        // Solo los últimos 70 días
        val limit = System.currentTimeMillis() - 70L * 24 * 3600 * 1000
        val a = JSONArray()
        for (e in list.filter { it.time > limit }.sortedBy { it.time }) a.put(JSONObject().put("t", e.time).put("k", e.kind).put("s", e.site))
        p.edit().putString(Prefs.WORKDAY, a.toString()).apply()
    }

    fun day(p: SharedPreferences, day: Long = System.currentTimeMillis()): List<Event> =
        all(p).filter { WorkStore.sameDay(it.time, day) }.sortedBy { it.time }

    /** Anota un evento ahora y devuelve su hora. */
    fun add(p: SharedPreferences, kind: String, site: String = ""): Long {
        val t = System.currentTimeMillis()
        save(p, all(p) + Event(t, kind, site))
        return t
    }

    /** Le pone el sitio a un evento (cuando el GPS ya supo dónde estás, o lo elegiste tú). */
    fun setSite(p: SharedPreferences, time: Long, site: String) =
        save(p, all(p).map { if (it.time == time) Event(it.time, it.kind, site) else it })

    /** El nombre que se ve: "📍 Llegué a RUMC". */
    fun labelOf(e: Event): String = if (e.site.isEmpty()) label(e.kind) else when (e.kind) {
        ARRIVE -> L.t("📍 Llegué a ${e.site}", "📍 Arrived at ${e.site}")
        LEAVE -> L.t("🚪 Salí de ${e.site}", "🚪 Left ${e.site}")
        else -> label(e.kind) + " · " + e.site
    }

    fun remove(p: SharedPreferences, time: Long) = save(p, all(p).filter { it.time != time })

    /** Mueve la hora de un evento [minutes] minutos (por si se te olvidó tocarlo a tiempo). */
    fun shift(p: SharedPreferences, time: Long, minutes: Int): Long {
        val nt = time + minutes * 60_000L
        save(p, all(p).map { if (it.time == time) Event(nt, it.kind, it.site) else it })
        return nt
    }

    /** Lo que toca después (para poner ese botón primero). */
    fun nextKind(events: List<Event>): String = when (events.lastOrNull()?.kind) {
        null -> START
        START -> ARRIVE
        ARRIVE -> LEAVE
        LEAVE -> HOME
        else -> START
    }

    fun summary(events: List<Event>, now: Long = System.currentTimeMillis()): Summary {
        if (events.isEmpty()) return Summary(0, 0, 0, false)
        val first = events.first().time
        val running = events.last().kind != HOME
        val end = if (running) now else events.last().time
        var site = 0L
        var arrivedAt = -1L
        for (e in events) {
            when (e.kind) {
                ARRIVE -> if (arrivedAt < 0) arrivedAt = e.time
                LEAVE -> if (arrivedAt >= 0) { site += e.time - arrivedAt; arrivedAt = -1 }
            }
        }
        if (arrivedAt >= 0) site += maxOf(0, end - arrivedAt)
        val total = maxOf(0, end - first)
        return Summary(total, site, maxOf(0, total - site), running)
    }

    /** Todos los días que tienen algo anotado (inicio del día → eventos), del más viejo al más nuevo. */
    fun byDay(p: SharedPreferences): List<Pair<Long, List<Event>>> {
        val map = java.util.TreeMap<Long, MutableList<Event>>()
        for (e in all(p)) {
            val c = Calendar.getInstance().apply { timeInMillis = e.time }
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            map.getOrPut(c.timeInMillis) { ArrayList() }.add(e)
        }
        return map.entries.map { it.key to it.value.sortedBy { e -> e.time } }
    }

    /** El lunes de esta semana a las 0:00. */
    fun weekStart(): Long {
        val c = Calendar.getInstance()
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis > System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, -7)
        return c.timeInMillis
    }

    /** Horas en número (8.25) para la hoja de cálculo. */
    fun hours(ms: Long): String = String.format(java.util.Locale.US, "%.2f", ms / 3_600_000.0)

    /** Archivo CSV (se abre en Excel o Google Sheets) con todos los días guardados. */
    fun csv(p: SharedPreferences): String {
        val df = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dn = java.text.SimpleDateFormat("EEEE", java.util.Locale.US)
        val sb = StringBuilder("Date,Day,Start (truck),Arrival,Departure,Home,Sites,Total hours,On site hours,Driving hours\n")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        for ((day, ev) in byDay(p)) {
            val sum = summary(ev, if (WorkStore.sameDay(day, System.currentTimeMillis())) System.currentTimeMillis() else ev.last().time)
            fun first(k: String) = ev.firstOrNull { it.kind == k }?.let { WorkStore.time(it.time) } ?: ""
            fun last(k: String) = ev.lastOrNull { it.kind == k }?.let { WorkStore.time(it.time) } ?: ""
            val sites = ev.map { it.site }.filter { it.isNotEmpty() }.distinct().joinToString(" / ")
            sb.append(listOf(df.format(day), dn.format(day), first(START), first(ARRIVE), last(LEAVE), last(HOME), q(sites),
                hours(sum.total), hours(sum.site), hours(sum.driving)).joinToString(",")).append("\n")
        }
        return sb.toString()
    }

    /** Total de la semana (lunes a domingo). */
    fun weekTotal(p: SharedPreferences): Long {
        val c = Calendar.getInstance()
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis > System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, -7)
        var sum = 0L
        for (d in 0 until 7) {
            val noon = c.timeInMillis + d * 24L * 3600 * 1000 + 12 * 3600 * 1000
            if (noon > System.currentTimeMillis() + 24L * 3600 * 1000) break
            sum += summary(day(p, noon)).total
        }
        return sum
    }

    fun dur(ms: Long): String {
        val m = ms / 60000
        return "${m / 60}h ${"%02d".format(m % 60)}m"
    }

    /** Resumen listo para escribir (en inglés para el ticket, o en español). */
    fun text(events: List<Event>, english: Boolean): String {
        if (events.isEmpty()) return ""
        val s = summary(events)
        val lines = events.map {
            (if (english) ticketLabel(it.kind) else label(it.kind).drop(2).trim()) + ": " + WorkStore.time(it.time) +
                (if (it.site.isNotEmpty()) " (${it.site})" else "")
        }
        val tot = if (english) "Total: ${dur(s.total)} (on site ${dur(s.site)}, driving ${dur(s.driving)})"
            else "Total: ${dur(s.total)} (en el sitio ${dur(s.site)}, manejando ${dur(s.driving)})"
        return (lines + tot).joinToString("\n")
    }
}
