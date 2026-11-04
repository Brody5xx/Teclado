package com.luisell.bestkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * 🗺️ Un mapita (de OpenStreetMap) con el círculo del área de detección de un lugar,
 * como en Samsung Rutinas. Cambias [radius] y el círculo crece o se achica.
 */
class MapPreview(context: Context, private val lat: Double, private val lon: Double) : View(context) {
    var radius = 250
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val main = Handler(Looper.getMainLooper())
    private val src = Rect()
    private val dst = RectF()

    companion object {
        private val io = Executors.newFixedThreadPool(3)
        private val cache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 60
        }
        private val loading = HashSet<String>()
    }

    private fun worldX(z: Int) = (lon + 180.0) / 360.0 * 256.0 * 2.0.pow(z)
    private fun worldY(z: Int): Double {
        val r = Math.toRadians(lat)
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * 256.0 * 2.0.pow(z)
    }
    private fun mpp(z: Int) = 156543.03392 * cos(Math.toRadians(lat)) / 2.0.pow(z)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        c.drawColor(0xFF1B2230.toInt())
        val scale = d * 0.8f // píxeles de pantalla por píxel del mapa
        // El zoom más cercano donde el círculo todavía cabe con aire alrededor
        var z = 18
        while (z > 3 && (radius / mpp(z)) * scale > minOf(w, h) * 0.36) z--
        val cx = worldX(z); val cy = worldY(z)
        val tilePx = 256f * scale
        val left = cx - w / 2 / scale; val top = cy - h / 2 / scale
        val tx0 = floor(left / 256).toInt(); val ty0 = floor(top / 256).toInt()
        val tx1 = floor((left + w / scale) / 256).toInt(); val ty1 = floor((top + h / scale) / 256).toInt()
        val n = 1 shl z
        for (tx in tx0..tx1) for (ty in ty0..ty1) {
            if (ty < 0 || ty >= n) continue
            val x = ((tx % n) + n) % n
            val key = "$z/$x/$ty"
            val b = synchronized(cache) { cache[key] }
            val sx = ((tx * 256 - left) * scale).toFloat(); val sy = ((ty * 256 - top) * scale).toFloat()
            if (b != null) {
                src.set(0, 0, b.width, b.height)
                dst.set(sx, sy, sx + tilePx, sy + tilePx)
                c.drawBitmap(b, src, dst, paint)
            } else load(key)
        }
        // Oscurece un poco para que el círculo resalte
        c.drawColor(0x33000000)
        // El círculo del área
        val rpx = (radius / mpp(z) * scale).toFloat()
        paint.style = Paint.Style.FILL
        paint.color = 0x3322D3EE
        c.drawCircle(w / 2, h / 2, rpx, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f * d
        paint.color = 0xFF22D3EE.toInt()
        c.drawCircle(w / 2, h / 2, rpx, paint)
        // El punto del lugar
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(w / 2, h / 2, 6f * d, paint)
        paint.color = 0xFFA78BFA.toInt()
        c.drawCircle(w / 2, h / 2, 4f * d, paint)
        // Crédito del mapa (lo pide OpenStreetMap)
        paint.color = 0xCCFFFFFF.toInt()
        paint.textSize = 9f * d
        c.drawText("© OpenStreetMap", 6f * d, h - 6f * d, paint)
    }

    private fun load(key: String) {
        synchronized(loading) { if (!loading.add(key)) return }
        io.execute {
            val bmp = try {
                val con = URL("https://tile.openstreetmap.org/$key.png").openConnection() as HttpURLConnection
                con.setRequestProperty("User-Agent", "BestKeyboard/12 (Android keyboard app)")
                con.connectTimeout = 8000; con.readTimeout = 8000
                con.inputStream.use { BitmapFactory.decodeStream(it) }
            } catch (_: Throwable) { null }
            if (bmp != null) synchronized(cache) { cache[key] = bmp }
            synchronized(loading) { loading.remove(key) }
            if (bmp != null) main.post { invalidate() }
        }
    }
}
