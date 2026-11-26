package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * 📄 Modo referencia: tu captura de pantalla grande DENTRO del teclado, con el texto marcado.
 * Tocas un renglón y se escribe en el campo donde estás (sin salir de la app, sin perder lo que llevas).
 * «Teclado» te regresa a las teclas y la captura se queda guardada: arriba sale «Referencia» para volver a abrirla
 * donde la dejaste (mismo zoom). Pellizca para agrandar, toca dos veces para acercar.
 */
@SuppressLint("ViewConstructor")
class RefPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        /** Tocaste un renglón: escribirlo. */
        fun onRefText(text: String)
        /** «Teclado»: volver a las teclas (la referencia se queda). */
        fun onRefKeyboard()
        /** «Elegir palabras»: el escáner con la foto. */
        fun onRefPick(bmp: Bitmap)
        /** «Pegar imagen». */
        fun onRefPaste()
        /** ✕: ya no necesitas la referencia. */
        fun onRefClose()
        fun onRefFeedback()
    }

    var actions: Actions? = null
    private fun d(v: Float) = PanelUi.dp(context, v)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val zoomView = ZoomImageView(context)
    private val hint = TextView(context)
    private val zoomLabel = TextView(context)
    private val row = LinearLayout(context)
    private var palette = Palette.DARK
    private var bmp: Bitmap? = null
    private var key: String? = null
    private val recognizer by lazy {
        com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
    }

    init {
        setBackgroundColor(0xF5000000.toInt())
        isClickable = true
        addView(zoomView, LayoutParams(-1, -1).apply { bottomMargin = d(56f) })
        zoomView.onZoom = { z ->
            zoomLabel.text = String.format(java.util.Locale.US, "%.1fx", z)
            zoomLabel.visibility = if (z > 1.05f) VISIBLE else GONE
        }
        zoomView.onBoxTap = { b ->
            actions?.onRefFeedback()
            actions?.onRefText(b.text)
            say(L.t("Escrito: ", "Typed: ") + b.text.take(40), 1800L)
        }
        fun circle(v: IconView) {
            v.color = 0xFFFFFFFF.toInt()
            v.background = PanelUi.pressable(0x99303134.toInt(), 0xCC505257.toInt(), d(22f).toFloat())
        }
        val close = IconView(context, Icons.CLOSE, 7.5f)
        circle(close)
        close.setOnClickListener { actions?.onRefFeedback(); actions?.onRefClose() }
        addView(close, LayoutParams(d(44f), d(44f), Gravity.TOP or Gravity.END).apply { setMargins(0, d(8f), d(8f), 0) })
        zoomLabel.textSize = 12f
        zoomLabel.setTextColor(0xFFFFFFFF.toInt())
        zoomLabel.setPadding(d(10f), d(4f), d(10f), d(4f))
        zoomLabel.background = PanelUi.rounded(0x99303134.toInt(), d(12f).toFloat())
        zoomLabel.visibility = GONE
        addView(zoomLabel, LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(d(12f), d(16f), 0, 0) })
        hint.textSize = 13f
        hint.setTextColor(0xFFFFFFFF.toInt())
        hint.maxLines = 2
        hint.setPadding(d(14f), d(6f), d(14f), d(6f))
        hint.background = PanelUi.rounded(0xE6202124.toInt(), d(16f).toFloat())
        addView(hint, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = d(64f); leftMargin = d(12f); rightMargin = d(12f) })
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(d(6f), 0, d(6f), d(6f))
        addView(row, LayoutParams(-1, d(52f), Gravity.BOTTOM))
        buildRow()
    }

    private fun buildRow() {
        row.removeAllViews()
        fun pill(label: String, mainBtn: Boolean, w: Float, act: () -> Unit) = row.addView(TextView(context).apply {
            text = label; textSize = 13.5f; gravity = Gravity.CENTER; maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(9, 14, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            setTextColor(if (mainBtn) palette.onAccent else 0xFFFFFFFF.toInt())
            background = if (mainBtn) PanelUi.pressable(palette.accent, palette.accentPressed, d(22f).toFloat())
                else PanelUi.pressable(0xFF303134.toInt(), 0xFF505257.toInt(), d(22f).toFloat())
            setOnClickListener { actions?.onRefFeedback(); act() }
        }, LinearLayout.LayoutParams(0, d(44f), w).apply { if (row.childCount > 0) leftMargin = d(6f) })
        pill(L.t("Teclado", "Keyboard"), true, 1f) { actions?.onRefKeyboard() }
        pill(L.t("Copiar todo", "Copy all"), false, 1.1f) { copyAll() }
        pill(L.t("Elegir palabras", "Pick words"), false, 1.25f) { bmp?.let { actions?.onRefPick(it) } }
        pill(L.t("Pegar foto", "Paste photo"), false, 0.95f) { actions?.onRefPaste() }
    }

    /** Abre la foto [uri] (o la misma de antes, tal como la dejaste). [id] la identifica. */
    fun open(uri: Uri, id: String) {
        if (id == key && bmp != null) {
            // La misma: se queda el zoom y la posición
            say(L.t("Toca un renglón y se escribe donde estás", "Tap a line and it's typed where you are"), 2500L)
            return
        }
        key = id
        bmp = null
        zoomView.setImage(null)
        say(L.t("Abriendo…", "Opening…"), 0L)
        io.execute {
            try {
                val cr = context.contentResolver
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var sm = 1
                while (maxOf(o.outWidth, o.outHeight) / sm > 2600) sm *= 2
                val b = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sm }) }
                main.post {
                    if (key != id) return@post
                    if (b == null) { say(L.t("No se pudo abrir la imagen.", "Couldn't open the image."), 0L); return@post }
                    bmp = b
                    zoomView.setImage(b)
                    read(b, id)
                }
            } catch (_: Throwable) { main.post { say(L.t("No se pudo abrir la imagen.", "Couldn't open the image."), 0L) } }
        }
    }

    /** Lee el texto (dentro del teléfono) y marca cada renglón para tocarlo. */
    private fun read(b: Bitmap, id: String) {
        say(L.t("Buscando el texto…", "Finding the text…"), 0L)
        recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(b, 0))
            .addOnSuccessListener { r ->
                if (key != id) return@addOnSuccessListener
                val list = ArrayList<ZoomImageView.Box>()
                for (bl in r.textBlocks) for (l in bl.lines) {
                    val bb = l.boundingBox ?: continue
                    if (l.text.isNotBlank()) list.add(ZoomImageView.Box(RectF(bb), l.text.trim()))
                }
                zoomView.boxes = list
                say(if (list.isEmpty()) L.t("No encontré texto. Pellizca para agrandar la imagen.", "No text found. Pinch to zoom the image.")
                    else L.t("Toca un renglón y se escribe donde estás · pellizca para agrandar", "Tap a line and it's typed where you are · pinch to zoom"), 4000L)
            }
            .addOnFailureListener { say(L.t("No se pudo leer el texto.", "Couldn't read the text."), 3000L) }
    }

    private fun copyAll() {
        val t = zoomView.boxes.joinToString("\n") { it.text }
        if (t.isBlank()) { say(L.t("No hay texto para copiar.", "No text to copy."), 2000L); return }
        try {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                .setPrimaryClip(android.content.ClipData.newPlainText("text", t))
        } catch (_: Exception) { }
        say(L.t("Todo el texto quedó copiado.", "All the text is copied."), 2200L)
    }

    private val hideHint = Runnable { hint.animate().alpha(0f).setDuration(350L).withEndAction { hint.visibility = GONE; hint.alpha = 1f }.start() }

    private fun say(t: String, ms: Long) {
        main.removeCallbacks(hideHint)
        hint.animate().cancel()
        hint.alpha = 1f
        hint.text = t
        hint.visibility = VISIBLE
        hint.scaleX = 1.05f; hint.scaleY = 1.05f
        hint.animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
        if (ms > 0) main.postDelayed(hideHint, ms)
    }

    /** Ya no se usa: se suelta la foto. */
    fun forget() {
        key = null
        bmp = null
        zoomView.setImage(null)
    }

    fun applyPalette(p: Palette) {
        palette = p
        zoomView.accent = p.accent
        buildRow()
    }
}
