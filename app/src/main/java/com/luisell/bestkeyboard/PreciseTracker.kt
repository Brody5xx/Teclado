package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * 🎯 GPS fino solo cerca de tus sitios: cuando entras al círculo grande (1 km) de un sitio, cuando te bajas
 * del vehículo o cuando te subes estando en un sitio, prende el GPS de alta precisión cada pocos segundos
 * para saber el minuto exacto en que entras o sales. Cuando ya sabe, se apaga solo (máximo 15 minutos).
 */
class PreciseTracker : Service() {

    companion object {
        private const val CHANNEL = "gps_precise"
        private const val NOTIF = 4730
        private const val MAX_MS = 15 * 60_000L
        @Volatile var running = false

        fun enabled(ctx: Context) = Prefs.get(ctx).getBoolean("gps_fine_near", true)

        /** Prende el GPS fino un rato. [why] = "near", "vehicle_out" o "vehicle_in". */
        fun start(ctx: Context, why: String) {
            if (!enabled(ctx) || !AutoSites.ready(ctx)) return
            try {
                val i = Intent(ctx, PreciseTracker::class.java).putExtra("why", why)
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (_: Throwable) { }
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var deadline = 0L
    private var startedAt = 0L
    private var why = ""
    private var cb: LocationCallback? = null

    /** Lo que se sabe de cada sitio mientras corre. */
    private class St {
        var inside: Boolean? = null
        var insideSince = 0L
        var lastIn = 0L
        var lastOut = 0L
        var outCount = 0
        var firstOut = 0L
        var confirmed = false
        var confirmedAt = 0L
        var exited = false
        var best: Location? = null
    }
    private val st = HashMap<String, St>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        foreground()
        val w = intent?.getStringExtra("why") ?: "near"
        val now = System.currentTimeMillis()
        if (!running) {
            running = true
            startedAt = now
            why = w
            deadline = now + MAX_MS
            st.clear()
            begin()
        } else {
            // Ya estaba corriendo: le da un poco más de tiempo (máximo 25 min en total)
            deadline = minOf(maxOf(deadline, now + 8 * 60_000L), startedAt + 25 * 60_000L)
            if (w.startsWith("vehicle")) why = w
        }
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, maxOf(1000L, deadline - now))
        return START_NOT_STICKY
    }

    private val timeout = Runnable { stopNow() }

    private fun foreground() {
        L.useScreen(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, L.t("Mi jornada: GPS fino", "My workday: precise GPS"), NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val n = b.setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(L.t("Mi jornada", "My workday"))
            .setContentText(L.t("Estás cerca de un sitio: ubicando con precisión unos minutos.", "You're near a site: locating precisely for a few minutes."))
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIF, n)
        } catch (_: Throwable) { stopSelf() }
    }

