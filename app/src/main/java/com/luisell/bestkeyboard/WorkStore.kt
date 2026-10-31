package com.luisell.bestkeyboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Un apunte de la libreta o un ticket guardado. */
class WorkItem(val time: Long, val text: String, val num: Int = 0)

/** La libreta del día (apuntes sueltos) y "Mis tickets" (con su número que nunca se reinicia). */
object WorkStore {

    private fun load(p: SharedPreferences, key: String): MutableList<WorkItem> = try {
        val a = JSONArray(p.getString(key, "[]"))
        MutableList(a.length()) {
            val o = a.getJSONObject(it)
            WorkItem(o.optLong("t"), o.optString("x"), o.optInt("n"))
        }
    } catch (_: Exception) { ArrayList() }

    private fun save(p: SharedPreferences, key: String, list: List<WorkItem>) {
        val a = JSONArray()
        for (w in list) a.put(JSONObject().put("t", w.time).put("x", w.text).put("n", w.num))
        p.edit().putString(key, a.toString()).apply()
    }

    fun sameDay(a: Long, b: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = a }
        val c2 = Calendar.getInstance().apply { timeInMillis = b }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    // ---------------------------------------------------------------- Libreta del día

    /** Los apuntes de hoy (los de otros días se borran solos). */
    fun notes(p: SharedPreferences): List<WorkItem> {
        val all = load(p, Prefs.NOTEBOOK)
        val now = System.currentTimeMillis()
        val today = all.filter { sameDay(it.time, now) }
        if (today.size != all.size) save(p, Prefs.NOTEBOOK, today)
        return today
    }

    fun addNote(p: SharedPreferences, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        save(p, Prefs.NOTEBOOK, notes(p) + WorkItem(System.currentTimeMillis(), t))
    }

    fun removeNote(p: SharedPreferences, time: Long) = save(p, Prefs.NOTEBOOK, notes(p).filter { it.time != time })
    fun clearNotes(p: SharedPreferences) = save(p, Prefs.NOTEBOOK, emptyList())

    /** Los apuntes con su hora, listos para que la IA arme el ticket. */
    fun notesText(p: SharedPreferences): String =
        notes(p).joinToString("\n") { "[" + time(it.time) + "] " + it.text }

    // ---------------------------------------------------------------- Mis tickets

    fun tickets(p: SharedPreferences): List<WorkItem> = load(p, Prefs.TICKETS).sortedByDescending { it.time }

    fun nextNumber(p: SharedPreferences): Int {
        val saved = p.getInt(Prefs.TICKET_NEXT, 0)
        if (saved > 0) return saved
        val max = load(p, Prefs.TICKETS).maxOfOrNull { it.num } ?: 0
        return max + 1
    }

    fun setNext(p: SharedPreferences, n: Int) { p.edit().putInt(Prefs.TICKET_NEXT, n.coerceAtLeast(1)).apply() }

    /** Guarda un ticket con el número [num] y deja listo el siguiente. */
    fun addTicket(p: SharedPreferences, num: Int, text: String) {
        val list = load(p, Prefs.TICKETS)
        list.removeAll { it.num == num }
        list.add(WorkItem(System.currentTimeMillis(), text.trim(), num))
        // Solo los últimos 300 (para no llenar el teléfono)
        val keep = list.sortedByDescending { it.time }.take(300)
        save(p, Prefs.TICKETS, keep)
        if (p.getInt(Prefs.TICKET_NEXT, 0) <= num) setNext(p, num + 1)
    }

    fun removeTicket(p: SharedPreferences, num: Int, time: Long) =
        save(p, Prefs.TICKETS, load(p, Prefs.TICKETS).filter { !(it.num == num && it.time == time) })

    fun hasTicketOn(p: SharedPreferences, day: Long): Boolean = load(p, Prefs.TICKETS).any { sameDay(it.time, day) }

    // ---------------------------------------------------------------- Fechas

    fun time(t: Long): String = SimpleDateFormat("h:mm a", Locale.US).format(t)

    /** "Monday, October 12, 2026 — 10/12/2026" (el formato de los tickets). */
    fun ticketDate(t: Long): String =
        SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(t) + " — " + SimpleDateFormat("MM/dd/yyyy", Locale.US).format(t)

    fun shortDate(t: Long, english: Boolean): String =
        if (english) SimpleDateFormat("MMM d, h:mm a", Locale.US).format(t)
        else SimpleDateFormat("d MMM, h:mm a", Locale("es")).format(t)
}
