package com.luisell.bestkeyboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import com.luisell.bestkeyboard.words.TextUtil
import com.luisell.bestkeyboard.words.VoiceText

/** El servicio del teclado: recibe las teclas y las escribe en la app que estés usando. */
class BestKeyboardService : InputMethodService(), KeyboardView.Listener {

    private enum class Page { LETTERS, SYM, SYM2, NUM }

    private var kv: KeyboardView? = null
    private lateinit var prefs: SharedPreferences
    private var vibrator: Vibrator? = null
    private var audio: AudioManager? = null
    private val handler = Handler(Looper.getMainLooper())

    private var page = Page.LETTERS
    private var shift = 0
    private var manualShift = false
    private var lastShiftTime = 0L
    private var lastWasSpace = false

    // Ajustes
    private var vibrateOn = true
    private var vibMs = 15L
    private var vibAmp = 140
    private var soundOn = false
    private var autoCap = true
    private var doubleSpace = true
    private var numberRow = true
    private var lang = "es"
    private var suggestOn = true
    private var autocorrectOn = true
    private var learnOn = true

    // Tipo de campo
    private var isPassword = false
    private var phone = false
    private var commaMode = ","
    private var fieldSuggest = true
    private var fieldCorrect = true
    private var fieldLearn = true

    // Sugerencias
    private val slotWords = arrayOfNulls<String>(3)
    private var slotTyped = -1
    private var autoSpace = false
    /** Última autocorrección: (lo que escribiste, la corrección, el separador). Borrar la deshace. */
    private var lastCorrection: Triple<String, String, String>? = null
    private val updateRunnable = Runnable { updateSuggestions() }

    // Paneles: 0 = teclas, 1 = emojis, 2 = portapapeles
    private var mode = 0
    private var emojiPanel: EmojiPanel? = null
    private var clipPanel: ClipPanel? = null
    private var clipboard: ClipboardManager? = null
    private var clipHistoryOn = true
    private var lastClipSig: String? = null
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    // Revisar ortografía palabra por palabra
    private class SpellErr(var start: Int, var end: Int, val word: String, val sugg: List<String>, val maybe: Boolean)
    private var sBar: SpellBar? = null
    private var spellErrors = ArrayList<SpellErr>()
    private var spellIdx = 0
    /** Copia del texto que se está revisando (para mostrarlo con los errores marcados). */
    private var spellText = ""
    private var spellBase = 0
    private var spellToken = 0
    private val spellWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var spellOn = false

    // Teclado de computadora ("modo hacker")
    private var hacker = false
    private var hFn = false
    /** 0 = apagado, 1 = solo para la próxima tecla, 2 = fijo. */
    private var hCtrl = 0
    private var hAlt = 0
    private var hShift = 0
    private val hackerActive get() = hacker && page != Page.NUM
    private var selStart = 0
    private var selEnd = 0

    // Deslizar para escribir
    private var swipeOn = true
    /** Palabra que acabas de escribir deslizando (y sus alternativas para la barra). */
    private var gestureWord: String? = null
    private var gestureAlts: List<String> = emptyList()
    /** Después de deslizar, la próxima letra o palabra pone un espacio antes (como Gboard). */
    private var phantomSpace = false

    // Edición de texto (panel 5)
    private var editPanel: EditPanel? = null
    private var morePanel: MorePanel? = null

