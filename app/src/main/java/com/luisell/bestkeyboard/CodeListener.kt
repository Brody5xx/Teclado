package com.luisell.bestkeyboard

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 📧 Códigos que llegan por correo (Gmail, Outlook, Samsung Email, Yahoo, Proton…) o en el aviso de
 * cualquier app: cuando sale el aviso, busca el código («Your verification code is 482913») y lo deja
 * listo arriba del teclado, igual que los de SMS. Solo se guarda el número (no el correo) y vence a los 10 minutos.
 * Todo pasa dentro del teléfono.
 */
class CodeListener : NotificationListenerService() {

    companion object {
        private const val KEY = "mail_code"
        const val AGE_MS = 10 * 60 * 1000L

        /** Las apps de correo conocidas (de estas se lee cualquier aviso con código). */
        private val MAIL = setOf(
            "com.google.android.gm", "com.google.android.gm.lite", "com.microsoft.office.outlook",
            "com.samsung.android.email.provider", "com.yahoo.mobile.client.android.mail", "ch.protonmail.android",
            "me.proton.android.mail", "com.aol.mobile.aolapp", "com.readdle.spark", "com.fsck.k9", "net.thunderbird.android",
            "com.easilydo.mail", "com.my.mail", "ru.mail.mailapp", "com.zoho.mail", "com.tutanota.tutanota",
            "de.tutao.tutanota", "com.apple.android.icloud", "com.blue.mail", "park.outlook.sign.in.client",
        )

        fun allowed(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, CodeListener::class.java).flattenToString()
            return flat.split(':').any { it == me || it.startsWith(ctx.packageName + "/") }
        }

        fun openSettings(ctx: Context) {
            val i = android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(i) } catch (_: Exception) { }
        }

        /** El código del correo más nuevo (si es de los últimos 10 minutos). */
        fun recent(ctx: Context): QuickPaste.Item? {
            val raw = Prefs.get(ctx).getString(KEY, "") ?: ""
            val parts = raw.split('|')
            if (parts.size < 3) return null
            val time = parts[1].toLongOrNull() ?: return null
            if (System.currentTimeMillis() - time > AGE_MS) return null
            val code = parts[0]
            val from = parts.getOrNull(2).orEmpty()
            val label = L.t("Código", "Code") + (if (from.isNotEmpty()) " ($from)" else "") + ": " + code
            return QuickPaste.Item(code, label, true, "mail:$code:$time", time)
        }

        private fun appName(ctx: Context, pkg: String): String = try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { "" }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        try {
            val pkg = n.packageName
            if (pkg == packageName) return
            val p = Prefs.get(this)
            if (!p.getBoolean(Prefs.OTP_SUGGEST, true) || !p.getBoolean(Prefs.MAIL_CODES, true)) return
            // De las apps de SMS ya se encarga el lector de SMS
            if (pkg.contains("messaging") || pkg.contains(".mms") || pkg.contains("sms")) return
            val mail = pkg in MAIL || pkg.contains("mail")
            // De otras apps solo si el aviso habla claramente de un código
            val x = n.notification.extras ?: return
            val parts = listOfNotNull(
                x.getCharSequence(Notification.EXTRA_TITLE), x.getCharSequence(Notification.EXTRA_SUB_TEXT),
                x.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: x.getCharSequence(Notification.EXTRA_TEXT),
            ).map { it.toString() } + (x.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() } ?: emptyList())
            val body = parts.joinToString("\n")
            if (body.isBlank()) return
            val code = QuickPaste.findCode(body) ?: return
            if (!mail && !Regex("(?i)(verification|verificaci|one[- ]time|c[oó]digo de|security code|login code|sign[- ]in code|passcode|\\bOTP\\b)").containsMatchIn(body)) return
            val now = System.currentTimeMillis()
            val prev = (p.getString(KEY, "") ?: "").split('|')
            if (prev.firstOrNull() == code && now - (prev.getOrNull(1)?.toLongOrNull() ?: 0L) < AGE_MS) return
            p.edit().putString(KEY, code + "|" + now + "|" + appName(this, pkg).replace("|", "")).apply()
            // Si el teclado está abierto, que salga ya
            BestKeyboardService.instance?.let { s -> android.os.Handler(mainLooper).post { s.codeArrived() } }
        } catch (_: Throwable) { }
    }
}
