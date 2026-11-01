package com.luisell.bestkeyboard

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/** Guía rápida: un recorrido paso a paso por todo lo que tiene el teclado. */
class GuideActivity : UiActivity() {

    private class Step(val icon: Int, val title: String, val body: String)

    private lateinit var steps: List<Step>
    private var index = 0
    private lateinit var icon: IconView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var page: LinearLayout
    private lateinit var dots: LinearLayout
    private lateinit var next: TextView
    private lateinit var prev: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        steps = listOf(
            Step(Icons.SETTINGS, tr("¡Bienvenido!", "Welcome!"), tr(
                "Este es un recorrido rápido por Best Keyboard. Desliza a los lados o toca «Siguiente».",
                "This is a quick tour of Best Keyboard. Swipe sideways or tap «Next».")),
            Step(Icons.MORE, tr("La barra de arriba", "The top bar"), tr(
                "Desliza la barra a los lados para ver todos los botones. Mantén presionado uno y arrástralo para acomodarlo. El ⋯ abre «Más opciones».",
                "Swipe the bar sideways to see all buttons. Press and hold one and drag it to rearrange. The ⋯ opens «More options».")),
            Step(Icons.EMOJI, tr("Emojis, GIFs y stickers", "Emojis, GIFs and stickers"), tr(
                "Toca 😀: arriba puedes buscar emojis y el botón GIF abre los GIFs y stickers. Mientras escribes, el teclado te sugiere emojis (casa → 🏠).",
                "Tap 😀: at the top you can search emojis and the GIF button opens GIFs and stickers. While you type, it suggests emojis (house → 🏠).")),
            Step(Icons.TICKET, tr("Notas → Ticket", "Notes → Ticket"), tr(
                "Escribe rápido con tus palabras y toca 📋: la IA lo vuelve un ticket formal con tu formato del trabajo. También puedes 🎤 dictar el reporte hablando.",
                "Type quickly in your own words and tap 📋: the AI turns it into a formal ticket in your work format. You can also 🎤 dictate the report.")),
            Step(Icons.NOTEBOOK, tr("Libreta y Mis tickets", "Notebook and My tickets"), tr(
                "📒 Apunta cosas durante el día y al final «Crear ticket del día». 🗂️ Mis tickets guarda cada ticket con su número.",
                "📒 Note things during the day and at the end «Create the day's ticket». 🗂️ My tickets saves each ticket with its number.")),
            Step(Icons.CLOCK, tr("Mi jornada", "My workday"), tr(
                "🛻 Comencé (en la camioneta), 📍 Llegué al sitio, 🚪 Salí del sitio y 🏠 Llegué a casa. Calcula tus horas y las pone en el ticket.",
                "🛻 Started (in the truck), 📍 Arrived on site, 🚪 Left the site and 🏠 Home. It adds up your hours and puts them in the ticket.")),
            Step(Icons.FORMAL, tr("Escribir más formal", "Write more formally"), tr(
                "Las ✨ sugerencias cambian palabras sencillas por formales (arreglé → reparé). ✅ «Revisar antes de enviar» revisa todo el mensaje.",
                "✨ suggestions swap simple words for formal ones (fixed → repaired). ✅ «Review before sending» checks the whole message.")),
            Step(Icons.BARCODE, tr("Escanear", "Scan"), tr(
                "Con una sola foto, el escáner lee el texto y también los códigos de barras y QR: números de serie y MAC sin escribirlos.",
                "With one photo, the scanner reads the text and also barcodes and QR codes: serial numbers and MACs without typing.")),
            Step(Icons.TRANSLATE, tr("Traducir", "Translate"), tr(
                "Escribe en español y sale en inglés, traduce un mensaje que te llegó, o en el micrófono toca «Escribir en inglés» para dictar en español y que salga en inglés.",
                "Type in Spanish and get English, translate a message you got, or in the mic tap «Type in English» to speak Spanish and get English.")),
            Step(Icons.FLOAT, tr("Flotante y una mano", "Floating and one hand"), tr(
                "El teclado puede ser una ventanita que mueves por la pantalla, o hacerse angosto para usarlo con una mano.",
                "The keyboard can be a small window you move around, or get narrower to use with one hand.")),
            Step(Icons.LOCK, tr("Datos y respaldo", "Data and backup"), tr(
                "🔒 Datos guarda correos y contraseñas con tu huella. En Ajustes haces una copia de seguridad con contraseña.",
                "🔒 Data keeps emails and passwords behind your fingerprint. In Settings you can make a password-protected backup.")),
            Step(Icons.SEARCH, tr("¡Listo!", "All set!"), tr(
                "En Ajustes usa el 🔎 buscador para encontrar cualquier opción. Puedes volver a ver esta guía cuando quieras.",
                "In Settings use the 🔎 search to find any option. You can see this guide again anytime.")),
        )

