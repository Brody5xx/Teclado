package com.luisell.bestkeyboard.words

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * 📚 Qué palabra suele venir después (de frases reales en español e inglés).
 * Así el teclado adivina la siguiente palabra desde el primer día, aunque todavía no haya
 * aprendido de ti. Lo que aprende de ti siempre pesa más.
 */
class NGrams {
    private class Next(val words: Array<String>, val counts: IntArray)
    private val map = HashMap<String, Next>(60000)

    fun load(input: InputStream) {
        // Las mismas palabras se repiten miles de veces: se guarda una sola copia de cada una (menos memoria)
        val pool = HashMap<String, String>(80000)
        fun intern(x: String): String = pool.getOrPut(x) { x }
        var curKey: String? = null
        val ws = ArrayList<String>(8)
        val cs = ArrayList<Int>(8)
        fun flush() {
            val k = curKey ?: return
            map[k] = Next(ws.toTypedArray(), IntArray(cs.size) { cs[it] })
            ws.clear(); cs.clear()
        }
        BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16).use { r ->
            while (true) {
                val line = r.readLine() ?: break
                val p = line.split('\t')
                if (p.size != 5) continue
                val key = p[0] + "|" + p[1] + "|" + p[2]
                if (key != curKey) { flush(); curKey = key }
                ws.add(intern(p[3]))
                cs.add(p[4].toIntOrNull() ?: 1)
            }
        }
        flush()
    }

    /** Las palabras que siguen a [ctx] ("thank" o "thank you" para [kind] "b" o "t"). */
    fun next(kind: String, lang: String, ctx: String, visit: (String, Int) -> Unit) {
        val n = map["$kind|$lang|$ctx"] ?: return
        for (i in n.words.indices) visit(n.words[i], n.counts[i])
    }

    fun count(kind: String, lang: String, ctx: String, word: String): Int {
        val n = map["$kind|$lang|$ctx"] ?: return 0
        for (i in n.words.indices) if (n.words[i] == word) return n.counts[i]
        return 0
    }
}
