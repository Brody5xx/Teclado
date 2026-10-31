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
