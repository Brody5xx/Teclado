package com.luisell.bestkeyboard

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import java.io.File

/**
 * 🖼 Invisible: abre la galería para elegir una foto, la copia (para poder leerla sin permisos)
 * y se la pasa al teclado, que vuelve a salir con la imagen lista para tocar el texto.
 */
class PickImageActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val i = if (Build.VERSION.SDK_INT >= 33) Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*")
            else Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try { startActivityForResult(i, 1) } catch (_: Exception) {
            try { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 1) } catch (_: Exception) { done(null) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = if (resultCode == RESULT_OK) data?.data else null
        if (uri == null) { done(null); return }
        Thread {
            val f = try {
                val out = File(cacheDir, "picked_image")
                contentResolver.openInputStream(uri)?.use { inp -> out.outputStream().use { inp.copyTo(it) } }
                out
            } catch (_: Exception) { null }
            runOnUiThread { done(f) }
        }.start()
    }

    private fun done(f: File?) {
        BestKeyboardService.pickedImage = f?.let { Uri.fromFile(it) }
        BestKeyboardService.pickWaiting = true
        // Que el teclado vuelva a salir en la app donde estabas
        Anywhere.requestShow(this)
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
