package com.luisell.bestkeyboard

import android.content.SharedPreferences

/**
 * 📊 Cuánto te ayuda el teclado: palabras que adivinó, letras que te ahorraste y errores que corrigió.
 * Se cuenta solo en tu teléfono (esta semana y desde siempre).
 */
object Stats {
    const val WORDS = "words"        // palabras escritas
    const val PREDICTED = "pred"     // palabras que adivinó antes de que escribieras una letra
    const val SAVED = "saved"        // letras que no tuviste que escribir
    const val FIXED = "fixed"        // errores que corrigió solo

    private fun week(): Int {
        val c = java.util.Calendar.getInstance()
        return c.get(java.util.Calendar.YEAR) * 100 + c.get(java.util.Calendar.WEEK_OF_YEAR)
    }

    private val pending = HashMap<String, Int>()
    private var lastFlush = 0L

    fun add(prefs: SharedPreferences, what: String, n: Int = 1) {
        if (n <= 0) return
        pending[what] = (pending[what] ?: 0) + n
        // Se guarda cada tanto (no en cada tecla) para no gastar batería
        val now = System.currentTimeMillis()
        if (now - lastFlush > 15_000L) flush(prefs)
    }

    fun flush(prefs: SharedPreferences) {
        lastFlush = System.currentTimeMillis()
        if (pending.isEmpty()) return
        val w = week()
        val e = prefs.edit()
        if (prefs.getInt("stat_week", 0) != w) {
            e.putInt("stat_week", w)
            for (k in listOf(WORDS, PREDICTED, SAVED, FIXED)) e.putInt("stat_w_$k", 0)
        }
        val sameWeek = prefs.getInt("stat_week", 0) == w
        for ((k, v) in pending) {
            e.putInt("stat_w_$k", (if (sameWeek) prefs.getInt("stat_w_$k", 0) else 0) + v)
            e.putInt("stat_t_$k", prefs.getInt("stat_t_$k", 0) + v)
        }
        e.apply()
        pending.clear()
    }

    /** (esta semana, desde siempre) */
    fun get(prefs: SharedPreferences, what: String): Pair<Int, Int> {
        val wk = if (prefs.getInt("stat_week", 0) == week()) prefs.getInt("stat_w_$what", 0) else 0
        return wk to prefs.getInt("stat_t_$what", 0)
    }
}
