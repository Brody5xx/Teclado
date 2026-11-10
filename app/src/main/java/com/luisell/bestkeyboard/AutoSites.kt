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
    const val ACTION_HOME_CHECK = "com.luisell.bestkeyboard.AUTO_HOME_CHECK"
    const val EXTRA_TIME = "time"
    const val EXTRA_SITE = "site"
    private const val NEW_ID = "new:"
    private const val SITE_ID = "site:"
    private const val HOME_ID = "home"
    /** 🎯 El círculo grande (1 km) alrededor de cada sitio: al entrar se prende el GPS fino. */
    private const val NEAR_ID = "near:"
    const val ACTION_ACTIVITY = "com.luisell.bestkeyboard.AUTO_ACTIVITY"
    /**
     * 🎯 Sensibilidad (como Samsung Rutinas): cuánto tienes que quedarte para que cuente como «llegué»
     * y qué tan rápido avisa el teléfono. Alta = rápido; Baja = solo si de verdad te quedas.
     */
    private fun sens(ctx: Context) = Prefs.get(ctx).getString(Prefs.SITE_SENS, "normal") ?: "normal"
    private fun dwellMs(ctx: Context): Int = when (sens(ctx)) { "high" -> 60_000; "low" -> 6 * 60_000; else -> 3 * 60_000 }
    private fun responsiveMs(ctx: Context): Int = when (sens(ctx)) { "high" -> 0; "low" -> 3 * 60_000; else -> 60_000 }

    fun isOn(ctx: Context) = Prefs.get(ctx).getBoolean(Prefs.AUTO_SITES, false)

    /** Cuánto tienes que quedarte en [site] para que cuente (más si tocaste «Deshacer» ahí). */
    fun dwellFor(ctx: Context, site: String): Int = dwellMs(ctx) + SiteLearn.extraDwellMs(Prefs.get(ctx), site)

    /** Cuándo entraste a [site] (sin borrarlo), o null. */
    fun enteredAt(p: android.content.SharedPreferences, site: String): Long? {
        val o = try { JSONObject(p.getString(Prefs.AUTO_ENTER, "{}") ?: "{}") } catch (_: Exception) { return null }
        val t = o.optLong(site, -1L)
        return if (t > 0 && WorkStore.sameDay(t, System.currentTimeMillis())) t else null
    }

    /** ¿Detectar cuando vas en vehículo? (necesita el permiso «Actividad física»). */
    fun vehicleOn(ctx: Context) = Prefs.get(ctx).getBoolean("veh_detect", true) &&
        (Build.VERSION.SDK_INT < 29 || ctx.checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED)

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
                .setCircularRegion(s.lat, s.lon, SiteStore.radiusOf(p, s).toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setLoiteringDelay(dwellFor(app, s.name))
                .setNotificationResponsiveness(responsiveMs(app))
                .build())
            // 🎯 El círculo grande: al acercarte se prende el GPS fino (para la hora exacta)
            if (PreciseTracker.enabled(app)) fences.add(Geofence.Builder()
                .setRequestId(NEAR_ID + s.name.take(80))
                .setCircularRegion(s.lat, s.lon, maxOf(1000f, SiteStore.radiusOf(p, s) + 600f))
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .setNotificationResponsiveness(30_000)
                .build())
            if (fences.size >= 94) break
        }
        // 🏠 Tu casa: para anotar solo «Llegué a casa» con la hora real
        SiteStore.home(p)?.let { (la, lo) ->
            fences.add(Geofence.Builder()
                .setRequestId(HOME_ID)
                .setCircularRegion(la, lo, 150f)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setLoiteringDelay(2 * 60 * 1000)
                .setNotificationResponsiveness(responsiveMs(app))
                .build())
        }
        // El lugar nuevo de hoy (no guardado): solo para saber cuándo te vas
        SiteStore.getNew(p)?.let { n ->
            fences.add(Geofence.Builder()
                .setRequestId(NEW_ID + n.short.take(80))
                .setCircularRegion(n.lat, n.lon, SiteStore.defaultRadius(p).toFloat())
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
        registerVehicle(app)
    }

    // ---------------------------------------------------------------- 🛻 Detectar el vehículo

    private fun vehiclePending(ctx: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(ctx, 5130, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_ACTIVITY), flags)
    }

    /** Pide a Android que avise cuando te subes o te bajas de un vehículo (con los sensores, casi sin batería). */
    @SuppressLint("MissingPermission")
    private fun registerVehicle(ctx: Context) {
        val ar = try { com.google.android.gms.location.ActivityRecognition.getClient(ctx) } catch (_: Throwable) { return }
        try { ar.removeActivityTransitionUpdates(vehiclePending(ctx)) } catch (_: Throwable) { }
        if (!ready(ctx) || !vehicleOn(ctx)) return
        fun tr(type: Int, kind: Int) = com.google.android.gms.location.ActivityTransition.Builder().setActivityType(type).setActivityTransition(kind).build()
        val v = com.google.android.gms.location.DetectedActivity.IN_VEHICLE
        val list = listOf(
            tr(v, com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            tr(v, com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_EXIT),
        )
        try { ar.requestActivityTransitionUpdates(com.google.android.gms.location.ActivityTransitionRequest(list), vehiclePending(ctx)) } catch (_: Throwable) { }
    }

    /** Te subiste o te bajaste de un vehículo. */
    private fun onVehicle(ctx: Context, p: android.content.SharedPreferences, intent: Intent) {
        val r = com.google.android.gms.location.ActivityTransitionResult.extractResult(intent) ?: return
        for (e in r.transitionEvents) {
            if (e.activityType != com.google.android.gms.location.DetectedActivity.IN_VEHICLE) continue
            val t = System.currentTimeMillis() - (android.os.SystemClock.elapsedRealtimeNanos() - e.elapsedRealTimeNanos) / 1_000_000L
            val day = WorkDay.day(p)
            if (e.transitionType == com.google.android.gms.location.ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                p.edit().putLong("veh_in", t).apply()
                if (!WorkDay.isWorkDay(p)) continue
                if (day.isEmpty()) askStart(ctx, p, t)
                // Estás en un sitio y te subes: viene la salida (el GPS fino da la hora exacta)
                else if (WorkDay.lastMain(day)?.kind == WorkDay.ARRIVE) PreciseTracker.start(ctx, "vehicle_in")
            } else {
                p.edit().putLong("veh_out", t).apply()
                if (WorkDay.isWorkDay(p) || day.isNotEmpty()) PreciseTracker.start(ctx, "vehicle_out")
            }
        }
    }

    /** 🛻 Te subiste a un vehículo y todavía no empieza tu día: pregunta (una vez al día) con la hora exacta. */
    private fun askStart(ctx: Context, p: android.content.SharedPreferences, at: Long) {
        if (p.getString("veh_ask_day", "") == dayKey()) return
        p.edit().putString("veh_ask_day", dayKey()).apply()
        val first = WorkDay.enabledKinds().first()
        val yes = PendingIntent.getBroadcast(ctx, 5131, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_MARK)
            .putExtra(EXTRA_TIME, at).putExtra(EXTRA_SITE, "").putExtra(WorkdayCenter.EXTRA_KIND, first),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notify(ctx, L.t("¿Empezó tu día?", "Did your day start?"),
            L.t("Te subiste a un vehículo a las ${WorkStore.time(at)}. Si es la camioneta del trabajo, toca «Sí».",
                "You got into a vehicle at ${WorkStore.time(at)}. If it's the work truck, tap «Yes»."),
            listOf(L.t("Sí, empezó a las ", "Yes, it started at ") + WorkStore.time(at) to yes), NOTIF_ID)
    }

    /** La hora en que te bajaste del vehículo, si fue cerca de [at] (para terminar el día al minuto). */
    private fun vehOutNear(p: android.content.SharedPreferences, at: Long): Long {
        val v = p.getLong("veh_out", 0L)
        return if (v > 0 && v >= at - 2 * 60_000L && v <= at + 20 * 60_000L && v <= System.currentTimeMillis()) v else at
    }

    /** La hora en que te subiste al vehículo, si fue poco antes de [at] (para empezar el día al minuto). */
    private fun vehInNear(p: android.content.SharedPreferences, at: Long): Long {
        val v = p.getLong("veh_in", 0L)
        return if (v > 0 && v <= at + 60_000L && v >= at - 20 * 60_000L) v else at
    }

    // ---------------------------------------------------------------- Entrar, quedarte y salir de un sitio
    // (los usan las geocercas y el GPS fino)

    fun siteEnter(ctx: Context, site: String, at: Long) {
        val p = Prefs.get(ctx)
        saveEnter(p, site, at)
        cancelPendingExit(ctx, site)
        if (SiteStore.find(p, site)?.isStart != true) falseExit(ctx, site, at)
    }

    fun siteDwell(ctx: Context, site: String, enterAt: Long) {
        val p = Prefs.get(ctx)
        takeEnter(p, site)
        if (SiteStore.find(p, site)?.isStart == true) endAtParking(ctx, site, vehOutNear(p, enterAt))
        else arrived(ctx, site, enterAt)
    }

    fun siteExit(ctx: Context, site: String, at: Long) {
        val p = Prefs.get(ctx)
        takeEnter(p, site)
        if (SiteStore.find(p, site)?.isStart == true) { if (WorkDay.day(p).isEmpty()) pendingExit(ctx, site, vehInNear(p, at)) }
        else pendingExit(ctx, site, at)
    }

    // ---------------------------------------------------------------- Cuando el teléfono avisa

    fun handle(ctx: Context, intent: Intent, rcv: BroadcastReceiver? = null) {
        val p = Prefs.get(ctx)
        L.set(p.getString(Prefs.LANG, "es") ?: "es")
        when (intent.action) {
            ACTION_UNDO -> {
                val t = intent.getLongExtra(EXTRA_TIME, -1L)
                // 🧠 Una llegada automática que no era: ese sitio esperará un poco más la próxima vez
                if (t > 0) WorkDay.day(p, t).firstOrNull { it.time == t && it.kind == WorkDay.ARRIVE }?.let { SiteLearn.undo(p, it.site); register(ctx) }
                if (t > 0) WorkDay.remove(p, t)
                WorkdayCenter.refresh(ctx)
                cancel(ctx)
                return
            }
            ACTION_EXIT_CHECK -> {
                // Pasaron los minutos de espera y no volviste a entrar: ahora sí se anota la salida
                val o = try { JSONObject(p.getString(Prefs.AUTO_EXIT_PENDING, "") ?: "") } catch (_: Exception) { null } ?: return
                p.edit().remove(Prefs.AUTO_EXIT_PENDING).apply()
                val sn = o.optString("s")
                if (SiteStore.find(p, sn)?.isStart == true) startFromParking(ctx, sn, o.optLong("t")) else left(ctx, sn, o.optLong("t"))
                return
            }
            ACTION_HOME_CHECK -> {
                // Pasó el rato en casa y no te fuiste: ahora sí se cierra el día (o se pregunta)
                val t = p.getLong(Prefs.HOME_PENDING, 0L)
                p.edit().remove(Prefs.HOME_PENDING).apply()
                if (t > 0) closeAtHome(ctx, t)
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
                val kind = intent.getStringExtra(WorkdayCenter.EXTRA_KIND) ?: WorkDay.ARRIVE
                if (kind == WorkDay.ARRIVE || WorkDay.day(p).isEmpty()) WorkDay.addAt(p, kind, site, t)
                WorkdayCenter.refresh(ctx)
                cancel(ctx)
                Todos.arrivedAt(ctx, site) // 📍 lo que pediste que te recordara al llegar aquí
                return
            }
        }
        if (!isOn(ctx)) return
        if (intent.action == ACTION_ACTIVITY) { onVehicle(ctx, p, intent); return }
        // 📅 Tu día libre: no anota nada solo (si ya empezaste a trabajar hoy, sí)
        if (!WorkDay.isWorkDay(p)) return
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError()) return
        val now = System.currentTimeMillis()
        val at = ev.triggeringLocation?.time?.takeIf { it in (now - 30 * 60_000L)..now } ?: now
        // 🎯 Antes de anotar, confirma con el GPS fino que de verdad entraste o saliste (el GPS a veces «brinca»)
        val pr = rcv?.goAsync()
        WorkdayCenter.preciseFix(ctx.applicationContext, 8000L, 30f) { fix ->
            try { handle(ctx.applicationContext, p, ev, at, fix) } catch (_: Throwable) { } finally { pr?.finish() }
        }
    }

    /** ¿Lo que avisó el teléfono es verdad? Mira la lectura fina (o la del aviso, si es buena). */
    private fun confirmed(ctx: Context, transition: Int, lat: Double, lon: Double, radius: Float,
                          fix: android.location.Location?, trig: android.location.Location?, name: String): Boolean {
        // 📶 Las redes Wi-Fi del sitio se ven: estás adentro (el GPS adentro de edificios falla, el Wi-Fi no)
        if (name.isNotEmpty() && SiteStore.find(Prefs.get(ctx), name) != null) {
            if (SiteLearn.wifiMatch(ctx, name) == true) {
                if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) { GpsCheck.logIgnored(ctx, true, name); return false }
                return true
            }
        }
        val loc = when {
            fix != null && fix.accuracy <= 100f -> fix
            trig != null && trig.accuracy <= radius -> trig
            else -> return true // no se puede revisar: se hace como antes
        }
        val d = FloatArray(1)
        android.location.Location.distanceBetween(lat, lon, loc.latitude, loc.longitude, d)
        val ok = when (transition) {
            // Llegar: tienes que estar dentro del círculo (con el margen de error de la lectura)
            Geofence.GEOFENCE_TRANSITION_ENTER, Geofence.GEOFENCE_TRANSITION_DWELL -> d[0] <= radius + loc.accuracy
            // Salir: si la lectura dice claramente que sigues adentro, era un brinco del GPS
            else -> d[0] + loc.accuracy >= radius * 0.9f
        }
        if (!ok) GpsCheck.logIgnored(ctx, transition == Geofence.GEOFENCE_TRANSITION_EXIT, name)
        return ok
    }

    private fun handle(ctx: Context, p: android.content.SharedPreferences, ev: GeofencingEvent, at: Long, fix: android.location.Location?) {
        val trig = ev.triggeringLocation
        val tr = ev.geofenceTransition
        for (g in ev.triggeringGeofences ?: emptyList()) {
            val gid = g.requestId
            // 🎯 Te acercas a un sitio: GPS fino unos minutos
            if (gid.startsWith(NEAR_ID)) { if (tr == Geofence.GEOFENCE_TRANSITION_ENTER) PreciseTracker.start(ctx, "near"); continue }
            val center: Triple<Double, Double, Float>? = when {
                gid == HOME_ID -> SiteStore.home(p)?.let { Triple(it.first, it.second, 150f) }
                gid.startsWith(NEW_ID) -> SiteStore.getNew(p)?.let { Triple(it.lat, it.lon, SiteStore.defaultRadius(p).toFloat()) }
                else -> SiteStore.find(p, gid.removePrefix(SITE_ID))?.let { Triple(it.lat, it.lon, SiteStore.radiusOf(p, it).toFloat()) }
            }
            if (center != null && !confirmed(ctx, tr, center.first, center.second, center.third, fix, trig,
                    if (gid == HOME_ID) L.t("Casa", "Home") else gid.removePrefix(SITE_ID).removePrefix(NEW_ID))) continue
            val id = gid
            if (id == HOME_ID) {
                when (tr) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> saveEnter(p, HOME_ID, at)
                    Geofence.GEOFENCE_TRANSITION_DWELL -> cameHome(ctx, vehOutNear(p, takeEnter(p, HOME_ID) ?: (at - 2 * 60_000L)))
                    Geofence.GEOFENCE_TRANSITION_EXIT -> { cancelHomePending(ctx); leftHome(ctx, at) }
                }
                continue
            }
            val isNew = id.startsWith(NEW_ID)
            val site = id.removePrefix(SITE_ID).removePrefix(NEW_ID)
            // El lugar nuevo de hoy (no guardado): solo para saber cuándo te vas
            if (isNew) {
                when (tr) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> { saveEnter(p, site, at); cancelPendingExit(ctx, site); falseExit(ctx, site, at) }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> { takeEnter(p, site); pendingExit(ctx, site, at) }
                }
                continue
            }
            // Tus sitios (y 🛻 el parqueo: al salir empieza el día, al volver y quedarte termina)
            when (tr) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> siteEnter(ctx, site, at)
                Geofence.GEOFENCE_TRANSITION_DWELL -> {
                    siteDwell(ctx, site, enteredAt(p, site) ?: (at - dwellFor(ctx, site)))
                    // 🧠 Aprende el Wi-Fi y dónde llegas
                    SiteLearn.learnWifi(ctx, site)
                    fix?.let { SiteLearn.learnPoint(ctx, site, it) }
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> siteExit(ctx, site, at)
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
            val t = WorkDay.addAt(p, WorkDay.ARRIVE, site, minOf(System.currentTimeMillis(), enterAt + SiteLearn.offsetMs(p, site, WorkDay.ARRIVE)))
            SiteLearn.markAuto(p, t)
            WorkdayCenter.refresh(ctx)
            Todos.arrivedAt(ctx, site)
            notify(ctx, L.t("📍 Llegaste a $site", "📍 You arrived at $site"),
                L.t("Lo anoté solo: llegaste a las ${WorkStore.time(t)}.", "Logged automatically: you arrived at ${WorkStore.time(t)}."),
                listOf(L.t("Deshacer", "Undo") to undoIntent(ctx, t), L.t("🕐 Cambiar hora", "🕐 Change time") to fixIntent(ctx, t)), NOTIF_ID)
        } else {
            // No has empezado tu jornada hoy: pregunta (puede que no estés trabajando)
            Todos.arrivedAt(ctx, site) // 📍 igual te recuerda lo de este sitio
            notify(ctx, L.t("📍 Estás en $site", "📍 You're at $site"),
                L.t("Desde las ${WorkStore.time(enterAt)}. ¿Estás trabajando?", "Since ${WorkStore.time(enterAt)}. Are you working?"),
                L.t("Marcar llegada", "Log arrival") to markIntent(ctx, enterAt, site))
        }
    }

    /** 🛻 Saliste del parqueo con la camioneta: empieza tu día (a la hora en que saliste). */
    private fun startFromParking(ctx: Context, site: String, at: Long) {
        val p = Prefs.get(ctx)
        if (WorkDay.day(p).isNotEmpty()) return // ya empezaste hoy
        val first = WorkDay.enabledKinds().first()
        val t = WorkDay.addAt(p, first, "", at)
        p.edit().putString(Prefs.PARKING_DAY, dayKey()).apply()
        WorkdayCenter.refresh(ctx)
        notify(ctx, L.t("🛻 Empezó tu día", "🛻 Your day started"),
            L.t("Saliste de $site a las ${WorkStore.time(t)}: anoté «${WorkDay.plainLabel(first)}».",
                "You left $site at ${WorkStore.time(t)}: logged «${WorkDay.plainLabel(first)}»."),
            listOf(L.t("Deshacer", "Undo") to undoIntent(ctx, t), L.t("🕐 Cambiar hora", "🕐 Change time") to fixIntent(ctx, t)), NOTIF_ID)
    }

    /**
     * 🏠 Saliste de tu casa sin haber empezado el día: te pregunta si te recogieron (puede ser que solo salgas a otra cosa).
     * Si dices que sí, el día empieza a la hora en que saliste.
     */
    private fun leftHome(ctx: Context, at: Long) {
        val p = Prefs.get(ctx)
        if (WorkDay.day(p).isNotEmpty()) return
        val first = WorkDay.enabledKinds().first()
        val yes = PendingIntent.getBroadcast(ctx, 5123, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_MARK)
            .putExtra(EXTRA_TIME, at).putExtra(EXTRA_SITE, "").putExtra(WorkdayCenter.EXTRA_KIND, first),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notify(ctx, L.t("🛻 ¿Empezó tu día?", "🛻 Did your day start?"),
            L.t("Saliste de tu casa a las ${WorkStore.time(at)}. Si ya vas en la camioneta, toca «Sí, empezó».",
                "You left home at ${WorkStore.time(at)}. If you're already in the truck, tap «Yes, it started»."),
            listOf(L.t("Sí, empezó a las ", "Yes, it started at ") + WorkStore.time(at) to yes), NOTIF_ID)
    }

    /** 🛻 Volviste al parqueo y dejaste la camioneta: termina tu día (a la hora en que llegaste). */
    private fun endAtParking(ctx: Context, site: String, at: Long) {
        val p = Prefs.get(ctx)
        val day = WorkDay.day(p)
        if (!WorkDay.isRunning(day)) return
        val last = WorkDay.lastMain(day) ?: return
        if (at <= last.time) return
        val fin = WorkDay.finalKind()
        val t = WorkDay.addAt(p, fin, "", at)
        WorkdayCenter.refresh(ctx)
        notify(ctx, L.t("🛻 Terminó tu día", "🛻 Your day ended"),
            L.t("Llegaste a $site a las ${WorkStore.time(t)}: anoté «${WorkDay.plainLabel(fin)}». Hoy: ${WorkDay.dur(WorkDay.summary(WorkDay.day(p)).total)}.",
                "You got to $site at ${WorkStore.time(t)}: logged «${WorkDay.plainLabel(fin)}». Today: ${WorkDay.dur(WorkDay.summary(WorkDay.day(p)).total)}."),
            listOf(L.t("Deshacer", "Undo") to undoIntent(ctx, t), L.t("🕐 Cambiar hora", "🕐 Change time") to fixIntent(ctx, t)), NOTIF_ID)
    }

    /** Llegaste a casa: si la jornada sigue abierta, se cierra con la hora en que llegaste. */
    /**
     * Llegaste a casa con el día abierto: todavía no se cierra. Se espera un rato (20 min) por si solo paraste
     * a buscar o dejar algo. Si te vas antes, no pasa nada.
     */
    private fun cameHome(ctx: Context, at: Long) {
        val p = Prefs.get(ctx)
        val day = WorkDay.day(p)
        if (!WorkDay.isRunning(day) || !WorkDay.isOn(WorkDay.HOME)) return
        val last = WorkDay.lastMain(day) ?: return
        if (at <= last.time) return
        p.edit().putLong(Prefs.HOME_PENDING, at).apply()
        val wait = ((p.getString(Prefs.HOME_WAIT, "20") ?: "20").toIntOrNull() ?: 20).coerceIn(1, 120)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager ?: return
        try { am.setWindow(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + wait * 60_000L, 60_000L, homeCheckIntent(ctx)) } catch (_: Exception) { }
    }

    private fun homeCheckIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 5124, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_HOME_CHECK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Te fuiste de la casa antes del rato de espera: era solo una parada. */
    private fun cancelHomePending(ctx: Context) {
        val p = Prefs.get(ctx)
        if (p.getLong(Prefs.HOME_PENDING, 0L) <= 0L) return
        p.edit().remove(Prefs.HOME_PENDING).apply()
        (ctx.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager)?.cancel(homeCheckIntent(ctx))
    }

    /** Te quedaste en casa: se cierra el día a la hora en que llegaste. Si empezaste en el parqueo, te pregunta. */
    private fun closeAtHome(ctx: Context, at: Long) {
        val p = Prefs.get(ctx)
        val day = WorkDay.day(p)
        if (!WorkDay.isRunning(day)) return
        val last = WorkDay.lastMain(day) ?: return
        if (at <= last.time) return
        if (p.getString(Prefs.PARKING_DAY, "") == dayKey()) {
            // Hoy recogiste la camioneta en el parqueo: normalmente terminas allá. Solo pregunta.
            val fin = WorkDay.finalKind()
            val yes = PendingIntent.getBroadcast(ctx, 5125, Intent(ctx, AutoSitesReceiver::class.java).setAction(ACTION_HOME_AT)
                .putExtra(EXTRA_TIME, at).putExtra(WorkdayCenter.EXTRA_KIND, fin), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notify(ctx, L.t("🏠 ¿Terminó tu día?", "🏠 Did your day end?"),
                L.t("Estás en casa desde las ${WorkStore.time(at)}. Hoy recogiste la camioneta en el parqueo: si ya terminaste aquí, toca «Sí».",
                    "You've been home since ${WorkStore.time(at)}. Today you picked up the truck at the parking lot: if you're done here, tap «Yes»."),
                listOf(L.t("Sí, terminó a las ", "Yes, it ended at ") + WorkStore.time(at) to yes), 4722)
            return
        }
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
    private fun pendingExit(ctx: Context, site: String, at0: Long) {
        val p = Prefs.get(ctx)
        // Si el GPS fino ya anotó una salida un poco antes, se queda esa hora (es la más exacta)
        val prev = try { JSONObject(p.getString(Prefs.AUTO_EXIT_PENDING, "") ?: "") } catch (_: Exception) { null }
        val at = if (prev != null && prev.optString("s") == site && prev.optLong("t") in (at0 - 30 * 60_000L)..at0) prev.optLong("t") else at0
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
        val t = WorkDay.addAt(p, WorkDay.LEAVE, last.site.ifEmpty { site }, maxOf(last.time + 60_000L, minOf(System.currentTimeMillis(), at + SiteLearn.offsetMs(p, last.site.ifEmpty { site }, WorkDay.LEAVE))))
        SiteLearn.markAuto(p, t)
        p.edit().putLong(Prefs.AUTO_LEAVE, t).apply()
        WorkdayCenter.refresh(ctx)
        notify(ctx, L.t("🚪 Saliste de ${last.site.ifEmpty { site }}", "🚪 You left ${last.site.ifEmpty { site }}"),
            L.t("Lo anoté solo: saliste a las ${WorkStore.time(t)}.", "Logged automatically: you left at ${WorkStore.time(t)}."),
            listOf(L.t("Deshacer", "Undo") to undoIntent(ctx, t), L.t("🕐 Cambiar hora", "🕐 Change time") to fixIntent(ctx, t)), NOTIF_ID)
    }

    private fun dayKey() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

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
        // Sin emojis en los avisos: el texto limpio (Android ya pone el ícono de la app)
        @Suppress("NAME_SHADOWING") val title = Emo.strip(title)
        @Suppress("NAME_SHADOWING") val text = Emo.strip(text)
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
            b.addAction(Notification.Action.Builder(null, Emo.stripUi(a.first), a.second).build())
        }
        try { nm.notify(id, b.build()) } catch (_: SecurityException) { }
    }
}

class AutoSitesReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        try { AutoSites.handle(ctx, intent, this) } catch (_: Throwable) { }
    }
}
