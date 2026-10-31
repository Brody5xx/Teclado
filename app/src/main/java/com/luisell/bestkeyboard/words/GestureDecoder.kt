package com.luisell.bestkeyboard.words

import kotlin.math.hypot
import kotlin.math.ln

/**
 * "Deslizar para escribir": adivina la palabra a partir del trazo del dedo.
 *
 * Cómo funciona, en sencillo:
 *  1. Mira en qué tecla empezó y en cuál terminó el trazo.
 *  2. Toma las palabras que empiezan y terminan con esas letras (en español e inglés).
 *  3. Para cada una dibuja el "camino ideal" por el centro de sus teclas y lo compara con tu trazo.
 *  4. Gana la que más se parece, ayudada por lo común que es la palabra y por lo que tú escribes.
 *
 * Las posiciones se miden en "anchos de tecla", así funciona igual en cualquier pantalla.
 */
class GestureDecoder(private val dicts: List<Dictionary>, private val user: UserData) {

    /** Una tecla: letra y centro (en anchos de tecla). */
    class KeyPos(val ch: Char, val x: Float, val y: Float)

    // Índice: (primera letra, última letra) -> palabras (más comunes primero), por diccionario
    private val index: List<HashMap<Int, IntArray>> = dicts.map { buildIndex(it) }

    private fun pairKey(a: Char, b: Char) = (a.code shl 16) or b.code

    private fun buildIndex(d: Dictionary): HashMap<Int, IntArray> {
        val tmp = HashMap<Int, ArrayList<Int>>()
        val buf = StringBuilder()
        for (i in 0 until d.size) {
            val k = keyChars(d.words[i], buf)
            if (k.length < 2) continue
            tmp.getOrPut(pairKey(k[0], k[k.length - 1])) { ArrayList() }.add(i)
        }
        val out = HashMap<Int, IntArray>(tmp.size * 2)
        for ((key, list) in tmp) {
            list.sortByDescending { d.logFreq[it] }
            val n = minOf(list.size, MAX_PER_PAIR)
            out[key] = IntArray(n) { list[it] }
        }
        return out
    }

    /**
     * Busca las mejores palabras para un trazo.
     * [path] son los puntos del dedo en píxeles; [keys] los centros de las letras en píxeles;
     * [keyWidth] el ancho de una tecla en píxeles.
     */
    fun decode(
        pathX: FloatArray, pathY: FloatArray, count: Int,
        keysPx: List<KeyPos>, keyWidth: Float, lang: String, prev: String?, max: Int = 4,
    ): List<String> {
        if (count < 2 || keysPx.isEmpty() || keyWidth <= 0f) return emptyList()
        // Todo en "anchos de tecla"
        val px = FloatArray(count) { pathX[it] / keyWidth }
        val py = FloatArray(count) { pathY[it] / keyWidth }
        val keys = HashMap<Char, KeyPos>()
        for (k in keysPx) keys[k.ch] = KeyPos(k.ch, k.x / keyWidth, k.y / keyWidth)

        val ux = FloatArray(N)
        val uy = FloatArray(N)
        val userLen = resample(px, py, count, ux, uy)
        if (userLen < 0.8f) return emptyList()

        val starts = nearKeys(keys, px[0], py[0])
        val ends = nearKeys(keys, px[count - 1], py[count - 1])
        val prevLower = prev?.lowercase()

        val tx = FloatArray(N)
        val ty = FloatArray(N)
        val kx = FloatArray(64)
        val ky = FloatArray(64)
        val buf = StringBuilder()
        val best = Top(max * 3)

        fun consider(word: String, lf: Float, other: Boolean) {
            val k = keyChars(word, buf)
            if (k.length < 2 || k.length > 60) return
            var n = 0
            for (c in k) {
                val kp = keys[c] ?: return
                kx[n] = kp.x; ky[n] = kp.y; n++
            }
            val tLen = resample(kx, ky, n, tx, ty)
            if (tLen < userLen * 0.45f || tLen > userLen * 2.2f + 1f) return
            // Cada letra del medio tiene que quedar cerca del trazo
            var miss = 0f
            for (j in 1 until n - 1) {
                var dmin = Float.MAX_VALUE
                for (q in 0 until N) {
                    val dd = hypot(kx[j] - ux[q], ky[j] - uy[q])
                    if (dd < dmin) dmin = dd
                }
                if (dmin > 1.6f) return
                if (dmin > 0.7f) miss += dmin - 0.7f
            }
            var loc = 0f
            for (q in 0 until N) loc += hypot(ux[q] - tx[q], uy[q] - ty[q])
            loc /= N
            val lower = word.lowercase()
            var score = -W_LOC * loc - W_MISS * miss + W_FREQ * lf
            if (other) score -= W_LANG
            val uc = user.count(lower)
            if (uc > 0) score += 0.4f * ln(1f + uc)
            if (prevLower != null) {
                val bc = user.bigramCount(prevLower, lower)
                if (bc > 0) score += 0.6f * ln(1f + bc)
            }
            best.offer(lower, word, score)
        }

        for (s in starts) for (e in ends) {
            val pk = pairKey(s, e)
            for ((di, d) in dicts.withIndex()) {
                val list = index[di][pk] ?: continue
                val other = d.lang != lang
                for (i in list) consider(d.words[i], d.logFreq[i], other)
            }
            // Tus palabras aprendidas que no están en el diccionario (nombres, siglas...)
            for (e2 in user.words.values) {
                if (!e2.known) continue
                val k = keyChars(e2.display, buf)
                if (k.length < 2 || k[0] != s || k[k.length - 1] != e) continue
                if (dicts.any { it.indexOf(e2.display.lowercase()) >= 0 }) continue
                consider(e2.display, 9f, false)
            }
        }
        return best.words(max)
    }

