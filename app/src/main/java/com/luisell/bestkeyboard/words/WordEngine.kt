package com.luisell.bestkeyboard.words

import kotlin.math.ln

/**
 * El "cerebro" de las sugerencias y del autocorrector.
 * Busca en los dos diccionarios a la vez (español e inglés), dando prioridad al idioma activo,
 * y suma lo que ha aprendido de ti.
 */
class WordEngine(private val dicts: List<Dictionary>, val user: UserData) {

    class Result(val suggestions: List<String>, val correction: String?)

    private fun langBonus(d: Dictionary, lang: String) = if (d.lang == lang) 0f else -2.0f

    /** ¿La palabra [cand] conserva los acentos y la ñ que escribiste? ("mañna" no debe volverse "manna") */
    private fun keepsMarks(typedLower: String, cand: String): Boolean {
        val c = cand.lowercase()
        for (ch in typedLower) if (ch.code > 127 && ch.isLetter() && c.indexOf(ch) < 0) return false
        return true
    }

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
                if (!keepsMarks(tl, w)) s -= 4f // escribiste "mañ": no sugerir "man"
                top.offer(key, w, s)
            }
        }
        for ((key, e) in user.words) {
            if (!e.known || !e.fold.startsWith(tf) || inAnyDict(key)) continue
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
        if (user.isKnown(lower)) return null
        // Palabras pegadas: "porfavor" -> "por favor"
        if (lang == "es") JOINED[lower]?.let { return it }

        // ¿Ya es una palabra? Primero en el idioma activo.
        val active = dicts.firstOrNull { it.lang == lang } ?: dicts[0]
        val ordered = if (active === dicts[0]) dicts else listOf(active) + dicts.filter { it !== active }
        for (d in ordered) {
            val i = d.indexOf(lower)
            if (i < 0) continue
            if (d !== active) {
                // Está solo en el otro idioma (ej. "aqui" en subtítulos en inglés): si en el
                // idioma activo existe con acento y es más común, se corrige ("aqui" -> "aquí")
                val j = active.bestForFold(TextUtil.fold(lower))
                if (j >= 0 && active.words[j].lowercase() != lower &&
                    active.logFreq[j] >= d.logFreq[i] + LN3 && keepsMarks(lower, active.words[j])
                ) return active.words[j]
            }
            val w = d.words[i]
            // Solo arreglamos la mayúscula especial ("i'm" -> "I'm")
            return if (w != lower && typed == lower) w else null
        }

        val tf = TextUtil.fold(lower)
        var best: String? = null
        var bestScore = Float.NEGATIVE_INFINITY

        // 1) Solo faltaban acentos, la ñ o el apóstrofo: "tambien" -> "también", "dont" -> "don't"
        for (d in dicts) {
            val i = d.bestForFold(tf)
            if (i < 0 || d.logFreq[i] < MIN_ACCENT_LF || !keepsMarks(lower, d.words[i])) continue
            val s = d.logFreq[i] + langBonus(d, lang)
            if (s > bestScore) { bestScore = s; best = d.words[i] }
        }
        if (best != null) return best
        if (tf.length < 3) return null

        // 2) Una letra de más, de menos, cambiada o al revés
        val mine = HashMap<String, UserData.Entry>()
        for (e in user.words.values) if (e.known) mine[e.fold] = e
        edits(tf) { variant, penalty ->
            for (d in dicts) {
                val i = d.bestForFold(variant)
                if (i < 0 || d.logFreq[i] < MIN_EDIT_LF) continue
                val key = d.words[i].lowercase()
                val marks = if (keepsMarks(lower, d.words[i])) 0f else -2.5f
                val s = d.logFreq[i] + langBonus(d, lang) + penalty + marks + userBonus(key)
                if (s > bestScore) { bestScore = s; best = d.words[i] }
            }
            val e = mine[variant]
            if (e != null) {
                val s = 10f + penalty + userBonus(e.display.lowercase())
                if (s > bestScore) { bestScore = s; best = e.display }
            }
        }
        // 3) Errores de sonido en español: nesecito -> necesito, aser -> hacer, vien -> bien
        for (d in dicts) {
            val i = d.bestForSound(tf)
            if (i < 0 || d.folds[i] == tf || d.logFreq[i] < MIN_EDIT_LF) continue
            val s = d.logFreq[i] + langBonus(d, lang) - 2.2f
            if (s > bestScore) { bestScore = s; best = d.words[i] }
        }
        return best
    }

    // ---------------------------------------------------------------- Revisión ortográfica

    /** Resultado de revisar una palabra: si está mal y cómo podría escribirse. */
    class Spell(val misspelled: Boolean, val suggestions: List<String>, val maybe: Boolean = false)

    /**
     * Revisa una palabra para el corrector ortográfico de Android (subrayado rojo en las apps).
     * Acepta palabras en español E inglés, y las que el teclado aprendió de ti.
     */
    fun spellCheck(typed: String, lang: String, max: Int = 5, strict: Boolean = false): Spell {
        val ok = Spell(false, emptyList())
        val w = typed.trim('\'', '’', '"', '“', '”', '¿', '¡', '(', ')')
        if (strict && w.length == 1 && w[0].isLetter()) return singleLetter(w, lang)
        if (w.length < 2 || w.length > 40) return ok
        if (w.any { it.isDigit() || it == '@' || it == '/' || it == '.' || it == '_' || it == '#' }) return ok
        val letters = w.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return ok   // siglas: RUMC, DVSS
        if (w.drop(1).any { it.isUpperCase() }) return ok                         // iPhone, McDonald
        val lower = w.lowercase()

        val fix = correction(w, lang)?.let { TextUtil.matchCase(it, w) }
        val known = if (strict) inAnyDict(lower) || user.isSure(lower) else isKnown(lower)
        // Revisión a fondo: palabras que existen pero son MUY raras, cuando hay una casi igual
        // muchísimo más común (ej. "hula" -> "hola"). Solo en el botón de revisar.
        if (strict && known && fix == null && !user.isKnown(lower)) {
            val rare = rareNeighbor(lower, lang)
            if (rare != null) return Spell(true, listOf(TextUtil.matchCase(rare, w)), maybe = true)
        }
        if (known && (fix == null || fix.equals(w, true) && fix == w)) return ok
        if (known && fix != null && fix.lowercase() == lower && fix != w) {
            // Solo la mayúscula especial: "i" -> "I", "i'm" -> "I'm"
            return Spell(true, listOf(fix))
        }

        // Varias opciones (la corrección principal primero)
        val top = TopN(max + 2)
        if (fix != null && !fix.equals(w, true)) top.offer(fix.lowercase(), fix, 1000f)
        val tf = TextUtil.fold(lower)
        for (d in dicts) {
            val i = d.bestForFold(tf)
            if (i >= 0 && d.logFreq[i] >= MIN_ACCENT_LF && keepsMarks(lower, d.words[i]))
                top.offer(d.words[i].lowercase(), d.words[i], 100f + d.logFreq[i] + langBonus(d, lang))
        }
        if (tf.length >= 3) {
            edits(tf) { variant, penalty ->
                for (d in dicts) {
                    val i = d.bestForFold(variant)
                    if (i < 0 || d.logFreq[i] < MIN_EDIT_LF) continue
                    val key = d.words[i].lowercase()
                    val marks = if (keepsMarks(lower, d.words[i])) 0f else -2.5f
                    top.offer(key, d.words[i], d.logFreq[i] + langBonus(d, lang) + penalty + marks + userBonus(key))
                }
            }
            for (d in dicts) {
                val i = d.bestForSound(tf)
                if (i >= 0 && d.folds[i] != tf && d.logFreq[i] >= MIN_EDIT_LF)
                    top.offer(d.words[i].lowercase(), d.words[i], d.logFreq[i] + langBonus(d, lang) - 2.2f)
            }
        }
        val list = top.words().filter { !it.equals(w, true) }.map { TextUtil.matchCase(it, w) }.distinct().take(max)
        return Spell(true, list)
    }

    /** Una letra sola que no es palabra ("n") -> palabras cortas que la contienen ("no", "en", "un"). */
    private fun singleLetter(w: String, lang: String): Spell {
        val c = w.lowercase()[0]
        val valid = if (lang == "es") "aeouy" else "ai"
        if (c in valid || (lang == "en" && w == "I")) return Spell(false, emptyList())
        val active = dicts.firstOrNull { it.lang == lang } ?: return Spell(false, emptyList())
        val found = ArrayList<Pair<String, Float>>()
        for (x in ALPHABET) for (cand in listOf("$c$x", "$x$c")) {
            val i = active.bestForFold(cand)
            if (i >= 0 && active.logFreq[i] >= MIN_EDIT_LF) found.add(Pair(active.words[i], active.logFreq[i]))
        }
        val list = found.sortedByDescending { it.second }.map { TextUtil.matchCase(it.first, w) }.distinct().take(4)
        return Spell(true, list)
    }

    /**
     * Palabra partida: "toda vio" -> "todavía". Solo si juntas forman una palabra común y no son
     * dos palabras muy comunes (para no unir "si no" o "de más").
     */
    fun joinedWord(a: String, b: String, lang: String): String? {
        if (a.length + b.length < 5 || a.any { !it.isLetter() } || b.any { !it.isLetter() }) return null
        val active = dicts.firstOrNull { it.lang == lang } ?: return null
        val la = active.indexOf(a.lowercase()).let { if (it >= 0) active.logFreq[it] else 0f }
        val lb = active.indexOf(b.lowercase()).let { if (it >= 0) active.logFreq[it] else 0f }
        if (minOf(la, lb) > ln(60000f)) return null
        if (user.bigramCount(a.lowercase(), b.lowercase()) >= 2) return null // tú las escribes juntas así
        val j = TextUtil.fold(a + b)
        val i = active.bestForFold(j)
        if (i >= 0 && active.logFreq[i] >= ln(2000f)) return TextUtil.matchCase(active.words[i], a)
        // Juntas y con una letra equivocada: "toda vio" -> "todavía"
        if (j.length < 7) return null
        var best: String? = null
        var need = maxOf(ln(8000f), maxOf(la, lb) - LN3)
        edits(j) { v, _ ->
            val k = active.bestForFold(v)
            if (k >= 0 && active.logFreq[k] >= need) { need = active.logFreq[k]; best = active.words[k] }
        }
        return best?.let { TextUtil.matchCase(it, a) }
    }

    /** Si [lower] es rarísima y hay una palabra a una letra de distancia 1000 veces más común, la da. */
    private fun rareNeighbor(lower: String, lang: String): String? {
        val active = dicts.firstOrNull { it.lang == lang } ?: return null
        // Qué tan común es en el idioma que estás usando (si no existe ahí, en el otro)
        var own = Float.NEGATIVE_INFINITY
        val ai = active.indexOf(lower)
        if (ai >= 0) own = active.logFreq[ai]
        else for (d in dicts) { val i = d.indexOf(lower); if (i >= 0 && d.logFreq[i] > own) own = d.logFreq[i] }
        if (own == Float.NEGATIVE_INFINITY || own > RARE_LF) return null
        val tf = TextUtil.fold(lower)
        if (tf.length < 3) return null
        var best: String? = null
        var bestLf = own + LN1000
        edits(tf) { variant, _ ->
            val i = active.bestForFold(variant)
            if (i >= 0 && active.logFreq[i] >= bestLf && active.words[i].lowercase() != lower) {
                bestLf = active.logFreq[i]
                best = active.words[i]
            }
        }
        if (best != null || own > ln(100f)) return best
        // Dos vocales equivocadas en una palabra rarísima: "hule" -> "hola" (2000 veces más común)
        val vowels = "aeiou"
        val chars = tf.toCharArray()
        val pos = chars.indices.filter { chars[it] in vowels }
        var need = own + ln(2000f)
        for (x in pos.indices) for (y in x + 1 until pos.size) {
            val p1 = pos[x]
            val p2 = pos[y]
            for (v1 in vowels) for (v2 in vowels) {
                if (v1 == chars[p1] || v2 == chars[p2]) continue
                val cc = chars.copyOf()
                cc[p1] = v1
                cc[p2] = v2
                val i = active.bestForFold(String(cc))
                if (i >= 0 && active.logFreq[i] >= need) { need = active.logFreq[i]; best = active.words[i] }
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
            visit(sb.toString(), if (dup) -1.6f else if (near) -1.9f else -2.8f)
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
                val vowel = isVowel(c)
                // Vocal que faltó entre dos consonantes ("cmara" -> "cámara") es muy común
                val between = vowel && i > 0 && i < n && !isVowel(w[i - 1]) && !isVowel(w[i])
                visit(sb.toString(), if (between) -1.7f else if (dup || vowel) -2.0f else -2.5f)
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
        private fun isVowel(c: Char) = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'
        private val LN3 = ln(3f)
        private val LN1000 = ln(1000f)
        private val RARE_LF = ln(150f)
        private val JOINED = mapOf(
            "porfavor" to "por favor", "osea" to "o sea", "aveces" to "a veces",
            "talvez" to "tal vez", "enserio" to "en serio", "apesar" to "a pesar",
            "sinembargo" to "sin embargo", "almenos" to "al menos", "envez" to "en vez",
            "depronto" to "de pronto", "porsupuesto" to "por supuesto",
            "derepente" to "de repente", "atravez" to "a través", "atraves" to "a través",
        )
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
