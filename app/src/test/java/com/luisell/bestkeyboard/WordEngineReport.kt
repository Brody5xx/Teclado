package com.luisell.bestkeyboard

import com.luisell.bestkeyboard.words.Dictionary
import com.luisell.bestkeyboard.words.UserData
import com.luisell.bestkeyboard.words.WordEngine
import org.junit.Test
import java.io.File

/**
 * Prueba del motor de palabras. Escribe un reporte (build/reporte-pruebas.txt)
 * que GitHub publica junto al APK, para revisar cómo corrige y sugiere.
 */
class WordEngineReport {

    @Test
    fun report() {
        val t0 = System.nanoTime()
        val es = File("src/main/assets/dict_es.txt").inputStream().use { Dictionary.load("es", it) }
        val en = File("src/main/assets/dict_en.txt").inputStream().use { Dictionary.load("en", it) }
        val loadMs = (System.nanoTime() - t0) / 1_000_000
        val user = UserData()
        val eng = WordEngine(listOf(es, en), user)
        val sb = StringBuilder()
        sb.append("Carga de diccionarios: ${loadMs} ms (es=${es.size}, en=${en.size})\n\n")

        sb.append("== Autocorrector (idioma español) ==\n")
        val esCases = listOf(
            "tambien", "manana", "esta", "está", "como", "que", "aqui", "despues", "porfavor",
            "hloa", "hola", "graicas", "trabjo", "cmara", "camaras", "puerta", "instalacion",
            "cable", "cableado", "fibra", "electrico", "electrica", "hospital", "supermercado",
            "mañna", "nesecito", "ayer", "asi", "dia", "vamoss", "q", "xq", "ok", "jajaja",
            "RUMC", "DVSS", "Mendoza", "Luisell", "acceso", "conexion", "revisar", "cambie",
            "haser", "vien", "osea", "aveces", "tecnico", "door", "the", "meeting",
        )
        for (w in esCases) sb.append("  $w -> ${eng.correction(w, "es") ?: "(sin cambio)"}\n")

        sb.append("\n== Autocorrector (idioma inglés) ==\n")
        val enCases = listOf(
            "i", "im", "dont", "cant", "teh", "recieve", "door", "lock", "camera", "wiring",
            "thier", "wierd", "becuase", "tommorow", "ok", "gonna", "were", "well", "its", "access",
            "id", "hes", "hola", "gracias",
        )
        for (w in enCases) sb.append("  $w -> ${eng.correction(w, "en") ?: "(sin cambio)"}\n")

        sb.append("\n== Sugerencias (español) ==\n")
        for (p in listOf("h", "ho", "tra", "gra", "cam", "pue", "tamb", "insta", "mañ", "nec", "Ho")) {
            sb.append("  $p -> ${eng.completions(p, null, "es")}\n")
        }
        sb.append("\n== Sugerencias (inglés) ==\n")
        for (p in listOf("t", "th", "don", "wh", "acc", "cam")) {
            sb.append("  $p -> ${eng.completions(p, null, "en")}\n")
        }

        sb.append("\n== Aprendizaje ==\n")
        user.learn("Luisell")
        user.add("RUMC"); user.add("RUMC")
        repeat(3) { user.addBigram("buenos", "días") }
        sb.append("  Lui -> ${eng.completions("Lui", null, "es")}\n")
        sb.append("  luisel -> ${eng.correction("luisel", "es")}\n")
        sb.append("  después de 'buenos' -> ${eng.predictions("buenos", "es")}\n")

        // Velocidad
        val t1 = System.nanoTime()
        var n = 0
        repeat(50) {
            for (p in listOf("a", "d", "c", "s", "e")) { eng.completions(p, null, "es"); n++ }
            for (w in listOf("instalacionn", "nesecito", "becuase")) { eng.correction(w, "es"); n++ }
        }
        val perMs = (System.nanoTime() - t1) / 1_000_000.0 / n
        sb.append("\nVelocidad: %.3f ms por búsqueda (en PC; en el teléfono ~3-5x más)\n".format(perMs))

        sb.append("\n== Dictado por voz ==\n")
        for (t in listOf(
            "hola coma cómo estás signo de interrogación",
            "ya terminé la instalación punto final mañana reviso las cámaras punto final",
            "el punto de acceso está listo",
            "llegué al hospital punto y aparte la puerta no abre",
            "necesito tres cosas dos puntos cable coma conectores y cinta",
        )) sb.append("  ").append(t).append(" -> ").append(com.luisell.bestkeyboard.words.VoiceText.process(t, "es").replace("\n", "⏎")).append('\n')
        for (t in listOf("hi comma how are you question mark", "the door is done period new line thanks")) {
            sb.append("  ").append(t).append(" -> ").append(com.luisell.bestkeyboard.words.VoiceText.process(t, "en").replace("\n", "⏎")).append('\n')
        }

        // ---------------------------------------------------------------- Corrector ortográfico
        sb.append("\n== Corrector ortográfico (subrayado rojo) ==\n")
        for ((lg, w) in listOf(
            "es" to "tambien", "es" to "nesecito", "es" to "trabjo", "es" to "aqui", "es" to "hola",
            "es" to "mañana", "es" to "door", "es" to "RUMC", "es" to "Luisell", "es" to "instalacion",
            "en" to "recieve", "en" to "becuase", "en" to "i", "en" to "camera", "en" to "hola", "en" to "dont",
        )) {
            val r = eng.spellCheck(w, lg)
            sb.append("  [$lg] $w -> ").append(if (r.misspelled) "MAL ${r.suggestions}" else "bien").append('\n')
        }

        sb.append("  Revisión a fondo (botón abc✓):\n")
        for (w in listOf("Hula", "Hule", "n", "ok", "y", "coma", "estas", "cume", "sensor", "cerradura", "hola")) {
            val r = eng.spellCheck(w, "es", 5, strict = true)
            sb.append("    $w -> ").append(if (r.misspelled) (if (r.maybe) "¿Quisiste decir? ${r.suggestions}" else "MAL ${r.suggestions}") else "bien").append('\n')
        }

        for ((x, y) in listOf("Toda" to "vio", "en" to "cima", "con" to "tigo", "si" to "no", "de" to "más", "para" to "que", "mi" to "casa")) {
            sb.append("    Partida: $x $y -> ${eng.joinedWord(x, y, "es") ?: "(bien)"}\n")
        }

        // ---------------------------------------------------------------- Deslizar
        sb.append("\n== Deslizar para escribir (trazos simulados) ==\n")
        val dec = com.luisell.bestkeyboard.words.GestureDecoder(listOf(es, en), UserData())
        val kw = 100f
        val keys = ArrayList<com.luisell.bestkeyboard.words.GestureDecoder.KeyPos>()
        "qwertyuiop".forEachIndexed { i, c -> keys.add(com.luisell.bestkeyboard.words.GestureDecoder.KeyPos(c, (i + 0.5f) * kw, 0.5f * 125f)) }
        "asdfghjklñ".forEachIndexed { i, c -> keys.add(com.luisell.bestkeyboard.words.GestureDecoder.KeyPos(c, (i + 0.5f) * kw, 1.5f * 125f)) }
        "zxcvbnm".forEachIndexed { i, c -> keys.add(com.luisell.bestkeyboard.words.GestureDecoder.KeyPos(c, (2f + i) * kw, 2.5f * 125f)) }
        val pos = keys.associateBy { it.ch }
        val rnd = java.util.Random(7)
        fun synth(word: String): Pair<FloatArray, FloatArray> {
            val k = com.luisell.bestkeyboard.words.GestureDecoder.keyChars(word)
            val px = ArrayList<Float>(); val py = ArrayList<Float>()
            val cx = k.map { pos[it]!!.x + rnd.nextGaussian().toFloat() * 25f }
            val cy = k.map { pos[it]!!.y + rnd.nextGaussian().toFloat() * 25f }
            for (i in 0 until k.length - 1) for (t in 0 until 8) {
                val f = t / 8f
                px.add(cx[i] + (cx[i + 1] - cx[i]) * f); py.add(cy[i] + (cy[i + 1] - cy[i]) * f)
            }
            px.add(cx.last()); py.add(cy.last())
            // Suavizado (el dedo redondea las esquinas)
            val sx = FloatArray(px.size); val sy = FloatArray(py.size)
            for (i in px.indices) {
                var ax = 0f; var ay = 0f; var n = 0
                for (j in maxOf(0, i - 2)..minOf(px.size - 1, i + 2)) { ax += px[j]; ay += py[j]; n++ }
                sx[i] = ax / n; sy[i] = ay / n
            }
            sx[0] = px[0]; sy[0] = py[0]; sx[sx.size - 1] = px.last(); sy[sy.size - 1] = py.last()
            return Pair(sx, sy)
        }
        val tests = listOf(
            "es" to "hola gracias trabajo cámara puerta instalación cable también mañana necesito hospital acceso revisar listo bueno cuando donde quiero tengo tiempo gente estoy mucho porque fibra cerradura llegué jefe cliente edificio problema",
            "en" to "the door camera access thanks hello work done today please reader cable install tomorrow meeting",
        )
        var total = 0; var top1 = 0; var top3 = 0
        val t2 = System.nanoTime()
        val fails = ArrayList<String>()
        for ((lg, list) in tests) for (w in list.split(" ")) repeat(3) {
            val (x, y) = synth(w)
            val r = dec.decode(x, y, x.size, keys, kw, lg, null)
            total++
            if (r.isNotEmpty() && r[0].equals(w, true)) top1++
            if (r.take(3).any { it.equals(w, true) }) top3++ else if (fails.size < 10) fails.add("$w→${r.take(3)}")
        }
        val gMs = (System.nanoTime() - t2) / 1_000_000.0 / total
        sb.append("  Acierta a la primera: $top1/$total (${top1 * 100 / total}%)\n")
        sb.append("  Entre las 3 de arriba: $top3/$total (${top3 * 100 / total}%)\n")
        sb.append("  Velocidad: %.1f ms por trazo (en PC)\n".format(gMs))
        sb.append("  Fallos: $fails\n")

        val out = File("build/reporte-pruebas.txt")
        out.parentFile.mkdirs()
        out.writeText(sb.toString())
        println(sb)
    }
}
