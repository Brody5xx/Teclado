package com.luisell.bestkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 🖼 Tu captura de pantalla dentro del teclado: lee el texto y te deja tocar los renglones
 * que quieres traducir (se marcan del color de tu tema). «Todo» marca todos.
 */
class ShotPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onShotTranslate(text: String)
        fun onShotBack()
        fun onShotGallery()
        fun onShotFeedback()
    }

    var actions: Actions? = null
    private var pal = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private class Line(val text: String, val box: Rect) { var on = false }

    private val lines = ArrayList<Line>()
    private var bmp: Bitmap? = null
    private var seq = 0

    private val col = LinearLayout(context)
    private val backBtn = IconView(context, Icons.BACK, 7.5f)
    private val title = TextView(context)
    private val allBtn = TextView(context)
    private val galBtn = TextView(context)
    private val scroll = ScrollView(context)
    private val shot = ShotView()
    private val msg = TextView(context)
    private val go = TextView(context)

    init {
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(d(6f), d(4f), d(6f), d(6f))
        val top = LinearLayout(context)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        backBtn.setOnClickListener { actions?.onShotFeedback(); actions?.onShotBack() }
        top.addView(backBtn, LinearLayout.LayoutParams(d(40f), d(34f)))
        title.textSize = 14f
        title.setTypeface(title.typeface, Typeface.BOLD)
        title.setPadding(d(10f), 0, d(6f), 0)
        title.maxLines = 2
        top.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        allBtn.textSize = 13f
        allBtn.gravity = Gravity.CENTER
        allBtn.setPadding(d(14f), d(7f), d(14f), d(7f))
        allBtn.setOnClickListener {
            actions?.onShotFeedback()
            val any = lines.any { !it.on }
            for (l in lines) l.on = any
            shot.invalidate(); refresh()
        }
        galBtn.textSize = 13f
        galBtn.gravity = Gravity.CENTER
        galBtn.setPadding(d(12f), d(7f), d(12f), d(7f))
        galBtn.setOnClickListener { actions?.onShotFeedback(); actions?.onShotGallery() }
        top.addView(galBtn, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = d(6f) })
        top.addView(allBtn, LinearLayout.LayoutParams(-2, -2))
        col.addView(top, LinearLayout.LayoutParams(-1, -2))

        val mid = FrameLayout(context)
        PanelUi.glowScrollbar(scroll)
        scroll.addView(shot, LayoutParams(-1, -2))
        mid.addView(scroll, LayoutParams(-1, -1))
        msg.textSize = 14f
        msg.gravity = Gravity.CENTER
        msg.setPadding(d(16f), d(16f), d(16f), d(16f))
        mid.addView(msg, LayoutParams(-1, -1))
        col.addView(mid, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = d(4f); bottomMargin = d(6f) })

        go.textSize = 15f
        go.gravity = Gravity.CENTER
        go.setTypeface(go.typeface, Typeface.BOLD)
        go.setPadding(d(16f), d(10f), d(16f), d(10f))
        go.setOnClickListener {
            val sel = lines.filter { it.on }
            if (sel.isEmpty()) return@setOnClickListener
            actions?.onShotFeedback()
            actions?.onShotTranslate(sel.joinToString("\n") { it.text })
        }
        col.addView(go, LinearLayout.LayoutParams(-1, -2))
        addView(col, LayoutParams(-1, -1))
        applyPalette(pal)
    }

    /** Abre la captura: la carga, lee el texto y lo deja listo para tocar. */
    fun open(s: Screenshots.Shot, clean: (String) -> String) {
        val my = ++seq
        lines.clear(); bmp = null
        title.text = L.t("Toca el texto que quieres traducir", "Tap the text you want to translate")
        allBtn.text = L.t("Todo", "All")
        galBtn.text = L.t("Galería", "Gallery")
        msg.text = L.t("Leyendo la imagen…", "Reading the image…")
        msg.visibility = View.VISIBLE
        scroll.visibility = View.INVISIBLE
        scroll.scrollTo(0, 0)
        refresh()
        Thread {
            val b = load(s)
            post {
                if (my != seq) return@post
                if (b == null) { msg.text = L.t("No se pudo abrir la imagen.", "Couldn't open the image."); return@post }
                bmp = b
                shot.requestLayout()
                com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
                    .process(com.google.mlkit.vision.common.InputImage.fromBitmap(b, 0))
                    .addOnSuccessListener { res ->
                        if (my != seq) return@addOnSuccessListener
                        for (bl in res.textBlocks) for (ln in bl.lines) {
                            val t = clean(ln.text)
                            val r = ln.boundingBox ?: continue
                            if (t.isNotEmpty()) lines.add(Line(t, r))
                        }
                        lines.sortWith(compareBy<Line> { it.box.centerY() / maxOf(1, d(14f)) }.thenBy { it.box.left })
                        msg.visibility = if (lines.isEmpty()) View.VISIBLE else View.GONE
                        if (lines.isEmpty()) msg.text = L.t("No encontré texto en la imagen.", "I didn't find text in the image.")
                        scroll.visibility = View.VISIBLE
                        shot.invalidate(); refresh()
                        // Empieza abajo: lo más nuevo de un chat suele estar abajo
                        scroll.post { scroll.scrollTo(0, maxOf(0, shot.height - scroll.height) * 2 / 3) }
                    }
                    .addOnFailureListener { if (my == seq) msg.text = L.t("No se pudo leer la imagen.", "Couldn't read the image.") }
            }
        }.start()
    }

    fun close() { seq++; bmp = null; lines.clear() }

    private fun load(s: Screenshots.Shot): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(s.uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (o.outWidth / (sample * 2) >= 1000) sample *= 2
        context.contentResolver.openInputStream(s.uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } catch (_: Throwable) { null }

    private fun refresh() {
        val n = lines.count { it.on }
        go.text = if (n == 0) L.t("Toca uno o más renglones", "Tap one or more lines") else L.t("Traducir", "Translate") + " ($n)"
        go.alpha = if (n == 0) 0.55f else 1f
        allBtn.text = if (lines.isNotEmpty() && lines.all { it.on }) L.t("Ninguno", "None") else L.t("Todo", "All")
    }

    /** La imagen con los renglones marcados. */
    private inner class ShotView : View(context) {
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val dim = Paint()
        private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
        private var downX = 0f; private var downY = 0f

        private fun scale(): Float = bmp?.let { width.toFloat() / it.width } ?: 1f

        override fun onMeasure(w: Int, h: Int) {
            val ww = MeasureSpec.getSize(w)
            val b = bmp
            setMeasuredDimension(ww, if (b == null) d(200f) else (b.height * ww.toFloat() / b.width).toInt())
        }

        override fun onDraw(c: Canvas) {
            val b = bmp ?: return
            val k = scale()
            c.save(); c.scale(k, k); c.drawBitmap(b, 0f, 0f, bp); c.restore()
            // Un velo suave para que resalte lo que se puede tocar
            dim.color = android.graphics.Color.argb(70, 0, 0, 0)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
            val pad = d(2f).toFloat(); val r = d(6f).toFloat()
            line.strokeWidth = d(1.2f).toFloat()
            for (l in lines) {
                val rf = RectF(l.box.left * k - pad, l.box.top * k - pad, l.box.right * k + pad, l.box.bottom * k + pad)
                if (l.on) {
                    fill.color = (pal.accent and 0x00FFFFFF) or (0x66 shl 24)
                    c.drawRoundRect(rf, r, r, fill)
                    line.color = pal.accent; line.alpha = 255
                } else {
                    fill.color = android.graphics.Color.argb(40, 255, 255, 255)
                    c.drawRoundRect(rf, r, r, fill)
                    line.color = pal.accent; line.alpha = 110
                }
                c.drawRoundRect(rf, r, r, line)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; return true }
                MotionEvent.ACTION_UP -> {
                    if (Math.abs(e.x - downX) > d(10f) || Math.abs(e.y - downY) > d(10f)) return true
                    val k = scale()
                    val slop = d(6f)
                    val hit = lines.minByOrNull { l ->
                        val bx = Rect((l.box.left * k).toInt() - slop, (l.box.top * k).toInt() - slop, (l.box.right * k).toInt() + slop, (l.box.bottom * k).toInt() + slop)
                        if (bx.contains(e.x.toInt(), e.y.toInt())) 0 else Int.MAX_VALUE
                    }?.takeIf { l ->
                        Rect((l.box.left * k).toInt() - slop, (l.box.top * k).toInt() - slop, (l.box.right * k).toInt() + slop, (l.box.bottom * k).toInt() + slop).contains(e.x.toInt(), e.y.toInt())
                    }
                    if (hit != null) { actions?.onShotFeedback(); hit.on = !hit.on; invalidate(); refresh() }
                    return true
                }
            }
            return true
        }
    }

    fun applyPalette(p: Palette) {
        pal = p
        background = PanelUi.barBackground(context, p)
        title.setTextColor(p.text)
        msg.setTextColor(p.text)
        backBtn.color = p.text
        backBtn.background = PanelUi.pressable(p.special, p.keyPressed, d(17f).toFloat())
        galBtn.setTextColor(p.text)
        galBtn.background = PanelUi.pressable(p.key, p.keyPressed, d(16f).toFloat())
        allBtn.setTextColor(p.text)
        allBtn.background = PanelUi.pressable(p.key, p.keyPressed, d(16f).toFloat())
        go.setTextColor(p.onAccent)
        go.background = PanelUi.pressable(p.accent, p.accentPressed, d(20f).toFloat())
        scroll.background = PanelUi.rounded(p.key, d(12f).toFloat())
        scroll.clipToOutline = true
        shot.invalidate()
    }
}
