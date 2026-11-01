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
    /** Almuerzo o descanso (no cuentan en el orden de los botones). */
    const val LUNCH = "lunch"
    const val BACK = "back"

    fun isBreak(kind: String) = kind == LUNCH || kind == BACK

    /** El último evento que no es almuerzo. */
    fun lastMain(events: List<Event>): Event? = events.lastOrNull { !isBreak(it.kind) }

    /** ¿Estás almorzando ahora? */
    fun onBreak(events: List<Event>): Boolean = events.lastOrNull { isBreak(it.kind) }?.kind == LUNCH

    /** ¿El día está en curso (empezaste y no has terminado)? */
    fun isRunning(events: List<Event>): Boolean = events.isNotEmpty() && lastMain(events)?.kind != finalKind()

    // ---- Almuerzo: pagado o no, y cuánto contar si no lo marcas
    var lunchPaid = false
        private set
    var lunchAutoMin = 30
        private set
    /** Horas normales por día (después son horas extra). 0 = sin horas extra. */
    var dayHours = 8
        private set
    const val WEEK_HOURS = 40

    /** Horas extra del día (0 si no hay). */
    fun overtime(total: Long): Long = if (dayHours <= 0) 0L else maxOf(0L, total - dayHours * 3600_000L)

    /** Horas extra de la semana (pasando de 40). */
    fun weekOvertime(total: Long): Long = if (dayHours <= 0) 0L else maxOf(0L, total - WEEK_HOURS * 3600_000L)

    /** true = siempre se descuenta el tiempo fijo, cualquier día y aunque tomes más o menos. */
    var lunchAlways = true
        private set

    /** [site] = el nombre del sitio (de "Mis sitios"), si se sabe. */
    class Event(val time: Long, val kind: String, val site: String = "")
    /** [lunch] = tiempo de almuerzo; [lunchAuto] = no lo marcaste y se contó el tiempo fijo. */
    class Summary(val total: Long, val site: Long, val driving: Long, val running: Boolean,
                  val lunch: Long = 0L, val lunchAuto: Boolean = false, val onLunch: Boolean = false,
                  /** Lo que se descontó de tus horas. */
                  val deducted: Long = 0L)

    /** El nombre de fábrica de cada botón. */
    fun defaultLabel(kind: String): String = when (kind) {
        START -> L.t("🛻 Comencé (camioneta)", "🛻 Started (truck)")
        ARRIVE -> L.t("📍 Llegué al sitio", "📍 Arrived on site")
        LEAVE -> L.t("🚪 Salí del sitio", "🚪 Left the site")
        LUNCH -> L.t("🍔 Salí a almorzar", "🍔 Lunch break")
        BACK -> L.t("↩️ Regresé del almuerzo", "↩️ Back from lunch")
        else -> L.t("🏠 Llegué a casa", "🏠 Home")
    }

    val ALL_KINDS = listOf(START, ARRIVE, LEAVE, HOME)

    // ---- Botones a tu gusto: cuáles se ven y cómo se llaman (se guardan en Prefs.WORKDAY_BTNS)
    private var cfgRaw: String? = null
    private var on: Set<String> = ALL_KINDS.toSet()
    private var names: Map<String, String> = emptyMap()

    /** Lee cómo personalizaste los botones (rápido: solo si cambió). */
    fun config(p: SharedPreferences) {
        icon = p.getString(Prefs.WORKDAY_ICON, "truck") ?: "truck"
        lunchPaid = p.getBoolean(Prefs.LUNCH_PAID, false)
        lunchAutoMin = (p.getString(Prefs.LUNCH_AUTO, "30") ?: "30").toIntOrNull() ?: 30
        lunchAlways = (p.getString(Prefs.LUNCH_RULE, "always") ?: "always") == "always"
        dayHours = (p.getString(Prefs.OVERTIME_DAY, "8") ?: "8").toIntOrNull() ?: 8
        val raw = p.getString(Prefs.WORKDAY_BTNS, "") ?: ""
        if (raw == cfgRaw) return
        cfgRaw = raw
        val o = try { JSONObject(raw.ifEmpty { "{}" }) } catch (_: Exception) { JSONObject() }
        val en = HashSet<String>()
        val nm = HashMap<String, String>()
        for (k in ALL_KINDS) {
            val b = o.optJSONObject(k)
            if (b == null || b.optBoolean("on", true)) en.add(k)
            val n = b?.optString("name", "")?.trim() ?: ""
            if (n.isNotEmpty()) nm[k] = n
        }
        on = if (en.isEmpty()) setOf(ARRIVE) else en
        names = nm
    }

    fun saveConfig(p: SharedPreferences, enabled: Set<String>, custom: Map<String, String>) {
        val o = JSONObject()
        for (k in ALL_KINDS) o.put(k, JSONObject().put("on", k in enabled).put("name", custom[k] ?: ""))
        p.edit().putString(Prefs.WORKDAY_BTNS, o.toString()).apply()
        config(p)
    }

    // ---- El ícono de Mi jornada (camioneta, reloj, maletín…)
    private var icon = "truck"
    val ICONS = listOf("truck", "clock", "stopwatch", "briefcase", "calendar", "hardhat", "building")

    fun iconId() = icon
    fun iconEmoji(id: String = icon) = when (id) {
        "clock" -> "⏰"; "stopwatch" -> "⏱️"; "briefcase" -> "💼"; "calendar" -> "📅"; "hardhat" -> "👷"; "building" -> "🏢"; else -> "🛻"
    }
    fun iconKind(id: String = icon) = when (id) {
        "clock" -> Icons.CLOCK; "stopwatch" -> Icons.STOPWATCH; "briefcase" -> Icons.BRIEFCASE; "calendar" -> Icons.CALENDAR
        "hardhat" -> Icons.HARDHAT; "building" -> Icons.BUILDING; else -> Icons.TRUCK
    }
    fun iconRes(id: String = icon) = when (id) {
        "clock" -> R.drawable.ic_wd_clock; "stopwatch" -> R.drawable.ic_wd_stopwatch; "briefcase" -> R.drawable.ic_wd_briefcase
        "calendar" -> R.drawable.ic_wd_calendar; "hardhat" -> R.drawable.ic_wd_hardhat; "building" -> R.drawable.ic_wd_building
        else -> R.drawable.ic_tile_truck
    }
    /** "🛻 Mi jornada" con el ícono que elegiste. */
    fun title() = iconEmoji() + " " + L.t("Mi jornada", "My workday")

    fun isOn(kind: String) = kind in on
    fun customName(kind: String): String = names[kind] ?: ""

    /** Los botones que usas, en orden. */
    fun enabledKinds(): List<String> = ALL_KINDS.filter { it in on }

    /** El que cierra el día (Casa; si no lo usas, Salí; si tampoco, el último que tengas). */
    fun finalKind(): String = enabledKinds().last()

    /** El nombre que se ve: el tuyo, o el de fábrica. */
    fun label(kind: String): String = (if (isBreak(kind)) null else names[kind]) ?: defaultLabel(kind)

    /** El nombre sin el emoji del principio ("Llegué al sitio"). */
    fun plainLabel(kind: String): String = label(kind).replace(Regex("^[^\\p{L}\\p{N}]+"), "").trim().ifEmpty { label(kind) }

    /** En inglés, para el ticket. */
    fun ticketLabel(kind: String): String = when (kind) {
        START -> "Start (truck)"
        ARRIVE -> "Arrival Time"
        LEAVE -> "Departure Time"
        LUNCH -> "Lunch Start"
        BACK -> "Lunch End"
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

    fun day(p: SharedPreferences, day: Long = System.currentTimeMillis()): List<Event> {
        config(p)
        return all(p).filter { WorkStore.sameDay(it.time, day) }.sortedBy { it.time }
    }

    /** Anota un evento ahora y devuelve su hora. */
    /** Anota un evento a una hora que ya pasó (llegada/salida automática). */
    fun addAt(p: SharedPreferences, kind: String, site: String, time: Long): Long {
        save(p, all(p) + Event(time, kind, site))
        return time
    }

    fun add(p: SharedPreferences, kind: String, site: String = ""): Long {
        val t = System.currentTimeMillis()
        save(p, all(p) + Event(t, kind, site))
        return t
    }

    /** Le pone el sitio a un evento (cuando el GPS ya supo dónde estás, o lo elegiste tú). */
    fun setSite(p: SharedPreferences, time: Long, site: String) =
        save(p, all(p).map { if (it.time == time) Event(it.time, it.kind, site) else it })

    /** El nombre que se ve: "📍 Llegué a Oficina central". */
    fun labelOf(e: Event): String = if (e.site.isEmpty()) label(e.kind) else if (names[e.kind] != null) label(e.kind) + " · " + e.site else when (e.kind) {
        ARRIVE -> L.t("📍 Llegué a ${e.site}", "📍 Arrived at ${e.site}")
        LEAVE -> L.t("🚪 Salí de ${e.site}", "🚪 Left ${e.site}")
        else -> label(e.kind) + " · " + e.site
    }

    /** Le cambia el nombre al sitio en los eventos de hoy ("123 Main St" → "Oficina central"). */
    fun renameSiteToday(p: SharedPreferences, old: String, new: String) {
        val now = System.currentTimeMillis()
        save(p, all(p).map { if (it.site == old && WorkStore.sameDay(it.time, now)) Event(it.time, it.kind, new) else it })
    }

    fun remove(p: SharedPreferences, time: Long) = save(p, all(p).filter { it.time != time })

    /** Mueve la hora de un evento [minutes] minutos (por si se te olvidó tocarlo a tiempo). */
    fun shift(p: SharedPreferences, time: Long, minutes: Int): Long {
        val nt = time + minutes * 60_000L
        save(p, all(p).map { if (it.time == time) Event(nt, it.kind, it.site) else it })
        return nt
    }

    /** Lo que toca después (para poner ese botón primero). */
    fun nextKind(events: List<Event>): String {
        val seq = enabledKinds()
        val last = lastMain(events)?.kind ?: return seq.first()
        val i = seq.indexOf(last)
        return if (i < 0) seq.first() else seq[(i + 1) % seq.size]
    }

    fun summary(events: List<Event>, now: Long = System.currentTimeMillis()): Summary {
        if (events.isEmpty()) return Summary(0, 0, 0, false)
        val first = events.first().time
        val running = isRunning(events)
        val end = if (running) now else lastMain(events)?.time ?: events.last().time
        var site = 0L
        var arrivedAt = -1L
        for (e in events) {
            when (e.kind) {
                ARRIVE -> if (arrivedAt < 0) arrivedAt = e.time
                LEAVE -> if (arrivedAt >= 0) { site += e.time - arrivedAt; arrivedAt = -1 }
            }
        }
        if (arrivedAt >= 0) site += maxOf(0, end - arrivedAt)
        var total = maxOf(0, end - first)
        // Almuerzo marcado: de «Salí a almorzar» a «Regresé» (si no has regresado, cuenta hasta ahora)
        var lunch = 0L
        var lunchAt = -1L
        for (e in events) {
            when (e.kind) {
                LUNCH -> if (lunchAt < 0) lunchAt = e.time
                BACK -> if (lunchAt >= 0) { lunch += e.time - lunchAt; lunchAt = -1 }
            }
        }
        val onLunch = lunchAt >= 0 && running
        if (lunchAt >= 0) lunch += maxOf(0, end - lunchAt)
        // No lo marcaste: si fue un día de 5 horas o más, cuenta el tiempo fijo que elegiste
        var auto = false
        val fixed = lunchAutoMin * 60_000L
        if (lunch == 0L && fixed > 0 && (lunchAlways || total >= 5L * 3600_000)) { lunch = fixed; auto = true }
        // Lo que se descuenta: siempre el tiempo fijo (cualquier día), o lo que marcaste
        val cut = when {
            lunchPaid -> 0L
            lunchAlways -> fixed
            else -> lunch
        }
        if (cut > 0) {
            // No pagado: no cuenta como horas trabajadas
            site = maxOf(0, site - minOf(site, cut))
            total = maxOf(0, total - cut)
        }
        return Summary(total, site, maxOf(0, total - site), running, lunch, auto, onLunch, cut)
    }

    /** Todos los días que tienen algo anotado (inicio del día → eventos), del más viejo al más nuevo. */
    fun byDay(p: SharedPreferences): List<Pair<Long, List<Event>>> {
        config(p)
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
        val sb = StringBuilder("Date,Day,Start (truck),Arrival,Departure,Home,Sites,Total hours,On site hours,Driving hours,Lunch hours\n")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        for ((day, ev) in byDay(p)) {
            val sum = summary(ev, if (WorkStore.sameDay(day, System.currentTimeMillis())) System.currentTimeMillis() else ev.last().time)
            fun first(k: String) = ev.firstOrNull { it.kind == k }?.let { WorkStore.time(it.time) } ?: ""
            fun last(k: String) = ev.lastOrNull { it.kind == k }?.let { WorkStore.time(it.time) } ?: ""
            val sites = ev.map { it.site }.filter { it.isNotEmpty() }.distinct().joinToString(" / ")
            sb.append(listOf(df.format(day), dn.format(day), first(START), first(ARRIVE), last(LEAVE), last(HOME), q(sites),
                hours(sum.total), hours(sum.site), hours(sum.driving), hours(sum.lunch)).joinToString(",")).append("\n")
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
            (if (english) ticketLabel(it.kind) else plainLabel(it.kind)) + ": " + WorkStore.time(it.time) +
                (if (it.site.isNotEmpty()) " (${it.site})" else "")
        }
        val ot = overtime(s.total)
        val tot = (if (english) "Total: ${dur(s.total)} (on site ${dur(s.site)}, driving ${dur(s.driving)})"
            else "Total: ${dur(s.total)} (en el sitio ${dur(s.site)}, manejando ${dur(s.driving)})") +
            (if (ot > 0) (if (english) "\nOvertime: ${dur(ot)}" else "\nHoras extra: ${dur(ot)}") else "")
        val lunch = if (s.lunch <= 0) emptyList() else listOf(
            if (english) "Lunch: ${dur(s.lunch)}" + (if (s.lunchAuto) " (not logged)" else "") + (if (lunchPaid) " (paid)" else " (unpaid, ${dur(s.deducted)} deducted)")
            else "Almuerzo: ${dur(s.lunch)}" + (if (s.lunchAuto) " (no marcado)" else "") + (if (lunchPaid) " (pagado)" else " (no pagado, se descuentan ${dur(s.deducted)})"))
        return (lines + tot + lunch).joinToString("\n")
    }
}
