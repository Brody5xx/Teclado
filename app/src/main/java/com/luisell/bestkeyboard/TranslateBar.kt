package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.Selection
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.graphics.Canvas
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Barra de traducción (como Gboard): arriba del teclado escribes en tu idioma
 * y la traducción va apareciendo en la app donde estás escribiendo.
 */
@SuppressLint("ViewConstructor")
class TranslateBar(context: Context) : LinearLayout(context) {

    interface Actions {
        fun onTranslateClose()
        fun onTranslateSwap()
        /** Tocaste el nombre de un idioma: pasa al siguiente (Inglés → Chino → …). */
        fun onTranslatePick(source: Boolean)
        fun onTranslateSource(text: String)
        fun onTranslateCursor()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val close = IconView(context, Icons.BACK, 8f)
    private val from = TextView(context)
    private val swap = IconView(context, Icons.SWAP, 8f)
    private val to = TextView(context)
    val box = CursorBox(context)
    private val status = TextView(context)
    private val sweep = SweepBorder(context)
    private var silent = false

    /** El teclado escribe aquí (no en la app) mientras traduces. */
    val inputInfo = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        imeOptions = EditorInfo.IME_ACTION_SEND
    }
    val connection: InputConnection

    init {
        orientation = VERTICAL
        setPadding(d(6f), d(6f), d(6f), d(2f))

        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        close.setOnClickListener { actions?.onTranslateClose() }
        row.addView(close, LayoutParams(d(44f), d(38f)))
        for (t in listOf(from, to)) {
            t.textSize = 15f
            t.gravity = Gravity.CENTER
        }
        from.setOnClickListener { actions?.onTranslatePick(true) }
        to.setOnClickListener { actions?.onTranslatePick(false) }
        swap.setOnClickListener { actions?.onTranslateSwap() }
        row.addView(from, LayoutParams(0, d(36f), 1f).apply { leftMargin = d(8f) })
        row.addView(swap, LayoutParams(d(48f), d(36f)))
        row.addView(to, LayoutParams(0, d(36f), 1f).apply { rightMargin = d(8f) })
        addView(row, LayoutParams(-1, -2))

        L.bindHint(box, "Escribe aquí para traducir", "Type here to translate")
        box.textSize = 16f
        box.isSingleLine = false
        box.maxLines = 3
        box.inputType = inputInfo.inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        box.showSoftInputOnFocus = false
        box.isFocusable = false
        box.setPadding(d(14f), d(8f), d(14f), d(8f))
        box.onTapMoved = { actions?.onTranslateCursor() }
        // El aviso («Preparando…») va ENCIMA de la caja, abajo a la derecha: así el teclado no brinca
        val holder = android.widget.FrameLayout(context)
        holder.addView(box, android.widget.FrameLayout.LayoutParams(-1, -2))
        // ✨ Mientras carga el idioma, una luz de colores le da la vuelta al borde de la caja
        holder.addView(sweep, android.widget.FrameLayout.LayoutParams(-1, -1))
        status.textSize = 11f
        status.maxLines = 1
        status.ellipsize = android.text.TextUtils.TruncateAt.END
        status.setPadding(d(10f), d(2f), d(10f), d(2f))
        status.visibility = GONE
        holder.addView(status, android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(d(40f), 0, d(8f), d(4f)) })
        addView(holder, LayoutParams(-1, -2).apply { topMargin = d(6f) })

