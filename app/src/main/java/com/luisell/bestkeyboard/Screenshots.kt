package com.luisell.bestkeyboard

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size

/** Busca si tomaste una captura de pantalla hace poco (para sugerirla arriba del teclado). */
object Screenshots {
    /** Cuánto tiempo se sugiere una captura después de tomarla. */
    private const val MAX_AGE_SEC = 5 * 60L

    class Shot(val id: Long, val uri: Uri, val mime: String)

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES"
        else android.Manifest.permission.READ_EXTERNAL_STORAGE

    /** Permiso completo de fotos (con "solo algunas fotos" no se ven las capturas nuevas). */
    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** Android 14: dio "solo algunas fotos" (hay que cambiarlo a "Permitir todo"). */
    fun hasPartial(ctx: Context): Boolean = Build.VERSION.SDK_INT >= 34 && !hasPermission(ctx) &&
        ctx.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED

    /** La captura más nueva de los últimos minutos, o null. (Llamar en segundo plano) */
    fun findRecent(ctx: Context): Shot? {
        if (!hasPermission(ctx)) return null
        val since = System.currentTimeMillis() / 1000 - MAX_AGE_SEC
        val pathCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        val proj = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE, pathCol, MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        return try {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Images.Media.DATE_ADDED} >= ? OR ${MediaStore.Images.Media.DATE_MODIFIED} >= ?",
                arrayOf(since.toString(), since.toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < 20) {
                    n++
                    val name = c.getString(1) ?: ""
                    val path = c.getString(3) ?: ""
                    val bucket = c.getString(4) ?: ""
                    val all = "$name $path $bucket"
                    val isShot = all.contains("screenshot", true) || all.contains("captura", true) ||
                        all.contains("screen_shot", true) || all.contains("screencap", true)
                    if (!isShot) continue
                    val id = c.getLong(0)
                    val mime = c.getString(2) ?: "image/png"
                    return@use Shot(id, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), mime)
                }
                null
            }
        } catch (_: Exception) { null }
    }

    /** Miniatura pequeña para el botón. (Llamar en segundo plano) */
    fun thumbnail(ctx: Context, shot: Shot, px: Int): Bitmap? = try {
        val t = if (Build.VERSION.SDK_INT >= 29) {
            try { ctx.contentResolver.loadThumbnail(shot.uri, Size(px, px), null) } catch (_: Throwable) { null }
        } else null
        t ?: run {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(shot.uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
            ctx.contentResolver.openInputStream(shot.uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            }
        }
    } catch (_: Throwable) { null }
}
