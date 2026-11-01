package com.luisell.bestkeyboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Lista de materiales: tocas lo que usaste, pones la cantidad, y se arma el Equipment List del ticket. */
object Materials {

    /** Los de siempre (en inglés, como van en el ticket). */
    val DEFAULTS = listOf(
        "Cat 6 cable (ft)", "RJ45 connectors", "Keystone jacks", "Patch cables", "Patch panel",
        "Maglock", "Card reader", "Electric strike", "Door contact", "REX motion sensor", "Request-to-exit button",
        "Power supply", "Backup battery", "IP camera", "Camera mount", "PoE injector", "Network switch",
        "Fiber optic cable (ft)", "Fiber patch cord", "Fiber connectors", "Junction box", "Conduit (ft)",
        "Wire mold (ft)", "18/2 wire (ft)", "22/4 wire (ft)", "Zip ties", "Screws and anchors",
    )

    fun custom(p: SharedPreferences): List<String> = try {
        val a = JSONArray(p.getString(Prefs.MATERIALS, "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) { emptyList() }

    fun all(p: SharedPreferences): List<String> = (custom(p) + DEFAULTS).distinct()

    fun addCustom(p: SharedPreferences, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val a = JSONArray()
        (listOf(n) + custom(p).filter { !it.equals(n, true) }).forEach { a.put(it) }
        p.edit().putString(Prefs.MATERIALS, a.toString()).apply()
    }

    fun removeCustom(p: SharedPreferences, name: String) {
        val a = JSONArray()
        custom(p).filter { it != name }.forEach { a.put(it) }
        p.edit().putString(Prefs.MATERIALS, a.toString()).apply()
    }

    /** Lo que usaste hoy: material → cantidad (se borra solo al otro día). */
    fun today(p: SharedPreferences): LinkedHashMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        try {
            val o = JSONObject(p.getString(Prefs.MATERIALS_TODAY, "{}"))
            if (!WorkStore.sameDay(o.optLong("day"), System.currentTimeMillis())) return out
            val c = o.optJSONObject("c") ?: return out
            for (k in c.keys()) { val n = c.optInt(k); if (n > 0) out[k] = n }
        } catch (_: Exception) { }
        return out
    }

    private fun saveToday(p: SharedPreferences, m: Map<String, Int>) {
        val c = JSONObject()
        for ((k, v) in m) if (v > 0) c.put(k, v)
        p.edit().putString(Prefs.MATERIALS_TODAY, JSONObject().put("day", System.currentTimeMillis()).put("c", c).toString()).apply()
    }

    fun change(p: SharedPreferences, name: String, delta: Int): Int {
        val m = today(p)
        val n = maxOf(0, (m[name] ?: 0) + delta)
        if (n == 0) m.remove(name) else m[name] = n
        saveToday(p, m)
        return n
    }

    fun set(p: SharedPreferences, name: String, qty: Int) {
        val m = today(p)
        if (qty <= 0) m.remove(name) else m[name] = qty
        saveToday(p, m)
    }

    fun clear(p: SharedPreferences) = saveToday(p, emptyMap())

    /** "Equipment List:\n- Maglock x 1\n- Cat 6 cable (ft) x 150" */
    fun text(p: SharedPreferences): String {
        val m = today(p)
        if (m.isEmpty()) return ""
        return "Equipment List:\n" + m.entries.joinToString("\n") { "- ${it.key} x ${it.value}" }
    }
}
