package com.luisell.bestkeyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.luisell.bestkeyboard.words.Dictionary
import com.luisell.bestkeyboard.words.GestureDecoder
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
    /** Cerebro de "deslizar para escribir" (se prepara justo después de los diccionarios). */
    @Volatile var gesture: GestureDecoder? = null
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
            // 🌍 Los diccionarios de tus 2 idiomas
            val (l1, l2) = Langs.pair(Prefs.get(app))
            val es = app.assets.open("dict_$l1.txt").use { Dictionary.load(l1, it) }
            val en = app.assets.open("dict_$l2.txt").use { Dictionary.load(l2, it) }
            val user = UserData()
            try { user.load(file) } catch (_: Exception) { }
            val e = WordEngine(listOf(es, en), user)
            // 🧹 Lo aprendido que parece error de dedo ("clmo" ~ "como"): ya no sale hasta que
            // lo escribas 4 veces (si es una palabra tuya de verdad, vuelve sola)
            try {
                for (x in user.words.values) {
                    if (x.sure || x.typo || x.count >= UserData.TYPO_KNOWN) continue
                    if (e.looksLikeTypo(x.display, "es")) { x.typo = true; x.legacy = false; user.dirty = true }
                }
            } catch (_: Exception) { }
            io.execute {
                // 📚 Frases de fábrica para adivinar la palabra siguiente
                try { val ng = com.luisell.bestkeyboard.words.NGrams(); app.assets.open("ngrams.txt").use { ng.load(it) }; e.base = ng } catch (_: Throwable) { }
                val g = try { GestureDecoder(listOf(es, en), user) } catch (_: Throwable) { null }
                g?.base = e.base
                g?.names = { e.names }
                main.post { gesture = g }
            }
            main.post {
                engine = e
                loading = false
                try { loadContacts(app) } catch (_: Exception) { }
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

    /** 📇 Lee los nombres de tus contactos (solo en tu teléfono) para sugerirlos bien escritos. */
    fun loadContacts(ctx: Context) {
        val app = ctx.applicationContext
        val e = engine ?: return
        val on = Prefs.get(app).getBoolean(Prefs.CONTACT_NAMES, true)
        val ok = app.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!on || !ok) { e.names = emptyMap(); return }
        io.execute {
            val map = HashMap<String, String>()
            try {
                app.contentResolver.query(android.provider.ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val n = c.getString(0) ?: continue
                        for (part in n.split(Regex("[^\\p{L}'’-]+"))) {
                            if (part.length < 2 || part.length > 24 || !part[0].isLetter()) continue
                            val disp = if (part[0].isLowerCase()) part.replaceFirstChar { it.uppercaseChar() } else part
                            map[part.lowercase()] = disp
                            if (map.size > 5000) break
                        }
                    }
                }
            } catch (_: Exception) { }
            main.post { e.names = map }
        }
    }

    /** Cambiaste de idiomas: se guardan tus palabras y se cargan los diccionarios nuevos. */
    fun reload(ctx: Context, onReady: (() -> Unit)? = null) {
        saveNow()
        main.post {
            engine = null
            gesture = null
            loading = false
            load(ctx, onReady)
        }
    }

    fun learnedCount(): Int = engine?.user?.words?.count { it.value.known } ?: 0
}
