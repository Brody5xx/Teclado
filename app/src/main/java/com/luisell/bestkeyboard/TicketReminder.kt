package com.luisell.bestkeyboard

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * Aviso diario del ticket (a las 7 PM si no lo cambias):
 * "✅ TICKET REGISTERED" si ya guardaste un ticket ese día, o "❌ NO TICKET" si todavía no.
 */
object TicketReminder {
    private const val CHANNEL = "ticket_reminder"
    private const val REQ = 4711

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, REQ, Intent(ctx, TicketReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Programa (o quita) el próximo aviso según los ajustes. */
    fun schedule(ctx: Context) {
        val p = Prefs.get(ctx)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        am.cancel(pending(ctx))
        if (!p.getBoolean(Prefs.REMINDER, false)) return
        val hour = (p.getString(Prefs.REMINDER_HOUR, "19") ?: "19").toIntOrNull()?.coerceIn(0, 23) ?: 19
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        try {
            // Sin permiso de alarma exacta: Android lo avisa dentro de una ventanita de pocos minutos
            am.setWindow(AlarmManager.RTC_WAKEUP, c.timeInMillis, 5 * 60 * 1000L, pending(ctx))
        } catch (_: Exception) { }
    }

    fun notify(ctx: Context) {
        val p = Prefs.get(ctx)
        val now = System.currentTimeMillis()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, L.t("Ticket del día", "Daily ticket"), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val done = WorkStore.hasTicketOn(p, now)
        val notes = WorkStore.notes(p).size
        val date = WorkStore.ticketDate(now)
        val title = if (done) "✅ TICKET REGISTERED" else "❌ NO TICKET"
        val text = when {
            done -> date
            notes > 0 -> "$date\n" + L.t("Tienes $notes apuntes en la libreta: abre el teclado, toca Libreta y «Crear ticket del día».",
                "You have $notes notes in the notebook: open the keyboard, tap Notebook and «Create the day's ticket».")
            else -> "$date\n" + L.t("Abre el teclado y toca Notas → Ticket para escribirlo.", "Open the keyboard and tap Notes → Ticket to write it.")
        }
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(ctx, CHANNEL) else android.app.Notification.Builder(ctx)
        b.setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(android.app.Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        try { nm.notify(REQ, b.build()) } catch (_: SecurityException) { }
    }
}

/** Suena la alarma del aviso (o se prendió el teléfono): avisa y programa el de mañana. */
class TicketReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        L.set(Prefs.get(ctx).getString(Prefs.LANG, "es") ?: "es")
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            // 📅 Los días que no trabajas no hay aviso del ticket
            val p = Prefs.get(ctx)
            if (WorkDay.isWorkDay(p) || WorkStore.hasTicketOn(p, System.currentTimeMillis())) TicketReminder.notify(ctx)
        }
        TicketReminder.schedule(ctx)
    }
}
