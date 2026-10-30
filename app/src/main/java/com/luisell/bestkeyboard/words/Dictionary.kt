package com.luisell.bestkeyboard.words

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.ln

/**
 * Un diccionario de un idioma, ordenado por la forma "plegada" (sin acentos),
 * para encontrar rápido todas las palabras que empiezan igual.
 */
class Dictionary private constructor(
    val lang: String,
    val words: Array<String>,
    val folds: Array<String>,
    val logFreq: FloatArray,
) {
    private val exact = HashMap<String, Int>(words.size * 2)
    private val bestByFold = HashMap<String, Int>(words.size * 2)

    init {
        for (i in words.indices) {
            val lower = words[i].lowercase()
            val e = exact[lower]
            if (e == null || logFreq[i] > logFreq[e]) exact[lower] = i
            val b = bestByFold[folds[i]]
            if (b == null || logFreq[i] > logFreq[b]) bestByFold[folds[i]] = i
        }
    }

    /** Solo español: palabras que "suenan igual" (necesito/nesecito, hacer/aser, bien/vien). */
    private val phonetic = HashMap<String, Int>()

    init {
        if (lang == "es") {
            val minLf = ln(100f)
            for (i in words.indices) {
                if (logFreq[i] < minLf) continue
                val k = soundKey(folds[i])
                val b = phonetic[k]
                if (b == null || logFreq[i] > logFreq[b]) phonetic[k] = i
            }
        }
    }

    fun bestForSound(fold: String): Int = if (lang == "es") phonetic[soundKey(fold)] ?: -1 else -1

    val size get() = words.size

    /** Índice de la palabra exacta (sin importar mayúsculas), o -1. */
    fun indexOf(lower: String): Int = exact[lower] ?: -1

    /** La palabra más común con esta forma sin acentos ("tambien" -> "también"), o -1. */
    fun bestForFold(fold: String): Int = bestByFold[fold] ?: -1

    /** Rango de palabras cuya forma plegada empieza con [prefix]. */
    fun prefixRange(prefix: String): IntRange {
        val lo = lowerBound(prefix)
        val hi = lowerBound(prefix + '￿')
        return lo until hi
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = folds.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (folds[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /** Clave de sonido en español: s/c/z, b/v, ll/y, h muda, qu/k, letras dobles. */
        fun soundKey(f: String): String {
            val sb = StringBuilder(f.length)
            var i = 0
            while (i < f.length) {
                val c = f[i]
                val next = if (i + 1 < f.length) f[i + 1] else ' '
                when {
                    c == 'c' && next == 'h' -> { sb.append('X'); i++ }
                    c == 'h' -> Unit
                    c == 'l' && next == 'l' -> { sb.append('y'); i++ }
                    c == 'q' && next == 'u' -> { sb.append('k'); i++ }
                    c == 'c' && (next == 'e' || next == 'i') -> sb.append('s')
                    c == 'c' || c == 'k' -> sb.append('k')
                    c == 'z' -> sb.append('s')
                    c == 'v' || c == 'w' -> sb.append('b')
                    c == 'g' && (next == 'e' || next == 'i') -> sb.append('j')
                    c == 'y' && i == f.length - 1 -> sb.append('i')
                    else -> sb.append(c)
                }
                i++
            }
            // Letras dobles seguidas cuentan como una (carro ~ caro no, pero sirve para dedazos)
            val out = StringBuilder(sb.length)
            for (ch in sb) if (out.isEmpty() || out[out.length - 1] != ch) out.append(ch)
            return out.toString()
        }

        /** Lee un archivo "palabra<TAB>frecuencia" ya ordenado por forma plegada. */
        fun load(lang: String, input: InputStream): Dictionary {
            val words = ArrayList<String>(52000)
            val freqs = ArrayList<Float>(52000)
            BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16).use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    words.add(line.substring(0, tab))
                    val f = line.substring(tab + 1).toLongOrNull() ?: 1L
                    freqs.add(ln(f.toDouble() + 1.0).toFloat())
                }
            }
            val w = words.toTypedArray()
            val folds = Array(w.size) { TextUtil.fold(w[it]) }
            val lf = FloatArray(freqs.size) { freqs[it] }
            return Dictionary(lang, w, folds, lf)
        }
    }
}
