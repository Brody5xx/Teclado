package com.luisell.bestkeyboard.words

/**
 * Convierte lo dictado en texto bien escrito:
 * "hola coma cómo estás signo de interrogación" -> "hola, ¿cómo estás?"
 * Solo usa frases claras para no cambiar palabras normales (por eso "punto" solo no cuenta,
 * se dice "punto final" o "punto y seguido").
 */
object VoiceText {

    private val ES = listOf(
        "punto y aparte" to ".\n",
        "punto y seguido" to ".",
        "punto y coma" to ";",
        "punto final" to ".",
        "dos puntos" to ":",
        "nueva línea" to "\n",
        "nueva linea" to "\n",
        "nuevo párrafo" to "\n\n",
        "signo de interrogación" to "?",
        "signo de pregunta" to "?",
        "signo de exclamación" to "!",
        "puntos suspensivos" to "…",
        "coma" to ",",
    )

    private val EN = listOf(
        "new paragraph" to "\n\n",
        "new line" to "\n",
        "full stop" to ".",
        "period" to ".",
        "question mark" to "?",
        "exclamation mark" to "!",
        "exclamation point" to "!",
        "semicolon" to ";",
        "colon" to ":",
        "comma" to ",",
    )

    fun process(raw: String, lang: String): String {
        var s = " " + raw.trim() + " "
        val rules = if (lang == "es") ES else EN
        for ((phrase, sym) in rules) {
            s = Regex("(?i)\\s+" + Regex.escape(phrase) + "(?=[\\s.,;:!?]|$)").replace(s, sym)
        }
        s = s.replace(Regex("[ \\t]+"), " ")
        // Sin espacio antes de signos; un espacio después
        s = s.replace(Regex(" ([.,;:!?…])"), "$1")
        s = s.replace(Regex("([.,;:!?…])(?=[^\\s.,;:!?…\\n])"), "$1 ")
        s = s.replace(Regex(" *\\n *"), "\n")
        s = s.trim(' ')
        // ❓ Si es una pregunta y no dijiste el signo, se pone solo
        if (s.isNotEmpty() && s.last().isLetterOrDigit() && looksLikeQuestion(s, lang)) s += "?"
        if (lang == "es") s = addSpanishOpeners(s)
        return capitalizeSentences(s)
    }

    private val Q_EN = setOf("what", "where", "when", "why", "who", "whose", "which", "how", "can", "could", "would", "will",
        "do", "does", "did", "is", "are", "was", "were", "should", "may", "have", "has", "am", "isn't", "aren't", "don't", "doesn't", "didn't")
    private val Q_ES = setOf("qué", "cómo", "dónde", "cuándo", "quién", "quiénes", "cuál", "cuáles", "cuánto", "cuánta", "cuántos",
        "cuántas", "puedes", "podrías", "tienes", "sabes", "vienes", "quieres", "vas", "estás", "me puedes", "por qué")

    /** La última frase empieza como pregunta («what time…», «cómo estás», «puedes…»). */
    private fun looksLikeQuestion(s: String, lang: String): Boolean {
        val start = maxOf(s.lastIndexOf('.'), s.lastIndexOf('!'), s.lastIndexOf('?'), s.lastIndexOf('\n')) + 1
        val sent = s.substring(start)
        // La frase entera, o lo que va después de la última coma («hola, qué hora es»)
        val parts = listOfNotNull(sent, sent.substringAfterLast(", ", "").ifEmpty { null })
        for (part in parts) {
            val ws = part.lowercase().split(Regex("[^\\p{L}'’]+")).filter { it.isNotEmpty() }
            if (ws.size < 2) continue
            val first = ws[0]
            val accented = lang == "es" && first.any { it in "áéíóú" } && first in Q_ES
            if (ws.size < 3 && !accented) continue
            val q = if (lang == "es") first in Q_ES || (first == "por" && ws[1] == "qué") || (first == "me" && ws[1] in setOf("puedes", "podrías", "mandas", "avisas"))
                else first in Q_EN
            if (q) return true
        }
        return false
    }

    /** "cómo estás?" -> "¿cómo estás?" (español abre las preguntas y exclamaciones). */
    private fun addSpanishOpeners(s: String): String {
        val sb = StringBuilder()
        var start = 0
        for (i in s.indices) {
            val c = s[i]
            if (c == '.' || c == '\n' || c == '?' || c == '!' || c == '…') {
                var piece = s.substring(start, i + 1)
                if (c == '?' || c == '!') {
                    val open = if (c == '?') '¿' else '¡'
                    // Abre después del último punto o coma ("Hola, ¿cómo estás?")
                    val lead = piece.length - piece.trimStart().length
                    val body = piece.substring(lead)
                    if (!body.startsWith(open)) {
                        val comma = body.lastIndexOf(", ")
                        piece = if (comma >= 0 && comma < body.length - 3) {
                            piece.substring(0, lead) + body.substring(0, comma + 2) + open + body.substring(comma + 2)
                        } else {
                            piece.substring(0, lead) + open + body
                        }
                    }
                }
                sb.append(piece)
                start = i + 1
            }
        }
        sb.append(s.substring(start))
        return sb.toString()
    }

    /** Mayúscula después de . ? ! y salto de línea. */
    private fun capitalizeSentences(s: String): String {
        val sb = StringBuilder(s)
        var cap = false
        for (i in sb.indices) {
            val c = sb[i]
            if (c == '.' || c == '?' || c == '!' || c == '\n' || c == '…') { cap = true; continue }
            if (cap && c.isLetter()) { sb.setCharAt(i, c.uppercaseChar()); cap = false }
            else if (cap && !c.isWhitespace() && c != '¿' && c != '¡') cap = false
        }
        return sb.toString()
    }
}
