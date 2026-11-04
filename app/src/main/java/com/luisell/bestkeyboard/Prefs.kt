package com.luisell.bestkeyboard

import android.content.Context
import android.content.SharedPreferences

/** Ajustes guardados del teclado. */
object Prefs {
    const val THEME = "theme"            // auto / light / dark / midnight / ocean / neon / forest / sunset / rose / hacker / aurora
    const val ACCENT = "accent_color"    // "" = el del tema, o un color como "FF4FD1FF"
    const val HACKER_COLOR = "hacker_color" // color del tema Hacker: "" (verde), un color, o "rainbow"
    const val KEY_COLOR = "key_color"    // color de las teclas en cualquier tema: "" (del tema), un color, o "rainbow"
    // ✨ Paneles y botones bonitos: "" = como el tema
    const val PANEL_BORDER = "panel_border"   // color del borde y la luz
    const val PANEL_FILL = "panel_fill"       // color de los botones y tarjetas
    const val PANEL_ICON = "panel_icon"       // color de los íconos
    const val PANEL_GLASS = "panel_glass"     // con fondo propio: "clear", "normal", "solid"
    const val PANEL_LINE = "panel_line"       // grosor del borde: "none", "thin", "thick"
    const val TEXT_COLOR = "text_color"  // color de las letras: "" (del tema) o un color
    const val KEY_SHAPE = "key_shape"    // square / normal / round / pill
    const val LABEL_SIZE = "label_size"  // small / normal / big
    const val STRIP_ARC = "strip_arc"      // none / soft / normal / strong (curva de la barra de arriba)
    const val STRIP_NAMES = "strip_names"  // nombre del botón del medio al deslizar
    const val STRIP_LIGHT = "strip_light"  // off / soft / normal / bright (línea de luz)
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
    const val AUTO_LANG = "auto_lang"   // 🌐 cambiar español/inglés solo, según lo que escribo
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
    /** Libreta del día y mis tickets (listas JSON). */
    const val NOTEBOOK = "notebook"
    const val TICKETS = "tickets"
    /** El número que llevará el próximo ticket (nunca se reinicia). */
    const val TICKET_NEXT = "ticket_next"
    /** Tickets con el formato del trabajo (Date, Location, Arrival/Departure, Job Description, Equipment List). */
    const val TICKET_FORMAT = "ticket_format_work"
    const val TICKET_FORMAT_OLD = "ticket_format_dvss"  // nombre viejo (se pasa al nuevo una sola vez)
    /** Aviso del ticket del día: prendido y a qué hora (0-23). */
    const val REMINDER = "ticket_reminder"
    const val REMINDER_HOUR = "ticket_reminder_hour"
    /** Palabra de la semana: prendida y la última semana que se mostró. */
    const val WORD_WEEK = "word_week"
    const val WORD_SHOWN = "word_week_shown"
    /** Mi jornada (comencé, llegué, salí, casa). */
    const val WORKDAY = "workday"
    /** Ya viste la guía rápida. */
    const val GUIDE_SEEN = "guide_seen"
    /** Mis sitios (lista JSON). */
    const val SITES = "sites"
    const val PAY = "pay"                      // 💵 tarifas y descuentos (solo en el teléfono)
    const val NIGHT_REMIND = "night_remind"    // 🌙 aviso si la jornada sigue abierta
    const val HOME_PLACE = "home_place"        // 🏠 dónde está tu casa (solo coordenadas, solo en el teléfono)
    const val NIGHT_TIME = "night_time"        // a qué hora (minutos desde las 0:00; 1260 = 9:00 PM)
    const val TODOS = "todos"                  // ✅ pendientes
    const val CHECKLIST = "checklist"          // 🧰 cosas para revisar antes de salir
    const val CHECK_DONE = "check_done"        // lo que marcaste hoy
    const val CHECK_ON = "check_on"            // enseñar la checklist al tocar «Comencé»
    const val GLOVES = "gloves"                // 🧤 modo guantes (teclas grandes)
    const val BG_KIND = "bg_kind"              // 🖼️ fondo propio: "" / "image" / "video"
    const val BG_DIM = "bg_dim"                // qué tanto se oscurece ("0.25" / "0.45" / "0.65")
    const val BG_SOUND = "bg_sound"            // 🔊 el video suena
    const val BG_STAMP = "bg_stamp"            // cambia cada vez que eliges otro fondo (para recargarlo)
    const val APP_ICON = "app_icon"            // ícono de la app: "light" (Línea de luz) o "diamond" (Diamante)
    const val SHORTCUTS = "shortcuts"          // ⚡ atajos de texto (/ok → frase)
    const val AUTO_SITES = "auto_sites"        // llegada y salida automáticas (geocercas)
    const val HOME_PENDING = "home_pending"            // llegaste a casa: se espera un rato antes de cerrar el día (por si solo paraste)
    const val HOME_WAIT = "home_wait"                  // cuántos minutos se espera en casa ("10" / "20" / "30")
    const val PARKING_DAY = "parking_day"              // el día que empezó en el parqueo de la camioneta (ese día termina allá)
    const val AUTO_EXIT_PENDING = "auto_exit_pending"  // saliste del sitio: se espera unos minutos antes de anotarlo
    const val AUTO_EXIT_WAIT = "auto_exit_wait"        // cuántos minutos se espera ("5" / "10" / "15")
    const val AUTO_LEAVE = "auto_leave"        // la última salida que se anotó sola (por si el GPS brincó)
    const val AUTO_ENTER = "auto_enter"        // a qué hora entraste a cada sitio (para la hora de llegada)
    const val OVERTIME_DAY = "overtime_day"    // horas normales por día ("0" = sin aviso)
    const val OVERTIME_SITE_SENT = "overtime_site_sent"  // día en que ya se avisó de las 8 horas EN EL SITIO
    const val OVERTIME_SENT = "overtime_sent"  // día en que ya se avisó de horas extra
    const val SITE_NEW = "site_new"            // lugar nuevo (no guardado) donde tocaste «Llegué»: para preguntar si lo guardas
    /** Lista de materiales (los tuyos) y lo que usaste hoy. */
    const val MATERIALS = "materials"
    const val MATERIALS_TODAY = "materials_today"
    /** Mostrar "Mi jornada" en la pantalla de bloqueo (notificación con botones). */
    const val WORKDAY_NOTIF = "workday_notif"
    /** La notificación de Mi jornada se queda siempre (con el botón 🛻 Comencé antes de empezar). */
    const val WORKDAY_ALWAYS = "workday_always"
    /** Botones de Mi jornada: cuáles se ven y sus nombres (JSON). */
    const val WORKDAY_BTNS = "workday_buttons"
    /** El ícono de Mi jornada: truck, clock, stopwatch, briefcase, calendar, hardhat o building. */
    const val WORKDAY_ICON = "workday_icon"
    const val LUNCH_PAID = "lunch_paid"        // true = el almuerzo es pagado (solo se anota, no se resta)
    const val LUNCH_RULE = "lunch_rule"        // "always" = siempre descuenta el tiempo fijo; "real" = lo que marques
    const val LUNCH_AUTO = "lunch_auto"        // "0" / "30" / "45" / "60": si no lo marcas, cuenta este tiempo
    /** Si hoy vas manejando: "yyyy-MM-dd:yes" o ":no" (se pregunta una vez al día). */
    const val DRIVING_DAY = "driving_day"
    /** Respaldo automático: carpeta elegida, contraseña (cifrada) y la última vez. */
    const val AUTO_BACKUP_DIR = "auto_backup_dir"
    const val AUTO_BACKUP_PW = "auto_backup_pw"
    const val AUTO_BACKUP_LAST = "auto_backup_last"

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    // ---- 🌗 Tema de día y de noche
    const val THEME_TIMED = "theme_timed"
    const val THEME_DAY = "theme_day"
    const val THEME_NIGHT = "theme_night"
    const val DAY_FROM = "day_from"       // hora en que empieza el día ("7")
    const val NIGHT_FROM = "night_from"   // hora en que empieza la noche ("19")

