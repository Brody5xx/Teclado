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
        if (key == Prefs.THEME) showHackerColors(animate = true)
    }
    /** Los colores del tema Hacker: solo se ven con el tema Hacker. */
    private var hackerSection: android.view.View? = null

    private fun showHackerColors(animate: Boolean) {
        val v = hackerSection ?: return
        val on = prefs.getString(Prefs.THEME, "auto") == "hacker"
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
        var pal = Palette.forTheme(prefs.getString(Prefs.THEME, "auto"), systemDark, prefs.getString(Prefs.HACKER_COLOR, ""))
        pal = Palette.custom(pal, prefs)
        preview.palette = pal
        preview.keyShape = prefs.getString(Prefs.KEY_SHAPE, "normal") ?: "normal"
        preview.labelScale = when (prefs.getString(Prefs.LABEL_SIZE, "normal")) { "small" -> 0.86f; "big" -> 1.16f; else -> 1f }
        preview.keyAnim = prefs.getString(Prefs.KEY_ANIM, "pop") ?: "pop"
        preview.keyHeightDp = when (prefs.getString(Prefs.HEIGHT, "normal")) { "low" -> 46f; "high" -> 58f; else -> 52f } * 0.9f
        preview.setRows(Layouts.letters(prefs.getBoolean(Prefs.NUMBER_ROW, true), ","))
        preview.setBarTools(Tools.load(this, prefs).first)
    }

    private fun buildOptions() {
        card(tr("Colores", "Colors"), Icons.THEME)
        themeGallery()
        accentSwatches()
        colorSwatches(tr("Color de las teclas", "Key color"), Prefs.KEY_COLOR, Palette.KEY_COLORS,
            tr("El primero (multicolor) es el del tema. El último es arcoíris 🌈.", "The first (multicolor) is the theme's. The last one is rainbow 🌈."))
        colorSwatches(tr("Color de las letras", "Letter color"), Prefs.TEXT_COLOR, Palette.TEXT_COLORS,
            tr("El primero (multicolor) es el del tema.", "The first (multicolor) is the theme's."))
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
