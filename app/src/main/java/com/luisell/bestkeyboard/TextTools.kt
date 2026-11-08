package com.luisell.bestkeyboard

import java.util.Calendar

/**
 * 📅 «Agregar al calendario»: encuentra la fecha y la hora en lo que escribiste o te llegó
 * («mañana a las 3 reunión», «friday 9am inspection», «el 15 de noviembre 7:30pm»).
 * 🧹 «Pegar limpio»: arregla el texto copiado.
 */
object TextTools {

    class Event(val title: String, val start: Calendar, val hasDate: Boolean, val hasTime: Boolean)

    private val DAYS = mapOf(
        "domingo" to Calendar.SUNDAY, "lunes" to Calendar.MONDAY, "martes" to Calendar.TUESDAY, "miercoles" to Calendar.WEDNESDAY,
        "miércoles" to Calendar.WEDNESDAY, "jueves" to Calendar.THURSDAY, "viernes" to Calendar.FRIDAY, "sabado" to Calendar.SATURDAY, "sábado" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY, "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY, "wednesday" to Calendar.WEDNESDAY,
        "thursday" to Calendar.THURSDAY, "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY,
    )
    private val MONTHS = mapOf(
        "enero" to 0, "febrero" to 1, "marzo" to 2, "abril" to 3, "mayo" to 4, "junio" to 5, "julio" to 6, "agosto" to 7,
        "septiembre" to 8, "setiembre" to 8, "octubre" to 9, "noviembre" to 10, "diciembre" to 11,
        "january" to 0, "jan" to 0, "february" to 1, "feb" to 1, "march" to 2, "mar" to 2, "april" to 3, "apr" to 3, "may" to 4,
        "june" to 5, "jun" to 5, "july" to 6, "jul" to 6, "august" to 7, "aug" to 7, "september" to 8, "sep" to 8, "sept" to 8,
        "october" to 9, "oct" to 9, "november" to 10, "nov" to 10, "december" to 11, "dec" to 11,
    )

    fun findEvent(raw: String, englishFirst: Boolean): Event? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        var t = " " + text.lowercase() + " "
        val now = Calendar.getInstance()
        val c = Calendar.getInstance()
        var hasDate = false
        var hasTime = false
        val used = ArrayList<IntRange>()
        fun mark(r: IntRange) { used.add(r) }

        // ---- La fecha
        Regex("""\bpasado mañana\b|\bday after tomorrow\b""").find(t)?.let { c.add(Calendar.DAY_OF_YEAR, 2); hasDate = true; mark(it.range) }
        if (!hasDate) Regex("""\b(mañana|tomorrow|tmrw|tmr)\b(?! (por la|de la) )""").find(t)?.let {
            // «de la mañana» es la hora (AM), no el día
            if (!Regex("""(de|por) la mañana""").containsMatchIn(t.substring(maxOf(0, it.range.first - 7), minOf(t.length, it.range.last + 1)))) {
                c.add(Calendar.DAY_OF_YEAR, 1); hasDate = true; mark(it.range)
            }
        }
        if (!hasDate) Regex("""\b(hoy|today|tonight|esta noche)\b""").find(t)?.let { hasDate = true; mark(it.range) }
        if (!hasDate) {
            Regex("""\b(\d{1,2}) de (${MONTHS.keys.joinToString("|")})\b""").find(t)?.let { m ->
                c.set(Calendar.MONTH, MONTHS[m.groupValues[2]]!!); c.set(Calendar.DAY_OF_MONTH, m.groupValues[1].toInt()); hasDate = true; mark(m.range)
            } ?: Regex("""\b(${MONTHS.keys.joinToString("|")})\.? (\d{1,2})(st|nd|rd|th)?\b""").find(t)?.let { m ->
                c.set(Calendar.MONTH, MONTHS[m.groupValues[1]]!!); c.set(Calendar.DAY_OF_MONTH, m.groupValues[2].toInt()); hasDate = true; mark(m.range)
            } ?: Regex("""\b(\d{1,2})/(\d{1,2})(/(\d{2,4}))?\b""").find(t)?.let { m ->
                val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt()
                val (mo, dd) = when { a > 12 -> b to a; b > 12 -> a to b; englishFirst -> a to b; else -> b to a }
                if (mo in 1..12 && dd in 1..31) {
                    c.set(Calendar.MONTH, mo - 1); c.set(Calendar.DAY_OF_MONTH, dd)
                    m.groupValues[4].toIntOrNull()?.let { y -> c.set(Calendar.YEAR, if (y < 100) 2000 + y else y) }
                    hasDate = true; mark(m.range)
                }
            } ?: Regex("""\b(el|this|next|el próximo|el proximo|este)? ?(${DAYS.keys.joinToString("|")})\b""").find(t)?.let { m ->
                val want = DAYS[m.groupValues[2]]!!
                var add = (want - c.get(Calendar.DAY_OF_WEEK) + 7) % 7
                if (m.groupValues[1].startsWith("next") || m.groupValues[1].contains("próximo") || m.groupValues[1].contains("proximo")) if (add == 0) add = 7
                c.add(Calendar.DAY_OF_YEAR, add); hasDate = true; mark(m.range)
            } ?: Regex("""\bel (\d{1,2})\b""").find(t)?.let { m ->
                val dd = m.groupValues[1].toInt()
                if (dd in 1..31) {
                    c.set(Calendar.DAY_OF_MONTH, dd)
                    if (c.before(now) && !sameDay(c, now)) c.add(Calendar.MONTH, 1)
                    hasDate = true; mark(m.range)
                }
            }
            // Una fecha que ya pasó este año es del año que viene
            if (hasDate && c.before(now) && !sameDay(c, now) && c.get(Calendar.YEAR) == now.get(Calendar.YEAR)) c.add(Calendar.YEAR, 1)
        }

