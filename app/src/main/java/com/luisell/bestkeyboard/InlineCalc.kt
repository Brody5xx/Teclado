package com.luisell.bestkeyboard

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * 🧮 Calculadora y conversor mientras escribes (sale con ✨ arriba del teclado):
 * «25*4=» → 100 · «120 ft a m» → 36.58 m · «20 usd a pesos» → con las tasas de hoy.
 */
object InlineCalc {

    // ---------------------------------------------------------------- Cuentas

    private val EXPR = Regex("([0-9.,+\\-*/x×÷()% ]{3,60})=$")

    /** «25*4=» → «100» (se escribe después del =). */
    fun calc(before: CharSequence): FormalWords.Match? {
        val t = before.toString()
        val m = EXPR.find(t) ?: return null
        val raw = m.groupValues[1].trim()
        if (!raw.any { it.isDigit() } || !raw.any { it in "+-*/x×÷%" }) return null
        // Debe empezar con número o paréntesis (no «hola - 5=»)
        if (!(raw[0].isDigit() || raw[0] == '(' || raw[0] == '-')) return null
        val v = try { Parser(raw.replace('x', '*').replace('×', '*').replace('÷', '/').replace(",", "")).parse() } catch (_: Exception) { return null }
        if (v.isNaN() || v.isInfinite()) return null
        return FormalWords.Match(0, fmt(v))
    }

    private fun fmt(v: Double): String {
        if (Math.abs(v - Math.rint(v)) < 1e-9 && Math.abs(v) < 1e15) return String.format(Locale.US, "%,d", v.toLong()).replace(",", ",")
        return String.format(Locale.US, "%,.2f", v).trimEnd('0').trimEnd('.')
    }

    /** Lee una cuenta con + − × ÷ %, paréntesis y el orden correcto. */
    private class Parser(val s: String) {
        var i = 0
        fun parse(): Double { val v = expr(); skip(); if (i < s.length) throw IllegalArgumentException(); return v }
        fun skip() { while (i < s.length && s[i] == ' ') i++ }
        fun expr(): Double {
            var v = term()
            while (true) { skip(); if (i >= s.length) return v
                when (s[i]) { '+' -> { i++; v += term() } '-' -> { i++; v -= term() } else -> return v } }
        }
        fun term(): Double {
            var v = factor()
            while (true) { skip(); if (i >= s.length) return v
                when (s[i]) { '*' -> { i++; v *= factor() } '/' -> { i++; v /= factor() } else -> return v } }
        }
        fun factor(): Double {
            skip()
            if (i < s.length && s[i] == '-') { i++; return -factor() }
            if (i < s.length && s[i] == '(') { i++; val v = expr(); skip(); if (i < s.length && s[i] == ')') i++; return pct(v) }
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (st == i) throw IllegalArgumentException()
            return pct(s.substring(st, i).toDouble())
        }
        fun pct(v: Double): Double { skip(); if (i < s.length && s[i] == '%') { i++; return v / 100.0 }; return v }
    }

    // ---------------------------------------------------------------- Unidades

    private val UNIT = Regex("(\\d+(?:[.,]\\d+)?)\\s*(°?[a-zA-Z°]{1,12})\\s+(?:a|en|to|in|=)\\s+(°?[a-zA-Z°]{1,12})\\s?$", RegexOption.IGNORE_CASE)

    /** Cada unidad: (tipo, cuánto vale en la unidad base, cómo se escribe). */
    private val UNITS: Map<String, Triple<String, Double, String>> = buildMap {
        fun u(kind: String, factor: Double, show: String, vararg names: String) { for (n in names) put(n, Triple(kind, factor, show)) }
        u("len", 1.0, "m", "m", "metro", "metros", "meter", "meters", "mt", "mts")
        u("len", 0.01, "cm", "cm", "centimetro", "centimetros", "centímetro", "centímetros")
        u("len", 0.001, "mm", "mm")
        u("len", 1000.0, "km", "km", "kilometro", "kilometros", "kilómetro", "kilómetros")
        u("len", 0.3048, "ft", "ft", "pie", "pies", "feet", "foot")
        u("len", 0.0254, "in", "in", "inch", "inches", "pulgada", "pulgadas", "pulg")
        u("len", 0.9144, "yd", "yd", "yarda", "yardas", "yard", "yards")
        u("len", 1609.344, "mi", "mi", "milla", "millas", "mile", "miles")
        u("mass", 1.0, "kg", "kg", "kilo", "kilos", "kilogramo", "kilogramos")
        u("mass", 0.001, "g", "g", "gramo", "gramos", "gram", "grams")
        u("mass", 0.45359237, "lb", "lb", "lbs", "libra", "libras", "pound", "pounds")
        u("mass", 0.028349523, "oz", "oz", "onza", "onzas", "ounce", "ounces")
        u("vol", 1.0, "L", "l", "litro", "litros", "liter", "liters", "lt")
        u("vol", 0.001, "ml", "ml")
        u("vol", 3.785411784, "gal", "gal", "galon", "galón", "galones", "gallon", "gallons")
        u("temp", 1.0, "°C", "c", "°c", "celsius", "centigrados")
        u("temp", 2.0, "°F", "f", "°f", "fahrenheit")
    }

