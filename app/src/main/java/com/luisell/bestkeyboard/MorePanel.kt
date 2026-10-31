package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Panel "Más opciones" (como el de Gboard): botones grandes con todas las herramientas,
 * incluso las que no caben en la barra de arriba.
 */
@SuppressLint("ViewConstructor")
class MorePanel(context: Context) : FrameLayout(context) {

    interface Actions {
        /** [tool] es una de las constantes KeyboardView.TOOL_... */
        fun onMoreTool(tool: Int)
        fun onMoreBack()
        fun onMoreFeedback()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val back = IconView(context, Icons.BACK, 8f)
    private val title = TextView(context)
    private val buttons = ArrayList<Pair<LinearLayout, Pair<IconView, TextView>>>()

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(8f), d(4f), d(8f), d(6f))

        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        back.setOnClickListener { actions?.onMoreFeedback(); actions?.onMoreBack() }
        head.addView(back, LinearLayout.LayoutParams(d(44f), d(40f)))
        L.bind(title, "Más opciones", "More options")
        title.textSize = 18f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(12f), 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(head, LinearLayout.LayoutParams(-1, d(46f)))

        val items = listOf(
            Triple(KeyboardView.TOOL_RESIZE, Icons.RESIZE, "Cambiar tamaño" to "Resize"),
            Triple(KeyboardView.TOOL_LANG, Icons.GLOBE, "Siguiente idioma" to "Next language"),
            Triple(KeyboardView.TOOL_THEME, Icons.THEME, "Tema claro / oscuro" to "Light / dark theme"),
            Triple(KeyboardView.TOOL_SETTINGS, Icons.SETTINGS, "Ajustes" to "Settings"),
            Triple(KeyboardView.TOOL_EMOJI, Icons.EMOJI, "Emojis" to "Emojis"),
            Triple(KeyboardView.TOOL_CLIP, Icons.CLIP, "Portapapeles" to "Clipboard"),
            Triple(KeyboardView.TOOL_TRANSLATE, Icons.TRANSLATE, "Traducir" to "Translate"),
            Triple(KeyboardView.TOOL_SCAN, Icons.SCAN, "Escanear texto" to "Scan text"),
            Triple(KeyboardView.TOOL_SPELL, Icons.SPELL, "Ortografía" to "Spell check"),
            Triple(KeyboardView.TOOL_EDIT, Icons.EDIT, "Edición de texto" to "Text editing"),
            Triple(KeyboardView.TOOL_VOICE, Icons.MIC, "Dictado por voz" to "Voice typing"),
        )
        val grid = LinearLayout(context)
        grid.orientation = LinearLayout.VERTICAL
        var row: LinearLayout? = null
        for ((n, it) in items.withIndex()) {
            if (n % 2 == 0) {
                row = LinearLayout(context)
                row.orientation = LinearLayout.HORIZONTAL
                grid.addView(row, LinearLayout.LayoutParams(-1, d(54f)).apply { topMargin = d(8f) })
            }
            val (tool, icon, names) = it
            row?.addView(button(tool, icon, names.first, names.second),
                LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(d(4f), 0, d(4f), 0) })
        }
        // Si quedó un espacio vacío en la última fila
        if (items.size % 2 == 1) row?.addView(android.view.View(context), LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(d(4f), 0, d(4f), 0) })

        val scroll = ScrollView(context)
        scroll.isVerticalScrollBarEnabled = true
        scroll.addView(grid, LayoutParams(-1, -2))
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(body, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    private fun button(tool: Int, icon: Int, es: String, en: String): LinearLayout {
        val b = LinearLayout(context)
        b.orientation = LinearLayout.HORIZONTAL
        b.gravity = Gravity.CENTER_VERTICAL
        b.setPadding(d(16f), 0, d(12f), 0)
        b.isClickable = true
        val iv = IconView(context, icon, 9f)
        iv.isClickable = false
        iv.isDuplicateParentStateEnabled = true
        b.addView(iv, LinearLayout.LayoutParams(d(28f), d(28f)))
        val t = TextView(context)
        L.bind(t, es, en)
        t.textSize = 15f
        t.maxLines = 1
        t.ellipsize = TextUtils.TruncateAt.END
        t.setPadding(d(12f), 0, 0, 0)
        b.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        b.setOnClickListener { actions?.onMoreFeedback(); actions?.onMoreTool(tool) }
        buttons.add(b to (iv to t))
        return b
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        back.color = p.text
        back.background = PanelUi.pressable(p.special, p.keyPressed, d(20f).toFloat())
        for ((b, pair) in buttons) {
            b.background = PanelUi.pressable(p.key, p.keyPressed, d(27f).toFloat())
            pair.first.color = p.text
            pair.first.bgColor = p.key
            pair.second.setTextColor(p.text)
        }
    }
}
