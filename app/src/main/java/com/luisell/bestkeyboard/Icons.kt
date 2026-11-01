package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * Todos los íconos del teclado, dibujados con el mismo estilo:
 * líneas finas con puntas redondeadas (moderno y minimalista, sin emojis).
 *
 * [s] es el "radio" del ícono: el dibujo cabe en un cuadro de 2s × 2s centrado en (cx, cy).
 */
object Icons {
    const val SETTINGS = 1
    const val THEME = 2
    const val EMOJI = 3
    const val CLIP = 4
    const val TRANSLATE = 5
    const val EDIT = 6
    const val SPELL = 7
    const val SCAN = 8
    const val MIC = 9
    const val HIDE = 10
    const val MENU = 11
    const val CLOSE = 12
    const val BACK = 13
    const val SWAP = 14
    const val PIN = 15
    const val IMAGE = 16
    const val RECENT = 17
    const val PEOPLE = 18
    const val ANIMAL = 19
    const val FOOD = 20
    const val TRAVEL = 21
    const val SPORT = 22
    const val IDEA = 23
    const val HEART = 24
    const val FLAG = 25
    const val DELETE = 26
    const val MORE = 27
    const val RESIZE = 28
    const val GLOBE = 29
    const val PENCIL = 30
    const val CHEVRON = 31
    const val HACKER = 32
    const val LOCK = 33
    const val MAIL = 34
    const val EYE = 35
    const val EYE_OFF = 36
    const val CODE = 37
    const val RIPPLE = 38
    const val TEXTSIZE = 39
    const val KEYSIZE = 40
    const val SPEAKER = 41
    const val VIBRATE = 42
    const val SHAPES = 43
    const val BRUSH = 44
    const val BUBBLE = 45
    const val READ = 46
    const val ONEHAND = 47
    const val CALC = 48
    const val GIF = 49
    const val SEARCH = 50
    const val TICKET = 51
    const val REPORT = 52
    const val FORMAL = 53
    const val FLOAT = 54
    const val REVIEW = 55
    const val BARCODE = 56
    const val CLOCK = 57
    const val NOTEBOOK = 58
    const val TICKETS = 59
    const val BOOK = 60
    const val TRUCK = 61
    const val BOX = 62
    const val REPLY = 63
    const val PIN = 64

    /** Íconos de las pestañas de emojis (en el mismo orden que EmojiData.TAB_ICONS). */
    val EMOJI_TABS = intArrayOf(RECENT, EMOJI, PEOPLE, ANIMAL, FOOD, TRAVEL, SPORT, IDEA, HEART, FLAG)

    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fl = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val r = RectF()

