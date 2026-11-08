package com.luisell.bestkeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File

/**
 * 🌍 La app en más idiomas (portugués, francés, italiano, alemán y chino).
 *
 * Todos los textos están en español e inglés. Para los demás idiomas, la primera vez que eliges uno
 * el teléfono traduce todos los textos del inglés con Google ML Kit (DENTRO del teléfono) y los guarda.
 * Después ya está listo al instante y sin internet.
 */
object UiTr {
    /** Idiomas extra de las pantallas (además de español e inglés). */
    val LANGS = listOf("pt", "fr", "it", "de", "zh")

    /** Nombre de cada idioma en su propio idioma (así lo reconoce quien lo habla). */
    fun native(code: String) = when (code) {
        "es" -> "Español"
        "en" -> "English"
        "pt" -> "Português"
        "fr" -> "Français"
        "it" -> "Italiano"
        "de" -> "Deutsch"
        "zh" -> "中文"
        else -> code
    }

    @Volatile private var app: Context? = null
    private val maps = HashMap<String, Map<String, String>>()

    fun init(ctx: Context) { if (app == null) app = ctx.applicationContext }

    private fun file(ctx: Context, code: String) = File(ctx.filesDir, "ui_$code.txt")

    /** ¿Ya está traducida la app a este idioma? */
    fun has(ctx: Context, code: String) = file(ctx, code).exists()

    // ---- Traducciones revisadas a mano (assets/ui_fixed.tsv): mandan sobre la traducción automática
    @Volatile private var fixed: Map<String, Array<String>>? = null
    private val COLS = listOf("pt", "fr", "it", "de", "zh")

    @Synchronized private fun fixedMap(): Map<String, Array<String>> {
        fixed?.let { return it }
        val ctx = app ?: return emptyMap()
        val m = HashMap<String, Array<String>>(1400)
        try {
            ctx.assets.open("ui_fixed.tsv").bufferedReader().forEachLine { l ->
                if (l.startsWith("#") || l.isBlank()) return@forEachLine
                val f = l.split('\t')
                if (f.size >= 6) m[unesc(f[0])] = Array(5) { unesc(f[it + 1]) }
            }
        } catch (_: Exception) { }
        fixed = m
        return m
    }

    private fun fixedOf(code: String, en: String, es: String?): String? {
        val i = COLS.indexOf(code)
        if (i < 0) return null
        val m = fixedMap()
        if (es != null) m["es:$es"]?.let { return it[i] }
        return m[en]?.get(i)
    }

    /** El texto en [code], o null si todavía no está traducido. */
    fun of(code: String, en: String, es: String? = null): String? {
        fixedOf(code, en, es)?.let { return it }
        val m = map(code) ?: return null
        return m[en]
    }

