package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/** Todos los botones de la barra de arriba. Se pueden acomodar entre la barra y "Más opciones". */
object Tools {
    class Info(val tool: Int, val icon: Int, val es: String, val en: String, val opens: Boolean) {
        val label get() = L.t(es, en)
    }

    val ALL = listOf(
        Info(KeyboardView.TOOL_EMOJI, Icons.EMOJI, "Emojis", "Emojis", true),
        Info(KeyboardView.TOOL_CLIP, Icons.CLIP, "Portapapeles", "Clipboard", true),
        Info(KeyboardView.TOOL_TRANSLATE, Icons.TRANSLATE, "Traducir", "Translate", false),
        Info(KeyboardView.TOOL_SCAN, Icons.SCAN, "Escanear texto", "Scan text", true),
        Info(KeyboardView.TOOL_SPELL, Icons.SPELL, "Ortografía", "Spell check", false),
        Info(KeyboardView.TOOL_EDIT, Icons.EDIT, "Edición de texto", "Text editing", true),
        Info(KeyboardView.TOOL_MORE, Icons.MORE, "Más opciones", "More options", true),
        Info(KeyboardView.TOOL_VOICE, Icons.MIC, "Micrófono", "Microphone", true),
        Info(KeyboardView.TOOL_HIDE, Icons.HIDE, "Bajar el teclado", "Hide keyboard", false),
        Info(KeyboardView.TOOL_RESIZE, Icons.RESIZE, "Cambiar tamaño", "Resize", false),
        Info(KeyboardView.TOOL_LANG, Icons.GLOBE, "Siguiente idioma", "Next language", false),
        Info(KeyboardView.TOOL_THEME, Icons.THEME, "Tema claro / oscuro", "Light / dark theme", false),
        Info(KeyboardView.TOOL_SETTINGS, Icons.SETTINGS, "Ajustes", "Settings", true),
    )

    /** El ⋯ siempre se queda en la barra (si no, no podrías abrir "Más opciones"). */
    const val FIXED = KeyboardView.TOOL_MORE

    private val DEFAULT_BAR = listOf(
        KeyboardView.TOOL_EMOJI, KeyboardView.TOOL_CLIP, KeyboardView.TOOL_TRANSLATE,
        KeyboardView.TOOL_SCAN, KeyboardView.TOOL_SPELL, KeyboardView.TOOL_EDIT,
        KeyboardView.TOOL_MORE, KeyboardView.TOOL_VOICE, KeyboardView.TOOL_HIDE,
    )

    fun info(tool: Int): Info? = ALL.firstOrNull { it.tool == tool }

    /** Cuántos botones caben en la barra de arriba. */
    fun maxBar(ctx: Context): Int {
        val dm = ctx.resources.displayMetrics
        val wDp = minOf(dm.widthPixels, dm.heightPixels) / dm.density
        return (wDp / 44f).toInt().coerceIn(5, 10)
    }

    private fun parse(s: String?): List<Int> =
        s?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { info(it) != null }?.distinct() ?: emptyList()

    /** Si sobran botones en la barra, los últimos pasan a "Más opciones" (nunca el ⋯ ni [keep]). */
    fun trim(bar: MutableList<Int>, more: MutableList<Int>, max: Int, keep: Int = -1) {
        while (bar.size > max) {
            val idx = bar.indexOfLast { it != FIXED && it != keep }
            if (idx < 0) break
            more.add(0, bar.removeAt(idx))
        }
    }

