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
import android.view.ViewGroup
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

    private enum class Page { LETTERS, SYM, SYM2, NUM, IP }
    /** Teclado de números o el de IP/MAC (sin sugerencias ni teclado de PC). */
    private val numLike: Boolean get() = page == Page.NUM || page == Page.IP

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
    private var vibStyle: String? = "modern"
    private var vibStrength: String? = "medium"
    private var soundStyle: String? = "modern"
    private var soundVol: String? = "medium"
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
    /** La casilla de la barra que tiene un emoji sugerido (o -1). */
    private var emojiSlot = -1
    /** El texto antes del cursor, leído una vez por actualización de sugerencias. */
    private var sugBefore: CharSequence? = null
    /** La casilla con una palabra más formal sugerida (o -1) y qué cambiaría. */
    private var formalSlot = -1
    private var formalMatch: FormalWords.Match? = null
    private var slotsBold = false
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
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClip()
        kv?.flashTool(KeyboardView.TOOL_CLIP) // 📋 destella el portapapeles
        handler.removeCallbacks(quickCheck)
        handler.postDelayed(quickCheck, 150)
    }

    // Revisar ortografía palabra por palabra
    private class SpellErr(var start: Int, var end: Int, val word: String, val sugg: List<String>, val maybe: Boolean)
    private var sBar: SpellBar? = null
    private var spellErrors = ArrayList<SpellErr>()
    private var spellIdx = 0
    /** Copia del texto que se está revisando (para mostrarlo con los errores marcados). */
    private var spellText = ""
    private var spellBase = 0
    private var spellToken = 0
    /** Revisando un texto seleccionado (ahí también se puede "Reescribir formal"). */
    private var spellSelection = false
    private var rewrites: List<String> = emptyList()
    private val spellWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var spellOn = false

    // Modo código: teclado para programar + ayuda de la IA (explicar, escribir, arreglar, pistas)
    private var codeOn = false
    private var codeLang = "Python"
    private var codeBar: CodeBar? = null
    private var codeToken = 0
    /** Las sugerencias de arriba son fragmentos de código (y estos son los fragmentos completos). */
    private var codeActive = false
    private val codeValues = arrayOfNulls<String>(3)
    /** Escribiendo la descripción en la cajita de "Escribir código". */
    private val codeAsking get() = codeOn && codeBar?.asking == true

    // Teclado de computadora ("modo hacker")
    private var hacker = false
    private var hFn = false
    /** 0 = apagado, 1 = solo para la próxima tecla, 2 = fijo. */
    private var hCtrl = 0
    private var hAlt = 0
    private var hShift = 0
    private val hackerActive get() = hacker && !numLike
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
    // Frases (7), calculadora (8) y GIFs (9)
    private var phrasePanel: PhrasePanel? = null
    private var calcPanel: CalcPanel? = null
    private var gifPanel: GifPanel? = null
    private var notebookPanel: ListPanel? = null
    private var ticketsPanel: ListPanel? = null
    private var workdayPanel: ListPanel? = null
    private var materialsPanel: ListPanel? = null
    private var todoPanel: ListPanel? = null
    private var bgVideoView: BgVideoView? = null
    private var bgImageView: android.widget.ImageView? = null
    private var bgLoaded = ""
    private var checkPanel: ListPanel? = null
    private var drivePanel: DrivePanel? = null
    /** "No mostrar el modo manejando" hasta el próximo evento de la jornada (tocaste ⌨️ Teclado). */
    private var driveSkipEvent = -1L
    /** Barra arriba del teclado con el mensaje traducido. */
    private var infoBar: InfoBar? = null
    private var readTrans: Translation? = null
    /** Buscar emojis o GIFs: 0 no, 1 emojis, 2 GIFs. */
    private var searching = 0
    private var searchBar: SearchBar? = null
    /** Lo que buscaste en la barra, para abrir los GIFs con eso. */
    private var pendingGifQuery: String? = null

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

    // Sugerencia rápida: lo que copiaste o el código del SMS (va antes que la captura)
    private var quick: QuickPaste.Item? = null
    /** Las sugerencias de arriba son de "Mis datos" (y estos son sus valores completos). */
    private var vaultActive = false
    private val vaultValues = arrayOfNulls<String>(3)
    private val quickCheck = Runnable { if (isInputViewShown) checkQuick() }
    /** Avisa cuando llega un SMS nuevo (para ofrecer el código con el teclado abierto). */
    private val smsObserver = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(quickCheck)
            handler.postDelayed(quickCheck, 700)
        }
    }

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
    private fun target(): InputConnection? = when {
        searching != 0 -> searchBar?.connection
        translating -> tBar?.connection
        codeAsking -> codeBar?.connection
        else -> currentInputConnection
    }
    private fun targetInfo(): EditorInfo? = when {
        searching != 0 -> searchBar?.inputInfo
        translating -> tBar?.inputInfo
        codeAsking -> codeBar?.inputInfo
        else -> currentInputEditorInfo
    }

    /** Envía una tecla (borrar, flechas, enter) a donde esté escribiendo el teclado. */
    private fun sendKey(code: Int, meta: Int = 0) {
        // La caja del traductor está dentro del teclado: ahí las teclas se aplican directo
        val inner = when {
            translating -> tBar?.box
            codeAsking -> codeBar?.box
            else -> null
        }
        val sb = if (searching != 0) searchBar else null
        if (sb != null) {
            when (code) {
                KeyEvent.KEYCODE_DEL -> sb.deleteBack()
                KeyEvent.KEYCODE_DPAD_LEFT -> sb.moveCursor(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> sb.moveCursor(1)
            }
            return
        }
        if (inner != null) {
            when (code) {
                KeyEvent.KEYCODE_DEL -> inner.deleteBack()
                KeyEvent.KEYCODE_DPAD_LEFT -> inner.moveCursor(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> inner.moveCursor(1)
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
        CloudText.onSuccess = { kv?.flashAi() } // ✨ la IA terminó: destello de luz
        TicketReminder.schedule(this)
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        WordStore.load(this) { scheduleUpdate() }
        Feedback.init(this)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        EmojiData.load(this)
        ClipStore.load(this)
        ClipStore.onChange = { if (mode == 2) clipPanel?.refresh() }
        lastClipSig = prefs.getString(Prefs.CLIP_LAST_SIG, null)
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.addPrimaryClipChangedListener(clipListener)
        try {
            contentResolver.registerContentObserver(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
            contentResolver.registerContentObserver(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, mediaObserver)
        } catch (_: Exception) { }
        try {
            contentResolver.registerContentObserver(android.net.Uri.parse("content://sms"), true, smsObserver)
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
        // 🖼️ El video de fondo va detrás de las teclas
        val bgv = BgVideoView(this)
        bgv.visibility = View.GONE
        box.addView(bgv, 0, FrameLayout.LayoutParams(-1, -1))
        bgVideoView = bgv
        // La imagen de fondo también va detrás (así se ve en emojis, portapapeles y todos los paneles)
        val bgi = android.widget.ImageView(this)
        bgi.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        bgi.visibility = View.GONE
        box.addView(bgi, 0, FrameLayout.LayoutParams(-1, -1))
        bgImageView = bgi
        val ep = EmojiPanel(this)
        ep.listener = panelListener
        ep.actions = object : EmojiPanel.Actions {
            override fun onEmojiSearch() = startSearch(false)
            override fun onEmojiGif() = showMode(9)
        }
        ep.visibility = View.GONE
        box.addView(ep, FrameLayout.LayoutParams(-1, -1))
        emojiPanel = ep
        val cp = ClipPanel(this)
        cp.listener = panelListener
        cp.actions = object : ClipPanel.Actions {
            override fun onPasteText(text: String) = commitPanelText(text)
            override fun onPasteImage(item: ClipItem) = pasteImage(item)
            override fun onClipWrite(item: ClipItem?) {
                clipEditing = item
                startSearch(gif = false, clipText = item?.text ?: "")
            }
            override fun onClipFind() = openFind()
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
            override fun onEnglishTap() {
                voiceEnglish = !voiceEnglish
                vp.setEnglish(voiceEnglish)
                if (voiceEnglish) {
                    // Hablas en español
                    voiceLang = "es"
                    vp.setLang(voiceLang)
                    if (listening) { recognizer?.cancel(); startVoice() }
                    val tr = voiceTrans ?: Translation("es", "en").also { voiceTrans = it }
                    if (!tr.ready) tr.prepare({}, {})
                    vp.setStatus(L.t("Habla en español: se escribe en inglés.", "Speak Spanish: it's typed in English."))
                }
            }
            override fun onReportTap() {
                when {
                    !reportMode -> {
                        reportMode = true
                        reportBuf.setLength(0)
                        vp.setReport(true, false)
                        vp.setPartial("")
                        if (!listening) startVoice()
                        vp.setStatus(L.t("Habla con tus palabras. Al terminar toca «Crear reporte».", "Speak in your own words. When done tap «Create report»."))
                    }
                    reportBuf.isBlank() -> { reportMode = false; vp.setReport(false, false) }
                    else -> finishReport()
                }
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
                    KeyboardView.TOOL_EDIT, KeyboardView.TOOL_SCAN, KeyboardView.TOOL_PHRASES,
                    KeyboardView.TOOL_CALC, KeyboardView.TOOL_GIF, KeyboardView.TOOL_REPORT, KeyboardView.TOOL_BARCODE,
                    KeyboardView.TOOL_NOTEBOOK, KeyboardView.TOOL_TICKETS, KeyboardView.TOOL_WORKDAY,
                    KeyboardView.TOOL_MATERIALS, KeyboardView.TOOL_DRIVE, KeyboardView.TOOL_TODO, KeyboardView.TOOL_CHECK -> onToolbar(tool)
                    else -> { showMode(0); onToolbar(tool) }
                }
            }
            override fun onMoreBack() = showMode(0)
            override fun onMoreFeedback() = onKeyFeedback()
            override fun onToolsChanged(bar: List<Int>, more: List<Int>) {
                Tools.save(prefs, bar, more)
                kv?.setBarTools(bar, more)
            }
        }
        box.addView(more, FrameLayout.LayoutParams(-1, -1))
        morePanel = more

        // Frases rápidas del trabajo
        val pp = PhrasePanel(this)
        pp.visibility = View.GONE
        pp.actions = object : PhrasePanel.Actions {
            override fun onPhrase(text: String) { showMode(0); insertPhrase(text) }
            override fun onPhraseBack() = showMode(0)
            override fun onPhraseEdit() {
                showMode(0)
                try {
                    startActivity(Intent(this@BestKeyboardService, PhrasesActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                } catch (_: Exception) { }
            }
            override fun onPhraseFeedback() = onKeyFeedback()
        }
        box.addView(pp, FrameLayout.LayoutParams(-1, -1))
        phrasePanel = pp

        // Calculadora
        val calc = CalcPanel(this)
        calc.visibility = View.GONE
        calc.actions = object : CalcPanel.Actions {
            override fun onCalcInsert(text: String) { showMode(0); insertText(text) }
            override fun onCalcBack() = showMode(0)
            override fun onCalcFeedback() = onKeyFeedback()
        }
        box.addView(calc, FrameLayout.LayoutParams(-1, -1))
        calcPanel = calc

        // GIFs y stickers
        val gp = GifPanel(this)
        gp.visibility = View.GONE
        gp.actions = object : GifPanel.Actions {
            override fun onGifPick(file: java.io.File) {
                pasteUri(android.net.Uri.parse("content://${ClipStore.AUTHORITY}/${file.name}"), "image/gif")
            }
            override fun onGifBack() = showMode(0)
            override fun onGifSettings() {
                showMode(0)
                try {
                    startActivity(Intent(this@BestKeyboardService, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_GIFS, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                } catch (_: Exception) { }
            }
            override fun onGifFeedback() = onKeyFeedback()
            override fun onGifSearch() = startSearch(true)
        }
        box.addView(gp, FrameLayout.LayoutParams(-1, -1))
        gifPanel = gp

        // Libreta del día (10) y Mis tickets (11)
        val nb = ListPanel(this)
        nb.visibility = View.GONE
        nb.onBack = { showMode(0) }
        nb.onFeedback = { onKeyFeedback() }
        box.addView(nb, FrameLayout.LayoutParams(-1, -1))
        notebookPanel = nb
        val tk = ListPanel(this)
        tk.visibility = View.GONE
        tk.onBack = { showMode(0) }
        tk.onFeedback = { onKeyFeedback() }
        box.addView(tk, FrameLayout.LayoutParams(-1, -1))
        ticketsPanel = tk
        val wd = ListPanel(this)
        wd.visibility = View.GONE
        wd.onBack = { showMode(0) }
        wd.onFeedback = { onKeyFeedback() }
        box.addView(wd, FrameLayout.LayoutParams(-1, -1))
        workdayPanel = wd
        val mt = ListPanel(this)
        mt.visibility = View.GONE
        mt.onBack = { showMode(0) }
        mt.onFeedback = { onKeyFeedback() }
        box.addView(mt, FrameLayout.LayoutParams(-1, -1))
        materialsPanel = mt
        // Pendientes (15) y checklist antes de salir (16)
        val td = ListPanel(this)
        td.visibility = View.GONE
        td.onBack = { showMode(0) }
        td.onFeedback = { onKeyFeedback() }
        box.addView(td, FrameLayout.LayoutParams(-1, -1))
        todoPanel = td
        val ck = ListPanel(this)
        ck.visibility = View.GONE
        ck.onBack = { showMode(0) }
        ck.onFeedback = { onKeyFeedback() }
        box.addView(ck, FrameLayout.LayoutParams(-1, -1))
        checkPanel = ck
        val drv = DrivePanel(this)
        drv.visibility = View.GONE
        drv.actions = object : DrivePanel.Actions {
            override fun onDriveVoice() = showMode(3)
            override fun onDriveStatus() = startStatus()
            override fun onDriveWorkday(kind: String) {
                WorkdayCenter.record(this@BestKeyboardService, kind) { e -> WorkdayCenter.toastFor(this@BestKeyboardService, e); maybeAskSaveSite() }
                showMode(0)
            }
            override fun onDriveKeyboard() {
                driveSkipEvent = WorkDay.day(prefs).lastOrNull()?.time ?: -1L
                setDriveSticky(false)
                showMode(0)
            }
            override fun onDriveNotDriving() {
                setDriving(false)
                setDriveSticky(false)
                showMode(0)
            }
            override fun onDriveFeedback() = onKeyFeedback()
            override fun onDriveDelete() = onDeletePress()
            override fun onDriveClearAll() {
                // 🗑️ Borra todo lo escrito (se puede recuperar con ↩️ Deshacer)
                val ic = currentInputConnection ?: return
                snapUndo()
                val b = ic.getTextBeforeCursor(20000, 0)?.length ?: 0
                val a = ic.getTextAfterCursor(20000, 0)?.length ?: 0
                ic.beginBatchEdit()
                ic.finishComposingText()
                ic.deleteSurroundingText(b, a)
                ic.endBatchEdit()
                kv?.flashUndo()
                lastCorrection = null
            }
            override fun onDriveUndo() = doUndo()
        }
        box.addView(drv, FrameLayout.LayoutParams(-1, -1))
        drivePanel = drv
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
            override fun onRewrite() { onKeyFeedback(); startRewrite() }
            override fun onGrammar() { onKeyFeedback(); startGrammar() }
            override fun onRewriteUse(index: Int) { onKeyFeedback(); useRewrite(index) }
            override fun onSpellLearn() { onKeyFeedback(); spellLearn() }
            override fun onSpellClose() { onKeyFeedback(); stopSpell() }
        }
        sBar = sb
        spellOn = false
        val ib = InfoBar(this)
        ib.visibility = View.GONE
        ib.onClose = { closeInfo() }
        ib.onFeedback = { onKeyFeedback() }
        infoBar = ib
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.addView(ib, LinearLayout.LayoutParams(-1, -2))
        val srch = SearchBar(this)
        srch.visibility = View.GONE
        srch.actions = object : SearchBar.Actions {
            override fun onSearchText(text: String) {
                if (srch.custom == "convert") srch.setInfo(convertResult(text))
                else if (srch.custom == "labels") srch.setInfo(Labels.preview(text))
                else if (srch.custom.isEmpty() && !srch.gifs && !srch.note && !srch.material && !srch.clip) srch.showResults(EmojiSearch.search(text))
                updateAutoCaps()
                scheduleUpdate()
            }
            override fun onSearchClose() {
                val gif = srch.gifs
                val note = srch.note
                val mat = srch.material
                val clip = srch.clip
                val custom = srch.custom
                clipEditing = null
                stopSearch()
                showMode(when (custom) { "todo" -> 15; "check" -> 16; "convert", "labels", "remind", "google" -> 0; else -> if (clip) 2 else if (mat) 13 else if (note) 10 else if (gif) 9 else 1 })
            }
            override fun onSearchPick(emoji: String) {
                currentInputConnection?.commitText(emoji, 1)
                EmojiData.addRecent(emoji)
            }
            override fun onSearchFeedback() = onKeyFeedback()
            override fun onSearchSave() = saveClipEdit()
        }
        searchBar = srch
        searching = 0
        root.addView(srch, LinearLayout.LayoutParams(-1, -2))
        root.addView(sb, LinearLayout.LayoutParams(-1, -2))
        root.addView(tb, LinearLayout.LayoutParams(-1, -2))
        val cb = CodeBar(this)
        cb.visibility = View.GONE
        cb.actions = codeActions
        codeBar = cb
        root.addView(cb, LinearLayout.LayoutParams(-1, -2))
        root.addView(box, LinearLayout.LayoutParams(-1, -2))

        mode = 0
        L.onChange {
            // Todo en vivo: la barra de información y el panel abierto se vuelven a pintar en el otro idioma
            if (infoBar?.visibility == View.VISIBLE) infoRegen?.let { r -> handler.post { r() } }
            if (mode in 10..16) handler.post { showMode(mode) }
            searchBar?.let { if (it.visibility == View.VISIBLE) it.setText(it.text()) }
            clipPanel?.refresh()
            tBar?.applyLang()
            if (shot != null || shotAsk) checkScreenshot()
            kv?.invalidate()
        }
        // Todo va dentro de un marco: normal abajo, o como ventanita flotante
        val handle = makeFloatHandle()
        handle.visibility = View.GONE
        root.addView(handle, 0, LinearLayout.LayoutParams(-1, -2))
        floatHandle = handle
        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.BOTTOM))
        frame.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (floating) clampFloat(false) }
        floatFrame = frame
        floatRoot = root
        applySettings()
        rebuild()
        return frame
    }

    /** Cambia entre teclas, emojis y portapapeles (los paneles usan la misma altura del teclado). */
    private fun showMode(want: Int) {
        // 🛻 Modo manejando fijo: se queda hasta que toques «⌨️ Teclado», la flecha ← o su botón otra vez
        val m = if (want == 0 && !isPassword && driveSticky()) 14 else want
        if (m == 14) setDriveSticky(true)
        mode = m
        val v = kv ?: return
        if (m != 0) { v.reset(); clearSlots() }
        if (m != 0 && searching != 0) stopSearch()
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
                reportMode = false
                reportBuf.setLength(0)
                vp.setReport(false, false)
                startVoice()
            } else if (listening || recognizer != null) {
                reportMode = false
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
        phrasePanel?.let { pp ->
            pp.visibility = if (m == 7) View.VISIBLE else View.GONE
            if (m == 7) pp.open()
        }
        calcPanel?.let { cp ->
            cp.visibility = if (m == 8) View.VISIBLE else View.GONE
            if (m == 8) cp.open()
        }
        gifPanel?.let { gp ->
            val was = gp.visibility == View.VISIBLE
            gp.visibility = if (m == 9) View.VISIBLE else View.GONE
            if (m == 9) { gp.open(pendingGifQuery); pendingGifQuery = null } else if (was) gp.close()
        }
        notebookPanel?.let { it.visibility = if (m == 10) View.VISIBLE else View.GONE; if (m == 10) refreshNotebook() }
        ticketsPanel?.let { it.visibility = if (m == 11) View.VISIBLE else View.GONE; if (m == 11) refreshTickets() }
        workdayPanel?.let { it.visibility = if (m == 12) View.VISIBLE else View.GONE; if (m == 12) refreshWorkday() }
        materialsPanel?.let { it.visibility = if (m == 13) View.VISIBLE else View.GONE; if (m == 13) refreshMaterials() }
        todoPanel?.let { it.visibility = if (m == 15) View.VISIBLE else View.GONE; if (m == 15) refreshTodos() }
        checkPanel?.let { it.visibility = if (m == 16) View.VISIBLE else View.GONE; if (m == 16) refreshChecklist() }
        drivePanel?.let { it.visibility = if (m == 14) View.VISIBLE else View.GONE
            if (m == 14) it.open(WorkDay.nextKind(WorkDay.day(prefs))) }
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
                if (quick == null) kv?.setChip(null, L.t("Activar sugerencia de capturas", "Turn on screenshot suggestions"))
            } else clearShot()
            return
        }
        val usedId = prefs.getLong(Prefs.SHOT_USED, -1L)
        val px = (48 * resources.displayMetrics.density).toInt()
        shotIo.execute {
            val s = Screenshots.findNewest(app)
            val thumb = if (s != null && s.id != usedId) Screenshots.thumbnail(app, s, px) else null // puede faltar: se muestra 🖼
            handler.post {
                if (s != null && s.id != usedId) {
                    shot = s
                    shotAsk = false
                    if (quick == null) kv?.setChip(thumb,
                        if (s.isVideo) L.t("▶ Grabación de pantalla", "▶ Screen recording") else L.t("Captura de pantalla", "Screenshot"))
                } else if (!Screenshots.hasVideoPermission(app) && prefs.getInt("shot_video_asks", 0) < 2) {
                    // Ya ve las fotos, pero falta el permiso de videos (para las grabaciones de pantalla)
                    if (!shotAsk) prefs.edit().putInt("shot_video_asks", prefs.getInt("shot_video_asks", 0) + 1).apply()
                    shot = null
                    shotAsk = true
                    if (quick == null) kv?.setChip(null, L.t("Activar sugerencia de grabaciones", "Turn on screen recording suggestions"))
                } else clearShot()
            }
        }
    }

    private fun clearShot() {
        shot = null
        shotAsk = false
        if (quick == null) kv?.setChip(null, null)
    }

    // ---------------------------------------------------------------- Copiado / códigos

    /** ¿Hay algo copiado hace poco o un código de SMS? Lo ofrece arriba del teclado. */
    private fun checkQuick() {
        val copyOn = prefs.getBoolean(Prefs.CLIP_SUGGEST, true)
        val codeOn = prefs.getBoolean(Prefs.OTP_SUGGEST, true)
        if (hackerActive) { clearQuick(false); return }
        // Campo de contraseña: botón "Mis contraseñas" (si guardaste alguna)
        if (isPassword) {
            VaultStore.load(this)
            if (prefs.getBoolean(Prefs.VAULT_SUGGEST, true) && VaultStore.passwords().isNotEmpty()) {
                showQuick(QuickPaste.Item("", L.t("Mis contraseñas", "My passwords"), false, "", System.currentTimeMillis(), picker = true))
                return
            }
        }
        if (!copyOn && !codeOn) { clearQuick(false); return }
        val done = prefs.getString(Prefs.QUICK_DONE, "") ?: ""
        // Lo copiado (se lee aquí, el teclado tiene permiso mientras está abierto)
        var fromClip: QuickPaste.Item? = null
        if (copyOn || codeOn) {
            val clip = try { clipboard?.primaryClip } catch (_: Exception) { null }
            val desc = clip?.description
            val sensitive = desc?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false) == true
            val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
            if (desc != null && !sensitive && !text.isNullOrBlank() && clip.getItemAt(0).uri == null) {
                val ts = desc.timestamp
                // La hora puede venir en dos formatos: se acepta si es reciente en cualquiera
                val age = minOf(kotlin.math.abs(System.currentTimeMillis() - ts), kotlin.math.abs(SystemClock.elapsedRealtime() - ts))
                val sig = "clip:$ts:${text.hashCode()}"
                val code0 = QuickPaste.asCode(text)
                // Lo copiado se queda hasta que lo pegues o toques ✕ (los códigos sí vencen a los 3 minutos)
                if ((code0 == null || age < QuickPaste.MAX_AGE_MS) && sig != done) {
                    val code = code0
                    if (code != null && codeOn) {
                        fromClip = QuickPaste.Item(code, L.t("Código: ", "Code: ") + code, true, sig, System.currentTimeMillis() - age)
                    } else if (copyOn && !isPassword) {
                        fromClip = QuickPaste.Item(text, L.t("Pegar: ", "Paste: ") + QuickPaste.preview(text), false, sig, System.currentTimeMillis() - age)
                    }
                }
            }
        }
        if (!codeOn || !QuickPaste.canReadSms(this)) { showQuick(fromClip); return }
        val app = applicationContext
        shotIo.execute {
            val sms = QuickPaste.recentSmsCode(app)?.takeIf { it.sig != done }
            handler.post {
                // El más nuevo de los dos
                val best = listOfNotNull(fromClip, sms).maxByOrNull { it.time }
                showQuick(best)
            }
        }
    }

    private fun showQuick(item: QuickPaste.Item?) {
        quick = item
        if (item == null) {
            kv?.setChip(null, null)
            checkScreenshot()
            return
        }
        kv?.setChip(null, item.label, if (item.isCode || item.picker || item.save) Icons.LOCK else Icons.CLIP)
    }

    /** Quita la sugerencia rápida; con [forget] no se vuelve a ofrecer (ya la usaste o seguiste escribiendo). */
    private fun clearQuick(forget: Boolean) {
        val q = quick ?: return
        if (forget && q.sig.isNotEmpty()) prefs.edit().putString(Prefs.QUICK_DONE, q.sig).apply()
        quick = null
        kv?.setChip(null, null)
    }

    /** Al salir de un campo de correo, guarda el correo que escribiste (para sugerirlo después). */
    private fun learnTypedEmail() {
        if (commaMode != "@" || isPassword || !prefs.getBoolean(Prefs.VAULT_LEARN, true)) return
        val ic = currentInputConnection ?: return
        val t = try { ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString()?.trim() } catch (_: Exception) { null } ?: return
        if (t.length in 6..80 && t.matches(Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}"))) {
            try { VaultStore.learnEmail(this, t) } catch (_: Exception) { }
        }
    }

    /** Seleccionaste un texto: arriba sale «Guardar en Datos». */
    private val selChipCheck = Runnable {
        if (selStart == selEnd || spellOn || codeOn || hackerActive || translating || mode != 0) return@Runnable
        val sel = try { currentInputConnection?.getSelectedText(0)?.toString()?.trim() } catch (_: Exception) { null }
        if (sel.isNullOrEmpty() || sel.length > 300) return@Runnable
        showQuick(QuickPaste.Item(sel, L.t("Guardar en Datos", "Save to Data"), false, "", System.currentTimeMillis(), save = true))
    }

    /** Tocaste la sugerencia: se escribe lo copiado o el código. */
    private fun useQuick() {
        val q = quick ?: return
        if (q.save) {
            // Abre "Datos" con lo seleccionado listo para guardar
            clearQuick(false)
            try {
                startActivity(Intent(this, DataActivity::class.java)
                    .putExtra(DataActivity.EXTRA_TEXT, q.text)
                    .putExtra(DataActivity.EXTRA_PASSWORD, isPassword)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            } catch (_: Exception) { }
            return
        }
        if (q.picker) {
            // Ventanita para elegir la contraseña (pide huella o PIN antes de escribirla)
            try {
                startActivity(Intent(this, VaultPickActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
            } catch (_: Exception) { }
            return
        }
        clearQuick(true)
        val ic = target() ?: return
        ic.commitText(q.text, 1)
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        updateAutoCaps()
        scheduleUpdate()
        if (q.isCode) Toast.makeText(this, Emo.strip(L.t("Código pegado", "Code pasted")), Toast.LENGTH_SHORT).show()
    }

    /** Tocaste "Captura de pantalla": se guarda en el portapapeles y se pega aquí. */
    /** ✕ en la sugerencia: ya no se ofrece (lo copiado sigue en tu portapapeles 📋). */
    private fun dismissChip() {
        if (quick != null) {
            val wasSave = quick?.save == true
            clearQuick(forget = !wasSave)
            checkScreenshot() // si había una captura esperando, ahora sale ella
            return
        }
        val s = shot
        if (s != null) prefs.edit().putLong(Prefs.SHOT_USED, s.id).apply()
        else if (shotAsk) prefs.edit().putInt(Prefs.SHOT_ASKS, 3).putInt("shot_video_asks", 2).apply()
        clearShot()
    }

    private fun useScreenshot() {
        if (shotAsk) {
            clearShot()
            askPermission(MainActivity.EXTRA_ASK_PHOTOS)
            return
        }
        val s = shot ?: return
        prefs.edit().putLong(Prefs.SHOT_USED, s.id).apply()
        clearShot()
        if (s.isVideo) { pasteUri(s.uri, s.mime); return }
        ClipStore.addImage(this, s.uri, s.mime) { item ->
            if (item != null) pasteImage(item)
            else Toast.makeText(this, Emo.strip(L.t("No se pudo abrir la captura.", "Couldn't open the screenshot.")), Toast.LENGTH_SHORT).show()
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
            if (!t.isNullOrBlank()) voicePanel?.setPartial(if (reportMode) (reportBuf.toString() + " " + t).trim() else t)
        }

        override fun onResults(results: android.os.Bundle?) {
            val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (reportMode) {
                // Modo reporte: se junta todo lo que dices y al final la IA lo escribe formal
                if (!t.isNullOrBlank()) reportBuf.append(' ').append(t.trim())
                voicePanel?.setPartial(reportBuf.toString().trim())
                voicePanel?.setReport(true, reportBuf.isNotBlank())
            } else {
                if (!t.isNullOrBlank()) commitVoice(t)
                voicePanel?.setPartial("")
            }
            // Sigue escuchando hasta que toques el micrófono o salgas
            if (listening && mode == 3) handler.post { if (listening && mode == 3) startVoice() }
        }
    }

    private var reportMode = false
    private val reportBuf = StringBuilder()

    /** Terminaste de dictar el reporte: la IA lo convierte en ticket formal. */
    private fun finishReport() {
        val notes = reportBuf.toString().trim()
        reportMode = false
        reportBuf.setLength(0)
        listening = false
        recognizer?.cancel()
        showMode(0)
        if (notes.isEmpty()) return
        aiSource = notes
        aiBase = -1
        aiLen = -1
        runTicket(ticketLang())
    }

    /** Escribe lo dictado con signos, espacio y mayúscula donde corresponde. */
    private fun commitVoice(raw: String) {
        val t = VoiceText.process(raw, voiceLang)
        if (t.isEmpty()) return
        if (voiceEnglish) {
            // Hablaste en español: se escribe en inglés. Con la clave de Gemini sale en inglés técnico y profesional
            val key = prefs.getString(Prefs.GEMINI_KEY, "")?.trim().orEmpty()
            if (true) { // con o sin clave (sin clave usa la IA gratis)
                voicePanel?.setStatus(L.t("Pasando a inglés…", "Translating to English…"))
                CloudText.ask("Translate the following Spanish into clear, natural, professional English, as a field technician " +
                    "would write it in a work message (use correct technical terms). Keep the meaning, do not add anything. " +
                    "Answer ONLY with the English text, no quotes, no notes.\n\nText:\n" + t, key) { list, _ ->
                    val out = list?.firstOrNull()?.trim()?.trim('"', '“', '”')
                    handler.post {
                        voicePanel?.setStatus(L.t("Habla en español: se escribe en inglés.", "Speak Spanish: it's typed in English."))
                        if (!out.isNullOrEmpty()) commitVoiceText(out) else mlTranslateVoice(t)
                    }
                }
                return
            }
            mlTranslateVoice(t)
            return
        }
        commitVoiceText(t)
    }

    /** Traducción dentro del teléfono (sin clave). */
    private fun mlTranslateVoice(t: String) {
        val tr = voiceTrans ?: Translation("es", "en").also { voiceTrans = it }
        val go = { tr.translate(t, { out -> handler.post { commitVoiceText(out) } }, { handler.post { commitVoiceText(t) } }) }
        if (tr.ready) go() else tr.prepare({ handler.post { go() } }, { handler.post {
            voicePanel?.setStatus(L.t("No se pudo traducir (la primera vez necesita internet).", "Couldn't translate (the first time needs internet)."))
            commitVoiceText(t)
        } })
    }

    private var voiceEnglish = false
    private var voiceTrans: Translation? = null

    private fun commitVoiceText(text: String) {
        val ic = target() ?: return
        var t = text.trim()
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
    private fun startSpell(grammarNow: Boolean = false) {
        if (isPassword || !fieldSuggest) {
            Toast.makeText(this, Emo.strip(L.t("Aquí no se puede revisar la ortografía.", "Spelling can't be checked here.")), Toast.LENGTH_SHORT).show()
            return
        }
        if (translating) stopTranslate()
        if (mode != 0) showMode(0)
        val ic = currentInputConnection ?: return
        val eng = WordStore.engine ?: run {
            Toast.makeText(this, Emo.strip(L.t("Un momento, el diccionario se está cargando…", "One moment, the dictionary is loading…")), Toast.LENGTH_SHORT).show()
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
        if (!selection && text.isNotBlank()) {
            // Sin selección: se selecciona TODO el texto y se revisa completo
            try { ic.setSelection(base, base + text.length) } catch (_: Exception) { }
        }
        val explicit = selection
        selection = true
        val bar = sBar ?: return
        spellOn = true
        spellErrors = ArrayList()
        spellText = text
        spellBase = base
        spellSelection = selection
        rewrites = emptyList()
        bar.rewriteOn = selection
        bar.visibility = View.VISIBLE
        bar.resetButtons()
        bar.message(L.t("Revisando tu texto…", "Checking your text…"))
        kv?.spellOn = true
        if (grammarNow) {
            // ✅ El botón va directo a «Corregir gramática» (es lo más preciso)
            if (text.isBlank()) { bar.message(L.t("Escribe algo primero.", "Type something first.")); handler.postDelayed({ if (spellOn) stopSpell() }, 1500); return }
            ++spellToken
            startGrammar()
            return
        }
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
                val r = try { eng.spellCheck(m.value, lng, 5, strict = true, ignoreUser = explicit) } catch (_: Exception) { null }
                if (r != null && r.misspelled) errs.add(SpellErr(base + m.range.first, base + m.range.last + 1, m.value, r.suggestions, r.maybe))
                k++
            }
            handler.post {
                if (!spellOn || token != spellToken) return@post
                spellErrors = errs
                if (errs.isEmpty() && selection) {
                    // Sin errores, pero se queda abierto para poder "Reescribir formal"
                    bar.message(L.t("✅ Sin errores de ortografía.", "✅ No spelling mistakes."))
                } else if (errs.isEmpty()) {
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
        if (spellErrors.isEmpty() && spellSelection) {
            // Revisaste lo seleccionado: se queda la opción de "Reescribir formal"
            val end = spellBase + spellText.length
            currentInputConnection?.setSelection(spellBase, end)
            bar.message(L.t("✅ Ortografía lista.", "✅ Spelling done."))
            return
        }
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
        WordStore.engine?.let { if (learnOn) { it.user.add(word, 1, lang); WordStore.scheduleSave() } }
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
        spellToken++
        kv?.spellOn = false
        sBar?.visibility = View.GONE
        // Quitar la marca: el cursor queda al final de la palabra marcada
        if (!keepSelection && selStart != selEnd) currentInputConnection?.setSelection(selEnd, selEnd)
        spellErrors = ArrayList()
    }

    // ---------------------------------------------------------------- Traducción

    private fun startTranslate() {
        if (isPassword) {
            Toast.makeText(this, Emo.strip(L.t("La traducción no funciona en campos de contraseña.", "Translation doesn't work in password fields.")), Toast.LENGTH_SHORT).show()
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
        if (text.length > 12) snapUndo() // pegar algo del portapapeles se puede deshacer
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
        val uri = ClipStore.uriOf(item) ?: return
        pasteUri(uri, item.mime ?: "image/png")
    }

    private fun pasteUri(uri: android.net.Uri, mime: String) {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        if (Build.VERSION.SDK_INT >= 25 && ei != null) {
            val accepted = ei.contentMimeTypes ?: emptyArray()
            if (accepted.any { ClipDescription.compareMimeTypes(mime, it) }) {
                val info = InputContentInfo(uri, ClipDescription(if (mime.startsWith("video")) "Video" else "Imagen", arrayOf(mime)), null)
                val ok = try {
                    ic.commitContent(info, InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                } catch (_: Exception) { false }
                if (ok) return
            }
        }
        try {
            val video = mime.startsWith("video")
            clipboard?.setPrimaryClip(ClipData.newUri(contentResolver, if (video) "Video" else "Imagen", uri))
            Toast.makeText(
                this,
                if (video) L.t("Esta app no recibe videos desde el teclado. Ya está copiado: mantén presionado donde escribes y toca Pegar (o compártelo desde la galería).",
                    "This app doesn't accept videos from the keyboard. It's copied: long-press where you type and tap Paste (or share it from the gallery).") else L.t("Esta app no recibe imágenes desde el teclado. Ya está copiada: mantén presionado donde escribes y toca Pegar.", "This app doesn't accept images from the keyboard. It's copied: long-press where you type and tap Paste."),
                Toast.LENGTH_LONG,
            ).show()
        } catch (_: Exception) { }
    }

    // Sin pantalla completa en horizontal: el teclado se ve igual que en vertical
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) { undoSnap = null; kv?.playIntro() } // ⌨️ la luz de bienvenida
        if (driveSticky() && !isPassword && mode != 14) handler.post { if (driveSticky()) showMode(14) } // 🛻 sigue en modo manejando
        applySettings()
        if (BgMedia.kind(this) == "video") bgVideoView?.play()
        // Android vuelve a poner el teclado abajo al abrirse: si es flotante, se acomoda otra vez
        if (prefs.getBoolean(Prefs.FLOAT, false)) handler.post { applyFloat() }
        if (!isPassword) maybeShowWord()
        if (!isPassword) handler.postDelayed({ if (isInputViewShown && mode == 0) { maybeDriving(); maybeAskSaveSite() } }, 350)

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
        // Algunas apps (como Instagram) piden "sin sugerencias" en su cuadro de mensajes:
        // por defecto el teclado las muestra igual (se puede cambiar en los ajustes)
        val noSuggestions = (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0 &&
            !prefs.getBoolean(Prefs.SUGGEST_ALL_APPS, true)
        fieldSuggest = cls == InputType.TYPE_CLASS_TEXT && !isPassword && commaMode == "," && !noSuggestions
        prefs.edit().putString(Prefs.LAST_FIELD,
            "${info.packageName} · 0x${Integer.toHexString(type)} · 0x${Integer.toHexString(info.imeOptions)}").apply()
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
        VaultStore.load(this)
        VaultStore.pendingInsert?.let { p ->
            // Volviste de elegir una contraseña: se escribe aquí
            VaultStore.pendingInsert = null
            currentInputConnection?.commitText(p, 1)
        }
        updateAutoCaps()
        scheduleUpdate()
        checkScreenshot()
        checkQuick()
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
        bgVideoView?.pause()
        learnTypedEmail()
        codeBar?.let { if (it.asking) it.hideAsk() }
        if (quick != null) clearQuick(false)
        if (shot != null || shotAsk) clearShot()
        if (mode != 0) showMode(0)
        closeInfo()
        stopSearch()
        if (translating) stopTranslate()
        if (spellOn) stopSpell(keepSelection = true)
        kv?.reset()
        clearSlots()
        WordStore.saveNow()
    }

    private fun withCustomColors(p: Palette) = Palette.custom(p, prefs)

    /** Ajustes que se guardan solos mientras escribes (no son cambios de personalización). */
    private val quietPrefs = setOf(
        Prefs.WORKDAY, Prefs.SITES, Prefs.MATERIALS, Prefs.MATERIALS_TODAY,
        Prefs.NOTEBOOK, Prefs.TICKETS, Prefs.TICKET_NEXT, Prefs.WORD_SHOWN,
        Prefs.CLIP_LAST_SIG, Prefs.QUICK_DONE, Prefs.SHOT_USED, Prefs.SHOT_ASKS, Prefs.LANG, Prefs.TR_SRC,
        Prefs.BAR_TOOLS, Prefs.MORE_TOOLS, Prefs.BAR_TOOLS2, Prefs.MORE_TOOLS2, Prefs.SIZE, Prefs.LAST_FIELD,
        Prefs.FLOAT_X, Prefs.FLOAT_Y,
    )
    private val applyLive = Runnable {
        if (kv == null) return@Runnable
        applySettings()
        rebuild()
        scheduleUpdate()
    }
    /** Cambias algo en los ajustes con el teclado abierto: se aplica al instante (tema, colores, forma…). */
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in quietPrefs) return@OnSharedPreferenceChangeListener
        handler.removeCallbacks(applyLive)
        handler.postDelayed(applyLive, 60)
    }

    override fun onDestroy() {
        bgVideoView?.release()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        clipboard?.removePrimaryClipChangedListener(clipListener)
        try { contentResolver.unregisterContentObserver(mediaObserver) } catch (_: Exception) { }
        try { contentResolver.unregisterContentObserver(smsObserver) } catch (_: Exception) { }
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
            handler.removeCallbacks(selChipCheck)
            handler.postDelayed(selChipCheck, 250)
        } else {
            if (quick?.save == true) clearQuick(false)
            scheduleUpdate()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySettings()
    }

    // ---------------------------------------------------------------- Ajustes

    /** 🖼️ Tu imagen o video de fondo (o ninguno). */
    private fun applyBackground(v: KeyboardView) {
        val kind = BgMedia.kind(this)
        v.bgDim = BgMedia.dim(this)
        val sound = prefs.getBoolean(Prefs.BG_SOUND, false)
        v.bgSoundOn = kind == "video" && sound
        val stamp = kind + ":" + prefs.getLong(Prefs.BG_STAMP, 0L)
        val bgv = bgVideoView
        when (kind) {
            "image" -> {
                bgImageView?.let {
                    if (bgLoaded != stamp || it.drawable == null) {
                        val side = maxOf(resources.displayMetrics.widthPixels, 720)
                        it.setImageBitmap(BgMedia.loadImage(this, side))
                    }
                    it.visibility = View.VISIBLE
                }
                v.bgImage = null
                v.bgVideo = true
                bgv?.release(); bgv?.visibility = View.GONE
            }
            "video" -> {
                v.bgImage = null
                v.bgVideo = true
                bgImageView?.let { it.setImageDrawable(null); it.visibility = View.GONE }
                bgv?.let {
                    it.visibility = View.VISIBLE
                    it.sound = sound
                    if (bgLoaded != stamp) it.reload()
                    if (isInputViewShown) it.play()
                }
            }
            else -> {
                v.bgImage = null
                v.bgVideo = false
                bgv?.release(); bgv?.visibility = View.GONE
                bgImageView?.let { it.setImageDrawable(null); it.visibility = View.GONE }
            }
        }
        bgLoaded = stamp
    }

    private fun applySettings() {
        val v = kv ?: return
        val theme = Prefs.theme(prefs)
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        var pal = Palette.forTheme(theme, systemDark, prefs.getString(Prefs.HACKER_COLOR, ""))
        // Color de acento elegido (si no, el del tema)
        pal = withCustomColors(pal)
        PanelUi.glowAccent = pal.accent
        PanelUi.glass = BgMedia.kind(this).isNotEmpty()
        PanelUi.pal = pal
        PanelUi.loadStyle(prefs)
        PanelUi.density = resources.displayMetrics.density
        v.palette = pal
        applyBackground(v)
        v.keyShape = prefs.getString(Prefs.KEY_SHAPE, "normal") ?: "normal"
        v.labelScale = when (prefs.getString(Prefs.LABEL_SIZE, "normal")) { "small" -> 0.86f; "big" -> 1.16f; else -> 1f } *
            (if (prefs.getBoolean(Prefs.GLOVES, false)) 1.2f else 1f)
        v.keyAnim = prefs.getString(Prefs.KEY_ANIM, "pop") ?: "pop"
        v.stripArc = when (prefs.getString(Prefs.STRIP_ARC, "normal")) { "none" -> 0f; "soft" -> 0.45f; "strong" -> 1.8f; else -> 1f }
        v.stripNames = prefs.getBoolean(Prefs.STRIP_NAMES, true)
        v.stripLight = when (prefs.getString(Prefs.STRIP_LIGHT, "normal")) { "off" -> 0f; "soft" -> 0.55f; "bright" -> 1.5f; else -> 1f }
        vibStyle = "system" // la única que funciona en todos los teléfonos (las otras quedan de respaldo)
        vibStrength = prefs.getString(Prefs.VIB_STRENGTH, "medium")
        soundStyle = prefs.getString(Prefs.SOUND_STYLE, "modern")
        soundVol = prefs.getString(Prefs.SOUND_VOL, "medium")
        emojiPanel?.applyPalette(pal)
        clipHistoryOn = prefs.getBoolean(Prefs.CLIP_HISTORY, true)
        clipPanel?.let { it.historyOn = clipHistoryOn; it.applyPalette(pal) }
        voicePanel?.applyPalette(pal)
        tBar?.applyPalette(pal)
        sBar?.applyPalette(pal)
        codeBar?.applyPalette(pal)
        codeOn = prefs.getBoolean(Prefs.CODE_MODE, false)
        codeLang = prefs.getString(Prefs.CODE_LANG, "Python") ?: "Python"
        v.codeOn = codeOn
        codeBar?.let { it.visibility = if (codeOn) View.VISIBLE else View.GONE; it.setLang(codeLang) }
        scanPanel?.applyPalette(pal)
        editPanel?.applyPalette(pal)
        morePanel?.applyPalette(pal)
        phrasePanel?.applyPalette(pal)
        calcPanel?.applyPalette(pal)
        gifPanel?.applyPalette(pal)
        notebookPanel?.applyPalette(pal)
        ticketsPanel?.applyPalette(pal)
        workdayPanel?.applyPalette(pal)
        materialsPanel?.applyPalette(pal)
        todoPanel?.applyPalette(pal)
        checkPanel?.applyPalette(pal)
        drivePanel?.applyPalette(pal)
        infoBar?.applyPalette(pal)
        searchBar?.applyPalette(pal)
        // Con fondo propio, los paneles (emojis, portapapeles, GIFs…) dejan ver tu imagen o video
        val custom = BgMedia.kind(this).isNotEmpty()
        val panelBg = if (custom) (((BgMedia.dim(this) * 255).toInt().coerceIn(0, 255)) shl 24) else pal.bg
        panelBox?.let { b ->
            for (i in 0 until b.childCount) {
                val c = b.getChildAt(i)
                if (c === kv || c === bgVideoView || c === bgImageView || c === scanPanel) continue
                c.setBackgroundColor(panelBg)
            }
        }
        if (floatFrame != null && prefs.getBoolean(Prefs.FLOAT, false) != floating) applyFloat()
        else if (floating) floatRoot?.background?.let { (it as? android.graphics.drawable.GradientDrawable)?.setColor(pal.bg) }
        WorkDay.config(prefs)
        v.oneHand = when (prefs.getString(Prefs.ONE_HAND, "off")) { "left" -> 1; "right" -> 2; else -> 0 }
        val (barTools, moreTools) = Tools.load(this, prefs)
        v.setBarTools(barTools, moreTools)
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
        val hk = if (hacker && !numLike) 0.86f else 1f
        // 🧤 Modo guantes: teclas más altas y letras más grandes
        val gloves = prefs.getBoolean(Prefs.GLOVES, false)
        v.glovesOn = gloves
        v.keyHeightDp = (if (landscape) base * 0.78f else base) * hk * (if (gloves) 1.28f else 1f)
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
        if (codeOn && page == Page.LETTERS) {
            v.setRows(CodeLayouts.letters())
            v.shift = shift
            v.swipeEnabled = false
            return
        }
        val rows = when (page) {
            Page.LETTERS -> Layouts.letters(numberRow, commaMode, english = lang == "en")
            Page.SYM -> Layouts.symbols()
            Page.SYM2 -> Layouts.symbols2()
            Page.NUM -> Layouts.numpad(phone)
            Page.IP -> Layouts.ipPad()
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
        if (codeOn && !codeAsking) { if (shift == 1 && !manualShift) setShift(0); return }
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
        fixSpan = null
        vaultActive = false
        codeActive = false
        slotWords.fill(null)
        slotTyped = -1
        emojiSlot = -1
        formalSlot = -1
        kv?.clearSuggestions()
    }

    /** Lo escrito desde el último espacio (para comparar con "Mis datos"). */
    private fun vaultToken(): String? {
        val ic = target() ?: return null
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return null
        val i = before.indexOfLast { it.isWhitespace() }
        return before.substring(i + 1)
    }

    /** Sugiere tus correos y atajos guardados. Devuelve true si los mostró. */
    private fun vaultSuggest(): Boolean {
        if (isPassword || hackerActive || translating || numLike || !prefs.getBoolean(Prefs.VAULT_SUGGEST, true)) return false
        if (VaultStore.items.isEmpty() || codeOn) return false
        val token = vaultToken() ?: return false
        val m = VaultStore.match(token, commaMode == "@")
        if (m.isEmpty()) return false
        val order = listOf(1, 0, 2) // el primero en el centro
        val labels = arrayOfNulls<String>(3)
        val values = arrayOfNulls<String>(3)
        for ((i, e) in m.withIndex()) {
            val slot = order[i]
            labels[slot] = QuickPaste.preview(e.value)
            values[slot] = e.value
        }
        showSlots(labels[0], labels[1], labels[2], true, false)
        for (i in 0..2) vaultValues[i] = values[i]
        vaultActive = true
        return true
    }

    private val CONTRACTIONS = mapOf(
        "ill" to "I'll", "id" to "I'd", "im" to "I'm", "ive" to "I've", "hell" to "he'll", "shell" to "she'll",
        "wed" to "we'd", "well" to "we'll", "were" to "we're", "its" to "it's", "lets" to "let's", "wont" to "won't",
        "cant" to "can't", "hed" to "he'd", "shed" to "she'd", "youre" to "you're", "theyre" to "they're", "whos" to "who's",
    )

    /** Si estás corrigiendo una palabra tocada en medio: (letras antes del cursor, letras después). */
    private var fixSpan: Pair<Int, Int>? = null

    private fun showSlots(left: String?, center: String?, right: String?, bold: Boolean, typedLeft: Boolean) {
        emojiSlot = -1
        formalSlot = -1
        slotsBold = bold
        vaultActive = false
        codeActive = false
        slotWords[0] = left
        slotWords[1] = center
        slotWords[2] = right
        slotTyped = if (typedLeft) 0 else -1
        kv?.setSuggestions(left, center, right, bold, typedLeft)
    }

    /** El idioma para sugerir y corregir: el de la frase que escribes (si se nota), si no el del teclado. */
    private fun sugLang(eng: com.luisell.bestkeyboard.words.WordEngine, before: CharSequence, curLen: Int): String {
        if (!prefs.getBoolean(Prefs.AUTO_LANG, true)) return lang
        return try { eng.contextLang(before, curLen) ?: lang } catch (_: Exception) { lang }
    }

    private fun canCorrect(word: String, before: CharSequence): Boolean {
        if (!autocorrectOn || !fieldCorrect || codeOn) return false
        // Un nombre propio en medio de la frase (ej. "García") no se corrige
        if (word.length > 1 && word[0].isUpperCase() && !TextUtil.isSentenceStart(before, word.length)) return false
        return true
    }

    private fun updateSuggestions() {
        updateWordSuggestions()
        // Se lee el texto una sola vez para las dos sugerencias (más rápido al escribir)
        sugBefore = try { target()?.getTextBeforeCursor(80, 0) } catch (_: Exception) { null }
        addFormalSuggestion()
        addEmojiSuggestion()
        sugBefore = null
    }

    /** Si escribiste algo informal ("arreglé", "ya sirve"), sugiere la forma formal con ✨. */
    private fun addFormalSuggestion() {
        formalSlot = -1
        if (vaultActive || codeActive || codeOn || searching != 0 || isPassword || hackerActive || numLike) return
        if (!suggestOn || !fieldSuggest || !prefs.getBoolean(Prefs.FORMAL_SUGGEST, true)) return
        val ic = target() ?: return
        val before = sugBefore ?: ic.getTextBeforeCursor(80, 0) ?: return
        val m = FormalWords.match(before)
            ?: (if (prefs.getBoolean(Prefs.WORD_WEEK, true)) WeeklyWord.match(before) else null)
            ?: return
        val slot = when {
            slotWords.all { it == null } -> 1
            slotTyped != 0 -> 0
            else -> 2
        }
        slotWords[slot] = "✨ " + m.replacement.trim()
        if (slotTyped == slot) slotTyped = -1
        formalSlot = slot
        formalMatch = m
        kv?.setSuggestions(slotWords[0], slotWords[1], slotWords[2], slotsBold && slot != 1, slotTyped == 0)
    }

    private fun useFormalSuggestion() {
        val m = formalMatch ?: return
        val ic = target() ?: return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(m.deleteLen, 0)
        ic.commitText(if (m.replacement.endsWith(" ")) m.replacement else m.replacement + " ", 1)
        ic.endBatchEdit()
        gestureWord = null
        phantomSpace = false
        lastCorrection = null
        autoSpace = true
        lastWasSpace = true
        clearSlots()
        updateAutoCaps()
        scheduleUpdate()
    }

    /** Pone en la barra un emoji que va con lo que escribes (casa → 🏠, jaja → 😂). */
    private fun addEmojiSuggestion() {
        if (vaultActive || codeActive || codeOn || searching != 0 || isPassword || hackerActive || numLike) return
        if (!suggestOn || !fieldSuggest || !prefs.getBoolean(Prefs.EMOJI_SUGGEST, true)) return
        val ic = target() ?: return
        val before = sugBefore ?: ic.getTextBeforeCursor(60, 0) ?: return
        if (before.isEmpty()) return
        // Solo justo después de la palabra o de un espacio (no después de un punto o un emoji)
        val last = before.last()
        if (!(TextUtil.isWordChar(last) || last == ' ')) return
        if (last == ' ' && before.length > 1 && !TextUtil.isWordChar(before[before.length - 2])) return
        val emoji = EmojiSearch.suggest(before) ?: return
        val slot = when {
            slotWords.all { it == null } -> 1
            else -> listOf(2, 0).firstOrNull { it != formalSlot && it != slotTyped } ?: return
        }
        slotWords[slot] = emoji
        if (slotTyped == slot) slotTyped = -1
        emojiSlot = slot
        kv?.setSuggestions(slotWords[0], slotWords[1], slotWords[2], slotsBold && slot != 1, slotTyped == 0)
    }

    /** Tocaste el emoji sugerido: va después de la palabra, con su espacio. */
    private fun useEmojiSuggestion(emoji: String) {
        val ic = target() ?: return
        val before = ic.getTextBeforeCursor(48, 0) ?: ""
        val cur = TextUtil.currentWord(before)
        ic.beginBatchEdit()
        if (cur.isNotEmpty()) {
            finishWord(ic)
            ic.commitText(" $emoji ", 1)
        } else {
            ic.commitText("$emoji ", 1)
        }
        ic.endBatchEdit()
        EmojiData.addRecent(emoji)
        gestureWord = null
        phantomSpace = false
        lastCorrection = null
        autoSpace = false
        lastWasSpace = true
        clearSlots()
        updateAutoCaps()
    }

    private fun updateWordSuggestions() {
        if (codeOn) { codeSuggest(); return }
        if (vaultSuggest()) return
        val eng = WordStore.engine
        val ic = target()
        if (!suggestOn || !fieldSuggest || eng == null || ic == null || numLike || hackerActive) {
            clearSlots(); return
        }
        val before = ic.getTextBeforeCursor(48, 0) ?: ""
        val after = ic.getTextAfterCursor(24, 0) ?: ""
        fixSpan = null
        if (after.isNotEmpty() && TextUtil.isWordChar(after[0])) {
            // 🔧 Tocaste en medio de una palabra mal escrita: sus correcciones salen aquí arriba (en tu idioma)
            val head = TextUtil.currentWord(before)
            val tail = after.takeWhile { TextUtil.isWordChar(it) }.toString()
            val whole = head + tail
            kv?.typingWord = true
            val sp = if (whole.length >= 2) try { eng.spellCheck(whole, lang, 3) } catch (_: Exception) { null } else null
            if (sp != null && sp.misspelled && sp.suggestions.isNotEmpty()) {
                val sg = sp.suggestions.map { TextUtil.matchCase(it, whole) }
                showSlots(sg.getOrNull(1), sg.getOrNull(0), sg.getOrNull(2), bold = true, typedLeft = false)
                fixSpan = head.length to tail.length
            } else clearSlots()
            return
        }

        val word = TextUtil.currentWord(before)
        kv?.typingWord = word.isNotEmpty()
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
            val prev2 = TextUtil.previousWords(before, 0).second
            val preds = if (prev != null) eng.predictions(prev, sugLang(eng, before, 0), 3, prev2) else emptyList()
            if (preds.isEmpty()) { clearSlots(); return }
            val capital = shift > 0 && page == Page.LETTERS
            val p = preds.map { if (capital) TextUtil.matchCase(it, "A") else it }
            showSlots(p.getOrNull(1), p.getOrNull(0), p.getOrNull(2), bold = false, typedLeft = false)
            return
        }

        val prev = TextUtil.previousWord(before, word.length)
        val sl = sugLang(eng, before, word.length)
        val res = eng.suggest(word, prev, sl, TextUtil.previousWords(before, word.length).second)
        val corr = if (canCorrect(word, before)) res.correction else null
        if (corr != null && corr != word) {
            val right = res.suggestions.firstOrNull { !it.equals(corr, true) && !it.equals(word, true) }
            showSlots(word, corr, right, bold = true, typedLeft = true)
            return
        }
        val list = res.suggestions.filter { it != word }.toMutableList()
        // 🇺🇸 Contracciones que también son palabras («ill» = enfermo, pero casi siempre quieres «I'll»): se ofrecen primero
        if (sl == "en") CONTRACTIONS[word.lowercase()]?.let { c ->
            val cc = if (c.startsWith("I'")) c else TextUtil.matchCase(c, word)
            list.remove(cc); list.add(0, cc)
        }
        val known = eng.isKnown(word.lowercase())
        if (known || res.suggestions.any { it == word }) {
            // Lo que escribiste es una palabra: va en el centro
            showSlots(list.getOrNull(0), word, list.getOrNull(1), bold = false, typedLeft = false)
        } else {
            // 🔎 No es una palabra: ¿quisiste decir…? (actulizo → actualizo)
            if (list.size < 2) {
                val near = try { eng.nearWords(word, prev, sl, 3) } catch (_: Exception) { emptyList() }
                    .map { TextUtil.matchCase(it, word) }.filter { it != word && it !in list }
                if (near.isNotEmpty()) list.addAll(0, near)
            }
            showSlots(word, list.getOrNull(0), list.getOrNull(1), bold = false, typedLeft = true)
        }
    }

    /** Mantener presionada una sugerencia: preguntar si la borras para que no vuelva a salir. */
    override fun onSuggestionLong(slot: Int) {
        if (codeActive || vaultActive || slot == formalSlot || slot == emojiSlot) return
        val w = slotWords.getOrNull(slot)?.trim() ?: return
        if (w.isEmpty() || w.contains(' ') || !w.any { it.isLetter() }) return
        val eng = WordStore.engine ?: return
        val bar = infoBar ?: return
        infoRegen = null
        bar.show(L.t("Borrar sugerencia", "Delete suggestion"),
            L.t("«$w» no volverá a salir en las sugerencias ni en el autocorrector.",
                "«$w» won't show up again in suggestions or autocorrect."),
            listOf(
                L.t("Borrar «$w»", "Delete «$w»") to {
                    eng.user.block(w)
                    WordStore.scheduleSave()
                    closeInfo()
                    updateSuggestions()
                },
                L.t("Cancelar", "Cancel") to { closeInfo() },
            ))
    }

    override fun onSuggestion(slot: Int) {
        if (codeActive) { useCodeSnippet(slot); return }
        if (slot == formalSlot && !vaultActive) { useFormalSuggestion(); return }
        if (slot == emojiSlot && !vaultActive) {
            slotWords.getOrNull(slot)?.let { useEmojiSuggestion(it) }
            return
        }
        if (vaultActive) {
            // Tocaste uno de "Mis datos": cambia lo escrito por el valor completo
            val v = vaultValues.getOrNull(slot) ?: return
            val ic = target() ?: return
            val token = vaultToken() ?: ""
            ic.beginBatchEdit()
            if (token.isNotEmpty()) ic.deleteSurroundingText(token.length, 0)
            ic.commitText(if (commaMode == "@" || v.contains('@') || v.contains('\n')) v else "$v ", 1)
            ic.endBatchEdit()
            vaultActive = false
            clearSlots()
            lastCorrection = null
            updateAutoCaps()
            return
        }
        val w = slotWords.getOrNull(slot) ?: return
        gestureWord = null
        phantomSpace = false
        val ic = target() ?: return
        fixSpan?.let { (b, a) ->
            // Cambia la palabra entera (la parte de antes y la de después del cursor)
            fixSpan = null
            ic.beginBatchEdit()
            ic.deleteSurroundingText(b, a)
            ic.commitText(w, 1)
            ic.endBatchEdit()
            clearSlots()
            lastCorrection = null
            autoLangPick(w)
            return
        }
        val before = ic.getTextBeforeCursor(48, 0) ?: ""
        val cur = TextUtil.currentWord(before)
        val prev = TextUtil.previousWord(before, cur.length)
        ic.beginBatchEdit()
        if (cur.isNotEmpty()) ic.deleteSurroundingText(cur.length, 0)
        ic.commitText("$w ", 1)
        ic.endBatchEdit()
        val eng = WordStore.engine
        if (eng != null && learnOn && fieldLearn) {
            // Se aprende con el idioma de la palabra (no el del teclado), así no se mezclan
            val wl = eng.langOf(w.lowercase().trim('\'', '’')) ?: lang
            if (slot == slotTyped) eng.user.learn(w) else eng.user.add(w, 1, wl)
            if (prev != null) eng.user.addBigram(prev, w)
            TextUtil.previousWords(before, cur.length).second?.let { p2 -> if (prev != null) eng.user.addTrigram(p2, prev, w) }
            WordStore.scheduleSave()
        }
        lastCorrection = null
        autoSpace = true
        lastWasSpace = true
        if (shift == 1) { manualShift = false; setShift(0) }
        if (page == Page.SYM || page == Page.SYM2) { page = Page.LETTERS; rebuild() }
        autoLangPick(w)
        updateAutoCaps()
    }

    /** 🌐 Las palabras que eliges de las sugerencias también cuentan para cambiar el idioma solo. */
    private fun autoLangPick(w: String) {
        val words = w.split(Regex("\\s+")).filter { it.any(Char::isLetter) }
        for (x in words) autoLang(x.trim { !it.isLetter() && it != '\'' && it != '’' })
    }

    /**
     * Se llama justo antes de escribir un espacio o un signo: corrige la palabra si hace falta
     * y aprende lo que escribiste. Devuelve la corrección hecha (lo escrito, lo corregido) o null.
     */
    private fun finishWord(ic: InputConnection): Pair<String, String>? {
        if (!fieldSuggest || numLike) return null
        val before = ic.getTextBeforeCursor(48, 0) ?: return null
        val word = TextUtil.currentWord(before)
        if (word.isEmpty()) return null
        val eng = WordStore.engine ?: return null
        val prev = TextUtil.previousWord(before, word.length)
        var final = word
        var fixed: Pair<String, String>? = null
        if (canCorrect(word, before)) {
            val corr = eng.correction(word, sugLang(eng, before, word.length))?.let { TextUtil.matchCase(it, word) }
            if (corr != null && corr != word) {
                ic.deleteSurroundingText(word.length, 0)
                ic.commitText(corr, 1)
                final = corr
                fixed = Pair(word, corr)
            }
        }
        if (learnOn && fieldLearn) {
            // Si parece un error de dedo (hay una palabra casi igual), cuesta más aprenderla
            val typo = fixed == null && eng.user.entry(final.lowercase()) == null && eng.looksLikeTypo(final, sugLang(eng, before, word.length))
            eng.user.add(final, 1, lang, typo)
            if (prev != null) eng.user.addBigram(prev, final)
            TextUtil.previousWords(before, word.length).second?.let { p2 -> if (prev != null) eng.user.addTrigram(p2, prev, final) }
            WordStore.scheduleSave()
        }
        autoLang(final)
        return fixed
    }

    // ---------------------------------------------------------------- Teclas

    private var lastDeleteAt = 0L

    override fun onKeyText(text: String) {
        lastDeleteAt = 0L
        if (spellOn) stopSpell()
        if (hackerActive) { hackerText(text); return }
        if (codeOn && !codeAsking) { codeText(text); return }
        // Las sugerencias (pegar, capturas) se quedan hasta que las uses o toques su ✕
        if (quick?.save == true) clearQuick(false)
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
        if (code == Codes.SHIFT && selStart != selEnd && !translating && prefs.getBoolean(Prefs.SEL_CAPS, false)) {
            if (changeSelectionCase()) return
        }
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
                val before = if (doubleSpace && !isPassword && lastWasSpace && !codeOn) ic.getTextBeforeCursor(2, 0) else null
                val sc = if (!isPassword && !codeOn) Shortcuts.match(prefs, ic.getTextBeforeCursor(40, 0)) else null
                if (sc != null) {
                    // ⚡ Atajo: "/ok" + espacio → la frase completa
                    ic.beginBatchEdit()
                    ic.deleteSurroundingText(sc.first.length, 0)
                    ic.commitText(sc.second + " ", 1)
                    ic.endBatchEdit()
                    lastWasSpace = true
                } else if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
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
                if (searching != 0) { searchEnter(); return }
                if (translating) { translateEnter(); return }
                if (codeAsking) { codeActions.onCodeAsk(codeBar?.box?.text?.toString() ?: ""); return }
                if (codeOn) { codeEnter(); return }
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
            Codes.UNDO -> doUndo()
            Codes.MODE_SYM -> { page = Page.SYM; rebuild() }
            Codes.MODE_SYM2 -> { page = Page.SYM2; rebuild() }
            Codes.MODE_LETTERS -> {
                page = Page.LETTERS
                manualShift = false
                rebuild()
                updateAutoCaps()
            }
            Codes.LANG -> setLang(if (lang == "es") "en" else "es")
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
        // ↩️ Al empezar a borrar se guarda cómo estaba (Deshacer lo recupera) y la tecla se ilumina
        val nowDel = SystemClock.uptimeMillis()
        if (nowDel - lastDeleteAt > 1500L) snapUndo()
        lastDeleteAt = nowDel
        kv?.flashUndo()
        if (hackerActive) { sendKey(KeyEvent.KEYCODE_DEL, hMeta()); hConsume(); return }
        if (codeOn && !codeAsking && codeDeleteIndent()) return
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
        if (action != KeyboardView.TOOL_UNDO && action != KeyboardView.TOOL_HIDE && action != KeyboardView.TOOL_MORE) snapUndo()
        when (action) {
            KeyboardView.TOOL_SETTINGS -> {
                val i = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(i)
            }
            KeyboardView.TOOL_THEME, KeyboardView.TOOL_ANIM, KeyboardView.TOOL_LABEL,
            KeyboardView.TOOL_KEYSIZE, KeyboardView.TOOL_SOUND, KeyboardView.TOOL_VIB, KeyboardView.TOOL_SHAPE -> {
                // Desde la barra de arriba: un toque pasa a la siguiente opción
                val name = Cycles.step(prefs, action, 1)
                val title = Tools.info(action)?.label ?: ""
                Toast.makeText(this, Emo.strip("$title: $name"), Toast.LENGTH_SHORT).show()
            }
            KeyboardView.TOOL_HIDE -> hideByUser()
            KeyboardView.TOOL_EMOJI -> showMode(1)
            KeyboardView.TOOL_CLIP -> showMode(2)
            KeyboardView.TOOL_VOICE -> showMode(3)
            KeyboardView.TOOL_EDIT -> showMode(5)
            KeyboardView.TOOL_SHOT -> if (quick != null) useQuick() else useScreenshot()
            KeyboardView.TOOL_CHIP_X -> dismissChip()
            KeyboardView.TOOL_SPELL -> if (spellOn) stopSpell() else startSpell(grammarNow = true) // ✅ directo a corregir gramática
            KeyboardView.TOOL_TRANSLATE -> if (translating) stopTranslate() else startTranslate()
            KeyboardView.TOOL_MORE -> showMode(6)
            KeyboardView.TOOL_HACKER -> toggleHacker()
            KeyboardView.TOOL_CODE -> toggleCode()
            KeyboardView.TOOL_STYLE -> {
                showMode(0)
                try {
                    startActivity(Intent(this, StyleActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                } catch (_: Exception) { }
            }
            KeyboardView.TOOL_VAULT -> {
                // Pide la huella y muestra tus correos, contraseñas y textos guardados
                showMode(0)
                try {
                    startActivity(Intent(this, VaultPickActivity::class.java)
                        .putExtra(VaultPickActivity.EXTRA_ALL, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
                } catch (_: Exception) { }
            }
            KeyboardView.TOOL_LANG -> onKeyCode(Codes.LANG)
            KeyboardView.TOOL_PHRASES -> showMode(7)
            KeyboardView.TOOL_CALC -> showMode(8)
            KeyboardView.TOOL_GIF -> showMode(9)
            KeyboardView.TOOL_READ -> readMessage()
            KeyboardView.TOOL_TICKET -> startTicket()
            KeyboardView.TOOL_BARCODE, KeyboardView.TOOL_SCAN -> {
                scanPanel?.codeMode = false
                if (hasPermission(android.Manifest.permission.CAMERA)) {
                    showMode(4)
                } else {
                    Toast.makeText(this, Emo.strip(L.t("Dale permiso a la cámara y vuelve a tocar el escáner.", "Allow the camera and tap the scanner again.")), Toast.LENGTH_LONG).show()
                    askPermission(MainActivity.EXTRA_ASK_CAMERA)
                }
            }
            KeyboardView.TOOL_STAMP -> showStamp()
            KeyboardView.TOOL_NOTEBOOK -> showMode(10)
            KeyboardView.TOOL_TICKETS -> showMode(11)
            KeyboardView.TOOL_WORKDAY -> showMode(12)
            KeyboardView.TOOL_MATERIALS -> showMode(13)
            KeyboardView.TOOL_REPLY -> startReplies()
            KeyboardView.TOOL_STATUS -> startStatus()
            KeyboardView.TOOL_WEEK -> startWeek(ticketLang())
            KeyboardView.TOOL_DRIVE -> if (mode == 14) { setDriveSticky(false); showMode(0) } else showMode(14)
            KeyboardView.TOOL_TODO -> showMode(15)
            KeyboardView.TOOL_CHECK -> showMode(16)
            KeyboardView.TOOL_CONVERT -> startConvert()
            KeyboardView.TOOL_IPPAD -> {
                // 🔢 Cambia al teclado de IP / MAC (y otra vez a las letras)
                showMode(0)
                page = if (page == Page.IP) Page.LETTERS else Page.IP
                manualShift = false
                rebuild()
                updateAutoCaps()
                if (page == Page.IP) Toast.makeText(this, Emo.strip(L.t("Teclado IP / MAC · «ABC» vuelve a las letras", "IP / MAC keypad · «ABC» goes back to letters")), Toast.LENGTH_SHORT).show()
            }
            KeyboardView.TOOL_LABELS -> startSearch(gif = false, custom = "labels", customHint = "PP1-01 a PP1-24" to "PP1-01 to PP1-24",
                info = Labels.preview(""))
            KeyboardView.TOOL_UNDO -> doUndo()
            KeyboardView.TOOL_GOOGLE -> startGoogle()
            KeyboardView.TOOL_REMIND -> startRemind()
            KeyboardView.TOOL_SPEAK -> startSpeak()
            KeyboardView.TOOL_BGSOUND -> {
                if (BgMedia.kind(this) != "video") {
                    Toast.makeText(this, Emo.strip(L.t("Primero pon un video de fondo en Ajustes → Estilo.", "First set a background video in Settings → Style.")), Toast.LENGTH_SHORT).show()
                } else {
                    val on = !prefs.getBoolean(Prefs.BG_SOUND, false)
                    prefs.edit().putBoolean(Prefs.BG_SOUND, on).apply()
                    applySettings()
                    kv?.invalidate()
                    Toast.makeText(this, Emo.strip(if (on) L.t("🔊 Video con sonido", "🔊 Video sound on") else L.t("🔇 Video en silencio", "🔇 Video muted")), Toast.LENGTH_SHORT).show()
                }
            }
            KeyboardView.TOOL_GLOVES -> {
                val on = !prefs.getBoolean(Prefs.GLOVES, false)
                prefs.edit().putBoolean(Prefs.GLOVES, on).apply()
                applySettings()
                Toast.makeText(this, Emo.strip(if (on) L.t("🧤 Modo guantes: teclas grandes", "🧤 Glove mode: big keys") else L.t("Modo guantes apagado", "Glove mode off")), Toast.LENGTH_SHORT).show()
            }
            KeyboardView.TOOL_SITES -> {
                showMode(0)
                try {
                    startActivity(Intent(this, SitesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                } catch (_: Exception) { }
            }
            KeyboardView.TOOL_WORD -> showWord()
            KeyboardView.TOOL_REVIEW -> startReview()
            KeyboardView.TOOL_FORMAL -> startFormalWord()
            KeyboardView.TOOL_REPORT -> {
                showMode(3)
                voicePanel?.let { vp ->
                    reportMode = true
                    reportBuf.setLength(0)
                    vp.setReport(true, false)
                    vp.setStatus(L.t("Habla con tus palabras. Al terminar toca «Crear reporte».", "Speak in your own words. When done tap «Create report»."))
                }
            }
            KeyboardView.TOOL_FLOAT -> {
                prefs.edit().putBoolean(Prefs.FLOAT, !prefs.getBoolean(Prefs.FLOAT, false)).apply()
                applyFloat()
            }
            KeyboardView.TOOL_ONEHAND -> {
                // Un toque: a la derecha (para el pulgar derecho); otro toque: normal
                val now = kv?.oneHand ?: 0
                onOneHand(if (now == 0) 2 else 0)
            }
            KeyboardView.TOOL_RESIZE -> {
                showMode(0)
                kv?.startResize()
            }
        }
    }

    // ---------------------------------------------------------------- Reescribir formal

    private fun startRewrite() {
        val bar = sBar ?: return
        val text = spellText.trim()
        if (text.isEmpty()) return
        bar.message(L.t("Reescribiendo con la IA del teléfono…", "Rewriting with the phone's AI…"), showRewrite = false)
        val token = spellToken
        FormalRewriter.run(this, spellText, lang,
            status = { msg -> if (spellOn && token == spellToken) bar.message(msg, showRewrite = false) },
            done = { list, err ->
                if (!spellOn || token != spellToken) return@run
                if (list == null) {
                    val noKey = prefs.getString(Prefs.GEMINI_KEY, "").isNullOrBlank()
                    if (noKey) {
                        bar.messageWithAction(
                            L.t("«Más formal» necesita tu clave GRATIS de Google Gemini (una sola vez).",
                                "«More formal» needs your FREE Google Gemini key (just once)."),
                            L.t("Poner mi clave", "Add my key"),
                        ) { stopSpell(keepSelection = true); askPermission(MainActivity.EXTRA_SHOW_AI) }
                    } else bar.message(err ?: "", showRewrite = false)
                } else {
                    rewrites = list
                    bar.showRewrite(list, 0)
                }
            })
    }

    /** "Corregir gramática": manda el texto a LanguageTool y muestra la versión corregida en verde. */
    private fun startGrammar() {
        val bar = sBar ?: return
        if (spellText.isBlank()) return
        bar.message(L.t("Revisando la gramática…", "Checking grammar…"), showRewrite = false)
        val token = spellToken
        CloudText.grammar(spellText, lang) { fixed, err ->
            if (!spellOn || token != spellToken) return@grammar
            when {
                fixed == null -> bar.message(err ?: "", showRewrite = true)
                fixed.count == 0 -> bar.message(L.t("✅ La gramática está bien.", "✅ Grammar looks good."), showRewrite = true)
                else -> {
                    rewrites = listOf(fixed.text)
                    val n = fixed.count
                    bar.showRewrite(rewrites, 0,
                        heading = if (n == 1) L.t("1 arreglo de gramática", "1 grammar fix") else L.t("$n arreglos de gramática", "$n grammar fixes"),
                        changes = fixed.changes)
                }
            }
        }
    }

    /** Cambia el texto seleccionado por la versión formal elegida. */
    private fun useRewrite(index: Int) {
        val ic = currentInputConnection ?: return
        val txt = rewrites.getOrNull(index) ?: return
        val end = spellBase + spellText.length
        ic.beginBatchEdit()
        ic.setSelection(spellBase, end)
        ic.commitText(txt, 1)
        ic.endBatchEdit()
        spellErrors = ArrayList()
        stopSpell(keepSelection = true)
    }

    // ---------------------------------------------------------------- Mayúsculas en lo seleccionado

    /**
     * Con texto seleccionado, ⇧ lo cambia: normal → MAYÚSCULAS → minúsculas → Tipo Título → MAYÚSCULAS...
     * El texto queda seleccionado para poder tocar ⇧ otra vez.
     */
    private fun changeSelectionCase(): Boolean {
        val ic = currentInputConnection ?: return false
        val sel = try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null }
        if (sel.isNullOrEmpty() || sel.none { it.isLetter() }) return false
        val upper = sel.uppercase()
        val lower = sel.lowercase()
        val out = when {
            sel == upper -> lower
            sel == lower -> titleCase(sel)
            else -> upper
        }
        val start = minOf(selStart, selEnd)
        ic.beginBatchEdit()
        ic.commitText(out, 1)
        ic.setSelection(start, start + out.length)
        ic.endBatchEdit()
        return true
    }

    private fun titleCase(s: String): String {
        val sb = StringBuilder(s.length)
        var startWord = true
        for (c in s) {
            sb.append(if (startWord && c.isLetter()) c.titlecaseChar() else c)
            startWord = !c.isLetterOrDigit() && c != '\'' && c != '’'
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- Modo código

    /** Enciende o apaga el Modo código (el teclado cambia para programar). */
    private fun toggleCode() {
        codeOn = !codeOn
        prefs.edit().putBoolean(Prefs.CODE_MODE, codeOn).apply()
        if (codeOn && hacker) prefs.edit().putBoolean(Prefs.HACKER, false).apply()
        codeToken++
        if (mode != 0) showMode(0)
        if (translating) stopTranslate()
        if (spellOn) stopSpell(keepSelection = true)
        codeBar?.let { it.hideAsk(); it.hideResult() }
        if (!numLike) page = Page.LETTERS
        manualShift = false
        setShift(0)
        applySettings()
        rebuild()
        clearSlots()
        if (codeOn) {
            Toast.makeText(this, Emo.strip(L.t("Modo código: Explicar, Escribir código, Arreglar error y Pista", "Code mode: Explain, Write code, Fix error and Hint")), Toast.LENGTH_SHORT).show()
        } else updateAutoCaps()
        scheduleUpdate()
    }

    /** Escribe tal cual (sin autocorrector ni espacios automáticos). */
    private fun codeText(text: String) {
        val ic = target() ?: return
        var t = text
        if (shift > 0 && t.length == 1) t = t.uppercase()
        ic.commitText(t, 1)
        if (shift == 1) { manualShift = false; setShift(0) }
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        scheduleUpdate()
    }

    /** Enter con sangría automática: copia la de la línea y agrega 4 espacios después de ":" o "{". */
    private fun codeEnter() {
        val ic = target() ?: return
        val ei = targetInfo()
        val action = if (ei != null) enterActionFor(ei) else EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE) { ic.performEditorAction(action); return }
        val before = ic.getTextBeforeCursor(500, 0)?.toString() ?: ""
        val line = before.substringAfterLast('\n')
        var indent = line.takeWhile { it == ' ' || it == '\t' }
        if (CodeWords.opensBlock(codeLang, line)) indent += CodeLayouts.TAB
        ic.commitText("\n" + indent, 1)
        lastWasSpace = false
        scheduleUpdate()
    }

    /** Borrar en una sangría vacía borra los 4 espacios de una vez. */
    private fun codeDeleteIndent(): Boolean {
        val ic = target() ?: return false
        if (selStart != selEnd) return false
        val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: return false
        val line = before.substringAfterLast('\n')
        if (line.length >= 4 && line.isNotEmpty() && line.all { it == ' ' } && line.endsWith(CodeLayouts.TAB)) {
            val n = if (line.length % 4 == 0) 4 else line.length % 4
            ic.deleteSurroundingText(n, 0)
            scheduleUpdate()
            return true
        }
        return false
    }

    /** Sugiere palabras del lenguaje (print(), for … in range():…) según lo que escribes. */
    private fun codeSuggest() {
        val ic = target()
        if (ic == null || codeAsking || numLike) { clearSlots(); return }
        val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
        val token = before.takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '<' || it == '#' }
        val list = CodeWords.complete(codeLang, token)
        if (list.isEmpty()) { clearSlots(); return }
        val order = listOf(1, 0, 2)
        val labels = arrayOfNulls<String>(3)
        val values = arrayOfNulls<String>(3)
        for ((i, w) in list.withIndex()) {
            labels[order[i]] = CodeWords.label(w)
            values[order[i]] = w
        }
        showSlots(labels[0], labels[1], labels[2], false, false)
        for (i in 0..2) codeValues[i] = values[i]
        codeActive = true
    }

    /** Tocaste un fragmento: reemplaza lo escrito y deja el cursor donde va (ej. dentro de print()). */
    private fun useCodeSnippet(slot: Int) {
        val snippet = codeValues.getOrNull(slot) ?: return
        val ic = target() ?: return
        val before = ic.getTextBeforeCursor(500, 0)?.toString() ?: ""
        val token = before.takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '<' || it == '#' }
        val indent = before.substringAfterLast('\n').takeWhile { it == ' ' || it == '\t' }
        // Las líneas nuevas del fragmento siguen la sangría de la línea actual
        val text = snippet.replace("\n", "\n" + indent)
        val mark = text.indexOf(CodeWords.CURSOR)
        val clean = text.replace(CodeWords.CURSOR.toString(), "")
        ic.beginBatchEdit()
        if (token.isNotEmpty()) ic.deleteSurroundingText(token.length, 0)
        ic.commitText(clean, 1)
        ic.endBatchEdit()
        if (mark >= 0) repeat(clean.length - mark) { sendKey(KeyEvent.KEYCODE_DPAD_LEFT) }
        clearSlots()
        lastCorrection = null
    }

    /** El código que seleccionaste, o todo el texto si no seleccionaste nada. */
    private fun codeSource(): String {
        val ic = currentInputConnection ?: return ""
        val sel = if (selStart != selEnd) try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null } else null
        if (!sel.isNullOrBlank()) return sel.take(12000)
        val et = try { ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 12000 }, 0) } catch (_: Exception) { null }
        val all = et?.text?.toString()
            ?: ((ic.getTextBeforeCursor(6000, 0)?.toString() ?: "") + (ic.getTextAfterCursor(6000, 0)?.toString() ?: ""))
        return all.take(12000)
    }

    /** Quita marcas de formato (**negritas**, # títulos) de las explicaciones. */
    private fun plain(t: String) = t.replace("**", "").replace("__", "")
        .replace(Regex("(?m)^#{1,6}\\s+"), "").replace(Regex("(?m)^\\s*[*-]\\s+"), "• ").trim()

    /** El primer bloque ```código``` de la respuesta (o null). */
    private fun codeBlock(t: String): String? {
        val m = Regex("```[a-zA-Z0-9+#]*\\n?([\\s\\S]*?)```").find(t) ?: return null
        return m.groupValues[1].trimEnd()
    }

    /** Pregunta a Gemini y muestra el resultado en la barra del Modo código. */
    private fun codeAsk(prompt: String, onAnswer: (String) -> Unit) {
        val cb = codeBar ?: return
        val key = prefs.getString(Prefs.GEMINI_KEY, "")?.trim().orEmpty()
        cb.showStatus(L.t("Pensando…", "Thinking…"))
        val token = ++codeToken
        CloudText.ask(prompt, key) { list, err ->
            if (!codeOn || token != codeToken) return@ask
            val ans = list?.firstOrNull()
            if (ans == null) cb.showStatus(err ?: L.t("No hubo respuesta.", "No answer.")) else onAnswer(ans)
        }
    }

    private val codeActions = object : CodeBar.Actions {
        override fun onCodeFeedback() = onKeyFeedback()
        override fun onCodeClose() = toggleCode()
        override fun onCodeLang() {
            val i = CodeWords.LANGS.indexOf(codeLang)
            codeLang = CodeWords.LANGS[(i + 1) % CodeWords.LANGS.size]
            prefs.edit().putString(Prefs.CODE_LANG, codeLang).apply()
            codeBar?.setLang(codeLang)
            scheduleUpdate()
        }

        override fun onCodeExplain() {
            val src = codeSource()
            if (src.isBlank()) { codeBar?.showStatus(L.t("Escribe o selecciona código primero.", "Type or select some code first.")); return }
            val prompt = if (L.english)
                "You are a patient programming teacher. Explain this $codeLang code line by line in very simple English for a total beginner. " +
                    "For each line write the line, then below it '→ ' and what it does. At the end, say in 1–2 sentences what the whole program does. " +
                    "No markdown (no **, no #).\n\nCode:\n$src"
            else
                "Eres un profesor de programación paciente. Explica este código de $codeLang línea por línea en ${aiLang()} muy sencillo, para un principiante total. " +
                    "Para cada línea escribe la línea y debajo '→ ' y lo que hace. Al final, di en 1–2 frases qué hace el programa completo. " +
                    "Sin markdown (sin **, sin #).\n\nCódigo:\n$src"
            codeAsk(prompt) { codeBar?.showResult(L.t("Explicación", "Explanation"), plain(it), null, false) }
        }

        override fun onCodeWrite() {
            codeBar?.showAsk()
            clearSlots()
            updateAutoCaps()
        }

        override fun onCodeCancelAsk() {
            codeBar?.hideAsk()
            scheduleUpdate()
        }

        override fun onCodeAsk(description: String) {
            if (description.isBlank()) return
            codeBar?.hideAsk()
            val prompt = if (L.english)
                "Write $codeLang code that does this: $description\nUse clear names and short comments in English explaining each part. " +
                    "Keep it simple for a beginner. Reply ONLY with the code, no ``` and no explanations outside the code."
            else
                "Escribe código en $codeLang que haga esto: $description\nUsa nombres claros y comentarios cortos en ${aiLang()} que expliquen cada parte. " +
                    "Que sea sencillo para un principiante. Responde SOLO con el código, sin ``` y sin explicaciones fuera del código."
            codeAsk(prompt) { ans ->
                val code = codeBlock(ans) ?: ans.trim()
                codeBar?.showResult(L.t("Código listo", "Code ready"), code, code, true)
            }
        }

        override fun onCodeFix() {
            val src = codeSource()
            if (src.isBlank()) { codeBar?.showStatus(L.t("Pega o selecciona tu código o el error primero.", "Paste or select your code or the error first.")); return }
            val prompt = if (L.english)
                "This is $codeLang code and/or an error message. 1) Explain in simple English, for a beginner, what is wrong and why. " +
                    "2) Then give the complete fixed code between ``` and ```. No other markdown.\n\n$src"
            else
                "Esto es código de $codeLang y/o un mensaje de error. 1) Explica en ${aiLang()} sencillo, para un principiante, qué está mal y por qué. " +
                    "2) Luego da el código corregido completo entre ``` y ```. Sin otro markdown.\n\n$src"
            codeAsk(prompt) { ans ->
                val code = codeBlock(ans)
                val why = plain(if (code != null) ans.replace(Regex("```[\\s\\S]*?```"), "").trim() else ans)
                val shown = if (code != null) why + "\n\n" + L.t("Código corregido:", "Fixed code:") + "\n" + code else why
                codeBar?.showResult(L.t("Qué estaba mal", "What was wrong"), shown, code, false)
            }
        }

        override fun onCodeHint() {
            val src = codeSource()
            val prompt = if (L.english)
                "I'm learning $codeLang. Give me ONE short hint (2–3 sentences) in simple English to move forward or fix this code, " +
                    "WITHOUT giving the full solution or writing the final code. No markdown.\n\n$src"
            else
                "Estoy aprendiendo $codeLang. Dame UNA pista corta (2–3 frases) en ${aiLang()} sencillo para avanzar o arreglar este código, " +
                    "SIN darme la solución completa ni escribir el código final. Sin markdown.\n\n$src"
            codeAsk(prompt) { codeBar?.showResult(L.t("Pista", "Hint"), plain(it), null, false) }
        }

        override fun onCodeInsert(text: String) {
            currentInputConnection?.commitText(text, 1)
            codeBar?.hideResult()
            scheduleUpdate()
        }

        override fun onCodeCopy(text: String) {
            try {
                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("code", text))
                Toast.makeText(this@BestKeyboardService, Emo.strip(L.t("Copiado", "Copied")), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { }
        }

        override fun onCodeAddKey() {
            codeBar?.hideResult()
            askPermission(MainActivity.EXTRA_SHOW_AI)
        }
    }

    // ---------------------------------------------------------------- Teclado de computadora

    /** Enciende o apaga el teclado de computadora ("modo hacker"). */
    private fun toggleHacker() {
        hacker = !hacker
        prefs.edit().putBoolean(Prefs.HACKER, hacker).apply()
        if (hacker && codeOn) prefs.edit().putBoolean(Prefs.CODE_MODE, false).apply()
        hCtrl = 0; hAlt = 0; hShift = 0; hFn = false
        if (mode != 0) showMode(0)
        if (!numLike) page = Page.LETTERS
        applySettings()
        rebuild()
        if (hacker) {
            clearSlots()
            setShift(0)
            Toast.makeText(this, Emo.strip(L.t("Teclado de computadora: Ctrl, Alt, Esc, Tab, flechas y Fn (F1–F12)",
                "PC keyboard: Ctrl, Alt, Esc, Tab, arrows and Fn (F1–F12)")), Toast.LENGTH_SHORT).show()
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

    override fun onToolsReordered(bar: List<Int>, more: List<Int>) {
        Tools.save(prefs, bar, more)
        morePanel?.setTools(bar, more, Tools.maxBar(this))
    }

    override fun onOneHand(mode: Int) {
        prefs.edit().putString(Prefs.ONE_HAND, when (mode) { 1 -> "left"; 2 -> "right"; else -> "off" }).apply()
        kv?.oneHand = mode
    }

    // ---------------------------------------------------------------- Frases

    /** Escribe la frase; si tiene "___", lo deja seleccionado para que escribas encima. */
    private fun insertPhrase(text: String) {
        val ic = target() ?: return
        val before = ic.getTextBeforeCursor(1, 0) ?: ""
        val space = if (before.isNotEmpty() && !before[0].isWhitespace()) " " else ""
        val full = space + text
        val blank = full.indexOf(Phrases.BLANK)
        ic.beginBatchEdit()
        ic.commitText(full, 1)
        if (blank >= 0) {
            val ex = try { ic.getExtractedText(ExtractedTextRequest(), 0) } catch (_: Exception) { null }
            if (ex != null) {
                val end = ex.startOffset + ex.selectionEnd
                val start = end - full.length + blank
                if (start >= 0) ic.setSelection(start, start + Phrases.BLANK.length)
            }
        }
        ic.endBatchEdit()
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
    }

    // ---------------------------------------------------------------- Traducir un mensaje

    /** Adivina si un texto está en inglés (si no, lo tomamos como español). */
    private fun looksEnglish(t: String): Boolean {
        val low = t.lowercase()
        val words = low.split(Regex("[^a-záéíóúñü']+")).filter { it.isNotEmpty() }
        val en = setOf("the", "and", "you", "is", "are", "to", "of", "it", "i", "for", "on", "with", "this", "that", "have", "be", "what", "can", "will", "please", "thanks", "my", "your", "at", "in", "we", "do", "not", "was", "hi", "hello", "ok", "yes", "how", "when", "where")
        val es = setOf("el", "la", "los", "las", "de", "que", "y", "en", "es", "un", "una", "por", "para", "con", "no", "se", "lo", "tu", "mi", "como", "pero", "si", "muy", "gracias", "hola", "está", "estoy", "qué", "yo", "te", "me", "ya", "al", "del", "cuando", "donde")
        var e = 0
        var sp = 0
        for (w in words) { if (w in en) e++; if (w in es) sp++ }
        if (Regex("[áéíóúñ¿¡]").containsMatchIn(low)) sp += 2
        return e > sp
    }

    // ---------------------------------------------------------------- 🔊 Escuchar texto

    /** Lee en voz alta lo que seleccionaste (o lo último que copiaste, o lo que acabas de escribir). */
    private fun startSpeak(given: String? = null, englishIn: Boolean? = null, slow: Boolean = false) {
        showMode(0)
        val bar = infoBar ?: return
        val ic = currentInputConnection
        val text = given ?: run {
            val sel = try { ic?.getSelectedText(0)?.toString()?.trim() } catch (_: Exception) { null }
            if (!sel.isNullOrEmpty()) return@run sel
            val clip = try { clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim() } catch (_: Exception) { null }
            val before = try { ic?.getTextBeforeCursor(400, 0)?.toString()?.trim() } catch (_: Exception) { null }
            // Lo que acabas de escribir (la última frase) o, si no hay, lo que copiaste
            val last = before?.split(Regex("(?<=[.!?\\n])\\s+"))?.lastOrNull { it.isNotBlank() }?.trim()
            last?.takeIf { it.isNotEmpty() } ?: clip
        }?.take(1200)
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, Emo.strip(L.t("Selecciona un texto (o cópialo) y vuelve a tocar 🔊.", "Select a text (or copy it) and tap 🔊 again.")), Toast.LENGTH_LONG).show()
            return
        }
        // ¿En qué idioma está? Una sola palabra sin acentos: el idioma del teclado
        val oneWord = !text.contains(' ')
        val english = englishIn ?: if (oneWord && !Regex("[áéíóúñ¿¡]").containsMatchIn(text.lowercase())) L.english else looksEnglish(text)
        Speak.rate = if (slow) 0.6f else 0.85f
        Speak.say(this, text, english)
        infoRegen = { startSpeak(text, english, slow) }
        bar.show(L.t("🔊 Escuchar", "🔊 Listen") + "  ·  " + (if (english) "English" else "Español"), text,
            listOf(
                "🔊 English" to { startSpeak(text, true, slow) },
                "🔊 Español" to { startSpeak(text, false, slow) },
                (if (slow) L.t("🐇 Normal", "🐇 Normal") else L.t("🐢 Más despacio", "🐢 Slower")) to { startSpeak(text, english, !slow) },
                L.t("⏹ Parar", "⏹ Stop") to { Speak.stop() },
                L.t("Cerrar", "Close") to { Speak.stop(); closeInfo() },
            ))
    }

    /** Traduce lo que seleccionaste o, si no hay nada, el último mensaje que copiaste. */
    private fun readMessage() {
        showMode(0)
        val ic = currentInputConnection
        val sel = try { ic?.getSelectedText(0)?.toString()?.trim() } catch (_: Exception) { null }
        val fromSel = !sel.isNullOrEmpty()
        val text = if (fromSel) sel else try {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        } catch (_: Exception) { null }
        val bar = infoBar ?: return
        if (text.isNullOrEmpty()) {
            bar.show(L.t("Traducir mensaje", "Translate message"), L.t(
                "Mantén presionado el mensaje que te llegó y toca Copiar. Después vuelve a tocar este botón.\n\nTambién puedes seleccionar texto aquí y tocarlo.",
                "Long-press the message you got and tap Copy. Then tap this button again.\n\nYou can also select text here and tap it."),
                listOf(L.t("Entendido", "Got it") to { closeInfo() }))
            return
        }
        val src = if (looksEnglish(text)) "en" else "es"
        val dst = if (src == "es") "en" else "es"
        val heading = if (src == "en") L.t("Inglés → Español", "English → Spanish") else L.t("Español → Inglés", "Spanish → English")
        bar.show(heading, L.t("Traduciendo…", "Translating…"), emptyList())
        readTrans?.close()
        val t = Translation(src, dst)
        readTrans = t
        val fail: (String) -> Unit = {
            handler.post {
                if (readTrans === t) bar.show(heading,
                    L.t("No se pudo traducir. La primera vez necesita internet para bajar el idioma.", "Couldn't translate. The first time it needs internet to download the language."),
                    listOf(L.t("Cerrar", "Close") to { closeInfo() }))
            }
        }
        t.prepare({
            t.translate(text, { out ->
                handler.post {
                    if (readTrans !== t) return@post
                    kv?.flashAi() // ✨ ya está la traducción
                    val buttons = ArrayList<Pair<String, () -> Unit>>()
                    if (fromSel) buttons.add(L.t("Reemplazar", "Replace") to {
                        currentInputConnection?.commitText(out, 1)
                        closeInfo()
                    })
                    buttons.add(L.t("Copiar", "Copy") to {
                        try { clipboard?.setPrimaryClip(ClipData.newPlainText("Traducción", out)) } catch (_: Exception) { }
                        Toast.makeText(this, Emo.strip(L.t("Copiado", "Copied")), Toast.LENGTH_SHORT).show()
                    })
                    if (!fromSel) buttons.add(L.t("Escribir", "Type it") to { insertText(out); closeInfo() })
                    buttons.add(L.t("Cerrar", "Close") to { closeInfo() })
                    bar.show(heading, out, buttons)
                }
            }, fail)
        }, fail)
    }

    // ---------------------------------------------------------------- IA para el trabajo

    /** El texto con el que trabaja la IA y dónde está (-1 = no reemplaza, solo escribe). */
    private var aiSource = ""
    private var aiBase = -1
    private var aiLen = -1
    private var aiSeq = 0

    /** La clave de Gemini, o "" si no tienes (entonces se usa la IA gratis, sin clave). */
    @Suppress("UNUSED_PARAMETER")
    private fun aiKey(title: String): String? = prefs.getString(Prefs.GEMINI_KEY, "")?.trim() ?: ""

    /** Lo seleccionado o, si no hay selección, todo el texto de la caja (texto, inicio, largo). */
    private fun grabField(): Triple<String, Int, Int>? {
        val ic = currentInputConnection ?: return null
        val selected = if (selStart != selEnd) try { ic.getSelectedText(0)?.toString() } catch (_: Exception) { null } else null
        if (!selected.isNullOrBlank()) return Triple(selected, minOf(selStart, selEnd), selected.length)
        val et = try {
            ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 20000; hintMaxLines = 500 }, 0)
        } catch (_: Exception) { null }
        if (et?.text != null) return Triple(et.text.toString(), maxOf(0, et.startOffset), et.text.length)
        val before = ic.getTextBeforeCursor(5000, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(5000, 0)?.toString() ?: ""
        return Triple(before + after, maxOf(0, minOf(selStart, selEnd) - before.length), before.length + after.length)
    }

    /** Pone [text] en lugar del texto original (o lo escribe donde está el cursor). */
    private fun aiPut(text: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (aiBase >= 0 && aiLen >= 0) ic.setSelection(aiBase, aiBase + aiLen)
        ic.commitText(text, 1)
        ic.endBatchEdit()
        aiBase = -1
        aiLen = -1
        lastCorrection = null
        closeInfo()
    }

    private fun aiCopy(text: String) {
        try { clipboard?.setPrimaryClip(ClipData.newPlainText("Best Keyboard", text)) } catch (_: Exception) { }
        Toast.makeText(this, Emo.strip(L.t("Copiado", "Copied")), Toast.LENGTH_SHORT).show()
    }

    private fun langName(l: String) = if (l == "en") "English" else "Spanish"

    /** Notas → Ticket: lo que escribiste rápido se vuelve un reporte formal y detallado. */
    private fun startTicket() {
        if (isPassword) return
        showMode(0)
        val f = grabField()
        if (f == null || f.first.isBlank()) {
            infoBar?.show(L.t("Notas → Ticket", "Notes → Ticket"), L.t(
                "Escribe tus notas rápido y con tus palabras (por ejemplo: «cambie maglock puerta 3 ya sirve») y vuelve a tocar este botón.",
                "Type your notes quickly in your own words (e.g. «swapped maglock door 3 works now») and tap this button again."),
                listOf(L.t("Entendido", "Got it") to { closeInfo() }))
            return
        }
        aiSource = f.first
        aiBase = f.second
        aiLen = f.third
        runTicket(ticketLang())
    }

    /** Las horas de "Mi jornada" de hoy, para que el ticket las use. */
    private fun workdayHint(): String {
        val sb = StringBuilder()
        val ev = WorkDay.day(prefs)
        if (ev.isNotEmpty()) {
            sb.append("Times recorded today with the time tracker (use them for Arrival Time / Departure Time unless the notes say otherwise):\n")
            sb.append(WorkDay.text(ev, true)).append("\n")
            val site = ev.lastOrNull { it.kind == WorkDay.ARRIVE && it.site.isNotEmpty() }?.site
            if (site != null) {
                val addr = SiteStore.find(prefs, site)?.address ?: ""
                sb.append("Location (from GPS): ").append(site).append(if (addr.isNotEmpty()) " — $addr" else "").append("\n")
            }
        }
        val mats = Materials.text(prefs)
        if (mats.isNotEmpty()) sb.append("Materials the technician marked as used today (include them in the Equipment List):\n").append(mats).append("\n")
        return sb.toString()
    }

    /** Los tickets del trabajo van en inglés; si no usas ese formato, en el idioma del teclado. */
    private fun ticketLang() = if (prefs.getBoolean(Prefs.TICKET_FORMAT, true)) "en" else lang

    private fun runTicket(outLang: String) {
        val title = if (outLang == "en") L.t("Ticket en inglés", "Ticket in English") else L.t("Ticket en español", "Ticket in Spanish")
        val key = aiKey(title) ?: return
        val bar = infoBar ?: return
        val my = ++aiSeq
        bar.show(title, L.t("Escribiendo el ticket…", "Writing the ticket…"), listOf(L.t("Cancelar", "Cancel") to { aiSeq++; closeInfo() }))
        val num = WorkStore.nextNumber(prefs)
        val workFormat = prefs.getBoolean(Prefs.TICKET_FORMAT, true)
        val format = if (!workFormat) "" else
            "Use exactly this layout (field names in English even if the text is in another language):\n" +
            "Ticket $num\nDate: ${WorkStore.ticketDate(System.currentTimeMillis())}\nLocation: ...\nArrival Time: ...\nDeparture Time: ...\n" +
            "Job Description:\n...\nEquipment List:\n- ...\n" +
            "If the notes say it is a service call, add \"(Service Call)\" after the ticket number and replace Job Description with " +
            "these sections: Issue Reported, Issue Found, Action Taken, Next Steps.\n" +
            "Equipment List: ONLY the materials and parts mentioned in the notes; never add anything. If none, write \"None\".\n" +
            "If a field is not in the notes, write \"—\" after it.\n"
        val prompt = "You help a low-voltage field technician (access control, card readers, maglocks, door automation, " +
            "IP cameras, NVR, fiber optics, Cat 6 cabling) write work tickets for coworkers and supervisors.\n" +
            "Turn the rough notes below into a clear, professional and detailed work ticket written in " +
            (if (workFormat && outLang == "en") "professional technical English" else langName(outLang)) + ".\n" + format +
            "Rules:\n- Keep every fact (sites, door numbers, rooms, times, names, part names). Do not invent facts.\n" +
            "- Expand abbreviations and fix spelling.\n- First person, past tense, short clear sentences, in a logical order " +
            "(arrival, work done, tests, result, pending items).\n- Plain text only. No markdown, no asterisks.\n" +
            "- Answer with the ticket text only.\n" + workdayHint() + "\nNotes:\n" + aiSource
        CloudText.ask(prompt, key) { list, err ->
            if (my != aiSeq) return@ask
            val out = list?.firstOrNull()?.replace("**", "")?.trim()
            if (out.isNullOrEmpty()) {
                bar.show(title, err ?: L.t("No se pudo crear el ticket.", "Couldn't create the ticket."),
                    listOf(L.t("Reintentar", "Retry") to { runTicket(outLang) }, L.t("Cerrar", "Close") to { closeInfo() }))
                return@ask
            }
            val other = if (outLang == "en") "es" else "en"
            // Al usarlo (reemplazar, escribir o copiar) se guarda en "Mis tickets" con su número
            val keep = { WorkStore.addTicket(prefs, num, out) }
            bar.show(title, out, listOf(
                (if (aiLen >= 0) L.t("Reemplazar", "Replace") else L.t("Escribir", "Type it")) to { keep(); aiPut(out) },
                (if (other == "en") "In English" else "En español") to { runTicket(other) },
                L.t("Copiar", "Copy") to { keep(); aiCopy(out) },
                L.t("Cerrar", "Close") to { closeInfo() },
            ))
        }
    }

    /** Revisar antes de enviar: ortografía, claridad y tono profesional. */
    private fun startReview() {
        if (isPassword) return
        showMode(0)
        val title = L.t("Revisar antes de enviar", "Review before sending")
        val f = grabField()
        if (f == null || f.first.isBlank()) {
            infoBar?.show(title, L.t("Escribe tu mensaje y toca este botón antes de enviarlo.", "Type your message and tap this button before sending it."),
                listOf(L.t("Entendido", "Got it") to { closeInfo() }))
            return
        }
        val key = aiKey(title) ?: return
        val bar = infoBar ?: return
        aiSource = f.first
        aiBase = f.second
        aiLen = f.third
        val my = ++aiSeq
        bar.show(title, L.t("Revisando…", "Reviewing…"), listOf(L.t("Cancelar", "Cancel") to { aiSeq++; closeInfo() }))
        val notesLang = if (L.english) "English" else "Spanish"
        val prompt = "Review this message before it is sent. Correct spelling, grammar and punctuation and make it clear and " +
            "professional, but keep the same language, meaning, tone and a similar length. Do not add new information.\n" +
            "Answer exactly in this format:\nTEXT:\n<the improved message>\nNOTES:\n<at most 3 very short notes in $notesLang " +
            "about what you changed, one per line, starting with - >\n\nMessage:\n" + aiSource
        CloudText.ask(prompt, key) { list, err ->
            if (my != aiSeq) return@ask
            val raw = list?.firstOrNull()?.replace("**", "")?.trim()
            if (raw.isNullOrEmpty()) {
                bar.show(title, err ?: L.t("No se pudo revisar.", "Couldn't review."),
                    listOf(L.t("Reintentar", "Retry") to { startReview() }, L.t("Cerrar", "Close") to { closeInfo() }))
                return@ask
            }
            val ti = raw.indexOf("TEXT:")
            val ni = raw.indexOf("NOTES:")
            val fixed = when {
                ti >= 0 && ni > ti -> raw.substring(ti + 5, ni).trim()
                ti >= 0 -> raw.substring(ti + 5).trim()
                else -> raw
            }
            val notes = if (ni >= 0) raw.substring(ni + 6).trim() else ""
            val same = fixed.trim() == aiSource.trim()
            val body = if (same) L.t("✅ Tu mensaje está bien. Puedes enviarlo.", "✅ Your message looks good. You can send it.")
                else fixed + (if (notes.isNotEmpty()) "\n\n💡 " + notes.replace("\n", "\n💡 ").replace("💡 - ", "💡 ") else "")
            val buttons = ArrayList<Pair<String, () -> Unit>>()
            if (!same) {
                buttons.add(L.t("Usar este", "Use this") to { aiPut(fixed) })
                buttons.add(L.t("Copiar", "Copy") to { aiCopy(fixed) })
            }
            buttons.add(L.t("Cerrar", "Close") to { closeInfo() })
            bar.show(title, body, buttons)
        }
    }

    /** Más formal (palabra): opciones más profesionales para lo seleccionado o la palabra donde está el cursor. */
    private fun startFormalWord() {
        if (isPassword) return
        showMode(0)
        val ic = currentInputConnection ?: return
        val title = L.t("Palabras más formales", "More formal words")
        var word: String? = if (selStart != selEnd) try { ic.getSelectedText(0)?.toString()?.trim() } catch (_: Exception) { null } else null
        var base = minOf(selStart, selEnd)
        var len = word?.length ?: 0
        if (word.isNullOrEmpty()) {
            // La palabra donde está el cursor (o la última que escribiste)
            val before = ic.getTextBeforeCursor(40, 0)?.toString() ?: ""
            val after = ic.getTextAfterCursor(40, 0)?.toString() ?: ""
            var b = before.length
            while (b > 0 && before[b - 1] == ' ') b--
            var a = b
            while (a > 0 && TextUtil.isWordChar(before[a - 1])) a--
            var e = 0
            if (b == before.length) while (e < after.length && TextUtil.isWordChar(after[e])) e++
            word = before.substring(a, b) + after.substring(0, e)
            base = selStart - (before.length - a)
            len = word.length
        }
        if (word.isNullOrBlank()) {
            infoBar?.show(title, L.t("Selecciona una palabra o frase (o deja el cursor en ella) y toca este botón.",
                "Select a word or phrase (or put the cursor on it) and tap this button."), listOf(L.t("Entendido", "Got it") to { closeInfo() }))
            return
        }
        val key = aiKey(title) ?: return
        val bar = infoBar ?: return
        val ctx = (ic.getTextBeforeCursor(160, 0)?.toString() ?: "") + (ic.getTextAfterCursor(80, 0)?.toString() ?: "")
        val my = ++aiSeq
        val target = word
        bar.show(title, L.t("Buscando opciones para «$target»…", "Finding options for «$target»…"), listOf(L.t("Cancelar", "Cancel") to { aiSeq++; closeInfo() }))
        val prompt = "Give 5 more formal and professional ways to say \"$target\", in the same language as that text. " +
            "Context where it is used: \"$ctx\". Each option must fit in the same place of the sentence (same grammatical form). " +
            "One option per line. No numbering, no quotes, no explanations."
        CloudText.ask(prompt, key) { list, err ->
            if (my != aiSeq) return@ask
            val opts = list?.firstOrNull()?.lines()?.map { it.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim('"', ' ') }
                ?.filter { it.isNotEmpty() && it.length < 80 }?.distinct()?.take(6) ?: emptyList()
            if (opts.isEmpty()) {
                bar.show(title, err ?: L.t("No encontré opciones.", "No options found."), listOf(L.t("Cerrar", "Close") to { closeInfo() }))
                return@ask
            }
            val buttons = ArrayList<Pair<String, () -> Unit>>()
            for (o in opts) buttons.add(o to {
                val c = currentInputConnection
                if (c != null) {
                    c.beginBatchEdit()
                    c.setSelection(base, base + len)
                    c.commitText(o, 1)
                    c.endBatchEdit()
                }
                lastCorrection = null
                closeInfo()
            })
            buttons.add(L.t("Cerrar", "Close") to { closeInfo() })
            bar.show(title, L.t("Toca una opción para cambiar «$target»:", "Tap an option to replace «$target»:"), buttons)
        }
    }

    // ---------------------------------------------------------------- Libreta del día y Mis tickets

    private fun refreshNotebook() {
        val panel = notebookPanel ?: return
        val notes = WorkStore.notes(prefs)
        val rows = notes.map { n ->
            ListPanel.Row(WorkStore.time(n.time), n.text,
                onTap = { insertText(n.text) },
                onDelete = { WorkStore.removeNote(prefs, n.time); refreshNotebook() })
        }
        val buttons = ArrayList<Pair<String, () -> Unit>>()
        buttons.add(L.t("➕ Apuntar", "➕ Add note") to { startSearch(gif = false, note = true) })
        if (notes.isNotEmpty()) {
            buttons.add(L.t("📋 Crear ticket del día", "📋 Create the day's ticket") to {
                aiSource = WorkStore.notesText(prefs)
                aiBase = -1
                aiLen = -1
                showMode(0)
                runTicket(ticketLang())
            })
            buttons.add(L.t("Vaciar", "Clear") to { WorkStore.clearNotes(prefs); refreshNotebook() })
        }
        panel.show(L.t("📒 Libreta de hoy", "📒 Today's notebook") + if (notes.isEmpty()) "" else " (${notes.size})", rows,
            L.t("Apunta lo que vas haciendo en el día (toca ➕ Apuntar). Al final, «Crear ticket del día» lo junta todo en un ticket.",
                "Note what you do during the day (tap ➕ Add note). At the end, «Create the day's ticket» puts it all in one ticket."),
            buttons)
    }

    private fun refreshTickets() {
        val panel = ticketsPanel ?: return
        val list = WorkStore.tickets(prefs)
        val rows = list.map { t ->
            ListPanel.Row(L.t("Ticket ", "Ticket ") + t.num + "  ·  " + WorkStore.shortDate(t.time, L.english), t.text,
                onTap = { showMode(0); insertText(t.text) },
                onCopy = { aiCopy(t.text) },
                onDelete = { WorkStore.removeTicket(prefs, t.num, t.time); refreshTickets() })
        }
        panel.show(L.t("🗂️ Mis tickets", "🗂️ My tickets") + "  ·  " + L.t("próximo: ", "next: ") + WorkStore.nextNumber(prefs), rows,
            L.t("Aquí se guardan los tickets que creas con 📋 Notas → Ticket. Toca uno para escribirlo y usarlo de base.",
                "Tickets you create with 📋 Notes → Ticket are saved here. Tap one to type it and use it as a base."),
            listOf(L.t("📋 Ticket nuevo", "📋 New ticket") to { showMode(0); startTicket() },
                L.t("🔍 Buscar", "🔍 Search") to { openFind() },
                L.t("📸 Enviar con fotos", "📸 Send with photos") to {
                    showMode(0)
                    try {
                        startActivity(Intent(this, TicketPhotosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    } catch (_: Exception) { }
                }))
    }

    // ---------------------------------------------------------------- Mi jornada

    /** 🏁 Terminaste tu día: el diamante grande con su luz y tu resumen. */
    private fun celebrateDay() {
        val bar = infoBar ?: return
        val sum = WorkDay.summary(WorkDay.day(prefs))
        val ot = WorkDay.overtime(sum.total)
        val wk = WorkDay.weekTotal(prefs)
        infoRegen = null
        bar.celebrate(L.t("🏁 ¡Terminaste tu día!", "🏁 Your day is done!"),
            L.t("Trabajaste ${WorkDay.dur(sum.total)} hoy", "You worked ${WorkDay.dur(sum.total)} today") +
                (if (ot > 0) L.t(" · ⏰ ${WorkDay.dur(ot)} de horas extra", " · ⏰ ${WorkDay.dur(ot)} overtime") else "") +
                L.t("\nEsta semana llevas ${WorkDay.dur(wk)}. ¡Buen trabajo! 💪", "\nThis week you have ${WorkDay.dur(wk)}. Great job! 💪"),
            listOf(L.t("Gracias", "Thanks") to { closeInfo() }))
    }

    /** ⏰ Llegaste a 40 horas esta semana (una sola vez por semana). */
    private fun maybeCelebrate40() {
        val wk = WorkDay.weekTotal(prefs)
        if (wk < 40L * 3600_000L) return
        val key = "ot40_" + WorkDay.weekStart()
        if (prefs.getBoolean(key, false)) return
        prefs.edit().putBoolean(key, true).apply()
        val bar = infoBar ?: return
        infoRegen = null
        bar.celebrate(L.t("⏰ ¡Llegaste a 40 horas!", "⏰ You reached 40 hours!"),
            L.t("Desde ahora, lo que trabajes esta semana cuenta como horas extra. ✨", "From now on, what you work this week counts as overtime. ✨"),
            listOf(L.t("¡Genial!", "Awesome!") to { closeInfo() }))
    }

    // ---------------------------------------------------------------- 🌐 Idioma (y cambio solo)

    /** Cambia el idioma del teclado (con la luz alrededor de la barra espaciadora). */
    private fun setLang(l: String) {
        if (l == lang) return
        lang = l
        prefs.edit().putString(Prefs.LANG, lang).apply()
        L.set(lang)
        kv?.spaceLabel = if (lang == "es") "Español" else "English"
        kv?.flashSpace() // 🌐 la luz le da la vuelta a la barra espaciadora
        langVotes.clear()
        rebuild() // en inglés el teclado cambia de verdad: sin la ñ (sale manteniendo la n)
        scheduleUpdate()
    }

    /** Lo que dicen las últimas palabras que escribiste: "es", "en" (las que son de los dos no cuentan). */
    private val langVotes = ArrayDeque<String>()

    /**
     * 🤖 Cambio de idioma solo: si tus últimas palabras son claramente del otro idioma
     * (2 seguidas), el teclado se cambia solo.
     */
    private fun autoLang(word: String) {
        if (!prefs.getBoolean(Prefs.AUTO_LANG, true) || isPassword || codeOn || hackerActive) return
        val w = word.lowercase().trim('\'', '’')
        if (w.length < 2) return
        val v = WordStore.engine?.langOf(w) ?: return
        langVotes.addLast(v)
        while (langVotes.size > 4) langVotes.removeFirst()
        val other = if (lang == "es") "en" else "es"
        val last2 = langVotes.takeLast(2)
        if (last2.size == 2 && last2.all { it == other }) setLang(other)
    }

    /** Cuántas cosas del día se veían la última vez (para iluminar la nueva). -1 = recién abierto. */
    private var wdShownCount = -1

    private fun refreshWorkday() {
        val panel = workdayPanel ?: return
        val events = WorkDay.day(prefs)
        val sum = WorkDay.summary(events)
        val rows = ArrayList<ListPanel.Row>()
        if (events.isNotEmpty()) {
            rows.add(ListPanel.Row(L.t("Hoy", "Today") + (if (sum.running) L.t(" · en curso", " · in progress") else ""),
                L.t("Total ${WorkDay.dur(sum.total)}\nEn el sitio ${WorkDay.dur(sum.site)} · Manejando ${WorkDay.dur(sum.driving)}",
                    "Total ${WorkDay.dur(sum.total)}\nOn site ${WorkDay.dur(sum.site)} · Driving ${WorkDay.dur(sum.driving)}") +
                    (if (WorkDay.overtime(sum.total) > 0) "\n" + L.t("⏰ Horas extra: ", "⏰ Overtime: ") + WorkDay.dur(WorkDay.overtime(sum.total)) else "") +
                    (if (sum.lunch > 0) "\n" + (if (sum.onLunch) L.t("🍔 Almorzando: ", "🍔 At lunch: ") else L.t("🍔 Almuerzo: ", "🍔 Lunch: ")) + WorkDay.dur(sum.lunch) +
                        (if (sum.lunchAuto) L.t(" (fijo, no marcado)", " (fixed, not logged)") else "") +
                        (if (WorkDay.lunchPaid) L.t(" · pagado", " · paid") else L.t(" · se descuentan ", " · deducted ") + WorkDay.dur(sum.deducted)) else "")))
        }
        // 💵 Cuánto llevas ganado esta semana
        val payCfg = Pay.config(prefs)
        if (payCfg.isSet) {
            val r = Pay.week(prefs, WorkDay.weekStart())
            rows.add(ListPanel.Row(L.t("💵 Semana", "💵 Week"),
                L.t("Bruto ${Pay.money(r.gross)} · te quedan aprox. ${Pay.money(r.net)}\nToca para ver el desglose",
                    "Gross ${Pay.money(r.gross)} · take-home approx. ${Pay.money(r.net)}\nTap to see the breakdown"),
                onTap = { showPay() }))
        }
        // ✅ Lo que acabas de anotar se ilumina con la luz (así sabes que sí quedó)
        if (events.size > wdShownCount && wdShownCount >= 0) {
            val newest = events.maxByOrNull { it.time }
            if (newest != null) panel.flashRow = rows.size + events.indexOf(newest)
        }
        wdShownCount = events.size
        handler.post { maybeCelebrate40() }
        for (e in events) {
            rows.add(ListPanel.Row(WorkStore.time(e.time), WorkDay.labelOf(e),
                onTap = { adjustEvent(e.time, e.kind) },
                onDelete = { WorkDay.remove(prefs, e.time); WorkdayCenter.refresh(this); refreshWorkday() }))
        }
        val next = WorkDay.nextKind(events)
        val kinds = WorkDay.enabledKinds()
        var order = listOf(next) + kinds.filter { it != next }
        // Almuerzo: mientras trabajas sale «🍔 Salí a almorzar»; si estás almorzando, «↩️ Regresé» va primero
        if (WorkDay.isRunning(events)) {
            order = if (WorkDay.onBreak(events)) listOf(WorkDay.BACK) + order
                else listOf(next, WorkDay.LUNCH) + order.drop(1)
        }
        val buttons = ArrayList<Pair<String, () -> Unit>>()
        for (k in order) buttons.add(WorkDay.label(k) to {
            WorkdayCenter.record(this, k) { refreshWorkday(); maybeAskSaveSite() }
            refreshWorkday()
            if (k == WorkDay.HOME) celebrateDay()
            if (k == WorkDay.START || (k == WorkDay.enabledKinds().first() && k != WorkDay.ARRIVE)) {
                askDriving()
                // 🧰 ¿Llevas todo? (la checklist de antes de salir)
                if (prefs.getBoolean(Prefs.CHECK_ON, true) && Todos.checkItems(prefs).isNotEmpty()) showMode(16)
            }
        })
        if (events.isNotEmpty()) {
            buttons.add(L.t("✍️ Escribir resumen", "✍️ Type summary") to {
                insertText(WorkDay.text(WorkDay.day(prefs), ticketLang() == "en"))
                showMode(0)
            })
        }
        buttons.add(L.t("🕐 Anotar con otra hora", "🕐 Log with another time") to { askLogAt() })
        buttons.add(L.t("📄 Hoja de horas (PDF)", "📄 Timesheet (PDF)") to { askTimesheet() })
        buttons.add(L.t("📅 Calendario", "📅 Calendar") to {
            showMode(0)
            try { startActivity(Intent(this, MonthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) } catch (_: Exception) { }
        })
        buttons.add(L.t("✏️ Editar botones", "✏️ Edit buttons") to {
            showMode(0)
            try {
                startActivity(Intent(this, WorkdayButtonsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            } catch (_: Exception) { }
        })
        val wk = WorkDay.weekTotal(prefs)
        val wkOt = WorkDay.weekOvertime(wk)
        panel.show(WorkDay.title() + "  ·  " + L.t("semana ", "week ") + WorkDay.dur(wk) + (if (wkOt > 0) L.t(" (+${WorkDay.dur(wkOt)} extra)", " (+${WorkDay.dur(wkOt)} OT)") else ""), rows,
            L.t("Toca los botones de abajo para anotar tu día: " + WorkDay.enabledKinds().joinToString(", ") { WorkDay.label(it) } + ". Si se te olvidó, toca la hora para cambiarla. Con ✏️ Editar botones les cambias el nombre o quitas los que no uses.",
                "Tap the buttons below to log your day: " + WorkDay.enabledKinds().joinToString(", ") { WorkDay.label(it) } + ". If you forgot, tap the time to change it. With ✏️ Edit buttons you can rename them or remove the ones you don't use."),
            buttons)
    }

    // ---------------------------------------------------------------- Modo manejando

    private fun today(): String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /** null = todavía no contestaste hoy; true/false = si vas manejando. */
    private fun drivingToday(): Boolean? {
        val v = prefs.getString(Prefs.DRIVING_DAY, "") ?: ""
        if (!v.startsWith(today() + ":")) return null
        return v.endsWith(":yes")
    }

    private fun setDriving(on: Boolean) {
        prefs.edit().putString(Prefs.DRIVING_DAY, today() + ":" + if (on) "yes" else "no").apply()
    }

    /** ¿Vas en camino ahora mismo? (después de Comencé o de Salí del sitio) */
    private fun traveling(): Boolean {
        val k = WorkDay.day(prefs).lastOrNull()?.kind
        return k == WorkDay.START || k == WorkDay.LEAVE
    }

    /** Pregunta una vez al día si vas manejando tú. */
    private fun askDriving() {
        val bar = infoBar ?: return
        infoRegen = { askDriving() }
        bar.show(L.t("🛻 ¿Vas manejando tú?", "🛻 Are you driving?"),
            L.t("Si manejas, el teclado te enseña botones grandes y la voz primero mientras vas en camino.",
                "If you drive, the keyboard shows big buttons and voice first while you're on the road."),
            listOf(
                L.t("Sí, yo manejo", "Yes, I'm driving") to { setDriving(true); closeInfo(); showMode(14) },
                L.t("No, voy de pasajero", "No, I'm a passenger") to { setDriving(false); closeInfo() },
            ))
    }

    /** Al abrir el teclado: si vas en camino y manejas, abre el modo manejando (o pregunta). */
    /** Llegaste a un lugar que no tienes guardado: pregunta si lo guardas en Mis sitios (una vez). */
    private var siteAsked = 0L
    private fun maybeAskSaveSite() {
        val n = SiteStore.getNew(prefs) ?: return
        if (n.time == siteAsked) return
        val bar = infoBar ?: return
        siteAsked = n.time
        bar.show(L.t("📍 Lugar nuevo", "📍 New place"),
            L.t("Llegaste a ${n.full.ifEmpty { n.short }} a las ${WorkStore.time(n.time)}. ¿Lo guardas en Mis sitios? Así la próxima vez lo reconozco solo.",
                "You arrived at ${n.full.ifEmpty { n.short }} at ${WorkStore.time(n.time)}. Save it in My sites? Next time I'll recognize it by myself."),
            listOf(
                L.t("💾 Guardar y ponerle nombre", "💾 Save and name it") to {
                    closeInfo()
                    try {
                        startActivity(Intent(this, SitesActivity::class.java).putExtra(SitesActivity.EXTRA_NEW, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    } catch (_: Exception) { }
                },
                L.t("Ahora no", "Not now") to { SiteStore.setNew(prefs, null); closeInfo() },
            ))
    }

    /** ¿El modo manejando está fijo hoy? (se apaga solo al día siguiente) */
    private fun driveSticky(): Boolean = prefs.getString(Prefs.DRIVE_STICKY, "") == today()

    private fun setDriveSticky(on: Boolean) {
        if (on == driveSticky()) return
        prefs.edit().putString(Prefs.DRIVE_STICKY, if (on) today() else "").apply()
    }

    private fun maybeDriving() {
        if (driveSticky()) { if (mode != 14) showMode(14); return }
        if (!traveling()) return
        val last = WorkDay.day(prefs).lastOrNull()?.time ?: return
        if (last == driveSkipEvent) return
        when (drivingToday()) {
            null -> askDriving()
            true -> showMode(14)
            false -> Unit
        }
    }

    // ---------------------------------------------------------------- Mensajes de estado

    private var statusEnglish = false

    /** Mensajes listos para el supervisor, con la hora y el sitio de tu jornada. */
    private fun startStatus(english: Boolean? = null) {
        showMode(0)
        val bar = infoBar ?: return
        val en = english ?: L.english
        statusEnglish = en
        val ev = WorkDay.day(prefs)
        val last = WorkDay.lastMain(ev)
        val site = ev.lastOrNull { it.kind == WorkDay.ARRIVE && it.site.isNotEmpty() }?.site ?: ""
        val now = System.currentTimeMillis()
        fun t(ms: Long) = WorkStore.time(ms)
        fun tx(es: String, eng: String) = if (en) eng else es
        val msgs = ArrayList<String>()
        if (last?.kind == WorkDay.ARRIVE) {
            msgs.add(if (site.isNotEmpty()) tx("Ya llegué a $site (${t(last.time)}).", "I arrived at $site (${t(last.time)}).")
                else tx("Ya llegué al sitio (${t(last.time)}).", "I arrived on site (${t(last.time)})."))
            msgs.add(tx("Ya estoy trabajando en " + site.ifEmpty { "el sitio" } + ". Les aviso cuando termine.",
                "I'm working at " + site.ifEmpty { "the site" } + " now. I'll let you know when I'm done."))
        }
        if (last?.kind == WorkDay.LEAVE || last?.kind == WorkDay.ARRIVE) {
            msgs.add(if (site.isNotEmpty()) tx("Terminé en $site, voy saliendo (${t(now)}).", "I finished at $site, heading out now (${t(now)}).")
                else tx("Terminé aquí, voy saliendo (${t(now)}).", "I'm done here, heading out now (${t(now)})."))
        }
        msgs.add(tx("Voy en camino.", "I'm on my way."))
        for (s in SiteStore.all(prefs).take(3)) msgs.add(tx("Voy en camino a ${s.name}.", "On my way to ${s.name}."))
        msgs.add(tx("Llego en 15 minutos.", "I'll be there in 15 minutes."))
        msgs.add(tx("Llego en 30 minutos.", "I'll be there in 30 minutes."))
        msgs.add(tx("Voy a llegar un poco tarde, llego como a las ${t(now + 20 * 60_000L)}.", "I'm running a little late, I'll be there around ${t(now + 20 * 60_000L)}."))
        msgs.add(tx("Voy de regreso a casa.", "I'm heading home."))
        val buttons = ArrayList<Pair<String, () -> Unit>>()
        buttons.add((if (en) "🇪🇸 Español" else "🇺🇸 English") to { startStatus(!en) })
        // 🛻 Al mandar (o cerrar) el mensaje de estado, si ibas en modo manejando, sigues en él
        for (m in msgs.distinct()) buttons.add(m to { insertText(m); closeInfo(); if (driveSticky()) showMode(14) })
        buttons.add(L.t("Cerrar", "Close") to { closeInfo(); if (driveSticky()) showMode(14) })
        infoRegen = { startStatus(english) }
        bar.show(L.t("📲 Mensajes rápidos", "📲 Quick messages"), "", buttons)
    }

    // ---------------------------------------------------------------- Resumen de la semana

    private fun weekText(english: Boolean): String {
        val start = WorkDay.weekStart()
        val dayFmt = java.text.SimpleDateFormat("EEEE MM/dd", if (english) java.util.Locale.US else java.util.Locale("es"))
        val sb = StringBuilder()
        sb.append(if (english) "Week of " else "Semana del ").append(java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US).format(start)).append("\n\n")
        var total = 0L
        var site = 0L
        for ((day, ev) in WorkDay.byDay(prefs)) {
            if (day < start) continue
            val sum = WorkDay.summary(ev, if (WorkStore.sameDay(day, System.currentTimeMillis())) System.currentTimeMillis() else ev.last().time)
            total += sum.total
            site += sum.site
            val sites = ev.map { it.site }.filter { it.isNotEmpty() }.distinct().joinToString(", ")
            sb.append(dayFmt.format(day).replaceFirstChar { it.uppercase() }).append(": ").append(WorkDay.dur(sum.total))
                .append(if (english) " (on site " else " (en el sitio ").append(WorkDay.dur(sum.site)).append(")")
                .append(if (sites.isNotEmpty()) " · $sites" else "").append("\n")
        }
        sb.append("\n").append(if (english) "Total: " else "Total: ").append(WorkDay.dur(total))
            .append(if (english) " (on site " else " (en el sitio ").append(WorkDay.dur(site)).append(")")
        val tickets = WorkStore.tickets(prefs).filter { it.time >= start }.map { it.num }.sorted()
        if (tickets.isNotEmpty()) sb.append("\n").append(if (english) "Tickets: " else "Tickets: ").append(tickets.joinToString(", "))
        return sb.toString()
    }

    /** El idioma en que la IA te contesta (el del teclado). */
    private fun aiLang() = if (L.english) "inglés (English)" else "español"

    // ---------------------------------------------------------------- ✅ Pendientes y 🧰 checklist

    private fun refreshTodos() {
        val panel = todoPanel ?: return
        val list = Todos.all(prefs).sortedWith(compareBy({ it.done }, { if (it.due > 0) it.due else Long.MAX_VALUE }))
        val rows = list.map { i ->
            ListPanel.Row(if (i.done) "✓" else "○", i.text + Todos.whenText(i).let { if (it.isEmpty() || i.done) "" else "\n$it" },
                onTap = { Todos.toggle(this, i.id); refreshTodos() },
                onCopy = if (i.done) null else ({ askTodoWhen(i.id) }),
                onDelete = { Todos.remove(this, i.id); refreshTodos() },
                copyLabel = "⏰")
        }
        panel.show(L.t("✅ Pendientes", "✅ To-dos") + if (list.any { !it.done }) " (${list.count { !it.done }})" else "", rows,
            L.t("Apunta lo que no se te puede olvidar (ej. «llevar la escalera mañana»). Toca uno para marcarlo hecho.",
                "Write down what you can't forget (e.g. «bring the ladder tomorrow»). Tap one to mark it done."),
            listOf(L.t("➕ Pendiente nuevo", "➕ New to-do") to {
                startSearch(gif = false, custom = "todo", customHint = "Ej. llevar la escalera mañana" to "E.g. bring the ladder tomorrow",
                    info = L.t("Escríbelo y toca Enter. Después eliges cuándo te aviso.", "Type it and tap Enter. Then pick when to remind you."))
            }))
    }

    /** ¿Cuándo te aviso? */
    private fun askTodoWhen(id: Long) {
        val bar = infoBar ?: return
        val now = java.util.Calendar.getInstance()
        fun at(daysAhead: Int, h: Int): Long = (now.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, daysAhead); set(java.util.Calendar.HOUR_OF_DAY, h)
            set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
        val set = { due: Long, site: String -> Todos.setDue(this, id, due, site); closeInfo(); refreshTodos() }
        val opts = ArrayList<Pair<String, () -> Unit>>()
        opts.add(L.t("En 1 hora", "In 1 hour") to { set(System.currentTimeMillis() + 3600_000L, "") })
        if (now.get(java.util.Calendar.HOUR_OF_DAY) < 19) opts.add(L.t("Hoy 7 PM", "Today 7 PM") to { set(at(0, 19), "") })
        opts.add(L.t("Mañana 6 AM", "Tomorrow 6 AM") to { set(at(1, 6), "") })
        opts.add(L.t("Mañana 7 AM", "Tomorrow 7 AM") to { set(at(1, 7), "") })
        for (s in SiteStore.all(prefs).take(6)) opts.add(L.t("📍 Al llegar a ", "📍 At ") + s.name to { set(0L, s.name) })
        opts.add(L.t("Sin aviso", "No reminder") to { set(0L, "") })
        infoRegen = { askTodoWhen(id) }
        bar.show(L.t("⏰ ¿Cuándo te aviso?", "⏰ When should I remind you?"), "", opts)
    }

    private fun refreshChecklist() {
        val panel = checkPanel ?: return
        val items = Todos.checkItems(prefs)
        val done = Todos.checked(prefs)
        val rows = items.map { n ->
            ListPanel.Row(if (n in done) "✅" else "⬜", n,
                onTap = { Todos.toggleCheck(prefs, n); refreshChecklist() },
                onDelete = { Todos.setCheckItems(prefs, items.filter { it != n }); refreshChecklist() })
        }
        val all = items.isNotEmpty() && items.all { it in done }
        panel.show(L.t("🧰 ¿Llevas todo?", "🧰 Got everything?") + " (${items.count { it in done }}/${items.size})" + if (all) " 👍" else "", rows,
            L.t("Toca lo que ya llevas. Sale sola al tocar «Comencé». Se reinicia cada día.",
                "Tap what you already have. It shows up when you tap «Started». It resets every day."),
            listOf(L.t("➕ Agregar", "➕ Add") to {
                startSearch(gif = false, custom = "check", customHint = "Ej. Escalera" to "E.g. Ladder",
                    info = L.t("Escríbelo y toca Enter.", "Type it and tap Enter."))
            }, L.t("Listo", "Done") to { showMode(0) }))
    }

    // ---------------------------------------------------------------- 📏 Convertidor

    private var convertUnit: Convert.Unit? = null
    private var convertCable = false
    private var convertRun = false

    // ---------------------------------------------------------------- 🔎 Buscar en Google

    /** Abre la barra para escribir lo que quieres buscar. Si seleccionaste texto, lo busca de una vez. */
    private fun startGoogle() {
        val sel = try { currentInputConnection?.getSelectedText(0)?.toString()?.trim() } catch (_: Exception) { null }
        if (!sel.isNullOrEmpty() && sel.length <= 300) { googleSearch(sel); return }
        startSearch(gif = false, custom = "google", customHint = "Buscar en Google…" to "Search Google…",
            info = L.t("Escribe y toca Enter: se abre Google con tu búsqueda.", "Type and tap Enter: Google opens with your search."))
    }

    private fun googleSearch(q: String) {
        val url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(q, "UTF-8")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(this, Emo.strip(L.t("No encontré un navegador.", "No browser found.")), Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- 📍 Recordar al llegar

    /** «Cuando llegue a este sitio, recuérdame…»: escribes qué y eliges el sitio. */
    private fun startRemind() {
        showMode(0)
        if (SiteStore.all(prefs).none { !it.isStart }) {
            val bar = infoBar ?: return
            infoRegen = { startRemind() }
            bar.show(L.t("📍 Recordar al llegar", "📍 Remind on arrival"),
                L.t("Primero guarda tus sitios en «Mis sitios». Después escribes qué recordar y eliges el sitio: te aviso solo cuando llegues.",
                    "First save your places in «My sites». Then type what to remember and pick the place: I'll remind you when you arrive."),
                listOf(L.t("Abrir Mis sitios", "Open My sites") to { closeInfo(); try { startActivity(Intent(this, SitesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) { } },
                    L.t("Cerrar", "Close") to { closeInfo() }))
            return
        }
        startSearch(gif = false, custom = "remind", customHint = "Ej. Llevar la escalera" to "E.g. Bring the ladder",
            info = L.t("¿Qué quieres que te recuerde? Después eliges el sitio.", "What should I remind you? Then pick the place."))
    }

    private fun pickRemindSite(id: Long, text: String) {
        val bar = infoBar ?: return
        val sites = SiteStore.all(prefs).filter { !it.isStart }
        infoRegen = { pickRemindSite(id, text) }
        bar.show(L.t("📍 ¿Al llegar a dónde?", "📍 When I get where?"), "«$text»",
            sites.take(10).map { s -> ("📍 " + s.name) to {
                Todos.setDue(this, id, 0L, s.name); closeInfo()
                Toast.makeText(this, Emo.strip(L.t("Listo: te aviso al llegar a ${s.name}.", "Done: I'll remind you at ${s.name}.")), Toast.LENGTH_SHORT).show()
            } } + listOf(L.t("Cancelar", "Cancel") to { Todos.remove(this, id); closeInfo() }))
    }

    // ---------------------------------------------------------------- ↩️ Deshacer

    private class UndoSnap(val text: String, val sel: Int, val at: Long)
    private var undoSnap: UndoSnap? = null

    /** Guarda cómo estaba el texto antes de algo grande (pegar, la IA, traducir, etiquetas…). */
    private fun snapUndo() {
        if (searching != 0 || translating || codeAsking) return
        val ic = currentInputConnection ?: return
        val et = try { ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 20000; hintMaxLines = 2000 }, 0) } catch (_: Exception) { null } ?: return
        if (et.startOffset != 0 || et.partialStartOffset > 0) return
        val t = et.text?.toString() ?: return
        if (t.length >= 20000) return
        undoSnap = UndoSnap(t, et.selectionEnd.coerceIn(0, t.length), System.currentTimeMillis())
    }

    /** ↩️ Deshacer: vuelve el texto a como estaba antes; si no hay nada guardado, le pide a la app que deshaga. */
    private fun doUndo() {
        showMode(0)
        val ic = currentInputConnection ?: return
        val snap = undoSnap
        val et = try { ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = 20000; hintMaxLines = 2000 }, 0) } catch (_: Exception) { null }
        val cur = et?.text?.toString()
        if (snap != null && cur != null && et.startOffset == 0 && cur != snap.text && System.currentTimeMillis() - snap.at < 10 * 60_000L) {
            undoSnap = UndoSnap(cur, et.selectionEnd.coerceIn(0, cur.length), System.currentTimeMillis()) // otro toque lo rehace
            ic.beginBatchEdit()
            ic.setSelection(0, cur.length)
            ic.commitText(snap.text, 1)
            ic.setSelection(snap.sel, snap.sel)
            ic.endBatchEdit()
            Toast.makeText(this, Emo.strip(L.t("↩️ Deshecho (toca otra vez para rehacer)", "↩️ Undone (tap again to redo)")), Toast.LENGTH_SHORT).show()
        } else {
            undoSnap = null
            val ok = try { ic.performContextMenuAction(android.R.id.undo) } catch (_: Exception) { false }
            if (!ok) {
                val now = android.os.SystemClock.uptimeMillis()
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
            }
        }
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        scheduleUpdate()
    }

    private fun startConvert() {
        showMode(0)
        val bar = infoBar ?: return
        val opts = ArrayList<Pair<String, () -> Unit>>()
        opts.add(L.t("🧭 ¿Cuántos pies por línea?", "🧭 How many feet per line?") to {
            closeInfo(); convertCable = false; convertRun = true; convertUnit = null
            startSearch(gif = false, custom = "convert", customHint = "Ej. 80 pasos techo 10" to "E.g. 80 steps ceiling 10",
                info = L.t("Di el camino del rack al equipo: en pies («120»), pasos («80 pasos»), placas del techo («40 placas») o metros. Pon el techo («techo 12») y cuántas líneas («x 6»).",
                    "Give the path from the rack to the device: in feet («120»), steps («80 steps»), ceiling tiles («40 tiles») or meters. Add the ceiling («ceiling 12») and how many lines («x 6»)."))
        })
        opts.add(L.t("🧵 Total de cable", "🧵 Cable total") to {
            closeInfo(); convertCable = true; convertRun = false; convertUnit = null
            startSearch(gif = false, custom = "convert", customHint = "Ej. 120 x 6 + 10%" to "E.g. 120 x 6 + 10%",
                info = L.t("Corridas en pies: «120 x 6» (6 de 120), «85, 90, 120» o con extra «+ 10%».", "Runs in feet: «120 x 6» (6 of 120), «85, 90, 120» or with extra «+ 10%»."))
        })
        for (u in Convert.UNITS) opts.add(L.t(u.es, u.en) to {
            closeInfo(); convertCable = false; convertRun = false; convertUnit = u
            startSearch(gif = false, custom = "convert", customHint = L.t("Número en ", "Number in ") + Convert.from(u) to "Number in " + Convert.from(u),
                info = L.t("Escribe el número. Enter lo escribe en tu texto.", "Type the number. Enter types it in your text."))
        })
        infoRegen = { startConvert() }
        bar.show(L.t("📏 Convertidor", "📏 Converter"), L.t("¿Qué quieres convertir?", "What do you want to convert?"), opts + listOf(L.t("Cerrar", "Close") to { closeInfo() }))
    }

    /** El resultado mientras escribes (o para escribirlo en tu texto). */
    private fun convertResult(text: String, forTyping: Boolean = false): String {
        val q = text.trim().replace(",", if (convertCable || convertRun) "," else ".")
        if (q.isEmpty()) return if (forTyping) "" else L.t("Escribe un número…", "Type a number…")
        if (convertRun) return Convert.runText(q, forTyping) ?: if (forTyping) "" else L.t("Ejemplo: 80 pasos techo 10", "Example: 80 steps ceiling 10")
        if (convertCable) return Convert.cable(q) ?: if (forTyping) "" else L.t("No entendí. Ejemplo: 120 x 6 + 10%", "I didn't get it. Example: 120 x 6 + 10%")
        val u = convertUnit ?: return ""
        val v = q.toDoubleOrNull() ?: return if (forTyping) "" else L.t("Solo números (ej. 25.5)", "Numbers only (e.g. 25.5)")
        val out = Convert.fmt(v) + " " + Convert.from(u) + " = " + Convert.fmt(u.f(v)) + " " + u.other
        return if (forTyping) Convert.fmt(v) + " " + Convert.from(u) + " (" + Convert.fmt(u.f(v)) + " " + u.other + ")" else "= " + out
    }

    /** 🕐 Anotar algo de hoy con la hora que tú escribas (ej. «Llegué a casa» a las 5:32pm). */
    private fun askLogAt() {
        showMode(0)
        val bar = infoBar ?: return
        val kinds = WorkDay.enabledKinds() + listOf(WorkDay.LUNCH, WorkDay.BACK)
        infoRegen = { askLogAt() }
        bar.show(L.t("🕐 ¿Qué quieres anotar?", "🕐 What do you want to log?"),
            L.t("Elige y luego escribe la hora en que pasó (ej. 5:32pm).", "Pick one and then type the time it happened (e.g. 5:32pm)."),
            kinds.map { k -> WorkDay.label(k) to { closeInfo(); TimeSetActivity.open(this, kind = k) } } +
                listOf(L.t("Cerrar", "Close") to { closeInfo() }))
    }

    /** 🔍 Buscar en tickets, libreta y portapapeles. */
    private fun openFind() {
        showMode(0)
        try { startActivity(Intent(this, FindActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) } catch (_: Exception) { }
    }

    /** 💵 El desglose del pago de la semana. */
    private fun showPay(weeksAgo: Int = 0) {
        showMode(0)
        val bar = infoBar ?: return
        val r = Pay.week(prefs, WorkDay.weekStart(weeksAgo))
        val thisWeek = weeksAgo == 0
        infoRegen = { showPay(weeksAgo) }
        bar.show(if (thisWeek) L.t("💵 Esta semana (aprox.)", "💵 This week (approx.)") else L.t("💵 Semana pasada (aprox.)", "💵 Last week (approx.)"),
            Pay.text(r, Pay.config(prefs)),
            listOf(
                (if (thisWeek) L.t("Semana pasada", "Last week") else L.t("Esta semana", "This week")) to {
                    showPay(if (thisWeek) 1 else 0)
                },
                L.t("Cerrar", "Close") to { closeInfo() },
            ))
    }

    /** 📄 ¿De qué semana quieres la hoja de horas? */
    private fun askTimesheet() {
        showMode(0)
        val bar = infoBar ?: return
        val en = ticketLang() == "en"
        infoRegen = { askTimesheet() }
        bar.show(L.t("📄 Hoja de horas", "📄 Timesheet"),
            L.t("Se crea un PDF con tus días, horas, sitios, almuerzo y horas extra, listo para mandar por WhatsApp o correo.",
                "A PDF is made with your days, hours, sites, lunch and overtime, ready to send by WhatsApp or email."),
            listOf(
                L.t("Esta semana", "This week") to { closeInfo(); sendTimesheet(WorkDay.weekStart(), en) },
                L.t("Semana pasada", "Last week") to { closeInfo(); sendTimesheet(WorkDay.weekStart(1), en) },
                (if (en) "En español" else "In English") to { closeInfo(); sendTimesheet(WorkDay.weekStart(), !en) },
                L.t("Cerrar", "Close") to { closeInfo() },
            ))
    }

    private fun sendTimesheet(weekStart: Long, english: Boolean) {
        val app = applicationContext
        // Al cambiar de horario (DST) la semana pasada puede quedar a las 23:00: se ajusta al día
        val c = java.util.Calendar.getInstance().apply {
            timeInMillis = weekStart + 6 * 3600 * 1000L
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }
        shotIo.execute {
            val name = try { Timesheet.build(app, c.timeInMillis, english) } catch (_: Exception) { null }
            handler.post {
                if (name == null) Toast.makeText(this, Emo.strip(L.t("No se pudo crear el PDF.", "Couldn't create the PDF.")), Toast.LENGTH_SHORT).show()
                else Timesheet.share(this, name, english)
            }
        }
    }

    private fun startWeek(outLang: String) {
        showMode(0)
        val bar = infoBar ?: return
        val en = outLang == "en"
        val text = weekText(en)
        infoRegen = { startWeek(outLang) }
        bar.show(L.t("📊 Resumen de la semana", "📊 Week summary"), text, listOf(
            L.t("Escribir", "Type it") to { insertText(text); closeInfo() },
            L.t("Copiar", "Copy") to { aiCopy(text) },
            L.t("📄 PDF", "📄 PDF") to { closeInfo(); sendTimesheet(WorkDay.weekStart(), en) },
            (if (en) "En español" else "In English") to { startWeek(if (en) "es" else "en") },
            L.t("Cerrar", "Close") to { closeInfo() },
        ))
    }

    // ---------------------------------------------------------------- Materiales

    private fun refreshMaterials() {
        val panel = materialsPanel ?: return
        val used = Materials.today(prefs)
        val custom = Materials.custom(prefs).toSet()
        // Los que ya usaste hoy van primero
        val names = used.keys.toList() + Materials.all(prefs).filter { it !in used }
        val rows = names.map { n ->
            val q = used[n] ?: 0
            ListPanel.Row(if (q > 0) "✓ x$q" else " ", n,
                onTap = { adjustMaterial(n) },
                onDelete = if (q > 0) ({ Materials.set(prefs, n, 0); refreshMaterials() })
                    else if (n in custom) ({ Materials.removeCustom(prefs, n); refreshMaterials() }) else null)
        }
        val buttons = ArrayList<Pair<String, () -> Unit>>()
        if (used.isNotEmpty()) buttons.add(L.t("✍️ Escribir lista", "✍️ Type list") to { insertText(Materials.text(prefs)); showMode(0) })
        buttons.add(L.t("➕ Material nuevo", "➕ New material") to { startSearch(gif = false, material = true) })
        if (used.isNotEmpty()) buttons.add(L.t("Limpiar", "Clear") to { Materials.clear(prefs); refreshMaterials() })
        panel.show(L.t("🧰 Materiales de hoy", "🧰 Today's materials") + if (used.isEmpty()) "" else " (${used.size})", rows,
            L.t("Toca un material para poner la cantidad.", "Tap a material to set the quantity."), buttons)
    }

    private fun adjustMaterial(name: String) {
        val bar = infoBar ?: return
        fun showBar() {
            val q = Materials.today(prefs)[name] ?: 0
            val step: (Int) -> Unit = { d -> Materials.change(prefs, name, d); refreshMaterials(); showBar() }
            bar.show("$name: $q", L.t("Toca para sumar o restar.", "Tap to add or subtract."), listOf(
                L.t("Listo", "Done") to { closeInfo() },
                "+1" to { step(1) }, "+5" to { step(5) }, "+10" to { step(10) }, "+50" to { step(50) }, "+100" to { step(100) },
                "−1" to { step(-1) }, "−10" to { step(-10) },
                L.t("Poner en 0", "Set to 0") to { Materials.set(prefs, name, 0); refreshMaterials(); showBar() },
            ))
        }
        showBar()
    }

    // ---------------------------------------------------------------- ⌨️ No bajar el teclado solo

    /** true = el siguiente «bajar el teclado» lo pediste tú (botón Bajar, flecha atrás). */
    private var userHide = false

    /** Bajar el teclado porque tú lo pediste. */
    private fun hideByUser() { userHide = true; requestHideSelf(0) }

    /** ¿Hay un cuadro de texto conectado al teclado ahora mismo? */
    private var inputActive = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        inputActive = true
    }

    override fun onFinishInput() {
        super.onFinishInput()
        inputActive = false
    }

    // ---------------------------------------------------------------- Respuestas rápidas

    /** Copias el mensaje que te llegó y la IA te da 3 respuestas: corta, formal y en el otro idioma. */
    private fun startReplies() {
        if (isPassword) return
        showMode(0)
        val title = L.t("💬 Respuestas con IA", "💬 AI replies")
        val ic = currentInputConnection
        val sel = try { ic?.getSelectedText(0)?.toString()?.trim() } catch (_: Exception) { null }
        val msg = if (!sel.isNullOrEmpty()) sel else try {
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
        } catch (_: Exception) { null }
        val bar = infoBar ?: return
        if (msg.isNullOrEmpty()) {
            bar.show(title, L.t("Mantén presionado el mensaje que te llegó y toca Copiar. Después toca este botón.",
                "Long-press the message you got and tap Copy. Then tap this button."), listOf(L.t("Entendido", "Got it") to { closeInfo() }))
            return
        }
        val key = aiKey(title) ?: return
        val my = ++aiSeq
        bar.show(title, L.t("Pensando respuestas…", "Thinking of replies…"), listOf(L.t("Cancelar", "Cancel") to { aiSeq++; closeInfo() }))
        // Cuenta los segundos, para que veas que sigue trabajando
        val startT = SystemClock.uptimeMillis()
        var waiting = true
        val tick = object : Runnable {
            override fun run() {
                if (!waiting || my != aiSeq || infoBar?.visibility != View.VISIBLE) return
                val sec = (SystemClock.uptimeMillis() - startT) / 1000
                bar.show(title, L.t("Pensando respuestas ($sec s)…", "Thinking of replies ($sec s)…"),
                    listOf(L.t("Cancelar", "Cancel") to { aiSeq++; closeInfo() }))
                handler.postDelayed(this, 1000)
            }
        }
        handler.postDelayed(tick, 1000)
        val prompt = "Someone sent this message to a low-voltage field technician (he works with coworkers and supervisors):\n\"\"\"\n" + msg +
            "\n\"\"\"\nWrite 3 possible replies he could send, as himself, first person:\n" +
            "1) SHORT: very short and friendly, same language as the message.\n" +
            "2) FORMAL: professional and clear, same language as the message.\n" +
            "3) OTHER: professional, but in the other language (Spanish if the message is in English, English if it is in Spanish).\n" +
            "Do not invent facts; if something is unknown, keep it general. No markdown.\n" +
            "Answer exactly in this format:\nSHORT: ...\nFORMAL: ...\nOTHER: ..."
        CloudText.ask(prompt, key) { list, err ->
            waiting = false
            if (my != aiSeq) return@ask
            val raw = list?.firstOrNull()?.replace("**", "")?.trim()
            if (raw.isNullOrEmpty()) {
                bar.show(title, err ?: L.t("No se pudo.", "Couldn't do it."), listOf(L.t("Reintentar", "Retry") to { startReplies() }, L.t("Cerrar", "Close") to { closeInfo() }))
                return@ask
            }
            fun part(tag: String, nextTags: List<String>): String {
                val i = raw.indexOf("$tag:")
                if (i < 0) return ""
                var end = raw.length
                for (n in nextTags) { val j = raw.indexOf("$n:", i + 1); if (j > 0 && j < end) end = j }
                return raw.substring(i + tag.length + 1, end).trim()
            }
            val short = part("SHORT", listOf("FORMAL", "OTHER"))
            val formal = part("FORMAL", listOf("SHORT", "OTHER"))
            val other = part("OTHER", listOf("SHORT", "FORMAL"))
            val opts = listOf(L.t("1 Corta", "1 Short") to short, L.t("2 Formal", "2 Formal") to formal, L.t("3 Otro idioma", "3 Other language") to other)
                .filter { it.second.isNotEmpty() }
            if (opts.isEmpty()) { bar.show(title, raw, listOf(L.t("Copiar", "Copy") to { aiCopy(raw) }, L.t("Cerrar", "Close") to { closeInfo() })); return@ask }
            val body = opts.joinToString("\n\n") { "${it.first}:\n${it.second}" }
            val buttons = ArrayList<Pair<String, () -> Unit>>()
            for (o in opts) buttons.add(o.first to { insertText(o.second); closeInfo() })
            buttons.add(L.t("Cerrar", "Close") to { closeInfo() })
            bar.show(title, body, buttons)
        }
    }

    /** Cambiar la hora de un evento (si se te olvidó tocarlo a tiempo). */
    private fun adjustEvent(time: Long, kind: String) {
        val bar = infoBar ?: return
        var t = time
        fun showBar() {
            infoRegen = { showBar() }
            val buttons = arrayListOf<Pair<String, () -> Unit>>(
                L.t("Listo", "Done") to { closeInfo() },
                L.t("🕐 Escribir la hora", "🕐 Type the time") to { closeInfo(); showMode(0); TimeSetActivity.open(this, eventTime = t) },
                "−15 min" to { t = WorkDay.shift(prefs, t, -15); WorkdayCenter.refresh(this); refreshWorkday(); showBar() },
                "−5 min" to { t = WorkDay.shift(prefs, t, -5); WorkdayCenter.refresh(this); refreshWorkday(); showBar() },
                "+5 min" to { t = WorkDay.shift(prefs, t, 5); WorkdayCenter.refresh(this); refreshWorkday(); showBar() },
                "+15 min" to { t = WorkDay.shift(prefs, t, 15); WorkdayCenter.refresh(this); refreshWorkday(); showBar() },
            )
            // En "Llegué" y "Salí" puedes elegir el sitio a mano
            if (kind == WorkDay.ARRIVE || kind == WorkDay.LEAVE) {
                for (site in SiteStore.all(prefs)) buttons.add("📍 " + site.name to {
                    WorkDay.setSite(prefs, t, site.name); WorkdayCenter.refresh(this); refreshWorkday(); closeInfo()
                })
            }
            bar.show(WorkDay.label(kind) + ": " + WorkStore.time(t),
                L.t("Cambia la hora si se te olvidó tocarlo a tiempo" + (if (kind == WorkDay.ARRIVE || kind == WorkDay.LEAVE) ", o elige el sitio." else "."),
                    "Change the time if you forgot to tap it on time" + (if (kind == WorkDay.ARRIVE || kind == WorkDay.LEAVE) ", or pick the site." else ".")),
                buttons)
        }
        showBar()
    }

    // ---------------------------------------------------------------- Hora, fecha y lugar

    private fun showStamp() {
        showMode(0)
        val bar = infoBar ?: return
        val now = System.currentTimeMillis()
        val time = WorkStore.time(now)
        val en = L.english
        val date = if (en) java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.US).format(now)
            else java.text.SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy", java.util.Locale("es")).format(now)
        val put: (String) -> Unit = { t -> insertText(t); closeInfo() }
        infoRegen = { showStamp() }
        bar.show(L.t("🕐 Hora, fecha y lugar", "🕐 Time, date and place"), "", listOf(
            time to { put(time) },
            L.t("Llegué a las $time", "Arrived at $time") to { put(L.t("Llegué a las $time", "Arrived at $time")) },
            L.t("Salí a las $time", "Departed at $time") to { put(L.t("Salí a las $time", "Departed at $time")) },
            date to { put(date) },
            WorkStore.ticketDate(now) to { put(WorkStore.ticketDate(now)) },
            L.t("📍 Dónde estoy", "📍 Where I am") to { insertPlace() },
            L.t("Cerrar", "Close") to { closeInfo() },
        ))
    }

    /** Escribe la dirección donde estás (pide permiso la primera vez). */
    @android.annotation.SuppressLint("MissingPermission")
    private fun insertPlace() {
        val bar = infoBar ?: return
        val title = L.t("📍 Dónde estoy", "📍 Where I am")
        if (!hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) && !hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
            bar.show(title, L.t("Dale permiso de ubicación a Best Keyboard y vuelve a tocar «Dónde estoy».",
                "Allow location for Best Keyboard and tap «Where I am» again."), listOf(L.t("Dar permiso", "Allow") to {
                closeInfo(); askPermission(MainActivity.EXTRA_ASK_LOCATION) }, L.t("Cerrar", "Close") to { closeInfo() }))
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager ?: return
        bar.show(title, L.t("Buscando dónde estás…", "Finding where you are…"), listOf(L.t("Cancelar", "Cancel") to { closeInfo() }))
        val my = ++aiSeq
        val got: (android.location.Location?) -> Unit = { loc ->
            if (my == aiSeq) {
                if (loc == null) bar.show(title, L.t("No pude saber dónde estás. Prende la ubicación del teléfono.", "Couldn't get your location. Turn on the phone's location."),
                    listOf(L.t("Cerrar", "Close") to { closeInfo() }))
                else Thread {
                    val text = try {
                        @Suppress("DEPRECATION")
                        android.location.Geocoder(this, if (L.english) java.util.Locale.US else java.util.Locale("es"))
                            .getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.getAddressLine(0)
                    } catch (_: Exception) { null } ?: String.format(java.util.Locale.US, "%.5f, %.5f", loc.latitude, loc.longitude)
                    handler.post {
                        if (my != aiSeq) return@post
                        bar.show(title, text, listOf(
                            L.t("Escribir", "Type it") to { insertText(text); closeInfo() },
                            L.t("Copiar", "Copy") to { aiCopy(text) },
                            L.t("Cerrar", "Close") to { closeInfo() },
                        ))
                    }
                }.start()
            }
        }
        val provider = when {
            lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) -> android.location.LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) -> android.location.LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) { got(null); return }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, mainExecutor) { loc -> got(loc ?: lm.getLastKnownLocation(provider)) }
            } else {
                got(lm.getLastKnownLocation(provider))
            }
        } catch (_: Exception) { got(null) }
    }

    // ---------------------------------------------------------------- Palabra de la semana

    private fun showWord() {
        showMode(0)
        val w = WeeklyWord.current()
        infoRegen = { showWord() }
        infoBar?.show(L.t("📖 Palabra de la semana", "📖 Word of the week"),
            "${w.title()}\n${w.meaningNow()}\n«${w.exampleNow()}»\n\n" +
                L.t("Reto: úsala en al menos 3 conversaciones esta semana.", "Challenge: use it in at least 3 conversations this week."),
            listOf(
                "🔊 " + w.en to { Speak.say(this, w.en, english = true) },
                "🔊 " + w.word to { Speak.say(this, w.word, english = false) },
                L.t("🔊 Ejemplo", "🔊 Example") to { Speak.say(this, w.exampleNow(), english = L.english) },
                L.t("Escribirla", "Type it") to { insertText(w.typed()); closeInfo() },
                L.t("Cerrar", "Close") to { closeInfo() }))
    }

    /** La primera vez que abres el teclado en la semana, te enseña la palabra nueva. */
    private fun maybeShowWord() {
        if (!prefs.getBoolean(Prefs.WORD_WEEK, true)) return
        val idx = WeeklyWord.weekIndex()
        if (prefs.getInt(Prefs.WORD_SHOWN, -1) == idx) return
        if (System.currentTimeMillis() < WeeklyWord.weekStart()) return
        prefs.edit().putInt(Prefs.WORD_SHOWN, idx).apply()
        handler.postDelayed({ if (isInputViewShown && mode == 0) showWord() }, 600)
    }

    // ---------------------------------------------------------------- Teclado flotante

    private var floating = false
    private var floatFrame: FrameLayout? = null
    private var floatRoot: LinearLayout? = null
    private var floatHandle: View? = null

    private fun setHeight(v: View?, h: Int, bottom: Boolean = true) {
        val lp = v?.layoutParams ?: return
        lp.height = h
        if (bottom) when (lp) {
            is LinearLayout.LayoutParams -> lp.gravity = android.view.Gravity.BOTTOM
            is FrameLayout.LayoutParams -> lp.gravity = android.view.Gravity.BOTTOM
        }
        v.layoutParams = lp
    }

    /** Prende o apaga el teclado flotante (una ventanita que puedes mover). */
    private fun applyFloat() {
        val on = prefs.getBoolean(Prefs.FLOAT, false)
        floating = on
        kv?.floatOn = on
        val frame = floatFrame ?: return
        val root = floatRoot ?: return
        val w = window?.window ?: return
        val h = if (on) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        try {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h)
            setHeight(w.findViewById(android.R.id.inputArea), h)
            setHeight(frame, h)
        } catch (_: Exception) { }
        floatHandle?.visibility = if (on) View.VISIBLE else View.GONE
        val dm = resources.displayMetrics
        if (on) {
            val kw = (minOf(dm.widthPixels, dm.heightPixels) * 0.9f).toInt().coerceAtMost(dm.widthPixels)
            val lp = FrameLayout.LayoutParams(kw, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.TOP or android.view.Gravity.START)
            lp.leftMargin = prefs.getInt(Prefs.FLOAT_X, (dm.widthPixels - kw) / 2)
            lp.topMargin = prefs.getInt(Prefs.FLOAT_Y, -1)
            root.layoutParams = lp
            val bg = android.graphics.drawable.GradientDrawable()
            bg.setColor(kv?.palette?.bg ?: 0xFF000000.toInt())
            bg.cornerRadius = 18f * dm.density
            root.background = bg
            root.clipToOutline = true
            root.elevation = 10f * dm.density
            frame.setBackgroundColor(0)
        } else {
            root.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, android.view.Gravity.BOTTOM)
            root.background = null
            root.clipToOutline = false
            root.elevation = 0f
        }
        frame.requestLayout()
    }

    /** Que la ventanita no se salga de la pantalla. */
    private fun clampFloat(save: Boolean) {
        if (!floating) return
        val frame = floatFrame ?: return
        val root = floatRoot ?: return
        val lp = root.layoutParams as? FrameLayout.LayoutParams ?: return
        if (frame.height == 0 || root.height == 0) return
        val margin = (12 * resources.displayMetrics.density).toInt()
        var y = lp.topMargin
        if (y < 0) y = frame.height - root.height - margin * 3
        val nx = lp.leftMargin.coerceIn(0, maxOf(0, frame.width - root.width))
        val ny = y.coerceIn(0, maxOf(0, frame.height - root.height))
        if (nx != lp.leftMargin || ny != lp.topMargin) {
            lp.leftMargin = nx
            lp.topMargin = ny
            root.layoutParams = lp
        }
        if (save) prefs.edit().putInt(Prefs.FLOAT_X, nx).putInt(Prefs.FLOAT_Y, ny).apply()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun makeFloatHandle(): View {
        val dm = resources.displayMetrics
        fun dp(v: Float) = (v * dm.density).toInt()
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        val dock = IconView(this, Icons.FLOAT, 6f)
        dock.setOnClickListener {
            onKeyFeedback()
            prefs.edit().putBoolean(Prefs.FLOAT, false).apply()
            applyFloat()
        }
        row.addView(dock, LinearLayout.LayoutParams(dp(44f), dp(28f)))
        val grip = object : View(this) {
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: android.graphics.Canvas) {
                paint.color = kv?.palette?.hint ?: 0xFF888888.toInt()
                val w = dp(44f).toFloat()
                val hh = dp(5f).toFloat()
                c.drawRoundRect(width / 2f - w / 2, height / 2f - hh / 2, width / 2f + w / 2, height / 2f + hh / 2, hh, hh, paint)
            }
        }
        row.addView(grip, LinearLayout.LayoutParams(0, dp(28f), 1f))
        row.addView(View(this), LinearLayout.LayoutParams(dp(44f), dp(28f)))
        var downX = 0f
        var downY = 0f
        var startL = 0
        var startT = 0
        val drag = View.OnTouchListener { _, e ->
            val root = floatRoot ?: return@OnTouchListener false
            val lp = root.layoutParams as? FrameLayout.LayoutParams ?: return@OnTouchListener false
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startL = lp.leftMargin; startT = lp.topMargin
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    lp.leftMargin = startL + (e.rawX - downX).toInt()
                    lp.topMargin = startT + (e.rawY - downY).toInt()
                    root.layoutParams = lp
                    clampFloat(false)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> clampFloat(true)
            }
            true
        }
        grip.setOnTouchListener(drag)
        dock.color = kv?.palette?.text ?: 0xFFFFFFFF.toInt()
        return row
    }

    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        if (!floating) return
        val frame = floatFrame ?: return
        val root = floatRoot ?: return
        if (!frame.isShown || frame.height == 0) return
        val loc = IntArray(2)
        frame.getLocationInWindow(loc)
        val bottom = loc[1] + frame.height
        // La app de atrás usa toda la pantalla; solo la ventanita recibe toques
        outInsets.contentTopInsets = bottom
        outInsets.visibleTopInsets = bottom
        outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
        root.getLocationInWindow(loc)
        outInsets.touchableRegion.set(loc[0], loc[1], loc[0] + root.width, loc[1] + root.height)
    }

    // ---------------------------------------------------------------- Buscar emojis y GIFs

    /** Guarda lo que escribiste o editaste en el portapapeles y regresa al portapapeles. */
    private fun saveClipEdit() {
        val bar = searchBar ?: return
        val full = bar.text()
        val item = clipEditing
        if (full.isNotBlank()) { if (item != null) ClipStore.editText(item, full) else ClipStore.addManual(full) }
        clipEditing = null
        stopSearch()
        showMode(2)
    }

    /** Lo que estás editando del portapapeles (null = agregando algo nuevo). */
    private var clipEditing: ClipItem? = null

    private fun startSearch(gif: Boolean, note: Boolean = false, material: Boolean = false, clipText: String? = null,
                            custom: String = "", customHint: Pair<String, String>? = null, info: String = "") {
        val bar = searchBar ?: return
        if (translating) stopTranslate()
        if (spellOn) stopSpell(keepSelection = true)
        closeInfo()
        showMode(0)
        searching = if (custom.isNotEmpty()) 6 else if (clipText != null) 5 else if (material) 4 else if (note) 3 else if (gif) 2 else 1
        bar.visibility = View.VISIBLE
        bar.start(gif, note, material, clipText, custom, customHint, info)
        lastCorrection = null
        autoSpace = false
        lastWasSpace = false
        page = if (custom == "convert" && !convertCable && !convertRun) Page.SYM else Page.LETTERS
        rebuild()
        updateAutoCaps()
        scheduleUpdate()
    }

    private fun stopSearch() {
        if (searching == 0) return
        searching = 0
        searchBar?.let { it.visibility = View.GONE }
        lastCorrection = null
        clearSlots()
        updateAutoCaps()
        scheduleUpdate()
    }

    /** Enter en la búsqueda: en GIFs muestra los resultados; en emojis vuelve al panel. */
    private fun searchEnter() {
        val bar = searchBar ?: return
        val q = bar.text().trim()
        val gif = bar.gifs
        when (bar.custom) {
            "todo" -> {
                stopSearch()
                if (q.isNotEmpty()) { val id = Todos.add(prefs, q); showMode(15); askTodoWhen(id) } else showMode(15)
                return
            }
            "check" -> {
                if (q.isNotEmpty()) Todos.setCheckItems(prefs, Todos.checkItems(prefs) + q)
                stopSearch()
                showMode(16)
                return
            }
            "labels" -> {
                val list = Labels.make(q)
                stopSearch()
                showMode(0)
                if (list != null) {
                    val multi = ((currentInputEditorInfo?.inputType ?: 0) and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                    snapUndo()
                    insertText(list.joinToString(if (multi) "\n" else ", "))
                } else if (q.isNotEmpty()) Toast.makeText(this, Emo.strip(L.t("No entendí. Ejemplo: PP1-01 a PP1-24", "I didn't get it. Example: PP1-01 to PP1-24")), Toast.LENGTH_SHORT).show()
                return
            }
            "google" -> {
                stopSearch()
                showMode(0)
                if (q.isNotEmpty()) googleSearch(q)
                return
            }
            "remind" -> {
                stopSearch()
                showMode(0)
                if (q.isNotEmpty()) pickRemindSite(Todos.add(prefs, q), q)
                return
            }
            "convert" -> {
                val r = convertResult(q, forTyping = true)
                stopSearch()
                showMode(0)
                if (r.isNotEmpty()) insertText(r)
                return
            }
        }
        if (bar.clip) {
            // En el portapapeles, Enter hace una línea nueva (se guarda con «✓ Guardar»)
            bar.connection.commitText("\n", 1)
            return
        }
        if (bar.material) {
            if (q.isNotEmpty()) Materials.addCustom(prefs, q)
            stopSearch()
            showMode(13)
            return
        }
        if (bar.note) {
            // Guardar el apunte y seguir apuntando
            if (q.isNotEmpty()) {
                WorkStore.addNote(prefs, q)
                bar.setText("")
                Toast.makeText(this, Emo.strip(L.t("Apunte guardado en la libreta", "Note saved in the notebook")), Toast.LENGTH_SHORT).show()
            } else { stopSearch(); showMode(10) }
            return
        }
        stopSearch()
        if (gif) {
            pendingGifQuery = q.ifEmpty { null }
            showMode(9)
        } else showMode(1)
    }

    /** Para volver a pintar la barra de información en el otro idioma (al cambiar con 🌐). */
    private var infoRegen: (() -> Unit)? = null

    private fun closeInfo() {
        infoRegen = null
        readTrans?.close()
        readTrans = null
        infoBar?.visibility = View.GONE
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
            eng.user.add(word, 1, lang)
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
        val kind = kv?.lastPressKind ?: Feedback.KIND_KEY
        if (vibrateOn) Feedback.vibrate(this, vibStyle, vibStrength, kv)
        if (soundOn) Feedback.sound(this, kind, soundStyle, soundVol)
    }
}
