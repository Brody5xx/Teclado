package com.luisell.bestkeyboard.words

import kotlin.math.ln

/**
 * El "cerebro" de las sugerencias y del autocorrector.
 * Busca en los dos diccionarios a la vez (español e inglés), dando prioridad al idioma activo,
 * y suma lo que ha aprendido de ti.
 */
class WordEngine(private val dicts: List<Dictionary>, val user: UserData) {

    /**
     * 👆 Dónde tocó el dedo: para la palabra [touchWord] (sin acentos), [touchAlt] dice en cada letra
     * cuál otra tecla estaba casi igual de cerca (o ' '). Esa letra cuesta poco cambiarla.
     */
    @Volatile var touchWord: String? = null
    @Volatile var touchAlt: String? = null

    fun setTouch(word: String?, alt: String?) {
        if (word == null || alt == null || alt.isBlank()) { touchWord = null; touchAlt = null; return }
        val f = TextUtil.fold(word.lowercase())
        if (f.length != alt.length) { touchWord = null; touchAlt = null; return }
        touchWord = f; touchAlt = alt
    }

    /** 📇 Nombres de tus contactos (en minúsculas → como se escribe: "luisell" → "Luisell"). */
    @Volatile var names: Map<String, String> = emptyMap()

    /** Las frases de fábrica (qué palabra suele venir después). Se cargan aparte. */
    @Volatile var base: NGrams? = null

    class Result(val suggestions: List<String>, val correction: String?)

    /** El otro idioma casi no aparece en las sugerencias (solo si no hay nada en el tuyo). */
    private fun langBonus(d: Dictionary, lang: String) = if (d.lang == lang) 0f else -6.0f
    /** Para corregir: el otro idioma cuenta menos, pero sí puede arreglar una palabra suya bien escrita. */
    private fun fixBonus(d: Dictionary, lang: String) = if (d.lang == lang) 0f else -3.5f

    /** ¿De qué idioma es esta palabra? "es", "en" o null si es de los dos (o de ninguno). */
    fun langOf(lower: String): String? {
        if (lower.any { it in "ñáéíóúü¿¡" }) return "es"
        val es = dicts.firstOrNull { it.lang == "es" }?.indexOf(lower) ?: -1
        val en = dicts.firstOrNull { it.lang == "en" }?.indexOf(lower) ?: -1
        val ue = user.entry(lower)
        if (es >= 0 && en < 0) return "es"
        if (en >= 0 && es < 0) return "en"
        if (es >= 0 && en >= 0) {
            // En los dos: gana el que sea mucho más común
            val de = dicts.first { it.lang == "es" }.logFreq[es]
            val dn = dicts.first { it.lang == "en" }.logFreq[en]
            return if (de > dn + 2.5f) "es" else if (dn > de + 2.5f) "en" else null
        }
        if (ue != null && ue.en + ue.es >= 3) {
            if (ue.en >= ue.es * 3) return "en"
            if (ue.es >= ue.en * 3) return "es"
        }
        return null
    }

    /**
     * 🌐 El idioma de la frase que estás escribiendo (la última palabra clara pesa más).
     * Así, si escribes «the camera» con el teclado en español, las sugerencias ya salen en inglés
     * sin esperar a que el teclado se cambie. [currentLength] = lo que llevas de la palabra actual.
     */
    fun contextLang(before: CharSequence, currentLength: Int, anySentence: Boolean = false): String? {
        val end = (before.length - currentLength).coerceIn(0, before.length)
        val text = before.subSequence(0, end)
        var start = 0
        if (!anySentence) for (i in text.indices.reversed()) { val c = text[i]; if (c == '.' || c == '!' || c == '?' || c == '\n') { start = i + 1; break } }
        val ws = ArrayList<String>()
        var i = text.length
        while (i > start && ws.size < (if (anySentence) 6 else 4)) {
            while (i > start && !TextUtil.isWordChar(text[i - 1])) i--
            val e = i
            while (i > start && TextUtil.isWordChar(text[i - 1])) i--
            if (e > i) ws.add(text.subSequence(i, e).toString().lowercase().trim('\'', '’'))
        }
        var es = 0f
        var en = 0f
        var w = 1f
        for (x in ws) {
            val v = if (x == "i" || x == "i'm" || x == "i'll") "en" else if (x.length < 2) null else langOf(x)
            if (v == "es") es += w else if (v == "en") en += w
            w *= 0.55f
        }
        return if (en > es) "en" else if (es > en) "es" else null
    }

