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

        val out = File("build/reporte-pruebas.txt")
        out.parentFile.mkdirs()
        out.writeText(sb.toString())
        println(sb)
    }
}
