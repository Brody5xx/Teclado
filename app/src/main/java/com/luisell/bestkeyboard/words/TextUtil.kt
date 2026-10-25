package com.luisell.bestkeyboard.words

/** Herramientas de texto pequeñas y rápidas para las sugerencias. */
object TextUtil {

    /** Quita acentos, apóstrofos y mayúsculas: "También" -> "tambien", "don't" -> "dont". */
    fun fold(w: CharSequence): String {
        val sb = StringBuilder(w.length)
        for (ch in w) {
            when (val c = ch.lowercaseChar()) {
                'à', 'á', 'â', 'ã', 'ä', 'å' -> sb.append('a')
                'è', 'é', 'ê', 'ë' -> sb.append('e')
                'ì', 'í', 'î', 'ï' -> sb.append('i')
                'ò', 'ó', 'ô', 'õ', 'ö' -> sb.append('o')
                'ù', 'ú', 'û', 'ü' -> sb.append('u')
                'ñ' -> sb.append('n')
                'ç' -> sb.append('c')
                'ý', 'ÿ' -> sb.append('y')
                '\'', '’' -> Unit
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    fun isWordChar(c: Char) = c.isLetter() || c == '\'' || c == '’'

    /** La palabra que se está escribiendo justo antes del cursor. */
    fun currentWord(before: CharSequence): String {
        var i = before.length
        while (i > 0 && isWordChar(before[i - 1])) i--
        var start = i
        while (start < before.length && (before[start] == '\'' || before[start] == '’')) start++
        return before.subSequence(start, before.length).toString()
    }

    /**
     * La palabra anterior (antes de los espacios), sin contar la palabra actual.
     * Si hay un signo de puntuación en medio, devuelve null (empieza otra frase).
     */
    fun previousWord(before: CharSequence, currentLength: Int): String? {
        var i = before.length - currentLength
        var sawSpace = false
        while (i > 0 && before[i - 1] == ' ') { i--; sawSpace = true }
        if (!sawSpace || i == 0) return null
        if (!isWordChar(before[i - 1])) return null
        val end = i
        while (i > 0 && isWordChar(before[i - 1])) i--
        val w = before.subSequence(i, end).toString().trim('\'', '’')
        return w.ifEmpty { null }
    }

    /** ¿Empieza una oración en esta posición? (inicio del texto o después de . ? ! y espacio) */
    fun isSentenceStart(before: CharSequence, wordLength: Int): Boolean {
        var i = before.length - wordLength
        while (i > 0 && before[i - 1] == ' ') i--
        if (i == 0) return true
        val c = before[i - 1]
        return c == '.' || c == '?' || c == '!' || c == '\n' || c == '¿' || c == '¡'
    }

    /** Copia las mayúsculas de lo que escribiste: "TAMBIEN" -> "TAMBIÉN", "Tambien" -> "También". */
    fun matchCase(word: String, typed: String): String {
        if (typed.isEmpty() || word.isEmpty()) return word
        val letters = typed.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return word.uppercase()
        if (typed[0].isUpperCase()) return word.replaceFirstChar { it.uppercaseChar() }
        return word
    }
}