    /**
     * 🇪🇸 ¿Esta palabra lleva acento según las palabras de al lado? (tu/tú, esta/está, termine/terminé…)
     * [left] = la palabra de antes, [right] = la de después (puede faltar). Devuelve la forma correcta
     * solo si las frases reales lo dicen claramente; si no, null.
     */
    fun accentFor(word: String, left: String?, right: String?, minGain: Float = 1.1f, minCount: Int = 2): String? {
        val bs = base ?: return null
        val es = dicts.firstOrNull { it.lang == "es" } ?: return null
        val lower = word.lowercase()
        if (lower.length < 2) return null
        val vars = es.variants(TextUtil.fold(lower))
        if (vars.size < 2) return null
        val cur = vars.firstOrNull { es.words[it].lowercase() == lower } ?: return null
        val l = left?.lowercase()
        val r = right?.lowercase()
        fun ev(i: Int): Pair<Float, Int> {
            val w = es.words[i].lowercase()
            val a = if (l != null) bs.count("b", "es", l, w) else 0
            val b = if (r != null) bs.count("b", "es", w, r) else 0
            return (ln(1f + a) + ln(1f + b)) to (a + b)
        }
        val sc = ev(cur).first
        var best = -1
        var bestS = sc
        var bestN = 0
        for (i in vars) if (i != cur) {
            val (sv, n) = ev(i)
            if (sv > bestS) { bestS = sv; best = i; bestN = n }
        }
        if (best < 0 || bestS - sc < minGain || bestN < minCount) return null
        if (user.isBlocked(es.words[best].lowercase())) return null
        return TextUtil.matchCase(es.words[best], word)
    }

    /** «ya termine» → terminé, «yo llegue» → llegué, «ayer arregle» → arreglé (pasado, yo). */
    fun pastTense(word: String, left: String?, left2: String? = null): String? {
        val l = left?.lowercase() ?: return null
        if (l !in PAST_WORDS) return null
        // «cuando yo llegue», «que ya termine»: ahí NO es pasado
        if (left2 != null && left2.lowercase() in NOT_PAST_BEFORE) return null
        val lower = word.lowercase()
        if (lower.length < 4 || !lower.endsWith("e")) return null
        val es = dicts.firstOrNull { it.lang == "es" } ?: return null
        val cand = lower.dropLast(1) + "é"
        val i = es.indexOf(cand)
        if (i < 0 || es.indexOf(lower) < 0) return null
        return TextUtil.matchCase(es.words[i], word)
    }

    /** ¿La palabra [cand] conserva los acentos y la ñ que escribiste? ("mañna" no debe volverse "manna") */
    private fun keepsMarks(typedLower: String, cand: String): Boolean {
        val c = cand.lowercase()
        for (ch in typedLower) if (ch.code > 127 && ch.isLetter() && c.indexOf(ch) < 0) return false
        return true
    }

