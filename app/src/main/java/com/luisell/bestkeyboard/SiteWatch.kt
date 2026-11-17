package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.app.Notification
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
 * 🎯 Precisión máxima en la salida: mientras estás en un sitio, vigila tu ubicación cada minuto con poca batería
 * (Wi-Fi y antenas). Apenas una lectura te ve afuera (o te subes a la camioneta), prende el GPS fino cada
 * 3 segundos unos minutos. Cuando está seguro (varias lecturas seguidas afuera y lejos), anota la salida a la hora
 * de la última vez que te vio adentro (o cuando te subiste a la camioneta), sin esperar.
 * Usa el mismo aviso fijo de «Mi jornada» (no sale otro aviso).
 */
class SiteWatch : Service() {

    companion object {
        @Volatile var running = false

        fun enabled(ctx: Context) = Prefs.get(ctx).getBoolean("site_watch", true)

        /** ¿Dónde estás ahora según Mi jornada? (el sitio de la última llegada, si el día sigue y no saliste) */
        fun current(ctx: Context): SiteStore.Site? {
            val p = Prefs.get(ctx)
            val day = WorkDay.day(p)
            if (!WorkDay.isRunning(day)) return null
            val last = WorkDay.lastMain(day) ?: return null
            if (last.kind != WorkDay.ARRIVE || last.site.isEmpty()) return null
            SiteStore.find(p, last.site)?.takeIf { !(it.lat == 0.0 && it.lon == 0.0) }?.let { return it }
            // Un lugar nuevo (día de servicio)
            val nw = SiteStore.getNew(p) ?: return null
            return if (nw.short == last.site) SiteStore.Site(nw.short, nw.full, nw.lat, nw.lon, radius = 80) else null
        }

        /** Prende o apaga el vigilante según dónde estés. */
        fun sync(ctx: Context) {
            val want = enabled(ctx) && AutoSites.ready(ctx) && current(ctx) != null
            if (want == running) return
            val i = Intent(ctx, SiteWatch::class.java)
            try {
                if (want) { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
                else ctx.stopService(i)
            } catch (_: Throwable) { }
        }

        /** Te subiste a la camioneta: el vigilante pasa a GPS fino ya. */
        fun burstNow() { instance?.burst() }
        @Volatile private var instance: SiteWatch? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private var cb: LocationCallback? = null
    private var fine = false
    private var lastInside = 0L
    private var firstOut = 0L
    private var outCount = 0
    private var siteName = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foreground()) { stopSelf(); return START_NOT_STICKY }
        val s = current(this)
        if (s == null || !enabled(this)) { stopSelf(); return START_NOT_STICKY }
        instance = this
        if (!running || s.name != siteName) {
            running = true
            siteName = s.name
            lastInside = System.currentTimeMillis(); firstOut = 0L; outCount = 0
            request(false)
        }
        return START_STICKY
    }

    private fun foreground(): Boolean {
        val n = WorkdayCenter.lastNotif ?: run {
            // Todavía no hay aviso fijo: uno sencillito (se reemplaza con el de Mi jornada enseguida)
            if (Build.VERSION.SDK_INT >= 26) (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .createNotificationChannel(android.app.NotificationChannel("workday", L.t("Mi jornada", "My workday"), android.app.NotificationManager.IMPORTANCE_LOW))
            @Suppress("DEPRECATION")
            (if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "workday") else Notification.Builder(this))
                .setSmallIcon(WorkDay.iconRes()).setContentTitle(L.t("Mi jornada", "My workday")).setOngoing(true).build()
        }
        return try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(WorkdayCenter.NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(WorkdayCenter.NOTIF_ID, n)
            true
        } catch (_: Throwable) { false }
    }

    /** [highAcc] = GPS fino cada 3 s; si no, cada minuto con poca batería. */
    @SuppressLint("MissingPermission")
    private fun request(highAcc: Boolean) {
        val client = try { LocationServices.getFusedLocationProviderClient(this) } catch (_: Throwable) { return }
        cb?.let { try { client.removeLocationUpdates(it) } catch (_: Throwable) { } }
        fine = highAcc
        val c = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) { for (l in r.locations) onFix(l) }
        }
        cb = c
        val req = if (highAcc) LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L).setMinUpdateIntervalMillis(2000L).build()
            else LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L).setMinUpdateIntervalMillis(30_000L).build()
        try { client.requestLocationUpdates(req, c, Looper.getMainLooper()) } catch (_: Throwable) { }
        if (highAcc) {
            // El GPS fino dura 6 minutos; si no pasó nada, vuelve a lo de poca batería
            main.postDelayed({ if (fine && outCount == 0) request(false) }, 6 * 60_000L)
        }
    }

    fun burst() { if (running && !fine) request(true) }

    private fun onFix(l: Location) {
        val p = Prefs.get(this)
        val s = current(this)
        if (s == null || s.name != siteName) { stopSelf(); return }
        val now = System.currentTimeMillis()
        val t = l.time.takeIf { it in (now - 120_000L)..(now + 5_000L) } ?: now
        if (fine) Trail.add(this, l)
        val weak = SiteLearn.weak(p, s.name)
        // Con GPS malo (adentro), las redes Wi-Fi del sitio dicen si sigues ahí
        val wifi = if (l.accuracy > 35f) SiteLearn.wifiMatch(this, s.name, 90) else null
        val w = if (wifi == true) true else Geo.where(p, s, l.latitude, l.longitude, l.accuracy)
        val d = FloatArray(1)
        Location.distanceBetween(s.lat, s.lon, l.latitude, l.longitude, d)
        val far = d[0] > Geo.fenceRadius(p, s) + (if (weak) 200f else 60f)
        when {
            w == true -> { lastInside = t; outCount = 0; firstOut = 0L }
            w == false && l.accuracy <= 60f -> {
                outCount++
                if (outCount == 1) { firstOut = t; if (!fine) request(true) } // ¡te mueves! GPS fino ya
                val vin = p.getLong("veh_in", 0L)
                val inTruck = vin > lastInside - 5 * 60_000L && vin > p.getLong("veh_out", 0L)
                val need = if (weak) 4 else 3
                if ((outCount >= need && far) || (inTruck && outCount >= 2 && d[0] > Geo.fenceRadius(p, s) + 100f)) {
                    // Seguro: saliste. La hora: cuando te subiste a la camioneta, o entre la última vez adentro y la primera afuera
                    val at = if (inTruck && vin in (lastInside - 2 * 60_000L)..(firstOut + 60_000L)) vin
                        else if (firstOut > 0 && lastInside > 0) (lastInside + firstOut) / 2 else t
                    AutoSites.watchLeft(this, s.name, at)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        running = false
        instance = null
        main.removeCallbacksAndMessages(null)
        cb?.let { c -> try { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(c) } catch (_: Throwable) { } }
        try { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH) } catch (_: Throwable) { }
        // El aviso de Mi jornada se queda (solo se va el vigilante)
        try { WorkdayCenter.refresh(applicationContext) } catch (_: Throwable) { }
        super.onDestroy()
    }
}
