package com.luisell.bestkeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.rewriting.Rewriter
import com.google.mlkit.genai.rewriting.RewriterOptions
import com.google.mlkit.genai.rewriting.Rewriting
import com.google.mlkit.genai.rewriting.RewritingRequest
import java.util.concurrent.Executor

/**
 * "Reescribir formal": usa la IA de Google que viene DENTRO de algunos teléfonos (Gemini Nano,
 * por medio de la app AICore). No usa internet para lo que escribes. Si el teléfono no la tiene,
 * avisa con un mensaje claro.
 */
object FormalRewriter {
    private var client: Rewriter? = null
    private var clientLang = ""
    private val main = Executor { Handler(Looper.getMainLooper()).post(it) }

    /** Más o menos 256 "tokens" es el límite de la IA: un párrafo. */
    const val MAX_CHARS = 900

    private fun get(ctx: Context, lang: String): Rewriter {
        val c = client
        if (c != null && clientLang == lang) return c
        c?.close()
        val opts = RewriterOptions.builder(ctx.applicationContext)
            .setOutputType(RewriterOptions.OutputType.PROFESSIONAL)
            .setLanguage(if (lang == "en") RewriterOptions.Language.ENGLISH else RewriterOptions.Language.SPANISH)
            .build()
        val n = Rewriting.getClient(opts)
        client = n
        clientLang = lang
        return n
    }

    /**
     * Reescribe [text] más formal. [status] muestra el progreso ("Descargando…"),
     * [done] recibe las versiones (la mejor primero) o un mensaje de error.
     */
    fun run(ctx: Context, text: String, lang: String, status: (String) -> Unit, done: (List<String>?, String?) -> Unit) {
        val fallback = { cloud(ctx, text, status, done) }
        if (text.length > MAX_CHARS) { fallback(); return }
        val r = try { get(ctx, lang) } catch (e: Throwable) { fallback(); return }
        val f = try { r.checkFeatureStatus() } catch (e: Throwable) { fallback(); return }
        f.addListener({
            val st = try { f.get() } catch (e: Throwable) { FeatureStatus.UNAVAILABLE }
            when (st) {
                FeatureStatus.AVAILABLE -> infer(r, text, done)
                FeatureStatus.UNAVAILABLE -> fallback()
                else -> download(r, text, status, done)
            }
        }, main)
    }

    /** El teléfono no trae la IA: se usa Google Gemini por internet con tu clave gratis. */
    private fun cloud(ctx: Context, text: String, status: (String) -> Unit, done: (List<String>?, String?) -> Unit) {
        val key = Prefs.get(ctx).getString(Prefs.GEMINI_KEY, "")?.trim().orEmpty()
        if (key.isEmpty()) {
            done(null, L.t(
                "Para «Más formal» necesitas una clave GRATIS de Google Gemini. Abre la app Best Keyboard → «IA para escribir» y sigue los pasos.",
                "For «More formal» you need a FREE Google Gemini key. Open the Best Keyboard app → «AI writing» and follow the steps.",
            ))
            return
        }
        status(L.t("Reescribiendo con Gemini…", "Rewriting with Gemini…"))
        CloudText.formal(text, key, done)
    }

    private fun download(r: Rewriter, text: String, status: (String) -> Unit, done: (List<String>?, String?) -> Unit) {
        status(L.t("Descargando la IA del teléfono (solo la primera vez)…", "Downloading the phone's AI (first time only)…"))
        var total = 0L
        val f = try {
            r.downloadFeature(object : DownloadCallback {
                override fun onDownloadStarted(bytesToDownload: Long) { total = bytesToDownload }
                override fun onDownloadProgress(totalBytesDownloaded: Long) {
                    if (total > 0) {
                        val pct = (100 * totalBytesDownloaded / total).toInt()
                        main.execute { status(L.t("Descargando la IA… $pct%", "Downloading the AI… $pct%")) }
                    }
                }
                override fun onDownloadCompleted() {}
                override fun onDownloadFailed(e: GenAiException) {}
            })
        } catch (e: Throwable) { done(null, unavailable()); return }
        f.addListener({
            try {
                f.get()
                status(L.t("Reescribiendo…", "Rewriting…"))
                infer(r, text, done)
            } catch (e: Throwable) {
                done(null, L.t("No se pudo descargar la IA. Revisa tu internet e inténtalo otra vez.", "Couldn't download the AI. Check your internet and try again."))
            }
        }, main)
    }

    private fun infer(r: Rewriter, text: String, done: (List<String>?, String?) -> Unit) {
        val f = try { r.runInference(RewritingRequest.builder(text).build()) } catch (e: Throwable) {
            done(null, L.t("No se pudo reescribir. Inténtalo otra vez.", "Couldn't rewrite. Try again.")); return
        }
        f.addListener({
            try {
                val list = f.get().results.map { it.text.trim() }.filter { it.isNotEmpty() }.distinct()
                if (list.isEmpty()) done(null, L.t("La IA no dio ninguna versión. Prueba con otro texto.", "The AI gave no version. Try other text."))
                else done(list, null)
            } catch (e: Throwable) {
                done(null, L.t("No se pudo reescribir. Inténtalo otra vez.", "Couldn't rewrite. Try again."))
            }
        }, main)
    }

    private fun unavailable() = L.t(
        "Tu teléfono no tiene la IA de Google (Gemini Nano) que se necesita para reescribir. " +
            "Solo la traen algunos teléfonos nuevos (Pixel 9 o más nuevos, Galaxy S25…).",
        "Your phone doesn't have Google's on-device AI (Gemini Nano) needed to rewrite. " +
            "Only some new phones have it (Pixel 9 or newer, Galaxy S25…).",
    )
}
