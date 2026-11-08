package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 🇨🇳 Extras del chino:
 *  - 联想: lo que suele seguir después de lo que elegiste (你 → 好, 们…).
 *  - 拼音 con tonos de cualquier texto chino (你好 → nǐ hǎo).
 */
object ZhExtras {
    @Volatile private var next: Map<String, List<String>>? = null
    @Volatile private var tone: Map<String, String>? = null

    fun load(ctx: Context) {
        if (next != null) return
        val app = ctx.applicationContext
        Thread {
            try {
                val m = HashMap<String, List<String>>(12000)
                app.assets.open("zh_next.txt").bufferedReader().forEachLine { l ->
                    val p = l.split('\t')
                    if (p.size >= 2) m[p[0]] = p.drop(1)
                }
                next = m
            } catch (_: Exception) { }
        }.start()
    }

    /** Lo que suele seguir después de [word] (primero mira las 2 últimas letras, después la última). */
    fun after(word: String): List<String> {
        val m = next ?: return emptyList()
        val out = LinkedHashSet<String>()
        if (word.length >= 2) m[word.takeLast(2)]?.let { out.addAll(it) }
        if (word.isNotEmpty()) m[word.takeLast(1)]?.let { out.addAll(it) }
        return out.take(16)
    }

    /** El pinyin con tonos (llamar en segundo plano: la primera vez lee el diccionario). */
    fun pinyin(ctx: Context, text: String): String {
        val t = tone ?: synchronized(this) {
            tone ?: HashMap<String, String>(50000).also { m ->
                try {
                    ctx.applicationContext.assets.open("zh_tone.txt").bufferedReader().forEachLine { l ->
                        val i = l.indexOf('\t'); if (i > 0) m[l.substring(0, i)] = l.substring(i + 1)
                    }
                } catch (_: Exception) { }
                tone = m
            }
        }
        val sb = StringBuilder()
        var i = 0
        var lastHan = false
        while (i < text.length) {
            var hit: String? = null
            var len = 0
            for (k in minOf(4, text.length - i) downTo 1) {
                val s = text.substring(i, i + k)
                val py = t[s]
                if (py != null) { hit = py; len = k; break }
            }
            if (hit != null) {
                if (lastHan) sb.append(' ')
                sb.append(hit)
                lastHan = true
                i += len
            } else {
                val ch = text[i]
                sb.append(when (ch) { '，' -> ", "; '。' -> ". "; '？' -> "? "; '！' -> "! "; '：' -> ": "; '；' -> "; "; '、' -> ", "; else -> ch.toString() })
                lastHan = false
                i++
            }
        }
        return sb.toString().replace(Regex(" +"), " ").trim()
    }

    fun hasChinese(t: String) = t.any { it.code in 0x4E00..0x9FFF }
}

