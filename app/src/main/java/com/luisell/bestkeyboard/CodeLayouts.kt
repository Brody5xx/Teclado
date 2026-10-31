package com.luisell.bestkeyboard

/**
 * Teclado del "Modo código": arriba los símbolos de programación (Tab, paréntesis, llaves, comillas,
 * dos puntos, igual), luego números y letras. Mantén una tecla para más símbolos.
 */
object CodeLayouts {
    /** Texto que escribe la tecla Tab: 4 espacios (la sangría de Python). */
    const val TAB = "    "

    private fun k(c: String, alts: String = "", w: Float = 1f) = Key(c, 0, c, w, alts.map { it.toString() })
    private fun sp(label: String, code: Int, w: Float) = Key(label, code, null, w, emptyList(), special = true)

    fun letters(): List<List<Key>> = listOf(
        listOf(
            Key("Tab", 0, TAB, 1.4f, emptyList(), special = true),
            k("(", "<"), k(")", ">"), k("[", "{"), k("]", "}"), k("{"), k("}"),
            k("\"", "'`"), k("'", "\"`"), k(":", ";"), k("=", "!+-*/%<>"),
        ),
        "1234567890".map { k(it.toString()) },
        "qwertyuiop".map { k(it.toString()) },
        "asdfghjkl".map { k(it.toString()) } + k("_", "-"),
        listOf(sp("⇧", Codes.SHIFT, 1.5f)) + "zxcvbnm".map { k(it.toString()) } + sp("⌫", Codes.DELETE, 1.5f),
        listOf(
            sp("?123", Codes.MODE_SYM, 1.5f), k(",", ";"), k("#", "@$&|\\"),
            Key(" ", Codes.SPACE, null, 3.6f, emptyList()),
            k(".", ",?!"), sp("↵", Codes.ENTER, 1.5f),
        ),
    )
}
