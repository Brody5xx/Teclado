package com.luisell.bestkeyboard.words

import kotlin.math.ln

/**
 * El "cerebro" de las sugerencias y del autocorrector.
 * Busca en los dos diccionarios a la vez (español e inglés), dando prioridad al idioma activo,
 * y suma lo que ha aprendido de ti.
 */
class WordEngine(private val dicts: List<Dictionary>, val user: UserData) {

    class Result(val suggestions: List<String>, val correction: String?)

    private fun langBonus(d: Dictionary, lang: String) = if (d.lang == lang) 0f else -1.3f

    private fun userBonus(lower: String): Float {
        val c = user.count(lower)
        return if (c > 0) 1.6f * ln(1f + c) else 0f
    }

    private fun bigramBonus(prevLower: String?, lower: String): Float {
        if (prevLower == null) return 0f
        val c = user.bigramCount(prevLower, lower)
        return if (c > 0) 2.5f * ln(1f + c) else 0f
    }

    private fun inAnyDict(lower: String): Boolean = dicts.any { it.indexOf(lower) >= 0 }

    /** ¿Es una palabra conocida (del diccionario o aprendida de ti)? */
    fun isKnown(lower: String): Boolean = inAnyDict(lower) || user.isKnown(lower)

    /** Cómo se muestra una palabra (con su acento o mayúscula correcta). */
    fun displayFor(lower: String, lang: String): String {
        val ordered = dicts.sortedBy { if (it.lang == lang) 0 else 1 }
        for (d in ordered) {
            val i = d.indexOf(lower)
            if (i >= 0) return d.words[i]
        }
        return user.entry(lower)?.display ?: lower
    }

    // ---------------------------------------------------------------- Sugerencias

    /** Las mejores palabras que empiezan como [typed]. */
    fun completions(typed: String, prev: String?, lang: String, max: Int = 6): List<String> {
        val tf = TextUtil.fold(typed)
        if (tf.isEmpty()) return emptyList()
        val tl = typed.lowercase()
        val prevLower = prev?.lowercase()
        val top = TopN(max)
        for (d in dicts) {
            val range = d.prefixRange(tf)
            val lb = langBonus(d, lang)
            for (i in range) {
                val w = d.words[i]
                val key = if (w[0].isUpperCase()) w.lowercase() else w
                var s = d.logFreq[i] + lb + userBonus(key) + bigramBonus(prevLower, key)
                val extra = d.folds[i].length - tf.length
                s += if (extra == 0) 0.6f else -0.08f * extra
                if (key.startsWith(tl)) s += 0.4f
                top.offer(key, w, s)
            }
        }
        for ((key, e) in user.words) {
            if (e.count < UserData.KNOWN || !e.fold.startsWith(tf) || inAnyDict(key)) continue
            var s = 9f + userBonus(key) + bigramBonus(prevLower, key)
            val extra = e.fold.length - tf.length
            s += if (extra == 0) 0.6f else -0.08f * extra
            top.offer(key, e.display, s)
        }
        return top.words()
    }

    /** Palabras que sueles escribir después de [prev] (aprendido de ti). */
    fun predictions(prev: String, lang: String, max: Int = 3): List<String> {
        val m = user.bigrams[prev.lowercase()] ?: return emptyList()
        return m.entries.sortedByDescending { it.value }.take(max).map { displayFor(it.key, lang) }
    }

    fun suggest(typed: String, prev: String?, lang: String): Result {
        val corr = correction(typed, lang)
        val comps = completions(typed, prev, lang).map { TextUtil.matchCase(it, typed) }
        return Result(comps, corr?.let { TextUtil.matchCase(it, typed) })
    }

    // ---------------------------------------------------------------- Autocorrector

    /**
     * Si [typed] está mal escrita, devuelve la corrección. Si está bien (o no hay una
     * corrección segura), devuelve null.
     */
    fun correction(typed: String, lang: String): String? {
        if (typed.isEmpty()) return null
        if (typed == "i") return if (lang == "en") "I" else null
        if (typed.length < 2) return null
        if (typed.any { it.isDigit() }) return null
        val letters = typed.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return null // siglas: RUMC, DVSS
        if (typed.drop(1).any { it.isUpperCase() }) return null                // McDonald, iPhone

        val lower = typed.lowercase()
        // ¿Ya es una palabra? Solo arreglamos la mayúscula especial ("i'm" -> "I'm")
        for (d in dicts) {
            val i = d.indexOf(lower)
            if (i >= 0) {
                val w = d.words[i]
                return if (w != lower && typed == lower) w else null
            }
        }
        if (user.isKnown(lower)) return null

        val tf = TextUtil.fold(lower)
        var best: String? = null
        var bestScore = Float.NEGATIVE_INFINITY

        // 1) Solo faltaban acentos, la ñ o el apóstrofo: "tambien" -> "también", "dont" -> "don't"
        for (d in dicts) {
            val i = d.bestForFold(tf)
            if (i < 0 || d.logFreq[i] < MIN_ACCENT_LF) continue
            val s = d.logFreq[i] + langBonus(d, lang)
            if (s > bestScore) { bestScore = s; best = d.words[i] }
        }
        if (best != null) return best
        if (tf.length < 3) return null

        // 2) Una letra de más, de menos, cambiada o al revés
        val mine = HashMap<String, UserData.Entry>()
        for (e in user.words.values) if (e.count >= UserData.KNOWN) mine[e.fold] = e
        edits(tf) { variant, penalty ->
            for (d in dicts) {
                val i = d.bestForFold(variant)
                if (i < 0 || d.logFreq[i] < MIN_EDIT_LF) continue
                val key = d.words[i].lowercase()
                val s = d.logFreq[i] + langBonus(d, lang) + penalty + userBonus(key)
                if (s > bestScore) { bestScore = s; best = d.words[i] }
            }
            val e = mine[variant]
            if (e != null) {
                val s = 10f + penalty + userBonus(e.display.lowercase())
                if (s > bestScore) { bestScore = s; best = e.display }
            }
        }
        return best
    }

