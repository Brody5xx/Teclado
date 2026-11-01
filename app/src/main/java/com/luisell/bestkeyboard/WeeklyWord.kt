package com.luisell.bestkeyboard

import java.util.Calendar

/**
 * Palabra de la semana: cada lunes una palabra nueva para hablar y escribir con más fluidez.
 * Trae su significado, un ejemplo y palabras sencillas que puede reemplazar (el teclado la sugiere ahí).
 */
object WeeklyWord {

    class Word(val word: String, val meaning: String, val example: String, val en: String, val replaces: List<String>)

    val WORDS = listOf(
        Word("Afable", "Amable y fácil de tratar.", "El nuevo vecino fue muy afable con todos en el edificio.", "Affable", listOf("amable", "buena onda")),
        Word("Asertivo", "Que dice lo que piensa con claridad y respeto.", "Fui asertivo y le expliqué que faltaba material.", "Assertive", listOf("directo")),
        Word("Perspicaz", "Que nota detalles que otros no ven.", "Fue perspicaz notar que el cable estaba mal etiquetado.", "Perceptive", listOf("listo", "observador")),
        Word("Cordial", "Amable y respetuoso, con buen trato.", "La reunión fue cordial y llegamos a un acuerdo.", "Cordial", listOf("amable", "amistoso")),
        Word("Pertinente", "Que viene al caso; oportuno.", "Hizo una pregunta muy pertinente sobre los permisos.", "Relevant", listOf("importante", "que viene al caso")),
        Word("Ecuánime", "Que mantiene la calma y es justo.", "Aunque hubo problemas, se mantuvo ecuánime.", "Even-tempered", listOf("tranquilo", "calmado")),
        Word("Elocuente", "Que habla de forma clara y convincente.", "Su explicación fue tan elocuente que todos entendieron.", "Eloquent", listOf("bien hablado")),
        Word("Ocurrente", "Que tiene ideas graciosas o ingeniosas.", "Mi compañero es muy ocurrente, siempre nos hace reír.", "Witty", listOf("chistoso", "gracioso")),
        Word("Diligente", "Que hace su trabajo con cuidado y rapidez.", "Fue diligente y terminó la instalación antes de tiempo.", "Diligent", listOf("trabajador", "chambeador")),
        Word("Sensato", "Que piensa y actúa con buen juicio.", "Fue sensato esperar al supervisor antes de cortar la luz.", "Sensible", listOf("con sentido", "razonable")),
        Word("Idóneo", "Perfecto o adecuado para algo.", "Este es el lugar idóneo para instalar la cámara.", "Ideal", listOf("ideal", "perfecto para")),
        Word("Escueto", "Breve, con pocas palabras.", "Su respuesta fue escueta pero clara.", "Brief", listOf("corto", "cortito")),
        Word("Jovial", "Alegre y de buen humor.", "Llegó jovial a pesar de trabajar desde temprano.", "Cheerful", listOf("alegre", "contento")),
        Word("Atinado", "Acertado, que da en el punto.", "Fue un comentario muy atinado.", "Spot-on", listOf("acertado", "buen punto")),
        Word("Desenvuelto", "Que se maneja con soltura y confianza.", "Estuvo muy desenvuelto al hablar con el cliente.", "Self-assured", listOf("suelto", "con confianza")),
        Word("Sereno", "Tranquilo, sin alterarse.", "Se quedó sereno cuando falló el sistema.", "Composed", listOf("tranquilo")),
        Word("Contundente", "Claro y firme, que convence.", "Le di una respuesta contundente sobre el presupuesto.", "Compelling", listOf("firme")),
        Word("Propicio", "Favorable, el momento justo.", "Es un momento propicio para pedir el material.", "Favorable", listOf("buen momento")),
        Word("Tenaz", "Que no se rinde fácilmente.", "Fue tenaz hasta encontrar la falla en la fibra.", "Tenacious", listOf("terco", "persistente")),
        Word("Versátil", "Que sabe hacer muchas cosas distintas.", "Es un técnico versátil: cámaras, fibra y control de acceso.", "Versatile", listOf("todoterreno")),
        Word("Apacible", "Tranquilo y agradable.", "Fue una tarde apacible después de un día pesado.", "Peaceful", listOf("tranquilo", "calmado")),
        Word("Ingenioso", "Que encuentra soluciones creativas.", "Fue ingenioso cómo pasó el cable sin abrir el techo.", "Resourceful", listOf("creativo", "listo")),
        Word("Solidario", "Que ayuda y apoya a los demás.", "Mi compañero fue solidario y se quedó a ayudarme.", "Supportive", listOf("buena gente")),
        Word("Oportuno", "Que llega en el momento justo.", "Tu llamada fue muy oportuna.", "Timely", listOf("a tiempo", "justo a tiempo")),
        Word("Coherente", "Que tiene lógica y orden.", "Su reporte fue claro y coherente.", "Coherent", listOf("que tiene sentido", "lógico")),
        Word("Grato", "Agradable, que da gusto.", "Fue grato trabajar con su equipo.", "Pleasant", listOf("agradable", "bonito")),
        Word("Prudente", "Que piensa antes de actuar para evitar problemas.", "Fue prudente avisar antes de desconectar el panel.", "Prudent", listOf("cuidadoso")),
        Word("Genuino", "Auténtico, sincero.", "Su agradecimiento fue genuino.", "Genuine", listOf("sincero", "de verdad")),
        Word("Entusiasta", "Que muestra muchas ganas y alegría.", "El equipo nuevo está entusiasta con el proyecto.", "Enthusiastic", listOf("con ganas", "emocionado")),
        Word("Minucioso", "Que cuida cada detalle.", "Hice una revisión minuciosa de todas las puertas.", "Thorough", listOf("detallado", "detallista")),
        Word("Resiliente", "Que se recupera rápido de los problemas.", "Fuimos resilientes después del cambio de planes.", "Resilient", listOf("fuerte", "aguantador")),
        Word("Locuaz", "Que habla mucho y con facilidad.", "El cliente era muy locuaz y nos contó todo.", "Talkative", listOf("platicador", "hablador")),
        Word("Sutil", "Delicado, que se nota poco.", "Le hice una sugerencia sutil.", "Subtle", listOf("discreto")),
        Word("Ameno", "Agradable y entretenido.", "La plática fue muy amena.", "Enjoyable", listOf("entretenido")),
        Word("Conciso", "Que dice mucho en pocas palabras.", "Sé conciso en el reporte: qué hiciste y cómo quedó.", "Concise", listOf("al grano")),
    )

    /** El primer lunes del plan (2 de noviembre de 2026). */
    private val START: Long = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 2, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    fun weekStart(): Long = START

    fun weekIndex(now: Long = System.currentTimeMillis()): Int =
        maxOf(0, ((now - START) / (7L * 24 * 3600 * 1000)).toInt())

    fun current(now: Long = System.currentTimeMillis()): Word = WORDS[weekIndex(now) % WORDS.size]

    /** Si lo último que escribiste es algo que esta palabra puede reemplazar, la sugiere. */
    fun match(before: CharSequence): FormalWords.Match? {
        val w = current()
        val text = before.toString()
        val trailing = if (text.endsWith(" ")) 1 else 0
        val core = text.substring(0, text.length - trailing)
        val norm = EmojiSearch.norm(core)
        for (r in w.replaces) {
            val k = EmojiSearch.norm(r)
            if (norm.endsWith(k) && (norm.length == k.length || !norm[norm.length - k.length - 1].isLetter())) {
                val out = w.word.lowercase() + if (trailing > 0) " " else ""
                return FormalWords.Match(k.length + trailing, out)
            }
        }
        return null
    }
}
