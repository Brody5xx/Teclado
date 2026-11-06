package com.luisell.bestkeyboard

/**
 * 🔢 Números, horas y medidas bien escritos (sale con ✨ arriba del teclado):
 * «7pm» → 7:00 PM, «730am» → 7:30 AM, «120ft» → 120 ft, «cat6» → Cat 6, «poe» → PoE.
 */
object SmartFormat {
    private val TIME = Regex("^(\\d{1,2})(?::?(\\d{2}))?\\s?(am|pm|a\\.m\\.?|p\\.m\\.?)$", RegexOption.IGNORE_CASE)
    private val MEASURE = Regex("^(\\d+(?:[.,]\\d+)?)(ft|in|m|cm|mm|km|mi|lb|lbs|kg|oz|v|vac|vdc|a|ma|w|kw|mb|gb|tb|mbps|gbps|ghz|mhz|awg|hz)$", RegexOption.IGNORE_CASE)
    private val CAT = Regex("^cat(5e|5|6a|6|7|8)$", RegexOption.IGNORE_CASE)
    private val TERMS = mapOf(
        "poe" to "PoE", "nvr" to "NVR", "dvr" to "DVR", "ptz" to "PTZ", "hdmi" to "HDMI", "usb" to "USB",
        "rj45" to "RJ45", "rj11" to "RJ11", "idf" to "IDF", "mdf" to "MDF", "wifi" to "Wi-Fi", "lan" to "LAN",
        "wan" to "WAN", "vlan" to "VLAN", "ip" to "IP", "mac" to "MAC", "dhcp" to "DHCP", "dns" to "DNS",
        "cctv" to "CCTV", "asap" to "ASAP", "fyi" to "FYI",
        "lte" to "LTE", "gps" to "GPS", "sfp" to "SFP",
    )
    private val UNITS = mapOf("lbs" to "lb", "vac" to "VAC", "vdc" to "VDC", "v" to "V", "a" to "A", "ma" to "mA", "w" to "W",
        "kw" to "kW", "mb" to "MB", "gb" to "GB", "tb" to "TB", "mbps" to "Mbps", "gbps" to "Gbps", "ghz" to "GHz", "mhz" to "MHz",
        "awg" to "AWG", "hz" to "Hz")

    /** [before] = el texto antes del cursor; [spanish] = la frase va en español. */
    fun match(before: CharSequence, spanish: Boolean): FormalWords.Match? {
        if (before.isEmpty()) return null
        val trailing = if (before.last() == ' ') 1 else 0
        var i = before.length - trailing
        val end = i
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        if (end - i < 2 || end - i > 14) return null
        val tok = before.subSequence(i, end).toString()
        val out = format(tok, spanish) ?: return null
        if (out == tok) return null
        val tail = if (trailing == 1) " " else ""
        return FormalWords.Match(tok.length + trailing, out + tail)
    }

    fun format(tok: String, spanish: Boolean): String? {
        TIME.matchEntire(tok)?.let { m ->
            var h = m.groupValues[1].toInt()
            var mm = m.groupValues[2]
            // «730pm» → 7:30
            if (mm.isEmpty() && m.groupValues[1].length == 2 && h > 12) return null
            if (mm.isEmpty() && tok.takeWhile { it.isDigit() }.length in 3..4) {
                val d = tok.takeWhile { it.isDigit() }
                h = d.dropLast(2).toInt(); mm = d.takeLast(2)
            }
            if (h !in 1..12) return null
            if (mm.isEmpty()) mm = "00"
            if (mm.toInt() > 59) return null
            val pm = m.groupValues[3].lowercase().startsWith("p")
            return if (spanish) "$h:$mm ${if (pm) "p. m." else "a. m."}" else "$h:$mm ${if (pm) "PM" else "AM"}"
        }
        // «730pm» sin dos puntos (la expresión de arriba no lo toma)
        Regex("^(\\d{3,4})\\s?(am|pm)$", RegexOption.IGNORE_CASE).matchEntire(tok)?.let { m ->
            val d = m.groupValues[1]
            val h = d.dropLast(2).toInt(); val mm = d.takeLast(2)
            if (h !in 1..12 || mm.toInt() > 59) return null
            val pm = m.groupValues[2].lowercase() == "pm"
            return if (spanish) "$h:$mm ${if (pm) "p. m." else "a. m."}" else "$h:$mm ${if (pm) "PM" else "AM"}"
        }
        MEASURE.matchEntire(tok)?.let { m ->
            val u = m.groupValues[2].lowercase()
            return m.groupValues[1] + " " + (UNITS[u] ?: u)
        }
        CAT.matchEntire(tok)?.let { m -> return "Cat " + m.groupValues[1].uppercase().replace("E", "e") }
        TERMS[tok.lowercase()]?.let { t -> return if (tok == tok.lowercase()) t else null }
        return null
    }
}
