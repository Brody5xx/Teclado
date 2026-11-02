package com.luisell.bestkeyboard

import java.util.Locale

/** 📏 Convertidor: pies ↔ metros, pulgadas ↔ cm, °F ↔ °C, libras ↔ kg, millas ↔ km, y total de cable. */
object Convert {
    class Unit(val id: String, val es: String, val en: String, val other: String, val f: (Double) -> Double)

    val UNITS = listOf(
        Unit("ft", "pies → metros", "feet → meters", "m") { it * 0.3048 },
        Unit("m", "metros → pies", "meters → feet", "ft") { it / 0.3048 },
        Unit("in", "pulgadas → cm", "inches → cm", "cm") { it * 2.54 },
        Unit("cm", "cm → pulgadas", "cm → inches", "in") { it / 2.54 },
        Unit("F", "°F → °C", "°F → °C", "°C") { (it - 32) * 5 / 9 },
        Unit("C", "°C → °F", "°C → °F", "°F") { it * 9 / 5 + 32 },
        Unit("lb", "libras → kg", "pounds → kg", "kg") { it * 0.45359237 },
        Unit("kg", "kg → libras", "kg → pounds", "lb") { it / 0.45359237 },
        Unit("mi", "millas → km", "miles → km", "km") { it * 1.609344 },
        Unit("km", "km → millas", "km → miles", "mi") { it / 1.609344 },
    )

    fun fmt(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return if (r == Math.floor(r)) r.toLong().toString() else String.format(Locale.US, "%.2f", r).trimEnd('0').trimEnd('.')
    }

    fun from(u: Unit) = when (u.id) { "F" -> "°F"; "C" -> "°C"; else -> u.id }

    /**
     * Cable: «120 x 6» (6 corridas de 120 pies), «85, 90, 120» (varias corridas) o «120 x 6 + 15%» (con extra).
     * Devuelve el total en pies y metros, o null si no se entiende.
     */
    fun cable(text: String): String? {
        var s = text.lowercase().replace("ft", "").replace("pies", "").replace("'", "").trim()
        var extra = 0.0
        Regex("\\+\\s*(\\d+(?:\\.\\d+)?)\\s*%").find(s)?.let { extra = it.groupValues[1].toDouble(); s = s.replace(it.value, "") }
        var total = 0.0
        val parts = s.split(Regex("[,;+]")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        for (part in parts) {
            val m = Regex("^(\\d+(?:\\.\\d+)?)\\s*(?:[x×*]\\s*(\\d+))?$").find(part) ?: return null
            total += m.groupValues[1].toDouble() * (m.groupValues[2].ifEmpty { "1" }.toDouble())
        }
        if (extra > 0) total *= 1 + extra / 100
        val boxes = Math.ceil(total / 1000.0).toInt()
        return L.t("${fmt(total)} pies (${fmt(total * 0.3048)} m) · $boxes caja(s) de 1000 pies",
            "${fmt(total)} ft (${fmt(total * 0.3048)} m) · $boxes box(es) of 1000 ft")
    }
}
