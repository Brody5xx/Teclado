package com.luisell.bestkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.TextureView
import java.io.File

/**
 * 🖼️ Fondo propio del teclado: una imagen o un video de tu galería.
 * El video se achica (se comprime) para que vaya ligero, se repite sin fin y puede sonar o estar en silencio.
 */
object BgMedia {
    fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "fondo").apply { mkdirs() }
    fun imageFile(ctx: Context) = File(dir(ctx), "imagen.jpg")
    fun videoFile(ctx: Context) = File(dir(ctx), "video.mp4")

    /** "" (ninguno), "image" o "video". */
    fun kind(ctx: Context): String {
        val k = Prefs.get(ctx).getString(Prefs.BG_KIND, "") ?: ""
        return when {
            k == "image" && imageFile(ctx).exists() -> "image"
            k == "video" && videoFile(ctx).exists() -> "video"
            else -> ""
        }
    }

    fun dim(ctx: Context): Float = (Prefs.get(ctx).getString(Prefs.BG_DIM, "0.45") ?: "0.45").toFloatOrNull() ?: 0.45f

    fun clear(ctx: Context) {
        Prefs.get(ctx).edit().putString(Prefs.BG_KIND, "").apply()
        imageFile(ctx).delete()
        videoFile(ctx).delete()
    }

    /** Guarda la imagen achicada (máx. 1440 px) para que no pese. (Llamar en segundo plano) */
    fun saveImage(ctx: Context, uri: Uri): Boolean = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1440) s *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        if (bmp == null) false else {
            imageFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            videoFile(ctx).delete()
            Prefs.get(ctx).edit().putString(Prefs.BG_KIND, "image").apply()
            true
        }
    } catch (_: Throwable) { false }

    /** La imagen para dibujarla en el teclado (del tamaño justo). */
    fun loadImage(ctx: Context, maxSide: Int): Bitmap? = try {
        val f = imageFile(ctx)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    } catch (_: Throwable) { null }

    // ---------------------------------------------------------------- Video

    /**
     * Comprime el video (más chico, hasta 640 px de alto) para que vaya ligero en el teclado.
     * [progress] recibe 0..100; [done] true si quedó listo. Si no se puede comprimir, se copia tal cual.
     */
    fun saveVideo(ctx: Context, uri: Uri, progress: (Int) -> Unit, done: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        val out = File(dir(app), "video_tmp.mp4")
        out.delete()
        val main = Handler(Looper.getMainLooper())
        fun finish(ok: Boolean) {
            if (ok && out.exists() && out.length() > 0) {
                videoFile(app).delete()
                out.renameTo(videoFile(app))
                imageFile(app).delete()
                Prefs.get(app).edit().putString(Prefs.BG_KIND, "video").apply()
                done(true)
            } else {
                // Plan B: copiarlo sin comprimir
                Thread {
                    val ok2 = try {
                        app.contentResolver.openInputStream(uri)?.use { i -> videoFile(app).outputStream().use { o -> i.copyTo(o) } }
                        imageFile(app).delete()
                        Prefs.get(app).edit().putString(Prefs.BG_KIND, "video").apply()
                        true
                    } catch (_: Throwable) { false }
                    main.post { done(ok2) }
                }.start()
            }
        }
        try {
            val transformer = androidx.media3.transformer.Transformer.Builder(app)
                .setVideoMimeType(androidx.media3.common.MimeTypes.VIDEO_H264)
                .addListener(object : androidx.media3.transformer.Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, result: androidx.media3.transformer.ExportResult) = finish(true)
                    override fun onError(composition: androidx.media3.transformer.Composition, result: androidx.media3.transformer.ExportResult,
                                         exception: androidx.media3.transformer.ExportException) { out.delete(); finish(false) }
                })
                .build()
            val item = androidx.media3.transformer.EditedMediaItem.Builder(androidx.media3.common.MediaItem.fromUri(uri))
                .setEffects(androidx.media3.transformer.Effects(emptyList(),
                    listOf<androidx.media3.common.Effect>(androidx.media3.effect.Presentation.createForHeight(640))))
                .build()
            transformer.start(item, out.path)
            val holder = androidx.media3.transformer.ProgressHolder()
            val poll = object : Runnable {
                override fun run() {
                    val state = try { transformer.getProgress(holder) } catch (_: Throwable) { -1 }
                    if (state == androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE) progress(holder.progress)
                    if (state != androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED) main.postDelayed(this, 400)
                }
            }
            main.post(poll)
        } catch (_: Throwable) {
            finish(false)
        }
    }
}

/**
 * El video de fondo detrás de las teclas: se reproduce completo, se repite sin fin,
 * se recorta para llenar el teclado y suena o no según tu ajuste.
 */
class BgVideoView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    var sound = false
        set(v) { field = v; player?.let { val vol = if (v) 1f else 0f; try { it.setVolume(vol, vol) } catch (_: Exception) { } } }
    private var wantPlay = false

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    /** Empieza (o sigue) el video. */
    fun play() {
        wantPlay = true
        val p = player
        if (p != null) { try { if (!p.isPlaying) p.start() } catch (_: Exception) { } ; return }
        val st = surfaceTexture ?: return
        open(st)
    }

    fun pause() {
        wantPlay = false
        try { player?.pause() } catch (_: Exception) { }
    }

    fun release() {
        wantPlay = false
        try { player?.release() } catch (_: Exception) { }
        player = null
        surface?.release()
        surface = null
    }

    /** Volver a cargar (cambiaste de video). */
    fun reload() {
        val keep = wantPlay
        try { player?.release() } catch (_: Exception) { }
        player = null
        if (keep) play()
    }

    private fun open(st: SurfaceTexture) {
        val f = BgMedia.videoFile(context)
        if (!f.exists()) return
        try {
            val s = surface ?: Surface(st).also { surface = it }
            val mp = MediaPlayer()
            mp.setDataSource(f.path)
            mp.setSurface(s)
            mp.isLooping = true
            val vol = if (sound) 1f else 0f
            mp.setVolume(vol, vol)
            mp.setOnPreparedListener { m ->
                fit(m.videoWidth, m.videoHeight)
                if (wantPlay) m.start()
            }
            mp.setOnVideoSizeChangedListener { _, w, h -> fit(w, h) }
            mp.prepareAsync()
            player = mp
        } catch (_: Exception) { }
    }

    /** Recorta el video para llenar el teclado sin estirarlo. */
    private fun fit(vw: Int, vh: Int) {
        if (vw <= 0 || vh <= 0 || width <= 0 || height <= 0) return
        val sx = width.toFloat() / vw
        val sy = height.toFloat() / vh
        val s = maxOf(sx, sy)
        val m = Matrix()
        m.setScale(vw * s / width, vh * s / height, width / 2f, height / 2f)
        setTransform(m)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        player?.let { try { fit(it.videoWidth, it.videoHeight) } catch (_: Exception) { } }
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { if (wantPlay) open(st) }
    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) { player?.let { fit(it.videoWidth, it.videoHeight) } }
    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { release(); return true }
    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
}
