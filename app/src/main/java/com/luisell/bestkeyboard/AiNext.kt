package com.luisell.bestkeyboard

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * ✨ Sugerencia con IA (opcional, apagada de fábrica): cuando haces una pausa al escribir, la IA gratis
 * (Pollinations) lee tu frase y propone cómo seguirla (2 a 6 palabras). Sale a la derecha de las sugerencias.
 * Solo se envía lo que llevas escrito en ESA frase; nunca en incógnito ni en contraseñas.
 */
object AiNext {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val seq = AtomicInteger()
    private val cache = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>?) = size > 60
    }

    fun cached(ctx: String): String? = synchronized(cache) { cache[ctx] }

    /** Pide la continuación de [ctx] (la frase que estás escribiendo). [done] recibe 2-6 palabras o null. */
    fun ask(ctx: String, lang: String, done: (String?) -> Unit) {
        cached(ctx)?.let { done(it); return }
        val my = seq.incrementAndGet()
        io.execute {
            if (seq.get() != my) return@execute // ya escribiste otra cosa
            val language = if (lang == "en") "English" else if (lang == "es") "Spanish" else "the same language as the text"
            val prompt = "Continue the following text with the most likely next 2 to 6 words, in $language, matching the writer's style. " +
                "Reply with ONLY those words: no quotes, no explanation, do not repeat the text.\nText: " + ctx
            val out = try { get(prompt) } catch (_: Exception) { null }
            val clean = out?.let { clean(it, ctx) }
            if (clean != null) synchronized(cache) { cache[ctx] = clean }
            main.post { if (seq.get() == my) done(clean) }
        }
    }

    fun cancel() { seq.incrementAndGet() }

    private fun get(prompt: String): String? {
        val enc = java.net.URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
        val c = URL("https://gen.pollinations.ai/text/$enc?model=openai&seed=7").openConnection() as HttpURLConnection
        c.connectTimeout = 3000; c.readTimeout = 5000
        c.setRequestProperty("User-Agent", "BestKeyboard")
        return try {
            if (c.responseCode !in 200..299) null
            else c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim().takeIf { it.isNotEmpty() && !it.startsWith("{") }
        } finally { c.disconnect() }
    }

    /** Deja solo las palabras: sin comillas, sin repetir lo que ya escribiste, máximo 6. */
    private fun clean(raw: String, ctx: String): String? {
        var t = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        t = t.trim('"', '“', '”', '«', '»', '\'', '`', ' ')
        // Si repitió el final de tu frase, se quita
        val tail = ctx.trimEnd().takeLast(40)
        if (tail.isNotEmpty() && t.startsWith(tail, true)) t = t.substring(tail.length).trim()
        val words = t.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(6)
        if (words.isEmpty() || words.joinToString(" ").length > 60) return null
        return words.joinToString(" ").trimEnd('.', ',', ';')
    }
}
