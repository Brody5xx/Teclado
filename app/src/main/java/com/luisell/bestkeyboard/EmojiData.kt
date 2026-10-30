package com.luisell.bestkeyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Un emoji con sus tonos de piel (si tiene). */
class Emoji(val text: String, val tones: List<String>, val name: String)

/**
 * Carga la lista de emojis en segundo plano y guarda los recientes.
 * Solo muestra los emojis que tu teléfono sabe dibujar (los muy nuevos se esconden).
 */
object EmojiData {
    /** Íconos de las pestañas: 0 = recientes, luego las 9 categorías. */
    val TAB_ICONS = listOf("🕘", "😀", "👋", "🐶", "🍔", "🚗", "⚽", "💡", "❤️", "🏳️")
    const val CATEGORIES = 9
    private const val MAX_RECENT = 40

    @Volatile var categories: List<List<Emoji>>? = null
        private set
    private var loading = false
    private val listeners = ArrayList<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()

    private var prefs: SharedPreferences? = null
    val recent = ArrayList<String>()

    fun load(ctx: Context, onReady: (() -> Unit)? = null) {
        if (categories != null) { onReady?.invoke(); return }
        if (onReady != null) listeners.add(onReady)
        if (loading) return
        loading = true
        val app = ctx.applicationContext
        val p = Prefs.get(app)
        prefs = p
        recent.clear()
        p.getString("emoji_recent", null)?.split('\u0001')?.filter { it.isNotEmpty() }?.let { recent.addAll(it) }
        io.execute {
            val paint = Paint()
            val cats = List(CATEGORIES) { ArrayList<Emoji>() }
            try {
                app.assets.open("emoji.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        val parts = line.split('\t')
                        if (parts.size < 2) continue
                        val cat = parts[0].toIntOrNull() ?: continue
                        if (cat !in 0 until CATEGORIES) continue
                        val e = parts[1]
                        if (!paint.hasGlyph(e)) continue
                        val tones = if (parts.size > 2 && parts[2].isNotEmpty())
                            parts[2].split(' ').filter { it.isNotEmpty() && paint.hasGlyph(it) } else emptyList()
                        cats[cat].add(Emoji(e, tones, if (parts.size > 3) parts[3] else ""))
                    }
                }
            } catch (_: Exception) { }
            main.post {
                categories = cats
                loading = false
                val l = ArrayList(listeners)
                listeners.clear()
                l.forEach { it() }
            }
        }
    }

    /** Busca un emoji por su texto (para saber si tiene tonos de piel en Recientes). */
    fun find(text: String): Emoji? {
        val cats = categories ?: return null
        for (c in cats) for (e in c) if (e.text == text || e.tones.contains(text)) return e
        return null
    }

    fun addRecent(e: String) {
        recent.remove(e)
        recent.add(0, e)
        while (recent.size > MAX_RECENT) recent.removeAt(recent.size - 1)
        prefs?.edit()?.putString("emoji_recent", recent.joinToString("\u0001"))?.apply()
    }
}
