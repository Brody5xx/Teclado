package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 🇨🇳 Escribir en chino con pinyin: escribes las letras (nihao) y eliges los caracteres (你好).
 *
 * Diccionario: assets/zh_py.txt (pinyin sin tonos → palabras con su frecuencia).
 * Datos: pinyin de mozillazg/pinyin-data y phrase-pinyin-data (MIT); frecuencias de FrequencyWords (CC BY-SA 4.0).
 */
object Pinyin {
    class Cand(val word: String, val consumed: Int)

    @Volatile private var abbr: Map<String, String> = emptyMap()
    private fun abbrOf(key: String): List<Pair<String, Int>> = abbr[key]?.split('\t')?.mapNotNull {
        val sp = it.lastIndexOf(' ')
        if (sp <= 0) null else it.substring(0, sp) to (it.substring(sp + 1).toIntOrNull() ?: 1)
    } ?: emptyList()
    @Volatile private var keys: Array<String> = emptyArray()
    @Volatile private var vals: Array<String> = emptyArray()
    @Volatile var ready = false
        private set
    @Volatile private var loading = false

    /** Carga el diccionario (en segundo plano, una sola vez). */
    fun load(ctx: Context) {
        if (ready || loading) return
        loading = true
        val app = ctx.applicationContext
        Thread {
            try {
                val k = ArrayList<String>(56000)
                val v = ArrayList<String>(56000)
                app.assets.open("zh_py.txt").bufferedReader().forEachLine { l ->
                    val t = l.indexOf('\t')
                    if (t > 0) { k.add(l.substring(0, t)); v.add(l.substring(t + 1)) }
                }
                keys = k.toTypedArray(); vals = v.toTypedArray()
                // 简拼: las iniciales (nh → 你好)
                val am = HashMap<String, String>(13000)
                try {
                    app.assets.open("zh_abbr.txt").bufferedReader().forEachLine { l ->
                        val t = l.indexOf('\t')
                        if (t > 0) am[l.substring(0, t)] = l.substring(t + 1)
                    }
                } catch (_: Exception) { }
                abbr = am
                ready = true
            } catch (_: Exception) { }
            loading = false
        }.start()
        loadLearned(Prefs.get(app))
    }

    private fun find(key: String): Int {
        var lo = 0; var hi = keys.size - 1
        while (lo <= hi) {
            val m = (lo + hi) ushr 1
            val c = keys[m].compareTo(key)
            if (c == 0) return m
            if (c < 0) lo = m + 1 else hi = m - 1
        }
        return -(lo + 1)
    }

    private fun entries(i: Int): List<Pair<String, Int>> = vals[i].split('\t').mapNotNull {
        val s = it.lastIndexOf(' ')
        if (s <= 0) null else it.substring(0, s) to (it.substring(s + 1).toIntOrNull() ?: 1)
    }

    private fun exact(key: String): List<Pair<String, Int>> {
        if (!ready) return emptyList()
        val i = find(key)
        return if (i >= 0) entries(i) else emptyList()
    }

    // ---- Lo que vas eligiendo sube en la lista (se guarda en el teléfono)
    private val learned = HashMap<String, Int>()
    private const val LEARN_KEY = "zh_learned"

    private fun loadLearned(p: SharedPreferences) {
        synchronized(learned) {
            learned.clear()
            (p.getString(LEARN_KEY, "") ?: "").split(';').forEach {
                val c = it.lastIndexOf(':')
                if (c > 0) learned[it.substring(0, c)] = it.substring(c + 1).toIntOrNull() ?: 1
            }
        }
    }

    fun learn(p: SharedPreferences, word: String) {
        if (p.getBoolean(Prefs.INCOGNITO, false)) return
        synchronized(learned) {
            learned[word] = (learned[word] ?: 0) + 1
            val keep = learned.entries.sortedByDescending { it.value }.take(400)
            p.edit().putString(LEARN_KEY, keep.joinToString(";") { "${it.key}:${it.value}" }).apply()
        }
    }

    fun forgetAll(p: SharedPreferences) { synchronized(learned) { learned.clear() }; p.edit().remove(LEARN_KEY).apply() }

    private fun score(w: String, f: Int): Long = f.toLong() + (synchronized(learned) { learned[w] } ?: 0) * 3_000_000L