    /** Las teclas cerca de un punto (hasta 3), para no fallar si el dedo empezó en el borde. */
    private fun nearKeys(keys: Map<Char, KeyPos>, x: Float, y: Float): List<Char> {
        val all = keys.values.map { Pair(hypot(it.x - x, it.y - y), it.ch) }.sortedBy { it.first }
        val near = all.filter { it.first <= 1.1f }.take(3).map { it.second }
        return if (near.isEmpty()) listOf(all.first().second) else near
    }

    /** Guarda las mejores sin repetir. */
    private class Top(private val cap: Int) {
        private val keys = ArrayList<String>()
        private val words = ArrayList<String>()
        private val scores = ArrayList<Float>()
        fun offer(key: String, word: String, s: Float) {
            val old = keys.indexOf(key)
            if (old >= 0) {
                if (scores[old] >= s) return
                keys.removeAt(old); words.removeAt(old); scores.removeAt(old)
            } else if (scores.size >= cap && s <= scores[scores.size - 1]) return
            var p = scores.size
            while (p > 0 && scores[p - 1] < s) p--
            keys.add(p, key); words.add(p, word); scores.add(p, s)
            if (scores.size > cap) { keys.removeAt(cap); words.removeAt(cap); scores.removeAt(cap) }
        }
        fun words(max: Int): List<String> = words.take(max)
    }

    companion object {
        const val N = 40
        private const val MAX_PER_PAIR = 1500
        private const val W_LOC = 4.0f
        private const val W_MISS = 1.0f
        private const val W_FREQ = 0.25f
        private const val W_LANG = 0.8f

        /** Letras de la palabra como teclas: sin acentos (pero con ñ), sin repetir seguidas. */
        fun keyChars(word: String, sb: StringBuilder = StringBuilder()): String {
            sb.setLength(0)
            for (ch in word) {
                val c = when (val l = ch.lowercaseChar()) {
                    'á', 'à', 'â', 'ä', 'ã', 'å' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'í', 'ì', 'î', 'ï' -> 'i'
                    'ó', 'ò', 'ô', 'ö', 'õ' -> 'o'
                    'ú', 'ù', 'û', 'ü' -> 'u'
                    'ç' -> 'c'
                    'ý', 'ÿ' -> 'y'
                    else -> l
                }
                if (c !in 'a'..'z' && c != 'ñ') continue
                if (sb.isNotEmpty() && sb[sb.length - 1] == c) continue
                sb.append(c)
            }
            return sb.toString()
        }

        /** Reparte [n] puntos en N puntos iguales a lo largo del camino. Devuelve el largo. */
        fun resample(x: FloatArray, y: FloatArray, n: Int, ox: FloatArray, oy: FloatArray): Float {
            val d = FloatArray(n)
            for (i in 1 until n) d[i] = d[i - 1] + hypot(x[i] - x[i - 1], y[i] - y[i - 1])
            val total = d[n - 1]
            if (total <= 0f) {
                for (k in 0 until N) { ox[k] = x[0]; oy[k] = y[0] }
                return 0f
            }
            var j = 0
            for (k in 0 until N) {
                val t = total * k / (N - 1)
                while (j < n - 2 && d[j + 1] < t) j++
                val seg = d[j + 1] - d[j]
                val f = if (seg == 0f) 0f else (t - d[j]) / seg
                ox[k] = x[j] + (x[j + 1] - x[j]) * f
                oy[k] = y[j] + (y[j + 1] - y[j]) * f
            }
            return total
        }
    }
}
