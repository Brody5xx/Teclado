package com.luisell.bestkeyboard.words

import java.io.File

/**
 * Lo que el teclado aprende de ti: tus palabras (nombres, palabras del trabajo...)
 * y qué palabra sueles escribir después de otra. Se guarda solo en tu teléfono.
 */
class UserData {

    /**
     * [sure] = la confirmaste tú ("+ Mi palabra", tocarla entre comillas, deshacer una corrección).
     * [legacy] = guardada por una versión vieja (se respeta como antes).
     */
    class Entry(var display: String, var count: Int, var sure: Boolean = false, var legacy: Boolean = false, var en: Int = 0, var es: Int = 0,
                var typo: Boolean = false) {
        val fold = TextUtil.fold(display)
        /**
         * Cuenta como palabra tuya: confirmada, o escrita varias veces. Si parece un error de dedo
         * ([typo]: hay una palabra casi igual, como "clmo" ~ "como"), hace falta escribirla más veces.
         */
        val known get() = sure || count >= (if (typo) TYPO_KNOWN else if (legacy) KNOWN else AUTO_KNOWN)
    }

    val words = HashMap<String, Entry>()
    /** Cuándo la escribiste por última vez (solo mientras el teclado está abierto; para que lo reciente salga primero). */
    val recent = HashMap<String, Long>()
    val bigrams = HashMap<String, HashMap<String, Int>>()
    /** Las 3 palabras seguidas que escribes ("nos vemos" → "mañana"). Clave: "nos vemos". */
    val trigrams = HashMap<String, HashMap<String, Int>>()
    /** Tus arreglos: escribiste "clmo", lo borraste y pusiste "como" → la próxima vez se corrige solo. */
    val fixes = HashMap<String, Pair<String, Int>>()

    fun addFix(typo: String, fix: String) {
        val k = typo.lowercase()
        val old = fixes[k]
        fixes[k] = fix to (if (old != null && old.first.equals(fix, true)) (old.second + 1).coerceAtMost(1000) else 1)
        if (!isSure(k)) words.remove(k)
        dirty = true
        if (fixes.size > 3000) fixes.entries.minByOrNull { it.value.second }?.let { fixes.remove(it.key) }
    }

    fun fixFor(lower: String): String? = fixes[lower]?.first
    /** Palabras que borraste de las sugerencias (mantener presionada → Borrar): no vuelven a salir. */
    val blocked = HashSet<String>()
    @Volatile var dirty = false

    fun isBlocked(lower: String): Boolean = lower in blocked

    /** Borra la palabra de lo aprendido y no la vuelve a sugerir nunca. */
    fun block(display: String) {
        val key = display.lowercase()
        words.remove(key)
        recent.remove(key)
        bigrams.remove(key)
        for (m in bigrams.values) m.remove(key)
        trigrams.keys.filter { it.split(' ').contains(key) }.forEach { trigrams.remove(it) }
        for (m in trigrams.values) m.remove(key)
        blocked.add(key)
        dirty = true
    }

    fun unblock(lower: String) { if (blocked.remove(lower)) dirty = true }

    fun count(lower: String): Int = words[lower]?.count ?: 0

    /** Una palabra cuenta como "tuya" cuando la confirmaste o la escribiste 4 veces. */
    fun isKnown(lower: String): Boolean = words[lower]?.known == true

    /** Solo las que confirmaste tú (para la revisión de ortografía a fondo). */
    fun isSure(lower: String): Boolean = words[lower]?.sure == true

    fun entry(lower: String): Entry? = words[lower]

    /** ¿Cuántas veces la escribiste en [lang]? ("es" o "en") */
    fun countIn(lower: String, lang: String): Int {
        val e = words[lower] ?: return 0
        return if (lang == "en") e.en else e.es
    }

    fun add(display: String, amount: Int = 1, lang: String? = null, typo: Boolean = false) {
        val key = display.lowercase()
        if (key in blocked) return
        val e = words[key]
        val enAdd = if (lang == "en") amount else 0
        val esAdd = if (lang == "es") amount else 0
        if (e == null) {
            words[key] = Entry(display, amount, en = enAdd, es = esAdd, typo = typo)
        } else {
            e.count = (e.count + amount).coerceAtMost(100000)
            e.en = (e.en + enAdd).coerceAtMost(100000)
            e.es = (e.es + esAdd).coerceAtMost(100000)
            // Si la escribes con una mayúscula especial (ej. "Luisell"), se recuerda así
            if (display != key && e.display == key) words[key] = Entry(display, e.count, e.sure, e.legacy, e.en, e.es, e.typo)
        }
        recent[key] = System.currentTimeMillis()
        if (recent.size > 400) recent.entries.minByOrNull { it.value }?.let { recent.remove(it.key) }
        dirty = true
        if (words.size > MAX_WORDS) trimWords()
    }

    /** Confirmar una palabra: desde ya no se autocorrige. */
    fun learn(display: String) {
        val key = display.lowercase()
        fixes.remove(key) // la confirmaste tal cual: ya no se cambia
        unblock(key) // la confirmaste tú: vuelve a ser tuya
        val c = count(key)
        if (c < KNOWN) add(display, KNOWN - c) else add(display, 1)
        words[key]?.let { it.sure = true; it.typo = false }
    }

    /** Deshiciste una corrección: no la vuelve a corregir, pero la revisión a fondo sí la revisa. */
    fun learnSoft(display: String) {
        val c = count(display.lowercase())
        if (c < AUTO_KNOWN) add(display, AUTO_KNOWN - c) else add(display, 1)
    }