    @Synchronized private fun map(code: String): Map<String, String>? {
        maps[code]?.let { return it }
        val ctx = app ?: return null
        val f = file(ctx, code)
        if (!f.exists()) return null
        val m = HashMap<String, String>(2048)
        try {
            f.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab > 0) m[unesc(line.substring(0, tab))] = unesc(line.substring(tab + 1))
            }
        } catch (_: Exception) { return null }
        maps[code] = m
        return m
    }

    private fun esc(x: String) = x.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
    private fun unesc(x: String): String {
        if (!x.contains('\\')) return x
        val b = StringBuilder(x.length)
        var i = 0
        while (i < x.length) {
            val c = x[i]
            if (c == '\\' && i + 1 < x.length) {
                when (x[i + 1]) { 'n' -> b.append('\n'); 't' -> b.append('\t'); else -> b.append(x[i + 1]) }
                i += 2
            } else { b.append(c); i++ }
        }
        return b.toString()
    }

    /** Todos los textos en inglés de la app (los saca tools/ui_strings.py al compilar). */
    private fun allStrings(ctx: Context): List<String> = try {
        ctx.assets.open("ui_strings.txt").bufferedReader().readLines().filter { it.isNotBlank() }.map { unesc(it) }
    } catch (_: Exception) { emptyList() }

    /** Los textos que faltan por traducir (por ejemplo, los nuevos de una actualización). */
    fun missing(ctx: Context, code: String): Int {
        init(ctx)
        val fx = fixedMap()
        val m = map(code) ?: return allStrings(ctx).count { it !in fx }
        return allStrings(ctx).count { it !in m && it !in fx }
    }

    @Volatile private var running = false

    /**
     * Traduce (lo que falta de) la app a [code]. [progress] recibe 0..100.
     * [done] = true si salió bien. Todo vuelve en el hilo principal.
     */
    fun translate(ctx: Context, code: String, progress: (Int) -> Unit, done: (Boolean) -> Unit) {
        init(ctx)
        val main = Handler(Looper.getMainLooper())
        if (running) { main.post { done(false) }; return }
        running = true
        val have = HashMap<String, String>(map(code) ?: emptyMap())
        val fx = fixedMap()
        val todo = allStrings(ctx).filter { it !in have && it !in fx } // las revisadas a mano no hace falta traducirlas
        if (todo.isEmpty()) {
            // Ya está todo: igual se deja el archivo para que se sepa que este idioma está listo
            try { if (!file(ctx, code).exists()) file(ctx, code).writeText("") } catch (_: Exception) { }
            running = false; main.post { done(true) }; return
        }
        val t = Translation("en", code)
        fun finish(ok: Boolean) {
            try {
                val f = file(ctx, code)
                val tmp = File(f.path + ".tmp")
                tmp.bufferedWriter().use { w -> for ((k, v) in have) { w.write(esc(k)); w.write("\t"); w.write(esc(v)); w.write("\n") } }
                if (have.isNotEmpty()) tmp.renameTo(f)
                synchronized(this) { maps.remove(code) }
            } catch (_: Exception) { }
            t.close()
            running = false
            main.post { done(ok) }
        }
        t.prepare(onReady = {
            // Cada línea por separado (así se guardan los saltos de línea y los espacios del principio y del final)
            var i = 0
            var lastPct = -1
            fun nextString() {
                if (i >= todo.size) { finish(true); return }
                val src = todo[i]
                val lines = src.split("\n")
                val out = arrayOfNulls<String>(lines.size)
                var li = 0
                fun nextLine() {
                    while (li < lines.size && lines[li].isBlank()) { out[li] = lines[li]; li++ }
                    if (li >= lines.size) {
                        have[src] = out.joinToString("\n") { it ?: "" }
                        i++
                        val pct = i * 100 / todo.size
                        if (pct != lastPct) { lastPct = pct; main.post { progress(pct) } }
                        if (i % 200 == 0) {
                            // Se guarda de vez en cuando por si se cierra la app a la mitad
                            try { file(ctx, code).bufferedWriter().use { w -> for ((k, v) in have) { w.write(esc(k)); w.write("\t"); w.write(esc(v)); w.write("\n") } } } catch (_: Exception) { }
                        }
                        nextString()
                        return
                    }
                    val line = lines[li]
                    val lead = line.takeWhile { it.isWhitespace() }
                    val trail = line.takeLastWhile { it.isWhitespace() }
                    val core = line.trim()
                    t.translate(core, { r ->
                        out[li] = lead + r + trail
                        li++
                        nextLine()
                    }, {
                        out[li] = line // si falla una línea se queda en inglés
                        li++
                        nextLine()
                    })
                }
                nextLine()
            }
            nextString()
        }, onError = { finish(false) })
    }

    /** El idioma de las pantallas que eligió la persona ("es", "en", "pt"…). */
    fun screenLang(ctx: Context): String {
        init(ctx)
        val p = Prefs.get(ctx)
        val pick = p.getString(Prefs.APP_LANG, "auto") ?: "auto"
        val code = if (pick == "auto") java.util.Locale.getDefault().language.let { if (it == "es") "es" else it } else pick
        return when {
            code == "es" || code == "en" -> code
            code in LANGS && has(ctx, code) -> code
            else -> if (pick == "auto") "en" else if (code in LANGS) "en" else "es"
        }
    }
}