    /** [bg] = color del fondo (para "cortar" detrás de las bolitas de los ajustes). */
    fun draw(c: Canvas, kind: Int, cx: Float, cy: Float, s: Float, color: Int, bg: Int, density: Float) {
        st.color = color
        st.strokeWidth = maxOf(1.6f * density, s * 0.17f)
        fl.color = color
        when (kind) {
            SETTINGS -> {
                // 3 controles deslizantes con bolitas huecas
                val w = s * 0.95f
                val knobs = floatArrayOf(-0.45f, 0.42f, -0.05f)
                for (k in 0..2) {
                    val y = cy + (k - 1) * s * 0.66f
                    c.drawLine(cx - w, y, cx + w, y, st)
                    val kx = cx + knobs[k] * w
                    fl.color = bg
                    c.drawCircle(kx, y, s * 0.3f, fl)
                    c.drawCircle(kx, y, s * 0.22f, st)
                }
            }
            THEME -> {
                val rr = s * 0.85f
                c.drawCircle(cx, cy, rr, st)
                r.set(cx - rr, cy - rr, cx + rr, cy + rr)
                c.drawArc(r, 90f, 180f, true, fl)
            }
            EMOJI -> {
                val rr = s * 0.9f
                c.drawCircle(cx, cy, rr, st)
                c.drawCircle(cx - rr * 0.36f, cy - rr * 0.22f, rr * 0.11f, fl)
                c.drawCircle(cx + rr * 0.36f, cy - rr * 0.22f, rr * 0.11f, fl)
                r.set(cx - rr * 0.48f, cy - rr * 0.2f, cx + rr * 0.48f, cy + rr * 0.5f)
                c.drawArc(r, 25f, 130f, false, st)
            }
            CLIP -> {
                // Portapapeles: hoja redondeada con su "pinza" arriba
                r.set(cx - s * 0.72f, cy - s * 0.72f, cx + s * 0.72f, cy + s * 0.95f)
                c.drawRoundRect(r, s * 0.24f, s * 0.24f, st)
                fl.color = bg
                r.set(cx - s * 0.38f, cy - s * 0.98f, cx + s * 0.38f, cy - s * 0.5f)
                c.drawRoundRect(r, s * 0.14f, s * 0.14f, fl)
                c.drawRoundRect(r, s * 0.14f, s * 0.14f, st)
                c.drawLine(cx - s * 0.32f, cy + s * 0.05f, cx + s * 0.32f, cy + s * 0.05f, st)
                c.drawLine(cx - s * 0.32f, cy + s * 0.45f, cx + s * 0.12f, cy + s * 0.45f, st)
            }
            TRANSLATE -> {
                // Arriba a la izquierda un "文" de trazos, abajo a la derecha una "A"
                val a = s * 0.95f
                c.drawLine(cx - a, cy - a * 0.62f, cx + a * 0.05f, cy - a * 0.62f, st)
                c.drawLine(cx - a * 0.47f, cy - a * 0.95f, cx - a * 0.47f, cy - a * 0.62f, st)
                // Las dos "piernas" del 文 se cruzan en X
                path.reset()
                path.moveTo(cx - a * 0.15f, cy - a * 0.45f)
                path.quadTo(cx - a * 0.4f, cy - a * 0.05f, cx - a * 0.95f, cy + a * 0.3f)
                c.drawPath(path, st)
                path.reset()
                path.moveTo(cx - a * 0.8f, cy - a * 0.45f)
                path.quadTo(cx - a * 0.55f, cy - a * 0.05f, cx - a * 0.05f, cy + a * 0.3f)
                c.drawPath(path, st)
                // A
                val ax = cx + a * 0.5f
                path.reset()
                path.moveTo(ax - a * 0.5f, cy + a * 0.95f)
                path.lineTo(ax, cy - a * 0.05f)
                path.lineTo(ax + a * 0.5f, cy + a * 0.95f)
                c.drawPath(path, st)
                c.drawLine(ax - a * 0.3f, cy + a * 0.58f, ax + a * 0.3f, cy + a * 0.58f, st)
            }
            EDIT -> {
                // Cursor de texto ⌶ entre ‹ ›
                val h = s * 0.8f
                c.drawLine(cx, cy - h, cx, cy + h, st)
                c.drawLine(cx - s * 0.22f, cy - h, cx + s * 0.22f, cy - h, st)
                c.drawLine(cx - s * 0.22f, cy + h, cx + s * 0.22f, cy + h, st)
                for (sx in intArrayOf(-1, 1)) {
                    path.reset()
                    path.moveTo(cx + sx * s * 0.62f, cy - s * 0.38f)
                    path.lineTo(cx + sx * s * 0.98f, cy)
                    path.lineTo(cx + sx * s * 0.62f, cy + s * 0.38f)
                    c.drawPath(path, st)
                }
            }
            SPELL -> {
                // Líneas de texto con una palomita ✓
                c.drawLine(cx - s * 0.95f, cy - s * 0.6f, cx + s * 0.85f, cy - s * 0.6f, st)
                c.drawLine(cx - s * 0.95f, cy - s * 0.05f, cx + s * 0.35f, cy - s * 0.05f, st)
                c.drawLine(cx - s * 0.95f, cy + s * 0.5f, cx - s * 0.25f, cy + s * 0.5f, st)
                path.reset()
                path.moveTo(cx + s * 0.05f, cy + s * 0.55f)
                path.lineTo(cx + s * 0.38f, cy + s * 0.88f)
                path.lineTo(cx + s * 0.98f, cy + s * 0.2f)
                c.drawPath(path, st)
            }
            SCAN -> {
                // 4 esquinas redondeadas y la línea del escáner
                val rr = s * 0.88f
                val k = rr * 0.5f
                val q = rr * 0.3f
                for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                    val x0 = cx + sx * rr
                    val y0 = cy + sy * rr
                    path.reset()
                    path.moveTo(x0 - sx * k, y0)
                    path.lineTo(x0 - sx * q, y0)
                    path.quadTo(x0, y0, x0, y0 - sy * q)
                    path.lineTo(x0, y0 - sy * k)
                    c.drawPath(path, st)
                }
                c.drawLine(cx - rr * 0.55f, cy, cx + rr * 0.55f, cy, st)
            }
            MIC -> {
                r.set(cx - s * 0.36f, cy - s * 0.98f, cx + s * 0.36f, cy + s * 0.22f)
                c.drawRoundRect(r, s * 0.36f, s * 0.36f, st)
                r.set(cx - s * 0.72f, cy - s * 0.55f, cx + s * 0.72f, cy + s * 0.6f)
                c.drawArc(r, 10f, 160f, false, st)
                c.drawLine(cx, cy + s * 0.62f, cx, cy + s * 0.95f, st)
            }
            HIDE -> {
                path.reset()
                path.moveTo(cx - s * 0.65f, cy - s * 0.28f)
                path.lineTo(cx, cy + s * 0.35f)
                path.lineTo(cx + s * 0.65f, cy - s * 0.28f)
                c.drawPath(path, st)
            }
            MENU -> {
                // Capas: un rombo arriba y dos capas debajo (moderno, "más opciones")
                val w = s * 0.98f
                val h = s * 0.5f
                path.reset()
                path.moveTo(cx, cy - s * 0.95f)
                path.lineTo(cx + w, cy - s * 0.95f + h)
                path.lineTo(cx, cy - s * 0.95f + 2 * h)
                path.lineTo(cx - w, cy - s * 0.95f + h)
                path.close()
                c.drawPath(path, fl)
                for (k in 1..2) {
                    val y = cy - s * 0.95f + h + k * s * 0.42f
                    path.reset()
                    path.moveTo(cx - w, y)
                    path.lineTo(cx, y + h)
                    path.lineTo(cx + w, y)
                    c.drawPath(path, st)
                }
            }
            CLOSE -> {
                val a = s * 0.62f
                c.drawLine(cx - a, cy - a, cx + a, cy + a, st)
                c.drawLine(cx + a, cy - a, cx - a, cy + a, st)
            }
            BACK -> {
                c.drawLine(cx - s * 0.8f, cy, cx + s * 0.8f, cy, st)
                path.reset()
                path.moveTo(cx - s * 0.2f, cy - s * 0.6f)
                path.lineTo(cx - s * 0.8f, cy)
                path.lineTo(cx - s * 0.2f, cy + s * 0.6f)
                c.drawPath(path, st)
            }
            SWAP -> {
                // Dos flechas: → arriba, ← abajo
                val y1 = cy - s * 0.38f
                val y2 = cy + s * 0.38f
                c.drawLine(cx - s * 0.85f, y1, cx + s * 0.85f, y1, st)
                path.reset()
                path.moveTo(cx + s * 0.45f, y1 - s * 0.38f)
                path.lineTo(cx + s * 0.85f, y1)
                path.lineTo(cx + s * 0.45f, y1 + s * 0.38f)
                c.drawPath(path, st)
                c.drawLine(cx - s * 0.85f, y2, cx + s * 0.85f, y2, st)
                path.reset()
                path.moveTo(cx - s * 0.45f, y2 - s * 0.38f)
                path.lineTo(cx - s * 0.85f, y2)
                path.lineTo(cx - s * 0.45f, y2 + s * 0.38f)
                c.drawPath(path, st)
            }
            PIN -> {
                // Chincheta moderna: cabeza redondeada + punta
                r.set(cx - s * 0.5f, cy - s * 0.95f, cx + s * 0.5f, cy - s * 0.2f)
                c.drawRoundRect(r, s * 0.2f, s * 0.2f, fl)
                c.drawLine(cx - s * 0.8f, cy - s * 0.12f, cx + s * 0.8f, cy - s * 0.12f, st)
                c.drawLine(cx, cy - s * 0.12f, cx, cy + s * 0.95f, st)
            }
            IMAGE -> {
                r.set(cx - s * 0.9f, cy - s * 0.75f, cx + s * 0.9f, cy + s * 0.75f)
                c.drawRoundRect(r, s * 0.25f, s * 0.25f, st)
                path.reset()
                path.moveTo(cx - s * 0.65f, cy + s * 0.5f)
                path.lineTo(cx - s * 0.15f, cy - s * 0.05f)
                path.lineTo(cx + s * 0.15f, cy + s * 0.22f)
                path.lineTo(cx + s * 0.35f, cy + s * 0.05f)
                path.lineTo(cx + s * 0.65f, cy + s * 0.5f)
                c.drawPath(path, st)
                c.drawCircle(cx + s * 0.4f, cy - s * 0.32f, s * 0.13f, fl)
            }
            RECENT -> {
                c.drawCircle(cx, cy, s * 0.9f, st)
                path.reset()
                path.moveTo(cx, cy - s * 0.5f)
                path.lineTo(cx, cy)
                path.lineTo(cx + s * 0.38f, cy + s * 0.25f)
                c.drawPath(path, st)
            }
            PEOPLE -> {
                c.drawCircle(cx, cy - s * 0.45f, s * 0.36f, st)
                r.set(cx - s * 0.8f, cy + s * 0.1f, cx + s * 0.8f, cy + s * 1.5f)
                c.drawArc(r, 180f, 180f, false, st)
                c.drawLine(cx - s * 0.8f, cy + s * 0.8f, cx + s * 0.8f, cy + s * 0.8f, st)
            }
            ANIMAL -> {
                // Huella
                r.set(cx - s * 0.48f, cy + s * 0.02f, cx + s * 0.48f, cy + s * 0.85f)
                c.drawOval(r, st)
                c.drawCircle(cx - s * 0.72f, cy - s * 0.2f, s * 0.17f, fl)
                c.drawCircle(cx - s * 0.28f, cy - s * 0.65f, s * 0.17f, fl)
                c.drawCircle(cx + s * 0.28f, cy - s * 0.65f, s * 0.17f, fl)
                c.drawCircle(cx + s * 0.72f, cy - s * 0.2f, s * 0.17f, fl)
            }
            FOOD -> {
                // Taza con vapor
                r.set(cx - s * 0.8f, cy - s * 0.15f, cx + s * 0.45f, cy + s * 0.9f)
                c.drawRoundRect(r, s * 0.2f, s * 0.2f, st)
                r.set(cx + s * 0.25f, cy + s * 0.05f, cx + s * 0.85f, cy + s * 0.6f)
                c.drawArc(r, -90f, 180f, false, st)
                for (k in 0..1) {
                    val x = cx - s * (0.38f - k * 0.45f)
                    path.reset()
                    path.moveTo(x, cy - s * 0.4f)
                    path.quadTo(x - s * 0.18f, cy - s * 0.62f, x, cy - s * 0.95f)
                    c.drawPath(path, st)
                }
            }
            TRAVEL -> {
                // Carro de frente
                path.reset()
                path.moveTo(cx - s * 0.9f, cy + s * 0.55f)
                path.lineTo(cx - s * 0.9f, cy + s * 0.05f)
                path.lineTo(cx - s * 0.6f, cy - s * 0.6f)
                path.lineTo(cx + s * 0.6f, cy - s * 0.6f)
                path.lineTo(cx + s * 0.9f, cy + s * 0.05f)
                path.lineTo(cx + s * 0.9f, cy + s * 0.55f)
                path.close()
                c.drawPath(path, st)
                c.drawLine(cx - s * 0.9f, cy + s * 0.05f, cx + s * 0.9f, cy + s * 0.05f, st)
                c.drawCircle(cx - s * 0.5f, cy + s * 0.3f, s * 0.1f, fl)
                c.drawCircle(cx + s * 0.5f, cy + s * 0.3f, s * 0.1f, fl)
                c.drawLine(cx - s * 0.6f, cy + s * 0.55f, cx - s * 0.6f, cy + s * 0.85f, st)
                c.drawLine(cx + s * 0.6f, cy + s * 0.55f, cx + s * 0.6f, cy + s * 0.85f, st)
            }
            SPORT -> {
                // Pelota
                c.drawCircle(cx, cy, s * 0.9f, st)
                r.set(cx - s * 1.5f, cy - s * 0.9f, cx - s * 0.1f, cy + s * 0.9f)
                c.drawArc(r, -60f, 120f, false, st)
                r.set(cx + s * 0.1f, cy - s * 0.9f, cx + s * 1.5f, cy + s * 0.9f)
                c.drawArc(r, 120f, 120f, false, st)
            }
            IDEA -> {
                // Foco
                r.set(cx - s * 0.65f, cy - s * 0.95f, cx + s * 0.65f, cy + s * 0.35f)
                c.drawArc(r, 140f, 260f, false, st)
                c.drawLine(cx - s * 0.32f, cy + s * 0.55f, cx + s * 0.32f, cy + s * 0.55f, st)
                c.drawLine(cx - s * 0.2f, cy + s * 0.88f, cx + s * 0.2f, cy + s * 0.88f, st)
                c.drawLine(cx - s * 0.5f, cy + s * 0.12f, cx - s * 0.32f, cy + s * 0.55f, st)
                c.drawLine(cx + s * 0.5f, cy + s * 0.12f, cx + s * 0.32f, cy + s * 0.55f, st)
            }
            HEART -> {
                path.reset()
                path.moveTo(cx, cy + s * 0.85f)
                path.cubicTo(cx - s * 1.25f, cy, cx - s * 0.75f, cy - s * 1.05f, cx, cy - s * 0.4f)
                path.cubicTo(cx + s * 0.75f, cy - s * 1.05f, cx + s * 1.25f, cy, cx, cy + s * 0.85f)
                path.close()
                c.drawPath(path, st)
            }
            FLAG -> {
                c.drawLine(cx - s * 0.7f, cy - s * 0.9f, cx - s * 0.7f, cy + s * 0.95f, st)
                path.reset()
                path.moveTo(cx - s * 0.7f, cy - s * 0.85f)
                path.quadTo(cx - s * 0.1f, cy - s * 1.1f, cx + s * 0.2f, cy - s * 0.75f)
                path.quadTo(cx + s * 0.5f, cy - s * 0.45f, cx + s * 0.85f, cy - s * 0.7f)
                path.lineTo(cx + s * 0.85f, cy + s * 0.1f)
                path.quadTo(cx + s * 0.5f, cy + s * 0.35f, cx + s * 0.2f, cy + s * 0.05f)
                path.quadTo(cx - s * 0.1f, cy - s * 0.3f, cx - s * 0.7f, cy - s * 0.05f)
                c.drawPath(path, st)
            }
            MORE -> {
                // Tres puntos
                for (k in -1..1) c.drawCircle(cx + k * s * 0.62f, cy, s * 0.15f, fl)
            }
            RESIZE -> {
                // Marco punteado con una esquina sólida (como Gboard)
                val a = s * 0.85f
                val dot = s * 0.09f
                for (k in 0..3) {
                    val t = -a + k * a * 0.42f
                    c.drawCircle(cx + t, cy - a, dot, fl)
                    c.drawCircle(cx - a, cy + t, dot, fl)
                    c.drawCircle(cx + a, cy - t, dot, fl)
                    c.drawCircle(cx - t, cy + a, dot, fl)
                }
                path.reset()
                path.moveTo(cx + a * 0.1f, cy - a)
                path.lineTo(cx + a, cy - a)
                path.lineTo(cx + a, cy - a * 0.1f)
                c.drawPath(path, st)
            }
            EYE, EYE_OFF -> {
                // Ojito (ver u ocultar la contraseña); tachado = oculta
                path.reset()
                path.moveTo(cx - s, cy)
                path.quadTo(cx, cy - s * 1.15f, cx + s, cy)
                path.quadTo(cx, cy + s * 1.15f, cx - s, cy)
                path.close()
                c.drawPath(path, st)
                c.drawCircle(cx, cy, s * 0.3f, fl)
                if (kind == EYE_OFF) {
                    val p0 = st.color
                    st.color = bg
                    st.strokeWidth = st.strokeWidth * 2.2f
                    c.drawLine(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f, st)
                    st.color = p0
                    st.strokeWidth = st.strokeWidth / 2.2f
                    c.drawLine(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s * 0.8f, st)
                }
            }
            SHAPES -> {
                // Un círculo y un cuadrito (formas de las teclas)
                c.drawCircle(cx - s * 0.32f, cy - s * 0.3f, s * 0.55f, st)
                r.set(cx - s * 0.05f, cy - s * 0.05f, cx + s * 0.95f, cy + s * 0.95f)
                c.drawRoundRect(r, s * 0.2f, s * 0.2f, fl)
            }
            BRUSH -> {
                // Pincel (Estilo)
                c.drawLine(cx + s * 0.9f, cy - s * 0.9f, cx - s * 0.1f, cy + s * 0.1f, st)
                path.reset()
                path.moveTo(cx - s * 0.2f, cy + s * 0.0f)
                path.quadTo(cx - s * 0.95f, cy + s * 0.1f, cx - s * 0.95f, cy + s * 0.95f)
                path.quadTo(cx - s * 0.1f, cy + s * 0.95f, cx + s * 0.0f, cy + s * 0.2f)
                path.close()
                c.drawPath(path, fl)
            }
            BUBBLE -> {
                // Globo de chat con dos líneas (Frases)
                r.set(cx - s * 0.95f, cy - s * 0.8f, cx + s * 0.95f, cy + s * 0.45f)
                c.drawRoundRect(r, s * 0.35f, s * 0.35f, st)
                path.reset()
                path.moveTo(cx - s * 0.45f, cy + s * 0.45f)
                path.lineTo(cx - s * 0.6f, cy + s * 0.95f)
                path.lineTo(cx - s * 0.05f, cy + s * 0.45f)
                c.drawPath(path, st)
                c.drawLine(cx - s * 0.55f, cy - s * 0.35f, cx + s * 0.55f, cy - s * 0.35f, st)
                c.drawLine(cx - s * 0.55f, cy + s * 0.02f, cx + s * 0.25f, cy + s * 0.02f, st)
            }
            READ -> {
                // Globo de mensaje con flechas (traducir mensaje)
                r.set(cx - s * 0.95f, cy - s * 0.85f, cx + s * 0.95f, cy + s * 0.45f)
                c.drawRoundRect(r, s * 0.35f, s * 0.35f, st)
                path.reset()
                path.moveTo(cx + s * 0.45f, cy + s * 0.45f)
                path.lineTo(cx + s * 0.6f, cy + s * 0.95f)
                path.lineTo(cx + s * 0.05f, cy + s * 0.45f)
                c.drawPath(path, st)
                c.drawLine(cx - s * 0.5f, cy - s * 0.38f, cx + s * 0.5f, cy - s * 0.38f, st)
                c.drawLine(cx + s * 0.28f, cy - s * 0.55f, cx + s * 0.5f, cy - s * 0.38f, st)
                c.drawLine(cx - s * 0.5f, cy + s * 0.0f, cx + s * 0.5f, cy + s * 0.0f, st)
                c.drawLine(cx - s * 0.28f, cy + s * 0.17f, cx - s * 0.5f, cy + s * 0.0f, st)
            }
            ONEHAND -> {
                // Teléfono con el teclado pegado a un lado
                r.set(cx - s * 0.6f, cy - s * 0.95f, cx + s * 0.6f, cy + s * 0.95f)
                c.drawRoundRect(r, s * 0.2f, s * 0.2f, st)
                r.set(cx - s * 0.6f, cy + s * 0.15f, cx + s * 0.25f, cy + s * 0.95f)
                c.drawRoundRect(r, s * 0.15f, s * 0.15f, fl)
            }
            CALC -> {
                // Calculadora
                r.set(cx - s * 0.75f, cy - s * 0.95f, cx + s * 0.75f, cy + s * 0.95f)
                c.drawRoundRect(r, s * 0.2f, s * 0.2f, st)
                c.drawLine(cx - s * 0.4f, cy - s * 0.5f, cx + s * 0.4f, cy - s * 0.5f, st)
                for (yy in 0..1) for (xx in 0..2)
                    c.drawCircle(cx - s * 0.4f + xx * s * 0.4f, cy + s * 0.05f + yy * s * 0.45f, s * 0.09f, fl)
            }
            BOX -> {
                // Caja (materiales)
                path.reset()
                path.moveTo(cx, cy - s * 0.95f)
                path.lineTo(cx + s * 0.9f, cy - s * 0.5f)
                path.lineTo(cx + s * 0.9f, cy + s * 0.5f)
                path.lineTo(cx, cy + s * 0.95f)
                path.lineTo(cx - s * 0.9f, cy + s * 0.5f)
                path.lineTo(cx - s * 0.9f, cy - s * 0.5f)
                path.close()
                c.drawPath(path, st)
                c.drawLine(cx - s * 0.9f, cy - s * 0.5f, cx, cy - s * 0.05f, st)
                c.drawLine(cx + s * 0.9f, cy - s * 0.5f, cx, cy - s * 0.05f, st)
                c.drawLine(cx, cy - s * 0.05f, cx, cy + s * 0.95f, st)
            }
            REPLY -> {
                // Globo con flecha de responder
                r.set(cx - s * 0.95f, cy - s * 0.8f, cx + s * 0.95f, cy + s * 0.5f)
                c.drawRoundRect(r, s * 0.35f, s * 0.35f, st)
                path.reset()
                path.moveTo(cx - s * 0.4f, cy + s * 0.5f)
                path.lineTo(cx - s * 0.55f, cy + s * 0.95f)
                path.lineTo(cx, cy + s * 0.5f)
                c.drawPath(path, st)
                path.reset()
                path.moveTo(cx - s * 0.1f, cy - s * 0.5f)
                path.lineTo(cx - s * 0.45f, cy - s * 0.2f)
                path.lineTo(cx - s * 0.1f, cy + s * 0.1f)
                c.drawPath(path, st)
                path.reset()
                path.moveTo(cx - s * 0.42f, cy - s * 0.2f)
                path.quadTo(cx + s * 0.45f, cy - s * 0.25f, cx + s * 0.5f, cy + s * 0.15f)
                c.drawPath(path, st)
            }
            PIN -> {
                // Marcador de mapa (mis sitios)
                path.reset()
                path.moveTo(cx, cy + s * 0.98f)
                path.cubicTo(cx - s * 0.95f, cy - s * 0.05f, cx - s * 0.75f, cy - s * 0.98f, cx, cy - s * 0.98f)
                path.cubicTo(cx + s * 0.75f, cy - s * 0.98f, cx + s * 0.95f, cy - s * 0.05f, cx, cy + s * 0.98f)
                c.drawPath(path, st)
                c.drawCircle(cx, cy - s * 0.3f, s * 0.25f, fl)
            }
            TRUCK -> {
                // Camioneta (mi jornada)
                path.reset()
                path.moveTo(cx - s, cy + s * 0.45f)
                path.lineTo(cx - s, cy - s * 0.1f)
                path.lineTo(cx - s * 0.1f, cy - s * 0.1f)
                path.lineTo(cx - s * 0.1f, cy - s * 0.65f)
                path.lineTo(cx + s * 0.5f, cy - s * 0.65f)
                path.lineTo(cx + s * 0.95f, cy - s * 0.1f)
                path.lineTo(cx + s * 0.95f, cy + s * 0.45f)
                path.close()
                c.drawPath(path, st)
                c.drawCircle(cx - s * 0.55f, cy + s * 0.55f, s * 0.24f, fl)
                c.drawCircle(cx + s * 0.5f, cy + s * 0.55f, s * 0.24f, fl)
            }
            BARCODE -> {
                // Esquinas de escáner con barras en medio
                val k = s * 0.95f
                val q = s * 0.35f
                c.drawLine(cx - k, cy - k, cx - k + q, cy - k, st); c.drawLine(cx - k, cy - k, cx - k, cy - k + q, st)
                c.drawLine(cx + k, cy - k, cx + k - q, cy - k, st); c.drawLine(cx + k, cy - k, cx + k, cy - k + q, st)
                c.drawLine(cx - k, cy + k, cx - k + q, cy + k, st); c.drawLine(cx - k, cy + k, cx - k, cy + k - q, st)
                c.drawLine(cx + k, cy + k, cx + k - q, cy + k, st); c.drawLine(cx + k, cy + k, cx + k, cy + k - q, st)
                val bars = floatArrayOf(-0.5f, -0.25f, -0.05f, 0.2f, 0.45f)
                for (b in bars) c.drawLine(cx + b * s, cy - s * 0.45f, cx + b * s, cy + s * 0.45f, st)
            }
            CLOCK -> {
                // Reloj (hora, fecha y lugar)
                c.drawCircle(cx, cy, s * 0.92f, st)
                c.drawLine(cx, cy, cx, cy - s * 0.55f, st)
                c.drawLine(cx, cy, cx + s * 0.4f, cy + s * 0.2f, st)
            }
            NOTEBOOK -> {
                // Libreta con espiral
                r.set(cx - s * 0.65f, cy - s * 0.95f, cx + s * 0.8f, cy + s * 0.95f)
                c.drawRoundRect(r, s * 0.15f, s * 0.15f, st)
                for (k in 0..3) c.drawLine(cx - s * 0.95f, cy - s * 0.6f + k * s * 0.4f, cx - s * 0.5f, cy - s * 0.6f + k * s * 0.4f, st)
                c.drawLine(cx - s * 0.2f, cy - s * 0.35f, cx + s * 0.5f, cy - s * 0.35f, st)
                c.drawLine(cx - s * 0.2f, cy + s * 0.05f, cx + s * 0.5f, cy + s * 0.05f, st)
            }
            TICKETS -> {
                // Hojas apiladas (mis tickets)
                r.set(cx - s * 0.5f, cy - s * 0.95f, cx + s * 0.95f, cy + s * 0.55f)
                c.drawRoundRect(r, s * 0.15f, s * 0.15f, st)
                r.set(cx - s * 0.95f, cy - s * 0.55f, cx + s * 0.5f, cy + s * 0.95f)
                fl.color = bg
                c.drawRoundRect(r, s * 0.15f, s * 0.15f, fl)
                fl.color = color
                c.drawRoundRect(r, s * 0.15f, s * 0.15f, st)
                c.drawLine(cx - s * 0.6f, cy - s * 0.1f, cx + s * 0.15f, cy - s * 0.1f, st)
                c.drawLine(cx - s * 0.6f, cy + s * 0.3f, cx + s * 0.15f, cy + s * 0.3f, st)
            }
            BOOK -> {
                // Libro abierto (palabra de la semana)
                path.reset()
                path.moveTo(cx, cy - s * 0.55f)
                path.quadTo(cx - s * 0.5f, cy - s * 0.85f, cx - s, cy - s * 0.7f)
                path.lineTo(cx - s, cy + s * 0.75f)
                path.quadTo(cx - s * 0.5f, cy + s * 0.6f, cx, cy + s * 0.9f)
                path.quadTo(cx + s * 0.5f, cy + s * 0.6f, cx + s, cy + s * 0.75f)
                path.lineTo(cx + s, cy - s * 0.7f)
                path.quadTo(cx + s * 0.5f, cy - s * 0.85f, cx, cy - s * 0.55f)
                c.drawPath(path, st)
                c.drawLine(cx, cy - s * 0.55f, cx, cy + s * 0.9f, st)
            }
            TICKET -> {
                // Tablita con hoja y renglones (ticket de trabajo)
                r.set(cx - s * 0.75f, cy - s * 0.8f, cx + s * 0.75f, cy + s * 0.98f)
                c.drawRoundRect(r, s * 0.18f, s * 0.18f, st)
                r.set(cx - s * 0.32f, cy - s * 0.98f, cx + s * 0.32f, cy - s * 0.62f)
                c.drawRoundRect(r, s * 0.1f, s * 0.1f, fl)
                c.drawLine(cx - s * 0.42f, cy - s * 0.2f, cx + s * 0.42f, cy - s * 0.2f, st)
                c.drawLine(cx - s * 0.42f, cy + s * 0.18f, cx + s * 0.42f, cy + s * 0.18f, st)
                c.drawLine(cx - s * 0.42f, cy + s * 0.56f, cx + s * 0.1f, cy + s * 0.56f, st)
            }
            REPORT -> {
                // Micrófono pequeño con renglones (dictar reporte)
                r.set(cx - s * 0.95f, cy - s * 0.85f, cx - s * 0.35f, cy + s * 0.15f)
                c.drawRoundRect(r, s * 0.3f, s * 0.3f, fl)
                r.set(cx - s * 1.05f, cy - s * 0.35f, cx - s * 0.25f, cy + s * 0.45f)
                c.drawArc(r, 0f, 180f, false, st)
                c.drawLine(cx - s * 0.65f, cy + s * 0.45f, cx - s * 0.65f, cy + s * 0.8f, st)
                c.drawLine(cx + s * 0.05f, cy - s * 0.55f, cx + s * 0.95f, cy - s * 0.55f, st)
                c.drawLine(cx + s * 0.05f, cy - s * 0.05f, cx + s * 0.95f, cy - s * 0.05f, st)
                c.drawLine(cx + s * 0.05f, cy + s * 0.45f, cx + s * 0.7f, cy + s * 0.45f, st)
            }
            FORMAL -> {
                // Destello de 4 puntas (palabras más formales)
                path.reset()
                path.moveTo(cx, cy - s)
                path.quadTo(cx + s * 0.12f, cy - s * 0.12f, cx + s, cy)
                path.quadTo(cx + s * 0.12f, cy + s * 0.12f, cx, cy + s)
                path.quadTo(cx - s * 0.12f, cy + s * 0.12f, cx - s, cy)
                path.quadTo(cx - s * 0.12f, cy - s * 0.12f, cx, cy - s)
                path.close()
                c.drawPath(path, fl)
            }
            FLOAT -> {
                // Ventanita flotante con su barrita para moverla
                r.set(cx - s * 0.95f, cy - s * 0.55f, cx + s * 0.95f, cy + s * 0.85f)
                c.drawRoundRect(r, s * 0.2f, s * 0.2f, st)
                c.drawLine(cx - s * 0.35f, cy - s * 0.88f, cx + s * 0.35f, cy - s * 0.88f, st)
                for (k in 0..2) c.drawCircle(cx - s * 0.5f + k * s * 0.5f, cy + s * 0.15f, s * 0.1f, fl)
            }
            REVIEW -> {
                // Círculo con palomita (revisar antes de enviar)
                c.drawCircle(cx, cy, s * 0.92f, st)
                path.reset()
                path.moveTo(cx - s * 0.45f, cy + s * 0.02f)
                path.lineTo(cx - s * 0.12f, cy + s * 0.35f)
                path.lineTo(cx + s * 0.48f, cy - s * 0.32f)
                c.drawPath(path, st)
            }
            SEARCH -> {
                // Lupa
                c.drawCircle(cx - s * 0.18f, cy - s * 0.18f, s * 0.58f, st)
                c.drawLine(cx + s * 0.25f, cy + s * 0.25f, cx + s * 0.85f, cy + s * 0.85f, st)
            }
            GIF -> {
                // Recuadro con la palabra GIF
                r.set(cx - s * 0.98f, cy - s * 0.7f, cx + s * 0.98f, cy + s * 0.7f)
                c.drawRoundRect(r, s * 0.2f, s * 0.2f, st)
                // G
                r.set(cx - s * 0.78f, cy - s * 0.38f, cx - s * 0.24f, cy + s * 0.38f)
                c.drawArc(r, 20f, 290f, false, st)
                c.drawLine(cx - s * 0.5f, cy + s * 0.05f, cx - s * 0.26f, cy + s * 0.05f, st)
                // I
                c.drawLine(cx - s * 0.04f, cy - s * 0.38f, cx - s * 0.04f, cy + s * 0.38f, st)
                // F
                c.drawLine(cx + s * 0.22f, cy - s * 0.38f, cx + s * 0.22f, cy + s * 0.38f, st)
                c.drawLine(cx + s * 0.22f, cy - s * 0.38f, cx + s * 0.72f, cy - s * 0.38f, st)
                c.drawLine(cx + s * 0.22f, cy - s * 0.02f, cx + s * 0.62f, cy - s * 0.02f, st)
            }
            RIPPLE -> {
                // Punto con ondas (efecto al tocar)
                c.drawCircle(cx, cy, s * 0.22f, fl)
                c.drawCircle(cx, cy, s * 0.55f, st)
                r.set(cx - s * 0.95f, cy - s * 0.95f, cx + s * 0.95f, cy + s * 0.95f)
                c.drawArc(r, 200f, 50f, false, st)
                c.drawArc(r, 290f, 50f, false, st)
                c.drawArc(r, 20f, 50f, false, st)
                c.drawArc(r, 110f, 50f, false, st)
            }
            TEXTSIZE -> {
                // "A" grande y "a" chiquita
                path.reset()
                path.moveTo(cx - s * 0.95f, cy + s * 0.85f)
                path.lineTo(cx - s * 0.4f, cy - s * 0.85f)
                path.lineTo(cx + s * 0.15f, cy + s * 0.85f)
                c.drawPath(path, st)
                c.drawLine(cx - s * 0.75f, cy + s * 0.3f, cx - s * 0.05f, cy + s * 0.3f, st)
                path.reset()
                path.moveTo(cx + s * 0.35f, cy + s * 0.85f)
                path.lineTo(cx + s * 0.65f, cy + s * 0.05f)
                path.lineTo(cx + s * 0.95f, cy + s * 0.85f)
                c.drawPath(path, st)
            }
            KEYSIZE -> {
                // Tecla chica dentro de una grande
                r.set(cx - s * 0.95f, cy - s * 0.95f, cx + s * 0.95f, cy + s * 0.95f)
                c.drawRoundRect(r, s * 0.3f, s * 0.3f, st)
                r.set(cx - s * 0.4f, cy - s * 0.4f, cx + s * 0.4f, cy + s * 0.4f)
                c.drawRoundRect(r, s * 0.15f, s * 0.15f, fl)
            }
            SPEAKER -> {
                path.reset()
                path.moveTo(cx - s * 0.9f, cy - s * 0.3f)
                path.lineTo(cx - s * 0.5f, cy - s * 0.3f)
                path.lineTo(cx, cy - s * 0.75f)
                path.lineTo(cx, cy + s * 0.75f)
                path.lineTo(cx - s * 0.5f, cy + s * 0.3f)
                path.lineTo(cx - s * 0.9f, cy + s * 0.3f)
                path.close()
                c.drawPath(path, st)
                r.set(cx - s * 0.2f, cy - s * 0.45f, cx + s * 0.45f, cy + s * 0.45f)
                c.drawArc(r, -60f, 120f, false, st)
                r.set(cx - s * 0.2f, cy - s * 0.85f, cx + s * 0.9f, cy + s * 0.85f)
                c.drawArc(r, -55f, 110f, false, st)
            }
            VIBRATE -> {
                // Teléfono con rayitas a los lados
                r.set(cx - s * 0.4f, cy - s * 0.85f, cx + s * 0.4f, cy + s * 0.85f)
                c.drawRoundRect(r, s * 0.18f, s * 0.18f, st)
                for (sx in intArrayOf(-1, 1)) {
                    val x = cx + sx * s * 0.7f
                    c.drawLine(x, cy - s * 0.4f, x, cy + s * 0.4f, st)
                    val x2 = cx + sx * s * 0.98f
                    c.drawLine(x2, cy - s * 0.2f, x2, cy + s * 0.2f, st)
                }
            }
            CODE -> {
                // </>  (programar)
                path.reset()
                path.moveTo(cx - s * 0.45f, cy - s * 0.6f)
                path.lineTo(cx - s * 1.0f, cy)
                path.lineTo(cx - s * 0.45f, cy + s * 0.6f)
                c.drawPath(path, st)
                path.reset()
                path.moveTo(cx + s * 0.45f, cy - s * 0.6f)
                path.lineTo(cx + s * 1.0f, cy)
                path.lineTo(cx + s * 0.45f, cy + s * 0.6f)
                c.drawPath(path, st)
                c.drawLine(cx + s * 0.2f, cy - s * 0.85f, cx - s * 0.2f, cy + s * 0.85f, st)
            }
            MAIL -> {
                // Sobre de carta
                r.set(cx - s * 0.95f, cy - s * 0.65f, cx + s * 0.95f, cy + s * 0.65f)
                c.drawRoundRect(r, s * 0.2f, s * 0.2f, st)
                path.reset()
                path.moveTo(cx - s * 0.8f, cy - s * 0.45f)
                path.lineTo(cx, cy + s * 0.1f)
                path.lineTo(cx + s * 0.8f, cy - s * 0.45f)
                c.drawPath(path, st)
            }
            LOCK -> {
                // Candado (códigos de verificación)
                r.set(cx - s * 0.75f, cy - s * 0.1f, cx + s * 0.75f, cy + s * 0.95f)
                c.drawRoundRect(r, s * 0.2f, s * 0.2f, st)
                r.set(cx - s * 0.45f, cy - s * 0.95f, cx + s * 0.45f, cy + s * 0.1f)
                c.drawArc(r, 180f, 180f, false, st)
                c.drawLine(cx - s * 0.45f, cy - s * 0.42f, cx - s * 0.45f, cy - s * 0.1f, st)
                c.drawLine(cx + s * 0.45f, cy - s * 0.42f, cx + s * 0.45f, cy - s * 0.1f, st)
                c.drawCircle(cx, cy + s * 0.42f, s * 0.13f, fl)
            }
            HACKER -> {
                // Hacker con capucha (con hombros) y ">_" en la cara
                path.reset()
                path.moveTo(cx - s * 1.0f, cy + s * 1.0f)
                path.quadTo(cx - s * 0.95f, cy + s * 0.55f, cx - s * 0.62f, cy + s * 0.45f)
                path.cubicTo(cx - s * 0.85f, cy - s * 0.2f, cx - s * 0.55f, cy - s * 0.85f, cx, cy - s * 1.02f)
                path.cubicTo(cx + s * 0.55f, cy - s * 0.85f, cx + s * 0.85f, cy - s * 0.2f, cx + s * 0.62f, cy + s * 0.45f)
                path.quadTo(cx + s * 0.95f, cy + s * 0.55f, cx + s * 1.0f, cy + s * 1.0f)
                c.drawPath(path, st)
                // Cara (hueco de la capucha)
                r.set(cx - s * 0.46f, cy - s * 0.5f, cx + s * 0.46f, cy + s * 0.5f)
                c.drawOval(r, fl)
                // >_ en color de fondo
                val p0 = st.color
                st.color = bg
                st.strokeWidth = st.strokeWidth * 0.8f
                path.reset()
                path.moveTo(cx - s * 0.26f, cy - s * 0.2f)
                path.lineTo(cx - s * 0.04f, cy)
                path.lineTo(cx - s * 0.26f, cy + s * 0.2f)
                c.drawPath(path, st)
                c.drawLine(cx + s * 0.04f, cy + s * 0.22f, cx + s * 0.26f, cy + s * 0.22f, st)
                st.color = p0
            }
            CHEVRON -> {
                path.reset()
                path.moveTo(cx - s * 0.25f, cy - s * 0.55f)
                path.lineTo(cx + s * 0.3f, cy)
                path.lineTo(cx - s * 0.25f, cy + s * 0.55f)
                c.drawPath(path, st)
            }
            PENCIL -> {
                // Lápiz inclinado
                val a = s * 0.62f
                path.reset()
                path.moveTo(cx - a - s * 0.2f, cy + a + s * 0.2f)
                path.lineTo(cx - a - s * 0.12f, cy + a - s * 0.32f)
                path.lineTo(cx + a * 0.55f, cy - a - s * 0.05f)
                path.lineTo(cx + a + s * 0.05f, cy - a * 0.55f)
                path.lineTo(cx - a + s * 0.32f, cy + a + s * 0.12f)
                path.close()
                c.drawPath(path, st)
                c.drawLine(cx + a * 0.1f, cy - a * 0.65f, cx + a * 0.65f, cy - a * 0.1f, st)
            }
            GLOBE -> {
                val rr = s * 0.9f
                c.drawCircle(cx, cy, rr, st)
                r.set(cx - rr * 0.42f, cy - rr, cx + rr * 0.42f, cy + rr)
                c.drawOval(r, st)
                c.drawLine(cx - rr, cy, cx + rr, cy, st)
            }
            DELETE -> {
                path.reset()
                path.moveTo(cx - s * 0.35f, cy - s * 0.65f)
                path.lineTo(cx + s * 0.95f, cy - s * 0.65f)
                path.lineTo(cx + s * 0.95f, cy + s * 0.65f)
                path.lineTo(cx - s * 0.35f, cy + s * 0.65f)
                path.lineTo(cx - s * 0.98f, cy)
                path.close()
                c.drawPath(path, st)
                val x0 = cx + s * 0.3f
                val a = s * 0.25f
                c.drawLine(x0 - a, cy - a, x0 + a, cy + a, st)
                c.drawLine(x0 + a, cy - a, x0 - a, cy + a, st)
            }
        }
    }
}

/** Botón con un ícono de [Icons] (para las barras y paneles). */
@SuppressLint("ViewConstructor")
class IconView(context: Context, var kind: Int, private val sizeDp: Float = 10f) : View(context) {
    var color: Int = 0xFFFFFFFF.toInt()
        set(v) { field = v; invalidate() }
    /** Color de fondo detrás del ícono (solo lo usan algunos íconos). */
    var bgColor: Int = 0
    init { isClickable = true }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        Icons.draw(canvas, kind, width / 2f, height / 2f, sizeDp * d, color, bgColor, d)
    }
}
