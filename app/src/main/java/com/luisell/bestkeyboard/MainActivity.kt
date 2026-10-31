package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Pantalla de la app: activar el teclado, probarlo y cambiar ajustes. */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var step1: TextView
    private lateinit var step2: TextView
    private lateinit var col: LinearLayout
    private lateinit var learnedInfo: TextView
    private lateinit var micBtn: TextView

    companion object {
        // Colores de la pantalla de ajustes (oscuro, futurista)
        const val BG = 0xFF0B0D12.toInt()
        const val CARD = 0xFF151922.toInt()
        const val CARD2 = 0xFF1C2130.toInt()
        const val LINE = 0xFF242A38.toInt()
        const val TEXT = 0xFFEDF0F7.toInt()
        const val SUB = 0xFF8C94A8.toInt()
        const val CYAN = 0xFF4FD1FF.toInt()
        const val VIOLET = 0xFF8B7CFF.toInt()
        const val EXTRA_ASK_MIC = "pedir_micro"
        const val EXTRA_ASK_CAMERA = "pedir_camara"
        const val EXTRA_ASK_PHOTOS = "pedir_fotos"
        private const val REQ_PHOTOS = 9
        private const val REQ_MIC = 7
        private const val REQ_CAMERA = 8
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Diseño oscuro y moderno, sin la barra de título vieja
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        title = "Best Keyboard"
        window.statusBarColor = BG
        window.navigationBarColor = BG
        setContentView(buildUi())
        handleAsk(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleAsk(intent)
    }

    private fun handleAsk(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_ASK_MIC, false) == true) askMic()
        if (i?.getBooleanExtra(EXTRA_ASK_CAMERA, false) == true) askCamera()
        if (i?.getBooleanExtra(EXTRA_ASK_PHOTOS, false) == true) askPhotos()
    }

    private lateinit var photosBtn: TextView

    private fun askPhotos() {
        if (Screenshots.hasPermission(this)) { refreshMic(); return }
        if (Screenshots.hasPartial(this)) {
            // Dio "solo algunas fotos": hay que cambiarlo a "Permitir todo" en los ajustes de la app
            Toast.makeText(this, "Toca Permisos → Fotos y videos → \"Permitir todo\".", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
            } catch (_: Exception) { }
            return
        }
        requestPermissions(arrayOf(Screenshots.permission()), REQ_PHOTOS)
    }

    private fun cameraGranted() = checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun askCamera() {
        if (cameraGranted()) { refreshMic(); return }
        requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQ_CAMERA)
    }

    private fun micGranted() = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun askMic() {
        if (micGranted()) { refreshMic(); return }
        requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_MIC)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PHOTOS) {
            refreshMic()
            Toast.makeText(
                this,
                if (Screenshots.hasPermission(this)) "¡Listo! Toma una captura y abre el teclado." else "Sin ese permiso no puedo sugerir tus capturas.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (requestCode == REQ_CAMERA) {
            refreshMic()
            Toast.makeText(
                this,
                if (cameraGranted()) "¡Listo! Vuelve a donde escribías y toca el escáner." else "Sin el permiso de la cámara no se puede escanear.",
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (requestCode != REQ_MIC) return
        refreshMic()
        if (micGranted()) {
            Toast.makeText(this, "¡Listo! Vuelve a donde escribías y toca 🎤 para dictar.", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Sin el permiso del micrófono no se puede dictar.", Toast.LENGTH_LONG).show()
        }
    }

    private lateinit var camBtn: TextView

    /** Abre la pantalla de Android donde se elige el corrector ortográfico. */
    private fun openSpellSettings() {
        val tries = listOf(
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$SpellCheckersSettingsActivity"),
            Intent("android.settings.SPELL_CHECKER_SETTINGS"),
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
        )
        for (i in tries) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) { }
        }
        Toast.makeText(this, "Búscalo en Ajustes → Sistema → Idiomas → Corrector ortográfico.", Toast.LENGTH_LONG).show()
    }

    private fun refreshMic() {
        if (::micBtn.isInitialized) {
            micBtn.text = if (micGranted()) "✓  Micrófono permitido" else "Permitir el micrófono"
        }
        if (::photosBtn.isInitialized) {
            photosBtn.text = if (Screenshots.hasPermission(this)) "✓  Capturas de pantalla permitidas"
            else "Permitir ver mis capturas"
        }
        if (::camBtn.isInitialized) {
            camBtn.text = if (cameraGranted()) "✓  Cámara permitida" else "Permitir la cámara"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshMic()
        WordStore.load(this) { refreshLearned() }
    }

    private fun refreshLearned() {
        if (!::learnedInfo.isInitialized) return
        val n = WordStore.learnedCount()
        learnedInfo.text = "Palabras tuyas aprendidas: $n"
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density

    /** La columna principal; [col] es la tarjeta donde se van agregando las opciones. */
    private lateinit var root: LinearLayout

    private fun buildUi(): View {
        val sv = ScrollView(this)
        sv.setBackgroundColor(BG)
        sv.isVerticalScrollBarEnabled = false
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(48))
        }
        sv.addView(root)
        col = root

        // ---- Encabezado
        val title = TextView(this)
        title.text = "Best Keyboard"
        title.textSize = 34f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(TEXT)
        title.post {
            title.paint.shader = LinearGradient(0f, 0f, title.width.toFloat(), 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            title.invalidate()
        }
        root.addView(title)
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        val meta = LinearLayout(this)
        meta.orientation = LinearLayout.HORIZONTAL
        meta.gravity = Gravity.CENTER_VERTICAL
        val dev = TextView(this)
        dev.text = "Developer: Luisell Peña Rodríguez"
        dev.textSize = 13f
        dev.setTextColor(SUB)
        meta.addView(dev, LinearLayout.LayoutParams(0, -2, 1f))
        val pill = TextView(this)
        pill.text = "v$version"
        pill.textSize = 11f
        pill.setTextColor(CYAN)
        pill.setPadding(dp(10), dp(4), dp(10), dp(4))
        pill.background = GradientDrawable().apply { cornerRadius = dpf(20f); setStroke(dp(1), 0x664FD1FF) }
        meta.addView(pill)
        root.addView(meta, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        // ---- Configuración inicial (tarjeta con borde de color)
        card("Configuración inicial", Icons.SETTINGS, glow = true)
        status = text("", 15f)
        step1 = button("1. Activar Best Keyboard", primary = true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        step2 = button("2. Elegir Best Keyboard", primary = true) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        text(
            "Android muestra un aviso de que el teclado \"podría recopilar lo que escribes\". Sale con TODOS " +
                "los teclados. Lo que escribes NUNCA sale de tu teléfono.", 12f, faded = true,
        )
        col.addView(EditText(this).apply {
            hint = "Toca aquí para probar el teclado"
            minLines = 2
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // ---- Apariencia
        card("Apariencia", Icons.THEME)
        segmented("Tema", Prefs.THEME, "auto", listOf("auto" to "Auto", "light" to "Claro", "dark" to "Oscuro"))
        segmented("Altura de las teclas", Prefs.HEIGHT, "normal", listOf("low" to "Baja", "normal" to "Normal", "high" to "Alta"))
        switch("Fila de números arriba", Prefs.NUMBER_ROW, true)
        switch("Letra grande al tocar", Prefs.PREVIEW, true)
        text("Para cambiar el tamaño: toca ⋯ arriba del teclado → «Cambiar tamaño».", 12f, faded = true)

        // ---- Tacto
        card("Al tocar una tecla", Icons.EDIT)
        switch("Vibración", Prefs.VIBRATE, true)
        segmented("Fuerza de la vibración", Prefs.VIB_STRENGTH, "medium", listOf("soft" to "Suave", "medium" to "Media", "strong" to "Fuerte"))
        switch("Sonido", Prefs.SOUND, false)

        // ---- Escritura
        card("Escritura", Icons.SPELL)
        switch("Mayúscula automática al empezar una oración", Prefs.AUTOCAP, true)
        switch("Doble espacio pone un punto", Prefs.DOUBLE_SPACE, true)
        switch("Mayúsculas en lo seleccionado", Prefs.SEL_CAPS, false,
            "Selecciona un texto y toca ⇧: pasa a MAYÚSCULAS. Toca otra vez para minúsculas y otra para Tipo Título.")

        // ---- Barra espaciadora
        card("Barra espaciadora", Icons.GLOBE)
        switch("Deslizar la barra cambia el idioma", Prefs.SPACE_LANG, false,
            "Se quita el botón 🌐. Desliza rápido para cambiar de idioma; mantén presionado y desliza para mover el cursor.")

        // ---- Sugerencias
        card("Sugerencias y autocorrector", Icons.TRANSLATE)
        switch("Mostrar sugerencias de palabras", Prefs.SUGGEST, true)
        switch("Deslizar el dedo para escribir", Prefs.SWIPE, true)
        switch("Autocorrector", Prefs.AUTOCORRECT, true, "Corrige al tocar espacio. Toca ⌫ justo después para deshacerlo.")
        switch("Aprender mis palabras", Prefs.LEARN, true, "Nombres, siglas y palabras del trabajo.")
        learnedInfo = text("", 12f, faded = true)
        button("Borrar las palabras aprendidas") {
            AlertDialog.Builder(this)
                .setTitle("¿Borrar lo aprendido?")
                .setMessage("El teclado olvidará tus palabras y las que sueles escribir juntas.")
                .setPositiveButton("Borrar") { _, _ ->
                    WordStore.clearLearned(this)
                    refreshLearned()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // ---- Ortografía
        card("Revisar ortografía", Icons.SPELL)
        text(
            "Toca el botón de ortografía arriba del teclado: ves todo tu texto con los errores en rojo; toca uno " +
                "para corregirlo o «Corregir todo».\n\n" +
                "Si SELECCIONAS un texto antes, se revisa solo eso, palabra por palabra, y aparece «Reescribir formal»: " +
                "la IA de Google dentro del teléfono (Gemini Nano) lo vuelve más profesional. Solo funciona en " +
                "teléfonos que tienen esa IA.", 13f, faded = true,
        )
        switch("Corrector ortográfico de Android", Prefs.SPELL, true, "Subraya en rojo en las apps que lo usan.")
        button("Abrir los ajustes del corrector de Android") { openSpellSettings() }

        // ---- Permisos y herramientas
        card("Voz, cámara y capturas", Icons.MIC)
        micBtn = button("Permitir el micrófono") { askMic() }
        camBtn = button("Permitir la cámara") { askCamera() }
        photosBtn = button("Permitir ver mis capturas") { askPhotos() }
        switch("Sugerir mi última captura de pantalla", Prefs.SHOTS, true)
        text("Dictado: di \"coma\", \"punto final\", \"nueva línea\"… Traducción y escáner funcionan dentro del teléfono.", 12f, faded = true)

        // ---- Portapapeles
        card("Portapapeles", Icons.CLIP)
        switch("Guardar todo lo que copio", Prefs.CLIP_HISTORY, true, "Dura 48 horas; lo fijado se queda para siempre. Las contraseñas no se guardan.")
        button("Borrar el historial (menos lo fijado)") {
            AlertDialog.Builder(this)
                .setTitle("¿Borrar el portapapeles?")
                .setMessage("Se borrará todo lo copiado, menos lo que fijaste.")
                .setPositiveButton("Borrar") { _, _ -> ClipStore.load(this) { ClipStore.clearUnpinned() } }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // ---- Trucos
        card("Trucos", Icons.IDEA)
        text(
            "•  Mantén una letra para los acentos (á, é, ñ…) y el punto para ¿ ? ¡ !\n" +
                "•  ⇧ dos veces = TODO EN MAYÚSCULAS.\n" +
                "•  Desliza el dedo por las letras para escribir una palabra entera.\n" +
                "•  Mantén la coma para los emojis.\n" +
                "•  ⋯ abre «Más opciones»; con el lápiz ✎ acomodas todos los botones.\n" +
                "•  El hacker 🧑‍💻 cambia a teclado de computadora (Ctrl, Alt, Esc, Tab, F1–F12).\n" +
                "•  Mantén 🌐 para cambiar a otro teclado.", 13f,
        )

        // ---- Novedades (lo más nuevo visible, lo demás escondido)
        card("Novedades", Icons.RECENT)
        subTitle("Versión 7.6")
        text(
            "•  Pantalla de ajustes nueva: oscura, minimalista y futurista.\n" +
                "•  Mayúsculas en lo seleccionado (actívalo en «Escritura»).\n" +
                "•  Selecciona un texto → ortografía → «Reescribir formal» con la IA del teléfono.", 13f,
        )
        val older = LinearLayout(this)
        older.orientation = LinearLayout.VERTICAL
        older.visibility = View.GONE
        val toggle = button("Ver versiones anteriores") {}
        toggle.setOnClickListener {
            val show = older.visibility != View.VISIBLE
            older.visibility = if (show) View.VISIBLE else View.GONE
            toggle.text = if (show) "Ocultar versiones anteriores" else "Ver versiones anteriores"
        }
        col.addView(older)
        col = older
        subTitle("Versión 7.5")
        text(
            "• Teclado de computadora (ícono del hacker, en ⋯): Esc, Tab, Ctrl, Alt, Shift, flechas y Fn " +
                "con F1–F12, Inicio/Fin y RePág/AvPág. Ideal para terminales y código.\n" +
                "   – Ctrl, Alt y Shift: un toque = solo la próxima tecla (borde azul); dos toques = fijo (azul); tres = apagado.\n" +
                "   – Mantén una tecla para su otro símbolo (por ejemplo 2 → @).\n" +
                "• Ortografía de una selección: selecciona palabras y toca el botón de ortografía. " +
                "Se revisan una por una, incluso tus palabras guardadas (usa «Saltar» para dejarlas).", 13f, faded = true,
        )

        subTitle("Versión 7.4")
        text(
            "• Revisar ortografía como Google Docs: ves todo tu texto con las palabras mal escritas en rojo. " +
                "Toca una para corregirla, o «Corregir todo».\n" +
                "• Mejores ideas para palabras muy mal escritas («autografia» → «ortografía»).\n" +
                "• Si deshaces una corrección, la revisión de ortografía igual la revisa.", 13f, faded = true,
        )

        subTitle("Versión 7.3")
        text(
            "• Ahora puedes mover TODOS los botones de la barra de arriba (también el micrófono y bajar el teclado). " +
                "En ⋯ → ✎: mantén presionado un botón y arrástralo; un toque lo pasa al otro lado.\n" +
                "• Botones de «Más opciones» más grandes, como en Gboard; los nombres largos se deslizan para leerlos.", 13f, faded = true,
        )

        subTitle("Versión 7.2")
        text(
            "• Acomoda la barra de arriba a tu manera: toca ⋯ y luego el lápiz ✎. Arrastra las opciones " +
                "dentro de la barra, o entre la barra y «Más opciones». Un toque las pasa al otro lado.\n" +
                "• «Más opciones» ya no repite lo que está en la barra de arriba.", 13f, faded = true,
        )

        subTitle("Versión 7.1")
        text(
            "• Botón ⋯ «Más opciones» en la barra de arriba: todas las herramientas con botones grandes " +
                "(ajustes, tema, siguiente idioma y más).\n" +
                "• «Cambiar tamaño»: en ⋯, desliza hacia arriba o abajo para hacer el teclado más alto o más bajo.\n" +
                "• Al mover el cursor con la barra espaciadora, el teclado se oscurece y dice «Desliza».", 13f, faded = true,
        )

        subTitle("Versión 7.0")
        text(
            "• Nueva opción en «Barra espaciadora»: deslizar rápido la barra cambia el idioma y se quita el botón 🌐. " +
                "Mantenerla presionada y deslizar sigue moviendo el cursor. Viene apagada.", 13f, faded = true,
        )

        subTitle("Versión 6.8")
        text(
            "• Íconos nuevos en todo el teclado: barra de herramientas, emojis, portapapeles, traductor, " +
                "edición, ortografía y escáner, todos con el mismo estilo de líneas finas.\n" +
                "• Nuevo botón de herramientas: un pequeño panel de control.\n" +
                "• La sugerencia de captura de pantalla tiene prioridad sobre las palabras.", 13f, faded = true,
        )

        subTitle("Versión 6.6")
        text(
            "• Los textos del teclado (paneles, botones y mensajes) cambian a inglés o español cuando " +
                "cambias el idioma con 🌐.\n" +
                "• La sugerencia de capturas pide el permiso desde el teclado si falta.", 13f, faded = true,
        )

        subTitle("Versión 6.5")
        text(
            "• ‹I› Edición de texto · Text editing: mover el cursor, seleccionar, copiar, cortar y pegar.\n" +
                "• Sugerencia de tu última captura de pantalla: un toque y se pega (y se guarda en el portapapeles).\n" +
                "• El portapapeles guarda 2 días; lo fijado 📌 se queda para siempre.", 13f, faded = true,
        )

        subTitle("Versión 6.2")
        text(
            "• Botón abc✓ para revisar la ortografía de todo el texto, error por error.\n" +
                "• Corrector ortográfico de Android ✔: subraya en rojo en las apps que lo usan.", 13f, faded = true,
        )

        subTitle("Versión 6")
        text(
            "• Deslizar para escribir ✍: pasa el dedo por las letras sin soltarlo y se escribe la palabra, " +
                "en español o inglés. Se ve la estela azul del dedo.\n" +
                "• Pone el espacio solo entre palabras deslizadas.\n" +
                "• Arriba salen otras palabras posibles por si no era esa; ⌫ borra la palabra entera.\n" +
                "• Aprende tus palabras y nombres para adivinarlos mejor con el uso.", 13f, faded = true,
        )

        subTitle("Versión 5")
        text(
            "• Escanear texto 📷 dentro del teclado (como Gboard): foto → eliges las palabras con el dedo → " +
                "Insertar, y se escriben al instante.\n" +
                "• Funciona sin internet y en español e inglés.", 13f, faded = true,
        )

        subTitle("Versión 4")
        text(
            "• Dictado por voz 🎤 en español e inglés, con los signos por voz (coma, punto final...).\n" +
                "• Traducción A文 como Gboard: escribes en tu idioma y la app recibe la traducción.\n" +
                "• La traducción funciona dentro del teléfono (Google ML Kit), sin enviar lo que escribes.", 13f, faded = true,
        )

        subTitle("Versión 3")
        text(
            "• Panel de emojis 😊 con más de 1 800 emojis en 9 categorías, Recientes y tonos de piel.\n" +
                "• Portapapeles infinito 📋: guarda todo lo que copias, textos e imágenes, sin límite.\n" +
                "• Pega imágenes directo en las apps que lo permiten (WhatsApp, Gmail, Messages...). " +
                "Si una app no lo permite, la imagen queda copiada para pegarla con mantener presionado → Pegar.\n" +
                "• Fija 📌 lo que usas siempre (direcciones, números de ticket...) para tenerlo arriba.", 13f, faded = true,
        )

        subTitle("Versión 2")
        text(
            "• Sugerencias de palabras en español e inglés al mismo tiempo (casi 100 000 palabras).\n" +
                "• Autocorrector: arregla dedazos y pone los acentos solo (tambien → también, " +
                "manana → mañana, aqui → aquí, dont → don't, im → I'm).\n" +
                "• Entiende errores de sonido del español: nesecito → necesito, haser → hacer, " +
                "vien → bien, y palabras pegadas: porfavor → por favor.\n" +
                "• Borrar justo después de una corrección la deshace.\n" +
                "• Aprende tus palabras (nombres, siglas, palabras del trabajo) y qué palabra sueles " +
                "escribir después de otra, para sugerirla antes de que empieces a escribir.\n" +
                "• Las siglas en MAYÚSCULAS (RUMC, DVSS) y los nombres propios no se corrigen.\n" +
                "• Todo funciona sin internet y lo aprendido se guarda solo en tu teléfono.", 13f, faded = true,
        )

        col = root

        val foot = TextView(this)
        foot.text = "Hecho con ❤ · funciona sin internet"
        foot.textSize = 12f
        foot.setTextColor(SUB)
        foot.gravity = Gravity.CENTER
        root.addView(foot, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
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
        step2.alpha = if (enabled) 1f else 0.45f
        status.text = when {
            selected -> "✅ ¡Todo listo! Ya estás usando Best Keyboard."
            enabled -> "Falta el paso 2: toca el botón y elige Best Keyboard."
            else -> "Sigue estos 2 pasos para empezar a usar el teclado:"
        }
    }

    // ---------------------------------------------------------------- Ayudantes de diseño

    /** Empieza una tarjeta nueva con título e ícono; lo que se agregue después va dentro. */
    private fun card(title: String, icon: Int, glow: Boolean = false) {
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val iv = IconView(this, icon, 8f)
        iv.isClickable = false
        iv.color = CYAN
        iv.bgColor = BG
        head.addView(iv, LinearLayout.LayoutParams(dp(22), dp(22)))
        val t = TextView(this)
        t.text = title.uppercase()
        t.textSize = 12f
        t.letterSpacing = 0.12f
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        t.setTextColor(SUB)
        t.setPadding(dp(10), 0, 0, 0)
        head.addView(t)
        root.addView(head, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28); bottomMargin = dp(10); leftMargin = dp(4) })

        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(10), dp(16), dp(14))
        c.background = GradientDrawable().apply {
            setColor(CARD)
            cornerRadius = dpf(22f)
            if (glow) setStroke(dp(1), 0x804FD1FF.toInt()) else setStroke(dp(1), LINE)
        }
        root.addView(c, LinearLayout.LayoutParams(-1, -2))
        col = c
    }

    private fun subTitle(s: String) {
        val t = TextView(this)
        t.text = s
        t.textSize = 14f
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        t.setTextColor(CYAN)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
    }

    private fun text(s: String, size: Float, bold: Boolean = false, faded: Boolean = false): TextView {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setTextColor(if (faded) SUB else TEXT)
        t.setLineSpacing(dpf(2f), 1f)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        return t
    }

    /** Botón en forma de píldora: [primary] = con degradado; si no, solo el borde. */
    private fun button(label: String, primary: Boolean = false, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.textSize = 15f
        b.gravity = Gravity.CENTER
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
        b.setPadding(dp(16), dp(13), dp(16), dp(13))
        b.isClickable = true
        b.setOnClickListener { onClick() }
        if (primary) {
            b.setTextColor(0xFF0B0D12.toInt())
            b.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x40FFFFFF),
                GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(26f) },
                null,
            )
        } else {
            b.setTextColor(CYAN)
            b.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x334FD1FF),
                GradientDrawable().apply { setColor(CARD2); cornerRadius = dpf(26f) },
                null,
            )
        }
        col.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        return b
    }

    /** Fila con un texto y un interruptor moderno a la derecha (toda la fila se puede tocar). */
    private fun switch(label: String, key: String, def: Boolean, help: String? = null) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTextColor(TEXT)
        texts.addView(t)
        if (help != null) {
            val h = TextView(this)
            h.text = help
            h.textSize = 12f
            h.setTextColor(SUB)
            h.setPadding(0, dp(3), 0, 0)
            texts.addView(h)
        }
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(12) })
        val tg = Toggle(this)
        tg.on = prefs.getBoolean(key, def)
        row.addView(tg, LinearLayout.LayoutParams(dp(48), dp(28)))
        row.setOnClickListener {
            tg.on = !tg.on
            prefs.edit().putBoolean(key, tg.on).apply()
        }
        if (col.childCount > 0) divider()
        col.addView(row, LinearLayout.LayoutParams(-1, -2))
    }

    private fun divider() {
        val v = View(this)
        v.setBackgroundColor(LINE)
        col.addView(v, LinearLayout.LayoutParams(-1, dp(1)))
    }

    /** Opciones en píldoras, una al lado de la otra (la elegida se ilumina). */
    private fun segmented(label: String, key: String, def: String, options: List<Pair<String, String>>) {
        if (col.childCount > 0) divider()
        val t = TextView(this)
        t.text = label
        t.textSize = 15f
        t.setTextColor(TEXT)
        col.addView(t, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(4), dp(4), dp(4), dp(4))
        bar.background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(24f) }
        val views = ArrayList<Pair<String, TextView>>()
        fun paint(sel: String?) {
            for ((v, tv) in views) {
                val on = v == sel
                tv.setTextColor(if (on) 0xFF0B0D12.toInt() else SUB)
                tv.background = if (on) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(20f) } else null
            }
        }
        for ((value, name) in options) {
            val o = TextView(this)
            o.text = name
            o.textSize = 14f
            o.gravity = Gravity.CENTER
            o.setPadding(dp(6), dp(9), dp(6), dp(9))
            o.setOnClickListener {
                prefs.edit().putString(key, value).apply()
                paint(value)
            }
            views.add(value to o)
            bar.addView(o, LinearLayout.LayoutParams(0, -2, 1f))
        }
        paint(prefs.getString(key, def))
        col.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(12) })
    }

    /** Interruptor futurista: píldora con degradado cuando está encendido. */
    @SuppressLint("ViewConstructor")
    private class Toggle(context: Context) : View(context) {
        var on = false
            set(v) { field = v; anim = if (v) 1f else 0f; invalidate() }
        private var anim = 0f
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            r.set(0f, 0f, w, h)
            if (on) {
                p.shader = LinearGradient(0f, 0f, w, 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            } else {
                p.shader = null
                p.color = LINE
            }
            c.drawRoundRect(r, h / 2, h / 2, p)
            p.shader = null
            val k = h / 2 - h * 0.12f
            val cx = if (on) w - h / 2 else h / 2
            p.color = if (on) 0xFFFFFFFF.toInt() else SUB
            c.drawCircle(cx, h / 2, k, p)
        }
    }
}
