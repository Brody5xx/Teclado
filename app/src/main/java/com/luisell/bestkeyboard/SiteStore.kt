package com.luisell.bestkeyboard

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** "Mis sitios": los lugares donde trabajas seguido, con su dirección y dónde están (GPS). */
object SiteStore {
    /** [rate] = pago por hora especial en este sitio (0 = el normal). */
    /**
     * [rate] = pago por hora especial en este sitio (0 = el normal).
     * [kind] = "site" (un trabajo) o "start" (🛻 donde recoges y dejas la camioneta: al salir empieza tu día, al volver termina).
     */
    class Site(val name: String, val address: String, val lat: Double, val lon: Double, val rate: Double = 0.0, val kind: String = "site",
               /** 📏 Distancia de detección de este lugar en metros (0 = la de todos). */
               val radius: Int = 0,
               /** 🔷 La forma real del sitio (las esquinas, [lat, lon]); vacía = se usa el círculo. */
               val shape: List<DoubleArray> = emptyList()) {
        val isStart get() = kind == "start"
        val hasShape get() = shape.size >= 3
    }

    /** 📏 La distancia que usan todos los lugares (si no le pusiste una propia). */
    fun defaultRadius(p: SharedPreferences): Int = (p.getString(Prefs.SITE_RADIUS, "250") ?: "250").toIntOrNull()?.coerceIn(50, 2000) ?: 250

    /** La distancia de detección de [s] en metros. */
    fun radiusOf(p: SharedPreferences, s: Site): Int = if (s.radius > 0) s.radius else defaultRadius(p)

    /** 🧠 Mueve el punto de un sitio (lo usa el aprendizaje de Mi jornada). */
    fun setPoint(p: SharedPreferences, name: String, la: Double, lo: Double) =
        save(p, all(p).map { if (it.name == name) Site(it.name, it.address, la, lo, it.rate, it.kind, it.radius, it.shape) else it })

    /** 🔷 Guarda (o borra, con una lista vacía) la forma real del sitio. */
    fun setShape(p: SharedPreferences, name: String, shape: List<DoubleArray>) =
        save(p, all(p).map { if (it.name == name) Site(it.name, it.address, it.lat, it.lon, it.rate, it.kind, it.radius, shape) else it })

    fun setRadius(p: SharedPreferences, name: String, r: Int) =
        save(p, all(p).map { if (it.name == name) Site(it.name, it.address, it.lat, it.lon, it.rate, it.kind, r, it.shape) else it })

    /** A cuántos metros cuenta como "estoy en ese sitio". */
    const val RADIUS_M = 400f

