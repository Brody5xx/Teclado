package com.luisell.bestkeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * 🎯 Revisa lo que hace que el GPS de Mi jornada sea preciso (permisos, precisión de Google,
 * búsqueda de Wi-Fi y batería) y lleva directo al ajuste que falta.
 */
object GpsCheck {

    class Check(val ok: Boolean, val es: String, val en: String, val fixEs: String, val fixEn: String, val fix: ((Activity) -> Unit)?)

    fun precise(ctx: Context) = ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun battery(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (_: Throwable) { true }

    @Suppress("DEPRECATION")
    fun wifiScan(ctx: Context): Boolean = try {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isScanAlwaysAvailable
    } catch (_: Throwable) { true }

    private fun openAppSettings(a: Activity) = try {
        a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.packageName)))
    } catch (_: Exception) { }

    private fun openScanning(a: Activity) {
        try { a.startActivity(Intent("android.settings.LOCATION_SCANNING_SETTINGS")) }
        catch (_: Exception) { try { a.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: Exception) { } }
    }

    @android.annotation.SuppressLint("BatteryLife")
    private fun openBattery(a: Activity) {
        // La ventanita de Android: «¿Permitir que Best Keyboard funcione siempre en segundo plano?»
        val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + a.packageName))
        if (ask.resolveActivity(a.packageManager) != null) try { a.startActivity(ask); return } catch (_: Exception) { }
        // Si el teléfono no la tiene: la página de la app (Batería › Sin restricciones)
        openAppSettings(a)
    }

    /** Lo que se puede revisar al instante. [google] = si la precisión de Google está prendida (null = revisando). */
    fun checks(ctx: Context, google: Boolean?, googleFix: ((Activity) -> Unit)?): List<Check> = listOf(
        Check(precise(ctx), "Ubicación precisa", "Precise location",
            "Toca para permitir la ubicación precisa.", "Tap to allow precise location.") { a ->
            a.requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), 31)
        },
        Check(AutoSites.hasBackground(ctx), "Ubicación «Permitir todo el tiempo»", "Location «Allow all the time»",
            "Toca, entra en Permisos › Ubicación y elige «Permitir todo el tiempo».", "Tap, go to Permissions › Location and choose «Allow all the time».") { a ->
            if (Build.VERSION.SDK_INT >= 29 && precise(a)) a.requestPermissions(arrayOf("android.permission.ACCESS_BACKGROUND_LOCATION"), 32) else openAppSettings(a)
        },
        Check(google ?: true, if (google == null) "Precisión de la ubicación de Google (revisando…)" else "Precisión de la ubicación de Google",
            if (google == null) "Google Location Accuracy (checking…)" else "Google Location Accuracy",
            "Toca y acepta «Activar» para usar Wi-Fi, redes y sensores además del GPS.", "Tap and accept «Turn on» to use Wi-Fi, networks and sensors besides GPS.", googleFix),
        Check(wifiScan(ctx), "Búsqueda de Wi-Fi", "Wi-Fi scanning",
            "Toca y prende «Búsqueda de Wi-Fi» (y «Búsqueda de Bluetooth»). Funciona aunque tengas el Wi-Fi apagado.",
            "Tap and turn on «Wi-Fi scanning» (and «Bluetooth scanning»). It works even with Wi-Fi off.") { a -> openScanning(a) },
        Check(battery(ctx), "Batería sin restricciones", "Unrestricted battery",
            "Toca y elige «Permitir» (o «Sin restricciones»), para que los avisos no lleguen tarde.",
            "Tap and choose «Allow» (or «Unrestricted»), so the alerts don't arrive late.") { a -> openBattery(a) },
    )

    /** ¿Está prendida la precisión de Google? Si no, [onFix] abre la ventanita de Google para prenderla. */
    fun checkGoogle(a: Activity, done: (Boolean, ((Activity) -> Unit)?) -> Unit) {
        try {
            val req = com.google.android.gms.location.LocationRequest.Builder(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
            val s = com.google.android.gms.location.LocationSettingsRequest.Builder().addLocationRequest(req).setNeedBle(true).setAlwaysShow(true).build()
            com.google.android.gms.location.LocationServices.getSettingsClient(a).checkLocationSettings(s)
                .addOnSuccessListener { done(true, null) }
                .addOnFailureListener { e ->
                    val r = e as? com.google.android.gms.common.api.ResolvableApiException
                    done(false) { act ->
                        try { r?.startResolutionForResult(act, 77) ?: act.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                        catch (_: Exception) { try { act.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } catch (_: Exception) { } }
                    }
                }
        } catch (_: Throwable) { done(true, null) }
    }

    // ---------------------------------------------------------------- Lecturas falsas que se ignoraron

    private const val KEY = "gps_ignored"

    /** Anota que el GPS «brincó» y no se anotó una llegada o salida falsa. */
    fun logIgnored(ctx: Context, exit: Boolean, place: String) {
        val p = Prefs.get(ctx)
        val n = p.getInt("$KEY.n", 0) + 1
        p.edit().putInt("$KEY.n", n).putLong("$KEY.t", System.currentTimeMillis())
            .putString("$KEY.what", (if (exit) "exit" else "enter") + "|" + place).apply()
    }

    /** «Se ignoraron 3 lecturas falsas. La última: una salida falsa de Sitio A, hoy 2:15 p. m.» */
    fun ignoredText(ctx: Context): String? {
        val p = Prefs.get(ctx)
        val n = p.getInt("$KEY.n", 0)
        if (n == 0) return null
        val t = p.getLong("$KEY.t", 0L)
        val (kind, place) = (p.getString("$KEY.what", "exit|") ?: "exit|").split("|", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val f = java.text.SimpleDateFormat("d MMM, h:mm a", if (L.english) java.util.Locale.US else java.util.Locale("es"))
        val what = if (kind == "exit") L.t("una salida falsa", "a false departure") else L.t("una llegada falsa", "a false arrival")
        return L.t("Lecturas falsas del GPS que no se anotaron: $n. La última: $what", "False GPS readings that weren't logged: $n. The last one: $what") +
            (if (place.isNotEmpty()) " ($place)" else "") + ", " + f.format(java.util.Date(t)) + "."
    }
}
