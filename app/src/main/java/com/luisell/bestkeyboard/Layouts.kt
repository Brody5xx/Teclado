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
    /** ↩️ Deshacer (tecla al lado del punto). */
    const val UNDO = -8
    /** 🇨🇳 Cambia el teclado chino entre pinyin (letras) y trazos (一丨丿丶乛). */
    const val ZH_TOGGLE = -9
    const val SPACE = 32

    // Teclado de computadora ("hacker")
    const val CTRL = -20
    const val ALT = -21
    const val FN = -22
    const val HSHIFT = -23
    /** Teclas especiales de computadora: código = HKEY_BASE - KeyEvent.KEYCODE_... */
    const val HKEY_BASE = -1000
    fun isHKey(code: Int) = code <= HKEY_BASE
    fun hkey(keyCode: Int) = HKEY_BASE - keyCode
    fun keyCodeOf(code: Int) = HKEY_BASE - code
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
    /** Se repite mientras la mantienes (flechas del teclado de computadora). */
    val repeatable: Boolean = false,
    /** Símbolo pequeño arriba a la derecha (lo que sale con Shift). */
    val topHint: String? = null,
    /** Espacio vacío a la izquierda/derecha (en «anchos de tecla»): la tecla se ve normal pero se puede tocar en ese hueco. */
    val padL: Float = 0f,
    val padR: Float = 0f,
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

    /** Fila de números, con ↶ Deshacer a la derecha del 0. */
    private fun numberRow(undo: Boolean = true) = "1234567890".map { ch(it.toString(), NUM_ALTS[it] ?: "") } +
        (if (undo) listOf(sp("↶", Codes.UNDO, 1f)) else emptyList())

    /** Si es false, no hay botón 🌐 (el idioma se cambia deslizando la barra espaciadora). */
    var showGlobe = true

    /** Las teclas de los 5 trazos: escriben un código interno que el teclado convierte en caracteres. */
    val STROKE_KEYS = mapOf("\uE101" to 'h', "\uE102" to 's', "\uE103" to 'p', "\uE104" to 'n', "\uE105" to 'z')
    val STROKE_SHOW = mapOf('h' to "一", 's' to "丨", 'p' to "丿", 'n' to "丶", 'z' to "乛")

    /** 🇨🇳 Teclado chino de trazos (笔画), como los teclados chinos: 一 丨 丿 丶 乛. */
    fun strokes(withNumberRow: Boolean): List<List<Key>> {
        val rows = mutableListOf<List<Key>>()
        if (withNumberRow) rows += numberRow()
        rows += listOf(Key("一", 0, "\uE101", 1f, emptyList()), Key("丨", 0, "\uE102", 1f, emptyList()),
            Key("丿", 0, "\uE103", 1f, emptyList()), Key("丶", 0, "\uE104", 1f, emptyList()), Key("乛", 0, "\uE105", 1f, emptyList()))
        rows += listOf(ch("，", "、；"), ch("。", "…"), ch("？"), ch("！"), del(1f))
        rows += listOf(sp("符号", Codes.MODE_SYM, 1.5f), Key("，", 0, "，", 1f, emptyList(), emojiKey = true),
            sp("拼音", Codes.ZH_TOGGLE, 1.2f), Key(" ", Codes.SPACE, null, 3.3f, emptyList()),
            ch("。", "，？！：；、…“”（）."), sp("↵", Codes.ENTER, 1.5f))
        return rows
    }

    /** 🇨🇳 Teclado en chino: los nombres de las teclas especiales en chino (符号, 中文…). */
    var chineseUi = false

    private fun bottomRow(modeLabel0: String, modeCode: Int, comma: String, undo: Boolean = false, chinese: Boolean = false): List<Key> {
        val modeLabel = if (chineseUi && modeLabel0 == "ABC") "中文" else modeLabel0
        val commaKey = if (chinese) Key("，", 0, "，", 1f, emptyList(), emojiKey = true) else when (comma) {
            "@" -> ch("@", ",")
            "/" -> ch("/", ",:")
            else -> Key(",", 0, ",", 1f, emptyList(), emojiKey = true)
        }
        return listOfNotNull(
            sp(if (chinese) "符号" else modeLabel, modeCode, 1.5f),
            commaKey,
            if (showGlobe) sp("🌐", Codes.LANG, 1f) else null,
            if (chinese) sp("笔画", Codes.ZH_TOGGLE, 1f) else null,
            Key(" ", Codes.SPACE, null, (if (showGlobe) 4f else 5f) - (if (undo) 1f else 0f) - (if (chinese) 1f else 0f), emptyList()),
            if (chinese) ch("。", "，？！：；、…“”（）.") else ch(".", ",?¿!¡'\"-:;@/…"),
            if (undo) sp("↶", Codes.UNDO, 1f) else null, // ↶ Deshacer (si no hay fila de números)
            sp("↵", Codes.ENTER, 1.5f),
        )
    }

    /** [english] = teclado en inglés: sin la ñ en su fila (sale manteniendo la n), como un teclado de EE. UU. */
    /** [chinese] = teclado de pinyin: la coma y el punto son los chinos (，。). */
    fun letters(withNumberRow: Boolean, comma: String, english: Boolean = false, chinese: Boolean = false): List<List<Key>> {
        val rows = mutableListOf<List<Key>>()
        if (withNumberRow) rows += numberRow()
        val digits = "1234567890"
        rows += "qwertyuiop".mapIndexed { i, c ->
            // Sin fila de números, el número sale al mantener la letra (como Gboard)
            val extra = if (withNumberRow) "" else digits[i].toString()
            ch(c.toString(), extra + (ALTS[c] ?: ""))
        }
        if (english) {
            // 9 teclas centradas: la «a» y la «l» se pueden tocar también en el hueco de su lado
            val mid = "asdfghjkl"
            rows += mid.mapIndexed { i, c ->
                val alts = split(ALTS[c] ?: "")
                when (i) {
                    0 -> Key(c.toString(), 0, c.toString(), 1.5f, alts, padL = 0.5f)
                    mid.lastIndex -> Key(c.toString(), 0, c.toString(), 1.5f, alts, padR = 0.5f)
                    else -> Key(c.toString(), 0, c.toString(), 1f, alts)
                }
            }
        } else rows += "asdfghjklñ".map { ch(it.toString(), ALTS[it] ?: "") }
        rows += listOf(sp("⇧", Codes.SHIFT, 1.5f)) +
            "zxcvbnm".map { ch(it.toString(), ALTS[it] ?: "") } +
            del()
        rows += bottomRow("?123", Codes.MODE_SYM, comma, undo = !withNumberRow, chinese = chinese)
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

    /**
     * 🔢 Teclado para IP y MAC: números grandes, punto, dos puntos y las letras A–F
     * (ej. 192.168.1.20 o 00:1A:2B:3C:4D:5E). «ABC» vuelve a las letras.
     */
    fun ipPad(): List<List<Key>> = listOf(
        listOf(ch("1"), ch("2"), ch("3"), ch("A", "a"), ch("B", "b")),
        listOf(ch("4"), ch("5"), ch("6"), ch("C", "c"), ch("D", "d")),
        listOf(ch("7"), ch("8"), ch("9"), ch("E", "e"), ch("F", "f")),
        listOf(ch(".", ","), ch("0"), ch(":"), ch("-", "_"), ch("/", "\\")),
        listOf(sp("ABC", Codes.MODE_LETTERS, 1.3f), Key(" ", Codes.SPACE, null, 1.6f, emptyList()), del(1.1f), sp("↵", Codes.ENTER, 1.1f)),
    )

    /** Teclado numérico grande para campos de números o teléfono. */
    fun numpad(phone: Boolean): List<List<Key>> = listOf(
        listOf(ch("1"), ch("2"), ch("3"), ch("-", "–")),
        listOf(ch("4"), ch("5"), ch("6"), if (phone) Key(" ", Codes.SPACE, null, 1f, emptyList()) else ch(",")),
        listOf(ch("7"), ch("8"), ch("9"), del(1f)),
        listOf(ch("+", "*#(),;/"), ch("0"), ch(".", ","), sp("↵", Codes.ENTER, 1f)),
    )
}
