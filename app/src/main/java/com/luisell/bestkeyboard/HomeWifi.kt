package com.luisell.bestkeyboard

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * 🏠📶 El Wi-Fi de tu casa: cuando el teléfono se desconecta de él, eso marca casi exacto la hora en que saliste;
 * cuando se conecta, la hora en que llegaste. Mi jornada lo usa junto con el GPS y la camioneta.
 * Solo se guarda el identificador de la red (no la contraseña), y solo en tu teléfono.
 */
object HomeWifi {
    private const val KEY = "home_wifi"
    private var watching = false
    @Volatile private var current: String? = null

    @Suppress("DEPRECATION")
    private fun connectedBssid(ctx: Context): String? = try {
        if (!GpsCheck.precise(ctx)) null else {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            wm.connectionInfo?.bssid?.takeIf { it != "02:00:00:00:00:00" && it.isNotEmpty() }
        }
    } catch (_: Throwable) { null }

    private fun known(ctx: Context): Set<String> = (Prefs.get(ctx).getString(KEY, "") ?: "").split(",").filter { it.isNotEmpty() }.toSet()

    /** Estás en casa: guarda el Wi-Fi al que estás conectado (hasta 3, por si tienes repetidor). */
    fun learn(ctx: Context) {
        val b = connectedBssid(ctx) ?: return
        val set = known(ctx).toMutableList()
        if (b in set) return
        set.add(b)
        while (set.size > 3) set.removeAt(0)
        Prefs.get(ctx).edit().putString(KEY, set.joinToString(",")).apply()
    }

    fun hasHome(ctx: Context) = known(ctx).isNotEmpty()

    /** Vigila el Wi-Fi mientras el teclado está vivo (casi siempre, porque es tu teclado). */
    fun watch(ctx: Context) {
        if (watching) return
        val app = ctx.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        watching = true
        current = connectedBssid(app)
        try {
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val b = connectedBssid(app)
                    current = b
                    if (b != null && b in known(app)) Prefs.get(app).edit().putLong("home_wifi_on", System.currentTimeMillis()).apply()
                }
                override fun onLost(network: Network) {
                    val was = current
                    current = null
                    if (was != null && was in known(app)) Prefs.get(app).edit().putLong("home_wifi_off", System.currentTimeMillis()).apply()
                }
            })
        } catch (_: Throwable) { watching = false }
    }

    /** La hora en que se desconectó el Wi-Fi de tu casa, si fue un poco antes de [at] (al salir). */
    fun offNear(ctx: Context, at: Long): Long {
        val v = Prefs.get(ctx).getLong("home_wifi_off", 0L)
        return if (v > 0 && v <= at + 60_000L && v >= at - 20 * 60_000L) v else at
    }

    /** La hora en que se conectó el Wi-Fi de tu casa, si fue cerca de [at] (al llegar). */
    fun onNear(ctx: Context, at: Long): Long {
        val v = Prefs.get(ctx).getLong("home_wifi_on", 0L)
        return if (v > 0 && v >= at - 5 * 60_000L && v <= at + 20 * 60_000L) v else at
    }
}
