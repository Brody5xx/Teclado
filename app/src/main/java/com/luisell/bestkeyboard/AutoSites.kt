package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONObject

/**
 * Llegada y salida automáticas: el teléfono avisa cuando entras o sales de uno de "Mis sitios"
 * (geocercas de Google, gastan muy poca batería) y Mi jornada anota la hora sola.
 *  - Entras y te quedas unos minutos → "📍 Llegué" con la hora en que ENTRASTE.
 *  - Sales → "🚪 Salí" con la hora en que saliste.
 * Siempre sale una notificación con «Deshacer», por si no era trabajo.
 */
object AutoSites {
    private const val CHANNEL = "workday_auto"
    const val ACTION_UNDO = "com.luisell.bestkeyboard.AUTO_UNDO"
    const val ACTION_MARK = "com.luisell.bestkeyboard.AUTO_MARK"
    const val ACTION_HOME_AT = "com.luisell.bestkeyboard.AUTO_HOME_AT"
    const val ACTION_EXIT_CHECK = "com.luisell.bestkeyboard.AUTO_EXIT_CHECK"
    const val EXTRA_TIME = "time"
    const val EXTRA_SITE = "site"
    private const val NEW_ID = "new:"
    private const val SITE_ID = "site:"
    private const val HOME_ID = "home"
    /** Cuánto tienes que quedarte para que cuente como "llegué" (para no marcar si solo pasas por ahí). */
    private const val DWELL_MS = 4 * 60 * 1000
    private const val RADIUS = 250f

    fun isOn(ctx: Context) = Prefs.get(ctx).getBoolean(Prefs.AUTO_SITES, false)

    /** "Permitir todo el tiempo": sin esto el teléfono no avisa con el teclado cerrado. */
    fun hasBackground(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 29 ||
            ctx.checkSelfPermission("android.permission.ACCESS_BACKGROUND_LOCATION") == PackageManager.PERMISSION_GRANTED

    fun ready(ctx: Context) = isOn(ctx) && WorkdayCenter.hasLocation(ctx) && hasBackground(ctx)

    private fun pending(ctx: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, 5120, Intent(ctx, AutoSitesReceiver::class.java), flags)
    }

    /** Pone (o quita) las geocercas de tus sitios. Llamar al cambiar los sitios, al prender el teléfono y al abrir la app. */
    @SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        val app = ctx.applicationContext
        val client = try { LocationServices.getGeofencingClient(app) } catch (_: Throwable) { return }
        try { client.removeGeofences(pending(app)) } catch (_: Throwable) { }
        if (!ready(app)) return
        val p = Prefs.get(app)
        val fences = ArrayList<Geofence>()
        for (s in SiteStore.all(p)) {
            if (s.lat == 0.0 && s.lon == 0.0) continue
            fences.add(Geofence.Builder()
                .setRequestId(SITE_ID + s.name.take(80))
                .setCircularRegion(s.lat, s.lon, RADIUS)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setLoiteringDelay(DWELL_MS)
                .build())
            if (fences.size >= 90) break
        }
        // 🏠 Tu casa: para anotar solo «Llegué a casa» con la hora real
        SiteStore.home(p)?.let { (la, lo) ->
            fences.add(Geofence.Builder()
                .setRequestId(HOME_ID)
                .setCircularRegion(la, lo, 150f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(2 * 60 * 1000)
                .build())
        }
        // El lugar nuevo de hoy (no guardado): solo para saber cuándo te vas
        SiteStore.getNew(p)?.let { n ->
            fences.add(Geofence.Builder()
                .setRequestId(NEW_ID + n.short.take(80))
                .setCircularRegion(n.lat, n.lon, RADIUS)
                .setExpirationDuration(18L * 3600 * 1000)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .build())
        }
        if (fences.isEmpty()) return
        val req = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(fences)
            .build()
        try { client.addGeofences(req, pending(app)) } catch (_: Throwable) { }
    }