    /** ¿Ahora es de día? (según las horas que elegiste) */
    fun isDayNow(p: SharedPreferences): Boolean {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val d = (p.getString(DAY_FROM, "7") ?: "7").toIntOrNull() ?: 7
        val n = (p.getString(NIGHT_FROM, "19") ?: "19").toIntOrNull() ?: 19
        return h in d until n
    }

    /** El tema que toca ahora (el de siempre, o el de día / de noche). */
    fun theme(p: SharedPreferences): String =
        if (p.getBoolean(THEME_TIMED, false)) {
            if (isDayNow(p)) p.getString(THEME_DAY, "light") ?: "light" else p.getString(THEME_NIGHT, "dark") ?: "dark"
        } else p.getString(THEME, "auto") ?: "auto"
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
                         text: Long, hint: Long, bg2: Long = bg, effect: String = "", mono: Boolean = false) =
            Palette(true, bg.toInt(), key.toInt(), pressed.toInt(), special.toInt(), accent.toInt(), accentP.toInt(),
                onAccent.toInt(), text.toInt(), hint.toInt(), special.toInt(), 0x66000000, bg2.toInt(), effect, mono)

        private fun light(bg: Long, key: Long, pressed: Long, special: Long, accent: Long, accentP: Long,
                          text: Long, hint: Long, bg2: Long = bg, effect: String = "") =
            Palette(false, bg.toInt(), key.toInt(), pressed.toInt(), special.toInt(), accent.toInt(), accentP.toInt(),
                0xFFFFFFFF.toInt(), text.toInt(), hint.toInt(), key.toInt(), 0x33000000, bg2.toInt(), effect)

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
            // ---- Temas con animación de fondo ✨
            Triple("galaxy", "✨ Galaxia" to "✨ Galaxy",
                dark(0xFF070A1F, 0x55262C5C, 0xAA4A4F9A, 0x66161A40, 0xFFB388FF, 0xFFD1B3FF, 0xFF120A2A, 0xFFF1EEFF, 0xFFA7A2D8, 0xFF1B0A33, effect = "stars")),
            Triple("winter", "✨ Invierno" to "✨ Winter",
                dark(0xFF0D1B2A, 0x661B3550, 0xAA3A5F86, 0x66132840, 0xFF90CAF9, 0xFFBBDEFB, 0xFF0D1B2A, 0xFFEAF4FF, 0xFF9DB4CC, 0xFF1B2F48, effect = "snow")),
            Triple("deepsea", "✨ Mar profundo" to "✨ Deep sea",
                dark(0xFF00121C, 0x5503334A, 0xAA0B5A7A, 0x66022A3D, 0xFF4DD0E1, 0xFF80DEEA, 0xFF002A33, 0xFFE0F7FA, 0xFF7FB4C2, 0xFF003344, effect = "bubbles")),
            Triple("fireflies", "✨ Luciérnagas" to "✨ Fireflies",
                dark(0xFF07140C, 0x55143322, 0xAA2A5A3E, 0x660E2A1A, 0xFFE6EE9C, 0xFFF0F4C3, 0xFF1A2200, 0xFFF1F8E9, 0xFF97B39E, 0xFF0F2416, effect = "fireflies")),
            Triple("storm", "✨ Lluvia" to "✨ Rain",
                dark(0xFF111820, 0x66222E3B, 0xAA3E5064, 0x661A2430, 0xFF81D4FA, 0xFFB3E5FC, 0xFF0A1A26, 0xFFE8EFF5, 0xFF8FA0B0, 0xFF1C2733, effect = "rain")),
            Triple("embers", "✨ Brasas" to "✨ Embers",
                dark(0xFF140604, 0x5533120A, 0xAA5A2414, 0x66280D07, 0xFFFF9100, 0xFFFFB74D, 0xFF2A1000, 0xFFFFF0E0, 0xFFC79A84, 0xFF2A0A03, effect = "embers")),
            Triple("northern", "✨ Aurora boreal" to "✨ Northern lights",
                dark(0xFF050B18, 0x55142035, 0xAA2A3D5E, 0x660C1628, 0xFF69F0AE, 0xFFB9F6CA, 0xFF002618, 0xFFEFFFF7, 0xFF95A9C2, 0xFF0A1630, effect = "aurora")),
            Triple("sakura", "✨ Sakura" to "✨ Sakura",
                light(0xFFFFEEF3, 0xFFFFFFFF, 0xFFFFCCDA, 0xFFFFD9E4, 0xFFEC407A, 0xFFD81B60, 0xFF4A2433, 0xFF9C6B7D, 0xFFFFF7FA, effect = "petals")),
            Triple("love", "✨ Amor" to "✨ Love",
                dark(0xFF1F0712, 0x553A1024, 0xAA6A2042, 0x662C0B1B, 0xFFFF4081, 0xFFFF80AB, 0xFF33000F, 0xFFFFE8F0, 0xFFC490A6, 0xFF2E0A1E, effect = "hearts")),
            Triple("autumn", "✨ Otoño" to "✨ Autumn",
                dark(0xFF1C120A, 0x55382414, 0xAA5E3D22, 0x662A1B0F, 0xFFFFA726, 0xFFFFCC80, 0xFF2A1600, 0xFFFFF3E0, 0xFFB89A7E, 0xFF2A1406, effect = "leaves")),
            Triple("party", "✨ Fiesta" to "✨ Party",
                dark(0xFF140A26, 0x552A1A4A, 0xAA4A2E7A, 0x661E1238, 0xFFFFD740, 0xFFFFE57F, 0xFF241A00, 0xFFFFFFFF, 0xFFB1A2D0, 0xFF26093A, effect = "confetti")),
            Triple("waves", "✨ Olas" to "✨ Waves",
                dark(0xFF04182A, 0x550B3150, 0xAA1A5580, 0x66082640, 0xFF40C4FF, 0xFF80D8FF, 0xFF002A40, 0xFFE1F5FE, 0xFF8DB2CC, 0xFF062B45, effect = "waves")),
            Triple("lava", "✨ Lámpara de lava" to "✨ Lava lamp",
                dark(0xFF1A0505, 0x55360D0D, 0xAA661818, 0x66280808, 0xFFFF6E40, 0xFFFF9E80, 0xFF2A0800, 0xFFFFEDE6, 0xFFC4918A, 0xFF2E0B00, effect = "lava")),
            Triple("circuit", "✨ Circuito" to "✨ Circuit",
                dark(0xFF020D0D, 0x55062626, 0xAA0D4848, 0x66041C1C, 0xFF00E5FF, 0xFF84FFFF, 0xFF002A2E, 0xFFE0FFFF, 0xFF7FB2B2, effect = "circuit", mono = true)),
            Triple("synthwave", "✨ Synthwave" to "✨ Synthwave",
                dark(0xFF12062A, 0x55281050, 0xAA4A1E84, 0x661C0A3C, 0xFFFF4FD8, 0xFFFF8AE6, 0xFF2A0024, 0xFFFFE6FA, 0xFFB79AD6, 0xFF2A0738, effect = "synthwave")),
            Triple("clouds", "✨ Nubes" to "✨ Clouds",
                light(0xFFBFE3FA, 0xCCFFFFFF, 0xFFE1F2FD, 0xBBE8F4FD, 0xFF1E88E5, 0xFF1565C0, 0xFF0D2A45, 0xFF5B7A96, 0xFFE8F5FF, effect = "clouds")),
            Triple("meteors", "✨ Meteoros" to "✨ Meteors",
                dark(0xFF03050F, 0x55141A33, 0xAA2A3466, 0x660A0E22, 0xFFFFAB40, 0xFFFFD180, 0xFF2A1400, 0xFFF0F2FF, 0xFF9AA0C2, 0xFF0B0A24, effect = "meteors")),
            Triple("bokeh", "✨ Luces" to "✨ Lights",
                dark(0xFF120D0A, 0x552A2018, 0xAA4A382A, 0x661E1611, 0xFFFFCC80, 0xFFFFE0B2, 0xFF2A1A00, 0xFFFFF8F0, 0xFFB8A592, 0xFF1A1020, effect = "bokeh")),
            Triple("glitter", "✨ Brillantina" to "✨ Glitter",
                dark(0xFF0F0B05, 0x55262010, 0xAA4A3E1E, 0x661A150A, 0xFFFFD54F, 0xFFFFE082, 0xFF241A00, 0xFFFFF8E1, 0xFFBBA77A, effect = "glitter")),
            Triple("livebow", "✨ Arcoíris vivo" to "✨ Living rainbow",
                dark(0xFF0A0A12, 0x55202030, 0xAA404060, 0x66161622, 0xFFFFFFFF, 0xFFE0E0E0, 0xFF111111, 0xFFFFFFFF, 0xFFB0B0C0, effect = "gradient")),
            Triple("christmas", "✨ Navidad" to "✨ Christmas",
                dark(0xFF0A1A0F, 0x55163A22, 0xAA2A5E3A, 0x660E2817, 0xFFFF5252, 0xFFFF8A80, 0xFF2A0000, 0xFFFFFFFF, 0xFFA8C4AE, 0xFF2A0A0A, effect = "snow")),
            Triple("violetnight", "✨ Noche violeta" to "✨ Violet night",
                dark(0xFF0E0618, 0x55241238, 0xAA45266A, 0x66180B28, 0xFFEA80FC, 0xFFF3B3FF, 0xFF2A0033, 0xFFF8EEFF, 0xFFB19BC8, 0xFF050B24, effect = "stars")),
            Triple("neonrain", "✨ Lluvia neón" to "✨ Neon rain",
                dark(0xFF0A0614, 0x551C1030, 0xAA3A2060, 0x66120A20, 0xFFFF2BD6, 0xFFFF7AE6, 0xFF2A0024, 0xFF8CF7FF, 0xFF9A88C0, 0xFF06152A, effect = "rain")),
            Triple("volcano", "✨ Volcán" to "✨ Volcano",
                dark(0xFF120E0D, 0x55302624, 0xAA544441, 0x66221B1A, 0xFFFF6D00, 0xFFFF9E40, 0xFF2A1000, 0xFFFFEFE6, 0xFFB09A92, 0xFF3A0E00, effect = "embers")),
            Triple("magicforest", "✨ Bosque mágico" to "✨ Magic forest",
                dark(0xFF06100E, 0x5512302A, 0xAA24544A, 0x660A221E, 0xFFB388FF, 0xFFD1B3FF, 0xFF1A0B33, 0xFFEFFFF8, 0xFF8FB3A8, 0xFF0E0A24, effect = "fireflies")),
            Triple("tropical", "✨ Tropical" to "✨ Tropical",
                light(0xFFD9FAF4, 0xCCFFFFFF, 0xFFB2F0E4, 0xBBC6F4EA, 0xFF00BFA5, 0xFF00897B, 0xFF0B3A33, 0xFF4F8A80, 0xFFB3EFFF, effect = "bubbles")),
            Triple("nightsakura", "✨ Cerezo nocturno" to "✨ Night blossom",
                dark(0xFF140A12, 0x55301A2A, 0xAA54304A, 0x66221220, 0xFFF48FB1, 0xFFF8BBD0, 0xFF33101F, 0xFFFFEEF4, 0xFFC09AAE, 0xFF0E0A1E, effect = "petals")),
            // ---- ✨ Temas animados nuevos
            Triple("aura", "✨ Aura de poder" to "✨ Power aura",
                dark(0xFF0C0800, 0x55281C05, 0xAA54400E, 0x661A1203, 0xFFFFC400, 0xFFFFE082, 0xFF241A00, 0xFFFFF8E1, 0xFFC8B27A, 0xFF2A1400, effect = "aura")),
            Triple("aurablue", "✨ Aura azul" to "✨ Blue aura",
                dark(0xFF020814, 0x55082040, 0xAA14407A, 0x66061628, 0xFF40C4FF, 0xFF80D8FF, 0xFF00203A, 0xFFE1F5FE, 0xFF8CB0CC, 0xFF04102A, effect = "aura")),
            Triple("storm", "✨ Tormenta" to "✨ Storm",
                dark(0xFF0A0E14, 0x551C2430, 0xAA34445A, 0x66121820, 0xFF90CAF9, 0xFFBBDEFB, 0xFF0A1A2A, 0xFFECF2F8, 0xFF8E9BAA, 0xFF141A24, effect = "storm")),
            Triple("fireworks", "✨ Fuegos artificiales" to "✨ Fireworks",
                dark(0xFF05030F, 0x55181430, 0xAA302A5C, 0x660E0B20, 0xFFFF4081, 0xFFFF80AB, 0xFF2A0012, 0xFFFFF0F6, 0xFFA59BC4, 0xFF0A0624, effect = "fireworks")),
            Triple("vortex", "✨ Galaxia espiral" to "✨ Spiral galaxy",
                dark(0xFF040312, 0x55141032, 0xAA2A2264, 0x660C0A24, 0xFFB388FF, 0xFFD1C4E9, 0xFF14082A, 0xFFF3EEFF, 0xFFA49BC8, 0xFF0A0420, effect = "vortex")),
            Triple("pond", "✨ Estanque" to "✨ Pond",
                dark(0xFF031414, 0x55082E2E, 0xAA14524E, 0x66062222, 0xFF64FFDA, 0xFFA7FFEB, 0xFF002A22, 0xFFE0FFF8, 0xFF86B2AA, 0xFF041E26, effect = "ripples")),
            Triple("diamond", "✨ Diamante" to "✨ Diamond",
                dark(0xFF06070F, 0x55141A34, 0xAA2E2F5E, 0x660C1026, 0xFFA78BFA, 0xFFC4B5FD, 0xFF140A2E, 0xFFF3F6FB, 0xFF9AA3C0, 0xFF0C0A22, effect = "diamond")),
            Triple("hexgrid", "✨ Panal sci-fi" to "✨ Sci-fi hive",
                dark(0xFF02080C, 0x55082028, 0xAA144050, 0x66061820, 0xFF00E5FF, 0xFF84FFFF, 0xFF002A30, 0xFFE0FFFF, 0xFF7FAAB2, effect = "hexgrid", mono = true)),
            Triple("pulse", "✨ Pulso neón" to "✨ Neon pulse",
                dark(0xFF0E0414, 0x55281038, 0xAA4A1E66, 0x661A0A26, 0xFFFF2BD6, 0xFFFF7AE6, 0xFF2A0024, 0xFFFFEAFB, 0xFFB79AC8, 0xFF140428, effect = "pulse")),
            Triple("emberstorm", "✨ Fénix" to "✨ Phoenix",
                dark(0xFF140500, 0x55361005, 0xAA66200A, 0x66240A03, 0xFFFF3D00, 0xFFFF8A65, 0xFF2A0800, 0xFFFFEDE6, 0xFFC9978A, 0xFF2A1200, effect = "aura")),
            Triple("winterstorm", "✨ Ventisca" to "✨ Blizzard",
                dark(0xFF0B1420, 0x55182838, 0xAA2E4A66, 0x66101C28, 0xFFB3E5FC, 0xFFE1F5FE, 0xFF0A2030, 0xFFF0F8FF, 0xFF9AB0C2, 0xFF182838, effect = "snow")),
            // ---- Más temas de colores
            Triple("wine", "Vino" to "Wine",
                dark(0xFF1A0910, 0xFF33141F, 0xFF4F2232, 0xFF2A0F19, 0xFFFF6E9C, 0xFFFF9EBD, 0xFF33061A, 0xFFFFEAF1, 0xFFC08E9F)),
            Triple("emerald", "Esmeralda" to "Emerald",
                dark(0xFF041A14, 0xFF0B3328, 0xFF16503F, 0xFF082A20, 0xFF00E5A0, 0xFF64FFCB, 0xFF00261A, 0xFFE6FFF6, 0xFF86B8A6)),
            Triple("slate", "Pizarra" to "Slate",
                dark(0xFF1C232B, 0xFF2C3640, 0xFF3F4C59, 0xFF242D36, 0xFF90A4AE, 0xFFB0BEC5, 0xFF111A1F, 0xFFECEFF1, 0xFF98A6B0)),
            Triple("retro", "Retro (ámbar)" to "Retro (amber)",
                dark(0xFF0D0802, 0xFF1F1405, 0xFF3A260A, 0xFF170F04, 0xFFFFB300, 0xFFFFCA28, 0xFF1A1000, 0xFFFFC34D, 0xFFA77A2C, mono = true)),
            Triple("ice", "Hielo" to "Ice",
                light(0xFFE8F6FB, 0xFFFFFFFF, 0xFFC7E9F5, 0xFFD3EEF7, 0xFF0097C7, 0xFF00779E, 0xFF0B2E3B, 0xFF5D8796, 0xFFF4FCFF)),
            Triple("sand", "Arena" to "Sand",
                light(0xFFF5EDE0, 0xFFFFFDF8, 0xFFE8D9BF, 0xFFEDE2CE, 0xFFB7791F, 0xFF975A16, 0xFF3D2E1A, 0xFF8A7458)),
            Triple("coral", "Coral" to "Coral",
                light(0xFFFFECE8, 0xFFFFFFFF, 0xFFFFCFC4, 0xFFFFDAD2, 0xFFFF5A4E, 0xFFE53935, 0xFF4A201B, 0xFF9C6A63)),
            Triple("lilac", "Lila" to "Lilac",
                light(0xFFF3EBFF, 0xFFFFFFFF, 0xFFE0CCFF, 0xFFE8DAFF, 0xFF9C4DFF, 0xFF7C2DE0, 0xFF2E1A4A, 0xFF7E6A9A)),
        )

        /** Las categorías de la galería de temas: (nombre es, nombre en, efectos que entran). "" = sin animación. */
        val CATEGORIES: List<Triple<String, String, Set<String>>> = listOf(
            Triple("🎨 Clásicos (sin animación)", "🎨 Classic (no animation)", setOf("")),
            Triple("💎 Best Keyboard", "💎 Best Keyboard", setOf("diamond")),
            Triple("🌌 Espacio", "🌌 Space", setOf("stars", "meteors", "vortex", "aurora")),
            Triple("🌿 Naturaleza", "🌿 Nature", setOf("snow", "rain", "leaves", "petals", "fireflies", "clouds", "storm")),
            Triple("🌊 Agua", "🌊 Water", setOf("bubbles", "waves", "ripples")),
            Triple("🔥 Fuego y energía", "🔥 Fire and energy", setOf("embers", "lava", "aura")),
            Triple("🎉 Fiesta y amor", "🎉 Party and love", setOf("confetti", "hearts", "fireworks", "glitter", "bokeh")),
            Triple("💾 Tecnología y retro", "💾 Tech and retro", setOf("matrix", "circuit", "synthwave", "hexgrid", "pulse", "gradient")),
        )

        /** En qué categoría va un tema. */
        fun categoryOf(pal: Palette?): Int {
            val e = pal?.effect ?: ""
            val i = CATEGORIES.indexOfFirst { e in it.third || (e.startsWith("matrix") && "matrix" in it.third) }
            return if (i < 0) 0 else i
        }

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
            "FF8E24AA", "FF5E35B1", "FF6D4C41", "FFFFCDD2", "FFC8E6C9", "FFBBDEFB", "FFE1BEE7",
            "FF263238", "FF37474F", "FF004D40", "FF1A237E", "FF311B92", "FF4A148C", "FF880E4F", "FFB71C1C",
            "FFBF360C", "FFE65100", "FFF57F17", "FF33691E", "FF006064", "FFFFF9C4", "FFFFE0B2", "FFD7CCC8",
            "FFB2EBF2", "FFF8BBD0", "rainbow",
        )
        val TEXT_COLORS = listOf(
            "FFFFFFFF", "FF000000", "FF9AA0A6", "FF4FD1FF", "FF2979FF", "FF7C4DFF", "FFE040FB", "FFFF4081",
            "FFFF1744", "FFFF6D00", "FFFFD54F", "FFC6FF00", "FF00E676", "FF1DE9B6",
            "FF263238", "FF5D4037", "FF1565C0", "FF6A1B9A", "FFAD1457", "FFFF8A80", "FFFFAB40", "FFFFFF8D",
            "FFB9F6CA", "FF84FFFF", "FF80D8FF", "FFB388FF", "FFFF80AB", "FFE0E0E0",
        )

        /** Colores para el tema Hacker (el último es arcoíris). */
        val HACKER_COLORS = listOf(
            "FF00FF66", "FF00E5FF", "FF2979FF", "FF7C4DFF", "FFE040FB", "FFFF4081", "FFFF1744", "FFFF6D00",
            "FFFFC400", "FFC6FF00", "FFFFFFFF", "FF1DE9B6", "FF00B0FF", "FFD500F9", "FFFF9100", "FFFFEA00",
            "FFB2FF59", "FFFF8A80", "rainbow",
        )

        /** Colores de acento para elegir (además del que trae el tema). */
        val ACCENTS = listOf(
            0xFF4FD1FF, 0xFF8AB4F8, 0xFF2979FF, 0xFF7C4DFF, 0xFFB388FF, 0xFFE040FB, 0xFFFF4081, 0xFFFF1744,
            0xFFFF6D00, 0xFFFF8A65, 0xFFFFD54F, 0xFFC6FF00, 0xFF69F0AE, 0xFF00E676, 0xFF1DE9B6, 0xFFFFFFFF,
            0xFF00B0FF, 0xFF651FFF, 0xFFD500F9, 0xFFF50057, 0xFFFF9100, 0xFFFFEA00, 0xFF76FF03, 0xFF00BFA5,
            0xFF90A4AE, 0xFFBCAAA4, 0xFFF48FB1, 0xFF80CBC4,
        ).map { it.toInt() }
    }
}