    // Sugerencia de captura de pantalla
    private var shot: Screenshots.Shot? = null
    /** El botón de arriba está pidiendo el permiso de fotos (en vez de ofrecer una captura). */
    private var shotAsk = false
    private val shotIo = java.util.concurrent.Executors.newSingleThreadExecutor()
    /** Avisa cuando se guarda una foto nueva (para ofrecer la captura con el teclado abierto). */
    private val mediaObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(shotCheck)
            handler.postDelayed(shotCheck, 900)
        }
    }
    private val shotCheck = Runnable { if (isInputViewShown) checkScreenshot() }

    // Escáner dentro del teclado (panel 4)
    private var scanPanel: ScanPanel? = null
    private var panelBox: PanelBox? = null
    private var scanPhoto: android.graphics.Bitmap? = null

    // Dictado por voz (panel 3)
    private var voicePanel: VoicePanel? = null
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var voiceLang = "es"

    // Traducción
    private var tBar: TranslateBar? = null
    private var translating = false
    private var trans: Translation? = null
    private var tSrc = "es"
    private var tDst = "en"
    private var tSeq = 0
    private var lastTranslatedSrc = ""
    private var pendingSend = false
    private val translateRunnable = Runnable { doTranslate() }

    /** Donde escribe el teclado: la caja de traducción (si estás traduciendo) o la app. */
    private fun target(): InputConnection? = if (translating) tBar?.connection else currentInputConnection
    private fun targetInfo(): EditorInfo? = if (translating) tBar?.inputInfo else currentInputEditorInfo

    /** Envía una tecla (borrar, flechas, enter) a donde esté escribiendo el teclado. */
    private fun sendKey(code: Int, meta: Int = 0) {
        // La caja del traductor está dentro del teclado: ahí las teclas se aplican directo
        val tb = tBar
        if (translating && tb != null) {
            when (code) {
                KeyEvent.KEYCODE_DEL -> tb.deleteBack()
                KeyEvent.KEYCODE_DPAD_LEFT -> tb.moveCursor(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> tb.moveCursor(1)
            }
            return
        }
        val c = target() ?: return
        val t = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        c.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
        c.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
    }

    private val panelListener = object : PanelListener {
        override fun onPanelText(text: String) = commitPanelText(text)
        override fun onPanelDelete() = onDeletePress()
        override fun onPanelBack() = showMode(0)
        override fun onPanelFeedback() = onKeyFeedback()
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        WordStore.load(this) { scheduleUpdate() }
        EmojiData.load(this)
        ClipStore.load(this)
        ClipStore.onChange = { if (mode == 2) clipPanel?.refresh() }
        lastClipSig = prefs.getString(Prefs.CLIP_LAST_SIG, null)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
        try {
            contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        } catch (_: Exception) { }
        tSrc = prefs.getString(Prefs.TR_SRC, "es") ?: "es"
        tDst = if (tSrc == "es") "en" else "es"
    }

    override fun onCreateInputView(): View {
        // Los textos de los paneles siguen al idioma del teclado (español / English)
        L.clearListeners()
        L.set(prefs.getString(Prefs.LANG, "es") ?: "es")
        val v = KeyboardView(this)
        v.listener = this
        kv = v
        // Las teclas mandan la altura; emojis y portapapeles usan la misma
        val box = PanelBox(this, v)
        val ep = EmojiPanel(this)
        ep.listener = panelListener
        ep.visibility = View.GONE
        box.addView(ep, FrameLayout.LayoutParams(-1, -1))
        emojiPanel = ep
        val cp = ClipPanel(this)
        cp.listener = panelListener
        cp.actions = object : ClipPanel.Actions {
            override fun onPasteText(text: String) = commitPanelText(text)
            override fun onPasteImage(item: ClipItem) = pasteImage(item)
        }
        cp.visibility = View.GONE
        box.addView(cp, FrameLayout.LayoutParams(-1, -1))
        clipPanel = cp
        val vp = VoicePanel(this)
        vp.listener = panelListener
        vp.actions = object : VoicePanel.Actions {
            override fun onMicTap() = if (listening) stopVoice(L.t("Toca el micrófono para hablar", "Tap the mic to speak")) else startVoice()
            override fun onVoiceLangTap() {
                voiceLang = if (voiceLang == "es") "en" else "es"
                vp.setLang(voiceLang)
                if (listening) { recognizer?.cancel(); startVoice() }
            }
        }
        vp.visibility = View.GONE
        box.addView(vp, FrameLayout.LayoutParams(-1, -1))
        voicePanel = vp

        val sp = ScanPanel(this)
        sp.visibility = View.GONE
        sp.actions = object : ScanPanel.Actions {
            override fun onScanInsert(text: String) { insertText(text); showMode(0) }
            override fun onScanClose() = showMode(0)
            override fun onScanFeedback() = onKeyFeedback()
            override fun onScanGallery() {
                showMode(0)
                val i = Intent(this@BestKeyboardService, ScanActivity::class.java)
                    .putExtra(ScanActivity.EXTRA_GALLERY, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                try { startActivity(i) } catch (_: Exception) { }
            }
        }
        box.addView(sp, FrameLayout.LayoutParams(-1, -1))
        box.tallPanel = sp

        val ed = EditPanel(this)
        ed.visibility = View.GONE
        ed.actions = object : EditPanel.Actions {
            override fun onEditMove(keyCode: Int, select: Boolean) {
                val meta = if (select) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
                sendKey(keyCode, meta)
            }
            override fun onEditCommand(id: Int) {
                val tb = tBar
                if (translating && tb != null) tb.box.onTextContextMenuItem(id)
                else target()?.performContextMenuAction(id)
            }
            override fun onEditDelete() = onDeletePress()
            override fun onEditBack() = showMode(0)
            override fun onEditFeedback() = onKeyFeedback()
        }
        box.addView(ed, FrameLayout.LayoutParams(-1, -1))
        editPanel = ed

        // Panel "Más opciones": todas las herramientas con botones grandes
        val more = MorePanel(this)
        more.visibility = View.GONE
        more.actions = object : MorePanel.Actions {
            override fun onMoreTool(tool: Int) {
                when (tool) {
                    KeyboardView.TOOL_EMOJI, KeyboardView.TOOL_CLIP, KeyboardView.TOOL_VOICE,
                    KeyboardView.TOOL_EDIT, KeyboardView.TOOL_SCAN -> onToolbar(tool)
                    else -> { showMode(0); onToolbar(tool) }
                }
            }
            override fun onMoreBack() = showMode(0)
            override fun onMoreFeedback() = onKeyFeedback()
            override fun onToolsChanged(bar: List<Int>, more: List<Int>) {
                Tools.save(prefs, bar, more)
                kv?.setBarTools(bar)
            }
        }
        box.addView(more, FrameLayout.LayoutParams(-1, -1))
        morePanel = more
        scanPanel = sp
        panelBox = box

        // Barra de traducción arriba del teclado (escondida hasta que la abras)
        val tb = TranslateBar(this)
        tb.visibility = View.GONE
        tb.actions = object : TranslateBar.Actions {
            override fun onTranslateClose() { onKeyFeedback(); stopTranslate() }
            override fun onTranslateSwap() { onKeyFeedback(); swapTranslate() }
            override fun onTranslateCursor() {
                onKeyFeedback()
                lastCorrection = null
                autoSpace = false
                lastWasSpace = false
                updateAutoCaps()
                scheduleUpdate()
            }
            override fun onTranslateSource(text: String) {
                handler.removeCallbacks(translateRunnable)
                handler.postDelayed(translateRunnable, 250)
                updateAutoCaps()
                scheduleUpdate()
            }
        }
        tBar = tb
        translating = false
        val sb = SpellBar(this)
        sb.visibility = View.GONE
        sb.actions = object : SpellBar.Actions {
            override fun onSpellPick(word: String) { onKeyFeedback(); spellPick(word) }
            override fun onSpellSkip() { onKeyFeedback(); spellSkip() }
            override fun onSpellSelect(index: Int) { onKeyFeedback(); spellShow(index) }
            override fun onSpellFixAll() { onKeyFeedback(); spellFixAll() }
            override fun onSpellLearn() { onKeyFeedback(); spellLearn() }
            override fun onSpellClose() { onKeyFeedback(); stopSpell() }
        }
        sBar = sb
        spellOn = false
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(sb, LinearLayout.LayoutParams(-1, -2))
        root.addView(tb, LinearLayout.LayoutParams(-1, -2))
        root.addView(box, LinearLayout.LayoutParams(-1, -2))

        mode = 0
        L.onChange {
            clipPanel?.refresh()
            tBar?.applyLang()
            if (shot != null || shotAsk) checkScreenshot()
            kv?.invalidate()
        }
        applySettings()
        rebuild()
        return root
    }

    /** Cambia entre teclas, emojis y portapapeles (los paneles usan la misma altura del teclado). */
    private fun showMode(m: Int) {
        mode = m
        val v = kv ?: return
        if (m != 0) { v.reset(); clearSlots() }
        v.visibility = if (m == 0) View.VISIBLE else View.INVISIBLE
        emojiPanel?.let { ep ->
            ep.visibility = if (m == 1) View.VISIBLE else View.GONE
            if (m == 1) ep.open() else ep.close()
        }
        clipPanel?.let { cp ->
            cp.visibility = if (m == 2) View.VISIBLE else View.GONE
            if (m == 2) {
                cp.historyOn = clipHistoryOn
                captureClip()
                cp.open()
            } else cp.close()
        }
        voicePanel?.let { vp ->
            vp.visibility = if (m == 3) View.VISIBLE else View.GONE
            if (m == 3) {
                voiceLang = lang
                vp.setLang(voiceLang)
                vp.setPartial("")
                startVoice()
            } else if (listening || recognizer != null) {
                listening = false
                recognizer?.cancel()
                vp.setListening(false)
            }
        }
        scanPanel?.let { sp ->
            val was = sp.visibility == View.VISIBLE
            sp.visibility = if (m == 4) View.VISIBLE else View.GONE
            if (m == 4) {
                val photo = scanPhoto
                scanPhoto = null
                if (photo != null) sp.openWithPhoto(photo) else sp.open()
            } else if (was) sp.close()
            if (m == 4 || was) panelBox?.requestLayout()
        }
        editPanel?.let { ed ->
            ed.visibility = if (m == 5) View.VISIBLE else View.GONE
            if (m == 5) ed.open()
        }
        morePanel?.let { mp ->
            mp.visibility = if (m == 6) View.VISIBLE else View.GONE
            if (m == 6) mp.open()
        }
        if (m == 0) { updateAutoCaps(); scheduleUpdate() }
    }

    // ---------------------------------------------------------------- Capturas de pantalla

    /** Si tomaste una captura hace poco, la ofrece arriba del teclado. */
    private fun checkScreenshot() {
        if (!prefs.getBoolean(Prefs.SHOTS, true) || isPassword) { clearShot(); return }
        val app = applicationContext
        if (!Screenshots.hasPermission(app)) {
            // Sin permiso no se ven las capturas: lo pide con un botón (máximo 3 veces)
            val asks = prefs.getInt(Prefs.SHOT_ASKS, 0)
            if (asks < 3 || shotAsk) {
                if (!shotAsk) prefs.edit().putInt(Prefs.SHOT_ASKS, asks + 1).apply()
                shot = null
                shotAsk = true
                kv?.setChip(null, L.t("Activar sugerencia de capturas", "Turn on screenshot suggestions"))
            } else clearShot()
            return
        }
        val usedId = prefs.getLong(Prefs.SHOT_USED, -1L)
        val px = (48 * resources.displayMetrics.density).toInt()
        shotIo.execute {
            val s = Screenshots.findRecent(app)
            val thumb = if (s != null && s.id != usedId) Screenshots.thumbnail(app, s, px) else null // puede faltar: se muestra 🖼
            handler.post {
                if (s != null && s.id != usedId) {
                    shot = s
                    shotAsk = false
                    kv?.setChip(thumb, L.t("Captura de pantalla", "Screenshot"))
                } else clearShot()
            }
        }
    }

    private fun clearShot() {
        shot = null
        shotAsk = false
        kv?.setChip(null, null)
    }

    /** Tocaste "Captura de pantalla": se guarda en el portapapeles y se pega aquí. */
    private fun useScreenshot() {
        if (shotAsk) {
            clearShot()
            askPermission(MainActivity.EXTRA_ASK_PHOTOS)
            return
        }
        val s = shot ?: return
        prefs.edit().putLong(Prefs.SHOT_USED, s.id).apply()
        clearShot()
        ClipStore.addImage(this, s.uri, s.mime) { item ->
            if (item != null) pasteImage(item)
            else Toast.makeText(this, L.t("No se pudo abrir la captura.", "Couldn't open the screenshot."), Toast.LENGTH_SHORT).show()
        }
    }

    /** Escribe un texto (del escáner) con el espacio correcto antes. */
    private fun insertText(text: String) {
        val ic = target() ?: return
        val before = ic.getTextBeforeCursor(1, 0) ?: ""
        val space = if (before.isNotEmpty() && !before[0].isWhitespace()) " " else ""
        ic.commitText(space + text, 1)
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
    }

    private fun hasPermission(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Abre la app para que des un permiso (micrófono o cámara). */
    private fun askPermission(extra: String) {
        val i = Intent(this, MainActivity::class.java)
            .putExtra(extra, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try { startActivity(i) } catch (_: Exception) { }
    }

    // ---------------------------------------------------------------- Dictado por voz

    private fun startVoice() {
        val vp = voicePanel ?: return
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            vp.setStatus(L.t("Necesito permiso para usar el micrófono. Ábrelo y toca \"Permitir\".", "I need permission to use the mic. Open it and tap \"Allow\"."))
            val i = Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ASK_MIC, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try { startActivity(i) } catch (_: Exception) { }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            vp.setStatus(L.t("Tu teléfono no tiene reconocimiento de voz. Instala o actualiza la app Google.", "Your phone has no speech recognition. Install or update the Google app."))
            return
        }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(voiceListener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (voiceLang == "es") "es-US" else "en-US")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        listening = true
        vp.setListening(true)
        vp.setStatus(L.t("Escuchando… habla ahora", "Listening… speak now"))
        try {
            r.startListening(intent)
        } catch (_: Exception) {
            stopVoice(L.t("No se pudo usar el micrófono. Inténtalo de nuevo.", "Couldn't use the mic. Try again."))
        }
    }

    private fun stopVoice(message: String) {
        listening = false
        recognizer?.cancel()
        voicePanel?.setListening(false)
        voicePanel?.setStatus(message)
    }

    private val voiceListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { voicePanel?.setLevel(rmsdB) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { if (listening) voicePanel?.setStatus(L.t("Escribiendo…", "Typing…")) }
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

        override fun onError(error: Int) {
            if (!listening) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    stopVoice(L.t("Toca el micrófono para hablar", "Tap the mic to speak"))
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    stopVoice(L.t("Falta el permiso del micrófono (ábrelo en los ajustes ⚙).", "Mic permission is missing (open settings ⚙)."))
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    stopVoice(L.t("Sin internet. Para dictar sin conexión, descarga el idioma en la app Google (Voz → Reconocimiento sin conexión).", "No internet. To dictate offline, download the language in the Google app (Voice → Offline speech recognition)."))
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    stopVoice(L.t("El micrófono está ocupado. Toca para intentarlo de nuevo.", "The mic is busy. Tap to try again."))
                else -> stopVoice(L.t("Toca el micrófono para hablar", "Tap the mic to speak"))
            }
        }

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) voicePanel?.setPartial(t)
        }

        override fun onResults(results: android.os.Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!t.isNullOrBlank()) commitVoice(t)
            voicePanel?.setPartial("")
            // Sigue escuchando hasta que toques el micrófono o salgas
            if (listening && mode == 3) handler.post { if (listening && mode == 3) startVoice() }
        }
    }

    /** Escribe lo dictado con signos, espacio y mayúscula donde corresponde. */
    private fun commitVoice(raw: String) {
        val ic = target() ?: return
        var t = VoiceText.process(raw, voiceLang)
        if (t.isEmpty()) return
        val before = ic.getTextBeforeCursor(64, 0) ?: ""
        if (TextUtil.isSentenceStart(before, 0) && autoCap) {
            val i = t.indexOfFirst { it.isLetter() }
            if (i >= 0) t = t.substring(0, i) + t[i].uppercaseChar() + t.substring(i + 1)
        }
        val needsSpace = before.isNotEmpty() && !before[before.length - 1].isWhitespace() &&
            t[0] !in ".,;:!?…\n"
        ic.commitText((if (needsSpace) " " else "") + t, 1)
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
    }

    // ---------------------------------------------------------------- Revisar ortografía

    /** Revisa todo el texto y empieza a mostrar los errores uno por uno. */
    private fun startSpell() {
        if (isPassword || !fieldSuggest) {
            Toast.makeText(this, L.t("Aquí no se puede revisar la ortografía.", "Spelling can't be checked here."), Toast.LENGTH_SHORT).show()
            return
        }
        if (translating) stopTranslate()
        if (mode != 0) showMode(0)
        val ic = currentInputConnection ?: return
        val eng = WordStore.engine ?: run {
            Toast.makeText(this, L.t("Un momento, el diccionario se está cargando…", "One moment, the dictionary is loading…"), Toast.LENGTH_SHORT).show()
            return
        }
        // Si seleccionaste palabras, se revisan solo esas (una por una, incluso tus palabras guardadas)
        var text: String
        var base: Int
        var selection = false
        val selected = if (selStart != selEnd) try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null } else null
        val et = if (!selected.isNullOrBlank()) null else try {
            ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 50000; hintMaxLines = 2000 }, 0)
        } catch (_: Exception) { null }
        if (!selected.isNullOrBlank()) {
            text = selected
            base = minOf(selStart, selEnd)
            selection = true
        } else if (et?.text != null) {
            text = et.text.toString()
            base = et.startOffset
        } else {
            val before = ic.getTextBeforeCursor(5000, 0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(5000, 0)?.toString() ?: ""
            text = before + after
            base = minOf(selStart, selEnd) - before.length
        }
        if (base < 0) base = 0
        val bar = sBar ?: return
        spellOn = true
        spellErrors = ArrayList()
        spellText = text
        spellBase = base
        bar.visibility = View.VISIBLE
        bar.resetButtons()
        bar.message(L.t("Revisando tu texto…", "Checking your text…"))
        kv?.spellOn = true
        val lng = lang
        val token = ++spellToken
        // Se revisa en segundo plano para que el teclado no se trabe con textos largos
        spellWorker.execute {
            val errs = ArrayList<SpellErr>()
            val found = Regex("[\\p{L}'’]+").findAll(text).toList()
            var k = 0
            while (k < found.size) {
                val m = found[k]
                // ¿Palabra partida en dos? ("toda vio" -> "todavía")
                if (k + 1 < found.size) {
                    val n = found[k + 1]
                    val gap = text.substring(m.range.last + 1, n.range.first)
                    if (gap == " ") {
                        val j = try { eng.joinedWord(m.value, n.value, lng) } catch (_: Exception) { null }
                        if (j != null) {
                            errs.add(SpellErr(base + m.range.first, base + n.range.last + 1, m.value + " " + n.value, listOf(j), true))
                            k += 2
                            continue
                        }
                    }
                }
                val r = try { eng.spellCheck(m.value, lng, 5, strict = true, ignoreUser = selection) } catch (_: Exception) { null }
                if (r != null && r.misspelled) errs.add(SpellErr(base + m.range.first, base + m.range.last + 1, m.value, r.suggestions, r.maybe))
                k++
            }
            handler.post {
                if (!spellOn || token != spellToken) return@post
                spellErrors = errs
                if (errs.isEmpty()) {
                    bar.message(if (selection) L.t("✅ No encontré errores en lo que seleccionaste.", "✅ No mistakes in your selection.")
                        else L.t("✅ No encontré errores de ortografía.", "✅ No spelling mistakes found."))
                    handler.postDelayed({ if (spellOn && spellErrors.isEmpty()) stopSpell() }, 2500)
                } else spellShow(0)
            }
        }
    }

    /** Muestra todo el texto con los errores marcados; el número [i] queda elegido (y marcado en la app). */
    private fun spellShow(i: Int) {
        val bar = sBar ?: return
        if (spellErrors.isEmpty()) {
            bar.message(L.t("✅ Listo, revisaste todo el texto.", "✅ Done, you checked all the text."))
            handler.postDelayed({ if (spellOn && spellErrors.isEmpty()) stopSpell() }, 2000)
            return
        }
        val idx = i.coerceIn(0, spellErrors.size - 1)
        spellIdx = idx
        val e = spellErrors[idx]
        currentInputConnection?.setSelection(e.start, e.end)
        val marks = spellErrors.map { SpellBar.Mark(it.start - spellBase, it.end - spellBase, it.maybe) }
        bar.showAll(spellText, marks, idx, e.sugg)
    }

    /** Cambia la palabra [e] por [word] en la app y en la copia del texto. */
    private fun spellReplace(e: SpellErr, word: String) {
        val ic = currentInputConnection ?: return
        ic.setSelection(e.start, e.end)
        ic.commitText(word, 1)
        val rs = e.start - spellBase
        val re = e.end - spellBase
        if (rs >= 0 && re <= spellText.length) spellText = spellText.substring(0, rs) + word + spellText.substring(re)
        val delta = word.length - (e.end - e.start)
        for (x in spellErrors) if (x !== e && x.start >= e.end) { x.start += delta; x.end += delta }
        WordStore.engine?.let { if (learnOn) { it.user.add(word); WordStore.scheduleSave() } }
    }

    /** Cambia la palabra elegida por la corrección y pasa a la siguiente. */
    private fun spellPick(word: String) {
        val e = spellErrors.getOrNull(spellIdx) ?: return
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        spellReplace(e, word)
        ic.endBatchEdit()
        spellErrors.remove(e)
        spellShow(spellIdx)
    }

    /** "Corregir todo": cambia cada palabra por su primera corrección (las que no tienen ideas se quedan). */
    private fun spellFixAll() {
        val ic = currentInputConnection ?: return
        val fixable = spellErrors.filter { it.sugg.isNotEmpty() }.sortedByDescending { it.start }
        if (fixable.isEmpty()) return
        ic.beginBatchEdit()
        // De atrás hacia adelante, para que las posiciones de las demás no cambien
        for (e in fixable) spellReplace(e, e.sugg[0])
        ic.endBatchEdit()
        spellErrors.removeAll(fixable.toSet())
        if (spellErrors.isEmpty()) {
            val end = spellBase + spellText.length
            ic.setSelection(end, end)
        }
        spellShow(0)
    }

    /** "Mi palabra": no la vuelve a marcar nunca (ni las demás iguales de este texto). */
    private fun spellLearn() {
        val e = spellErrors.getOrNull(spellIdx) ?: return
        WordStore.engine?.let { it.user.learn(e.word); WordStore.scheduleSave() }
        val lower = e.word.lowercase()
        spellErrors.removeAll { it.word.lowercase() == lower }
        spellShow(spellIdx)
    }

    /** "Saltar": la deja como está y pasa a la siguiente. */
    private fun spellSkip() {
        val e = spellErrors.getOrNull(spellIdx) ?: return
        spellErrors.remove(e)
        spellShow(spellIdx)
    }

    private fun stopSpell(keepSelection: Boolean = false) {
        if (!spellOn) return
        spellOn = false
        kv?.spellOn = false
        sBar?.visibility = View.GONE
        // Quitar la marca: el cursor queda al final de la palabra marcada
        if (!keepSelection && selStart != selEnd) currentInputConnection?.setSelection(selEnd, selEnd)
        spellErrors = ArrayList()
    }

    // ---------------------------------------------------------------- Traducción

    private fun startTranslate() {
        if (isPassword) {
            Toast.makeText(this, L.t("La traducción no funciona en campos de contraseña.", "Translation doesn't work in password fields."), Toast.LENGTH_SHORT).show()
            return
        }
        val tb = tBar ?: return
        if (mode != 0) showMode(0)
        tb.clear()
        tb.setLangs(tSrc, tDst)
        tb.visibility = View.VISIBLE
        translating = true
        kv?.translateOn = true
        tb.startCursor()
        lastTranslatedSrc = ""
        pendingSend = false
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        page = Page.LETTERS
        rebuild()
        prepareTranslator()
        updateAutoCaps()
        scheduleUpdate()
    }

    private fun prepareTranslator() {
        trans?.close()
        val tb = tBar ?: return
        tb.setStatus(L.t("Preparando… (la primera vez descarga el idioma, unos 30 MB)", "Getting ready… (the first time it downloads the language, about 30 MB)"))
        val t = Translation(tSrc, tDst)
        trans = t
        t.prepare(
            onReady = { if (trans === t) { tb.setStatus(""); doTranslate() } },
            onError = {
                if (trans === t) tb.setStatus(L.t("No se pudo descargar el idioma. Conéctate a internet una vez para descargarlo.", "Couldn't download the language. Connect to the internet once to download it."))
            },
        )
    }

    private fun stopTranslate() {
        if (!translating) return
        handler.removeCallbacks(translateRunnable)
        translating = false
        pendingSend = false
        tSeq++
        currentInputConnection?.finishComposingText()
        trans?.close()
        trans = null
        tBar?.let { it.clear(); it.visibility = View.GONE }
        kv?.translateOn = false
        lastCorrection = null
        updateAutoCaps()
        scheduleUpdate()
    }

    private fun swapTranslate() {
        val a = tSrc
        tSrc = tDst
        tDst = a
        prefs.edit().putString(Prefs.TR_SRC, tSrc).apply()
        tBar?.setLangs(tSrc, tDst)
        lastTranslatedSrc = ""
        prepareTranslator()
    }

    private fun doTranslate() {
        if (!translating) return
        val tb = tBar ?: return
        val text = tb.box.text?.toString() ?: ""
        val seq = ++tSeq
        if (text.isBlank()) {
            currentInputConnection?.setComposingText("", 1)
            lastTranslatedSrc = text
            if (pendingSend) sendTranslated()
            return
        }
        val t = trans ?: return
        if (!t.ready) return
        t.translate(text,
            onResult = { r ->
                if (seq == tSeq && translating) {
                    currentInputConnection?.setComposingText(r, 1)
                    lastTranslatedSrc = text
                    if (pendingSend) sendTranslated()
                }
            },
            onError = { if (seq == tSeq) tb.setStatus(L.t("No se pudo traducir. Inténtalo de nuevo.", "Couldn't translate. Try again.")) },
        )
    }

    /** Enter mientras traduces: deja la traducción en la app y la envía (como Gboard). */
    private fun translateEnter() {
        val text = tBar?.box?.text?.toString() ?: ""
        if (text == lastTranslatedSrc) {
            sendTranslated()
        } else {
            pendingSend = true
            handler.removeCallbacks(translateRunnable)
            doTranslate()
        }
    }

    private fun sendTranslated() {
        pendingSend = false
        handler.removeCallbacks(translateRunnable)
        tSeq++
        currentInputConnection?.finishComposingText()
        tBar?.clear()
        lastTranslatedSrc = ""
        translating = false
        handleEnter()
        translating = true
        updateAutoCaps()
        scheduleUpdate()
    }

    private fun commitPanelText(text: String) {
        val ic = target() ?: return
        gestureWord = null
        phantomSpace = false
        ic.commitText(text, 1)
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
    }

    // ---------------------------------------------------------------- Portapapeles

    /** Guarda en el historial lo que acabas de copiar (texto o imagen). */
    private fun captureClip() {
        if (!clipHistoryOn) return
        val cm = clipboard ?: return
        val clip = try { cm.primaryClip } catch (_: Exception) { null } ?: return
        if (clip.itemCount == 0) return
        val desc = clip.description
        // Contraseñas y datos marcados como privados no se guardan
        if (desc.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true) return
        val item = clip.getItemAt(0)
        val uri = item.uri
        val ts = if (Build.VERSION.SDK_INT >= 26) desc.timestamp else 0L
        val sig = "$ts|${item.text?.toString()?.hashCode()}|$uri"
        if (sig == lastClipSig) return
        lastClipSig = sig
        prefs.edit().putString(Prefs.CLIP_LAST_SIG, sig).apply()
        if (uri != null) {
            if (uri.authority == ClipStore.AUTHORITY) return // la pusimos nosotros
            var mime: String? = null
            for (i in 0 until desc.mimeTypeCount) {
                val mt = desc.getMimeType(i)
                if (mt.startsWith("image/")) { mime = mt; break }
            }
            if (mime == null) mime = try { contentResolver.getType(uri) } catch (_: Exception) { null }
            if (mime != null && mime.startsWith("image/")) {
                ClipStore.addImage(this, uri, mime)
                return
            }
        }
        val text = item.text?.toString()
            ?: (if (item.htmlText != null || uri != null) try { item.coerceToText(this)?.toString() } catch (_: Exception) { null } else null)
            ?: return
        ClipStore.addText(text)
    }

    /** Pega una imagen: directo si la app la acepta (WhatsApp, Gmail...), si no, la deja copiada. */
    private fun pasteImage(item: ClipItem) {
        val ic = currentInputConnection ?: return
        val uri = ClipStore.uriOf(item) ?: return
        val mime = item.mime ?: "image/png"
        val ei = currentInputEditorInfo
        if (Build.VERSION.SDK_INT >= 25 && ei != null) {
            val accepted = ei.contentMimeTypes ?: emptyArray()
            if (accepted.any { ClipDescription.compareMimeTypes(mime, it) }) {
                val info = InputContentInfo(uri, ClipDescription("Imagen", arrayOf(mime)), null)
                val ok = try {
                    ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                } catch (_: Exception) { false }
                if (ok) return
            }
        }
        try {
            clipboard?.setPrimaryClip(ClipData.newUri(contentResolver, "Imagen", uri))
            Toast.makeText(
                this,
                L.t("Esta app no recibe imágenes desde el teclado. Ya está copiada: mantén presionado donde escribes y toca Pegar.", "This app doesn't accept images from the keyboard. It's copied: long-press where you type and tap Paste."),
                Toast.LENGTH_LONG,
            ).show()
        } catch (_: Exception) { }
    }

    // Sin pantalla completa en horizontal: el teclado se ve igual que en vertical
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applySettings()

        val type = info.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_DATETIME
        phone = cls == InputType.TYPE_CLASS_PHONE
        isPassword = (cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        commaMode = when {
            cls != InputType.TYPE_CLASS_TEXT -> ","
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> "@"
            variation == InputType.TYPE_TEXT_VARIATION_URI -> "/"
            else -> ","
        }
        val noSuggestions = (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        fieldSuggest = cls == InputType.TYPE_CLASS_TEXT && !isPassword && commaMode == "," && !noSuggestions
        fieldCorrect = fieldSuggest && variation != InputType.TYPE_TEXT_VARIATION_FILTER
        fieldLearn = fieldSuggest && (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0
        kv?.enterAction = enterActionFor(info)

        if (!restarting) {
            page = if (numeric || phone) Page.NUM else Page.LETTERS
            shift = 0
            manualShift = false
            if (mode != 0) showMode(0)
            if (translating) stopTranslate()
            if (spellOn) stopSpell(keepSelection = true)
            hCtrl = 0; hAlt = 0; hShift = 0; hFn = false
        }
        lastWasSpace = false
        autoSpace = false
        lastCorrection = null
        rebuild()
        insertScanned(info)
        updateAutoCaps()
        scheduleUpdate()
        checkScreenshot()
        // Volviste de elegir una foto en la galería: ábrela en el escáner para elegir palabras
        ScanActivity.takePendingPhoto()?.let { bmp ->
            scanPhoto = bmp
            showMode(4)
        }
    }

    /** Si vienes de escanear texto con la cámara, lo escribe aquí. */
    private fun insertScanned(info: EditorInfo) {
        if (isPassword) return
        val text = ScanActivity.takePending() ?: return
        val ic = currentInputConnection ?: return
        if (info.inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL) return
        val before = ic.getTextBeforeCursor(1, 0) ?: ""
        val space = if (before.isNotEmpty() && !before[0].isWhitespace()) " " else ""
        ic.commitText(space + text, 1)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (shot != null || shotAsk) clearShot()
        if (mode != 0) showMode(0)
        if (translating) stopTranslate()
        if (spellOn) stopSpell(keepSelection = true)
        kv?.reset()
        clearSlots()
        WordStore.saveNow()
    }

    override fun onDestroy() {
        clipboard?.removePrimaryClipChangedListener(clipListener)
        try { contentResolver.unregisterContentObserver(mediaObserver) } catch (_: Exception) { }
        recognizer?.destroy()
        recognizer = null
        trans?.close()
        ClipStore.onChange = null
        WordStore.saveNow()
        super.onDestroy()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        updateAutoCaps()
        if (newSelStart != newSelEnd) {
            clearSlots()
            handler.removeCallbacks(updateRunnable)
        } else {
            scheduleUpdate()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySettings()
    }

    // ---------------------------------------------------------------- Ajustes

    private fun applySettings() {
        val v = kv ?: return
        val theme = prefs.getString(Prefs.THEME, "auto")
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = when (theme) {
            "light" -> false
            "dark" -> true
            else -> systemDark
        }
        val pal = if (dark) Palette.DARK else Palette.LIGHT
        v.palette = pal
        emojiPanel?.applyPalette(pal)
        clipHistoryOn = prefs.getBoolean(Prefs.CLIP_HISTORY, true)
        clipPanel?.let { it.historyOn = clipHistoryOn; it.applyPalette(pal) }
        voicePanel?.applyPalette(pal)
        tBar?.applyPalette(pal)
        sBar?.applyPalette(pal)
        scanPanel?.applyPalette(pal)
        editPanel?.applyPalette(pal)
        morePanel?.applyPalette(pal)
        val (barTools, moreTools) = Tools.load(this, prefs)
        v.setBarTools(barTools)
        morePanel?.setTools(barTools, moreTools, Tools.maxBar(this))
        colorNavBar(pal)

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val base = when (prefs.getString(Prefs.HEIGHT, "normal")) {
            "low" -> 46f
            "high" -> 58f
            else -> 52f
        }
        hacker = prefs.getBoolean(Prefs.HACKER, false)
        v.hackerOn = hacker
        // El teclado de computadora tiene 5 filas apretadas: teclas un poco más bajitas
        val hk = if (hacker && page != Page.NUM) 0.86f else 1f
        v.keyHeightDp = (if (landscape) base * 0.78f else base) * hk
        if (!v.resizing) v.heightScale = prefs.getFloat(Prefs.SIZE, 1f)
        v.showPreview = prefs.getBoolean(Prefs.PREVIEW, true)

        vibrateOn = prefs.getBoolean(Prefs.VIBRATE, true)
        when (prefs.getString(Prefs.VIB_STRENGTH, "medium")) {
            "soft" -> { vibMs = 8L; vibAmp = 60 }
            "strong" -> { vibMs = 25L; vibAmp = 255 }
            else -> { vibMs = 15L; vibAmp = 140 }
        }
        soundOn = prefs.getBoolean(Prefs.SOUND, false)
        autoCap = prefs.getBoolean(Prefs.AUTOCAP, true)
        doubleSpace = prefs.getBoolean(Prefs.DOUBLE_SPACE, true)
        lang = prefs.getString(Prefs.LANG, "es") ?: "es"
        L.set(lang)
        suggestOn = prefs.getBoolean(Prefs.SUGGEST, true)
        autocorrectOn = prefs.getBoolean(Prefs.AUTOCORRECT, true)
        learnOn = prefs.getBoolean(Prefs.LEARN, true)
        swipeOn = prefs.getBoolean(Prefs.SWIPE, true)
        v.swipeEnabled = swipeOn && page == Page.LETTERS && fieldSuggest

        val spaceLang = prefs.getBoolean(Prefs.SPACE_LANG, false)
        v.spaceSwitchesLang = spaceLang
        val newNumberRow = prefs.getBoolean(Prefs.NUMBER_ROW, true)
        if (newNumberRow != numberRow || Layouts.showGlobe == spaceLang) {
            numberRow = newNumberRow
            Layouts.showGlobe = !spaceLang
            rebuild()
        }
        v.spaceLabel = if (lang == "es") "Español" else "English"
    }

    @Suppress("DEPRECATION")
    private fun colorNavBar(p: Palette) {
        val w = window?.window ?: return
        w.navigationBarColor = p.bg
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val decor = w.decorView
            var flags = decor.systemUiVisibility
            flags = if (p.isDark) flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            else flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            decor.systemUiVisibility = flags
        }
    }

    private fun rebuild() {
        val v = kv ?: return
        if (hackerActive) {
            v.setRows(if (hFn) HackerLayouts.fn() else HackerLayouts.main(hShift > 0))
            v.shift = 0
            v.swipeEnabled = false
            updateMods()
            return
        }
        v.setMods(emptySet(), emptySet())
        val rows = when (page) {
            Page.LETTERS -> Layouts.letters(numberRow, commaMode)
            Page.SYM -> Layouts.symbols()
            Page.SYM2 -> Layouts.symbols2()
            Page.NUM -> Layouts.numpad(phone)
        }
        v.setRows(rows)
        v.shift = if (page == Page.LETTERS) shift else 0
        v.swipeEnabled = swipeOn && page == Page.LETTERS && fieldSuggest
    }

    private fun setShift(s: Int) {
        shift = s
        kv?.shift = if (page == Page.LETTERS && !hackerActive) s else 0
    }

    /** Pone mayúscula sola al empezar una oración. */
    private fun updateAutoCaps() {
        if (hackerActive) return
        if (shift == 2 || manualShift) return
        if (!autoCap || isPassword || page != Page.LETTERS) {
            if (shift == 1) setShift(0)
            return
        }
        val ic = target() ?: return
        val ei = targetInfo() ?: return
        val caps = ic.getCursorCapsMode(ei.inputType) != 0
        setShift(if (caps) 1 else 0)
    }

    private fun enterActionFor(ei: EditorInfo): Int {
        if (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EditorInfo.IME_ACTION_NONE
        val a = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        return if (a == EditorInfo.IME_ACTION_UNSPECIFIED) EditorInfo.IME_ACTION_NONE else a
    }

    // ---------------------------------------------------------------- Sugerencias

    private fun scheduleUpdate() {
        handler.removeCallbacks(updateRunnable)
        handler.post(updateRunnable)
    }

    private fun clearSlots() {
        slotWords.fill(null)
        slotTyped = -1
        kv?.clearSuggestions()
    }

    private fun showSlots(left: String?, center: String?, right: String?, bold: Boolean, typedLeft: Boolean) {
        slotWords[0] = left
        slotWords[1] = center
        slotWords[2] = right
        slotTyped = if (typedLeft) 0 else -1
        kv?.setSuggestions(left, center, right, bold, typedLeft)
    }

    private fun canCorrect(word: String, before: CharSequence): Boolean {
        if (!autocorrectOn || !fieldCorrect) return false
        // Un nombre propio en medio de la frase (ej. "Mendoza") no se corrige
        if (word.length > 1 && word[0].isUpperCase() && !TextUtil.isSentenceStart(before, word.length)) return false
        return true
    }

    private fun updateSuggestions() {
        val eng = WordStore.engine
        val ic = target()
        if (!suggestOn || !fieldSuggest || eng == null || ic == null || page == Page.NUM || hackerActive) {
            clearSlots(); return
        }
        val before = ic.getTextBeforeCursor(48, 0) ?: ""
        val after = ic.getTextAfterCursor(1, 0) ?: ""
        if (after.isNotEmpty() && TextUtil.isWordChar(after[0])) { clearSlots(); return }

        val word = TextUtil.currentWord(before)
        // Recién deslizaste: la barra muestra las otras palabras posibles
        val gw = gestureWord
        if (gw != null) {
            if (word == gw) {
                val others = gestureAlts.filter { it != gw }
                showSlots(others.getOrNull(0), gw, others.getOrNull(1), bold = false, typedLeft = false)
                return
            }
            gestureWord = null
        }
        if (word.isEmpty()) {
            // Predicción: lo que sueles escribir después de la palabra anterior
            val prev = TextUtil.previousWord(before, 0)
            val preds = if (prev != null) eng.predictions(prev, lang) else emptyList()
            if (preds.isEmpty()) { clearSlots(); return }
            val capital = shift > 0 && page == Page.LETTERS
            val p = preds.map { if (capital) TextUtil.matchCase(it, "A") else it }
            showSlots(p.getOrNull(1), p.getOrNull(0), p.getOrNull(2), bold = false, typedLeft = false)
            return
        }

        val prev = TextUtil.previousWord(before, word.length)
        val res = eng.suggest(word, prev, lang)
        val corr = if (canCorrect(word, before)) res.correction else null
        if (corr != null && corr != word) {
            val right = res.suggestions.firstOrNull { !it.equals(corr, true) && !it.equals(word, true) }
            showSlots(word, corr, right, bold = true, typedLeft = true)
            return
        }
        val list = res.suggestions.filter { it != word }.toMutableList()
        val known = eng.isKnown(word.lowercase())
        if (known || res.suggestions.any { it == word }) {
            // Lo que escribiste es una palabra: va en el centro
            showSlots(list.getOrNull(0), word, list.getOrNull(1), bold = false, typedLeft = false)
        } else {
            showSlots(word, list.getOrNull(0), list.getOrNull(1), bold = false, typedLeft = true)
        }
    }

    override fun onSuggestion(slot: Int) {
        val w = slotWords.getOrNull(slot) ?: return
        gestureWord = null
        phantomSpace = false
        val ic = target() ?: return
        val before = ic.getTextBeforeCursor(48, 0) ?: ""
        val cur = TextUtil.currentWord(before)
        val prev = TextUtil.previousWord(before, cur.length)
        ic.beginBatchEdit()
        if (cur.isNotEmpty()) ic.deleteSurroundingText(cur.length, 0)
        ic.commitText("$w ", 1)
        ic.endBatchEdit()
        val eng = WordStore.engine
        if (eng != null && learnOn && fieldLearn) {
            if (slot == slotTyped) eng.user.learn(w) else eng.user.add(w)
            if (prev != null) eng.user.addBigram(prev, w)
            WordStore.scheduleSave()
        }
        lastCorrection = null
        autoSpace = true
        lastWasSpace = true
        if (shift == 1) { manualShift = false; setShift(0) }
        if (page == Page.SYM || page == Page.SYM2) { page = Page.LETTERS; rebuild() }
        updateAutoCaps()
    }

    /**
     * Se llama justo antes de escribir un espacio o un signo: corrige la palabra si hace falta
     * y aprende lo que escribiste. Devuelve la corrección hecha (lo escrito, lo corregido) o null.
     */
    private fun finishWord(ic: InputConnection): Pair<String, String>? {
        if (!fieldSuggest || page == Page.NUM) return null
        val before = ic.getTextBeforeCursor(48, 0) ?: return null
        val word = TextUtil.currentWord(before)
        if (word.isEmpty()) return null
        val eng = WordStore.engine ?: return null
        val prev = TextUtil.previousWord(before, word.length)
        var final = word
        var fixed: Pair<String, String>? = null
        if (canCorrect(word, before)) {
            val corr = eng.correction(word, lang)?.let { TextUtil.matchCase(it, word) }
            if (corr != null && corr != word) {
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText(corr, 1)
                final = corr
                fixed = Pair(word, corr)
            }
        }
        if (learnOn && fieldLearn) {
            eng.user.add(final)
            if (prev != null) eng.user.addBigram(prev, final)
            WordStore.scheduleSave()
        }
        return fixed
    }

    // ---------------------------------------------------------------- Teclas

    override fun onKeyText(text: String) {
        if (spellOn) stopSpell()
        if (hackerActive) { hackerText(text); return }
        if (shot != null || shotAsk) clearShot()
        val ic = target() ?: return
        val phantom = phantomSpace
        phantomSpace = false
        gestureWord = null
        if (phantom && text.isNotEmpty() && text[0].isLetterOrDigit()) ic.commitText(" ", 1)
        var t = text
        if (page == Page.LETTERS && shift > 0 && t.length == 1) {
            t = Character.toUpperCase(t[0]).toString()
        }
        lastCorrection = null
        val punct = t.length == 1 && t[0] in ".,?!;:"
        if (punct && autoSpace) {
            // "hola " + "," -> "hola, " (el espacio que puso la sugerencia se mueve después del signo)
            val b = ic.getTextBeforeCursor(1, 0)
            if (b != null && b.length == 1 && b[0] == ' ') {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(1, 0)
                ic.commitText("$t ", 1)
                ic.endBatchEdit()
                autoSpace = false
                lastWasSpace = false
                if (shift == 1) { manualShift = false; setShift(0) }
                return
            }
        }
        autoSpace = false
        if (punct) {
            ic.beginBatchEdit()
            val fixed = finishWord(ic)
            ic.commitText(t, 1)
            ic.endBatchEdit()
            if (fixed != null) lastCorrection = Triple(fixed.first, fixed.second, t)
        } else {
            ic.commitText(t, 1)
        }
        lastWasSpace = false
        if (shift == 1) {
            manualShift = false
            setShift(0)
        }
    }

    override fun onKeyCode(code: Int) {
        if (spellOn && code != Codes.SHIFT) stopSpell()
        if (hackerActive && hackerKey(code)) return
        val ic = target()
        if (code != Codes.SHIFT) {
            lastCorrection = null
            phantomSpace = false
            gestureWord = null
        }
        when (code) {
            Codes.SPACE -> {
                if (ic == null) return
                autoSpace = false
                val before = if (doubleSpace && !isPassword && lastWasSpace) ic.getTextBeforeCursor(2, 0) else null
                if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    // Doble espacio = punto y espacio
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastWasSpace = false
                } else {
                    ic.beginBatchEdit()
                    val fixed = finishWord(ic)
                    ic.commitText(" ", 1)
                    ic.endBatchEdit()
                    if (fixed != null) lastCorrection = Triple(fixed.first, fixed.second, " ")
                    lastWasSpace = true
                }
                if (shift == 1) { manualShift = false; setShift(0) }
                if (page == Page.SYM || page == Page.SYM2) {
                    page = Page.LETTERS
                    rebuild()
                }
                updateAutoCaps()
            }
            Codes.ENTER -> {
                if (ic == null) return
                autoSpace = false
                if (translating) { translateEnter(); return }
                ic.beginBatchEdit()
                finishWord(ic)
                ic.endBatchEdit()
                handleEnter()
                lastWasSpace = false
                manualShift = false
                updateAutoCaps()
            }
            Codes.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                val quick = now - lastShiftTime < 350
                val next = when (shift) {
                    0 -> 1
                    1 -> if (quick) 2 else 0
                    else -> 0
                }
                lastShiftTime = now
                manualShift = next != 0
                setShift(next)
                scheduleUpdate()
            }
            Codes.MODE_SYM -> { page = Page.SYM; rebuild() }
            Codes.MODE_SYM2 -> { page = Page.SYM2; rebuild() }
            Codes.MODE_LETTERS -> {
                page = Page.LETTERS
                manualShift = false
                rebuild()
                updateAutoCaps()
            }
            Codes.LANG -> {
                lang = if (lang == "es") "en" else "es"
                prefs.edit().putString(Prefs.LANG, lang).apply()
                L.set(lang)
                kv?.spaceLabel = if (lang == "es") "Español" else "English"
                scheduleUpdate()
            }
        }
    }

    private fun handleEnter() {
        val ic = target() ?: return
        val ei = targetInfo()
        val action = if (ei != null) enterActionFor(ei) else EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
            return
        }
        val multiLine = ei != null && (ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (multiLine) ic.commitText("\n", 1) else sendKey(KeyEvent.KEYCODE_ENTER)
    }

    override fun onDeletePress() {
        if (spellOn) stopSpell()
        if (hackerActive) { sendKey(KeyEvent.KEYCODE_DEL, hMeta()); hConsume(); return }
        val ic = target() ?: return
        autoSpace = false
        lastWasSpace = false
        phantomSpace = false
        // Borrar justo después de deslizar borra la palabra entera
        val gw = gestureWord
        gestureWord = null
        if (gw != null) {
            val before = ic.getTextBeforeCursor(gw.length, 0)
            if (before != null && before.toString() == gw) {
                ic.deleteSurroundingText(gw.length, 0)
                return
            }
        }
        val lc = lastCorrection
        lastCorrection = null
        if (lc != null) {
            // Deshacer la autocorrección: "también " -> "tambien" (y ya no se corrige más)
            val (typed, corr, sep) = lc
            val tail = corr + sep
            val before = ic.getTextBeforeCursor(tail.length, 0)
            if (before != null && before.toString() == tail) {
                ic.beginBatchEdit()
                ic.deleteSurroundingText(tail.length, 0)
                ic.commitText(typed, 1)
                ic.endBatchEdit()
                val eng = WordStore.engine
                if (eng != null && learnOn && fieldLearn) {
                    eng.user.learnSoft(typed)
                    WordStore.scheduleSave()
                }
                return
            }
        }
        sendKey(KeyEvent.KEYCODE_DEL)
    }

    override fun onCursorMove(steps: Int) {
        if (target() == null) return
        gestureWord = null
        phantomSpace = false
        val key = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendKey(key) }
        if (translating) { updateAutoCaps(); scheduleUpdate() }
        lastWasSpace = false
        autoSpace = false
        lastCorrection = null
    }

    override fun onToolbar(action: Int) {
        when (action) {
            KeyboardView.TOOL_SETTINGS -> {
                val i = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(i)
            }
            KeyboardView.TOOL_THEME -> {
                val nowDark = kv?.palette?.isDark ?: true
                prefs.edit().putString(Prefs.THEME, if (nowDark) "light" else "dark").apply()
                applySettings()
            }
            KeyboardView.TOOL_HIDE -> requestHideSelf(0)
            KeyboardView.TOOL_EMOJI -> showMode(1)
            KeyboardView.TOOL_CLIP -> showMode(2)
            KeyboardView.TOOL_VOICE -> showMode(3)
            KeyboardView.TOOL_EDIT -> showMode(5)
            KeyboardView.TOOL_SHOT -> useScreenshot()
            KeyboardView.TOOL_SPELL -> if (spellOn) stopSpell() else startSpell()
            KeyboardView.TOOL_SCAN -> {
                if (hasPermission(android.Manifest.permission.CAMERA)) {
                    showMode(4)
                } else {
                    Toast.makeText(this, L.t("Dale permiso a la cámara y vuelve a tocar el escáner.", "Allow the camera and tap the scanner again."), Toast.LENGTH_LONG).show()
                    askPermission(MainActivity.EXTRA_ASK_CAMERA)
                }
            }
            KeyboardView.TOOL_TRANSLATE -> if (translating) stopTranslate() else startTranslate()
            KeyboardView.TOOL_MORE -> showMode(6)
            KeyboardView.TOOL_HACKER -> toggleHacker()
            KeyboardView.TOOL_LANG -> onKeyCode(Codes.LANG)
            KeyboardView.TOOL_RESIZE -> {
                showMode(0)
                kv?.startResize()
            }
        }
    }

    // ---------------------------------------------------------------- Teclado de computadora

    /** Enciende o apaga el teclado de computadora ("modo hacker"). */
    private fun toggleHacker() {
        hacker = !hacker
        prefs.edit().putBoolean(Prefs.HACKER, hacker).apply()
        hCtrl = 0; hAlt = 0; hShift = 0; hFn = false
        if (mode != 0) showMode(0)
        if (page != Page.NUM) page = Page.LETTERS
        applySettings()
        rebuild()
        if (hacker) {
            clearSlots()
            setShift(0)
            Toast.makeText(this, L.t("Teclado de computadora: Ctrl, Alt, Esc, Tab, flechas y Fn (F1–F12)",
                "PC keyboard: Ctrl, Alt, Esc, Tab, arrows and Fn (F1–F12)"), Toast.LENGTH_SHORT).show()
        } else {
            updateAutoCaps()
            scheduleUpdate()
        }
    }

    /** Ctrl/Alt/Shift encendidos ahora mismo, como "meta state" de Android. */
    private fun hMeta(): Int {
        var m = 0
        if (hCtrl > 0) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (hAlt > 0) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (hShift > 0) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        return m
    }

    /** Las teclas encendidas "una vez" se apagan después de usarse (las fijas se quedan). */
    private fun hConsume() {
        val shiftWas = hShift
        if (hCtrl == 1) hCtrl = 0
        if (hAlt == 1) hAlt = 0
        if (hShift == 1) hShift = 0
        if (shiftWas != hShift) rebuild() else updateMods()
    }

    private fun updateMods() {
        val once = HashSet<Int>()
        val locked = HashSet<Int>()
        fun put(code: Int, st: Int) { if (st == 1) once.add(code) else if (st == 2) locked.add(code) }
        put(Codes.CTRL, hCtrl)
        put(Codes.ALT, hAlt)
        put(Codes.HSHIFT, hShift)
        if (hFn) locked.add(Codes.FN)
        kv?.setMods(once, locked)
    }

    /** Un toque: solo la próxima tecla. Dos toques: fijo. Tres: apagado. */
    private fun nextModState(cur: Int) = (cur + 1) % 3

    /** Teclas especiales del teclado de computadora. Devuelve true si la manejó. */
    private fun hackerKey(code: Int): Boolean {
        when {
            code == Codes.CTRL -> { hCtrl = nextModState(hCtrl); updateMods() }
            code == Codes.ALT -> { hAlt = nextModState(hAlt); updateMods() }
            code == Codes.HSHIFT -> { hShift = nextModState(hShift); rebuild() }
            code == Codes.FN -> { hFn = !hFn; rebuild() }
            code == Codes.ENTER -> { sendKey(KeyEvent.KEYCODE_ENTER, hMeta()); hConsume() }
            code == Codes.SPACE -> {
                if (hCtrl > 0 || hAlt > 0) sendKey(KeyEvent.KEYCODE_SPACE, hMeta())
                else target()?.commitText(" ", 1)
                hConsume()
            }
            Codes.isHKey(code) -> { sendKey(Codes.keyCodeOf(code), hMeta()); hConsume() }
            else -> return false
        }
        return true
    }

    private val keyMap by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }

    /**
     * Escribe un carácter. Con Ctrl o Alt se manda como tecla de verdad (Ctrl+C, Alt+B...),
     * que es lo que entienden las terminales.
     */
    private fun hackerText(text: String) {
        val ic = target() ?: return
        if ((hCtrl > 0 || hAlt > 0) && text.length == 1) {
            val events = try { keyMap.getEvents(charArrayOf(text[0])) } catch (_: Exception) { null }
            val ev = events?.firstOrNull {
                it.action == KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(it.keyCode)
            }
            if (ev != null) {
                sendKey(ev.keyCode, hMeta() or ev.metaState)
                hConsume()
                return
            }
        }
        ic.commitText(text, 1)
        hConsume()
    }

    override fun onResizeDone(scale: Float) {
        prefs.edit().putFloat(Prefs.SIZE, scale).apply()
    }

    override fun onGesture(xs: FloatArray, ys: FloatArray, count: Int) {
        val v = kv ?: return
        val ic = target() ?: return
        val dec = WordStore.gesture ?: return
        val before = ic.getTextBeforeCursor(48, 0) ?: ""
        // Si estabas a mitad de una palabra escrita con toques, termínala primero
        val partial = TextUtil.currentWord(before)
        val prev = if (partial.isEmpty()) TextUtil.previousWord(before, 0) else partial
        val found = dec.decode(xs, ys, count, v.letterCenters(), v.letterWidth(), lang, prev)
        if (found.isEmpty()) return
        fun cased(w: String) = when {
            shift == 2 -> w.uppercase()
            shift == 1 -> w.replaceFirstChar { it.uppercaseChar() }
            else -> w
        }
        val words = found.map { cased(it) }
        val word = words[0]
        val last = if (before.isEmpty()) ' ' else before[before.length - 1]
        val needSpace = before.isNotEmpty() && !last.isWhitespace() && last !in "¿¡(\"'“‘@#/"
        ic.beginBatchEdit()
        ic.commitText((if (needSpace) " " else "") + word, 1)
        ic.endBatchEdit()
        val eng = WordStore.engine
        if (eng != null && learnOn && fieldLearn) {
            eng.user.add(word)
            if (prev != null) eng.user.addBigram(prev, word)
            WordStore.scheduleSave()
        }
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        gestureWord = word
        gestureAlts = words
        phantomSpace = true
        if (shift == 1) { manualShift = false; setShift(0) }
        scheduleUpdate()
    }

    override fun onLongPressGlobe() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showInputMethodPicker()
    }

    override fun onKeyFeedback() {
        if (vibrateOn) {
            val vib = vibrator
            if (vib != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val amp = if (vib.hasAmplitudeControl()) vibAmp else VibrationEffect.DEFAULT_AMPLITUDE
                    vib.vibrate(VibrationEffect.createOneShot(vibMs, amp))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(vibMs)
                }
            }
        }
        if (soundOn) audio?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
    }
}
