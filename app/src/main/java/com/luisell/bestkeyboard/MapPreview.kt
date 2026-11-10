package com.luisell.bestkeyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
 * 🗺️ El mapa de Mi jornada (como Google Maps):
 *  - 🛰️ Satélite (fotos de Esri, con las calles y sus nombres encima) o Mapa (OpenStreetMap);
 *  - 🤏 se acerca y aleja con dos dedos, se mueve arrastrando, y tiene sus botones + / − / centrar;
 *  - 🔷 la forma del sitio: tocar agrega esquinas; en modo Editar arrastras los puntos, tocas uno para
 *    elegirlo (y quitarlo) y arrastras los puntitos del medio de cada lado para agregar una esquina ahí;
 *  - 🗺️ el recorrido del día con sus marcas (Revisión del día).
 */
class MapPreview(context: Context, private val lat: Double, private val lon: Double) : View(context) {
    var radius = 250
        set(v) { field = v; invalidate() }

    /** 🔷 La forma del sitio ([lat, lon] por esquina). */
    var shape: List<DoubleArray> = emptyList()
        set(v) { field = v; if (selected >= v.size) selected = -1; invalidate() }
    /** Si no es null, se puede dibujar la forma: avisa cada vez que cambia (al soltar el dedo). */
    var onCorner: ((List<DoubleArray>) -> Unit)? = null
    /** ✏️ Modo Editar: mover los puntos, elegir uno, agregar en el medio de un lado. */
    var editMode = false
        set(v) { field = v; if (!v) selected = -1; onSelect?.invoke(selected); invalidate() }
    /** El punto elegido en modo Editar (-1 = ninguno). */
    var selected = -1
        private set
    var onSelect: ((Int) -> Unit)? = null

    /** 🗺️ El recorrido del día ([lat, lon, …]) y las marcas (llegué, salí…). */
    var path: List<DoubleArray> = emptyList()
        set(v) { field = v; invalidate() }
    class Mark(val lat: Double, val lon: Double, val label: String, val color: Int)
    var marks: List<Mark> = emptyList()
        set(v) { field = v; invalidate() }

    /** Para acercar (+1) o alejar (-1) el mapa con botones de afuera. */
    var zoomShift = 0
        set(v) { val step = v - field; field = v; zoomBy(step.toDouble()) }

    /** 🛰️ Satélite (se acuerda de tu elección). */
    var satellite = Prefs.get(context).getBoolean("map_sat", true)
        set(v) { field = v; Prefs.get(context).edit().putBoolean("map_sat", v).apply(); invalidate() }

    /** Los nombres de los botones, en el idioma de la pantalla. */
    var mapLabel = L.t("Mapa", "Map")
    var satLabel = L.t("Satélite", "Satellite")

    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val main = Handler(Looper.getMainLooper())
    private val src = Rect()
    private val dst = RectF()

