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
import java.util.Locale

/** Pantalla de la app: activar el teclado, probarlo y cambiar ajustes (en español o inglés). */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var step1: TextView
    private lateinit var step2: TextView
    private lateinit var col: LinearLayout
    private lateinit var learnedInfo: TextView
    private lateinit var micBtn: TextView
    private lateinit var photosBtn: TextView
    private lateinit var camBtn: TextView

    /** Idioma de esta pantalla: el del teléfono, o el que elijas. */
    private var en = false
    private fun tr(es: String, en: String) = if (this.en) en else es

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
        en = when (prefs.getString(Prefs.APP_LANG, "auto")) {
            "es" -> false
            "en" -> true
            else -> Locale.getDefault().language != "es"
        }
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

    private fun toast(es: String, en: String) = Toast.makeText(this, tr(es, en), Toast.LENGTH_LONG).show()

    private fun askPhotos() {
        if (Screenshots.hasPermission(this)) { refreshMic(); return }
        if (Screenshots.hasPartial(this)) {
            // Dio "solo algunas fotos": hay que cambiarlo a "Permitir todo" en los ajustes de la app
            toast("Toca Permisos → Fotos y videos → \"Permitir todo\".", "Tap Permissions → Photos and videos → \"Allow all\".")
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
        refreshMic()
        when (requestCode) {
            REQ_PHOTOS -> if (Screenshots.hasPermission(this)) toast("¡Listo! Toma una captura y abre el teclado.", "Done! Take a screenshot and open the keyboard.")
                else toast("Sin ese permiso no puedo sugerir tus capturas.", "Without that permission I can't suggest your screenshots.")
            REQ_CAMERA -> if (cameraGranted()) toast("¡Listo! Vuelve a donde escribías y toca el escáner.", "Done! Go back to where you were typing and tap the scanner.")
                else toast("Sin el permiso de la cámara no se puede escanear.", "Without the camera permission you can't scan.")
            REQ_MIC -> if (micGranted()) toast("¡Listo! Vuelve a donde escribías y toca el micrófono para dictar.", "Done! Go back to where you were typing and tap the mic to dictate.")
                else toast("Sin el permiso del micrófono no se puede dictar.", "Without the microphone permission you can't dictate.")
        }
    }

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
        toast("Búscalo en Ajustes → Sistema → Idiomas → Corrector ortográfico.", "Find it in Settings → System → Languages → Spell checker.")
    }

    private fun refreshMic() {
        if (::micBtn.isInitialized) micBtn.text = if (micGranted()) tr("✓  Micrófono permitido", "✓  Microphone allowed") else tr("Permitir el micrófono", "Allow the microphone")
        if (::photosBtn.isInitialized) photosBtn.text = if (Screenshots.hasPermission(this)) tr("✓  Capturas de pantalla permitidas", "✓  Screenshots allowed")
            else tr("Permitir ver mis capturas", "Allow seeing my screenshots")
        if (::camBtn.isInitialized) camBtn.text = if (cameraGranted()) tr("✓  Cámara permitida", "✓  Camera allowed") else tr("Permitir la cámara", "Allow the camera")
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshMic()
        WordStore.load(this) { refreshLearned() }
    }

    private fun refreshLearned() {
        if (!::learnedInfo.isInitialized) return
        learnedInfo.text = tr("Palabras tuyas aprendidas: ", "Your learned words: ") + WordStore.learnedCount()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dpf(v: Float) = v * resources.displayMetrics.density

    /** La columna principal; [col] es la tarjeta donde se van agregando las opciones. */
    private lateinit var root: LinearLayout

    private fun confirm(titleEs: String, titleEn: String, msgEs: String, msgEn: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(tr(titleEs, titleEn))
            .setMessage(tr(msgEs, msgEn))
            .setPositiveButton(tr("Borrar", "Delete")) { _, _ -> action() }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }

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

        // ---- Idioma de la app
        card(tr("Idioma", "Language"), Icons.GLOBE)
        segmented(tr("Idioma de esta pantalla", "Language of this screen"), Prefs.APP_LANG, "auto",
            listOf("auto" to tr("Del teléfono", "Phone's"), "es" to "Español", "en" to "English")) { recreate() }

        // ---- Configuración inicial (tarjeta con borde de color)
        card(tr("Configuración inicial", "Setup"), Icons.SETTINGS, glow = true)
        status = text("", 15f)
        step1 = button(tr("1. Activar Best Keyboard", "1. Enable Best Keyboard"), primary = true) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        step2 = button(tr("2. Elegir Best Keyboard", "2. Choose Best Keyboard"), primary = true) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        text(tr(
            "Android muestra un aviso de que el teclado \"podría recopilar lo que escribes\". Sale con TODOS los teclados. " +
                "Lo que escribes no sale de tu teléfono, salvo cuando tú tocas «Corregir gramática» o «Más formal».",
            "Android warns that the keyboard \"may collect what you type\". It shows for ALL keyboards. " +
                "What you type stays on your phone, except when you tap «Fix grammar» or «More formal».",
        ), 12f, faded = true)
        col.addView(EditText(this).apply {
            hint = tr("Toca aquí para probar el teclado", "Tap here to try the keyboard")
            minLines = 2
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // ---- Apariencia
        card(tr("Apariencia", "Appearance"), Icons.THEME)
        segmented(tr("Tema", "Theme"), Prefs.THEME, "auto", listOf("auto" to "Auto", "light" to tr("Claro", "Light"), "dark" to tr("Oscuro", "Dark")))
        segmented(tr("Altura de las teclas", "Key height"), Prefs.HEIGHT, "normal",
            listOf("low" to tr("Baja", "Low"), "normal" to "Normal", "high" to tr("Alta", "High")))
        switch(tr("Fila de números arriba", "Number row on top"), Prefs.NUMBER_ROW, true)
        switch(tr("Letra grande al tocar", "Big letter on tap"), Prefs.PREVIEW, true)
        text(tr("Para cambiar el tamaño: toca ⋯ arriba del teclado → «Cambiar tamaño».", "To resize: tap ⋯ above the keyboard → «Resize»."), 12f, faded = true)

        // ---- Tacto
        card(tr("Al tocar una tecla", "When tapping a key"), Icons.EDIT)
        switch(tr("Vibración", "Vibration"), Prefs.VIBRATE, true)
        segmented(tr("Fuerza de la vibración", "Vibration strength"), Prefs.VIB_STRENGTH, "medium",
            listOf("soft" to tr("Suave", "Soft"), "medium" to tr("Media", "Medium"), "strong" to tr("Fuerte", "Strong")))
        switch(tr("Sonido", "Sound"), Prefs.SOUND, false)

        // ---- Escritura
        card(tr("Escritura", "Typing"), Icons.SPELL)
        switch(tr("Mayúscula automática al empezar una oración", "Auto-capitalize sentences"), Prefs.AUTOCAP, true)
        switch(tr("Doble espacio pone un punto", "Double space adds a period"), Prefs.DOUBLE_SPACE, true)
        switch(tr("Mayúsculas en lo seleccionado", "Uppercase the selection"), Prefs.SEL_CAPS, false,
            tr("Selecciona un texto y toca ⇧: pasa a MAYÚSCULAS. Otra vez: minúsculas. Otra: Tipo Título.",
                "Select text and tap ⇧: it becomes UPPERCASE. Again: lowercase. Again: Title Case."))

        // ---- Barra espaciadora
        card(tr("Barra espaciadora", "Space bar"), Icons.GLOBE)
        switch(tr("Deslizar la barra cambia el idioma", "Swipe the space bar to change language"), Prefs.SPACE_LANG, false,
            tr("Se quita el botón 🌐. Desliza rápido para cambiar de idioma; mantén presionado y desliza para mover el cursor.",
                "The 🌐 key goes away. Swipe fast to change language; press, hold and slide to move the cursor."))

        // ---- Sugerencias
        card(tr("Sugerencias y autocorrector", "Suggestions and autocorrect"), Icons.TRANSLATE)
        switch(tr("Mostrar sugerencias de palabras", "Show word suggestions"), Prefs.SUGGEST, true)
        switch(tr("Deslizar el dedo para escribir", "Swipe typing"), Prefs.SWIPE, true)
        switch(tr("Autocorrector", "Autocorrect"), Prefs.AUTOCORRECT, true,
            tr("Corrige al tocar espacio. Toca ⌫ justo después para deshacerlo.", "Fixes words when you tap space. Tap ⌫ right after to undo."))
        switch(tr("Aprender mis palabras", "Learn my words"), Prefs.LEARN, true, tr("Nombres, siglas y palabras del trabajo.", "Names, acronyms and work words."))
        learnedInfo = text("", 12f, faded = true)
        button(tr("Borrar las palabras aprendidas", "Delete learned words")) {
            confirm("¿Borrar lo aprendido?", "Delete what was learned?",
                "El teclado olvidará tus palabras y las que sueles escribir juntas.", "The keyboard will forget your words and the ones you usually type together.") {
                WordStore.clearLearned(this)
                refreshLearned()
            }
        }

        // ---- Ortografía
        card(tr("Revisar ortografía", "Spell check"), Icons.SPELL)
        text(tr(
            "Toca el botón de ortografía: se selecciona TODO tu texto y ves los errores en rojo; toca uno para corregirlo " +
                "o «Corregir todo». Si antes seleccionas solo una parte, se revisa solo esa parte.\n\n" +
                "Después puedes tocar «Corregir gramática» (gratis, con internet) o «Más formal».",
            "Tap the spell check button: ALL your text gets selected and mistakes show in red; tap one to fix it " +
                "or «Fix all». If you select only a part first, only that part is checked.\n\n" +
                "Then you can tap «Fix grammar» (free, uses internet) or «More formal».",
        ), 13f, faded = true)
        switch(tr("Corrector ortográfico de Android", "Android spell checker"), Prefs.SPELL, true,
            tr("Subraya en rojo en las apps que lo usan.", "Underlines in red in apps that use it."))
        button(tr("Abrir los ajustes del corrector de Android", "Open Android spell checker settings")) { openSpellSettings() }

        // ---- IA para escribir
        card(tr("IA para escribir", "AI writing"), Icons.HACKER)
        text(tr(
            "«Más formal» usa la IA de Google. Si tu teléfono no la trae por dentro, usa Google Gemini por internet con " +
                "una clave GRATIS:\n" +
                "1. Toca «Conseguir mi clave gratis» y entra con tu cuenta de Google.\n" +
                "2. Toca «Create API key» (Crear clave) y cópiala.\n" +
                "3. Pégala aquí abajo. ¡Listo!",
            "«More formal» uses Google's AI. If your phone doesn't have it built in, it uses Google Gemini online with " +
                "a FREE key:\n" +
                "1. Tap «Get my free key» and sign in with your Google account.\n" +
                "2. Tap «Create API key» and copy it.\n" +
                "3. Paste it below. Done!",
        ), 13f, faded = true)
        button(tr("Conseguir mi clave gratis", "Get my free key"), primary = true) {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/apikey"))) } catch (_: Exception) { }
        }
        val keyBox = EditText(this).apply {
            hint = tr("Pega aquí tu clave (empieza con AIza…)", "Paste your key here (starts with AIza…)")
            setText(prefs.getString(Prefs.GEMINI_KEY, ""))
            isSingleLine = true
            textSize = 14f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        col.addView(keyBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        button(tr("Guardar clave", "Save key")) {
            prefs.edit().putString(Prefs.GEMINI_KEY, keyBox.text.toString().trim()).apply()
            toast("Clave guardada. Ya puedes usar «Más formal».", "Key saved. You can now use «More formal».")
        }
        text(tr("La clave se guarda solo en tu teléfono. El texto se envía a Google solo cuando tocas «Más formal».",
            "The key is saved only on your phone. Text is sent to Google only when you tap «More formal»."), 12f, faded = true)

        // ---- Permisos y herramientas
        card(tr("Voz, cámara y capturas", "Voice, camera and screenshots"), Icons.MIC)
        micBtn = button("") { askMic() }
        camBtn = button("") { askCamera() }
        photosBtn = button("") { askPhotos() }
        switch(tr("Sugerir mi última captura de pantalla", "Suggest my latest screenshot"), Prefs.SHOTS, true)
        text(tr("Dictado: di \"coma\", \"punto final\", \"nueva línea\"… Traducción y escáner funcionan dentro del teléfono.",
            "Dictation: say \"comma\", \"period\", \"new line\"… Translation and scanner work on the phone."), 12f, faded = true)

        // ---- Portapapeles
        card(tr("Portapapeles", "Clipboard"), Icons.CLIP)
        switch(tr("Guardar todo lo que copio", "Save everything I copy"), Prefs.CLIP_HISTORY, true,
            tr("Dura 48 horas; lo fijado se queda para siempre. Las contraseñas no se guardan.", "Lasts 48 hours; pinned items stay forever. Passwords aren't saved."))
        button(tr("Borrar el historial (menos lo fijado)", "Clear history (except pinned)")) {
            confirm("¿Borrar el portapapeles?", "Clear the clipboard?",
                "Se borrará todo lo copiado, menos lo que fijaste.", "Everything copied will be deleted, except pinned items.") {
                ClipStore.load(this) { ClipStore.clearUnpinned() }
            }
        }

        // ---- Trucos
        card(tr("Trucos", "Tips"), Icons.IDEA)
        text(tr(
            "•  Mantén una letra para los acentos (á, é, ñ…) y el punto para ¿ ? ¡ !\n" +
                "•  ⇧ dos veces = TODO EN MAYÚSCULAS.\n" +
                "•  Desliza el dedo por las letras para escribir una palabra entera.\n" +
                "•  Mantén la coma para los emojis.\n" +
                "•  ⋯ abre «Más opciones»; con el lápiz ✎ acomodas todos los botones.\n" +
                "•  El hacker cambia a teclado de computadora (Ctrl, Alt, Esc, Tab, F1–F12).\n" +
                "•  Mantén 🌐 para cambiar a otro teclado.",
            "•  Hold a letter for accents (á, é, ñ…) and the period for ¿ ? ¡ !\n" +
                "•  ⇧ twice = ALL CAPS.\n" +
                "•  Slide your finger over the letters to type a whole word.\n" +
                "•  Hold the comma for emojis.\n" +
                "•  ⋯ opens «More options»; the pencil ✎ lets you arrange every button.\n" +
                "•  The hacker switches to a PC keyboard (Ctrl, Alt, Esc, Tab, F1–F12).\n" +
                "•  Hold 🌐 to switch to another keyboard.",
        ), 13f)

        // ---- Novedades (lo más nuevo visible, lo demás escondido)
        card(tr("Novedades", "What's new"), Icons.RECENT)
        val notes = releaseNotes()
        subTitle(tr("Versión ", "Version ") + notes[0].first)
        text(notes[0].second, 13f)
        val older = LinearLayout(this)
        older.orientation = LinearLayout.VERTICAL
        older.visibility = View.GONE
        val toggle = button(tr("Ver versiones anteriores", "See older versions")) {}
        toggle.setOnClickListener {
            val show = older.visibility != View.VISIBLE
            older.visibility = if (show) View.VISIBLE else View.GONE
            toggle.text = if (show) tr("Ocultar versiones anteriores", "Hide older versions") else tr("Ver versiones anteriores", "See older versions")
        }
        col.addView(older)
        col = older
        for ((v, body) in notes.drop(1)) {
            subTitle(tr("Versión ", "Version ") + v)
            text(body, 13f, faded = true)
        }
        col = root

        val foot = TextView(this)
        foot.text = tr("Hecho con ❤ · Best Keyboard", "Made with ❤ · Best Keyboard")
        foot.textSize = 12f
        foot.setTextColor(SUB)
        foot.gravity = Gravity.CENTER
        root.addView(foot, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        return sv
    }

    /** Novedades de cada versión (la primera es la más nueva). */
    private fun releaseNotes(): List<Pair<String, String>> = listOf(
        "7.7" to tr(
            "•  Ajustes en español o inglés (según tu teléfono, o elígelo arriba).\n" +
                "•  El botón de ortografía selecciona todo el texto solo.\n" +
                "•  «Corregir gramática» gratis (LanguageTool) y «Más formal» con Google Gemini.",
            "•  Settings in Spanish or English (from your phone, or choose it on top).\n" +
                "•  The spell check button selects all the text by itself.\n" +
                "•  Free «Fix grammar» (LanguageTool) and «More formal» with Google Gemini.",
        ),
        "7.6" to tr(
            "•  Pantalla de ajustes nueva: oscura, minimalista y futurista.\n•  Mayúsculas en lo seleccionado.\n•  Reescribir formal con la IA del teléfono.",
            "•  New settings screen: dark, minimal and futuristic.\n•  Uppercase the selection.\n•  Formal rewrite with the phone's AI.",
        ),
        "7.5" to tr(
            "•  Teclado de computadora (hacker): Esc, Tab, Ctrl, Alt, flechas y F1–F12.\n•  Ortografía de solo lo seleccionado.",
            "•  PC keyboard (hacker): Esc, Tab, Ctrl, Alt, arrows and F1–F12.\n•  Spell check of just the selection.",
        ),
        "7.4" to tr("•  Ortografía como Google Docs: todo el texto con los errores en rojo.", "•  Spell check like Google Docs: all your text with mistakes in red."),
        "7.3" to tr("•  Todos los botones de la barra se pueden mover.", "•  Every toolbar button can be moved."),
        "7.2" to tr("•  Acomoda la barra de arriba con el lápiz ✎.", "•  Arrange the top bar with the pencil ✎."),
        "7.1" to tr("•  «Más opciones» (⋯) y «Cambiar tamaño».", "•  «More options» (⋯) and «Resize»."),
        "7.0" to tr("•  Deslizar la barra espaciadora para cambiar de idioma.", "•  Swipe the space bar to change language."),
        "6.8" to tr("•  Íconos nuevos en todo el teclado.", "•  New icons across the keyboard."),
        "6.6" to tr("•  Los textos del teclado cambian de idioma con 🌐.", "•  Keyboard texts follow the 🌐 language."),
        "6.5" to tr("•  Edición de texto, sugerencia de capturas, portapapeles de 48 horas.", "•  Text editing, screenshot suggestion, 48-hour clipboard."),
        "6" to tr("•  Deslizar para escribir.", "•  Swipe typing."),
        "5" to tr("•  Escanear texto con la cámara.", "•  Scan text with the camera."),
        "4" to tr("•  Dictado por voz y traducción.", "•  Voice typing and translation."),
        "3" to tr("•  Emojis y portapapeles con imágenes.", "•  Emojis and clipboard with images."),
        "2" to tr("•  Sugerencias y autocorrector en español e inglés.", "•  Suggestions and autocorrect in Spanish and English."),
    )

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val selected = current.startsWith("$packageName/")
        step1.text = if (enabled) tr("✓ 1. Best Keyboard activado", "✓ 1. Best Keyboard enabled") else tr("1. Activar Best Keyboard", "1. Enable Best Keyboard")
        step2.text = if (selected) tr("✓ 2. Best Keyboard elegido", "✓ 2. Best Keyboard chosen") else tr("2. Elegir Best Keyboard", "2. Choose Best Keyboard")
        step2.isEnabled = enabled
        step2.alpha = if (enabled) 1f else 0.45f
        status.text = when {
            selected -> tr("✅ ¡Todo listo! Ya estás usando Best Keyboard.", "✅ All set! You're using Best Keyboard.")
            enabled -> tr("Falta el paso 2: toca el botón y elige Best Keyboard.", "Step 2 left: tap the button and choose Best Keyboard.")
            else -> tr("Sigue estos 2 pasos para empezar a usar el teclado:", "Follow these 2 steps to start using the keyboard:")
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
    private fun segmented(label: String, key: String, def: String, options: List<Pair<String, String>>, onChange: (() -> Unit)? = null) {
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
                val changed = prefs.getString(key, def) != value
                prefs.edit().putString(key, value).apply()
                paint(value)
                if (changed) onChange?.invoke()
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
