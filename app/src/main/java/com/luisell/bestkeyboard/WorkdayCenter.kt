package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast

/**
 * El centro de "Mi jornada": anota los eventos (desde el teclado, el widget o la notificación),
 * busca en qué sitio estás con el GPS, y actualiza el widget y la notificación de la pantalla de bloqueo.
 */
object WorkdayCenter {
    const val ACTION = "com.luisell.bestkeyboard.WORKDAY"
    const val ACTION_REFRESH = "com.luisell.bestkeyboard.WORKDAY_REFRESH"
    const val EXTRA_KIND = "kind"
    private const val CHANNEL = "workday"
    private const val NOTIF_ID = 4712
    private val main = Handler(Looper.getMainLooper())

    fun hasLocation(ctx: Context) =
        ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Dónde estás ahora (o null). Espera máximo unos segundos. */
    @SuppressLint("MissingPermission")
    fun locate(ctx: Context, done: (Location?) -> Unit) {
        if (!hasLocation(ctx)) { done(null); return }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: run { done(null); return }
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        } ?: run { done(null); return }
        var finished = false
        val finish = { l: Location? -> if (!finished) { finished = true; done(l) } }
        val last = try { lm.getLastKnownLocation(provider) } catch (_: Exception) { null }
        main.postDelayed({ finish(last) }, 8000)
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, ctx.mainExecutor) { l -> finish(l ?: last) }
            } else finish(last)
        } catch (_: Exception) { finish(last) }
    }

    /** Anota un evento de la jornada. Al llegar, busca el sitio con el GPS; al salir, usa el mismo sitio. */
    fun record(ctx: Context, kind: String, done: ((WorkDay.Event) -> Unit)? = null) {
        val p = Prefs.get(ctx)
        val events = WorkDay.day(p)
        val site = if (kind == WorkDay.LEAVE) events.lastOrNull { it.kind == WorkDay.ARRIVE }?.site ?: "" else ""
        val t = WorkDay.add(p, kind, site)
        refresh(ctx)
        if (kind == WorkDay.ARRIVE && SiteStore.all(p).isNotEmpty()) {
            locate(ctx) { loc ->
                val s = loc?.let { SiteStore.nearest(p, it.latitude, it.longitude) }
                if (s != null) WorkDay.setSite(p, t, s.name)
                refresh(ctx)
                done?.invoke(WorkDay.Event(t, kind, s?.name ?: ""))
            }
        } else done?.invoke(WorkDay.Event(t, kind, site))
    }

    /** Actualiza el widget y la notificación. */
    fun refresh(ctx: Context) {
        try { updateWidgets(ctx) } catch (_: Exception) { }
        try { updateNotification(ctx) } catch (_: Exception) { }
        try {
            if (Build.VERSION.SDK_INT >= 24) android.service.quicksettings.TileService.requestListeningState(ctx, ComponentName(ctx, WorkdayTile::class.java))
        } catch (_: Exception) { }
        scheduleMorning(ctx)
    }

    /** Cada madrugada se pone en cero (para que la notificación de ayer no se quede). */
    private fun scheduleMorning(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val c = java.util.Calendar.getInstance()
        c.set(java.util.Calendar.HOUR_OF_DAY, 3); c.set(java.util.Calendar.MINUTE, 0); c.set(java.util.Calendar.SECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) c.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val pi = PendingIntent.getBroadcast(ctx, 4713, Intent(ctx, WorkdayReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try { am.setWindow(android.app.AlarmManager.RTC, c.timeInMillis, 30 * 60 * 1000L, pi) } catch (_: Exception) { }
    }

    /** Qué toca después (para la tarjeta de ajustes rápidos). */
    fun nextFor(ctx: Context): String = WorkDay.nextKind(WorkDay.day(Prefs.get(ctx)))

    private fun intentFor(ctx: Context, kind: String, req: Int): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // "Llegué" abre una pantallita invisible (para poder usar el GPS); lo demás se anota directo
        return if (kind == WorkDay.ARRIVE) {
            PendingIntent.getActivity(ctx, req, Intent(ctx, WorkdayActionActivity::class.java)
                .putExtra(EXTRA_KIND, kind).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags)
        } else {
            PendingIntent.getBroadcast(ctx, req, Intent(ctx, WorkdayReceiver::class.java).setAction(ACTION).putExtra(EXTRA_KIND, kind), flags)
        }
    }

    private fun status(ctx: Context): Pair<String, String> {
        val ev = WorkDay.day(Prefs.get(ctx))
        val s = WorkDay.summary(ev)
        if (ev.isEmpty()) return L.t("🛻 Mi jornada", "🛻 My workday") to L.t("Toca 🛻 al subir a la camioneta", "Tap 🛻 when you get in the truck")
        val last = ev.last()
        val title = L.t("🛻 Mi jornada · ", "🛻 My workday · ") + WorkDay.dur(s.total)
        val text = when (last.kind) {
            WorkDay.ARRIVE -> (if (last.site.isNotEmpty()) L.t("📍 En ${last.site}", "📍 At ${last.site}") else L.t("📍 En el sitio", "📍 On site")) +
                L.t(" desde ", " since ") + WorkStore.time(last.time)
            WorkDay.HOME -> L.t("🏠 Terminaste · en el sitio ", "🏠 Done · on site ") + WorkDay.dur(s.site)
            else -> L.t("🛻 Manejando desde ", "🛻 Driving since ") + WorkStore.time(last.time)
        }
        return title to text
    }

    // ---------------------------------------------------------------- Notificación (pantalla de bloqueo)

    private fun updateNotification(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val p = Prefs.get(ctx)
        val ev = WorkDay.day(p)
        val running = ev.isNotEmpty() && ev.last().kind != WorkDay.HOME
        val always = p.getBoolean(Prefs.WORKDAY_ALWAYS, true)
        if (!p.getBoolean(Prefs.WORKDAY_NOTIF, true) || (!running && !always)) { nm.cancel(NOTIF_ID); return }
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, L.t("Mi jornada", "My workday"), NotificationManager.IMPORTANCE_LOW)
            ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        val (title, text) = status(ctx)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
        b.setSmallIcon(R.drawable.ic_tile_truck)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(ctx, 98, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        if (running) b.setShowWhen(true).setWhen(ev.first().time).setUsesChronometer(true) else b.setShowWhen(false)
        // Sin empezar (o ya terminaste): solo el botón 🛻 Comencé, listo en la pantalla de bloqueo
        val next = if (!running) listOf(WorkDay.START) else when (ev.last().kind) {
            WorkDay.START -> listOf(WorkDay.ARRIVE, WorkDay.HOME)
            WorkDay.ARRIVE -> listOf(WorkDay.LEAVE)
            else -> listOf(WorkDay.ARRIVE, WorkDay.HOME)
        }
        for ((i, k) in next.withIndex()) {
            @Suppress("DEPRECATION")
            b.addAction(Notification.Action.Builder(null, WorkDay.label(k), intentFor(ctx, k, 200 + i)).build())
        }
        try { nm.notify(NOTIF_ID, b.build()) } catch (_: SecurityException) { }
    }

    // ---------------------------------------------------------------- Widget

    private fun updateWidgets(ctx: Context) {
        val am = AppWidgetManager.getInstance(ctx) ?: return
        val ids = am.getAppWidgetIds(ComponentName(ctx, WorkdayWidget::class.java))
        if (ids.isEmpty()) return
        val views = widgetViews(ctx)
        for (id in ids) am.updateAppWidget(id, views)
    }

    fun widgetViews(ctx: Context): RemoteViews {
        L.set(Prefs.get(ctx).getString(Prefs.LANG, "es") ?: "es")
        val v = RemoteViews(ctx.packageName, R.layout.widget_workday)
        val p = Prefs.get(ctx)
        val ev = WorkDay.day(p)
        val (title, text) = status(ctx)
        v.setTextViewText(R.id.w_title, if (ev.isEmpty()) title else L.t("🛻 Mi jornada", "🛻 My workday"))
        v.setTextViewText(R.id.w_status, text)
        val running = ev.isNotEmpty() && ev.last().kind != WorkDay.HOME
        if (ev.isNotEmpty()) {
            val elapsed = WorkDay.summary(ev).total
            v.setChronometer(R.id.w_timer, SystemClock.elapsedRealtime() - elapsed, null, running)
            v.setViewVisibility(R.id.w_timer, View.VISIBLE)
        } else v.setViewVisibility(R.id.w_timer, View.GONE)
        val next = WorkDay.nextKind(ev)
        val ids = listOf(R.id.w_start to WorkDay.START, R.id.w_arrive to WorkDay.ARRIVE, R.id.w_leave to WorkDay.LEAVE, R.id.w_home to WorkDay.HOME)
        val names = mapOf(
            WorkDay.START to L.t("🛻\nComencé", "🛻\nStart"),
            WorkDay.ARRIVE to L.t("📍\nLlegué", "📍\nArrived"),
            WorkDay.LEAVE to L.t("🚪\nSalí", "🚪\nLeft"),
            WorkDay.HOME to L.t("🏠\nCasa", "🏠\nHome"),
        )
        for ((i, pair) in ids.withIndex()) {
            val (id, k) = pair
            v.setTextViewText(id, names[k])
            v.setInt(id, "setBackgroundResource", if (k == next) R.drawable.widget_btn_next else R.drawable.widget_btn)
            v.setTextColor(id, if (k == next) 0xFF0B1020.toInt() else 0xFFFFFFFF.toInt())
            v.setOnClickPendingIntent(id, intentFor(ctx, k, 100 + i))
        }
        v.setOnClickPendingIntent(R.id.w_title, PendingIntent.getActivity(ctx, 99,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return v
    }

    fun toastFor(ctx: Context, e: WorkDay.Event) {
        L.set(Prefs.get(ctx).getString(Prefs.LANG, "es") ?: "es")
        Toast.makeText(ctx, WorkDay.labelOf(e) + " · " + WorkStore.time(e.time), Toast.LENGTH_SHORT).show()
    }
}

/** El widget de "Mi jornada" (pantalla de inicio y, si tu teléfono lo deja, pantalla de bloqueo). */
class WorkdayWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, am: AppWidgetManager, ids: IntArray) {
        val views = WorkdayCenter.widgetViews(ctx)
        for (id in ids) am.updateAppWidget(id, views)
    }
}

