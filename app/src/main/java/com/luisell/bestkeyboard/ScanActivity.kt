package com.luisell.bestkeyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.Executors

/**
 * Escanear texto: tomas una foto (o eliges una de la galería), el teléfono lee el texto
 * DENTRO del teléfono (Google ML Kit, sin internet), lo revisas y lo insertas en el teclado.
 */
class ScanActivity : Activity() {

    companion object {
        const val EXTRA_GALLERY = "galeria"
        private const val REQ_CAMERA = 1
        private const val REQ_GALLERY = 2
        private const val PHOTO = "scan_photo.jpg"
        private const val MAX_AGE_MS = 3 * 60 * 1000L

        @Volatile private var pending: String? = null
        @Volatile private var pendingAt = 0L

        /** El teclado lo recoge cuando vuelves a la app donde escribías. */
        fun takePending(): String? {
            val t = pending ?: return null
            pending = null
            return if (System.currentTimeMillis() - pendingAt < MAX_AGE_MS) t else null
        }

        @Volatile private var pendingPhoto: Bitmap? = null
        @Volatile private var pendingPhotoAt = 0L

        /** Foto elegida en la galería: el teclado la abre en su escáner para elegir palabras. */
        fun takePendingPhoto(): Bitmap? {
            val b = pendingPhoto ?: return null
            pendingPhoto = null
            return if (System.currentTimeMillis() - pendingPhotoAt < MAX_AGE_MS) b else null
        }
    }

    /** Abierta solo para elegir una foto de la galería (vuelve directo al teclado). */
    private var galleryMode = false

    private lateinit var photo: ImageView
    private lateinit var status: TextView
    private lateinit var edit: EditText
    private lateinit var insertBtn: Button
    private lateinit var copyBtn: Button
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        L.set(Prefs.get(this).getString(Prefs.LANG, "es") ?: "es")
        title = L.t("Escanear texto", "Scan text")
        setContentView(buildUi())
        galleryMode = intent?.getBooleanExtra(EXTRA_GALLERY, false) == true
        if (galleryMode) status.text = L.t("Elige una foto…", "Pick a photo…")
        if (savedInstanceState == null) {
            if (galleryMode) openGallery() else openCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { recognizer.close() } catch (_: Exception) { }
        io.shutdown()
    }

