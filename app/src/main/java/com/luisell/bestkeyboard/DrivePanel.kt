package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Modo manejando: botones grandes para no escribir mientras manejas.
 * 🎤 Dictar, 📲 Mensaje de estado, la jornada (Llegué / Casa) y volver al teclado.
 */
@SuppressLint("ViewConstructor")
class DrivePanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onDriveVoice()
        fun onDriveStatus()
        fun onDriveWorkday(kind: String)
        fun onDriveKeyboard()
        fun onDriveNotDriving()
        fun onDriveFeedback()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val title = TextView(context)
    private val backBtn = IconView(context, Icons.BACK, 7.5f)
    private val big = ArrayList<Pair<TextView, Boolean>>()
    private val notDriving = TextView(context)
    private val grid = LinearLayout(context)
    private var nextKind = WorkDay.ARRIVE

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(8f), d(6f), d(8f), d(6f))
        title.textSize = 15f
        title.gravity = Gravity.CENTER
        title.setTypeface(title.typeface, Typeface.BOLD)
        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        backBtn.setOnClickListener { actions?.onDriveFeedback(); actions?.onDriveKeyboard() }
        head.addView(backBtn, LinearLayout.LayoutParams(d(40f), d(36f)))
        head.addView(title, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = d(40f) })
        body.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = d(6f) })
        grid.orientation = LinearLayout.VERTICAL
        body.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        notDriving.textSize = 13f
        notDriving.gravity = Gravity.CENTER
        notDriving.setPadding(0, d(8f), 0, d(4f))
        notDriving.setOnClickListener { actions?.onDriveFeedback(); actions?.onDriveNotDriving() }
        body.addView(notDriving, LinearLayout.LayoutParams(-1, -2))
        addView(body, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    /** [next] = lo que toca en la jornada (Llegué o Casa). */
    fun open(next: String) {
        nextKind = next
        title.text = L.t("🛻 Modo manejando · usa la voz", "🛻 Driving mode · use your voice")
        notDriving.text = L.t("No voy manejando (teclado normal hoy)", "I'm not driving (normal keyboard today)")
        grid.removeAllViews()
        big.clear()
        val items = listOf(
            Triple(L.t("🎤\nDictar", "🎤\nDictate"), true) { actions?.onDriveVoice() },
            Triple(L.t("📲\nMensaje de estado", "📲\nStatus message"), false) { actions?.onDriveStatus() },
            Triple(WorkDay.label(next).replaceFirst(" ", "\n"), false) { actions?.onDriveWorkday(nextKind) },
            Triple(L.t("⌨️\nTeclado", "⌨️\nKeyboard"), false) { actions?.onDriveKeyboard() },
        )
        for (r in 0..1) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0..1) {
                val (label, primary, act) = items[r * 2 + c]
                val t = TextView(context)
                t.text = label
                t.textSize = 18f
                t.gravity = Gravity.CENTER
                t.setTypeface(t.typeface, Typeface.BOLD)
                t.setOnClickListener { actions?.onDriveFeedback(); act() }
                big.add(t to primary)
                row.addView(t, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(d(5f), d(5f), d(5f), d(5f)) })
            }
            grid.addView(row, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        paint()
    }

    private fun paint() {
        val p = palette
        for ((t, primary) in big) {
            t.setTextColor(if (primary) p.onAccent else p.text)
            t.background = if (primary) PanelUi.pressable(p.accent, p.accentPressed, d(22f).toFloat())
            else PanelUi.pressable(p.key, p.keyPressed, d(22f).toFloat())
        }
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        title.setTextColor(p.text)
        backBtn.color = p.text
        backBtn.bgColor = p.bg
        backBtn.background = PanelUi.pressable(p.special, p.keyPressed, d(18f).toFloat())
        notDriving.setTextColor(p.hint)
        paint()
    }
}
