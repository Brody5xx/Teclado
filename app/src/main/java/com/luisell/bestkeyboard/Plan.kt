package com.luisell.bestkeyboard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * 🗓️ El plan del día: eliges a qué sitios vas (hoy o mañana) y la detección se enfoca en esos
 * (avisa más rápido y prende el GPS fino al acercarte). O marcas «Día de servicio»: vas a varios lugares,
 * así que se vigilan todos y, además, cuando te bajas de la camioneta y te quedas en un lugar que no tienes
 * guardado, se anota la llegada ahí (y la salida cuando te vuelves a subir).
 */
object Plan {
    const val ACTION_ASK = "com.luisell.bestkeyboard.PLAN_ASK"
    const val ACTION_STOP = "com.luisell.bestkeyboard.PLAN_STOP"
    const val ACTION_STOP_LEAVE = "com.luisell.bestkeyboard.PLAN_STOP_LEAVE"

    fun key(t: Long) = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(t))
    fun today() = key(System.currentTimeMillis())
    fun tomorrow() = key(System.currentTimeMillis() + 24 * 3600_000L)

    /** El plan de un día: (sitios elegidos, día de servicio). */
    fun get(p: SharedPreferences, day: String): Pair<Set<String>, Boolean> {
        val sites = try { JSONArray(p.getString("plan_$day", "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } } catch (_: Exception) { emptySet() }
        return sites to p.getBoolean("plan_service_$day", false)
    }

    fun set(ctx: Context, day: String, sites: Set<String>, service: Boolean) {
        val e = Prefs.get(ctx).edit().putString("plan_$day", JSONArray(sites.toList()).toString()).putBoolean("plan_service_$day", service)
        // Se borran los planes viejos
        val days = (Prefs.get(ctx).getString("plan_days", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
        if (day !in days) days.add(day)
        while (days.size > 5) { val old = days.removeAt(0); e.remove("plan_$old"); e.remove("plan_service_$old") }
        e.putString("plan_days", days.joinToString(",")).apply()
        AutoSites.register(ctx)
    }

    /** ¿Hoy hay plan? */
    fun active(p: SharedPreferences): Boolean = get(p, today()).let { it.first.isNotEmpty() || it.second }
    fun service(p: SharedPreferences): Boolean = get(p, today()).second
    /** ¿Este sitio está en el plan de hoy? (sin plan, o día de servicio, cuentan todos) */
    fun focus(p: SharedPreferences, site: String): Boolean = get(p, today()).let { (s, srv) -> srv || s.isEmpty() || site in s }
    fun planned(p: SharedPreferences, site: String): Boolean = get(p, today()).let { (s, srv) -> srv || site in s }

    // ---------------------------------------------------------------- 🔔 «¿A dónde vas mañana?»

    private fun askIntent(ctx: Context) = PendingIntent.getBroadcast(ctx, 5170, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_ASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Programa la pregunta de las 7:30 p. m. (si mañana trabajas). */
    fun scheduleAsk(ctx: Context) {
        if (!Prefs.get(ctx).getBoolean("plan_ask", true)) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val c = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 19); set(java.util.Calendar.MINUTE, 30); set(java.util.Calendar.SECOND, 0) }
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
        try { am.setWindow(AlarmManager.RTC_WAKEUP, c.timeInMillis, 20 * 60_000L, askIntent(ctx)) } catch (_: Exception) { }
    }

    fun ask(ctx: Context) {
        val p = Prefs.get(ctx)
        scheduleAsk(ctx)
        if (!AutoSites.isOn(ctx) || SiteStore.all(p).none { !it.isStart }) return
        val tmr = System.currentTimeMillis() + 24 * 3600_000L
        if (!WorkDay.isWorkDay(p, tmr) || WorkDay.day(p, tmr).isNotEmpty()) return
        if (get(p, tomorrow()).let { it.first.isNotEmpty() || it.second }) return
        L.useScreen(ctx)
        val open = PendingIntent.getActivity(ctx, 5171, Intent(ctx, SitesActivity::class.java).putExtra(SitesActivity.EXTRA_PLAN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        AutoSites.notify(ctx, L.t("¿A dónde vas mañana?", "Where are you going tomorrow?"),
            L.t("Elige tus sitios (o «Día de servicio») y la llegada y la salida se detectan mejor.", "Pick your sites (or «Service day») and arrival and departure are detected better."),
            listOf(L.t("Elegir", "Choose") to open), 4741)
    }

    // ---------------------------------------------------------------- 🛠️ Día de servicio: paradas en lugares nuevos

    private fun alarm(ctx: Context, action: String, req: Int, minutes: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(ctx, req, Intent(ctx, AutoSitesReceiver::class.java).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pi) } catch (_: Exception) { }
    }

    /** Te bajaste de la camioneta en día de servicio: en 6 minutos revisa si te quedaste ahí. */
    fun vehicleOut(ctx: Context, at: Long) {
        val p = Prefs.get(ctx)
        if (!service(p) || !WorkDay.isRunning(WorkDay.day(p))) return
        WorkdayCenter.preciseFix(ctx.applicationContext, 9000L, 30f) { l ->
            if (l == null) return@preciseFix
            p.edit().putString("stop_fix", JSONArray().put(l.latitude).put(l.longitude).put(at).toString()).apply()
            alarm(ctx, ACTION_STOP, 5172, 6)
        }
    }

    /** ¿Te quedaste? Si sigues ahí (y no es uno de tus sitios, tu casa ni una ferretería): llegaste a un lugar nuevo. */
    fun checkStop(ctx: Context, rcv: android.content.BroadcastReceiver?) {
        val p = Prefs.get(ctx)
        L.useScreen(ctx)
        val a = try { JSONArray(p.getString("stop_fix", "") ?: "") } catch (_: Exception) { return }
        val la = a.getDouble(0); val lo = a.getDouble(1); val at = a.getLong(2)
        if (p.getLong("veh_in", 0L) > at) return // te volviste a subir
        val day = WorkDay.day(p)
        if (!WorkDay.isRunning(day) || WorkDay.lastMain(day)?.kind == WorkDay.ARRIVE) return
        val pr = rcv?.goAsync()
        WorkdayCenter.preciseFix(ctx.applicationContext, 9000L, 30f) { l ->
            try {
                if (l == null) return@preciseFix
                val d = FloatArray(1)
                android.location.Location.distanceBetween(la, lo, l.latitude, l.longitude, d)
                if (d[0] > 80f) return@preciseFix
                if (SiteStore.all(p).any { !(it.lat == 0.0 && it.lon == 0.0) && Geo.where(p, it, la, lo, 20f) != false }) return@preciseFix
                SiteStore.home(p)?.let { (hla, hlo) -> android.location.Location.distanceBetween(hla, hlo, la, lo, d); if (d[0] < 200f) return@preciseFix }
                if (Stores.cached(p).any { android.location.Location.distanceBetween(it.lat, it.lon, la, lo, d); d[0] < 120f }) return@preciseFix
                Thread {
                    val full = SiteStore.addressOf(ctx, la, lo)
                    val short = SiteStore.shortAddress(full).ifEmpty { L.t("Lugar nuevo", "New place") }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            SiteStore.setNew(p, SiteStore.NewPlace(at, short, full, la, lo))
                            val t = WorkDay.addAt(p, WorkDay.ARRIVE, short, at)
                            SiteLearn.markAuto(p, t)
                            WorkdayCenter.refresh(ctx)
                            AutoSites.register(ctx)
                            val undo = PendingIntent.getBroadcast(ctx, (t / 1000 % 100000).toInt(), Intent(ctx, AutoSitesReceiver::class.java).setAction(AutoSites.ACTION_UNDO).putExtra(AutoSites.EXTRA_TIME, t),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            val save = PendingIntent.getActivity(ctx, 5173, Intent(ctx, SitesActivity::class.java).putExtra(SitesActivity.EXTRA_NEW, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                            AutoSites.notify(ctx, L.t("Llegaste a ", "You arrived at ") + short,
                                L.t("Día de servicio: lo anoté solo, llegaste a las ${WorkStore.time(t)}.", "Service day: logged automatically, you arrived at ${WorkStore.time(t)}."),
                                listOf(L.t("Deshacer", "Undo") to undo, L.t("Guardar este lugar", "Save this place") to save), 4720)
                        } catch (_: Throwable) { }
                    }
                }.start()
            } catch (_: Throwable) { } finally { pr?.finish() }
        }
    }

    /** Te subiste a la camioneta estando en un lugar nuevo: en 4 minutos, si ya te alejaste, se anota la salida. */
    fun vehicleIn(ctx: Context) {
        val p = Prefs.get(ctx)
        val last = WorkDay.lastMain(WorkDay.day(p)) ?: return
        val nw = SiteStore.getNew(p) ?: return
        if (last.kind != WorkDay.ARRIVE || last.site != nw.short) return
        alarm(ctx, ACTION_STOP_LEAVE, 5174, 4)
    }

    fun checkLeave(ctx: Context, rcv: android.content.BroadcastReceiver?) {
        val p = Prefs.get(ctx)
        L.useScreen(ctx)
        val last = WorkDay.lastMain(WorkDay.day(p)) ?: return
        val nw = SiteStore.getNew(p) ?: return
        val vin = p.getLong("veh_in", 0L)
        if (last.kind != WorkDay.ARRIVE || last.site != nw.short || vin <= last.time || p.getLong("veh_out", 0L) > vin) return
        val pr = rcv?.goAsync()
        WorkdayCenter.preciseFix(ctx.applicationContext, 9000L, 40f) { l ->
            try {
                if (l == null) return@preciseFix
                val d = FloatArray(1)
                android.location.Location.distanceBetween(nw.lat, nw.lon, l.latitude, l.longitude, d)
                if (d[0] < 150f) return@preciseFix
                val t = WorkDay.addAt(p, WorkDay.LEAVE, nw.short, vin)
                SiteLearn.markAuto(p, t)
                WorkdayCenter.refresh(ctx)
                val undo = PendingIntent.getBroadcast(ctx, (t / 1000 % 100000).toInt(), Intent(ctx, AutoSitesReceiver::class.java).setAction(AutoSites.ACTION_UNDO).putExtra(AutoSites.EXTRA_TIME, t),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                AutoSites.notify(ctx, L.t("Saliste de ", "You left ") + nw.short,
                    L.t("Lo anoté solo: saliste a las ${WorkStore.time(t)} (cuando te subiste a la camioneta).", "Logged automatically: you left at ${WorkStore.time(t)} (when you got into the truck)."),
                    listOf(L.t("Deshacer", "Undo") to undo), 4720)
            } catch (_: Throwable) { } finally { pr?.finish() }
        }
    }
}
