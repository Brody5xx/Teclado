package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Panel con una lista y botones abajo (Libreta del día y Mis tickets).
 * Cada fila: un texto chiquito arriba (hora o número), el texto, y botones de copiar / borrar.
 */
@SuppressLint("ViewConstructor")
class ListPanel(context: Context) : FrameLayout(context) {

    class Row(
        val meta: String,
        val text: String,
        val onTap: (() -> Unit)? = null,
        val onCopy: (() -> Unit)? = null,
        val onDelete: (() -> Unit)? = null,
    )

    var onBack: (() -> Unit)? = null
    var onFeedback: (() -> Unit)? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val back = IconView(context, Icons.BACK, 8f)
    private val title = TextView(context)
    private val list = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val glow = GlowScrollbar(context)
    private val buttons = LinearLayout(context)
    private var rows: List<Row> = emptyList()
    private var actions: List<Pair<String, () -> Unit>> = emptyList()
    private var empty = ""

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(6f), d(4f), d(6f), d(6f))
        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        back.setOnClickListener { onFeedback?.invoke(); onBack?.invoke() }
        head.addView(back, LinearLayout.LayoutParams(d(44f), d(38f)))
        title.textSize = 17f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(12f), 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(head, LinearLayout.LayoutParams(-1, d(44f)))

        list.orientation = LinearLayout.VERTICAL
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list)
        val stack = FrameLayout(context)
        stack.addView(scroll, LayoutParams(-1, -1))
        stack.addView(glow, LayoutParams(-1, -1))
        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val content = list.height.toFloat()
            val view = scroll.height.toFloat()
            if (content > view && view > 0f) glow.show(y / (content - view), view / content)
        }
        body.addView(stack, LinearLayout.LayoutParams(-1, 0, 1f))

        buttons.orientation = LinearLayout.HORIZONTAL
        val hs = HorizontalScrollView(context)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(buttons)
        body.addView(hs, LinearLayout.LayoutParams(-1, -2).apply { topMargin = d(6f) })
        addView(body, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    fun show(heading: String, items: List<Row>, emptyText: String, bottom: List<Pair<String, () -> Unit>>) {
        title.text = heading
        rows = items
        empty = emptyText
        actions = bottom
        rebuild()
        scroll.scrollTo(0, 0)
    }

    private fun rebuild() {
        val p = palette
        list.removeAllViews()
        if (rows.isEmpty()) {
            val t = TextView(context)
            t.text = empty
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setTextColor(p.hint)
            t.setPadding(d(20f), d(28f), d(20f), d(28f))
            list.addView(t, LinearLayout.LayoutParams(-1, -2))
        }
        for (r in rows) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(d(14f), d(8f), d(4f), d(8f))
            row.background = PanelUi.pressable(p.key, p.keyPressed, d(16f).toFloat())
            val col = LinearLayout(context)
            col.orientation = LinearLayout.VERTICAL
            val meta = TextView(context)
            meta.text = r.meta
            meta.textSize = 11.5f
            meta.setTextColor(p.accent)
            col.addView(meta)
            val tx = TextView(context)
            tx.text = r.text
            tx.textSize = 14f
            tx.maxLines = 3
            tx.ellipsize = android.text.TextUtils.TruncateAt.END
            tx.setTextColor(p.text)
            col.addView(tx)
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            r.onCopy?.let { cp ->
                val b = TextView(context)
                b.text = L.t("Copiar", "Copy")
                b.textSize = 12f
                b.gravity = Gravity.CENTER
                b.setTextColor(p.text)
                b.background = PanelUi.pressable(p.special, p.keyPressed, d(14f).toFloat())
                b.setOnClickListener { onFeedback?.invoke(); cp() }
                row.addView(b, LinearLayout.LayoutParams(-2, d(30f)).apply { leftMargin = d(6f) })
                b.setPadding(d(10f), 0, d(10f), 0)
            }
            r.onDelete?.let { del ->
                val x = IconView(context, Icons.CLOSE, 5.5f)
                x.color = p.hint
                x.setOnClickListener { onFeedback?.invoke(); del() }
                row.addView(x, LinearLayout.LayoutParams(d(36f), d(36f)))
            }
            r.onTap?.let { tap -> row.setOnClickListener { onFeedback?.invoke(); tap() } }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(d(2f), d(3f), d(2f), d(3f)) })
        }
        buttons.removeAllViews()
        for ((i, a) in actions.withIndex()) {
            val b = TextView(context)
            b.text = a.first
            b.textSize = 14f
            b.gravity = Gravity.CENTER
            b.setPadding(d(16f), 0, d(16f), 0)
            if (i == 0) {
                b.setTypeface(b.typeface, Typeface.BOLD)
                b.setTextColor(p.onAccent)
                b.background = PanelUi.pressable(p.accent, p.accentPressed, d(18f).toFloat())
            } else {
                b.setTextColor(p.text)
                b.background = PanelUi.pressable(p.special, p.keyPressed, d(18f).toFloat())
            }
            b.setOnClickListener { onFeedback?.invoke(); a.second() }
            buttons.addView(b, LinearLayout.LayoutParams(-2, d(38f)).apply { rightMargin = d(8f) })
        }
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        back.color = p.text
        back.background = PanelUi.pressable(p.special, p.keyPressed, d(19f).toFloat())
        rebuild()
    }
}