    /** Las opciones para lo que llevas escrito (la primera es la que pone la barra espaciadora). */
    fun candidates(buf: String, max: Int = 40): List<Cand> {
        if (buf.isEmpty() || !ready) return emptyList()
        val out = ArrayList<Cand>()
        val seen = HashSet<String>()
        fun add(w: String, n: Int) { if (out.size < max && seen.add(w)) out.add(Cand(w, n)) }
        val full = exact(buf).sortedByDescending { score(it.first, it.second) }
        // Frase completa armada por partes (wo ai ni → 我爱你) cuando no hay una palabra exacta
        if (full.isEmpty()) sentence(buf)?.let { add(it, buf.length) }
        full.forEach { add(it.first, buf.length) }
        // Una sola letra (h): los caracteres más usados que empiezan así (好, 还, 会…)
        if (buf.length == 1) {
            var i = find(buf).let { if (it < 0) -(it + 1) else it + 1 }
            val more = ArrayList<Pair<String, Int>>()
            var n = 0
            while (i < keys.size && keys[i].startsWith(buf) && n < 4000) {
                if (keys[i].length <= 6) entries(i).firstOrNull { it.first.length == 1 }?.let { more.add(it) }
                i++; n++
            }
            more.sortedByDescending { score(it.first, it.second) }.take(24).forEach { add(it.first, 1) }
        }
        // Palabras que empiezan así (nih → 你好)
        if (buf.length >= 2) {
            var i = find(buf).let { if (it < 0) -(it + 1) else it + 1 }
            val more = ArrayList<Pair<String, Int>>()
            var n = 0
            while (i < keys.size && keys[i].startsWith(buf) && n < 300) {
                more.addAll(entries(i).take(3)); i++; n++
            }
            more.sortedByDescending { score(it.first, it.second) }.take(if (full.isEmpty()) 12 else 6).forEach { add(it.first, buf.length) }
        }
        // 简拼: solo las iniciales (nh → 你好, xx → 谢谢, wm → 我们). Si no hay palabra entera, van primero
        if (buf.length >= 2) {
            val ab = abbrOf(buf).sortedByDescending { score(it.first, it.second) }
            if (full.isEmpty() && out.isEmpty()) ab.forEach { add(it.first, buf.length) }
            else ab.take(4).forEach { add(it.first, buf.length) }
            // Las iniciales de solo el principio (hdjx → lo de «hdj» y sigue con «x»)
            if (out.isEmpty()) for (k in buf.length - 1 downTo 2) {
                val part = abbrOf(buf.substring(0, k))
                if (part.isNotEmpty()) { part.sortedByDescending { score(it.first, it.second) }.take(8).forEach { add(it.first, k) }; break }
            }
        }
        // Solo el principio (nihaoma → 你好 y queda «ma»)
        for (k in buf.length - 1 downTo 1) {
            val part = exact(buf.substring(0, k))
            if (part.isEmpty()) continue
            part.sortedByDescending { score(it.first, it.second) }.take(if (k == 1) 30 else 8).forEach { add(it.first, k) }
        }
        // Nunca vacío: los caracteres más usados que empiezan con la primera letra
        if (out.isEmpty()) {
            var i = find(buf.substring(0, 1)).let { if (it < 0) -(it + 1) else it }
            val more = ArrayList<Pair<String, Int>>()
            var n = 0
            while (i < keys.size && keys[i].startsWith(buf.substring(0, 1)) && n < 4000) {
                if (keys[i].length <= 6) entries(i).firstOrNull { it.first.length == 1 }?.let { more.add(it) }
                i++; n++
            }
            more.sortedByDescending { score(it.first, it.second) }.take(20).forEach { add(it.first, 1) }
        }
        return out
    }

    /** Parte el pinyin en los pedazos más largos que existen y junta la mejor palabra de cada uno. */
    private fun sentence(buf: String): String? {
        val sb = StringBuilder()
        var i = 0
        var parts = 0
        while (i < buf.length) {
            var best = -1
            for (k in minOf(buf.length, i + 24) downTo i + 1) {
                if (exact(buf.substring(i, k)).isNotEmpty()) { best = k; break }
            }
            if (best < 0) return null
            val w = exact(buf.substring(i, best)).maxByOrNull { score(it.first, it.second) } ?: return null
            sb.append(w.first)
            i = best
            parts++
        }
        return if (parts >= 2) sb.toString() else null
    }

    /** Signos chinos: , → ，  . → 。  ? → ？ … */
    fun punct(t: String): String = when (t) {
        "," -> "，"; "." -> "。"; "?" -> "？"; "!" -> "！"; ":" -> "："; ";" -> "；"
        "(" -> "（"; ")" -> "）"; "\"" -> "“"; "'" -> "‘"
        else -> t
    }
}

/** 🇨🇳 Escribir con los 5 trazos (一丨丿丶乛) en el orden en que se escribe el carácter. Datos: rime-stroke (LGPL). */
object Stroke {
    @Volatile private var codes: Array<String> = emptyArray()
    @Volatile private var chars: Array<String> = emptyArray()
    @Volatile private var freqs: IntArray = IntArray(0)
    @Volatile var ready = false
        private set
    @Volatile private var loading = false