        connection = box.onCreateInputConnection(inputInfo)
            ?: android.view.inputmethod.BaseInputConnection(box, true)
        box.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!silent) actions?.onTranslateSource(s?.toString() ?: "")
            }
        })
        applyPalette(palette)
    }

    fun setLangs(src: String, dst: String) {
        val shown = isShown // al abrir no se anima, solo al cambiar
        val swapped = shown && src == lastDst && dst == lastSrc && src != dst
        val srcChanged = shown && src != lastSrc
        val dstChanged = shown && dst != lastDst
        from.text = Translation.name(src)
        to.text = Translation.name(dst)
        lastSrc = src
        lastDst = dst
        if (swapped) {
            // ⇄ Los dos idiomas se cruzan de lado y la flechita da media vuelta
            val dist = (to.left - from.left).toFloat().takeIf { it > 0f } ?: d(160f).toFloat()
            for ((v, start) in listOf(from to dist, to to -dist)) {
                v.animate().cancel()
                v.translationX = start; v.scaleX = 0.86f; v.scaleY = 0.86f
                v.animate().translationX(0f).scaleX(1f).scaleY(1f).setDuration(380L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.1f)).start()
            }
            swap.animate().cancel()
            swap.rotation = 0f
            swap.animate().rotation(180f).setDuration(380L).setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction { swap.rotation = 0f }.start()
        } else {
            if (srcChanged) flip(from)
            if (dstChanged) flip(to)
        }
    }

    /** El botón del idioma da una vueltita (como una tarjeta) y brilla al cambiar. */
    private fun flip(v: TextView) {
        v.animate().cancel()
        v.cameraDistance = 8000f * resources.displayMetrics.density
        v.rotationX = -90f; v.alpha = 0.4f
        v.animate().rotationX(0f).alpha(1f).setDuration(320L).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        v.scaleX = 1.08f; v.scaleY = 1.08f
        v.animate().scaleX(1f).scaleY(1f).setDuration(420L).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
    }

    /** ✨ La luz alrededor de la caja mientras se prepara el idioma (o mientras traduce). */
    fun loading(on: Boolean) = sweep.run(on)

    private val showStatus = Runnable {
        status.alpha = 0f
        status.visibility = VISIBLE
        status.animate().alpha(1f).setDuration(200L).start()
    }

    /**
     * El aviso de abajo a la derecha. Si el idioma ya está descargado casi nunca se ve: solo sale si tarda
     * más de un momento (así cambiar de idioma no parpadea).
     */
    fun setStatus(text: String, now: Boolean = false) {
        removeCallbacks(showStatus)
        if (text.isEmpty()) {
            if (status.visibility == VISIBLE) status.animate().alpha(0f).setDuration(160L).withEndAction { status.visibility = GONE }.start()
            return
        }
        status.animate().cancel()
        status.text = Emo.strip(text)
        if (now || status.visibility == VISIBLE) { status.alpha = 1f; status.visibility = VISIBLE } else postDelayed(showStatus, 700L)
    }

    /** Borrar hacia atrás dentro de la caja (una letra, o un emoji completo). */
    fun deleteBack() = box.deleteBack()

    /** Mover el cursor dentro de la caja (deslizar sobre la barra espaciadora). */
    fun moveCursor(steps: Int) = box.moveCursor(steps)

    /** Muestra el cursor parpadeando al abrir el traductor. */
    fun startCursor() {
        box.text?.let { if (Selection.getSelectionEnd(it) < 0) Selection.setSelection(it, it.length) }
        box.resetBlink()
    }

    private var lastSrc = "es"
    private var lastDst = "en"

    /** Vuelve a poner los nombres de los idiomas (cuando cambias el idioma del teclado). */
    fun applyLang() = setLangs(lastSrc, lastDst)

    /** Vacía la caja sin traducir. */
    fun clear() {
        silent = true
        box.text?.clear()
        silent = false
        box.text?.let { Selection.setSelection(it, 0) }
    }

    fun applyPalette(p: Palette) {
        palette = p
        background = PanelUi.barBackground(context, p) // 🎨 ya no gris
        val r = d(18f).toFloat()
        close.color = p.text
        close.background = PanelUi.pressable(p.special, p.keyPressed, r)
        swap.color = p.text
        for (t in listOf(from, to)) {
            t.setTextColor(p.onAccent)
            t.background = PanelUi.pressable(p.accent, p.accentPressed, r)
        }
        box.setTextColor(p.text)
        box.cursorColor = p.accent
        box.setHintTextColor(p.hint)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.setColor(p.key)
        PanelUi.decorate(bg)
        bg.cornerRadius = d(20f).toFloat()
        bg.setStroke(d(1.5f), p.accent)
        box.background = bg
        status.setTextColor(p.hint)
        status.background = PanelUi.rounded((p.key and 0x00FFFFFF) or 0xE6000000.toInt(), d(10f).toFloat())
    }
}

