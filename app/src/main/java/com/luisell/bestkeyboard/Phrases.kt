package com.luisell.bestkeyboard

import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Frases rápidas: plantillas para los reportes y tickets del trabajo (control de acceso, cámaras,
 * cableado, fibra…) en español e inglés, más las tuyas. "___" marca lo que tienes que llenar:
 * al insertar una frase, el primer "___" queda seleccionado para que escribas encima.
 */
object Phrases {
    const val BLANK = "___"

    val EN = listOf(
        "Arrived on site at ___ and checked in with ___.",
        "Met with ___ to review the scope of work.",
        "Inspected the access control panel at ___. All readers are online.",
        "Replaced the faulty mag lock at door ___ and tested operation. Working properly.",
        "Ran new Cat 6 cable from IDF ___ to ___, terminated and tested. Passed.",
        "Installed IP camera at ___, adjusted the view and verified recording on the NVR.",
        "Spliced and tested fiber at ___. Light levels are within spec.",
        "Troubleshot door ___. The issue was ___. Repaired and tested.",
        "Programmed new access card(s) for ___.",
        "Door automation at ___ adjusted and tested. Opening and closing correctly.",
        "Waiting on parts: ___. Will return to complete the job.",
        "Job completed. Work area cleaned up and ___ signed off.",
        "Left site at ___.",
        "Please let me know if you need anything else. Thank you.",
    )
    val ES = listOf(
        "Llegué al sitio a las ___ y me reporté con ___.",
        "Me reuní con ___ para revisar el alcance del trabajo.",
        "Revisé el panel de control de acceso en ___. Todos los lectores están en línea.",
        "Reemplacé el mag lock dañado de la puerta ___ y probé su funcionamiento. Funciona correctamente.",
        "Pasé cable Cat 6 nuevo desde el IDF ___ hasta ___, lo terminé y lo probé. Pasó la prueba.",
        "Instalé la cámara IP en ___, ajusté la vista y verifiqué la grabación en el NVR.",
        "Empalmé y probé la fibra en ___. Los niveles de luz están dentro de especificación.",
        "Revisé la puerta ___. El problema era ___. Se reparó y se probó.",
        "Programé tarjeta(s) de acceso nuevas para ___.",
        "Ajusté y probé la automatización de la puerta en ___. Abre y cierra correctamente.",
        "Esperando piezas: ___. Regreso para terminar el trabajo.",
        "Trabajo terminado. Área de trabajo limpia y ___ firmó de conformidad.",
        "Salí del sitio a las ___.",
        "Quedo atento por si necesitan algo más. Gracias.",
    )

    /** Tus frases (guardadas en el teléfono). */
    fun mine(p: SharedPreferences): List<String> = try {
        val a = JSONArray(p.getString(Prefs.PHRASES, "[]"))
        (0 until a.length()).map { a.getString(it) }.filter { it.isNotBlank() }
    } catch (_: Exception) { emptyList() }

    fun saveMine(p: SharedPreferences, list: List<String>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        p.edit().putString(Prefs.PHRASES, a.toString()).apply()
    }

    /** Todas las que se ven en el teclado: las tuyas primero, luego los ejemplos del idioma elegido. */
    fun all(p: SharedPreferences, english: Boolean): List<String> {
        val ex = if (p.getBoolean(Prefs.PHRASES_EXAMPLES, true)) (if (english) EN else ES) else emptyList()
        return mine(p) + ex
    }
}
