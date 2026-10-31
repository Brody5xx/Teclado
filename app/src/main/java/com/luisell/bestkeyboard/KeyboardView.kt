package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
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
        /** Terminaste de cambiar el tamaño del teclado ([scale] = 1 es el normal). */
        fun onResizeDone(scale: Float)
        /** Modo una mano: 0 normal, 1 izquierda, 2 derecha. */
        fun onOneHand(mode: Int) {}
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
        const val TOOL_RESIZE = 22
        const val TOOL_LANG = 23
        const val TOOL_MORE = 24
        const val TOOL_HACKER = 25
        const val TOOL_VAULT = 26
        const val TOOL_CODE = 27
        const val TOOL_ANIM = 28
        const val TOOL_LABEL = 29
        const val TOOL_KEYSIZE = 30
        const val TOOL_SOUND = 31
        const val TOOL_VIB = 32
        const val TOOL_SHAPE = 33
        const val TOOL_STYLE = 34
        const val TOOL_PHRASES = 35
        const val TOOL_READ = 36
        const val TOOL_ONEHAND = 37
        const val TOOL_CALC = 38
        const val TOOL_GIF = 39
        const val MIN_SCALE = 0.72f
        const val MAX_SCALE = 1.4f
        private const val LONG_PRESS_MS = 350L
        /** Cuánto mantener la barra espaciadora para mover el cursor (con la opción de idioma). */
        private const val SPACE_HOLD_MS = 300L
        private const val REPEAT_START_MS = 400L
    }

    var listener: Listener? = null
    private val d = resources.displayMetrics.density

    var palette: Palette = Palette.DARK
        set(v) {
            field = v
            text.typeface = if (v.mono) Typeface.MONOSPACE else Typeface.DEFAULT
            invalidate()
        }
    var keyHeightDp = 52f
        set(v) { if (field != v) { field = v; requestLayout(); invalidate() } }
    var shift = 0 // 0 = minúsculas, 1 = una mayúscula, 2 = bloqueo de mayúsculas
        set(v) { if (field != v) { field = v; invalidate() } }
    var enterAction = EditorInfo.IME_ACTION_NONE
        set(v) { if (field != v) { field = v; invalidate() } }
    /** Deslizar la barra espaciadora cambia el idioma en vez de mover el cursor. */
    var spaceSwitchesLang = false
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
    /** Modo código encendido (para iluminar su botón). */
    var codeOn = false
        set(v) { if (field != v) { field = v; invalidate() } }

    /** Modo una mano: 0 normal, 1 teclas a la izquierda, 2 teclas a la derecha. */
    var oneHand = 0
        set(v) { if (field != v) { field = v; if (width > 0) layoutKeys(); invalidate() } }
    private val handW get() = if (oneHand == 0) 0f else width * 0.18f
    private val keysLeft get() = if (oneHand == 2) handW else 0f

    /** Teclado de computadora: Ctrl/Alt/Shift/Fn encendidos (una vez) o fijos (dos toques). */
    var hackerOn = false
        set(v) { if (field != v) { field = v; invalidate() } }
    private var modsOnce: Set<Int> = emptySet()
    private var modsLocked: Set<Int> = emptySet()
    fun setMods(once: Set<Int>, locked: Set<Int>) {
        if (once == modsOnce && locked == modsLocked) return
        modsOnce = once
        modsLocked = locked
        invalidate()
    }

    /** Tamaño elegido con "Cambiar tamaño" (1 = normal). */
    var heightScale = 1f
        set(v) { val n = v.coerceIn(MIN_SCALE, MAX_SCALE); if (field != n) { field = n; requestLayout(); invalidate() } }
    private val keyH get() = keyHeightDp * heightScale * d
    private val gapX = 5f * d
    private val gapY = 8f * d
    private var radius = 7f * d

    // ---------------------------------------------------------------- Personalización

    /** Forma de las teclas: square / normal / round / pill. */
    var keyShape = "normal"
        set(v) {
            field = v
            radius = when (v) { "square" -> 3f; "round", "outline" -> 13f; "pill" -> 60f; "circle" -> 200f; else -> 7f } * d
            invalidate()
        }
    /** Tamaño de las letras de las teclas (1 = normal). */
    var labelScale = 1f
        set(v) { field = v; invalidate() }
    /** Animación al tocar: none / pop / ripple / glow. */
    var keyAnim = "pop"
    private var labelMul = 1f

    /** Qué sonido toca la última tecla: letra, borrar o espacio/enter. */
    var lastPressKind = Feedback.KIND_KEY
        private set

    private class Anim(val key: Key, val start: Long, val x: Float, val y: Float, val type: String)
    private val anims = ArrayList<Anim>()
    private val animPath = Path()

    /** Los efectos al tocar. */
    object Fx {
        /** Todos los efectos al tocar (el orden es el de los ajustes). */
        val ALL = listOf(
            "none", "random", "pop", "zoom", "lift", "jelly", "shake", "spin", "wave", "ripple", "glow", "flash",
            "neon", "rings", "bubble", "sparks", "stars", "confetti", "hearts", "fire", "snow", "bubbles", "fly",
        )
        /** Los que mueven la tecla (los demás dibujan algo encima). */
        private val MOVES = setOf("pop", "zoom", "lift", "jelly", "shake", "spin")
        fun duration(type: String) = when (type) {
            "ripple" -> 380L; "glow" -> 420L; "sparks" -> 480L; "bubble" -> 420L; "lift" -> 280L; "jelly" -> 340L
            "shake" -> 320L; "spin" -> 340L; "wave" -> 650L; "flash" -> 240L; "neon" -> 620L; "rings" -> 620L
            "stars" -> 560L; "confetti" -> 760L; "hearts" -> 850L; "fire" -> 650L; "snow" -> 950L
            "bubbles" -> 950L; "fly" -> 620L; else -> 230L
        }
        fun moves(type: String) = type in MOVES
    }

    // ---------------------------------------------------------------- Lluvia de código (tema Hacker)

    private class Drop(var y: Float, var speed: Float, val chars: CharArray)
    private var drops: Array<Drop> = emptyArray()
    private var lastRain = 0L
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER }
    private val rnd = java.util.Random()
    private val GLYPHS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾙﾚﾛﾜ0123456789<>{}[]=+*#$%&"

    private fun blend(c: Int, t: Int, f: Float): Int {
        fun ch(sh: Int) = (((c shr sh) and 0xFF) + ((((t shr sh) and 0xFF) - ((c shr sh) and 0xFF)) * f)).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Columnas de letras que caen detrás de las teclas. Solo se mueve mientras el teclado se ve. */
    private fun drawMatrix(c: Canvas) {
        val cell = 14f * d
        val cols = (width / cell).toInt().coerceAtLeast(1)
        if (drops.size != cols) {
            drops = Array(cols) {
                Drop(-rnd.nextFloat() * height, (60f + rnd.nextFloat() * 120f) * d, CharArray(18) { GLYPHS[rnd.nextInt(GLYPHS.length)] })
            }
        }
        val now = SystemClock.uptimeMillis()
        val dt = if (lastRain == 0L) 0f else ((now - lastRain).coerceAtMost(100L)) / 1000f
        lastRain = now
        rainPaint.textSize = 12f * d
        for ((i, dr) in drops.withIndex()) {
            dr.y += dr.speed * dt
            if (dr.y - dr.chars.size * cell > height) {
                dr.y = -rnd.nextFloat() * height * 0.5f
                dr.speed = (60f + rnd.nextFloat() * 120f) * d
            }
            if (rnd.nextInt(12) == 0) dr.chars[rnd.nextInt(dr.chars.size)] = GLYPHS[rnd.nextInt(GLYPHS.length)]
            val x = i * cell + cell / 2
            for (j in dr.chars.indices) {
                val y = dr.y - j * cell
                if (y < -cell || y > height + cell) continue
                val a = if (j == 0) 200 else (150 * (1f - j.toFloat() / dr.chars.size)).toInt()
                val base = if (palette.effect == "matrix-rainbow")
                    android.graphics.Color.HSVToColor(floatArrayOf((i * 360f / drops.size + now / 40f) % 360f, 0.85f, 1f))
                else palette.accent
                // La primera letra de cada columna brilla más (casi blanca)
                val col = if (j == 0) blend(base, 0xFFFFFF, 0.6f) else base
                rainPaint.color = (a shl 24) or (col and 0x00FFFFFF)
                c.drawText(dr.chars[j].toString(), x, y, rainPaint)
            }
        }
        if (isShown) postInvalidateDelayed(45)
    }

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
    /** Con "deslizar cambia el idioma": si mantienes la barra espaciadora, se activa el cursor. */
    private var spaceHold = false
    /** Moviendo el cursor con la barra espaciadora: el teclado se oscurece y dice "Desliza". */
    private var spaceCursor = false
    private val spaceHoldRunnable = Runnable {
        if (activeKey?.code == Codes.SPACE && !spaceDragging) {
            spaceHold = true
            spaceCursor = true
            listener?.onKeyFeedback()
            invalidate()
        }
    }
    /** Tecla que se está repitiendo (borrar o una flecha). */
    private var repeatKey: Key? = null
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val k = repeatKey
            if (k != null && k.code != Codes.DELETE) listener?.onKeyCode(k.code) else listener?.onDeletePress()
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
        spaceCursor = false
        resizing = false
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
        val w = width.toFloat() - handW
        val x0 = keysLeft
        val kh = keyH
        var y = stripH
        for (row in rows) {
            val total = row.fold(0f) { a, k -> a + k.weight }
            var x = x0
            for ((i, k) in row.withIndex()) {
                val kw = w * k.weight / total
                val right = if (i == row.lastIndex) x0 + w else x + kw
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

    /** Botón de herramientas de la esquina: muestra las herramientas aunque haya sugerencias (como Gboard). */
    private var forceTools = false
    private val menuW get() = 48f * d
    private val showingSuggestions get() = hasSuggestions && !forceTools && chipLabel == null

    // Sugerencia de captura de pantalla (botón con la miniatura)
    private var shotThumb: android.graphics.Bitmap? = null
    private var chipLabel: String? = null
    private var chipIcon = Icons.IMAGE
    private val shotRect = RectF()
    private val showingShot get() = chipLabel != null && !forceTools

    /** Botón grande en el centro de la barra (la captura de pantalla, o el aviso del permiso). */
    fun setChip(thumb: android.graphics.Bitmap?, label: String?, icon: Int = Icons.IMAGE) {
        if (shotThumb === thumb && chipLabel == label && chipIcon == icon) return
        shotThumb = thumb
        chipLabel = label
        chipIcon = icon
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

    /** Los botones de la barra de arriba, en el orden que elegiste (todos se pueden mover). */
    private var barTools = intArrayOf(TOOL_EMOJI, TOOL_CLIP, TOOL_TRANSLATE, TOOL_SCAN, TOOL_SPELL, TOOL_EDIT, TOOL_MORE, TOOL_VOICE, TOOL_HIDE)

    fun setBarTools(tools: List<Int>) {
        val n = tools.toIntArray()
        if (!n.contentEquals(barTools)) {
            barTools = n
            invalidate()
        }
    }

    /** Ancho de cada botón: se reparten a lo ancho de la barra. */
    private fun toolStep(): Float = width.toFloat() / maxOf(1, barTools.size)

    private fun toolHit(x: Float): Int {
        val i = (x / toolStep()).toInt()
        return if (i in barTools.indices) barTools[i] else -1
    }

    // ---------------------------------------------------------------- Toques

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (resizing) { resizeTouch(e); return true }
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
        if (oneHand != 0 && (if (oneHand == 1) x >= width - handW else x < handW)) {
            // Botones del costado: arriba cambia de lado, abajo vuelve al teclado completo
            listener?.onKeyFeedback()
            val top = y < stripH + (height - stripH) / 2f
            listener?.onOneHand(if (!top) 0 else if (oneHand == 1) 2 else 1)
            return
        }
        val key = findKey(x, y) ?: return
        activeKey = key
        if (forceTools) { forceTools = false; invalidate(0, 0, width, stripH.toInt() + 1) }
        lastPressKind = when (key.code) {
            Codes.DELETE -> Feedback.KIND_DELETE
            Codes.SPACE, Codes.ENTER -> Feedback.KIND_SPACE
            else -> Feedback.KIND_KEY
        }
        if (keyAnim != "none") {
            anims.removeAll { it.key === key }
            val type = if (keyAnim == "random") Fx.ALL.drop(2).random() else keyAnim
            anims.add(Anim(key, SystemClock.uptimeMillis(), x, y, type))
        }
        listener?.onKeyFeedback()
        lastPressKind = Feedback.KIND_KEY
        // ¿Podría empezar un trazo? (solo desde una letra)
        gestureCandidate = swipeEnabled && key.code == 0 && key.label.length == 1 && key.label[0].isLetter()
        gesturing = false
        gCount = 0
        downY = y
        if (gestureCandidate) addGesturePoint(x, y)
        when (key.code) {
            Codes.DELETE -> {
                repeatKey = key
                listener?.onDeletePress()
                repeatCount = 0
                handler.postDelayed(repeatRunnable, REPEAT_START_MS)
            }
            Codes.SHIFT -> listener?.onKeyCode(Codes.SHIFT)
            Codes.SPACE -> {
                spaceHold = false
                if (spaceSwitchesLang) handler.postDelayed(spaceHoldRunnable, SPACE_HOLD_MS)
            }
            else -> if (key.repeatable) {
                repeatKey = key
                listener?.onKeyCode(key.code)
                repeatCount = 0
                handler.postDelayed(repeatRunnable, REPEAT_START_MS)
            } else if (key.alts.isNotEmpty() || key.code == Codes.LANG || key.emojiKey) {
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
            if (spaceSwitchesLang && !spaceHold) {
                // Deslizar rápido a un lado cambia el idioma (una sola vez por deslizamiento).
                // Si primero mantienes presionado, se mueve el cursor (más abajo).
                if (!spaceDragging && abs(x - downX) > 36f * d) {
                    handler.removeCallbacks(spaceHoldRunnable)
                    spaceDragging = true
                    listener?.onKeyFeedback()
                    listener?.onKeyCode(Codes.LANG)
                }
                return
            }
            // Deslizar sobre la barra espaciadora mueve el cursor
            if (!spaceDragging && abs(x - downX) > 18f * d) {
                spaceDragging = true
                spaceLastX = x
                if (!spaceCursor) { spaceCursor = true; invalidate() }
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
        handler.removeCallbacks(spaceHoldRunnable)
        if (spaceCursor) { spaceCursor = false; invalidate() }
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
            else -> if (!key.repeatable) listener?.onKeyCode(key.code)
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
        if (p.bg2 != p.bg) {
            // Fondo en degradado (temas Océano, Neón, Aurora…)
            fill.shader = android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), p.bg, p.bg2, android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
            fill.shader = null
        } else c.drawColor(p.bg)
        if (p.effect.startsWith("matrix")) drawMatrix(c) else lastRain = 0L
        drawStrip(c, p)
        if (oneHand != 0) drawHandSide(c, p)
        val now = SystemClock.uptimeMillis()
        labelMul = labelScale
        // Ola: las teclas vecinas de la fila saltan una tras otra
        val waves = anims.filter { it.type == "wave" }
        for ((ri, row) in rows.withIndex()) for ((ci, k) in row.withIndex()) {
            val a = if (anims.isEmpty()) null else anims.firstOrNull { it.key === k }
            var waveDy = 0f
            for (w in waves) {
                val wi = row.indexOf(w.key)
                if (wi < 0) continue
                val delay = kotlin.math.abs(ci - wi) * 45L
                val tt = ((now - w.start - delay).toFloat() / 260f)
                if (tt in 0f..1f) waveDy += -5f * d * kotlin.math.sin(Math.PI * tt).toFloat()
            }
            if ((a == null || !Fx.moves(a.type)) && waveDy == 0f) { drawKey(c, k, p); continue }
            c.save()
            if (waveDy != 0f) c.translate(0f, waveDy)
            if (a != null && Fx.moves(a.type)) {
                val t = ((now - a.start).toFloat() / Fx.duration(a.type)).coerceIn(0f, 1f)
                val cx = k.rect.centerX()
                val cy = k.rect.centerY()
                val damp = 1f - t
                when (a.type) {
                    "pop" -> {
                        // Se hunde un poquito y rebota
                        val sc = if (t < 0.35f) 1f - 0.1f * (t / 0.35f) else {
                            val u = (t - 0.35f) / 0.65f - 1f
                            0.9f + 0.1f * (1f + 2.70158f * u * u * u + 1.70158f * u * u)
                        }
                        c.scale(sc, sc, cx, cy)
                    }
                    "zoom" -> { val sc = 1f + 0.14f * kotlin.math.sin(Math.PI * t).toFloat(); c.scale(sc, sc, cx, cy) }
                    "lift" -> c.translate(0f, -6f * d * kotlin.math.sin(Math.PI * t).toFloat())
                    "jelly" -> {
                        // Gelatina: se aplasta y se estira
                        val w = 0.14f * kotlin.math.sin(2 * Math.PI * t).toFloat() * damp
                        c.scale(1f + w, 1f - w, cx, cy)
                    }
                    "shake" -> c.translate(4f * d * kotlin.math.sin(6 * Math.PI * t).toFloat() * damp, 0f)
                    "spin" -> c.rotate(14f * kotlin.math.sin(3 * Math.PI * t).toFloat() * damp, cx, cy)
                }
            }
            drawKey(c, k, p)
            c.restore()
        }
        // Lo que se dibuja encima (ondas, chispas, corazones…) va después, para que no lo tapen otras teclas
        for (a in anims) {
            if (Fx.moves(a.type) || a.type == "wave") continue
            val t = ((now - a.start).toFloat() / Fx.duration(a.type)).coerceIn(0f, 1f)
            drawKeyAnim(c, a.key, a, t, p)
        }
        labelMul = 1f
        if (anims.isNotEmpty()) {
            anims.removeAll { now - it.start > Fx.duration(it.type) }
            if (anims.isNotEmpty()) postInvalidateOnAnimation()
        }
        if (gesturing && gCount > 1) drawTrail(c, p)
        val ak = activeKey
        if (altKeys != null) drawAlts(c, p)
        else if (ak != null && showPreview && ak.code == 0 && !longPressed) drawPreview(c, ak, p)
        if (spaceCursor) drawSlideHint(c)
        if (resizing) drawResize(c, p)
    }

    /** El costado vacío del modo una mano: flecha para cambiar de lado y botón para agrandar. */
    private fun drawHandSide(c: Canvas, p: Palette) {
        val left = if (oneHand == 1) width - handW else 0f
        val cx = left + handW / 2f
        val midY = stripH + (height - stripH) / 2f
        val s = minOf(handW, keyH) * 0.22f
        val color = p.hint
        st(color)
        // Flecha hacia el otro lado
        val dir = if (oneHand == 1) 1f else -1f
        val ay = stripH + (midY - stripH) / 2f
        c.drawLine(cx - s * dir, ay, cx + s * dir, ay, stroke)
        c.drawLine(cx + s * dir, ay, cx + s * 0.3f * dir, ay - s * 0.6f, stroke)
        c.drawLine(cx + s * dir, ay, cx + s * 0.3f * dir, ay + s * 0.6f, stroke)
        // Agrandar: dos esquinas que se abren
        val by = midY + (height - midY) / 2f
        c.drawLine(cx - s, by - s, cx - s * 0.3f, by - s, stroke)
        c.drawLine(cx - s, by - s, cx - s, by - s * 0.3f, stroke)
        c.drawLine(cx + s, by + s, cx + s * 0.3f, by + s, stroke)
        c.drawLine(cx + s, by + s, cx + s, by + s * 0.3f, stroke)
        c.drawLine(cx - s, by - s, cx + s, by + s, stroke)
    }

    /** Mientras mueves el cursor con la barra espaciadora: teclas oscurecidas y "Desliza". */
    private fun drawSlideHint(c: Canvas) {
        fill.color = 0x99000000.toInt()
        c.drawRect(0f, stripH, width.toFloat(), height.toFloat(), fill)
        text.isFakeBoldText = true
        drawLabel(c, L.t("Desliza", "Slide"), width / 2f, stripH + (height - stripH) * 0.42f, keyH * 0.5f, 0xFFFFFFFF.toInt())
        text.isFakeBoldText = false
    }

    // ---------------------------------------------------------------- Cambiar tamaño

    /** Modo "Cambiar tamaño": arrastra hacia arriba o abajo para hacer el teclado más alto o más bajo. */
    var resizing = false
        private set
    private var resizeStartY = 0f
    private var resizeStartScale = 1f
    private var resizeBtn = -1 // 0 = restablecer, 1 = listo
    private val resetRect = RectF()
    private val doneRect = RectF()

    fun startResize() {
        reset()
        resizing = true
        invalidate()
    }

    private fun finishResize() {
        resizing = false
        invalidate()
        listener?.onResizeDone(heightScale)
    }

    private fun drawResize(c: Canvas, p: Palette) {
        val w = width.toFloat()
        val h = height.toFloat()
        fill.color = 0xAA000000.toInt()
        c.drawRect(0f, 0f, w, h, fill)
        // Borde azul y agarradera arriba
        stroke.color = p.accent
        stroke.strokeWidth = 2.5f * d
        tmp.set(2f * d, 2f * d, w - 2f * d, h - 2f * d)
        c.drawRoundRect(tmp, 10f * d, 10f * d, stroke)
        fill.color = p.accent
        tmp.set(w / 2 - 28f * d, 6f * d, w / 2 + 28f * d, 12f * d)
        c.drawRoundRect(tmp, 3f * d, 3f * d, fill)
        // Flechas ↕ y texto
        val cy = h * 0.36f
        drawLabel(c, "↕", w / 2, cy - keyH * 0.55f, keyH * 0.6f, 0xFFFFFFFF.toInt())
        drawLabel(c, L.t("Desliza hacia arriba o abajo", "Drag up or down"), w / 2, cy + keyH * 0.15f, keyH * 0.3f, 0xFFFFFFFF.toInt())
        // Botones
        val bw = minOf(140f * d, w * 0.38f)
        val bh = 44f * d
        val by = h * 0.62f
        resetRect.set(w / 2 - 8f * d - bw, by, w / 2 - 8f * d, by + bh)
        doneRect.set(w / 2 + 8f * d, by, w / 2 + 8f * d + bw, by + bh)
        fill.color = if (resizeBtn == 0) p.keyPressed else p.special
        c.drawRoundRect(resetRect, bh / 2, bh / 2, fill)
        fill.color = if (resizeBtn == 1) p.accentPressed else p.accent
        c.drawRoundRect(doneRect, bh / 2, bh / 2, fill)
        drawLabel(c, L.t("Restablecer", "Reset"), resetRect.centerX(), resetRect.centerY(), 15f * d, p.text)
        drawLabel(c, L.t("Listo", "Done"), doneRect.centerX(), doneRect.centerY(), 15f * d, p.onAccent)
    }

    private fun resizeTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resizeBtn = when {
                    resetRect.contains(e.x, e.y) -> 0
                    doneRect.contains(e.x, e.y) -> 1
                    else -> -1
                }
                resizeStartY = e.rawY
                resizeStartScale = heightScale
                if (resizeBtn >= 0) listener?.onKeyFeedback()
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (resizeBtn < 0 && rows.isNotEmpty()) {
                // Hacia arriba (dedo sube) = más alto. Se usa rawY porque la vista cambia de tamaño.
                val baseRows = rows.size * keyHeightDp * d
                heightScale = resizeStartScale + (resizeStartY - e.rawY) / baseRows
            }
            MotionEvent.ACTION_UP -> {
                val b = resizeBtn
                resizeBtn = -1
                when (b) {
                    0 -> if (resetRect.contains(e.x, e.y)) heightScale = 1f
                    1 -> if (doneRect.contains(e.x, e.y)) finishResize()
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { resizeBtn = -1; invalidate() }
        }
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

    /** El fondo de la tecla según la forma elegida (normal, círculo, solo borde, transparente…). */
    private fun drawKeyBg(c: Canvas, k: Key, bg: Int, pressed: Boolean, p: Palette) {
        val r = k.rect
        if (keyShape == "circle" && (k.code == 0 || k.code == Codes.SHIFT || k.code == Codes.DELETE)) {
            // Teclas redondas (las anchas como espacio se quedan en píldora)
            val side = minOf(r.width(), r.height())
            tmp.set(r.centerX() - side / 2, r.centerY() - side / 2, r.centerX() + side / 2, r.centerY() + side / 2)
        } else tmp.set(r)
        val solid = k.code == Codes.ENTER || bg == p.accent || bg == p.accentPressed
        when {
            keyShape == "flat" && !pressed && !solid -> Unit // sin fondo: solo la letra
            keyShape == "outline" && !pressed && !solid -> {
                stroke.color = if (k.special) p.hint else (p.text and 0x00FFFFFF) or 0x55000000
                stroke.strokeWidth = 1.4f * d
                c.drawRoundRect(tmp, radius, radius, stroke)
            }
            else -> {
                if (!p.isDark) {
                    fill.color = p.shadow
                    tmp.offset(0f, 1f * d)
                    c.drawRoundRect(tmp, radius, radius, fill)
                    tmp.offset(0f, -1f * d)
                }
                fill.color = bg
                c.drawRoundRect(tmp, radius, radius, fill)
            }
        }
        // Tema Hacker: borde verde de vidrio (brilla al tocar)
        if (p.effect.startsWith("matrix")) {
            stroke.color = if (pressed) p.accent else (p.accent and 0x00FFFFFF) or 0x55000000
            stroke.strokeWidth = (if (pressed) 2f else 1f) * d
            c.drawRoundRect(tmp, radius, radius, stroke)
        }
    }

    private fun alphaOf(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)
    private fun hue(h: Float) = android.graphics.Color.HSVToColor(floatArrayOf(((h % 360f) + 360f) % 360f, 0.8f, 1f))

    /** Estrella de [n] puntas (para el efecto Estrellas). */
    private fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        animPath.reset()
        for (i in 0 until 10) {
            val rr = if (i % 2 == 0) r else r * 0.45f
            val ang = Math.PI / 5 * i - Math.PI / 2
            val px = x + (kotlin.math.cos(ang) * rr).toFloat()
            val py = y + (kotlin.math.sin(ang) * rr).toFloat()
            if (i == 0) animPath.moveTo(px, py) else animPath.lineTo(px, py)
        }
        animPath.close()
        fill.color = color
        c.drawPath(animPath, fill)
    }

    /** Corazoncito relleno. */
    private fun heart(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        animPath.reset()
        animPath.moveTo(x, y + s * 0.85f)
        animPath.cubicTo(x - s * 1.25f, y, x - s * 0.75f, y - s * 1.05f, x, y - s * 0.4f)
        animPath.cubicTo(x + s * 0.75f, y - s * 1.05f, x + s * 1.25f, y, x, y + s * 0.85f)
        animPath.close()
        fill.color = color
        c.drawPath(animPath, fill)
    }

    /** Dibuja el efecto [a] de la tecla [k] en el momento [t] (0 = empieza, 1 = termina). */
    private fun drawKeyAnim(c: Canvas, k: Key, a: Anim, t: Float, p: Palette) {
        val seed = (a.start % 997).toInt()
        val rnd = java.util.Random(a.start)
        val fade = 1f - t
        when (a.type) {
            "ripple" -> {
                c.save()
                animPath.reset()
                animPath.addRoundRect(k.rect, radius, radius, Path.Direction.CW)
                c.clipPath(animPath)
                val maxR = kotlin.math.hypot(k.rect.width(), k.rect.height())
                fill.color = alphaOf(p.accent, fade * 0.45f)
                c.drawCircle(a.x, a.y, maxR * (0.15f + 0.95f * t), fill)
                c.restore()
            }
            "glow" -> {
                fill.color = alphaOf(p.accent, fade * 0.19f)
                c.drawRoundRect(k.rect, radius, radius, fill)
                stroke.color = alphaOf(p.accent, fade)
                stroke.strokeWidth = 2.5f * d
                c.drawRoundRect(k.rect, radius, radius, stroke)
            }
            "flash" -> {
                fill.color = alphaOf(0xFFFFFFFF.toInt(), fade * 0.65f)
                c.drawRoundRect(k.rect, radius, radius, fill)
            }
            "neon" -> {
                // Borde que cambia de color como un letrero de neón
                for (w in 0..2) {
                    stroke.color = alphaOf(hue(t * 360f + seed + w * 40f), fade * (0.9f - w * 0.25f))
                    stroke.strokeWidth = (2f + w * 2.5f) * d
                    c.drawRoundRect(k.rect, radius, radius, stroke)
                }
            }
            "sparks" -> {
                for (i in 0 until 9) {
                    val ang = (i * 40 + seed * 7) * Math.PI / 180.0
                    val dist = (10f + 26f * t) * d * (0.7f + ((seed + i * 13) % 10) / 20f)
                    val x = a.x + (kotlin.math.cos(ang) * dist).toFloat()
                    val y = a.y + (kotlin.math.sin(ang) * dist).toFloat() - 6f * d * t
                    fill.color = alphaOf(p.accent, fade)
                    c.drawCircle(x, y, (2.6f - 1.6f * t) * d, fill)
                }
            }
            "bubble" -> {
                stroke.color = alphaOf(p.accent, fade * 0.86f)
                stroke.strokeWidth = (2.5f - 1.5f * t) * d
                val grow = 12f * d * t
                tmp.set(k.rect.left - grow, k.rect.top - grow, k.rect.right + grow, k.rect.bottom + grow)
                c.drawRoundRect(tmp, radius + grow, radius + grow, stroke)
            }
            "rings" -> {
                // Tres anillos que salen uno tras otro
                for (i in 0..2) {
                    val tt = ((t - i * 0.18f) / 0.64f).coerceIn(0f, 1f)
                    if (tt <= 0f || tt >= 1f) continue
                    stroke.color = alphaOf(p.accent, (1f - tt) * 0.9f)
                    stroke.strokeWidth = 2f * d
                    c.drawCircle(a.x, a.y, (6f + 34f * tt) * d, stroke)
                }
            }
            "stars" -> {
                for (i in 0 until 6) {
                    val ang = (i * 60 + seed) * Math.PI / 180.0
                    val dist = (8f + 30f * t) * d
                    star(c, a.x + (kotlin.math.cos(ang) * dist).toFloat(), a.y + (kotlin.math.sin(ang) * dist).toFloat(),
                        (5f - 2f * t) * d, alphaOf(if (i % 2 == 0) 0xFFFFD54F.toInt() else p.accent, fade))
                }
            }
            "confetti" -> {
                // Papelitos de colores que saltan y caen
                for (i in 0 until 16) {
                    val vx = (rnd.nextFloat() - 0.5f) * 70f * d
                    val vy = -(30f + rnd.nextFloat() * 50f) * d
                    val x = a.x + vx * t
                    val y = a.y + vy * t + 90f * d * t * t
                    val sz = (2.5f + rnd.nextFloat() * 2f) * d
                    c.save()
                    c.rotate(t * 540f * (if (i % 2 == 0) 1 else -1) + i * 30f, x, y)
                    fill.color = alphaOf(hue(i * 47f + seed), fade + 0.2f)
                    c.drawRect(x - sz, y - sz * 0.6f, x + sz, y + sz * 0.6f, fill)
                    c.restore()
                }
            }
            "hearts" -> {
                for (i in 0 until 3) {
                    val tt = ((t - i * 0.12f) / 0.76f).coerceIn(0f, 1f)
                    if (tt <= 0f) continue
                    val x = a.x + (i - 1) * 12f * d + 5f * d * kotlin.math.sin(tt * 8f + i).toFloat()
                    val y = a.y - 44f * d * tt
                    heart(c, x, y, (4f + 3f * tt) * d, alphaOf(if (i == 1) 0xFFFF4081.toInt() else 0xFFFF80AB.toInt(), 1f - tt))
                }
            }
            "fire" -> {
                // Chispitas de fuego que suben y cambian de amarillo a rojo
                for (i in 0 until 12) {
                    val tt = ((t - (i % 4) * 0.08f) / 0.76f).coerceIn(0f, 1f)
                    if (tt <= 0f) continue
                    val x = k.rect.left + k.rect.width() * (0.2f + 0.6f * rnd.nextFloat()) + 3f * d * kotlin.math.sin(tt * 12f + i).toFloat()
                    val y = k.rect.top + k.rect.height() * 0.3f - 34f * d * tt * (0.6f + rnd.nextFloat() * 0.6f)
                    val col = if (tt < 0.35f) 0xFFFFE082.toInt() else if (tt < 0.7f) 0xFFFF9800.toInt() else 0xFFFF3D00.toInt()
                    fill.color = alphaOf(col, 1f - tt)
                    c.drawCircle(x, y, (3.4f - 2.4f * tt) * d, fill)
                }
            }
            "snow" -> {
                // Copitos que caen despacito
                for (i in 0 until 9) {
                    val x0 = k.rect.left + k.rect.width() * rnd.nextFloat()
                    val x = x0 + 6f * d * kotlin.math.sin(t * 6f + i).toFloat()
                    val y = k.rect.top + k.rect.height() * 0.2f + 40f * d * t * (0.6f + rnd.nextFloat() * 0.6f)
                    fill.color = alphaOf(0xFFFFFFFF.toInt(), fade * 0.9f)
                    c.drawCircle(x, y, (1.6f + rnd.nextFloat() * 1.6f) * d, fill)
                }
            }
            "bubbles" -> {
                // Burbujitas que suben moviéndose de lado a lado
                for (i in 0 until 6) {
                    val tt = ((t - i * 0.07f) / 0.65f).coerceIn(0f, 1f)
                    if (tt <= 0f) continue
                    val x = a.x + (rnd.nextFloat() - 0.5f) * 24f * d + 5f * d * kotlin.math.sin(tt * 10f + i).toFloat()
                    val y = a.y - 50f * d * tt
                    stroke.color = alphaOf(p.accent, (1f - tt) * 0.9f)
                    stroke.strokeWidth = 1.3f * d
                    c.drawCircle(x, y, (2f + 5f * tt) * d, stroke)
                }
            }
            "fly" -> {
                // La letra sale volando hacia arriba
                if (k.code == 0) {
                    val sz = keyH * 0.42f * (1f + 0.8f * t)
                    drawLabel(c, display(k.label), k.rect.centerX(), k.rect.centerY() - 40f * d * t, sz / labelMul.coerceAtLeast(0.01f),
                        alphaOf(p.accent, fade))
                }
            }
        }
    }

    private fun drawLabel(c: Canvas, s: String, cx: Float, cy: Float, sizeIn: Float, color: Int) {
        val size = sizeIn * labelMul
        text.textSize = size
        text.color = color
        text.getFontMetrics(fm)
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text)
    }

    private fun drawKey(c: Canvas, k: Key, p: Palette) {
        val pressed = k === activeKey
        val modLocked = k.code in modsLocked
        val modOnce = k.code in modsOnce
        val rainbow = p.rainbowKeys && !k.special && k.code != Codes.ENTER
        val bg = when {
            k.code == Codes.ENTER -> if (pressed) p.accentPressed else p.accent
            modLocked -> if (pressed) p.accentPressed else p.accent
            rainbow -> android.graphics.Color.HSVToColor(floatArrayOf(
                (k.rect.centerX() / width.coerceAtLeast(1) * 300f + k.rect.top / 6f) % 360f, if (pressed) 0.45f else 0.7f, if (p.isDark) 0.75f else 0.9f))
            pressed -> p.keyPressed
            k.special -> p.special
            else -> p.key
        }
        drawKeyBg(c, k, bg, pressed, p)

        val cx = k.rect.centerX()
        val cy = k.rect.centerY()
        val s = keyH * 0.19f
        val fg = if (k.code == Codes.ENTER || modLocked) p.onAccent else if (modOnce) p.accent else p.text
        if (modOnce) {
            // Encendido para la próxima tecla: borde azul
            stroke.color = p.accent
            stroke.strokeWidth = 2f * d
            c.drawRoundRect(k.rect, radius, radius, stroke)
        }
        k.topHint?.let { drawLabel(c, it, k.rect.right - k.rect.width() * 0.2f, k.rect.top + keyH * 0.2f, keyH * 0.2f, p.hint) }
        when (k.code) {
            0 -> {
                drawFit(c, display(k.label), cx, cy, if (k.label.length > 1) keyH * 0.3f else keyH * 0.42f, fg, k.rect.width())
                if (k.emojiKey) {
                    drawLabel(c, "☺", k.rect.right - keyH * 0.2f, k.rect.top + keyH * 0.2f, keyH * 0.24f, p.hint)
                }
            }
            Codes.SPACE -> drawLabel(c, if (spaceSwitchesLang) "‹  $spaceLabel  ›" else spaceLabel, cx, cy, keyH * 0.26f, p.hint)
            Codes.SHIFT -> drawShift(c, cx, cy, s, fg)
            Codes.DELETE -> drawDelete(c, cx, cy, minOf(s, k.rect.width() * 0.22f), fg)
            Codes.HSHIFT -> drawShift(c, cx, cy, minOf(s, k.rect.width() * 0.25f), fg)
            Codes.ENTER -> drawEnter(c, cx, cy, s, fg)
            Codes.LANG -> drawGlobe(c, cx, cy, s, fg)
            else -> drawFit(c, k.label, cx, cy, if (Codes.isHKey(k.code) && k.label.length == 1) keyH * 0.36f else keyH * 0.29f, fg, k.rect.width())
        }
    }

    /** Como drawLabel, pero achica el texto si no cabe en la tecla ("PgUp" en teclas angostas). */
    private fun drawFit(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, w: Float) {
        text.textSize = size * labelMul
        val tw = text.measureText(s)
        val max = w * 0.86f
        drawLabel(c, s, cx, cy, if (tw > max && tw > 0) size * max / tw else size, color)
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
        val step = toolStep()
        val tools = barTools
        val xs = FloatArray(tools.size) { step * (it + 0.5f) }
        for (n in tools.indices) {
            val i = tools[n]
            val cx = xs[n]
            if (stripPressed == i) {
                fill.color = p.keyPressed
                c.drawCircle(cx, cy, stripH * 0.4f, fill)
            }
            val kind = when (i) {
                TOOL_SETTINGS -> Icons.SETTINGS
                TOOL_THEME -> Icons.THEME
                TOOL_EMOJI -> Icons.EMOJI
                TOOL_CLIP -> Icons.CLIP
                TOOL_TRANSLATE -> Icons.TRANSLATE
                TOOL_EDIT -> Icons.EDIT
                TOOL_SPELL -> Icons.SPELL
                TOOL_SCAN -> Icons.SCAN
                TOOL_VOICE -> Icons.MIC
                TOOL_HIDE -> Icons.HIDE
                TOOL_MORE -> Icons.MORE
                TOOL_RESIZE -> Icons.RESIZE
                TOOL_LANG -> Icons.GLOBE
                TOOL_HACKER -> Icons.HACKER
                TOOL_VAULT -> Icons.LOCK
                TOOL_CODE -> Icons.CODE
                TOOL_ANIM -> Icons.RIPPLE
                TOOL_LABEL -> Icons.TEXTSIZE
                TOOL_KEYSIZE -> Icons.KEYSIZE
                TOOL_SOUND -> Icons.SPEAKER
                TOOL_VIB -> Icons.VIBRATE
                TOOL_SHAPE -> Icons.SHAPES
                TOOL_STYLE -> Icons.BRUSH
                TOOL_PHRASES -> Icons.BUBBLE
                TOOL_READ -> Icons.READ
                TOOL_ONEHAND -> Icons.ONEHAND
                TOOL_CALC -> Icons.CALC
                TOOL_GIF -> Icons.GIF
                else -> continue
            }
            val on = (i == TOOL_TRANSLATE && translateOn) || (i == TOOL_SPELL && spellOn) || (i == TOOL_HACKER && hackerOn) || (i == TOOL_CODE && codeOn) || (i == TOOL_ONEHAND && oneHand != 0)
            val bgc = if (stripPressed == i) p.keyPressed else p.bg
            Icons.draw(c, kind, cx, cy, s, if (on) p.accent else p.hint, bgc, d)
        }
    }

    /** Botón de la esquina que abre las herramientas (panel de control). */
    private fun drawMenuButton(c: Canvas, p: Palette) {
        val cy = stripH / 2f
        val mx = menuW / 2f + 2f * d
        val pressed = stripPressed == TOOL_MENU
        if (pressed) {
            fill.color = p.keyPressed
            c.drawCircle(mx, cy, stripH * 0.4f, fill)
        }
        Icons.draw(c, Icons.MENU, mx, cy, stripH * 0.22f, p.text, if (pressed) p.keyPressed else p.bg, d)
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
            Icons.draw(c, chipIcon, cx, cy, r * 0.6f, p.text, p.key, d)
        }
        text.textAlign = Paint.Align.LEFT
        drawLabel(c, label, cx + r + 10f * d, cy, stripH * 0.33f, p.text)
        text.textAlign = Paint.Align.CENTER
    }

    private fun drawSuggestions(c: Canvas, p: Palette) {
        val cellW = (width - menuW) / 3f
        val cy = stripH / 2f
        // Botón de la esquina para abrir las herramientas
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
