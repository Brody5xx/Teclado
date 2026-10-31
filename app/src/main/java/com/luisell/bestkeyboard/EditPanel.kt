package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Panel "Edición de texto · Text editing" (como Gboard): mover el cursor, seleccionar,
 * seleccionar todo, cortar, copiar, pegar, ir al inicio/fin y borrar.
 */
@SuppressLint("ViewConstructor")
class EditPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onEditMove(keyCode: Int, select: Boolean)
        fun onEditCommand(id: Int)   // android.R.id.selectAll / cut / copy / paste
        fun onEditDelete()
        fun onEditBack()
        fun onEditFeedback()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    /** Modo seleccionar: las flechas van marcando el texto. */
    private var selecting = false
    private val title = TextView(context)
    private val back = IconView(context, Icons.BACK, 8f)
    private val keys = ArrayList<Pair<TextView, Boolean>>() // (botón, es de acción)
    private lateinit var selectBtn: TextView

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(6f), d(4f), d(6f), d(6f))

        // Encabezado
        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        back.setOnClickListener { actions?.onEditFeedback(); actions?.onEditBack() }
        head.addView(back, LinearLayout.LayoutParams(d(44f), d(40f)))
        L.bind(title, "Edición de texto", "Text editing")
        title.textSize = 18f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(12f), 0, 0, 0)
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(head, LinearLayout.LayoutParams(-1, d(46f)))

        // Parte de arriba: 4 columnas
        val grid = LinearLayout(context)
        grid.orientation = LinearLayout.HORIZONTAL
        fun col() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        fun lp(weight: Float = 1f) = LinearLayout.LayoutParams(-1, 0, weight).apply { setMargins(d(3f), d(3f), d(3f), d(3f)) }

        val c0 = col()
        c0.addView(moveKey("‹", "‹", android.view.KeyEvent.KEYCODE_DPAD_LEFT), lp())
        val c1 = col()
        c1.addView(moveKey("˄", "˄", android.view.KeyEvent.KEYCODE_DPAD_UP), lp())
        selectBtn = key("Seleccionar", "Select", false) {
            selecting = !selecting
            paintSelect()
        }
        selectBtn.textSize = 14f
        c1.addView(selectBtn, lp())
        c1.addView(moveKey("˅", "˅", android.view.KeyEvent.KEYCODE_DPAD_DOWN), lp())
        val c2 = col()
        c2.addView(moveKey("›", "›", android.view.KeyEvent.KEYCODE_DPAD_RIGHT), lp())
        val c3 = col()
        c3.addView(command("Todo", "Select all", android.R.id.selectAll), lp())
        c3.addView(command("Cortar", "Cut", android.R.id.cut), lp())
        c3.addView(command("Copiar", "Copy", android.R.id.copy), lp())
        c3.addView(command("Pegar", "Paste", android.R.id.paste), lp())
        for (c in listOf(c0, c1, c2, c3)) grid.addView(c, LinearLayout.LayoutParams(0, -1, 1f))
        body.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))

        // Abajo: inicio, fin, borrar
        val bottom = LinearLayout(context)
        bottom.orientation = LinearLayout.HORIZONTAL
        val blp = { LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(d(3f), d(3f), d(3f), d(3f)) } }
        bottom.addView(moveKey("|‹  Inicio", "|‹  Start", android.view.KeyEvent.KEYCODE_MOVE_HOME), blp())
        bottom.addView(moveKey("Fin  ›|", "End  ›|", android.view.KeyEvent.KEYCODE_MOVE_END), blp())
        val del = key("⌫", "⌫", true) {}
        makeRepeating(del, { actions?.onEditFeedback() }) { actions?.onEditDelete() }
        bottom.addView(del, blp())
        body.addView(bottom, LinearLayout.LayoutParams(-1, d(54f)))

        addView(body, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    private fun key(es: String, en: String, action: Boolean, onClick: () -> Unit): TextView {
        val t = TextView(context)
        L.bind(t, es, en)
        t.textSize = 16f
        t.gravity = Gravity.CENTER
        t.setOnClickListener { actions?.onEditFeedback(); onClick() }
        keys.add(Pair(t, action))
        return t
    }

    /** Flecha que repite mientras la mantienes (y selecciona si "Seleccionar" está encendido). */
    private fun moveKey(es: String, en: String, code: Int): TextView {
        val t = key(es, en, false) {}
        t.textSize = if (es.length <= 1) 26f else 15f
        makeRepeating(t, { actions?.onEditFeedback() }) { actions?.onEditMove(code, selecting) }
        return t
    }

    private fun command(es: String, en: String, id: Int): TextView {
        val t = key(es, en, true) {
            actions?.onEditCommand(id)
            // Después de copiar, cortar o pegar se apaga el modo seleccionar
            if (id != android.R.id.selectAll) { selecting = false; paintSelect() }
        }
        t.textSize = 14f
        return t
    }

    fun open() {
        selecting = false
        paintSelect()
    }

    private fun paintSelect() {
        val r = d(10f).toFloat()
        if (selecting) {
            selectBtn.setTextColor(palette.onAccent)
            selectBtn.background = PanelUi.pressable(palette.accent, palette.accentPressed, r)
        } else {
            selectBtn.setTextColor(palette.text)
            selectBtn.background = PanelUi.pressable(palette.key, palette.keyPressed, r)
        }
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        back.color = p.text
        back.background = PanelUi.pressable(p.special, p.keyPressed, d(20f).toFloat())
        val r = d(10f).toFloat()
        for ((t, action) in keys) {
            t.setTextColor(p.text)
            t.background = PanelUi.pressable(if (action) p.special else p.key, p.keyPressed, r)
        }
        paintSelect()
    }
}
