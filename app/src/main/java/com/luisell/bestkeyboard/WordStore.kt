package com.luisell.bestkeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.luisell.bestkeyboard.words.Dictionary
import com.luisell.bestkeyboard.words.UserData
import com.luisell.bestkeyboard.words.WordEngine
import java.io.File
import java.util.concurrent.Executors

/**
 * Carga los diccionarios en segundo plano (para que el teclado abra al instante)
 * y guarda lo aprendido en el teléfono, sin internet.
 */
object WordStore {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile var engine: WordEngine? = null
        private set
    private var loading = false
    private var userFile: File? = null
    private val listeners = ArrayList<() -> Unit>()

    /** Empieza a cargar (si no está cargado). [onReady] se llama en el hilo principal. */
    fun load(ctx: Context, onReady: (() -> Unit)? = null) {
        if (engine != null) { onReady?.invoke(); return }
        if (onReady != null) listeners.add(onReady)
        if (loading) return
        loading = true
        val app = ctx.applicationContext
        val file = File(app.filesDir, "aprendido.txt")
        userFile = file
        io.execute {
            val es = app.assets.open("dict_es.txt").use { Dictionary.load("es", it) }
            val en = app.assets.open("dict_en.txt").use { Dictionary.load("en", it) }
            val user = UserData()
            try { user.load(file) } catch (_: Exception) { }
            val e = WordEngine(listOf(es, en), user)
            main.post {
                engine = e
                loading = false
                val l = ArrayList(listeners)
                listeners.clear()
                l.forEach { it() }
            }
        }
    }

    private val saveRunnable = Runnable { saveNow() }

    /** Guarda unos segundos después del último cambio (para no gastar batería). */
    fun scheduleSave() {
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, 8000)
    }

    fun saveNow() {
        main.removeCallbacks(saveRunnable)
        val e = engine ?: return
        val f = userFile ?: return
        if (!e.user.dirty) return
        e.user.dirty = false
        val text = e.user.snapshot()
        io.execute {
            try {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(text, Charsets.UTF_8)
                tmp.renameTo(f)
            } catch (_: Exception) { }
        }
    }

    /** Borra todo lo aprendido. */
    fun clearLearned(ctx: Context) {
        engine?.user?.clear()
        val f = File(ctx.applicationContext.filesDir, "aprendido.txt")
        io.execute { f.delete() }
        engine?.user?.dirty = false
    }

    fun learnedCount(): Int = engine?.user?.words?.count { it.value.count >= UserData.KNOWN } ?: 0
}
