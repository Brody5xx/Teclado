package com.luisell.bestkeyboard

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import java.util.Locale

/** 🔊 Pronunciar palabras (en inglés o español) con la voz del teléfono. */
object Speak {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: Pair<String, Locale>? = null

    /** Dice [text] en inglés ([english] = true) o en español, un poquito despacio para que se entienda. */
    fun say(ctx: Context, text: String, english: Boolean) {
        val loc = if (english) Locale.US else Locale("es", "US")
        val t = tts
        if (t != null && ready) { speakNow(ctx, t, text, loc); return }
        pending = text to loc
        if (t != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            val p = pending
            pending = null
            val engine = tts
            if (!ready || engine == null) {
                Toast.makeText(ctx.applicationContext, L.t("Tu teléfono no tiene voz instalada (Ajustes → Texto a voz).", "Your phone has no voice installed (Settings → Text-to-speech)."), Toast.LENGTH_LONG).show()
                tts = null
                return@TextToSpeech
            }
            if (p != null) speakNow(ctx, engine, p.first, p.second)
        }
    }

    private fun speakNow(ctx: Context, t: TextToSpeech, text: String, loc: Locale) {
        val r = t.setLanguage(loc)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Si no hay voz en español de EE. UU., prueba la de España/México
            if (loc.language == "es") t.setLanguage(Locale("es"))
        }
        t.setSpeechRate(0.85f)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bk_word")
    }
}
