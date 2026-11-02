package com.luisell.bestkeyboard

import java.util.Calendar

/**
 * Palabra de la semana: cada lunes una palabra nueva para hablar y escribir con más fluidez.
 * Trae su significado, un ejemplo y palabras sencillas que puede reemplazar (el teclado la sugiere ahí).
 */
object WeeklyWord {

    class Word(val word: String, val meaning: String, val example: String, val en: String, val replaces: List<String>) {
        /** En inglés: significado, ejemplo y palabras sencillas que puede reemplazar. */
        val enMeaning get() = EN[word]?.first ?: meaning
        val enExample get() = EN[word]?.second ?: example
        val enReplaces get() = EN[word]?.third ?: emptyList()

        /** Todo en el idioma del teclado. */
        fun title() = if (L.english) "$en  ($word)" else "$word  ($en)"
        fun meaningNow() = if (L.english) enMeaning else meaning
        fun exampleNow() = if (L.english) enExample else example
        fun typed() = (if (L.english) en else word).lowercase()
    }

    private val EN: Map<String, Triple<String, String, List<String>>> = mapOf(
        "Afable" to Triple("Friendly and easy to talk to.", "The new neighbor was very affable with everyone in the building.", listOf("friendly", "nice")),
        "Asertivo" to Triple("Says what they think clearly and respectfully.", "I was assertive and explained that we were short on material.", listOf("direct")),
        "Perspicaz" to Triple("Notices details others miss.", "It was perceptive to notice the cable was mislabeled.", listOf("smart", "observant")),
        "Cordial" to Triple("Kind and respectful, polite.", "The meeting was cordial and we reached an agreement.", listOf("polite", "friendly")),
        "Pertinente" to Triple("To the point; fitting.", "He asked a very relevant question about the permits.", listOf("important", "on point")),
        "Ecuánime" to Triple("Stays calm and fair.", "Even with problems, she stayed even-tempered.", listOf("calm")),
        "Elocuente" to Triple("Speaks clearly and convincingly.", "His explanation was so eloquent that everyone understood.", listOf("well-spoken")),
        "Ocurrente" to Triple("Has funny or clever ideas.", "My coworker is very witty, he always makes us laugh.", listOf("funny")),
        "Diligente" to Triple("Does the work carefully and quickly.", "He was diligent and finished the installation early.", listOf("hardworking", "hard-working")),
        "Sensato" to Triple("Thinks and acts with good judgment.", "It was sensible to wait for the supervisor before cutting the power.", listOf("reasonable")),
        "Idóneo" to Triple("Perfect or just right for something.", "This is the ideal spot to install the camera.", listOf("perfect for", "best")),
        "Escueto" to Triple("Short, with few words.", "His answer was brief but clear.", listOf("short")),
        "Jovial" to Triple("Cheerful and in a good mood.", "He showed up cheerful even after starting early.", listOf("happy", "glad")),
        "Atinado" to Triple("Right on target.", "That was a spot-on comment.", listOf("good point", "right")),
        "Desenvuelto" to Triple("Handles things with ease and confidence.", "He was very self-assured talking to the client.", listOf("confident")),
        "Sereno" to Triple("Calm, not upset.", "She stayed composed when the system went down.", listOf("calm")),
        "Contundente" to Triple("Clear and firm, convincing.", "I gave a compelling answer about the budget.", listOf("firm", "strong")),
        "Propicio" to Triple("Favorable, the right moment.", "It's a favorable moment to ask for the material.", listOf("good time", "good moment")),
        "Tenaz" to Triple("Doesn't give up easily.", "He was tenacious until he found the fiber fault.", listOf("stubborn", "persistent")),
        "Versátil" to Triple("Can do many different things.", "She's a versatile tech: cameras, fiber and access control.", listOf("all-around")),
        "Apacible" to Triple("Calm and pleasant.", "It was a peaceful afternoon after a long day.", listOf("quiet", "calm")),
        "Ingenioso" to Triple("Finds creative solutions.", "It was resourceful how he ran the cable without opening the ceiling.", listOf("creative", "clever")),
        "Solidario" to Triple("Helps and supports others.", "My coworker was supportive and stayed to help me.", listOf("helpful")),
        "Oportuno" to Triple("Comes at just the right time.", "Your call was very timely.", listOf("on time", "just in time")),
        "Coherente" to Triple("Logical and well ordered.", "His report was clear and coherent.", listOf("makes sense", "logical")),
        "Grato" to Triple("Pleasant, a pleasure.", "It was pleasant to work with your team.", listOf("nice", "good")),
        "Prudente" to Triple("Thinks before acting to avoid problems.", "It was prudent to warn everyone before disconnecting the panel.", listOf("careful")),
        "Genuino" to Triple("Real, sincere.", "His thanks were genuine.", listOf("real", "sincere")),
        "Entusiasta" to Triple("Shows a lot of energy and joy.", "The new team is enthusiastic about the project.", listOf("excited", "eager")),
        "Minucioso" to Triple("Takes care of every detail.", "I did a thorough check of all the doors.", listOf("detailed")),
        "Resiliente" to Triple("Recovers quickly from problems.", "We were resilient after the change of plans.", listOf("tough", "strong")),
        "Locuaz" to Triple("Talks a lot and easily.", "The client was very talkative and told us everything.", listOf("chatty")),
        "Sutil" to Triple("Delicate, barely noticeable.", "I made a subtle suggestion.", listOf("discreet")),
        "Ameno" to Triple("Pleasant and entertaining.", "The talk was very enjoyable.", listOf("fun", "entertaining")),
        "Conciso" to Triple("Says a lot in few words.", "Be concise in the report: what you did and how it ended up.", listOf("to the point"))
    )

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
        // Escribes en español → la palabra en español; en inglés → la palabra en inglés
        for ((r, word) in w.replaces.map { it to w.word } + w.enReplaces.map { it to w.en }) {
            val k = EmojiSearch.norm(r)
            if (norm.endsWith(k) && (norm.length == k.length || !norm[norm.length - k.length - 1].isLetter())) {
                val out = word.lowercase() + if (trailing > 0) " " else ""
                return FormalWords.Match(k.length + trailing, out)
            }
        }
        return null
    }
}