/** Botones del widget y de la notificación (menos "Llegué", que necesita el GPS). */
class WorkdayReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == WorkdayCenter.ACTION_REFRESH || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WorkdayCenter.refresh(ctx)
            return
        }
        val kind = intent.getStringExtra(WorkdayCenter.EXTRA_KIND) ?: return
        val pending = goAsync()
        WorkdayCenter.record(ctx, kind) { e ->
            WorkdayCenter.toastFor(ctx, e)
            pending.finish()
        }
    }
}

/** Pantallita invisible para "Llegué al sitio": usa el GPS para saber en qué sitio estás (también desde la pantalla de bloqueo). */
class WorkdayActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        val kind = intent.getStringExtra(WorkdayCenter.EXTRA_KIND) ?: run { finish(); return }
        WorkdayCenter.record(this, kind) { e ->
            WorkdayCenter.toastFor(applicationContext, e)
            finish()
        }
    }
}

/**
 * Botón en los ajustes rápidos (deslizas la barra de arriba del teléfono, también con la pantalla bloqueada):
 * un toque anota lo que sigue en tu jornada (Comencé → Llegué → Salí → Casa).
 */
class WorkdayTile : android.service.quicksettings.TileService() {
    override fun onStartListening() {
        super.onStartListening()
        paint()
    }

    private fun paint() {
        val t = qsTile ?: return
        L.set(Prefs.get(this).getString(Prefs.LANG, "es") ?: "es")
        val ev = WorkDay.day(Prefs.get(this))
        val running = ev.isNotEmpty() && ev.last().kind != WorkDay.HOME
        val next = WorkDay.nextKind(ev)
        t.label = WorkDay.label(next).drop(2).trim()
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = if (running) L.t("Mi jornada · ", "Workday · ") + WorkDay.dur(WorkDay.summary(ev).total) else L.t("Mi jornada", "My workday")
        t.state = if (running) android.service.quicksettings.Tile.STATE_ACTIVE else android.service.quicksettings.Tile.STATE_INACTIVE
        t.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val kind = WorkdayCenter.nextFor(this)
        WorkdayCenter.record(this, kind) { e ->
            WorkdayCenter.toastFor(applicationContext, e)
            paint()
        }
        paint()
    }
}
