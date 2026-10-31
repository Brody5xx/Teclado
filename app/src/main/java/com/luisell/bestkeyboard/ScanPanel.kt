package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Escáner DENTRO del teclado (como Gboard): cámara en vivo → foto → las palabras se marcan →
 * eliges con el dedo → Insertar. El texto se lee dentro del teléfono, sin internet.
 */
@SuppressLint("ViewConstructor")
class ScanPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        fun onScanInsert(text: String)
        fun onScanClose()
        fun onScanGallery()
        fun onScanFeedback()
    }

    var actions: Actions? = null
    private fun d(v: Float) = PanelUi.dp(context, v)
    private val main = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { main.post(it) }
    private val worker = Executors.newSingleThreadExecutor()

    private val preview = PreviewView(context)
    private val photo = WordsView(context)
    private val hint = TextView(context)
    private val shutter = View(context)
    private val gallery = GalleryIcon(context)
    private val closeBtn = IconView(context, Icons.CLOSE, 7.5f)
    private val backBtn = IconView(context, Icons.BACK, 8.5f)
    private val resultBar = LinearLayout(context)
    private val resultText = TextView(context)
    private val insertBtn = TextView(context)

    private var owner: PanelLifecycle? = null
    private var provider: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null
    private var busy = false
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    init {
        setBackgroundColor(0xFF000000.toInt())
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        preview.scaleType = PreviewView.ScaleType.FILL_CENTER
        addView(preview, LayoutParams(-1, -1))
        photo.visibility = GONE
        photo.onSelection = { updateResult() }
        addView(photo, LayoutParams(-1, -1))

        fun circle(v: IconView) {
            v.color = 0xFFFFFFFF.toInt()
            v.background = PanelUi.pressable(0x99303134.toInt(), 0xCC505257.toInt(), d(24f).toFloat())
        }
        circle(closeBtn)
        closeBtn.setOnClickListener { actions?.onScanFeedback(); actions?.onScanClose() }
        addView(closeBtn, LayoutParams(d(48f), d(48f), Gravity.TOP or Gravity.END).apply { setMargins(0, d(10f), d(10f), 0) })
        circle(backBtn)
        backBtn.visibility = GONE
        backBtn.setOnClickListener { actions?.onScanFeedback(); retake() }
        addView(backBtn, LayoutParams(d(48f), d(48f), Gravity.TOP or Gravity.START).apply { setMargins(d(10f), d(10f), 0, 0) })

        hint.textSize = 15f
        hint.setTextColor(0xFFFFFFFF.toInt())
        hint.setPadding(d(16f), d(8f), d(16f), d(8f))
        hint.background = PanelUi.rounded(0xAA202124.toInt(), d(20f).toFloat())
        addView(hint, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(100f) })

        // Botón de tomar foto (círculo blanco)
        shutter.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0x33FFFFFF)
            setStroke(d(4f), 0xFFFFFFFF.toInt())
        }
        shutter.setOnClickListener { takePhoto() }
        addView(shutter, LayoutParams(d(72f), d(72f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(18f) })

        gallery.setOnClickListener { actions?.onScanFeedback(); actions?.onScanGallery() }
        addView(gallery, LayoutParams(d(52f), d(52f), Gravity.BOTTOM or Gravity.START).apply { setMargins(d(24f), 0, 0, d(28f)) })

        // Barra de resultado: texto elegido + Insertar
        resultBar.orientation = LinearLayout.HORIZONTAL
        resultBar.gravity = Gravity.CENTER_VERTICAL
        resultBar.setPadding(d(16f), d(10f), d(12f), d(10f))
        resultBar.background = PanelUi.rounded(0xF0202124.toInt(), d(18f).toFloat())
        resultText.textSize = 16f
        resultText.setTextColor(0xFFFFFFFF.toInt())
        resultText.maxLines = 2
        resultText.ellipsize = TextUtils.TruncateAt.END
        resultBar.addView(resultText, LinearLayout.LayoutParams(0, -2, 1f))
        L.bind(insertBtn, "Insertar  ↑", "Insert  ↑")
        insertBtn.textSize = 16f
        insertBtn.gravity = Gravity.CENTER
        insertBtn.setPadding(d(20f), d(12f), d(20f), d(12f))
        insertBtn.setOnClickListener { insert() }
        resultBar.addView(insertBtn, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = d(10f) })
        resultBar.visibility = GONE
        addView(resultBar, LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(d(8f), 0, d(8f), d(8f)) })
    }

    fun applyPalette(p: Palette) {
        insertBtn.setTextColor(p.onAccent)
        insertBtn.background = PanelUi.pressable(p.accent, p.accentPressed, d(24f).toFloat())
        photo.accent = p.accent
    }

    // ---------------------------------------------------------------- Cámara

    fun open() {
        retake()
        startCamera()
    }

    fun close() {
        stopCamera()
        photo.clear()
    }

    /** Abre el escáner directamente con una foto (de la galería), sin cámara. */
    fun openWithPhoto(bmp: Bitmap) {
        stopCamera()
        busy = true
        resultBar.visibility = GONE
        showPhoto(bmp)
    }

    private fun startCamera() {
        if (owner != null) return
        val o = PanelLifecycle()
        owner = o
        o.start()
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (owner !== o) return@addListener
            try {
                val p = future.get()
                provider = p
                val pv = Preview.Builder().build()
                pv.setSurfaceProvider(preview.surfaceProvider)
                val ic = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                capture = ic
                p.unbindAll()
                p.bindToLifecycle(o, CameraSelector.DEFAULT_BACK_CAMERA, pv, ic)
            } catch (_: Exception) {
                hint.text = L.t("No se pudo abrir la cámara. Toca el botón de galería para elegir una foto.", "Couldn't open the camera. Tap the gallery button to pick a photo.")
            }
        }, mainExecutor)
    }

    private fun stopCamera() {
        try { provider?.unbindAll() } catch (_: Exception) { }
        owner?.stop()
        owner = null
        capture = null
    }

    private fun retake() {
        busy = false
        photo.clear()
        photo.visibility = GONE
        preview.visibility = VISIBLE
        resultBar.visibility = GONE
        backBtn.visibility = GONE
        shutter.visibility = VISIBLE
        gallery.visibility = VISIBLE
        hint.visibility = VISIBLE
        hint.text = L.t("Toma una foto de las palabras", "Take a photo of words to scan")
        if (owner == null && isShown) startCamera()
    }

    private fun takePhoto() {
        val ic = capture ?: return
        if (busy) return
        busy = true
        actions?.onScanFeedback()
        hint.text = L.t("Leyendo el texto…", "Reading the text…")
        ic.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = try { toBitmap(image) } catch (_: Throwable) { null } finally { image.close() }
                main.post {
                    if (bmp == null) { busy = false; hint.text = L.t("No se pudo tomar la foto. Inténtalo otra vez.", "Couldn't take the photo. Try again."); return@post }
                    showPhoto(bmp)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                main.post { busy = false; hint.text = L.t("No se pudo tomar la foto. Inténtalo otra vez.", "Couldn't take the photo. Try again.") }
            }
        })
    }

    /** Foto → imagen derecha y de tamaño razonable (más rápida de leer). */
    private fun toBitmap(image: ImageProxy): Bitmap {
        var b = image.toBitmap()
        val maxSide = 2000f
        val scale = minOf(1f, maxSide / maxOf(b.width, b.height))
        val rot = image.imageInfo.rotationDegrees
        if (scale < 1f || rot != 0) {
            val m = Matrix()
            if (scale < 1f) m.postScale(scale, scale)
            if (rot != 0) m.postRotate(rot.toFloat())
            b = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        }
        return b
    }

    /** Muestra una foto (de la cámara o de la galería) y busca las palabras. */
    fun showPhoto(bmp: Bitmap) {
        stopCamera()
        preview.visibility = GONE
        shutter.visibility = GONE
        gallery.visibility = GONE
        backBtn.visibility = VISIBLE
        photo.setPhoto(bmp)
        photo.visibility = VISIBLE
        hint.visibility = VISIBLE
        hint.text = L.t("Leyendo el texto…", "Reading the text…")
        recognizer.process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { result ->
                val words = ArrayList<WordsView.Word>()
                var line = 0
                for (block in result.textBlocks) for (l in block.lines) {
                    for (e in l.elements) {
                        val r = e.boundingBox ?: continue
                        words.add(WordsView.Word(e.text, RectF(r), line))
                    }
                    line++
                }
                busy = false
                if (words.isEmpty()) {
                    hint.text = L.t("No encontré texto. Prueba con más luz o más cerca.", "No text found. Try more light or get closer.")
                    return@addOnSuccessListener
                }
                hint.visibility = GONE
                photo.setWords(words)
                resultBar.visibility = VISIBLE
                updateResult()
            }
            .addOnFailureListener {
                busy = false
                hint.text = L.t("No se pudo leer el texto. Inténtalo otra vez.", "Couldn't read the text. Try again.")
            }
    }

    private fun updateResult() {
        val t = photo.selectedText()
        resultText.text = if (t.isEmpty()) L.t("Desliza el dedo sobre las palabras", "Slide your finger over the words") else t.replace('\n', ' ')
        insertBtn.alpha = if (t.isEmpty()) 0.5f else 1f
    }

    private fun insert() {
        val t = photo.selectedText()
        if (t.isEmpty()) return
        actions?.onScanFeedback()
        actions?.onScanInsert(t)
    }

    /** Botón moderno de galería: marco de foto con montaña y sol, dibujado a mano (sin emoji). */
    private class GalleryIcon(context: Context) : View(context) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val r = RectF()
        private val path = android.graphics.Path()
        init { isClickable = true }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val dd = resources.displayMetrics.density
            // Fondo: círculo de vidrio oscuro con borde fino
            p.style = Paint.Style.FILL
            p.color = if (isPressed) 0xCC505257.toInt() else 0x66000000
            c.drawCircle(w / 2, h / 2, minOf(w, h) / 2, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.2f * dd
            p.color = 0x66FFFFFF
            c.drawCircle(w / 2, h / 2, minOf(w, h) / 2 - p.strokeWidth, p)
            // Marco de la foto
            val s = minOf(w, h) * 0.24f
            r.set(w / 2 - s, h / 2 - s * 0.8f, w / 2 + s, h / 2 + s * 0.8f)
            p.color = 0xFFFFFFFF.toInt()
            p.strokeWidth = 2f * dd
            p.strokeJoin = Paint.Join.ROUND
            c.drawRoundRect(r, s * 0.25f, s * 0.25f, p)
            // Montañas
            p.style = Paint.Style.FILL
            path.reset()
            path.moveTo(r.left + s * 0.2f, r.bottom - s * 0.2f)
            path.lineTo(r.left + s * 0.75f, r.top + s * 0.75f)
            path.lineTo(r.left + s * 1.15f, r.bottom - s * 0.55f)
            path.lineTo(r.left + s * 1.4f, r.top + s * 1.0f)
            path.lineTo(r.right - s * 0.2f, r.bottom - s * 0.2f)
            path.close()
            c.drawPath(path, p)
            // Sol
            c.drawCircle(r.right - s * 0.5f, r.top + s * 0.5f, s * 0.17f, p)
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            invalidate()
        }
    }

    /** "Vida" mínima que la cámara necesita para saber cuándo encender y apagar. */
    private class PanelLifecycle : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun start() {
            registry.currentState = Lifecycle.State.CREATED
            registry.currentState = Lifecycle.State.RESUMED
        }
        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    /**
     * La foto con las palabras marcadas. Desliza el dedo de una palabra a otra para elegir;
     * toca una palabra para elegir solo esa.
     */
    class WordsView(context: Context) : View(context) {
        class Word(val text: String, val box: RectF, val line: Int)

        var onSelection: (() -> Unit)? = null
        var accent = 0xFF8AB4F8.toInt()
        private var bmp: Bitmap? = null
        private var words: List<Word> = emptyList()
        private var selA = -1
        private var selB = -1
        private var anchor = -1
        /** Qué gotita estás arrastrando: 0 = ninguna, 1 = la del inicio, 2 = la del final. */
        private var dragHandle = 0
        private val handleR get() = 11f * dens
        private val startPt = android.graphics.PointF()
        private val endPt = android.graphics.PointF()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val dst = RectF()
        private val tmp = RectF()
        private var scale = 1f
        private var offX = 0f
        private var offY = 0f
        private val dens = resources.displayMetrics.density

        fun setPhoto(b: Bitmap) { bmp = b; words = emptyList(); selA = -1; selB = -1; invalidate() }

        fun setWords(w: List<Word>) {
            words = w
            // Al principio todo queda elegido; desliza el dedo para elegir menos
            selA = 0
            selB = w.size - 1
            invalidate()
            onSelection?.invoke()
        }

        fun clear() { bmp = null; words = emptyList(); selA = -1; selB = -1; invalidate() }

        fun selectedText(): String {
            if (selA < 0 || selB < 0 || words.isEmpty()) return ""
            val sb = StringBuilder()
            for (i in selA..selB) {
                if (i > selA) sb.append(if (words[i].line != words[i - 1].line) '\n' else ' ')
                sb.append(words[i].text)
            }
            return sb.toString()
        }

        private fun layoutPhoto() {
            val b = bmp ?: return
            scale = minOf(width / b.width.toFloat(), height / b.height.toFloat())
            offX = (width - b.width * scale) / 2f
            offY = (height - b.height * scale) / 2f
        }

        override fun onDraw(c: Canvas) {
            val b = bmp ?: return
            layoutPhoto()
            dst.set(offX, offY, offX + b.width * scale, offY + b.height * scale)
            paint.color = 0xFF000000.toInt()
            c.drawBitmap(b, null, dst, paint)
            if (words.isEmpty()) return
            paint.color = 0x55000000
            c.drawRect(dst, paint)
            val pad = 2f * dens
            for ((i, w) in words.withIndex()) {
                toScreen(w.box, tmp)
                tmp.inset(-pad, -pad)
                val sel = i in selA..selB
                paint.color = if (sel) (accent and 0x00FFFFFF) or 0x77000000 else 0x55FFFFFF
                c.drawRoundRect(tmp, 4f * dens, 4f * dens, paint)
            }
            drawHandles(c)
        }

        /** Las dos gotitas azules: al principio y al final de lo elegido. */
        private fun drawHandles(c: Canvas) {
            if (selA < 0 || selB < 0 || selA >= words.size || selB >= words.size) return
            val pad = 2f * dens
            toScreen(words[selA].box, tmp)
            startPt.set(tmp.left - pad, tmp.bottom + pad)
            toScreen(words[selB].box, tmp)
            endPt.set(tmp.right + pad, tmp.bottom + pad)
            val r = handleR
            paint.color = accent
            // Inicio: gotita colgando hacia la izquierda, con la punta en la esquina de la palabra
            c.drawCircle(startPt.x - r, startPt.y + r, r, paint)
            c.drawRect(startPt.x - r, startPt.y, startPt.x, startPt.y + r, paint)
            // Final: gotita colgando hacia la derecha
            c.drawCircle(endPt.x + r, endPt.y + r, r, paint)
            c.drawRect(endPt.x, endPt.y, endPt.x + r, endPt.y + r, paint)
        }

        /** ¿Tocaste una gotita? (zona generosa para el dedo) */
        private fun handleAt(x: Float, y: Float): Int {
            if (selA < 0 || selB < 0) return 0
            val r = handleR
            val reach = 30f * dens
            fun near(cx: Float, cy: Float): Float {
                val dx = x - cx
                val dy = y - cy
                return dx * dx + dy * dy
            }
            val ds = near(startPt.x - r, startPt.y + r)
            val de = near(endPt.x + r, endPt.y + r)
            val lim = reach * reach
            return when {
                ds <= lim && ds <= de -> 1
                de <= lim -> 2
                else -> 0
            }
        }

        private fun toScreen(r: RectF, out: RectF) {
            out.set(offX + r.left * scale, offY + r.top * scale, offX + r.right * scale, offY + r.bottom * scale)
        }

        /** La palabra bajo el dedo (o la más cercana si no tocas ninguna exacta). */
        private fun wordAt(x: Float, y: Float): Int {
            var best = -1
            var bestD = Float.MAX_VALUE
            for ((i, w) in words.withIndex()) {
                toScreen(w.box, tmp)
                if (tmp.contains(x, y)) return i
                val dx = maxOf(tmp.left - x, 0f, x - tmp.right)
                val dy = maxOf(tmp.top - y, 0f, y - tmp.bottom)
                val dd = dx * dx + dy * dy
                if (dd < bestD) { bestD = dd; best = i }
            }
            return if (bestD < (40f * dens) * (40f * dens)) best else -1
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (words.isEmpty()) return true
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragHandle = handleAt(e.x, e.y)
                    if (dragHandle != 0) return true
                    anchor = wordAt(e.x, e.y)
                    if (anchor >= 0) { selA = anchor; selB = anchor; invalidate(); onSelection?.invoke() }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragHandle != 0) {
                        // Mueve la gotita: el otro extremo se queda fijo
                        val i = wordAt(e.x, e.y - handleR * 2)
                        if (i >= 0) {
                            var a = selA
                            var b = selB
                            if (dragHandle == 1) a = i else b = i
                            if (a > b) {
                                val t = a; a = b; b = t
                                dragHandle = if (dragHandle == 1) 2 else 1
                            }
                            if (a != selA || b != selB) { selA = a; selB = b; invalidate(); onSelection?.invoke() }
                        }
                        return true
                    }
                    if (anchor < 0) anchor = wordAt(e.x, e.y)
                    val i = wordAt(e.x, e.y)
                    if (anchor >= 0 && i >= 0) {
                        val a = minOf(anchor, i)
                        val b = maxOf(anchor, i)
                        if (a != selA || b != selB) { selA = a; selB = b; invalidate(); onSelection?.invoke() }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { anchor = -1; dragHandle = 0 }
            }
            return true
        }
    }
}
