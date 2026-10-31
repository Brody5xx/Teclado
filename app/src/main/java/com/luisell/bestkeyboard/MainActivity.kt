package com.luisell.bestkeyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import android.widget.Toast

/** Pantalla de la app: activar el teclado, probarlo y cambiar ajustes. */
class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var step1: Button
    private lateinit var step2: Button
    private lateinit var col: LinearLayout
    private lateinit var learnedInfo: TextView
    private lateinit var micBtn: Button

    companion object {
        const val EXTRA_ASK_MIC = "pedir_micro"
        const val EXTRA_ASK_CAMERA = "pedir_camara"
        const val EXTRA_ASK_PHOTOS = "pedir_fotos"
        private const val REQ_PHOTOS = 9
        private const val REQ_MIC = 7
        private const val REQ_CAMERA = 8
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        title = "Best Keyboard"
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

    private lateinit var photosBtn: Button

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

    private lateinit var camBtn: Button

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
            micBtn.text = if (micGranted()) "✓ Micrófono permitido" else "🎤 Permitir el micrófono para dictar"
        }
        if (::photosBtn.isInitialized) {
            photosBtn.text = if (Screenshots.hasPermission(this)) "✓ Fotos permitidas (capturas de pantalla)"
            else "🖼 Permitir ver mis capturas de pantalla"
        }
        if (::camBtn.isInitialized) {
            camBtn.text = if (cameraGranted()) "✓ Cámara permitida" else "📷 Permitir la cámara para escanear"
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
                "Ese aviso sale con TODOS los teclados. Lo que escribes NUNCA sale de tu teléfono: el internet " +
                "solo se usa para descargar una vez los idiomas de traducción.", 13f, faded = true,
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
        text("Para hacer el teclado más alto o más bajo: toca ⋯ arriba del teclado y luego «Cambiar tamaño».", 13f)
        switch("Fila de números arriba", Prefs.NUMBER_ROW, true)
        switch("Mostrar la letra grande al tocar", Prefs.PREVIEW, true)

        section("Al tocar una tecla")
        switch("Vibración", Prefs.VIBRATE, true)
        radio("Fuerza de la vibración", Prefs.VIB_STRENGTH, "medium", listOf(
            "soft" to "Suave", "medium" to "Media", "strong" to "Fuerte",
        ))
        switch("Sonido", Prefs.SOUND, false)

        section("Barra espaciadora")
        text("Mantén presionada la barra espaciadora y desliza el dedo para mover el cursor entre las letras.", 14f)
        switch("Deslizar la barra espaciadora cambia el idioma (y se quita el botón 🌐)", Prefs.SPACE_LANG, false)
        text("Si lo activas: desliza rápido la barra espaciadora para cambiar entre español e inglés. " +
            "Para mover el cursor, mantenla presionada un momento (sentirás una vibración) y luego desliza.", 13f)

        section("Sugerencias y autocorrector")
        switch("Mostrar sugerencias de palabras", Prefs.SUGGEST, true)
        switch("Deslizar el dedo para escribir", Prefs.SWIPE, true)
        switch("Autocorrector (corrige al tocar espacio)", Prefs.AUTOCORRECT, true)
        switch("Aprender mis palabras (nombres, palabras del trabajo)", Prefs.LEARN, true)
        text(
            "Si el autocorrector cambia una palabra que estaba bien, toca borrar ⌫ justo después: " +
                "vuelve a tu palabra y la aprende para no cambiarla más.", 13f, faded = true,
        )
        learnedInfo = text("", 13f, faded = true)
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

        section("Revisar ortografía (botón abc✓)")
        text(
            "Toca abc✓ arriba del teclado: revisa TODO lo que escribiste y te lleva error por error. Cada " +
                "palabra mal escrita se marca en el texto; arriba eliges la corrección (la azul es la mejor), " +
                "\"Saltar ›\" para dejarla como está, o \"+ Mi palabra\" para que no la vuelva a marcar. " +
                "Revisa español e inglés a la vez. También avisa de palabras raras que parecen un error " +
                "(\"¿Quisiste decir…?\", por ejemplo hula → hola).", 13f, faded = true,
        )

        section("Corrector ortográfico de Android ✔")
        text(
            "Subraya en rojo las palabras mal escritas en tus apps (Gmail, Mensajes, notas...) en español E " +
                "inglés. Toca la palabra subrayada para ver las correcciones. Para encenderlo:", 13f, faded = true,
        )
        button("1. Abrir los ajustes del corrector ortográfico") { openSpellSettings() }
        text(
            "2. Enciende \"Usar corrector ortográfico\" y elige \"Best Keyboard\" como corrector.\n" +
                "Si el botón no te lleva directo: Ajustes → Sistema → Idiomas (o Teclado) → Corrector ortográfico.",
            13f, faded = true,
        )
        switch("Corrector ortográfico activo", Prefs.SPELL, true)

        section("Dictado por voz 🎤")
        micBtn = button("🎤 Permitir el micrófono para dictar") { askMic() }
        text(
            "Toca 🎤 arriba del teclado y habla. Sigue escuchando hasta que toques el micrófono otra vez. " +
                "Puedes decir los signos: \"coma\", \"punto final\", \"punto y aparte\", \"dos puntos\", " +
                "\"signo de interrogación\", \"signo de exclamación\", \"nueva línea\". En inglés: \"comma\", " +
                "\"period\", \"question mark\", \"new line\". Usa el reconocimiento de voz de Google del teléfono.",
            13f, faded = true,
        )

        section("Traducción A文")
        text(
            "Toca A文 arriba del teclado. Escribe en la cajita y la traducción aparece sola en la app donde " +
                "estás escribiendo. Toca ⇄ para cambiar Español ⇄ Inglés. Enter envía la traducción. " +
                "La primera vez descarga cada idioma (unos 30 MB); después traduce sin internet, dentro del teléfono.",
            13f, faded = true,
        )

        section("Escanear texto 📷")
        camBtn = button("📷 Permitir la cámara para escanear") { askCamera() }
        text(
            "Toca el escáner (el cuadrito con esquinas) arriba del teclado: la cámara se abre DENTRO del teclado. " +
                "Toma la foto, las palabras se marcan y quedan todas elegidas; desliza el dedo sobre las palabras " +
                "para elegir solo las que quieres (o toca una sola). Toca Insertar y se escriben al instante. " +
                "Con 🖼 eliges una foto de la galería. Todo se lee dentro del teléfono, sin internet.",
            13f, faded = true,
        )

        section("Portapapeles")
        switch("Guardar todo lo que copio (textos e imágenes)", Prefs.CLIP_HISTORY, true)
        text(
            "Lo que copias dura 2 días (48 horas) y después se borra solo. Lo que fijas con 📌 se queda " +
                "para siempre. Se guarda solo en tu teléfono. Las contraseñas que las apps marcan como " +
                "privadas no se guardan.", 13f, faded = true,
        )

        section("Capturas de pantalla 🖼")
        switch("Sugerir mi última captura de pantalla", Prefs.SHOTS, true)
        photosBtn = button("🖼 Permitir ver mis capturas de pantalla") { askPhotos() }
        text(
            "Cuando tomas una captura, arriba del teclado aparece \"Captura de pantalla\". Tócala y se pega " +
                "en el chat (WhatsApp, Claude, Gmail...) y también se guarda en tu portapapeles. Se ofrece " +
                "durante 3 minutos después de tomarla.", 13f, faded = true,
        )
        button("Borrar el historial del portapapeles (menos lo fijado)") {
            AlertDialog.Builder(this)
                .setTitle("¿Borrar el portapapeles?")
                .setMessage("Se borrará todo lo copiado, menos lo que fijaste con 📌.")
                .setPositiveButton("Borrar") { _, _ -> ClipStore.load(this) { ClipStore.clearUnpinned() } }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        section("Escritura")
        switch("Mayúscula automática al empezar una oración", Prefs.AUTOCAP, true)
        switch("Doble espacio pone un punto", Prefs.DOUBLE_SPACE, true)

        section("Trucos")
        text(
            "• Mantén presionada una letra para los acentos (á, é, ñ, ü...).\n" +
                "• Mantén presionado el punto para ¿ ? ¡ ! y más signos.\n" +
                "• Toca ⇧ dos veces seguidas para escribir TODO EN MAYÚSCULAS.\n" +
                "• Desliza el dedo sobre la barra espaciadora para mover el cursor.\n" +
                "• Desliza el dedo por las letras sin soltarlo para escribir una palabra entera. Si salió otra, " +
                "toca la correcta arriba; borrar ⌫ justo después quita la palabra entera.\n" +
                "• Toca una sugerencia de arriba para escribirla. La del centro en negrita es la corrección.\n" +
                "• Si la palabra que escribiste sale entre comillas “así”, tócala para que el teclado la aprenda.\n" +
                "• Emojis: toca 😊 arriba del teclado, o mantén presionada la coma.\n" +
                "• En los emojis, mantén presionado uno con personas o manos para elegir el tono de piel.\n" +
                "• Portapapeles: toca 📋 arriba del teclado. Toca algo para pegarlo; mantenlo presionado para " +
                "fijarlo arriba 📌 o borrarlo.\n" +
                "• 🎤 dicta por voz y A文 traduce mientras escribes.\n" +
                "• Toca 🌐 para cambiar Español / English (las sugerencias usan los dos idiomas a la vez).\n" +
                "• Mantén presionado 🌐 para cambiar a otro teclado (por ejemplo, Gboard).\n" +
                "• Arriba del teclado: ⚙ ajustes, 😊 emojis, 📋 portapapeles, A文 traducir, ⛶ escanear, abc✓ revisar ortografía, ‹I› edición de texto, 🎤 dictar, ⌄ esconder.\n" +
                "• Mientras escribes, toca ▦ a la izquierda para ver estas herramientas.\n" +
                "• ‹I› Edición de texto: flechas, Seleccionar (las flechas marcan el texto), Todo, Cortar, Copiar, Pegar, Inicio y Fin.\n" +
                "• En los campos de correo (como \"Para\" en Gmail) no salen sugerencias, para no cambiarte una dirección.\n" +
                "• Mantén presionada una tecla de borrar para borrar rápido.", 14f,
        )

        section("Novedades — Versión 7.5")
        text(
            "• Teclado de computadora (ícono del hacker, en ⋯): Esc, Tab, Ctrl, Alt, Shift, flechas y Fn " +
                "con F1–F12, Inicio/Fin y RePág/AvPág. Ideal para terminales y código.\n" +
                "   – Ctrl, Alt y Shift: un toque = solo la próxima tecla (borde azul); dos toques = fijo (azul); tres = apagado.\n" +
                "   – Mantén una tecla para su otro símbolo (por ejemplo 2 → @).\n" +
                "• Ortografía de una selección: selecciona palabras y toca el botón de ortografía. " +
                "Se revisan una por una, incluso tus palabras guardadas (usa «Saltar» para dejarlas).", 14f,
        )

        section("Versión 7.4")
        text(
            "• Revisar ortografía como Google Docs: ves todo tu texto con las palabras mal escritas en rojo. " +
                "Toca una para corregirla, o «Corregir todo».\n" +
                "• Mejores ideas para palabras muy mal escritas («autografia» → «ortografía»).\n" +
                "• Si deshaces una corrección, la revisión de ortografía igual la revisa.", 14f,
        )

        section("Versión 7.3")
        text(
            "• Ahora puedes mover TODOS los botones de la barra de arriba (también el micrófono y bajar el teclado). " +
                "En ⋯ → ✎: mantén presionado un botón y arrástralo; un toque lo pasa al otro lado.\n" +
                "• Botones de «Más opciones» más grandes, como en Gboard; los nombres largos se deslizan para leerlos.", 14f,
        )

        section("Versión 7.2")
        text(
            "• Acomoda la barra de arriba a tu manera: toca ⋯ y luego el lápiz ✎. Arrastra las opciones " +
                "dentro de la barra, o entre la barra y «Más opciones». Un toque las pasa al otro lado.\n" +
                "• «Más opciones» ya no repite lo que está en la barra de arriba.", 14f,
        )

        section("Versión 7.1")
        text(
            "• Botón ⋯ «Más opciones» en la barra de arriba: todas las herramientas con botones grandes " +
                "(ajustes, tema, siguiente idioma y más).\n" +
                "• «Cambiar tamaño»: en ⋯, desliza hacia arriba o abajo para hacer el teclado más alto o más bajo.\n" +
                "• Al mover el cursor con la barra espaciadora, el teclado se oscurece y dice «Desliza».", 14f,
        )

        section("Versión 7.0")
        text(
            "• Nueva opción en «Barra espaciadora»: deslizar rápido la barra cambia el idioma y se quita el botón 🌐. " +
                "Mantenerla presionada y deslizar sigue moviendo el cursor. Viene apagada.", 14f,
        )

        section("Versión 6.8")
        text(
            "• Íconos nuevos en todo el teclado: barra de herramientas, emojis, portapapeles, traductor, " +
                "edición, ortografía y escáner, todos con el mismo estilo de líneas finas.\n" +
                "• Nuevo botón de herramientas: un pequeño panel de control.\n" +
                "• La sugerencia de captura de pantalla tiene prioridad sobre las palabras.", 14f,
        )

        section("Versión 6.6")
        text(
            "• Los textos del teclado (paneles, botones y mensajes) cambian a inglés o español cuando " +
                "cambias el idioma con 🌐.\n" +
                "• La sugerencia de capturas pide el permiso desde el teclado si falta.", 14f,
        )

        section("Versión 6.5")
        text(
            "• ‹I› Edición de texto · Text editing: mover el cursor, seleccionar, copiar, cortar y pegar.\n" +
                "• Sugerencia de tu última captura de pantalla: un toque y se pega (y se guarda en el portapapeles).\n" +
                "• El portapapeles guarda 2 días; lo fijado 📌 se queda para siempre.", 14f,
        )

        section("Versión 6.2")
        text(
            "• Botón abc✓ para revisar la ortografía de todo el texto, error por error.\n" +
                "• Corrector ortográfico de Android ✔: subraya en rojo en las apps que lo usan.", 14f,
        )

        section("Versión 6")
        text(
            "• Deslizar para escribir ✍: pasa el dedo por las letras sin soltarlo y se escribe la palabra, " +
                "en español o inglés. Se ve la estela azul del dedo.\n" +
                "• Pone el espacio solo entre palabras deslizadas.\n" +
                "• Arriba salen otras palabras posibles por si no era esa; ⌫ borra la palabra entera.\n" +
                "• Aprende tus palabras y nombres para adivinarlos mejor con el uso.", 14f,
        )

        section("Versión 5")
        text(
            "• Escanear texto 📷 dentro del teclado (como Gboard): foto → eliges las palabras con el dedo → " +
                "Insertar, y se escriben al instante.\n" +
                "• Funciona sin internet y en español e inglés.", 14f,
        )

        section("Versión 4")
        text(
            "• Dictado por voz 🎤 en español e inglés, con los signos por voz (coma, punto final...).\n" +
                "• Traducción A文 como Gboard: escribes en tu idioma y la app recibe la traducción.\n" +
                "• La traducción funciona dentro del teléfono (Google ML Kit), sin enviar lo que escribes.", 14f,
        )

        section("Versión 3")
        text(
            "• Panel de emojis 😊 con más de 1 800 emojis en 9 categorías, Recientes y tonos de piel.\n" +
                "• Portapapeles infinito 📋: guarda todo lo que copias, textos e imágenes, sin límite.\n" +
                "• Pega imágenes directo en las apps que lo permiten (WhatsApp, Gmail, Messages...). " +
                "Si una app no lo permite, la imagen queda copiada para pegarla con mantener presionado → Pegar.\n" +
                "• Fija 📌 lo que usas siempre (direcciones, números de ticket...) para tenerlo arriba.", 14f,
        )

        section("Versión 2")
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
                "• Todo funciona sin internet y lo aprendido se guarda solo en tu teléfono.", 14f,
        )

        section("Todas las funciones listas ✅")
        text(
            "Español e inglés, autocorrector, corrector ortográfico, sugerencias, emojis, temas, vibración, dictado por voz, " +
                "deslizar para escribir, escanear texto, portapapeles infinito con imágenes y traducción.", 14f,
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
