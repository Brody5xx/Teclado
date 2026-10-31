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
    private const val MAX_AGE_SEC = 3 * 60L

    class Shot(val id: Long, val uri: Uri, val mime: String)

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) "android.permission.READ_MEDIA_IMAGES"
        else android.Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(ctx: Context): Boolean {
        if (ctx.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED) return true
        // Android 14: "permitir solo algunas fotos" también sirve (si eligió la captura)
        return Build.VERSION.SDK_INT >= 34 &&
            ctx.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED
    }

    /** La captura más nueva de los últimos minutos, o null. (Llamar en segundo plano) */
    fun findRecent(ctx: Context): Shot? {
        if (!hasPermission(ctx)) return null
        val since = System.currentTimeMillis() / 1000 - MAX_AGE_SEC
        val pathCol = if (Build.VERSION.SDK_INT >= 29) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        val proj = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE, pathCol,
        )
        return try {
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Images.Media.DATE_ADDED} >= ?", arrayOf(since.toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                var n = 0
                while (c.moveToNext() && n < 20) {
                    n++
                    val name = c.getString(1) ?: ""
                    val path = c.getString(3) ?: ""
                    val isShot = path.contains("screenshot", true) || name.contains("screenshot", true) ||
                        path.contains("captura", true) || name.contains("captura", true)
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
        if (Build.VERSION.SDK_INT >= 29) {
            ctx.contentResolver.loadThumbnail(shot.uri, Size(px, px), null)
        } else {
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
