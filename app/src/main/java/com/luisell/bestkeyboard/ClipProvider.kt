package com.luisell.bestkeyboard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Deja que otras apps (WhatsApp, Gmail...) lean SOLO la imagen que tú pegas desde el teclado.
 * No es pública: Android da permiso temporal únicamente para esa imagen.
 */
class ClipProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    /** La foto del escáner: la app de la cámara la escribe aquí (archivo temporal). */
    private fun scanFile(uri: Uri): File? {
        if (uri.lastPathSegment != SCAN_PHOTO) return null
        val ctx = context ?: return null
        return File(ctx.cacheDir, SCAN_PHOTO)
    }

    private fun fileFor(uri: Uri): File {
        scanFile(uri)?.let { if (it.exists()) return it else throw FileNotFoundException() }
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        if (!name.matches(Regex("[A-Za-z0-9._-]+")) || name.startsWith(".")) throw FileNotFoundException()
        val ctx = context ?: throw FileNotFoundException()
        val f = File(ClipStore.clipsDir(ctx), name)
        if (!f.exists() || name.endsWith(".json") || name.endsWith(".tmp")) throw FileNotFoundException()
        return f
    }

    override fun getType(uri: Uri): String {
        val n = uri.lastPathSegment ?: ""
        return when {
            n.endsWith(".jpg") -> "image/jpeg"
            n.endsWith(".gif") -> "image/gif"
            n.endsWith(".webp") -> "image/webp"
            else -> "image/png"
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        // Solo la foto del escáner se puede escribir; todo lo demás es solo lectura
        val scan = scanFile(uri)
        if (scan != null) return ParcelFileDescriptor.open(scan, ParcelFileDescriptor.parseMode(mode))
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        val f = scanFile(uri) ?: fileFor(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val c = MatrixCursor(cols, 1)
        c.addRow(cols.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        })
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    companion object {
        const val SCAN_PHOTO = "scan_photo.jpg"
    }
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
