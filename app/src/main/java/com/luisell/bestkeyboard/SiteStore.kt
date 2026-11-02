package com.luisell.bestkeyboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** "Mis sitios": los lugares donde trabajas seguido, con su dirección y dónde están (GPS). */
object SiteStore {
    /** [rate] = pago por hora especial en este sitio (0 = el normal). */
    class Site(val name: String, val address: String, val lat: Double, val lon: Double, val rate: Double = 0.0)

    /** A cuántos metros cuenta como "estoy en ese sitio". */
    const val RADIUS_M = 400f

    fun all(p: SharedPreferences): List<Site> = try {
        val a = JSONArray(p.getString(Prefs.SITES, "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Site(o.optString("n"), o.optString("a"), o.optDouble("la", 0.0), o.optDouble("lo", 0.0), o.optDouble("r", 0.0))
        }
    } catch (_: Exception) { emptyList() }

    private fun save(p: SharedPreferences, list: List<Site>) {
        val a = JSONArray()
        for (s in list) a.put(JSONObject().put("n", s.name).put("a", s.address).put("la", s.lat).put("lo", s.lon).put("r", s.rate))
        p.edit().putString(Prefs.SITES, a.toString()).apply()
    }

    /** Pago por hora especial de un sitio (0 = el normal). */
    fun setRate(p: SharedPreferences, name: String, rate: Double) =
        save(p, all(p).map { if (it.name == name) Site(it.name, it.address, it.lat, it.lon, rate) else it })

    fun add(p: SharedPreferences, site: Site) = save(p, all(p).filter { !it.name.equals(site.name, true) } + site)
    fun remove(p: SharedPreferences, name: String) = save(p, all(p).filter { it.name != name })

    fun find(p: SharedPreferences, name: String): Site? = all(p).firstOrNull { it.name == name }

    // ---- 🏠 Tu casa: solo las coordenadas, solo en tu teléfono (nunca sale en tickets ni en el PDF)
    fun home(p: SharedPreferences): Pair<Double, Double>? = try {
        val o = JSONObject(p.getString(Prefs.HOME_PLACE, "") ?: "")
        o.getDouble("la") to o.getDouble("lo")
    } catch (_: Exception) { null }

    fun setHome(p: SharedPreferences, lat: Double?, lon: Double?) {
        if (lat == null || lon == null) p.edit().remove(Prefs.HOME_PLACE).apply()
        else p.edit().putString(Prefs.HOME_PLACE, JSONObject().put("la", lat).put("lo", lon).toString()).apply()
    }

    // ---- Lugar nuevo: llegaste a un sitio que no tienes guardado (se pregunta si lo guardas)
    class NewPlace(val time: Long, val short: String, val full: String, val lat: Double, val lon: Double)

    fun setNew(p: SharedPreferences, n: NewPlace?) {
        if (n == null) { p.edit().remove(Prefs.SITE_NEW).apply(); return }
        p.edit().putString(Prefs.SITE_NEW, JSONObject().put("t", n.time).put("s", n.short).put("f", n.full)
            .put("la", n.lat).put("lo", n.lon).toString()).apply()
    }

    /** El lugar nuevo de hoy (o null). */
    fun getNew(p: SharedPreferences): NewPlace? = try {
        val o = JSONObject(p.getString(Prefs.SITE_NEW, "") ?: "")
        val n = NewPlace(o.optLong("t"), o.optString("s"), o.optString("f"), o.optDouble("la"), o.optDouble("lo"))
        if (WorkStore.sameDay(n.time, System.currentTimeMillis())) n else { setNew(p, null); null }
    } catch (_: Exception) { null }

    /** "123 Main St, Springfield, USA" → "123 Main St". */
    fun shortAddress(full: String): String = full.substringBefore(",").trim().ifEmpty { full }

    /** Busca la dirección de un punto del GPS (necesita internet; llamar en segundo plano). */
    fun addressOf(ctx: android.content.Context, lat: Double, lon: Double): String = try {
        @Suppress("DEPRECATION")
        android.location.Geocoder(ctx, java.util.Locale.US).getFromLocation(lat, lon, 1)?.firstOrNull()?.getAddressLine(0) ?: ""
    } catch (_: Exception) { "" }

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
