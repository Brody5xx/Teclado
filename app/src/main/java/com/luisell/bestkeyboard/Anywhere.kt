package com.luisell.bestkeyboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * ⌨️ «Teclado en todas partes»: una notificación fija que, al tocarla, abre Best Keyboard en
 * cualquier app (también en terminales o cuando el teléfono cree que hay un teclado de PC conectado).
 * Se prende y se apaga con su botón en la barra de arriba; apagado, la notificación desaparece.
 */
object Anywhere {
    const val ACTION_SHOW = "com.luisell.bestkeyboard.ANYWHERE_SHOW"
    const val ACTION_OFF = "com.luisell.bestkeyboard.ANYWHERE_OFF"
    private const val CHANNEL = "anywhere"
    private const val NOTIF_ID = 4740

    fun isOn(ctx: Context) = Prefs.get(ctx).getBoolean(Prefs.KB_ANYWHERE, false)

    /** Hasta cuándo el teclado sigue intentando salir (después de tocar la notificación). */
    @Volatile var showUntil = 0L

    /** Pide que salga el teclado: lo intenta varias veces mientras la cortina se cierra y la app vuelve. */
    fun requestShow(ctx: Context) {
        showUntil = android.os.SystemClock.uptimeMillis() + 4000L
        val svc = BestKeyboardService.instance
        if (svc != null) svc.forceShowSoon()
        else try {
            (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker()
        } catch (_: Exception) { }
    }

    fun set(ctx: Context, on: Boolean) {
        Prefs.get(ctx).edit().putBoolean(Prefs.KB_ANYWHERE, on).apply()
        refresh(ctx)
    }

    /** Pone (o quita) la notificación según esté prendido o no. */
    fun refresh(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (!isOn(ctx)) { nm.cancel(NOTIF_ID); return }
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, L.t("Teclado en todas partes", "Keyboard anywhere"), NotificationManager.IMPORTANCE_LOW)
            ch.setShowBadge(false)
            ch.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            nm.createNotificationChannel(ch)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // Una pantallita invisible: al tocar, Android cierra solo la cortina y vuelves a tu app con el teclado
        val show = PendingIntent.getActivity(ctx, 740, Intent(ctx, AnywhereActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS), flags)
        val off = PendingIntent.getBroadcast(ctx, 741, Intent(ctx, AnywhereReceiver::class.java).setAction(ACTION_OFF), flags)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL) else Notification.Builder(ctx)
        b.setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(L.t("Teclado en todas partes", "Keyboard anywhere"))
            .setContentText(L.t("Toca aquí para abrir Best Keyboard en la app donde estás.", "Tap here to open Best Keyboard in the app you're in."))
            .setContentIntent(show)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
        @Suppress("DEPRECATION")
        b.addAction(Notification.Action.Builder(null, L.t("Abrir teclado", "Open keyboard"), show).build())
        @Suppress("DEPRECATION")
        b.addAction(Notification.Action.Builder(null, L.t("Apagar", "Turn off"), off).build())
        try { nm.notify(NOTIF_ID, b.build()) } catch (_: SecurityException) { }
    }
}

/** Los botones de la notificación. */
class AnywhereReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        L.set(Prefs.get(ctx).getString(Prefs.LANG, "es") ?: "es")
        when (intent.action) {
            Anywhere.ACTION_OFF -> Anywhere.set(ctx, false)
            Anywhere.ACTION_SHOW -> Anywhere.requestShow(ctx)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Anywhere.refresh(ctx)
        }
    }
}


/** Invisible: se abre y se cierra al instante; la cortina se va sola y el teclado sale en tu app. */
class AnywhereActivity : android.app.Activity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        Anywhere.requestShow(this)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
