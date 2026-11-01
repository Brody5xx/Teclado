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
        /** Botón "Reporte": prende el modo reporte o, si ya hablaste, crea el reporte. */
        fun onReportTap()
        /** Botón "Escribir en inglés": hablas en español y se escribe en inglés. */
        fun onEnglishTap()
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
    private val reportBtn = TextView(context)
    private val backBtn = IconView(context, Icons.BACK, 7.5f)
    private val enBtn = TextView(context)
    private var enOn = false
    private var reportOn = false
    private var reportReady = false

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.gravity = Gravity.CENTER_HORIZONTAL

        partial.textSize = 17f
        partial.gravity = Gravity.CENTER
        partial.maxLines = 4
        partial.ellipsize = TextUtils.TruncateAt.START
        partial.setPadding(d(20f), d(12f), d(20f), 0)
        body.addView(partial, LinearLayout.LayoutParams(-1, 0, 1f))

        mic.setOnClickListener { listener?.onPanelFeedback(); actions?.onMicTap() }
        body.addView(mic, LinearLayout.LayoutParams(d(110f), d(110f)))

        status.textSize = 14f
        status.gravity = Gravity.CENTER
        status.setPadding(d(16f), d(6f), d(16f), d(6f))
        body.addView(status, LinearLayout.LayoutParams(-1, -2))

        // Modo reporte: hablas con tus palabras y la IA lo escribe como ticket formal
        reportBtn.textSize = 14f
        reportBtn.gravity = Gravity.CENTER
        reportBtn.setPadding(d(16f), 0, d(16f), 0)
        reportBtn.setOnClickListener { listener?.onPanelFeedback(); actions?.onReportTap() }
        enBtn.textSize = 14f
        enBtn.gravity = Gravity.CENTER
        enBtn.setPadding(d(16f), 0, d(16f), 0)
        enBtn.setOnClickListener { listener?.onPanelFeedback(); actions?.onEnglishTap() }
        val chips = LinearLayout(context)
        chips.orientation = LinearLayout.HORIZONTAL
        chips.gravity = Gravity.CENTER
        chips.addView(reportBtn, LinearLayout.LayoutParams(-2, d(36f)).apply { rightMargin = d(8f) })
        chips.addView(enBtn, LinearLayout.LayoutParams(-2, d(36f)))
        body.addView(chips, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = d(6f) })

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
        // Atrás (vuelve a las letras)
        backBtn.setOnClickListener { listener?.onPanelFeedback(); listener?.onPanelBack() }
        addView(backBtn, LayoutParams(PanelUi.dp(context, 40f), PanelUi.dp(context, 36f), Gravity.TOP or Gravity.START).apply {
            setMargins(PanelUi.dp(context, 6f), PanelUi.dp(context, 6f), 0, 0)
        })
        applyPalette(palette)
        setReport(false, false)
        setEnglish(false)
    }

    fun setListening(on: Boolean) { mic.listening = on }
    fun setLevel(rmsDb: Float) { mic.level = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f) }
    fun setStatus(text: String) { status.text = text }
    fun setPartial(text: String) { partial.text = text }
    /** [on] = modo reporte prendido; [ready] = ya hay algo dictado para convertir. */
    fun setReport(on: Boolean, ready: Boolean) {
        reportOn = on
        reportReady = ready
        reportBtn.text = when {
            on && ready -> L.t("✓ Crear reporte", "✓ Create report")
            on -> L.t("📋 Modo reporte: ON", "📋 Report mode: ON")
            else -> L.t("📋 Dictar reporte", "📋 Dictate report")
        }
        paintReport()
    }

    /** [on] = lo que dictas en español se escribe en inglés. */
    fun setEnglish(on: Boolean) {
        enOn = on
        enBtn.text = if (on) "🇺🇸 " + L.t("En inglés: ON", "English: ON") else "🇺🇸 " + L.t("Escribir en inglés", "Type in English")
        paintReport()
    }

    private fun paintReport() {
        val p = palette
        val rr = d(18f).toFloat()
        enBtn.setTextColor(if (enOn) p.onAccent else p.text)
        enBtn.background = if (enOn) PanelUi.pressable(p.accent, p.accentPressed, rr) else PanelUi.pressable(p.key, p.keyPressed, rr)
        val r = d(18f).toFloat()
        if (reportOn) {
            reportBtn.setTextColor(p.onAccent)
            reportBtn.background = PanelUi.pressable(p.accent, p.accentPressed, r)
        } else {
            reportBtn.setTextColor(p.text)
            reportBtn.background = PanelUi.pressable(p.key, p.keyPressed, r)
        }
    }

    fun setLang(lang: String) { langBtn.text = if (lang == "es") "🎤 Español" else "🎤 English" }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        mic.palette = p
        status.setTextColor(p.hint)
        partial.setTextColor(p.text)
        backBtn.color = p.text
        backBtn.bgColor = p.bg
        backBtn.background = PanelUi.pressable(p.special, p.keyPressed, d(18f).toFloat())
        val r = d(7f).toFloat()
        for (b in listOf(abc, del)) { b.setTextColor(p.text); b.background = PanelUi.pressable(p.special, p.keyPressed, r) }
        langBtn.setTextColor(p.text)
        langBtn.background = PanelUi.pressable(p.key, p.keyPressed, r)
        paintReport()
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
