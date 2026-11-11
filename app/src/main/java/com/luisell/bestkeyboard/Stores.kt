package com.luisell.bestkeyboard

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🛒 La lista de la ferretería: anotas lo que tienes que comprar y, cuando llegas a una ferretería
 * (una grande o una pequeña, o una tienda de material eléctrico), te sale el aviso con toda la lista.
 * Las ferreterías cerca de donde andas salen del mapa gratis de OpenStreetMap; el teléfono las vigila
 * igual que tus sitios (geocercas, casi sin batería). Solo mientras tengas algo en la lista.
 */
object Stores {
    private const val LIST = "shop_list"
    private const val CACHE = "shop_stores"
    const val STORE_ID = "store:"

    class Item(val text: String, val done: Boolean)

    // ---------------------------------------------------------------- 📝 La lista

    fun items(p: SharedPreferences): List<Item> = try {
        val a = JSONArray(p.getString(LIST, "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Item(o.optString("t"), o.optBoolean("d")) } }
    } catch (_: Exception) { emptyList() }

    private fun save(ctx: Context, list: List<Item>) {
        val a = JSONArray()
        for (i in list) a.put(JSONObject().put("t", i.text).put("d", i.done))
        Prefs.get(ctx).edit().putString(LIST, a.toString()).apply()
    }

    fun pending(p: SharedPreferences) = items(p).filter { !it.done }

    /** Agrega cosas (puedes escribir varias separadas por coma: «cinta, conectores RJ45, tornillos»). */
    fun add(ctx: Context, text: String) {
        val p = Prefs.get(ctx)
        val news = text.split(",", "\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
        if (news.isEmpty()) return
        val had = pending(p).isNotEmpty()
        save(ctx, items(p) + news.filter { n -> items(p).none { it.text.equals(n, true) && !it.done } }.map { Item(it, false) })
        // La primera cosa de la lista: busca las ferreterías de por aquí
        if (!had) refreshSoon(ctx, force = true)
    }

    fun toggle(ctx: Context, text: String) = save(ctx, items(Prefs.get(ctx)).map { if (it.text == text) Item(it.text, !it.done) else it })
    fun clearDone(ctx: Context) { save(ctx, items(Prefs.get(ctx)).filter { !it.done }); AutoSites.register(ctx) }
    fun clearAll(ctx: Context) { save(ctx, emptyList()); AutoSites.register(ctx) }
    fun boughtAll(ctx: Context) { save(ctx, items(Prefs.get(ctx)).map { Item(it.text, true) }); AutoSites.register(ctx) }

    // ---------------------------------------------------------------- 🏬 Las ferreterías de por aquí

    class Store(val name: String, val lat: Double, val lon: Double)

    fun cached(p: SharedPreferences): List<Store> = try {
        val o = JSONObject(p.getString(CACHE, "{}") ?: "{}")
        val a = o.optJSONArray("s") ?: JSONArray()
        (0 until a.length()).map { a.getJSONArray(it).let { s -> Store(s.getString(0), s.getDouble(1), s.getDouble(2)) } }
    } catch (_: Exception) { emptyList() }

    @Volatile private var busy = false

    /**
     * Busca (en segundo plano) las ferreterías a 15 km de donde estás, si hace falta:
     * la primera vez, si te moviste más de 8 km o si la búsqueda tiene más de 3 días.
     */
    @android.annotation.SuppressLint("MissingPermission")
    fun refreshSoon(ctx: Context, force: Boolean = false) {
        val app = ctx.applicationContext
        val p = Prefs.get(app)
        if (pending(p).isEmpty() || busy || !WorkdayCenter.hasLocation(app)) return
        val o = try { JSONObject(p.getString(CACHE, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        val client = try { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(app) } catch (_: Throwable) { return }
        busy = true
        try {
            client.lastLocation.addOnSuccessListener { l ->
                if (l == null) { busy = false; return@addOnSuccessListener }
                val d = FloatArray(1)
                android.location.Location.distanceBetween(o.optDouble("la", 0.0), o.optDouble("lo", 0.0), l.latitude, l.longitude, d)
                val old = System.currentTimeMillis() - o.optLong("t", 0L) > 3 * 24 * 3600_000L
                if (!force && o.has("s") && d[0] < 8000f && !old) { busy = false; return@addOnSuccessListener }
                Thread {
                    try { fetch(app, l.latitude, l.longitude) } catch (_: Throwable) { }
                    busy = false
                    android.os.Handler(android.os.Looper.getMainLooper()).post { AutoSites.register(app) }
                }.start()
            }.addOnFailureListener { busy = false }
        } catch (_: Throwable) { busy = false }
    }

    /** Pregunta al mapa gratis (OpenStreetMap / Overpass) por las ferreterías cerca. */
    private fun fetch(ctx: Context, la: Double, lo: Double) {
        val q = """[out:json][timeout:25];nwr["shop"~"^(hardware|doityourself|trade|electrical)$"](around:15000,$la,$lo);out center 80;"""
        val con = java.net.URL("https://overpass-api.de/api/interpreter").openConnection() as java.net.HttpURLConnection
        con.requestMethod = "POST"
        con.doOutput = true
        con.connectTimeout = 15000; con.readTimeout = 30000
        con.setRequestProperty("User-Agent", "BestKeyboard/15 (Android keyboard app)")
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        con.outputStream.use { it.write(("data=" + java.net.URLEncoder.encode(q, "UTF-8")).toByteArray()) }
        if (con.responseCode !in 200..299) return
        val body = con.inputStream.bufferedReader().use { it.readText() }
        val els = JSONObject(body).optJSONArray("elements") ?: return
        val list = ArrayList<Triple<String, Double, Double>>()
        for (i in 0 until els.length()) {
            val e = els.getJSONObject(i)
            val c = e.optJSONObject("center")
            val sla = if (e.has("lat")) e.getDouble("lat") else c?.optDouble("lat") ?: continue
            val slo = if (e.has("lon")) e.getDouble("lon") else c?.optDouble("lon") ?: continue
            val name = e.optJSONObject("tags")?.optString("name", "")?.ifEmpty { null } ?: L.t("Ferretería", "Hardware store")
            list.add(Triple(name, sla, slo))
        }
        // Las más cerca primero (máximo 25: el teléfono vigila hasta 100 lugares en total)
        val d = FloatArray(1)
        val near = list.sortedBy { android.location.Location.distanceBetween(la, lo, it.second, it.third, d); d[0] }.take(25)
        val a = JSONArray()
        for ((n, x, y) in near) a.put(JSONArray().put(n).put(x).put(y))
        Prefs.get(ctx).edit().putString(CACHE, JSONObject().put("t", System.currentTimeMillis()).put("la", la).put("lo", lo).put("s", a).toString()).apply()
    }

    /** Llegaste a una ferretería: el aviso con tu lista (una vez cada 2 horas por tienda). */
    fun arrived(ctx: Context, idx: Int) {
        val p = Prefs.get(ctx)
        val st = cached(p).getOrNull(idx) ?: return
        val list = pending(p)
        if (list.isEmpty()) return
        val key = "shop_told_$idx"
        if (System.currentTimeMillis() - p.getLong(key, 0L) < 2 * 3600_000L) return
        p.edit().putLong(key, System.currentTimeMillis()).apply()
        val done = android.app.PendingIntent.getBroadcast(ctx, 5160, android.content.Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_BOUGHT),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        AutoSites.notify(ctx, L.t("Estás en ", "You're at ") + st.name + L.t(": tu lista", ": your list"),
            list.joinToString("\n") { "•  " + it.text }, listOf(L.t("Ya compré todo", "I bought everything") to done), 4730 + 9)
    }

    const val ACTION_BOUGHT = "com.luisell.bestkeyboard.SHOP_BOUGHT"
}
