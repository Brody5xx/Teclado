package com.luisell.bestkeyboard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 🔔 «¿Se te olvidó?»: mientras tu día está abierto, cada media hora revisa:
 *  - que no se te haya olvidado marcar el fin del día (ya es tarde y no se anotó);
 *  - que no sigas «en un sitio» cuando ya te fuiste (el GPS dice que estás lejos);
 *  - que, si llevas 6 horas, sepas que igual te descuentan el almuerzo.
 * Cada aviso sale una sola vez y trae el botón para arreglarlo con la hora que más probablemente fue.
 */
object Forgot {
    const val ACTION = "com.luisell.bestkeyboard.FORGOT_CHECK"
    const val ACTION_ADD = "com.luisell.bestkeyboard.FORGOT_ADD"
    private const val ID_END = 4724
    private const val ID_LEAVE = 4725
    private const val ID_LUNCH = 4726

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(ctx, 5150, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Programa la próxima revisión (o la quita si el día no está abierto). */
    fun schedule(ctx: Context) {
        val p = Prefs.get(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val running = WorkDay.isRunning(WorkDay.day(p))
        try {
            if (!running) { am.cancel(pending(ctx)); return }
            am.setWindow(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30 * 60_000L, 10 * 60_000L, pending(ctx))
        } catch (_: Exception) { }
    }

    private fun dayKey() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    private fun once(ctx: Context, what: String): Boolean {
        val p = Prefs.get(ctx)
        val k = "forgot_$what"
        if (p.getString(k, "") == dayKey()) return false
        p.edit().putString(k, dayKey()).apply()
        return true
    }

    /** Agrega un evento desde el aviso (con la hora sugerida). */
    private fun addIntent(ctx: Context, req: Int, kind: String, site: String, t: Long): PendingIntent =
        PendingIntent.getBroadcast(ctx, req, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_ADD)
            .apply { if (t > 0) putExtra(AutoSites.EXTRA_TIME, t) }.putExtra(AutoSites.EXTRA_SITE, site).putExtra(WorkdayCenter.EXTRA_KIND, kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Abre la ventanita para escribir la hora. */
    private fun timeIntent(ctx: Context, req: Int, kind: String): PendingIntent =
        PendingIntent.getActivity(ctx, req, Intent(ctx, TimeSetActivity::class.java).putExtra(TimeSetActivity.EXTRA_KIND, kind)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun add(ctx: Context, intent: Intent) {
        val p = Prefs.get(ctx)
        val t = intent.getLongExtra(AutoSites.EXTRA_TIME, System.currentTimeMillis())
        val k = intent.getStringExtra(WorkdayCenter.EXTRA_KIND) ?: return
        val site = intent.getStringExtra(AutoSites.EXTRA_SITE) ?: ""
        WorkDay.addAt(p, k, site, t)
        WorkdayCenter.refresh(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.cancel(ID_END); nm?.cancel(ID_LEAVE); if (k == WorkDay.LUNCH) nm?.cancel(ID_LUNCH)
    }

    /** A qué hora sueles terminar (el promedio de tus últimos días; si no hay, las 6 p. m.). */
    private fun usualEnd(ctx: Context): Int {
        val p = Prefs.get(ctx)
        val fin = WorkDay.finalKind()
        val mins = WorkDay.byDay(p).takeLast(15).mapNotNull { (_, ev) -> ev.lastOrNull { it.kind == fin }?.time }
            .map { java.util.Calendar.getInstance().apply { timeInMillis = it }.let { c -> c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE) } }
        return if (mins.size >= 3) mins.sorted()[mins.size / 2] else 18 * 60
    }

    fun check(ctx: Context, rcv: android.content.BroadcastReceiver?) {
        val p = Prefs.get(ctx)
        L.useScreen(ctx)
        WorkDay.config(p)
        val day = WorkDay.day(p)
        if (!WorkDay.isRunning(day)) return
        val now = System.currentTimeMillis()
        val last = WorkDay.lastMain(day) ?: return
        val cal = java.util.Calendar.getInstance()
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        // 🍽️ Llevas 6 horas y no marcaste almuerzo
        val sum = WorkDay.summary(day, now)
        if (p.getBoolean("lunch_ask", false) && !WorkDay.lunchPaid && day.none { it.kind == WorkDay.LUNCH } && now - day.first().time >= 6 * 3600_000L && once(ctx, "lunch")) {
            AutoSites.notify(ctx, L.t("¿Tomaste el almuerzo?", "Did you take lunch?"),
                L.t("Ya llevas ${WorkDay.dur(now - day.first().time)}. Aunque no lo marques, te descuentan ${WorkDay.lunchAutoMin} min de almuerzo.",
                    "You're at ${WorkDay.dur(now - day.first().time)}. Even if you don't log it, ${WorkDay.lunchAutoMin} min of lunch are deducted."),
                listOf(L.t("Marcar almuerzo ahora", "Log lunch now") to addIntent(ctx, 5151, WorkDay.LUNCH, "", -1L)), ID_LUNCH)
        }

        // 🏁 Ya es tarde y el día sigue abierto
        if (nowMin >= usualEnd(ctx) + 90 && now - last.time >= 60 * 60_000L && once(ctx, "end")) {
            val fin = WorkDay.finalKind()
            // La hora más probable: cuando te bajaste de la camioneta, o cuando llegaste al Wi-Fi de casa
            val guess = listOf(p.getLong("veh_out", 0L), p.getLong("home_wifi_on", 0L)).filter { it > last.time && it < now }.maxOrNull()
            val actions = ArrayList<Pair<String, PendingIntent>>()
            if (guess != null) actions.add(L.t("Terminé a las ", "I ended at ") + WorkStore.time(guess) to addIntent(ctx, 5152, fin, "", guess))
            actions.add(L.t("Poner la hora", "Set the time") to timeIntent(ctx, 5153, fin))
            AutoSites.notify(ctx, L.t("¿Olvidaste marcar que terminaste?", "Did you forget to log that you ended?"),
                L.t("Tu día sigue abierto desde las ${WorkStore.time(day.first().time)}. Lo último fue «${WorkDay.labelOf(last)}» a las ${WorkStore.time(last.time)}.",
                    "Your day is still open since ${WorkStore.time(day.first().time)}. The last thing was «${WorkDay.labelOf(last)}» at ${WorkStore.time(last.time)}."),
                actions, ID_END)
        }

        // 📍 Sigues «en un sitio», pero el GPS dice que ya no estás ahí
        val site = if (last.kind == WorkDay.ARRIVE) SiteStore.find(p, last.site) else null
        if (site != null && !(site.lat == 0.0 && site.lon == 0.0) && now - last.time >= 30 * 60_000L && WorkdayCenter.hasLocation(ctx)) {
            val pr = rcv?.goAsync()
            WorkdayCenter.preciseFix(ctx.applicationContext, 9000L, 40f) { fix ->
                try {
                    if (fix != null && fix.accuracy <= 80f && Geo.where(p, site, fix.latitude, fix.longitude, fix.accuracy) == false) {
                        val dd = FloatArray(1)
                        android.location.Location.distanceBetween(site.lat, site.lon, fix.latitude, fix.longitude, dd)
                        if (dd[0] > Geo.fenceRadius(p, site) + 300f && once(ctx, "leave_" + last.time)) {
                            // La hora más probable: la última vez que el GPS te vio adentro (o cuando te subiste al vehículo)
                            val inside = Trail.day(ctx, now).filter { it[3] > last.time && Geo.where(p, site, it[0], it[1], it[2].toFloat()) == true }.maxOfOrNull { it[3].toLong() }
                            val veh = p.getLong("veh_in", 0L).takeIf { it > last.time && it < now }
                            val guess = veh ?: inside
                            val actions = ArrayList<Pair<String, PendingIntent>>()
                            if (guess != null) actions.add(L.t("Salí a las ", "I left at ") + WorkStore.time(guess) to addIntent(ctx, 5154, WorkDay.LEAVE, site.name, guess))
                            actions.add(L.t("Poner la hora", "Set the time") to timeIntent(ctx, 5155, WorkDay.LEAVE))
                            AutoSites.notify(ctx, L.t("¿Saliste de ${site.name}?", "Did you leave ${site.name}?"),
                                L.t("Llegaste a las ${WorkStore.time(last.time)} y no se anotó la salida, pero ahora estás lejos de ahí.",
                                    "You arrived at ${WorkStore.time(last.time)} and no departure was logged, but you're far from there now."),
                                actions, ID_LEAVE)
                        }
                    }
                } catch (_: Throwable) { } finally { pr?.finish() }
            }
        }
    }
}
