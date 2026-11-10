package com.luisell.bestkeyboard

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🧠 Mi jornada aprende de cada sitio:
 *  - 📍 dónde llegas de verdad (la entrada, el parqueo): con varias visitas mueve el punto del sitio ahí;
 *  - 📶 las redes Wi-Fi que se ven ahí (sin conectarse), para saber que estás adentro aunque el GPS falle;
 *  - ↩️ los «Deshacer» (llegadas falsas): ese sitio espera un poco más antes de anotar;
 *  - 🕐 tus correcciones de hora: si siempre corriges unos minutos, lo aplica solo.
 * Todo se guarda solo en tu teléfono.
 */
object SiteLearn {

    private const val KEY = "site_learn"

    private fun all(p: SharedPreferences): JSONObject = try { JSONObject(p.getString(KEY, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
    private fun of(o: JSONObject, site: String): JSONObject = o.optJSONObject(site) ?: JSONObject().also { o.put(site, it) }
    private fun save(p: SharedPreferences, o: JSONObject) = p.edit().putString(KEY, o.toString()).apply()

    // ---------------------------------------------------------------- 📍 Dónde llegas

    /** Guarda dónde estabas al confirmar la llegada; con 3 o más visitas parecidas, mueve el punto del sitio. */
    fun learnPoint(ctx: Context, site: String, l: Location) {
        if (l.accuracy > 35f) return
        val p = Prefs.get(ctx)
        val s = SiteStore.find(p, site) ?: return
        if (s.lat == 0.0 && s.lon == 0.0) return
        val o = all(p)
        val me = of(o, site)
        val pts = me.optJSONArray("pts") ?: JSONArray()
        pts.put(JSONArray().put(l.latitude).put(l.longitude).put(l.accuracy.toDouble()))
        while (pts.length() > 10) pts.remove(0)
        me.put("pts", pts)
        if (!me.has("oLa")) { me.put("oLa", s.lat); me.put("oLo", s.lon) }
        me.put("visits", me.optInt("visits", 0) + 1)
        save(p, o)
        if (pts.length() < 3) return
        // El centro de las últimas visitas (las más precisas pesan más)
        var wSum = 0.0; var la = 0.0; var lo = 0.0
        val list = (0 until pts.length()).map { pts.getJSONArray(it) }.takeLast(8)
        for (a in list) { val w = 1.0 / maxOf(5.0, a.getDouble(2)); wSum += w; la += a.getDouble(0) * w; lo += a.getDouble(1) * w }
        la /= wSum; lo /= wSum
        val d = FloatArray(1)
        // Que sean parecidas (todas a menos de 80 m del centro)
        val spread = list.maxOf { Location.distanceBetween(la, lo, it.getDouble(0), it.getDouble(1), d); d[0] }
        if (spread > 80f) return
        // Que el nuevo punto esté dentro del círculo de antes (no se va a otro lugar)
        val r = SiteStore.radiusOf(p, s)
        Location.distanceBetween(me.optDouble("oLa", s.lat), me.optDouble("oLo", s.lon), la, lo, d)
        if (d[0] > r * 0.8f) return
        Location.distanceBetween(s.lat, s.lon, la, lo, d)
        if (d[0] < 15f) return
        SiteStore.setPoint(p, site, la, lo)
        AutoSites.register(ctx)
    }

    /** Si el punto se movió solo, vuelve al que guardaste tú. */
    fun restorePoint(ctx: Context, site: String): Boolean {
        val p = Prefs.get(ctx)
        val o = all(p)
        val me = o.optJSONObject(site) ?: return false
        if (!me.has("oLa")) return false
        SiteStore.setPoint(p, site, me.getDouble("oLa"), me.getDouble("oLo"))
        me.remove("pts"); me.remove("oLa"); me.remove("oLo")
        save(p, o)
        AutoSites.register(ctx)
        return true
    }

    fun moved(p: SharedPreferences, site: String): Boolean {
        val me = all(p).optJSONObject(site) ?: return false
        val s = SiteStore.find(p, site) ?: return false
        return me.has("oLa") && (me.optDouble("oLa") != s.lat || me.optDouble("oLo") != s.lon)
    }

    // ---------------------------------------------------------------- 📶 Wi-Fi del sitio

    @Suppress("DEPRECATION")
    private fun scan(ctx: Context): List<android.net.wifi.ScanResult> = try {
        if (!GpsCheck.precise(ctx)) emptyList() else {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val now = android.os.SystemClock.elapsedRealtime() * 1000L
            // Solo las que se vieron en los últimos 3 minutos
            wm.scanResults.filter { now - it.timestamp < 3 * 60_000_000L }
        }
    } catch (_: Throwable) { emptyList() }

    /** Pide al teléfono que busque redes (Android lo limita; si no puede, usa la última búsqueda). */
    @Suppress("DEPRECATION")
    fun requestScan(ctx: Context) {
        try { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).startScan() } catch (_: Throwable) { }
    }

    /** Estás en el sitio: guarda las redes que se ven (las más fuertes). */
    fun learnWifi(ctx: Context, site: String) {
        val res = scan(ctx).sortedByDescending { it.level }.take(15)
        if (res.isEmpty()) return
        val p = Prefs.get(ctx)
        val o = all(p)
        val me = of(o, site)
        val w = me.optJSONObject("wifi") ?: JSONObject()
        for (r in res) {
            val b = r.BSSID ?: continue
            w.put(b, w.optInt(b, 0) + 1)
        }
        // Máximo 40 redes: se quedan las que más se repiten
        if (w.length() > 40) {
            val keep = w.keys().asSequence().toList().sortedByDescending { w.optInt(it) }.take(40).toSet()
            for (k in w.keys().asSequence().toList()) if (k !in keep) w.remove(k)
        }
        me.put("wifi", w)
        save(p, o)
    }

    /** ¿Las redes que se ven ahora son las de ese sitio? true = sí, false = no, null = no se sabe. */
    fun wifiMatch(ctx: Context, site: String): Boolean? {
        val p = Prefs.get(ctx)
        val w = all(p).optJSONObject(site)?.optJSONObject("wifi") ?: return null
        val known = w.keys().asSequence().filter { w.optInt(it) >= 2 }.toSet()
        if (known.size < 2) return null
        val now = scan(ctx)
        if (now.isEmpty()) return null
        val hits = now.filter { it.BSSID in known }
        return hits.size >= 2 || hits.any { it.level > -65 }
    }

    fun wifiCount(p: SharedPreferences, site: String): Int =
        all(p).optJSONObject(site)?.optJSONObject("wifi")?.let { w -> w.keys().asSequence().count { w.optInt(it) >= 2 } } ?: 0

    // ---------------------------------------------------------------- ↩️ Deshacer y 🕐 correcciones

    /** Tocaste «Deshacer» en una llegada automática de este sitio. */
    fun undo(p: SharedPreferences, site: String) {
        if (site.isEmpty()) return
        val o = all(p)
        val me = of(o, site)
        val a = me.optJSONArray("undo") ?: JSONArray()
        a.put(System.currentTimeMillis())
        while (a.length() > 6) a.remove(0)
        me.put("undo", a)
        save(p, o)
        stat(p, "undo", site)
    }

    /** Minutos extra que espera este sitio antes de anotar la llegada (2 por cada «Deshacer» del último mes, máximo 6). */
    fun extraDwellMs(p: SharedPreferences, site: String): Int {
        val a = all(p).optJSONObject(site)?.optJSONArray("undo") ?: return 0
        val since = System.currentTimeMillis() - 30L * 24 * 3600_000L
        val n = (0 until a.length()).count { a.optLong(it) > since }
        return minOf(n * 2, 6) * 60_000
    }

    /** Las horas que se anotaron solas (para saber si una corrección es de algo automático). */
    fun markAuto(p: SharedPreferences, t: Long) {
        val a = try { JSONArray(p.getString("auto_events", "[]")) } catch (_: Exception) { JSONArray() }
        a.put(t)
        while (a.length() > 40) a.remove(0)
        p.edit().putString("auto_events", a.toString()).apply()
        stat(p, "auto", "")
    }

    fun isAuto(p: SharedPreferences, t: Long): Boolean {
        val a = try { JSONArray(p.getString("auto_events", "[]")) } catch (_: Exception) { return false }
        return (0 until a.length()).any { a.optLong(it) == t }
    }

    /** Cambiaste la hora de una llegada o salida automática: guarda cuánto la moviste. */
    fun correction(p: SharedPreferences, site: String, kind: String, deltaMs: Long) {
        if (site.isEmpty() || Math.abs(deltaMs) > 60 * 60_000L || deltaMs == 0L) return
        val o = all(p)
        val me = of(o, site)
        val key = "corr_$kind"
        val a = me.optJSONArray(key) ?: JSONArray()
        a.put(deltaMs)
        while (a.length() > 5) a.remove(0)
        me.put(key, a)
        save(p, o)
        stat(p, "fixed", site)
    }

    /** Cuánto mover la hora automática: la mediana de tus correcciones (si son 2 o más y van al mismo lado), máximo 10 min. */
    fun offsetMs(p: SharedPreferences, site: String, kind: String): Long {
        val a = all(p).optJSONObject(site)?.optJSONArray("corr_$kind") ?: return 0L
        if (a.length() < 2) return 0L
        val v = (0 until a.length()).map { a.optLong(it) }.sorted()
        if (!(v.all { it > 0 } || v.all { it < 0 })) return 0L
        return v[v.size / 2].coerceIn(-10 * 60_000L, 10 * 60_000L)
    }

    // ---------------------------------------------------------------- 📊 Reporte de la semana

    private fun weekKey(t: Long = System.currentTimeMillis()): String {
        val c = java.util.Calendar.getInstance().apply { firstDayOfWeek = java.util.Calendar.MONDAY; minimalDaysInFirstWeek = 4; timeInMillis = t }
        return "wk_" + c.get(java.util.Calendar.YEAR) + "_" + c.get(java.util.Calendar.WEEK_OF_YEAR)
    }

    /** Suma uno: [what] = "auto" (se anotó solo), "fixed" (corregiste la hora) o "undo" (era falsa). */
    fun stat(p: SharedPreferences, what: String, site: String) {
        val k = weekKey()
        val o = try { JSONObject(p.getString(k, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        o.put(what, o.optInt(what, 0) + 1)
        if (site.isNotEmpty() && what != "auto") {
            val bad = o.optJSONObject("bad") ?: JSONObject()
            bad.put(site, bad.optInt(site, 0) + 1)
            o.put("bad", bad)
        }
        val e = p.edit().putString(k, o.toString())
        // Se guardan solo las últimas 6 semanas
        val weeks = (p.getString("wk_list", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
        if (k !in weeks) weeks.add(k)
        while (weeks.size > 6) e.remove(weeks.removeAt(0))
        e.putString("wk_list", weeks.joinToString(",")).apply()
    }

    /** «Esta semana: 18 se anotaron solas, 2 corregiste y 1 deshiciste (89 % bien). La que más falla: Sitio A.» */
    fun weekReport(p: SharedPreferences, weeksAgo: Int = 0): String? {
        val o = try { JSONObject(p.getString(weekKey(System.currentTimeMillis() - weeksAgo * 7L * 24 * 3600_000L), "{}") ?: "{}") } catch (_: Exception) { return null }
        val auto = o.optInt("auto", 0)
        if (auto == 0) return null
        val fixed = o.optInt("fixed", 0); val undo = o.optInt("undo", 0)
        val good = (100 * (auto - minOf(auto, fixed + undo)) / auto)
        val bad = o.optJSONObject("bad")
        val worst = bad?.keys()?.asSequence()?.maxByOrNull { bad.optInt(it) }
        val head = if (weeksAgo == 0) L.t("Esta semana", "This week") else L.t("La semana pasada", "Last week")
        return L.t("$head: $auto se anotaron solas, corregiste $fixed y deshiciste $undo ($good % bien).",
            "$head: $auto were logged automatically, you corrected $fixed and undid $undo ($good % right).") +
            (if (worst != null) " " + L.t("La que más falla: $worst (súbele la distancia o dibuja su forma).", "The one that fails most: $worst (raise its distance or draw its shape).") else "")
    }

    /** «Aprendió de 5 llegadas · Wi-Fi: 8 redes · punto ajustado». */
    fun summary(p: SharedPreferences, site: String): String {
        val me = all(p).optJSONObject(site) ?: return ""
        val parts = ArrayList<String>()
        val v = me.optInt("visits", 0)
        if (v > 0) parts.add(L.t("aprendió de $v llegadas", "learned from $v arrivals"))
        val w = wifiCount(p, site)
        if (w > 0) parts.add(L.t("Wi-Fi: $w redes", "Wi-Fi: $w networks"))
        if (moved(p, site)) parts.add(L.t("punto ajustado", "point adjusted"))
        if (extraDwellMs(p, site) > 0) parts.add(L.t("espera ${extraDwellMs(p, site) / 60_000} min más", "waits ${extraDwellMs(p, site) / 60_000} min more"))
        return parts.joinToString(" · ")
    }
}
