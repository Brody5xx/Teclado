package com.luisell.bestkeyboard

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.widget.TextView

/**
 * Sin emojis en los paneles: el emoji del principio de un texto («📍 Llegué») se cambia por
 * uno de nuestros íconos pequeños (como los de la barra de arriba). Lo que escribes tú no se toca.
 */
object Emo {
    private val MAP = linkedMapOf(
        "📍" to Icons.MAP_PIN, "🧭" to Icons.MAP_PIN, "⏰" to Icons.CLOCK, "🕐" to Icons.CLOCK, "⏱" to Icons.STOPWATCH,
        "🛻" to Icons.TRUCK, "🚗" to Icons.TRUCK, "🚙" to Icons.TRUCK, "🍔" to Icons.FOOD, "↩" to Icons.UNDO,
        "🏠" to Icons.HOME, "🚪" to Icons.BACK, "🏁" to Icons.FLAG, "📲" to Icons.SEND, "📤" to Icons.SEND,
        "🔎" to Icons.SEARCH, "🔍" to Icons.SEARCH, "📏" to Icons.RULER, "🧵" to Icons.RULER, "✅" to Icons.CHECK,
        "✓" to Icons.CHECK, "✔" to Icons.CHECK, "💾" to Icons.CHECK, "🗑" to Icons.TRASH, "📋" to Icons.CLIP,
        "🌐" to Icons.GLOBE, "⌨" to Icons.KEYBOARD, "🎤" to Icons.MIC, "💵" to Icons.MONEY, "💰" to Icons.MONEY,
        "📄" to Icons.REPORT, "📅" to Icons.CALENDAR, "✏" to Icons.PENCIL, "✍" to Icons.PENCIL, "📝" to Icons.PENCIL,
        "🧰" to Icons.TOOLBOX, "📒" to Icons.NOTEBOOK, "🗂" to Icons.TICKETS, "🎨" to Icons.BRUSH, "💬" to Icons.BUBBLE,
        "📸" to Icons.IMAGE, "🖼" to Icons.IMAGE, "🎬" to Icons.IMAGE, "▶" to Icons.IMAGE, "⚠" to Icons.WARN,
        "❌" to Icons.CLOSE, "✕" to Icons.CLOSE, "💼" to Icons.BRIEFCASE, "⛑" to Icons.HARDHAT, "👷" to Icons.HARDHAT,
        "🏢" to Icons.BUILDING, "📦" to Icons.BOX, "⭐" to Icons.STAR, "✨" to Icons.STAR, "🔢" to Icons.NETWORK,
        "🏷" to Icons.TAG, "🇺🇸" to Icons.GLOBE, "🔊" to Icons.SPEAKER, "🎯" to Icons.STAR, "🤖" to Icons.STAR,
        "🌙" to Icons.CLOCK, "📒" to Icons.NOTEBOOK, "📚" to Icons.BOOK, "💡" to Icons.IDEA, "🔧" to Icons.EDIT,
        "👍" to Icons.CHECK, "💪" to Icons.STAR, "📊" to Icons.WEEK, "🗓" to Icons.CALENDAR, "🎟" to Icons.TICKET, "⌫" to Icons.DELETE, "📶" to Icons.NETWORK, "🔒" to Icons.LOCK,
    )

    private fun isEmojiCp(cp: Int): Boolean =
        cp >= 0x1F000 || cp in 0x2190..0x21FF || cp in 0x2300..0x23FF || cp in 0x25A0..0x27BF ||
            cp in 0x2B00..0x2BFF || cp == 0xFE0F || cp == 0x200D || cp in 0x1F1E6..0x1F1FF || cp == 0x20E3