    companion object {
        private val io = Executors.newFixedThreadPool(4)
        private val cache = object : LinkedHashMap<String, Bitmap>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 120
        }
        private val loading = HashSet<String>()
        /** Las que no existen (para no pedirlas otra vez y usar la de más lejos agrandada). */
        private val missing = HashSet<String>()
        private const val MAX_Z = 21.5
        private const val OSM_MAX = 19
        private const val SAT_MAX = 20
    }

    // Lo que moviste con los dedos (null = el encuadre automático)
    private var userZ: Double? = null
    private var userLa: Double? = null
    private var userLo: Double? = null
    private var lastZ = 18.0
    private var lastLa = lat
    private var lastLo = lon

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
    private var scale = d * 0.8f

    /** El centro y el zoom: con recorrido o marcas, que quepa todo; si no, el sitio (y su forma). */
    private fun frame(w: Float, h: Float): Triple<Double, Double, Double> {
        val pts = ArrayList<DoubleArray>()
        if (onCorner == null) { for (p in path) pts.add(p); for (m in marks) pts.add(doubleArrayOf(m.lat, m.lon)) }
        else for (p in shape) pts.add(p)
        if (pts.isNotEmpty()) {
            if (onCorner != null) pts.add(doubleArrayOf(lat, lon))
            val minLa = pts.minOf { it[0] }; val maxLa = pts.maxOf { it[0] }
            val minLo = pts.minOf { it[1] }; val maxLo = pts.maxOf { it[1] }
            var z = 20
            while (z > 3 && ((wx(maxLo, z) - wx(minLo, z)) * base > w * 0.7 || (wy(minLa, z) - wy(maxLa, z)) * base > h * 0.65)) z--
            return Triple((minLa + maxLa) / 2, (minLo + maxLo) / 2, minOf(z.toDouble(), 19.5))
        }
        var z = 18
        val reach = maxOf(radius.toDouble(), 60.0)
        while (z > 3 && (reach / mpp(z)) * base > minOf(w, h) * 0.36) z--
        return Triple(lat, lon, z.toDouble())
    }

    private fun sx(lo: Double) = ((wx(lo, vz) - vLeft) * scale).toFloat()
    private fun sy(la: Double) = ((wy(la, vz) - vTop) * scale).toFloat()

    /** De la pantalla a [lat, lon]. */
    private fun toLatLon(x: Float, y: Float): DoubleArray {
        val mx = vLeft + x / scale; val my = vTop + y / scale
        val nz = 256.0 * 2.0.pow(vz)
        return doubleArrayOf(Math.toDegrees(atan(kotlin.math.sinh(PI * (1 - 2 * my / nz)))), mx / nz * 360.0 - 180.0)
    }

    // ---------------------------------------------------------------- Dibujar

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        c.drawColor(0xFF1B2230.toInt())
        val (aLa, aLo, aZ) = frame(w, h)
        val zf = (userZ ?: aZ).coerceIn(3.0, MAX_Z)
        val cLa = userLa ?: aLa; val cLo = userLo ?: aLo
        lastZ = zf; lastLa = cLa; lastLo = cLo
        // 🛰️ En satélite se piden las fotos de más cerca (más detalle en la pantalla)
        val bias = if (satellite) kotlin.math.log2(base.toDouble()).coerceIn(0.0, 1.5) else 0.0
        val max = if (satellite) SAT_MAX else OSM_MAX
        val z = floor(zf + bias).toInt().coerceIn(3, max)
        scale = (base * 2.0.pow(zf - z)).toFloat()
        vz = z
        val cx = wx(cLo, z); val cy = wy(cLa, z)
        val left = cx - w / 2 / scale; val top = cy - h / 2 / scale
        vLeft = left; vTop = top
        if (satellite) {
            drawLayer(c, "s", z, scale, left, top, w, h)
            // Las calles y sus nombres: a su tamaño normal (sin pedirlas de más cerca, así las letras no salen chiquitas)
            val lz = floor(zf + 0.35).toInt().coerceIn(3, OSM_MAX)
            val ls = (base * 2.0.pow(zf - lz)).toFloat()
            drawLayer(c, "t", lz, ls, wx(cLo, lz) - w / 2 / ls, wy(cLa, lz) - h / 2 / ls, w, h)
        } else drawLayer(c, "o", z, scale, left, top, w, h)
        if (!satellite) c.drawColor(0x22000000)
        // Un degradado suave arriba y abajo (para que los botones y el crédito se lean)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, intArrayOf(0x55000000, 0x00000000, 0x00000000, 0x66000000),
            floatArrayOf(0f, 0.18f, 0.8f, 1f), android.graphics.Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val hasRoute = onCorner == null && (path.isNotEmpty() || marks.isNotEmpty())
        if (!hasRoute) drawSite(c, z)
        else drawRoute(c)
        drawControls(c, w, h)
    }

    /** Cuándo llegó cada foto (para que aparezca suave, no de golpe). */
    private val arrived = HashMap<String, Long>()

    /**
     * Una capa de fotos (s = satélite, t = calles encima, o = mapa). Los cuadros se pegan sin rayitas entre ellos;
     * si uno no llegó (o no existe tan de cerca), se ve el de más lejos agrandado, y el nuevo aparece suave encima.
     */
    private fun drawLayer(c: Canvas, layer: String, z: Int, sc: Float, left: Double, top: Double, w: Float, h: Float) {
        val tilePx = 256f * sc
        val tx0 = floor(left / 256).toInt(); val ty0 = floor(top / 256).toInt()
        val tx1 = floor((left + w / sc) / 256).toInt(); val ty1 = floor((top + h / sc) / 256).toInt()
        val n = 1 shl z
        val full = if (layer == "t") 235 else 255
        val now = android.os.SystemClock.uptimeMillis()
        var fading = false
        for (tx in tx0..tx1) for (ty in ty0..ty1) {
            if (ty < 0 || ty >= n) continue
            val x = ((tx % n) + n) % n
            val px = ((tx * 256 - left) * sc).toFloat(); val py = ((ty * 256 - top) * sc).toFloat()
            // Bordes en píxeles enteros y 1 px de más: así no se ven líneas finas entre los cuadros
            dst.set(floor(px), floor(py), kotlin.math.ceil(px + tilePx) + 1f, kotlin.math.ceil(py + tilePx) + 1f)
            val key = "$layer/$z/$x/$ty"
            val b = synchronized(cache) { cache[key] }
            val since = arrived[key]
            val a = if (b == null) 0f else if (since == null) 1f else ((now - since) / 260f).coerceIn(0f, 1f)
            if (b == null && synchronized(missing) { key !in missing }) load(key)
            // Debajo (mientras llega o mientras aparece): la de más lejos, agrandada
            if (a < 1f && layer != "t") for (up in 1..5) {
                val pz = z - up
                if (pz < 3) break
                val f = 1 shl up
                val pk = "$layer/$pz/${x / f}/${ty / f}"
                val pb = synchronized(cache) { cache[pk] } ?: continue
                val sub = pb.width.toFloat() / f
                val ox = (x % f) * sub; val oy = (ty % f) * sub
                src.set(ox.toInt(), oy.toInt(), (ox + sub).toInt(), (oy + sub).toInt())
                paint.alpha = full
                c.drawBitmap(pb, src, dst, paint)
                break
            }
            if (b != null) {
                if (a < 1f) fading = true
                paint.alpha = (full * a).toInt()
                src.set(0, 0, b.width, b.height)
                c.drawBitmap(b, src, dst, paint)
            }
        }
        paint.alpha = 255
        if (fading) postInvalidateOnAnimation()
    }

    private fun drawSite(c: Canvas, z: Int) {
        val ox = sx(lon); val oy = sy(lat)
        val rpx = (radius / mpp(z) * scale).toFloat()
        val drawing = onCorner != null
        if (shape.size >= 2 || drawing) {
            // Con forma: el círculo queda tenue, de guía
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f * d; paint.color = 0x8822D3EE.toInt()
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f * d, 5f * d), 0f)
            c.drawCircle(ox, oy, rpx, paint)
            paint.pathEffect = null
        } else {
            paint.style = Paint.Style.FILL; paint.color = 0x3322D3EE
            c.drawCircle(ox, oy, rpx, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f * d; paint.color = 0xFF22D3EE.toInt()
            c.drawCircle(ox, oy, rpx, paint)
        }
        // 🔷 La forma del sitio
        if (shape.isNotEmpty()) {
            val pa = Path()
            for ((i, pt) in shape.withIndex()) { val x = sx(pt[1]); val y = sy(pt[0]); if (i == 0) pa.moveTo(x, y) else pa.lineTo(x, y) }
            if (shape.size >= 3) {
                pa.close()
                paint.style = Paint.Style.FILL; paint.color = 0x4D8B5CF6
                c.drawPath(pa, paint)
            }
            // Borde con un halo blanco (se ve bien sobre el satélite)
            paint.style = Paint.Style.STROKE; paint.strokeJoin = Paint.Join.ROUND
            paint.strokeWidth = 5f * d; paint.color = 0x66FFFFFF
            c.drawPath(pa, paint)
            paint.strokeWidth = 2.5f * d; paint.color = 0xFFA78BFA.toInt()
            c.drawPath(pa, paint)
            // ✏️ Los puntitos del medio de cada lado: arrástralos para agregar una esquina ahí
            if (editMode && shape.size >= 2) {
                val sides = if (shape.size >= 3) shape.size else 1
                for (i in 0 until sides) {
                    val a = shape[i]; val b = shape[(i + 1) % shape.size]
                    val mx = (sx(a[1]) + sx(b[1])) / 2; val my = (sy(a[0]) + sy(b[0])) / 2
                    paint.style = Paint.Style.FILL; paint.color = 0xCCFFFFFF.toInt(); c.drawCircle(mx, my, 6f * d, paint)
                    paint.color = 0xFF8B5CF6.toInt()
                    paint.strokeWidth = 1.6f * d; paint.style = Paint.Style.STROKE
                    c.drawLine(mx - 3f * d, my, mx + 3f * d, my, paint); c.drawLine(mx, my - 3f * d, mx, my + 3f * d, paint)
                }
            }
            // Las esquinas
            for ((i, pt) in shape.withIndex()) {
                val x = sx(pt[1]); val y = sy(pt[0])
                val sel = i == selected
                val r = if (sel) 11f * d else if (editMode) 8.5f * d else 6.5f * d
                paint.style = Paint.Style.FILL
                paint.color = 0x55000000; c.drawCircle(x, y + 1.5f * d, r + 1.5f * d, paint) // sombrita
                paint.color = if (sel) 0xFF22D3EE.toInt() else 0xFFFFFFFF.toInt(); c.drawCircle(x, y, r, paint)
                paint.color = if (sel) 0xFFFFFFFF.toInt() else 0xFF8B5CF6.toInt(); c.drawCircle(x, y, r * 0.55f, paint)
            }
        }
        // 📍 El punto del lugar (un pin)
        drawPin(c, ox, oy)
    }

    private fun drawPin(c: Canvas, x: Float, y: Float) {
        val r = 9f * d
        val pin = Path().apply {
            moveTo(x, y)
            cubicTo(x - r * 0.4f, y - r * 0.9f, x - r, y - r * 1.4f, x - r, y - r * 2.1f)
            arcTo(RectF(x - r, y - r * 3.1f, x + r, y - r * 1.1f), 180f, 180f)
            cubicTo(x + r, y - r * 1.4f, x + r * 0.4f, y - r * 0.9f, x, y)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = 0x55000000; c.save(); c.translate(0f, 1.5f * d); c.drawPath(pin, paint); c.restore()
        paint.shader = android.graphics.LinearGradient(x, y - r * 3f, x, y, 0xFF22D3EE.toInt(), 0xFF8B5CF6.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawPath(pin, paint)
        paint.shader = null
        paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y - r * 2.1f, r * 0.38f, paint)
    }

    private fun drawRoute(c: Canvas) {
        // 🗺️ El recorrido: una línea suave que une los puntos
        if (path.size >= 2) {
            val pa = Path()
            for ((i, pt) in path.withIndex()) { val x = sx(pt[1]); val y = sy(pt[0]); if (i == 0) pa.moveTo(x, y) else pa.lineTo(x, y) }
            paint.style = Paint.Style.STROKE; paint.strokeJoin = Paint.Join.ROUND; paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = 6f * d; paint.color = 0x66FFFFFF; c.drawPath(pa, paint)
            paint.strokeWidth = 3.5f * d; paint.color = 0xFF22D3EE.toInt(); c.drawPath(pa, paint)
        }
        paint.style = Paint.Style.FILL
        for (pt in path) { paint.color = 0xFFFFFFFF.toInt(); c.drawCircle(sx(pt[1]), sy(pt[0]), 2.5f * d, paint) }
        // Las marcas con su hora
        paint.textSize = 11f * d
        for (m in marks) {
            val x = sx(m.lon); val y = sy(m.lat)
            paint.color = 0x55000000; c.drawCircle(x, y + 1.5f * d, 9.5f * d, paint)
            paint.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, 8.5f * d, paint)
            paint.color = m.color; c.drawCircle(x, y, 6f * d, paint)
            if (m.label.isNotEmpty()) {
                val tw = paint.measureText(m.label)
                paint.color = 0xE610141C.toInt()
                c.drawRoundRect(x + 11f * d, y - 10f * d, x + 21f * d + tw, y + 9f * d, 8f * d, 8f * d, paint)
                paint.color = 0xFFFFFFFF.toInt()
                c.drawText(m.label, x + 16f * d, y + 4f * d, paint)
            }
        }
    }

    // ---------------------------------------------------------------- Botones sobre el mapa

    private val chip = RectF()
    private val zIn = RectF()
    private val zOut = RectF()
    private val center = RectF()

    private fun drawControls(c: Canvas, w: Float, h: Float) {
        // Crédito del mapa (lo piden Esri y OpenStreetMap)
        paint.style = Paint.Style.FILL
        paint.textSize = 9f * d
        paint.color = 0xDDFFFFFF.toInt()
        c.drawText(if (satellite) "Imagery © Esri, Maxar, Earthstar Geographics" else "© OpenStreetMap", 8f * d, h - 6f * d, paint)
        drawScale(c, h - 14f * d)
        // 🛰️ «Satélite / Mapa» arriba a la derecha
        paint.textSize = 12.5f * d
        val label = if (satellite) mapLabel else satLabel
        val tw = paint.measureText(label)
        chip.set(w - tw - 32f * d, 10f * d, w - 10f * d, 42f * d)
        glass(c, chip, 16f * d)
        paint.style = Paint.Style.FILL; paint.color = 0xFFFFFFFF.toInt()
        c.drawText(label, chip.left + 11f * d, chip.centerY() + 4.5f * d, paint)
        // 🤏 + y − abajo a la derecha (como Google), y el botón de centrar encima
        val bw = 40f * d
        val right = w - 10f * d
        zOut.set(right - bw, h - 26f * d - bw, right, h - 26f * d)
        zIn.set(right - bw, zOut.top - bw, right, zOut.top)
        val pill = RectF(zIn.left, zIn.top, zOut.right, zOut.bottom)
        glass(c, pill, 14f * d)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * d; paint.color = 0xFFFFFFFF.toInt(); paint.strokeCap = Paint.Cap.ROUND
        val s = 7f * d
        c.drawLine(zIn.centerX() - s, zIn.centerY(), zIn.centerX() + s, zIn.centerY(), paint)
        c.drawLine(zIn.centerX(), zIn.centerY() - s, zIn.centerX(), zIn.centerY() + s, paint)
        c.drawLine(zOut.centerX() - s, zOut.centerY(), zOut.centerX() + s, zOut.centerY(), paint)
        paint.strokeWidth = 1f * d; paint.color = 0x44FFFFFF
        c.drawLine(pill.left + 8f * d, zIn.bottom, pill.right - 8f * d, zIn.bottom, paint)
        center.set(right - bw, zIn.top - 8f * d - bw, right, zIn.top - 8f * d)
        glass(c, center, 20f * d)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f * d; paint.color = 0xFF22D3EE.toInt()
        c.drawCircle(center.centerX(), center.centerY(), 7f * d, paint)
        paint.style = Paint.Style.FILL
        c.drawCircle(center.centerX(), center.centerY(), 3f * d, paint)
        paint.style = Paint.Style.STROKE
        for (k in 0 until 4) {
            val a = Math.toRadians(90.0 * k)
            val x1 = center.centerX() + (cos(a) * 9f * d).toFloat(); val y1 = center.centerY() + (kotlin.math.sin(a) * 9f * d).toFloat()
            val x2 = center.centerX() + (cos(a) * 12f * d).toFloat(); val y2 = center.centerY() + (kotlin.math.sin(a) * 12f * d).toFloat()
            c.drawLine(x1, y1, x2, y2, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
    }

    /** 📏 La escala (como Google): una rayita con «50 m» arriba y «200 ft» abajo. */
    private fun drawScale(c: Canvas, h: Float) {
        val mPerPx = mpp(vz, lastLa) / scale
        val maxPx = 90f * d
        fun nice(v: Double): Double {
            val e = 10.0.pow(floor(kotlin.math.log10(v)))
            val f = v / e
            return e * when { f >= 5 -> 5.0; f >= 2 -> 2.0; else -> 1.0 }
        }
        val m = nice(mPerPx * maxPx); val ft = nice(mPerPx * maxPx * 3.28084)
        val mPx = (m / mPerPx).toFloat(); val ftPx = (ft / 3.28084 / mPerPx).toFloat()
        val x0 = 10f * d; val y = h - 24f * d
        val mLabel = if (m >= 1000) "${(m / 1000).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }} km" else "${m.toInt()} m"
        val ftLabel = if (ft >= 5280) "${(ft / 5280).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }} mi" else "${ft.toInt()} ft"
        paint.textSize = 10f * d
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = 4f * d; paint.color = 0x99000000.toInt()
        c.drawLine(x0, y, x0 + maxOf(mPx, ftPx), y, paint)
        paint.strokeWidth = 2f * d; paint.color = 0xFFFFFFFF.toInt()
        c.drawLine(x0, y, x0 + maxOf(mPx, ftPx), y, paint)
        c.drawLine(x0 + mPx, y - 5f * d, x0 + mPx, y, paint)
        c.drawLine(x0 + ftPx, y, x0 + ftPx, y + 5f * d, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
        paint.setShadowLayer(3f * d, 0f, 0f, 0xCC000000.toInt())
        c.drawText(mLabel, x0 + 2f * d, y - 6f * d, paint)
        c.drawText(ftLabel, x0 + 2f * d, y + 14f * d, paint)
        paint.clearShadowLayer()
    }

    /** Un botón de vidrio oscuro con borde fino del color del tema. */
    private fun glass(c: Canvas, r: RectF, rad: Float) {
        paint.style = Paint.Style.FILL; paint.color = 0xE010141C.toInt()
        c.drawRoundRect(r, rad, rad, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f * d; paint.color = 0x8822D3EE.toInt()
        c.drawRoundRect(r, rad, rad, paint)
    }

    // ---------------------------------------------------------------- Dedos

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var pinched = false
    /** La esquina que estás arrastrando (-1 = ninguna). */
    private var dragIdx = -1
    private var dragChanged = false

    /** 🤏 Dos dedos: acercar y alejar (alrededor de donde están los dedos). */
    private val pinch = android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(g: android.view.ScaleGestureDetector): Boolean {
            pinched = true
            val before = lastZ
            val nz = (before + kotlin.math.log2(g.scaleFactor.toDouble())).coerceIn(3.0, MAX_Z)
            val fx = g.focusX - width / 2f; val fy = g.focusY - height / 2f
            val k = 1.0 - 1.0 / 2.0.pow(nz - before)
            panBy(-fx * k.toFloat(), -fy * k.toFloat())
            userZ = nz; lastZ = nz
            invalidate()
            return true
        }
    })

    private fun zoomBy(step: Double) {
        userZ = ((userZ ?: lastZ) + step).coerceIn(3.0, MAX_Z)
        if (userLa == null) { userLa = lastLa; userLo = lastLo }
        invalidate()
    }

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

    /** La esquina que está debajo del dedo (o -1). */
    private fun hitCorner(x: Float, y: Float): Int {
        var best = -1; var bd = (24f * d) * (24f * d)
        for ((i, pt) in shape.withIndex()) {
            val dx = sx(pt[1]) - x; val dy = sy(pt[0]) - y
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = i }
        }
        return best
    }

    /** El puntito del medio de un lado (devuelve el lado, o -1). */
    private fun hitMid(x: Float, y: Float): Int {
        if (shape.size < 2) return -1
        val sides = if (shape.size >= 3) shape.size else 1
        for (i in 0 until sides) {
            val a = shape[i]; val b = shape[(i + 1) % shape.size]
            val mx = (sx(a[1]) + sx(b[1])) / 2; val my = (sy(a[0]) + sy(b[0])) / 2
            if ((mx - x) * (mx - x) + (my - y) * (my - y) < (20f * d) * (20f * d)) return i
        }
        return -1
    }

    private fun select(i: Int) { selected = i; onSelect?.invoke(i); invalidate() }

    /** ✏️ Quita la esquina elegida. */
    fun removeSelected() {
        if (selected !in shape.indices) return
        val idx = selected
        select(-1)
        shape = shape.filterIndexed { i, _ -> i != idx }
        onCorner?.invoke(shape)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        pinch.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y; moved = false; pinched = false; dragChanged = false
                dragIdx = -1
                parent?.requestDisallowInterceptTouchEvent(true)
                if (onCorner != null && editMode) {
                    dragIdx = hitCorner(e.x, e.y)
                    if (dragIdx < 0) {
                        // Del medio de un lado: nace una esquina nueva ahí y la arrastras
                        val side = hitMid(e.x, e.y)
                        if (side >= 0) {
                            val a = shape[side]; val b = shape[(side + 1) % shape.size]
                            val mid = doubleArrayOf((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
                            val list = shape.toMutableList(); list.add(side + 1, mid)
                            shape = list
                            dragIdx = side + 1; dragChanged = true
                        }
                    }
                    if (dragIdx >= 0) select(dragIdx)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> { moved = true; if (dragIdx >= 0 && dragChanged) onCorner?.invoke(shape); dragIdx = -1; return true }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount == 1 && !pinch.isInProgress) {
                    if (!moved && (Math.abs(e.x - downX) > 8 * d || Math.abs(e.y - downY) > 8 * d)) moved = true
                    if (moved && !pinched) {
                        if (dragIdx >= 0) {
                            // Mueve la esquina con el dedo
                            val list = shape.toMutableList(); list[dragIdx] = toLatLon(e.x, e.y)
                            shape = list; dragChanged = true
                        } else { panBy(e.x - lastX, e.y - lastY); invalidate() }
                    }
                }
                lastX = e.x; lastY = e.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragIdx >= 0) { if (dragChanged) onCorner?.invoke(shape); dragIdx = -1; return true }
                if (moved || pinched) return true
                // Un toque: los botones del mapa, o una esquina nueva
                when {
                    chip.contains(e.x, e.y) -> { satellite = !satellite; return true }
                    zIn.contains(e.x, e.y) -> { zoomBy(1.0); return true }
                    zOut.contains(e.x, e.y) -> { zoomBy(-1.0); return true }
                    center.contains(e.x, e.y) -> { userLa = null; userLo = null; userZ = null; invalidate(); return true }
                }
                val cb = onCorner ?: return true
                if (editMode) { select(hitCorner(e.x, e.y)); return true }
                shape = shape + listOf(toLatLon(e.x, e.y))
                cb(shape)
                return true
            }
            MotionEvent.ACTION_CANCEL -> { parent?.requestDisallowInterceptTouchEvent(false); dragIdx = -1; return true }
        }
        return true
    }

    // ---------------------------------------------------------------- Bajar las fotos

    private fun load(key: String) {
        synchronized(loading) { if (!loading.add(key)) return }
        io.execute {
            val parts = key.split("/")
            val (z, x, y) = Triple(parts[1], parts[2], parts[3])
            val url = when (parts[0]) {
                "s" -> "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
                "t" -> "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/$z/$y/$x"
                else -> "https://tile.openstreetmap.org/$z/$x/$y.png"
            }
            var notThere = false
            var bmp = try {
                val con = URL(url).openConnection() as HttpURLConnection
                con.setRequestProperty("User-Agent", "BestKeyboard/15 (Android keyboard app)")
                con.connectTimeout = 8000; con.readTimeout = 8000
                if (con.responseCode in 400..499) { notThere = true; null }
                else if (con.responseCode !in 200..299) null else con.inputStream.use { BitmapFactory.decodeStream(it) }
            } catch (_: Throwable) { null }
            // Esri manda una imagen gris («todavía no hay foto») donde no tiene tan de cerca: no sirve
            if (bmp != null && parts[0] == "s" && looksEmpty(bmp)) { bmp = null; notThere = true }
            if (bmp != null) { synchronized(cache) { cache[key] = bmp }; main.post { arrived[key] = android.os.SystemClock.uptimeMillis() } }
            else if (notThere) synchronized(missing) { missing.add(key) }
            synchronized(loading) { loading.remove(key) }
            main.post { invalidate() }
        }
    }

    /** ¿Es la imagen gris de «no hay foto aquí»? (casi todos los puntos del mismo gris claro) */
    private fun looksEmpty(b: Bitmap): Boolean {
        var gray = 0; var n = 0
        val step = maxOf(1, b.width / 9)
        var yy = step / 2
        while (yy < b.height) {
            var xx = step / 2
            while (xx < b.width) {
                val c = b.getPixel(xx, yy)
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
                if (Math.abs(r - g) < 6 && Math.abs(g - bl) < 6 && r in 180..235) gray++
                n++; xx += step
            }
            yy += step
        }
        return n > 0 && gray * 100 / n > 85
    }
}
