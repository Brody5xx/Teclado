package com.luisell.bestkeyboard

/**
 * Idioma de los textos del teclado (botones, paneles, mensajes).
 * Sigue al idioma del teclado: si cambias a English con 🌐, todo se ve en inglés.
 *
 * Para cualquier texto nuevo usa:  L.t("Texto en español", "Text in English")
 */
object L {
    @Volatile var english = false
        private set

    private val listeners = ArrayList<() -> Unit>()
    private val bound = ArrayList<Triple<android.widget.TextView, String, String>>()
    private val hints = ArrayList<Triple<android.widget.TextView, String, String>>()

    /** Pone el texto de un botón/título en los dos idiomas; se cambia solo con 🌐. */
    fun bind(v: android.widget.TextView, es: String, en: String) {
        bound.add(Triple(v, es, en))
        v.text = t(es, en)
    }

    /** Igual que [bind], pero para el texto gris de ayuda de una caja ("Escribe aquí..."). */
    fun bindHint(v: android.widget.TextView, es: String, en: String) {
        hints.add(Triple(v, es, en))
        v.hint = t(es, en)
    }

    /** Idioma del teclado ("es", "en", "pt"…). */
    @Volatile private var cur = "es"
    /** Si elegiste las pantallas en portugués, francés, italiano, alemán o chino, el teclado también. */
    @Volatile var screen: String? = null

    /** El texto en el idioma actual del teclado. */
    fun t(es: String, en: String): String {
        screen?.let { return UiTr.of(it, en, es) ?: en }
        return when (cur) {
            "es" -> es
            "en" -> en
            else -> UiTr.of(cur, en, es) ?: if (english) en else es
        }
    }

    /** Lee el idioma de las pantallas (se llama al abrir el teclado). */
    fun setScreen(ctx: android.content.Context) {
        val c = UiTr.screenLang(ctx)
        val v = if (c in UiTr.LANGS) c else null
        if (v == screen) return
        screen = v
        refresh()
    }

    private fun refresh() {
        for ((v, a, b) in bound) v.text = t(a, b)
        for ((v, a, b) in hints) v.hint = t(a, b)
        for (l in ArrayList(listeners)) l()
    }

    /**
     * 🔔 Para los avisos, el widget y lo que pasa con el teclado cerrado: el idioma de las pantallas de la app
     * (no el idioma en que estás escribiendo). El teclado vuelve a poner el suyo cuando se abre.
     */
    fun useScreen(ctx: android.content.Context) {
        // Con el teclado abierto se queda su idioma (si no, sus botones cambiarían de idioma)
        if (BestKeyboardService.instance?.isInputViewShown == true) return
        forceScreen(ctx)
    }

    private fun forceScreen(ctx: android.content.Context) {
        val c = try { UiTr.screenLang(ctx) } catch (_: Throwable) { "es" }
        screen = if (c in UiTr.LANGS) c else null
        english = c == "en" || c == "fr" || c == "de"
        cur = c
    }

    /** Igual que [useScreen], pero solo mientras corre [block] (por si el teclado está abierto). */
    fun <T> withScreen(ctx: android.content.Context, block: () -> T): T {
        val oc = cur; val oe = english; val os = screen
        forceScreen(ctx)
        try { return block() } finally { cur = oc; english = oe; screen = os }
    }

    /** Cambia el idioma de los textos y avisa a todos los paneles para que se actualicen. */
    fun set(lang: String) {
        // Los textos del teclado van en español o inglés (francés y alemán → inglés; portugués e italiano → español)
        val en = lang == "en" || lang == "fr" || lang == "de"
        if (en == english && lang == cur) return
        english = en
        cur = lang
        refresh()
    }

    /** Los paneles se anotan aquí para volver a poner sus textos al cambiar de idioma. */
    fun onChange(l: () -> Unit) { listeners.add(l) }

    /** Al crear el teclado de nuevo se olvidan los botones viejos. */
    fun clearListeners() {
        listeners.clear()
        bound.clear()
        hints.clear()
    }
}
