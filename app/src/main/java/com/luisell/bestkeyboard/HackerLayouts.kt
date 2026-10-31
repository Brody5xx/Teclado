package com.luisell.bestkeyboard

import android.view.KeyEvent

/**
 * Teclado de computadora ("modo hacker"), como el de una PC:
 * Esc, Tab, Ctrl, Alt, Shift, flechas, y con Fn: F1–F12, Inicio/Fin, RePág/AvPág, números.
 * Sirve para terminales (Termux, SSH) y para escribir código.
 */
object HackerLayouts {

    /** Lo que sale con Shift en cada tecla (como en un teclado de EE.UU.). */
    private const val NORMAL = "`1234567890-=[]\\;',./"
    private const val SHIFTED = "~!@#$%^&*()_+{}|:\"<>?"

    fun shifted(c: Char): Char {
        val i = NORMAL.indexOf(c)
        return if (i >= 0) SHIFTED[i] else c.uppercaseChar()
    }

    /** Tecla que escribe un carácter (con Shift muestra su otra cara). */
    private fun ch(c: Char, shift: Boolean, w: Float = 1f): Key {
        val other = shifted(c)
        val shown = if (shift) other else c
        val hint = if (!c.isLetter() && other != c) (if (shift) c else other).toString() else null
        val alts = if (other != c) listOf((if (shift) c else other).toString()) else emptyList()
        return Key(shown.toString(), 0, shown.toString(), w, alts, topHint = hint)
    }

    private fun row(chars: String, shift: Boolean) = chars.map { ch(it, shift) }

    private fun sp(label: String, code: Int, w: Float = 1f, repeat: Boolean = false) =
        Key(label, code, null, w, emptyList(), special = true, repeatable = repeat)

    private fun hk(label: String, keyCode: Int, w: Float = 1f, repeat: Boolean = false) =
        sp(label, Codes.hkey(keyCode), w, repeat)

    private fun arrows() = listOf(
        hk("←", KeyEvent.KEYCODE_DPAD_LEFT, repeat = true),
        hk("↓", KeyEvent.KEYCODE_DPAD_DOWN, repeat = true),
        hk("→", KeyEvent.KEYCODE_DPAD_RIGHT, repeat = true),
    )

    private fun bottom() = listOf(
        hk("Esc", KeyEvent.KEYCODE_ESCAPE, 1.3f),
        sp("Fn", Codes.FN, 1.2f),
        sp("Alt", Codes.ALT, 1.2f),
        Key(" ", Codes.SPACE, null, 5f, emptyList()),
    ) + arrows()

    /** Página principal: letras, números y símbolos de programación. */
    fun main(shift: Boolean): List<List<Key>> = listOf(
        row("`1234567890-=", shift) + sp("⌫", Codes.DELETE, 1.5f),
        listOf(hk("Tab", KeyEvent.KEYCODE_TAB, 1.5f)) + row("qwertyuiop[]\\", shift),
        listOf(sp("Ctrl", Codes.CTRL, 1.75f)) + row("asdfghjkl;'", shift) + sp("↵", Codes.ENTER, 1.75f),
        listOf(sp("⇧", Codes.HSHIFT, 1.5f)) + row("zxcvbnm,./", shift) +
            hk("↑", KeyEvent.KEYCODE_DPAD_UP, repeat = true) + sp("⇧", Codes.HSHIFT, 1f),
        bottom(),
    )

    /** Página Fn: F1–F12, teclado numérico e Inicio/Fin/RePág/AvPág. */
    fun fn(): List<List<Key>> = listOf(
        listOf(
            hk("F1", KeyEvent.KEYCODE_F1), hk("F2", KeyEvent.KEYCODE_F2), hk("F3", KeyEvent.KEYCODE_F3),
            hk("F4", KeyEvent.KEYCODE_F4),
        ) + row("789/", false) + hk("Home", KeyEvent.KEYCODE_MOVE_HOME, 1.3f) + sp("⌫", Codes.DELETE, 1.3f),
        listOf(
            hk("F5", KeyEvent.KEYCODE_F5), hk("F6", KeyEvent.KEYCODE_F6), hk("F7", KeyEvent.KEYCODE_F7),
            hk("F8", KeyEvent.KEYCODE_F8),
        ) + row("456*", false) + hk("End", KeyEvent.KEYCODE_MOVE_END, 1.3f) + hk("Del", KeyEvent.KEYCODE_FORWARD_DEL, 1.3f, repeat = true),
        listOf(
            hk("F9", KeyEvent.KEYCODE_F9), hk("F10", KeyEvent.KEYCODE_F10), hk("F11", KeyEvent.KEYCODE_F11),
            hk("F12", KeyEvent.KEYCODE_F12),
        ) + row("123-", false) + hk("PgUp", KeyEvent.KEYCODE_PAGE_UP, 1.3f, repeat = true) + sp("↵", Codes.ENTER, 1.3f),
        listOf(
            sp("⇧", Codes.HSHIFT), hk("Ins", KeyEvent.KEYCODE_INSERT), hk("PrtSc", KeyEvent.KEYCODE_SYSRQ),
            hk("Tab", KeyEvent.KEYCODE_TAB),
        ) + row("0.=+", false) + hk("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, 1.3f, repeat = true) +
            hk("↑", KeyEvent.KEYCODE_DPAD_UP, 1.3f, repeat = true),
        listOf(hk("Esc", KeyEvent.KEYCODE_ESCAPE, 1.3f), sp("Fn", Codes.FN, 1.2f), sp("Ctrl", Codes.CTRL, 1.2f), sp("Alt", Codes.ALT, 1.2f),
            Key(" ", Codes.SPACE, null, 3.8f, emptyList())) + arrows(),
    )
}
