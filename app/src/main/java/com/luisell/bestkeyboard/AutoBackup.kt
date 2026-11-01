package com.luisell.bestkeyboard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Copia de seguridad automática cada semana, en la carpeta que elegiste (por ejemplo, una de Google Drive),
 * con la misma contraseña de siempre. Guarda las 4 más recientes.
 */
object AutoBackup {
    private const val WEEK = 7L * 24 * 3600 * 1000

    fun enabled(ctx: Context): Boolean {
        val p = Prefs.get(ctx)
        return !p.getString(Prefs.AUTO_BACKUP_DIR, "").isNullOrEmpty() && !p.getString(Prefs.AUTO_BACKUP_PW, "").isNullOrEmpty()
    }

    fun turnOn(ctx: Context, tree: Uri, password: String) {
        try {
            ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) { }
        Prefs.get(ctx).edit()
            .putString(Prefs.AUTO_BACKUP_DIR, tree.toString())
            .putString(Prefs.AUTO_BACKUP_PW, VaultStore.seal(password))
            .apply()
        runNow(ctx)
        schedule(ctx)
    }

    fun turnOff(ctx: Context) {
        Prefs.get(ctx).edit().remove(Prefs.AUTO_BACKUP_DIR).remove(Prefs.AUTO_BACKUP_PW).apply()
        schedule(ctx)
    }

    /** Hace la copia ahora. Devuelve true si se guardó. */
    fun runNow(ctx: Context): Boolean {
        val p = Prefs.get(ctx)
        val dir = p.getString(Prefs.AUTO_BACKUP_DIR, "") ?: ""
        val pw = VaultStore.unseal(p.getString(Prefs.AUTO_BACKUP_PW, "") ?: "") ?: return false
        if (dir.isEmpty()) return false
        return try {
            val tree = Uri.parse(dir)
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = "best-keyboard-auto-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".bkb"
            val doc = DocumentsContract.createDocument(ctx.contentResolver, parent, "application/octet-stream", name) ?: return false
            val bytes = Backup.export(ctx, pw)
            ctx.contentResolver.openOutputStream(doc, "wt")?.use { it.write(bytes) } ?: return false
            p.edit().putLong(Prefs.AUTO_BACKUP_LAST, System.currentTimeMillis()).apply()
            cleanOld(ctx, tree)
            true
        } catch (_: Exception) { false }
    }

    /** Deja solo las 4 copias automáticas más nuevas. */
    private fun cleanOld(ctx: Context, tree: Uri) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val found = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(1) ?: continue
                if (n.startsWith("best-keyboard-auto-")) found.add(c.getString(0) to n)
            }
        }
        for ((id, _) in found.sortedByDescending { it.second }.drop(4)) {
            try { DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) } catch (_: Exception) { }
        }
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(ctx, 4714, Intent(ctx, AutoBackupReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
        if (!enabled(ctx)) return
        val last = Prefs.get(ctx).getLong(Prefs.AUTO_BACKUP_LAST, 0L)
        val at = maxOf(System.currentTimeMillis() + 60_000L, last + WEEK)
        try { am.setWindow(AlarmManager.RTC, at, 6 * 3600 * 1000L, pi) } catch (_: Exception) { }
    }
}

class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
                    val last = Prefs.get(ctx).getLong(Prefs.AUTO_BACKUP_LAST, 0L)
                    if (System.currentTimeMillis() - last > 6L * 24 * 3600 * 1000) AutoBackup.runNow(ctx)
                }
                AutoBackup.schedule(ctx)
            } finally { pending.finish() }
        }.start()
    }
}
