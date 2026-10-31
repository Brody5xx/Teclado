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
    class Entry(var display: String, var count: Int, var sure: Boolean = false, var legacy: Boolean = false) {
        val fold = TextUtil.fold(display)
        /** Cuenta como palabra tuya: confirmada, o escrita varias veces. */
        val known get() = sure || count >= (if (legacy) KNOWN else AUTO_KNOWN)
    }

    val words = HashMap<String, Entry>()
    val bigrams = HashMap<String, HashMap<String, Int>>()
    @Volatile var dirty = false

    fun count(lower: String): Int = words[lower]?.count ?: 0

    /** Una palabra cuenta como "tuya" cuando la confirmaste o la escribiste 4 veces. */
    fun isKnown(lower: String): Boolean = words[lower]?.known == true

    /** Solo las que confirmaste tú (para la revisión de ortografía a fondo). */
    fun isSure(lower: String): Boolean = words[lower]?.sure == true

    fun entry(lower: String): Entry? = words[lower]

    fun add(display: String, amount: Int = 1) {
        val key = display.lowercase()
        val e = words[key]
        if (e == null) {
            words[key] = Entry(display, amount)
        } else {
            e.count = (e.count + amount).coerceAtMost(100000)
            // Si la escribes con una mayúscula especial (ej. "Luisell"), se recuerda así
            if (display != key && e.display == key) words[key] = Entry(display, e.count, e.sure, e.legacy)
        }
        dirty = true
        if (words.size > MAX_WORDS) trimWords()
    }

    /** Confirmar una palabra: desde ya no se autocorrige. */
    fun learn(display: String) {
        val key = display.lowercase()
        val c = count(key)
        if (c < KNOWN) add(display, KNOWN - c) else add(display, 1)
        words[key]?.sure = true
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
        val m = bigrams.getOrPut(p) { HashMap() }
        m[n] = ((m[n] ?: 0) + 1).coerceAtMost(100000)
        dirty = true
        if (bigrams.size > MAX_BIGRAM_KEYS) trimBigrams()
    }

    fun bigramCount(prevLower: String, nextLower: String): Int = bigrams[prevLower]?.get(nextLower) ?: 0

    fun clear() {
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
                .append(if (e.sure) "1" else if (e.legacy) "L" else "0").append('\n')
        }
        for ((p, m) in bigrams) for ((n, c) in m) {
            sb.append("b\t").append(p).append('\t').append(n).append('\t').append(c).append('\n')
        }
        return sb.toString()
    }

    fun load(file: File) {
        if (!file.exists()) return
        file.forEachLine(Charsets.UTF_8) { line ->
            val p = line.split('\t')
            when {
                (p.size == 3 || p.size == 4) && p[0] == "w" -> {
                    val c = p[2].toIntOrNull() ?: return@forEachLine
                    val flag = if (p.size == 4) p[3] else "L" // versión vieja: se respeta como antes
                    words[p[1].lowercase()] = Entry(p[1], c, sure = flag == "1", legacy = flag == "L")
                }
                p.size == 4 && p[0] == "b" -> {
                    val c = p[3].toIntOrNull() ?: return@forEachLine
                    bigrams.getOrPut(p[1]) { HashMap() }[p[2]] = c
                }
            }
        }
    }

    companion object {
        const val KNOWN = 2
        /** Palabras que no están en el diccionario: hay que escribirlas 4 veces para aprenderlas solas. */
        const val AUTO_KNOWN = 4
        const val MAX_WORDS = 20000
        const val MAX_BIGRAM_KEYS = 15000
    }
}
