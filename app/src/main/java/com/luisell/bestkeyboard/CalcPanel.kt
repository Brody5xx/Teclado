package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Calculadora dentro del teclado: haces la cuenta y tocas «Insertar» para escribir el resultado.
 * Entiende + − × ÷ %, paréntesis y decimales.
 */
@SuppressLint("ViewConstructor")
class CalcPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onCalcInsert(text: String)
        fun onCalcBack()
        fun onCalcFeedback()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val expr = StringBuilder()
    private val exprView = TextView(context)
    private val resultView = TextView(context)
    private val keys = ArrayList<Pair<TextView, Int>>() // (botón, tipo: 0 número, 1 operación, 2 igual/insertar)

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(6f), d(4f), d(6f), d(6f))

        val display = LinearLayout(context)
        display.orientation = LinearLayout.VERTICAL
        display.setPadding(d(14f), d(4f), d(14f), d(4f))
        exprView.textSize = 18f
        exprView.gravity = Gravity.END
        exprView.maxLines = 1
        exprView.ellipsize = TextUtils.TruncateAt.START
        resultView.textSize = 26f
        resultView.gravity = Gravity.END
        resultView.setTypeface(resultView.typeface, Typeface.BOLD)
        resultView.maxLines = 1
        display.addView(exprView, LinearLayout.LayoutParams(-1, -2))
        display.addView(resultView, LinearLayout.LayoutParams(-1, -2))
        body.addView(display, LinearLayout.LayoutParams(-1, -2))

        val grid = listOf(
            listOf("C", "(", ")", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "%", "⌫"),
            listOf("ABC", L.t("Insertar", "Insert"), "="),
        )
        for (row in grid) {
            val r = LinearLayout(context)
            r.orientation = LinearLayout.HORIZONTAL
            for (k in row) {
                val t = TextView(context)
                t.text = k
                t.textSize = if (k.length > 2) 15f else 20f
                t.gravity = Gravity.CENTER
                val type = when {
                    k == "=" || k.length > 3 -> 2
                    k[0].isDigit() || k == "." -> 0
                    else -> 1
                }
                t.setOnClickListener { actions?.onCalcFeedback(); press(k) }
                keys.add(t to type)
                val w = if (k.length > 3) 2f else 1f
                r.addView(t, LinearLayout.LayoutParams(0, -1, w).apply { setMargins(d(3f), d(3f), d(3f), d(3f)) })
            }
            body.addView(r, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        addView(body, LayoutParams(-1, -1))
        applyPalette(palette)
        refresh()
    }

    fun open() { refresh() }

    private fun press(k: String) {
        when (k) {
            "C" -> expr.setLength(0)
            "⌫" -> if (expr.isNotEmpty()) expr.setLength(expr.length - 1)
            "ABC" -> { actions?.onCalcBack(); return }
            "=" -> {
                val r = evaluate(expr.toString())
                if (r != null) { expr.setLength(0); expr.append(format(r)) }
            }
            else -> if (k.length > 3) {
                val r = evaluate(expr.toString())
                val out = if (r != null) format(r) else expr.toString()
                if (out.isNotEmpty()) actions?.onCalcInsert(out)
                return
            } else expr.append(k)
        }
        refresh()
    }

    private fun refresh() {
        exprView.text = expr.toString().ifEmpty { "0" }
        val r = evaluate(expr.toString())
        resultView.text = if (r != null) "= " + format(r) else ""
    }

    private fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "Error"
        val r = Math.round(v * 1e8) / 1e8
        return if (r == Math.floor(r) && kotlin.math.abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    // ---------------------------------------------------------------- Hacer la cuenta

    private var src = ""
    private var pos = 0

    /** Resultado de la cuenta, o null si está incompleta. */
    private fun evaluate(s: String): Double? {
        if (s.isBlank()) return null
        src = s.replace('×', '*').replace('÷', '/').replace('−', '-')
        pos = 0
        return try {
            val v = sum()
            if (pos != src.length) null else v
        } catch (_: Exception) { null }
    }

    private fun sum(): Double {
        var v = product()
        while (pos < src.length && (src[pos] == '+' || src[pos] == '-')) {
            val op = src[pos++]
            val b = product()
            v = if (op == '+') v + b else v - b
        }
        return v
    }

    private fun product(): Double {
        var v = unary()
        while (pos < src.length && (src[pos] == '*' || src[pos] == '/')) {
            val op = src[pos++]
            val b = unary()
            v = if (op == '*') v * b else v / b
        }
        return v
    }

    private fun unary(): Double {
        if (pos < src.length && src[pos] == '-') { pos++; return -unary() }
        var v = atom()
        // "%": 50% = 0.5
        while (pos < src.length && src[pos] == '%') { pos++; v /= 100.0 }
        return v
    }

    private fun atom(): Double {
        if (pos < src.length && src[pos] == '(') {
            pos++
            val v = sum()
            if (pos < src.length && src[pos] == ')') pos++ else throw IllegalStateException()
            return v
        }
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.')) pos++
        if (start == pos) throw IllegalStateException()
        return src.substring(start, pos).toDouble()
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        exprView.setTextColor(p.hint)
        resultView.setTextColor(p.text)
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
