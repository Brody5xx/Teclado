package com.luisell.bestkeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Pantalla de la app: activar el teclado, probarlo y cambiar ajustes. */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var step1: Button
    private lateinit var step2: Button
    private lateinit var col: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        title = "Best Keyboard"
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun buildUi(): View {
        val sv = ScrollView(this)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(40))
        }
        sv.addView(col)

        text("Best Keyboard", 28f, bold = true)
        text("Developer: Luisell Peña Rodríguez", 14f)
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "" }
        text("Versión $version", 13f, faded = true)

        section("Configuración inicial")
        status = text("", 15f)
        step1 = button("1. Activar Best Keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        step2 = button("2. Elegir Best Keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        text(
            "Al activarlo, Android muestra un aviso de que el teclado \"podría recopilar lo que escribes\". " +
                "Ese aviso sale con TODOS los teclados. Best Keyboard no tiene permiso de internet: " +
                "nada de lo que escribes sale de tu teléfono.", 13f, faded = true,
        )
        col.addView(EditText(this).apply {
            hint = "Toca aquí para probar el teclado"
            minLines = 2
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        section("Apariencia")
        radio("Tema", Prefs.THEME, "auto", listOf(
            "auto" to "Automático (igual que el teléfono)", "light" to "Claro", "dark" to "Oscuro",
        ))
        radio("Altura de las teclas", Prefs.HEIGHT, "normal", listOf(
            "low" to "Baja", "normal" to "Normal", "high" to "Alta",
        ))
        switch("Fila de números arriba", Prefs.NUMBER_ROW, true)
        switch("Mostrar la letra grande al tocar", Prefs.PREVIEW, true)

        section("Al tocar una tecla")
        switch("Vibración", Prefs.VIBRATE, true)
        radio("Fuerza de la vibración", Prefs.VIB_STRENGTH, "medium", listOf(
            "soft" to "Suave", "medium" to "Media", "strong" to "Fuerte",
        ))
        switch("Sonido", Prefs.SOUND, false)

        section("Escritura")
        switch("Mayúscula automática al empezar una oración", Prefs.AUTOCAP, true)
        switch("Doble espacio pone un punto", Prefs.DOUBLE_SPACE, true)

        section("Trucos")
        text(
            "• Mantén presionada una letra para los acentos (á, é, ñ, ü...).\n" +
                "• Mantén presionado el punto para ¿ ? ¡ ! y más signos.\n" +
                "• Toca ⇧ dos veces seguidas para escribir TODO EN MAYÚSCULAS.\n" +
                "• Desliza el dedo sobre la barra espaciadora para mover el cursor.\n" +
                "• Toca 🌐 para cambiar Español / English.\n" +
                "• Mantén presionado 🌐 para cambiar a otro teclado (por ejemplo, Gboard).\n" +
                "• Arriba del teclado: ⚙ abre esta pantalla, ◐ cambia claro/oscuro, ⌄ esconde el teclado.\n" +
                "• Mantén presionada una tecla de borrar para borrar rápido.", 14f,
        )

        section("Novedades — Versión 1")
        text(
            "Primera versión: teclado nativo y rápido con letras (incluye Ñ y acentos), números, " +
                "símbolos, temas claro y oscuro, vibración, sonido, mayúscula automática, doble espacio " +
                "para el punto, mover el cursor con la barra espaciadora y teclado numérico grande para " +
                "campos de teléfono y números.", 14f,
        )

        section("Próximamente")
        text(
            "v2: sugerencias de palabras y autocorrector (español e inglés)\n" +
                "v3: emojis y portapapeles infinito con imágenes\n" +
                "v4: dictado por voz y traducción\n" +
                "v5: escanear texto con la cámara\n" +
                "v6: deslizar el dedo para escribir", 14f,
        )
        return sv
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith("$packageName/")
        step1.text = if (enabled) "✓ 1. Best Keyboard activado" else "1. Activar Best Keyboard"
        step2.text = if (selected) "✓ 2. Best Keyboard elegido" else "2. Elegir Best Keyboard"
        step2.isEnabled = enabled
        status.text = when {
            selected -> "✅ ¡Todo listo! Ya estás usando Best Keyboard."
            enabled -> "Falta el paso 2: toca el botón y elige Best Keyboard."
            else -> "Sigue estos 2 pasos para empezar a usar el teclado:"
        }
    }

    // ---------------------------------------------------------------- Ayudantes

    private fun text(s: String, size: Float, bold: Boolean = false, faded: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        if (faded) t.alpha = 0.7f
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        return t
    }

    private fun section(title: String) {
        val t = TextView(this)
        t.text = title
        t.textSize = 18f
        t.setTypeface(t.typeface, Typeface.BOLD)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
    }

    private fun button(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.gravity = Gravity.CENTER
        b.setOnClickListener { onClick() }
        col.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return b
    }

    @Suppress("DEPRECATION")
    private fun switch(label: String, key: String, def: Boolean) {
        val s = Switch(this)
        s.text = label
        s.textSize = 15f
        s.isChecked = prefs.getBoolean(key, def)
        s.setPadding(0, dp(10), 0, dp(10))
        s.setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
        col.addView(s, LinearLayout.LayoutParams(-1, -2))
    }

    private fun radio(label: String, key: String, def: String, options: List<Pair<String, String>>) {
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val group = RadioGroup(this)
        val current = prefs.getString(key, def)
        for ((value, name) in options) {
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = name
            rb.tag = value
            group.addView(rb)
            if (value == current) rb.isChecked = true
        }
        group.setOnCheckedChangeListener { g, id ->
            val v = g.findViewById<RadioButton>(id)?.tag as? String
            if (v != null) prefs.edit().putString(key, v).apply()
        }
        col.addView(group, LinearLayout.LayoutParams(-1, -2))
    }
}