        val screen = FrameLayout(this)
        screen.setBackgroundColor(BG)
        val skip = TextView(this)
        skip.text = tr("Saltar", "Skip")
        skip.textSize = 15f
        skip.setTextColor(SUB)
        skip.setPadding(dp(20), dp(16), dp(20), dp(16))
        skip.setOnClickListener { done() }
        screen.addView(skip, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))

        page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.gravity = Gravity.CENTER_HORIZONTAL
        page.setPadding(dp(32), 0, dp(32), 0)
        val circle = FrameLayout(this)
        circle.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(CYAN, VIOLET)).apply { shape = GradientDrawable.OVAL }
        icon = IconView(this, Icons.SETTINGS, 26f)
        icon.isClickable = false
        icon.color = 0xFFFFFFFF.toInt()
        icon.bgColor = CYAN
        circle.addView(icon, FrameLayout.LayoutParams(-1, -1))
        page.addView(circle, LinearLayout.LayoutParams(dp(120), dp(120)))
        title = TextView(this)
        title.textSize = 26f
        title.gravity = Gravity.CENTER
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(TEXT)
        page.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        body = TextView(this)
        body.textSize = 17f
        body.gravity = Gravity.CENTER
        body.setLineSpacing(dpf(4f), 1f)
        body.setTextColor(SUB)
        page.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        screen.addView(page, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.VERTICAL
        bottom.gravity = Gravity.CENTER_HORIZONTAL
        bottom.setPadding(dp(24), 0, dp(24), dp(28))
        dots = LinearLayout(this)
        dots.orientation = LinearLayout.HORIZONTAL
        bottom.addView(dots, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(20) })
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        prev = TextView(this)
        next = TextView(this)
        for (b in listOf(prev, next)) {
            b.textSize = 16f
            b.gravity = Gravity.CENTER
            b.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        }
        prev.setTextColor(TEXT)
        prev.background = GradientDrawable().apply { cornerRadius = dpf(26f); setStroke(dp(1), LINE) }
        next.setTextColor(0xFF0B1020.toInt())
        next.background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(CYAN, VIOLET)).apply { cornerRadius = dpf(26f) }
        prev.setOnClickListener { go(index - 1) }
        next.setOnClickListener { if (index == steps.lastIndex) done() else go(index + 1) }
        row.addView(prev, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(10) })
        row.addView(next, LinearLayout.LayoutParams(0, dp(52), 1f))
        bottom.addView(row, LinearLayout.LayoutParams(-1, -2))
        screen.addView(bottom, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        // Deslizar a los lados para cambiar de paso
        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) < abs(vy) || abs(vx) < 400) return false
                if (vx < 0) { if (index < steps.lastIndex) go(index + 1) } else go(index - 1)
                return true
            }
        })
        screen.setOnTouchListener { _, e -> gd.onTouchEvent(e) }
        setContentView(screen)
        show(0, 0)
    }

    private fun go(i: Int) {
        if (i < 0 || i > steps.lastIndex || i == index) return
        val dir = if (i > index) 1 else -1
        // Sale hacia un lado y entra el siguiente desde el otro
        page.animate().translationX(-dir * dpf(60f)).alpha(0f).setDuration(160).withEndAction {
            show(i, dir)
        }.start()
    }

    private fun show(i: Int, dir: Int) {
        index = i
        val s = steps[i]
        icon.kind = s.icon
        icon.invalidate()
        title.text = s.title
        body.text = s.body
        prev.text = tr("Anterior", "Back")
        prev.visibility = if (i == 0) View.INVISIBLE else View.VISIBLE
        next.text = if (i == steps.lastIndex) tr("¡Empezar!", "Let's go!") else tr("Siguiente", "Next")
        dots.removeAllViews()
        for (k in steps.indices) {
            val d = View(this)
            d.background = GradientDrawable().apply { cornerRadius = dpf(4f); setColor(if (k == i) CYAN else LINE) }
            dots.addView(d, LinearLayout.LayoutParams(if (k == i) dp(22) else dp(8), dp(8)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        if (dir != 0) {
            page.translationX = dir * dpf(60f)
            page.alpha = 0f
            page.animate().translationX(0f).alpha(1f).setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
        }
    }

    private fun done() {
        prefs.edit().putBoolean(Prefs.GUIDE_SEEN, true).apply()
        finish()
    }
}
