package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/** Panel de emojis: pestañas de categorías, recientes y tonos de piel. */
@SuppressLint("ViewConstructor")
class EmojiPanel(context: Context) : LinearLayout(context) {

    var listener: PanelListener? = null
    private var palette = Palette.DARK
    private var tab = 1
    private var items: List<String> = emptyList()

    private val grid = GridView(context)
    private val empty = TextView(context)
    private val tabsRow = LinearLayout(context)
    private val tabViews = ArrayList<TextView>()
    private val abc: TextView
    private val del: TextView
    private val bar = LinearLayout(context)
    private var popup: PopupWindow? = null

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                textSize = 27f
                gravity = Gravity.CENTER
                layoutParams = AbsListView.LayoutParams(-1, PanelUi.dp(context, 48f))
                setTextColor(palette.text)
            }
            tv.text = items[position]
            return tv
        }
    }

    init {
        orientation = VERTICAL
        val d = { v: Float -> PanelUi.dp(context, v) }

        grid.columnWidth = d(46f)
        grid.numColumns = GridView.AUTO_FIT
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.selector = ColorDrawable(0x33888888)
        grid.isVerticalScrollBarEnabled = false
        grid.setPadding(d(4f), d(4f), d(4f), d(4f))
        grid.clipToPadding = false
        grid.setOnItemClickListener { _, _, pos, _ -> pick(items[pos]) }
        grid.setOnItemLongClickListener { _, v, pos, _ -> showTones(v, items[pos]) }

        empty.text = "Aquí aparecerán los emojis que más uses."
        empty.gravity = Gravity.CENTER
        empty.textSize = 15f
        empty.visibility = GONE

        addView(grid, LayoutParams(-1, 0, 1f))
        addView(empty, LayoutParams(-1, 0, 1f))

        bar.orientation = HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(d(4f), d(3f), d(4f), d(5f))
        abc = PanelUi.barButton(context, "ABC", palette)
        abc.setOnClickListener { listener?.onPanelFeedback(); listener?.onPanelBack() }
        bar.addView(abc, LayoutParams(d(54f), d(40f)))

        tabsRow.orientation = HORIZONTAL
        for (i in EmojiData.TAB_ICONS.indices) {
            val t = TextView(context)
            t.text = EmojiData.TAB_ICONS[i]
            t.textSize = 18f
            t.gravity = Gravity.CENTER
            t.setOnClickListener { listener?.onPanelFeedback(); selectTab(i) }
            tabViews.add(t)
            tabsRow.addView(t, LayoutParams(0, d(40f), 1f))
        }
        bar.addView(tabsRow, LayoutParams(0, d(40f), 1f))

        del = PanelUi.barButton(context, "⌫", palette)
        PanelUi.makeRepeatingDelete(del) { listener }
        bar.addView(del, LayoutParams(d(54f), d(40f)))
        addView(bar, LayoutParams(-1, -2))
        applyPalette(palette)
    }

    /** Se llama cada vez que se abre el panel. */
    fun open() {
        EmojiData.load(context) {
            // Abre en "Recientes"; si todavía no tienes, empieza en las caritas
            selectTab(if (EmojiData.recent.isEmpty()) 1 else 0)
        }
        if (EmojiData.categories == null) {
            items = emptyList()
            adapter.notifyDataSetChanged()
        }
    }

    fun close() {
        popup?.dismiss()
        popup = null
    }

    private fun selectTab(i: Int) {
        tab = i
        val cats = EmojiData.categories
        items = when {
            i == 0 -> ArrayList(EmojiData.recent)
            cats == null -> emptyList()
            else -> cats[i - 1].map { it.text }
        }
        val showEmpty = i == 0 && items.isEmpty()
        empty.visibility = if (showEmpty) VISIBLE else GONE
        grid.visibility = if (showEmpty) GONE else VISIBLE
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        for ((n, t) in tabViews.withIndex()) {
            t.background = if (n == i) PanelUi.rounded(palette.keyPressed, PanelUi.dp(context, 7f).toFloat()) else null
        }
    }

    private fun pick(e: String) {
        listener?.onPanelFeedback()
        listener?.onPanelText(e)
        EmojiData.addRecent(e)
    }

    /** Mantener presionado un emoji con tonos de piel muestra los 5 tonos. */
    private fun showTones(anchor: View, text: String): Boolean {
        val e = EmojiData.find(text) ?: return false
        if (e.tones.isEmpty()) return false
        listener?.onPanelFeedback()
        popup?.dismiss()
        val d = { v: Float -> PanelUi.dp(context, v) }
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.setPadding(d(4f), d(4f), d(4f), d(4f))
        row.background = PanelUi.rounded(palette.popup, d(10f).toFloat())
        val pw = PopupWindow(row, -2, -2, true)
        for (t in listOf(e.text) + e.tones) {
            val tv = TextView(context)
            tv.text = t
            tv.textSize = 28f
            tv.gravity = Gravity.CENTER
            tv.background = PanelUi.pressable(palette.popup, palette.keyPressed, d(7f).toFloat())
            tv.setOnClickListener { pw.dismiss(); pick(t) }
            row.addView(tv, LayoutParams(d(46f), d(50f)))
        }
        pw.isOutsideTouchable = true
        pw.elevation = d(6f).toFloat()
        row.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val loc = IntArray(2)
        anchor.getLocationInWindow(loc)
        val x = (loc[0] + anchor.width / 2 - row.measuredWidth / 2).coerceIn(0, maxOf(0, width - row.measuredWidth))
        val y = maxOf(0, loc[1] - row.measuredHeight)
        pw.showAtLocation(this, Gravity.NO_GRAVITY, x, y)
        popup = pw
        return true
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        empty.setTextColor(p.hint)
        val r = PanelUi.dp(context, 7f).toFloat()
        abc.setTextColor(p.text)
        abc.background = PanelUi.pressable(p.special, p.keyPressed, r)
        del.setTextColor(p.text)
        del.background = PanelUi.pressable(p.special, p.keyPressed, r)
        for ((n, t) in tabViews.withIndex()) {
            t.background = if (n == tab) PanelUi.rounded(p.keyPressed, r) else null
        }
        for (i in 0 until grid.childCount) (grid.getChildAt(i) as? TextView)?.setTextColor(p.text)
    }
}