    private fun userBonus(lower: String, lang: String? = null): Float {
        val c = user.count(lower)
        if (c <= 0) return 0f
        var b = 2.2f * ln(1f + c)
        // ⏳ Lo que ya no usas pesa menos poco a poco (lo de este mes, completo)
        val e = user.entry(lower)
        if (e != null && e.day > 0 && !e.sure) {
            val age = UserData.today() - e.day
            if (age > 30) b *= if (age > 365) 0.45f else if (age > 180) 0.6f else if (age > 90) 0.75f else 0.88f
        }
        if (lang != null) {
            // Lo que escribes en este idioma pesa más; lo que solo usas en el otro, menos
            val mine = user.countIn(lower, lang)
            val other = user.countIn(lower, if (lang == "en") "es" else "en")
            if (mine == 0 && other >= 2) b -= 2.5f      // solo la usas en el otro idioma
            else b += 0.6f * ln(1f + mine)
        }
        // Lo que escribiste hace poco sale primero
        val t = user.recent[lower]
        if (t != null) {
            val ago = System.currentTimeMillis() - t
            b += if (ago < 20 * 60_000L) 1.4f else if (ago < 6 * 3600_000L) 0.6f else 0f
        }
        return b
    }

    private fun bigramBonus(prevLower: String?, lower: String): Float {
        if (prevLower == null) return 0f
        val c = user.bigramCount(prevLower, lower)
        return if (c > 0) 3.4f * ln(1f + c) else 0f
    }

    /** Lo que dice la frase: tus pares y tríos de palabras, y los de fábrica (más suaves). */
    private fun ctxBonus(prevLower: String?, prev2Lower: String?, key: String, lang: String): Float {
        var b = bigramBonus(prevLower, key)
        if (prevLower == null) return b
        if (prev2Lower != null) {
            val c = user.trigramCount("$prev2Lower $prevLower", key)
            if (c > 0) b += 3.0f * ln(1f + c)
        }
        val bs = base ?: return b
        val c = bs.count("b", lang, prevLower, key)
        if (c > 0) b += 1.1f * ln(1f + c)
        if (prev2Lower != null) {
            val t = bs.count("t", lang, "$prev2Lower $prevLower", key)
            if (t > 0) b += 1.5f * ln(1f + t)
        }
        return b
    }

    private fun inAnyDict(lower: String): Boolean = dicts.any { it.indexOf(lower) >= 0 }

    /** ¿Es una palabra conocida (del diccionario o aprendida de ti)? */
    fun isKnown(lower: String): Boolean = inAnyDict(lower) || user.isKnown(lower) || names.containsKey(lower)

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
    fun completions(typed: String, prev: String?, lang: String, max: Int = 6, prev2: String? = null): List<String> {
        val tf = TextUtil.fold(typed)
        if (tf.isEmpty()) return emptyList()
        val tl = typed.lowercase()
        val prevLower = prev?.lowercase()
        val prev2Lower = prev2?.lowercase()
        val top = TopN(max)
        for (d in dicts) {
            val range = d.prefixRange(tf)
            val lb = langBonus(d, lang)
            for (i in range) {
                val w = d.words[i]
                val key = if (w[0].isUpperCase()) w.lowercase() else w
                var s = d.logFreq[i] + lb + userBonus(key, lang) + ctxBonus(prevLower, prev2Lower, key, lang)
                val extra = d.folds[i].length - tf.length
                s += if (extra == 0) 0.6f else -0.08f * extra
                if (key.startsWith(tl)) s += 0.4f
                if (!keepsMarks(tl, w)) s -= 4f // escribiste "mañ": no sugerir "man"
                top.offer(key, w, s)
            }
        }
        // 👆 Tocaste entre dos teclas: también lo que empieza con la otra letra
        val ta = if (tf == touchWord) touchAlt else null
        if (ta != null && tf.length >= 2) {
            for (pos in ta.indices) {
                val alt = ta[pos]
                if (alt == ' ') continue
                val v = tf.substring(0, pos) + alt + tf.substring(pos + 1)
                for (d in dicts) {
                    val lb = langBonus(d, lang)
                    var n = 0
                    for (i in d.prefixRange(v)) {
                        if (++n > 400) break
                        val w = d.words[i]
                        val key = if (w[0].isUpperCase()) w.lowercase() else w
                        var s = d.logFreq[i] + lb + userBonus(key, lang) + ctxBonus(prevLower, prev2Lower, key, lang) - 1.3f
                        val extra = d.folds[i].length - v.length
                        s += if (extra == 0) 0.6f else -0.08f * extra
                        top.offer(key, w, s)
                    }
                }
            }
        }
        // 📇 Nombres de tus contactos, bien escritos y con mayúscula
        if (tf.length >= 2) for ((key, disp) in names) {
            if (!TextUtil.fold(key).startsWith(tf) || user.isBlocked(key)) continue
            var s = 8.5f + userBonus(key, lang) + ctxBonus(prevLower, prev2Lower, key, lang)
            val extra = key.length - tf.length
            s += if (extra == 0) 0.6f else -0.08f * extra
            top.offer(key, disp, s)
        }
        for ((key, e) in user.words) {
            if (!e.known || !e.fold.startsWith(tf) || inAnyDict(key)) continue
            var s = 9f + userBonus(key, lang) + ctxBonus(prevLower, prev2Lower, key, lang)
            val extra = e.fold.length - tf.length
            s += if (extra == 0) 0.6f else -0.08f * extra
            top.offer(key, e.display, s)
        }
        return top.words().filter { !user.isBlocked(it.lowercase()) }
    }

