package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/** Las herramientas que se pueden acomodar entre la barra de arriba y "Más opciones". */
object Tools {
    class Info(val tool: Int, val icon: Int, val es: String, val en: String) {
        val label get() = L.t(es, en)
    }

    val ALL = listOf(
        Info(KeyboardView.TOOL_EMOJI, Icons.EMOJI, "Emojis", "Emojis"),
        Info(KeyboardView.TOOL_CLIP, Icons.CLIP, "Portapapeles", "Clipboard"),
        Info(KeyboardView.TOOL_TRANSLATE, Icons.TRANSLATE, "Traducir", "Translate"),
        Info(KeyboardView.TOOL_SCAN, Icons.SCAN, "Escanear texto", "Scan text"),
        Info(KeyboardView.TOOL_SPELL, Icons.SPELL, "Ortografía", "Spell check"),
        Info(KeyboardView.TOOL_EDIT, Icons.EDIT, "Edición de texto", "Text editing"),
        Info(KeyboardView.TOOL_RESIZE, Icons.RESIZE, "Cambiar tamaño", "Resize"),
        Info(KeyboardView.TOOL_LANG, Icons.GLOBE, "Siguiente idioma", "Next language"),
        Info(KeyboardView.TOOL_THEME, Icons.THEME, "Tema claro / oscuro", "Light / dark theme"),
        Info(KeyboardView.TOOL_SETTINGS, Icons.SETTINGS, "Ajustes", "Settings"),
    )

    private val DEFAULT_BAR = listOf(
        KeyboardView.TOOL_EMOJI, KeyboardView.TOOL_CLIP, KeyboardView.TOOL_TRANSLATE,
        KeyboardView.TOOL_SCAN, KeyboardView.TOOL_SPELL, KeyboardView.TOOL_EDIT,
    )

    fun info(tool: Int): Info? = ALL.firstOrNull { it.tool == tool }

    /** Cuántas herramientas caben en la barra de arriba (además de ⋯, micrófono y esconder). */
    fun maxBar(ctx: Context): Int {
        val dm = ctx.resources.displayMetrics
        val wDp = minOf(dm.widthPixels, dm.heightPixels) / dm.density
        return (((wDp - 112f) / 40f).toInt() - 1).coerceIn(3, 8)
    }

    private fun parse(s: String?): List<Int> =
        s?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { info(it) != null }?.distinct() ?: emptyList()

    /** (barra de arriba, más opciones), en el orden que elegiste. */
    fun load(ctx: Context, prefs: SharedPreferences): Pair<List<Int>, List<Int>> {
        val max = maxBar(ctx)
        val saved = prefs.getString(Prefs.BAR_TOOLS, null)
        val bar = (if (saved == null) DEFAULT_BAR else parse(saved)).take(max)
        val more = parse(prefs.getString(Prefs.MORE_TOOLS, null)).filter { it !in bar }.toMutableList()
        for (t in ALL) if (t.tool !in bar && t.tool !in more) more.add(t.tool)
        return bar to more
    }

    fun save(prefs: SharedPreferences, bar: List<Int>, more: List<Int>) {
        prefs.edit()
            .putString(Prefs.BAR_TOOLS, bar.joinToString(","))
            .putString(Prefs.MORE_TOOLS, more.joinToString(","))
            .apply()
    }
}

/**
 * Panel "Más opciones" (como el de Gboard): botones grandes con las herramientas que no están
 * en la barra de arriba. Con el lápiz ✎ puedes acomodarlas arrastrándolas.
 */
@SuppressLint("ViewConstructor")
class MorePanel(context: Context) : FrameLayout(context) {

