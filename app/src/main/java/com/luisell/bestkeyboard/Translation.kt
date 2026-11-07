package com.luisell.bestkeyboard

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation as MlTranslation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Traducción español ⇄ inglés con Google ML Kit, DENTRO del teléfono.
 * Solo la primera vez descarga los idiomas (unos 30 MB); después funciona sin internet.
 */
class Translation(private val src: String, private val dst: String) {

    private var translator: Translator? = null
    @Volatile var ready = false
        private set
    private var closed = false

    private fun code(l: String) = when (l) {
        "es" -> TranslateLanguage.SPANISH
        "zh" -> TranslateLanguage.CHINESE
        "pt" -> TranslateLanguage.PORTUGUESE
        "fr" -> TranslateLanguage.FRENCH
        "it" -> TranslateLanguage.ITALIAN
        "de" -> TranslateLanguage.GERMAN
        else -> TranslateLanguage.ENGLISH
    }

    companion object {
        /** Los idiomas del traductor (el chino también: para leer y contestar mensajes en chino). */
        val CODES = listOf("es", "en", "zh", "pt", "fr", "it", "de")

        fun name(l: String) = when (l) {
            "es" -> L.t("Español", "Spanish")
            "zh" -> L.t("Chino", "Chinese")
            "pt" -> L.t("Portugués", "Portuguese")
            "fr" -> L.t("Francés", "French")
            "it" -> L.t("Italiano", "Italian")
            "de" -> L.t("Alemán", "German")
            else -> L.t("Inglés", "English")
        }

        /** El siguiente idioma de la lista (sin repetir [skip]). */
        fun next(cur: String, skip: String): String {
            var i = CODES.indexOf(cur)
            repeat(CODES.size) {
                i = (i + 1) % CODES.size
                if (CODES[i] != skip) return CODES[i]
            }
            return cur
        }

        /** ¿El texto está en chino? (tiene letras chinas). */
        fun isChinese(t: String): Boolean {
            var n = 0
            for (ch in t) if (ch.code in 0x4E00..0x9FFF || ch.code in 0x3400..0x4DBF) { n++; if (n >= 2) return true }
            return false
        }
    }

    /** Prepara el traductor (descarga el idioma si hace falta). */
    fun prepare(onReady: () -> Unit, onError: (String) -> Unit) {
        val t = MlTranslation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(code(src))
                .setTargetLanguage(code(dst))
                .build()
        )
        translator = t
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { if (!closed) { ready = true; onReady() } }
            .addOnFailureListener { if (!closed) onError(it.message ?: "error") }
    }

    fun translate(text: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        val t = translator ?: return
        if (!ready) return
        t.translate(text)
            .addOnSuccessListener { if (!closed) onResult(it) }
            .addOnFailureListener { if (!closed) onError(it.message ?: "error") }
    }

    fun close() {
        closed = true
        translator?.close()
        translator = null
    }
}
