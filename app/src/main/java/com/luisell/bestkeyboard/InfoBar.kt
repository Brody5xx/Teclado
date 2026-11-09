package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Barra arriba del teclado que muestra un resultado (por ejemplo, un mensaje traducido) con botones. */
@SuppressLint("ViewConstructor")
class InfoBar(context: Context) : LinearLayout(context) {

    var onClose: (() -> Unit)? = null
    var onFeedback: (() -> Unit)? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val close = IconView(context, Icons.CLOSE, 7f)
    private val title = TextView(context)
    private val body = TextView(context)
    private val scroll = object : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(d(170f), MeasureSpec.AT_MOST))
        }
    }
    private val chips = LinearLayout(context)
    /** Los dos idiomas que se pueden tocar para cambiarlos (Traducir captura). */
    private val langRow = LinearLayout(context)
    /** 💎 El diamante con la luz dando vueltas mientras espera (traduciendo, la IA pensando…). */
    private val loader = DiamondView(context, loader = true)
    /** 🏁 El diamante grande de las celebraciones (terminaste tu día, llegaste a 40 horas). */
    private val party = DiamondView(context)

    init {
        PanelUi.glowScrollbar(scroll)
        orientation = VERTICAL
        setPadding(d(6f), d(6f), d(6f), d(4f))
        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        close.setOnClickListener { onFeedback?.invoke(); onClose?.invoke() }
        row.addView(close, LayoutParams(d(40f), d(36f)))
        title.textSize = 15f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(10f), 0, d(8f), 0)
        row.addView(title, LayoutParams(0, -2, 1f))
        langRow.orientation = HORIZONTAL
        langRow.gravity = Gravity.CENTER_VERTICAL
        langRow.visibility = GONE
        langRow.setPadding(d(8f), 0, 0, 0)
        row.addView(langRow, LayoutParams(0, -2, 1f))
        loader.visibility = GONE
        row.addView(loader, LayoutParams(d(30f), d(30f)).apply { rightMargin = d(8f) })
        addView(row, LayoutParams(-1, -2))
        party.visibility = GONE
        addView(party, LayoutParams(d(84f), d(84f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = d(4f) })
        body.textSize = 15f
        body.setLineSpacing(d(2f).toFloat(), 1f)
        body.setPadding(d(14f), d(10f), d(14f), d(10f))
        body.setTextIsSelectable(false)
        scroll.addView(body, LayoutParams(-1, -2))
        addView(scroll, LayoutParams(-1, -2).apply { topMargin = d(6f) })
        chips.orientation = HORIZONTAL
        chips.setPadding(0, d(6f), 0, d(2f))
        val hs = HorizontalScrollView(context)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(chips)
        addView(hs, LayoutParams(-1, -2))
        applyPalette(palette)
    }

    /** Muestra [text] con su título y botones ([buttons] = nombre → acción; el primero se pinta azul). */
    /** 🏁 Como [show], pero con el diamante grande y su luz (para celebrar algo). */
    fun celebrate(heading: String, text: String, buttons: List<Pair<String, () -> Unit>>) {
        show(heading, text, buttons)
        party.visibility = VISIBLE
        party.restart(250L)
    }

    fun show(heading: String, text: String, buttons: List<Pair<String, () -> Unit>>) {
        party.visibility = GONE
        langRow.visibility = GONE
        title.visibility = VISIBLE
        Emo.apply(title, heading, PanelUi.icon(palette), 17f) // sin emojis: ícono pequeño
        // Los emojis al principio de cada renglón (los nuestros) se quitan; lo demás queda igual
        body.text = if (keepEmojis) text else text.lines().joinToString("\n") { ln -> Emo.split(ln).second.let { r -> if (r.isEmpty()) ln else r } }
        // Si está esperando (sin botones y el texto termina en «…»), sale el diamante con su luz
        loader.visibility = if (buttons.size <= 1 && text.trim().endsWith("…")) VISIBLE else GONE // también con «Cancelar»
        if (loader.visibility == VISIBLE) loader.invalidate()
        scroll.visibility = if (text.isEmpty()) GONE else VISIBLE
        scroll.scrollTo(0, 0)
        chips.removeAllViews()
        for ((i, b) in buttons.withIndex()) {
            val c = TextView(context)
            c.textSize = 14f
            c.gravity = Gravity.CENTER
            c.setPadding(d(16f), d(8f), d(16f), d(8f))
            if (i == 0) {
                c.setTypeface(c.typeface, Typeface.BOLD)
                c.setTextColor(palette.onAccent)
                c.background = PanelUi.pressable(palette.accent, palette.accentPressed, d(18f).toFloat())
            } else {
                c.setTextColor(palette.text)
                c.background = PanelUi.pressable(palette.key, palette.keyPressed, d(18f).toFloat())
            }
            Emo.apply(c, b.first, if (i == 0) palette.onAccent else PanelUi.icon(palette), 15f)
            c.setOnClickListener { onFeedback?.invoke(); b.second() }
            chips.addView(c, LayoutParams(-2, -2).apply { rightMargin = d(8f) })
        }
        visibility = VISIBLE
    }

    /**
     * En vez del título, dos idiomas que se tocan para cambiarlos: [Chino ▾] ⇄ [Español ▾].
     * Se llama después de [show].
     */
    fun showLangs(from: String, to: String, onFrom: () -> Unit, onTo: () -> Unit, onSwap: (() -> Unit)?) {
        langRow.removeAllViews()
        fun pill(label: String, act: () -> Unit) = TextView(context).apply {
            text = label + "  ▾"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.text)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(d(12f), d(7f), d(10f), d(7f))
            background = PanelUi.pressable(palette.key, palette.keyPressed, d(16f).toFloat())
            setOnClickListener { onFeedback?.invoke(); act() }
        }
        langRow.addView(pill(from, onFrom), LayoutParams(0, -2, 1f))
        val sw = TextView(context).apply {
            text = "⇄"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(if (onSwap != null) palette.text else PanelUi.icon(palette))
            if (onSwap != null) {
                background = PanelUi.pressable(palette.special, palette.keyPressed, d(16f).toFloat())
                setOnClickListener { v -> onFeedback?.invoke(); v.animate().rotationBy(180f).setDuration(260L).withEndAction { onSwap() }.start() }
            }
        }
        langRow.addView(sw, LayoutParams(d(36f), d(32f)).apply { leftMargin = d(6f); rightMargin = d(6f) })
        langRow.addView(pill(to, onTo), LayoutParams(0, -2, 1f))
        title.visibility = GONE
        langRow.visibility = VISIBLE
    }

    /**
     * ✨ El destello de la traducción: el texto aparece suave (sube un poquito mientras se aclara)
     * y una luz del color de tu tema le da una vuelta completa al borde de la tarjeta, como la barrita de colores.
     */
    fun shine() {
        body.alpha = 0f
        body.translationY = d(6f).toFloat()
        body.animate().alpha(1f).translationY(0f).setDuration(380L).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        val w = scroll.width; val h = scroll.height
        if (w <= 0 || h <= 0) { scroll.post { if (scroll.width > 0) runComet() }; return }
        runComet()
    }

    private fun runComet() {
        shineFx?.let { scroll.overlay.remove(it) }
        val fx = CometFx(palette.accent, d(14f).toFloat(), d(1f).toFloat())
        fx.setBounds(0, 0, scroll.width, scroll.height)
        scroll.overlay.add(fx)
        shineFx = fx
        val a = android.animation.ValueAnimator.ofFloat(0f, 1f)
        a.duration = 1500L
        a.startDelay = 120L
        a.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        a.addUpdateListener { fx.t = it.animatedValue as Float; fx.invalidateSelf(); scroll.invalidate() }
        a.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(an: android.animation.Animator) { scroll.overlay.remove(fx); scroll.invalidate(); if (shineFx === fx) shineFx = null }
        })
        a.start()
    }
    private var shineFx: CometFx? = null

    /** Una luz con cola que recorre el borde redondeado una vez y se apaga al final. */
    private class CometFx(val color: Int, val radius: Float, val dp: Float) : android.graphics.drawable.Drawable() {
        var t = 0f
        private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND
        }
        private val path = android.graphics.Path()
        private val seg = android.graphics.Path()
        private var pm: android.graphics.PathMeasure? = null
        private val pos = FloatArray(2)

        override fun onBoundsChange(b: android.graphics.Rect) {
            path.reset()
            val r = android.graphics.RectF(b).apply { inset(dp * 1.2f, dp * 1.2f) }
            path.addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW)
            pm = android.graphics.PathMeasure(path, true)
        }

        override fun draw(c: android.graphics.Canvas) {
            val m = pm ?: return
            val len = m.length
            if (len <= 0f) return
            // Se enciende al empezar y se apaga al terminar la vuelta
            val fade = (minOf(t / 0.12f, (1f - t) / 0.18f)).coerceIn(0f, 1f)
            val tail = len * 0.28f
            val head = len * 0.15f + (len + tail) * t
            val pieces = 18
            for (i in 0 until pieces) {
                val a0 = head - tail * (i + 1) / pieces
                val a1 = head - tail * i / pieces
                val k = 1f - i / pieces.toFloat()
                p.color = color
                p.alpha = (230 * k * k * fade).toInt()
                p.strokeWidth = dp * (1.2f + 1.8f * k)
                drawPart(c, m, len, a0, a1)
            }
            // La punta brilla blanca
            val hp = ((head % len) + len) % len
            m.getPosTan(hp, pos, null)
            p.style = android.graphics.Paint.Style.FILL
            p.color = color; p.alpha = (90 * fade).toInt()
            c.drawCircle(pos[0], pos[1], dp * 6f, p)
            p.color = android.graphics.Color.WHITE; p.alpha = (255 * fade).toInt()
            c.drawCircle(pos[0], pos[1], dp * 2.2f, p)
            p.style = android.graphics.Paint.Style.STROKE
        }

        private fun drawPart(c: android.graphics.Canvas, m: android.graphics.PathMeasure, len: Float, from: Float, to: Float) {
            var a = ((from % len) + len) % len
            var b = a + (to - from)
            seg.reset()
            if (b <= len) m.getSegment(a, b, seg, true)
            else { m.getSegment(a, len, seg, true); m.getSegment(0f, b - len, seg, true) }
            c.drawPath(seg, p)
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }

    /** Lo que muestran como resultado (traducciones, lo copiado…): eso no se toca, puede tener tus emojis. */
    var keepEmojis = false

    fun applyPalette(p: Palette) {
        palette = p
        background = PanelUi.barBackground(context, p) // 🎨 ya no gris
        title.setTextColor(p.text)
        close.color = p.text
        close.background = PanelUi.pressable(p.special, p.keyPressed, d(17f).toFloat())
        body.setTextColor(p.text)
        body.background = PanelUi.rounded(p.key, d(14f).toFloat())
    }
}
