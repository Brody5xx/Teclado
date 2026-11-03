package com.luisell.bestkeyboard

/**
 * 🏷️ Etiquetas en serie: de «PP1-01 a PP1-24» hace la lista completa
 * (PP1-01, PP1-02 … PP1-24). También «CAM-1 x 12» (12 empezando en CAM-1).
 * Respeta los ceros (01, 02…) y lo que va antes y después del número.
 */
object Labels {
    const val MAX = 500

    fun make(input: String): List<String>? {
        val s = input.trim()
        if (s.isEmpty()) return null
        // «inicio x cantidad»
        Regex("^(.*?\\d+\\D*?)\\s*[x×*]\\s*(\\d+)$", RegexOption.IGNORE_CASE).find(s)?.let { m ->
            val start = m.groupValues[1].trim()
            val count = m.groupValues[2].toIntOrNull() ?: return null
            val p = parts(start) ?: return null
            return series(p, p.third, p.third + count - 1)
        }
        // «inicio a fin», «inicio to fin», «inicio hasta fin», «inicio .. fin», «inicio → fin»
        val sep = Regex("\\s+(?:a|to|hasta|al|thru|through)\\s+|\\s*(?:\\.\\.|→|~)\\s*", RegexOption.IGNORE_CASE)
        val two = s.split(sep).map { it.trim() }.filter { it.isNotEmpty() }
        if (two.size != 2) return null
        val a = parts(two[0]) ?: return null
        val endNum = Regex("(\\d+)(?!.*\\d)").find(two[1])?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return series(a, a.third, endNum)
    }

    /** (antes del número, después del número, número, cuántos dígitos). */
    private fun parts(t: String): Triple<String, String, Int>? {
        val m = Regex("(\\d+)(?!.*\\d)").find(t) ?: return null
        width = m.value.length
        return Triple(t.substring(0, m.range.first), t.substring(m.range.last + 1), m.value.toIntOrNull() ?: return null)
    }
    private var width = 1

    private fun series(p: Triple<String, String, Int>, from: Int, to: Int): List<String>? {
        val w = width
        val step = if (to >= from) 1 else -1
        val n = kotlin.math.abs(to - from) + 1
        if (n > MAX) return null
        return (0 until n).map { i -> p.first + (from + i * step).toString().padStart(w, '0') + p.second }
    }

    /** Lo que ves mientras escribes. */
    fun preview(input: String): String {
        val l = make(input) ?: return L.t("Ej. «PP1-01 a PP1-24» o «CAM-1 x 12» (máx. $MAX)", "E.g. «PP1-01 to PP1-24» or «CAM-1 x 12» (max $MAX)")
        val show = if (l.size <= 4) l.joinToString(", ") else l.take(3).joinToString(", ") + " … " + l.last()
        return L.t("${l.size} etiqueta(s): ", "${l.size} label(s): ") + show + L.t("  · Enter las escribe", "  · Enter types them")
    }
}
