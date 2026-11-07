package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

/**
 * 💱 Divisas dentro del teclado (como en Best Notes): a cuánto está cada moneda del mundo hoy
 * y la cuenta al instante. En la cantidad puedes hacer cálculos (50+25×2). «Escribir» pone el resultado.
 * Tasas del día gratis de ExchangeRate-API (open.er-api.com); sin internet usa las últimas guardadas.
 */
@SuppressLint("ViewConstructor")
class FxPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onFxInsert(text: String)
        fun onFxBack()
        fun onFxFeedback()
    }

    var actions: Actions? = null
    private var pal = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)
    private val prefs = Prefs.get(context)

    private var from = prefs.getString("fx_from", "USD") ?: "USD"
    private var to = prefs.getString("fx_to", null) ?: Fx.localCurrency(context).let { if (it == "USD") "EUR" else it }
    private val expr = StringBuilder(prefs.getString("fx_amt", "1") ?: "1")

    private val body = LinearLayout(context)
    private val backBtn = IconView(context, Icons.BACK, 7.5f)
    private val title = TextView(context)
    private val info = TextView(context)
    private val fromRow = LinearLayout(context)
    private val toRow = LinearLayout(context)
    private val fromBadge = TextView(context); private val fromName = TextView(context); private val fromAmt = TextView(context)
    private val toBadge = TextView(context); private val toName = TextView(context); private val toAmt = TextView(context)
    private val rateLine = TextView(context)
    private val swapBtn = SwapButton(context)
    private val keys = ArrayList<Pair<TextView, Int>>()

    // La lista para elegir moneda (tapa el panel)
    private val picker = LinearLayout(context)
    private val pickTitle = TextView(context)
    private val pickBack = IconView(context, Icons.BACK, 7.5f)
    private val pickList = LinearLayout(context)
    private val pickScroll = ScrollView(context)
    private var pickingFrom = true

    init {
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(6f), d(4f), d(6f), d(6f))

        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        backBtn.setOnClickListener { actions?.onFxFeedback(); actions?.onFxBack() }
        top.addView(backBtn, LinearLayout.LayoutParams(d(40f), d(34f)))
        title.textSize = 16f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setPadding(d(10f), 0, 0, 0)
        top.addView(title, LinearLayout.LayoutParams(-2, -2))
        info.textSize = 11f
        info.gravity = Gravity.END
        info.maxLines = 1
        info.ellipsize = TextUtils.TruncateAt.END
        info.setPadding(d(8f), 0, d(8f), 0)
        top.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(top, LinearLayout.LayoutParams(-1, -2))

        for ((row, parts) in listOf(fromRow to Triple(fromBadge, fromName, fromAmt), toRow to Triple(toBadge, toName, toAmt))) {
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(d(10f), d(4f), d(12f), d(4f))
            val (badge, name, amt) = parts
            badge.textSize = 12f
            badge.gravity = Gravity.CENTER
            badge.setTypeface(Typeface.DEFAULT_BOLD)
            row.addView(badge, LinearLayout.LayoutParams(d(46f), d(30f)))
            name.textSize = 13f
            name.maxLines = 1
            name.ellipsize = TextUtils.TruncateAt.END
            name.setPadding(d(8f), 0, d(6f), 0)
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            amt.textSize = 21f
            amt.maxLines = 1
            amt.ellipsize = TextUtils.TruncateAt.START
            amt.gravity = Gravity.END
            amt.setTypeface(Typeface.DEFAULT_BOLD)
            row.addView(amt, LinearLayout.LayoutParams(0, -2, 1.4f))
        }
        fromRow.setOnClickListener { actions?.onFxFeedback(); openPicker(true) }
        toRow.setOnClickListener { actions?.onFxFeedback(); openPicker(false) }
        // Las dos monedas, y a la derecha las flechitas ↑↓ para cambiarlas de lugar
        val pair = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        rows.addView(fromRow, LinearLayout.LayoutParams(-1, d(40f)))
        rows.addView(toRow, LinearLayout.LayoutParams(-1, d(40f)).apply { topMargin = d(4f) })
        pair.addView(rows, LinearLayout.LayoutParams(0, -2, 1f))
        swapBtn.setOnClickListener {
            actions?.onFxFeedback()
            val x = from; from = to; to = x
            swapBtn.flash() // ✨ destello
            save(); refresh()
        }
        pair.gravity = Gravity.CENTER_VERTICAL
        pair.addView(swapBtn, LinearLayout.LayoutParams(d(46f), d(46f)).apply { leftMargin = d(2f) })
        body.addView(pair, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(3f) })
        rateLine.textSize = 11.5f
        rateLine.gravity = Gravity.CENTER
        rateLine.maxLines = 1
        rateLine.setPadding(0, d(2f), 0, 0)
        body.addView(rateLine, LinearLayout.LayoutParams(-1, -2))

        val grid = listOf(
            listOf("7", "8", "9", "÷", "⌫"),
            listOf("4", "5", "6", "×", "C"),
            listOf("1", "2", "3", "−", "%"),
            listOf("ABC", "0", ".", "+", L.t("Escribir", "Type it")),
        )
        for (row in grid) {
            val r = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in row) {
                val t = TextView(context)
                t.text = k
                t.textSize = if (k.length > 2) 15f else 21f
                t.gravity = Gravity.CENTER
                t.includeFontPadding = false // los números quedan en el centro de la tecla
                t.setPadding(0, 0, 0, 0)
                val type = when {
                    k.length > 3 -> 2
                    k[0].isDigit() || k == "." -> 0
                    else -> 1
                }
                t.setOnClickListener { actions?.onFxFeedback(); press(k) }
                keys.add(t to type)
                r.addView(t, LinearLayout.LayoutParams(0, -1, if (k.length > 3) 1.6f else 1f).apply { setMargins(d(3f), d(3f), d(3f), d(3f)) })
            }
            body.addView(r, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        addView(body, LayoutParams(-1, -1))

        // ---- Elegir moneda
        picker.orientation = LinearLayout.VERTICAL
        picker.visibility = View.GONE
        picker.isClickable = true
        val ph = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(d(6f), d(4f), d(6f), d(4f)) }
        pickBack.setOnClickListener { actions?.onFxFeedback(); picker.visibility = View.GONE }
        ph.addView(pickBack, LinearLayout.LayoutParams(d(40f), d(34f)))
        pickTitle.textSize = 16f
        pickTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        pickTitle.setPadding(d(10f), 0, 0, 0)
        ph.addView(pickTitle, LinearLayout.LayoutParams(-1, -2))
        picker.addView(ph, LinearLayout.LayoutParams(-1, -2))
        pickList.orientation = LinearLayout.VERTICAL
        pickList.setPadding(d(8f), 0, d(8f), d(8f))
        pickScroll.isVerticalScrollBarEnabled = false
        pickScroll.addView(pickList)
        picker.addView(pickScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(picker, LayoutParams(-1, -1))

        applyPalette(pal)
    }

    fun open() {
        picker.visibility = View.GONE
        title.text = L.t("Divisas", "Currency")
        (keys.lastOrNull()?.first)?.text = L.t("Escribir", "Type it")
        Fx.rates(context) { post { refresh() } }
        refresh()
    }

    private fun press(k: String) {
        when (k) {
            "C" -> expr.setLength(0)
            "⌫" -> if (expr.isNotEmpty()) expr.setLength(expr.length - 1)
            "ABC" -> { actions?.onFxBack(); return }
            else -> if (k.length > 3) {
                val t = resultText()
                if (t != null) actions?.onFxInsert(t)
                return
            } else {
                if (expr.toString() == "0" && (k[0].isDigit())) expr.setLength(0)
                if (expr.length < 40) expr.append(k)
            }
        }
        save()
        refresh()
    }

    private fun save() {
        prefs.edit().putString("fx_from", from).putString("fx_to", to).putString("fx_amt", expr.toString()).apply()
    }

    private fun amount(): Double? = Calc.eval(expr.toString())

    private fun resultText(): String? {
        val a = amount() ?: return null
        val v = Fx.convert(context, a, from, to) ?: return null
        return Fx.fmt(a) + " " + from + " = " + Fx.fmt(v) + " " + to
    }

    private fun refresh() {
        fromBadge.text = from
        toBadge.text = to
        fromName.text = Fx.name(from)
        toName.text = Fx.name(to)
        fromAmt.text = expr.toString().ifEmpty { "0" }
        val a = amount()
        val r = Fx.convert(context, a ?: 0.0, from, to)
        toAmt.text = if (a == null) "…" else if (r == null) "—" else Fx.fmt(r)
        val one = Fx.convert(context, 1.0, from, to)
        val back = Fx.convert(context, 1.0, to, from)
        rateLine.text = if (one != null && back != null) "1 $from = ${Fx.fmt(one)} $to     1 $to = ${Fx.fmt(back)} $from" else ""
        info.text = Fx.status(context)
    }

    private fun openPicker(isFrom: Boolean) {
        pickingFrom = isFrom
        pickTitle.text = if (isFrom) L.t("Convertir de…", "Convert from…") else L.t("Convertir a…", "Convert to…")
        pickList.removeAllViews()
        val sel = if (isFrom) from else to
        val all = Fx.codes(context)
        val pop = Fx.POPULAR.filter { it in all }
        val rest = all.filter { it !in pop }.sortedBy { Fx.name(it).lowercase() }
        fun header(s: String) {
            pickList.addView(TextView(context).apply {
                text = s; textSize = 11f; setTextColor(pal.hint); letterSpacing = 0.1f
                setPadding(d(8f), d(10f), 0, d(4f))
            })
        }
        fun item(c: String) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(d(8f), 0, d(10f), 0)
                background = PanelUi.pressable(if (c == sel) pal.special else 0, pal.keyPressed, d(12f).toFloat())
                setOnClickListener {
                    actions?.onFxFeedback()
                    if (pickingFrom) from = c else to = c
                    picker.visibility = View.GONE
                    save(); refresh()
                }
            }
            val b = TextView(context).apply { text = c; textSize = 12f; gravity = Gravity.CENTER; setTypeface(Typeface.DEFAULT_BOLD) }
            badgeStyle(b, c == sel)
            row.addView(b, LinearLayout.LayoutParams(d(46f), d(28f)))
            row.addView(TextView(context).apply {
                text = Fx.name(c); textSize = 14f; setTextColor(if (c == sel) pal.accent else pal.text)
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(d(10f), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            pickList.addView(row, LinearLayout.LayoutParams(-1, d(42f)))
        }
        header(L.t("MÁS USADAS", "MOST USED"))
        pop.forEach { item(it) }
        header(L.t("TODAS (A–Z)", "ALL (A–Z)"))
        rest.forEach { item(it) }
        picker.visibility = View.VISIBLE
        pickScroll.scrollTo(0, 0)
    }

    private fun badgeStyle(b: TextView, on: Boolean) {
        b.setTextColor(if (on) pal.onAccent else pal.text)
        b.background = GradientDrawable().apply {
            cornerRadius = d(10f).toFloat()
            if (on) setColor(pal.accent) else { setColor(pal.key); setStroke(d(1f), pal.hint and 0x55FFFFFF) }
        }
    }

    private fun plain(v: Double): String {
        val r = Math.round(v * 1e6) / 1e6
        return if (r == Math.floor(r) && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    fun applyPalette(p: Palette) {
        pal = p
        setBackgroundColor(p.bg)
        picker.setBackgroundColor(p.bg or 0xFF000000.toInt())
        title.setTextColor(p.text); pickTitle.setTextColor(p.text)
        info.setTextColor(p.hint); rateLine.setTextColor(p.hint)
        for (b in listOf(backBtn, pickBack)) {
            b.color = p.text; b.bgColor = p.bg
            b.background = PanelUi.pressable(p.special, p.keyPressed, d(18f).toFloat())
        }
        for (row in listOf(fromRow, toRow)) {
            row.background = PanelUi.pressable(p.key, p.keyPressed, d(14f).toFloat())
        }
        badgeStyle(fromBadge, false); badgeStyle(toBadge, true)
        swapBtn.ink = p.onAccent
        fromName.setTextColor(p.hint); toName.setTextColor(p.hint)
        fromAmt.setTextColor(p.text); toAmt.setTextColor(p.accent)
        val r = d(12f).toFloat()
        for ((t, type) in keys) {
            when (type) {
                2 -> { t.setTextColor(p.onAccent); t.background = PanelUi.pressable(p.accent, p.accentPressed, r) }
                1 -> { t.setTextColor(p.text); t.background = PanelUi.pressable(p.special, p.keyPressed, r) }
                else -> { t.setTextColor(p.text); t.background = PanelUi.pressable(p.key, p.keyPressed, r) }
            }
        }
    }
}

/** Las tasas de cambio del día y los nombres de las monedas. */
object Fx {
    val POPULAR = listOf("USD", "EUR", "DOP", "MXN", "COP", "GBP", "CAD", "CNY", "ARS", "CLP", "PEN", "BRL", "JPY", "CHF",
        "GTQ", "HNL", "CRC", "UYU", "BOB", "PYG", "NIO", "CUP", "VES", "INR", "KRW", "PHP")

    @Volatile private var rates: org.json.JSONObject? = null
    @Volatile private var fetching = false

    private fun load(ctx: Context): org.json.JSONObject? {
        rates?.let { return it }
        Prefs.get(ctx).getString("fx_rates", null)?.let { rates = try { org.json.JSONObject(it) } catch (_: Exception) { null } }
        return rates
    }

    /** Baja las tasas si tienen más de 6 horas (o si no hay). [onDone] cuando llegan. */
    fun rates(ctx: Context, onDone: (() -> Unit)? = null): org.json.JSONObject? {
        val p = Prefs.get(ctx)
        val r = load(ctx)
        val age = System.currentTimeMillis() - p.getLong("fx_time", 0L)
        if ((r == null || age > 6 * 3_600_000L) && !fetching) {
            fetching = true
            Thread {
                try {
                    val c = java.net.URL("https://open.er-api.com/v6/latest/USD").openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 8000; c.readTimeout = 10000
                    val j = org.json.JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    j.optJSONObject("rates")?.let { nr ->
                        rates = nr
                        p.edit().putString("fx_rates", nr.toString()).putLong("fx_time", System.currentTimeMillis())
                            .putLong("fx_day", System.currentTimeMillis() / 86_400_000L).apply()
                    }
                    c.disconnect()
                } catch (_: Exception) { }
                fetching = false
                onDone?.invoke()
            }.start()
        }
        return r
    }

    fun codes(ctx: Context): List<String> {
        val r = load(ctx) ?: return POPULAR
        val out = ArrayList<String>()
        val it = r.keys()
        while (it.hasNext()) out.add(it.next())
        return out
    }

    fun convert(ctx: Context, a: Double, f: String, t: String): Double? {
        val r = load(ctx) ?: return null
        val rf = r.optDouble(f, Double.NaN); val rt = r.optDouble(t, Double.NaN)
        if (rf.isNaN() || rt.isNaN() || rf == 0.0) return null
        return a / rf * rt
    }

    /** El nombre de la moneda en el idioma de la pantalla (Peso dominicano, Euro…). */
    fun name(code: String): String = try {
        val loc = if (L.english) Locale.ENGLISH else Locale("es")
        java.util.Currency.getInstance(code).getDisplayName(loc).replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { code }

    fun fmt(v: Double): String {
        val a = kotlin.math.abs(v)
        val dec = if (a >= 1 || a == 0.0) 2 else if (a >= 0.01) 4 else 6
        return String.format(Locale.US, "%,.${dec}f", v)
    }

    fun status(ctx: Context): String {
        val t = Prefs.get(ctx).getLong("fx_time", 0L)
        if (load(ctx) == null) return if (fetching) L.t("Bajando las tasas…", "Getting rates…") else L.t("Sin internet: conéctate una vez", "No internet: connect once")
        if (t == 0L) return L.t("Tasas guardadas", "Saved rates")
        val f = java.text.SimpleDateFormat("d MMM, h:mm a", if (L.english) Locale.US else Locale("es"))
        return L.t("Tasas del ", "Rates from ") + f.format(java.util.Date(t))
    }

    /** La moneda de tu país (por la SIM o el idioma del teléfono). */
    fun localCurrency(ctx: Context): String {
        val iso = try {
            (ctx.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager)?.let { it.simCountryIso.ifEmpty { it.networkCountryIso } }
        } catch (_: Exception) { null }?.uppercase()?.ifEmpty { null } ?: Locale.getDefault().country
        return try { java.util.Currency.getInstance(Locale("", iso)).currencyCode } catch (_: Exception) { "USD" }
    }
}

/** Cuentas sencillas: + − × ÷ %, paréntesis y decimales. */
object Calc {
    private var src = ""
    private var pos = 0

    @Synchronized fun eval(s: String): Double? {
        if (s.isBlank()) return null
        src = s.replace('×', '*').replace('÷', '/').replace('−', '-').replace(",", "")
        pos = 0
        return try { val v = sum(); if (pos != src.length || v.isNaN() || v.isInfinite()) null else v } catch (_: Exception) { null }
    }

    private fun sum(): Double {
        var v = product()
        while (pos < src.length && (src[pos] == '+' || src[pos] == '-')) {
            val op = src[pos++]; val b = product(); v = if (op == '+') v + b else v - b
        }
        return v
    }

    private fun product(): Double {
        var v = unary()
        while (pos < src.length && (src[pos] == '*' || src[pos] == '/')) {
            val op = src[pos++]; val b = unary(); v = if (op == '*') v * b else v / b
        }
        return v
    }

    private fun unary(): Double {
        if (pos < src.length && src[pos] == '-') { pos++; return -unary() }
        var v = atom()
        while (pos < src.length && src[pos] == '%') { pos++; v /= 100.0 }
        return v
    }

    private fun atom(): Double {
        if (pos < src.length && src[pos] == '(') {
            pos++; val v = sum()
            if (pos < src.length && src[pos] == ')') pos++ else throw IllegalStateException()
            return v
        }
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
        if (start == pos) throw IllegalStateException()
        return src.substring(start, pos).toDouble()
    }
}


/** El botón ↑↓ redondo con los colores del diamante; al tocarlo da un destello de luz. */
class SwapButton(context: Context) : View(context) {
    var ink = 0xFF0B0D12.toInt()
        set(v) { field = v; invalidate() }
    private val dd = context.resources.displayMetrics.density
    private val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    private val glow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val clip = android.graphics.Path()
    private var start = 0L
    private val MS = 650f

    init { isClickable = true; contentDescription = "Swap" }

    fun flash() { start = android.os.SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    override fun onDraw(c: android.graphics.Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f; val r = minOf(w, h) / 2f * 0.72f
        val t = if (start == 0L) 1f else ((android.os.SystemClock.uptimeMillis() - start) / MS).coerceIn(0f, 1f)
        val on = t < 1f
        val press = if (isPressed) 0.92f else if (on) 1f - 0.08f * kotlin.math.sin(t * Math.PI).toFloat() else 1f
        c.save()
        c.scale(press, press, cx, cy)
        // Aro de luz que se abre
        if (on) {
            val rr = r * (1f + 0.36f * t)
            glow.shader = android.graphics.RadialGradient(cx, cy, rr, intArrayOf(0x0022D3EE, ((1f - t) * 140).toInt() shl 24 or 0x8B5CF6, 0x008B5CF6), floatArrayOf(0.6f, 0.85f, 1f), android.graphics.Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, rr, glow)
        }
        fill.shader = android.graphics.LinearGradient(cx - r, cy - r, cx + r, cy + r, 0xFF22D3EE.toInt(), 0xFF8B5CF6.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, fill)
        // El brillo blanco que lo cruza en diagonal
        if (on) {
            clip.reset(); clip.addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
            c.save(); c.clipPath(clip)
            val x = -r * 1.5f + (w + r * 3f) * t
            glow.shader = android.graphics.LinearGradient(x - r * 0.6f, 0f, x + r * 0.6f, h, intArrayOf(0x00FFFFFF, 0xCCFFFFFF.toInt(), 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, glow)
            c.restore()
        }
        // Las flechitas ↑↓ (giran media vuelta con el destello)
        val e = if (on) 1f - (1f - t) * (1f - t) else 1f
        c.rotate(if (on) 180f * e else 0f, cx, cy)
        line.color = ink
        line.strokeWidth = 2f * dd
        val a = r * 0.42f; val gap = r * 0.22f; val head = r * 0.2f
        c.drawLine(cx - gap, cy + a, cx - gap, cy - a, line)
        c.drawLine(cx - gap - head, cy - a + head, cx - gap, cy - a, line)
        c.drawLine(cx - gap + head, cy - a + head, cx - gap, cy - a, line)
        c.drawLine(cx + gap, cy - a, cx + gap, cy + a, line)
        c.drawLine(cx + gap - head, cy + a - head, cx + gap, cy + a, line)
        c.drawLine(cx + gap + head, cy + a - head, cx + gap, cy + a, line)
        c.restore()
        // Estrellita de luz arriba a la derecha
        if (on) {
            val k = kotlin.math.sin(t * Math.PI).toFloat()
            val sx = cx + r * 0.62f; val sy = cy - r * 0.62f; val sr = 6f * dd * k
            glow.shader = android.graphics.RadialGradient(sx, sy, sr.coerceAtLeast(0.1f), intArrayOf(0xFFFFFFFF.toInt(), 0x00FFFFFF), null, android.graphics.Shader.TileMode.CLAMP)
            c.drawCircle(sx, sy, sr, glow)
            line.color = 0xFFFFFFFF.toInt(); line.strokeWidth = 1.2f * dd
            c.drawLine(sx - sr * 1.6f, sy, sx + sr * 1.6f, sy, line)
            c.drawLine(sx, sy - sr * 1.6f, sx, sy + sr * 1.6f, line)
            postInvalidateOnAnimation()
        } else start = 0L
        glow.shader = null
    }

    override fun drawableStateChanged() { super.drawableStateChanged(); invalidate() }
}
