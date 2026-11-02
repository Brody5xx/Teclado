package com.luisell.bestkeyboard

import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * "Estilo": todo lo que cambia cómo se ve, se siente y suena el teclado, con el teclado
 * de muestra arriba que cambia al instante (puedes tocar sus teclas para probar).
 */
class StyleActivity : UiActivity() {

    private lateinit var preview: KeyboardView
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        applyPreview()
        if (key == Prefs.THEME || key == Prefs.THEME_TIMED || key == Prefs.THEME_DAY || key == Prefs.THEME_NIGHT) showHackerColors(animate = true)
    }
    /** Los colores del tema Hacker: solo se ven con el tema Hacker. */
    private var hackerSection: android.view.View? = null

    private fun showHackerColors(animate: Boolean) {
        val v = hackerSection ?: return
        val on = Prefs.theme(prefs) == "hacker" || prefs.getString(Prefs.THEME, "auto") == "hacker"
        if (!animate) { v.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE; return }
        // Si algo falla con la animación, solo se muestra o esconde (nunca se cierra la pantalla)
        try { if (on) slideOpen(v) else slideClose(v) } catch (_: Exception) {
            v.visibility = if (on) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        Feedback.init(this)

        val screen = LinearLayout(this)
        screen.orientation = LinearLayout.VERTICAL
        screen.setBackgroundColor(BG)

        // Encabezado: ← Estilo
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.setPadding(dp(12), dp(18), dp(18), dp(8))
        val back = IconView(this, Icons.BACK, 9f)
        back.color = TEXT
        back.setOnClickListener { finish() }
        head.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val title = TextView(this)
        title.text = tr("Estilo", "Style")
        title.textSize = 28f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(TEXT)
        title.setPadding(dp(6), 0, 0, 0)
        title.post {
            title.paint.shader = LinearGradient(0f, 0f, title.width.toFloat(), 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            title.invalidate()
        }
        head.addView(title)
        screen.addView(head)

        // El teclado de muestra (se queda fijo arriba)
        preview = KeyboardView(this)
        preview.setRows(Layouts.letters(prefs.getBoolean(Prefs.NUMBER_ROW, true), ","))
        preview.spaceLabel = tr("Toca para probar", "Tap to try")
        preview.listener = object : KeyboardView.Listener {
            override fun onKeyText(text: String) {}
            override fun onKeyCode(code: Int) {}
            override fun onDeletePress() {}
            override fun onCursorMove(steps: Int) {}
            override fun onToolbar(action: Int) {}
            override fun onLongPressGlobe() {}
            override fun onSuggestion(slot: Int) {}
            override fun onGesture(xs: FloatArray, ys: FloatArray, count: Int) {}
            override fun onResizeDone(scale: Float) {}
            override fun onKeyFeedback() {
                if (prefs.getBoolean(Prefs.VIBRATE, true)) Feedback.vibrate(this@StyleActivity, "system", prefs.getString(Prefs.VIB_STRENGTH, "medium"), preview)
                if (prefs.getBoolean(Prefs.SOUND, false)) Feedback.sound(this@StyleActivity, preview.lastPressKind,
                    prefs.getString(Prefs.SOUND_STYLE, "modern"), prefs.getString(Prefs.SOUND_VOL, "medium"))
            }
        }
        screen.addView(preview, LinearLayout.LayoutParams(-1, -2))

        val sv = ScrollView(this)

        PanelUi.glowScrollbar(sv)
        sv.isVerticalScrollBarEnabled = false
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), 0, dp(18), dp(48))
        }
        col = root
        sv.addView(root)
        screen.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(screen)

        buildOptions()
        applyPreview()
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }

    /** Pone en el teclado de muestra lo que elegiste. */
    private fun applyPreview() {
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        var pal = Palette.forTheme(Prefs.theme(prefs), systemDark, prefs.getString(Prefs.HACKER_COLOR, ""))
        pal = Palette.custom(pal, prefs)
        preview.palette = pal
        preview.keyShape = prefs.getString(Prefs.KEY_SHAPE, "normal") ?: "normal"
        preview.labelScale = when (prefs.getString(Prefs.LABEL_SIZE, "normal")) { "small" -> 0.86f; "big" -> 1.16f; else -> 1f }
        preview.keyAnim = prefs.getString(Prefs.KEY_ANIM, "pop") ?: "pop"
        preview.keyHeightDp = when (prefs.getString(Prefs.HEIGHT, "normal")) { "low" -> 46f; "high" -> 58f; else -> 52f } * 0.9f
        preview.setRows(Layouts.letters(prefs.getBoolean(Prefs.NUMBER_ROW, true), ","))
        preview.setBarTools(Tools.load(this, prefs).first)
    }

    private lateinit var bgStatus: android.widget.TextView

    private fun paintBg() {
        bgStatus.text = when (BgMedia.kind(this)) {
            "image" -> tr("✓ Tienes una imagen de fondo.", "✓ You have a background image.")
            "video" -> tr("✓ Tienes un video de fondo.", "✓ You have a background video.")
            else -> tr("Sin fondo propio (se usa el del tema).", "No custom background (the theme's is used).")
        }
    }

    private fun pickBg(type: String, req: Int) {
        val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType(type)
        try { @Suppress("DEPRECATION") startActivityForResult(i, req) } catch (_: Exception) {
            toast("No encontré la galería.", "Couldn't find the gallery.")
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        try { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
        val stamp = { prefs.edit().putLong(Prefs.BG_STAMP, System.currentTimeMillis()).apply(); paintBg() }
        when (requestCode) {
            71 -> Thread {
                val ok = BgMedia.saveImage(this, uri)
                runOnUiThread {
                    if (ok) { stamp(); toast("🖼️ Listo: tu imagen ya está de fondo.", "🖼️ Done: your image is now the background.") }
                    else toast("No se pudo abrir la imagen.", "Couldn't open the image.")
                }
            }.start()
            72 -> {
                val msg = android.widget.TextView(this).apply {
                    text = tr("Comprimiendo el video… 0%", "Compressing the video… 0%")
                    textSize = 16f; setTextColor(TEXT); setPadding(dp(24), dp(20), dp(24), dp(8))
                }
                val dlg = android.app.AlertDialog.Builder(this)
                    .setTitle(tr("🎬 Preparando tu video", "🎬 Preparing your video"))
                    .setView(msg)
                    .setCancelable(false)
                    .show()
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                BgMedia.saveVideo(this, uri, { p -> msg.text = tr("Comprimiendo el video… $p%", "Compressing the video… $p%") }) { ok ->
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    try { dlg.dismiss() } catch (_: Exception) { }
                    if (ok) { stamp(); toast("🎬 Listo: tu video ya está de fondo.", "🎬 Done: your video is now the background.") }
                    else toast("No se pudo preparar el video.", "Couldn't prepare the video.")
                }
            }
        }
    }

    private fun buildOptions() {
        card(tr("Colores", "Colors"), Icons.THEME)
        themeGallery()
        // ---- 🖼️ Fondo propio: tu imagen o tu video
        card(tr("Fondo propio", "Your own background"), Icons.IMAGE)
        text(tr("Pon una foto o un video de tu galería detrás de las teclas. El video se comprime para que vaya ligero, se ve completo y se repite sin fin.",
            "Put a photo or video from your gallery behind the keys. The video is compressed to stay light, plays in full and loops forever."), 12f, faded = true)
        bgStatus = text("", 13f)
        button(tr("🖼️ Elegir imagen", "🖼️ Pick image"), primary = true) { pickBg("image/*", 71) }
        button(tr("🎬 Elegir video", "🎬 Pick video")) { pickBg("video/*", 72) }
        switch(tr("🔊 Sonido del video", "🔊 Video sound"), Prefs.BG_SOUND, false,
            tr("Apagado = en silencio. También lo cambias rápido con el botón 🔊 del teclado.", "Off = muted. You can also switch it quickly with the 🔊 keyboard button."))
        segmented(tr("Oscurecer el fondo (para leer bien las letras)", "Darken the background (to read the letters)"), Prefs.BG_DIM, "0.45",
            listOf("0.25" to tr("Poco", "Little"), "0.45" to tr("Medio", "Medium"), "0.65" to tr("Mucho", "A lot")))
        button(tr("✕ Quitar el fondo", "✕ Remove background")) {
            BgMedia.clear(this)
            prefs.edit().putLong(Prefs.BG_STAMP, System.currentTimeMillis()).apply()
            paintBg()
        }
        text(tr("Si tu tema es animado (✨), sus animaciones salen encima de tu fondo.", "If your theme is animated (✨), its animations show on top of your background."), 12f, faded = true)
        paintBg()

        // ---- 🌗 Un tema de día y otro de noche (cambia solo según la hora)
        switch(tr("🌗 Tema de día y de noche", "🌗 Day and night theme"), Prefs.THEME_TIMED, false,
            tr("Si lo prendes, el teclado usa un tema de día y otro de noche, y cambia solo según la hora.",
                "If on, the keyboard uses a day theme and a night theme, and switches by itself by the time."))
        themeGallery(Prefs.THEME_DAY, tr("☀️ Tema de día", "☀️ Day theme"), "light", grouped = false)
        segmented(tr("El día empieza a las", "Day starts at"), Prefs.DAY_FROM, "7",
            listOf("6" to "6 AM", "7" to "7 AM", "8" to "8 AM", "9" to "9 AM"))
        themeGallery(Prefs.THEME_NIGHT, tr("🌙 Tema de noche", "🌙 Night theme"), "dark", grouped = false)
        segmented(tr("La noche empieza a las", "Night starts at"), Prefs.NIGHT_FROM, "19",
            listOf("17" to "5 PM", "18" to "6 PM", "19" to "7 PM", "20" to "8 PM"))
        // ---- 🎨 Colores a tu gusto: escondidos (el tema ya trae los suyos); solo si quieres cambiar alguno
        divider()
        val custom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
        val anyCustom = !prefs.getString(Prefs.ACCENT, "").isNullOrEmpty() || !prefs.getString(Prefs.KEY_COLOR, "").isNullOrEmpty() ||
            !prefs.getString(Prefs.TEXT_COLOR, "").isNullOrEmpty()
        lateinit var openBtn: android.widget.TextView
        fun label(open: Boolean) = (if (open) "▲  " else "▼  ") + tr("🎨 Cambiar los colores del tema (opcional)", "🎨 Change the theme colors (optional)") +
            if (anyCustom) tr("  · tienes cambios", "  · you have changes") else ""
        openBtn = button(label(false)) {
            val open = custom.visibility != android.view.View.VISIBLE
            try { if (open) slideOpen(custom) else slideClose(custom) } catch (_: Exception) {
                custom.visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE
            }
            openBtn.text = label(open)
        }
        text(tr("Cada tema ya trae sus colores. Ábrelo solo si quieres cambiar alguno. El primer círculo (de colores) deja el color del tema.",
            "Each theme already has its colors. Open this only if you want to change one. The first (multicolor) circle keeps the theme's color."), 12f, faded = true)
        col.addView(custom, LinearLayout.LayoutParams(-1, -2))
        val mainCol = col
        col = custom
        accentSwatches()
        colorSwatches(tr("Color de las teclas", "Key color"), Prefs.KEY_COLOR, Palette.KEY_COLORS,
            tr("El último es arcoíris 🌈.", "The last one is rainbow 🌈."))
        colorSwatches(tr("Color de las letras", "Letter color"), Prefs.TEXT_COLOR, Palette.TEXT_COLORS, "")
        button(tr("↺ Volver a los colores del tema", "↺ Back to the theme colors")) {
            prefs.edit().remove(Prefs.ACCENT).remove(Prefs.KEY_COLOR).remove(Prefs.TEXT_COLOR).apply()
            recreate()
        }
        col = mainCol
        hackerSection = hackerSwatches()
        showHackerColors(animate = false)

        card(tr("Teclas", "Keys"), Icons.SHAPES)
        chips(tr("Forma", "Shape"), Prefs.KEY_SHAPE, "normal", listOf(
            "square" to tr("Cuadradas", "Square"), "normal" to "Normal", "round" to tr("Redondas", "Round"),
            "pill" to tr("Píldora", "Pill"), "circle" to tr("Círculo", "Circle"), "outline" to tr("Solo borde", "Outline"),
            "flat" to tr("Sin fondo", "No background"),
        ))
        segmented(tr("Tamaño de las letras", "Letter size"), Prefs.LABEL_SIZE, "normal",
            listOf("small" to tr("Pequeñas", "Small"), "normal" to "Normal", "big" to tr("Grandes", "Big")))
        segmented(tr("Altura de las teclas", "Key height"), Prefs.HEIGHT, "normal",
            listOf("low" to tr("Bajas", "Low"), "normal" to "Normal", "high" to tr("Altas", "Tall")))
        switch(tr("Fila de números arriba", "Number row on top"), Prefs.NUMBER_ROW, true)
        switch(tr("Letra grande al tocar", "Big letter on tap"), Prefs.PREVIEW, true)

        card(tr("Barra de arriba", "Top bar"), Icons.MENU)
        text(tr("Cómo se mueve la barra de botones de arriba cuando la deslizas.", "How the top button bar moves when you swipe it."), 12f, faded = true)
        segmented(tr("Curva de rueda", "Wheel curve"), Prefs.STRIP_ARC, "normal",
            listOf("none" to tr("Plana", "Flat"), "soft" to tr("Suave", "Soft"), "normal" to "Normal", "strong" to tr("Fuerte", "Strong")))
        segmented(tr("Línea de luz", "Light line"), Prefs.STRIP_LIGHT, "normal",
            listOf("off" to tr("Apagada", "Off"), "soft" to tr("Suave", "Soft"), "normal" to "Normal", "bright" to tr("Brillante", "Bright")))
        switch(tr("Nombre del botón del medio", "Name of the middle button"), Prefs.STRIP_NAMES, true)

        card(tr("Animación al tocar", "Tap animation"), Icons.RIPPLE)
        chipGrid(tr("Efecto", "Effect"), Prefs.KEY_ANIM, "pop", listOf(
            "none" to tr("Ninguno", "None"), "random" to tr("🎲 Sorpresa", "🎲 Surprise"), "pop" to tr("Rebote", "Bounce"),
            "zoom" to "Zoom", "lift" to tr("Saltar", "Jump"), "jelly" to tr("Gelatina", "Jelly"),
            "shake" to tr("Temblor", "Shake"), "spin" to tr("Giro", "Spin"), "wave" to tr("Ola", "Wave"),
            "ripple" to tr("Onda", "Ripple"), "glow" to tr("Brillo", "Glow"), "flash" to "Flash",
            "neon" to tr("Neón", "Neon"), "rings" to tr("Anillos", "Rings"), "bubble" to tr("Burbuja", "Bubble"),
            "sparks" to tr("Chispas", "Sparks"), "stars" to tr("Estrellas", "Stars"), "confetti" to tr("Confeti", "Confetti"),
            "hearts" to tr("Corazones", "Hearts"), "fire" to tr("Fuego", "Fire"), "snow" to tr("Nieve", "Snow"),
            "bubbles" to tr("Burbujitas", "Bubbles"), "fly" to tr("Letra voladora", "Flying letter"),
            "drop" to tr("Hundir", "Press"), "swing" to tr("Columpio", "Swing"), "lightning" to tr("Rayo", "Lightning"),
            "petals" to tr("Pétalos", "Petals"), "music" to tr("Notas musicales", "Music notes"), "emojis" to tr("Emoji sorpresa", "Surprise emoji"),
            "ink" to tr("Tinta", "Ink"), "pulse" to tr("Latido", "Pulse"), "orbit" to tr("Órbita", "Orbit"),
            "rainbow" to tr("Arcoíris", "Rainbow"), "pixel" to tr("Pixeles", "Pixels"),
            "flip" to tr("Voltear", "Flip"), "heartbeat" to tr("Latido de corazón", "Heartbeat"), "rubber" to tr("Liga", "Rubber"),
            "shards" to tr("Romper", "Shatter"), "smoke" to tr("Humo", "Smoke"), "spiral" to tr("Espiral", "Spiral"),
            "flower" to tr("Flor", "Flower"), "coin" to tr("Moneda", "Coin"), "balloon" to tr("Globo", "Balloon"),
            "butterfly" to tr("Mariposa", "Butterfly"), "glitch" to tr("Falla digital", "Glitch"), "splash" to tr("Salpicar", "Splash"),
        ))
        text(tr("Toca las teclas de arriba para ver el efecto.", "Tap the keys above to see the effect."), 12f, faded = true)

        card(tr("Vibración", "Vibration"), Icons.VIBRATE)
        switch(tr("Vibrar al tocar", "Vibrate on tap"), Prefs.VIBRATE, true)
        segmented(tr("Fuerza", "Strength"), Prefs.VIB_STRENGTH, "medium",
            listOf("soft" to tr("Suave", "Soft"), "medium" to tr("Media", "Medium"), "strong" to tr("Fuerte", "Strong")))

        card(tr("Sonido", "Sound"), Icons.SPEAKER)
        switch(tr("Sonido al tocar", "Sound on tap"), Prefs.SOUND, false)
        segmented(tr("Tipo", "Type"), Prefs.SOUND_STYLE, "modern",
            listOf("modern" to tr("Moderno", "Modern"), "classic" to tr("Clásico", "Classic")))
        segmented(tr("Volumen", "Volume"), Prefs.SOUND_VOL, "medium",
            listOf("low" to tr("Bajo", "Low"), "medium" to tr("Medio", "Medium"), "high" to tr("Alto", "High")))
        text(tr("No suena si el teléfono está en silencio o en vibración.", "It doesn't play when the phone is on silent or vibrate."), 12f, faded = true)
    }
}