    private var nearKey = ""
    private var nearCache: List<String> = emptyList()

    /**
     * 🔎 Escribiste algo que no es palabra ("actulizo", "actuliso"): las palabras que más se le
     * parecen, aunque sean poco comunes. Busca una letra de más, de menos, cambiada o al revés,
     * cómo suena (s/z/c, b/v, h…), y palabras que EMPIEZAN parecido (mientras escribes).
     * Solo para la barra de sugerencias (el autocorrector sigue siendo prudente).
     */
    fun nearWords(typed: String, prev: String?, lang: String, max: Int = 3): List<String> {
        val tl = typed.lowercase()
        val tf = TextUtil.fold(tl)
        if (tf.length < 3 || tf.length > 20) return emptyList()
        val key = "$lang|${prev?.lowercase()}|$tl"
        if (key == nearKey) return nearCache
        val prevLower = prev?.lowercase()
        val top = TopN(max + 2)
        fun offerIdx(d: Dictionary, i: Int, base: Float) {
            if (i < 0 || d.logFreq[i] < MIN_NEAR_LF) return
            val k = d.words[i].lowercase()
            if (k == tl || user.isBlocked(k)) return
            val marks = if (keepsMarks(tl, d.words[i])) 0f else -2.5f
            top.offer(k, d.words[i], d.logFreq[i] + langBonus(d, lang) + base + marks + userBonus(k, lang) + bigramBonus(prevLower, k))
        }
        // Cómo suena lo que escribiste, tal cual
        for (d in dicts) offerIdx(d, d.bestForSound(tf), -1.5f)
        edits(tf) { v, pen ->
            for (d in dicts) {
                offerIdx(d, d.bestForFold(v), pen)
                // Una letra mal + cómo suena: "actuliso" -> "actualizo"
                offerIdx(d, d.bestForSound(v), pen - 1.2f)
            }
        }
        // Tus palabras también (una letra de distancia)
        val mine = HashMap<String, UserData.Entry>()
        for (e in user.words.values) if (e.known && !e.typo) mine[e.fold] = e
        if (mine.isNotEmpty()) edits(tf) { v, pen ->
            val e = mine[v]
            if (e != null && e.display.lowercase() != tl) top.offer(e.display.lowercase(), e.display, 9f + pen + userBonus(e.display.lowercase(), lang))
        }
        // Mientras escribes: palabras que empiezan casi igual ("actuliz" -> actualizar, actualización)
        if (tf.length >= 4) edits(tf) { v, pen ->
            for (d in dicts) {
                val r = d.prefixRange(v)
                var n = 0
                for (i in r) {
                    if (++n > 60) break
                    if (d.folds[i].length == v.length) continue
                    offerIdx(d, i, pen - 1.0f - 0.06f * (d.folds[i].length - v.length))
                }
            }
        }
        // Nada a una letra: busca a dos letras (más lento, solo si hace falta)
        if (top.words().isEmpty() && tf.length in 5..12) {
            val seen = HashSet<String>()
            edits(tf) { v1, p1 ->
                if (seen.add(v1)) edits(v1) { v2, p2 ->
                    for (d in dicts) offerIdx(d, d.bestForFold(v2), p1 + p2)
                }
            }
        }
        val out = top.words().take(max)
        nearKey = key
        nearCache = out
        return out
    }

