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
    }

    var listener: PanelListener? = null
    var actions: Actions? = null
    var historyOn = true
        set(v) { field = v; refresh() }

    private var palette = Palette.DARK
    private var items: List<ClipItem> = emptyList()
    private val d = { v: Float -> PanelUi.dp(context, v) }

    private val title = TextView(context)
    private val clearBtn = TextView(context)
    private val grid = GridView(context)
    private val empty = TextView(context)
    private val abc: TextView
    private val del: TextView
    private val body = LinearLayout(context)
    private val overlay = PanelOverlay(context, 0x66000000)
    private var confirmClear = false
    private val handler = Handler(Looper.getMainLooper())

    // Miniaturas de las imágenes (se cargan en segundo plano para que todo vaya fluido)
    private val thumbs = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val decoder = Executors.newSingleThreadExecutor()

    private inner class Holder(val box: FrameLayout, val text: TextView, val image: ImageView, val pin: TextView)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = items[position].id
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val h = (convertView?.tag as? Holder) ?: makeHolder()
            val it = items[position]
            h.box.background = PanelUi.pressable(palette.key, palette.keyPressed, d(9f).toFloat())
            h.text.setTextColor(palette.text)
            h.pin.visibility = if (it.pinned) VISIBLE else GONE
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

        val header = LinearLayout(context)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(d(12f), d(4f), d(8f), d(2f))
        title.textSize = 15f
        title.setTypeface(title.typeface, android.graphics.Typeface.BOLD)
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        clearBtn.textSize = 14f
        clearBtn.setPadding(d(12f), d(8f), d(12f), d(8f))
        clearBtn.setOnClickListener { onClearTap() }
        header.addView(clearBtn, LinearLayout.LayoutParams(-2, -2))
        body.addView(header, LinearLayout.LayoutParams(-1, d(40f)))

        grid.numColumns = 2
        grid.horizontalSpacing = d(6f)
        grid.verticalSpacing = d(6f)
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.setPadding(d(6f), d(2f), d(6f), d(6f))
        grid.clipToPadding = false
        grid.selector = android.graphics.drawable.ColorDrawable(0)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> paste(items[pos]) }
        grid.setOnItemLongClickListener { _, _, pos, _ -> showMenu(items[pos]); true }

        empty.gravity = Gravity.CENTER
        empty.textSize = 15f
        empty.setPadding(d(24f), 0, d(24f), 0)
        grid.isVerticalScrollBarEnabled = true
        grid.isFastScrollEnabled = true
        grid.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
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
        hint.text = "Toca para pegar · Mantén para fijar o borrar"
        hint.textSize = 12f
        hint.gravity = Gravity.CENTER
        hint.tag = "hint"
        bar.addView(hint, LinearLayout.LayoutParams(0, d(40f), 1f))
        del = PanelUi.barButton(context, "⌫", palette)
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
        val pin = TextView(context)
        pin.text = "📌"
        pin.textSize = 12f
        box.addView(pin, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = d(2f); rightMargin = d(4f)
        })
        val h = Holder(box, text, image, pin)
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

    fun open() {
        overlay.hide()
        confirmClear = false
        ClipStore.load(context) { ClipStore.purgeOld(); refresh() }
        refresh()
        grid.setSelection(0)
    }

    fun close() = overlay.hide()

    fun refresh() {
        items = if (historyOn) ClipStore.ordered() else emptyList()
        adapter.notifyDataSetChanged()
        val n = ClipStore.items.size
        title.text = if (n == 0) "Portapapeles" else "Portapapeles ($n)"
        val showEmpty = items.isEmpty()
        empty.text = if (!historyOn) "El historial del portapapeles está apagado.\nPuedes encenderlo en los ajustes (⚙)."
        else "Todo lo que copies (textos e imágenes) aparecerá aquí.\nDura 2 días; lo que fijes 📌 se queda para siempre."
        empty.visibility = if (showEmpty) VISIBLE else GONE
        grid.visibility = if (showEmpty) GONE else VISIBLE
        clearBtn.visibility = if (historyOn && ClipStore.items.any { !it.pinned }) VISIBLE else GONE
        clearBtn.text = if (confirmClear) "¿Borrar todo?" else "Borrar"
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
        preview.text = if (item.isImage) "🖼  Imagen" else item.text?.trim()
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
        option(if (item.pinned) "📌   Quitar de fijados" else "📌   Fijar arriba") { ClipStore.togglePin(item); refresh() }
        option("📋   Pegar") { paste(item) }
        option("🗑   Borrar") { ClipStore.remove(item); refresh() }
        val lp = LayoutParams(minOf(d(280f), width - d(32f)), -2, Gravity.CENTER)
        overlay.show(card, lp)
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        empty.setTextColor(p.hint)
        val r = d(7f).toFloat()
        abc.setTextColor(p.text)
        abc.background = PanelUi.pressable(p.special, p.keyPressed, r)
        del.setTextColor(p.text)
        del.background = PanelUi.pressable(p.special, p.keyPressed, r)
        findViewWithTag<TextView>("hint")?.setTextColor(p.hint)
        refresh()
    }
}
