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
 * 🎤 Dictar, 📲 Mensajes rápidos, ⌫ borrar, la jornada (Llegué / Casa) y volver al teclado.
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
        /** ⌫ Borrar una letra. */
        fun onDriveDelete()
        /** 🗑️ Borrar todo lo escrito. */
        fun onDriveClearAll()
        /** ↩️ Recuperar lo que borraste. */
        fun onDriveUndo()
    }

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val title = TextView(context)
    private val backBtn = IconView(context, Icons.BACK, 7.5f)
    private val big = ArrayList<Pair<TextView, Boolean>>()
    private val notDriving = TextView(context)
    private val grid = LinearLayout(context)
    private val delBtn = TextView(context)
    private val clearBtn = TextView(context)
    private val repeat = android.os.Handler(android.os.Looper.getMainLooper())
    private var undoMode = false
    private val endUndo = Runnable { setUndo(false) }

    /** Después de «Borrar todo», el mismo botón dice «↩️ Deshacer» por 8 segundos. */
    private fun setUndo(on: Boolean) {
        undoMode = on
        repeat.removeCallbacks(endUndo)
        clearBtn.tag = if (on) L.t("↩️ Deshacer", "↩️ Undo") else L.t("🗑️ Borrar todo", "🗑️ Delete all")
        if (on) repeat.postDelayed(endUndo, 8000)
        paint()
        if (on) LightFx.flash(clearBtn, d(18f).toFloat())
    }
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
        // ⌫ Borrar letra por letra (mantén presionado para borrar seguido) y 🗑️ Borrar todo
        val tools = LinearLayout(context)
        tools.orientation = LinearLayout.HORIZONTAL
        for (b in listOf(delBtn, clearBtn)) {
            b.textSize = 15f
            b.gravity = Gravity.CENTER
            b.setTypeface(b.typeface, Typeface.BOLD)
        }
        val tick = object : Runnable {
            override fun run() { actions?.onDriveDelete(); repeat.postDelayed(this, 70) }
        }
        delBtn.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    actions?.onDriveFeedback(); actions?.onDriveDelete()
                    repeat.postDelayed(tick, 450)
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> { v.isPressed = false; repeat.removeCallbacks(tick) }
            }
            true
        }
        clearBtn.setOnClickListener {
            actions?.onDriveFeedback()
            if (undoMode) {
                // ↩️ Lo recupera y el botón vuelve a ser «Borrar todo»
                actions?.onDriveUndo()
                setUndo(false)
            } else {
                actions?.onDriveClearAll()
                setUndo(true) // durante unos segundos el botón se vuelve «↩️ Deshacer»
            }
        }
        tools.addView(delBtn, LinearLayout.LayoutParams(0, d(48f), 1f).apply { setMargins(d(5f), d(4f), d(5f), 0) })
        tools.addView(clearBtn, LinearLayout.LayoutParams(0, d(48f), 1f).apply { setMargins(d(5f), d(4f), d(5f), 0) })
        body.addView(tools, LinearLayout.LayoutParams(-1, -2))
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
        Emo.apply(title, L.t("🛻 Modo manejando · usa la voz", "🛻 Driving mode · use your voice"), PanelUi.icon(palette), 18f)
        notDriving.text = L.t("No voy manejando (teclado normal hoy)", "I'm not driving (normal keyboard today)")
        delBtn.tag = L.t("⌫ Borrar letra", "⌫ Delete letter")
        undoMode = false
        clearBtn.tag = L.t("🗑️ Borrar todo", "🗑️ Delete all")
        grid.removeAllViews()
        big.clear()
        val items = listOf(
            Triple(L.t("🎤\nDictar", "🎤\nDictate"), true) { actions?.onDriveVoice() },
            Triple(L.t("📲\nMensajes rápidos", "📲\nQuick messages"), false) { actions?.onDriveStatus() },
            Triple(WorkDay.label(next).replaceFirst(" ", "\n"), false) { actions?.onDriveWorkday(nextKind) },
            Triple(L.t("⌨️\nTeclado", "⌨️\nKeyboard"), false) { actions?.onDriveKeyboard() },
        )
        for (r in 0..1) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0..1) {
                val (label, primary, act) = items[r * 2 + c]
                val t = TextView(context)
                t.tag = label
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
            // Ícono pequeño (como los de la barra de arriba) en vez de emoji
            Emo.applyTop(t, (t.tag as? String) ?: "", if (primary) p.onAccent else PanelUi.icon(p))
            t.background = if (primary) PanelUi.pressable(p.accent, p.accentPressed, d(22f).toFloat())
            else PanelUi.pressable(p.key, p.keyPressed, d(22f).toFloat())
        }
        for (b in listOf(delBtn, clearBtn)) {
            b.setTextColor(p.text)
            Emo.apply(b, (b.tag as? String) ?: "", PanelUi.icon(p), 18f)
            b.background = PanelUi.glassButton(context, p, d(18f).toFloat())
        }
        if (undoMode) { clearBtn.setTextColor(p.onAccent); Emo.apply(clearBtn, (clearBtn.tag as? String) ?: "", p.onAccent, 18f); clearBtn.background = PanelUi.pressable(p.accent, p.accentPressed, d(18f).toFloat()) }
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
