package com.luisell.bestkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * GIFs y stickers (de GIPHY). Necesita una llave gratis de GIPHY que se pone en Ajustes.
 * Tocas uno y se pega en la conversación (si la app acepta GIFs desde el teclado).
 */
@SuppressLint("ViewConstructor")
class GifPanel(context: Context) : FrameLayout(context) {

    interface Actions {
        /** El GIF ya está descargado en [file] (dentro de la carpeta de imágenes del teclado). */
        fun onGifPick(file: File)
        fun onGifBack()
        fun onGifSettings()
        fun onGifSearch()
        fun onGifFeedback()
    }

    class Gif(val preview: String, val full: String, val id: String)

    var actions: Actions? = null
    private var palette = Palette.DARK
    private fun d(v: Float) = PanelUi.dp(context, v)
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(4)
    private var seq = 0

    private val back = IconView(context, Icons.BACK, 8f)
    private val gifTab = TextView(context)
    private val stickerTab = TextView(context)
    private val cats = LinearLayout(context)
    private val grid = GridLayout(context)
    private val status = TextView(context)
    private val scroll = ScrollView(context)
    private var stickers = false
    private var query = ""
    private val catViews = ArrayList<TextView>()
    private var searchBtn: IconView? = null
    private val thumbs = ArrayList<ImageView>()

    /** Lo que buscas al tocar cada botón (en inglés, GIPHY encuentra más). */
    private val CATS = listOf(
        Triple("Tendencias", "Trending", ""),
        Triple("Jaja", "LOL", "lol"),
        Triple("Gracias", "Thanks", "thank you"),
        Triple("Sí", "Yes", "yes"),
        Triple("No", "No", "no"),
        Triple("Wow", "Wow", "wow"),
        Triple("Amor", "Love", "love"),
        Triple("Triste", "Sad", "sad"),
        Triple("Enojado", "Angry", "angry"),
        Triple("Bailar", "Dance", "dance"),
        Triple("Buenos días", "Good morning", "good morning"),
        Triple("Buenas noches", "Good night", "good night"),
        Triple("Felicidades", "Congrats", "congratulations"),
        Triple("Cumpleaños", "Birthday", "happy birthday"),
        Triple("Trabajo", "Work", "working"),
        Triple("Cansado", "Tired", "tired"),
        Triple("OK", "OK", "ok"),
    )
    private var catIndex = 0

