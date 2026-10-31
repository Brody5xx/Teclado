package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Panel de emojis: una sola lista con scroll (Recientes + 9 categorías).
 * Las pestañas de abajo saltan a cada categoría y se iluminan solas al deslizar.
 * Tiene exactamente la altura del teclado.
 */
@SuppressLint("ViewConstructor")
class EmojiPanel(context: Context) : FrameLayout(context) {

    var listener: PanelListener? = null
    private var palette = Palette.DARK
    private var items: List<String> = emptyList()
    /** Dónde empieza cada pestaña dentro de la lista (0 = recientes). */
    private val sectionStart = IntArray(EmojiData.TAB_ICONS.size)
    private var currentTab = -1

    private val body = LinearLayout(context)
    private val grid = GridView(context)
    private val tabViews = ArrayList<IconView>()
    private val abc: TextView
    private val del: IconView
    private val overlay = PanelOverlay(context, 0x33000000)
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = AbsListView.LayoutParams(-1, d(44f))
            }
            tv.setTextColor(palette.text)
            tv.text = items[position]
            return tv
        }
    }

    init {
        body.orientation = LinearLayout.VERTICAL

        grid.columnWidth = d(42f)
        grid.numColumns = GridView.AUTO_FIT
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.selector = ColorDrawable(0x33888888)
        grid.isVerticalScrollBarEnabled = true
        grid.isFastScrollEnabled = true
        grid.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        grid.setPadding(d(2f), d(4f), d(2f), d(4f))
        grid.clipToPadding = false
        grid.setOnItemClickListener { _, _, pos, _ -> pick(items[pos]) }
        grid.setOnItemLongClickListener { _, v, pos, _ -> showTones(v, items[pos]) }
        grid.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}
            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {
                highlight(tabFor(first))
            }
        })
        body.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(d(4f), d(3f), d(4f), d(5f))
        abc = PanelUi.barButton(context, "ABC", palette)
        abc.setOnClickListener { listener?.onPanelFeedback(); listener?.onPanelBack() }
        bar.addView(abc, LinearLayout.LayoutParams(d(54f), d(40f)))

        val tabsRow = LinearLayout(context)
        tabsRow.orientation = LinearLayout.HORIZONTAL
        for (i in EmojiData.TAB_ICONS.indices) {
            val t = IconView(context, Icons.EMOJI_TABS.getOrElse(i) { Icons.EMOJI }, 8.5f)
            t.setOnClickListener { listener?.onPanelFeedback(); jumpTo(i) }
            tabViews.add(t)
            tabsRow.addView(t, LinearLayout.LayoutParams(0, d(40f), 1f))
        }
        bar.addView(tabsRow, LinearLayout.LayoutParams(0, d(40f), 1f))

        del = IconView(context, Icons.DELETE, 9.5f)
        PanelUi.makeRepeatingDelete(del) { listener }
        bar.addView(del, LinearLayout.LayoutParams(d(54f), d(40f)))
        body.addView(bar, LinearLayout.LayoutParams(-1, -2))

        addView(body, LayoutParams(-1, -1))
        addView(overlay, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    /** Se llama cada vez que se abre el panel. */
    fun open() {
        overlay.hide()
        EmojiData.load(context) {
            build()
            // Abre en Recientes; si todavía no tienes, empieza en las caritas
            jumpTo(if (EmojiData.recent.isEmpty()) 1 else 0)
        }
    }

    fun close() = overlay.hide()

    private fun build() {
        val cats = EmojiData.categories ?: return
        val list = ArrayList<String>(2000)
        sectionStart[0] = 0
        list.addAll(EmojiData.recent)
        for (c in cats.indices) {
            sectionStart[c + 1] = list.size
            for (e in cats[c]) list.add(e.text)
        }
        items = list
        adapter.notifyDataSetChanged()
    }

    private fun tabFor(position: Int): Int {
        var t = 0
        for (i in sectionStart.indices) if (position >= sectionStart[i]) t = i
        // Sin recientes, la sección 0 está vacía: muestra caritas
        if (t == 0 && EmojiData.recent.isEmpty()) t = 1
        return t
    }

    private fun jumpTo(tab: Int) {
        overlay.hide()
        grid.setSelection(sectionStart.getOrElse(tab) { 0 })
        highlight(tab)
    }

    private fun highlight(tab: Int) {
        if (tab == currentTab) return
        currentTab = tab
        val r = d(7f).toFloat()
        for ((n, t) in tabViews.withIndex()) {
            t.background = if (n == tab) PanelUi.rounded(palette.keyPressed, r) else null
            t.color = if (n == tab) palette.text else palette.hint
            t.bgColor = if (n == tab) palette.keyPressed else palette.bg
        }
    }

    private fun pick(e: String) {
        listener?.onPanelFeedback()
        listener?.onPanelText(e)
        EmojiData.addRecent(e)
    }

    /** Mantener presionado un emoji de personas o manos muestra los 5 tonos de piel. */
    private fun showTones(anchor: View, text: String): Boolean {
        val e = EmojiData.find(text) ?: return false
        if (e.tones.isEmpty()) return false
        listener?.onPanelFeedback()
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(d(4f), d(4f), d(4f), d(4f))
        row.background = PanelUi.rounded(palette.popup, d(10f).toFloat())
        row.elevation = d(6f).toFloat()
        val cell = d(44f)
        for (t in listOf(e.text) + e.tones) {
            val tv = TextView(context)
            tv.text = t
            tv.textSize = 26f
            tv.gravity = Gravity.CENTER
            tv.background = PanelUi.pressable(palette.popup, palette.keyPressed, d(7f).toFloat())
            tv.setOnClickListener { overlay.hide(); pick(t) }
            row.addView(tv, LinearLayout.LayoutParams(cell, d(50f)))
        }
        val rowW = cell * (1 + e.tones.size) + d(8f)
        val rowH = d(58f)
        // Posición del emoji dentro del panel
        val a = IntArray(2)
        val me = IntArray(2)
        anchor.getLocationInWindow(a)
        getLocationInWindow(me)
        val ax = a[0] - me[0]
        val ay = a[1] - me[1]
        val lp = LayoutParams(-2, -2, Gravity.TOP or Gravity.START)
        lp.leftMargin = (ax + anchor.width / 2 - rowW / 2).coerceIn(0, maxOf(0, width - rowW))
        lp.topMargin = if (ay - rowH >= 0) ay - rowH else (ay + anchor.height).coerceAtMost(maxOf(0, height - rowH))
        overlay.show(row, lp)
        return true
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        val r = d(7f).toFloat()
        abc.setTextColor(p.text)
        abc.background = PanelUi.pressable(p.special, p.keyPressed, r)
        del.color = p.text
        del.bgColor = p.special
        del.background = PanelUi.pressable(p.special, p.keyPressed, r)
        for (t in tabViews) { t.color = p.hint; t.bgColor = p.bg; t.invalidate() }
        val cur = currentTab
        currentTab = -1
        highlight(cur)
        adapter.notifyDataSetChanged()
    }
}
