package com.luisell.bestkeyboard

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda cualquier error que cierre la app o el teclado, para poder copiarlo y arreglarlo rápido.
 * Lo que se guarda: la versión, el teléfono, la hora y qué parte del código falló (nada de lo que escribes).
 */
class BestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try { CrashLog.save(this, e) } catch (_: Throwable) { }
            previous?.uncaughtException(thread, e)
        }
    }
}

object CrashLog {
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "ultimo-error.txt")

    fun save(ctx: Context, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Exception) { "?" }
        val text = "Best Keyboard $version\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()) + "\n\n" +
            sw.toString().take(12000)
        file(ctx).writeText(text)
    }

    fun last(ctx: Context): String? = file(ctx).takeIf { it.exists() }?.readText()
    fun clear(ctx: Context) { file(ctx).delete() }
}
