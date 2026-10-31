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
    private var spellOn = false
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

    // Sugerencia de captura de pantalla
    private var shot: Screenshots.Shot? = null
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
            override fun onMicTap() = if (listening) stopVoice("Toca el micrófono para hablar") else startVoice()
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
            override fun onSpellSkip() { onKeyFeedback(); spellShow(spellIdx + 1) }
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
        if (m == 0) { updateAutoCaps(); scheduleUpdate() }
    }

    // ---------------------------------------------------------------- Capturas de pantalla

    /** Si tomaste una captura hace poco, la ofrece arriba del teclado. */
    private fun checkScreenshot() {
        if (!prefs.getBoolean(Prefs.SHOTS, true) || isPassword) { clearShot(); return }
        val app = applicationContext
        val usedId = prefs.getLong(Prefs.SHOT_USED, -1L)
        val px = (48 * resources.displayMetrics.density).toInt()
        shotIo.execute {
            val s = Screenshots.findRecent(app)
            val thumb = if (s != null && s.id != usedId) Screenshots.thumbnail(app, s, px) else null
            handler.post {
                if (s != null && s.id != usedId && thumb != null) {
                    shot = s
                    kv?.setScreenshot(thumb)
                } else clearShot()
            }
        }
    }

    private fun clearShot() {
        shot = null
        kv?.setScreenshot(null)
    }

    /** Tocaste "Captura de pantalla": se guarda en el portapapeles y se pega aquí. */
    private fun useScreenshot() {
        val s = shot ?: return
        prefs.edit().putLong(Prefs.SHOT_USED, s.id).apply()
        clearShot()
        ClipStore.addImage(this, s.uri, s.mime) { item ->
            if (item != null) pasteImage(item)
            else Toast.makeText(this, "No se pudo abrir la captura.", Toast.LENGTH_SHORT).show()
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
            vp.setStatus("Necesito permiso para usar el micrófono. Ábrelo y toca \"Permitir\".")
            val i = Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ASK_MIC, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try { startActivity(i) } catch (_: Exception) { }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            vp.setStatus("Tu teléfono no tiene reconocimiento de voz. Instala o actualiza la app Google.")
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
        vp.setStatus(if (voiceLang == "es") "Escuchando… habla ahora" else "Listening… speak now")
        try {
            r.startListening(intent)
        } catch (_: Exception) {
            stopVoice("No se pudo usar el micrófono. Inténtalo de nuevo.")
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
        override fun onEndOfSpeech() { if (listening) voicePanel?.setStatus("Escribiendo…") }
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

        override fun onError(error: Int) {
            if (!listening) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    stopVoice("Toca el micrófono para hablar")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    stopVoice("Falta el permiso del micrófono (ábrelo en los ajustes ⚙).")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    stopVoice("Sin internet. Para dictar sin conexión, descarga el idioma en la app Google (Voz → Reconocimiento sin conexión).")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    stopVoice("El micrófono está ocupado. Toca para intentarlo de nuevo.")
                else -> stopVoice("Toca el micrófono para hablar")
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
            Toast.makeText(this, "Aquí no se puede revisar la ortografía.", Toast.LENGTH_SHORT).show()
            return
        }
        if (translating) stopTranslate()
        if (mode != 0) showMode(0)
        val ic = currentInputConnection ?: return
        val eng = WordStore.engine ?: run {
            Toast.makeText(this, "Un momento, el diccionario se está cargando…", Toast.LENGTH_SHORT).show()
            return
        }
        // Todo el texto del cuadro (o lo que la app deje leer alrededor del cursor)
        var text: String
        var base: Int
        val et = try {
            ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 50000; hintMaxLines = 2000 }, 0)
        } catch (_: Exception) { null }
        if (et?.text != null) {
            text = et.text.toString()
            base = et.startOffset
        } else {
            val before = ic.getTextBeforeCursor(5000, 0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(5000, 0)?.toString() ?: ""
            text = before + after
            base = minOf(selStart, selEnd) - before.length
        }
        if (base < 0) base = 0
        spellErrors = ArrayList()
        val found = Regex("[\\p{L}'’]+").findAll(text).toList()
        var k = 0
        while (k < found.size) {
            val m = found[k]
            // ¿Palabra partida en dos? ("toda vio" -> "todavía")
            if (k + 1 < found.size) {
                val n = found[k + 1]
                val gap = text.substring(m.range.last + 1, n.range.first)
                if (gap == " ") {
                    val j = try { eng.joinedWord(m.value, n.value, lang) } catch (_: Exception) { null }
                    if (j != null) {
                        spellErrors.add(SpellErr(base + m.range.first, base + n.range.last + 1, m.value + " " + n.value, listOf(j), true))
                        k += 2
                        continue
                    }
                }
            }
            val r = try { eng.spellCheck(m.value, lang, 5, strict = true) } catch (_: Exception) { null }
            if (r != null && r.misspelled) spellErrors.add(SpellErr(base + m.range.first, base + m.range.last + 1, m.value, r.suggestions, r.maybe))
            k++
        }
        val bar = sBar ?: return
        spellOn = true
        bar.visibility = View.VISIBLE
        bar.resetButtons()
        kv?.spellOn = true
        if (spellErrors.isEmpty()) {
            bar.message("✅ No encontré errores de ortografía.")
            handler.postDelayed({ if (spellOn && spellErrors.isEmpty()) stopSpell() }, 2500)
            return
        }
        spellShow(0)
    }

    /** Marca el error número [i] en la app y muestra sus correcciones. */
    private fun spellShow(i: Int) {
        val bar = sBar ?: return
        spellIdx = i
        if (i >= spellErrors.size) {
            val last = spellErrors.lastOrNull()
            if (last != null) currentInputConnection?.setSelection(last.end, last.end)
            bar.message("✅ Listo, revisaste todo el texto.")
            spellErrors = ArrayList()
            handler.postDelayed({ if (spellOn && spellErrors.isEmpty()) stopSpell() }, 2000)
            return
        }
        val e = spellErrors[i]
        currentInputConnection?.setSelection(e.start, e.end)
        bar.show(i + 1, spellErrors.size, e.word, e.sugg, e.maybe)
    }

    /** Cambia la palabra marcada por la corrección elegida y pasa a la siguiente. */
    private fun spellPick(word: String) {
        val ic = currentInputConnection ?: return
        val e = spellErrors.getOrNull(spellIdx) ?: return
        ic.beginBatchEdit()
        ic.setSelection(e.start, e.end)
        ic.commitText(word, 1)
        ic.endBatchEdit()
        val delta = word.length - (e.end - e.start)
        for (j in spellIdx + 1 until spellErrors.size) {
            spellErrors[j].start += delta
            spellErrors[j].end += delta
        }
        WordStore.engine?.let { if (learnOn) { it.user.add(word); WordStore.scheduleSave() } }
        spellShow(spellIdx + 1)
    }

    /** "Mi palabra": no la vuelve a marcar nunca (ni las demás iguales de este texto). */
    private fun spellLearn() {
        val e = spellErrors.getOrNull(spellIdx) ?: return
        WordStore.engine?.let { it.user.learn(e.word); WordStore.scheduleSave() }
        val lower = e.word.lowercase()
        val keep = ArrayList<SpellErr>()
        for ((j, x) in spellErrors.withIndex()) if (j <= spellIdx || x.word.lowercase() != lower) keep.add(x)
        spellErrors = keep
        spellShow(spellIdx + 1)
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
            Toast.makeText(this, "La traducción no funciona en campos de contraseña.", Toast.LENGTH_SHORT).show()
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
        tb.setStatus("Preparando… (la primera vez descarga el idioma, unos 30 MB)")
        val t = Translation(tSrc, tDst)
        trans = t
        t.prepare(
            onReady = { if (trans === t) { tb.setStatus(""); doTranslate() } },
            onError = {
                if (trans === t) tb.setStatus("No se pudo descargar el idioma. Conéctate a internet una vez para descargarlo.")
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
            onError = { if (seq == tSeq) tb.setStatus("No se pudo traducir. Inténtalo de nuevo.") },
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
                "Esta app no recibe imágenes desde el teclado. Ya está copiada: mantén presionado donde escribes y toca Pegar.",
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
        colorNavBar(pal)

        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val base = when (prefs.getString(Prefs.HEIGHT, "normal")) {
            "low" -> 46f
            "high" -> 58f
            else -> 52f
        }
        v.keyHeightDp = if (landscape) base * 0.78f else base
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
        suggestOn = prefs.getBoolean(Prefs.SUGGEST, true)
        autocorrectOn = prefs.getBoolean(Prefs.AUTOCORRECT, true)
        learnOn = prefs.getBoolean(Prefs.LEARN, true)
        swipeOn = prefs.getBoolean(Prefs.SWIPE, true)
        v.swipeEnabled = swipeOn && page == Page.LETTERS && fieldSuggest

        val newNumberRow = prefs.getBoolean(Prefs.NUMBER_ROW, true)
        if (newNumberRow != numberRow) {
            numberRow = newNumberRow
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
        kv?.shift = if (page == Page.LETTERS) s else 0
    }

    /** Pone mayúscula sola al empezar una oración. */
    private fun updateAutoCaps() {
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
        if (!suggestOn || !fieldSuggest || eng == null || ic == null || page == Page.NUM) {
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
        if (shot != null) clearShot()
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
                    eng.user.learn(typed)
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
                    Toast.makeText(this, "Dale permiso a la cámara y vuelve a tocar el escáner.", Toast.LENGTH_LONG).show()
                    askPermission(MainActivity.EXTRA_ASK_CAMERA)
                }
            }
            KeyboardView.TOOL_TRANSLATE -> if (translating) stopTranslate() else startTranslate()
        }
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
