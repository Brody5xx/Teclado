package com.luisell.bestkeyboard

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Ayudas con internet (solo cuando tú tocas el botón):
 *  - Corregir gramática: LanguageTool (gratis, sin cuenta).
 *  - Reescribir formal: Google Gemini con tu clave gratis (si el teléfono no trae la IA de Google).
 */
object CloudText {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Resultado de la gramática: texto corregido y las partes que cambiaron (en el texto nuevo). */
    class Fixed(val text: String, val changes: List<IntRange>, val count: Int)

    private fun post(url: String, body: String, contentType: String, headers: Map<String, String> = emptyMap()): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 12000
            c.readTimeout = 30000
            c.doOutput = true
            c.setRequestProperty("Content-Type", contentType)
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpError(code, text)
            return text
        } finally {
            c.disconnect()
        }
    }

    class HttpError(val code: Int, val body: String) : Exception("HTTP $code")

    private fun noInternet() = L.t("No hay internet. Conéctate e inténtalo otra vez.", "No internet. Connect and try again.")

    // ---------------------------------------------------------------- Gramática (LanguageTool)

    /** Revisa la gramática y ortografía de [text] (español o inglés, lo detecta solo). */
    fun grammar(text: String, lang: String, done: (Fixed?, String?) -> Unit) {
        io.execute {
            try {
                val body = "text=" + URLEncoder.encode(text, "UTF-8") +
                    "&language=auto&preferredLanguages=" + (if (lang == "en") "en,es" else "es,en") +
                    "&motherTongue=es"
                val json = JSONObject(post("https://api.languagetool.org/v2/check", body, "application/x-www-form-urlencoded"))
                val matches = json.optJSONArray("matches") ?: JSONArray()
                // Se aplican de atrás hacia adelante, sin que se pisen
                class M(val off: Int, val len: Int, val rep: String)
                val list = ArrayList<M>()
                for (i in 0 until matches.length()) {
                    val m = matches.getJSONObject(i)
                    val reps = m.optJSONArray("replacements") ?: continue
                    if (reps.length() == 0) continue
                    val rep = reps.getJSONObject(0).optString("value")
                    val off = m.optInt("offset", -1)
                    val len = m.optInt("length", 0)
                    if (off < 0 || off + len > text.length) continue
                    list.add(M(off, len, rep))
                }
                list.sortBy { it.off }
                val keep = ArrayList<M>()
                var lastEnd = -1
                for (m in list) if (m.off >= lastEnd) { keep.add(m); lastEnd = m.off + m.len }
                val sb = StringBuilder()
                val changes = ArrayList<IntRange>()
                var pos = 0
                for (m in keep) {
                    sb.append(text, pos, m.off)
                    val start = sb.length
                    sb.append(m.rep)
                    if (m.rep.isNotEmpty()) changes.add(start until sb.length)
                    pos = m.off + m.len
                }
                sb.append(text, pos, text.length)
                val r = Fixed(sb.toString(), changes, keep.size)
                main.post { done(r, null) }
            } catch (e: HttpError) {
                val msg = if (e.code == 429) L.t("Demasiadas revisiones seguidas. Espera un minuto.", "Too many checks in a row. Wait a minute.")
                else L.t("El servicio de gramática no respondió. Inténtalo otra vez.", "The grammar service didn't respond. Try again.")
                main.post { done(null, msg) }
            } catch (e: Exception) {
                main.post { done(null, noInternet()) }
            }
        }
    }

    // ---------------------------------------------------------------- Reescribir formal (Gemini)

    /** Modelos de Gemini a probar, en orden (si uno está ocupado o ya no existe, se usa el siguiente). */
    private val MODELS = listOf(
        "gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest", "gemini-2.5-flash-lite", "gemini-2.0-flash",
    )

    /** Saca el mensaje de error que manda Google (para mostrarlo si nada funciona). */
    private fun googleMessage(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message").orEmpty().take(140)
    } catch (_: Exception) { "" }

    /** Reescribe [text] formal y profesional con Google Gemini usando la clave gratis [key]. */
    fun formal(text: String, key: String, done: (List<String>?, String?) -> Unit) {
        val prompt = "Reescribe el siguiente texto con un tono formal y profesional, corrigiendo la " +
            "ortografía y la gramática. Mantén el mismo idioma del texto y el mismo significado; no " +
            "agregues información nueva. Responde SOLO con el texto reescrito, sin comillas, sin " +
            "títulos y sin explicaciones.\n\nTexto:\n" + text
        ask(prompt, key) { list, err -> done(list?.map { it.trim('"', '“', '”') }, err) }
    }

    /** Le pregunta a Google Gemini ([prompt]) con tu clave; prueba varios modelos si uno falla. */
    fun ask(prompt: String, key: String, done: (List<String>?, String?) -> Unit) {
        io.execute {
            val req = JSONObject().put(
                "contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))),
            ).put("generationConfig", JSONObject().put("temperature", 0.4))
            var lastError: HttpError? = null
            var network = false
            for (model in MODELS) {
                // Dos intentos por modelo si Google dice que está ocupado (503)
                for (attempt in 0..1) {
                    try {
                        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
                        val out = JSONObject(post(url, req.toString(), "application/json", mapOf("x-goog-api-key" to key.trim())))
                        val parts = out.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        val sb = StringBuilder()
                        if (parts != null) for (i in 0 until parts.length()) {
                            val p = parts.optJSONObject(i) ?: continue
                            if (p.optBoolean("thought", false)) continue // "pensamientos" del modelo, no van
                            sb.append(p.optString("text"))
                        }
                        val result = sb.toString().trim()
                        if (result.isNotEmpty()) {
                            main.post { done(listOf(result), null) }
                            return@execute
                        }
                        break // respuesta vacía: probar el siguiente modelo
                    } catch (e: HttpError) {
                        lastError = e
                        when (e.code) {
                            // Clave mala o no permitida: no tiene caso probar otro modelo
                            401, 403 -> { main.post { done(null, keyProblem(e)) }; return@execute }
                            400 -> if (e.body.contains("API key", true) || e.body.contains("API_KEY", true)) {
                                main.post { done(null, keyProblem(e)) }; return@execute
                            } else break
                            503, 500 -> if (attempt == 0) { Thread.sleep(1500); continue } else break
                            else -> break // 404 (modelo ya no existe), 429 (límite de ese modelo)...
                        }
                    } catch (e: Exception) {
                        network = true
                        break
                    }
                }
            }
            val err = lastError
            val msg = when {
                err == null && network -> noInternet()
                err?.code == 429 -> L.t("Llegaste al límite gratis de Gemini por ahora. Espera un rato.", "You reached Gemini's free limit for now. Wait a while.")
                err != null -> L.t("Gemini no respondió (error ${err.code}). ", "Gemini didn't respond (error ${err.code}). ") + googleMessage(err.body)
                else -> L.t("La IA no dio respuesta. Inténtalo otra vez.", "The AI gave no answer. Try again.")
            }
            main.post { done(null, msg) }
        }
    }

    private fun keyProblem(e: HttpError): String {
        val g = googleMessage(e.body)
        return L.t("La clave de Gemini no funciona. Revísala en los ajustes del teclado.", "The Gemini key doesn't work. Check it in the keyboard settings.") +
            (if (g.isNotEmpty()) " ($g)" else "")
    }
}