    /** (ícono o -1, el texto sin el emoji del principio). */
    fun split(text: String): Pair<Int, String> {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (!isEmojiCp(cp)) break
            i += Character.charCount(cp)
        }
        if (i == 0) return -1 to text
        val em = text.substring(0, i)
        val kind = MAP.entries.firstOrNull { em.startsWith(it.key) }?.value ?: -1
        return kind to text.substring(i).trimStart()
    }

    /** Quita todos los emojis de un texto (para avisos y títulos). Deja letras, números y signos normales. */
    /** Solo los emojis de verdad (deja → ✓ ✕ ⌫ ⇧ ✎ y los signos normales). */
    private fun isRealEmoji(cp: Int): Boolean =
        cp >= 0x1F000 || cp in 0x2600..0x26FF || (cp in 0x2700..0x27BF && cp != 0x2713 && cp != 0x2715 && cp != 0x2716 && cp != 0x270E) ||
            cp in 0x23E9..0x23FA || cp == 0x231A || cp == 0x231B || cp == 0x2B50 || cp == 0x2B55 || cp == 0xFE0F || cp == 0x200D || cp == 0x20E3 ||
            cp == 0x21A9 || cp == 0x21AA || cp == 0x2934 || cp == 0x2935

    fun hasRealEmoji(text: CharSequence): Boolean {
        var i = 0
        val t = text.toString()
        while (i < t.length) { val cp = t.codePointAt(i); if (isRealEmoji(cp)) return true; i += Character.charCount(cp) }
        return false
    }

    /** 🚫 Para las pantallas de la app: quita los emojis (se ven anticuados) y arregla los espacios que quedan. */
    fun stripUi(text: CharSequence): String {
        val sb = StringBuilder()
        var i = 0
        val t = text.toString()
        while (i < t.length) {
            val cp = t.codePointAt(i)
            if (!isRealEmoji(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex(" +([,.;:!?»)])"), "$1")
            .replace(Regex("([«(]) +"), "$1")
            .replace(Regex("(?m)^ +"), "")
            .replace(Regex("(?m)^([•·]) *$"), "")
            .replace(Regex(" · ·"), " ·")
            .trim()
    }

    fun strip(text: CharSequence): String {
        val sb = StringBuilder()
        var i = 0
        val t = text.toString()
        while (i < t.length) {
            val cp = t.codePointAt(i)
            if (!isEmojiCp(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString().replace(Regex("[ \\t]{2,}"), " ").replace(Regex("(?m)^ +"), "").trim()
    }

    /** Pone [text] en [tv]: el emoji del principio se vuelve ícono a la izquierda. */
    fun apply(tv: TextView, text: String, color: Int, iconDp: Float = 16f) {
        val (k, rest) = split(text)
        tv.text = if (k >= 0 || rest != text) rest else text
        if (k >= 0) {
            val px = (iconDp * tv.resources.displayMetrics.density).toInt()
            val dr = IconDrawable(k, color, tv.resources.displayMetrics.density)
            dr.setBounds(0, 0, px, px)
            tv.setCompoundDrawablesRelative(dr, null, null, null)
            tv.compoundDrawablePadding = (6 * tv.resources.displayMetrics.density).toInt()
        } else tv.setCompoundDrawablesRelative(null, null, null, null)
    }
}

/** Como [Emo.apply] pero con el ícono arriba del texto (para los botones grandes). */
fun Emo.applyTop(tv: TextView, text: String, color: Int, iconDp: Float = 28f) {
    val (k, rest) = split(text)
    tv.text = rest
    if (k >= 0) {
        val dd = tv.resources.displayMetrics.density
        val px = (iconDp * dd).toInt()
        val dr = IconDrawable(k, color, dd)
        dr.setBounds(0, 0, px, px)
        tv.setCompoundDrawablesRelative(null, dr, null, null)
        tv.compoundDrawablePadding = (4 * dd).toInt()
    } else tv.setCompoundDrawablesRelative(null, null, null, null)
}

/**
 * Centra el ícono junto con el texto (Android pone el ícono pegado a la orilla del botón).
 * [top] = ícono arriba del texto: se centra todo hacia arriba y abajo; si no, ícono a la izquierda: se centra de lado a lado.
 */
fun Emo.center(tv: TextView, top: Boolean) {
    if (tv.getTag(0x7f0b0001) == true) { tv.requestLayout(); return }
    tv.setTag(0x7f0b0001, true)
    tv.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
        val t2 = v as TextView
        val w = r - l; val h = b - t
        if (w <= 0 || h <= 0) return@addOnLayoutChangeListener
        val ds = t2.compoundDrawablesRelative
        if (top) {
            val dr = ds[1]
            val ih = dr?.bounds?.height() ?: 0
            t2.includeFontPadding = false
            val fm = t2.paint.fontMetricsInt
            val lines = maxOf(1, t2.lineCount)
            val th = (fm.descent - fm.ascent) * lines
            val content = ih + (if (dr != null) t2.compoundDrawablePadding else 0) + th
            // Un poquito más arriba del centro exacto: así el texto no toca el borde de abajo
            val lift = (6 * t2.resources.displayMetrics.density).toInt()
            val pad = maxOf(0, (h - content) / 2 - lift)
            if (t2.paddingTop != pad) t2.post { t2.setPadding(t2.paddingLeft, pad, t2.paddingRight, 0); t2.gravity = android.view.Gravity.CENTER_HORIZONTAL or android.view.Gravity.TOP }
        } else {
            val dr = ds[0]
            val iw = dr?.bounds?.width() ?: 0
            val tw = t2.paint.measureText(t2.text.toString()).toInt()
            val content = iw + (if (dr != null) t2.compoundDrawablePadding else 0) + tw
            val pad = maxOf(0, (w - content) / 2)
            if (t2.paddingLeft != pad) t2.post { t2.setPadding(pad, t2.paddingTop, 0, t2.paddingBottom); t2.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL }
        }
    }
    tv.requestLayout()
}

/** Uno de nuestros íconos como Drawable (para ponerlo al lado de un texto). */
class IconDrawable(private val kind: Int, var color: Int, private val d: Float) : Drawable() {
    override fun draw(c: Canvas) {
        val b = bounds
        Icons.draw(c, kind, b.exactCenterX(), b.exactCenterY(), b.width() * 0.36f, color, 0, d)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