    fun forget(lower: String) {
        if (words.remove(lower) != null) dirty = true
    }

    fun addBigram(prev: String, next: String) {
        val p = prev.lowercase()
        val n = next.lowercase()
        if (p in blocked || n in blocked) return
        val m = bigrams.getOrPut(p) { HashMap() }
        m[n] = ((m[n] ?: 0) + 1).coerceAtMost(100000)
        dirty = true
        if (bigrams.size > MAX_BIGRAM_KEYS) trimBigrams()
    }

    fun bigramCount(prevLower: String, nextLower: String): Int = bigrams[prevLower]?.get(nextLower) ?: 0

    fun addTrigram(prev2: String, prev: String, next: String) {
        val k = prev2.lowercase() + " " + prev.lowercase()
        val n = next.lowercase()
        if (n in blocked || prev.lowercase() in blocked || prev2.lowercase() in blocked) return
        val m = trigrams.getOrPut(k) { HashMap() }
        m[n] = ((m[n] ?: 0) + 1).coerceAtMost(100000)
        dirty = true
        if (trigrams.size > MAX_TRIGRAM_KEYS) {
            val drop = trigrams.entries.sortedBy { it.value.values.sum() }.take(trigrams.size - MAX_TRIGRAM_KEYS * 9 / 10)
            for (e in drop) trigrams.remove(e.key)
        }
    }

    fun trigramCount(ctxLower: String, nextLower: String): Int = trigrams[ctxLower]?.get(nextLower) ?: 0

    fun clear() {
        blocked.clear()
        trigrams.clear()
        fixes.clear()
        words.clear()
        bigrams.clear()
        dirty = true
    }

    private fun trimWords() {
        val drop = words.entries.sortedBy { it.value.count }.take(words.size - MAX_WORDS * 9 / 10)
        for (e in drop) words.remove(e.key)
    }

    private fun trimBigrams() {
        val drop = bigrams.entries.sortedBy { it.value.values.sum() }.take(bigrams.size - MAX_BIGRAM_KEYS * 9 / 10)
        for (e in drop) bigrams.remove(e.key)
    }

    // ---------------------------------------------------------------- Guardar / cargar

    /** Copia el contenido a texto (se llama en el hilo principal, se escribe en otro hilo). */
    fun snapshot(): String {
        val sb = StringBuilder()
        for ((_, e) in words) {
            sb.append("w\t").append(e.display).append('\t').append(e.count).append('\t')
                .append(if (e.sure) "1" else if (e.legacy) "L" else if (e.typo) "T" else "0").append('\t').append(e.en).append('\t').append(e.es)
                .append('\t').append(recent[e.display.lowercase()] ?: 0L).append('\n')
        }
        for ((p, m) in bigrams) for ((n, c) in m) {
            sb.append("b\t").append(p).append('\t').append(n).append('\t').append(c).append('\n')
        }
        for ((p, m) in trigrams) for ((n, c) in m) {
            sb.append("t\t").append(p).append('\t').append(n).append('\t').append(c).append('\n')
        }
        for (x in blocked) sb.append("x\t").append(x).append('\n')
        for ((k, v) in fixes) sb.append("f\t").append(k).append('\t').append(v.first).append('\t').append(v.second).append('\n')
        return sb.toString()
    }

    fun load(file: File) {
        if (!file.exists()) return
        file.forEachLine(Charsets.UTF_8) { line ->
            val p = line.split('\t')
            when {
                (p.size in 3..7) && p[0] == "w" -> {
                    val c = p[2].toIntOrNull() ?: return@forEachLine
                    val flag = if (p.size >= 4) p[3] else "L" // versión vieja: se respeta como antes
                    val en = if (p.size >= 5) (p[4].toIntOrNull() ?: 0) else 0
                    val es = if (p.size >= 6) (p[5].toIntOrNull() ?: 0) else 0
                    words[p[1].lowercase()] = Entry(p[1], c, sure = flag == "1", legacy = flag == "L", en = en, es = es, typo = flag == "T")
                    // Cuándo la escribiste por última vez (así lo reciente sigue saliendo primero después de actualizar)
                    val t = if (p.size >= 7) (p[6].toLongOrNull() ?: 0L) else 0L
                    if (t > 0) recent[p[1].lowercase()] = t
                }
                p.size == 2 && p[0] == "x" -> blocked.add(p[1])
                p.size == 4 && p[0] == "f" -> fixes[p[1]] = p[2] to (p[3].toIntOrNull() ?: 1)
                p.size == 4 && p[0] == "t" -> {
                    val c = p[3].toIntOrNull() ?: return@forEachLine
                    trigrams.getOrPut(p[1]) { HashMap() }[p[2]] = c
                }
                p.size == 4 && p[0] == "b" -> {
                    val c = p[3].toIntOrNull() ?: return@forEachLine
                    bigrams.getOrPut(p[1]) { HashMap() }[p[2]] = c
                }
            }
        }
        if (recent.size > 400) {
            val keep = recent.entries.sortedByDescending { it.value }.take(400).map { it.key to it.value }
            recent.clear(); for ((k, v) in keep) recent[k] = v
        }
    }

    companion object {
        const val KNOWN = 2
        /** Palabras que no están en el diccionario: con escribirlas 2 veces ya las aprende sola. */
        const val AUTO_KNOWN = 2
        /** Las que parecen error de dedo ("clmo", "actulizo"): hay que escribirlas 4 veces. */
        const val TYPO_KNOWN = 4
        const val MAX_WORDS = 20000
        const val MAX_BIGRAM_KEYS = 15000
        const val MAX_TRIGRAM_KEYS = 15000
    }
}