    interface Actions {
        /** [tool] es una de las constantes KeyboardView.TOOL_... */
        fun onMoreTool(tool: Int)
        fun onMoreBack()
        fun onMoreFeedback()
        /** Acomodaste las herramientas: hay que guardarlas y actualizar la barra. */
        fun onToolsChanged(bar: List<Int>, more: List<Int>)
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val back = IconView(context, Icons.BACK, 8f)
    private val title = TextView(context)
    private val editBtn = IconView(context, Icons.PENCIL, 8f)
    private val doneBtn = TextView(context)
    private val grid = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val arranger = ToolArranger(context)
    private val buttons = ArrayList<Pair<LinearLayout, Pair<IconView, TextView>>>()

    private var bar: List<Int> = emptyList()
    private var more: List<Int> = emptyList()
    private var maxBar = 6
    var editing = false
        private set

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(8f), d(4f), d(8f), d(6f))

        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        back.setOnClickListener {
            actions?.onMoreFeedback()
            if (editing) setEditing(false) else actions?.onMoreBack()
        }
        head.addView(back, LinearLayout.LayoutParams(d(44f), d(40f)))
        title.textSize = 18f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(12f), 0, 0, 0)
        title.maxLines = 1
        title.ellipsize = TextUtils.TruncateAt.END
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        editBtn.setOnClickListener { actions?.onMoreFeedback(); setEditing(true) }
        head.addView(editBtn, LinearLayout.LayoutParams(d(64f), d(38f)))
        doneBtn.textSize = 15f
        doneBtn.gravity = Gravity.CENTER
        doneBtn.setPadding(d(18f), 0, d(18f), 0)
        doneBtn.setOnClickListener { actions?.onMoreFeedback(); setEditing(false) }
        head.addView(doneBtn, LinearLayout.LayoutParams(-2, d(38f)))
        body.addView(head, LinearLayout.LayoutParams(-1, d(46f)))

        val content = FrameLayout(context)
        grid.orientation = LinearLayout.VERTICAL
        scroll.isVerticalScrollBarEnabled = true
        scroll.addView(grid, LayoutParams(-1, -2))
        content.addView(scroll, LayoutParams(-1, -1))
        arranger.onChanged = { b, m ->
            bar = b
            more = m
            actions?.onToolsChanged(b, m)
        }
        arranger.onFeedback = { actions?.onMoreFeedback() }
        content.addView(arranger, LayoutParams(-1, -1))
        body.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(body, LayoutParams(-1, -1))

        L.onChange { refreshTexts(); rebuild(); arranger.invalidate() }
        setEditing(false)
        applyPalette(palette)
    }

    /** Pone qué herramientas van en la barra y cuáles aquí. */
    fun setTools(bar: List<Int>, more: List<Int>, maxBar: Int) {
        this.bar = bar
        this.more = more
        this.maxBar = maxBar
        rebuild()
        arranger.set(bar, more, maxBar)
    }

    fun open() = setEditing(false)

    private fun setEditing(on: Boolean) {
        editing = on
        arranger.visibility = if (on) VISIBLE else GONE
        scroll.visibility = if (on) GONE else VISIBLE
        editBtn.visibility = if (on) GONE else VISIBLE
        doneBtn.visibility = if (on) VISIBLE else GONE
        if (on) arranger.set(bar, more, maxBar) else rebuild()
        refreshTexts()
    }

    private fun refreshTexts() {
        title.text = if (editing) L.t("Personalizar", "Customize") else L.t("Más opciones", "More options")
        doneBtn.text = L.t("Listo", "Done")
    }

    private fun rebuild() {
        grid.removeAllViews()
        buttons.clear()
        if (more.isEmpty()) {
            val t = TextView(context)
            t.text = L.t("Todas las opciones están en la barra de arriba. Toca ✎ para acomodarlas.",
                "All options are in the top bar. Tap ✎ to arrange them.")
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setPadding(d(16f), d(24f), d(16f), d(24f))
            t.setTextColor(palette.hint)
            grid.addView(t)
            return
        }
        var row: LinearLayout? = null
        for ((n, tool) in more.withIndex()) {
            val info = Tools.info(tool) ?: continue
            if (n % 2 == 0) {
                val r = LinearLayout(context)
                r.orientation = LinearLayout.HORIZONTAL
                grid.addView(r, LinearLayout.LayoutParams(-1, d(54f)).apply { topMargin = d(8f) })
                row = r
            }
            row?.addView(button(info), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(d(4f), 0, d(4f), 0) })
        }
        if (more.size % 2 == 1) row?.addView(View(context), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(d(4f), 0, d(4f), 0) })
        paintButtons()
    }

    private fun button(info: Tools.Info): LinearLayout {
        val b = LinearLayout(context)
        b.orientation = LinearLayout.HORIZONTAL
        b.gravity = Gravity.CENTER_VERTICAL
        b.setPadding(d(16f), 0, d(12f), 0)
        b.isClickable = true
        val iv = IconView(context, info.icon, 9f)
        iv.isClickable = false
        b.addView(iv, LinearLayout.LayoutParams(d(28f), d(28f)))
        val t = TextView(context)
        t.text = info.label
        t.textSize = 15f
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(d(12f), 0, 0, 0)
        b.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        b.setOnClickListener { actions?.onMoreFeedback(); actions?.onMoreTool(info.tool) }
        buttons.add(b to (iv to t))
        return b
    }

    private fun paintButtons() {
        val p = palette
        for ((b, pair) in buttons) {
            b.background = PanelUi.pressable(p.key, p.keyPressed, d(27f).toFloat())
            pair.first.color = p.text
            pair.first.bgColor = p.key
            pair.second.setTextColor(p.text)
        }
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        back.color = p.text
        back.background = PanelUi.pressable(p.special, p.keyPressed, d(20f).toFloat())
        editBtn.color = p.text
        editBtn.background = PanelUi.pressable(p.special, p.keyPressed, d(19f).toFloat())
        doneBtn.setTextColor(p.onAccent)
        doneBtn.background = PanelUi.pressable(p.accent, p.accentPressed, d(19f).toFloat())
        arranger.palette = p
        paintButtons()
    }
}

