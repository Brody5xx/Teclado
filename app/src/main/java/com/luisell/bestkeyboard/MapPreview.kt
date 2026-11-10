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
import android.view.MotionEvent
import android.view.View
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * 🗺️ Un mapa (de OpenStreetMap, o satélite de Esri) con el círculo del área de detección de un lugar,
 * como en Samsung Rutinas. Se acerca y aleja con dos dedos, se mueve arrastrando, y el botón de arriba
 * cambia entre «Mapa» y «Satélite».
 */
class MapPreview(context: Context, private val lat: Double, private val lon: Double) : View(context) {
    var radius = 250
        set(v) { field = v; invalidate() }

    /** 🔷 La forma del sitio ([lat, lon] por esquina). */
    var shape: List<DoubleArray> = emptyList()
        set(v) { field = v; invalidate() }
    /** Si no es null, tocar el mapa agrega una esquina a [shape] y avisa aquí. */
    var onCorner: ((List<DoubleArray>) -> Unit)? = null
    /** 🗺️ El recorrido del día ([lat, lon, …]) y las marcas (llegué, salí…). */
    var path: List<DoubleArray> = emptyList()
        set(v) { field = v; invalidate() }
    class Mark(val lat: Double, val lon: Double, val label: String, val color: Int)
    var marks: List<Mark> = emptyList()
        set(v) { field = v; invalidate() }
    /** Para acercar (+1) o alejar (-1) el mapa con botones. */
    var zoomShift = 0
        set(v) { val step = v - field; field = v; userZ = ((userZ ?: lastZ) + step).coerceIn(3.0, MAX_Z); invalidate() }

    /** 🛰️ Satélite (se acuerda de tu elección). */
    var satellite = Prefs.get(context).getBoolean("map_sat", true)
        set(v) { field = v; Prefs.get(context).edit().putBoolean("map_sat", v).apply(); invalidate() }

    // Lo que moviste con los dedos (null = el encuadre automático)
    private var userZ: Double? = null
    private var userLa: Double? = null
    private var userLo: Double? = null
    private var lastZ = 18.0
    private var lastLa = lat
    private var lastLo = lon

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val main = Handler(Looper.getMainLooper())
    private val src = Rect()
    private val dst = RectF()

