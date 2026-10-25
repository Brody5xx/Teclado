package com.luisell.bestkeyboard

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

/** El servicio del teclado: recibe las teclas y las escribe en la app que estés usando. */
class BestKeyboardService : InputMethodService(), KeyboardView.Listener {

    private enum class Page { LETTERS, SYM, SYM2, NUM }

    private var kv: KeyboardView? = null
    private lateinit var prefs: SharedPreferences
    private var vibrator: Vibrator? = null
    private var audio: AudioManager? = null

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

    // Tipo de campo
    private var isPassword = false
    private var phone = false
    private var commaMode = ","

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        audio = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        kv = v
        applySettings()
        rebuild()
        return v
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
        kv?.enterAction = enterActionFor(info)

        if (!restarting) {
            page = if (numeric || phone) Page.NUM else Page.LETTERS
            shift = 0
            manualShift = false
        }
        lastWasSpace = false
        rebuild()
        updateAutoCaps()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        kv?.reset()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updateAutoCaps()
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
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        val caps = ic.getCursorCapsMode(ei.inputType) != 0
        setShift(if (caps) 1 else 0)
    }

    private fun enterActionFor(ei: EditorInfo): Int {
        if (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EditorInfo.IME_ACTION_NONE
        val a = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        return if (a == EditorInfo.IME_ACTION_UNSPECIFIED) EditorInfo.IME_ACTION_NONE else a
    }

    // ---------------------------------------------------------------- Teclas

    override fun onKeyText(text: String) {
        val ic = currentInputConnection ?: return
        var t = text
        if (page == Page.LETTERS && shift > 0 && t.length == 1) {
            t = Character.toUpperCase(t[0]).toString()
        }
        ic.commitText(t, 1)
        lastWasSpace = false
        if (shift == 1) {
            manualShift = false
            setShift(0)
        }
    }

    override fun onKeyCode(code: Int) {
        val ic = currentInputConnection
        when (code) {
            Codes.SPACE -> {
                if (ic == null) return
                val before = if (doubleSpace && !isPassword && lastWasSpace) ic.getTextBeforeCursor(2, 0) else null
                if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                    // Doble espacio = punto y espacio
                    ic.deleteSurroundingText(1, 0)
                    ic.commitText(". ", 1)
                    lastWasSpace = false
                } else {
                    ic.commitText(" ", 1)
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
            }
        }
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo
        val action = if (ei != null) enterActionFor(ei) else EditorInfo.IME_ACTION_NONE
        if (action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
            return
        }
        val multiLine = ei != null && (ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (multiLine) ic.commitText("\n", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    override fun onDeletePress() {
        if (currentInputConnection == null) return
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        lastWasSpace = false
    }

    override fun onCursorMove(steps: Int) {
        if (currentInputConnection == null) return
        val key = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(key) }
        lastWasSpace = false
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
        }
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