    /** «nosvemos» → «nos vemos»: dos palabras comunes de tu idioma que se dicen juntas. */
    private fun splitWord(tf: String, lang: String): String? {
        val active = dicts.firstOrNull { it.lang == lang } ?: return null
        val bs = base
        var best: String? = null
        var bestS = Float.NEGATIVE_INFINITY
        for (i in 1 until tf.length) {
            val a = tf.substring(0, i)
            val b = tf.substring(i)
            if (b.length < 2) continue
            if (a.length == 1 && !(a == "a" || a == "y" || a == "o" || (lang == "en" && a == "i"))) continue
            val ia = active.bestForFold(a)
            val ib = active.bestForFold(b)
            if (ia < 0 || ib < 0) continue
            val wa = active.words[ia]
            val wb = active.words[ib]
            val pair = bs?.count("b", lang, wa.lowercase(), wb.lowercase()) ?: 0
            val common = minOf(active.logFreq[ia], active.logFreq[ib])
            if (pair == 0 && common < ln(20000f)) continue
            if (common < ln(300f)) continue
            val sc = active.logFreq[ia] + active.logFreq[ib] + 2f * ln(1f + pair)
            if (sc > bestS) { bestS = sc; best = (if (lang == "en" && wa == "i") "I" else wa) + " " + wb }
        }
        return best
    }