    init {
        val body = LinearLayout(context)
        body.orientation = LinearLayout.VERTICAL
        body.setPadding(d(6f), d(4f), d(6f), d(4f))

        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        back.setOnClickListener { actions?.onGifFeedback(); actions?.onGifBack() }
        head.addView(back, LinearLayout.LayoutParams(d(44f), d(36f)))
        for ((t, isSticker) in listOf(gifTab to false, stickerTab to true)) {
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setTypeface(t.typeface, Typeface.BOLD)
            t.setPadding(d(16f), 0, d(16f), 0)
            t.setOnClickListener {
                actions?.onGifFeedback()
                if (stickers != isSticker) { stickers = isSticker; paintTabs(); load() }
            }
            head.addView(t, LinearLayout.LayoutParams(-2, d(34f)).apply { leftMargin = d(8f) })
        }
        // Buscar GIFs (abre la barra de búsqueda con el teclado)
        val searchBtn = IconView(context, Icons.SEARCH, 7f)
        searchBtn.setOnClickListener { actions?.onGifFeedback(); actions?.onGifSearch() }
        this.searchBtn = searchBtn
        head.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        head.addView(searchBtn, LinearLayout.LayoutParams(d(52f), d(34f)))
        L.bind(gifTab, "GIFs", "GIFs")
        L.bind(stickerTab, "Stickers", "Stickers")
        body.addView(head, LinearLayout.LayoutParams(-1, d(42f)))

        cats.orientation = LinearLayout.HORIZONTAL
        val hs = HorizontalScrollView(context)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(cats)
        for ((i, c) in CATS.withIndex()) {
            val t = TextView(context)
            t.text = L.t(c.first, c.second)
            t.textSize = 13f
            t.gravity = Gravity.CENTER
            t.setPadding(d(12f), 0, d(12f), 0)
            t.setOnClickListener {
                actions?.onGifFeedback()
                catIndex = i
                query = c.third
                paintCats()
                load()
            }
            catViews.add(t)
            cats.addView(t, LinearLayout.LayoutParams(-2, d(30f)).apply { rightMargin = d(6f) })
        }
        body.addView(hs, LinearLayout.LayoutParams(-1, d(36f)).apply { topMargin = d(2f) })

        val stack = FrameLayout(context)
        grid.columnCount = 3
        PanelUi.glowScrollbar(scroll)
        scroll.addView(grid, LayoutParams(-1, -2))
        stack.addView(scroll, LayoutParams(-1, -1))
        status.gravity = Gravity.CENTER
        status.textSize = 14f
        status.setPadding(d(20f), d(10f), d(20f), d(10f))
        status.setOnClickListener {
            if (key().isEmpty()) { actions?.onGifFeedback(); actions?.onGifSettings() }
        }
        stack.addView(status, LayoutParams(-1, -1))
        body.addView(stack, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = d(4f) })
        addView(body, LayoutParams(-1, -1))
        applyPalette(palette)
    }

    private fun key() = Prefs.get(context).getString(Prefs.GIPHY_KEY, "")?.trim() ?: ""

    fun open(search: String? = null) {
        for ((i, t) in catViews.withIndex()) t.text = L.t(CATS[i].first, CATS[i].second)
        if (search != null) {
            // Lo que buscaste en la barra: ninguna categoría encendida
            query = search
            catIndex = -1
        } else if (catIndex < 0) {
            catIndex = 0
            query = ""
        }
        paintTabs()
        paintCats()
        load()
    }

    fun close() {
        seq++
        grid.removeAllViews()
        thumbs.clear()
    }

    private fun showStatus(msg: String?) {
        status.text = msg ?: ""
        status.visibility = if (msg == null) View.GONE else View.VISIBLE
    }

    private fun load() {
        val my = ++seq
        grid.removeAllViews()
        thumbs.clear()
        val k = key()
        if (k.isEmpty()) {
            showStatus(L.t(
                "Para ver GIFs necesitas una llave gratis de GIPHY.\n\nToca aquí y sigue los pasos en Ajustes → GIFs.",
                "To see GIFs you need a free GIPHY key.\n\nTap here and follow the steps in Settings → GIFs."))
            return
        }
        showStatus(L.t("Cargando…", "Loading…"))
        val type = if (stickers) "stickers" else "gifs"
        val lang = if (L.english) "en" else "es"
        val url = if (query.isEmpty())
            "https://api.giphy.com/v1/$type/trending?api_key=${enc(k)}&limit=30&rating=pg-13"
        else
            "https://api.giphy.com/v1/$type/search?api_key=${enc(k)}&q=${enc(query)}&limit=30&rating=pg-13&lang=$lang"
        io.execute {
            val result = try { parse(get(url)) } catch (e: Exception) { e }
            main.post {
                if (my != seq) return@post
                when (result) {
                    is Exception -> showStatus(errorText(result))
                    is List<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val list = result as List<Gif>
                        if (list.isEmpty()) showStatus(L.t("No encontré nada.", "Nothing found.")) else { showStatus(null); fill(list, my) }
                    }
                }
            }
        }
    }

    private fun errorText(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m.contains("401") || m.contains("403") -> L.t("La llave de GIPHY no sirve. Revísala en Ajustes → GIFs.", "The GIPHY key doesn't work. Check it in Settings → GIFs.")
            m.contains("429") -> L.t("Se usaron muchas búsquedas esta hora. Intenta en un rato.", "Too many searches this hour. Try again in a while.")
            else -> L.t("No hay internet o GIPHY no respondió.", "No internet or GIPHY didn't respond.")
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        try {
            val code = c.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return c.inputStream.use { it.readBytes() }
        } finally { c.disconnect() }
    }

    private fun parse(bytes: ByteArray): List<Gif> {
        val data = JSONObject(String(bytes)).getJSONArray("data")
        val out = ArrayList<Gif>()
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val imgs = o.optJSONObject("images") ?: continue
            val prev = imgs.optJSONObject("fixed_width_small")?.optString("url")
                ?: imgs.optJSONObject("fixed_width")?.optString("url") ?: continue
            val full = imgs.optJSONObject("fixed_width")?.optString("url")
                ?: imgs.optJSONObject("downsized")?.optString("url") ?: prev
            if (prev.isEmpty()) continue
            out.add(Gif(prev, full, o.optString("id", i.toString())))
        }
        return out
    }

    private fun fill(list: List<Gif>, my: Int) {
        val cell = (width - d(12f)) / 3 - d(6f)
        for (g in list) {
            val iv = ImageView(context)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.background = PanelUi.rounded(palette.key, d(10f).toFloat())
            iv.clipToOutline = true
            iv.setOnClickListener { actions?.onGifFeedback(); pick(g) }
            val lp = GridLayout.LayoutParams()
            lp.width = cell.coerceAtLeast(d(60f))
            lp.height = cell.coerceAtLeast(d(60f))
            lp.setMargins(d(3f), d(3f), d(3f), d(3f))
            grid.addView(iv, lp)
            thumbs.add(iv)
            io.execute {
                val bytes = try { get(g.preview) } catch (_: Exception) { null } ?: return@execute
                val drawable = decode(bytes)
                main.post {
                    if (my != seq || drawable == null) return@post
                    iv.setImageDrawable(drawable)
                    if (Build.VERSION.SDK_INT >= 28 && drawable is android.graphics.drawable.AnimatedImageDrawable) drawable.start()
                }
            }
        }
    }

    /** GIF animado en Android 9+; en teléfonos más viejos, solo el primer cuadro. */
    private fun decode(bytes: ByteArray): Drawable? = try {
        if (Build.VERSION.SDK_INT >= 28) {
            android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))
        } else {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
        }
    } catch (_: Exception) { null }

    private fun pick(g: Gif) {
        showStatus(L.t("Enviando…", "Sending…"))
        val dir = ClipStore.clipsDir(context)
        io.execute {
            val file = try {
                // Solo guardamos los últimos GIFs enviados (para no llenar el teléfono)
                dir.listFiles { f -> f.name.startsWith("gif_") }?.sortedBy { it.lastModified() }?.dropLast(10)?.forEach { it.delete() }
                val bytes = get(g.full)
                val f = File(dir, "gif_" + g.id.replace(Regex("[^A-Za-z0-9]"), "") + ".gif")
                f.writeBytes(bytes)
                f
            } catch (_: Exception) { null }
            main.post {
                if (file == null) showStatus(L.t("No se pudo descargar el GIF.", "Couldn't download the GIF."))
                else { showStatus(null); actions?.onGifPick(file) }
            }
        }
    }

    private fun paintTabs() {
        for ((t, isSticker) in listOf(gifTab to false, stickerTab to true)) {
            val on = stickers == isSticker
            t.setTextColor(if (on) palette.onAccent else palette.text)
            t.background = PanelUi.pressable(if (on) palette.accent else palette.special, if (on) palette.accentPressed else palette.keyPressed, d(17f).toFloat())
        }
    }

    private fun paintCats() {
        for ((i, t) in catViews.withIndex()) {
            val on = i == catIndex
            t.setTextColor(if (on) palette.onAccent else palette.text)
            t.background = PanelUi.pressable(if (on) palette.accent else palette.key, if (on) palette.accentPressed else palette.keyPressed, d(15f).toFloat())
        }
    }

    fun applyPalette(p: Palette) {
        palette = p
        setBackgroundColor(p.bg)
        back.color = p.text
        back.background = PanelUi.pressable(p.special, p.keyPressed, d(18f).toFloat())
        searchBtn?.let { it.color = p.text; it.background = PanelUi.pressable(p.special, p.keyPressed, d(17f).toFloat()) }
        status.setTextColor(p.hint)
        paintTabs()
        paintCats()
        for (t in thumbs) t.background = PanelUi.rounded(p.key, d(10f).toFloat())
    }
}