    fun load(ctx: Context) {
        if (ready || loading) return
        loading = true
        val app = ctx.applicationContext
        Thread {
            try {
                val c = ArrayList<String>(8200); val h = ArrayList<String>(8200); val f = ArrayList<Int>(8200)
                app.assets.open("zh_stroke.txt").bufferedReader().forEachLine { l ->
                    val p = l.split('\t')
                    if (p.size >= 3) { c.add(p[0]); h.add(p[1]); f.add(p[2].toIntOrNull() ?: 0) }
                }
                codes = c.toTypedArray(); chars = h.toTypedArray(); freqs = f.toIntArray()
                ready = true
            } catch (_: Exception) { }
            loading = false
        }.start()
    }

    /** Los caracteres que empiezan con esos trazos: primero los exactos, luego los más usados. */
    fun candidates(buf: String, max: Int = 40): List<Pinyin.Cand> {
        if (buf.isEmpty() || !ready) return emptyList()
        val exact = ArrayList<Int>(); val more = ArrayList<Int>()
        for (i in codes.indices) {
            val c = codes[i]
            if (c == buf) exact.add(i) else if (c.startsWith(buf)) more.add(i)
        }
        exact.sortByDescending { freqs[it] }
        more.sortByDescending { freqs[it] }
        return (exact + more).take(max).map { Pinyin.Cand(chars[it], buf.length) }
    }
}

/** La fila de caracteres chinos para elegir (tapa la barra de sugerencias mientras escribes pinyin). */
@SuppressLint("ViewConstructor")
class PinyinBar(context: Context) : HorizontalScrollView(context) {
    var onPick: ((Int) -> Unit)? = null
    var onFeedback: (() -> Unit)? = null
    private val row = LinearLayout(context)
    private val pinyin = TextView(context)
    private var pal = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    init {
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        addView(row, LayoutParams(-2, -1))
    }

    /**
     * Se ve IGUAL que la barra de sugerencias de los otros idiomas: el mismo tamaño de letra, 3 casillas
     * por pantalla con la rayita gris entre ellas, el primero en negrita y nada de fondo.
     * A la izquierda (donde va el botón de herramientas) va el pinyin que llevas; desliza para ver más.
     */
    fun show(buf: String, list: List<Pinyin.Cand>) {
        row.removeAllViews()
        val h = (layoutParams?.height ?: 0).takeIf { it > 0 } ?: d(46f)
        val menuW = d(48f)
        val w = (if (width > 0) width else resources.displayMetrics.widthPixels)
        val cellW = (w - menuW) / 3
        val textPx = h * 0.37f
        pinyin.text = buf
        pinyin.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, h * 0.27f)
        pinyin.setTextColor(pal.hint)
        pinyin.gravity = Gravity.CENTER
        pinyin.maxLines = 1
        pinyin.ellipsize = android.text.TextUtils.TruncateAt.START
        pinyin.setPadding(d(4f), 0, d(4f), 0)
        row.addView(pinyin, LinearLayout.LayoutParams(menuW, -1))
        row.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        row.dividerDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor((pal.hint and 0x00FFFFFF) or (70 shl 24)); setSize(maxOf(1, d(1f)), (h * 0.4f).toInt())
        }
        row.dividerPadding = (h * 0.3f).toInt()
        list.forEachIndexed { i, c ->
            val t = TextView(context)
            t.text = c.word
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textPx)
            t.typeface = if (i == 0) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            t.setTextColor(pal.text)
            t.gravity = Gravity.CENTER
            t.maxLines = 1
            t.includeFontPadding = false
            t.background = PanelUi.pressable(0x00000000, pal.keyPressed, d(10f).toFloat())
            t.setOnClickListener { onFeedback?.invoke(); onPick?.invoke(i) }
            // Las palabras largas (frases) ocupan lo que necesiten; las demás, una casilla como las sugerencias
            t.measure(0, 0)
            row.addView(t, LinearLayout.LayoutParams(maxOf(cellW, t.measuredWidth + d(16f)), -1))
        }
        scrollTo(0, 0)
        visibility = VISIBLE
    }

    fun hide() { visibility = GONE; row.removeAllViews() }

    fun applyPalette(p: Palette) {
        pal = p
        // Transparente, igual que las sugerencias de los otros idiomas: se ve el fondo y el destello del teclado
        // (los botones de la barra se esconden mientras escribes, así que no hay nada encima)
        background = null
        isClickable = true // los toques no pasan a la barra de atrás
    }
}
