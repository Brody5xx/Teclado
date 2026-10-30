package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Panel del dictado por voz: micrófono animado, lo que vas diciendo y el idioma. */
@SuppressLint("ViewConstructor")
class VoicePanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onMicTap()
        fun onVoiceLangTap()
    }

    var listener: PanelListener? = null
    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val mic = MicView(context)
    private val status = TextView(context)
    private val partial = TextView(context)
    private val langBtn: TextView
    private val abc: TextView
    private val del: TextView

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.gravity = Gravity.CENTER_HORIZONTAL

        partial.textSize = 17f
        partial.gravity = Gravity.CENTER
        partial.maxLines = 3
        partial.ellipsize = TextUtils.TruncateAt.START
        partial.setPadding(d(20f), d(12f), d(20f), 0)
        body.addView(partial, LinearLayout.LayoutParams(-1, 0, 1f))

        mic.setOnClickListener { listener?.onPanelFeedback(); actions?.onMicTap() }
        body.addView(mic, LinearLayout.LayoutParams(d(110f), d(110f)))

        status.textSize = 14f
        status.gravity = Gravity.CENTER
        status.setPadding(d(16f), d(6f), d(16f), d(6f))
        body.addView(status, LinearLayout.LayoutParams(-1, -2))

        val bar = LinearLayout(context)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(d(4f), d(3f), d(4f), d(5f))
        abc = PanelUi.barButton(context, "ABC", palette)
        abc.setOnClickListener { listener?.onPanelFeedback(); listener?.onPanelBack() }
        bar.addView(abc, LinearLayout.LayoutParams(d(54f), d(40f)))
        langBtn = PanelUi.barButton(context, "Español", palette, special = false)
        langBtn.setOnClickListener { listener?.onPanelFeedback(); actions?.onVoiceLangTap() }
        bar.addView(langBtn, LinearLayout.LayoutParams(0, d(40f), 1f).apply { leftMargin = d(8f); rightMargin = d(8f) })
        del = PanelUi.barButton(context, "⌫", palette)
        PanelUi.makeRepeatingDelete(del) { listener }
        bar.addView(del, LinearLayout.LayoutParams(d(54f), d(40f)))
        body.addView(bar, LinearLayout.LayoutParams(-1, -2))

        addView(body, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    fun setListening(on: Boolean) { mic.listening = on }
    fun setLevel(rmsDb: Float) { mic.level = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f) }
    fun setStatus(text: String) { status.text = text }
    fun setPartial(text: String) { partial.text = text }
    fun setLang(lang: String) { langBtn.text = if (lang == "es") "🎤 Español" else "🎤 English" }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        mic.palette = p
        status.setTextColor(p.hint)
        partial.setTextColor(p.text)
        val r = d(7f).toFloat()
        for (b in listOf(abc, del)) { b.setTextColor(p.text); b.background = PanelUi.pressable(p.special, p.keyPressed, r) }
        langBtn.setTextColor(p.text)
        langBtn.background = PanelUi.pressable(p.key, p.keyPressed, r)
    }

    /** El círculo del micrófono: crece con tu voz mientras escucha. */
    private class MicView(context: Context) : View(context) {
        var palette = Palette.DARK
            set(v) { field = v; invalidate() }
        var listening = false
            set(v) { field = v; if (!v) level = 0f; invalidate() }
        var level = 0f
            set(v) { field = field * 0.6f + v * 0.4f; invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()

        init { isClickable = true }

        override fun onDraw(c: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val base = minOf(width, height) * 0.3f
            if (listening) {
                paint.color = palette.accent
                paint.alpha = 60
                c.drawCircle(cx, cy, base * (1.15f + level * 0.5f), paint)
            }
            paint.color = if (listening) palette.accent else palette.key
            paint.alpha = 255
            c.drawCircle(cx, cy, base, paint)
            // Dibujo del micrófono
            val fg = if (listening) palette.onAccent else palette.text
            paint.color = fg
            val s = base * 0.42f
            r.set(cx - s * 0.42f, cy - s * 0.95f, cx + s * 0.42f, cy + s * 0.25f)
            c.drawRoundRect(r, s * 0.42f, s * 0.42f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.16f
            paint.strokeCap = Paint.Cap.ROUND
            r.set(cx - s * 0.7f, cy - s * 0.55f, cx + s * 0.7f, cy + s * 0.6f)
            c.drawArc(r, 0f, 180f, false, paint)
            c.drawLine(cx, cy + s * 0.6f, cx, cy + s * 0.95f, paint)
            c.drawLine(cx - s * 0.4f, cy + s * 0.95f, cx + s * 0.4f, cy + s * 0.95f, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