    companion object {
        private val io = Executors.newFixedThreadPool(3)
        private val cache = object : LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 90
        }
        private val loading = HashSet<String>()
        private const val MAX_Z = 21.0
        /** Las fotos de satélite y el mapa llegan hasta este zoom; más cerca, se agrandan. */
        private const val TILE_MAX = 19
    }

    private fun wx(lo: Double, z: Int) = (lo + 180.0) / 360.0 * 256.0 * 2.0.pow(z)
    private fun wy(la: Double, z: Int): Double {
        val r = Math.toRadians(la)
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * 256.0 * 2.0.pow(z)
    }
    private fun mpp(z: Int, la: Double = lat) = 156543.03392 * cos(Math.toRadians(la)) / 2.0.pow(z)

    // Lo que se calculó al dibujar (para convertir un toque en lat/lon)
    private var vz = 18
    private var vLeft = 0.0
    private var vTop = 0.0
    private val base get() = d * 0.8f
    /** Píxeles de pantalla por píxel del mapa (con el zoom de los dedos). */
    private var scale = d * 0.8f

    /** El centro y el zoom: con recorrido o marcas, que quepa todo; si no, el círculo del sitio. */
    private fun frame(w: Float, h: Float): Triple<Double, Double, Int> {
        val pts = ArrayList<DoubleArray>()
        for (p in path) pts.add(p)
        for (m in marks) pts.add(doubleArrayOf(m.lat, m.lon))
        if (pts.size >= 1 && onCorner == null) {
            val minLa = pts.minOf { it[0] }; val maxLa = pts.maxOf { it[0] }
            val minLo = pts.minOf { it[1] }; val maxLo = pts.maxOf { it[1] }
            val cLa = (minLa + maxLa) / 2; val cLo = (minLo + maxLo) / 2
            var z = 18
            while (z > 3 && ((wx(maxLo, z) - wx(minLo, z)) * base > w * 0.8 || (wy(minLa, z) - wy(maxLa, z)) * base > h * 0.75)) z--
            return Triple(cLa, cLo, z)
        }
        var z = 18
        val reach = maxOf(radius.toDouble(), 60.0)
        while (z > 3 && (reach / mpp(z)) * base > minOf(w, h) * 0.36) z--
        return Triple(lat, lon, z)
    }

    private fun sx(lo: Double) = ((wx(lo, vz) - vLeft) * scale).toFloat()
    private fun sy(la: Double) = ((wy(la, vz) - vTop) * scale).toFloat()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        c.drawColor(0xFF1B2230.toInt())
        val (aLa, aLo, aZ) = frame(w, h)
        // El zoom y el centro: los que pusiste con los dedos, o el encuadre automático
        val zf = (userZ ?: aZ.toDouble()).coerceIn(3.0, MAX_Z)
        val cLa = userLa ?: aLa; val cLo = userLo ?: aLo
        lastZ = zf; lastLa = cLa; lastLo = cLo
        val z = floor(zf).toInt().coerceIn(3, TILE_MAX)
        scale = (base * 2.0.pow(zf - z)).toFloat()
        vz = z
        val cx = wx(cLo, z); val cy = wy(cLa, z)
        val tilePx = 256f * scale
        val left = cx - w / 2 / scale; val top = cy - h / 2 / scale
        vLeft = left; vTop = top
        val tx0 = floor(left / 256).toInt(); val ty0 = floor(top / 256).toInt()
        val tx1 = floor((left + w / scale) / 256).toInt(); val ty1 = floor((top + h / scale) / 256).toInt()
        val n = 1 shl z
        for (tx in tx0..tx1) for (ty in ty0..ty1) {
            if (ty < 0 || ty >= n) continue
            val x = ((tx % n) + n) % n
            val key = (if (satellite) "s/" else "o/") + "$z/$x/$ty"
            val b = synchronized(cache) { cache[key] }
            val px = ((tx * 256 - left) * scale).toFloat(); val py = ((ty * 256 - top) * scale).toFloat()
            if (b != null) {
                src.set(0, 0, b.width, b.height)
                dst.set(px, py, px + tilePx, py + tilePx)
                c.drawBitmap(b, src, dst, paint)
            } else load(key)
        }
        // Oscurece un poco para que lo de encima resalte
        c.drawColor(0x33000000)
        val hasRoute = path.isNotEmpty() || marks.isNotEmpty()
        if (!hasRoute || onCorner != null) {
            val ox = sx(lon); val oy = sy(lat)
            val rpx = (radius / mpp(z) * scale).toFloat()
            if (shape.size >= 2 || onCorner != null) {
                // Con forma: el círculo queda tenue, de guía
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d; paint.color = 0x6622D3EE
                c.drawCircle(ox, oy, rpx, paint)
            } else {
                paint.style = Paint.Style.FILL; paint.color = 0x3322D3EE
                c.drawCircle(ox, oy, rpx, paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * d; paint.color = 0xFF22D3EE.toInt()
                c.drawCircle(ox, oy, rpx, paint)
            }
            // 🔷 La forma del sitio
            if (shape.isNotEmpty()) {
                val pa = android.graphics.Path()
                for ((i, pt) in shape.withIndex()) { val x = sx(pt[1]); val y = sy(pt[0]); if (i == 0) pa.moveTo(x, y) else pa.lineTo(x, y) }
                if (shape.size >= 3) {
                    pa.close()
                    paint.style = Paint.Style.FILL; paint.color = 0x44A78BFA
                    c.drawPath(pa, paint)
                }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * d; paint.color = 0xFFA78BFA.toInt()
                c.drawPath(pa, paint)
                paint.style = Paint.Style.FILL
                for (pt in shape) {
                    paint.color = 0xFFFFFFFF.toInt(); c.drawCircle(sx(pt[1]), sy(pt[0]), 5f * d, paint)
                    paint.color = 0xFFA78BFA.toInt(); c.drawCircle(sx(pt[1]), sy(pt[0]), 3f * d, paint)
                }
            }
            // El punto del lugar
            paint.style = Paint.Style.FILL
            paint.color = 0xFFFFFFFF.toInt()
            c.drawCircle(ox, oy, 6f * d, paint)
            paint.color = 0xFFA78BFA.toInt()
            c.drawCircle(ox, oy, 4f * d, paint)
        }
        if (hasRoute && onCorner == null) {
            // 🗺️ El recorrido: una línea suave que une los puntos
            if (path.size >= 2) {
                val pa = android.graphics.Path()
                for ((i, pt) in path.withIndex()) { val x = sx(pt[1]); val y = sy(pt[0]); if (i == 0) pa.moveTo(x, y) else pa.lineTo(x, y) }
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * d; paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
                paint.color = 0xCC22D3EE.toInt()
                c.drawPath(pa, paint)
            }
            paint.style = Paint.Style.FILL
            for (pt in path) { paint.color = 0xFF22D3EE.toInt(); c.drawCircle(sx(pt[1]), sy(pt[0]), 2.5f * d, paint) }
            // Las marcas con su hora
            paint.textSize = 11f * d
            for (m in marks) {
                val x = sx(m.lon); val y = sy(m.lat)
                paint.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, 8f * d, paint)
                paint.color = m.color; c.drawCircle(x, y, 6f * d, paint)
                if (m.label.isNotEmpty()) {
                    val tw = paint.measureText(m.label)
                    paint.color = 0xDD10141C.toInt()
                    c.drawRoundRect(x + 10f * d, y - 9f * d, x + 18f * d + tw, y + 8f * d, 6f * d, 6f * d, paint)
                    paint.color = 0xFFFFFFFF.toInt()
                    c.drawText(m.label, x + 14f * d, y + 4f * d, paint)
                }
            }
        }
        // Crédito del mapa (lo piden OpenStreetMap y Esri)
        paint.style = Paint.Style.FILL
        paint.color = 0xCCFFFFFF.toInt()
        paint.textSize = 9f * d
        c.drawText(if (satellite) "Imagery © Esri, Maxar, Earthstar Geographics" else "© OpenStreetMap", 6f * d, h - 6f * d, paint)
        // 🛰️ El botón «Satélite / Mapa» (arriba a la derecha)
        paint.textSize = 12f * d
        val label = if (satellite) L.t("Mapa", "Map") else L.t("Satélite", "Satellite")
        val tw = paint.measureText(label)
        chip.set(w - tw - 30f * d, 8f * d, w - 8f * d, 38f * d)
        paint.color = 0xE610141C.toInt()
        c.drawRoundRect(chip, 15f * d, 15f * d, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f * d; paint.color = 0x8822D3EE.toInt()
        c.drawRoundRect(chip, 15f * d, 15f * d, paint)
        paint.style = Paint.Style.FILL; paint.color = 0xFFFFFFFF.toInt()
        c.drawText(label, chip.left + 11f * d, chip.centerY() + 4f * d, paint)
    }

    private val chip = RectF()
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var pinched = false

    /** 🤏 Dos dedos: acercar y alejar (alrededor de donde están los dedos). */
    private val pinch = android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(g: android.view.ScaleGestureDetector): Boolean {
            pinched = true
            val before = lastZ
            val nz = (before + kotlin.math.log2(g.scaleFactor.toDouble())).coerceIn(3.0, MAX_Z)
            // Que el punto entre los dedos se quede en su lugar
            val fx = g.focusX - width / 2f; val fy = g.focusY - height / 2f
            val k = 1.0 - 1.0 / 2.0.pow(nz - before)
            panBy(-fx * k.toFloat(), -fy * k.toFloat())
            userZ = nz; lastZ = nz
            invalidate()
            return true
        }
    })

    /** Mueve el centro del mapa [dx], [dy] píxeles de pantalla. */
    private fun panBy(dx: Float, dy: Float) {
        val z = vz
        val nz = 256.0 * 2.0.pow(z)
        val cx = wx(lastLo, z) - dx / scale
        val cy = wy(lastLa, z) - dy / scale
        userLo = cx / nz * 360.0 - 180.0
        userLa = Math.toDegrees(atan(kotlin.math.sinh(PI * (1 - 2 * cy / nz))))
        lastLo = userLo!!; lastLa = userLa!!
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        pinch.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; moved = false; pinched = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> { moved = true; return true }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount == 1 && !pinch.isInProgress) {
                    if (!moved && (Math.abs(e.x - downX) > 10 * d || Math.abs(e.y - downY) > 10 * d)) moved = true
                    if (moved && !pinched) { panBy(e.x - lastX, e.y - lastY); invalidate() }
                }
                lastX = e.x; lastY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (moved || pinched) return true
                // Un toque: el botón Satélite/Mapa, o una esquina nueva
                if (chip.contains(e.x, e.y)) { satellite = !satellite; return true }
                val cb = onCorner ?: return true
                val mx = vLeft + e.x / scale; val my = vTop + e.y / scale
                val nz = 256.0 * 2.0.pow(vz)
                val lo = mx / nz * 360.0 - 180.0
                val la = Math.toDegrees(atan(kotlin.math.sinh(PI * (1 - 2 * my / nz))))
                shape = shape + listOf(doubleArrayOf(la, lo))
                cb(shape)
                return true
            }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); return true }
        }
        return true
    }

    private fun load(key: String) {
        synchronized(loading) { if (!loading.add(key)) return }
        io.execute {
            val bmp = try {
                val parts = key.split("/")
                val (z, x, y) = Triple(parts[1], parts[2], parts[3])
                val url = if (parts[0] == "s") "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
                    else "https://tile.openstreetmap.org/$z/$x/$y.png"
                val con = URL(url).openConnection() as HttpURLConnection
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