    fun units(before: CharSequence): FormalWords.Match? {
        val m = UNIT.find(before.toString()) ?: return null
        val n = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val a = UNITS[m.groupValues[2].lowercase()] ?: return null
        val b = UNITS[m.groupValues[3].lowercase()] ?: return null
        if (a.first != b.first || a.third == b.third) return null
        val v = if (a.first == "temp") {
            val c = if (a.third == "°F") (n - 32) * 5 / 9 else n
            if (b.third == "°F") c * 9 / 5 + 32 else c
        } else n * a.second / b.second
        val shown = String.format(Locale.US, if (Math.abs(v) >= 100) "%,.1f" else "%,.2f", v).trimEnd('0').trimEnd('.')
        val trailing = before.isNotEmpty() && before.last() == ' '
        return FormalWords.Match(0, (if (trailing) "" else " ") + "= $shown ${b.third}")
    }

    // ---------------------------------------------------------------- Monedas

    private val MONEY = Regex("\\$?(\\d+(?:[.,]\\d+)?)\\s*([a-zA-Záéíóú$]{1,12})\\s+(?:a|en|to|in)\\s+([a-zA-Záéíóú]{2,12})(?:\\s+(dominicanos|mexicanos|colombianos|argentinos|chilenos))?\\s?$", RegexOption.IGNORE_CASE)

    private fun code(word: String, ctx: Context, extra: String? = null): String? {
        val w = word.lowercase().trim('$')
        val e = extra?.lowercase()
        return when {
            e == "dominicanos" -> "DOP"; e == "mexicanos" -> "MXN"; e == "colombianos" -> "COP"; e == "argentinos" -> "ARS"; e == "chilenos" -> "CLP"
            w in setOf("usd", "dolar", "dólar", "dolares", "dólares", "dollar", "dollars", "us") -> "USD"
            w in setOf("eur", "euro", "euros") -> "EUR"
            w in setOf("mxn") -> "MXN"; w in setOf("dop", "rd") -> "DOP"; w in setOf("cop") -> "COP"; w in setOf("ars") -> "ARS"
            w in setOf("clp") -> "CLP"; w in setOf("gbp", "libras esterlinas") -> "GBP"; w in setOf("cad") -> "CAD"; w in setOf("brl", "reales") -> "BRL"
            w in setOf("peso", "pesos") -> localPeso(ctx)
            w.length == 3 && w.all { it.isLetter() } -> w.uppercase()
            else -> null
        }
    }

    /** «pesos» = los del país de tu teléfono (dominicanos, mexicanos, colombianos…). */
    private fun localPeso(ctx: Context): String {
        val iso = try {
            (ctx.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager)?.let { it.simCountryIso.ifEmpty { it.networkCountryIso } }
        } catch (_: Exception) { null }?.uppercase() ?: Locale.getDefault().country
        return when (iso) { "DO" -> "DOP"; "CO" -> "COP"; "AR" -> "ARS"; "CL" -> "CLP"; "UY" -> "UYU"; "PH" -> "PHP"; "CU" -> "CUP"; else -> "MXN" }
    }

    @Volatile private var rates: JSONObject? = null
    @Volatile private var fetching = false

    /** Las tasas del día (gratis, sin clave). Se bajan una vez al día y se guardan. */
    private fun ratesOf(ctx: Context): JSONObject? {
        val p = Prefs.get(ctx)
        val day = System.currentTimeMillis() / 86_400_000L
        rates ?: p.getString("fx_rates", null)?.let { rates = try { JSONObject(it) } catch (_: Exception) { null } }
        val stale = p.getLong("fx_day", 0L) != day
        if ((rates == null || stale) && !fetching) {
            fetching = true
            Thread {
                try {
                    val c = java.net.URL("https://open.er-api.com/v6/latest/USD").openConnection() as java.net.HttpURLConnection
                    c.connectTimeout = 8000; c.readTimeout = 10000
                    val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    j.optJSONObject("rates")?.let { r -> rates = r; p.edit().putString("fx_rates", r.toString()).putLong("fx_day", day).apply() }
                    c.disconnect()
                } catch (_: Exception) { }
                fetching = false
            }.start()
        }
        return rates
    }

    fun money(ctx: Context, before: CharSequence): FormalWords.Match? {
        val m = MONEY.find(before.toString()) ?: return null
        val n = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val from = code(m.groupValues[2], ctx) ?: return null
        val to = code(m.groupValues[3], ctx, m.groupValues[4].ifEmpty { null }) ?: return null
        if (from == to) return null
        val r = ratesOf(ctx) ?: return null
        val rf = r.optDouble(from, Double.NaN)
        val rt = r.optDouble(to, Double.NaN)
        if (rf.isNaN() || rt.isNaN() || rf == 0.0) return null
        val v = n / rf * rt
        val trailing = before.isNotEmpty() && before.last() == ' '
        return FormalWords.Match(0, (if (trailing) "" else " ") + "= " + String.format(Locale.US, "%,.2f", v) + " " + to)
    }
}
