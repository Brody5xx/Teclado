package com.luisell.bestkeyboard

import android.content.Context
import android.content.SharedPreferences

/** Ajustes guardados del teclado. */
object Prefs {
    const val THEME = "theme"            // auto / light / dark
    const val HEIGHT = "key_height"      // low / normal / high
    const val NUMBER_ROW = "number_row"
    const val PREVIEW = "key_preview"
    const val VIBRATE = "vibrate"
    const val VIB_STRENGTH = "vib_strength" // soft / medium / strong
    const val SOUND = "sound"
    const val AUTOCAP = "autocap"
    const val DOUBLE_SPACE = "double_space"
    const val LANG = "lang"              // es / en
    const val SUGGEST = "suggest"
    const val AUTOCORRECT = "autocorrect"
    const val LEARN = "learn"
    const val CLIP_HISTORY = "clip_history"
    const val CLIP_LAST_SIG = "clip_last_sig"
    const val TR_SRC = "translate_from"
    const val SWIPE = "swipe"
    const val SPELL = "spell_check"

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
}

/** Colores del teclado. */
class Palette(
    val isDark: Boolean,
    val bg: Int,
    val key: Int,
    val keyPressed: Int,
    val special: Int,
    val accent: Int,
    val accentPressed: Int,
    val onAccent: Int,
    val text: Int,
    val hint: Int,
    val popup: Int,
    val shadow: Int,
) {
    companion object {
        val DARK = Palette(
            isDark = true,
            bg = 0xFF17181C.toInt(),
            key = 0xFF2F3139.toInt(),
            keyPressed = 0xFF4A4D59.toInt(),
            special = 0xFF42454F.toInt(),
            accent = 0xFF8AB4F8.toInt(),
            accentPressed = 0xFFAECBFA.toInt(),
            onAccent = 0xFF0B1A33.toInt(),
            text = 0xFFF1F3F4.toInt(),
            hint = 0xFF9AA0A6.toInt(),
            popup = 0xFF3C4049.toInt(),
            shadow = 0x66000000,
        )
        val LIGHT = Palette(
            isDark = false,
            bg = 0xFFE8EAED.toInt(),
            key = 0xFFFFFFFF.toInt(),
            keyPressed = 0xFFD2D5DA.toInt(),
            special = 0xFFC8CCD3.toInt(),
            accent = 0xFF1A73E8.toInt(),
            accentPressed = 0xFF1557B0.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            text = 0xFF202124.toInt(),
            hint = 0xFF5F6368.toInt(),
            popup = 0xFFFFFFFF.toInt(),
            shadow = 0x33000000,
        )
    }
}
