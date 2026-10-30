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

    private fun code(l: String) = if (l == "es") TranslateLanguage.SPANISH else TranslateLanguage.ENGLISH

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
