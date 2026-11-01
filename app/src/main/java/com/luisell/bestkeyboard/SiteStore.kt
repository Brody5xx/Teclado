package com.luisell.bestkeyboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** "Mis sitios": los lugares donde trabajas seguido, con su dirección y dónde están (GPS). */
object SiteStore {
    class Site(val name: String, val address: String, val lat: Double, val lon: Double)

    /** A cuántos metros cuenta como "estoy en ese sitio". */
    const val RADIUS_M = 400f

    fun all(p: SharedPreferences): List<Site> = try {
        val a = JSONArray(p.getString(Prefs.SITES, "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Site(o.optString("n"), o.optString("a"), o.optDouble("la", 0.0), o.optDouble("lo", 0.0))
        }
    } catch (_: Exception) { emptyList() }

    private fun save(p: SharedPreferences, list: List<Site>) {
        val a = JSONArray()
        for (s in list) a.put(JSONObject().put("n", s.name).put("a", s.address).put("la", s.lat).put("lo", s.lon))
        p.edit().putString(Prefs.SITES, a.toString()).apply()
    }

    fun add(p: SharedPreferences, site: Site) = save(p, all(p).filter { !it.name.equals(site.name, true) } + site)
    fun remove(p: SharedPreferences, name: String) = save(p, all(p).filter { it.name != name })

    fun find(p: SharedPreferences, name: String): Site? = all(p).firstOrNull { it.name == name }

    /** El sitio guardado más cerca de donde estás (si estás a menos de [RADIUS_M]). */
    fun nearest(p: SharedPreferences, lat: Double, lon: Double): Site? {
        var best: Site? = null
        var bestD = Float.MAX_VALUE
        val out = FloatArray(1)
        for (s in all(p)) {
            if (s.lat == 0.0 && s.lon == 0.0) continue
            android.location.Location.distanceBetween(lat, lon, s.lat, s.lon, out)
            if (out[0] < bestD) { bestD = out[0]; best = s }
        }
        return if (bestD <= RADIUS_M) best else null
    }
}