    private fun buildUi(): View {
        val sv = ScrollView(this)
        PanelUi.glowScrollbar(sv)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(12), dp(16), dp(32))
        sv.addView(col)

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(smallButton(L.t("Tomar foto", "Take photo")) { openCamera() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(smallButton(L.t("Galería", "Gallery")) { openGallery() }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
        col.addView(row, LinearLayout.LayoutParams(-1, -2))

        photo = ImageView(this)
        photo.adjustViewBounds = true
        photo.scaleType = ImageView.ScaleType.FIT_CENTER
        photo.visibility = View.GONE
        col.addView(photo, LinearLayout.LayoutParams(-1, dp(220)).apply { topMargin = dp(12) })

        status = TextView(this)
        status.textSize = 15f
        status.text = L.t("Toma una foto del texto que quieres escribir (etiquetas, números de serie, papeles...).", "Take a photo of the text you want to type (labels, serial numbers, papers...).")
        status.setPadding(0, dp(12), 0, dp(6))
        col.addView(status, LinearLayout.LayoutParams(-1, -2))

        edit = EditText(this)
        edit.hint = L.t("Aquí aparecerá el texto. Puedes corregirlo antes de insertarlo.", "The text will show up here. You can fix it before inserting it.")
        edit.minLines = 4
        edit.gravity = Gravity.TOP or Gravity.START
        edit.textSize = 16f
        col.addView(edit, LinearLayout.LayoutParams(-1, -2))

        insertBtn = smallButton(L.t("⌨  Insertar donde estaba escribiendo", "⌨  Insert where I was typing")) { insert() }
        insertBtn.setTypeface(insertBtn.typeface, Typeface.BOLD)
        col.addView(insertBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        copyBtn = smallButton(L.t("Copiar el texto", "Copy the text")) { copy() }
        col.addView(copyBtn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        val tip = TextView(this)
        tip.textSize = 13f
        tip.alpha = 0.7f
        tip.text = L.t("Consejos: buena luz, el texto derecho y que llene la foto. El texto se lee dentro del teléfono, sin internet.",
            "Tips: good light, straight text that fills the photo. The text is read on the phone, no internet needed.")
        tip.setPadding(0, dp(14), 0, 0)
        col.addView(tip, LinearLayout.LayoutParams(-1, -2))
        setButtonsEnabled(false)
        return sv
    }

    private fun smallButton(label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        return b
    }

    private fun setButtonsEnabled(on: Boolean) {
        insertBtn.isEnabled = on
        copyBtn.isEnabled = on
    }

    private fun photoFile() = File(cacheDir, PHOTO)

    private fun openCamera() {
        photoFile().delete()
        val uri = Uri.parse("content://${ClipStore.AUTHORITY}/$PHOTO")
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("foto", uri)
        try {
            startActivityForResult(i, REQ_CAMERA)
        } catch (_: ActivityNotFoundException) {
            status.text = L.t("No encontré la app de la cámara. Usa el botón Galería.", "Couldn't find the camera app. Use the Gallery button.")
        } catch (_: SecurityException) {
            status.text = L.t("No se pudo abrir la cámara. Usa el botón Galería.", "Couldn't open the camera. Use the Gallery button.")
        }
    }

    private fun openGallery() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try {
            startActivityForResult(Intent.createChooser(i, L.t("Elige una foto", "Pick a photo")), REQ_GALLERY)
        } catch (_: ActivityNotFoundException) {
            status.text = L.t("No encontré la galería.", "Couldn't find the gallery.")
        }
    }

    @Deprecated("Usamos la API clásica para no agregar librerías extra")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (galleryMode) {
            // Volver al teclado con la foto, para elegir las palabras con las gotitas
            val uri = if (resultCode == RESULT_OK) data?.data else null
            if (uri == null) { finish(); return }
            status.text = L.t("Abriendo la foto…", "Opening the photo…")
            io.execute {
                val bmp = loadBitmap(uri, 2000)
                main.post {
                    if (bmp != null) {
                        pendingPhoto = bmp
                        pendingPhotoAt = System.currentTimeMillis()
                    } else {
                        Toast.makeText(this, L.t("No se pudo abrir la foto.", "Couldn't open the photo."), Toast.LENGTH_SHORT).show()
                    }
                    finish()
                }
            }
            return
        }
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_CAMERA -> {
                val f = photoFile()
                if (f.exists() && f.length() > 0) read(Uri.fromFile(f))
                else status.text = L.t("No llegó la foto. Inténtalo otra vez.", "The photo didn't come through. Try again.")
            }
            REQ_GALLERY -> data?.data?.let { read(it) }
        }
    }

    /** Lee el texto de la foto. */
    private fun read(uri: Uri) {
        status.text = L.t("Leyendo el texto…", "Reading the text…")
        setButtonsEnabled(false)
        showPreview(uri)
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (_: Exception) {
            status.text = L.t("No se pudo abrir la foto.", "Couldn't open the photo.")
            return
        }
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.textBlocks.joinToString("\n") { it.text }.trim()
                if (text.isEmpty()) {
                    status.text = L.t("No encontré texto en la foto. Prueba con más luz o más cerca.", "No text found in the photo. Try with more light or closer.")
                } else {
                    edit.setText(text)
                    edit.setSelection(text.length)
                    status.text = L.t("Listo. Revisa el texto (puedes corregirlo o borrar lo que no quieras) y toca Insertar.", "Done. Check the text (you can fix it or delete what you don't want) and tap Insert.")
                    setButtonsEnabled(true)
                }
            }
            .addOnFailureListener {
                status.text = L.t("No se pudo leer el texto. Inténtalo otra vez.", "Couldn't read the text. Try again.")
            }
    }

    /** Abre una foto derecha (según cómo se tomó) y de tamaño razonable. */
    private fun loadBitmap(uri: Uri, maxSide: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (o.outWidth / (s * 2) >= maxSide || o.outHeight / (s * 2) >= maxSide) s *= 2
        var bmp: Bitmap? = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
        }
        val rot = try {
            contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) { 0f }
        val b = bmp
        if (b != null && rot != 0f) {
            bmp = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot) }, true)
        }
        bmp
    } catch (_: Throwable) { null }

    /** Muestra la foto pequeña (girada bien) para que sepas qué se leyó. */
    private fun showPreview(uri: Uri) {
        io.execute {
            try {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1
                while (o.outWidth / (s * 2) >= 1000 && o.outHeight / (s * 2) >= 1000) s *= 2
                var bmp: Bitmap = contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
                } ?: return@execute
                val rot = try {
                    contentResolver.openInputStream(uri)?.use {
                        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    } ?: 0f
                } catch (_: Exception) { 0f }
                if (rot != 0f) {
                    val m = Matrix().apply { postRotate(rot) }
                    bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                }
                main.post {
                    if (!isFinishing) {
                        photo.setImageBitmap(bmp)
                        photo.visibility = View.VISIBLE
                    }
                }
            } catch (_: Throwable) { }
        }
    }

    private fun insert() {
        val t = edit.text?.toString()?.trim() ?: ""
        if (t.isEmpty()) return
        pending = t
        pendingAt = System.currentTimeMillis()
        Toast.makeText(this, L.t("Listo: el texto aparece donde estabas escribiendo.", "Done: the text shows up where you were typing."), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun copy() {
        val t = edit.text?.toString()?.trim() ?: ""
        if (t.isEmpty()) return
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("Texto escaneado", t))
        Toast.makeText(this, L.t("Copiado. También queda en tu portapapeles.", "Copied. It's also in your clipboard."), Toast.LENGTH_SHORT).show()
    }
}