/**
 * Caja de texto del traductor con cursor propio que parpadea (Android no dibuja el cursor
 * dentro de un teclado) y que deja tocar entre las palabras para escribir ahí.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView", "ClickableViewAccessibility")
class CursorBox(context: Context) : EditText(context) {
    /** Se llama cuando tocas la caja para mover el cursor. */
    var onTapMoved: (() -> Unit)? = null
    var cursorColor = 0xFF8AB4F8.toInt()
    private var ready = false
    private var blinkOn = true
    private val cursorPaint = android.graphics.Paint()
    private val blink = object : Runnable {
        override fun run() {
            if (!isShown) { blinkOn = true; return }
            blinkOn = !blinkOn
            invalidate()
            postDelayed(this, 500)
        }
    }

    init { ready = true }

    /** Borrar hacia atrás (una letra, o un emoji completo). */
    fun deleteBack() {
        val e = text ?: return
        var start = Selection.getSelectionStart(e)
        var end = Selection.getSelectionEnd(e)
        if (start < 0 || end < 0) { start = e.length; end = e.length }
        if (start > end) { val t = start; start = end; end = t }
        if (start != end) {
            e.delete(start, end)
            return
        }
        if (start == 0) return
        val bi = android.icu.text.BreakIterator.getCharacterInstance()
        bi.setText(e.toString())
        val prev = bi.preceding(start)
        e.delete(if (prev == android.icu.text.BreakIterator.DONE) start - 1 else prev, start)
    }

    /** Mover el cursor [steps] letras (negativo = a la izquierda). */
    fun moveCursor(steps: Int) {
        val e = text ?: return
        var pos = Selection.getSelectionEnd(e)
        if (pos < 0) pos = e.length
        Selection.setSelection(e, (pos + steps).coerceIn(0, e.length))
    }

    /** Enciende el cursor y vuelve a empezar el parpadeo (después de escribir o mover). */
    fun resetBlink() {
        if (!ready) return
        blinkOn = true
        removeCallbacks(blink)
        postDelayed(blink, 500)
        invalidate()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready) return
        if (selEnd >= 0) bringPointIntoView(selEnd)
        resetBlink()
    }

    override fun onDetachedFromWindow() {
        if (ready) removeCallbacks(blink)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (!ready || !blinkOn) return
        val l = layout ?: return
        val e = text ?: return
        var sel = Selection.getSelectionEnd(e)
        if (sel < 0) sel = e.length
        val line = l.getLineForOffset(sel)
        val x = compoundPaddingLeft + l.getPrimaryHorizontal(sel)
        val top = extendedPaddingTop + l.getLineTop(line).toFloat()
        val bottom = extendedPaddingTop + l.getLineBottom(line).toFloat()
        val w = resources.displayMetrics.density * 2f
        cursorPaint.color = cursorColor
        canvas.drawRect(x - w / 2, top + w, x + w / 2, bottom - w, cursorPaint)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
            val off = getOffsetForPosition(event.x, event.y)
            val e = text
            if (off >= 0 && e != null) {
                Selection.setSelection(e, off.coerceIn(0, e.length))
                onTapMoved?.invoke()
            }
        }
        return true
    }
}

/** ✨ Una luz de colores (los del diamante) que recorre el borde redondo de la caja. */
class SweepBorder(context: Context) : View(context) {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE }
    private val rect = android.graphics.RectF()
    private val m = android.graphics.Matrix()
    private var phase = 0f
    private var anim: android.animation.ValueAnimator? = null
    private val d = resources.displayMetrics.density

    init { alpha = 0f; isClickable = false; isFocusable = false }

    // No pide espacio propio: toma justo el tamaño de la caja (si no, la barra se estiraba a toda la pantalla)
    override fun onMeasure(w: Int, h: Int) {
        val mw = if (MeasureSpec.getMode(w) == MeasureSpec.EXACTLY) MeasureSpec.getSize(w) else 0
        val mh = if (MeasureSpec.getMode(h) == MeasureSpec.EXACTLY) MeasureSpec.getSize(h) else 0
        setMeasuredDimension(mw, mh)
    }

    private var since = 0L
    private val offNow = Runnable { run(false) }

    fun run(on: Boolean) {
        removeCallbacks(offNow)
        // Que la luz dé al menos una vuelta completa (aunque el idioma cargue al instante)
        if (!on && anim != null && android.os.SystemClock.uptimeMillis() - since < 1300L) {
            postDelayed(offNow, 1300L - (android.os.SystemClock.uptimeMillis() - since)); return
        }
        if (on) {
            if (anim == null) since = android.os.SystemClock.uptimeMillis()
            if (anim == null) anim = android.animation.ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 1400L; repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
            animate().cancel(); animate().alpha(1f).setDuration(220L).start()
        } else {
            animate().cancel()
            animate().alpha(0f).setDuration(300L).withEndAction { anim?.cancel(); anim = null }.start()
        }
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); anim?.cancel(); anim = null }

    override fun onDraw(c: Canvas) {
        if (alpha <= 0f) return
        val cx = width / 2f; val cy = height / 2f
        val sh = android.graphics.SweepGradient(cx, cy,
            intArrayOf(0x00000000, LightFx.COLORS[0], LightFx.COLORS[1], LightFx.COLORS[2], 0x00000000, 0x00000000),
            floatArrayOf(0f, 0.08f, 0.16f, 0.24f, 0.32f, 1f))
        m.reset(); m.setRotate(phase, cx, cy); sh.setLocalMatrix(m)
        paint.shader = sh
        paint.strokeWidth = 2.6f * d
        val inset = paint.strokeWidth / 2f
        rect.set(inset, inset, width - inset, height - inset)
        c.drawRoundRect(rect, 20f * d, 20f * d, paint)
    }
}
