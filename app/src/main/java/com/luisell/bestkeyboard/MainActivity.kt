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
        /** Abrir la app directo en la tarjeta "GIFs". */
        const val EXTRA_GIFS = "ver_gifs"
        const val EXTRA_BACKUP = "ver_copia"
        /** Pedir el permiso de ubicación (para escribir dónde estás). */
        const val EXTRA_ASK_LOCATION = "pedir_ubicacion"
        private const val REQ_LOCATION = 22
        private const val REQ_NOTIF = 23
        private const val REQ_AUTO = 24
        private const val REQ_HOURS = 25
        private const val REQ_CONTACTS = 26
        private const val KEY_SECTION = "settings_section"
        private const val REQ_PHOTOS = 9
        private const val REQ_MIC = 7
        private const val REQ_CAMERA = 8
        private const val REQ_SMS = 10
        private const val REQ_EXPORT = 20
        private const val REQ_IMPORT = 21
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Diseño oscuro y moderno, sin la barra de título vieja
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        // Después de una actualización: traduce en silencio los textos nuevos que falten
        if (uiCode in UiTr.LANGS && UiTr.missing(this, uiCode) > 0) UiTr.translate(this, uiCode, {}, {})
        title = "Best Keyboard"
        window.statusBarColor = BG
        window.navigationBarColor = BG
        setContentView(buildUi())
        handleAsk(intent)
        TicketReminder.schedule(this)
        WorkdayCenter.refresh(this)
        AutoBackup.schedule(this)
        prefs.registerOnSharedPreferenceChangeListener(reminderListener)
        // Mi jornada en la pantalla de bloqueo necesita permiso de notificaciones (se pide una vez)
        if (android.os.Build.VERSION.SDK_INT >= 33 && prefs.getBoolean(Prefs.WORKDAY_NOTIF, true) && !prefs.getBoolean("asked_notif", false) &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("asked_notif", true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
        // La primera vez: ¿para qué lo usas? (y después la guía rápida)
        if (!prefs.contains(Prefs.PROFILE)) {
            // Quien ya lo usaba se queda con todo (como estaba)
            if (prefs.getBoolean(Prefs.GUIDE_SEEN, false)) prefs.edit().putString(Prefs.PROFILE, "tech").putBoolean(Prefs.WORK_PACK, true).apply()
            else if (savedInstanceState == null) { askProfile(first = true); return }
        }
        if (savedInstanceState == null && !prefs.getBoolean(Prefs.GUIDE_SEEN, false)) {
            prefs.edit().putBoolean(Prefs.GUIDE_SEEN, true).apply()
            startActivity(Intent(this, GuideActivity::class.java))
        }
    }

    /** 📅 Los días que trabajas: tocas cada día para prenderlo o apagarlo. */
    private fun workDaysPicker() {
        text(tr("Días que trabajas", "Days you work"), 16f, bold = true).apply { setPadding(0, dp(14), 0, 0) }
        text(tr("Los días apagados no sale la notificación de Mi jornada, ni el aviso del ticket, ni se anota nada solo con el GPS. Si trabajas un día libre, todo se prende ese día cuando toques Comencé.",
            "On days that are off there's no My workday notification, no ticket reminder and nothing gets logged by GPS. If you work on a day off, everything turns on that day when you tap Start."), 12f, faded = true)
        // Lunes primero (Calendar: 2 = lunes … 7 = sábado, 1 = domingo)
        val order = listOf(2, 3, 4, 5, 6, 7, 1)
        // Los nombres de los días en el idioma de la pantalla (chino: 一 二 三…, inglés: M T W…), del propio Android
        val loc = java.util.Locale(uiCode)
        val sym = android.icu.text.DateFormatSymbols(loc)
        val narrow = sym.getWeekdays(android.icu.text.DateFormatSymbols.FORMAT, android.icu.text.DateFormatSymbols.NARROW)
        val abbr = sym.getWeekdays(android.icu.text.DateFormatSymbols.FORMAT, android.icu.text.DateFormatSymbols.ABBREVIATED)
        val wide = sym.getWeekdays(android.icu.text.DateFormatSymbols.FORMAT, android.icu.text.DateFormatSymbols.WIDE)
        val short = if (uiCode == "es") listOf("L", "M", "M", "J", "V", "S", "D") else order.map { narrow[it] }
        val long = order.map { wide[it] }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val dots = ArrayList<TextView>()
        val summary = text("", 13f, faded = true)
        fun paint() {
            val on = WorkDay.workDays(prefs)
            order.forEachIndexed { i, dnum ->
                val b = dots[i]
                val sel = dnum in on
                b.setTextColor(if (sel) 0xFF0B0D12.toInt() else SUB)
                b.background = if (sel) GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(CYAN, VIOLET)).apply { shape = GradientDrawable.OVAL }
                else GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(CARD2); setStroke(dp(1), LINE) }
                b.contentDescription = long[i] + if (sel) tr(": trabajas", ": you work") else tr(": libre", ": off")
            }
            val days = order.filter { it in on }
            summary.text = when {
                days.size == 7 -> tr("Trabajas todos los días.", "You work every day.")
                days == listOf(2, 3, 4, 5, 6) -> tr("Trabajas de lunes a viernes.", "You work Monday to Friday.")
                days.isEmpty() -> tr("No elegiste ningún día: todo está apagado (menos cuando toques Comencé).", "No days picked: everything is off (except when you tap Start).")
                else -> tr("Trabajas: ", "You work: ") + days.joinToString(", ") { long[order.indexOf(it)] } + "."
            }
        }
        fun changed() {
            paint()
            WorkdayCenter.refresh(this)
            TicketReminder.schedule(this)
        }
        order.forEachIndexed { i, dnum ->
            val b = TextView(this).apply {
                text = short[i]; textSize = 14f; gravity = Gravity.CENTER; includeFontPadding = false
                setTypeface(Typeface.DEFAULT_BOLD)
                isClickable = true
                setOnClickListener {
                    val now = WorkDay.workDays(prefs).toMutableSet()
                    if (dnum in now) now.remove(dnum) else now.add(dnum)
                    WorkDay.setWorkDays(prefs, now)
                    animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(120).start() }.start()
                    changed()
                }
            }
            dots.add(b)
            // Cada día ocupa 1/7 del ancho y el círculo va centrado: caben siempre, en cualquier pantalla
            val cell = android.widget.FrameLayout(this)
            cell.addView(b, android.widget.FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
            row.addView(cell, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        col.addView(row, col.childCount - 1, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(2) })
        val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun range(a: Int, b: Int) = abbr[a].trimEnd('.').replaceFirstChar { it.uppercase() } + " – " + abbr[b].trimEnd('.').replaceFirstChar { it.uppercase() }
        for ((label, set) in listOf(range(2, 6) to setOf(2, 3, 4, 5, 6),
                range(2, 7) to setOf(2, 3, 4, 5, 6, 7),
                tr("Todos", "Every day") to setOf(1, 2, 3, 4, 5, 6, 7))) {
            presets.addView(TextView(this).apply {
                text = label; textSize = 13f; gravity = Gravity.CENTER; setTextColor(CYAN)
                maxLines = 1; includeFontPadding = false
                setPadding(dp(4), dp(11), dp(4), dp(11))
                background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x334FD1FF),
                    GradientDrawable().apply { setColor(CARD2); cornerRadius = dpf(20f) }, null)
                setOnClickListener { WorkDay.setWorkDays(prefs, set); changed() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        col.addView(presets, col.indexOfChild(summary), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(6) })
        paint()
    }

    /** El nombre de un idioma en el idioma de la pantalla (en chino: 西班牙语, 英语…). */
    private fun langIn(code: String): String =
        java.util.Locale(code).getDisplayLanguage(uiLocale).replaceFirstChar { it.titlecase(uiLocale) }.ifBlank { Langs.name(code) }

    /** 🌍 Cambia el idioma de las pantallas (si es uno nuevo, primero lo traduce dentro del teléfono). */
    private fun setScreenLang(code: String) {
        val target = if (code == "auto") java.util.Locale.getDefault().language else code
        if (target !in UiTr.LANGS || UiTr.missing(this, target) == 0) {
            prefs.edit().putString(Prefs.APP_LANG, code).apply()
            recreate()
            return
        }
        val dlg = busy("Traduciendo la app… 0%", "Translating the app… 0%")
        val label = busyLabel
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        UiTr.translate(this, target, progress = { pct ->
            label?.text = tr("Traduciendo la app… ", "Translating the app… ") + pct + "%"
        }, done = { ok ->
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            dlg.close()
            if (ok || UiTr.has(this, target)) {
                prefs.edit().putString(Prefs.APP_LANG, code).apply()
                recreate()
            } else toast("No se pudo traducir. Conéctate a internet la primera vez (baja el idioma, unos 30 MB).",
                "Couldn't translate. Connect to the internet the first time (it downloads the language, about 30 MB).")
        })
    }

    /** 👋 ¿Para qué usas el teclado? Así se prende solo lo que te sirve. */
    private val profiles get() = listOf(
        Triple("chat", tr("Chatear y redes sociales", "Chatting and social media"), tr("Mensajes rápidos, emojis, GIFs y fotos.", "Quick messages, emojis, GIFs and photos.")),
        Triple("student", tr("Estudiar", "Studying"), tr("Escribir bien, traducir, corregir y calculadora.", "Writing well, translating, fixing and a calculator.")),
        Triple("office", tr("Oficina", "Office"), tr("Correos formales, IA para escribir y atajos.", "Formal emails, AI writing and shortcuts.")),
        Triple("tech", tr("Técnico o trabajo de campo", "Technician or field work"), tr("Todo lo anterior y además: tickets, horas de trabajo, sitios y herramientas.", "All of the above plus: tickets, work hours, sites and tools.")),
    )

    private fun askProfile(first: Boolean) {
        val list = profiles
        val curId = prefs.getString(Prefs.PROFILE, null)
        choiceDialog(tr("¿Para qué vas a usar el teclado?", "What will you use the keyboard for?"),
            list.map { it.second to it.third }, list.indexOfFirst { it.first == curId }, cancelable = !first) { which ->
                val id = list[which].first
                prefs.edit().putString(Prefs.PROFILE, id).putBoolean(Prefs.WORK_PACK, id == "tech")
                    .putBoolean(Prefs.FORMAL_SUGGEST, id == "office" || id == "tech").apply()
                if (first && !prefs.getBoolean(Prefs.GUIDE_SEEN, false)) {
                    prefs.edit().putBoolean(Prefs.GUIDE_SEEN, true).apply()
                    startActivity(Intent(this, GuideActivity::class.java))
                }
                recreate()
            }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleAsk(intent)
    }

    private fun handleAsk(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_ASK_MIC, false) == true) askMic()
        if (i?.getBooleanExtra(EXTRA_ASK_CAMERA, false) == true) askCamera()
        if (i?.getBooleanExtra(EXTRA_ASK_LOCATION, false) == true) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        }
        if (i?.getBooleanExtra(EXTRA_ASK_PHOTOS, false) == true) askPhotos()
        if (i?.getBooleanExtra(EXTRA_SHOW_AI, false) == true) {
            // Bajar hasta la tarjeta de la IA para pegar la clave
            selectSec("ai", scroll = false)
            scrollView.postDelayed({ aiCard?.let { scrollView.smoothScrollTo(0, maxOf(0, it.top - dp(60))) } }, 250)
        }
        if (i?.getBooleanExtra(EXTRA_BACKUP, false) == true) selectSec("backup", scroll = false)
        if (i?.getBooleanExtra(EXTRA_GIFS, false) == true) {
            selectSec("media", scroll = false)
            scrollView.postDelayed({ gifCard?.let { scrollView.smoothScrollTo(0, maxOf(0, it.top - dp(60))) } }, 250)
        }
    }


    private fun askPhotos() {
        val need = Screenshots.missing(this)
        if (need.isEmpty()) { refreshMic(); return }
        if (Screenshots.hasPermission(this) == false && Screenshots.hasPartial(this)) {
            // Dio "solo algunas fotos": hay que cambiarlo a "Permitir todo" en los ajustes de la app
            toast("Toca Permisos → Fotos y videos → \"Permitir todo\".", "Tap Permissions → Photos and videos → \"Allow all\".")
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
            } catch (_: Exception) { }
            return
        }
        requestPermissions(need, REQ_PHOTOS)
    }

    private fun cameraGranted() = checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /** Cambias el aviso del ticket: se vuelve a programar (y pide permiso para notificaciones). */
    private val reminderListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.WORKDAY_NOTIF || key == Prefs.WORKDAY_ALWAYS) WorkdayCenter.refresh(this)
        if (key == Prefs.REMINDER || key == Prefs.REMINDER_HOUR || key == Prefs.WORKDAY_NOTIF) {
            TicketReminder.schedule(this)
            if ((prefs.getBoolean(Prefs.REMINDER, false) || prefs.getBoolean(Prefs.WORKDAY_NOTIF, true)) && android.os.Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }
        }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(reminderListener)
        super.onDestroy()
    }

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

    // ---------------------------------------------------------------- Copia de seguridad

    private var backupPassword = ""

    /** Pide la contraseña del respaldo ([export] = guardar; si no, para abrir uno). */
    private fun askBackupPassword(export: Boolean, data: ByteArray? = null, auto: Boolean = false) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), dp(4))
        fun field(h: String) = EditText(this).apply {
            hint = h
            isSingleLine = true
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val p1 = field(tr("Contraseña del respaldo", "Backup password"))
        val p2 = field(tr("Repite la contraseña", "Repeat the password"))
        // 👁 El ojito: tócalo para ver la contraseña y revisar que esté bien escrita
        fun withEye(e: EditText): View {
            val f = android.widget.FrameLayout(this)
            e.setPadding(dp(14), dp(12), dp(48), dp(12))
            f.addView(e, android.widget.FrameLayout.LayoutParams(-1, -2))
            val eye = IconView(this, Icons.EYE, 7.5f)
            eye.color = SUB
            eye.bgColor = BG
            var shown = false
            eye.setOnClickListener {
                shown = !shown
                val pos = e.selectionEnd
                e.inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    (if (shown) android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)
                e.setSelection(pos.coerceIn(0, e.text.length))
                eye.kind = if (shown) Icons.EYE_OFF else Icons.EYE
                eye.color = if (shown) CYAN else SUB
                eye.invalidate()
            }
            f.addView(eye, android.widget.FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END or Gravity.CENTER_VERTICAL))
            return f
        }
        box.addView(withEye(p1), LinearLayout.LayoutParams(-1, -2))
        if (export) box.addView(withEye(p2), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        val msg = TextView(this)
        msg.text = if (export) tr("Anótala: sin ella no se puede abrir el respaldo.", "Write it down: without it the backup can't be opened.")
        else tr("La contraseña que pusiste al guardarlo.", "The password you used when saving it.")
        msg.textSize = 12f
        msg.setTextColor(SUB)
        msg.setPadding(0, dp(10), 0, 0)
        box.addView(msg)
        AlertDialog.Builder(this)
            .setTitle(if (export) tr("Guardar copia de seguridad", "Save backup") else tr("Restaurar copia de seguridad", "Restore backup"))
            .setView(box)
            .setPositiveButton(tr("Seguir", "Continue")) { _, _ ->
                val pw = p1.text.toString()
                if (pw.length < 4) { toast("La contraseña debe tener al menos 4 letras.", "The password must have at least 4 characters."); return@setPositiveButton }
                if (export) {
                    if (pw != p2.text.toString()) { toast("Las contraseñas no son iguales.", "The passwords don't match."); return@setPositiveButton }
                    backupPassword = pw
                    if (auto) {
                        // Elige la carpeta donde se guardará cada semana (puede ser de Google Drive)
                        try {
                            @Suppress("DEPRECATION")
                            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_AUTO)
                        } catch (_: Exception) { toast("No se pudo abrir el selector de carpetas.", "Couldn't open the folder picker.") }
                        return@setPositiveButton
                    }
                    try {
                        @Suppress("DEPRECATION")
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "best-keyboard-respaldo.bkb"), REQ_EXPORT)
                    } catch (_: Exception) { }
                } else if (data != null) {
                    val busyDlg = busy("Restaurando tu copia…", "Restoring your backup…")
                    Thread {
                    val ok = try { Backup.import(this, data, pw) } catch (_: Throwable) { false }
                    runOnUiThread {
                    busyDlg.close()
                    if (ok) {
                        // Lo que Android no deja pasar de un teléfono a otro: hay que darlo otra vez
                        android.app.AlertDialog.Builder(this)
                            .setTitle(tr("✅ ¡Todo se restauró!", "✅ Everything was restored!"))
                            .setMessage(tr(
                                "Ajustes, estilo, palabras, Datos, frases, atajos, portapapeles, Mi jornada, tickets, sitios, pendientes, tu pago y tu fondo ya están aquí.\n\n" +
                                    "En este teléfono solo falta darle otra vez los permisos (Android no los pasa):\n" +
                                    "1. Activar y elegir Best Keyboard (arriba).\n" +
                                    "2. Micrófono, fotos, notificaciones y SMS (botones de arriba).\n" +
                                    "3. Ubicación «Permitir todo el tiempo» en Mis sitios, si usas llegada y salida automáticas.\n" +
                                    "4. Volver a prender la copia automática (elegir la carpeta otra vez).\n" +
                                    "5. Poner el widget de Mi jornada en tu pantalla, si lo usabas.",
                                "Settings, style, words, Data, phrases, shortcuts, clipboard, My workday, tickets, sites, to-dos, your pay and your background are here.\n\n" +
                                    "On this phone you only need to give the permissions again (Android doesn't transfer them):\n" +
                                    "1. Enable and choose Best Keyboard (above).\n" +
                                    "2. Microphone, photos, notifications and SMS (buttons above).\n" +
                                    "3. Location «Allow all the time» in My sites, if you use automatic arrival and departure.\n" +
                                    "4. Turn the automatic backup on again (pick the folder again).\n" +
                                    "5. Add the My workday widget to your screen, if you used it."))
                            .setPositiveButton(tr("Entendido", "Got it")) { _, _ -> recreate() }
                            .setCancelable(false)
                            .show()
                    } else toast("Contraseña incorrecta o archivo no válido.", "Wrong password or invalid file.")
                    }
                    }.start()
                }
            }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_AUTO -> {
                val pw = backupPassword
                backupPassword = ""
                val busyDlg = busy("Guardando la primera copia…", "Saving the first backup…")
                Thread {
                    AutoBackup.turnOn(this, uri, pw)
                    runOnUiThread {
                        busyDlg.close()
                        toast("¡Listo! Se guarda una copia cada semana en esa carpeta.", "Done! A copy is saved every week in that folder.")
                        recreate()
                    }
                }.start()
            }
            REQ_HOURS -> try {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(WorkDay.csv(prefs).toByteArray(Charsets.UTF_8)) }
                toast("Horas guardadas. Ábrelo con Excel o Google Sheets.", "Hours saved. Open it with Excel or Google Sheets.")
            } catch (_: Exception) { toast("No se pudo guardar el archivo.", "Couldn't save the file.") }
            REQ_EXPORT -> {
                val pw = backupPassword
                backupPassword = ""
                val busyDlg = busy("Guardando la copia…", "Saving the backup…")
                Thread {
                    val ok = try {
                        val bytes = Backup.export(this, pw)
                        contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                        prefs.edit().putLong(Prefs.BACKUP_LAST, System.currentTimeMillis()).apply()
                        true
                    } catch (_: Throwable) { false }
                    runOnUiThread {
                        busyDlg.close()
                        if (!ok) toast("No se pudo guardar el archivo.", "Couldn't save the file.")
                        else if (BgMedia.kind(this) == "video" && !Backup.videoFits(this))
                            toast("Copia guardada. Tu video de fondo es muy pesado para la copia: en el teléfono nuevo elígelo otra vez.",
                                "Backup saved. Your background video is too big for the backup: pick it again on the new phone.")
                        else toast("Copia de seguridad guardada.", "Backup saved.")
                    }
                }.start()
            }
            REQ_IMPORT -> try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
                askBackupPassword(false, bytes)
            } catch (_: Exception) { toast("No se pudo abrir el archivo.", "Couldn't open the file.") }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshMic()
        when (requestCode) {
            REQ_CONTACTS -> {
                refreshContacts()
                if (contactsGranted()) { WordStore.loadContacts(this); toast("¡Listo! Escribe el nombre de un contacto y sale bien escrito.", "Done! Type a contact's name and it shows up spelled right.") }
                else toast("Sin el permiso de contactos no puedo sugerir sus nombres.", "Without the contacts permission I can't suggest their names.")
            }
            REQ_NOTIF -> WorkdayCenter.refresh(this)
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
        if (::photosBtn.isInitialized) photosBtn.text = if (Screenshots.missing(this).isEmpty()) tr("✓  Capturas de pantalla permitidas", "✓  Screenshots allowed")
            else tr("Permitir ver mis capturas", "Allow seeing my screenshots")
        if (::smsBtn.isInitialized) smsBtn.text = if (smsGranted()) tr("✓  Códigos de SMS permitidos", "✓  SMS codes allowed") else tr("Permitir leer códigos de SMS", "Allow reading SMS codes")
        if (::camBtn.isInitialized) camBtn.text = if (cameraGranted()) tr("✓  Cámara permitida", "✓  Camera allowed") else tr("Permitir la cámara", "Allow the camera")
    }

    private var contactsBtn: TextView? = null
    private var statsText: TextView? = null

    private fun refreshStats() {
        val t = statsText ?: return
        Stats.flush(prefs)
        val (ww, wt) = Stats.get(prefs, Stats.WORDS)
        val (pw, pt) = Stats.get(prefs, Stats.PREDICTED)
        val (sw, st) = Stats.get(prefs, Stats.SAVED)
        val (fw, ft) = Stats.get(prefs, Stats.FIXED)
        fun pct(a: Int, b: Int) = if (b > 0) " (${a * 100 / b}%)" else ""
        t.text = tr("Esta semana:\n•  $ww palabras escritas\n•  $pw adivinadas sin escribir ni una letra${pct(pw, ww)}\n•  $sw letras que no tuviste que escribir\n•  $fw errores corregidos solos\n\nDesde siempre: $wt palabras · $pt adivinadas · $st letras ahorradas · $ft corregidos",
            "This week:\n•  $ww words typed\n•  $pw guessed before you typed a letter${pct(pw, ww)}\n•  $sw letters you didn't have to type\n•  $fw mistakes fixed by itself\n\nAll time: $wt words · $pt guessed · $st letters saved · $ft fixed")
    }
    private fun contactsGranted() = checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun refreshContacts() {
        val on = prefs.getBoolean(Prefs.CONTACT_NAMES, true) && contactsGranted()
        contactsBtn?.text = if (on) tr("✓ Sugerir nombres de mis contactos (toca para apagar)", "✓ Suggest my contacts' names (tap to turn off)")
            else tr("Sugerir nombres de mis contactos", "Suggest my contacts' names")
    }

    private fun toggleContacts() {
        if (!contactsGranted()) {
            prefs.edit().putBoolean(Prefs.CONTACT_NAMES, true).apply()
            requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
            return
        }
        val on = !prefs.getBoolean(Prefs.CONTACT_NAMES, true)
        prefs.edit().putBoolean(Prefs.CONTACT_NAMES, on).apply()
        WordStore.loadContacts(this)
        refreshContacts()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshMic()
        refreshContacts()
        refreshStats()
        WordStore.load(this) { refreshLearned() }
        if (AutoSites.isOn(this)) AutoSites.register(this)
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
    private var gifCard: View? = null

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
        PanelUi.glowScrollbar(sv)
        scrollView = sv
        sv.setBackgroundColor(BG)
        sv.isVerticalScrollBarEnabled = false
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(28), dp(18), dp(48))
        }
        sv.addView(root)
        col = root

        // ---- Encabezado: el logo (diamante sobre el teclado) + «Best Keyboard» + la línea de luz
        val logo = android.widget.ImageView(this)
        logo.setImageResource(R.drawable.ic_fg_mix)
        logo.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        // ☄️ Encima del logo: el cometa de luz que recorre el borde del diamante
        val logoBox = android.widget.FrameLayout(this)
        logoBox.addView(logo, android.widget.FrameLayout.LayoutParams(-1, -1))
        logoBox.addView(CometView(this), android.widget.FrameLayout.LayoutParams(-1, -1))
        root.addView(logoBox, LinearLayout.LayoutParams(dp(150), dp(150)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = -dp(22); bottomMargin = -dp(26) })
        val name = LinearLayout(this)
        name.orientation = LinearLayout.HORIZONTAL
        name.gravity = Gravity.CENTER
        val best = TextView(this)
        best.text = "Best"
        best.textSize = 34f
        best.setTypeface(Typeface.create("sans-serif", Typeface.BOLD))
        best.setTextColor(TEXT)
        best.post {
            best.paint.shader = LinearGradient(0f, 0f, best.width.toFloat(), 0f,
                intArrayOf(0xFF67E8F9.toInt(), 0xFFA78BFA.toInt(), 0xFFF0ABFC.toInt()), null, Shader.TileMode.CLAMP)
            best.invalidate()
        }
        val kb = TextView(this)
        kb.text = " Keyboard"
        kb.textSize = 34f
        kb.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL))
        kb.setTextColor(0xFFF3F6FB.toInt())
        name.addView(best)
        name.addView(kb)
        root.addView(name, LinearLayout.LayoutParams(-1, -2))
        val glow = View(this)
        glow.background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x0022D3EE, 0xFF22D3EE.toInt(), 0xFFFFFFFF.toInt(), 0xFF8B5CF6.toInt(), 0x008B5CF6))
        root.addView(glow, LinearLayout.LayoutParams(dp(240), dp(2)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8); bottomMargin = dp(14) })
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        val meta = LinearLayout(this)
        meta.orientation = LinearLayout.HORIZONTAL
        meta.gravity = Gravity.CENTER_VERTICAL
        val dev = TextView(this)
        dev.text = tr("Desarrollador: ", "Developer: ") + "Luisell Peña Rodríguez"
        dev.textSize = 13f
        dev.setTextColor(SUB)
        meta.addView(dev, LinearLayout.LayoutParams(0, -2, 1f))
        val pill = TextView(this)
        pill.text = "v" + (version ?: "").replace(" (build ", tr(" (compilación ", " (build "))
        pill.textSize = 11f
        pill.setTextColor(CYAN)
        pill.setPadding(dp(10), dp(4), dp(10), dp(4))
        pill.background = GradientDrawable().apply { cornerRadius = dpf(20f); setStroke(dp(1), 0x664FD1FF) }
        meta.addView(pill)
        root.addView(meta, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })

        // ---- Si la app tuvo un error, se puede copiar para arreglarlo
        CrashLog.last(this)?.let { err ->
            card(tr("⚠️ Hubo un error", "⚠️ There was an error"), Icons.CLOSE, glow = true, keywords = "-")
            val crashBody = col
            text(tr("La app o el teclado se cerró por un error. Toca «Copiar error» y pégalo en el chat con Claude para arreglarlo.",
                "The app or keyboard closed because of an error. Tap «Copy error» and paste it in the chat with Claude to fix it."), 13f)
            text(err.lineSequence().take(6).joinToString("\n"), 11f, faded = true)
            button(tr("Copiar error", "Copy error"), primary = true) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("Error", err))
                toast("Error copiado. Pégalo en el chat.", "Error copied. Paste it in the chat.")
            }
            val closeCrash = {
                CrashLog.clear(this)
                val i = root.indexOfChild(crashBody)
                if (i > 0) root.getChildAt(i - 1).visibility = View.GONE
                crashBody.visibility = View.GONE
            }
            button(tr("Ya lo mandé, borrar", "Sent it, delete")) { closeCrash() }
            // La ✕ de arriba también lo quita (antes era solo un dibujo)
            val head = root.getChildAt(root.indexOfChild(crashBody) - 1) as? LinearLayout
            head?.getChildAt(0)?.let { x ->
                x.isClickable = true
                x.setOnClickListener { closeCrash() }
                x.layoutParams = (x.layoutParams as LinearLayout.LayoutParams).apply { width = dp(36); height = dp(36) }
                x.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(CARD) }
            }
        }

        // ---- Buscador de ajustes
        val searchBox = EditText(this).apply {
            this@MainActivity.searchBox = this
            hint = tr("🔎  Buscar en ajustes (vibración, ticket, GIF…)", "🔎  Search settings (vibration, ticket, GIF…)")
            isSingleLine = true
            textSize = 15f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(24f); setStroke(dp(1), 0x664FD1FF) }
        }
        root.addView(searchBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(18) })
        val noResults = TextView(this).apply {
            text = tr("No encontré nada con esa palabra.", "Nothing found for that word.")
            textSize = 14f
            setTextColor(SUB)
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(24))
            visibility = View.GONE
        }
        root.addView(noResults, LinearLayout.LayoutParams(-1, -2))
        root.addView(sectionBar(), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s?.toString() ?: ""
                val n = filterCards(q)
                sectionBarView?.visibility = if (q.isBlank()) View.VISIBLE else View.GONE
                noResults.visibility = if (q.isNotBlank() && n == 0) View.VISIBLE else View.GONE
            }
        })

        cardGroup = "start"
        // ---- Idioma de la app
        card(tr("Idioma", "Language"), Icons.GLOBE, keywords = "idiomas teclado portugues frances italiano aleman chino traductor languages keyboard portuguese french italian german chinese translator")
        // Idioma de las pantallas: español, inglés y 5 más (se traducen dentro del teléfono la primera vez)
        text(tr("Idioma de esta pantalla", "Language of this screen"), 16f, bold = true)
        val screenCodes = listOf("auto", "es", "en") + UiTr.LANGS
        val curPick = prefs.getString(Prefs.APP_LANG, "auto") ?: "auto"
        fun pickName(c: String) = if (c == "auto") tr("Del teléfono", "Phone's") else UiTr.native(c)
        button(pickName(curPick) + "  ›") {
            choiceDialog(tr("Idioma de esta pantalla", "Language of this screen"),
                screenCodes.map { c -> pickName(c) to (if (c == "auto") tr("El mismo idioma de tu teléfono", "Same language as your phone") else if (c in UiTr.LANGS && !UiTr.has(this, c)) tr("Se traduce la primera vez (necesita internet)", "Translated the first time (needs internet)") else null) },
                screenCodes.indexOf(curPick)) { w -> setScreenLang(screenCodes[w]) }
        }

        // ---- 🌍 Idiomas del teclado (en la misma tarjeta del idioma)
        divider()
        text(tr("Idiomas del teclado", "Keyboard languages"), 16f, bold = true)
        text(tr("Elige tus 2 idiomas. El teclado cambia solo entre ellos mientras escribes, con su diccionario y sus sugerencias.",
            "Pick your 2 languages. The keyboard switches between them by itself while you type, with their dictionary and suggestions."), 13f, faded = true)
        val (kl1, kl2) = Langs.pair(prefs)
        fun pickLang(key: String, current: String) {
            choiceDialog(tr("Elige el idioma", "Pick the language"), Langs.ALL.map { langIn(it.code) to (if (langIn(it.code) != it.name) it.name else null) },
                Langs.ALL.indexOfFirst { it.code == current }) { which ->
                    val code = Langs.ALL[which].code
                    val e = prefs.edit().putString(key, code)
                    // Si eliges el mismo que el otro, se cambian de lugar
                    val otherKey = if (key == Prefs.KB_LANG1) Prefs.KB_LANG2 else Prefs.KB_LANG1
                    if (Langs.pair(prefs).let { if (key == Prefs.KB_LANG1) it.second else it.first } == code)
                        e.putString(otherKey, current)
                    e.putString(Prefs.LANG, if (key == Prefs.KB_LANG1) code else Langs.pair(prefs).first)
                    e.apply()
                    val busyDlg = busy("Cargando el diccionario…", "Loading the dictionary…")
                    WordStore.reload(this) { busyDlg.close(); recreate() }
                }
        }
        button(tr("Idioma 1: ", "Language 1: ") + langIn(kl1)) { pickLang(Prefs.KB_LANG1, kl1) }
        button(tr("Idioma 2: ", "Language 2: ") + langIn(kl2)) { pickLang(Prefs.KB_LANG2, kl2) }
        text(tr("Todos usan el teclado QWERTY. Las letras con acento (é, ü, ç, ã, ß…) salen manteniendo la letra.",
            "All use the QWERTY layout. Accented letters (é, ü, ç, ã, ß…) come up by holding the letter."), 12f, faded = true)
        text(tr("Chino (中文): escribe el pinyin sin tonos (nihao) y toca los caracteres en la fila de arriba (你好). La barra espaciadora pone el primero; Enter deja las letras.",
            "Chinese (中文): type the pinyin without tones (nihao) and tap the characters in the row above (你好). Space puts the first one; Enter keeps the letters."), 12f, faded = true)
        if (kl1 == "zh" || kl2 == "zh") {
            // 🇨🇳 Solo si uno de tus idiomas es el chino
            if (!prefs.contains(Prefs.ZH_LAYOUT)) prefs.edit().putString(Prefs.ZH_LAYOUT, Prefs.zhLayout(prefs)).apply()
            segmented(tr("Teclado chino", "Chinese keyboard"), Prefs.ZH_LAYOUT, "qwerty",
                listOf("qwerty" to tr("Pinyin", "Pinyin"), "t9" to tr("9 teclas", "9 keys"), "stroke" to tr("Trazos", "Strokes")))
            text(tr("Pinyin: las 26 letras. 9 teclas (九宫格): ABC, DEF… como los teléfonos chinos, para escribir con una mano. Trazos (笔画): 一 丨 丿 丶 乛. En el teclado chino los cambias con la tecla de abajo (九键 → 笔画 → 拼音).",
                "Pinyin: the 26 letters. 9 keys (九宫格): ABC, DEF… like Chinese phones, to type with one hand. Strokes (笔画): 一 丨 丿 丶 乛. On the Chinese keyboard switch with the key at the bottom (九键 → 笔画 → 拼音)."), 12f, faded = true)
        }
        text(tr("El traductor también tiene chino: copia un mensaje en chino (o toma una captura) y toca «Traducir mensaje o captura», o en la barra de traducir toca el nombre del idioma para cambiarlo (Inglés → Chino → …).",
            "The translator also has Chinese: copy a message in Chinese (or take a screenshot) and tap «Translate message or screenshot», or in the translate bar tap the language name to change it (English → Chinese → …)."), 12f, faded = true)

        // ---- ¿Para qué lo usas?
        card(tr("Para qué lo usas", "What you use it for"), Icons.BRIEFCASE, keywords = "perfil trabajo paquete tickets profile work pack")
        val cur = profiles.firstOrNull { it.first == (prefs.getString(Prefs.PROFILE, "tech") ?: "tech") } ?: profiles.last()
        text(cur.second, 16f, bold = true)
        text(cur.third, 13f, faded = true)
        button(tr("Cambiar", "Change")) { askProfile(first = false) }
        switch(tr("Herramientas del trabajo de campo", "Field work tools"), Prefs.WORK_PACK, true,
            tr("Tickets, Libreta, Mi jornada, Mis sitios, checklist, modo guantes, teclado IP/MAC y etiquetas. Apágalo si no las usas y el teclado se ve más simple.",
                "Tickets, Notebook, My workday, My sites, checklist, glove mode, IP/MAC pad and labels. Turn it off if you don't use them and the keyboard looks simpler."))

        // ---- 👓 Modo fácil (accesibilidad)
        card(tr("Modo fácil", "Easy mode"), Icons.EYE, keywords = "accesibilidad grande contraste vista mayores talkback accessibility big contrast")
        switch(tr("Teclas grandes y fáciles de ver", "Big, easy-to-see keys"), Prefs.EASY_MODE, false,
            tr("Letras más grandes, teclas más altas, negro con blanco y amarillo (máximo contraste) y vibración fuerte. Ideal para personas mayores o con poca vista.",
                "Bigger letters, taller keys, black with white and yellow (maximum contrast) and strong vibration. Great for older people or low vision."))
        text(tr("Con TalkBack (el lector de pantalla de Android) el teclado dice cada tecla al pasar el dedo y la escribe al levantarlo.",
            "With TalkBack (Android's screen reader) the keyboard says each key as you slide your finger and types it when you lift."), 12f, faded = true)

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

        // ---- Guía rápida
        card(tr("Guía rápida", "Quick guide"), Icons.BOOK, keywords = "ayuda tutorial help como usar")
        text(tr("Un recorrido corto por todo lo que tiene el teclado.", "A short tour of everything the keyboard has."), 13f, faded = true)
        button(tr("Ver la guía", "See the guide"), primary = true) { startActivity(Intent(this, GuideActivity::class.java)) }

        // ---- Estilo (pantalla aparte con el teclado de muestra)
        card(tr("Estilo del teclado", "Keyboard style"), Icons.BRUSH, glow = true,
            keywords = "tema temas colores color vibracion vibrar sonido animacion efecto forma teclas tamano letras hacker arcoiris theme vibration sound animation shape keys size letters rainbow")
        text(tr("Temas, colores, forma y tamaño de las teclas, animaciones, vibración y sonido. Con un teclado de muestra que cambia al instante.",
            "Themes, colors, key shape and size, animations, vibration and sound. With a sample keyboard that changes instantly."), 13f, faded = true)
        button(tr("Abrir Estilo", "Open Style"), primary = true) {
            startActivity(Intent(this, StyleActivity::class.java))
        }
        text(tr("Para hacer el teclado más alto o más bajo: toca ⋯ arriba del teclado → «Tamaño».", "To resize: tap ⋯ above the keyboard → «Resize»."), 12f, faded = true)

        cardGroup = "type"
        // ---- Escritura
        card(tr("Escritura", "Typing"), Icons.SPELL)
        switch(tr("Mayúscula automática al empezar una oración", "Auto-capitalize sentences"), Prefs.AUTOCAP, true)
        switch(tr("Doble espacio pone un punto", "Double space adds a period"), Prefs.DOUBLE_SPACE, true)
        switch(tr("Mayúsculas en lo seleccionado", "Uppercase the selection"), Prefs.SEL_CAPS, false,
            tr("Selecciona un texto y toca ⇧: pasa a MAYÚSCULAS. Otra vez: minúsculas. Otra: Tipo Título.",
                "Select text and tap ⇧: it becomes UPPERCASE. Again: lowercase. Again: Title Case."))

        // ---- Barra espaciadora
        card(tr("Barra espaciadora", "Space bar"), Icons.GLOBE)
        switch(tr("🌐 Cambiar el idioma solo", "🌐 Switch language automatically"), Prefs.AUTO_LANG, true,
            tr("Si escribes 2 palabras seguidas en inglés, el teclado se pone en inglés solo (y al revés). Verás la luz en la barra espaciadora.",
                "If you type 2 words in a row in Spanish, the keyboard switches by itself (and the other way). You'll see the light on the space bar."))
        switch(tr("Recordar el idioma de cada app", "Remember each app's language"), Prefs.APP_LANG_MEMORY, true,
            tr("Cada app empieza en el idioma que usaste ahí la última vez: si en WhatsApp escribes en español y en la app del trabajo en inglés, el teclado ya abre así.",
                "Each app starts in the language you last used there: if you write Spanish in WhatsApp and English in your work app, the keyboard opens that way."))
        switch(tr("Deslizar la barra cambia el idioma", "Swipe the space bar to change language"), Prefs.SPACE_LANG, false,
            tr("Se quita el botón del globo (idioma). Desliza rápido para cambiar de idioma; mantén presionado y desliza para mover el cursor.",
                "The globe (language) key goes away. Swipe fast to change language; press, hold and slide to move the cursor."))

        // ---- Sugerencias
        card(tr("Sugerencias y autocorrector", "Suggestions and autocorrect"), Icons.TRANSLATE)
        switch(tr("Mostrar sugerencias de palabras", "Show word suggestions"), Prefs.SUGGEST, true)
        switch(tr("Sugerencias en todas las apps", "Suggestions in every app"), Prefs.SUGGEST_ALL_APPS, true,
            tr("Algunas apps (como Instagram) piden no mostrar sugerencias. Con esto encendido salen igual.",
                "Some apps (like Instagram) ask for no suggestions. With this on, they show anyway."))
        switch(tr("Deslizar el dedo para escribir", "Swipe typing"), Prefs.SWIPE, true)
        switch(tr("Teclas que se acomodan a tu dedo", "Keys that adapt to your finger"), Prefs.ADAPT_TOUCH, true,
            tr("El teclado aprende si sueles tocar un poquito abajo o a un lado de cada tecla, y lo corrige solo. Empieza a funcionar después de unas 150 letras.",
                "The keyboard learns if you usually tap a bit below or to the side of each key, and corrects it by itself. It starts working after about 150 letters."))
        switch(tr("Autocorrector", "Autocorrect"), Prefs.AUTOCORRECT, true,
            tr("Corrige al tocar espacio. Toca ⌫ justo después para deshacerlo.", "Fixes words when you tap space. Tap ⌫ right after to undo."))
        switch(tr("Aprender mis palabras", "Learn my words"), Prefs.LEARN, true, tr("Nombres, siglas y palabras del trabajo.", "Names, acronyms and work words."))
        contactsBtn = button("") { toggleContacts() }
        text(tr("Los nombres se leen solo dentro de tu teléfono para escribirlos bien y con mayúscula. Nada se envía.",
            "Names are read only on your phone to spell them right and capitalized. Nothing is sent."), 12f, faded = true)
        learnedInfo = text("", 12f, faded = true)
        button(tr("Ver y editar mis palabras", "See and edit my words"), primary = true) {
            startActivity(Intent(this, WordsActivity::class.java))
        }
        button(tr("Borrar las palabras aprendidas", "Delete learned words")) {
            confirm("¿Borrar lo aprendido?", "Delete what was learned?",
                "El teclado olvidará tus palabras y las que sueles escribir juntas.", "The keyboard will forget your words and the ones you usually type together.") {
                WordStore.clearLearned(this)
                refreshLearned()
            }
        }

        // ---- 📊 Estadísticas: cuánto te ayuda el teclado
        card(tr("Estadísticas", "Statistics"), Icons.STOPWATCH, keywords = "estadisticas ahorro letras palabras adivinadas stats saved letters")
        statsText = text("", 14f)
        text(tr("Se cuenta solo en tu teléfono. Mientras más lo uses, más aprende de ti y más te adivina.",
            "Counted only on your phone. The more you use it, the more it learns from you and the more it guesses."), 12f, faded = true)

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

        cardGroup = "ai"
        // ---- IA para escribir
        card(tr("IA para escribir", "AI writing"), Icons.HACKER)
        aiCard = col
        text(tr(
            "La IA del teclado es GRATIS y no necesita clave: es la misma que usa Best Notes. " +
                "Solo se usa cuando tú tocas un botón de IA (Más formal, Respuestas con IA, Terminar la frase, Notas → Ticket…). " +
                "A veces tarda unos segundos: si el servicio está ocupado, lo vuelve a intentar solo.",
            "The keyboard's AI is FREE and needs no key: it's the same one Best Notes uses. " +
                "It's only used when you tap an AI button (More formal, AI replies, Finish sentence, Notes → Ticket…). " +
                "Sometimes it takes a few seconds: if the service is busy, it tries again by itself.",
        ), 13f, faded = true)
        text(tr("El texto se envía a la IA solo cuando tocas uno de esos botones.",
            "Text is sent to the AI only when you tap one of those buttons."), 12f, faded = true)

        // ---- Permisos y herramientas
        card(tr("Voz, cámara y capturas", "Voice, camera and screenshots"), Icons.MIC)
        micBtn = button("") { askMic() }
        camBtn = button("") { askCamera() }
        photosBtn = button("") { askPhotos() }
        switch(tr("Sugerir mi última captura de pantalla", "Suggest my latest screenshot"), Prefs.SHOTS, true)
        text(tr("Dictado: di \"coma\", \"punto final\", \"nueva línea\"… Traducción y escáner funcionan dentro del teléfono.",
            "Dictation: say \"comma\", \"period\", \"new line\"… Translation and scanner work on the phone."), 12f, faded = true)

        cardGroup = "data"
        // ---- Datos (como Samsung Pass): pantalla aparte
        card(tr("Datos: correos, contraseñas y atajos", "Data: emails, passwords and shortcuts"), Icons.MAIL)
        text(tr(
            "Guarda tus correos, contraseñas y textos que usas mucho. El teclado te los sugiere al escribir 2–3 letras. " +
                "Las contraseñas piden tu huella. Todo cifrado y solo en tu teléfono.",
            "Save your emails, passwords and texts you use a lot. The keyboard suggests them after 2–3 letters. " +
                "Passwords ask for your fingerprint. All encrypted and only on your phone.",
        ), 13f, faded = true)
        button(tr("Abrir Datos", "Open Data"), primary = true) { startActivity(Intent(this, DataActivity::class.java)) }
        text(tr("Qué guarda el teclado, que todo se queda en tu teléfono, y borrar cada cosa:",
            "What the keyboard saves, that it all stays on your phone, and delete each thing:"), 12f, faded = true)
        button(tr("Mis datos y privacidad", "My data and privacy")) { startActivity(Intent(this, PrivacyActivity::class.java)) }
        switch(tr("Modo incógnito", "Incognito mode"), Prefs.INCOGNITO, false,
            tr("No aprende ni guarda nada de lo que escribes o copias. También se prende solo en las pestañas privadas. Lo prendes rápido con el botón «Modo incógnito» en ⋯.",
                "It doesn't learn or save anything you type or copy. It also turns on by itself in private tabs. Turn it on quickly with the «Incognito mode» button in ⋯."))

        cardGroup = "ai"
        // ---- Trabajo: tickets, reportes y escribir más formal
        card(tr("Escribir como profesional", "Write like a pro"), Icons.TICKET)
        text(tr(
            "En la barra de arriba (desliza) o en ⋯ tienes:\n" +
                "•  📋 Notas → Ticket: escribe rápido con tus palabras y la IA lo vuelve un ticket formal (español o inglés).\n" +
                "•  🎤 Dictar reporte: cuéntalo hablando y toca «Crear reporte».\n" +
                "•  ✨ Más formal (palabra): opciones profesionales para una palabra o frase.\n" +
                "•  ✅ Revisar antes de enviar: ortografía, claridad y tono profesional.\n" +
                "Usan tu clave gratis de Gemini (tarjeta «IA para escribir»).",
            "In the top bar (swipe) or in ⋯ you have:\n" +
                "•  📋 Notes → Ticket: type quickly in your own words and the AI turns it into a formal ticket (Spanish or English).\n" +
                "•  🎤 Dictate report: say it out loud and tap «Create report».\n" +
                "•  ✨ Formal words: professional options for a word or phrase.\n" +
                "•  ✅ Review before sending: spelling, clarity and professional tone.\n" +
                "They use your free Gemini key (card «AI writing»).",
        ), 13f, faded = true)
        switch(tr("Sugerir palabras más formales mientras escribo", "Suggest more formal words while I type"), Prefs.FORMAL_SUGGEST, true,
            tr("Sin internet. Escribe «arreglé» y sale ✨ reparé; «ya sirve» → ✨ funciona correctamente; «fixed» → ✨ repaired.",
                "No internet needed. Type «fixed» and get ✨ repaired; «works now» → ✨ is now working properly."))
        switch(tr("Teclado flotante", "Floating keyboard"), Prefs.FLOAT, false,
            tr("El teclado se vuelve una ventanita: muévela con la barrita de arriba. El botón de la izquierda lo regresa abajo.",
                "The keyboard becomes a small window: move it with the bar on top. The left button puts it back at the bottom."))

        cardGroup = "type"
        // ---- Emojis
        card(tr("Emojis", "Emojis"), Icons.EMOJI)
        switch(tr("Sugerir emojis mientras escribo", "Suggest emojis while I type"), Prefs.EMOJI_SUGGEST, true,
            tr("Escribe «casa», «jajaja», «te quiero» o «estoy cansado» y arriba te sugiere el emoji que va. Tócalo y se pone después de la palabra.",
                "Type «house», «haha», «love you» or «so tired» and it suggests the matching emoji above. Tap it and it goes after the word."))

        cardGroup = "ai"
        // ---- Frases rápidas
        card(tr("Frases rápidas", "Quick phrases"), Icons.BUBBLE)
        text(tr(
            "Toca el botón 💬 «Frases» en el teclado (en ⋯) y escribe un reporte completo con un toque, en español o inglés. " +
                "Lo que dice ___ queda seleccionado para que lo llenes.",
            "Tap the 💬 «Phrases» button on the keyboard (in ⋯) and write a full report line with one tap, in Spanish or English. " +
                "The ___ part gets selected so you can fill it in.",
        ), 13f, faded = true)
        button(tr("Abrir Frases", "Open Phrases"), primary = true) { startActivity(Intent(this, PhrasesActivity::class.java)) }
        text(tr("⚡ Atajos de texto: escribe /voy + espacio y se cambia por «Voy en camino, llego en unos minutos.». Tú creas los tuyos.",
            "⚡ Text shortcuts: type /omw + space and it becomes «On my way, I'll be there in a few minutes.». Create your own."), 13f, faded = true)
        button(tr("⚡ Atajos de texto", "⚡ Text shortcuts")) { startActivity(Intent(this, ShortcutsActivity::class.java)) }

        cardGroup = "work"
        // ---- Tickets del trabajo
        card(tr("Tickets del trabajo", "Work tickets"), Icons.TICKETS)
        switch(tr("Usar el formato de mis tickets del trabajo", "Use my work ticket format"), Prefs.TICKET_FORMAT, true,
            tr("En inglés técnico con Date, Location, Arrival/Departure Time, Job Description y Equipment List. Los «Service Call» usan Issue Reported, Issue Found, Action Taken y Next Steps.",
                "Technical English with Date, Location, Arrival/Departure Time, Job Description and Equipment List. «Service Call» tickets use Issue Reported, Issue Found, Action Taken and Next Steps."))
        text(tr("Número del próximo ticket (sigue contando, nunca se reinicia):", "Next ticket number (keeps counting, never resets):"), 13f, faded = true)
        val numBox = EditText(this).apply {
            setText(WorkStore.nextNumber(prefs).toString())
            isSingleLine = true
            textSize = 15f
            setTextColor(TEXT)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        col.addView(numBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        button(tr("Guardar número", "Save number")) {
            val n = numBox.text.toString().trim().toIntOrNull()
            if (n == null || n < 1) toast("Escribe un número.", "Type a number.")
            else { WorkStore.setNext(prefs, n); toast("Listo: el próximo será el Ticket $n.", "Done: the next one will be Ticket $n.") }
        }
        switch(tr("Aviso diario del ticket", "Daily ticket reminder"), Prefs.REMINDER, false,
            tr("Te avisa «✅ TICKET REGISTERED» o «❌ NO TICKET» con la fecha.", "Shows «✅ TICKET REGISTERED» or «❌ NO TICKET» with the date."))
        segmented(tr("Hora del aviso", "Reminder time"), Prefs.REMINDER_HOUR, "19", listOf(
            "17" to "5 PM", "18" to "6 PM", "19" to "7 PM", "20" to "8 PM", "21" to "9 PM",
        ))

        cardGroup = "work"
        // ---- Mi jornada: widget, pantalla de bloqueo y mis sitios
        card(tr("Mi jornada", "My workday"), Icons.TRUCK, keywords = "horas horario widget bloqueo camioneta sitios gps hours lock screen truck sites")
        text(tr(
            "Anota 🛻 Comencé, 📍 Llegué, 🚪 Salí y 🏠 Casa desde el teclado, desde el widget o desde la pantalla de bloqueo.",
            "Log 🛻 Started, 📍 Arrived, 🚪 Left and 🏠 Home from the keyboard, the widget or the lock screen."), 13f, faded = true)
        button(tr("➕ Poner el widget en la pantalla de inicio", "➕ Add the widget to the home screen"), primary = true) {
            val am = getSystemService(android.appwidget.AppWidgetManager::class.java)
            val ok = try {
                am != null && am.isRequestPinAppWidgetSupported &&
                    am.requestPinAppWidget(android.content.ComponentName(this, WorkdayWidget::class.java), null, null)
            } catch (_: Exception) { false }
            if (!ok) toast("Mantén presionada la pantalla de inicio → Widgets → Best Keyboard → Mi jornada.",
                "Long-press the home screen → Widgets → Best Keyboard → My workday.")
        }
        switch(tr("Mostrar Mi jornada en la pantalla de bloqueo", "Show My workday on the lock screen"), Prefs.WORKDAY_NOTIF, true,
            tr("Mientras estás trabajando sale una notificación con el tiempo corriendo y los botones Llegué / Salí / Casa, que puedes tocar sin desbloquear.",
                "While you're working, a notification shows the running time and the Arrived / Left / Home buttons, which you can tap without unlocking."))
        switch(tr("Dejarla siempre (con 🛻 Comencé antes de empezar)", "Keep it always (with 🛻 Start before you begin)"), Prefs.WORKDAY_ALWAYS, true,
            tr("Así puedes tocar 🛻 Comencé desde la pantalla de bloqueo, sin desbloquear.", "So you can tap 🛻 Start from the lock screen, without unlocking."))
        workDaysPicker()
        text(tr("También hay un botón en los ajustes rápidos: desliza la barra de arriba del teléfono hacia abajo, toca el lápiz y arrastra «Comencé / Mi jornada».",
            "There's also a Quick Settings button: swipe down from the top of the phone, tap the pencil and drag «Start / My workday»."), 12f, faded = true)
        button(tr("✏️ Editar botones (nombres y cuáles se ven)", "✏️ Edit buttons (names and which ones show)")) {
            startActivity(Intent(this, WorkdayButtonsActivity::class.java))
        }
        button(tr("📍 Mis sitios", "📍 My sites")) { startActivity(Intent(this, SitesActivity::class.java)) }
        button(tr("🕐 Ver y cambiar mis horas (Calendario)", "🕐 See and change my hours (Calendar)"), primary = true) {
            startActivity(Intent(this, MonthActivity::class.java))
        }
        text(tr("Toca un día y luego la hora que quieres cambiar (o ✕ para borrarla).", "Tap a day and then the time you want to change (or ✕ to delete it)."), 12f, faded = true)
        button(tr("📄 Exportar mis horas (Excel / Sheets)", "📄 Export my hours (Excel / Sheets)")) {
            try {
                val name = "mis-horas-" + java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date()) + ".csv"
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("text/csv").putExtra(Intent.EXTRA_TITLE, name), REQ_HOURS)
            } catch (_: Exception) { }
        }

        cardGroup = "word"
        // ---- Palabra de la semana
        card(tr("Palabra de la semana", "Word of the week"), Icons.BOOK)
        val ww = WeeklyWord.current()
        text(tr("${ww.word}  (${ww.en})", "${ww.en}  (${ww.word})"), 18f, bold = true)
        text(tr(ww.meaning, ww.enMeaning) + "\n«" + tr(ww.example, ww.enExample) + "»", 13f)
        button("🔊 " + ww.en + tr("  (inglés)", "  (English)")) { Speak.say(this, ww.en, english = true) }
        button("🔊 " + ww.word + tr("  (español)", "  (Spanish)")) { Speak.say(this, ww.word, english = false) }
        text(tr("Reto: úsala en al menos 3 conversaciones esta semana. Cada lunes sale una nueva.",
            "Challenge: use it in at least 3 conversations this week. A new one comes every Monday."), 12f, faded = true)
        switch(tr("Mostrarla en el teclado y sugerirla", "Show it on the keyboard and suggest it"), Prefs.WORD_WEEK, true)

        cardGroup = "type"
        // ---- ✌️ Dividido
        card(tr("Teclado dividido", "Split keyboard"), Icons.ONEHAND, keywords = "dividido tablet acostado horizontal pulgares split landscape thumbs")
        segmented(tr("Dos mitades para escribir con los pulgares", "Two halves to type with your thumbs"), Prefs.SPLIT_KB, "auto", listOf(
            "auto" to tr("Acostado o tablet", "Landscape or tablet"),
            "on" to tr("Siempre", "Always"),
            "off" to tr("Nunca", "Never"),
        ))

        // ---- Una mano
        card(tr("Modo una mano", "One-hand mode"), Icons.ONEHAND)
        segmented(tr("Teclado más angosto", "Narrower keyboard"), Prefs.ONE_HAND, "off", listOf(
            "off" to tr("Normal", "Normal"),
            "left" to tr("Izquierda", "Left"),
            "right" to tr("Derecha", "Right"),
        ))
        text(tr("También con el botón «Una mano» del teclado. En el costado vacío: la flecha cambia de lado y el otro botón lo vuelve normal.",
            "Also with the «One hand» button on the keyboard. On the empty side: the arrow switches sides and the other button makes it normal."), 12f, faded = true)

        cardGroup = "media"
        // ---- GIFs
        card(tr("GIFs y stickers", "GIFs and stickers"), Icons.GIF)
        gifCard = col
        text(tr(
            "Los GIFs vienen de GIPHY y necesitan una llave GRATIS (solo una vez):\n" +
                "1. Toca «Conseguir llave de GIPHY» y crea una cuenta (o entra).\n" +
                "2. Toca «Create an API Key», elige «API» (no SDK) y ponle cualquier nombre, por ejemplo «Best Keyboard».\n" +
                "3. Copia la llave que te da y pégala aquí abajo.",
            "GIFs come from GIPHY and need a FREE key (only once):\n" +
                "1. Tap «Get a GIPHY key» and create an account (or sign in).\n" +
                "2. Tap «Create an API Key», choose «API» (not SDK) and give it any name, like «Best Keyboard».\n" +
                "3. Copy the key and paste it below.",
        ), 13f, faded = true)
        button(tr("Conseguir llave de GIPHY", "Get a GIPHY key"), primary = true) {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://developers.giphy.com/dashboard/"))) } catch (_: Exception) { }
        }
        val gifBox = EditText(this).apply {
            hint = tr("Pega aquí tu llave de GIPHY", "Paste your GIPHY key here")
            setText(prefs.getString(Prefs.GIPHY_KEY, ""))
            isSingleLine = true
            textSize = 14f
            setTextColor(TEXT)
            setHintTextColor(SUB)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(BG); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        col.addView(gifBox, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        button(tr("Guardar llave", "Save key")) {
            prefs.edit().putString(Prefs.GIPHY_KEY, gifBox.text.toString().trim()).apply()
            toast("Llave guardada. Toca el botón de emojis y luego GIF en el teclado.", "Key saved. Tap the emoji button and then GIF on the keyboard.")
        }
        text(tr("Si la app no acepta GIFs desde el teclado, el GIF queda copiado para que lo pegues.",
            "If the app doesn't accept GIFs from the keyboard, the GIF is copied so you can paste it."), 12f, faded = true)

        cardGroup = "backup"
        // ---- Copia de seguridad
        card(tr("Copia de seguridad", "Backup"), Icons.CLIP)
        text(tr(
            "Guarda en un archivo TODO lo tuyo, protegido con una contraseña: ajustes, estilo, todo lo que el teclado aprendió de ti (palabras, frases, tus arreglos, las sugerencias que borraste), estadísticas, el idioma de cada app, Datos, frases rápidas, atajos, portapapeles, " +
                "Mi jornada (horas, sitios, tu casa, tu pago), tickets, libreta, materiales, pendientes, checklist y tu fondo. " +
                "Guárdalo en Google Drive o pásalo a tu teléfono nuevo y toca «Restaurar».",
            "Save EVERYTHING of yours to one file, protected with a password: settings, style, everything the keyboard learned from you (words, phrases, your fixes, deleted suggestions), statistics, each app's language, Data, quick phrases, shortcuts, clipboard, " +
                "My workday (hours, sites, your home, your pay), tickets, notebook, materials, to-dos, checklist and your background. " +
                "Keep it in Google Drive or move it to your new phone and tap «Restore».",
        ), 13f, faded = true)
        prefs.getLong(Prefs.BACKUP_LAST, 0L).let { last ->
            text(if (last > 0) tr("Tu última copia: ", "Your last backup: ") + java.text.DateFormat.getDateInstance().format(java.util.Date(last))
                else tr("Todavía no has hecho una copia.", "You haven't made a backup yet."), 13f)
        }
        button(tr("Guardar copia de seguridad", "Save backup"), primary = true) { askBackupPassword(true) }
        button(tr("Restaurar copia de seguridad", "Restore backup")) {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT)
            } catch (_: Exception) { }
        }
        // Copia automática cada semana
        subTitle(tr("Copia automática cada semana", "Automatic weekly backup"))
        if (AutoBackup.enabled(this)) {
            val last = prefs.getLong(Prefs.AUTO_BACKUP_LAST, 0L)
            text(tr("✅ Prendida. Última copia: ", "✅ On. Last copy: ") +
                (if (last > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(last)) else tr("todavía no", "not yet")), 13f)
            button(tr("Hacer una copia ahora", "Make a copy now")) {
                val busyDlg = busy("Haciendo la copia…", "Making the backup…")
                Thread {
                    val ok = AutoBackup.runNow(this)
                    runOnUiThread { busyDlg.close(); toast(if (ok) "Copia guardada." else "No se pudo guardar. Vuelve a elegir la carpeta.",
                        if (ok) "Backup saved." else "Couldn't save. Pick the folder again.") }
                }.start()
            }
            button(tr("Apagar copia automática", "Turn off automatic backup")) { AutoBackup.turnOff(this); recreate() }
        } else {
            text(tr("Elige una contraseña y una carpeta (por ejemplo, de Google Drive). Cada semana se guarda sola una copia nueva y se quedan las 4 más recientes.",
                "Pick a password and a folder (for example, in Google Drive). Every week a new copy is saved by itself, keeping the 4 most recent."), 12f, faded = true)
            button(tr("Prender copia automática", "Turn on automatic backup")) { askBackupPassword(true, auto = true) }
        }

        cardGroup = "data"
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

        cardGroup = "more"
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
                "•  Mantén el botón del globo para cambiar a otro teclado.",
            "•  Hold a letter for accents (á, é, ñ…) and the period for ¿ ? ¡ !\n" +
                "•  ⇧ twice = ALL CAPS.\n" +
                "•  Slide your finger over the letters to type a whole word.\n" +
                "•  Hold the comma for emojis.\n" +
                "•  ⋯ opens «More options»; the pencil ✎ lets you arrange every button.\n" +
                "•  The hacker switches to a PC keyboard (Ctrl, Alt, Esc, Tab, F1–F12).\n" +
                "•  </> Code mode: a keyboard for coding, and the AI explains, writes and fixes code.\n" +
                "•  Hold the globe key to switch to another keyboard.",
        ), 13f)

        // ---- Novedades (lo más nuevo visible, lo demás escondido)
        card(tr("Novedades", "What's new"), Icons.RECENT, keywords = "-")
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

        // Para revisar problemas: qué pidió el último cuadro de texto
        val diag = TextView(this)
        diag.text = tr("Último cuadro de texto: ", "Last text box: ") + (prefs.getString(Prefs.LAST_FIELD, "—") ?: "—")
        diag.textSize = 10f
        diag.setTextColor(LINE)
        diag.gravity = Gravity.CENTER
        root.addView(diag, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })

        val foot = TextView(this)
        foot.text = tr("Hecho con cariño · Best Keyboard", "Made with care · Best Keyboard")
        foot.textSize = 12f
        foot.setTextColor(SUB)
        foot.gravity = Gravity.CENTER
        root.addView(foot, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        cardGroup = null
        return withEdgePanel(sv)
    }

    // ---------------------------------------------------------------- Panel Edge (como el de Samsung)

    private class Sec(val id: String, val es: String, val en: String, val icon: Int)
    private val secs get() = allSecs.filter { it.id != "work" || prefs.getBoolean(Prefs.WORK_PACK, true) }
    private val allSecs = listOf(
        Sec("start", "Inicio", "Start", Icons.SETTINGS),
        Sec("type", "Escribir", "Typing", Icons.SPELL),
        Sec("ai", "IA para escribir", "AI writing", Icons.HACKER),
        Sec("work", "Trabajo", "Work", Icons.TRUCK),
        Sec("data", "Datos y copiado", "Data and copy", Icons.LOCK),
        Sec("media", "Voz y GIFs", "Voice and GIFs", Icons.MIC),
        Sec("word", "Palabra", "Word", Icons.BOOK),
        Sec("backup", "Copia", "Backup", Icons.CLIP),
        Sec("more", "Trucos y novedades", "Tips and news", Icons.IDEA),
    )
    private var searchBox: EditText? = null
    private var sectionBarView: View? = null
    private var secLabel: TextView? = null
    private var secIcon: IconView? = null
    private var edgeDim: View? = null
    private var edgePanel: LinearLayout? = null
    private var edgeHandle: View? = null
    private val secRows = HashMap<String, LinearLayout>()

    /** La barrita debajo del buscador: dice en qué sección estás y abre el panel. */
    private fun sectionBar(): View {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(14), dp(10), dp(10), dp(10))
        bar.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x3322D3EE, 0x338B5CF6)).apply {
            cornerRadius = dpf(18f); setStroke(dp(1), 0x664FD1FF)
        }
        val iv = IconView(this, Icons.SETTINGS, 8f)
        iv.isClickable = false
        iv.color = CYAN
        iv.bgColor = CARD
        secIcon = iv
        bar.addView(iv, LinearLayout.LayoutParams(dp(24), dp(24)))
        val t = TextView(this)
        t.textSize = 16f
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        t.setTextColor(TEXT)
        t.setPadding(dp(10), 0, 0, 0)
        secLabel = t
        bar.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        val more = TextView(this)
        more.text = tr("Secciones  ›", "Sections  ›")
        more.textSize = 13f
        more.setTextColor(CYAN)
        bar.addView(more)
        bar.setOnClickListener { openEdge() }
        sectionBarView = bar
        return bar
    }

    /** Pone la lista de ajustes dentro de un marco con la manija del borde y el panel. */
    private fun withEdgePanel(sv: View): View {
        val frame = android.widget.FrameLayout(this)
        frame.addView(sv, android.widget.FrameLayout.LayoutParams(-1, -1))

        // Manija en el borde derecho (como la del panel Edge)
        val handle = object : View(this) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0xFF22D3EE.toInt(), 0xFF8B5CF6.toInt()), null, Shader.TileMode.CLAMP)
                p.alpha = 210
                c.drawRoundRect(android.graphics.RectF(w - dp(7), 0f, w - dp(2), h), dpf(4f), dpf(4f), p)
            }
        }
        var downX = 0f
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX = e.rawX; true }
                android.view.MotionEvent.ACTION_UP -> { openEdge(); true }
                android.view.MotionEvent.ACTION_MOVE -> { if (downX - e.rawX > dp(20)) openEdge(); true }
                else -> true
            }
        }
        edgeHandle = handle
        frame.addView(handle, android.widget.FrameLayout.LayoutParams(dp(26), dp(110), Gravity.END or Gravity.CENTER_VERTICAL))

        // Fondo oscuro que cierra el panel al tocar afuera
        val dim = View(this)
        dim.setBackgroundColor(0x99000000.toInt())
        dim.visibility = View.GONE
        dim.setOnClickListener { closeEdge() }
        edgeDim = dim
        frame.addView(dim, android.widget.FrameLayout.LayoutParams(-1, -1))

        // El panel con todas las secciones
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(10), dp(14), dp(10), dp(14))
        panel.background = GradientDrawable().apply {
            setColor(0xF0161B26.toInt()); cornerRadius = dpf(26f); setStroke(dp(1), 0x804FD1FF.toInt())
        }
        panel.elevation = dpf(12f)
        panel.isClickable = true
        val title = TextView(this)
        title.text = tr("SECCIONES", "SECTIONS")
        title.textSize = 11f
        title.letterSpacing = 0.14f
        title.setTextColor(SUB)
        title.gravity = Gravity.CENTER
        panel.addView(title, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        for (sec in secs) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(8), dp(7), dp(12), dp(7))
            val circle = android.widget.FrameLayout(this)
            circle.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(CARD2); setStroke(dp(1), LINE) }
            val iv = IconView(this, sec.icon, 8f)
            iv.isClickable = false
            iv.color = CYAN
            iv.bgColor = CARD2
            circle.addView(iv, android.widget.FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            row.addView(circle, LinearLayout.LayoutParams(dp(40), dp(40)))
            val t = TextView(this)
            t.text = tr(sec.es, sec.en)
            t.textSize = 14f
            t.setTextColor(TEXT)
            t.setPadding(dp(12), 0, 0, 0)
            row.addView(t)
            row.setOnClickListener { selectSec(sec.id); closeEdge() }
            secRows[sec.id] = row
            panel.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
        }
        panel.visibility = View.GONE
        edgePanel = panel
        frame.addView(panel, android.widget.FrameLayout.LayoutParams(dp(230), -2, Gravity.END or Gravity.CENTER_VERTICAL).apply { rightMargin = dp(10) })

        val saved = prefs.getString(KEY_SECTION, "start") ?: "start"
        selectSec(if (secs.any { it.id == saved }) saved else "start", scroll = false)
        return frame
    }

    private fun openEdge() {
        val panel = edgePanel ?: return
        if (panel.visibility == View.VISIBLE) return
        edgeDim?.apply { alpha = 0f; visibility = View.VISIBLE; animate().alpha(1f).setDuration(180).start() }
        panel.visibility = View.VISIBLE
        panel.translationX = dpf(260f)
        panel.animate().translationX(0f).setDuration(240).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        edgeHandle?.visibility = View.INVISIBLE
        LightFx.flash(panel, dpf(26f))
    }

    private fun closeEdge() {
        val panel = edgePanel ?: return
        if (panel.visibility != View.VISIBLE) return
        edgeDim?.animate()?.alpha(0f)?.setDuration(160)?.withEndAction { edgeDim?.visibility = View.GONE }?.start()
        panel.animate().translationX(dpf(260f)).setDuration(200).withEndAction { panel.visibility = View.GONE }.start()
        edgeHandle?.visibility = View.VISIBLE
    }

    /** Muestra solo las tarjetas de esa sección. */
    private fun selectSec(id: String, scroll: Boolean = true) {
        activeGroup = id
        try { prefs.edit().putString(KEY_SECTION, id).apply() } catch (_: Exception) { }
        val box = searchBox
        if (box != null && box.text.isNotEmpty()) box.setText("") else filterCards("")
        val sec = secs.firstOrNull { it.id == id }
        if (sec != null) {
            secLabel?.text = tr(sec.es, sec.en)
            secIcon?.kind = sec.icon
            secIcon?.invalidate()
        }
        for ((k, row) in secRows) {
            row.background = if (k == id) GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x5522D3EE, 0x558B5CF6)).apply { cornerRadius = dpf(16f) } else null
        }
        if (scroll) sectionBarView?.let { b -> scrollView.post { scrollView.smoothScrollTo(0, maxOf(0, b.top - dp(8))) } }
    }

    override fun onBackPressed() {
        if (edgePanel?.visibility == View.VISIBLE) { closeEdge(); return }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    /** Novedades de cada versión (la primera es la más nueva). */
    private fun releaseNotes(): List<Pair<String, String>> = listOf(
        "12.18.3" to tr("•  Mientras la IA piensa (Respuestas rápidas, Más formal…) también sale el diamante con la luz dando vueltas.", "•  While the AI is thinking (Quick replies, More formal…) the diamond with its spinning light also shows."),
        "14.2.0" to tr("•  «Traducir mensaje» y «Traducir captura» ahora son uno solo: «Traducir mensaje o captura». Traduce lo que seleccionaste; si no, lo más nuevo entre lo que copiaste y tu última captura. Con «Usar captura» o «Usar lo copiado» cambias entre los dos.\n" +
            "•  Los idiomas se tocan para cambiarlos también al traducir mensajes, y ⇄ da la vuelta.\n" +
            "•  Destello nuevo: cuando sale la traducción, una luz cruza la tarjeta y brillan estrellitas del color de tu tema.",
            "•  «Translate message» and «Translate screenshot» are now one: «Translate message or screenshot». It translates what you selected; if nothing, the newest of what you copied and your latest screenshot. «Use screenshot» or «Use copied text» switches between them.\n" +
            "•  Tap the languages to change them when translating messages too, and ⇄ spins around.\n" +
            "•  New sparkle: when the translation appears, a light sweeps across the card and little stars shine in your theme color."),
        "14.1.0" to tr("•  Traducir captura: ahora eliges los idiomas. Toca el idioma de la izquierda para decir en qué está la captura (o «Detectar solo») y el de la derecha para elegir a cuál se traduce. Con ⇄ los volteas. Se acuerda de tu elección.\n" +
            "•  Detecta mejor el idioma (también portugués, francés, italiano y alemán) y ya no salen letras raras como «O O O» de los íconos.",
            "•  Translate screenshot: now you choose the languages. Tap the left language to say what the screenshot is in (or «Detect») and the right one to choose what to translate into. ⇄ swaps them. It remembers your choice.\n" +
            "•  Better language detection (also Portuguese, French, Italian and German) and no more odd letters like «O O O» from icons."),
        "14.0.1" to tr("•  Arreglado: con «Teclado en todas partes» prendido, el teclado se abría solo en algunas apps (por ejemplo, al cerrar «Compartir»). Ahora solo se fuerza cuando Android lo esconde por creer que hay un teclado de PC, o cuando tocas «Abrir teclado».",
            "•  Fixed: with «Keyboard anywhere» on, the keyboard opened by itself in some apps (for example, after closing «Share»). Now it's only forced when Android hides it thinking there's a PC keyboard, or when you tap «Open keyboard»."),
        "14.0.0" to tr("•  Chino 联想: después de elegir un carácter, arriba salen solos los que suelen seguir (你 → 好, 们…). Toca y sigue escribiendo frases casi sin teclear.\n" +
            "•  Chino a mano (手写): escribe el carácter con el dedo y elige arriba. Si sigues escribiendo después de una pausa, se pone el primero solo. Lo eliges con el botón del teclado chino o con la tecla de abajo (九键 → 笔画 → 手写 → 拼音). La primera vez baja el reconocimiento (~20 MB).\n" +
            "•  Ver pinyin (en ⋯): selecciona o copia un texto chino y te muestra cómo se lee con tonos (你好 → nǐ hǎo) y qué quiere decir.\n" +
            "•  Traducir captura (en ⋯): toma una captura de pantalla y el teclado lee el texto (chino, inglés, español…) y lo traduce, aunque no se pueda copiar. Sin internet, después de la primera vez.\n" +
            "•  Agregar al calendario (en ⋯): escribe o copia «mañana a las 3 reunión», «viernes 9am inspección» o «el 15 de noviembre 7:30pm cena» y lo agrega con la fecha y la hora puestas.\n" +
            "•  Pegar limpio (en ⋯): pega lo copiado sin espacios raros, en una línea, sin emojis o sin espacios (para números de serie y MAC).",
            "•  Chinese 联想: after picking a character, the ones that usually follow show up by themselves (你 → 好, 们…). Tap and keep writing phrases almost without typing.\n" +
            "•  Chinese handwriting (手写): write the character with your finger and pick it above. If you keep writing after a pause, the first one goes in by itself. Pick it with the Chinese keyboard button or the bottom key (九键 → 笔画 → 手写 → 拼音). The first time it downloads the recognizer (~20 MB).\n" +
            "•  See pinyin (in ⋯): select or copy Chinese text and it shows how it's read with tones (你好 → nǐ hǎo) and what it means.\n" +
            "•  Translate screenshot (in ⋯): take a screenshot and the keyboard reads the text (Chinese, English, Spanish…) and translates it, even if it can't be copied. Offline after the first time.\n" +
            "•  Add to calendar (in ⋯): type or copy «tomorrow at 3 meeting», «friday 9am inspection» or «nov 15 7:30pm dinner» and it adds it with the date and time set.\n" +
            "•  Clean paste (in ⋯): paste what you copied without odd spaces, on one line, without emojis or without spaces (for serial numbers and MACs)."),
        "13.9.1" to tr("•  Teclado en todas partes: al tocar «Abrir teclado» la cortina de notificaciones se cierra sola y el teclado se queda abierto en tu app (antes se cerraba al subir la cortina).",
            "•  Keyboard anywhere: tapping «Open keyboard» closes the notification shade by itself and the keyboard stays open in your app (before, it closed when you swiped the shade up)."),
        "13.9.0" to tr("•  Teclado en todas partes: nuevo botón (la llave) en la barra de arriba. Al prenderlo sale una notificación fija; tócala y Best Keyboard se abre en la app donde estés, también en terminales o apps que solo dejan usar un teclado de PC.\n" +
            "•  Mientras está prendido, el teclado de la pantalla sale siempre, aunque el teléfono crea que hay un teclado físico conectado.\n" +
            "•  Al apagarlo (con el mismo botón o con «Apagar» en la notificación), la notificación desaparece.",
            "•  Keyboard anywhere: new button (the key) in the top bar. Turn it on and a fixed notification appears; tap it and Best Keyboard opens in the app you're in, even in terminals or apps that only allow a PC keyboard.\n" +
            "•  While it's on, the on-screen keyboard always shows, even if the phone thinks a physical keyboard is connected.\n" +
            "•  Turn it off (with the same button or «Turn off» in the notification) and the notification goes away."),
        "13.8.1" to tr("•  Nuevo botón en la barra de arriba (el teclado) para elegir el teclado chino: 拼音 (26 letras), 九键 (9 teclas) o 笔画 (trazos). Solo aparece cuando el teclado está en chino, y siempre es el primero de la barra.",
            "•  New button in the top bar (the keyboard icon) to pick the Chinese keyboard: 拼音 (26 letters), 九键 (9 keys) or 笔画 (strokes). It only shows when the keyboard is in Chinese, always first in the bar."),
        "13.8.0" to tr("•  Teclado chino de 9 teclas (九宫格), como en los teléfonos chinos: ABC, DEF, GHI… Tocas una vez cada tecla (64426 = nihao → 你好) y arriba salen los caracteres; también con iniciales (64 → 你好). Tiene 重输 (borrar todo) y la puntuación china a un lado.\n" +
            "•  Solo aparece cuando el teclado está en chino: la tecla de abajo cambia entre 九键 (9 teclas), 笔画 (trazos) y 拼音 (26 letras). En Inicio › Idioma › «Teclado chino» también lo eliges (solo si tienes el chino).",
            "•  Chinese 9-key keyboard (九宫格), like on Chinese phones: ABC, DEF, GHI… Tap each key once (64426 = nihao → 你好) and the characters show up above; also with initials (64 → 你好). It has 重输 (clear all) and Chinese punctuation on the side.\n" +
            "•  It only shows when the keyboard is in Chinese: the key at the bottom switches between 九键 (9 keys), 笔画 (strokes) and 拼音 (26 letters). You can also pick it in Start › Language › «Chinese keyboard» (only if you have Chinese)."),
        "13.7.4" to tr("•  Chino: los caracteres ahora van DENTRO de la misma barra de sugerencias del teclado (ya no es una barra aparte encima). Se ve igual que en español e inglés: transparente, con tu fondo y el destello. Desliza para ver más.",
            "•  Chinese: the characters now go INSIDE the keyboard's own suggestion bar (no longer a separate bar on top). It looks just like Spanish and English: transparent, with your background and the sparkle. Swipe to see more."),
        "13.7.3" to tr("•  Chino con iniciales (简拼), como Sogou y Gboard: nh → 你好, xx → 谢谢, wm → 我们, hd → 好的, zm → 怎么, dbq → 对不起. Ya no hace falta escribir el pinyin entero.\n" +
            "•  La fila de caracteres nunca se queda vacía: si las letras no forman pinyin, salen los caracteres más usados que empiezan con esa letra.\n" +
            "•  Frases de chat comunes: 好的, 没事, 没问题, 不客气, 晚安, 在吗, 多少钱…",
            "•  Chinese with initials (简拼), like Sogou and Gboard: nh → 你好, xx → 谢谢, wm → 我们, hd → 好的, zm → 怎么, dbq → 对不起. No need to type the whole pinyin anymore.\n" +
            "•  The character row is never empty: if the letters aren't pinyin, the most used characters starting with that letter show up.\n" +
            "•  Common chat phrases: 好的, 没事, 没问题, 不客气, 晚安, 在吗, 多少钱…"),
        "13.7.2" to tr("•  Chino: los caracteres de arriba se ven exactamente como las sugerencias en español e inglés: mismo tamaño de letra, 3 por pantalla con la rayita gris entre ellos, el primero en negrita y sin barra encima. Desliza para ver más.",
            "•  Chinese: the characters on top look exactly like the Spanish and English suggestions: same text size, 3 per screen with the grey line between them, the first in bold and no bar on top. Swipe to see more."),
        "13.7.1" to tr("•  Chino: la fila de caracteres ahora se ve igual que las sugerencias de los otros idiomas: transparente, con tu fondo y el destello del teclado detrás (ya no es una barra gris encima).\n" +
            "•  Chino: la primera letra ya no se queda en mayúscula suelta (Je → todo va al pinyin). Solo si pones la mayúscula tú, sale la letra.",
            "•  Chinese: the character row now looks just like the other languages' suggestions: transparent, with your background and the keyboard's sparkle behind (no more grey bar on top).\n" +
            "•  Chinese: the first letter no longer stays as a loose capital (Je → it all goes to pinyin). Only when you set the capital yourself does the letter come out."),
        "13.7.0" to tr("•  Teclado chino de trazos (笔画): las teclas son 一 丨 丿 丶 乛, como en los teclados chinos. Tocas los trazos en el orden en que se escribe el carácter y eliges arriba (los más usados primero). Más de 8,100 caracteres.\n" +
            "•  En chino, las teclas especiales están en chino: 符号 (símbolos), 笔画 / 拼音 (cambiar entre trazos y pinyin), 中文 (volver de los símbolos), y la coma y el punto son ， 。\n" +
            "•  Se activa con la tecla 笔画 del teclado chino, o en Inicio › Idioma › «Chino con trazos».",
            "•  Chinese stroke keyboard (笔画): the keys are 一 丨 丿 丶 乛, like Chinese keyboards. Tap the strokes in the order the character is written and pick it above (most used first). More than 8,100 characters.\n" +
            "•  In Chinese, the special keys are in Chinese: 符号 (symbols), 笔画 / 拼音 (switch between strokes and pinyin), 中文 (back from symbols), and the comma and period are ， 。\n" +
            "•  Turn it on with the 笔画 key on the Chinese keyboard, or in Start › Language › «Chinese with strokes»."),
        "13.6.3" to tr("•  Chino: mientras escribes pinyin, los botones de la barra de arriba se esconden por completo y solo queda la fila de caracteres, limpia.",
            "•  Chinese: while you type pinyin, the top bar buttons hide completely and only the clean character row stays."),
        "13.6.2" to tr("•  Chino arreglado: la fila de caracteres ahora tiene fondo sólido (ya no se ven los botones de atrás encima de los caracteres).\n" +
            "•  En chino ya no se pone la mayúscula sola al empezar: la primera letra también va al pinyin.\n" +
            "•  En chino la coma y el punto del teclado son los chinos: ， y 。 (manteniendo el punto salen ？！：；、).\n" +
            "•  Mejor orden de los caracteres: salen primero los que más se usan (ji → 几, 记, 机…).",
            "•  Chinese fixed: the character row now has a solid background (the buttons behind no longer show over the characters).\n" +
            "•  In Chinese the first letter is no longer capitalized by itself: it also goes to the pinyin.\n" +
            "•  In Chinese the comma and period keys are the Chinese ones: ， and 。 (hold the period for ？！：；、).\n" +
            "•  Better character order: the most used come first (ji → 几, 记, 机…)."),
        "13.6.1" to tr("•  También se traducen «Desarrollador», la palabra «compilación» de la versión, los nombres de los idiomas del teclado (en chino: 西班牙语, 英语…) y las opciones Normal, Zoom y Flash.",
            "•  «Developer», the word «build» in the version, the keyboard language names (in Chinese: 西班牙语, 英语…) and the Normal, Zoom and Flash options are now translated too."),
        "13.6.0" to tr("•  Traducciones revisadas a mano en portugués, francés, italiano, alemán y chino: todos los botones, títulos, secciones, avisos, ayudas y la guía (más de 1,250 textos). Ya no salen frases raras como «Mis sitios» = «mis páginas web».\n" +
            "•  Las secciones del menú (Trabajo, Datos y copiado, Voz y GIFs, Trucos y novedades) ya se traducen.\n" +
            "•  En el calendario, los días y los meses salen en el idioma de la pantalla.",
            "•  Hand-checked translations in Portuguese, French, Italian, German and Chinese: every button, title, section, notice, help text and the guide (more than 1,250 texts). No more odd phrases like «My sites» = «my websites».\n" +
            "•  The menu sections (Work, Data and copy, Voice and GIFs, Tips and news) are now translated.\n" +
            "•  In the calendar, days and months show in the screen language."),
        "13.5.2" to tr("•  Días que trabajas: los días salen en el idioma de la pantalla (en chino 一 二 三 四 五 六 日), igual que «Lun – Vie» y la lista de días.",
            "•  Days you work: the days show in the screen language (in Chinese 一 二 三 四 五 六 日), and so do «Mon – Fri» and the list of days."),
        "13.5.1" to tr("•  Días que trabajas: los círculos de los días caben completos en cualquier pantalla y los botones rápidos quedan en una línea (Lun – Vie, Lun – Sáb, Todos).",
            "•  Days you work: the day circles fit completely on any screen and the quick buttons stay on one line (Mon – Fri, Mon – Sat, Every day)."),
        "13.5.0" to tr("•  Días que trabajas (Secciones › Trabajo › Mi jornada): toca cada día (L M M J V S D) para prenderlo o apagarlo, o usa «Lunes a viernes», «Lunes a sábado» o «Todos». Lo cambias cuando quieras.\n" +
            "•  Los días libres no sale la notificación de Mi jornada, ni el aviso del ticket, ni se anota nada solo con el GPS.\n" +
            "•  Si trabajas un día libre, toca Comencé (en el teclado o el widget) y todo se prende ese día.",
            "•  Days you work (Sections › Work › My workday): tap each day (M T W T F S S) to turn it on or off, or use «Monday to Friday», «Monday to Saturday» or «Every day». Change it whenever you want.\n" +
            "•  On days off there's no My workday notification, no ticket reminder and nothing gets logged by GPS.\n" +
            "•  If you work on a day off, tap Start (in the keyboard or the widget) and everything turns on that day."),
        "13.4.2" to tr("•  Divisas: el botón ↑↓ ahora es redondo y más pequeño, con los colores del diamante. Al tocarlo da un destello: un aro de luz, un brillo que lo cruza, una estrellita y las flechas dan media vuelta.",
            "•  Currency: the ↑↓ button is now round and smaller, in the diamond's colors. Tapping it sparkles: a ring of light, a shine across it, a little star and the arrows spin half a turn."),
        "13.4.1" to tr("•  Divisas: nuevo botón ↑↓ al lado de las monedas para cambiarlas de lugar (da la vuelta al tocarlo).\n" +
            "•  Las teclas de Divisas son más grandes y los números quedan bien centrados (4 filas en vez de 5).",
            "•  Currency: new ↑↓ button next to the currencies to swap them (it spins when you tap it).\n" +
            "•  The Currency keys are bigger and the numbers are properly centered (4 rows instead of 5)."),
        "13.4.0" to tr("•  Divisas (como en Best Notes): toca ⋯ › Divisas. Ves a cuánto está cada moneda del mundo hoy y la conversión al instante.\n" +
            "•  Toca una moneda para cambiarla: arriba las más usadas y abajo todas de la A a la Z, con su nombre.\n" +
            "•  En la cantidad puedes hacer cuentas (50+25×2, 15%) y ⇅ cambia las monedas de lugar. Debajo sale 1 = … en los dos sentidos.\n" +
            "•  «Escribir» pone el resultado donde estás escribiendo: «100 USD = 5,890.00 DOP».\n" +
            "•  Las tasas son del día (se actualizan cada 6 horas) y sin internet usa las últimas guardadas.",
            "•  Currency (like in Best Notes): tap ⋯ › Currency. See what every currency in the world is worth today and convert instantly.\n" +
            "•  Tap a currency to change it: the most used on top and all of them A to Z below, with their names.\n" +
            "•  In the amount you can do math (50+25×2, 15%) and ⇅ swaps the currencies. Below you see 1 = … both ways.\n" +
            "•  «Type it» puts the result where you're typing: «100 USD = 5,890.00 DOP».\n" +
            "•  Rates are from today (updated every 6 hours) and offline it uses the last saved ones."),
        "13.3.1" to tr("•  Arreglado el chino: la fila de caracteres tapaba todo el teclado y no dejaba escribir la segunda letra. Ahora es una fila arriba, del alto de las sugerencias.\n" +
            "•  Con una sola letra (h) ya salen los caracteres más usados (好, 还, 会…).",
            "•  Chinese fixed: the character row covered the whole keyboard and didn't let you type the second letter. Now it's one row at the top, the height of the suggestions.\n" +
            "•  With just one letter (h) the most used characters show up (好, 还, 会…)."),
        "13.3.0" to tr("•  Chino en el teclado: en Idiomas del teclado elige 中文. Escribe el pinyin sin tonos (nihao, xiexie, woaini) y arriba salen los caracteres para tocar (你好, 谢谢, 我爱你). La barra espaciadora pone el primero, Enter deja las letras y borrar quita una letra del pinyin.\n" +
            "•  Escribe frases completas seguidas (nihaoma) y te arma la frase; si eliges solo una parte, sigue con el resto.\n" +
            "•  Lo que más eliges sube al primer lugar. Los signos salen en chino: ，。？！\n" +
            "•  El dictado por voz también funciona en chino.",
            "•  Chinese on the keyboard: in Keyboard languages pick 中文. Type the pinyin without tones (nihao, xiexie, woaini) and the characters show up above to tap (你好, 谢谢, 我爱你). Space puts the first one, Enter keeps the letters and delete removes one pinyin letter.\n" +
            "•  Type whole phrases in a row (nihaoma) and it builds the phrase; if you pick just a part, it keeps going with the rest.\n" +
            "•  What you pick most moves to first place. Punctuation comes out in Chinese: ，。？！\n" +
            "•  Voice typing also works in Chinese."),
        "13.2.1" to tr("•  La rayita de luz debajo del título de las ventanas ahora tiene color hasta la punta y el brillo la recorre completa, de un extremo al otro.",
            "•  The light line under the windows' title now has color all the way to the tip and the shine runs across all of it, end to end."),
        "13.2.0" to tr("•  La app ahora está en 7 idiomas: español, inglés, portugués, francés, italiano, alemán y chino. Ve a Inicio › Idioma › «Idioma de esta pantalla» y elige el tuyo.\n" +
            "•  La primera vez que eliges portugués, francés, italiano, alemán o chino, el teléfono traduce toda la app (necesita internet una vez; tarda menos de un minuto). Después funciona al instante y sin internet.\n" +
            "•  Los ajustes, la guía, Mis datos, los paneles del teclado y los mensajes salen en el idioma que elijas.",
            "•  The app now comes in 7 languages: Spanish, English, Portuguese, French, Italian, German and Chinese. Go to Start › Language › «Language of this screen» and pick yours.\n" +
            "•  The first time you pick Portuguese, French, Italian, German or Chinese, the phone translates the whole app (needs internet once; takes less than a minute). After that it works instantly and offline.\n" +
            "•  Settings, the guide, My data, the keyboard panels and the messages show in the language you pick."),
        "13.1.0" to tr("•  Chino en el traductor: copia un mensaje en chino, toca «Traducir mensaje» y te sale en español. En la barra de traducir, toca el nombre de un idioma para cambiarlo (Español, Inglés, Chino, Portugués, Francés, Italiano o Alemán).\n" +
            "•  «Idiomas del teclado» ahora está en la misma tarjeta de «Idioma», junto al idioma de la pantalla.\n" +
            "•  La rayita de luz debajo del título de las ventanas se traza con una estrellita y después un brillo la recorre.\n" +
            "•  Todas las ventanas para elegir (guardar en Mis datos, opciones de un sitio) tienen el estilo nuevo.\n" +
            "•  La línea del ojito de incógnito ya no tiene el borde negro: es una línea de luz limpia.",
            "•  Chinese in the translator: copy a message in Chinese, tap «Translate message» and you get it in your language. In the translate bar, tap a language name to change it (Spanish, English, Chinese, Portuguese, French, Italian or German).\n" +
            "•  «Keyboard languages» is now in the same «Language» card, next to the screen language.\n" +
            "•  The light line under the windows' title draws in with a little star, then a shine runs along it.\n" +
            "•  All the pick-one windows (save in My data, site options) have the new style.\n" +
            "•  The incognito eye's line no longer has the black outline: it's a clean line of light."),
        "13.0.2" to tr("•  La ventana «¿Para qué vas a usar el teclado?» ahora es bonita: tarjeta oscura con cada opción como un botón grande y la elegida marcada con los colores del diamante.\n" +
            "•  La ventana para elegir el idioma tiene el mismo estilo.\n" +
            "•  «Idiomas del teclado» (español, inglés, portugués, francés, italiano y alemán) ahora está en Inicio, justo debajo del idioma de la pantalla, para encontrarlo fácil.",
            "•  The «What will you use the keyboard for?» window looks nice now: a dark card with each option as a big button and the chosen one marked with the diamond's colors.\n" +
            "•  The language picker has the same style.\n" +
            "•  «Keyboard languages» (Spanish, English, Portuguese, French, Italian and German) is now in Start, right under the screen language, so it's easy to find."),
        "13.0.1" to tr("•  El ojito del modo incógnito ahora tiene animación: al prenderlo, una línea de luz (los colores del diamante) cruza el ojo; al apagarlo, la línea se regresa y el ojo queda descubierto.",
            "•  The incognito eye now animates: turning it on draws a line of light (the diamond's colors) across the eye; turning it off pulls the line back and the eye is uncovered."),
        "13.0.0" to tr("Best Keyboard 13: para cualquier persona.\n" +
            "•  Al instalarlo te pregunta para qué lo usas (chatear, estudiar, oficina o trabajo de campo) y prende solo lo que te sirve. Las herramientas del trabajo (tickets, Mi jornada, sitios…) son un paquete que se puede apagar (Secciones › Inicio).\n" +
            "•  6 idiomas: español, inglés, portugués, francés, italiano y alemán. Eliges tus 2 y cambia solo entre ellos (Secciones › Escribir › Idiomas del teclado).\n" +
            "•  Modo fácil: teclas grandes, alto contraste y vibración fuerte. Con TalkBack dice cada tecla al pasar el dedo.\n" +
            "•  Modo incógnito (en ⋯ y en Datos): no aprende ni guarda nada; se prende solo en las pestañas privadas. La barra espaciadora dice «Incógnito».\n" +
            "•  Mis datos y privacidad: qué guarda el teclado y un botón para borrar cada cosa.\n" +
            "•  Calculadora y conversor: 25*4= → 100, «120 ft a m» → 36.58 m, «70 f a c» → 21.11 °C, «20 usd a pesos» con las tasas de hoy.\n" +
            "•  Atajos con fecha y hora: /hoy, /hora, /ahora; y en tus atajos {fecha}, {hora} y [lo que se llena] seleccionado.\n" +
            "•  Teclado dividido en dos mitades cuando el teléfono está acostado o en tablet (se puede cambiar).",
            "Best Keyboard 13: for everyone.\n" +
            "•  When installed it asks what you use it for (chat, study, office or field work) and turns on only what helps you. Work tools (tickets, My workday, sites…) are a pack you can turn off (Sections › Start).\n" +
            "•  6 languages: Spanish, English, Portuguese, French, Italian and German. Pick your 2 and it switches between them by itself (Sections › Typing › Keyboard languages).\n" +
            "•  Easy mode: big keys, high contrast and strong vibration. With TalkBack it says each key as you slide your finger.\n" +
            "•  Incognito mode (in ⋯ and in Data): it doesn't learn or save anything; turns on by itself in private tabs. The space bar says «Incognito».\n" +
            "•  My data and privacy: what the keyboard saves and a button to delete each thing.\n" +
            "•  Calculator and converter: 25*4= → 100, «120 ft to m» → 36.58 m, «70 f to c» → 21.11 °C, «20 usd to eur» with today's rates.\n" +
            "•  Shortcuts with date and time: /today, /time, /now; and in your shortcuts {date}, {time} and [what you fill in] selected.\n" +
            "•  Split keyboard in two halves in landscape or on tablets (can be changed)."),
        "12.33.2" to tr("•  Copia de seguridad: la contraseña tiene el ojito para verla y revisar que esté bien escrita (en los dos campos, y también al restaurar).",
            "•  Backup: the password has an eye button to show it and check it's typed right (in both fields, and also when restoring)."),
        "12.33.1" to tr("•  Recordatorio de la copia de seguridad: si no tienes la copia automática prendida y hace más de un mes que no haces una, el teclado te lo recuerda una vez al mes, con un botón que te lleva directo a hacerla. En la sección Copia ahora sale la fecha de tu última copia.",
            "•  Backup reminder: if automatic backup is off and you haven't made one in over a month, the keyboard reminds you once a month, with a button that takes you straight there. The Backup section now shows the date of your last backup."),
        "12.33.0" to tr("•  Sin emojis en ninguna pantalla: ajustes, Estilo, Calendario, Mis sitios, Datos, la guía, la notificación de Mi jornada y el widget. Se ve más limpio y moderno. (El ☺️ de la coma y los emojis que tú escribes se quedan.)\n" +
            "•  «Terminar la frase con IA»: si cambias de idioma, el título y los botones se cambian también.",
            "•  No emojis on any screen: settings, Style, Calendar, My sites, Data, the guide, the My workday notification and the widget. Cleaner and more modern. (The comma's ☺️ and the emojis you type stay.)\n" +
            "•  «Finish sentence with AI»: if you switch language, the title and buttons switch too."),
        "12.32.5" to tr("•  💵 Tu cheque en el Calendario: en «Ver y cambiar mis horas» ahora sale debajo de los días cuánto te queda aprox. en el cheque de esta semana (y la pasada), con el desglose de horas, extras y descuentos. Si todavía no pusiste tu pago por hora, ahí mismo hay un botón para ponerlo.",
            "•  💵 Your paycheck in the Calendar: «See and change my hours» now shows, under the days, your approximate take-home pay for this week (and last week), with the breakdown of hours, overtime and deductions. If you haven't set your hourly pay yet, there's a button right there."),
        "12.32.4" to tr("•  Guía rápida al día (26 pasos): sugerencias que te conocen, acentos y signos solos, aprende de ti, números y medidas, IA gratis para terminar la frase, dictado bilingüe, estadísticas y cómo pasar todo a otro teléfono.\n" +
            "•  Revisada la copia de seguridad: guarda todo, incluido lo que el teclado aprendió de ti.",
            "•  Quick guide up to date (26 steps): suggestions that know you, accents and punctuation, it learns from you, numbers and measures, free AI to finish sentences, bilingual dictation, statistics and how to move everything to another phone.\n" +
            "•  Backup checked: it saves everything, including what the keyboard learned from you."),
        "12.32.3" to tr("•  IA: se regresó a pedir de uno en uno (como Best Notes). Preguntar a dos lugares a la vez hacía que el servicio gratis rechazara todo.\n" +
            "•  «Terminar la frase con IA» continúa lo que escribiste («Hola como» → estás? / te fue hoy?).",
            "•  AI: back to one request at a time (like Best Notes). Asking two places at once made the free service reject everything.\n" +
            "•  «Finish sentence with AI» continues what you wrote («Hola como» → estás? / te fue hoy?)."),
        "12.32.2" to tr("•  IA más rápida: le pregunta a la dirección nueva y a la vieja al mismo tiempo y usa la primera que conteste.\n" +
            "•  «Terminar la frase con IA» ahora sí continúa lo que escribiste («Hola como» → estás? / te fue hoy?) en vez de inventar otra frase, y con instrucciones más cortas contesta antes.",
            "•  Faster AI: it asks the new and the old address at the same time and uses the first one that answers.\n" +
            "•  «Finish sentence with AI» now really continues what you wrote («Hola como» → estás? / te fue hoy?) instead of making up another sentence, and with shorter instructions it answers sooner."),
        "12.32.1" to tr("•  IA: la IA gratis (Pollinations) se está cambiando de dirección y la vieja casi no contesta. Ahora el teclado prueba primero la dirección nueva y la vieja solo de respaldo.\n" +
            "•  Arreglado que un intento atorado hacía esperar al siguiente (por eso salía «Pensando 82 s»). Ahora cada vez que tocas empieza enseguida, y en 90 segundos como máximo te dice si no se pudo.",
            "•  AI: the free AI (Pollinations) is moving to a new address and the old one barely answers. The keyboard now tries the new address first and the old one only as a backup.\n" +
            "•  Fixed a stuck attempt making the next one wait (that's why it said «Thinking 82 s»). Now every tap starts right away, and within 90 seconds at most it tells you if it couldn't."),
        "12.32.0" to tr("•  La IA del teclado ahora es la misma de Best Notes (gratis, sin clave) y ya no usa Gemini para nada. Funciona igual que en Best Notes: espera hasta 45 segundos cada respuesta, si el servicio está ocupado lo vuelve a intentar solo (hasta 4 veces) y manda un pedido a la vez. Sirve para Terminar la frase, Más formal, Respuestas con IA, Notas → Ticket, Modo código y todo lo demás.\n" +
            "•  En ajustes se quitó lo de la clave de Gemini: ya no hace falta.",
            "•  The keyboard's AI is now the same one Best Notes uses (free, no key) and it no longer uses Gemini at all. It works like in Best Notes: waits up to 45 seconds for each answer, retries by itself if the service is busy (up to 4 times) and sends one request at a time. Works for Finish sentence, More formal, AI replies, Notes → Ticket, Code mode and everything else.\n" +
            "•  The Gemini key section was removed from settings: it's no longer needed."),
        "12.31.1" to tr("•  Arreglado «Terminar la frase con IA» (y también «Más formal», «Respuestas con IA»…): daba error 404 o se quedaba pensando porque Google le cambió el nombre a sus modelos de Gemini. Ahora el teclado le pregunta a Google cuáles puede usar tu clave y usa el que funcione. También acepta las claves nuevas que empiezan con «AQ.».\n" +
            "•  «Terminar la frase con IA» tiene su propio icono (renglones con una flecha), ya no el mismo que «Más formal».",
            "•  Fixed «Finish sentence with AI» (and «More formal», «AI replies»…): it gave error 404 or kept thinking because Google renamed its Gemini models. Now the keyboard asks Google which ones your key can use and uses the one that works. New keys starting with «AQ.» also work.\n" +
            "•  «Finish sentence with AI» has its own icon (lines with an arrow), no longer the same as «More formal»."),
        "12.31.0" to tr("•  📊 Estadísticas (Secciones › Escribir): cuántas palabras escribiste esta semana, cuántas te adivinó sin escribir ni una letra, cuántas letras te ahorraste y cuántos errores corrigió solo.\n" +
            "•  Cada app recuerda su idioma: si en WhatsApp escribes en español y en la app del trabajo en inglés, el teclado ya abre así en cada una (se puede apagar en Barra espaciadora).\n" +
            "•  Teclas que se acomodan a tu dedo: el teclado aprende si sueles tocar un poquito abajo o a un lado de cada tecla y lo corrige solo (después de unas 150 letras). Se puede apagar en Sugerencias.\n" +
            "•  La copia de seguridad (y la automática de cada semana) ya guarda todo lo que el teclado aprendió de ti: palabras, frases, tus arreglos y las sugerencias que borraste.",
            "•  📊 Statistics (Sections › Typing): how many words you typed this week, how many it guessed before you typed a letter, how many letters you saved and how many mistakes it fixed.\n" +
            "•  Each app remembers its language: if you write Spanish in WhatsApp and English in your work app, the keyboard opens that way in each (can be turned off in Space bar).\n" +
            "•  Keys that adapt to your finger: the keyboard learns if you usually tap a bit below or to the side of each key and corrects it (after about 150 letters). Can be turned off in Suggestions.\n" +
            "•  The backup (and the weekly automatic one) saves everything the keyboard learned from you: words, phrases, your fixes and the suggestions you deleted."),
        "12.30.0" to tr("•  Más rápido al escribir: las sugerencias tardan hasta 40 veces menos cuando escribes algo que no es palabra, y 3 veces menos con 1 o 2 letras. Así no se traba aunque escribas rápido.\n" +
            "•  Dictado: en teléfonos con Android 14 o más nuevo reconoce si hablas español o inglés en cada frase (puedes mezclar sin tocar nada) y pone la puntuación solo. Si dices una pregunta, pone «¿…?» solo: «cómo estás» → ¿Cómo estás?, «what time do you want to meet» → …meet?\n" +
            "•  Portapapeles: cada cosa copiada dice cuándo la copiaste (Hoy 9:41, Ayer, 3 nov). Con la lupa buscas en todo lo copiado.\n" +
            "•  Mi jornada: nuevo botón «Resumen de la semana» dentro del panel, y en el resumen el botón «Mandar» abre WhatsApp, correo o Mensajes con tus horas listas.\n" +
            "•  Revisión: arreglado un cierre que podía pasar si apagabas todos los botones de Mi jornada, y otros detalles internos.",
            "•  Faster typing: suggestions take up to 40 times less when you type something that isn't a word, and 3 times less with 1 or 2 letters. No lag even when typing fast.\n" +
            "•  Dictation: on Android 14 or newer it detects whether you speak Spanish or English in each sentence (mix freely) and adds punctuation by itself. If you say a question, it adds «?» by itself.\n" +
            "•  Clipboard: each copied item shows when you copied it (Today 9:41, Yesterday, Nov 3). Use the magnifier to search everything you copied.\n" +
            "•  My workday: new «Week summary» button inside the panel, and the summary's «Send» button opens WhatsApp, email or Messages with your hours ready.\n" +
            "•  Review: fixed a crash that could happen if you turned off every My workday button, and other internal details."),
        "12.29.0" to tr("•  Cambio de idioma más rápido: ahora cuenta palabras cortas como «I», «hey», «the», «you», «dale», «oye», «ya», «yo». En casi todas las frases cambia en la 2.ª palabra (antes en la 3.ª o 4.ª), escribiendo o tocando sugerencias.\n" +
            "•  Signo de pregunta: si la frase es una pregunta («can you send me…», «¿me mandas…», «puedes…»), a la izquierda sale «?» y se pone pegado a la palabra.\n" +
            "•  Coma antes de «pero» y «but»: «ya quedó listo pero» → ✨ «listo, pero».\n" +
            "•  Mayúsculas inteligentes: las siglas que siempre escribes con mayúscula (NVR, PoE…) se ponen solas.\n" +
            "•  Más emojis según la frase: «on my way» → 🚗, «ya llegué» → 📍, «todo listo» → ✅, «buen trabajo» → 👍, «feliz cumpleaños» → 🎉.\n" +
            "•  Nuevo botón «Terminar la frase con IA» (en ⋯): escribe el principio y te da 3 formas de terminarlo. Solo se manda tu texto cuando tú lo tocas.",
            "•  Faster language switch: it now counts short words like «I», «hey», «the», «you», «dale», «oye». In almost every sentence it switches on the 2nd word (before, the 3rd or 4th), typing or tapping suggestions.\n" +
            "•  Question mark: if the sentence is a question («can you send me…»), «?» shows on the left and goes right after the word.\n" +
            "•  Comma before «but» and «pero»: «it works but» → ✨ «works, but».\n" +
            "•  Smart capitals: acronyms you always capitalize (NVR, PoE…) get capitalized by themselves.\n" +
            "•  More emojis for whole phrases: «on my way» → 🚗, «all done» → ✅, «good job» → 👍.\n" +
            "•  New «Finish sentence with AI» button (in ⋯): type the start and get 3 ways to finish it. Your text is only sent when you tap it."),
        "12.28.0" to tr("•  Frases de fábrica 3 veces más grandes (139.000 frases reales en español e inglés): adivina 4 de cada 10 palabras sin escribir ni una letra y escribes 72% menos letras.\n" +
            "•  Frase completa: cuando lo que sigue es obvio (sobre todo tus frases de siempre), a la derecha sale completo, por ejemplo «I'm on» → my way to the job. Un toque y se escribe todo.\n" +
            "•  Lo reciente pesa más: las palabras que ya no usas van bajando poco a poco.\n" +
            "•  Palabras pegadas en los dos idiomas: thankyou → thank you, alot → a lot, atleast → at least, nosvemos → nos vemos, buenosdias → buenos días, notengo → no tengo.\n" +
            "•  Números, horas y medidas con ✨: 7pm → 7:00 PM (en español 7:00 p. m.), 730am → 7:30 AM, 120ft → 120 ft, 24v → 24 V, cat6 → Cat 6, poe → PoE, nvr → NVR, wifi → Wi-Fi.",
            "•  Built-in phrases 3 times bigger (139,000 real sentences in Spanish and English): it guesses 4 in 10 words without typing a letter and you type 72% fewer letters.\n" +
            "•  Full phrase: when what comes next is obvious (especially your usual phrases), it shows the whole thing on the right, for example «I'm on» → my way to the job. One tap types it all.\n" +
            "•  Recent counts more: words you no longer use slowly go down.\n" +
            "•  Stuck-together words in both languages: thankyou → thank you, alot → a lot, atleast → at least, nosvemos → nos vemos.\n" +
            "•  Numbers, times and measures with ✨: 7pm → 7:00 PM, 730am → 7:30 AM, 120ft → 120 ft, 24v → 24 V, cat6 → Cat 6, poe → PoE, nvr → NVR, wifi → Wi-Fi."),
        "12.27.0" to tr("•  Acentos según la frase: «tu puedes» → ✨ tú puedes, «ya esta» → ya está, «esta listo» → está listo, «el dijo» → él dijo, «se que» → sé que. Y deja igual «tu casa», «esta noche», «el carro». Después de ¿ o ¡ pone el acento solo (¿cómo?, ¿qué?, ¿dónde?). «ya termine» → ya terminé, «yo llegue» → yo llegué (pero «cuando yo llegue» se queda igual).\n" +
            "•  Contracciones en inglés sin el apóstrofo: dont → don't, im → I'm, cant → can't, thats → that's, didnt → didn't…\n" +
            "•  Aprende de tus arreglos: si borras una palabra mal escrita y la escribes bien, la próxima vez se corrige sola. Los ves (y puedes quitarlos) en «Ver y editar mis palabras».\n" +
            "•  Sabe dónde tocó tu dedo: si tocas entre dos teclas, considera las dos letras (tecka → tecla).\n" +
            "•  La primera palabra de una frase usa el idioma de lo último que escribiste ahí.\n" +
            "•  Nombres de tus contactos bien escritos y con mayúscula (Ajustes › Escribir › Sugerencias). Nada sale del teléfono.\n" +
            "•  Deslizar el dedo para escribir ahora también usa la frase, tus frases y los nombres de tus contactos.",
            "•  Accents by sentence (Spanish): «tu puedes» → ✨ tú puedes, «ya esta» → ya está, «el dijo» → él dijo; «tu casa», «esta noche» stay the same. After ¿ or ¡ it adds the accent by itself. «ya termine» → ya terminé.\n" +
            "•  English contractions without the apostrophe: dont → don't, im → I'm, cant → can't, thats → that's, didnt → didn't…\n" +
            "•  Learns from your fixes: if you delete a misspelled word and type it right, next time it fixes itself. See (and remove) them in «See and edit my words».\n" +
            "•  Knows where your finger landed: if you tap between two keys, it considers both letters.\n" +
            "•  The first word of a sentence uses the language of what you last wrote there.\n" +
            "•  Your contacts' names spelled right and capitalized (Settings › Typing › Suggestions). Nothing leaves the phone.\n" +
            "•  Swipe typing now also uses the sentence, your phrases and your contacts' names."),
        "12.26.0" to tr("•  Adivina la palabra siguiente desde el primer día, en inglés y en español: «thank you» → so much, «I will be there in» → a few minutes, «muchas» → gracias, «no te» → preocupes. Antes solo adivinaba lo que tú ya habías escrito.\n" +
            "•  Ahora recuerda las 2 palabras anteriores (no solo 1): aprende tus frases completas y te las va completando. Lo que tú escribes siempre pesa más que lo de fábrica.\n" +
            "•  En pruebas con frases normales: adivina 1 de cada 3 palabras sin escribir ni una letra, y escribes 66% menos letras.\n" +
            "   (Frases de ejemplo de tatoeba.org, licencia CC-BY 2.0.)",
            "•  Guesses the next word from day one, in English and Spanish: «thank you» → so much, «I will be there in» → a few minutes, «no te» → preocupes. Before, it only guessed what you had already typed.\n" +
            "•  It now remembers the 2 previous words (not just 1): it learns your full phrases and completes them for you. What you type always counts more than the built-in ones.\n" +
            "•  In tests with normal sentences: it guesses 1 in 3 words without typing a single letter, and you type 66% fewer letters.\n" +
            "   (Example sentences from tatoeba.org, CC-BY 2.0 license.)"),
        "12.25.0" to tr("•  Sugerencias en inglés mucho mejores: el teclado ahora mira el idioma de la frase que estás escribiendo. Si empiezas «the camera…» con el teclado en español, desde la segunda palabra ya te sugiere y corrige en inglés (antes «chec» salía «chef» y «camer» salía «comer»). Y al revés: escribiendo en español con el teclado en inglés.\n" +
            "•  Menos nombres de películas en las sugerencias (thomas, adam, todd…): ahora salen al final.",
            "•  Much better English suggestions: the keyboard now looks at the language of the sentence you're writing. If you start «the camera…» with the keyboard in Spanish, from the second word it already suggests and corrects in English (before, «chec» became «chef»). And the other way: typing Spanish with the keyboard in English.\n" +
            "•  Fewer movie names in suggestions (thomas, adam, todd…): they now go last."),
        "12.24.0" to tr("•  Sugerencias mucho más inteligentes:\n" +
            "   –  Diccionario 3 veces más grande: 140.000 palabras en español y 85.000 en inglés, con las formas de los verbos que faltaban (actualizo, actualicé, reinicié, conecté…).\n" +
            "   –  Si escribes algo que no es palabra («actulizo», «actuliso»), te sale «¿quisiste decir…?» con las palabras más parecidas: por una letra, por cómo suena (s/z, b/v, h) o porque empiezan casi igual.\n" +
            "   –  Ya no aprende tan fácil los errores de dedo: si escribes algo como «clmo» y existe «como», hay que escribirlo 4 veces para que lo aprenda. Las palabras raras que ya había aprendido así se esconden solas.\n" +
            "   –  Lo que aprendió de ti NO se borra al actualizar: tus palabras, cuántas veces las usas y ahora también lo que escribiste hace poco.",
            "•  Much smarter suggestions:\n" +
            "   –  A 3 times bigger dictionary: 140,000 Spanish words and 85,000 English words, with the verb forms that were missing.\n" +
            "   –  If you type something that isn't a word, you get «did you mean…?» with the closest words: by one letter, by how it sounds, or because they start almost the same.\n" +
            "   –  It no longer learns typos so easily: if you type something like «clmo» and «como» exists, you have to type it 4 times for it to learn it. Odd words it already learned that way hide by themselves.\n" +
            "   –  What it learned from you is NOT erased when you update: your words, how often you use them and now also what you typed recently."),
        "12.23.3" to tr("•  Borrar sugerencias: deja presionada una sugerencia (por ejemplo una palabra mal escrita que aprendió) y toca «Borrar». No vuelve a salir ni en las sugerencias ni en el autocorrector. En «Ver y editar mis palabras» ves las que borraste y puedes devolverlas con ↶.",
            "•  Delete suggestions: press and hold a suggestion (for example a misspelled word it learned) and tap «Delete». It won't show up again in suggestions or autocorrect. In «See and edit my words» you can see the ones you deleted and bring them back with ↶."),
        "12.23.2" to tr("•  Cambio de idioma solo, también con las sugerencias: si eliges 2 palabras seguidas del otro idioma tocando las sugerencias (sin escribirlas), el teclado se cambia solo a ese idioma, con la luz en la barra espaciadora. Y esas palabras se aprenden en su idioma correcto, para que no se mezclen.",
            "•  Automatic language switch, also with suggestions: if you pick 2 words in a row in the other language by tapping the suggestions (without typing them), the keyboard switches to that language by itself, with the light on the space bar. And those words are learned in their right language, so they don't get mixed."),
        "12.23.1" to tr("•  Guía rápida al día: ahora tiene 22 pasos con todo lo nuevo (cambio de idioma solo, pegar con la ✕, deshacer, Respuestas con IA, Mis sitios, Modo manejo, herramientas del trabajo, Datos con huella, palabra de la semana, estilo y el panel de secciones), en español y en inglés.",
            "•  Updated quick guide: it now has 22 steps with everything new (automatic language switch, paste with the ✕, undo, AI replies, My sites, Driving mode, work tools, Data with fingerprint, word of the week, style and the sections panel), in Spanish and English."),
        "12.23.0" to tr("•  Ajustes más limpios: ahora se ve una sola sección a la vez. Toca la barrita «Secciones ›» debajo del buscador, o la rayita de luz en el borde derecho de la pantalla, y se abre un panel Edge (como el de Samsung) con todas las secciones: Inicio, Escribir, IA y trabajo, Mi jornada, Datos y copiado, Voz y GIFs, Palabra, Copia y Trucos. El buscador sigue buscando en todo.",
            "•  Cleaner settings: now you see one section at a time. Tap the «Sections ›» bar under the search box, or the light strip on the right edge of the screen, and an Edge panel (like Samsung's) opens with every section: Start, Typing, AI and work, My workday, Data and copy, Voice and GIFs, Word, Backup and Tips. The search box still searches everything."),
        "12.22.5" to tr("•  📚 Palabra de la semana: ahora hay 105 palabras (antes 35), una cada lunes por 2 años, todas con su significado, ejemplo y traducción al inglés.",
            "•  📚 Word of the week: now there are 105 words (was 35), one every Monday for 2 years, all with meaning, example and English translation."),
        "12.22.4" to tr("•  Se quitó «Dictar reporte» de la barra de arriba: ya está dentro del micrófono.", "•  «Dictate report» was removed from the top bar: it's already inside the microphone."),
        "12.22.3" to tr("•  🔐 Datos en el teclado pide tu huella cada vez que tocas el candado; adentro, tus correos, usuarios y contraseñas se escriben sin pedirla otra vez.",
            "•  🔐 Data on the keyboard asks for your fingerprint every time you tap the lock; inside, your emails, usernames and passwords are typed without asking again."),
        "12.22.2" to tr("•  🔐 Datos en el teclado pide tu huella apenas lo abres; después entras libre a tus cuentas y datos.",
            "•  🔐 Data on the keyboard asks for your fingerprint as soon as you open it; then you can freely open your accounts and items."),
        "12.22.1" to tr("•  🔐 Datos en el teclado ahora es igual que en los ajustes: la lista de cuentas con su flecha; tocas una, pones tu huella y tocas el dato para escribirlo. Se quitaron los repetidos.",
            "•  🔐 Data on the keyboard now works like in settings: the account list with its arrow; tap one, use your fingerprint and tap the item to type it. Duplicates were removed."),
        "12.22.0" to tr("•  🔐 Datos nuevo, como el administrador de contraseñas de Google: una lista de cuentas (página o app) con buscador. Toca una, pon tu huella y ves su usuario, correo, contraseña, preguntas de seguridad, clave de seguridad y PIN, con ojito para ver y botón para copiar (lo copiado se borra solo al minuto). Puedes crear contraseñas seguras. Lo que tenías guardado se juntó solo en cuentas.",
            "•  🔐 New Data, like Google's password manager: a list of accounts (website or app) with search. Tap one, use your fingerprint and see its username, email, password, security questions, security key and PIN, with an eye to see and a copy button (copies clear by themselves after a minute). You can create strong passwords. What you had saved was grouped into accounts automatically."),
        "12.21.1" to tr("•  ↶ La tecla Deshacer ahora está a la derecha del 0, y su flecha es una flecha de verdad que da la vuelta (con su punta).",
            "•  ↶ The Undo key is now to the right of 0, and its arrow is a real arrow that goes around (with its tip)."),
        "12.21.0" to tr("•  🌸 IA de respaldo gratis (la misma de Best Notes, sin clave): si Gemini no contesta o no tienes clave, Respuestas con IA, Más formal, Notas → Ticket, Revisar, el dictado en inglés y el Modo código le preguntan a esta IA. Ya casi nunca se queda sin respuesta.",
            "•  🌸 Free backup AI (the same as Best Notes, no key): if Gemini doesn't answer or you have no key, AI replies, More formal, Notes → Ticket, Review, English dictation and Code mode ask this AI instead. It almost never goes unanswered now."),
        "12.20.1" to tr("•  «Respuestas con IA» ya no se queda pensando: cuenta los segundos y, si a los 25 segundos no contestó, te dice qué revisar.",
            "•  «AI replies» no longer stays thinking: it counts the seconds and, if it hasn't answered after 25 seconds, tells you what to check."),
        "12.20.0" to tr("•  ↩️ Deshacer ahora es una tecla del teclado (al lado del punto) y se quitó de la barra de arriba. Cuando borras algo, la tecla se ilumina con la luz para recordarte que puedes recuperarlo.",
            "•  ↩️ Undo is now a key on the keyboard (next to the period) and was removed from the top bar. When you delete something, the key lights up to remind you that you can get it back."),
        "12.19.2" to tr("•  Arreglado: «Respuestas con IA» (y lo demás con IA) se quedaba pensando. Ahora la IA contesta en segundos (sin «pensar» largo), usa primero el modelo que ya te funcionó, no se queda en fila detrás de otra pregunta y, si se tarda más de 45 segundos, te avisa en vez de quedarse pensando.",
            "•  Fixed: «AI replies» (and other AI features) stayed thinking. The AI now answers in seconds (no long «thinking»), tries first the model that already worked, doesn't wait in line behind another question and, if it takes more than 45 seconds, tells you instead of staying stuck."),
        "12.19.1" to tr("•  El teclado vuelve a bajarse como antes (se quitó «Mantener el teclado abierto»).", "•  The keyboard goes down like before again («Keep the keyboard open» was removed)."),
        "12.19.0" to tr("•  Mantener el teclado abierto: si tocas algo para seleccionarlo, el teclado ya no se baja solo; se baja con el botón Bajar o la flecha atrás (lo apagas en Ajustes).\n•  «Respuestas rápidas» (la de la IA) ahora se llama «Respuestas con IA», para no confundirla con «Mensajes rápidos». Mientras piensa, sale el diamante con su luz.",
            "•  Keep the keyboard open: if you tap something to select it, the keyboard no longer goes down by itself; it goes down with the Hide button or the back arrow (turn it off in Settings).\n•  «Quick replies» (the AI one) is now called «AI replies», so it isn't confused with «Quick messages». While it thinks, the diamond shows with its light."),
        "12.18.2" to tr("•  Modo manejando: el texto de los botones grandes sube un poco (ya no toca el borde de abajo).", "•  Driving mode: the big buttons' text moves up a bit (no longer touches the bottom edge)."),
        "12.18.1" to tr("•  Modo manejando: los íconos de los botones grandes quedan centrados con su texto; el del título va pegado al título, y los de Borrar letra y Borrar todo son más grandes y van junto a su texto.",
            "•  Driving mode: the big buttons' icons are centered with their text; the title icon sits next to the title, and Delete letter / Delete all icons are bigger and next to their text."),
        "12.18.0" to tr("•  Sin emojis en el teclado: los paneles, botones, avisos y barras de arriba ahora usan íconos pequeños como los de la barra de arriba (el emoji de la coma se queda). Íconos nuevos: teclado, casa, dinero, aviso y destello.",
            "•  No emojis on the keyboard: panels, buttons, notices and top bars now use small icons like the top bar ones (the comma emoji stays). New icons: keyboard, home, money, warning and sparkle."),
        "12.17.4" to tr("•  ↩️ Modo manejando: después de «Borrar todo», ese mismo botón se vuelve «Deshacer» por 8 segundos para recuperar lo que borraste.",
            "•  ↩️ Driving mode: after «Delete all», that same button becomes «Undo» for 8 seconds to get back what you deleted."),
        "12.17.3" to tr("•  🛻 Modo manejando: botones nuevos «⌫ Borrar letra» (mantén presionado para borrar seguido) y «🗑️ Borrar todo». «Mensaje de estado» ahora se llama «Mensajes rápidos».",
            "•  🛻 Driving mode: new «⌫ Delete letter» (hold to keep deleting) and «🗑️ Delete all» buttons. «Status message» is now called «Quick messages»."),
        "12.17.2" to tr("•  🎨 Las barras de arriba (búsqueda, traducir, revisar, código) ya no son grises: tienen un degradado con el color de tu tema y la línea de luz. Puedes elegir su color en Estilo → Paneles y botones.",
            "•  🎨 The top bars (search, translate, check, code) are no longer grey: they have a gradient with your theme color and the light line. Pick their color in Style → Panels and buttons."),
        "12.17.1" to tr("•  ✨ La barra de búsqueda (Google, emojis, GIFs, convertidor…) es más bonita: borde de colores, luz que le da vueltas, fondo en degradado y línea de luz.",
            "•  ✨ The search bar (Google, emojis, GIFs, converter…) looks nicer: colored border, a light circling it, gradient background and light line."),
        "12.17.0" to tr("•  🔎 Buscar en Google (en ⋯ Más opciones): escribe en la barra y Enter abre Google con tu búsqueda. Si seleccionaste texto, lo busca de una vez.",
            "•  🔎 Search Google (in ⋯ More options): type in the bar and Enter opens Google with your search. If you selected text, it searches it right away."),
        "12.16.5" to tr("•  🇺🇸 En inglés, al escribir «ill», «id», «well», «its»… te sugiere primero «I'll», «I'd», «we'll», «it's»… (son palabras reales, por eso no salen en rojo).",
            "•  🇺🇸 In English, typing «ill», «id», «well», «its»… first suggests «I'll», «I'd», «we'll», «it's»… (they're real words, so they're not underlined)."),
        "12.16.4" to tr("•  💬 Mientras escribes una palabra salen sus sugerencias aunque haya «Pegar» o «Captura» (esas vuelven al terminar la palabra).\n•  🔴 Las palabras subrayadas en rojo se revisan en el idioma del teclado (en inglés ya no te da palabras en español).\n•  🔧 Toca en medio de una palabra mal escrita: sus correcciones salen bonitas arriba del teclado; toca una y se cambia la palabra entera.",
            "•  💬 While you type a word its suggestions show even if «Paste» or «Screenshot» is there (those come back when the word is done).\n•  🔴 Red-underlined words are checked in the keyboard's language (in English it no longer gives Spanish words).\n•  🔧 Tap inside a misspelled word: its corrections show nicely above the keyboard; tap one and the whole word changes."),
        "12.16.3" to tr("•  🛻 Al mandar un mensaje de estado, el modo manejando se queda.", "•  🛻 Sending a status message keeps driving mode on."),
        "12.16.2" to tr("•  🛻 El modo manejando ya no se quita solo: después de dictar, marcar tu jornada o cerrar el teclado, vuelve al modo manejando. Se quita solo con «⌨️ Teclado», la flecha ← o tocando su botón otra vez.",
            "•  🛻 Driving mode no longer turns off by itself: after dictating, logging your workday or closing the keyboard, it comes back to driving mode. It only turns off with «⌨️ Keyboard», the ← arrow or tapping its button again."),
        "12.16.1" to tr("•  🌐 El idioma se cambia solo con 2 palabras seguidas (antes 3).\n•  🇺🇸 En inglés el teclado es de inglés de verdad: sin la ñ en su fila (la sacas manteniendo la n).",
            "•  🌐 The language now switches after 2 words in a row (was 3).\n•  🇺🇸 In English the keyboard is truly English: no ñ in its row (hold n to get it)."),
        "12.16.0" to tr("•  📏 Distancia de detección de los lugares (en Mis sitios): una para todos y, tocando un lugar, la suya propia viendo el círculo en un mapa (como Samsung Rutinas).\n•  🎯 Sensibilidad: Alta (1 minuto adentro y avisa al instante), Normal (3 minutos) o Baja (6 minutos, menos batería).",
            "•  📏 Detection distance for places (in My sites): one for all and, by tapping a place, its own while seeing the circle on a map (like Samsung Routines).\n•  🎯 Sensitivity: High (1 minute inside and reacts right away), Normal (3 minutes) or Low (6 minutes, less battery)."),
        "12.15.1" to tr("•  ⏰ Arreglado: el aviso de las 8 horas en el sitio no llegaba (se confundía con el aviso viejo y se perdía al actualizar). Ahora también llega con el teléfono dormido.",
            "•  ⏰ Fixed: the 8-hours-on-site notice didn't arrive (it got mixed with the old notice and was lost when updating). It now also arrives with the phone asleep."),
        "12.15.0" to tr("•  ✕ Las sugerencias de arriba (pegar, capturas, grabaciones) tienen una ✕ chiquita. Ahora se quedan aunque sigas escribiendo, hasta que las pegues o toques la ✕ (lo copiado sigue en tu portapapeles).",
            "•  ✕ The top suggestions (paste, screenshots, recordings) have a small ✕. They now stay even while you type, until you paste them or tap the ✕ (what you copied stays in your clipboard)."),
        "12.14.1" to tr("•  ⏰ El aviso de «ya llevas 8 horas» ahora cuenta solo el tiempo en el sitio de trabajo (lo manejando no cuenta).",
            "•  ⏰ The «you've worked 8 hours» notice now counts only time on the job site (driving doesn't count)."),
        "12.14.0" to tr("•  🎯 Sugerencias más precisas: ya casi no se mezcla el español con el inglés; el teclado recuerda en qué idioma usas cada palabra.\n" +
                "•  🧠 Aprende más rápido: tus palabras nuevas se aprenden con escribirlas 2 veces, y lo que escribiste hace poco sale primero.\n" +
                "•  🌐 Cambio de idioma solo: si escribes 3 palabras seguidas en el otro idioma, el teclado se cambia solo (con la luz en la barra espaciadora). Lo apagas en Ajustes.\n" +
                "•  ✅ El botón de revisar ahora va directo a «Corregir gramática».",
            "•  🎯 More accurate suggestions: Spanish and English barely mix now; the keyboard remembers which language you use each word in.\n" +
                "•  🧠 Learns faster: your new words are learned after typing them twice, and what you typed recently comes first.\n" +
                "•  🌐 Automatic language switch: if you type 3 words in a row in the other language, the keyboard switches by itself (with the light on the space bar). Turn it off in Settings.\n" +
                "•  ✅ The check button now goes straight to «Fix grammar»."),
        "12.13.4" to tr("•  ⭐ La estrella de la luz de arriba es un poco más grande.", "•  ⭐ The star of the top light is a bit bigger."),
        "12.13.3" to tr("•  ⭐ La luz de arriba llega hasta el final de la derecha y ahí sale una estrella mediana.", "•  ⭐ The top light reaches the far right and a medium star appears there."),
        "12.13.2" to tr("•  ✨ Las sugerencias de arriba (pegar, capturas y grabaciones) son más bonitas: vidrio con brillo, borde de color y aro de colores en la miniatura, y todas tienen la luz dando vueltas.\n•  ⭐ La luz de arriba vuelve a ir hacia la derecha y termina en una estrellita completa, sin cortarse.",
            "•  ✨ The top suggestions (paste, screenshots and recordings) look nicer: glass with shine, colored border and a colored ring on the thumbnail, and all have the light circling them.\n•  ⭐ The top light goes right again and ends in a small, complete star."),
        "12.13.1" to tr("•  📋 La sugerencia de pegar tiene la luz dando vueltas (como Shift fijo).\n•  🌐 Al cambiar de idioma, la luz le da la vuelta a la barra espaciadora y destella (como Enter).\n•  ⭐ La luz de arriba ahora va por el borde, de derecha a izquierda, y termina en una estrella gorda y grande a la izquierda.",
            "•  📋 The paste suggestion has the light circling it (like caps lock).\n•  🌐 When you change language, the light goes around the space bar and sparkles (like Enter).\n•  ⭐ The top light now runs along the edge, right to left, and ends in a big, bold star on the left."),
        "12.13.0" to tr("•  ⭐ La estrella del final de la luz de arriba ahora es más grande y está centrada en la línea.\n" +
                "•  ⇧ Mayúsculas fijas: la luz da vueltas en la tecla Shift mientras estén prendidas.\n" +
                "•  📋 Al copiar algo, el botón del portapapeles destella.\n" +
                "•  🌐 Al cambiar de idioma, la luz cruza la barra espaciadora.\n" +
                "•  🏁 Al terminar tu día, una celebración con el diamante y tu resumen.\n" +
                "•  ⏰ Al llegar a 40 horas en la semana, un destello especial.\n" +
                "•  ✨ Tema nuevo «Diamante» (categoría 💎 Best Keyboard): chispas de luz que trazan teclas al azar.\n" +
                "•  🔘 Los botones grandes de la app tienen un reflejo de luz que los cruza de vez en cuando.",
            "•  ⭐ The star at the end of the top light is now bigger and centered on the line.\n" +
                "•  ⇧ Caps lock: the light circles the Shift key while it's on.\n" +
                "•  📋 When you copy something, the clipboard button sparkles.\n" +
                "•  🌐 When you change language, the light crosses the space bar.\n" +
                "•  🏁 When you finish your day, a celebration with the diamond and your summary.\n" +
                "•  ⏰ When you reach 40 hours in the week, a special sparkle.\n" +
                "•  ✨ New «Diamond» theme (💎 Best Keyboard category): sparks of light trace random keys.\n" +
                "•  🔘 The big app buttons have a light reflection that crosses them now and then."),
        "12.12.0" to tr("•  ✨ La luz del logo en todas partes:\n" +
                "   ⏎ Al tocar Enter, la luz le da la vuelta a la tecla y destella.\n" +
                "   ⌨️ Al abrir el teclado, la luz cruza la parte de arriba.\n" +
                "   🤖 Cuando la IA o la traducción terminan, la luz sale del centro hacia los lados.\n" +
                "   ⏳ Mientras esperas (traducir, IA, copia de seguridad, comprimir video) sale el diamante con la luz dando vueltas.\n" +
                "   📍 En Mi jornada, lo que acabas de anotar se ilumina para que sepas que sí quedó.\n" +
                "   💎 Cada pantalla de la app tiene el diamante al lado del título, con su luz.",
            "•  ✨ The logo light everywhere:\n" +
                "   ⏎ When you tap Enter, the light goes around the key and sparkles.\n" +
                "   ⌨️ When the keyboard opens, the light crosses the top.\n" +
                "   🤖 When the AI or a translation finishes, the light runs from the center to the sides.\n" +
                "   ⏳ While you wait (translate, AI, backup, video compression) the diamond shows with its light spinning.\n" +
                "   📍 In My workday, what you just logged lights up so you know it was saved.\n" +
                "   💎 Every app screen has the diamond next to the title, with its light."),
        "12.11.0" to tr("•  💎 El botón de Ajustes del teclado ahora es el diamante del logo, con sus colores y la misma línea de luz que lo traza y destella de vez en cuando.",
            "•  💎 The keyboard's Settings button is now the logo diamond, with its colors and the same line of light that traces it and sparkles now and then."),
        "12.10.2" to tr("•  ☄️ La luz del logo ahora es una línea de luz que va trazando el borde del diamante; al terminar, todo el borde brilla, destella la punta y se apaga despacio.",
            "•  ☄️ The logo light is now a line of light that traces the diamond's edge; when it finishes, the whole edge glows, the tip sparkles and it fades out slowly."),
        "12.10.1" to tr("•  🛠️ Arreglado: la app se cerraba al empezar el destello del logo.", "•  🛠️ Fixed: the app closed when the logo sparkle started."),
        "12.10.0" to tr("•  ☄️ El logo de arriba ahora tiene un cometa de luz que recorre el borde del diamante y termina con un destello en la punta.\n•  💎 Un solo ícono: el diamante sobre el teclado con el nombre (los otros se quitaron).",
            "•  ☄️ The logo at the top now has a comet of light that runs along the diamond's edge and ends with a sparkle at the tip.\n•  💎 One icon only: the diamond over the keyboard with the name (the others were removed)."),
        "12.9.0" to tr("•  🎨 Paneles y botones a tu gusto (Estilo → Paneles y botones): cambia el color del borde y la luz, de los botones y de los íconos, el grosor del borde y qué tan transparente es el vidrio. Se ve en vivo.",
            "•  🎨 Panels and buttons your way (Style → Panels and buttons): change the border and light color, the buttons and the icons, the border thickness and how clear the glass is. Live preview."),
        "12.8.0" to tr("•  ✨ Todo lo gris ahora es bonito: tarjetas y botones (como «Traducir mensaje») tienen brillo, borde fino del color de tu tema y, con fondo propio, son de vidrio.\n" +
                "•  🔢 Teclado IP / MAC: números grandes con punto, dos puntos y A–F para escribir 192.168.1.20 o 00:1A:2B:3C:4D:5E. «ABC» vuelve a las letras.\n" +
                "•  🏷️ Etiquetas en serie: escribe «PP1-01 a PP1-24» o «CAM-1 x 12» y te escribe la lista completa.\n" +
                "•  🧭 ¿Cuántos pies por línea? (en Convertidor): di el camino en pies, pasos o placas del techo, y la altura del techo; te dice los pies de cada línea, las cajas de 1000 y si pasa del límite de 328 pies.\n" +
                "•  📍 Recordar al llegar: «cuando llegue a este sitio, recuérdame llevar la escalera».\n" +
                "•  ↩️ Deshacer: si pegaste algo o la IA cambió tu texto, lo devuelve como estaba (otro toque lo rehace).",
            "•  ✨ Everything grey is now pretty: cards and buttons (like «Translate message») have a shine, a thin border in your theme color and, with your own background, they're glass.\n" +
                "•  🔢 IP / MAC keypad: big numbers with dot, colon and A–F to type 192.168.1.20 or 00:1A:2B:3C:4D:5E. «ABC» goes back to letters.\n" +
                "•  🏷️ Serial labels: type «PP1-01 to PP1-24» or «CAM-1 x 12» and it types the whole list.\n" +
                "•  🧭 How many feet per line? (in Converter): give the path in feet, steps or ceiling tiles, and the ceiling height; it tells you the feet per line, the 1000 ft boxes and if it's over the 328 ft limit.\n" +
                "•  📍 Remind on arrival: «when I get to this site, remind me to bring the ladder».\n" +
                "•  ↩️ Undo: if you pasted something or the AI changed your text, it puts it back (tap again to redo)."),
        "12.7.3" to tr("•  ✨ «Personalizar» (el lápiz ✎) más bonito: ya no tiene la franja oscura, la barra de arriba es de vidrio con borde y línea de luz, y los íconos son de color.",
            "•  ✨ Nicer «Customize» (the pencil ✎): no more dark strip, the top bar is glass with a border and light line, and the icons are colored."),
        "12.7.2" to tr("•  ✨ «Más opciones» más bonito: los botones ahora se ven como las teclas, con un brillo suave, borde fino del color de tu tema e íconos de color. Con tu foto o video de fondo son de vidrio y se ve el fondo a través. Al tocarlos se iluminan.",
            "•  ✨ Nicer «More options»: the buttons now look like the keys, with a soft shine, a thin border in your theme color and colored icons. With your own photo or video background they're glass and you can see the background through them. They light up when tapped."),
        "12.7.1" to tr("•  💎 El ícono de la app ahora trae el nombre «Best Keyboard» debajo del diamante, con la línea de luz. Si prefieres el de antes, está en Estilo → Ícono de la app.",
            "•  💎 The app icon now shows the name «Best Keyboard» under the diamond, with the light line. If you prefer the old one, it's in Style → App icon."),
        "12.7.0" to tr("•  💎 La pantalla principal ahora tiene el logo nuevo: el diamante sobre el teclado, «Best Keyboard» con su degradado y la línea de luz debajo.",
            "•  💎 The main screen now shows the new logo: the diamond over the keyboard, «Best Keyboard» with its gradient and the light line below."),
        "12.6.2" to tr("•  ✨ Ícono nuevo de fábrica «Diamante sobre el teclado»: el diamante de cristal parado en la línea de luz azul, con un teclado de cristal en perspectiva y su reflejo.",
            "•  ✨ New default icon «Diamond on the keyboard»: the crystal diamond standing on the blue light line, with a glass keyboard in perspective and its reflection."),
        "12.6.1" to tr("•  ✨ Ícono nuevo «Diamante de luz»: el diamante de cristal cruzado por la línea de luz. Es el de fábrica; en Estilo → Ícono de la app puedes elegir también Línea de luz o Diamante.",
            "•  ✨ New «Light diamond» icon: the crystal diamond crossed by the light line. It's the default; in Style → App icon you can also pick Light line or Diamond."),
        "12.6" to tr("•  ✨ Ícono nuevo de la app: «Línea de luz». En Estilo → Ícono de la app también puedes elegir «Diamante».",
            "•  ✨ New app icon: «Light line». In Style → App icon you can also pick «Diamond»."),
        "12.5.2" to tr("•  🏠 Paradas en casa: al llegar a la casa con el día abierto se espera un rato (20 min) antes de cerrarlo. Si solo paraste y te vas antes, tu día sigue abierto.\n•  Si ese día recogiste la camioneta en el parqueo, en la casa solo te pregunta «¿Terminó tu día?».",
            "•  🏠 Stops at home: when you get home with the day open, it waits a while (20 min) before closing it. If you just stopped and leave before, your day stays open.\n•  If you picked up the truck at the parking lot that day, at home it only asks «Did your day end?»."),
        "12.5.1" to tr("•  🏠 Si te recogen en tu casa: al salir de la casa sin haber empezado el día, te pregunta «¿Empezó tu día?» y, si dices que sí, empieza a la hora en que saliste.",
            "•  🏠 If you get picked up at home: when you leave home without having started the day, it asks «Did your day start?» and, if you say yes, it starts at the time you left."),
        "12.5" to tr("•  🛻 Parqueo de la camioneta (Mis sitios): guarda donde recoges y dejas la camioneta. Al salir de ahí empieza tu día solo, y al volver y quedarte, termina solo (con «Deshacer» y «Cambiar hora»).\n•  Toca un sitio guardado para cambiarle el pago o convertirlo en parqueo.",
            "•  🛻 Truck parking (My sites): save where you pick up and drop off the truck. When you leave it your day starts by itself, and when you come back and stay, it ends by itself (with «Undo» and «Change time»).\n•  Tap a saved site to change its pay or turn it into the parking."),
        "12.4.5" to tr("•  La ✕ del aviso «Hubo un error» ahora sí lo quita.",
            "•  The ✕ on the «There was an error» card now closes it."),
        "12.4.4" to tr("•  Arreglado: la app se cerraba al cambiar la hora con «🕐 Reloj». Ahora sale un reloj redondo moderno.\n•  El calendario (días, meses y resumen) sale completo en el idioma de la app.",
            "•  Fixed: the app closed when changing the time with «🕐 Clock». Now a modern round clock shows up.\n•  The calendar (days, months and summary) is fully in the app's language."),
        "12.4.3" to tr("•  Botón «🕐 Ver y cambiar mis horas» en la app (Mi jornada).\n•  Tocar el aviso de Mi jornada abre tus horas de hoy para cambiarlas.",
            "•  «🕐 See and change my hours» button in the app (My workday).\n•  Tapping the My workday notification opens today's hours so you can change them."),
        "12.4.2" to tr("•  Salida automática más segura: espera unos minutos (5, 10, 15 o 20) antes de anotarla; si vuelves a entrar antes, no se anota.\n•  Los avisos de llegada y salida automáticas traen «🕐 Cambiar hora».\n•  En el Calendario, toca cualquier hora de un día para cambiarla, o ✕ para borrarla.",
            "•  Safer automatic departure: it waits a few minutes (5, 10, 15 or 20) before logging it; if you come back in, it isn't logged.\n•  Automatic arrival and departure alerts now have «🕐 Change time».\n•  In the Calendar, tap any time of a day to change it, or ✕ to delete it."),
        "12.4.1" to tr("•  Aviso de Mi jornada más claro: el reloj cuenta tus horas trabajadas (ya sin el almuerzo).\n•  Llegada y salida automáticas: si el GPS brinca y marca una «salida» falsa pero sigues en el sitio, se borra sola.",
            "•  Clearer My workday notification: the clock counts your worked hours (lunch already taken out).\n•  Automatic arrival and departure: if GPS jumps and logs a false «departure» but you're still on site, it's removed by itself."),
        "12.4" to tr("•  🔊 Escuchar texto: selecciona una palabra o frase (o cópiala) y toca 🔊 en la barra de arriba; el teléfono la lee en inglés o en español, también más despacio.",
            "•  🔊 Listen to text: select a word or phrase (or copy it) and tap 🔊 on the top bar; the phone reads it in English or Spanish, slower too."),
        "12.3.2" to tr("•  Barra de arriba: cuando sale el nombre en la cápsula, toda la cápsula es de ese botón (ya no se toca el de al lado por accidente).",
            "•  Top bar: when the name shows in the capsule, the whole capsule belongs to that button (no more tapping the neighbor by accident)."),
        "12.3.1" to tr("•  📖 Palabra de la semana: botones 🔊 para escuchar cómo se dice en inglés y en español (y el ejemplo).",
            "•  📖 Word of the week: 🔊 buttons to hear how it sounds in English and Spanish (and the example)."),
        "12.3" to tr("•  💾 La copia de seguridad ahora guarda TODO: también el portapapeles (textos e imágenes fijadas), tu imagen o video de fondo, los emojis recientes, Mi jornada, tu pago, pendientes y atajos.\n•  Al restaurar en un teléfono nuevo se vuelven a programar los avisos y la llegada/salida automática, y te dice qué permisos dar otra vez.\n•  Guardar y restaurar ya no congela la pantalla.",
            "•  💾 The backup now saves EVERYTHING: also the clipboard (texts and pinned images), your background image or video, recent emojis, My workday, your pay, to-dos and shortcuts.\n•  When restoring on a new phone, reminders and automatic arrival/departure are scheduled again, and it tells you which permissions to give again.\n•  Saving and restoring no longer freezes the screen."),
        "12.2.3" to tr("•  🕐 Recientes es su propia página: solo salen los emojis que tú usaste, y el último que usaste va primero.",
            "•  🕐 Recent is its own page: only the emojis you used show up, and the last one you used goes first."),
        "12.2.2" to tr("•  Emojis: al tocar una categoría de abajo (🕐 Recientes, 😀, 🐶…) ahora va justo al principio de esa categoría.",
            "•  Emojis: tapping a category at the bottom (🕐 Recent, 😀, 🐶…) now goes right to the start of that category."),
        "12.2.1" to tr("•  Tu imagen o video de fondo ahora también se ve en emojis, GIFs, portapapeles y todos los paneles.\n•  Ventana nueva y moderna mientras se prepara el video, con barra de progreso y botón «Cancelar».",
            "•  Your background image or video now also shows in emojis, GIFs, clipboard and every panel.\n•  New modern window while the video is prepared, with a progress bar and a «Cancel» button."),
        "12.2" to tr("•  🖼️ Fondo propio (Estilo): pon una imagen o un video de tu galería detrás de las teclas. El video se comprime, se ve completo y se repite sin fin.\n•  🔊 Sonido del video: prendido o en silencio (en Estilo o con el botón 🔊 del teclado).\n•  Con fondo propio las teclas se vuelven de vidrio para que se vea tu fondo.",
            "•  🖼️ Your own background (Style): put an image or video from your gallery behind the keys. The video is compressed, plays in full and loops forever.\n•  🔊 Video sound: on or muted (in Style or with the 🔊 keyboard button).\n•  With your own background the keys turn to glass so it shows through."),
        "12.1" to tr("•  ✨ 10 temas animados nuevos: Aura de poder, Aura azul, Fénix, Tormenta (con relámpagos), Fuegos artificiales, Galaxia espiral, Estanque, Panal sci-fi, Pulso neón y Ventisca.\n•  Los temas ahora están por categorías: Clásicos, Espacio, Naturaleza, Agua, Fuego y energía, Fiesta y amor, Tecnología y retro.\n•  Estilo más sencillo: los colores a tu gusto quedan escondidos en «🎨 Cambiar los colores del tema (opcional)», con un botón para volver a los del tema.",
            "•  ✨ 10 new animated themes: Power aura, Blue aura, Phoenix, Storm (with lightning), Fireworks, Spiral galaxy, Pond, Sci-fi hive, Neon pulse and Blizzard.\n•  Themes are now grouped: Classic, Space, Nature, Water, Fire and energy, Party and love, Tech and retro.\n•  Simpler Style: custom colors are tucked into «🎨 Change the theme colors (optional)», with a button to go back to the theme's."),
        "12.0.2" to tr("•  Al cambiar de idioma con 🌐 todo se cambia al instante: la barra de información, los paneles abiertos y la búsqueda.\n•  La luz de las barras de scroll ahora es más larga y más fina.",
            "•  When you switch language with 🌐 everything changes instantly: the info bar, open panels and search.\n•  The scroll bar light is now longer and thinner."),
        "12.0.1" to tr("•  Barras de scroll nuevas en todos lados: una línea corta de luz igual a la de la barra de arriba (blanca en el centro y del color de tu tema).",
            "•  New scroll bars everywhere: a short light line just like the top bar's (white in the center and in your theme color)."),
        "12.0" to tr("•  ✅ Pendientes: apunta lo que no se te puede olvidar y elige cuándo te aviso (a una hora o al llegar a uno de tus sitios).\n•  🧰 Checklist antes de salir: sale sola al tocar «Comencé»; marca lo que llevas.\n•  🧤 Modo guantes: teclas más grandes con un toque.\n•  📏 Convertidor: pies ↔ metros, pulgadas ↔ cm, °F ↔ °C, libras ↔ kg, millas ↔ km y total de cable (120 x 6 + 10%).",
            "•  ✅ To-dos: write down what you can't forget and pick when to be reminded (at a time or when you arrive at one of your sites).\n•  🧰 Pre-trip checklist: shows up when you tap «Started»; check off what you have.\n•  🧤 Glove mode: bigger keys with one tap.\n•  📏 Converter: feet ↔ meters, inches ↔ cm, °F ↔ °C, pounds ↔ kg, miles ↔ km and cable total (120 x 6 + 10%)."),
        "11.9.1" to tr("•  Palabra de la semana en inglés completa (palabra, significado y ejemplo) cuando el teclado está en inglés.\n•  El escáner y las respuestas de la IA de código ahora salen en tu idioma.",
            "•  Word of the week fully in English (word, meaning and example) when the keyboard is in English.\n•  The scanner and the code AI answers now use your language."),
        "11.9" to tr("•  🏠 Guarda tu casa en Mis sitios: al llegar se anota solo «Llegué a casa» con la hora real (solo en tu teléfono; en tickets y PDF solo dice «Casa»).\n•  🕐 Escribe la hora tú mismo (5:32pm, 532p o 17:32): en «Anotar con otra hora», al tocar una hora en Mi jornada y en el aviso de la noche.\n•  🌙 El aviso de la noche te sugiere la hora en que seguramente llegaste (tu salida + lo que tardas normalmente).",
            "•  🏠 Save your home in My sites: when you get there, «Home» is logged with the real time (only on your phone; tickets and PDF just say «Home»).\n•  🕐 Type the time yourself (5:32pm, 532p or 17:32): in «Log with another time», when tapping a time in My workday and in the night reminder.\n•  🌙 The night reminder suggests the time you probably got home (your departure + your usual drive)."),
        "11.8.1" to tr("•  🌙 Aviso de «¿Se te olvidó marcar Casa?»: ahora eliges la hora que quieras.",
            "•  🌙 «Forgot to log Home?» reminder: now you pick the time you want."),
        "11.8" to tr("•  📅 Calendario del mes (Mi jornada): las horas de cada día con colores; toca un día para ver el detalle.\n•  🔍 Buscar en tus tickets, la libreta y el portapapeles (Mis tickets y la lupa del portapapeles).\n•  🌗 Tema de día y de noche (Estilo): elige uno para el día y otro para la noche, y cambia solo según la hora.",
            "•  📅 Month calendar (My workday): hours for each day in colors; tap a day to see the details.\n•  🔍 Search your tickets, notebook and clipboard (My tickets and the clipboard magnifier).\n•  🌗 Day and night theme (Style): pick one for day and one for night, and it switches by itself."),
        "11.7" to tr("•  💵 Tu pago: pago por hora manejando y en el sitio, sitios que pagan más, horas extra a tiempo y medio, y descuentos de taxes %, 401(k) % y seguro de vida. Mi jornada te dice cuánto llevas en la semana.\n•  🌙 A las 9 PM, si no marcaste «Casa», te llega un aviso para cerrar el día.",
            "•  💵 Your pay: hourly pay driving and on site, sites that pay more, overtime at time and a half, and deductions for taxes %, 401(k) % and life insurance. My workday shows how much you've made this week.\n•  🌙 At 9 PM, if you didn't log «Home», you get a reminder to close the day."),
        "11.6" to tr("•  📄 Hoja de horas en PDF (Mi jornada y Resumen de la semana): días, horas, sitios, almuerzo y horas extra, lista para mandar.\n•  📸 Ticket con fotos (Mis tickets): elige las fotos de ese día y mándalas junto con el ticket.\n•  🗣️ Escribir en inglés (micrófono): con tu clave de Gemini lo dictado sale en inglés técnico y profesional.\n•  ⚡ Atajos de texto: escribe /voy + espacio y se cambia por tu frase completa (Ajustes → Frases rápidas).",
            "•  📄 PDF timesheet (My workday and Week summary): days, hours, sites, lunch and overtime, ready to send.\n•  📸 Ticket with photos (My tickets): pick that day's photos and send them with the ticket.\n•  🗣️ Type in English (mic): with your Gemini key, dictation comes out in professional technical English.\n•  ⚡ Text shortcuts: type /omw + space and it becomes your full phrase (Settings → Quick phrases)."),
        "11.5" to tr("•  🤖 Llegada y salida automáticas (Mis sitios): al llegar a uno de tus sitios y quedarte unos minutos se anota «Llegué» con la hora en que entraste; al irte se anota «Salí». Siempre con un aviso para «Deshacer».\n•  ⏰ Horas extra: aviso al pasar tus horas normales del día (8 h, 9 h o 10 h) y las horas extra del día y de la semana (más de 40 h).",
            "•  🤖 Automatic arrival and departure (My sites): when you arrive at one of your sites and stay a few minutes, «Arrived» is logged with the time you got there; when you leave, «Left» is logged. Always with an «Undo» alert.\n•  ⏰ Overtime: alert when you pass your regular daily hours (8, 9 or 10 h) and overtime shown for the day and the week (over 40 h)."),
        "11.4.1" to tr("•  Ejemplos nuevos y genéricos en toda la app (sin nombres de lugares reales).",
            "•  New generic examples throughout the app (no real place names)."),
        "11.4" to tr("•  Mi jornada: al tocar «📍 Llegué al sitio» en un lugar que no tienes guardado, el GPS busca la dirección y la anota («Llegué a 123 Main St»).\n•  Luego te pregunta «¿Guardar este sitio?»: le pones nombre una vez y la próxima vez lo reconoce solo (y lo de hoy también cambia al nombre nuevo).",
            "•  My workday: when you tap «📍 Arrived on site» at a place you haven't saved, GPS finds the address and logs it («Arrived at 123 Main St»).\n•  Then it asks «Save this site?»: name it once and next time it's recognized automatically (today's entries switch to the new name too)."),
        "11.3.3" to tr("•  Mi jornada: elige cuánto te descuentan de almuerzo (15 min, 30 min, 45 min, 1 hora) o escribe otro tiempo con «✏️ Otro tiempo…».",
            "•  My workday: choose how much lunch is deducted (15 min, 30 min, 45 min, 1 hour) or type another time with «✏️ Other time…»."),
        "11.3.2" to tr("•  Mi jornada: el almuerzo descuenta siempre 30 minutos, cualquier día (aunque sea corto o tomes más o menos). Los botones 🍔 y ↩️ sirven para anotar cuánto tomaste.",
            "•  My workday: lunch always deducts 30 minutes, any day (even short ones or if you take more or less). The 🍔 and ↩️ buttons log how long you took."),
        "11.3.1" to tr("•  Mi jornada: el almuerzo ahora viene como «No pagado»: se descuentan 30 minutos de tus horas (o el tiempo real si marcas 🍔 y ↩️).",
            "•  My workday: lunch now defaults to «Unpaid»: 30 minutes are subtracted from your hours (or the real time if you log 🍔 and ↩️)."),
        "11.3" to tr("•  Barra de arriba: ahora se ve el nombre completo de cada botón.\n•  Mi jornada cuenta el almuerzo: botones «🍔 Salí a almorzar» y «↩️ Regresé». Si no lo marcas, cuenta un tiempo fijo (30 min, 45 min o 1 hora). Puedes elegir si es pagado o no pagado en ✏️ Editar botones.",
            "•  Top bar: each button's full name is now shown.\n•  My workday counts lunch: «🍔 Lunch break» and «↩️ Back from lunch» buttons. If you don't log it, a fixed time is counted (30 min, 45 min or 1 hour). Choose paid or unpaid in ✏️ Edit buttons."),
        "11.2" to tr("•  Barra de arriba más fluida: mientras deslizas solo se mueven los íconos; cuando se detiene (o dejas el dedo quieto), el botón del medio se abre suave con su nombre y luego se cierra solo.\n•  La barra se acomoda con un botón justo debajo de la luz.",
            "•  Smoother top bar: while you swipe only the icons move; when it stops (or you hold your finger still), the middle button opens smoothly with its name and then closes by itself.\n•  The bar settles with a button right under the light."),
        "11.1" to tr("•  Barra de arriba sin fin: sigue dando vueltas. Al pasar por el principio se frena un poquito (se ancla), vibra suave y sale una línea de luz que marca el final.\n•  El nombre del botón del medio ahora sale dentro de la barra, en una cápsula al lado del ícono, y los vecinos se apartan para hacerle espacio (ya no tapa las teclas).\n•  Al soltar, la barra se acomoda sola con los botones en su lugar.",
            "•  Endless top bar: it keeps going around. When it passes the start it slows a little (anchors), vibrates softly and a light line marks the end.\n•  The middle button's name now shows inside the bar, in a capsule next to the icon, and the neighbors move aside to make room (it no longer covers the keys).\n•  When you let go, the bar settles with the buttons in place."),
        "11.0" to tr("•  Barra de arriba: al deslizarla, el nombre del botón que está en la luz sale en una burbujita debajo.\n•  Ajustes → Estilo → Barra de arriba: elige qué tanto se curva la rueda, qué tanto brilla la línea de luz y si quieres ver el nombre.",
            "•  Top bar: when you swipe it, the name of the button in the light shows in a little bubble below.\n•  Settings → Style → Top bar: choose how much the wheel curves, how bright the light line is and whether to show the name."),
        "10.9" to tr("•  La línea de luz de la barra de arriba ahora es horizontal, abajo de los botones.\n•  Los botones se mueven en arco, como una rueda: el del medio sube y crece, los de los lados bajan y se inclinan.\n•  Grabaciones de pantalla: si grabas la pantalla, el teclado te sugiere el video para pegarlo, igual que las capturas.",
            "•  The top bar light line is now horizontal, under the buttons.\n•  Buttons move in an arc, like a wheel: the middle one rises and grows, the side ones drop and tilt.\n•  Screen recordings: if you record your screen, the keyboard suggests the video to paste, just like screenshots."),
        "10.8" to tr("•  Barra de arriba: al deslizarla sale una línea de luz en el medio y el botón que pasa por ahí crece, como elegir un personaje en un videojuego.",
            "•  Top bar: when you swipe it, a line of light shows in the middle and the button passing over it grows, like picking a character in a video game."),
        "10.7" to tr("•  Portapapeles: al seleccionar, cada nota tiene un rombo ◇ (vacío o con palomita).\n•  Agregar o editar en el portapapeles abre una caja grande con varias líneas y el botón «✓ Guardar».",
            "•  Clipboard: when selecting, each note has a diamond ◇ (empty or checked).\n•  Adding or editing in the clipboard opens a big multi-line box with a «✓ Save» button."),
        "10.6" to tr(
            "•  Mi jornada: elige el ícono (camioneta, reloj, cronómetro, maletín, calendario, casco o edificio).\n" +
                "•  Portapapeles como Gboard: mantén presionado para seleccionar varios (los demás tiemblan), fijar, borrar o editar lo que copiaste.\n" +
                "•  ✏️ Lápiz en el portapapeles para agregar textos tuyos.\n" +
                "•  Botón ← de atrás en todos los paneles.",
            "•  My workday: pick the icon (truck, clock, stopwatch, briefcase, calendar, hard hat or building).\n" +
                "•  Clipboard like Gboard: press and hold to select several (the others wiggle), pin, delete or edit what you copied.\n" +
                "•  ✏️ Pencil in the clipboard to add your own texts.\n" +
                "•  ← Back button on every panel.",
        ),
        "10.5" to tr("•  Mi jornada a tu gusto: cambia el nombre de los botones y quita los que no uses (por ejemplo «Comencé» si no vas en camioneta).",
            "•  My workday your way: rename the buttons and hide the ones you don't use (for example «Started» if you don't ride a truck)."),
        "10.4" to tr(
            "•  ✨ 19 temas animados nuevos: Amor (corazones), Otoño (hojas), Fiesta (confeti), Olas, Lámpara de lava, Circuito, Synthwave, Nubes, Meteoros, Luces, Brillantina, Arcoíris vivo, Navidad, Noche violeta, Lluvia neón, Volcán, Bosque mágico, Tropical y Cerezo nocturno. Ya son 27 animados y 55 temas en total.\n" +
                "•  12 efectos nuevos al tocar: Voltear, Latido de corazón, Liga, Romper, Humo, Espiral, Flor, Moneda, Globo, Mariposa, Falla digital y Salpicar. Ya son 46.",
            "•  ✨ 19 new animated themes: Love (hearts), Autumn (leaves), Party (confetti), Waves, Lava lamp, Circuit, Synthwave, Clouds, Meteors, Lights, Glitter, Living rainbow, Christmas, Violet night, Neon rain, Volcano, Magic forest, Tropical and Night blossom. Now 27 animated and 55 themes in total.\n" +
                "•  12 new tap effects: Flip, Heartbeat, Rubber, Shatter, Smoke, Spiral, Flower, Coin, Balloon, Butterfly, Glitch and Splash. Now 46.",
        ),
        "10.3" to tr(
            "•  ✨ 8 temas con fondo animado: Galaxia (estrellas fugaces), Invierno (nieve), Mar profundo (burbujas), Luciérnagas, Lluvia, Brasas, Aurora boreal y Sakura (pétalos).\n" +
                "•  8 temas nuevos más: Vino, Esmeralda, Pizarra, Retro (ámbar), Hielo, Arena, Coral y Lila. Ya son 36.\n" +
                "•  11 efectos nuevos al tocar: Hundir, Columpio, Rayo, Pétalos, Notas musicales, Emoji sorpresa, Tinta, Latido, Órbita, Arcoíris y Pixeles. Ya son 34.\n" +
                "•  Muchos colores nuevos para teclas, letras, acento y el tema Hacker.",
            "•  ✨ 8 themes with animated backgrounds: Galaxy (shooting stars), Winter (snow), Deep sea (bubbles), Fireflies, Rain, Embers, Northern lights and Sakura (petals).\n" +
                "•  8 more new themes: Wine, Emerald, Slate, Retro (amber), Ice, Sand, Coral and Lilac. Now 36.\n" +
                "•  11 new tap effects: Press, Swing, Lightning, Petals, Music notes, Surprise emoji, Ink, Pulse, Orbit, Rainbow and Pixels. Now 34.\n" +
                "•  Lots of new colors for keys, letters, accent and the Hacker theme.",
        ),
        "10.2" to tr(
            "•  📲 Mensajes de estado con un toque (ya llegué, voy saliendo…) con la hora y el sitio.\n" +
                "•  📊 Resumen de la semana: horas por día, sitios y tickets.\n" +
                "•  📄 Exportar tus horas a Excel / Google Sheets.\n" +
                "•  🛻 Modo manejando: pregunta si manejas y te da botones grandes y la voz.\n" +
                "•  💾 Copia de seguridad automática cada semana.",
            "•  📲 One-tap status messages (arrived, heading out…) with the time and site.\n" +
                "•  📊 Week summary: hours per day, sites and tickets.\n" +
                "•  📄 Export your hours to Excel / Google Sheets.\n" +
                "•  🛻 Driving mode: asks if you drive and gives you big buttons and voice.\n" +
                "•  💾 Automatic weekly backup.",
        ),
        "10.1" to tr(
            "•  Mi jornada en la pantalla de bloqueo con el botón 🛻 Comencé (sin desbloquear).\n" +
                "•  Botón de Mi jornada en los ajustes rápidos del teléfono.\n" +
                "•  El widget se ve completo aunque sea pequeño.",
            "•  My workday on the lock screen with the 🛻 Start button (no unlocking).\n" +
                "•  My workday button in the phone's Quick Settings.\n" +
                "•  The widget looks complete even when small.",
        ),
        "10.0" to tr(
            "•  🏠 Widget de Mi jornada y botones en la pantalla de bloqueo.\n" +
                "•  📍 Mis sitios: al tocar «Llegué», el GPS sabe en qué sitio estás.\n" +
                "•  🧰 Materiales: toca lo que usaste y se arma el Equipment List.\n" +
                "•  💬 Respuestas rápidas con IA (corta, formal y en el otro idioma).\n" +
                "•  ⚡ Más rápido al escribir.",
            "•  🏠 My workday widget and buttons on the lock screen.\n" +
                "•  📍 My sites: when you tap «Arrived», GPS knows which site you're at.\n" +
                "•  🧰 Materials: tap what you used and the Equipment List is built.\n" +
                "•  💬 Quick AI replies (short, formal and in the other language).\n" +
                "•  ⚡ Faster while typing.",
        ),
        "9.9.1" to tr("•  Una sola cámara: el escáner lee el texto y los códigos de barras y QR a la vez.\n•  Los tickets dicen «del trabajo» (sin nombre de compañía).",
            "•  One camera: the scanner reads text and barcodes/QR codes at the same time.\n•  Tickets say «work» (no company name)."),
        "9.9" to tr(
            "•  🛻 Mi jornada: Comencé (camioneta), Llegué al sitio, Salí del sitio y Llegué a casa, con tus horas del día y de la semana.\n" +
                "•  🇺🇸 En el micrófono: habla en español y se escribe en inglés.\n" +
                "•  🔎 Buscador en los ajustes.\n" +
                "•  📘 Guía rápida.\n" +
                "•  🐞 Si algo se cierra por un error, aquí sale para copiarlo y mandármelo.",
            "•  🛻 My workday: Started (truck), Arrived on site, Left the site and Home, with your hours for the day and week.\n" +
                "•  🇺🇸 In the mic: speak Spanish and it's typed in English.\n" +
                "•  🔎 Search in settings.\n" +
                "•  📘 Quick guide.\n" +
                "•  🐞 If something closes because of an error, it shows here so you can copy it and send it to me.",
        ),
        "9.8.1" to tr("•  Arreglado: Estilo se cerraba al elegir otro tema.", "•  Fixed: Style closed when picking another theme."),
        "9.8" to tr("•  En Estilo, los colores del tema Hacker solo salen cuando eliges el tema Hacker, y aparecen deslizándose hacia abajo.",
            "•  In Style, the Hacker theme colors only show when you pick the Hacker theme, and they slide down into view."),
        "9.7" to tr(
            "•  📷 Escanear códigos de barras y QR (número de serie, MAC).\n" +
                "•  🕐 Hora, fecha y lugar con un toque.\n" +
                "•  📒 Libreta del día: tus apuntes se vuelven el ticket del día.\n" +
                "•  🗂️ Mis tickets con número que sigue contando, y formato del trabajo.\n" +
                "•  ⏰ Aviso diario: ✅ TICKET REGISTERED / ❌ NO TICKET.\n" +
                "•  📖 Palabra de la semana.\n" +
                "•  Mantén presionado un botón de la barra de arriba para acomodarlo (los demás tiemblan).",
            "•  📷 Scan barcodes and QR codes (serial number, MAC).\n" +
                "•  🕐 Time, date and place with one tap.\n" +
                "•  📒 Day notebook: your notes become the day's ticket.\n" +
                "•  🗂️ My tickets with a number that keeps counting, and work ticket format.\n" +
                "•  ⏰ Daily reminder: ✅ TICKET REGISTERED / ❌ NO TICKET.\n" +
                "•  📖 Word of the week.\n" +
                "•  Press and hold a top-bar button to move it (the others wiggle).",
        ),
        "9.6" to tr(
            "•  📋 Notas → Ticket: tus notas rápidas se vuelven un ticket formal en español o inglés.\n" +
                "•  🎤 Dictar reporte: hablas con tus palabras y la IA lo escribe ordenado y formal.\n" +
                "•  ✨ Palabras más formales: sugerencias mientras escribes (arreglé → reparé) y opciones con IA.\n" +
                "•  ✅ Revisar antes de enviar.\n" +
                "•  Teclado flotante que puedes mover por la pantalla.",
            "•  📋 Notes → Ticket: your quick notes become a formal ticket in Spanish or English.\n" +
                "•  🎤 Dictate report: speak in your own words and the AI writes it neatly and formally.\n" +
                "•  ✨ More formal words: suggestions while you type (fixed → repaired) and AI options.\n" +
                "•  ✅ Review before sending.\n" +
                "•  Floating keyboard you can move around the screen.",
        ),
        "9.5" to tr("•  La barra de arriba se desliza más fluida: al soltar el dedo sigue un poquito y se frena suave.",
            "•  The top bar scrolls more smoothly: when you lift your finger it keeps going a little and slows down gently."),
        "9.4" to tr("•  Barra de scroll nueva en emojis, GIFs y stickers: un destello de luz blanca, fino y suave, que se apaga solo y no estorba al tocar.",
            "•  New scroll bar in emojis, GIFs and stickers: a thin, soft flash of white light that fades by itself and doesn't get in the way."),
        "9.3" to tr("•  Buscar emojis, GIFs y stickers sin salir del panel: sale un tecladito abajo y los resultados aparecen mientras escribes.",
            "•  Search emojis, GIFs and stickers without leaving the panel: a small keyboard shows below and results appear as you type."),
        "9.2" to tr(
            "•  Buscar emojis y GIFs con una barra de búsqueda (escribe «casa», «feliz», «perro»…).\n" +
                "•  El botón GIF ahora está dentro del panel de emojis.\n" +
                "•  Sugerencias de emojis según lo que escribes: casa 🏠, jaja 😂, te quiero ❤️.\n" +
                "•  Barra de scroll fina como un destello de luz (ya no estorba al tocar los emojis de la orilla).\n" +
                "•  Desliza la barra de arriba para ver los botones de «Más opciones».",
            "•  Search emojis and GIFs with a search bar (type «house», «happy», «dog»…).\n" +
                "•  The GIF button is now inside the emoji panel.\n" +
                "•  Emoji suggestions from what you type: house 🏠, haha 😂, love you ❤️.\n" +
                "•  Thin scroll bar like a flash of light (it no longer gets in the way of edge emojis).\n" +
                "•  Swipe the top bar to see the «More options» buttons.",
        ),
        "9.1" to tr(
            "•  💬 Frases rápidas para reportes del trabajo (español e inglés) y las tuyas.\n" +
                "•  Traducir mensaje: copia lo que te llegó y tradúcelo al instante.\n" +
                "•  Modo una mano, calculadora y GIFs/stickers dentro del teclado.\n" +
                "•  Copia de seguridad con contraseña (ajustes, palabras, Datos y frases).",
            "•  💬 Quick phrases for work reports (Spanish and English) plus your own.\n" +
                "•  Translate message: copy what you got and translate it instantly.\n" +
                "•  One-hand mode, calculator and GIFs/stickers inside the keyboard.\n" +
                "•  Password-protected backup (settings, words, Data and phrases).",
        ),
        "9.0" to tr(
            "•  Color de las teclas y de las letras en cualquier tema (también teclas arcoíris 🌈).\n" +
                "•  23 efectos al tocar: Gelatina, Temblor, Giro, Ola, Flash, Neón, Anillos, Estrellas, Confeti, Corazones, Fuego, Nieve, Letra voladora y 🎲 Sorpresa.\n" +
                "•  «Mis palabras»: ver, cambiar, borrar y agregar tus palabras.\n" +
                "•  «Datos»: cambia lo guardado y, al seleccionar un texto, «Guardar en Datos».",
            "•  Key and letter color in any theme (rainbow keys too 🌈).\n" +
                "•  23 tap effects: Jelly, Shake, Spin, Wave, Flash, Neon, Rings, Stars, Confetti, Hearts, Fire, Snow, Flying letter and 🎲 Surprise.\n" +
                "•  «My words»: see, change, delete and add your words.\n" +
                "•  «Data»: edit what's saved and, when you select text, «Save to Data».",
        ),
        "8.9" to tr("•  Sugerencias también en Instagram y otras apps que las apagaban.", "•  Suggestions also in Instagram and other apps that turned them off."),
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
