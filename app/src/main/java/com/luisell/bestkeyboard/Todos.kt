package com.luisell.bestkeyboard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * ✅ Pendientes con aviso ("llevar la escalera mañana") y 🧰 la checklist de antes de salir.
 * El aviso puede ser a una hora o al llegar a uno de tus sitios.
 */
object Todos {
    class Item(val id: Long, val text: String, val due: Long, val site: String, val done: Boolean)

    const val ACTION_DUE = "com.luisell.bestkeyboard.TODO_DUE"
    const val ACTION_DONE = "com.luisell.bestkeyboard.TODO_DONE"
    const val EXTRA_ID = "id"

    fun all(p: SharedPreferences): List<Item> = try {
        val a = JSONArray(p.getString(Prefs.TODOS, "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it)
            Item(o.optLong("i"), o.optString("t"), o.optLong("d"), o.optString("s"), o.optBoolean("x")) }
    } catch (_: Exception) { emptyList() }

    private fun save(p: SharedPreferences, list: List<Item>) {
        val a = JSONArray()
        // Los hechos se borran solos después de 3 días
        val old = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
        for (i in list.filter { !it.done || it.id > old })
            a.put(JSONObject().put("i", i.id).put("t", i.text).put("d", i.due).put("s", i.site).put("x", i.done))
        p.edit().putString(Prefs.TODOS, a.toString()).apply()
    }

    fun add(p: SharedPreferences, text: String): Long {
        val id = System.currentTimeMillis()
        save(p, all(p) + Item(id, text.trim(), 0L, "", false))
        return id
    }

    fun setDue(ctx: Context, id: Long, due: Long, site: String = "") {
        val p = Prefs.get(ctx)
        save(p, all(p).map { if (it.id == id) Item(it.id, it.text, due, site, it.done) else it })
        schedule(ctx)
    }

    fun toggle(ctx: Context, id: Long) {
        val p = Prefs.get(ctx)
        save(p, all(p).map { if (it.id == id) Item(it.id, it.text, it.due, it.site, !it.done) else it })
        schedule(ctx)
    }

    fun remove(ctx: Context, id: Long) {
        val p = Prefs.get(ctx)
        save(p, all(p).filter { it.id != id })
        schedule(ctx)
    }

    /** "mañana 6:00 AM", "📍 al llegar a Bodega"… */
    fun whenText(i: Item): String = when {
        i.site.isNotEmpty() -> L.t("📍 al llegar a ", "📍 when you arrive at ") + i.site
        i.due > 0 -> "⏰ " + WorkStore.shortDate(i.due, L.english) + " " + WorkStore.time(i.due)
        else -> ""
    }

    /** Programa el aviso del próximo pendiente con hora. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(ctx, 4730, Intent(ctx, TodoReceiver::class.java).setAction(ACTION_DUE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        val next = all(Prefs.get(ctx)).filter { !it.done && it.due > System.currentTimeMillis() }.minByOrNull { it.due } ?: return
        try { am.setWindow(AlarmManager.RTC_WAKEUP, next.due, 60_000L, pi) } catch (_: Exception) { }
    }

    /** Avisa los pendientes que ya tocan. */
    fun fireDue(ctx: Context) {
        val p = Prefs.get(ctx)
        L.set(p.getString(Prefs.LANG, "es") ?: "es")
        val now = System.currentTimeMillis()
        val due = all(p).filter { !it.done && it.due in 1..(now + 60_000L) }
        for (i in due) {
            notifyItem(ctx, i)
            // Ya avisó: se queda en la lista pero sin hora
            save(p, all(p).map { if (it.id == i.id) Item(it.id, it.text, 0L, it.site, it.done) else it })
        }
        schedule(ctx)
    }

    /** Llegaste a un sitio: avisa los pendientes de ese sitio. */
    fun arrivedAt(ctx: Context, site: String) {
        if (site.isEmpty()) return
        val p = Prefs.get(ctx)
        L.set(p.getString(Prefs.LANG, "es") ?: "es")
        for (i in all(p).filter { !it.done && it.site.equals(site, true) }) {
            notifyItem(ctx, i)
            save(p, all(p).map { if (it.id == i.id) Item(it.id, it.text, it.due, "", it.done) else it })
        }
    }

    private fun notifyItem(ctx: Context, i: Item) {
        val done = PendingIntent.getBroadcast(ctx, (i.id % 100000).toInt(), Intent(ctx, TodoReceiver::class.java)
            .setAction(ACTION_DONE).putExtra(EXTRA_ID, i.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        AutoSites.notify(ctx, L.t("✅ Pendiente", "✅ To-do"), i.text, L.t("Hecho", "Done") to done, 4800 + (i.id % 1000).toInt())
    }

    // ---------------------------------------------------------------- 🧰 Checklist antes de salir

    fun checkItems(p: SharedPreferences): List<String> {
        val raw = p.getString(Prefs.CHECKLIST, null)
            ?: return listOf(L.t("Taladro y brocas", "Drill and bits"), L.t("Escalera", "Ladder"), L.t("Ponchadora", "Crimper"),
                L.t("Probador de cable", "Cable tester"), L.t("Cargador del teléfono", "Phone charger"), L.t("Gafas de seguridad", "Safety glasses"))
        return try { val a = JSONArray(raw); (0 until a.length()).map { a.getString(it) } } catch (_: Exception) { emptyList() }
    }

    fun setCheckItems(p: SharedPreferences, list: List<String>) {
        val a = JSONArray(); list.forEach { a.put(it) }
        p.edit().putString(Prefs.CHECKLIST, a.toString()).apply()
    }

    private fun todayKey() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /** Lo que ya marcaste hoy. */
    fun checked(p: SharedPreferences): Set<String> {
        val raw = p.getString(Prefs.CHECK_DONE, "") ?: ""
        if (!raw.startsWith(todayKey() + "|")) return emptySet()
        return raw.substringAfter("|").split("\u0001").filter { it.isNotEmpty() }.toSet()
    }

    fun toggleCheck(p: SharedPreferences, item: String) {
        val s = checked(p).toMutableSet()
        if (!s.add(item)) s.remove(item)
        p.edit().putString(Prefs.CHECK_DONE, todayKey() + "|" + s.joinToString("\u0001")).apply()
    }
}

class TodoReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Todos.ACTION_DUE -> Todos.fireDue(ctx)
            Todos.ACTION_DONE -> {
                val id = intent.getLongExtra(Todos.EXTRA_ID, -1L)
                val p = Prefs.get(ctx)
                if (Todos.all(p).any { it.id == id && !it.done }) Todos.toggle(ctx, id)
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)?.cancel(4800 + (id % 1000).toInt())
            }
            Intent.ACTION_BOOT_COMPLETED -> Todos.schedule(ctx)
        }
    }
}