/**
 * ✍️ 手写: escribir chino con el dedo. Google ML Kit reconoce el dibujo (la primera vez baja el modelo, ~20 MB;
 * después funciona sin internet). Arriba salen los caracteres; si sigues escribiendo después de una pausa,
 * se pone el primero solo (como en los teclados chinos).
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class HandPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onHandText(text: String)
        fun onHandDelete()
        fun onHandEnter()
        fun onHandSpace()
        fun onHandSymbols()
        fun onHandNextLayout()
        fun onHandFeedback()
    }

    var actions: Actions? = null
    private var pal = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)

    private val candRow = LinearLayout(context)
    private val candScroll = HorizontalScrollView(context)
    private val status = TextView(context)
    private val pad = InkView(context)
    private val bottom = LinearLayout(context)
    private val buttons = ArrayList<TextView>()
    private var cands: List<String> = emptyList()

    // ---- Reconocimiento
    private var recognizer: com.google.mlkit.vision.digitalink.DigitalInkRecognizer? = null
    private var model: com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel? = null
    private var ready = false
    private var downloading = false
    private val strokes = ArrayList<com.google.mlkit.vision.digitalink.Ink.Stroke>()
    private var cur: com.google.mlkit.vision.digitalink.Ink.Stroke.Builder? = null
    private var lastUp = 0L
    private val recognizeRun = Runnable { recognize() }

    init {
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        candScroll.isHorizontalScrollBarEnabled = false
        candScroll.overScrollMode = OVER_SCROLL_NEVER
        candRow.orientation = LinearLayout.HORIZONTAL
        candRow.gravity = Gravity.CENTER_VERTICAL
        candScroll.addView(candRow, LayoutParams(-2, -1))
        val top = FrameLayout(context)
        top.addView(candScroll, LayoutParams(-1, -1))
        status.textSize = 13f
        status.gravity = Gravity.CENTER
        top.addView(status, LayoutParams(-1, -1))
        col.addView(top, LinearLayout.LayoutParams(-1, d(46f)))
        col.addView(pad, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(d(6f), d(2f), d(6f), d(4f)) })
        bottom.orientation = LinearLayout.HORIZONTAL
        for ((label, w) in listOf("符号" to 1.3f, "拼音" to 1.3f, "，" to 1f, "空格" to 3f, "。" to 1f, "⌫" to 1.3f, "↵" to 1.4f)) {
            val t = TextView(context)
            t.text = label
            t.textSize = if (label.length > 1) 15f else 19f
            t.gravity = Gravity.CENTER
            t.setOnClickListener { actions?.onHandFeedback(); press(label) }
            if (label == "⌫") t.setOnLongClickListener { clearInk(); true }
            buttons.add(t)
            bottom.addView(t, LinearLayout.LayoutParams(0, -1, w).apply { setMargins(d(3f), d(3f), d(3f), d(5f)) })
        }
        col.addView(bottom, LinearLayout.LayoutParams(-1, d(50f)))
        addView(col, LayoutParams(-1, -1))
        pad.onStroke = { ev -> onInk(ev) }
        applyPalette(pal)
    }

    private fun press(label: String) {
        when (label) {
            "符号" -> { clearInk(); actions?.onHandSymbols() }
            "拼音" -> { clearInk(); actions?.onHandNextLayout() }
            "空格" -> if (cands.isNotEmpty()) pick(0) else actions?.onHandSpace()
            "⌫" -> if (strokes.isNotEmpty()) { strokes.removeAt(strokes.size - 1); pad.undoLast(); if (strokes.isEmpty()) showCands(emptyList()) else post(recognizeRun) } else actions?.onHandDelete()
            "↵" -> { if (cands.isNotEmpty()) pick(0) else actions?.onHandEnter() }
            else -> { if (cands.isNotEmpty()) pick(0); actions?.onHandText(label) }
        }
    }

    fun open() {
        clearInk()
        prepare()
    }

    private fun prepare() {
        if (ready || downloading) return
        try {
            val id = com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hani-CN") ?: return
            val m = com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel.builder(id).build()
            model = m
            downloading = true
            setStatus(L.t("Preparando la escritura a mano… (la primera vez baja unos 20 MB)", "Getting handwriting ready… (the first time it downloads about 20 MB)"))
            com.google.mlkit.common.model.RemoteModelManager.getInstance()
                .download(m, com.google.mlkit.common.model.DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    recognizer = com.google.mlkit.vision.digitalink.DigitalInkRecognition.getClient(
                        com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions.builder(m).build())
                    ready = true; downloading = false
                    setStatus(if (strokes.isEmpty()) L.t("Escribe aquí con el dedo", "Write here with your finger") else "")
                    if (strokes.isNotEmpty()) recognize()
                }
                .addOnFailureListener {
                    downloading = false
                    setStatus(L.t("No se pudo bajar. Conéctate a internet una vez.", "Couldn't download. Connect to the internet once."))
                }
        } catch (_: Throwable) {
            downloading = false
            setStatus(L.t("La escritura a mano no está disponible en este teléfono.", "Handwriting isn't available on this phone."))
        }
    }

    private fun setStatus(s: String) {
        status.text = s
        status.visibility = if (s.isEmpty() || cands.isNotEmpty()) GONE else VISIBLE
    }

    private fun onInk(e: MotionEvent) {
        val t = System.currentTimeMillis()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                removeCallbacks(recognizeRun)
                // Empezaste otro carácter después de una pausa: se pone el primero
                if (cands.isNotEmpty() && strokes.isNotEmpty() && t - lastUp > 900L) pick(0)
                cur = com.google.mlkit.vision.digitalink.Ink.Stroke.builder().also {
                    it.addPoint(com.google.mlkit.vision.digitalink.Ink.Point.create(e.x, e.y, t))
                }
            }
            MotionEvent.ACTION_MOVE -> cur?.addPoint(com.google.mlkit.vision.digitalink.Ink.Point.create(e.x, e.y, t))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cur?.let { it.addPoint(com.google.mlkit.vision.digitalink.Ink.Point.create(e.x, e.y, t)); strokes.add(it.build()) }
                cur = null
                lastUp = t
                postDelayed(recognizeRun, 280L)
            }
        }
    }

    private fun recognize() {
        val r = recognizer
        if (r == null) { prepare(); return }
        if (strokes.isEmpty()) return
        val b = com.google.mlkit.vision.digitalink.Ink.builder()
        for (s in strokes) b.addStroke(s)
        val n = strokes.size
        r.recognize(b.build()).addOnSuccessListener { res ->
            if (strokes.size != n) return@addOnSuccessListener
            showCands(res.candidates.map { it.text }.filter { it.isNotBlank() }.distinct().take(12))
        }
    }

    private fun showCands(list: List<String>) {
        cands = list
        candRow.removeAllViews()
        val cellW = (width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) / 4
        list.forEachIndexed { i, w ->
            val t = TextView(context)
            t.text = w
            t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, d(46f) * 0.42f)
            t.typeface = if (i == 0) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            t.setTextColor(pal.text)
            t.gravity = Gravity.CENTER
            t.background = PanelUi.pressable(0, pal.keyPressed, d(10f).toFloat())
            t.setOnClickListener { actions?.onHandFeedback(); pick(i) }
            t.measure(0, 0)
            candRow.addView(t, LinearLayout.LayoutParams(maxOf(cellW, t.measuredWidth + d(16f)), -1))
        }
        candScroll.scrollTo(0, 0)
        status.visibility = if (list.isEmpty() && strokes.isEmpty()) VISIBLE else GONE
    }

    private fun pick(i: Int) {
        val w = cands.getOrNull(i) ?: return
        actions?.onHandText(w)
        clearInk()
    }

    private fun clearInk() {
        removeCallbacks(recognizeRun)
        strokes.clear()
        cur = null
        pad.clear()
        showCands(emptyList())
        setStatus(if (ready) L.t("Escribe aquí con el dedo", "Write here with your finger") else status.text.toString())
    }

    fun applyPalette(p: Palette) {
        pal = p
        setBackgroundColor(p.bg)
        status.setTextColor(p.hint)
        pad.ink = p.accent
        pad.background = PanelUi.rounded(p.key, d(16f).toFloat())
        val r = d(10f).toFloat()
        for (b in buttons) {
            val special = b.text.length > 1 || b.text == "⌫" || b.text == "↵"
            if (b.text == "↵") { b.setTextColor(p.onAccent); b.background = PanelUi.pressable(p.accent, p.accentPressed, r) }
            else { b.setTextColor(p.text); b.background = PanelUi.pressable(if (special) p.special else p.key, p.keyPressed, r) }
        }
    }

    /** El papel donde escribes: dibuja tus trazos con el color del tema. */
    private class InkView(ctx: Context) : View(ctx) {
        var ink = 0xFF22D3EE.toInt()
        var onStroke: ((MotionEvent) -> Unit)? = null
        private val paths = ArrayList<Path>()
        private var cur: Path? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            strokeWidth = ctx.resources.displayMetrics.density * 5f
        }
        fun clear() { paths.clear(); cur = null; invalidate() }
        fun undoLast() { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1); invalidate() }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { cur = Path().apply { moveTo(e.x, e.y) }; paths.add(cur!!) }
                MotionEvent.ACTION_MOVE -> cur?.lineTo(e.x, e.y)
            }
            onStroke?.invoke(e)
            invalidate()
            return true
        }
        override fun onDraw(c: Canvas) {
            paint.color = ink
            for (p in paths) c.drawPath(p, paint)
        }
    }
}
