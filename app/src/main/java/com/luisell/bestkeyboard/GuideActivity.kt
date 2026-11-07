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
            Step(Icons.SPELL, tr("Sugerencias que te conocen", "Suggestions that know you"), tr(
                "Adivina la palabra siguiente antes de que escribas («thank you» → so much) y, si la frase es obvia, te da la frase completa. Si escribes algo mal («actulizo»), te dice «¿quisiste decir…?». Mantén presionada una sugerencia para borrarla para siempre.",
                "It guesses the next word before you type («thank you» → so much) and, if the sentence is obvious, gives you the whole phrase. If you misspell something, it shows «did you mean…?». Press and hold a suggestion to delete it forever.")),
            Step(Icons.SPELL, tr("Acentos y signos solos", "Accents and punctuation"), tr(
                "«tu puedes» → ✨ tú puedes, «ya esta» → ya está, «ya termine» → ya terminé. Después de ¿ pone el acento (¿cómo?). Si es pregunta sale «?», y antes de «pero» te sugiere la coma. En inglés: dont → don't, im → I'm.",
                "Spanish accents by sentence: «tu puedes» → ✨ tú puedes, «ya esta» → ya está. If it's a question, «?» shows up, and it suggests the comma before «but». In English: dont → don't, im → I'm.")),
            Step(Icons.SETTINGS, tr("Aprende de ti", "It learns from you"), tr(
                "Si borras una palabra mal escrita y la escribes bien, la próxima vez se corrige sola. Las teclas se acomodan a dónde sueles tocar, cada app recuerda su idioma y, si quieres, sugiere los nombres de tus contactos bien escritos.",
                "If you delete a misspelled word and type it right, next time it fixes itself. Keys adapt to where you usually tap, each app remembers its language and, if you want, it suggests your contacts' names spelled right.")),
            Step(Icons.CALC, tr("Calculadora y conversor", "Calculator and converter"), tr(
                "Escribe 25*4= y te sugiere 100. «120 ft a m» → 36.58 m, «70 f a c» → 21.11 °C, «20 usd a pesos» con las tasas de hoy. Y 7pm → 7:00 PM, cat6 → Cat 6.",
                "Type 25*4= and it suggests 100. «120 ft to m» → 36.58 m, «70 f to c» → 21.11 °C, «20 usd to eur» with today's rates. And 7pm → 7:00 PM, cat6 → Cat 6.")),
            Step(Icons.BUBBLE, tr("Atajos con fecha y hora", "Shortcuts with date and time"), tr(
                "Escribe /hoy, /hora o /ahora y un espacio: sale la fecha o la hora de ahora. En tus atajos puedes poner {fecha} o {hora}, y lo que va entre [corchetes] queda seleccionado para cambiarlo.",
                "Type /today, /time or /now and a space: you get today's date or the time. In your shortcuts you can use {date} or {time}, and whatever is in [brackets] gets selected so you can change it.")),
            Step(Icons.GLOBE, tr("Para todos", "For everyone"), tr(
                "Elige para qué lo usas y se prende solo lo que te sirve. Puedes escoger tus 2 idiomas (español, inglés, portugués, francés, italiano o alemán), usar el Modo fácil (teclas grandes y alto contraste) y, acostado o en tablet, el teclado se divide en dos.",
                "Pick what you use it for and only what helps you turns on. Choose your 2 languages (Spanish, English, Portuguese, French, Italian or German), use Easy mode (big keys and high contrast) and, in landscape or on a tablet, the keyboard splits in two.")),
            Step(Icons.EYE_OFF, tr("Tu privacidad", "Your privacy"), tr(
                "Todo se queda en tu teléfono. En «Mis datos y privacidad» ves qué guarda y lo borras. Con el Modo incógnito (en ⋯) no aprende ni guarda nada, y se prende solo en las pestañas privadas.",
                "Everything stays on your phone. In «My data and privacy» you see what it saves and delete it. With Incognito mode (in ⋯) it doesn't learn or save anything, and it turns on by itself in private tabs.")),
            Step(Icons.CLIP, tr("Pegar con un toque", "Paste with one tap"), tr(
                "Lo que copias, los códigos de verificación y tus capturas salen arriba del teclado: un toque y se pega, o toca la ✕ para quitarlo. En el portapapeles cada cosa dice cuándo la copiaste (Hoy, Ayer, 3 nov).",
                "What you copy, verification codes and your screenshots show above the keyboard: one tap pastes it, or tap the ✕ to remove it. In the clipboard each item says when you copied it (Today, Yesterday, Nov 3).")),
            Step(Icons.UNDO, tr("Deshacer", "Undo"), tr(
                "Al lado del 0 está la tecla de deshacer. Si borras algo sin querer, se ilumina: tócala y vuelve lo que borraste.",
                "Next to the 0 is the undo key. If you delete something by mistake, it lights up: tap it and what you deleted comes back.")),
            Step(Icons.EMOJI, tr("Emojis, GIFs y stickers", "Emojis, GIFs and stickers"), tr(
                "Toca el botón de emojis (o mantén la coma): arriba puedes buscar emojis y el botón GIF abre los GIFs y stickers. Mientras escribes también te sugiere emojis.",
                "Tap the emoji button (or hold the comma): at the top you can search emojis and the GIF button opens GIFs and stickers. It also suggests emojis while you type.")),
            Step(Icons.REPLY, tr("IA gratis: respuestas y terminar la frase", "Free AI: replies and finish sentence"), tr(
                "«Respuestas con IA»: copia el mensaje que te llegó y te da varias respuestas. «Terminar la frase con IA»: escribe el principio y te da 3 formas de terminarlo. Es la misma IA gratis de Best Notes, sin clave.",
                "«AI replies»: copy the message you got and it gives you several replies. «Finish sentence with AI»: type the start and get 3 ways to finish it. Same free AI as Best Notes, no key needed.")),
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
                "Comencé (en la camioneta), Llegué al sitio, Salí del sitio y Casa. Calcula tus horas, te avisa a las 8 horas en el sitio y celebra contigo. Con «Resumen de la semana» → «Mandar» envías tus horas por WhatsApp o correo.",
                "Started (in the truck), Arrived on site, Left the site and Home. It adds up your hours, tells you at 8 hours on site and celebrates with you. With «Week summary» → «Send» you send your hours by WhatsApp or email.")),
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
            Step(Icons.TRANSLATE, tr("Traducir, dictar y buscar", "Translate, dictate and search"), tr(
                "Traduce lo que escribes o lo que te llegó. Al dictar con el micrófono pone los signos solo (¿Cómo estás?) y, en teléfonos nuevos, entiende si hablas español o inglés en cada frase. «Buscar en Google» busca sin salir del teclado.",
                "Translate what you type or what you got. When dictating it adds punctuation by itself and, on newer phones, understands whether you speak Spanish or English in each sentence. «Search Google» searches without leaving the keyboard.")),
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
            Step(Icons.STOPWATCH, tr("Estadísticas", "Statistics"), tr(
                "En Ajustes › Escribir ves cuántas palabras te adivinó esta semana, cuántas letras te ahorraste y cuántos errores corrigió solo.",
                "In Settings › Typing you see how many words it guessed this week, how many letters you saved and how many mistakes it fixed.")),
            Step(Icons.SEARCH, tr("¡Listo!", "All set!"), tr(
                "En Ajustes toca «Secciones ›» o la rayita de luz del borde derecho para ver todas las secciones, o usa el buscador. Si cambias de teléfono, haz una copia en «Copia»: se lleva todo, hasta lo que el teclado aprendió de ti.",
                "In Settings tap «Sections ›» or the light strip on the right edge to see every section, or use the search. If you change phones, make a backup in «Backup»: it takes everything, even what the keyboard learned from you.")),
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
