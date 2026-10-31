package com.luisell.bestkeyboard

import android.graphics.RectF

/** Códigos de las teclas especiales. Las teclas normales usan 0 y escriben su texto. */
object Codes {
    const val SHIFT = -1
    const val MODE_SYM = -2
    const val MODE_LETTERS = -3
    const val ENTER = -4
    const val DELETE = -5
    const val LANG = -6
    const val MODE_SYM2 = -7
    const val SPACE = 32
}

/** Una tecla. `cell` es el área que responde al toque, `rect` es lo que se dibuja. */
class Key(
    val label: String,
    val code: Int,
    val text: String?,
    val weight: Float,
    val alts: List<String>,
    val special: Boolean = false,
    /** Mantener presionada abre los emojis (la coma). */
    val emojiKey: Boolean = false,
) {
    val cell = RectF()
    val rect = RectF()
}

/** Todas las distribuciones del teclado (letras, símbolos, números). */
object Layouts {

    // Letras con acentos al mantener presionado
    private val ALTS = mapOf(
        'e' to "éèêë€", 'y' to "ý¥", 'u' to "úüùû", 'i' to "íìîï", 'o' to "óòôöõœº",
        'a' to "áàâäãåæª", 's' to "ß$§", 'c' to "ç¢©", 'n' to "ñ",
    )

    private val NUM_ALTS = mapOf(
        '1' to "¹½⅓¼", '2' to "²⅔", '3' to "³¾", '4' to "⁴", '0' to "ⁿ°∅",
    )

    private val SYM_ALTS = mapOf(
        "$" to "€£¥¢₹", "&" to "§", "-" to "–—·", "+" to "±", "(" to "[{<", ")" to "]}>",
        "/" to "\\|", "*" to "★†‡", "\"" to "“”„«»", "'" to "‘’‚‹›", "!" to "¡", "?" to "¿",
        "#" to "№", "%" to "‰", "=" to "≠≈∞", "^" to "←↑↓→",
    )

    private fun split(s: String) = s.map { it.toString() }

    private fun ch(c: String, alts: String = "", w: Float = 1f) = Key(c, 0, c, w, split(alts))

    private fun sp(label: String, code: Int, w: Float) = Key(label, code, null, w, emptyList(), special = true)

    private fun del(w: Float = 1.5f) = sp("⌫", Codes.DELETE, w)

    private fun numberRow() = "1234567890".map { ch(it.toString(), NUM_ALTS[it] ?: "") }

    /** Si es false, no hay botón 🌐 (el idioma se cambia deslizando la barra espaciadora). */
    var showGlobe = true

    private fun bottomRow(modeLabel: String, modeCode: Int, comma: String): List<Key> {
        val commaKey = when (comma) {
            "@" -> ch("@", ",")
            "/" -> ch("/", ",:")
            else -> Key(",", 0, ",", 1f, emptyList(), emojiKey = true)
        }
        return listOfNotNull(
            sp(modeLabel, modeCode, 1.5f),
            commaKey,
            if (showGlobe) sp("🌐", Codes.LANG, 1f) else null,
            Key(" ", Codes.SPACE, null, if (showGlobe) 4f else 5f, emptyList()),
            ch(".", ",?¿!¡'\"-:;@/…"),
            sp("↵", Codes.ENTER, 1.5f),
        )
    }

    fun letters(withNumberRow: Boolean, comma: String): List<List<Key>> {
        val rows = mutableListOf<List<Key>>()
        if (withNumberRow) rows += numberRow()
        val digits = "1234567890"
        rows += "qwertyuiop".mapIndexed { i, c ->
            // Sin fila de números, el número sale al mantener la letra (como Gboard)
            val extra = if (withNumberRow) "" else digits[i].toString()
            ch(c.toString(), extra + (ALTS[c] ?: ""))
        }
        rows += "asdfghjklñ".map { ch(it.toString(), ALTS[it] ?: "") }
        rows += listOf(sp("⇧", Codes.SHIFT, 1.5f)) +
            "zxcvbnm".map { ch(it.toString(), ALTS[it] ?: "") } +
            del()
        rows += bottomRow("?123", Codes.MODE_SYM, comma)
        return rows
    }

    fun symbols(): List<List<Key>> = listOf(
        numberRow(),
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { ch(it, SYM_ALTS[it] ?: "") },
        listOf(sp("=\\<", Codes.MODE_SYM2, 1.5f)) +
            listOf("*", "\"", "'", ":", ";", "!", "?").map { ch(it, SYM_ALTS[it] ?: "") } +
            del(),
        bottomRow("ABC", Codes.MODE_LETTERS, ","),
    )

    fun symbols2(): List<List<Key>> = listOf(
        split("~`|•√π÷×¶∆").map { ch(it) },
        split("£€¥¢^°={}\\").map { ch(it, SYM_ALTS[it] ?: "") },
        listOf(sp("?123", Codes.MODE_SYM, 1.5f)) + split("%©®™✓[]").map { ch(it, SYM_ALTS[it] ?: "") } + del(),
        bottomRow("ABC", Codes.MODE_LETTERS, ","),
    )

    /** Teclado numérico grande para campos de números o teléfono. */
    fun numpad(phone: Boolean): List<List<Key>> = listOf(
        listOf(ch("1"), ch("2"), ch("3"), ch("-", "–")),
        listOf(ch("4"), ch("5"), ch("6"), if (phone) Key(" ", Codes.SPACE, null, 1f, emptyList()) else ch(",")),
        listOf(ch("7"), ch("8"), ch("9"), del(1f)),
        listOf(ch("+", "*#(),;/"), ch("0"), ch(".", ","), sp("↵", Codes.ENTER, 1f)),
    )
}
