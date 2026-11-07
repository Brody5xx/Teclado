package com.luisell.bestkeyboard

import android.content.SharedPreferences

/** 🌍 Los idiomas del teclado: eliges 2 (por ejemplo español e inglés) y el teclado cambia solo entre ellos. */
object Langs {
    class Lang(val code: String, val name: String, val voice: String)

    val ALL = listOf(
        Lang("es", "Español", "es-US"),
        Lang("en", "English", "en-US"),
        Lang("pt", "Português", "pt-BR"),
        Lang("fr", "Français", "fr-FR"),
        Lang("it", "Italiano", "it-IT"),
        Lang("de", "Deutsch", "de-DE"),
    )

    fun of(code: String?): Lang = ALL.firstOrNull { it.code == code } ?: ALL[0]
    fun name(code: String?) = of(code).name
    fun voice(code: String?) = of(code).voice

    /** Tus 2 idiomas (el primero y el segundo). */
    fun pair(p: SharedPreferences): Pair<String, String> {
        val a = of(p.getString(Prefs.KB_LANG1, "es")).code
        var b = of(p.getString(Prefs.KB_LANG2, "en")).code
        if (b == a) b = if (a == "en") "es" else "en"
        return a to b
    }

    /** El otro de tus dos idiomas. */
    fun other(p: SharedPreferences, lang: String): String { val (a, b) = pair(p); return if (lang == a) b else a }
}