    @SuppressLint("MissingPermission")
    private fun begin() {
        val client = try { LocationServices.getFusedLocationProviderClient(this) } catch (_: Throwable) { stopNow(); return }
        val c = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) { for (l in r.locations) onFix(l) }
        }
        cb = c
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).setMinUpdateIntervalMillis(2000L).build()
            client.requestLocationUpdates(req, c, Looper.getMainLooper())
        } catch (_: Throwable) { stopNow() }
        SiteLearn.requestScan(this)
    }

    private var lastScan = 0L
    private var lastTrail = 0L

    private fun onFix(l: Location) {
        val p = Prefs.get(this)
        if (!WorkDay.isWorkDay(p) && WorkDay.day(p).isEmpty()) { stopNow(); return }
        val now = System.currentTimeMillis()
        if (now - lastScan > 30_000L) { lastScan = now; SiteLearn.requestScan(this) }
        val t = l.time.takeIf { it in (now - 60_000L)..(now + 5_000L) } ?: now
        if (now - lastTrail > 20_000L) { lastTrail = now; Trail.add(this, l) }
        val acc = minOf(l.accuracy, 40f)
        val vehOut = p.getLong("veh_out", 0L)
        var anyNear = false
        for (s in SiteStore.all(p)) {
            if (s.lat == 0.0 && s.lon == 0.0) continue
            val r = Geo.fenceRadius(p, s)
            val d = FloatArray(1)
            Location.distanceBetween(s.lat, s.lon, l.latitude, l.longitude, d)
            if (d[0] < r + 1300f) anyNear = true else continue
            // El Wi-Fi solo ayuda cuando el GPS está malo (adentro de edificios); con GPS bueno, manda el GPS
            val wifi = if (d[0] < r + 300f && l.accuracy > 35f) SiteLearn.wifiMatch(this, s.name, 60) else null
            // Una lectura mala no cuenta (salvo que el Wi-Fi diga que estás adentro)
            if (l.accuracy > 60f && wifi != true) continue
            // 🔷 Con la forma del sitio (si la dibujaste) o con el círculo
            val w = Geo.where(p, s, l.latitude, l.longitude, acc)
            val inside = wifi == true || w == true
            val outside = wifi != true && w == false
            val x = st.getOrPut(s.name) { St() }
            val prev = x.inside
            if (prev == null) {
                // La primera lectura: solo dice dónde estás, no anota nada
                if (inside) { x.inside = true; x.insideSince = AutoSites.enteredAt(p, s.name) ?: t; x.lastIn = t }
                else if (outside) { x.inside = false; x.lastOut = t }
                continue
            }
            if (inside) {
                if (prev == false) {
                    // Entraste: entre la última lectura de afuera y esta
                    val at = if (x.lastOut > 0 && t - x.lastOut < 60_000L) (x.lastOut + t) / 2 else t
                    x.inside = true; x.insideSince = at; x.confirmed = false; x.exited = false
                    AutoSites.siteEnter(this, s.name, at)
                }
                x.outCount = 0
                x.lastIn = t
                if (l.accuracy <= 30f && (x.best == null || l.accuracy < x.best!!.accuracy)) x.best = l
                // Te quedaste: se anota la llegada (si te bajaste del vehículo aquí, basta 1 minuto)
                val parked = vehOut > 0 && vehOut >= x.insideSince - 60_000L
                val need = if (parked) 60_000L else AutoSites.dwellFor(this, s.name).toLong()
                if (!x.confirmed && t - x.insideSince >= need) {
                    x.confirmed = true; x.confirmedAt = now
                    AutoSites.siteDwell(this, s.name, x.insideSince)
                    x.best?.let { SiteLearn.learnPoint(this, s.name, it) }
                    SiteLearn.learnWifi(this, s.name)
                }
            } else if (outside) {
                if (prev == true) {
                    x.outCount++
                    if (x.outCount == 1) x.firstOut = if (x.lastIn > 0 && t - x.lastIn < 60_000L) (x.lastIn + t) / 2 else t
                    // Dos lecturas seguidas afuera: saliste (a la hora de la primera)
                    if (x.outCount >= 2) {
                        x.inside = false; x.exited = true; x.confirmed = false
                        AutoSites.siteExit(this, s.name, x.firstOut)
                    }
                }
                x.lastOut = t
            }
        }
        // ¿Ya sabe todo? Se apaga para no gastar batería
        val runFor = now - startedAt
        val done = st.values.any { (it.confirmed && now - it.confirmedAt > 60_000L) || (it.exited && it.lastOut - it.firstOut > 90_000L) } &&
            st.values.none { it.inside == true && !it.confirmed }
        if ((done && runFor > 90_000L) || (!anyNear && runFor > 3 * 60_000L)) stopNow()
    }

    private fun stopNow() {
        running = false
        main.removeCallbacks(timeout)
        cb?.let { c -> try { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(c) } catch (_: Throwable) { } }
        cb = null
        try { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true) } catch (_: Throwable) { }
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacks(timeout)
        cb?.let { c -> try { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(c) } catch (_: Throwable) { } }
        super.onDestroy()
    }
}
