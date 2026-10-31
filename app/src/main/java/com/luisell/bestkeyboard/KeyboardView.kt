package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * El teclado completo dibujado a mano en una sola vista nativa (Canvas).
 * Es la forma más rápida: no hay botones ni WebView, solo dibujo directo.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKeyText(text: String)
        fun onKeyCode(code: Int)
        fun onDeletePress()
        fun onCursorMove(steps: Int)
        fun onToolbar(action: Int)
        fun onLongPressGlobe()
        fun onKeyFeedback()
        fun onSuggestion(slot: Int)
        /** Terminaste un trazo de "deslizar para escribir" (puntos en píxeles). */
        fun onGesture(xs: FloatArray, ys: FloatArray, count: Int)
    }

    companion object {
        const val TOOL_SETTINGS = 0
        const val TOOL_THEME = 1
        const val TOOL_HIDE = 2
        const val TOOL_EMOJI = 3
        const val TOOL_CLIP = 4
        const val TOOL_TRANSLATE = 5
        const val TOOL_VOICE = 6
        const val TOOL_SCAN = 7
        const val TOOL_SPELL = 8
        const val TOOL_MENU = 9
        const val TOOL_EDIT = 20
        const val TOOL_SHOT = 21
        private const val LONG_PRESS_MS = 350L
        private const val REPEAT_START_MS = 400L
    }

    var listener: Listener? = null
    private val d = resources.displayMetrics.density

    var palette: Palette = Palette.DARK
        set(v) { field = v; invalidate() }
    var keyHeightDp = 52f
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }
    var shift = 0 // 0 = minúsculas, 1 = una mayúscula, 2 = bloqueo de mayúsculas
        set(v) { if (field != v) { field = v; invalidate() } }
    var enterAction = EditorInfo.IME_ACTION_NONE
        set(v) { if (field != v) { field = v; invalidate() } }
    var spaceLabel = ""
        set(v) { if (field != v) { field = v; invalidate() } }
    var showPreview = true
    /** Deslizar para escribir (solo en las letras y en campos de texto normales). */
    var swipeEnabled = false

    // Trazo del dedo al deslizar
    private var gestureCandidate = false
    private var gesturing = false
    private var downY = 0f
    private var gx = FloatArray(512)
    private var gy = FloatArray(512)
    private var gCount = 0
    private val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trailPath = Path()

    private fun addGesturePoint(x: Float, y: Float) {
        if (gCount == gx.size) {
            gx = gx.copyOf(gx.size * 2)
            gy = gy.copyOf(gy.size * 2)
        }
        gx[gCount] = x
        gy[gCount] = y
        gCount++
    }

    /** Centros de las letras (para el cerebro del deslizar). */
    fun letterCenters(): List<com.luisell.bestkeyboard.words.GestureDecoder.KeyPos> {
        val out = ArrayList<com.luisell.bestkeyboard.words.GestureDecoder.KeyPos>()
        for (row in rows) for (k in row) {
            if (k.code == 0 && k.label.length == 1 && k.label[0].isLetter()) {
                out.add(com.luisell.bestkeyboard.words.GestureDecoder.KeyPos(k.label[0].lowercaseChar(), k.rect.centerX(), k.rect.centerY()))
            }
        }
        return out
    }

    /** Ancho de una tecla de letra. */
    fun letterWidth(): Float {
        for (row in rows) for (k in row) if (k.code == 0 && k.label.length == 1 && k.label[0].isLetter()) return k.cell.width()
        return 0f
    }

    /** Ilumina el botón de revisar mientras revisas la ortografía. */
    var spellOn = false
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Ilumina el botón de traducir mientras traduces. */
    var translateOn = false
        set(v) { if (field != v) { field = v; invalidate() } }

    // Barra de sugerencias: [izquierda, centro, derecha]
    private val slots = arrayOfNulls<String>(3)
    private var centerBold = false
    private var leftQuoted = false
    private val hasSuggestions get() = slots[0] != null || slots[1] != null || slots[2] != null

    fun setSuggestions(left: String?, center: String?, right: String?, bold: Boolean, quoteLeft: Boolean) {
        if (slots[0] == left && slots[1] == center && slots[2] == right &&
            centerBold == bold && leftQuoted == quoteLeft) return
        slots[0] = left; slots[1] = center; slots[2] = right
        centerBold = bold
        leftQuoted = quoteLeft
        invalidate(0, 0, width, stripH.toInt() + 1)
    }

    fun clearSuggestions() = setSuggestions(null, null, null, false, false)

    private var rows: List<List<Key>> = emptyList()
    private val stripH = 46f * d
    private val bottomPad = 6f * d
    private val keyH get() = keyHeightDp * d
    private val gapX = 5f * d
    private val gapY = 8f * d
    private val radius = 7f * d

    // Pinturas creadas una sola vez (nada se crea mientras se dibuja = más fluido)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fm = Paint.FontMetrics()
    private val path = Path()
    private val tmp = RectF()

    // Estado del toque
    private val handler = Handler(Looper.getMainLooper())
    private var activeId = -1
    private var activeKey: Key? = null
    private var downX = 0f
    private var longPressed = false
    private var stripPressed = -1
    private var spaceDragging = false
    private var spaceLastX = 0f
    private var repeatCount = 0

    // Ventanita de acentos
    private var altKeys: List<String>? = null
    private var altSel = 0
    private var altLeft = 0f
    private var altTop = 0f
    private var altW = 0f
    private var altH = 0f
    private var altCellW = 0f
    private val altPad = 4f * d

    private val longPressRunnable = Runnable { onLongPress() }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            listener?.onDeletePress()
            repeatCount++
            handler.postDelayed(this, if (repeatCount > 12) 30L else 60L)
        }
    }

    fun setRows(r: List<List<Key>>) {
        val heightChanges = r.size != rows.size
        rows = r
        reset()
        if (heightChanges) requestLayout()
        if (width > 0) layoutKeys()
        invalidate()
    }

    /** Cancela cualquier toque en curso (por ejemplo al cerrar el teclado). */
    fun reset() {
        gestureCandidate = false
        if (gesturing) { gesturing = false; gCount = 0 }
        handler.removeCallbacksAndMessages(null)
        activeKey = null
        activeId = -1
        altKeys = null
        stripPressed = -1
        longPressed = false
        spaceDragging = false
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (stripH + rows.size * keyH + bottomPad).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutKeys()
    }

    private fun layoutKeys() {
        val w = width.toFloat()
        val kh = keyH
        var y = stripH
        for (row in rows) {
            val total = row.fold(0f) { a, k -> a + k.weight }
            var x = 0f
            for ((i, k) in row.withIndex()) {
                val kw = w * k.weight / total
                val right = if (i == row.lastIndex) w else x + kw
                k.cell.set(x, y, right, y + kh)
                val l = x + gapX / 2 + (if (i == 0) gapX / 2 else 0f)
                val r = right - gapX / 2 - (if (i == row.lastIndex) gapX / 2 else 0f)
                k.rect.set(l, y + gapY / 2, r, y + kh - gapY / 2)
                x += kw
            }
            y += kh
        }
    }

    private fun findKey(x: Float, yIn: Float): Key? {
        if (rows.isEmpty()) return null
        val lastRow = rows.last()
        if (lastRow.isEmpty()) return null
        val y = min(yIn, lastRow[0].cell.bottom - 1f)
        for (row in rows) {
            if (row.isEmpty()) continue
            val c = row[0].cell
            if (y < c.top || y >= c.bottom) continue
            for (k in row) if (x >= k.cell.left && x < k.cell.right) return k
            return if (x < c.left) row.first() else row.last()
        }
        return null
    }

    /** Botón ▦ de la esquina: muestra las herramientas aunque haya sugerencias (como Gboard). */
    private var forceTools = false
    private val menuW get() = 48f * d
    private val showingSuggestions get() = hasSuggestions && !forceTools

    // Sugerencia de captura de pantalla (botón con la miniatura)
    private var shotThumb: android.graphics.Bitmap? = null
    private var chipLabel: String? = null
    private val shotRect = RectF()
    private val showingShot get() = chipLabel != null && !hasSuggestions && !forceTools

    /** Botón grande en el centro de la barra (la captura de pantalla, o el aviso del permiso). */
    fun setChip(thumb: android.graphics.Bitmap?, label: String?) {
        if (shotThumb === thumb && chipLabel == label) return
        shotThumb = thumb
        chipLabel = label
        invalidate(0, 0, width, stripH.toInt() + 1)
    }

    private fun stripHit(x: Float): Int {
        if (showingSuggestions) {
            if (x < menuW) return TOOL_MENU
            val slot = ((x - menuW) / ((width - menuW) / 3f)).toInt().coerceIn(0, 2)
            return if (slots[slot] != null) 10 + slot else -1
        }
        if (showingShot) {
            if (x < menuW) return TOOL_MENU
            return if (x >= shotRect.left && x <= shotRect.right) TOOL_SHOT else -1
        }
        return toolHit(x)
    }

    /** Herramientas de la izquierda (en orden) y su separación según el ancho de la pantalla. */
    private val leftTools = intArrayOf(TOOL_SETTINGS, TOOL_EMOJI, TOOL_CLIP, TOOL_TRANSLATE, TOOL_SCAN, TOOL_SPELL, TOOL_EDIT)
    private fun toolStep(): Float = minOf(52f * d, (width - 112f * d) / leftTools.size)

    private fun toolHit(x: Float): Int {
        if (x > width - 56f * d) return TOOL_HIDE
        if (x > width - 108f * d) return TOOL_VOICE
        val i = (x / toolStep()).toInt()
        return if (i in leftTools.indices) leftTools[i] else -1
    }

    // ---------------------------------------------------------------- Toques

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> startPointer(e.getPointerId(0), e.getX(0), e.getY(0))
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Mientras deslizas para escribir, otro dedo no interrumpe
                if (gesturing) return true
                // Escritura rápida: si tocas otra tecla antes de soltar, la anterior se escribe ya
                if (activeId != -1) releasePointer(true)
                val i = e.actionIndex
                startPointer(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(activeId)
                if (i >= 0) movePointer(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.getPointerId(e.actionIndex) == activeId) releasePointer(true)
            }
            MotionEvent.ACTION_UP -> {
                if (e.getPointerId(e.actionIndex) == activeId) releasePointer(true)
                activeId = -1
            }
            MotionEvent.ACTION_CANCEL -> releasePointer(false)
        }
        return true
    }

    private fun startPointer(id: Int, x: Float, y: Float) {
        activeId = id
        downX = x
        longPressed = false
        spaceDragging = false
        altKeys = null
        if (y < stripH) {
            stripPressed = stripHit(x)
            if (stripPressed >= 0) listener?.onKeyFeedback()
            invalidate()
            return
        }
        val key = findKey(x, y) ?: return
        activeKey = key
        if (forceTools) { forceTools = false; invalidate(0, 0, width, stripH.toInt() + 1) }
        listener?.onKeyFeedback()
        // ¿Podría empezar un trazo? (solo desde una letra)
        gestureCandidate = swipeEnabled && key.code == 0 && key.label.length == 1 && key.label[0].isLetter()
        gesturing = false
        gCount = 0
        downY = y
        if (gestureCandidate) addGesturePoint(x, y)
        when (key.code) {
            Codes.DELETE -> {
                listener?.onDeletePress()
                repeatCount = 0
                handler.postDelayed(repeatRunnable, REPEAT_START_MS)
            }
            Codes.SHIFT -> listener?.onKeyCode(Codes.SHIFT)
            else -> if (key.alts.isNotEmpty() || key.code == Codes.LANG || key.emojiKey) {
                handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
            }
        }
        invalidate()
    }

    private fun movePointer(x: Float, y: Float) {
        if (gesturing) {
            val lx = gx[gCount - 1]
            val ly = gy[gCount - 1]
            if (abs(x - lx) + abs(y - ly) > 2f * d) addGesturePoint(x, y)
            invalidate()
            return
        }
        val key = activeKey ?: return
        val alts = altKeys
        if (gestureCandidate && alts == null && !longPressed) {
            addGesturePoint(x, y)
            // Si el dedo se aleja de la primera tecla, es un trazo (no un toque)
            val limit = key.cell.width() * 0.6f
            if (abs(x - downX) > limit || abs(y - downY) > key.cell.height() * 0.6f) {
                gesturing = true
                handler.removeCallbacks(longPressRunnable)
                activeKey = null
                invalidate()
                return
            }
        }
        if (alts != null) {
            val sel = ((x - altLeft - altPad) / altCellW).toInt().coerceIn(0, alts.size - 1)
            if (sel != altSel) { altSel = sel; invalidate() }
            return
        }
        if (key.code == Codes.SPACE) {
            // Deslizar sobre la barra espaciadora mueve el cursor
            if (!spaceDragging && abs(x - downX) > 18f * d) {
                spaceDragging = true
                spaceLastX = x
            }
            if (spaceDragging) {
                val step = 11f * d
                val n = ((x - spaceLastX) / step).toInt()
                if (n != 0) {
                    listener?.onCursorMove(n)
                    spaceLastX += n * step
                }
            }
            return
        }
        if (key.code == 0 && !longPressed) {
            val k = findKey(x, y)
            if (k != null && k !== key && k.code == 0) {
                activeKey = k
                handler.removeCallbacks(longPressRunnable)
                if (k.alts.isNotEmpty()) handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                invalidate()
            }
        }
    }

    private fun releasePointer(commit: Boolean) {
        handler.removeCallbacks(longPressRunnable)
        handler.removeCallbacks(repeatRunnable)
        activeId = -1
        if (gesturing) {
            gesturing = false
            gestureCandidate = false
            val n = gCount
            gCount = 0
            invalidate()
            if (commit && n >= 2) listener?.onGesture(gx.copyOf(n), gy.copyOf(n), n)
            return
        }
        gestureCandidate = false
        if (stripPressed >= 0) {
            val action = stripPressed
            stripPressed = -1
            invalidate()
            if (commit) {
                when {
                    action == TOOL_MENU -> { forceTools = true; invalidate() }
                    action in 10..12 -> listener?.onSuggestion(action - 10)
                    else -> { forceTools = false; listener?.onToolbar(action) }
                }
            }
            return
        }
        val key = activeKey
        val alts = altKeys
        activeKey = null
        altKeys = null
        invalidate()
        if (key == null || !commit) return
        if (alts != null) {
            listener?.onKeyText(alts[altSel])
            return
        }
        if (longPressed) return
        when (key.code) {
            0 -> key.text?.let { listener?.onKeyText(it) }
            Codes.SPACE -> if (!spaceDragging) listener?.onKeyCode(Codes.SPACE)
            Codes.DELETE, Codes.SHIFT -> Unit
            else -> listener?.onKeyCode(key.code)
        }
    }

    private fun onLongPress() {
        val key = activeKey ?: return
        longPressed = true
        if (key.code == Codes.LANG) {
            listener?.onLongPressGlobe()
            return
        }
        if (key.emojiKey) {
            // Mantener la coma abre los emojis (como en Gboard)
            listener?.onKeyFeedback()
            listener?.onToolbar(TOOL_EMOJI)
            return
        }
        if (key.alts.isEmpty()) return
        val list = key.alts.map { if (shift > 0) upper(it) else it }
        altCellW = max(key.rect.width(), 34f * d)
        altW = list.size * altCellW + 2 * altPad
        val maxW = width - 4f * d
        if (altW > maxW) {
            altCellW = (maxW - 2 * altPad) / list.size
            altW = maxW
        }
        altH = keyH + altPad
        altLeft = (key.rect.centerX() - altCellW / 2 - altPad)
            .coerceIn(2f * d, max(2f * d, width - altW - 2f * d))
        altTop = max(2f * d, key.rect.top - altH - 4f * d)
        altSel = ((key.rect.centerX() - altLeft - altPad) / altCellW).toInt().coerceIn(0, list.size - 1)
        altKeys = list
        listener?.onKeyFeedback()
        invalidate()
    }

    private fun upper(s: String): String =
        if (s.length == 1) Character.toUpperCase(s[0]).toString() else s.uppercase()

    private fun display(label: String): String =
        if (shift > 0 && label.length == 1 && label[0].isLetter()) upper(label) else label

    // ---------------------------------------------------------------- Dibujo

    override fun onDraw(c: Canvas) {
        val p = palette
        c.drawColor(p.bg)
        drawStrip(c, p)
        for (row in rows) for (k in row) drawKey(c, k, p)
        if (gesturing && gCount > 1) drawTrail(c, p)
        val ak = activeKey
        if (altKeys != null) drawAlts(c, p)
        else if (ak != null && showPreview && ak.code == 0 && !longPressed) drawPreview(c, ak, p)
    }

    /** La estela azul del dedo al deslizar (más fina en la cola). */
    private fun drawTrail(c: Canvas, p: Palette) {
        trail.color = p.accent
        val start = maxOf(0, gCount - 60)
        val segs = gCount - 1 - start
        for (i in start until gCount - 1) {
            val f = (i - start + 1).toFloat() / segs.coerceAtLeast(1)
            trail.strokeWidth = (2f + 5f * f) * d
            trail.alpha = (80 + 175 * f).toInt()
            c.drawLine(gx[i], gy[i], gx[i + 1], gy[i + 1], trail)
        }
        trail.alpha = 255
    }

    private fun st(color: Int) {
        stroke.color = color
        stroke.strokeWidth = 1.8f * d
    }

    private fun drawLabel(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int) {
        text.textSize = size
        text.color = color
        text.getFontMetrics(fm)
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text)
    }

    private fun drawKey(c: Canvas, k: Key, p: Palette) {
        val pressed = k === activeKey
        val bg = when {
            k.code == Codes.ENTER -> if (pressed) p.accentPressed else p.accent
            pressed -> p.keyPressed
            k.special -> p.special
            else -> p.key
        }
        if (!p.isDark) {
            fill.color = p.shadow
            tmp.set(k.rect)
            tmp.offset(0f, 1f * d)
            c.drawRoundRect(tmp, radius, radius, fill)
        }
        fill.color = bg
        c.drawRoundRect(k.rect, radius, radius, fill)

        val cx = k.rect.centerX()
        val cy = k.rect.centerY()
        val s = keyH * 0.19f
        val fg = if (k.code == Codes.ENTER) p.onAccent else p.text
        when (k.code) {
            0 -> {
                drawLabel(c, display(k.label), cx, cy, if (k.label.length > 1) keyH * 0.3f else keyH * 0.42f, fg)
                if (k.emojiKey) {
                    drawLabel(c, "☺", k.rect.right - keyH * 0.2f, k.rect.top + keyH * 0.2f, keyH * 0.24f, p.hint)
                }
            }
            Codes.SPACE -> drawLabel(c, spaceLabel, cx, cy, keyH * 0.26f, p.hint)
            Codes.SHIFT -> drawShift(c, cx, cy, s, fg)
            Codes.DELETE -> drawDelete(c, cx, cy, s, fg)
            Codes.ENTER -> drawEnter(c, cx, cy, s, fg)
            Codes.LANG -> drawGlobe(c, cx, cy, s, fg)
            else -> drawLabel(c, k.label, cx, cy, keyH * 0.29f, fg)
        }
    }

    private fun drawShift(c: Canvas, cx: Float, cyIn: Float, s: Float, color: Int) {
        val cy = cyIn - s * 0.1f
        path.reset()
        path.moveTo(cx, cy - s)
        path.lineTo(cx + s, cy)
        path.lineTo(cx + s * 0.45f, cy)
        path.lineTo(cx + s * 0.45f, cy + s * 0.85f)
        path.lineTo(cx - s * 0.45f, cy + s * 0.85f)
        path.lineTo(cx - s * 0.45f, cy)
        path.lineTo(cx - s, cy)
        path.close()
        if (shift == 0) {
            st(color); c.drawPath(path, stroke)
        } else {
            fill.color = color; c.drawPath(path, fill)
        }
        if (shift == 2) {
            st(color)
            c.drawLine(cx - s * 0.6f, cy + s * 1.3f, cx + s * 0.6f, cy + s * 1.3f, stroke)
        }
    }

    private fun drawDelete(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        st(color)
        path.reset()
        path.moveTo(cx - s * 1.2f, cy)
        path.lineTo(cx - s * 0.5f, cy - s * 0.75f)
        path.lineTo(cx + s * 1.1f, cy - s * 0.75f)
        path.lineTo(cx + s * 1.1f, cy + s * 0.75f)
        path.lineTo(cx - s * 0.5f, cy + s * 0.75f)
        path.close()
        c.drawPath(path, stroke)
        c.drawLine(cx - s * 0.05f, cy - s * 0.32f, cx + s * 0.6f, cy + s * 0.32f, stroke)
        c.drawLine(cx - s * 0.05f, cy + s * 0.32f, cx + s * 0.6f, cy - s * 0.32f, stroke)
    }

    private fun drawEnter(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        st(color)
        when (enterAction) {
            EditorInfo.IME_ACTION_SEARCH -> {
                c.drawCircle(cx - s * 0.2f, cy - s * 0.2f, s * 0.6f, stroke)
                c.drawLine(cx + s * 0.25f, cy + s * 0.25f, cx + s * 0.85f, cy + s * 0.85f, stroke)
            }
            EditorInfo.IME_ACTION_SEND -> {
                path.reset()
                path.moveTo(cx - s * 0.85f, cy - s * 0.85f)
                path.lineTo(cx + s * 0.95f, cy)
                path.lineTo(cx - s * 0.85f, cy + s * 0.85f)
                path.lineTo(cx - s * 0.45f, cy)
                path.close()
                c.drawPath(path, stroke)
                c.drawLine(cx - s * 0.45f, cy, cx + s * 0.2f, cy, stroke)
            }
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> {
                c.drawLine(cx - s * 0.85f, cy, cx + s * 0.85f, cy, stroke)
                c.drawLine(cx + s * 0.3f, cy - s * 0.55f, cx + s * 0.85f, cy, stroke)
                c.drawLine(cx + s * 0.3f, cy + s * 0.55f, cx + s * 0.85f, cy, stroke)
            }
            EditorInfo.IME_ACTION_PREVIOUS -> {
                c.drawLine(cx - s * 0.85f, cy, cx + s * 0.85f, cy, stroke)
                c.drawLine(cx - s * 0.3f, cy - s * 0.55f, cx - s * 0.85f, cy, stroke)
                c.drawLine(cx - s * 0.3f, cy + s * 0.55f, cx - s * 0.85f, cy, stroke)
            }
            EditorInfo.IME_ACTION_DONE -> {
                path.reset()
                path.moveTo(cx - s * 0.85f, cy)
                path.lineTo(cx - s * 0.25f, cy + s * 0.6f)
                path.lineTo(cx + s * 0.9f, cy - s * 0.6f)
                c.drawPath(path, stroke)
            }
            else -> {
                path.reset()
                path.moveTo(cx + s * 0.8f, cy - s * 0.75f)
                path.lineTo(cx + s * 0.8f, cy + s * 0.25f)
                path.lineTo(cx - s * 0.8f, cy + s * 0.25f)
                c.drawPath(path, stroke)
                c.drawLine(cx - s * 0.8f, cy + s * 0.25f, cx - s * 0.35f, cy - s * 0.2f, stroke)
                c.drawLine(cx - s * 0.8f, cy + s * 0.25f, cx - s * 0.35f, cy + s * 0.7f, stroke)
            }
        }
    }

    private fun drawGlobe(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        st(color)
        val r = s * 0.9f
        c.drawCircle(cx, cy, r, stroke)
        tmp.set(cx - r * 0.42f, cy - r, cx + r * 0.42f, cy + r)
        c.drawOval(tmp, stroke)
        c.drawLine(cx - r, cy, cx + r, cy, stroke)
        c.drawLine(cx - r * 0.82f, cy - r * 0.5f, cx + r * 0.82f, cy - r * 0.5f, stroke)
        c.drawLine(cx - r * 0.82f, cy + r * 0.5f, cx + r * 0.82f, cy + r * 0.5f, stroke)
    }

    private fun drawStrip(c: Canvas, p: Palette) {
        if (showingSuggestions) { drawSuggestions(c, p); return }
        if (showingShot) { drawShot(c, p); return }
        val cy = stripH / 2f
        val s = stripH * 0.22f
        // Orden en pantalla: ajustes, emojis, portapapeles, tema ... esconder
        // (El cambio claro/oscuro está en los ajustes ⚙)
        val step = toolStep()
        val tools = leftTools + intArrayOf(TOOL_VOICE, TOOL_HIDE)
        val xs = FloatArray(tools.size) { if (it < leftTools.size) step * (it + 0.5f) else 0f }
        xs[leftTools.size] = width - 80f * d
        xs[leftTools.size + 1] = width - 28f * d
        for (n in tools.indices) {
            val i = tools[n]
            val cx = xs[n]
            if (stripPressed == i) {
                fill.color = p.keyPressed
                c.drawCircle(cx, cy, stripH * 0.4f, fill)
            }
            st(p.hint)
            when (i) {
                TOOL_SETTINGS -> {
                    c.drawCircle(cx, cy, s * 0.35f, stroke)
                    c.drawCircle(cx, cy, s * 0.75f, stroke)
                    for (t in 0 until 8) {
                        val a = t * Math.PI / 4
                        val ca = cos(a).toFloat()
                        val sa = sin(a).toFloat()
                        c.drawLine(cx + ca * s * 0.75f, cy + sa * s * 0.75f, cx + ca * s * 1.08f, cy + sa * s * 1.08f, stroke)
                    }
                }
                TOOL_THEME -> {
                    val r = s * 0.85f
                    c.drawCircle(cx, cy, r, stroke)
                    tmp.set(cx - r, cy - r, cx + r, cy + r)
                    fill.color = p.hint
                    c.drawArc(tmp, 90f, 180f, true, fill)
                }
                TOOL_EMOJI -> {
                    val r = s * 0.9f
                    c.drawCircle(cx, cy, r, stroke)
                    fill.color = p.hint
                    c.drawCircle(cx - r * 0.35f, cy - r * 0.25f, r * 0.12f, fill)
                    c.drawCircle(cx + r * 0.35f, cy - r * 0.25f, r * 0.12f, fill)
                    tmp.set(cx - r * 0.5f, cy - r * 0.3f, cx + r * 0.5f, cy + r * 0.55f)
                    c.drawArc(tmp, 20f, 140f, false, stroke)
                }
                TOOL_CLIP -> {
                    tmp.set(cx - s * 0.7f, cy - s * 0.75f, cx + s * 0.7f, cy + s * 0.95f)
                    c.drawRoundRect(tmp, s * 0.2f, s * 0.2f, stroke)
                    tmp.set(cx - s * 0.35f, cy - s * 0.98f, cx + s * 0.35f, cy - s * 0.55f)
                    fill.color = p.hint
                    c.drawRoundRect(tmp, s * 0.12f, s * 0.12f, fill)
                    c.drawLine(cx - s * 0.35f, cy - s * 0.05f, cx + s * 0.35f, cy - s * 0.05f, stroke)
                    c.drawLine(cx - s * 0.35f, cy + s * 0.4f, cx + s * 0.2f, cy + s * 0.4f, stroke)
                }
                TOOL_TRANSLATE -> {
                    drawLabel(c, "A文", cx, cy, stripH * 0.36f, if (translateOn) p.accent else p.hint)
                }
                TOOL_EDIT -> {
                    // ‹I›: edición de texto
                    drawLabel(c, "I", cx, cy, stripH * 0.42f, p.hint)
                    st(p.hint)
                    c.drawLine(cx - s * 0.55f, cy, cx - s * 0.95f, cy - s * 0.35f, stroke)
                    c.drawLine(cx - s * 0.55f, cy, cx - s * 0.95f, cy + s * 0.35f, stroke)
                    c.drawLine(cx + s * 0.55f, cy, cx + s * 0.95f, cy - s * 0.35f, stroke)
                    c.drawLine(cx + s * 0.55f, cy, cx + s * 0.95f, cy + s * 0.35f, stroke)
                }
                TOOL_SPELL -> {
                    // "abc" con una palomita: revisar ortografía
                    val col = if (spellOn) p.accent else p.hint
                    drawLabel(c, "abc", cx - s * 0.25f, cy - s * 0.25f, stripH * 0.27f, col)
                    st(col)
                    c.drawLine(cx - s * 0.1f, cy + s * 0.6f, cx + s * 0.25f, cy + s * 0.95f, stroke)
                    c.drawLine(cx + s * 0.25f, cy + s * 0.95f, cx + s * 0.95f, cy + s * 0.2f, stroke)
                }
                TOOL_SCAN -> {
                    // Marco de escáner: 4 esquinas y una línea en el medio
                    val r = s * 0.85f
                    val k = r * 0.45f
                    for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                        val x0 = cx + sx * r
                        val y0 = cy + sy * r
                        c.drawLine(x0, y0, x0 - sx * k, y0, stroke)
                        c.drawLine(x0, y0, x0, y0 - sy * k, stroke)
                    }
                    c.drawLine(cx - r * 0.55f, cy, cx + r * 0.55f, cy, stroke)
                }
                TOOL_VOICE -> {
                    tmp.set(cx - s * 0.38f, cy - s * 0.95f, cx + s * 0.38f, cy + s * 0.25f)
                    fill.color = p.hint
                    c.drawRoundRect(tmp, s * 0.38f, s * 0.38f, fill)
                    tmp.set(cx - s * 0.68f, cy - s * 0.55f, cx + s * 0.68f, cy + s * 0.6f)
                    c.drawArc(tmp, 0f, 180f, false, stroke)
                    c.drawLine(cx, cy + s * 0.6f, cx, cy + s * 0.95f, stroke)
                }
                TOOL_HIDE -> {
                    path.reset()
                    path.moveTo(cx - s * 0.7f, cy - s * 0.3f)
                    path.lineTo(cx, cy + s * 0.4f)
                    path.lineTo(cx + s * 0.7f, cy - s * 0.3f)
                    c.drawPath(path, stroke)
                }
            }
        }
    }

    /** Botón ▦ de la esquina (4 cuadritos). */
    private fun drawMenuButton(c: Canvas, p: Palette) {
        val cy = stripH / 2f
        val mx = menuW / 2f + 2f * d
        if (stripPressed == TOOL_MENU) {
            fill.color = p.keyPressed
            c.drawCircle(mx, cy, stripH * 0.4f, fill)
        }
        fill.color = p.text
        val q = 5.5f * d
        val g = 1.8f * d
        for (gx0 in intArrayOf(-1, 1)) for (gy0 in intArrayOf(-1, 1)) {
            val l = if (gx0 < 0) mx - g / 2 - q else mx + g / 2
            val t = if (gy0 < 0) cy - g / 2 - q else cy + g / 2
            tmp.set(l, t, l + q, t + q)
            c.drawRoundRect(tmp, 1.6f * d, 1.6f * d, fill)
        }
    }

    /** "🖼 Captura de pantalla": botón con la miniatura de tu última captura. */
    private fun drawShot(c: Canvas, p: Palette) {
        drawMenuButton(c, p)
        val label = chipLabel ?: return
        val b = shotThumb
        text.textSize = stripH * 0.33f
        val tw = text.measureText(label)
        val h = stripH * 0.78f
        val w = h + 10f * d + tw + 16f * d
        val left = (width - w) / 2f
        val top = (stripH - h) / 2f
        shotRect.set(left, top, left + w, top + h)
        fill.color = if (stripPressed == TOOL_SHOT) p.keyPressed else p.key
        c.drawRoundRect(shotRect, h / 2f, h / 2f, fill)
        // Miniatura en círculo
        val r = h / 2f - 3f * d
        val cx = left + 3f * d + r
        val cy = stripH / 2f
        if (b != null) {
            c.save()
            path.reset()
            path.addCircle(cx, cy, r, Path.Direction.CW)
            c.clipPath(path)
            val sc = maxOf(2 * r / b.width, 2 * r / b.height)
            tmp.set(cx - b.width * sc / 2, cy - b.height * sc / 2, cx + b.width * sc / 2, cy + b.height * sc / 2)
            c.drawBitmap(b, null, tmp, null)
            c.restore()
        } else {
            drawLabel(c, "🖼", cx, cy, r * 1.3f, p.text)
        }
        text.textAlign = Paint.Align.LEFT
        drawLabel(c, label, cx + r + 10f * d, cy, stripH * 0.33f, p.text)
        text.textAlign = Paint.Align.CENTER
    }

    private fun drawSuggestions(c: Canvas, p: Palette) {
        val cellW = (width - menuW) / 3f
        val cy = stripH / 2f
        // Botón ▦ (4 cuadritos) para abrir las herramientas
        drawMenuButton(c, p)
        for (i in 0..2) {
            val left = menuW + i * cellW
            if (stripPressed == 10 + i) {
                fill.color = p.keyPressed
                tmp.set(left + 4f * d, 4f * d, left + cellW - 4f * d, stripH - 4f * d)
                c.drawRoundRect(tmp, radius, radius, fill)
            }
            if (i > 0) {
                fill.color = p.hint
                fill.alpha = 70
                c.drawRect(left - 0.5f * d, stripH * 0.3f, left + 0.5f * d, stripH * 0.7f, fill)
                fill.alpha = 255
            }
            val word = slots[i] ?: continue
            var label = if (i == 0 && leftQuoted) "“$word”" else word
            val bold = i == 1 && centerBold
            text.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val maxW = cellW - 12f * d
            var size = stripH * 0.37f
            text.textSize = size
            var w = text.measureText(label)
            if (w > maxW) {
                size = maxOf(size * maxW / w, stripH * 0.27f)
                text.textSize = size
                w = text.measureText(label)
                while (w > maxW && label.length > 2) {
                    label = label.substring(0, label.length - 2) + "…"
                    w = text.measureText(label)
                }
            }
            drawLabel(c, label, left + cellW / 2f, cy, size, p.text)
            text.typeface = Typeface.DEFAULT
        }
    }

    private fun drawBubble(c: Canvas, r: RectF, p: Palette) {
        fill.color = p.shadow
        r.offset(0f, 2f * d)
        c.drawRoundRect(r, radius * 1.3f, radius * 1.3f, fill)
        r.offset(0f, -2f * d)
        fill.color = p.popup
        c.drawRoundRect(r, radius * 1.3f, radius * 1.3f, fill)
    }

    private fun drawPreview(c: Canvas, k: Key, p: Palette) {
        val w = max(k.rect.width() * 1.3f, 44f * d)
        val h = keyH * 1.15f
        val left = (k.rect.centerX() - w / 2).coerceIn(2f * d, max(2f * d, width - w - 2f * d))
        val top = max(2f * d, k.rect.top - h - 2f * d)
        tmp.set(left, top, left + w, top + h)
        drawBubble(c, tmp, p)
        drawLabel(c, display(k.label), tmp.centerX(), tmp.centerY(), keyH * 0.55f, p.text)
    }

    private fun drawAlts(c: Canvas, p: Palette) {
        val alts = altKeys ?: return
        tmp.set(altLeft, altTop, altLeft + altW, altTop + altH)
        drawBubble(c, tmp, p)
        for (i in alts.indices) {
            val l = altLeft + altPad + i * altCellW
            if (i == altSel) {
                fill.color = p.accent
                tmp.set(l, altTop + altPad, l + altCellW, altTop + altH - altPad)
                c.drawRoundRect(tmp, radius, radius, fill)
            }
            drawLabel(c, alts[i], l + altCellW / 2, altTop + altH / 2, keyH * 0.42f,
                if (i == altSel) p.onAccent else p.text)
        }
    }
}
