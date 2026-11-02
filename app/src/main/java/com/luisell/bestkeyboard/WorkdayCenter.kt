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
        // 🧰 Al empezar el día desde la pantalla de bloqueo o el widget: te recuerda tu checklist
        if (kind == WorkDay.enabledKinds().first() && kind != WorkDay.ARRIVE && p.getBoolean(Prefs.CHECK_ON, true)) {
            val items = Todos.checkItems(p).filter { it !in Todos.checked(p) }
            if (items.isNotEmpty()) AutoSites.notify(ctx, L.t("🧰 ¿Llevas todo?", "🧰 Got everything?"), items.joinToString(" · "), null, 4723)
        }
        if (kind == WorkDay.ARRIVE && hasLocation(ctx)) {
            // La hora ya quedó anotada; ahora el GPS busca dónde estás
            locate(ctx) { loc ->
                val s = loc?.let { SiteStore.nearest(p, it.latitude, it.longitude) }
                if (s != null || loc == null) {
                    if (s != null) { WorkDay.setSite(p, t, s.name); Todos.arrivedAt(ctx, s.name) }
                    refresh(ctx)
                    done?.invoke(WorkDay.Event(t, kind, s?.name ?: ""))
                    return@locate
                }
                // Un lugar que no tienes guardado: busca la dirección y luego te pregunta si lo guardas
                Thread {
                    val full = SiteStore.addressOf(ctx, loc.latitude, loc.longitude)
                    val short = if (full.isEmpty()) "" else SiteStore.shortAddress(full)
                    main.post {
                        if (short.isNotEmpty()) {
                            WorkDay.setSite(p, t, short)
                            SiteStore.setNew(p, SiteStore.NewPlace(t, short, full, loc.latitude, loc.longitude))
                            // Para saber solo cuándo te vas de este lugar nuevo
                            AutoSites.register(ctx)
                        }
                        refresh(ctx)
                        done?.invoke(WorkDay.Event(t, kind, short))
                    }
                }.start()
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
        try { scheduleOvertime(ctx) } catch (_: Exception) { }
        try { scheduleNight(ctx) } catch (_: Exception) { }
    }

    const val ACTION_NIGHT = "com.luisell.bestkeyboard.WORKDAY_NIGHT"

    /** 🌙 A las 9 PM: si la jornada sigue abierta, te avisa. */
    private fun scheduleNight(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(ctx, 4715, Intent(ctx, WorkdayReceiver::class.java).setAction(ACTION_NIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        val p = Prefs.get(ctx)
        if (!p.getBoolean(Prefs.NIGHT_REMIND, true) || !WorkDay.isRunning(WorkDay.day(p))) return
        val c = java.util.Calendar.getInstance()
        val at = p.getInt(Prefs.NIGHT_TIME, 21 * 60)
        c.set(java.util.Calendar.HOUR_OF_DAY, at / 60); c.set(java.util.Calendar.MINUTE, at % 60); c.set(java.util.Calendar.SECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis()) return
        try { am.setWindow(android.app.AlarmManager.RTC_WAKEUP, c.timeInMillis, 10 * 60_000L, pi) } catch (_: Exception) { }
    }

    /** "🌙 Tu jornada sigue abierta" con el botón para cerrarla. */
    fun nightAlert(ctx: Context) {
        val p = Prefs.get(ctx)
        L.set(p.getString(Prefs.LANG, "es") ?: "es")
        val ev = WorkDay.day(p)
        if (!p.getBoolean(Prefs.NIGHT_REMIND, true) || !WorkDay.isRunning(ev)) return
        val leave = ev.lastOrNull { it.kind == WorkDay.LEAVE }
        val text = (if (leave != null) L.t("Saliste de ${leave.site.ifEmpty { "tu sitio" }} a las ${WorkStore.time(leave.time)}. ",
            "You left ${leave.site.ifEmpty { "your site" }} at ${WorkStore.time(leave.time)}. ") else "") +
            L.t("¿A qué hora llegaste a casa?", "What time did you get home?")
        val fin = WorkDay.finalKind()
        // Botones: la hora que calculo (salida + lo que tardas normalmente) o escribir la hora tú
        val guess = leave?.let { it.time + WorkDay.typicalCommute(p) }?.takeIf { it < System.currentTimeMillis() }
        val actions = ArrayList<Pair<String, PendingIntent>>()
        if (guess != null) actions.add(L.t("🏠 A las ", "🏠 At ") + WorkStore.time(guess) to
            PendingIntent.getBroadcast(ctx, 4716, Intent(ctx, AutoSitesReceiver::class.java).setAction(AutoSites.ACTION_HOME_AT)
                .putExtra(AutoSites.EXTRA_TIME, guess).putExtra(EXTRA_KIND, fin), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        actions.add(L.t("🕐 Escribir la hora", "🕐 Type the time") to
            PendingIntent.getActivity(ctx, 4717, Intent(ctx, TimeSetActivity::class.java).putExtra(TimeSetActivity.EXTRA_KIND, fin)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        actions.add(L.t("Ahora", "Now") to intentFor(ctx, fin, 210))
        AutoSites.notify(ctx, L.t("🌙 Tu jornada sigue abierta", "🌙 Your workday is still open"), text, actions, 4722)
    }

    const val ACTION_OVERTIME = "com.luisell.bestkeyboard.WORKDAY_OVERTIME"

    /** Programa el aviso de "ya llevas 8 horas" para la hora justa. */
    private fun scheduleOvertime(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(ctx, 4714, Intent(ctx, WorkdayReceiver::class.java).setAction(ACTION_OVERTIME),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        val p = Prefs.get(ctx)
        val ev = WorkDay.day(p)
        if (WorkDay.dayHours <= 0 || !WorkDay.isRunning(ev)) return
        if (p.getString(Prefs.OVERTIME_SENT, "") == todayKey()) return
        val left = WorkDay.dayHours * 3600_000L - WorkDay.summary(ev).total
        if (left <= 0) { overtimeAlert(ctx); return }
        try { am.setWindow(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + left, 2 * 60_000L, pi) } catch (_: Exception) { }
    }

    private fun todayKey() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

    /** "⏰ Ya llevas 8 horas: desde ahora es tiempo extra" (una vez al día). */
    fun overtimeAlert(ctx: Context) {
        val p = Prefs.get(ctx)
        L.set(p.getString(Prefs.LANG, "es") ?: "es")
        val ev = WorkDay.day(p)
        if (WorkDay.dayHours <= 0 || !WorkDay.isRunning(ev) || p.getString(Prefs.OVERTIME_SENT, "") == todayKey()) return
        if (WorkDay.summary(ev).total < WorkDay.dayHours * 3600_000L - 60_000L) { scheduleOvertime(ctx); return }
        p.edit().putString(Prefs.OVERTIME_SENT, todayKey()).apply()
        AutoSites.notify(ctx, L.t("⏰ Ya llevas ${WorkDay.dayHours} horas", "⏰ You've worked ${WorkDay.dayHours} hours"),
            L.t("Desde ahora es tiempo extra. Semana: ${WorkDay.dur(WorkDay.weekTotal(p))}.", "From now on it's overtime. Week: ${WorkDay.dur(WorkDay.weekTotal(p))}."),
            null, 4721)
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
        if (ev.isEmpty()) return WorkDay.title() to L.t("Toca «", "Tap «") + WorkDay.label(WorkDay.enabledKinds().first()) + L.t("» para empezar", "» to start")
        val last = ev.last()
        val title = WorkDay.title() + " · " + WorkDay.dur(s.total)
        val text = when (last.kind) {
            WorkDay.LUNCH -> L.t("🍔 Almorzando desde ", "🍔 At lunch since ") + WorkStore.time(last.time)
            WorkDay.ARRIVE -> (if (last.site.isNotEmpty()) L.t("📍 En ${last.site}", "📍 At ${last.site}") else L.t("📍 En el sitio", "📍 On site")) +
                L.t(" desde ", " since ") + WorkStore.time(last.time)
            WorkDay.finalKind() -> L.t("✅ Terminaste · en el sitio ", "✅ Done · on site ") + WorkDay.dur(s.site)
            else -> WorkDay.label(last.kind) + " · " + WorkStore.time(last.time)
        }
        return title to text
    }

    // ---------------------------------------------------------------- Notificación (pantalla de bloqueo)

    private fun updateNotification(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val p = Prefs.get(ctx)
        val ev = WorkDay.day(p)
        val running = WorkDay.isRunning(ev)
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
        b.setSmallIcon(WorkDay.iconRes())
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(PendingIntent.getActivity(ctx, 98, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        if (running) b.setShowWhen(true).setWhen(ev.first().time).setUsesChronometer(true) else b.setShowWhen(false)
        // Sin empezar (o ya terminaste): solo el botón 🛻 Comencé, listo en la pantalla de bloqueo
        // Lo que sigue y, si es otro, el botón que cierra el día
        val next = if (!running) listOf(WorkDay.enabledKinds().first())
            else if (WorkDay.onBreak(ev)) listOf(WorkDay.BACK, WorkDay.finalKind())
            else listOf(WorkDay.nextKind(ev), WorkDay.LUNCH, WorkDay.finalKind()).distinct()
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
        v.setTextViewText(R.id.w_title, if (ev.isEmpty()) title else WorkDay.title())
        v.setTextViewText(R.id.w_status, text)
        val running = WorkDay.isRunning(ev)
        if (ev.isNotEmpty()) {
            val elapsed = WorkDay.summary(ev).total
            v.setChronometer(R.id.w_timer, SystemClock.elapsedRealtime() - elapsed, null, running)
            v.setViewVisibility(R.id.w_timer, View.VISIBLE)
        } else v.setViewVisibility(R.id.w_timer, View.GONE)
        val next = WorkDay.nextKind(ev)
        val ids = listOf(R.id.w_start to WorkDay.START, R.id.w_arrive to WorkDay.ARRIVE, R.id.w_leave to WorkDay.LEAVE, R.id.w_home to WorkDay.HOME)
        val short = mapOf(
            WorkDay.START to L.t("🛻\nComencé", "🛻\nStart"),
            WorkDay.ARRIVE to L.t("📍\nLlegué", "📍\nArrived"),
            WorkDay.LEAVE to L.t("🚪\nSalí", "🚪\nLeft"),
            WorkDay.HOME to L.t("🏠\nCasa", "🏠\nHome"),
        )
        for ((i, pair) in ids.withIndex()) {
            val (id, k) = pair
            // Los botones que quitaste no salen; los que renombraste salen con tu nombre
            v.setViewVisibility(id, if (WorkDay.isOn(k)) View.VISIBLE else View.GONE)
            val custom = WorkDay.customName(k)
            v.setTextViewText(id, if (custom.isEmpty()) short[k] else custom.replaceFirst(" ", "\n"))
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
        if (intent.action == WorkdayCenter.ACTION_OVERTIME) { WorkdayCenter.overtimeAlert(ctx); return }
        if (intent.action == WorkdayCenter.ACTION_NIGHT) { WorkdayCenter.nightAlert(ctx); return }
        if (intent.action == WorkdayCenter.ACTION_REFRESH || intent.action == Intent.ACTION_BOOT_COMPLETED) {
            WorkdayCenter.refresh(ctx)
            // Al prender el teléfono las geocercas se borran: se vuelven a poner
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) AutoSites.register(ctx)
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
        val running = WorkDay.isRunning(ev)
        val next = WorkDay.nextKind(ev)
        t.label = WorkDay.plainLabel(next)
        t.icon = android.graphics.drawable.Icon.createWithResource(this, WorkDay.iconRes())
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
