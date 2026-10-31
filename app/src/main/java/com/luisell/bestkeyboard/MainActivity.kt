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
class MainActivity : UiActivity() {

    private lateinit var status: TextView
    private lateinit var step1: TextView
    private lateinit var step2: TextView
    private lateinit var learnedInfo: TextView
    private lateinit var micBtn: TextView
    private lateinit var photosBtn: TextView
    private lateinit var camBtn: TextView
    private lateinit var smsBtn: TextView
    private lateinit var vaultList: LinearLayout

    /** Idioma de esta pantalla: el del teléfono, o el que elijas. */

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
        /** Abrir la app directo en la tarjeta "IA para escribir". */
        const val EXTRA_SHOW_AI = "ver_ia"
        private const val REQ_PHOTOS = 9
        private const val REQ_MIC = 7
        private const val REQ_CAMERA = 8
        private const val REQ_SMS = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Diseño oscuro y moderno, sin la barra de título vieja
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
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
        if (i?.getBooleanExtra(EXTRA_SHOW_AI, false) == true) {
            // Bajar hasta la tarjeta de la IA para pegar la clave
            scrollView.postDelayed({ aiCard?.let { scrollView.smoothScrollTo(0, maxOf(0, it.top - dp(60))) } }, 250)
        }
    }


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

    private fun smsGranted() = QuickPaste.canReadSms(this)

    private fun askSms() {
        if (smsGranted()) { refreshMic(); return }
        requestPermissions(arrayOf(android.Manifest.permission.READ_SMS), REQ_SMS)
    }

    /** Android a veces bloquea este permiso en apps instaladas desde un APK: aquí se explica cómo permitirlo. */
    private fun smsBlockedHelp() {
        AlertDialog.Builder(this)
            .setTitle(tr("Permitir leer los códigos", "Allow reading codes"))
            .setMessage(tr(
                "Si Android no te dejó dar el permiso:\n\n" +
                    "1. Toca «Abrir información de la app».\n" +
                    "2. Toca los 3 puntitos ⋮ arriba a la derecha → «Permitir ajustes restringidos».\n" +
                    "3. Entra a Permisos → SMS → Permitir.\n" +
                    "4. Vuelve aquí.",
                "If Android didn't let you give the permission:\n\n" +
                    "1. Tap «Open app info».\n" +
                    "2. Tap the 3 dots ⋮ at the top right → «Allow restricted settings».\n" +
                    "3. Go to Permissions → SMS → Allow.\n" +
                    "4. Come back here.",
            ))
            .setPositiveButton(tr("Abrir información de la app", "Open app info")) { _, _ ->
                try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) } catch (_: Exception) { }
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
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
            REQ_SMS -> if (smsGranted()) toast("¡Listo! Cuando te llegue un código por SMS, aparece arriba del teclado.", "Done! When a code arrives by SMS, it shows above the keyboard.")
                else smsBlockedHelp()
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
        if (::smsBtn.isInitialized) smsBtn.text = if (smsGranted()) tr("✓  Códigos de SMS permitidos", "✓  SMS codes allowed") else tr("Permitir leer códigos de SMS", "Allow reading SMS codes")
        if (::camBtn.isInitialized) camBtn.text = if (cameraGranted()) tr("✓  Cámara permitida", "✓  Camera allowed") else tr("Permitir la cámara", "Allow the camera")
    }

    override fun onResume() {
        super.onResume()
        refreshVault()
        refreshStatus()
        refreshMic()
        WordStore.load(this) { refreshLearned() }
    }

    /** Dibuja la lista de "Mis datos" (las contraseñas se ven con puntitos). */
    private fun refreshVault() {
        if (!::vaultList.isInitialized) return
        try { VaultStore.load(this) } catch (_: Exception) { }
        vaultList.removeAllViews()
        if (VaultStore.items.isEmpty()) {
            val t = TextView(this)
            t.text = tr("Todavía no guardaste nada.", "Nothing saved yet.")
            t.textSize = 13f
            t.setTextColor(SUB)
            t.setPadding(0, dp(12), 0, 0)
            vaultList.addView(t)
            return
        }
        for (e in VaultStore.items.sortedBy { it.kind }) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(10), 0, dp(10))
            val iv = IconView(this, when (e.kind) { VaultStore.EMAIL -> Icons.MAIL; VaultStore.PASSWORD -> Icons.LOCK; else -> Icons.CLIP }, 8f)
            iv.isClickable = false
            iv.color = CYAN
            iv.bgColor = CARD
            row.addView(iv, LinearLayout.LayoutParams(dp(26), dp(26)))
            val texts = LinearLayout(this)
            texts.orientation = LinearLayout.VERTICAL
            texts.setPadding(dp(12), 0, dp(8), 0)
            val a = TextView(this)
            a.text = if (e.kind == VaultStore.PASSWORD) e.label.ifBlank { tr("Contraseña", "Password") } else e.value
            a.textSize = 15f
            a.setTextColor(TEXT)
            a.maxLines = 1
            a.ellipsize = android.text.TextUtils.TruncateAt.END
            texts.addView(a)
            val b = TextView(this)
            val extra = ArrayList<String>()
            if (e.kind == VaultStore.PASSWORD) extra += "••••••••" else if (e.label.isNotBlank() && e.kind == VaultStore.TEXT) extra += e.label
            if (e.shortcut.isNotBlank()) extra += tr("atajo: ", "shortcut: ") + e.shortcut
            b.text = extra.joinToString("  ·  ")
            b.textSize = 12f
            b.setTextColor(SUB)
            if (extra.isNotEmpty()) texts.addView(b)
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            val del = IconView(this, Icons.CLOSE, 6f)
            del.color = SUB
            del.setOnClickListener {
                confirm("¿Borrar este dato?", "Delete this item?", "Se borrará de tu teléfono.", "It will be deleted from your phone.") {
                    VaultStore.remove(this, e.id)
                    refreshVault()
                }
            }
            row.addView(del, LinearLayout.LayoutParams(dp(36), dp(36)))
            if (vaultList.childCount > 0) vaultList.addView(View(this).apply { setBackgroundColor(LINE) }, LinearLayout.LayoutParams(-1, dp(1)))
            vaultList.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
    }

    /** Ventanita para agregar un correo, una contraseña o un texto con atajo. */
    private fun addVaultDialog() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), dp(4))
        var kind = VaultStore.EMAIL
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        tabs.background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(22f) }
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4))
        fun field(hint: String, secret: Boolean = false) = EditText(this).apply {
            this.hint = hint
            isSingleLine = true
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
            inputType = if (secret) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            else android.text.InputType.TYPE_CLASS_TEXT
        }
        val name = field(tr("Nombre (ej.: Banco, Trabajo)", "Name (e.g.: Bank, Work)"))
        val value = field(tr("Correo", "Email"))
        val shortcut = field(tr("Atajo opcional (ej.: lp)", "Optional shortcut (e.g.: lp)"))
        val opts = listOf(
            VaultStore.EMAIL to tr("Correo", "Email"),
            VaultStore.PASSWORD to tr("Contraseña", "Password"),
            VaultStore.TEXT to tr("Texto / atajo", "Text / shortcut"),
        )
        val tabViews = ArrayList<TextView>()
        var eyeView: IconView? = null
        var shown = false
        fun paint() {
            for ((i, tv) in tabViews.withIndex()) {
                val on = opts[i].first == kind
                tv.setTextColor(if (on) 0xFF0B0D12.toInt() else SUB)
                tv.background = if (on) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(18f) } else null
            }
            value.hint = when (kind) {
                VaultStore.EMAIL -> tr("Correo", "Email")
                VaultStore.PASSWORD -> tr("Contraseña", "Password")
                else -> tr("Texto (dirección, teléfono, firma…)", "Text (address, phone, signature…)")
            }
            value.inputType = android.text.InputType.TYPE_CLASS_TEXT or when (kind) {
                VaultStore.PASSWORD -> android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                VaultStore.EMAIL -> android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                else -> 0
            }
            shortcut.visibility = if (kind == VaultStore.PASSWORD) View.GONE else View.VISIBLE
            shown = false
            eyeView?.let { e ->
                e.visibility = if (kind == VaultStore.PASSWORD) View.VISIBLE else View.GONE
                e.kind = Icons.EYE_OFF
                e.color = SUB
            }
            value.setPadding(dp(14), dp(12), if (kind == VaultStore.PASSWORD) dp(50) else dp(14), dp(12))
        }
        for ((k, label) in opts) {
            val t = TextView(this)
            t.text = label
            t.textSize = 13f
            t.gravity = Gravity.CENTER
            t.setPadding(dp(4), dp(9), dp(4), dp(9))
            t.setOnClickListener { kind = k; paint() }
            tabViews.add(t)
            tabs.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        box.addView(tabs, LinearLayout.LayoutParams(-1, -2))
        // La caja del valor con el ojito a la derecha (solo para contraseñas)
        val valueBox = android.widget.FrameLayout(this)
        valueBox.addView(value, android.widget.FrameLayout.LayoutParams(-1, -2))
        val eye = IconView(this, Icons.EYE_OFF, 8f)
        eye.color = SUB
        eye.bgColor = BG
        eye.setOnClickListener {
            shown = !shown
            val pos = value.selectionEnd
            value.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                if (shown) android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            value.setSelection(pos.coerceIn(0, value.text.length))
            eye.kind = if (shown) Icons.EYE else Icons.EYE_OFF
            eye.color = if (shown) CYAN else SUB
        }
        valueBox.addView(eye, android.widget.FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END or Gravity.CENTER_VERTICAL))
        box.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        box.addView(valueBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        box.addView(shortcut, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        eyeView = eye
        paint()
        AlertDialog.Builder(this)
            .setTitle(tr("Agregar a Mis datos", "Add to My data"))
            .setView(box)
            .setPositiveButton(tr("Guardar", "Save")) { _, _ ->
                val v = value.text.toString()
                if (v.isBlank()) return@setPositiveButton
                try {
                    VaultStore.add(this, kind, name.text.toString(), if (kind == VaultStore.PASSWORD) v else v.trim(),
                        if (kind == VaultStore.PASSWORD) "" else shortcut.text.toString())
                    toast("Guardado.", "Saved.")
                } catch (_: Exception) {
                    toast("No se pudo guardar.", "Couldn't save.")
                }
                refreshVault()
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }

    private fun refreshLearned() {
        if (!::learnedInfo.isInitialized) return
        learnedInfo.text = tr("Palabras tuyas aprendidas: ", "Your learned words: ") + WordStore.learnedCount()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }


    /** La columna principal; [col] es la tarjeta donde se van agregando las opciones. */
    private lateinit var scrollView: ScrollView
    private var aiCard: View? = null

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
        scrollView = sv
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

        // ---- Estilo (pantalla aparte con el teclado de muestra)
        card(tr("Estilo del teclado", "Keyboard style"), Icons.BRUSH, glow = true)
        text(tr("Temas, colores, forma y tamaño de las teclas, animaciones, vibración y sonido. Con un teclado de muestra que cambia al instante.",
            "Themes, colors, key shape and size, animations, vibration and sound. With a sample keyboard that changes instantly."), 13f, faded = true)
        button(tr("Abrir Estilo", "Open Style"), primary = true) {
            startActivity(Intent(this, StyleActivity::class.java))
        }
        text(tr("Para hacer el teclado más alto o más bajo: toca ⋯ arriba del teclado → «Tamaño».", "To resize: tap ⋯ above the keyboard → «Resize»."), 12f, faded = true)

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
        aiCard = col
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

        // ---- Mis datos (como Samsung Pass)
        card(tr("Mis datos: correos, contraseñas y atajos", "My data: emails, passwords and shortcuts"), Icons.MAIL)
        text(tr(
            "Guarda tus correos, contraseñas y textos que usas mucho. Al escribir 2–3 letras (o su atajo), el teclado " +
                "te los sugiere arriba. Las contraseñas solo salen en campos de contraseña y piden tu huella o PIN. " +
                "Todo se guarda cifrado y solo en tu teléfono.",
            "Save your emails, passwords and texts you use a lot. Type 2–3 letters (or the shortcut) and the keyboard " +
                "suggests them on top. Passwords only show in password fields and ask for your fingerprint or PIN. " +
                "Everything is encrypted and stays only on your phone.",
        ), 13f, faded = true)
        switch(tr("Sugerir mis datos al escribir", "Suggest my data while typing"), Prefs.VAULT_SUGGEST, true)
        switch(tr("Guardar los correos que escribo", "Save the emails I type"), Prefs.VAULT_LEARN, true,
            tr("Cuando escribes tu correo en un campo de correo, se guarda solo.", "When you type your email in an email field, it's saved automatically."))
        vaultList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(vaultList, LinearLayout.LayoutParams(-1, -2))
        button(tr("+  Agregar", "+  Add"), primary = true) { addVaultDialog() }

        // ---- Sugerencias rápidas: copiado y códigos
        card(tr("Copiado y códigos", "Copied text and codes"), Icons.LOCK)
        switch(tr("Sugerir lo que acabo de copiar", "Suggest what I just copied"), Prefs.CLIP_SUGGEST, true,
            tr("Arriba del teclado sale «Pegar: …» por 3 minutos. Un toque y se escribe.", "«Paste: …» shows above the keyboard for 3 minutes. One tap types it."))
        switch(tr("Sugerir códigos de verificación", "Suggest verification codes"), Prefs.OTP_SUGGEST, true,
            tr("Cuando te llega un SMS con un código (o lo copias), sale «Código: 123456».", "When an SMS with a code arrives (or you copy one), «Code: 123456» shows up."))
        smsBtn = button("") { askSms() }
        text(tr("Los SMS se leen solo dentro de tu teléfono, únicamente para encontrar el código. Nada se envía.",
            "SMS are read only on your phone, just to find the code. Nothing is sent."), 12f, faded = true)

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
                "•  </> Modo código: teclado para programar y la IA te explica, escribe y arregla código.\n" +
                "•  Mantén 🌐 para cambiar a otro teclado.",
            "•  Hold a letter for accents (á, é, ñ…) and the period for ¿ ? ¡ !\n" +
                "•  ⇧ twice = ALL CAPS.\n" +
                "•  Slide your finger over the letters to type a whole word.\n" +
                "•  Hold the comma for emojis.\n" +
                "•  ⋯ opens «More options»; the pencil ✎ lets you arrange every button.\n" +
                "•  The hacker switches to a PC keyboard (Ctrl, Alt, Esc, Tab, F1–F12).\n" +
                "•  </> Code mode: a keyboard for coding, and the AI explains, writes and fixes code.\n" +
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
        "8.8" to tr(
            "•  «Más opciones» deja solo «Estilo» (lo demás está adentro).\n" +
                "•  El tema Hacker en 12 colores, incluido arcoíris 🌈 (en Estilo → Colores).",
            "•  «More options» keeps only «Style» (everything else is inside).\n" +
                "•  The Hacker theme in 12 colors, including rainbow 🌈 (in Style → Colors).",
        ),
        "8.7" to tr(
            "•  Pantalla «Estilo» con teclado de muestra que cambia al instante.\n" +
                "•  20 temas (Grafito, Café, Fuego, Oro, Cyberpunk, Cielo, Menta, Lavanda, Durazno…) y 16 colores de acento.\n" +
                "•  Nuevas formas (Círculo, Solo borde, Sin fondo) y efectos (Zoom, Saltar, Chispas, Burbuja).\n" +
                "•  Tema Hacker nuevo: teclas de vidrio verde sobre una lluvia de código que cae.",
            "•  «Style» screen with a sample keyboard that changes instantly.\n" +
                "•  20 themes (Graphite, Coffee, Fire, Gold, Cyberpunk, Sky, Mint, Lavender, Peach…) and 16 accent colors.\n" +
                "•  New shapes (Circle, Outline, No background) and effects (Zoom, Jump, Sparks, Bubble).\n" +
                "•  New Hacker theme: green glass keys over falling code rain.",
        ),
        "8.6" to tr(
            "•  Sonido nuevo: toque corto y suave (menos de 20 ms).\n" +
                "•  Vibración tipo «Sistema» (como Gboard) y botón para probar los 3 tipos.\n" +
                "•  En «Más opciones»: Tema, Efecto, Letras, Teclas, Sonido y Vibración se cambian con un toque o deslizando.",
            "•  New sound: a short, soft tap (under 20 ms).\n" +
                "•  «System» vibration (like Gboard) and a button to test the 3 types.\n" +
                "•  In «More options»: Theme, Effect, Letters, Keys, Sound and Vibration change with a tap or a swipe.",
        ),
        "8.5" to tr("•  Los cambios de los ajustes (tema, colores, forma, animación…) se ven al instante en el teclado abierto.",
            "•  Settings changes (theme, colors, shape, animation…) show instantly on the open keyboard."),
        "8.4" to tr(
            "•  11 temas (Medianoche, Océano, Neón, Bosque, Atardecer, Rosa, Hacker, Aurora…) y color de acento.\n" +
                "•  Forma de las teclas, tamaño de las letras y animaciones al tocar (Rebote, Onda, Brillo).\n" +
                "•  Vibración moderna (clic del teléfono) y sonidos nuevos: letra, borrar y espacio.",
            "•  11 themes (Midnight, Ocean, Neon, Forest, Sunset, Rose, Hacker, Aurora…) and accent color.\n" +
                "•  Key shape, letter size and tap animations (Bounce, Ripple, Glow).\n" +
                "•  Modern vibration (the phone's click) and new sounds: letter, delete and space.",
        ),
        "8.3" to tr(
            "•  Modo código </> (en ⋯): teclado para programar (Tab, ( ) [ ] { } : =), sangría automática, " +
                "palabras de Python/JavaScript/Kotlin/Java/HTML/C++ y la IA: Explicar, Escribir código, Arreglar error y Pista.",
            "•  Code mode </> (in ⋯): a keyboard for coding (Tab, ( ) [ ] { } : =), auto-indent, " +
                "Python/JavaScript/Kotlin/Java/HTML/C++ words and AI: Explain, Write code, Fix error and Hint.",
        ),
        "8.2" to tr("•  Botón 🔒 «Mis datos» en el teclado (en ⋯): pide tu huella y muestra tus correos, contraseñas y textos.",
            "•  🔒 «My data» button on the keyboard (in ⋯): asks for your fingerprint and shows your emails, passwords and texts."),
        "8.1" to tr("•  Ojito 👁 para ver la contraseña mientras la escribes en «Mis datos».", "•  Eye 👁 to see the password while you type it in «My data»."),
        "8.0" to tr(
            "•  «Mis datos» (como Samsung Pass): correos, contraseñas y atajos guardados y cifrados. Escribe 2–3 letras " +
                "o el atajo y el teclado te los sugiere. Las contraseñas piden huella o PIN.\n" +
                "•  «Más formal» prueba varios modelos de Gemini y dice el error exacto si falla.",
            "•  «My data» (like Samsung Pass): saved and encrypted emails, passwords and shortcuts. Type 2–3 letters " +
                "or the shortcut and the keyboard suggests them. Passwords ask for fingerprint or PIN.\n" +
                "•  «More formal» tries several Gemini models and shows the exact error if it fails.",
        ),
        "7.9" to tr(
            "•  Sugerencia de lo que copias: «Pegar: …» arriba del teclado.\n" +
                "•  Códigos de verificación de tus SMS: «Código: 123456», un toque y se pega.",
            "•  Suggestion of what you copy: «Paste: …» above the keyboard.\n" +
                "•  Verification codes from your SMS: «Code: 123456», one tap pastes it.",
        ),
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

}