    /** (barra de arriba, más opciones), en el orden que elegiste. */
    fun load(ctx: Context, prefs: SharedPreferences): Pair<List<Int>, List<Int>> {
        val max = maxBar(ctx)
        val saved = prefs.getString(Prefs.BAR_TOOLS2, null)
        val bar: MutableList<Int>
        val more: MutableList<Int>
        if (saved != null) {
            bar = parse(saved).toMutableList()
            more = parse(prefs.getString(Prefs.MORE_TOOLS2, null)).toMutableList()
        } else {
            // Versión anterior: el micrófono, esconder y ⋯ estaban fijos a la derecha
            val old = prefs.getString(Prefs.BAR_TOOLS, null)
            bar = (if (old == null) DEFAULT_BAR else parse(old).filter { it != FIXED } +
                listOf(KeyboardView.TOOL_MORE, KeyboardView.TOOL_VOICE, KeyboardView.TOOL_HIDE)).toMutableList()
            more = parse(prefs.getString(Prefs.MORE_TOOLS, null)).toMutableList()
        }
        more.removeAll { it in bar }
        if (FIXED !in bar) { more.remove(FIXED); bar.add(FIXED) }
        for (t in ALL) if (t.tool !in bar && t.tool !in more) more.add(t.tool)
        trim(bar, more, max)
        return bar to more
    }

    fun save(prefs: SharedPreferences, bar: List<Int>, more: List<Int>) {
        prefs.edit()
            .putString(Prefs.BAR_TOOLS2, bar.joinToString(","))
            .putString(Prefs.MORE_TOOLS2, more.joinToString(","))
            .apply()
    }
}

/**
 * Panel "Más opciones" (como el de Gboard): botones grandes con lo que no está en la barra de arriba.
 * Con el lápiz ✎ puedes acomodar todos los botones arrastrándolos.
 */
@SuppressLint("ViewConstructor")
class MorePanel(context: Context) : FrameLayout(context) {

    interface Actions {
        /** [tool] es una de las constantes KeyboardView.TOOL_... */
        fun onMoreTool(tool: Int)
        fun onMoreBack()
        fun onMoreFeedback()
        /** Acomodaste los botones: hay que guardarlos y actualizar la barra. */
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
    private class Btn(val box: LinearLayout, val icon: IconView, val text: TextView, val chevron: IconView?)
    private val buttons = ArrayList<Btn>()

    private var bar: List<Int> = emptyList()
    private var more: List<Int> = emptyList()
    private var maxBar = 9
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
        grid.setPadding(0, 0, 0, d(6f))
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

