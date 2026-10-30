package com.luisell.bestkeyboard

import android.os.Handler
import android.os.Looper
import android.service.textservice.SpellCheckerService
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo

/**
 * Corrector ortográfico de Android: las apps (Gmail, Mensajes, notas...) le preguntan qué
 * palabras están mal, las subrayan en rojo y al tocarlas muestran nuestras correcciones.
 * Revisa español E inglés a la vez, con los mismos diccionarios del teclado, sin internet.
 */
class BestSpellChecker : SpellCheckerService() {

    override fun onCreate() {
        super.onCreate()
        Handler(Looper.getMainLooper()).post { WordStore.load(this) }
    }

    override fun createSession(): Session = BestSession()

    private inner class BestSession : Session() {

        override fun onCreate() {}

        private fun lang(): String {
            val l = locale ?: ""
            if (l.startsWith("es")) return "es"
            if (l.startsWith("en")) return "en"
            return Prefs.get(this@BestSpellChecker).getString(Prefs.LANG, "es") ?: "es"
        }

        override fun onGetSuggestions(textInfo: TextInfo?, suggestionsLimit: Int): SuggestionsInfo {
            val word = textInfo?.text ?: ""
            val eng = WordStore.engine
            // Mientras carga, o si algo falla, no marcamos nada (mejor que marcar de más)
            if (eng == null || word.isBlank()) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOf())
            }
            if (!Prefs.get(this@BestSpellChecker).getBoolean(Prefs.SPELL, true)) {
                return SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOf())
            }
            return try {
                val r = eng.spellCheck(word, lang(), suggestionsLimit.coerceIn(1, 6))
                if (!r.misspelled) {
                    SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOf())
                } else {
                    var attrs = SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
                    if (r.suggestions.isNotEmpty()) attrs = attrs or SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS
                    SuggestionsInfo(attrs, r.suggestions.toTypedArray())
                }
            } catch (_: Throwable) {
                SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, arrayOf())
            }
        }

        override fun onGetSuggestionsMultiple(
            textInfos: Array<out TextInfo>?, suggestionsLimit: Int, sequentialWords: Boolean,
        ): Array<SuggestionsInfo> {
            val infos = textInfos ?: return arrayOf()
            return Array(infos.size) { i ->
                val r = onGetSuggestions(infos[i], suggestionsLimit)
                r.setCookieAndSequence(infos[i].cookie, infos[i].sequence)
                r
            }
        }
    }
}
