package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Panel del portapapeles infinito: textos e imágenes que copiaste, en 2 columnas. */
@SuppressLint("ViewConstructor")
class ClipPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onPasteText(text: String)
        fun onPasteImage(item: ClipItem)
        /** El lápiz: agregar algo nuevo al portapapeles ([item] = null) o editar uno. */
        fun onClipWrite(item: ClipItem?)
        /** 🔍 Buscar en el portapapeles y los tickets. */
        fun onClipFind() {}
    }

    var listener: PanelListener? = null
    var actions: Actions? = null
    var historyOn = true
        set(v) { field = v; refresh() }

    private var palette = Palette.DARK
    private var items: List<ClipItem> = emptyList()
    private val d = { v: Float -> PanelUi.dp(context, v) }

    private val title = TextView(context)
    private val backBtn = IconView(context, Icons.BACK, 7.5f)
    private val addBtn = IconView(context, Icons.PENCIL, 7f)
    private val findBtn = IconView(context, Icons.SEARCH, 7f)
    // Modo seleccionar (mantener presionado): varios a la vez, como Gboard
    private val selected = LinkedHashSet<Long>()
    private val selBar = LinearLayout(context)
    private val normalBar = LinearLayout(context)
    private val selClose = IconView(context, Icons.CLOSE, 6.5f)
    private val selCount = TextView(context)
    private val selEdit = IconView(context, Icons.PENCIL, 7f)
    private val selPin = IconView(context, Icons.PIN, 7f)
    private val selDel = IconView(context, Icons.TRASH, 7f)
    private val selecting get() = selected.isNotEmpty()
    /** Mientras seleccionas, los demás tiemblan un poquito. */
    private val wiggle = object : Runnable {
        override fun run() {
            if (!selecting || !isShown) { stopWiggle(); return }
            val t = android.os.SystemClock.uptimeMillis() / 1000.0
            for (i in 0 until grid.childCount) {
                val v = grid.getChildAt(i)
                val pos = grid.firstVisiblePosition + i
                val it = items.getOrNull(pos)
                v.rotation = if (it == null || it.id in selected) 0f else (1.4 * kotlin.math.sin(t * 2 * Math.PI * 4.5 + pos * 1.7)).toFloat()
            }
            grid.postOnAnimation(this)
        }
    }
    private val clearBtn = TextView(context)
    private val grid = GridView(context)
    private val empty = TextView(context)
    private val abc: TextView
    private val del: IconView
    private val body = LinearLayout(context)
    private val overlay = PanelOverlay(context, 0x66000000)
    private var confirmClear = false
    private val handler = Handler(Looper.getMainLooper())

    // Miniaturas de las imágenes (se cargan en segundo plano para que todo vaya fluido)
    private val thumbs = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val decoder = Executors.newSingleThreadExecutor()

    private inner class Holder(val box: FrameLayout, val text: TextView, val image: ImageView, val pin: IconView, val check: IconView)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h = (convertView?.tag as? Holder) ?: makeHolder()
            val it = items[position]
            val sel = it.id in selected
            h.box.background = if (sel) android.graphics.drawable.GradientDrawable().apply {
                setColor(palette.keyPressed); cornerRadius = d(9f).toFloat(); setStroke(d(2f), palette.accent)
            } else PanelUi.pressable(palette.key, palette.keyPressed, d(9f).toFloat())
            // Seleccionando: rombo vacío en los que no están marcados, rombo con palomita en los marcados
            h.check.visibility = if (selecting) VISIBLE else GONE
            h.check.kind = if (sel) Icons.SEL_ON else Icons.SEL_OFF
            h.check.color = if (sel) palette.accent else palette.text
            h.check.bgColor = if (sel) palette.onAccent else palette.key
            h.check.background = null
            h.check.invalidate()
            h.text.setTextColor(palette.text)
            h.pin.visibility = if (it.pinned && !selecting) VISIBLE else GONE
            h.pin.color = palette.accent
            h.pin.bgColor = palette.bg
            h.pin.background = PanelUi.rounded(palette.bg, d(10f).toFloat())
            if (it.isImage) {
                h.text.visibility = GONE
                h.image.visibility = VISIBLE
                bindThumb(h.image, it)
            } else {
                h.image.visibility = GONE
                h.image.tag = null
                h.text.visibility = VISIBLE
                h.text.text = it.text?.trim()
            }
            return h.box
        }
    }

    init {
        body.orientation = LinearLayout.VERTICAL

        // Arriba (normal): ← atrás · Portapapeles · Borrar · ✏️ agregar
        val header = FrameLayout(context)
        normalBar.orientation = LinearLayout.HORIZONTAL
        normalBar.gravity = Gravity.CENTER_VERTICAL
        normalBar.setPadding(d(6f), d(4f), d(6f), d(2f))
        backBtn.setOnClickListener { listener?.onPanelFeedback(); listener?.onPanelBack() }
        normalBar.addView(backBtn, LinearLayout.LayoutParams(d(40f), d(36f)))
        title.textSize = 15f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        title.setPadding(d(10f), 0, 0, 0)
        normalBar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        clearBtn.textSize = 14f
        clearBtn.setPadding(d(12f), d(8f), d(12f), d(8f))
        clearBtn.setOnClickListener { onClearTap() }
        normalBar.addView(clearBtn, LinearLayout.LayoutParams(-2, -2))
        addBtn.setOnClickListener { listener?.onPanelFeedback(); actions?.onClipWrite(null) }
        normalBar.addView(addBtn, LinearLayout.LayoutParams(d(40f), d(36f)).apply { leftMargin = d(4f) })
        findBtn.setOnClickListener { listener?.onPanelFeedback(); actions?.onClipFind() }
        normalBar.addView(findBtn, LinearLayout.LayoutParams(d(40f), d(36f)).apply { leftMargin = d(2f) })
        header.addView(normalBar, LayoutParams(-1, -1))
        // Arriba (seleccionando): ✕ · "2 seleccionados" · ✏️ · 📌 · 🗑
        selBar.orientation = LinearLayout.HORIZONTAL
        selBar.gravity = Gravity.CENTER_VERTICAL
        selBar.setPadding(d(6f), d(4f), d(6f), d(2f))
        selClose.setOnClickListener { listener?.onPanelFeedback(); clearSelection() }
        selBar.addView(selClose, LinearLayout.LayoutParams(d(40f), d(36f)))
        selCount.textSize = 15f
        selCount.setTypeface(selCount.typeface, android.graphics.Typeface.BOLD)
        selCount.setPadding(d(10f), 0, 0, 0)
        selBar.addView(selCount, LinearLayout.LayoutParams(0, -2, 1f))
        selEdit.setOnClickListener {
            listener?.onPanelFeedback()
            val it = items.firstOrNull { c -> c.id in selected } ?: return@setOnClickListener
            clearSelection()
            actions?.onClipWrite(it)
        }
        selPin.setOnClickListener {
            listener?.onPanelFeedback()
            val list = items.filter { it.id in selected }
            ClipStore.setPinned(list, !list.all { it.pinned })
            clearSelection()
        }
        selDel.setOnClickListener {
            listener?.onPanelFeedback()
            ClipStore.removeAll(items.filter { it.id in selected })
            clearSelection()
        }
        for (b in listOf(selEdit, selPin, selDel)) selBar.addView(b, LinearLayout.LayoutParams(d(44f), d(36f)).apply { leftMargin = d(4f) })
        selBar.visibility = GONE
        header.addView(selBar, LayoutParams(-1, -1))
        body.addView(header, LinearLayout.LayoutParams(-1, d(44f)))

        grid.numColumns = 2
        grid.horizontalSpacing = d(6f)
        grid.verticalSpacing = d(6f)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.setPadding(d(6f), d(2f), d(6f), d(6f))
        grid.clipToPadding = false
        grid.selector = android.graphics.drawable.ColorDrawable(0)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ ->
            val it = items[pos]
            if (selecting) toggleSelect(it) else paste(it)
        }
        grid.setOnItemLongClickListener { _, _, pos, _ -> toggleSelect(items[pos]); true }

        empty.gravity = Gravity.CENTER
        empty.textSize = 15f
        empty.setPadding(d(24f), 0, d(24f), 0)
        grid.isVerticalScrollBarEnabled = false
        grid.isFastScrollEnabled = false
        body.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(empty, LinearLayout.LayoutParams(-1, 0, 1f))

        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(d(4f), d(3f), d(4f), d(5f))
        abc = PanelUi.barButton(context, "ABC", palette)
        abc.setOnClickListener { listener?.onPanelFeedback(); listener?.onPanelBack() }
        bar.addView(abc, LinearLayout.LayoutParams(d(54f), d(40f)))
        val hint = TextView(context)
        L.bind(hint, "Toca para pegar · Mantén para seleccionar", "Tap to paste · Hold to select")
        hint.textSize = 12f
        hint.gravity = Gravity.CENTER
        hint.tag = "hint"
        bar.addView(hint, LinearLayout.LayoutParams(0, d(40f), 1f))
        del = IconView(context, Icons.DELETE, 9.5f)
        PanelUi.makeRepeatingDelete(del) { listener }
        bar.addView(del, LinearLayout.LayoutParams(d(54f), d(40f)))
        body.addView(bar, LinearLayout.LayoutParams(-1, -2))

        addView(body, LayoutParams(-1, -1))
        addView(overlay, LayoutParams(-1, -1))

        applyPalette(palette)
    }

    private fun makeHolder(): Holder {
        val box = FrameLayout(context)
        box.layoutParams = AbsListView.LayoutParams(-1, d(86f))
        val text = TextView(context)
        text.textSize = 14f
        text.maxLines = 4
        text.ellipsize = TextUtils.TruncateAt.END
        text.setPadding(d(10f), d(8f), d(10f), d(8f))
        box.addView(text, FrameLayout.LayoutParams(-1, -1))
        val image = ImageView(context)
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.clipToOutline = true
        image.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, d(9f).toFloat())
            }
        }
        box.addView(image, FrameLayout.LayoutParams(-1, -1))
        val pin = IconView(context, Icons.PIN, 5.5f)
        pin.isClickable = false
        box.addView(pin, FrameLayout.LayoutParams(d(20f), d(20f), Gravity.TOP or Gravity.END).apply {
            topMargin = d(2f); rightMargin = d(4f)
        })
        val check = IconView(context, Icons.SEL_OFF, 7f)
        check.isClickable = false
        check.visibility = GONE
        box.addView(check, FrameLayout.LayoutParams(d(26f), d(26f), Gravity.TOP or Gravity.END).apply {
            topMargin = d(4f); rightMargin = d(4f)
        })
        val h = Holder(box, text, image, pin, check)
        box.tag = h
        return h
    }

    private fun bindThumb(iv: ImageView, item: ClipItem) {
        val key = item.file ?: return
        iv.tag = key
        val cached = thumbs.get(key)
        if (cached != null) { iv.setImageBitmap(cached); return }
        iv.setImageDrawable(null)
        val file = ClipStore.fileOf(context, item) ?: return
        val target = d(180f)
        decoder.execute {
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, o)
                var s = 1
                while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2
                val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
                    ?: return@execute
                handler.post {
                    thumbs.put(key, bmp)
                    if (iv.tag == key) iv.setImageBitmap(bmp)
                }
            } catch (_: Throwable) { }
        }
    }

    // ---------------------------------------------------------------- Seleccionar

    private fun toggleSelect(it: ClipItem) {
        listener?.onPanelFeedback()
        if (!selected.remove(it.id)) selected.add(it.id)
        if (selecting) startWiggle() else stopWiggle()
        paintSelection()
        adapter.notifyDataSetChanged()
    }

    private fun clearSelection() {
        selected.clear()
        stopWiggle()
        paintSelection()
        refresh()
    }

    private fun startWiggle() {
        grid.removeCallbacks(wiggle)
        grid.postOnAnimation(wiggle)
    }

    private fun stopWiggle() {
        grid.removeCallbacks(wiggle)
        for (i in 0 until grid.childCount) grid.getChildAt(i).rotation = 0f
    }

    private fun paintSelection() {
        normalBar.visibility = if (selecting) GONE else VISIBLE
        selBar.visibility = if (selecting) VISIBLE else GONE
        val n = selected.size
        selCount.text = if (n == 1) L.t("1 seleccionado", "1 selected") else L.t("$n seleccionados", "$n selected")
        // Editar: solo con un texto seleccionado
        val one = items.firstOrNull { it.id in selected }
        selEdit.visibility = if (n == 1 && one != null && !one.isImage) VISIBLE else GONE
    }

    fun open() {
        overlay.hide()
        selected.clear()
        stopWiggle()
        paintSelection()
        confirmClear = false
        ClipStore.load(context) { ClipStore.purgeOld(); refresh() }
        refresh()
        grid.setSelection(0)
    }

    fun close() {
        overlay.hide()
        selected.clear()
        stopWiggle()
    }

    fun refresh() {
        items = if (historyOn) ClipStore.ordered() else emptyList()
        selected.retainAll(items.map { it.id }.toSet())
        adapter.notifyDataSetChanged()
        val n = ClipStore.items.size
        val word = L.t("Portapapeles", "Clipboard")
        title.text = if (n == 0) word else "$word ($n)"
        val showEmpty = items.isEmpty()
        empty.text = if (!historyOn) L.t(
            "El historial del portapapeles está apagado.\nPuedes encenderlo en los ajustes.",
            "Clipboard history is off.\nYou can turn it on in settings.",
        )
        else L.t(
            "Todo lo que copies (textos e imágenes) aparecerá aquí.\nDura 2 días; lo que fijes 📌 se queda para siempre.",
            "Everything you copy (text and images) shows up here.\nIt lasts 2 days; what you pin 📌 stays forever.",
        )
        empty.visibility = if (showEmpty) VISIBLE else GONE
        grid.visibility = if (showEmpty) GONE else VISIBLE
        clearBtn.visibility = if (historyOn && ClipStore.items.any { !it.pinned }) VISIBLE else GONE
        clearBtn.text = if (confirmClear) L.t("¿Borrar todo?", "Clear all?") else L.t("Borrar", "Clear")
        clearBtn.setTextColor(if (confirmClear) 0xFFE57373.toInt() else palette.accent)
    }

    private fun onClearTap() {
        listener?.onPanelFeedback()
        if (!confirmClear) {
            confirmClear = true
            refresh()
            handler.postDelayed({ confirmClear = false; refresh() }, 3000)
            return
        }
        confirmClear = false
        handler.removeCallbacksAndMessages(null)
        ClipStore.clearUnpinned()
        refresh()
    }

    private fun paste(item: ClipItem) {
        listener?.onPanelFeedback()
        if (item.isImage) actions?.onPasteImage(item) else item.text?.let { actions?.onPasteText(it) }
    }

    /** Menú al mantener presionado: fijar, pegar o borrar (se dibuja dentro del panel). */
    private fun showMenu(item: ClipItem) {
        listener?.onPanelFeedback()
        val card = LinearLayout(context)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(d(6f), d(6f), d(6f), d(6f))
        card.background = PanelUi.rounded(palette.popup, d(12f).toFloat())
        card.elevation = d(8f).toFloat()
        val preview = TextView(context)
        preview.text = if (item.isImage) L.t("🖼  Imagen", "🖼  Image") else item.text?.trim()
        preview.maxLines = 2
        preview.ellipsize = TextUtils.TruncateAt.END
        preview.textSize = 13f
        preview.setTextColor(palette.hint)
        preview.setPadding(d(14f), d(8f), d(14f), d(6f))
        card.addView(preview, LinearLayout.LayoutParams(-1, -2))
        fun option(label: String, action: () -> Unit) {
            val tv = TextView(context)
            tv.text = label
            tv.textSize = 16f
            tv.setTextColor(palette.text)
            tv.setPadding(d(14f), d(12f), d(14f), d(12f))
            tv.background = PanelUi.pressable(palette.popup, palette.keyPressed, d(8f).toFloat())
            tv.setOnClickListener { overlay.hide(); listener?.onPanelFeedback(); action() }
            card.addView(tv, LinearLayout.LayoutParams(-1, -2))
        }
        option(if (item.pinned) L.t("📌   Quitar de fijados", "📌   Unpin") else L.t("📌   Fijar arriba", "📌   Pin to top")) {
            ClipStore.togglePin(item); refresh()
        }
        option(L.t("📋   Pegar", "📋   Paste")) { paste(item) }
        option(L.t("🗑   Borrar", "🗑   Delete")) { ClipStore.remove(item); refresh() }
        val lp = LayoutParams(minOf(d(280f), width - d(32f)), -2, Gravity.CENTER)
        overlay.show(card, lp)
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        selCount.setTextColor(p.text)
        empty.setTextColor(p.hint)
        for (b in listOf(backBtn, addBtn, findBtn, selClose, selEdit, selPin, selDel)) {
            b.color = p.text
            b.bgColor = p.bg
            b.background = PanelUi.pressable(p.bg, p.keyPressed, d(18f).toFloat())
        }
        val r = d(7f).toFloat()
        abc.setTextColor(p.text)
        abc.background = PanelUi.pressable(p.special, p.keyPressed, r)
        del.color = p.text
        del.bgColor = p.special
        del.background = PanelUi.pressable(p.special, p.keyPressed, r)
        findViewWithTag<TextView>("hint")?.setTextColor(p.hint)
        refresh()
    }
}