    /** Pone qué botones van en la barra y cuáles aquí. */
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
            t.text = L.t("Todos los botones están en la barra de arriba. Toca ✎ para acomodarlos.",
                "All buttons are in the top bar. Tap ✎ to arrange them.")
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
                grid.addView(r, LinearLayout.LayoutParams(-1, d(58f)).apply { topMargin = d(10f) })
                row = r
            }
            row?.addView(button(info), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(d(5f), 0, d(5f), 0) })
        }
        if (more.size % 2 == 1) row?.addView(View(context), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(d(5f), 0, d(5f), 0) })
        paintButtons()
    }

    /** Botón grande como en Gboard: ícono, nombre (se desliza si es largo) y › si abre algo. */
    private fun button(info: Tools.Info): LinearLayout {
        val b = LinearLayout(context)
        b.orientation = LinearLayout.HORIZONTAL
        b.gravity = Gravity.CENTER_VERTICAL
        b.setPadding(d(18f), 0, d(12f), 0)
        b.isClickable = true
        val iv = IconView(context, info.icon, 10.5f)
        iv.isClickable = false
        b.addView(iv, LinearLayout.LayoutParams(d(30f), d(30f)))
        val t = TextView(context)
        t.text = info.label
        t.textSize = 16f
        t.isSingleLine = true
        t.ellipsize = TextUtils.TruncateAt.MARQUEE
        t.marqueeRepeatLimit = -1
        t.isHorizontalFadingEdgeEnabled = true
        t.setPadding(d(14f), 0, d(4f), 0)
        b.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        var chev: IconView? = null
        if (info.opens) {
            chev = IconView(context, Icons.CHEVRON, 7f)
            chev.isClickable = false
            b.addView(chev, LinearLayout.LayoutParams(d(22f), d(22f)))
        }
        b.setOnClickListener { actions?.onMoreFeedback(); actions?.onMoreTool(info.tool) }
        // "Seleccionado" hace que el texto largo se deslice solo
        t.post { t.isSelected = true }
        buttons.add(Btn(b, iv, t, chev))
        return b
    }

    private fun paintButtons() {
        val p = palette
        for (btn in buttons) {
            btn.box.background = PanelUi.pressable(p.key, p.keyPressed, d(29f).toFloat())
            btn.icon.color = p.text
            btn.icon.bgColor = p.key
            btn.text.setTextColor(p.text)
            btn.chevron?.color = p.text
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
 * Pantalla de "Personalizar": arriba la barra del teclado (fija) y abajo "Más opciones" (con scroll).
 * Mantén presionado un botón y arrástralo; un toque lo pasa al otro lado; desliza para ver más.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class ToolArranger(context: Context) : View(context) {
    var onChanged: ((List<Int>, List<Int>) -> Unit)? = null
    var onFeedback: (() -> Unit)? = null
    var palette = Palette.DARK
        set(v) { field = v; invalidate() }

    private val dd = resources.displayMetrics.density
    private val h = Handler(Looper.getMainLooper())
    private var bar = ArrayList<Int>()
    private var more = ArrayList<Int>()
    private var maxBar = 9

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()

    // Zonas
    private val barArea = RectF()
    private var gridTop = 0f
    private val pillH get() = 52f * dd
    private val gapY get() = 10f * dd
    private var scrollY2 = 0f

    // Toque / arrastre
    private var pending = -1
    private var pendingFromBar = false
    private var dragging = false
    private var scrolling = false
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var curX = 0f
    private var curY = 0f
    private var anim = false
    private val startDrag = Runnable {
        if (pending >= 0 && !scrolling) {
            dragging = true
            onFeedback?.invoke()
            invalidate()
        }
    }

    fun set(b: List<Int>, m: List<Int>, max: Int) {
        bar = ArrayList(b)
        more = ArrayList(m)
        maxBar = max
        pending = -1
        dragging = false
        scrollY2 = 0f
        invalidate()
    }

    private fun layoutAreas() {
        val label = 22f * dd
        barArea.set(0f, label, width.toFloat(), label + 52f * dd)
        gridTop = barArea.bottom + 30f * dd
    }

    private fun slotW() = width.toFloat() / maxBar
    private fun pillRect(i: Int, out: RectF) {
        val w = width / 2f
        val c = i % 2
        val r = i / 2
        val top = gridTop + r * (pillH + gapY) - scrollY2
        out.set(c * w + 4f * dd, top, (c + 1) * w - 4f * dd, top + pillH)
    }

    private fun maxScroll(n: Int): Float {
        val rows = (n + 1) / 2
        val content = rows * (pillH + gapY)
        return maxOf(0f, content - (height - gridTop))
    }

    override fun onDraw(c: Canvas) {
        val p = palette
        layoutAreas()
        anim = false
        val target = dropTarget()
        val HOLE = -2
        val showBar = bar.filter { it != pending || !dragging }.toMutableList()
        val showMore = more.filter { it != pending || !dragging }.toMutableList()
        if (target != null) {
            if (target.first) showBar.add(target.second.coerceIn(0, showBar.size), HOLE)
            else showMore.add(target.second.coerceIn(0, showMore.size), HOLE)
        }

        // "Más opciones" (con scroll, debajo de la barra)
        c.save()
        c.clipRect(0f, gridTop - 4f * dd, width.toFloat(), height.toFloat())
        for ((i, t) in showMore.withIndex()) {
            pillRect(i, tmp)
            if (tmp.bottom < gridTop - pillH || tmp.top > height) continue
            if (t == HOLE) drawHole(c, RectF(tmp), p) else drawPill(c, t, RectF(tmp), p, false)
        }
        if (showMore.isEmpty()) {
            text.color = p.hint
            text.textSize = 13f * dd
            text.textAlign = Paint.Align.CENTER
            c.drawText(L.t("Arrastra aquí lo que no quieras en la barra", "Drag here what you don't want in the bar"),
                width / 2f, gridTop + 30f * dd, text)
        }
        c.restore()

        // Títulos
        fill.color = p.bg
        c.drawRect(0f, 0f, width.toFloat(), gridTop - 4f * dd, fill)
        text.color = p.hint
        text.textAlign = Paint.Align.LEFT
        text.textSize = 12f * dd
        c.drawText(L.t("Barra de arriba — mantén presionado y arrastra", "Top bar — press, hold and drag"), 6f * dd, barArea.top - 7f * dd, text)
        c.drawText(L.t("Más opciones (un toque lo pasa a la barra)", "More options (tap to move to the bar)"), 6f * dd, gridTop - 10f * dd, text)

        // La barra de arriba
        fill.color = p.key
        tmp.set(barArea.left + 2f * dd, barArea.top, barArea.right - 2f * dd, barArea.bottom)
        c.drawRoundRect(tmp, 16f * dd, 16f * dd, fill)
        stroke.color = p.hint
        stroke.strokeWidth = 1.2f * dd
        val sw = slotW()
        for (i in 0 until maxBar) {
            val cx = sw * (i + 0.5f)
            val cy = barArea.centerY()
            when (val t = showBar.getOrNull(i)) {
                null -> c.drawCircle(cx, cy, 3.5f * dd, stroke)
                HOLE -> {
                    fill.color = p.accent and 0x55FFFFFF
                    tmp.set(cx - sw / 2 + 3f * dd, barArea.top + 5f * dd, cx + sw / 2 - 3f * dd, barArea.bottom - 5f * dd)
                    c.drawRoundRect(tmp, 12f * dd, 12f * dd, fill)
                }
                else -> {
                    if (t == pending && !dragging) {
                        fill.color = p.keyPressed
                        c.drawCircle(cx, cy, 19f * dd, fill)
                    }
                    Tools.info(t)?.let { Icons.draw(c, it.icon, cx, cy, minOf(10f * dd, sw * 0.24f), p.text, p.key, dd) }
                }
            }
        }

        // Lo que vas arrastrando, debajo del dedo
        if (dragging && pending >= 0) {
            val w = width / 2f - 8f * dd
            tmp.set(curX - w / 2, curY - pillH / 2, curX + w / 2, curY + pillH / 2)
            drawPill(c, pending, RectF(tmp), p, true)
        }
        if (anim) postInvalidateDelayed(33)
    }

    private fun drawHole(c: Canvas, r: RectF, p: Palette) {
        fill.color = p.accent and 0x55FFFFFF
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
    }

    private fun drawPill(c: Canvas, tool: Int, r: RectF, p: Palette, lifted: Boolean) {
        val info = Tools.info(tool) ?: return
        val fixed = tool == Tools.FIXED
        fill.color = if (lifted || (tool == pending && !dragging)) p.keyPressed else p.key
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, fill)
        if (lifted) {
            stroke.color = p.accent
            stroke.strokeWidth = 2f * dd
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, stroke)
        }
        val ix = r.left + 30f * dd
        Icons.draw(c, info.icon, ix, r.centerY(), 10f * dd, if (fixed) p.hint else p.text, fill.color, dd)
        // Nombre: si no cabe, se va deslizando para leerlo completo
        text.color = p.text
        text.textAlign = Paint.Align.LEFT
        text.textSize = 15f * dd
        val tx = ix + 22f * dd
        val avail = r.right - 16f * dd - tx
        val label = info.label
        val tw = text.measureText(label)
        val by = r.centerY() - (text.ascent() + text.descent()) / 2
        if (tw <= avail) {
            c.drawText(label, tx, by, text)
        } else {
            val gap = 40f * dd
            val period = tw + gap
            val off = ((android.os.SystemClock.uptimeMillis() / 1000f * 30f * dd) % period)
            c.save()
            c.clipRect(tx, r.top, tx + avail, r.bottom)
            c.drawText(label, tx - off, by, text)
            c.drawText(label, tx - off + period, by, text)
            c.restore()
            anim = true
        }
    }

    /** ¿Dónde caería lo que arrastras? (true = barra, índice) o null. */
    private fun dropTarget(): Pair<Boolean, Int>? {
        if (!dragging || pending < 0) return null
        if (curY < (barArea.bottom + gridTop) / 2) {
            val count = bar.count { it != pending }
            val i = (curX / slotW()).toInt().coerceIn(0, minOf(count, maxBar - 1))
            return true to i
        }
        if (pending == Tools.FIXED) return null
        val count = more.count { it != pending }
        val row = ((curY - gridTop + scrollY2) / (pillH + gapY)).toInt().coerceAtLeast(0)
        val col = if (curX < width / 2f) 0 else 1
        return false to (row * 2 + col).coerceIn(0, count)
    }

    private fun hit(x: Float, y: Float): Pair<Int, Boolean>? {
        if (barArea.contains(x, y)) {
            val i = (x / slotW()).toInt()
            return bar.getOrNull(i)?.let { it to true }
        }
        if (y < gridTop) return null
        for (i in more.indices) {
            pillRect(i, tmp)
            if (tmp.contains(x, y)) return more[i] to false
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                layoutAreas()
                downX = e.x; downY = e.y; lastY = e.y; curX = e.x; curY = e.y
                dragging = false
                scrolling = false
                val hh = hit(e.x, e.y)
                pending = hh?.first ?: -1
                pendingFromBar = hh?.second ?: false
                if (pending >= 0) h.postDelayed(startDrag, 260L)
                parent?.requestDisallowInterceptTouchEvent(true)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                curX = e.x; curY = e.y
                if (dragging) {
                    // Si llevas el botón al borde de abajo, la lista se mueve sola
                    if (curY > height - 24f * dd) scrollBy2(6f * dd)
                    else if (curY < gridTop + 10f * dd && curY > barArea.bottom + 10f * dd) scrollBy2(-6f * dd)
                    invalidate()
                } else if (!scrolling && abs(curY - downY) + abs(curX - downX) > 10f * dd) {
                    h.removeCallbacks(startDrag)
                    scrolling = true
                    pending = -1
                    invalidate()
                }
                if (scrolling) {
                    scrollBy2(lastY - e.y)
                }
                lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                h.removeCallbacks(startDrag)
                curX = e.x; curY = e.y
                if (dragging) drop(dropTarget())
                else if (!scrolling && pending >= 0) toggle()
                pending = -1
                dragging = false
                scrolling = false
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                h.removeCallbacks(startDrag)
                pending = -1; dragging = false; scrolling = false; invalidate()
            }
        }
        return true
    }

    private fun scrollBy2(dy: Float) {
        val n = more.size + (if (dragging) 1 else 0)
        val ns = (scrollY2 + dy).coerceIn(0f, maxScroll(n))
        if (ns != scrollY2) { scrollY2 = ns; invalidate() }
    }

    /** Un toque sin arrastrar: lo pasa al otro lado. */
    private fun toggle() {
        val t = pending
        if (pendingFromBar) {
            if (t == Tools.FIXED) return
            bar.remove(t)
            more.add(0, t)
        } else {
            more.remove(t)
            bar.add(t)
            Tools.trim(bar, more, maxBar, keep = t)
        }
        changed()
    }

    private fun drop(target: Pair<Boolean, Int>?) {
        target ?: return
        val t = pending
        bar.remove(t)
        more.remove(t)
        if (target.first) {
            bar.add(target.second.coerceIn(0, bar.size), t)
            Tools.trim(bar, more, maxBar, keep = t)
        } else {
            more.add(target.second.coerceIn(0, more.size), t)
        }
        changed()
    }

    private fun changed() {
        onFeedback?.invoke()
        onChanged?.invoke(ArrayList(bar), ArrayList(more))
    }
}