    /** ¿Se parecen? (a 2 letras o menos, sin contar acentos) — para aprender tus arreglos. */
    fun similar(a: String, b: String): Boolean {
        val x = TextUtil.fold(a.lowercase())
        val y = TextUtil.fold(b.lowercase())
        if (x == y) return true
        if (kotlin.math.abs(x.length - y.length) > 2 || x.length < 2 || y.length < 2) return false
        var prev = IntArray(y.length + 1) { it }
        for (i in 1..x.length) {
            val cur = IntArray(y.length + 1)
            cur[0] = i
            for (j in 1..y.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[y.length] <= 2
    }

    /** ¿Está en el diccionario? */
    fun inDictionary(lower: String): Boolean = inAnyDict(lower)

    /** ¿Parece un error de dedo? (no es palabra, pero hay una palabra casi igual) */
    fun looksLikeTypo(typed: String, lang: String): Boolean {
        val tl = typed.lowercase()
        if (inAnyDict(tl) || user.isSure(tl)) return false
        if (typed.length > 1 && typed.drop(1).any { it.isUpperCase() }) return false // iPhone, McDonald
        val tf = TextUtil.fold(tl)
        if (tf.length < 3) return false
        var found = false
        edits(tf) { v, _ ->
            if (!found) for (d in dicts) {
                val i = d.bestForFold(v)
                if (i >= 0 && d.logFreq[i] >= MIN_NEAR_LF && d.words[i].lowercase() != tl) { found = true; break }
            }
        }
        if (!found) for (d in dicts) { val i = d.bestForSound(tf); if (i >= 0 && d.folds[i] != tf) { found = true; break } }
        return found
    }

    /**
     * La palabra siguiente: lo que TÚ sueles escribir después (lo que más pesa) y, si no hay,
     * lo que se suele decir en ese idioma ("thank you" → for, so; "nos vemos" → mañana…).
     */
    fun predictions(prev: String, lang: String, max: Int = 3, prev2: String? = null): List<String> {
        val p = prev.lowercase()
        val p2 = prev2?.lowercase()
        val score = HashMap<String, Float>()
        fun okLang(k: String) = langOf(k).let { l -> l == null || l == lang }
        fun add(k: String, v: Float) { if (!user.isBlocked(k)) score[k] = (score[k] ?: 0f) + v }
        user.bigrams[p]?.forEach { (k, c) -> add(k, 2.5f + 3.4f * ln(1f + c) + (if (okLang(k)) 0f else -5f)) }
        if (p2 != null) user.trigrams["$p2 $p"]?.forEach { (k, c) -> add(k, 3.5f + 3.0f * ln(1f + c) + (if (okLang(k)) 0f else -5f)) }
        base?.let { bs ->
            bs.next("b", lang, p) { k, c -> add(k, 1.1f * ln(1f + c)) }
            if (p2 != null) bs.next("t", lang, "$p2 $p") { k, c -> add(k, 0.8f + 1.5f * ln(1f + c)) }
        }
        return score.entries.sortedByDescending { it.value }.take(max).map { e ->
            val k = e.key
            if (lang == "en" && (k == "i" || k.startsWith("i'"))) "I" + k.drop(1) else displayFor(k, lang)
        }
    }

    /**
     * 💬 La frase completa cuando es obvia: «I will be there» → in a few minutes,
     * «muchas» → gracias por tu ayuda. Va palabra por palabra mientras esté MUY claro
     * (tus frases de siempre primero). Devuelve 2 a 4 palabras, o null.
     */
    fun phrase(prev: String, lang: String, prev2: String? = null, first: String? = null): String? {
        var p1 = prev.lowercase()
        var p2 = prev2?.lowercase()
        val out = ArrayList<String>()
        if (first != null) { out.add(first); p2 = p1; p1 = first.lowercase() }
        while (out.size < 5) {
            val next = confidentNext(p1, p2, lang) ?: break
            if (out.size >= 1 && next == out.last().lowercase()) break
            out.add(next)
            p2 = p1; p1 = next
        }
        if (out.size < 2) return null
        return out.mapIndexed { i, w -> if (lang == "en" && (w == "i" || w.startsWith("i'"))) "I" + w.drop(1) else if (i == 0 && first != null) w else displayFor(w, lang) }
            .joinToString(" ")
    }

    /** La palabra siguiente solo si es muy probable (si no, null). */
    private fun confidentNext(p1: String, p2: String?, lang: String): String? {
        // 1) Tus tríos de palabras
        if (p2 != null) user.trigrams["$p2 $p1"]?.let { m ->
            val tot = m.values.sum()
            val top = m.maxByOrNull { it.value }
            if (top != null && top.value >= 2 && top.value * 10 >= tot * 6 && !user.isBlocked(top.key)) return top.key
        }
        // 2) Tus pares de palabras
        user.bigrams[p1]?.let { m ->
            val tot = m.values.sum()
            val top = m.maxByOrNull { it.value }
            if (top != null && top.value >= 3 && top.value * 10 >= tot * 7 && !user.isBlocked(top.key)) return top.key
        }
        val bs = base ?: return null
        // 3) Las frases de fábrica (tríos)
        if (p2 != null) {
            var tot = 0; var best: String? = null; var bc = 0
            bs.next("t", lang, "$p2 $p1") { w, c -> tot += c; if (c > bc) { bc = c; best = w } }
            if (best != null && bc >= 3 && bc * 10 >= tot * 5) return best
        }
        // 4) Pares de fábrica muy claros («muchas» → gracias)
        run {
            var tot = 0; var best: String? = null; var bc = 0
            bs.next("b", lang, p1) { w, c -> tot += c; if (c > bc) { bc = c; best = w } }
            if (best != null && bc >= 8 && bc * 10 >= tot * 6) return best
        }
        return null
    }

    fun suggest(typed: String, prev: String?, lang: String, prev2: String? = null): Result {
        val corr = correction(typed, lang)
        val comps = completions(typed, prev, lang, prev2 = prev2).map { TextUtil.matchCase(it, typed) }
        return Result(comps, corr?.let { TextUtil.matchCase(it, typed) })
    }

    // ---------------------------------------------------------------- Autocorrector

    /**
     * Si [typed] está mal escrita, devuelve la corrección. Si está bien (o no hay una
     * corrección segura), devuelve null.
     */
    fun correction(typed: String, lang: String): String? =
        correctionRaw(typed, lang)?.takeIf { !user.isBlocked(it.lowercase()) }

    private fun correctionRaw(typed: String, lang: String): String? {
        if (typed.isEmpty()) return null
        if (typed == "i") return if (lang == "en") "I" else null
        if (typed.length < 2) return null
        if (typed.any { it.isDigit() }) return null
        val letters = typed.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return null // siglas: ONU, USB
        if (typed.drop(1).any { it.isUpperCase() }) return null                // McDonald, iPhone

        val lower = typed.lowercase()
        // Tú ya lo corregiste así antes
        user.fixFor(lower)?.let { if (!user.isBlocked(it.lowercase())) return it }
        // Un contacto escrito en minúsculas («luisell» → «Luisell»), si no es también una palabra
        names[lower]?.let { n -> if (typed == lower && n != lower && !inAnyDict(lower)) return n }
        if (user.isKnown(lower)) return null
        // Palabras pegadas: "porfavor" -> "por favor", "alot" -> "a lot"
        if (lang == "es") JOINED[lower]?.let { return it }
        if (lang == "en") JOINED_EN[lower]?.let { return it }

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
            val s = d.logFreq[i] + fixBonus(d, lang)
            if (s > bestScore) { bestScore = s; best = d.words[i] }
        }
        if (best != null) return best
        if (tf.length < 3) return null

        // 2) Una letra de más, de menos, cambiada o al revés
        val mine = HashMap<String, UserData.Entry>()
        for (e in user.words.values) if (e.known && !e.typo) mine[e.fold] = e
        edits(tf) { variant, penalty ->
            for (d in dicts) {
                val i = d.bestForFold(variant)
                // 👆 Tocaste entre dos teclas: basta con que sea una palabra real (aunque poco común)
                val minLf = if (penalty >= -1f) LN20 else MIN_EDIT_LF
                if (i < 0 || d.logFreq[i] < minLf) continue
                val key = d.words[i].lowercase()
                val marks = if (keepsMarks(lower, d.words[i])) 0f else -2.5f
                val s = d.logFreq[i] + fixBonus(d, lang) + penalty + marks + userBonus(key, lang)
                if (s > bestScore) { bestScore = s; best = d.words[i] }
            }
            val e = mine[variant]
            if (e != null) {
                val s = 10f + penalty + userBonus(e.display.lowercase(), lang)
                if (s > bestScore) { bestScore = s; best = e.display }
            }
        }
        // 2b) Dos palabras pegadas que se dicen juntas ("thankyou", "nosvemos"): solo si no hay otra idea
        if (best == null && tf.length >= 6 && typed.none { it.isUpperCase() }) splitWord(tf, lang)?.let { return it }
        // 3) Errores de sonido en español: nesecito -> necesito, aser -> hacer, vien -> bien
        for (d in dicts) {
            val i = d.bestForSound(tf)
            if (i < 0 || d.folds[i] == tf || d.logFreq[i] < MIN_EDIT_LF) continue
            val s = d.logFreq[i] + fixBonus(d, lang) - 2.2f
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
    /**
     * [ignoreUser] = revisar una selección: tus palabras guardadas TAMBIÉN se revisan
     * (para eso está "Saltar").
     */
    fun spellCheck(typed: String, lang: String, max: Int = 5, strict: Boolean = false, ignoreUser: Boolean = false): Spell {
        val ok = Spell(false, emptyList())
        val w = typed.trim('\'', '’', '"', '“', '”', '¿', '¡', '(', ')')
        if (strict && w.length == 1 && w[0].isLetter()) return singleLetter(w, lang)
        if (w.length < 2 || w.length > 40) return ok
        if (w.any { it.isDigit() || it == '@' || it == '/' || it == '.' || it == '_' || it == '#' }) return ok
        val letters = w.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return ok   // siglas: ONU, USB
        if (w.drop(1).any { it.isUpperCase() }) return ok                         // iPhone, McDonald
        val lower = w.lowercase()

        val fix = correction(w, lang)?.let { TextUtil.matchCase(it, w) }
        val known = if (ignoreUser) inAnyDict(lower) else if (strict) inAnyDict(lower) || user.isSure(lower) else isKnown(lower)
        // Revisión a fondo: palabras que existen pero son MUY raras, cuando hay una casi igual
        // muchísimo más común (ej. "hula" -> "hola"). Solo en el botón de revisar.
        if (strict && known && fix == null && (ignoreUser || !user.isKnown(lower))) {
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
        var list = top.words().filter { !it.equals(w, true) }.map { TextUtil.matchCase(it, w) }.distinct().take(max)
        // Revisión a fondo sin ninguna idea: busca palabras a 2 letras de distancia
        // ("autografia" -> "ortografía"). Más lento, por eso solo aquí.
        if (strict && list.isEmpty() && tf.length in 5..14) {
            val seen = HashSet<String>()
            edits(tf) { v1, p1 ->
                if (seen.add(v1)) edits(v1) { v2, p2 ->
                    for (d in dicts) {
                        val i = d.bestForFold(v2)
                        if (i < 0 || d.logFreq[i] < LN150) continue
                        val key = d.words[i].lowercase()
                        top.offer(key, d.words[i], d.logFreq[i] + langBonus(d, lang) + p1 + p2 + userBonus(key))
                    }
                }
            }
            list = top.words().filter { !it.equals(w, true) }.map { TextUtil.matchCase(it, w) }.distinct().take(max)
        }
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
        // Letra cambiada por otra (si el dedo quedó entre las dos teclas, casi no cuesta)
        val ta = if (w == touchWord) touchAlt else null
        for (i in 0 until n) {
            for (c in ALPHABET) {
                if (c == w[i]) continue
                sb.setLength(0); sb.append(w); sb.setCharAt(i, c)
                visit(sb.toString(), if (ta != null && ta[i] == c) -0.8f else if (Keys.near(w[i], c)) -1.8f else -3.2f)
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
        private val PAST_WORDS = setOf("yo", "ya", "ayer", "anoche", "hoy", "antier", "anteayer", "recién", "recien")
        private val NOT_PAST_BEFORE = setOf("que", "cuando", "si", "aunque", "hasta", "antes", "para", "ojalá", "ojala", "mientras", "apenas", "en cuanto", "cuanto")
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
            "adios" to "adiós", "nose" to "no sé", "nomas" to "no más",             "encambio" to "en cambio", "enfin" to "en fin", "sobretodo" to "sobre todo",
            "talves" to "tal vez", "aver" to "a ver", "porlomenos" to "por lo menos",
            "deveras" to "de veras", "envezde" to "en vez de", "alrato" to "al rato",             "nisiquiera" to "ni siquiera", "otravez" to "otra vez", "devez" to "de vez",         )
        private val JOINED_EN = mapOf(
            "alot" to "a lot", "thankyou" to "thank you", "thanku" to "thank you", "infront" to "in front",
            "atleast" to "at least", "eachother" to "each other", "aswell" to "as well", "incase" to "in case",
            "everytime" to "every time", "alittle" to "a little", "noone" to "no one", "inbetween" to "in between",
            "ofcourse" to "of course", "rightnow" to "right now", "goodmorning" to "good morning",
            "goodnight" to "good night", "seeyou" to "see you", "letme" to "let me", "iam" to "I am",
            "imgoing" to "I'm going", "onmyway" to "on my way", "asap" to "ASAP", "wifi" to "Wi-Fi",
        )
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz"
        private val MIN_ACCENT_LF = ln(30f)
        private val MIN_EDIT_LF = ln(300f)
        /** Para las sugerencias de "¿quisiste decir…?": basta con que sea una palabra real. */
        private val MIN_NEAR_LF = ln(4f)
        private val LN150 = ln(150f)
        private val LN20 = ln(20f)
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
