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
            Step(Icons.DIAMOND, tr("¡Bienvenido!", "Welcome!"), tr(
                "Este es un recorrido rápido por Best Keyboard. Desliza a los lados o toca «Siguiente».",
                "This is a quick tour of Best Keyboard. Swipe sideways or tap «Next».")),
            Step(Icons.MORE, tr("La barra de arriba", "The top bar"), tr(
                "Desliza la barra a los lados para ver todos los botones. Mantén presionado uno y arrástralo para acomodarlo. Los tres puntos (⋯) abren «Más opciones», y con el lápiz acomodas todo.",
                "Swipe the bar sideways to see all buttons. Press and hold one and drag it to rearrange. The three dots (⋯) open «More options», and the pencil lets you arrange everything.")),
            Step(Icons.GLOBE, tr("Español e inglés solos", "Spanish and English by itself"), tr(
                "Si escribes 2 palabras seguidas en inglés, el teclado se cambia a inglés solo (y al revés). Verás la luz en la barra espaciadora. En inglés no sale la ñ: déjala presionando la n.",
                "If you type 2 words in a row in the other language, the keyboard switches by itself. You'll see the light on the space bar. In English there's no ñ: hold the n to get it.")),
            Step(Icons.SPELL, tr("Sugerencias y corrector", "Suggestions and spell check"), tr(
                "El teclado aprende tus palabras y te las sugiere en el idioma correcto. Las palabras mal escritas se subrayan en rojo; toca la palabra para ver cómo corregirla.",
                "The keyboard learns your words and suggests them in the right language. Misspelled words get a red underline; tap the word to see how to fix it.")),
            Step(Icons.CLIP, tr("Pegar con un toque", "Paste with one tap"), tr(
                "Lo que copias, los códigos de verificación y tus capturas salen arriba del teclado. Un toque y se pega. Si no lo quieres, toca la ✕ pequeña y desaparece.",
                "What you copy, verification codes and your screenshots show above the keyboard. One tap pastes it. If you don't want it, tap the small ✕ and it goes away.")),
            Step(Icons.UNDO, tr("Deshacer", "Undo"), tr(
                "Al lado del 0 está la tecla de deshacer. Si borras algo sin querer, se ilumina: tócala y vuelve lo que borraste.",
                "Next to the 0 is the undo key. If you delete something by mistake, it lights up: tap it and what you deleted comes back.")),
            Step(Icons.EMOJI, tr("Emojis, GIFs y stickers", "Emojis, GIFs and stickers"), tr(
                "Toca el botón de emojis (o mantén la coma): arriba puedes buscar emojis y el botón GIF abre los GIFs y stickers. Mientras escribes también te sugiere emojis.",
                "Tap the emoji button (or hold the comma): at the top you can search emojis and the GIF button opens GIFs and stickers. It also suggests emojis while you type.")),
            Step(Icons.REPLY, tr("Respuestas con IA", "AI replies"), tr(
                "Copia el mensaje que te llegó y toca «Respuestas con IA»: te da varias respuestas listas para enviar. Funciona gratis, sin clave.",
                "Copy the message you got and tap «AI replies»: it gives you several replies ready to send. It's free, no key needed.")),
            Step(Icons.FORMAL, tr("Escribir más formal", "Write more formally"), tr(
                "Las sugerencias con estrellita cambian palabras sencillas por formales (arreglé → reparé). «Revisar antes de enviar» revisa todo el mensaje y «Corregir gramática» lo arregla.",
                "Suggestions with a little star swap simple words for formal ones (fixed → repaired). «Review before sending» checks the whole message and «Fix grammar» fixes it.")),
            Step(Icons.TICKET, tr("Notas → Ticket", "Notes → Ticket"), tr(
                "Escribe rápido con tus palabras y toca Notas → Ticket: la IA lo vuelve un ticket formal con tu formato del trabajo. En el micrófono también puedes dictar el reporte hablando.",
                "Type quickly in your own words and tap Notes → Ticket: the AI turns it into a formal ticket in your work format. In the mic you can also dictate the report.")),
            Step(Icons.NOTEBOOK, tr("Libreta y Mis tickets", "Notebook and My tickets"), tr(
                "En la Libreta apuntas cosas durante el día y al final tocas «Crear ticket del día». Mis tickets guarda cada ticket con su número.",
                "In the Notebook you jot things down during the day and at the end tap «Create the day's ticket». My tickets saves each ticket with its number.")),
            Step(Icons.CLOCK, tr("Mi jornada", "My workday"), tr(
                "Comencé (en la camioneta), Llegué al sitio, Salí del sitio y Casa. Calcula tus horas, las pone en el ticket y te avisa cuando llevas 8 horas en el sitio de trabajo (sin contar el manejo). Al terminar el día y al llegar a 40 horas, ¡celebra contigo!",
                "Started (in the truck), Arrived on site, Left the site and Home. It adds up your hours, puts them in the ticket and tells you when you reach 8 hours on site (not counting driving). At the end of the day and at 40 hours, it celebrates with you!")),
            Step(Icons.MAP_PIN, tr("Mis sitios", "My sites"), tr(
                "Guarda los lugares donde trabajas y el teclado marca Llegué y Salí solo. A cada sitio le ajustas la distancia en el mapa y la sensibilidad. Con «Recordar al llegar» te avisa algo cuando llegues a un sitio.",
                "Save the places where you work and the keyboard marks Arrived and Left by itself. For each site you set the distance on the map and the sensitivity. With «Remind on arrival» it tells you something when you get to a site.")),
            Step(Icons.STEER, tr("Modo manejo", "Driving mode"), tr(
                "Botones grandes para mandar Mensajes rápidos y tu estado sin mirar mucho. Se queda prendido hasta que toques «Teclado» o el mismo botón de Modo manejo. También borra letra por letra o todo de una vez (y lo puedes deshacer).",
                "Big buttons to send Quick messages and your status without looking much. It stays on until you tap «Keyboard» or the Driving mode button again. It also deletes letter by letter or everything at once (and you can undo it).")),
            Step(Icons.NETWORK, tr("Herramientas del trabajo", "Work tools"), tr(
                "Teclado IP/MAC para escribir direcciones rápido, Etiquetas con números de serie, y «¿Cuántos pies por línea?» que te calcula el cable que tienes que correr.",
                "IP/MAC pad to type addresses fast, Labels with serial numbers, and «How many feet per line?» which works out the cable you need to run.")),
            Step(Icons.BARCODE, tr("Escanear", "Scan"), tr(
                "Con una sola foto, el escáner lee el texto y también los códigos de barras y QR: números de serie y MAC sin escribirlos.",
                "With one photo, the scanner reads the text and also barcodes and QR codes: serial numbers and MACs without typing.")),
            Step(Icons.TRANSLATE, tr("Traducir y buscar", "Translate and search"), tr(
                "Escribe en español y sale en inglés, traduce un mensaje que te llegó, o dicta en español y que salga en inglés. Con «Buscar en Google» escribes y buscas sin salir del teclado.",
                "Type in Spanish and get English, translate a message you got, or speak Spanish and get English. With «Search Google» you type and search without leaving the keyboard.")),
            Step(Icons.LOCK, tr("Datos (tus contraseñas)", "Data (your passwords)"), tr(
                "Como el administrador de Google: cada cuenta guarda usuario, correo, contraseña, preguntas, clave y PIN. En el teclado toca el candado, pon tu huella y elige lo que quieras pegar. Todo cifrado y solo en tu teléfono.",
                "Like Google's password manager: each account keeps user, email, password, questions, key and PIN. On the keyboard tap the lock, use your fingerprint and pick what to paste. All encrypted and only on your phone.")),
            Step(Icons.BOOK, tr("Palabra de la semana", "Word of the week"), tr(
                "Cada lunes sale una palabra nueva con su significado, un ejemplo y cómo se dice en inglés. Hay 105 palabras: una por semana por 2 años.",
                "Every Monday there's a new word with its meaning, an example and how to say it in Spanish. There are 105 words: one a week for 2 years.")),
            Step(Icons.BRUSH, tr("Tu estilo", "Your style"), tr(
                "En Estilo cambias el tema (como el Diamante), los colores de los paneles y botones, la forma y el tamaño de las teclas, las animaciones de luz, la vibración y el sonido.",
                "In Style you change the theme (like Diamond), the colors of panels and buttons, the shape and size of the keys, the light animations, vibration and sound.")),
            Step(Icons.FLOAT, tr("Flotante y una mano", "Floating and one hand"), tr(
                "El teclado puede ser una ventanita que mueves por la pantalla, o hacerse angosto para usarlo con una mano.",
                "The keyboard can be a small window you move around, or get narrower to use with one hand.")),
            Step(Icons.SEARCH, tr("¡Listo!", "All set!"), tr(
                "En Ajustes toca «Secciones ›» o la rayita de luz del borde derecho y se abre el panel con todas las secciones. Con el buscador encuentras cualquier opción. Haz una copia de seguridad en «Copia» para no perder nada.",
                "In Settings tap «Sections ›» or the light strip on the right edge and the panel opens with every section. With the search box you find any option. Make a backup in «Backup» so you never lose anything.")),
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
        body.textSize = 15.5f
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
            dots.addView(d, LinearLayout.LayoutParams(if (k == i) dp(16) else dp(6), dp(6)).apply { setMargins(dp(2), 0, dp(2), 0) })
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
