package com.luisell.bestkeyboard

import android.content.Context
import android.content.SharedPreferences

/** Ajustes guardados del teclado. */
object Prefs {
    const val THEME = "theme"            // auto / light / dark / midnight / ocean / neon / forest / sunset / rose / hacker / aurora
    const val ACCENT = "accent_color"    // "" = el del tema, o un color como "FF4FD1FF"
    const val KEY_SHAPE = "key_shape"    // square / normal / round / pill
    const val LABEL_SIZE = "label_size"  // small / normal / big
    const val KEY_ANIM = "key_anim"      // none / pop / ripple / glow
    const val VIB_STYLE = "vib_style"    // modern / classic
    const val SOUND_STYLE = "sound_style" // modern / classic
    const val SOUND_VOL = "sound_volume" // low / medium / high
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
    const val SHOTS = "suggest_screenshots"
    const val SHOT_USED = "screenshot_used"
    const val SHOT_ASKS = "screenshot_asks"
    /** Deslizar la barra espaciadora cambia el idioma (y se quita el botón 🌐). */
    const val SPACE_LANG = "space_switches_lang"
    /** Tamaño del teclado elegido con "Cambiar tamaño" (1 = normal). */
    const val SIZE = "keyboard_size"
    /** Herramientas de la barra de arriba y de "Más opciones" (números separados por comas). */
    const val BAR_TOOLS = "bar_tools"
    const val MORE_TOOLS = "more_tools"
    /** Versión nueva: TODOS los botones (también micrófono, esconder y ⋯) se pueden mover. */
    const val BAR_TOOLS2 = "bar_tools_all"
    /** Teclado de computadora ("modo hacker") encendido. */
    const val HACKER = "hacker_keyboard"
    /** Con texto seleccionado, ⇧ cambia lo seleccionado a MAYÚSCULAS / minúsculas / Tipo Título. */
    const val SEL_CAPS = "selection_caps"
    /** Clave gratis de Google Gemini (para "Más formal" si el teléfono no trae la IA). */
    const val GEMINI_KEY = "gemini_key"
    /** Idioma de la pantalla de ajustes: auto / es / en. */
    const val APP_LANG = "app_lang"
    /** Sugerir arriba lo que acabas de copiar. */
    const val CLIP_SUGGEST = "suggest_copied"
    /** Sugerir el código de verificación de los SMS. */
    const val OTP_SUGGEST = "suggest_codes"
    /** Lo último que ya usaste o descartaste (para no volver a ofrecerlo). */
    const val QUICK_DONE = "quick_done"
    /** Sugerir mis correos, atajos y contraseñas guardados. */
    const val VAULT_SUGGEST = "vault_suggest"
    /** Guardar solo los correos que escribo en campos de correo. */
    const val VAULT_LEARN = "vault_learn_emails"
    /** Modo código encendido y el lenguaje elegido (Python, JavaScript…). */
    const val CODE_MODE = "code_mode"
    const val CODE_LANG = "code_lang"
    const val MORE_TOOLS2 = "more_tools_all"

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
}

