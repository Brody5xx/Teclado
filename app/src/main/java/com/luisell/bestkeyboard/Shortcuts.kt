package com.luisell.bestkeyboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** ⚡ Atajos de texto: escribes "/ok" + espacio y se cambia por la frase completa que guardaste. */
object Shortcuts {
    class Item(val code: String, val text: String)

    /** Los de ejemplo (la primera vez). */
    private fun defaults(): List<Item> = listOf(
        Item("/ok", L.t("Ok, perfecto. Gracias.", "Ok, perfect. Thanks.")),
        Item("/voy", L.t("Voy en camino, llego en unos minutos.", "On my way, I'll be there in a few minutes.")),
        Item("/llegue", L.t("Ya llegué.", "I'm here.")),
        Item("/gracias", L.t("Muchas gracias, que tengas un buen día.", "Thank you very much, have a great day.")),
        Item("/omw", "On my way, I'll be there in a few minutes."),
    )

    fun all(p: SharedPreferences): List<Item> {
        val raw = p.getString(Prefs.SHORTCUTS, null) ?: return defaults()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { val o = a.getJSONObject(it); Item(o.optString("c"), o.optString("t")) }
                .filter { it.code.length > 1 && it.text.isNotEmpty() }
        } catch (_: Exception) { defaults() }
    }

    private fun save(p: SharedPreferences, list: List<Item>) {
        val a = JSONArray()
        for (i in list) a.put(JSONObject().put("c", i.code).put("t", i.text))
        p.edit().putString(Prefs.SHORTCUTS, a.toString()).apply()
    }

    /** "ok" o "/OK" → "/ok". */
    fun clean(code: String): String {
        val c = code.trim().lowercase().replace(Regex("[^\\p{L}\\p{N}_]"), "")
        return if (c.isEmpty()) "" else "/$c"
    }

    fun add(p: SharedPreferences, code: String, text: String) {
        val c = clean(code)
        if (c.isEmpty() || text.isBlank()) return
        save(p, all(p).filter { it.code != c } + Item(c, text.trim()))
    }

    fun remove(p: SharedPreferences, code: String) = save(p, all(p).filter { it.code != code })

    fun find(p: SharedPreferences, code: String): String? =
        all(p).firstOrNull { it.code == code.lowercase() }?.text ?: BUILT_IN[code.lowercase()]

    /** 📅 Los que siempre están: la fecha y la hora de ahora. */
    private val BUILT_IN = mapOf(
        "/hoy" to "{fecha}", "/fecha" to "{fecha}", "/hora" to "{hora}", "/ahora" to "{fecha}, {hora}",
        "/today" to "{fecha}", "/date" to "{fecha}", "/time" to "{hora}", "/now" to "{fecha}, {hora}",
    )

    /**
     * Pone la fecha, la hora y el día ({fecha}, {hora}, {dia}) y quita los corchetes de lo que hay que llenar:
     * «Llego en [10] minutos» → «Llego en 10 minutos», con el «10» seleccionado para cambiarlo.
     * Devuelve (el texto, dónde empieza y termina lo que queda seleccionado o null).
     */
    fun render(text: String, english: Boolean): Pair<String, IntRange?> {
        val loc = if (english) java.util.Locale.US else java.util.Locale("es")
        val now = java.util.Date()
        val fecha = java.text.DateFormat.getDateInstance(java.text.DateFormat.LONG, loc).format(now)
        val hora = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT, loc).format(now)
        val dia = java.text.SimpleDateFormat("EEEE", loc).format(now)
        var t = text
        for ((k, v) in listOf("{fecha}" to fecha, "{date}" to fecha, "{hora}" to hora, "{time}" to hora, "{dia}" to dia, "{día}" to dia, "{day}" to dia))
            t = t.replace(k, v, ignoreCase = true)
        var sel: IntRange? = null
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            val close = if (c == '[') t.indexOf(']', i + 1) else -1
            if (close > i) {
                val inner = t.substring(i + 1, close)
                if (sel == null) sel = sb.length until sb.length + inner.length
                sb.append(inner)
                i = close + 1
            } else { sb.append(c); i++ }
        }
        return sb.toString() to sel
    }

    private val TAIL = Regex("(?:^|\\s)(/[\\p{L}\\p{N}_]+)$")

    /** Si lo que está antes del cursor termina en un atajo, devuelve (atajo, frase). */
    fun match(p: SharedPreferences, before: CharSequence?): Pair<String, String>? {
        if (before.isNullOrEmpty()) return null
        val m = TAIL.find(before) ?: return null
        val code = m.groupValues[1]
        val text = find(p, code) ?: return null
        return code to text
    }
}
