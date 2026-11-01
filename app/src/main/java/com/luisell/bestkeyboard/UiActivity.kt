package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * Base de las pantallas de la app (Ajustes y Estilo): colores oscuros, tarjetas, interruptores,
 * opciones en píldora y la galería de temas. Todo en español o inglés.
 */
abstract class UiActivity : Activity() {
    protected lateinit var prefs: SharedPreferences
    /** La columna principal y [col] = la tarjeta donde se agregan las opciones. */
    protected lateinit var root: LinearLayout
    protected lateinit var col: LinearLayout

    /** Idioma de la pantalla: el del teléfono, o el que elijas. */
    protected var en = false
    protected fun tr(es: String, en: String) = if (this.en) en else es
    protected fun toast(es: String, en: String) = Toast.makeText(this, tr(es, en), Toast.LENGTH_LONG).show()
    protected fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    protected fun dpf(v: Float) = v * resources.displayMetrics.density

    /** Lee el idioma elegido para las pantallas. */
    protected fun loadLang() {
        en = when (prefs.getString(Prefs.APP_LANG, "auto")) {
            "es" -> false
            "en" -> true
            else -> java.util.Locale.getDefault().language != "es"
        }
    }

    companion object {
        const val BG = MainActivity.BG
        const val CARD = MainActivity.CARD
        const val CARD2 = MainActivity.CARD2
        const val LINE = MainActivity.LINE
        const val TEXT = MainActivity.TEXT
        const val SUB = MainActivity.SUB
        const val CYAN = MainActivity.CYAN
        const val VIOLET = MainActivity.VIOLET
    }

    // ---------------------------------------------------------------- Temas