/** Colores del teclado. [bg2] = segundo color del fondo (degradado) en algunos temas. */
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
    val bg2: Int = bg,
) {
    /** El mismo tema con otro color de acento (Enter, botones encendidos…). */
    fun withAccent(c: Int): Palette {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val light = (0.299 * r + 0.587 * g + 0.114 * b) > 150
        fun mix(t: Int, f: Float): Int {
            val rr = (r + (t - r) * f).toInt().coerceIn(0, 255)
            val gg = (g + (t - g) * f).toInt().coerceIn(0, 255)
            val bb = (b + (t - b) * f).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        return Palette(isDark, bg, key, keyPressed, special, c or 0xFF000000.toInt(),
            if (isDark) mix(255, 0.3f) else mix(0, 0.2f), if (light) 0xFF101114.toInt() else 0xFFFFFFFF.toInt(),
            text, hint, popup, shadow, bg2)
    }

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

        private fun dark(bg: Long, key: Long, pressed: Long, special: Long, accent: Long, accentP: Long, onAccent: Long,
                         text: Long, hint: Long, bg2: Long = bg) =
            Palette(true, bg.toInt(), key.toInt(), pressed.toInt(), special.toInt(), accent.toInt(), accentP.toInt(),
                onAccent.toInt(), text.toInt(), hint.toInt(), special.toInt(), 0x66000000, bg2.toInt())

        /** Temas para elegir en los ajustes: id → (nombre en español, nombre en inglés, colores). */
        val THEMES: List<Triple<String, Pair<String, String>, Palette?>> = listOf(
            Triple("auto", "Automático" to "Automatic", null),
            Triple("light", "Claro" to "Light", LIGHT),
            Triple("dark", "Oscuro" to "Dark", DARK),
            Triple("midnight", "Medianoche" to "Midnight",
                dark(0xFF000000, 0xFF1A1A1A, 0xFF333333, 0xFF262626, 0xFF4FD1FF, 0xFF8BE3FF, 0xFF001018, 0xFFFFFFFF, 0xFF8A8A8A)),
            Triple("ocean", "Océano" to "Ocean",
                dark(0xFF0B1E33, 0xFF16324F, 0xFF22476E, 0xFF1D3D5E, 0xFF4FC3F7, 0xFF81D4FA, 0xFF04263A, 0xFFE3F2FD, 0xFF90A4BE, 0xFF0E2E52)),
            Triple("neon", "Neón" to "Neon",
                dark(0xFF140A24, 0xFF261640, 0xFF3A2361, 0xFF2F1B50, 0xFFB388FF, 0xFFD1B3FF, 0xFF1A0B33, 0xFFF3E9FF, 0xFFA594C4, 0xFF22103F)),
            Triple("forest", "Bosque" to "Forest",
                dark(0xFF0F1F17, 0xFF1B3326, 0xFF2A4A38, 0xFF22402F, 0xFF69F0AE, 0xFFB9F6CA, 0xFF003320, 0xFFE8F5E9, 0xFF8FB39C)),
            Triple("sunset", "Atardecer" to "Sunset",
                dark(0xFF2A1220, 0xFF3D1D2E, 0xFF5A2B44, 0xFF4A2338, 0xFFFF8A65, 0xFFFFAB91, 0xFF3A1005, 0xFFFFF1EC, 0xFFC9A0A8, 0xFF3D1A12)),
            Triple("rose", "Rosa" to "Rose", Palette(false, 0xFFFCE4EC.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8BBD0.toInt(),
                0xFFF8C8D8.toInt(), 0xFFE91E63.toInt(), 0xFFC2185B.toInt(), 0xFFFFFFFF.toInt(), 0xFF3E2030.toInt(),
                0xFF8E6A7A.toInt(), 0xFFFFFFFF.toInt(), 0x33000000)),
            Triple("hacker", "Hacker" to "Hacker",
                dark(0xFF000000, 0xFF0A140A, 0xFF143014, 0xFF0F1F0F, 0xFF00E676, 0xFF69F0AE, 0xFF000000, 0xFF00E676, 0xFF2E7D32)),
            Triple("aurora", "Aurora" to "Aurora",
                dark(0xFF0B1026, 0xFF1C2140, 0xFF2D3463, 0xFF242A52, 0xFF64FFDA, 0xFFA7FFEB, 0xFF002E26, 0xFFF0F4FF, 0xFF9AA3C7, 0xFF2A1240)),
        )

        /** Los colores del tema elegido ("auto" sigue al teléfono). */
        fun forTheme(id: String?, systemDark: Boolean): Palette =
            THEMES.firstOrNull { it.first == id }?.third ?: if (systemDark) DARK else LIGHT

        /** Colores de acento para elegir (además del que trae el tema). */
        val ACCENTS = listOf(
            0xFF4FD1FF, 0xFF8AB4F8, 0xFFB388FF, 0xFFFF4081, 0xFFFF8A65, 0xFFFFD54F, 0xFF69F0AE, 0xFF00E676, 0xFFFFFFFF,
        ).map { it.toInt() }
    }
}
