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
        /** Acomodaste los botones de la barra de arriba (barra, más opciones). */
        fun onToolsReordered(bar: List<Int>, more: List<Int>) {}
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
        const val TOOL_TICKET = 40
        const val TOOL_REPORT = 41
        const val TOOL_FORMAL = 42
        const val TOOL_FLOAT = 43
        const val TOOL_REVIEW = 44
        const val TOOL_BARCODE = 45
        const val TOOL_STAMP = 46
        const val TOOL_NOTEBOOK = 47
        const val TOOL_TICKETS = 48
        const val TOOL_WORD = 49
        const val TOOL_WORKDAY = 50
        const val TOOL_MATERIALS = 51
        const val TOOL_REPLY = 52
        const val TOOL_SITES = 53
        const val TOOL_STATUS = 54
        const val TOOL_WEEK = 55
        const val TOOL_DRIVE = 56
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

    /** Teclado flotante encendido (para iluminar su botón). */
    var floatOn = false
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
            "drop", "swing", "lightning", "petals", "music", "emojis", "ink", "pulse", "orbit", "rainbow", "pixel",
            "flip", "heartbeat", "rubber", "shards", "smoke", "spiral", "flower", "coin", "balloon", "butterfly", "glitch", "splash",
        )
        /** Los que mueven la tecla (los demás dibujan algo encima). */
        private val MOVES = setOf("pop", "zoom", "lift", "jelly", "shake", "spin", "drop", "swing", "flip", "heartbeat", "rubber")
        fun duration(type: String) = when (type) {
            "ripple" -> 380L; "glow" -> 420L; "sparks" -> 480L; "bubble" -> 420L; "lift" -> 280L; "jelly" -> 340L
            "shake" -> 320L; "spin" -> 340L; "wave" -> 650L; "flash" -> 240L; "neon" -> 620L; "rings" -> 620L
            "stars" -> 560L; "confetti" -> 760L; "hearts" -> 850L; "fire" -> 650L; "snow" -> 950L
            "bubbles" -> 950L; "fly" -> 620L; "drop" -> 300L; "swing" -> 520L; "lightning" -> 360L; "petals" -> 1000L
            "music" -> 900L; "emojis" -> 800L; "ink" -> 520L; "pulse" -> 520L; "orbit" -> 700L; "rainbow" -> 600L
            "pixel" -> 520L; "flip" -> 360L; "heartbeat" -> 520L; "rubber" -> 420L; "shards" -> 560L; "smoke" -> 900L
            "spiral" -> 700L; "flower" -> 650L; "coin" -> 700L; "balloon" -> 1100L; "butterfly" -> 1000L; "glitch" -> 320L
            "splash" -> 560L; else -> 230L
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

    // ---------------------------------------------------------------- Fondos animados (temas ✨)

    /** Una partícula del fondo: copo, estrella, burbuja, luciérnaga, gota, brasa o pétalo. */
    private class Part(var x: Float, var y: Float, var vx: Float, var vy: Float, var r: Float, var ph: Float)
    private var parts: Array<Part> = emptyArray()
    private var partsFor = ""
    private var lastAmb = 0L

    private fun newPart(effect: String, anywhere: Boolean): Part {
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        val x = rnd.nextFloat() * w
        return when (effect) {
            "snow" -> Part(x, if (anywhere) rnd.nextFloat() * h else -6f * d, 0f, (18f + rnd.nextFloat() * 36f) * d, (1.2f + rnd.nextFloat() * 2.2f) * d, rnd.nextFloat() * 6f)
            "rain" -> Part(x, if (anywhere) rnd.nextFloat() * h else -20f * d, -60f * d, (420f + rnd.nextFloat() * 320f) * d, (8f + rnd.nextFloat() * 10f) * d, 0f)
            "bubbles" -> Part(x, if (anywhere) rnd.nextFloat() * h else h + 10f * d, 0f, -(16f + rnd.nextFloat() * 30f) * d, (2f + rnd.nextFloat() * 5f) * d, rnd.nextFloat() * 6f)
            "embers" -> Part(x, if (anywhere) rnd.nextFloat() * h else h + 4f * d, (rnd.nextFloat() - 0.5f) * 20f * d, -(25f + rnd.nextFloat() * 55f) * d, (1.2f + rnd.nextFloat() * 2f) * d, rnd.nextFloat() * 6f)
            "fireflies" -> Part(x, rnd.nextFloat() * h, (rnd.nextFloat() - 0.5f) * 24f * d, (rnd.nextFloat() - 0.5f) * 24f * d, (1.8f + rnd.nextFloat() * 1.6f) * d, rnd.nextFloat() * 6f)
            "petals" -> Part(x, if (anywhere) rnd.nextFloat() * h else -8f * d, 12f * d, (22f + rnd.nextFloat() * 26f) * d, (3f + rnd.nextFloat() * 3f) * d, rnd.nextFloat() * 360f)
            "hearts" -> Part(x, if (anywhere) rnd.nextFloat() * h else h + 10f * d, 0f, -(14f + rnd.nextFloat() * 22f) * d, (3.5f + rnd.nextFloat() * 4f) * d, rnd.nextFloat() * 6f)
            "leaves" -> Part(x, if (anywhere) rnd.nextFloat() * h else -10f * d, 8f * d, (20f + rnd.nextFloat() * 28f) * d, (4f + rnd.nextFloat() * 3f) * d, rnd.nextFloat() * 360f)
            "confetti" -> Part(x, if (anywhere) rnd.nextFloat() * h else -8f * d, (rnd.nextFloat() - 0.5f) * 20f * d, (30f + rnd.nextFloat() * 40f) * d, (2.2f + rnd.nextFloat() * 2f) * d, rnd.nextFloat() * 360f)
            "meteors" -> Part(rnd.nextFloat() * w * 1.2f - w * 0.3f, -rnd.nextFloat() * h * 2f, 260f * d, 170f * d, (30f + rnd.nextFloat() * 40f) * d, 0f)
            "bokeh" -> Part(x, rnd.nextFloat() * h, (rnd.nextFloat() - 0.5f) * 10f * d, (rnd.nextFloat() - 0.5f) * 10f * d, (10f + rnd.nextFloat() * 22f) * d, rnd.nextFloat() * 360f)
            "glitter" -> Part(x, rnd.nextFloat() * h, 0f, 0f, (2f + rnd.nextFloat() * 3f) * d, rnd.nextFloat() * 6f)
            else -> Part(x, rnd.nextFloat() * h, 0f, 0f, (0.6f + rnd.nextFloat() * 1.6f) * d, rnd.nextFloat() * 6f) // estrellas
        }
    }

    /** Fondos que no son partículas (olas, lava, circuito, synthwave, nubes, arcoíris vivo). Devuelve true si dibujó. */
    private fun drawScene(c: Canvas, p: Palette, effect: String, w: Float, h: Float, t: Float): Boolean {
        when (effect) {
            "waves" -> {
                // Olas que se mueven abajo del teclado
                for (b in 0..2) {
                    animPath.reset()
                    val base = h * (0.55f + b * 0.14f)
                    animPath.moveTo(0f, h)
                    var x = 0f
                    while (x <= w + 12f * d) {
                        val y = base + (8f + b * 3f) * d * kotlin.math.sin(x / (46f * d) + t * (1.1f + b * 0.4f) * (if (b % 2 == 0) 1 else -1) + b).toFloat()
                        animPath.lineTo(x, y)
                        x += 10f * d
                    }
                    animPath.lineTo(w, h)
                    animPath.close()
                    fill.color = alphaOf(if (b == 1) 0xFFFFFFFF.toInt() else p.accent, 0.10f + b * 0.05f)
                    c.drawPath(animPath, fill)
                }
            }
            "lava" -> {
                // Burbujas grandes de lava que suben y bajan despacito
                for (i in 0 until 6) {
                    val cx = w * (0.12f + 0.16f * i) + 30f * d * kotlin.math.sin(t * 0.35f + i * 1.7f).toFloat()
                    val cy = h * 0.5f + h * 0.42f * kotlin.math.sin(t * (0.22f + i * 0.04f) + i * 2.1f).toFloat()
                    val r = (34f + 14f * (i % 3)) * d
                    val col = if (i % 2 == 0) p.accent else 0xFFFF3D00.toInt()
                    fill.shader = android.graphics.RadialGradient(cx, cy, r, alphaOf(col, 0.45f), alphaOf(col, 0f), android.graphics.Shader.TileMode.CLAMP)
                    c.drawCircle(cx, cy, r, fill)
                    fill.shader = null
                }
            }
            "circuit" -> {
                // Cuadrícula de circuito con pulsos de luz que la recorren
                val step = 26f * d
                stroke.color = alphaOf(p.accent, 0.07f)
                stroke.strokeWidth = 1f * d
                var gx = 0f
                while (gx < w) { c.drawLine(gx, 0f, gx, h, stroke); gx += step }
                var gy = 0f
                while (gy < h) { c.drawLine(0f, gy, w, gy, stroke); gy += step }
                for (i in 0 until 10) {
                    val horizontal = i % 2 == 0
                    val lane = ((i * 7 + 3) % (if (horizontal) (h / step).toInt().coerceAtLeast(1) else (w / step).toInt().coerceAtLeast(1))) * step
                    val span = if (horizontal) w else h
                    val pos = ((t * (60f + i * 9f) * d + i * 97f * d) % (span + 80f * d)) - 40f * d
                    for (k in 0 until 6) {
                        val pp = pos - k * 5f * d
                        fill.color = alphaOf(p.accent, 0.8f * (1f - k / 6f))
                        if (horizontal) c.drawCircle(pp, lane, (2.2f - k * 0.25f) * d, fill)
                        else c.drawCircle(lane, pp, (2.2f - k * 0.25f) * d, fill)
                    }
                }
            }
            "synthwave" -> {
                // Sol retro con rayas y una cuadrícula que avanza hacia ti
                val horizon = h * 0.42f
                val sunR = minOf(w, h) * 0.26f
                fill.shader = android.graphics.LinearGradient(0f, horizon - sunR, 0f, horizon, 0xFFFFD54F.toInt(), 0xFFFF4081.toInt(), android.graphics.Shader.TileMode.CLAMP)
                fill.alpha = 150
                c.save()
                c.clipRect(0f, 0f, w, horizon)
                c.drawCircle(w / 2f, horizon, sunR, fill)
                c.restore()
                fill.shader = null
                fill.alpha = 255
                fill.color = p.bg
                for (k in 1..4) { val y = horizon - k * sunR * 0.16f; c.drawRect(w / 2f - sunR, y, w / 2f + sunR, y + k * 1.2f * d, fill) }
                stroke.color = alphaOf(0xFFE040FB.toInt(), 0.45f)
                stroke.strokeWidth = 1.2f * d
                c.drawLine(0f, horizon, w, horizon, stroke)
                val off = (t * 0.6f) % 1f
                for (k in 0 until 9) {
                    val z = (k + off) / 9f
                    val y = horizon + (h - horizon) * z * z
                    stroke.color = alphaOf(0xFFE040FB.toInt(), 0.15f + 0.4f * z)
                    c.drawLine(0f, y, w, y, stroke)
                }
                for (k in -8..8) {
                    stroke.color = alphaOf(0xFF18FFFF.toInt(), 0.25f)
                    c.drawLine(w / 2f + k * w * 0.02f, horizon, w / 2f + k * w * 0.16f, h, stroke)
                }
            }
            "clouds" -> {
                // Nubes suaves que pasan despacito
                for (i in 0 until 5) {
                    val speed = (8f + i * 3f) * d
                    val cw = (70f + (i % 3) * 25f) * d
                    val cx = ((t * speed + i * 140f * d) % (w + cw * 2)) - cw
                    val cy = h * (0.12f + 0.18f * i) % h
                    fill.color = alphaOf(0xFFFFFFFF.toInt(), 0.55f)
                    c.drawCircle(cx, cy, cw * 0.28f, fill)
                    c.drawCircle(cx + cw * 0.28f, cy - cw * 0.1f, cw * 0.34f, fill)
                    c.drawCircle(cx + cw * 0.6f, cy, cw * 0.26f, fill)
                    tmp.set(cx - cw * 0.1f, cy, cx + cw * 0.75f, cy + cw * 0.22f)
                    c.drawRoundRect(tmp, cw * 0.11f, cw * 0.11f, fill)
                }
            }
            "gradient" -> {
                // Colores del arcoíris que van cambiando poco a poco
                val h0 = (t * 20f) % 360f
                fill.shader = android.graphics.LinearGradient(0f, 0f, w, h,
                    intArrayOf(alphaOf(hue(h0), 0.35f), alphaOf(hue(h0 + 90f), 0.35f), alphaOf(hue(h0 + 180f), 0.35f)), null,
                    android.graphics.Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w, h, fill)
                fill.shader = null
            }
            else -> return false
        }
        return true
    }

    /** Dibuja el fondo animado detrás de las teclas. Solo se mueve mientras el teclado se ve. */
    private fun drawAmbient(c: Canvas, p: Palette) {
        val effect = p.effect
        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        val t = now / 1000f
        val dt = if (lastAmb == 0L) 0f else ((now - lastAmb).coerceAtMost(100L)) / 1000f
        lastAmb = now
        if (drawScene(c, p, effect, w, h, t)) { if (isShown) postInvalidateDelayed(40); return }
        if (effect == "aurora") {
            // Cortinas de luz verde, turquesa y morada que se mueven despacito
            val cols = intArrayOf(0xFF69F0AE.toInt(), 0xFF18FFFF.toInt(), 0xFFB388FF.toInt())
            for (b in 0..2) {
                animPath.reset()
                val base = h * (0.18f + b * 0.14f)
                animPath.moveTo(0f, base)
                var x = 0f
                while (x <= w + 20f * d) {
                    val y = base + (14f + b * 6f) * d * kotlin.math.sin(x / (70f * d) + t * (0.6f + b * 0.25f) + b).toFloat()
                    animPath.lineTo(x, y)
                    x += 12f * d
                }
                animPath.lineTo(w, base + 70f * d)
                animPath.lineTo(0f, base + 70f * d)
                animPath.close()
                fill.shader = android.graphics.LinearGradient(0f, base - 20f * d, 0f, base + 70f * d,
                    alphaOf(cols[b], 0.32f), alphaOf(cols[b], 0f), android.graphics.Shader.TileMode.CLAMP)
                c.drawPath(animPath, fill)
                fill.shader = null
            }
            if (isShown) postInvalidateDelayed(40)
            return
        }
        val count = when (effect) {
            "rain" -> 70; "stars" -> 70; "snow" -> 60; "fireflies" -> 22; "petals" -> 22; "bubbles" -> 26
            "hearts" -> 16; "leaves" -> 18; "confetti" -> 45; "meteors" -> 6; "bokeh" -> 14; "glitter" -> 40; else -> 40
        }
        if (partsFor != effect || parts.size != count) {
            parts = Array(count) { newPart(effect, true) }
            partsFor = effect
        }
        for ((i, q) in parts.withIndex()) {
            when (effect) {
                "snow" -> {
                    q.y += q.vy * dt
                    q.x += 10f * d * kotlin.math.sin(t * 1.2f + q.ph).toFloat() * dt
                    if (q.y > h + 6f * d) parts[i] = newPart(effect, false)
                    fill.color = alphaOf(0xFFFFFFFF.toInt(), 0.75f)
                    c.drawCircle(q.x, q.y, q.r, fill)
                }
                "rain" -> {
                    q.y += q.vy * dt
                    q.x += q.vx * dt
                    if (q.y > h + 20f * d) parts[i] = newPart(effect, false)
                    stroke.color = alphaOf(p.accent, 0.35f)
                    stroke.strokeWidth = 1.1f * d
                    c.drawLine(q.x, q.y, q.x + q.vx / q.vy * q.r, q.y + q.r, stroke)
                }
                "bubbles" -> {
                    q.y += q.vy * dt
                    val x = q.x + 6f * d * kotlin.math.sin(t * 2f + q.ph).toFloat()
                    if (q.y < -10f * d) parts[i] = newPart(effect, false)
                    stroke.color = alphaOf(p.accent, 0.45f)
                    stroke.strokeWidth = 1f * d
                    c.drawCircle(x, q.y, q.r, stroke)
                    fill.color = alphaOf(0xFFFFFFFF.toInt(), 0.35f)
                    c.drawCircle(x - q.r * 0.35f, q.y - q.r * 0.35f, q.r * 0.22f, fill)
                }
                "embers" -> {
                    q.y += q.vy * dt
                    q.x += q.vx * dt + 8f * d * kotlin.math.sin(t * 3f + q.ph).toFloat() * dt
                    if (q.y < -6f * d) parts[i] = newPart(effect, false)
                    val life = (q.y / h).coerceIn(0f, 1f)
                    val col = if (life > 0.6f) 0xFFFFD180.toInt() else if (life > 0.3f) 0xFFFF9100.toInt() else 0xFFFF3D00.toInt()
                    fill.color = alphaOf(col, 0.35f + 0.5f * life)
                    c.drawCircle(q.x, q.y, q.r, fill)
                }
                "fireflies" -> {
                    // Vuelan despacito cambiando de dirección y prenden y apagan
                    if (rnd.nextInt(40) == 0) { q.vx = (rnd.nextFloat() - 0.5f) * 24f * d; q.vy = (rnd.nextFloat() - 0.5f) * 24f * d }
                    q.x = (q.x + q.vx * dt).let { if (it < 0) w else if (it > w) 0f else it }
                    q.y = (q.y + q.vy * dt).let { if (it < 0) h else if (it > h) 0f else it }
                    val glow = 0.25f + 0.75f * ((kotlin.math.sin(t * 2.2f + q.ph) + 1f) / 2f).toFloat()
                    fill.color = alphaOf(p.accent, glow * 0.25f)
                    c.drawCircle(q.x, q.y, q.r * 3.2f, fill)
                    fill.color = alphaOf(p.accent, glow)
                    c.drawCircle(q.x, q.y, q.r, fill)
                }
                "petals" -> {
                    q.y += q.vy * dt
                    q.x += (q.vx + 16f * d * kotlin.math.sin(t * 1.5f + i).toFloat()) * dt
                    q.ph += 60f * dt
                    if (q.y > h + 8f * d || q.x > w + 10f * d) parts[i] = newPart(effect, false)
                    c.save()
                    c.rotate(q.ph, q.x, q.y)
                    fill.color = alphaOf(if (i % 3 == 0) 0xFFF48FB1.toInt() else 0xFFF8BBD0.toInt(), 0.8f)
                    tmp.set(q.x - q.r, q.y - q.r * 0.55f, q.x + q.r, q.y + q.r * 0.55f)
                    c.drawOval(tmp, fill)
                    c.restore()
                }
                "hearts" -> {
                    q.y += q.vy * dt
                    val x = q.x + 10f * d * kotlin.math.sin(t * 1.6f + q.ph).toFloat()
                    if (q.y < -12f * d) parts[i] = newPart(effect, false)
                    heart(c, x, q.y, q.r, alphaOf(if (i % 3 == 0) 0xFFFF80AB.toInt() else p.accent, 0.55f))
                }
                "leaves" -> {
                    q.y += q.vy * dt
                    q.x += (q.vx + 20f * d * kotlin.math.sin(t * 1.3f + i).toFloat()) * dt
                    q.ph += 90f * dt
                    if (q.y > h + 10f * d || q.x > w + 12f * d) parts[i] = newPart(effect, false)
                    val col = intArrayOf(0xFFFF8F00.toInt(), 0xFFE65100.toInt(), 0xFFFFC107.toInt(), 0xFFBF360C.toInt(), 0xFF8D6E63.toInt())[i % 5]
                    c.save()
                    c.rotate(q.ph, q.x, q.y)
                    fill.color = alphaOf(col, 0.85f)
                    tmp.set(q.x - q.r, q.y - q.r * 0.5f, q.x + q.r, q.y + q.r * 0.5f)
                    c.drawOval(tmp, fill)
                    stroke.color = alphaOf(0xFF4E342E.toInt(), 0.6f)
                    stroke.strokeWidth = 0.8f * d
                    c.drawLine(q.x - q.r, q.y, q.x + q.r, q.y, stroke)
                    c.restore()
                }
                "confetti" -> {
                    q.y += q.vy * dt
                    q.x += (q.vx + 12f * d * kotlin.math.sin(t * 3f + i).toFloat()) * dt
                    q.ph += 200f * dt
                    if (q.y > h + 8f * d) parts[i] = newPart(effect, false)
                    c.save()
                    c.rotate(q.ph, q.x, q.y)
                    fill.color = alphaOf(hue(i * 47f), 0.85f)
                    c.drawRect(q.x - q.r, q.y - q.r * 0.5f, q.x + q.r, q.y + q.r * 0.5f, fill)
                    c.restore()
                }
                "meteors" -> {
                    q.x += q.vx * dt
                    q.y += q.vy * dt
                    if (q.y > h + 40f * d || q.x > w + 60f * d) parts[i] = newPart(effect, false)
                    if (q.y > -10f * d) {
                        val len = q.r
                        stroke.shader = android.graphics.LinearGradient(q.x, q.y, q.x - len * 0.84f, q.y - len * 0.55f,
                            alphaOf(0xFFFFFFFF.toInt(), 0.95f), alphaOf(p.accent, 0f), android.graphics.Shader.TileMode.CLAMP)
                        stroke.strokeWidth = 1.8f * d
                        c.drawLine(q.x, q.y, q.x - len * 0.84f, q.y - len * 0.55f, stroke)
                        stroke.shader = null
                        fill.color = alphaOf(0xFFFFFFFF.toInt(), 0.9f)
                        c.drawCircle(q.x, q.y, 1.6f * d, fill)
                    }
                }
                "bokeh" -> {
                    q.x = (q.x + q.vx * dt).let { if (it < -q.r) w + q.r else if (it > w + q.r) -q.r else it }
                    q.y = (q.y + q.vy * dt).let { if (it < -q.r) h + q.r else if (it > h + q.r) -q.r else it }
                    val pulse = 0.5f + 0.5f * ((kotlin.math.sin(t * 0.8f + i) + 1f) / 2f).toFloat()
                    val col = if (i % 3 == 0) p.accent else hue(q.ph + t * 6f)
                    fill.shader = android.graphics.RadialGradient(q.x, q.y, q.r, alphaOf(col, 0.28f * pulse), alphaOf(col, 0f), android.graphics.Shader.TileMode.CLAMP)
                    c.drawCircle(q.x, q.y, q.r, fill)
                    fill.shader = null
                }
                "glitter" -> {
                    // Brillitos que aparecen y desaparecen en lugares al azar
                    val life = ((t * 0.7f + q.ph) % 1f)
                    if (life < 0.02f) { q.x = rnd.nextFloat() * w; q.y = rnd.nextFloat() * h }
                    val a = kotlin.math.sin(life * Math.PI).toFloat()
                    star(c, q.x, q.y, q.r * (0.6f + a * 0.6f), alphaOf(if (i % 4 == 0) 0xFFFFFFFF.toInt() else p.accent, a))
                }
                else -> {
                    // Estrellas que titilan (y de vez en cuando una fugaz)
                    val tw = 0.2f + 0.8f * ((kotlin.math.sin(t * (1.2f + (i % 5) * 0.4f) + q.ph) + 1f) / 2f).toFloat()
                    fill.color = alphaOf(if (i % 7 == 0) p.accent else 0xFFFFFFFF.toInt(), tw)
                    c.drawCircle(q.x, q.y, q.r, fill)
                }
            }
        }
        if (effect == "stars") {
            // Estrella fugaz cada ~6 segundos
            val cycle = (t % 6f) / 6f
            if (cycle < 0.12f) {
                val k = cycle / 0.12f
                val sx = w * (0.15f + 0.7f * k)
                val sy = h * (0.1f + 0.25f * k)
                stroke.color = alphaOf(0xFFFFFFFF.toInt(), 1f - k)
                stroke.strokeWidth = 1.6f * d
                c.drawLine(sx, sy, sx - 40f * d, sy - 14f * d, stroke)
            }
        }
        if (isShown) postInvalidateDelayed(33)
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

    /** Los de "Más opciones": se ven deslizando la barra de arriba hacia la izquierda. */
    private var extraTools = IntArray(0)
    private var allTools = barTools
    /** Cuánto deslizaste la barra de arriba (en píxeles). */
    private var stripScroll = 0f
    private var stripDown = false
    private var stripDragging = false
    private var stripDownScroll = 0f
    private val stripVelocity = android.view.VelocityTracker.obtain()
    private val stripFling = android.widget.OverScroller(context, android.view.animation.DecelerateInterpolator(1.6f))

    // ---- Acomodar los botones de la barra (mantener presionado y arrastrar)
    private var editing = false
    private var editList = ArrayList<Int>()
    private var editTool = -1
    private var editX = 0f
    /** Dónde se dibuja cada botón (para que se muevan suave al acomodarse). */
    private val toolX = HashMap<Int, Float>()
    private val stripEditRunnable = Runnable {
        if (!stripDown || stripDragging || stripPressed < 0) return@Runnable
        val t = stripPressed
        if (t !in allTools) return@Runnable
        editing = true
        editTool = t
        editList = ArrayList(allTools.toList())
        editX = downX
        stripPressed = -1
        listener?.onKeyFeedback()
        postInvalidateOnAnimation()
    }

    /** Mueve el botón que arrastras al lugar donde está el dedo. */
    private fun editMove() {
        val step = toolStep()
        val target = ((editX + stripScroll) / step).toInt().coerceIn(0, editList.size - 1)
        val cur = editList.indexOf(editTool)
        if (cur >= 0 && target != cur) {
            editList.removeAt(cur)
            editList.add(target, editTool)
            listener?.onKeyFeedback()
        }
    }

    override fun computeScroll() {
        if (stripFling.computeScrollOffset()) {
            stripScroll = stripFling.currX.toFloat().coerceIn(0f, maxStripScroll())
            invalidate(0, 0, width, stripH.toInt() + 1)
            postInvalidateOnAnimation()
        }
    }

    fun setBarTools(tools: List<Int>, extra: List<Int> = extraTools.toList()) {
        val n = tools.toIntArray()
        val e = extra.filter { it !in tools }.toIntArray()
        if (!n.contentEquals(barTools) || !e.contentEquals(extraTools)) {
            barTools = n
            extraTools = e
            allTools = n + e
            stripScroll = stripScroll.coerceIn(0f, maxStripScroll())
            invalidate()
        }
    }

    /** Ancho de cada botón: se reparten a lo ancho de la barra. */
    private fun toolStep(): Float = width.toFloat() / maxOf(1, barTools.size)

    private fun maxStripScroll(): Float = maxOf(0f, allTools.size * toolStep() - width)

    private fun toolHit(x: Float): Int {
        val i = ((x + stripScroll) / toolStep()).toInt()
        return if (i in allTools.indices) allTools[i] else -1
    }

    // ---------------------------------------------------------------- Toques

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (resizing) { resizeTouch(e); return true }
        // Mide la velocidad del dedo (para que la barra de arriba siga deslizando al soltar)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) stripVelocity.clear()
        stripVelocity.addMovement(e)
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
        stripDown = false
        stripDragging = false
        if (y < stripH) {
            stripDown = true
            // Si la barra todavía iba deslizando, el toque solo la frena (no aprieta un botón)
            val wasFlinging = !stripFling.isFinished
            stripFling.forceFinished(true)
            stripDownScroll = stripScroll
            stripPressed = if (wasFlinging) -1 else stripHit(x)
            if (stripPressed >= 0) listener?.onKeyFeedback()
            // Mantener presionado un botón: modo acomodar (los demás tiemblan)
            if (stripPressed >= 0 && !showingSuggestions && !showingShot && stripPressed in allTools) {
                handler.postDelayed(stripEditRunnable, 420L)
            }
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
        if (stripDown && editing) {
            editX = x
            editMove()
            invalidate()
            return
        }
        if (stripDown) {
            if (abs(x - downX) > 10f * d) handler.removeCallbacks(stripEditRunnable)
            // Deslizar la barra de arriba para ver los demás botones
            if (!showingSuggestions && !showingShot && maxStripScroll() > 0f && (stripDragging || abs(x - downX) > 10f * d)) {
                stripDragging = true
                stripPressed = -1
                stripScroll = (stripDownScroll - (x - downX)).coerceIn(0f, maxStripScroll())
                invalidate()
            }
            return
        }
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
        handler.removeCallbacks(stripEditRunnable)
        if (stripDown && editing) {
            stripDown = false
            editing = false
            stripPressed = -1
            // Guarda el nuevo orden: los primeros van en la barra, los demás en "Más opciones"
            val n = barTools.size
            val list = editList.toMutableList()
            val fixedAt = list.indexOf(TOOL_MORE)
            if (fixedAt >= n && n > 0) { list.removeAt(fixedAt); list.add(n - 1, TOOL_MORE) }
            val bar = list.take(n)
            val more = list.drop(n)
            setBarTools(bar, more)
            listener?.onToolsReordered(bar, more)
            listener?.onKeyFeedback()
            invalidate()
            return
        }
        if (stripDown) {
            stripDown = false
            if (stripDragging) {
                stripDragging = false
                stripPressed = -1
                // Sigue deslizando un poco y se frena suave (como en el teléfono)
                stripVelocity.computeCurrentVelocity(1000, 8000f * d)
                val vx = stripVelocity.xVelocity
                val max = maxStripScroll()
                if (abs(vx) > 120f * d && max > 0f) {
                    stripFling.fling(stripScroll.toInt(), 0, (-vx).toInt(), 0, 0, max.toInt(), 0, 0)
                    postInvalidateOnAnimation()
                } else invalidate()
                return
            }
        }
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
        if (p.effect.startsWith("matrix")) drawMatrix(c)
        else if (p.effect.isNotEmpty()) drawAmbient(c, p)
        else { lastRain = 0L; lastAmb = 0L }
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
                    // Se hunde como un botón de verdad
                    "drop" -> c.translate(0f, 4f * d * kotlin.math.sin(Math.PI * t).toFloat())
                    // Se columpia colgada de arriba
                    "swing" -> c.rotate(12f * kotlin.math.sin(4 * Math.PI * t).toFloat() * damp, cx, k.rect.top)
                    // Se da la vuelta como una carta
                    "flip" -> c.scale(kotlin.math.cos(2 * Math.PI * t).toFloat().let { if (kotlin.math.abs(it) < 0.08f) 0.08f else it }, 1f, cx, cy)
                    // Late dos veces como un corazón
                    "heartbeat" -> {
                        val b = if (t < 0.5f) kotlin.math.sin(2 * Math.PI * t).toFloat() else 0.6f * kotlin.math.sin(2 * Math.PI * (t - 0.5f)).toFloat()
                        val sc = 1f + 0.12f * maxOf(0f, b)
                        c.scale(sc, sc, cx, cy)
                    }
                    // Se estira de lado como liga
                    "rubber" -> {
                        val st = 0.22f * kotlin.math.sin(3 * Math.PI * t).toFloat() * damp
                        c.scale(1f + st, 1f - st * 0.6f, cx, cy)
                    }
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
            "lightning" -> {
                // Un rayito que baja zigzagueando sobre la tecla
                animPath.reset()
                var x = a.x
                var y = k.rect.top - 22f * d
                animPath.moveTo(x, y)
                for (i in 0 until 5) {
                    x += (if (i % 2 == 0) 7f else -7f) * d + (rnd.nextFloat() - 0.5f) * 4f * d
                    y += 9f * d
                    animPath.lineTo(x, y)
                }
                stroke.color = alphaOf(0xFFFFF59D.toInt(), fade)
                stroke.strokeWidth = 3.2f * d
                c.drawPath(animPath, stroke)
                stroke.color = alphaOf(0xFFFFFFFF.toInt(), fade)
                stroke.strokeWidth = 1.2f * d
                c.drawPath(animPath, stroke)
                fill.color = alphaOf(0xFFFFF59D.toInt(), fade * 0.25f)
                c.drawRoundRect(k.rect, radius, radius, fill)
            }
            "petals" -> {
                // Pétalos rosas que caen girando
                for (i in 0 until 6) {
                    val x0 = k.rect.left + k.rect.width() * rnd.nextFloat()
                    val x = x0 + 10f * d * kotlin.math.sin(t * 5f + i).toFloat()
                    val y = k.rect.top + 46f * d * t * (0.6f + rnd.nextFloat() * 0.5f)
                    c.save()
                    c.rotate(t * 360f + i * 50f, x, y)
                    fill.color = alphaOf(if (i % 2 == 0) 0xFFF48FB1.toInt() else 0xFFF8BBD0.toInt(), fade)
                    tmp.set(x - 4f * d, y - 2.2f * d, x + 4f * d, y + 2.2f * d)
                    c.drawOval(tmp, fill)
                    c.restore()
                }
            }
            "music" -> {
                // Notitas musicales que suben bailando
                for (i in 0 until 3) {
                    val tt = ((t - i * 0.15f) / 0.7f).coerceIn(0f, 1f)
                    if (tt <= 0f) continue
                    val x = a.x + (i - 1) * 13f * d + 6f * d * kotlin.math.sin(tt * 9f + i).toFloat()
                    val y = a.y - 48f * d * tt
                    drawLabel(c, if (i == 1) "♫" else "♪", x, y, (13f + 4f * tt) * d / labelMul.coerceAtLeast(0.01f), alphaOf(hue(i * 110f + seed), 1f - tt))
                }
            }
            "emojis" -> {
                // Un emoji alegre que salta de la tecla
                val list = arrayOf("😀", "😂", "😍", "🎉", "🔥", "⭐", "💯", "👍", "😎", "🥳", "❤️", "✨")
                val e = list[seed % list.size]
                val sc = if (t < 0.3f) t / 0.3f else 1f
                drawLabel(c, e, a.x, a.y - 38f * d * t, (16f + 6f * sc) * d / labelMul.coerceAtLeast(0.01f), alphaOf(0xFFFFFFFF.toInt(), fade))
            }
            "ink" -> {
                // Una gota de tinta que se extiende dentro de la tecla
                c.save()
                animPath.reset()
                animPath.addRoundRect(k.rect, radius, radius, Path.Direction.CW)
                c.clipPath(animPath)
                val maxR = kotlin.math.hypot(k.rect.width(), k.rect.height())
                fill.color = alphaOf(hue(seed * 3f), fade * 0.5f)
                c.drawCircle(a.x, a.y, maxR * (0.1f + 0.9f * kotlin.math.sqrt(t)), fill)
                c.restore()
            }
            "pulse" -> {
                // El borde de la tecla late hacia afuera dos veces
                for (i in 0..1) {
                    val tt = ((t - i * 0.3f) / 0.7f).coerceIn(0f, 1f)
                    if (tt <= 0f || tt >= 1f) continue
                    val g = 10f * d * tt
                    stroke.color = alphaOf(p.accent, (1f - tt) * 0.9f)
                    stroke.strokeWidth = 2f * d
                    tmp.set(k.rect.left - g, k.rect.top - g, k.rect.right + g, k.rect.bottom + g)
                    c.drawRoundRect(tmp, radius + g, radius + g, stroke)
                }
            }
            "orbit" -> {
                // Bolitas que giran alrededor de la tecla como planetas
                val cxk = k.rect.centerX()
                val cyk = k.rect.centerY()
                val rr = maxOf(k.rect.width(), k.rect.height()) * 0.6f
                for (i in 0 until 3) {
                    val ang = t * 2 * Math.PI * 1.3 + i * 2.094
                    fill.color = alphaOf(hue(i * 120f + seed), fade)
                    c.drawCircle(cxk + (kotlin.math.cos(ang) * rr).toFloat(), cyk + (kotlin.math.sin(ang) * rr * 0.6f).toFloat(), 2.8f * d, fill)
                }
            }
            "rainbow" -> {
                // La tecla se pinta de arcoíris y se desvanece
                fill.shader = android.graphics.SweepGradient(k.rect.centerX(), k.rect.centerY(),
                    intArrayOf(0xFFFF1744.toInt(), 0xFFFFC400.toInt(), 0xFF00E676.toInt(), 0xFF00B0FF.toInt(), 0xFFD500F9.toInt(), 0xFFFF1744.toInt()), null)
                fill.alpha = (fade * 0.55f * 255).toInt()
                c.drawRoundRect(k.rect, radius, radius, fill)
                fill.shader = null
                fill.alpha = 255
            }
            "pixel" -> {
                // Cuadritos de videojuego que se dispersan
                for (i in 0 until 10) {
                    val vx = (rnd.nextFloat() - 0.5f) * 60f * d
                    val vy = (rnd.nextFloat() - 0.8f) * 50f * d
                    val x = a.x + vx * t
                    val y = a.y + vy * t + 30f * d * t * t
                    val sz = 3f * d
                    fill.color = alphaOf(if (i % 2 == 0) p.accent else hue(i * 36f), fade)
                    c.drawRect(x - sz, y - sz, x + sz, y + sz, fill)
                }
            }
            "shards" -> {
                // La tecla "se rompe" en triangulitos que salen volando
                for (i in 0 until 8) {
                    val ang = (i * 45 + seed) * Math.PI / 180.0
                    val dist = 34f * d * t
                    val x = k.rect.centerX() + (kotlin.math.cos(ang) * dist).toFloat()
                    val y = k.rect.centerY() + (kotlin.math.sin(ang) * dist).toFloat() + 20f * d * t * t
                    val sz = 5f * d * (1f - 0.4f * t)
                    c.save()
                    c.rotate(t * 400f + i * 45f, x, y)
                    animPath.reset()
                    animPath.moveTo(x, y - sz); animPath.lineTo(x + sz, y + sz); animPath.lineTo(x - sz, y + sz * 0.6f); animPath.close()
                    fill.color = alphaOf(if (i % 2 == 0) p.key else p.accent, fade)
                    c.drawPath(animPath, fill)
                    c.restore()
                }
            }
            "smoke" -> {
                // Humito gris que sube y se deshace
                for (i in 0 until 5) {
                    val tt = ((t - i * 0.1f) / 0.6f).coerceIn(0f, 1f)
                    if (tt <= 0f) continue
                    val x = a.x + 8f * d * kotlin.math.sin(tt * 5f + i).toFloat() + (i - 2) * 4f * d
                    val y = a.y - 40f * d * tt
                    fill.color = alphaOf(if (p.isDark) 0xFFCFD8DC.toInt() else 0xFF78909C.toInt(), (1f - tt) * 0.4f)
                    c.drawCircle(x, y, (4f + 9f * tt) * d, fill)
                }
            }
            "spiral" -> {
                // Puntitos que salen en espiral
                for (i in 0 until 14) {
                    val tt = (t - i * 0.025f).coerceIn(0f, 1f)
                    val ang = tt * 4 * Math.PI + i * 0.45
                    val r = 34f * d * tt
                    fill.color = alphaOf(hue(i * 25f + seed), (1f - tt))
                    c.drawCircle(a.x + (kotlin.math.cos(ang) * r).toFloat(), a.y + (kotlin.math.sin(ang) * r).toFloat(), 2.2f * d, fill)
                }
            }
            "flower" -> {
                // Una flor que se abre donde tocaste
                val grow = if (t < 0.4f) t / 0.4f else 1f
                val pr = 7f * d * grow
                for (i in 0 until 6) {
                    val ang = i * 60.0 + t * 90.0
                    val rad = ang * Math.PI / 180.0
                    val x = a.x + (kotlin.math.cos(rad) * pr).toFloat()
                    val y = a.y + (kotlin.math.sin(rad) * pr).toFloat()
                    fill.color = alphaOf(if (i % 2 == 0) 0xFFF48FB1.toInt() else 0xFFFFF59D.toInt(), fade)
                    c.drawCircle(x, y, 5f * d * grow, fill)
                }
                fill.color = alphaOf(0xFFFFC107.toInt(), fade)
                c.drawCircle(a.x, a.y, 3.5f * d * grow, fill)
            }
            "coin" -> {
                // Una moneda dorada que sube girando
                val y = a.y - 42f * d * t
                val sx = kotlin.math.abs(kotlin.math.cos(t * 4 * Math.PI)).toFloat().coerceAtLeast(0.12f)
                c.save()
                c.scale(sx, 1f, a.x, y)
                fill.color = alphaOf(0xFFFFC107.toInt(), fade)
                c.drawCircle(a.x, y, 8f * d, fill)
                stroke.color = alphaOf(0xFFFF8F00.toInt(), fade)
                stroke.strokeWidth = 1.5f * d
                c.drawCircle(a.x, y, 6f * d, stroke)
                c.restore()
            }
            "balloon" -> {
                // Un globo que sube con su hilito
                val x = a.x + 6f * d * kotlin.math.sin(t * 6f).toFloat()
                val y = a.y - 70f * d * t
                fill.color = alphaOf(hue(seed * 7f), fade * 0.9f)
                tmp.set(x - 7f * d, y - 9f * d, x + 7f * d, y + 9f * d)
                c.drawOval(tmp, fill)
                stroke.color = alphaOf(p.hint, fade)
                stroke.strokeWidth = 1f * d
                c.drawLine(x, y + 9f * d, x + 2f * d * kotlin.math.sin(t * 10f).toFloat(), y + 22f * d, stroke)
            }
            "butterfly" -> {
                // Una mariposa que sale volando
                val x = a.x + 30f * d * t + 8f * d * kotlin.math.sin(t * 8f).toFloat()
                val y = a.y - 50f * d * t
                val flap = kotlin.math.abs(kotlin.math.sin(t * 30f)).toFloat().coerceAtLeast(0.2f)
                val col = hue(seed * 5f)
                for (side in listOf(-1f, 1f)) {
                    c.save()
                    c.scale(flap, 1f, x, y)
                    fill.color = alphaOf(col, fade)
                    tmp.set(x + side * 7f * d - 5f * d, y - 6f * d, x + side * 7f * d + 5f * d, y + 2f * d)
                    c.drawOval(tmp, fill)
                    fill.color = alphaOf(blend(col, 0xFFFFFF, 0.4f) or 0xFF000000.toInt(), fade)
                    tmp.set(x + side * 5f * d - 3.5f * d, y, x + side * 5f * d + 3.5f * d, y + 7f * d)
                    c.drawOval(tmp, fill)
                    c.restore()
                }
                stroke.color = alphaOf(0xFF3E2723.toInt(), fade)
                stroke.strokeWidth = 1.5f * d
                c.drawLine(x, y - 5f * d, x, y + 6f * d, stroke)
            }
            "glitch" -> {
                // Falla digital: rayas de color movidas de lado
                for (i in 0 until 4) {
                    val yy = k.rect.top + k.rect.height() * rnd.nextFloat()
                    val hh = (2f + rnd.nextFloat() * 4f) * d
                    val off = (rnd.nextFloat() - 0.5f) * 14f * d * fade
                    fill.color = alphaOf(if (i % 2 == 0) 0xFF00E5FF.toInt() else 0xFFFF1744.toInt(), fade * 0.7f)
                    c.drawRect(k.rect.left + off, yy, k.rect.right + off, yy + hh, fill)
                }
            }
            "splash" -> {
                // Gotitas de agua que salpican
                for (i in 0 until 8) {
                    val vx = (rnd.nextFloat() - 0.5f) * 56f * d
                    val vy = -(28f + rnd.nextFloat() * 30f) * d
                    val x = a.x + vx * t
                    val y = a.y + vy * t + 110f * d * t * t
                    fill.color = alphaOf(0xFF4FC3F7.toInt(), fade)
                    c.drawCircle(x, y, (2.6f - t) * d, fill)
                }
                stroke.color = alphaOf(0xFF4FC3F7.toInt(), fade * 0.7f)
                stroke.strokeWidth = 1.4f * d
                tmp.set(a.x - 18f * d * t, a.y - 5f * d * t, a.x + 18f * d * t, a.y + 5f * d * t)
                c.drawOval(tmp, stroke)
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

    /** El ícono de cada botón de la barra de arriba (-1 si no tiene). */
    private fun toolIcon(i: Int): Int = when (i) {
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
        TOOL_TICKET -> Icons.TICKET
        TOOL_REPORT -> Icons.REPORT
        TOOL_FORMAL -> Icons.FORMAL
        TOOL_FLOAT -> Icons.FLOAT
        TOOL_REVIEW -> Icons.REVIEW
        TOOL_BARCODE -> Icons.BARCODE
        TOOL_STAMP -> Icons.CLOCK
        TOOL_NOTEBOOK -> Icons.NOTEBOOK
        TOOL_TICKETS -> Icons.TICKETS
        TOOL_WORD -> Icons.BOOK
        TOOL_WORKDAY -> Icons.TRUCK
        TOOL_MATERIALS -> Icons.BOX
        TOOL_REPLY -> Icons.REPLY
        TOOL_SITES -> Icons.MAP_PIN
        TOOL_STATUS -> Icons.SEND
        TOOL_WEEK -> Icons.WEEK
        TOOL_DRIVE -> Icons.STEER
        else -> -1
    }

    private fun drawStrip(c: Canvas, p: Palette) {
        if (showingSuggestions) { drawSuggestions(c, p); return }
        if (showingShot) { drawShot(c, p); return }
        val cy = stripH / 2f
        val s = stripH * 0.22f
        val step = toolStep()
        val tools = if (editing) editList.toIntArray() else allTools
        if (editing) {
            // Si llevas el botón a la orilla, la barra se desliza poco a poco
            val edge = step * 0.7f
            val sp = when {
                editX < edge -> -(1f - editX / edge) * 9f * d
                editX > width - edge -> (1f - (width - editX) / edge) * 9f * d
                else -> 0f
            }
            if (sp != 0f) {
                val before = stripScroll
                stripScroll = (stripScroll + sp).coerceIn(0f, maxStripScroll())
                if (stripScroll != before) editMove()
            }
        }
        stripScroll = stripScroll.coerceIn(0f, maxStripScroll())
        var moving = editing
        val xs = FloatArray(tools.size) {
            // Se van deslizando suave a su nuevo lugar
            val target = step * (it + 0.5f)
            val t = tools[it]
            val cur = toolX[t] ?: target
            val nx = if (abs(target - cur) < 0.6f) target else cur + (target - cur) * 0.3f
            if (nx != target) moving = true
            toolX[t] = nx
            nx - stripScroll
        }
        val now = SystemClock.uptimeMillis() / 1000.0
        if (extraTools.isNotEmpty() && stripScroll < 1f) {
            // Rayita al borde derecho: avisa que hay más botones deslizando
            fill.color = alphaOf(p.hint, 0.5f)
            tmp.set(width - 3f * d, cy - stripH * 0.22f, width - 1f * d, cy + stripH * 0.22f)
            c.drawRoundRect(tmp, d, d, fill)
        }
        for (n in tools.indices) {
            val i = tools[n]
            if (editing && i == editTool) continue
            val cx = xs[n]
            if (cx < -step || cx > width + step) continue
            if (n == barTools.size && n > 0) {
                // Separador entre la barra y "Más opciones"
                fill.color = alphaOf(p.hint, 0.35f)
                c.drawRect(cx - step / 2f - 0.5f * d, cy - stripH * 0.25f, cx - step / 2f + 0.5f * d, cy + stripH * 0.25f, fill)
            }
            if (stripPressed == i) {
                fill.color = p.keyPressed
                c.drawCircle(cx, cy, stripH * 0.4f, fill)
            }
            val kind = toolIcon(i)
            if (kind < 0) continue
            val on = (i == TOOL_TRANSLATE && translateOn) || (i == TOOL_SPELL && spellOn) || (i == TOOL_HACKER && hackerOn) || (i == TOOL_CODE && codeOn) || (i == TOOL_ONEHAND && oneHand != 0) || (i == TOOL_FLOAT && floatOn)
            val bgc = if (stripPressed == i) p.keyPressed else p.bg
            // Modo acomodar: los demás tiemblan un poquito
            val ang = if (editing) (7.0 * kotlin.math.sin(now * 2 * Math.PI * 4.5 + i * 1.7)).toFloat() else 0f
            if (ang != 0f) { c.save(); c.rotate(ang, cx, cy) }
            Icons.draw(c, kind, cx, cy, s, if (on) p.accent else p.hint, bgc, d)
            if (ang != 0f) c.restore()
        }
        if (editing) {
            // El que arrastras va debajo del dedo, un poco más grande
            val kind = toolIcon(editTool)
            if (kind >= 0) {
                val cx = editX.coerceIn(step / 2f, width - step / 2f)
                fill.color = p.keyPressed
                c.drawCircle(cx, cy, stripH * 0.42f, fill)
                st(p.accent)
                c.drawCircle(cx, cy, stripH * 0.42f, stroke)
                c.save()
                c.scale(1.2f, 1.2f, cx, cy)
                Icons.draw(c, kind, cx, cy, s, p.text, p.keyPressed, d)
                c.restore()
            }
        }
        if (moving) postInvalidateOnAnimation()
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