/**
 * Pantalla de "Personalizar": arriba la barra del teclado y abajo "Más opciones".
 * Arrastra cualquier herramienta para cambiarla de lugar (o tócala para pasarla al otro lado).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ToolArranger(context: Context) : View(context) {
    var onChanged: ((List<Int>, List<Int>) -> Unit)? = null
    var onFeedback: (() -> Unit)? = null
    var palette = Palette.DARK
        set(v) { field = v; invalidate() }

    private val dd = resources.displayMetrics.density
    private var bar = ArrayList<Int>()
    private var more = ArrayList<Int>()
    private var maxBar = 6

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tmp = RectF()

    // Zonas calculadas al dibujar
    private val barArea = RectF()
    private val moreArea = RectF()
    private val barRects = ArrayList<RectF>()
    private val moreRects = ArrayList<RectF>()
    private var cols = 4

    // Arrastre
    private var dragTool = -1
    private var dragFromBar = false
    private var downX = 0f
    private var downY = 0f
    private var curX = 0f
    private var curY = 0f
    private var moved = false

    fun set(b: List<Int>, m: List<Int>, max: Int) {
        bar = ArrayList(b)
        more = ArrayList(m)
        maxBar = max
        dragTool = -1
        invalidate()
    }

    private fun layoutAreas() {
        val w = width.toFloat()
        val h = height.toFloat()
        val label = 20f * dd
        val barH = 50f * dd
        barArea.set(0f, label, w, label + barH)
        barRects.clear()
        val slotW = w / maxBar
        for (i in 0 until maxBar) barRects.add(RectF(i * slotW, barArea.top, (i + 1) * slotW, barArea.bottom))
        val top = barArea.bottom + 8f * dd + label
        moreArea.set(0f, top, w, h)
        cols = if (w / dd > 360f) 4 else 3
        val n = maxOf(1, Tools.ALL.size)
        val rows = (n + cols - 1) / cols
        val tileH = minOf(58f * dd, (h - top - 4f * dd) / rows)
        val tileW = w / cols
        moreRects.clear()
        for (i in 0 until n) {
            val c = i % cols
            val r = i / cols
            moreRects.add(RectF(c * tileW, top + r * tileH, (c + 1) * tileW, top + (r + 1) * tileH))
        }
    }

    override fun onDraw(c: Canvas) {
        val p = palette
        layoutAreas()
        text.color = p.hint
        text.textAlign = Paint.Align.LEFT
        text.textSize = 12f * dd
        c.drawText(L.t("Barra de arriba — arrastra para acomodar", "Top bar — drag to arrange"), 4f * dd, barArea.top - 6f * dd, text)
        c.drawText(L.t("Más opciones", "More options"), 4f * dd, moreArea.top - 6f * dd, text)
        text.textAlign = Paint.Align.CENTER

        // Fondo de la barra
        fill.color = p.key
        tmp.set(barArea.left + 2f * dd, barArea.top, barArea.right - 2f * dd, barArea.bottom)
        c.drawRoundRect(tmp, 14f * dd, 14f * dd, fill)

        val target = dropTarget()
        val HOLE = -2
        val showBar = bar.filter { it != dragTool || !moved }.toMutableList()
        val showMore = more.filter { it != dragTool || !moved }.toMutableList()
        if (target != null) {
            if (target.first) showBar.add(target.second.coerceIn(0, showBar.size), HOLE)
            else showMore.add(target.second.coerceIn(0, showMore.size), HOLE)
        }

        stroke.color = p.hint
        stroke.strokeWidth = 1.2f * dd
        for (i in 0 until maxBar) {
            val r = barRects[i]
            when (val t = showBar.getOrNull(i)) {
                null -> c.drawCircle(r.centerX(), r.centerY(), 4f * dd, stroke)
                HOLE -> drawHole(c, r, p)
                else -> drawIcon(c, t, r.centerX(), r.centerY(), p.text, p.key)
            }
        }

        for ((i, t) in showMore.withIndex()) {
            val r = moreRects.getOrNull(i) ?: break
            if (t == HOLE) drawHole(c, r, p) else drawTile(c, t, r, p, false)
        }
        if (showMore.isEmpty()) {
            text.color = p.hint
            text.textSize = 13f * dd
            c.drawText(L.t("Arrastra aquí lo que no quieras en la barra", "Drag here what you don't want in the bar"),
                width / 2f, moreArea.top + 30f * dd, text)
        }

        // Lo que vas arrastrando, debajo del dedo
        if (dragTool >= 0 && moved) {
            val w = (moreRects.firstOrNull()?.width() ?: 90f * dd)
            val h = (moreRects.firstOrNull()?.height() ?: 56f * dd)
            tmp.set(curX - w / 2, curY - h / 2, curX + w / 2, curY + h / 2)
            drawTile(c, dragTool, RectF(tmp), p, true)
        }
    }

    private fun drawHole(c: Canvas, r: RectF?, p: Palette) {
        r ?: return
        fill.color = p.accent and 0x55FFFFFF
        tmp.set(r.left + 4f * dd, r.top + 4f * dd, r.right - 4f * dd, r.bottom - 4f * dd)
        c.drawRoundRect(tmp, 14f * dd, 14f * dd, fill)
    }

    private fun drawIcon(c: Canvas, tool: Int, cx: Float, cy: Float, color: Int, bg: Int) {
        val info = Tools.info(tool) ?: return
        Icons.draw(c, info.icon, cx, cy, 10f * dd, color, bg, dd)
    }

    private fun drawTile(c: Canvas, tool: Int, r: RectF, p: Palette, lifted: Boolean) {
        val info = Tools.info(tool) ?: return
        fill.color = if (lifted) p.keyPressed else p.key
        tmp.set(r.left + 4f * dd, r.top + 4f * dd, r.right - 4f * dd, r.bottom - 4f * dd)
        c.drawRoundRect(tmp, 14f * dd, 14f * dd, fill)
        if (lifted) {
            stroke.color = p.accent
            stroke.strokeWidth = 2f * dd
            c.drawRoundRect(tmp, 14f * dd, 14f * dd, stroke)
        }
        val iconY = tmp.top + tmp.height() * 0.38f
        Icons.draw(c, info.icon, tmp.centerX(), iconY, minOf(9f * dd, tmp.height() * 0.17f), p.text, fill.color, dd)
        text.color = p.text
        text.textSize = 11f * dd
        val label = TextUtils.ellipsize(info.label, android.text.TextPaint(text), tmp.width() - 6f * dd, TextUtils.TruncateAt.END).toString()
        c.drawText(label, tmp.centerX(), tmp.bottom - tmp.height() * 0.16f, text)
    }

    /** ¿Dónde caería lo que arrastras? (true = barra, índice) o null si fuera. */
    private fun dropTarget(): Pair<Boolean, Int>? {
        if (dragTool < 0 || !moved) return null
        if (curY < (barArea.bottom + moreArea.top) / 2) {
            val count = bar.count { it != dragTool }
            val i = (curX / (width.toFloat() / maxBar)).toInt().coerceIn(0, minOf(count, maxBar - 1))
            return true to i
        }
        val count = more.count { it != dragTool }
        var best = count
        for ((i, r) in moreRects.withIndex()) {
            if (i > count) break
            if (r.contains(curX, curY)) { best = i; break }
        }
        return false to best
    }

    private fun hit(x: Float, y: Float): Pair<Int, Boolean>? {
        if (barArea.contains(x, y)) {
            val i = (x / (width.toFloat() / maxBar)).toInt()
            return bar.getOrNull(i)?.let { it to true }
        }
        for ((i, r) in moreRects.withIndex()) if (r.contains(x, y)) return more.getOrNull(i)?.let { it to false }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                layoutAreas()
                val h = hit(e.x, e.y) ?: return true
                dragTool = h.first
                dragFromBar = h.second
                downX = e.x; downY = e.y; curX = e.x; curY = e.y
                moved = false
                parent?.requestDisallowInterceptTouchEvent(true)
                onFeedback?.invoke()
            }
            MotionEvent.ACTION_MOVE -> if (dragTool >= 0) {
                curX = e.x; curY = e.y
                if (!moved && abs(curX - downX) + abs(curY - downY) > 8f * dd) moved = true
                invalidate()
            }
            MotionEvent.ACTION_UP -> if (dragTool >= 0) {
                curX = e.x; curY = e.y
                if (moved) drop(dropTarget()) else toggle()
                dragTool = -1
                moved = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { dragTool = -1; moved = false; invalidate() }
        }
        return true
    }

    /** Un toque sin arrastrar: lo pasa al otro lado. */
    private fun toggle() {
        val t = dragTool
        if (dragFromBar) {
            bar.remove(t)
            more.add(0, t)
        } else {
            more.remove(t)
            bar.add(t)
            trimBar()
        }
        changed()
    }

    private fun drop(target: Pair<Boolean, Int>?) {
        target ?: return
        val t = dragTool
        bar.remove(t)
        more.remove(t)
        if (target.first) {
            bar.add(target.second.coerceIn(0, bar.size), t)
            trimBar(keep = t)
        } else {
            more.add(target.second.coerceIn(0, more.size), t)
        }
        changed()
    }

    /** Si la barra se llenó de más, la última herramienta pasa a "Más opciones". */
    private fun trimBar(keep: Int = -1) {
        while (bar.size > maxBar) {
            val idx = if (bar.last() == keep) bar.size - 2 else bar.size - 1
            more.add(0, bar.removeAt(idx))
        }
    }

    private fun changed() {
        onFeedback?.invoke()
        onChanged?.invoke(ArrayList(bar), ArrayList(more))
    }
}
