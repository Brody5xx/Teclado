package com.luisell.bestkeyboard

import android.app.AlertDialog
import android.content.Intent
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 🛡 Mis datos y privacidad: qué guarda el teclado (en palabras simples), que todo se queda en el
 * teléfono, y un botón para borrar cada cosa.
 */
class PrivacyActivity : UiActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        loadLang()
        window.statusBarColor = BG
        window.navigationBarColor = BG
        WordStore.load(this) { paint() }
        paint()
    }

    override fun onResume() {
        super.onResume()
        if (::root.isInitialized) paint()
    }

    private fun ask(es: String, en: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(tr("¿Borrar?", "Delete?"))
            .setMessage(tr(es, en))
            .setPositiveButton(tr("Borrar", "Delete")) { _, _ -> action(); WordStore.scheduleSave(); paint() }
            .setNegativeButton(tr("Cancelar", "Cancel"), null)
            .show()
    }

    private fun paint() {
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
        title.text = tr("Mis datos", "My data")
        title.textSize = 28f
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
        title.setTextColor(TEXT)
        title.setPadding(dp(6), 0, 0, 0)
        title.post {
            title.paint.shader = LinearGradient(0f, 0f, title.width.toFloat(), 0f, CYAN, VIOLET, Shader.TileMode.CLAMP)
            title.invalidate()
        }
        head.addView(title)
        head.addView(DiamondView(this), LinearLayout.LayoutParams(dp(26), dp(26)).apply { leftMargin = dp(10) })
        root.addView(head)

        text(tr("Todo lo que el teclado guarda se queda SOLO en tu teléfono. Aquí ves qué es y puedes borrar cada cosa.",
            "Everything the keyboard saves stays ONLY on your phone. Here you can see what it is and delete each thing."), 14f)

        val u = WordStore.engine?.user

        // ---- Lo que aprendió de ti
        card(tr("Lo que aprendió de ti", "What it learned from you"), Icons.BOOK, keywords = "-")
        val nWords = u?.words?.values?.count { it.known } ?: 0
        val nPairs = (u?.bigrams?.values?.sumOf { it.size } ?: 0) + (u?.trigrams?.values?.sumOf { it.size } ?: 0)
        text(tr("$nWords palabras tuyas (nombres, siglas, palabras del trabajo).", "$nWords of your words (names, acronyms, work words)."), 14f)
        button(tr("Borrar mis palabras", "Delete my words")) {
            ask("El teclado olvidará las palabras que aprendió de ti.", "The keyboard will forget the words it learned from you.") {
                u?.words?.clear(); u?.recent?.clear(); u?.dirty = true
            }
        }
        text(tr("$nPairs frases (qué palabra sueles escribir después de otra).", "$nPairs phrases (which word you usually type after another)."), 14f)
        button(tr("Borrar mis frases", "Delete my phrases")) {
            ask("El teclado olvidará tus frases (las de fábrica se quedan).", "The keyboard will forget your phrases (the built-in ones stay).") {
                u?.bigrams?.clear(); u?.trigrams?.clear(); u?.dirty = true
            }
        }
        val nFix = u?.fixes?.size ?: 0
        val nBlocked = u?.blocked?.size ?: 0
        text(tr("$nFix arreglos tuyos y $nBlocked sugerencias que borraste.", "$nFix of your fixes and $nBlocked suggestions you deleted."), 14f)
        button(tr("Borrar arreglos y sugerencias borradas", "Delete fixes and deleted suggestions")) {
            ask("Se borran tus arreglos («clmo» → «como») y las sugerencias borradas vuelven a salir.",
                "Your fixes are deleted and the suggestions you deleted can show up again.") {
                u?.fixes?.clear(); u?.blocked?.clear(); u?.dirty = true
            }
        }
        val adaptN = prefs.getInt("adapt_n", 0)
        text(tr("Dónde sueles tocar las teclas: " + (if (adaptN > 0) "aprendido de $adaptN letras." else "todavía nada."),
            "Where you usually tap the keys: " + (if (adaptN > 0) "learned from $adaptN letters." else "nothing yet.")), 14f)
        button(tr("Borrar lo que aprendió de mi dedo", "Delete what it learned from my finger")) {
            ask("Las teclas vuelven a su lugar normal y empieza a aprender de nuevo.", "The keys go back to normal and it starts learning again.") {
                prefs.edit().remove("adapt_dx").remove("adapt_dy").remove("adapt_n").apply()
            }
        }

        // ---- Otras cosas guardadas
        card(tr("Otras cosas guardadas", "Other saved things"), Icons.CLIP, keywords = "-")
        ClipStore.load(this) { }
        text(tr("Portapapeles: ${ClipStore.items.size} cosas copiadas (duran 2 días; lo fijado se queda).",
            "Clipboard: ${ClipStore.items.size} copied items (they last 2 days; pinned ones stay)."), 14f)
        button(tr("Borrar el portapapeles (menos lo fijado)", "Clear the clipboard (except pinned)")) {
            ask("Se borra todo lo copiado, menos lo que fijaste.", "Everything copied is deleted, except what you pinned.") { ClipStore.clearUnpinned() }
        }
        val apps = (prefs.getString(Prefs.APP_LANGS, "") ?: "").split('|').count { it.contains('=') }
        text(tr("El idioma de $apps apps.", "The language of $apps apps."), 14f)
        button(tr("Olvidar el idioma de cada app", "Forget each app's language")) {
            ask("Cada app empieza otra vez en el idioma que tenga el teclado.", "Each app starts again in the keyboard's current language.") {
                prefs.edit().remove(Prefs.APP_LANGS).apply()
            }
        }
        text(tr("Estadísticas: cuántas palabras te adivinó y te corrigió.", "Statistics: how many words it guessed and fixed."), 14f)
        button(tr("Borrar estadísticas", "Delete statistics")) {
            ask("Las estadísticas vuelven a cero.", "Statistics go back to zero.") {
                val e = prefs.edit()
                for (k in prefs.all.keys) if (k.startsWith("stat_")) e.remove(k)
                e.apply()
            }
        }
        VaultStore.load(this)
        text(tr("Datos: ${VaultStore.items.size} cosas guardadas (correos, contraseñas, cuentas), cifradas y con tu huella.",
            "Data: ${VaultStore.items.size} saved items (emails, passwords, accounts), encrypted and behind your fingerprint."), 14f)
        button(tr("Abrir Datos", "Open Data")) { startActivity(Intent(this, DataActivity::class.java)) }

        // ---- Lo que sale del teléfono
        card(tr("Lo que sale a internet", "What goes online"), Icons.GLOBE, keywords = "-")
        text(tr(
            "Nada se manda solo. Solo sale lo que tú pides, cuando tocas el botón:\n" +
                "•  IA (Más formal, Respuestas, Terminar la frase, Ticket): el texto va a la IA gratis.\n" +
                "•  Corregir gramática: el texto va al revisor de gramática.\n" +
                "•  GIFs y Buscar en Google: lo que buscas.\n" +
                "Los nombres de tus contactos se leen en el teléfono y no se guardan ni se mandan.\n" +
                "En modo incógnito (en ⋯) el teclado no aprende ni guarda lo que escribes o copias.",
            "Nothing is sent by itself. Only what you ask for, when you tap the button:\n" +
                "•  AI (More formal, Replies, Finish sentence, Ticket): the text goes to the free AI.\n" +
                "•  Fix grammar: the text goes to the grammar checker.\n" +
                "•  GIFs and Search Google: what you search.\n" +
                "Your contacts' names are read on the phone and are not saved or sent.\n" +
                "In incognito mode (in ⋯) the keyboard doesn't learn or save what you type or copy."), 13f, faded = true)

        setContentView(sv)
    }
}