    fun all(p: SharedPreferences): List<Site> = try {
        val a = JSONArray(p.getString(Prefs.SITES, "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val sh = o.optJSONArray("sh")
            Site(o.optString("n"), o.optString("a"), o.optDouble("la", 0.0), o.optDouble("lo", 0.0), o.optDouble("r", 0.0), o.optString("k", "site"), o.optInt("rd", 0),
                if (sh == null) emptyList() else (0 until sh.length()).map { i -> sh.getJSONArray(i).let { doubleArrayOf(it.getDouble(0), it.getDouble(1)) } })
        }
    } catch (_: Exception) { emptyList() }

    private fun save(p: SharedPreferences, list: List<Site>) {
        val a = JSONArray()
        for (s in list) {
            val o = JSONObject().put("n", s.name).put("a", s.address).put("la", s.lat).put("lo", s.lon).put("r", s.rate).put("k", s.kind).put("rd", s.radius)
            if (s.shape.isNotEmpty()) o.put("sh", JSONArray().apply { for (pt in s.shape) put(JSONArray().put(pt[0]).put(pt[1])) })
            a.put(o)
        }
        p.edit().putString(Prefs.SITES, a.toString()).apply()
    }

    /** Pago por hora especial de un sitio (0 = el normal). */
    fun setRate(p: SharedPreferences, name: String, rate: Double) =
        save(p, all(p).map { if (it.name == name) Site(it.name, it.address, it.lat, it.lon, rate, it.kind, it.radius, it.shape) else it })

    /** Cambia el tipo: sitio de trabajo, o 🛻 punto de comienzo y fin del día. */
    fun setKind(p: SharedPreferences, name: String, kind: String) =
        save(p, all(p).map { if (it.name == name) Site(it.name, it.address, it.lat, it.lon, it.rate, kind, it.radius, it.shape) else it })

    fun add(p: SharedPreferences, site: Site) = save(p, all(p).filter { !it.name.equals(site.name, true) } + site)
    fun remove(p: SharedPreferences, name: String) = save(p, all(p).filter { it.name != name })

    fun find(p: SharedPreferences, name: String): Site? = all(p).firstOrNull { it.name == name }

    // ---- 🏠 Tu casa: solo las coordenadas, solo en tu teléfono (nunca sale en tickets ni en el PDF)
    fun home(p: SharedPreferences): Pair<Double, Double>? = try {
        val o = JSONObject(p.getString(Prefs.HOME_PLACE, "") ?: "")
        o.getDouble("la") to o.getDouble("lo")
    } catch (_: Exception) { null }

    private fun homeJson(p: SharedPreferences): JSONObject? = try { JSONObject(p.getString(Prefs.HOME_PLACE, "") ?: "").takeIf { it.has("la") } } catch (_: Exception) { null }

    /** Guarda tu casa. Se queda su distancia; su forma solo si el punto no se movió lejos. */
    fun setHome(p: SharedPreferences, lat: Double?, lon: Double?, address: String = "") {
        if (lat == null || lon == null) { p.edit().remove(Prefs.HOME_PLACE).apply(); return }
        val old = homeJson(p)
        val o = JSONObject().put("la", lat).put("lo", lon).put("a", address)
        if (old != null) {
            if (old.optInt("rd", 0) > 0) o.put("rd", old.optInt("rd"))
            val d = FloatArray(1)
            android.location.Location.distanceBetween(old.optDouble("la"), old.optDouble("lo"), lat, lon, d)
            if (d[0] <= 150f && old.has("sh")) o.put("sh", old.getJSONArray("sh"))
        }
        p.edit().putString(Prefs.HOME_PLACE, o.toString()).apply()
    }

    /** 📏 La distancia de detección de tu casa (150 m si no le pusiste una). */
    fun homeRadius(p: SharedPreferences): Int = homeJson(p)?.optInt("rd", 0)?.takeIf { it > 0 } ?: 150

    fun setHomeRadius(p: SharedPreferences, r: Int) {
        val o = homeJson(p) ?: return
        if (r > 0) o.put("rd", r) else o.remove("rd")
        p.edit().putString(Prefs.HOME_PLACE, o.toString()).apply()
    }

    /** 🔷 La forma de tu casa (vacía = el círculo). */
    fun setHomeShape(p: SharedPreferences, shape: List<DoubleArray>) {
        val o = homeJson(p) ?: return
        if (shape.isEmpty()) o.remove("sh") else o.put("sh", JSONArray().apply { for (pt in shape) put(JSONArray().put(pt[0]).put(pt[1])) })
        p.edit().putString(Prefs.HOME_PLACE, o.toString()).apply()
    }

    /** Tu casa como un sitio (para el mapa, la forma y la distancia). */
    fun homeSite(p: SharedPreferences, name: String = L.t("Casa", "Home")): Site? {
        val o = homeJson(p) ?: return null
        val sh = o.optJSONArray("sh")
        return Site(name, o.optString("a", ""), o.optDouble("la"), o.optDouble("lo"), kind = "home", radius = homeRadius(p),
            shape = if (sh == null) emptyList() else (0 until sh.length()).map { i -> sh.getJSONArray(i).let { doubleArrayOf(it.getDouble(0), it.getDouble(1)) } })
    }

    /** La dirección de tu casa (solo se ve en Mis sitios, nunca en tickets ni en el PDF). */
    fun homeAddress(p: SharedPreferences): String = try { JSONObject(p.getString(Prefs.HOME_PLACE, "") ?: "").optString("a", "") } catch (_: Exception) { "" }

    fun setHomeAddress(p: SharedPreferences, address: String) {
        val h = home(p) ?: return
        setHome(p, h.first, h.second, address)
    }

    /**
     * ✏️ Cambia la dirección de un sitio (y su punto, si se encontró en el mapa). Si el punto se mueve lejos,
     * se borran su forma y lo aprendido (eran del lugar de antes).
     */
    fun setAddress(p: SharedPreferences, name: String, address: String, la: Double?, lo: Double?) {
        save(p, all(p).map {
            if (it.name != name) it
            else if (la == null || lo == null) Site(it.name, address, it.lat, it.lon, it.rate, it.kind, it.radius, it.shape)
            else {
                val d = FloatArray(1)
                android.location.Location.distanceBetween(it.lat, it.lon, la, lo, d)
                val far = it.lat == 0.0 && it.lon == 0.0 || d[0] > 150f
                Site(it.name, address, la, lo, it.rate, it.kind, it.radius, if (far) emptyList() else it.shape)
            }
        })
    }

    /** Busca en el mapa dónde queda una dirección (necesita internet; llamar en segundo plano). */
    fun findAddress(ctx: android.content.Context, address: String): android.location.Address? = try {
        @Suppress("DEPRECATION")
        android.location.Geocoder(ctx, java.util.Locale.US).getFromLocationName(address, 1)?.firstOrNull()
    } catch (_: Exception) { null }

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
            if (s.isStart) continue // el parqueo de la camioneta no es un sitio de trabajo
            android.location.Location.distanceBetween(lat, lon, s.lat, s.lon, out)
            // Dentro de su área (más un margen, porque el GPS no es exacto)
            if (out[0] > radiusOf(p, s) + 120f) continue
            if (out[0] < bestD) { bestD = out[0]; best = s }
        }
        return best
    }
}