    /** Genera todas las variantes a una "tecla" de distancia, con su castigo. */
    private inline fun edits(w: String, visit: (String, Float) -> Unit) {
        val n = w.length
        val sb = StringBuilder(n + 1)
        // Letra de más
        for (i in 0 until n) {
            sb.setLength(0); sb.append(w, 0, i).append(w, i + 1, n)
            val dup = (i > 0 && w[i - 1] == w[i]) || (i + 1 < n && w[i + 1] == w[i])
            val near = (i > 0 && Keys.near(w[i - 1], w[i])) || (i + 1 < n && Keys.near(w[i + 1], w[i]))
            visit(sb.toString(), if (dup) -1.6f else if (near) -1.9f else -2.4f)
        }
        // Dos letras al revés
        for (i in 0 until n - 1) {
            if (w[i] == w[i + 1]) continue
            sb.setLength(0); sb.append(w)
            sb.setCharAt(i, w[i + 1]); sb.setCharAt(i + 1, w[i])
            visit(sb.toString(), -2.0f)
        }
        // Letra cambiada por otra
        for (i in 0 until n) {
            for (c in ALPHABET) {
                if (c == w[i]) continue
                sb.setLength(0); sb.append(w); sb.setCharAt(i, c)
                visit(sb.toString(), if (Keys.near(w[i], c)) -1.8f else -3.2f)
            }
        }
        // Letra que faltó
        for (i in 0..n) {
            for (c in ALPHABET) {
                sb.setLength(0); sb.append(w, 0, i).append(c).append(w, i, n)
                val dup = (i > 0 && w[i - 1] == c) || (i < n && w[i] == c)
                visit(sb.toString(), if (dup) -2.0f else -2.5f)
            }
        }
    }

    /** Guarda las N mejores sin repetir. */
    private class TopN(private val max: Int) {
        private val keys = ArrayList<String>(max + 1)
        private val words = ArrayList<String>(max + 1)
        private val scores = ArrayList<Float>(max + 1)

        fun offer(key: String, word: String, score: Float) {
            val existing = keys.indexOf(key)
            if (existing >= 0) {
                if (scores[existing] >= score) return
                keys.removeAt(existing); words.removeAt(existing); scores.removeAt(existing)
            } else if (scores.size >= max && score <= scores[scores.size - 1]) {
                return
            }
            var pos = scores.size
            while (pos > 0 && scores[pos - 1] < score) pos--
            keys.add(pos, key); words.add(pos, word); scores.add(pos, score)
            if (scores.size > max) {
                keys.removeAt(max); words.removeAt(max); scores.removeAt(max)
            }
        }

        fun words(): List<String> = ArrayList(words)
    }

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        private val MIN_ACCENT_LF = ln(30f)
        private val MIN_EDIT_LF = ln(300f)
    }
}

/** Qué teclas están al lado de cuál (para saber si fue un dedazo). */
object Keys {
    private val near = Array(26) { BooleanArray(26) }

    init {
        val rows = listOf("qwertyuiop" to 0f, "asdfghjkln" to 0f, "zxcvbnm" to 1.5f)
        val pos = HashMap<Char, Pair<Float, Float>>()
        for ((r, row) in rows.withIndex()) {
            for ((i, c) in row.first.withIndex()) {
                if (r == 1 && i == 9) continue // la ñ se pliega a n
                pos[c] = Pair(row.second + i, r.toFloat())
            }
        }
        for ((a, pa) in pos) for ((b, pb) in pos) {
            if (a == b) continue
            val dx = pa.first - pb.first
            val dy = pa.second - pb.second
            if (dx * dx + dy * dy <= 2.1f) near[a - 'a'][b - 'a'] = true
        }
    }

    fun near(a: Char, b: Char): Boolean {
        if (a !in 'a'..'z' || b !in 'a'..'z') return false
        return near[a - 'a'][b - 'a']
    }
}
