package com.luisell.bestkeyboard

import android.content.SharedPreferences
import android.location.Location
import kotlin.math.cos
import kotlin.math.sqrt

/** 📐 Cuentas de mapa: si un punto está dentro de la forma de un sitio y a cuántos metros de su borde. */
object Geo {

    /** ¿El punto está dentro de la forma? (la cuenta del rayo) */
    fun inPoly(la: Double, lo: Double, poly: List<DoubleArray>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val yi = poly[i][0]; val xi = poly[i][1]; val yj = poly[j][0]; val xj = poly[j][1]
            if ((yi > la) != (yj > la) && lo < (xj - xi) * (la - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    /** A cuántos metros está el punto del borde más cercano de la forma. */
    fun edgeDist(la: Double, lo: Double, poly: List<DoubleArray>): Float {
        val kx = 111320.0 * cos(Math.toRadians(la)); val ky = 110540.0
        var best = Double.MAX_VALUE
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[(i + 1) % poly.size]
            val ax = (a[1] - lo) * kx; val ay = (a[0] - la) * ky
            val bx = (b[1] - lo) * kx; val by = (b[0] - la) * ky
            val dx = bx - ax; val dy = by - ay
            val len = dx * dx + dy * dy
            val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
            val px = ax + t * dx; val py = ay + t * dy
            best = minOf(best, sqrt(px * px + py * py))
        }
        return best.toFloat()
    }

    /** Qué tan lejos llega la forma desde el punto del sitio (para el círculo que vigila el teléfono). */
    fun reach(s: SiteStore.Site): Float {
        val d = FloatArray(1)
        return s.shape.maxOfOrNull { Location.distanceBetween(s.lat, s.lon, it[0], it[1], d); d[0] } ?: 0f
    }

    /**
     * ¿Estás en el sitio? true = sí (con margen), false = claramente afuera, null = en la orilla (no se sabe).
     * Usa la forma si la tiene; si no, el círculo.
     */
    fun where(p: SharedPreferences, s: SiteStore.Site, la: Double, lo: Double, acc: Float): Boolean? {
        val a = minOf(acc, 40f)
        if (s.hasShape) {
            val inP = inPoly(la, lo, s.shape)
            val e = edgeDist(la, lo, s.shape)
            return when {
                inP && e >= minOf(a * 0.5f, 10f) -> true
                !inP && e > 15f + a * 0.5f -> false
                inP -> true
                else -> null
            }
        }
        val r = SiteStore.radiusOf(p, s).toFloat()
        val d = FloatArray(1)
        Location.distanceBetween(s.lat, s.lon, la, lo, d)
        return when {
            d[0] + a * 0.5f <= r -> true
            d[0] - a * 0.5f > r + 15f -> false
            else -> null
        }
    }

    /** El radio del círculo que vigila el teléfono: el de siempre, o lo que haga falta para cubrir la forma. */
    fun fenceRadius(p: SharedPreferences, s: SiteStore.Site): Float =
        if (s.hasShape) maxOf(80f, reach(s) + 40f) else SiteStore.radiusOf(p, s).toFloat()
}
