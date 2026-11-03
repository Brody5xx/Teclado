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
        var longest = 0.0
        for (part in parts) Regex("^(\\d+(?:\\.\\d+)?)").find(part)?.let { longest = maxOf(longest, it.groupValues[1].toDouble()) }
        if (extra > 0) total *= 1 + extra / 100
        val boxes = Math.ceil(total / 1000.0).toInt()
        val warn = if (longest > 328) L.t(" · ❌ una línea pasa de 328 pies", " · ❌ a line is over 328 ft") else ""
        return L.t("${fmt(total)} pies (${fmt(total * 0.3048)} m) · $boxes caja(s) de 1000 pies", "${fmt(total)} ft (${fmt(total * 0.3048)} m) · $boxes box(es) of 1000 ft") + warn
    }

    /**
     * 🧭 ¿Cuántos pies tiene una corrida (una línea de cable)?
     * Tú das el camino (en pies, pasos, placas del techo o metros) y la altura del techo.
     * Se suma: el camino + subir y bajar (2 veces el techo) + 15 pies de holgura (la vuelta en el rack y en el equipo)
     * y un 10% por curvas. Ej.: «80 pasos techo 10», «40 placas», «150», «150 x 6».
     */
    class Run(val perLine: Int, val path: Double, val updown: Double, val lines: Int)

    fun runOf(text: String): Run? {
        var s = text.lowercase().replace(",", ".").trim()
        if (s.isEmpty()) return null
        var ceil = 10.0
        Regex("(?:techo|ceiling|alto|altura|height|t)\\s*(\\d+(?:\\.\\d+)?)").find(s)?.let { ceil = it.groupValues[1].toDouble(); s = s.replace(it.value, " ") }
        var lines = 1
        Regex("[x×*]\\s*(\\d+)").find(s)?.let { lines = it.groupValues[1].toInt().coerceIn(1, 500); s = s.replace(it.value, " ") }
        val m = Regex("(\\d+(?:\\.\\d+)?)\\s*([a-zñ]*)").find(s) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = m.groupValues[2]
        val feet = when {
            unit.startsWith("pas") || unit.startsWith("step") -> n * 2.5      // un paso normal ≈ 2.5 pies
            unit.startsWith("plac") || unit.startsWith("bald") || unit.startsWith("tile") || unit.startsWith("plaf") -> n * 2.0 // placa del techo = 2 pies
            unit == "m" || unit.startsWith("metr") || unit.startsWith("meter") -> n * 3.281
            else -> n
        }
        val updown = ceil * 2
        val raw = (feet + updown + 15.0) * 1.10
        val per = (Math.ceil(raw / 5.0) * 5).toInt()
        return Run(per, feet, updown, lines)
    }

    fun runText(text: String, forTyping: Boolean): String? {
        val r = runOf(text) ?: return null
        val m = fmt(r.perLine * 0.3048)
        val ok = when {
            r.perLine > 328 -> L.t("❌ Pasa de 328 pies (100 m): la red puede fallar. Busca un switch o un rack más cerca.",
                "❌ Over 328 ft (100 m): the network may fail. Find a closer switch or rack.")
            r.perLine > 295 -> L.t("⚠️ Casi al límite (328 pies). Trata de ir por un camino más corto.",
                "⚠️ Almost at the limit (328 ft). Try a shorter route.")
            else -> L.t("✅ Bien, dentro del límite.", "✅ Good, within the limit.")
        }
        val total = r.perLine * r.lines
        val many = if (r.lines > 1) L.t("  ·  ${r.lines} líneas = $total pies · ${Math.ceil(total / 1000.0).toInt()} caja(s) de 1000",
            "  ·  ${r.lines} lines = $total ft · ${Math.ceil(total / 1000.0).toInt()} box(es) of 1000") else ""
        if (forTyping) return L.t("${r.perLine} pies por línea", "${r.perLine} ft per line") + (if (r.lines > 1) L.t(" x ${r.lines} = $total pies", " x ${r.lines} = $total ft") else "")
        return L.t("≈ ${r.perLine} pies por línea ($m m)", "≈ ${r.perLine} ft per line ($m m)") + many + "\n" +
            L.t("Camino ${fmt(r.path)} + subir y bajar ${fmt(r.updown)} + holgura 15 + 10% curvas. ", "Path ${fmt(r.path)} + up and down ${fmt(r.updown)} + slack 15 + 10% bends. ") + ok
    }
}