    /** Galería de temas: tarjetas con un mini teclado de cada tema (desliza a los lados). */
    protected fun themeGallery() {
        val t = TextView(this)
        t.text = tr("Tema", "Theme")
        t.textSize = 15f
        t.setTextColor(TEXT)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val cards = ArrayList<ThemeCard>()
        val current = prefs.getString(Prefs.THEME, "auto")
        for ((id, names, pal) in Palette.THEMES) {
            val c = ThemeCard(this, pal, tr(names.first, names.second))
            c.chosen = id == current
            c.setOnClickListener {
                prefs.edit().putString(Prefs.THEME, id).apply()
                cards.forEach { it.chosen = false }
                c.chosen = true
            }
            cards.add(c)
            row.addView(c, LinearLayout.LayoutParams(dp(104), dp(118)).apply { rightMargin = dp(10) })
        }
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(row)
        col.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(6) })
    }

    /** Círculos de colores para el acento (Enter, botones encendidos…). */
    protected fun accentSwatches() {
        divider()
        val t = TextView(this)
        t.text = tr("Color de acento", "Accent color")
        t.textSize = 15f
        t.setTextColor(TEXT)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val dots = ArrayList<Pair<String, View>>()
        val cur = prefs.getString(Prefs.ACCENT, "") ?: ""
        fun paint(sel: String) {
            for ((v, view) in dots) {
                val color = if (v.isEmpty()) LINE else v.toLong(16).toInt()
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (v.isEmpty()) {
                        colors = intArrayOf(CYAN, VIOLET)
                        gradientType = GradientDrawable.SWEEP_GRADIENT
                    } else setColor(color)
                    setStroke(dp(if (v == sel) 3 else 1), if (v == sel) TEXT else LINE)
                }
            }
        }
        val options = listOf("") + Palette.ACCENTS.map { String.format("%08X", it) }
        for (v in options) {
            val dot = View(this)
            dot.setOnClickListener {
                prefs.edit().putString(Prefs.ACCENT, v).apply()
                paint(v)
            }
            dots.add(v to dot)
            row.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(10) })
        }
        paint(cur)
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(row)
        col.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(4) })
        text(tr("El primero (multicolor) usa el color que trae el tema.", "The first one (multicolor) uses the theme's own color."), 12f, faded = true)
    }

    /** Fila de colores para una opción ([key]); el primero (multicolor) = el del tema. */
    protected fun colorSwatches(label: String, key: String, choices: List<String>, help: String) {
        divider()
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTextColor(TEXT)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val dots = ArrayList<Pair<String, View>>()
        val cur = prefs.getString(key, "") ?: ""
        fun paint(sel: String) {
            for ((v, view) in dots) {
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    when (v) {
                        "" -> { colors = intArrayOf(CYAN, VIOLET); gradientType = GradientDrawable.SWEEP_GRADIENT }
                        "rainbow" -> {
                            colors = intArrayOf(0xFFFF1744.toInt(), 0xFFFFC400.toInt(), 0xFF00E676.toInt(), 0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF1744.toInt())
                            gradientType = GradientDrawable.SWEEP_GRADIENT
                        }
                        else -> setColor(v.toLong(16).toInt())
                    }
                    setStroke(dp(if (v == sel) 3 else 1), if (v == sel) TEXT else LINE)
                }
            }
        }
        for (v in listOf("") + choices) {
            val dot = View(this)
            dot.setOnClickListener {
                prefs.edit().putString(key, v).apply()
                paint(v)
            }
            dots.add(v to dot)
            row.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(10) })
        }
        paint(cur)
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(row)
        col.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(4) })
        text(help, 12f, faded = true)
    }

    /** Colores del tema Hacker (teclas y lluvia de código). El último es arcoíris. */
    /** Colores del tema Hacker, dentro de su propia sección (para poder esconderla). */
    protected fun hackerSwatches(): LinearLayout {
        val outer = col
        val section = LinearLayout(this)
        section.orientation = LinearLayout.VERTICAL
        outer.addView(section, LinearLayout.LayoutParams(-1, -2))
        col = section
        divider()
        val t = TextView(this)
        t.text = tr("Color del tema Hacker", "Hacker theme color")
        t.textSize = 15f
        t.setTextColor(TEXT)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val dots = ArrayList<Pair<String, View>>()
        val cur = (prefs.getString(Prefs.HACKER_COLOR, "") ?: "").ifEmpty { Palette.HACKER_COLORS[0] }
        fun paint(sel: String) {
            for ((v, view) in dots) {
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (v == "rainbow") {
                        colors = intArrayOf(0xFFFF1744.toInt(), 0xFFFFC400.toInt(), 0xFF00E676.toInt(), 0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF1744.toInt())
                        gradientType = GradientDrawable.SWEEP_GRADIENT
                    } else setColor(v.toLong(16).toInt())
                    setStroke(dp(if (v == sel) 3 else 1), if (v == sel) TEXT else LINE)
                }
            }
        }
        for (v in Palette.HACKER_COLORS) {
            val dot = View(this)
            dot.setOnClickListener {
                // Elegir un color también pone el tema Hacker, para verlo de una vez
                prefs.edit().putString(Prefs.HACKER_COLOR, v).putString(Prefs.THEME, "hacker").apply()
                paint(v)
            }
            dots.add(v to dot)
            row.addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(10) })
        }
        paint(cur)
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(row)
        col.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(4) })
        text(tr("Al elegir un color se pone el tema Hacker. El último es arcoíris 🌈.", "Picking a color switches to the Hacker theme. The last one is rainbow 🌈."), 12f, faded = true)
        col = outer
        return section
    }

    /** La animación que está corriendo en cada sección (para cancelarla si cambias rápido). */
    private val slideAnims = HashMap<View, android.animation.Animator>()

    /** Abre una sección deslizándola hacia abajo (crece suave y aparece). */
    protected fun slideOpen(v: View) {
        if (v.visibility == View.VISIBLE && v.tag != "closing") return
        v.animate().cancel()
        slideAnims.remove(v)?.cancel()
        v.tag = "opening"
        val parentW = (v.parent as? View)?.width ?: 0
        v.measure(View.MeasureSpec.makeMeasureSpec(maxOf(parentW, 1), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val target = v.measuredHeight
        val start = if (v.visibility == View.VISIBLE) v.height else 0
        v.layoutParams = v.layoutParams.apply { height = start }
        v.visibility = View.VISIBLE
        v.alpha = if (start == 0) 0f else v.alpha
        v.translationY = if (start == 0) -dpf(14f) else v.translationY
        val a = android.animation.ValueAnimator.ofInt(start, target)
        a.duration = 380
        a.interpolator = android.view.animation.DecelerateInterpolator(1.8f)
        a.addUpdateListener {
            val h = it.animatedValue as Int
            v.layoutParams = v.layoutParams.apply { height = h }
            val f = it.animatedFraction
            v.alpha = maxOf(v.alpha, f)
            v.translationY = -dpf(14f) * (1f - f)
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (v.tag != "opening") return
                v.layoutParams = v.layoutParams.apply { height = LinearLayout.LayoutParams.WRAP_CONTENT }
                v.alpha = 1f
                v.translationY = 0f
                v.tag = null
            }
        })
        slideAnims[v] = a
        a.start()
    }

    /** Cierra una sección deslizándola hacia arriba y la esconde. */
    protected fun slideClose(v: View) {
        if (v.visibility != View.VISIBLE || v.tag == "closing") return
        slideAnims.remove(v)?.cancel()
        v.tag = "closing"
        val start = v.height
        val a = android.animation.ValueAnimator.ofInt(start, 0)
        a.duration = 280
        a.interpolator = android.view.animation.AccelerateInterpolator(1.4f)
        a.addUpdateListener {
            v.layoutParams = v.layoutParams.apply { height = it.animatedValue as Int }
            val f = it.animatedFraction
            v.alpha = 1f - f
            v.translationY = -dpf(14f) * f
        }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (v.tag != "closing") return
                v.visibility = View.GONE
                v.layoutParams = v.layoutParams.apply { height = LinearLayout.LayoutParams.WRAP_CONTENT }
                v.translationY = 0f
                v.alpha = 1f
                v.tag = null
            }
        })
        slideAnims[v] = a
        a.start()
    }

    /** Tarjeta con un mini teclado dibujado con los colores del tema. */
    @SuppressLint("ViewConstructor")
    private class ThemeCard(context: Context, private val pal: Palette?, private val name: String) : View(context) {
        var chosen = false
            set(v) { field = v; invalidate() }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private val dd = resources.displayMetrics.density

        init { isClickable = true }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val boardH = h - 26 * dd
            r.set(0f, 0f, w, boardH)
            // "Automático": mitad clara, mitad oscura
            val a = pal ?: Palette.DARK
            if (pal == null) {
                p.color = Palette.LIGHT.bg
                c.drawRoundRect(r, 16 * dd, 16 * dd, p)
                c.save()
                c.clipRect(w / 2, 0f, w, boardH)
                p.color = Palette.DARK.bg
                c.drawRoundRect(r, 16 * dd, 16 * dd, p)
                c.restore()
            } else {
                p.shader = LinearGradient(0f, 0f, 0f, boardH, a.bg, a.bg2, Shader.TileMode.CLAMP)
                c.drawRoundRect(r, 16 * dd, 16 * dd, p)
                p.shader = null
            }
            // 3 filas de teclitas
            val pad = 8 * dd
            val gap = 3 * dd
            val rows = 3
            val kh = (boardH - pad * 2 - gap * (rows - 1)) / rows
            for (row in 0 until rows) {
                val n = if (row == 2) 5 else 6
                val kw = (w - pad * 2 - gap * (n - 1)) / n
                for (i in 0 until n) {
                    val left = pad + i * (kw + gap)
                    val top = pad + row * (kh + gap)
                    r.set(left, top, left + kw, top + kh)
                    val half = pal == null && left > w / 2
                    val kp = if (pal == null) (if (half) Palette.DARK else Palette.LIGHT) else a
                    p.color = if (row == 2 && i == n - 1) kp.accent else kp.key
                    c.drawRoundRect(r, 3 * dd, 3 * dd, p)
                }
            }
            if (chosen) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3 * dd
                p.shader = LinearGradient(0f, 0f, w, 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
                r.set(1.5f * dd, 1.5f * dd, w - 1.5f * dd, boardH - 1.5f * dd)
                c.drawRoundRect(r, 15 * dd, 15 * dd, p)
                p.shader = null
                p.style = Paint.Style.FILL
            }
            p.color = if (chosen) TEXT else SUB
            p.textSize = 12.5f * dd
            p.textAlign = Paint.Align.CENTER
            p.typeface = if (chosen) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            c.drawText(name, w / 2, h - 7 * dd, p)
        }
    }

    // ---------------------------------------------------------------- Ayudantes de diseño

    /** Empieza una tarjeta nueva con título e ícono; lo que se agregue después va dentro. */
    /** Palabras extra para el buscador de ajustes (por tarjeta). "-" = no sale en la búsqueda. */
    protected val cardKeywords = HashMap<View, String>()

    protected fun card(title: String, icon: Int, glow: Boolean = false, keywords: String = "") {
        val head = LinearLayout(this)
        head.tag = "cardHead"
        if (keywords.isNotEmpty()) cardKeywords[head] = keywords
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val iv = IconView(this, icon, 8f)
        iv.isClickable = false
        iv.color = CYAN
        iv.bgColor = BG
        head.addView(iv, LinearLayout.LayoutParams(dp(22), dp(22)))
        val t = TextView(this)
        t.text = title.uppercase()
        t.textSize = 12f
        t.letterSpacing = 0.12f
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        t.setTextColor(SUB)
        t.setPadding(dp(10), 0, 0, 0)
        head.addView(t)
        root.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28); bottomMargin = dp(10); leftMargin = dp(4) })

        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(10), dp(16), dp(14))
        c.background = GradientDrawable().apply {
            setColor(CARD)
            cornerRadius = dpf(22f)
            if (glow) setStroke(dp(1), 0x804FD1FF.toInt()) else setStroke(dp(1), LINE)
        }
        c.tag = "cardBody"
        root.addView(c, LinearLayout.LayoutParams(-1, -2))
        col = c
    }

    /** Todo el texto de una vista (para buscar). */
    private fun allText(v: View, sb: StringBuilder) {
        if (v is TextView) { sb.append(' ').append(v.text); v.hint?.let { sb.append(' ').append(it) } }
        if (v is android.view.ViewGroup) for (i in 0 until v.childCount) allText(v.getChildAt(i), sb)
    }

    private fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    /** Muestra solo las tarjetas que tienen lo que buscas. Devuelve cuántas quedaron. */
    protected fun filterCards(query: String): Int {
        val words = norm(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        var shown = 0
        var i = 0
        while (i < root.childCount) {
            val head = root.getChildAt(i)
            if (head.tag != "cardHead" || i + 1 >= root.childCount) { i++; continue }
            val body = root.getChildAt(i + 1)
            val kw = cardKeywords[head] ?: ""
            val ok = if (words.isEmpty()) true else if (kw == "-") false else {
                val sb = StringBuilder(kw)
                allText(head, sb)
                allText(body, sb)
                val hay = norm(sb.toString())
                words.all { hay.contains(it) }
            }
            head.visibility = if (ok) View.VISIBLE else View.GONE
            body.visibility = if (ok) View.VISIBLE else View.GONE
            if (ok) shown++
            i += 2
        }
        return shown
    }

    protected fun subTitle(s: String) {
        val t = TextView(this)
        t.text = s
        t.textSize = 14f
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        t.setTextColor(CYAN)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    protected fun text(s: String, size: Float, bold: Boolean = false, faded: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(if (faded) SUB else TEXT)
        t.setLineSpacing(dpf(2f), 1f)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return t
    }

    /** Botón en forma de píldora: [primary] = con degradado; si no, solo el borde. */
    protected fun button(label: String, primary: Boolean = false, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 15f
        b.gravity = Gravity.CENTER
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        b.setPadding(dp(16), dp(13), dp(16), dp(13))
        b.isClickable = true
        b.setOnClickListener { onClick() }
        if (primary) {
            b.setTextColor(0xFF0B0D12.toInt())
            b.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF),
                GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(26f) },
                null,
            )
        } else {
            b.setTextColor(CYAN)
            b.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x334FD1FF),
                GradientDrawable().apply { setColor(CARD2); cornerRadius = dpf(26f) },
                null,
            )
        }
        col.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        return b
    }

    /** Fila con un texto y un interruptor moderno a la derecha (toda la fila se puede tocar). */
    protected fun switch(label: String, key: String, def: Boolean, help: String? = null) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTextColor(TEXT)
        texts.addView(t)
        if (help != null) {
            val h = TextView(this)
            h.text = help
            h.textSize = 12f
            h.setTextColor(SUB)
            h.setPadding(0, dp(3), 0, 0)
            texts.addView(h)
        }
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(12) })
        val tg = Toggle(this)
        tg.on = prefs.getBoolean(key, def)
        row.addView(tg, LinearLayout.LayoutParams(dp(48), dp(28)))
        row.setOnClickListener {
            tg.on = !tg.on
            prefs.edit().putBoolean(key, tg.on).apply()
        }
        if (col.childCount > 0) divider()
        col.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    /** Muchas opciones en píldoras que se deslizan a los lados (la elegida se ilumina). */
    protected fun chips(label: String, key: String, def: String, options: List<Pair<String, String>>) {
        if (col.childCount > 0) divider()
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTextColor(TEXT)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val views = ArrayList<Pair<String, TextView>>()
        fun paint(sel: String?) {
            for ((v, tv) in views) {
                val on = v == sel
                tv.setTextColor(if (on) 0xFF0B0D12.toInt() else SUB)
                tv.background = if (on) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(20f) }
                else GradientDrawable().apply { setColor(BG); cornerRadius = dpf(20f) }
            }
        }
        for ((value, name) in options) {
            val o = TextView(this)
            o.text = name
            o.textSize = 14f
            o.gravity = Gravity.CENTER
            o.setPadding(dp(16), dp(9), dp(16), dp(9))
            o.setOnClickListener {
                prefs.edit().putString(key, value).apply()
                paint(value)
            }
            views.add(value to o)
            row.addView(o, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
        }
        paint(prefs.getString(key, def))
        val hs = android.widget.HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(row)
        col.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(12) })
    }

    /** Muchas opciones en una cuadrícula de [cols] columnas (la elegida se ilumina). */
    protected fun chipGrid(label: String, key: String, def: String, options: List<Pair<String, String>>, cols: Int = 3) {
        if (col.childCount > 0) divider()
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTextColor(TEXT)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val views = ArrayList<Pair<String, TextView>>()
        fun paint(sel: String?) {
            for ((v, tv) in views) {
                val on = v == sel
                tv.setTextColor(if (on) 0xFF0B0D12.toInt() else SUB)
                tv.background = if (on) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(16f) }
                else GradientDrawable().apply { setColor(BG); cornerRadius = dpf(16f) }
            }
        }
        var row: LinearLayout? = null
        for ((i, pair) in options.withIndex()) {
            if (i % cols == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
            val (value, name) = pair
            val o = TextView(this)
            o.text = name
            o.textSize = 13f
            o.gravity = Gravity.CENTER
            o.maxLines = 1
            o.setPadding(dp(4), dp(10), dp(4), dp(10))
            o.setOnClickListener {
                prefs.edit().putString(key, value).apply()
                paint(value)
            }
            views.add(value to o)
            row?.addView(o, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        val rest = options.size % cols
        if (rest != 0) repeat(cols - rest) { row?.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(3), 0, dp(3), 0) }) }
        paint(prefs.getString(key, def))
        col.addView(View(this), LinearLayout.LayoutParams(-1, dp(10)))
    }

    protected fun divider() {
        val v = View(this)
        v.setBackgroundColor(LINE)
        col.addView(v, LinearLayout.LayoutParams(-1, dp(1)))
    }

    /** Opciones en píldoras, una al lado de la otra (la elegida se ilumina). */
    protected fun segmented(label: String, key: String, def: String, options: List<Pair<String, String>>, onChange: (() -> Unit)? = null) {
        if (col.childCount > 0) divider()
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTextColor(TEXT)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(4), dp(4), dp(4), dp(4))
        bar.background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(24f) }
        val views = ArrayList<Pair<String, TextView>>()
        fun paint(sel: String?) {
            for ((v, tv) in views) {
                val on = v == sel
                tv.setTextColor(if (on) 0xFF0B0D12.toInt() else SUB)
                tv.background = if (on) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(20f) } else null
            }
        }
        for ((value, name) in options) {
            val o = TextView(this)
            o.text = name
            o.textSize = 14f
            o.gravity = Gravity.CENTER
            o.setPadding(dp(6), dp(9), dp(6), dp(9))
            o.setOnClickListener {
                val changed = prefs.getString(key, def) != value
                prefs.edit().putString(key, value).apply()
                paint(value)
                if (changed) onChange?.invoke()
            }
            views.add(value to o)
            bar.addView(o, LinearLayout.LayoutParams(0, -2, 1f))
        }
        paint(prefs.getString(key, def))
        col.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(12) })
    }

    /** Interruptor futurista: píldora con degradado cuando está encendido. */
    @SuppressLint("ViewConstructor")
    private class Toggle(context: Context) : View(context) {
        var on = false
            set(v) { field = v; anim = if (v) 1f else 0f; invalidate() }
        private var anim = 0f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            r.set(0f, 0f, w, h)
            if (on) {
                p.shader = LinearGradient(0f, 0f, w, 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            } else {
                p.shader = null
                p.color = LINE
            }
            c.drawRoundRect(r, h / 2, h / 2, p)
            p.shader = null
            val k = h / 2 - h * 0.12f
            val cx = if (on) w - h / 2 else h / 2
            p.color = if (on) 0xFFFFFFFF.toInt() else SUB
            c.drawCircle(cx, h / 2, k, p)
        }
    }
}
