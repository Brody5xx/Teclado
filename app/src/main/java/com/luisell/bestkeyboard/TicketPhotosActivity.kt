package com.luisell.bestkeyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

/**
 * 📸 Enviar un ticket con fotos: eliges las fotos que tomaste ese día (antes y después del trabajo)
 * y se mandan juntas con el texto del ticket por WhatsApp, correo, etc.
 */
class TicketPhotosActivity : UiActivity() {

    private var tickets: List<WorkItem> = emptyList()
    private var idx = 0
    private lateinit var ticketText: TextView
    private lateinit var ticketTitle: TextView
    private lateinit var grid: GridLayout
    private lateinit var info: TextView
    private lateinit var sendBtn: TextView
    private val selected = LinkedHashSet<Uri>()
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        tickets = WorkStore.tickets(prefs)
        val sv = ScrollView(this)
        PanelUi.glowScrollbar(sv)
        sv.setBackgroundColor(BG)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(48))
        }
        col = root
        sv.addView(root)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val back = IconView(this, Icons.BACK, 9f)
        back.color = TEXT
        back.setOnClickListener { finish() }
        head.addView(back, LinearLayout.LayoutParams(dp(44), dp(44)))
        val title = TextView(this)
        title.text = tr("Ticket con fotos", "Ticket with photos")
        title.textSize = 24f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(TEXT)
        title.setPadding(dp(6), 0, 0, 0)
        title.post {
            title.paint.shader = LinearGradient(0f, 0f, title.width.toFloat(), 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            title.invalidate()
        }
        head.addView(title)
        root.addView(head)

        if (tickets.isEmpty()) {
            text(tr("Todavía no tienes tickets. Crea uno con 📋 Notas → Ticket en el teclado.",
                "You don't have tickets yet. Create one with 📋 Notes → Ticket on the keyboard."), 14f, faded = true)
            setContentView(sv)
            return
        }
        // ---- El ticket (‹ › para cambiar)
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        val prev = TextView(this).apply { text = "‹"; textSize = 26f; setTextColor(TEXT); gravity = Gravity.CENTER }
        val next = TextView(this).apply { text = "›"; textSize = 26f; setTextColor(TEXT); gravity = Gravity.CENTER }
        ticketTitle = TextView(this).apply { textSize = 16f; setTextColor(TEXT); gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD) }
        prev.setOnClickListener { if (idx < tickets.size - 1) { idx++; showTicket() } }
        next.setOnClickListener { if (idx > 0) { idx--; showTicket() } }
        nav.addView(prev, LinearLayout.LayoutParams(dp(44), dp(44)))
        nav.addView(ticketTitle, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(next, LinearLayout.LayoutParams(dp(44), dp(44)))
        root.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        ticketText = TextView(this).apply {
            textSize = 13f
            setTextColor(SUB)
            maxLines = 8
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply { setColor(CARD); cornerRadius = dpf(14f); setStroke(dp(1), LINE) }
        }
        root.addView(ticketText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })

        card(tr("Fotos de ese día", "Photos from that day"), Icons.IMAGE)
        info = text("", 12f, faded = true)
        grid = GridLayout(this).apply { columnCount = 3 }
        col.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        sendBtn = button("", primary = true) { send() }
        setContentView(sv)
        showTicket()
    }

    override fun onResume() {
        super.onResume()
        // Volviste de dar el permiso de fotos
        if (::grid.isInitialized && grid.childCount == 0 && Screenshots.hasPermission(this)) loadPhotos()
    }

    private fun showTicket() {
        val t = tickets[idx]
        ticketTitle.text = tr("Ticket ", "Ticket ") + t.num + "  ·  " + WorkStore.shortDate(t.time, en)
        ticketText.text = t.text
        selected.clear()
        loadPhotos()
    }

    private fun paintSend() {
        sendBtn.text = if (selected.isEmpty()) tr("📤 Enviar solo el ticket", "📤 Send just the ticket")
            else tr("📤 Enviar ticket + ${selected.size} foto(s)", "📤 Send ticket + ${selected.size} photo(s)")
    }

    private fun loadPhotos() {
        grid.removeAllViews()
        paintSend()
        if (!Screenshots.hasPermission(this)) {
            info.text = tr("Para ver tus fotos, permite «Fotos y videos».", "To see your photos, allow «Photos and videos».")
            button(tr("Permitir fotos", "Allow photos")) { requestPermissions(arrayOf(Screenshots.permission()), 51) }
            return
        }
        info.text = tr("Toca las fotos que quieres mandar (antes y después del trabajo).", "Tap the photos you want to send (before and after the job).")
        val day = tickets[idx].time
        val cell = (resources.displayMetrics.widthPixels - dp(36) - dp(12)) / 3
        val ctx: Context = this
        io.execute {
            val uris = photosOf(day)
            runOnUiThread {
                if (uris.isEmpty()) info.text = tr("No encontré fotos de ese día.", "No photos from that day.")
                for (u in uris) {
                    val f = FrameLayout(ctx)
                    val iv = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(CARD) }
                    f.addView(iv, FrameLayout.LayoutParams(-1, -1))
                    val mark = TextView(ctx).apply { textSize = 16f; gravity = Gravity.CENTER; setTextColor(0xFF0B1020.toInt()) }
                    f.addView(mark, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
                    fun paint() {
                        val on = u in selected
                        mark.text = if (on) "✓" else ""
                        mark.background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            if (on) setColor(CYAN) else { setColor(0x55000000); setStroke(dp(2), 0xFFFFFFFF.toInt()) }
                        }
                        f.alpha = if (on || selected.isEmpty()) 1f else 0.75f
                    }
                    paint()
                    f.setOnClickListener {
                        if (u in selected) selected.remove(u) else if (selected.size < 10) selected.add(u)
                        for (i in 0 until grid.childCount) (grid.getChildAt(i).tag as? () -> Unit)?.invoke()
                        paintSend()
                    }
                    f.tag = { paint() }
                    grid.addView(f, GridLayout.LayoutParams().apply { width = cell; height = cell; setMargins(dp(2), dp(2), dp(2), dp(2)) })
                    io.execute {
                        val b = thumb(u, cell)
                        if (b != null) runOnUiThread { iv.setImageBitmap(b) }
                    }
                }
            }
        }
    }

    /** Las fotos tomadas ese día (de la cámara y capturas), de la más nueva a la más vieja. */
    private fun photosOf(day: Long): List<Uri> {
        val c = Calendar.getInstance().apply { timeInMillis = day; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val from = c.timeInMillis
        val to = from + 24L * 3600 * 1000
        val out = ArrayList<Uri>()
        try {
            contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
                "(${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?) OR " +
                    "(${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.DATE_ADDED} < ?)",
                arrayOf(from.toString(), to.toString(), (from / 1000).toString(), (to / 1000).toString()),
                "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { cur ->
                while (cur.moveToNext() && out.size < 60) out.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cur.getLong(0)))
            }
        } catch (_: Exception) { }
        return out
    }

    private fun thumb(u: Uri, px: Int): Bitmap? = try {
        if (Build.VERSION.SDK_INT >= 29) contentResolver.loadThumbnail(u, Size(px, px), null)
        else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Thumbnails.getThumbnail(contentResolver, ContentUris.parseId(u), MediaStore.Images.Thumbnails.MINI_KIND, null)
        }
    } catch (_: Throwable) { null }

    private fun send() {
        val t = tickets[idx]
        // El texto queda copiado también (algunas apps no ponen el texto cuando mandas varias fotos)
        try {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Ticket", t.text))
        } catch (_: Exception) { }
        val i = if (selected.isEmpty()) {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t.text)
        } else if (selected.size == 1) {
            Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, selected.first()).putExtra(Intent.EXTRA_TEXT, t.text)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(selected))
                .putExtra(Intent.EXTRA_TEXT, t.text)
        }
        i.putExtra(Intent.EXTRA_SUBJECT, "Ticket " + t.num)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (selected.isNotEmpty()) toast("El texto del ticket también quedó copiado, por si la app no lo pone.", "The ticket text is also copied, in case the app doesn't add it.")
        try { startActivity(Intent.createChooser(i, tr("Enviar ticket", "Send ticket"))) } catch (_: Exception) { }
    }
}