        // ---- La hora
        val tm = Regex("""\b(a las|a la|at|@)? ?(\d{1,2})(:(\d{2}))? ?(a\.? ?m\.?|p\.? ?m\.?|am|pm)?( ?de la (mañana|tarde|noche)| ?(in the) (morning|afternoon|evening))?\b""")
        for (m in tm.findAll(t)) {
            val prefix = m.groupValues[1]
            val ampm = m.groupValues[5].replace(".", "").replace(" ", "")
            val part = m.groupValues[7].ifEmpty { when (m.groupValues[9]) { "morning" -> "mañana"; "afternoon" -> "tarde"; "evening" -> "noche"; else -> "" } }
            val hasMin = m.groupValues[4].isNotEmpty()
            // Solo cuenta como hora si dice «a las», tiene minutos, am/pm o «de la tarde»
            if (prefix.isEmpty() && !hasMin && ampm.isEmpty() && part.isEmpty()) continue
            if (used.any { it.first <= m.range.first && m.range.last <= it.last }) continue
            var h = m.groupValues[2].toInt()
            val min = m.groupValues[4].toIntOrNull() ?: 0
            if (h > 23 || min > 59) continue
            when {
                ampm == "pm" || part == "tarde" || part == "noche" -> if (h < 12) h += 12
                ampm == "am" || part == "mañana" -> if (h == 12) h = 0
                h in 1..7 -> h += 12 // «a las 3» casi siempre es de la tarde
            }
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, min)
            hasTime = true; mark(m.range)
            break
        }
        if (!hasDate && !hasTime) return null
        if (!hasTime) { c.set(Calendar.HOUR_OF_DAY, 9); c.set(Calendar.MINUTE, 0) }
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        // Si solo dijiste la hora y ya pasó hoy, es mañana
        if (!hasDate && c.before(now)) c.add(Calendar.DAY_OF_YEAR, 1)

        // ---- El título: lo que queda sin la fecha ni la hora
        val sb = StringBuilder(t)
        for (r in used.sortedByDescending { it.first }) sb.replace(r.first, minOf(sb.length, r.last + 1), " ")
        var title = sb.toString().replace(Regex("""\s+"""), " ").trim().trim(',', '.', '-', ':', ';', '!', '?')
        title = title.replace(Regex("""^(y|and|para|for|el|la|on)\s+""", RegexOption.IGNORE_CASE), "").trim()
        if (title.isNotEmpty()) {
            // Se usa el texto original (con sus mayúsculas) si se puede
            val i = text.lowercase().indexOf(title)
            title = if (i >= 0) text.substring(i, i + title.length) else title.replaceFirstChar { it.uppercase() }
        }
        return Event(title.take(80), c, hasDate, hasTime)
    }

    private fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    // ---------------------------------------------------------------- 🧹 Pegar limpio

    /** Quita caracteres invisibles, espacios dobles y renglones vacíos de más. */
    fun clean(s: String): String = s
        .replace(Regex("[​-‍﻿⁠­]"), "")
        .replace(' ', ' ').replace('\t', ' ')
        .lines().joinToString("\n") { it.replace(Regex(" {2,}"), " ").trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** Todo en un solo renglón. */
    fun oneLine(s: String): String = clean(s).replace(Regex("""\s*\n\s*"""), " ").replace(Regex(" {2,}"), " ").trim()

    /** Sin emojis. */
    fun noEmoji(s: String): String = clean(Emo.stripUi(s))

    /** Sin ningún espacio (números de serie, MAC, códigos). */
    fun noSpaces(s: String): String = s.replace(Regex("""\s+"""), "").replace(Regex("[​-‍﻿]"), "")
}
