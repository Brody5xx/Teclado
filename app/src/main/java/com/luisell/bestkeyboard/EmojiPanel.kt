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

    interface Actions {
        fun onEmojiSearch()
        fun onEmojiGif()
    }
    var actions: Actions? = null
    private var palette = Palette.DARK
    private var items: List<String> = emptyList()
    /** Dónde empieza cada pestaña dentro de la lista (0 = recientes). */
    private val sectionStart = IntArray(EmojiData.TAB_ICONS.size)
    private var currentTab = -1

    private val body = LinearLayout(context)
    private val grid = GlowGrid(context)
    private val glow = GlowScrollbar(context)
    private var userScrolling = false
    private val tabViews = ArrayList<IconView>()
    private val abc: TextView
    private val del: IconView
    private val overlay = PanelOverlay(context, 0x33000000)
    private val searchPill = LinearLayout(context)
    private val searchIcon = IconView(context, Icons.SEARCH, 6.5f)
    private val searchText = TextView(context)
    private val gifBtn = TextView(context)
    // Buscar sin salir del panel
    private val searchBack = IconView(context, Icons.BACK, 7.5f)
    private val panelBack = IconView(context, Icons.BACK, 7.5f)
    private val searchClear = IconView(context, Icons.CLOSE, 5.5f)
    private val mini = MiniKeys(context)
    private var tabsBar: View? = null
    private var searching = false
    private val query = StringBuilder()
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

        // Arriba: buscar emojis y el botón de GIFs
        val top = LinearLayout(context)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        top.setPadding(d(6f), d(5f), d(6f), d(2f))
        searchBack.setOnClickListener { listener?.onPanelFeedback(); stopSearch() }
        searchBack.visibility = View.GONE
        // Atrás (vuelve a las letras)
        panelBack.setOnClickListener { listener?.onPanelFeedback(); listener?.onPanelBack() }
        top.addView(panelBack, LinearLayout.LayoutParams(d(40f), d(36f)).apply { rightMargin = d(6f) })
        top.addView(searchBack, LinearLayout.LayoutParams(d(44f), d(36f)).apply { rightMargin = d(6f) })
        searchPill.orientation = LinearLayout.HORIZONTAL
        searchPill.gravity = Gravity.CENTER_VERTICAL
        searchPill.setPadding(d(10f), 0, d(10f), 0)
        searchPill.addView(searchIcon, LinearLayout.LayoutParams(d(24f), d(24f)))
        L.bind(searchText, "Buscar emojis", "Search emojis")
        searchText.textSize = 14f
        searchText.setPadding(d(8f), 0, 0, 0)
        searchPill.addView(searchText, LinearLayout.LayoutParams(0, -2, 1f))
        searchClear.setOnClickListener { listener?.onPanelFeedback(); query.setLength(0); refreshSearch() }
        searchClear.visibility = View.GONE
        searchPill.addView(searchClear, LinearLayout.LayoutParams(d(30f), d(30f)))
        searchPill.setOnClickListener {
            listener?.onPanelFeedback()
            if (!searching) startSearch() else mini.visibility = View.VISIBLE
        }
        top.addView(searchPill, LinearLayout.LayoutParams(0, d(36f), 1f))
        gifBtn.text = "GIF"
        gifBtn.textSize = 13f
        gifBtn.gravity = Gravity.CENTER
        gifBtn.setTypeface(gifBtn.typeface, android.graphics.Typeface.BOLD)
        gifBtn.setOnClickListener { listener?.onPanelFeedback(); actions?.onEmojiGif() }
        top.addView(gifBtn, LinearLayout.LayoutParams(d(58f), d(36f)).apply { leftMargin = d(8f) })
        body.addView(top, LinearLayout.LayoutParams(-1, -2))

        grid.columnWidth = d(42f)
        grid.numColumns = GridView.AUTO_FIT
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.selector = ColorDrawable(0x33888888)
        grid.isFastScrollEnabled = false
        // Sin la barra de Android: usamos la nuestra (destello de luz que no se puede agarrar)
        grid.isVerticalScrollBarEnabled = false
        grid.setPadding(d(2f), d(4f), d(2f), d(4f))
        grid.clipToPadding = false
        grid.setOnItemClickListener { _, _, pos, _ -> pick(items[pos]) }
        grid.setOnItemLongClickListener { _, v, pos, _ -> showTones(v, items[pos]) }
        grid.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                userScrolling = scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE
                if (userScrolling) showGlow()
            }
            override fun onScroll(view: AbsListView?, first: Int, visible: Int, total: Int) {
                if (!searching && jumpLock < 0) highlight(tabFor(first))
                if (userScrolling) showGlow()
            }
        })
        val gridBox = FrameLayout(context)
        gridBox.addView(grid, LayoutParams(-1, -1))
        gridBox.addView(glow, LayoutParams(-1, -1))
        body.addView(gridBox, LinearLayout.LayoutParams(-1, 0, 1f))
        mini.visibility = View.GONE
        mini.actions = object : MiniKeys.Actions {
            override fun onMiniText(s: String) { if (query.length < 30) query.append(s); refreshSearch() }
            override fun onMiniDelete() {
                if (query.isNotEmpty()) query.setLength(query.length - 1)
                refreshSearch()
            }
            override fun onMiniEnter() { mini.visibility = View.GONE }
            override fun onMiniFeedback() { listener?.onPanelFeedback() }
        }
        body.addView(mini, LinearLayout.LayoutParams(-1, d(176f)))

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
        tabsBar = bar

        addView(body, LayoutParams(-1, -1))
        addView(overlay, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    private fun showGlow() {
        val (pos, visible) = grid.scrollInfo()
        glow.show(pos, visible)
    }

    /** Se llama cada vez que se abre el panel. */
    fun open() {
        overlay.hide()
        if (searching) stopSearch(rebuild = false)
        EmojiData.load(context) {
            build()
            // Abre en Recientes; si todavía no tienes, empieza en las caritas
            jumpTo(if (EmojiData.recent.isEmpty()) 1 else 0)
        }
    }

    fun close() = overlay.hide()

    // ---------------------------------------------------------------- Buscar

    private fun startSearch() {
        searching = true
        query.setLength(0)
        overlay.hide()
        searchBack.visibility = View.VISIBLE
        panelBack.visibility = View.GONE
        gifBtn.visibility = View.GONE
        tabsBar?.visibility = View.GONE
        mini.visibility = View.VISIBLE
        refreshSearch()
    }

    private fun stopSearch(rebuild: Boolean = true) {
        searching = false
        query.setLength(0)
        searchBack.visibility = View.GONE
        panelBack.visibility = View.VISIBLE
        searchClear.visibility = View.GONE
        gifBtn.visibility = View.VISIBLE
        tabsBar?.visibility = View.VISIBLE
        mini.visibility = View.GONE
        searchText.text = L.t("Buscar emojis", "Search emojis")
        searchText.setTextColor(palette.hint)
        if (rebuild) {
            build()
            jumpTo(if (EmojiData.recent.isEmpty()) 1 else 0)
        }
    }

    /** Muestra lo escrito y los emojis encontrados (mientras escribes). */
    private fun refreshSearch() {
        val q = query.toString()
        val cursor = android.text.SpannableString("$q|")
        cursor.setSpan(android.text.style.ForegroundColorSpan(palette.accent), q.length, q.length + 1, 0)
        searchText.text = if (q.isEmpty()) L.t("Escribe: casa, feliz, perro…", "Type: house, happy, dog…") else cursor
        searchText.setTextColor(if (q.isEmpty()) palette.hint else palette.text)
        searchClear.visibility = if (q.isEmpty()) View.INVISIBLE else View.VISIBLE
        items = if (q.isBlank()) EmojiData.recent.toList() else EmojiSearch.search(q)
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
    }

    /** 🕐 Recientes es su propia página: solo los que tú usaste (el último que usaste va primero). */
    private var showingRecents = false

    private fun build() {
        val cats = EmojiData.categories ?: return
        if (showingRecents) {
            items = EmojiData.recent.toList()
            adapter.notifyDataSetChanged()
            return
        }
        val list = ArrayList<String>(2000)
        sectionStart[0] = 0
        for (c in cats.indices) {
            sectionStart[c + 1] = list.size
            for (e in cats[c]) list.add(e.text)
        }
        items = list
        adapter.notifyDataSetChanged()
    }

    private fun tabFor(position: Int): Int {
        if (showingRecents) return 0
        var t = 1
        for (i in 1 until sectionStart.size) if (position >= sectionStart[i]) t = i
        return t
    }

    private fun jumpTo(tab: Int) {
        overlay.hide()
        // Recientes: su propia página; las demás categorías van seguidas
        val recents = tab == 0
        if (recents != showingRecents) { showingRecents = recents; build() }
        val pos = if (recents) 0 else sectionStart.getOrElse(tab) { 0 }
        // La sección empieza arriba del todo (antes quedaba al final de la sección y había que tocar la de al lado)
        grid.setSelection(pos)
        grid.post { grid.smoothScrollToPositionFromTop(pos, 0, 0) }
        jumpLock = tab
        grid.postDelayed({ jumpLock = -1 }, 350)
        highlight(tab)
    }

    /** Mientras salta a una sección, no se cambia la pestaña marcada. */
    private var jumpLock = -1

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
        searchPill.background = PanelUi.pressable(p.key, p.keyPressed, d(18f).toFloat())
        searchIcon.color = p.hint
        searchBack.color = p.text
        panelBack.color = p.text
        panelBack.bgColor = p.bg
        panelBack.background = PanelUi.pressable(p.special, p.keyPressed, d(18f).toFloat())
        searchBack.background = PanelUi.pressable(p.special, p.keyPressed, d(18f).toFloat())
        searchClear.color = p.hint
        mini.applyPalette(p)
        searchText.setTextColor(p.hint)
        gifBtn.setTextColor(p.onAccent)
        gifBtn.background = PanelUi.pressable(p.accent, p.accentPressed, d(18f).toFloat())
        val cur = currentTab
        currentTab = -1
        highlight(cur)
        adapter.notifyDataSetChanged()
    }
}