    // ---------------------------------------------------------------- Cuando el teléfono avisa

    fun handle(ctx: Context, intent: Intent) {
        val p = Prefs.get(ctx)
        L.set(p.getString(Prefs.LANG, "es") ?: "es")
        when (intent.action) {
            ACTION_UNDO -> {
                val t = intent.getLongExtra(EXTRA_TIME, -1L)
                if (t > 0) WorkDay.remove(p, t)
                WorkdayCenter.refresh(ctx)
                cancel(ctx)
                return
            }
            ACTION_EXIT_CHECK -> {
                // Pasaron los minutos de espera y no volviste a entrar: ahora sí se anota la salida
                val o = try { JSONObject(p.getString(Prefs.AUTO_EXIT_PENDING, "") ?: "") } catch (_: Exception) { null } ?: return
                p.edit().remove(Prefs.AUTO_EXIT_PENDING).apply()
                left(ctx, o.optString("s"), o.optLong("t"))
                return
            }
            ACTION_HOME_AT -> {
                val t = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
                val k = intent.getStringExtra(WorkdayCenter.EXTRA_KIND) ?: WorkDay.HOME
                WorkDay.addAt(p, k, "", t)
                WorkdayCenter.refresh(ctx)
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(4722)
                return
            }
            ACTION_MARK -> {
                val t = intent.getLongExtra(EXTRA_TIME, System.currentTimeMillis())
                val site = intent.getStringExtra(EXTRA_SITE) ?: ""
                WorkDay.addAt(p, WorkDay.ARRIVE, site, t)
                WorkdayCenter.refresh(ctx)
                cancel(ctx)
                return
            }
        }
        if (!isOn(ctx)) return
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError()) return
        val now = System.currentTimeMillis()
        val at = ev.triggeringLocation?.time?.takeIf { it in (now - 30 * 60_000L)..now } ?: now
        for (g in ev.triggeringGeofences ?: emptyList()) {
            val id = g.requestId
            if (id == HOME_ID) {
                when (ev.geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> saveEnter(p, HOME_ID, at)
                    Geofence.GEOFENCE_TRANSITION_DWELL -> cameHome(ctx, takeEnter(p, HOME_ID) ?: (at - 2 * 60_000L))
                }
                continue
            }
            val isNew = id.startsWith(NEW_ID)
            val site = id.removePrefix(SITE_ID).removePrefix(NEW_ID)
            when (ev.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> { saveEnter(p, site, at); cancelPendingExit(ctx, site); falseExit(ctx, site, at) }
                Geofence.GEOFENCE_TRANSITION_DWELL -> if (!isNew) arrived(ctx, site, takeEnter(p, site) ?: (at - DWELL_MS))
                Geofence.GEOFENCE_TRANSITION_EXIT -> { takeEnter(p, site); pendingExit(ctx, site, at) }
            }
        }
    }

    private fun saveEnter(p: android.content.SharedPreferences, site: String, t: Long) {
        val o = try { JSONObject(p.getString(Prefs.AUTO_ENTER, "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
        o.put(site, t)
        p.edit().putString(Prefs.AUTO_ENTER, o.toString()).apply()
    }

    private fun takeEnter(p: android.content.SharedPreferences, site: String): Long? {
        val o = try { JSONObject(p.getString(Prefs.AUTO_ENTER, "{}") ?: "{}") } catch (_: Exception) { return null }
        val t = o.optLong(site, -1L)
        o.remove(site)
        p.edit().putString(Prefs.AUTO_ENTER, o.toString()).apply()
        // Solo si fue hoy y hace menos de 3 horas
        return if (t > 0 && System.currentTimeMillis() - t < 3 * 3600_000L && WorkStore.sameDay(t, System.currentTimeMillis())) t else null
    }

    private fun arrived(ctx: Context, site: String, enterAt: Long) {
        val p = Prefs.get(ctx)
        val day = WorkDay.day(p)
        val last = WorkDay.lastMain(day)
        // Ya estás marcado en este sitio
        if (last?.kind == WorkDay.ARRIVE && (last.site == site || last.site.isEmpty())) return
        if (WorkDay.isRunning(day)) {
            val t = WorkDay.addAt(p, WorkDay.ARRIVE, site, enterAt)
            WorkdayCenter.refresh(ctx)
            Todos.arrivedAt(ctx, site)
            notify(ctx, L.t("📍 Llegaste a $site", "📍 You arrived at $site"),
                L.t("Lo anoté solo: llegaste a las ${WorkStore.time(t)}.", "Logged automatically: you arrived at ${WorkStore.time(t)}."),
                listOf(L.t("Deshacer", "Undo") to undoIntent(ctx, t), L.t("🕐 Cambiar hora", "🕐 Change time") to fixIntent(ctx, t)), NOTIF_ID)
        } else {
            // No has empezado tu jornada hoy: pregunta (puede que no estés trabajando)
            notify(ctx, L.t("📍 Estás en $site", "📍 You're at $site"),
                L.t("Desde las ${WorkStore.time(enterAt)}. ¿Estás trabajando?", "Since ${WorkStore.time(enterAt)}. Are you working?"),
                L.t("Marcar llegada", "Log arrival") to markIntent(ctx, enterAt, site))
        }
    }

    /** Llegaste a casa: si la jornada sigue abierta, se cierra con la hora en que llegaste. */
    private fun cameHome(ctx: Context, at: Long) {
        val p = Prefs.get(ctx)
        val day = WorkDay.day(p)
        if (!WorkDay.isRunning(day) || !WorkDay.isOn(WorkDay.HOME)) return
        val last = WorkDay.lastMain(day) ?: return
        // Si todavía estás marcado en un sitio, primero se anota que saliste (a la hora de tu última salida conocida no se sabe: se deja)
        if (at <= last.time) return
        val t = WorkDay.addAt(p, WorkDay.HOME, "", at)
        WorkdayCenter.refresh(ctx)
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(4722)
        notify(ctx, L.t("🏠 Llegaste a casa", "🏠 You got home"),
            L.t("Lo anoté solo: llegaste a las ${WorkStore.time(t)}. Hoy: ${WorkDay.dur(WorkDay.summary(WorkDay.day(p)).total)}.",
                "Logged automatically: you got home at ${WorkStore.time(t)}. Today: ${WorkDay.dur(WorkDay.summary(WorkDay.day(p)).total)}."),
            L.t("Deshacer", "Undo") to undoIntent(ctx, t))
    }

    /**
     * Volviste a entrar al sitio poco después de una «salida» automática: el GPS brincó (pasa adentro de edificios).
     * Se borra esa salida falsa, sin molestarte.
     */
    /** Saliste del sitio: no se anota todavía; se espera unos minutos por si el GPS brincó. */
    private fun pendingExit(ctx: Context, site: String, at: Long) {
        val p = Prefs.get(ctx)
        p.edit().putString(Prefs.AUTO_EXIT_PENDING, JSONObject().put("s", site).put("t", at).toString()).apply()
        val wait = ((p.getString(Prefs.AUTO_EXIT_WAIT, "10") ?: "10").toIntOrNull() ?: 10).coerceIn(1, 60)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        try { am.setWindow(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + wait * 60_000L, 60_000L, exitCheckIntent(ctx)) } catch (_: Exception) { }
    }

    /** Volviste a entrar antes de que pasaran los minutos: no te saliste (era el GPS). */
    private fun cancelPendingExit(ctx: Context, site: String) {
        val p = Prefs.get(ctx)
        val o = try { JSONObject(p.getString(Prefs.AUTO_EXIT_PENDING, "") ?: "") } catch (_: Exception) { null } ?: return
        if (o.optString("s") != site) return
        p.edit().remove(Prefs.AUTO_EXIT_PENDING).apply()
        (ctx.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager)?.cancel(exitCheckIntent(ctx))
    }

    private fun exitCheckIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 5122, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_EXIT_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Abre la ventanita para escribir la hora de un evento (desde el aviso). */
    private fun fixIntent(ctx: Context, t: Long): PendingIntent =
        PendingIntent.getActivity(ctx, (t / 1000 % 100000).toInt() + 7, Intent(ctx, TimeSetActivity::class.java)
            .putExtra(TimeSetActivity.EXTRA_EVENT, t).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun falseExit(ctx: Context, site: String, at: Long) {
        val p = Prefs.get(ctx)
        val auto = p.getLong(Prefs.AUTO_LEAVE, 0L)
        if (auto <= 0 || at - auto > 12 * 60_000L || at < auto) return
        val ev = WorkDay.day(p).lastOrNull { it.time == auto && it.kind == WorkDay.LEAVE } ?: return
        if (ev.site.isNotEmpty() && ev.site != site) return
        WorkDay.remove(p, auto)
        p.edit().remove(Prefs.AUTO_LEAVE).apply()
        cancel(ctx)
        WorkdayCenter.refresh(ctx)
    }

    private fun left(ctx: Context, site: String, at: Long) {
        val p = Prefs.get(ctx)
        val day = WorkDay.day(p)
        val last = WorkDay.lastMain(day) ?: return
        if (last.kind != WorkDay.ARRIVE || !WorkDay.isRunning(day)) return
        if (last.site.isNotEmpty() && last.site != site) return
        if (at <= last.time) return
        val t = WorkDay.addAt(p, WorkDay.LEAVE, last.site.ifEmpty { site }, at)
        p.edit().putLong(Prefs.AUTO_LEAVE, t).apply()
        WorkdayCenter.refresh(ctx)
        notify(ctx, L.t("🚪 Saliste de ${last.site.ifEmpty { site }}", "🚪 You left ${last.site.ifEmpty { site }}"),
            L.t("Lo anoté solo: saliste a las ${WorkStore.time(t)}.", "Logged automatically: you left at ${WorkStore.time(t)}."),
            listOf(L.t("Deshacer", "Undo") to undoIntent(ctx, t), L.t("🕐 Cambiar hora", "🕐 Change time") to fixIntent(ctx, t)), NOTIF_ID)
    }

    private fun undoIntent(ctx: Context, t: Long): PendingIntent =
        PendingIntent.getBroadcast(ctx, (t / 1000 % 100000).toInt(), Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_UNDO).putExtra(EXTRA_TIME, t),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun markIntent(ctx: Context, t: Long, site: String): PendingIntent =
        PendingIntent.getBroadcast(ctx, 5121, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_MARK).putExtra(EXTRA_TIME, t).putExtra(EXTRA_SITE, site),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private const val NOTIF_ID = 4720

    private fun cancel(ctx: Context) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(NOTIF_ID)
    }

    fun notify(ctx: Context, title: String, text: String, action: Pair<String, PendingIntent>?, id: Int = NOTIF_ID) =
        notify(ctx, title, text, if (action == null) emptyList() else listOf(action), id)

    fun notify(ctx: Context, title: String, text: String, actions: List<Pair<String, PendingIntent>>, id: Int) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, L.t("Mi jornada: avisos automáticos", "My workday: automatic alerts"),
                NotificationManager.IMPORTANCE_DEFAULT))
        }
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
        b.setSmallIcon(WorkDay.iconRes())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(ctx, 97, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        for (a in actions.take(3)) {
            @Suppress("DEPRECATION")
            b.addAction(Notification.Action.Builder(null, a.first, a.second).build())
        }
        try { nm.notify(id, b.build()) } catch (_: SecurityException) { }
    }
}

class AutoSitesReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try { AutoSites.handle(ctx, intent) } catch (_: Throwable) { }
    }
}
