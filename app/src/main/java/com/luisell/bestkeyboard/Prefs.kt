package com.luisell.bestkeyboard

import android.content.Context
import android.content.SharedPreferences

/** Ajustes guardados del teclado. */
object Prefs {
    const val THEME = "theme"            // auto / light / dark / midnight / ocean / neon / forest / sunset / rose / hacker / aurora
    const val ACCENT = "accent_color"    // "" = el del tema, o un color como "FF4FD1FF"
    const val HACKER_COLOR = "hacker_color" // color del tema Hacker: "" (verde), un color, o "rainbow"
    const val KEY_COLOR = "key_color"    // color de las teclas en cualquier tema: "" (del tema), un color, o "rainbow"
    const val TEXT_COLOR = "text_color"  // color de las letras: "" (del tema) o un color
    const val KEY_SHAPE = "key_shape"    // square / normal / round / pill
    const val LABEL_SIZE = "label_size"  // small / normal / big
    const val KEY_ANIM = "key_anim"      // none / pop / ripple / glow
    const val VIB_STYLE = "vib_style"    // system / modern / classic
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
    /** Mostrar sugerencias aunque la app pida que no (Instagram y otras). */
    const val SUGGEST_ALL_APPS = "suggest_all_apps"
    /** Qué pidió el último cuadro de texto (para revisar problemas). */
    const val LAST_FIELD = "last_field_info"
    const val CODE_LANG = "code_lang"
    const val MORE_TOOLS2 = "more_tools_all"
    /** Tus frases rápidas (lista JSON). */
    const val PHRASES = "phrases"
    /** Mostrar las frases de ejemplo del trabajo. */
    const val PHRASES_EXAMPLES = "phrases_examples"
    /** Modo una mano: "off", "left" o "right". */
    const val ONE_HAND = "one_hand"
    /** Llave gratis de GIPHY para buscar GIFs y stickers. */
    const val GIPHY_KEY = "giphy_key"
    /** Sugerir emojis según lo que escribes (casa → 🏠, jaja → 😂). */
    const val EMOJI_SUGGEST = "emoji_suggest"
    /** Sugerir palabras más formales mientras escribes (arreglé → reparé). */
    const val FORMAL_SUGGEST = "formal_suggest"
    /** Teclado flotante y dónde lo dejaste. */
    const val FLOAT = "float_keyboard"
    const val FLOAT_X = "float_x"
    const val FLOAT_Y = "float_y"

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
    /** Efecto especial del tema ("matrix" = lluvia de código del tema Hacker). */
    val effect: String = "",
    /** Letras tipo código (monoespaciadas). */
    val mono: Boolean = false,
    /** Teclas de colores del arcoíris (cada columna un color). */
    val rainbowKeys: Boolean = false,
) {
    private fun copy(key: Int = this.key, keyPressed: Int = this.keyPressed, special: Int = this.special,
                     text: Int = this.text, hint: Int = this.hint, rainbowKeys: Boolean = this.rainbowKeys) =
        Palette(isDark, bg, key, keyPressed, special, accent, accentPressed, onAccent, text, hint, popup, shadow, bg2, effect, mono, rainbowKeys)

    /** El mismo tema con teclas de otro color (las especiales un poco más oscuras). */
    fun withKeys(c: Int): Palette {
        val k = c or 0xFF000000.toInt()
        val lum = luma(k)
        val dark = lum < 140
        val text2 = if (dark) 0xFFF5F6F8.toInt() else 0xFF15161A.toInt()
        return copy(key = k, keyPressed = mixC(k, if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), 0.22f),
            special = mixC(k, 0xFF000000.toInt(), if (dark) 0.25f else 0.14f), text = text2,
            hint = mixC(text2, k, 0.45f))
    }

    /** Teclas arcoíris: cada columna de un color. */
    fun withRainbowKeys() = copy(rainbowKeys = true, text = 0xFFFFFFFF.toInt(), hint = 0xDDFFFFFF.toInt())

    /** El mismo tema con letras de otro color. */
    fun withText(c: Int): Palette {
        val t = c or 0xFF000000.toInt()
        return copy(text = t, hint = mixC(t, key, 0.4f))
    }

    private fun luma(c: Int) = 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
    private fun mixC(c: Int, t: Int, f: Float): Int {
        fun ch(sh: Int) = (((c shr sh) and 0xFF) + ((((t shr sh) and 0xFF) - ((c shr sh) and 0xFF)) * f)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

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
            text, hint, popup, shadow, bg2, effect, mono, rainbowKeys)
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

        private fun light(bg: Long, key: Long, pressed: Long, special: Long, accent: Long, accentP: Long,
                          text: Long, hint: Long, bg2: Long = bg) =
            Palette(false, bg.toInt(), key.toInt(), pressed.toInt(), special.toInt(), accent.toInt(), accentP.toInt(),
                0xFFFFFFFF.toInt(), text.toInt(), hint.toInt(), key.toInt(), 0x33000000, bg2.toInt())

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
            // Hacker: teclas de vidrio verde sobre una lluvia de código que cae (como en Matrix)
            Triple("hacker", "Hacker" to "Hacker", Palette(true, 0xFF000000.toInt(), 0x5500150A, 0xAA00FF66.toInt(),
                0x66002610, 0xFF00FF66.toInt(), 0xFF7CFFB2.toInt(), 0xFF000000.toInt(), 0xFF3DFF8A.toInt(),
                0xFF1E9E52.toInt(), 0xEE001A0C.toInt(), 0x00000000, 0xFF000000.toInt(), effect = "matrix", mono = true)),
            Triple("aurora", "Aurora" to "Aurora",
                dark(0xFF0B1026, 0xFF1C2140, 0xFF2D3463, 0xFF242A52, 0xFF64FFDA, 0xFFA7FFEB, 0xFF002E26, 0xFFF0F4FF, 0xFF9AA3C7, 0xFF2A1240)),
            Triple("graphite", "Grafito" to "Graphite",
                dark(0xFF1E1F22, 0xFF35373C, 0xFF4E5157, 0xFF2A2C30, 0xFFE0E0E0, 0xFFFFFFFF, 0xFF111111, 0xFFEDEDED, 0xFF9E9E9E)),
            Triple("coffee", "Café" to "Coffee",
                dark(0xFF1E1410, 0xFF33241D, 0xFF4A352B, 0xFF2B1E18, 0xFFD7A86E, 0xFFE9C496, 0xFF2A1A0C, 0xFFF5E9DF, 0xFFB09684)),
            Triple("fire", "Fuego" to "Fire",
                dark(0xFF1A0505, 0xFF330D0D, 0xFF551616, 0xFF2A0A0A, 0xFFFF5722, 0xFFFF8A65, 0xFF2A0800, 0xFFFFECE6, 0xFFC08A80, 0xFF2E1300)),
            Triple("gold", "Oro" to "Gold",
                dark(0xFF0D0D0D, 0xFF1C1A14, 0xFF2E2A1F, 0xFF16140F, 0xFFFFC107, 0xFFFFD54F, 0xFF241A00, 0xFFFFF3D0, 0xFFB8A577)),
            Triple("cyberpunk", "Cyberpunk" to "Cyberpunk",
                dark(0xFF1A0526, 0xFF2D0B40, 0xFF4A1466, 0xFF240833, 0xFFFFEA00, 0xFFFFF176, 0xFF1A0526, 0xFFFF80D0, 0xFFB46FA0, 0xFF06133A)),
            Triple("sky", "Cielo" to "Sky",
                light(0xFFE3F2FD, 0xFFFFFFFF, 0xFFBBDEFB, 0xFFC5E1F8, 0xFF1E88E5, 0xFF1565C0, 0xFF0D2A45, 0xFF5B7A96, 0xFFD0E8FF)),
            Triple("mint", "Menta" to "Mint",
                light(0xFFE0F7EF, 0xFFFFFFFF, 0xFFB9F0DB, 0xFFC4EEDD, 0xFF00A86B, 0xFF00875A, 0xFF0F3A2A, 0xFF5E8A79)),
            Triple("lavender", "Lavanda" to "Lavender",
                light(0xFFEDE7F6, 0xFFFFFFFF, 0xFFD1C4E9, 0xFFDCD2EE, 0xFF7E57C2, 0xFF5E35B1, 0xFF2A1F3D, 0xFF7A6D90, 0xFFF3E5F5)),
            Triple("peach", "Durazno" to "Peach",
                light(0xFFFFF0E6, 0xFFFFFFFF, 0xFFFFD6BF, 0xFFFFDCCB, 0xFFFF7043, 0xFFF4511E, 0xFF4A2A1C, 0xFF9A7765)),
        )

        /** Aplica los colores que elegiste (acento, teclas, letras) encima del tema. */
        fun custom(p: Palette, prefs: android.content.SharedPreferences): Palette {
            var pal = p
            prefs.getString(Prefs.ACCENT, "")?.takeIf { it.isNotEmpty() }?.toLongOrNull(16)?.let { pal = pal.withAccent(it.toInt()) }
            when (val k = prefs.getString(Prefs.KEY_COLOR, "") ?: "") {
                "" -> Unit
                "rainbow" -> pal = pal.withRainbowKeys()
                else -> k.toLongOrNull(16)?.let { pal = pal.withKeys(it.toInt()) }
            }
            prefs.getString(Prefs.TEXT_COLOR, "")?.takeIf { it.isNotEmpty() }?.toLongOrNull(16)?.let { pal = pal.withText(it.toInt()) }
            return pal
        }

        /** Los colores del tema elegido ("auto" sigue al teléfono). [hackerColor] = color del tema Hacker. */
        fun forTheme(id: String?, systemDark: Boolean, hackerColor: String? = null): Palette {
            if (id == "hacker" && !hackerColor.isNullOrEmpty()) {
                if (hackerColor == "rainbow") return hacker(0xFF00E5FF.toInt(), rainbow = true)
                hackerColor.toLongOrNull(16)?.let { return hacker(it.toInt()) }
            }
            return THEMES.firstOrNull { it.first == id }?.third ?: if (systemDark) DARK else LIGHT
        }

        private fun mix(c: Int, t: Int, f: Float): Int {
            fun ch(sh: Int) = (((c shr sh) and 0xFF) + ((((t shr sh) and 0xFF) - ((c shr sh) and 0xFF)) * f)).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
        private fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

        /** El tema Hacker en cualquier color: teclas de vidrio y lluvia de código de ese color. */
        fun hacker(c: Int, rainbow: Boolean = false) = Palette(
            true, 0xFF000000.toInt(), alpha(mix(c, 0xFF000000.toInt(), 0.88f), 0x66), alpha(c, 0xAA),
            alpha(mix(c, 0xFF000000.toInt(), 0.82f), 0x88), c or 0xFF000000.toInt(), mix(c, 0xFFFFFFFF.toInt(), 0.45f),
            0xFF000000.toInt(), mix(c, 0xFFFFFFFF.toInt(), 0.25f), mix(c, 0xFF000000.toInt(), 0.35f),
            alpha(mix(c, 0xFF000000.toInt(), 0.9f), 0xEE), 0x00000000, 0xFF000000.toInt(),
            effect = if (rainbow) "matrix-rainbow" else "matrix", mono = true,
        )

        /** Colores para las teclas y las letras (en cualquier tema). */
        val KEY_COLORS = listOf(
            "FF000000", "FF1C1C1E", "FF2F3139", "FF5F6368", "FFFFFFFF", "FFF1F3F4", "FF0D47A1", "FF1E88E5",
            "FF00ACC1", "FF00897B", "FF43A047", "FF7CB342", "FFFDD835", "FFFB8C00", "FFE53935", "FFD81B60",
            "FF8E24AA", "FF5E35B1", "FF6D4C41", "FFFFCDD2", "FFC8E6C9", "FFBBDEFB", "FFE1BEE7", "rainbow",
        )
        val TEXT_COLORS = listOf(
            "FFFFFFFF", "FF000000", "FF9AA0A6", "FF4FD1FF", "FF2979FF", "FF7C4DFF", "FFE040FB", "FFFF4081",
            "FFFF1744", "FFFF6D00", "FFFFD54F", "FFC6FF00", "FF00E676", "FF1DE9B6",
        )

        /** Colores para el tema Hacker (el último es arcoíris). */
        val HACKER_COLORS = listOf(
            "FF00FF66", "FF00E5FF", "FF2979FF", "FF7C4DFF", "FFE040FB", "FFFF4081", "FFFF1744", "FFFF6D00",
            "FFFFC400", "FFC6FF00", "FFFFFFFF", "rainbow",
        )

        /** Colores de acento para elegir (además del que trae el tema). */
        val ACCENTS = listOf(
            0xFF4FD1FF, 0xFF8AB4F8, 0xFF2979FF, 0xFF7C4DFF, 0xFFB388FF, 0xFFE040FB, 0xFFFF4081, 0xFFFF1744,
            0xFFFF6D00, 0xFFFF8A65, 0xFFFFD54F, 0xFFC6FF00, 0xFF69F0AE, 0xFF00E676, 0xFF1DE9B6, 0xFFFFFFFF,
        ).map { it.toInt() }
    }
}
