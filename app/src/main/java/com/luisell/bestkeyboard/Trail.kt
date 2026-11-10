package com.luisell.bestkeyboard

import android.content.Context
import android.location.Location
import org.json.JSONArray

/**
 * 🗺️ Los puntos donde el GPS te ubicó durante el día (cerca de tus sitios, al llegar y al salir).
 * Solo en tu teléfono, se borran solos después de 8 días. Sirven para la «Revisión del día».
 */
object Trail {
    private fun key(t: Long) = "trail_" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(t))

    fun add(ctx: Context, l: Location?) {
        if (l == null || l.accuracy > 80f) return
        val p = Prefs.get(ctx)
        val now = System.currentTimeMillis()
        val t = l.time.takeIf { it in (now - 30 * 60_000L)..(now + 5_000L) } ?: now
        val k = key(t)
        val a = try { JSONArray(p.getString(k, "[]")) } catch (_: Exception) { JSONArray() }
        if (a.length() > 0) {
            val last = a.getJSONArray(a.length() - 1)
            val d = FloatArray(1)
            Location.distanceBetween(last.getDouble(0), last.getDouble(1), l.latitude, l.longitude, d)
            // Casi el mismo lugar y hace poco: no hace falta otro punto
            if (d[0] < 20f && t - last.getLong(3) < 2 * 60_000L) return
        }
        a.put(JSONArray().put(l.latitude).put(l.longitude).put(l.accuracy.toDouble()).put(t))
        while (a.length() > 400) a.remove(0)
        val e = p.edit().putString(k, a.toString())
        // Se borran los días de hace más de 8 días
        val days = (p.getString("trail_days", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
        if (k !in days) days.add(k)
        while (days.size > 8) e.remove(days.removeAt(0))
        e.putString("trail_days", days.joinToString(",")).apply()
    }

    /** Los puntos de un día: [lat, lon, precisión, hora]. */
    fun day(ctx: Context, day: Long): List<DoubleArray> {
        val a = try { JSONArray(Prefs.get(ctx).getString(key(day), "[]")) } catch (_: Exception) { return emptyList() }
        return (0 until a.length()).map { i -> a.getJSONArray(i).let { doubleArrayOf(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getLong(3).toDouble()) } }
    }
}
