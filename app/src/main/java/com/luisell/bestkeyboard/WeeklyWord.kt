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
        "Conciso" to Triple("Says a lot in few words.", "Be concise in the report: what you did and how it ended up.", listOf("to the point")),
        "Meticuloso" to Triple("Does everything with great care and order.", "He was meticulous labeling every cable in the rack.", listOf("careful", "detailed")),
        "Proactivo" to Triple("Acts ahead of problems without being asked.", "I was proactive and ordered the material before it ran out.", listOf("ready", "ahead")),
        "Empático" to Triple("Understands how others feel.", "He was empathetic with the upset customer.", listOf("understanding")),
        "Puntual" to Triple("Arrives exactly on time.", "She is always punctual for the morning meetings.", listOf("on time")),
        "Eficaz" to Triple("Achieves what it sets out to do.", "The fix was effective and the system was working again.", listOf("works", "good")),
        "Eficiente" to Triple("Does things well without wasting time or material.", "He was efficient: he finished using less cable than planned.", listOf("fast", "quick")),
        "Íntegro" to Triple("Honest and fair, does the right thing.", "He is an upright coworker who always tells the truth.", listOf("honest")),
        "Ameritar" to Triple("To deserve or need something.", "The failure warrants a full inspection.", listOf("need", "deserve")),
        "Exhaustivo" to Triple("Checks everything, leaving nothing out.", "We did an exhaustive test of the cameras.", listOf("complete", "full")),
        "Imprescindible" to Triple("Something you can't do without.", "The cable tester is essential in the truck.", listOf("necessary", "needed")),
        "Factible" to Triple("Can be done.", "It's feasible to finish today if the material arrives.", listOf("possible", "doable")),
        "Viable" to Triple("Has a good chance of working.", "The route through the ceiling is the most viable option.", listOf("workable", "possible")),
        "Óptimo" to Triple("The best possible.", "The camera ended up at the optimal angle.", listOf("best", "perfect")),
        "Fluido" to Triple("Flows without obstacles, continuously.", "Communication with the team was smooth.", listOf("easy", "fine")),
        "Constructivo" to Triple("Helps to improve.", "I gave him constructive feedback on the report.", listOf("helpful")),
        "Receptivo" to Triple("Listens to and accepts new ideas.", "The supervisor was receptive to my proposal.", listOf("open")),
        "Tajante" to Triple("Very firm, leaving no doubt.", "He was firm: nobody goes in without a hard hat.", listOf("clear", "strict")),
        "Sobrio" to Triple("Simple and elegant, not overdone.", "The client wanted an understated finish in the lobby.", listOf("simple", "plain")),
        "Sustancial" to Triple("Important, significant.", "There was a substantial improvement in the signal.", listOf("big", "major")),
        "Notable" to Triple("Noticeable and worth attention.", "The change was remarkable after the installation.", listOf("big", "noticeable")),
        "Fehaciente" to Triple("Proves something beyond doubt.", "We have conclusive proof that the panel works.", listOf("sure", "proof")),
        "Inminente" to Triple("About to happen very soon.", "The inspection is imminent, we need to finish today.", listOf("soon", "coming")),
        "Paulatino" to Triple("Happens little by little.", "The switch to the new system will be gradual.", listOf("slow", "little by little")),
        "Inherente" to Triple("Comes with something; part of it.", "Risk is inherent to working at heights.", listOf("part of", "built-in")),
        "Primordial" to Triple("The most important, the first thing.", "Safety is paramount at every site.", listOf("most important", "main")),
        "Gestionar" to Triple("To take charge of making something happen.", "I'll arrange the permit to enter the electrical room.", listOf("get", "handle")),
        "Agilizar" to Triple("To make something go faster.", "To expedite things, each of us takes one floor.", listOf("speed up", "rush")),
        "Corroborar" to Triple("To confirm something is true.", "I'll verify the IP address before configuring.", listOf("check", "confirm")),
        "Solventar" to Triple("To solve a problem.", "We managed to resolve the card reader failure.", listOf("fix", "solve")),
        "Optimizar" to Triple("To make something work as well as possible.", "I want to optimize the cable route to save material.", listOf("improve")),
        "Priorizar" to Triple("To decide what goes first.", "We need to prioritize the main entrance doors.", listOf("put first")),
        "Coordinar" to Triple("To organize several people to work together.", "I'll coordinate with the electrician for tomorrow.", listOf("organize", "plan")),
        "Delegar" to Triple("To hand a task to someone else.", "I'll delegate the labeling to my coworker.", listOf("hand off", "assign")),
        "Supervisar" to Triple("To watch that something is done right.", "I had to oversee the second-floor installation.", listOf("check", "watch")),
        "Documentar" to Triple("To record in writing or photos what was done.", "We need to document every change in the ticket.", listOf("write down", "note")),
        "Anticipar" to Triple("To get ahead of what will happen.", "I anticipated we'd be short on connectors and brought more.", listOf("expect", "foresee")),
        "Enfatizar" to Triple("To give special importance to something when saying it.", "I want to emphasize that the panel must stay closed.", listOf("stress", "highlight")),
        "Puntualizar" to Triple("To clarify an important detail.", "I want to clarify that the change was the client's request.", listOf("explain")),
        "Concretar" to Triple("To make something defined or done.", "We managed to finalize the installation date.", listOf("set", "close")),
        "Sobrellevar" to Triple("To bear a difficult situation.", "We knew how to cope with the material delay.", listOf("handle", "deal with")),
        "Afianzar" to Triple("To make something firmer or safer.", "We need to secure the camera mount.", listOf("tighten", "fix")),
        "Agradecido" to Triple("Thankful and appreciative.", "I'm grateful for today's help.", listOf("thankful")),
        "Comprometido" to Triple("Keeps their promises.", "He is a technician very committed to his work.", listOf("responsible", "dedicated")),
        "Confiable" to Triple("Someone you can trust.", "He's a reliable coworker, always finishes on time.", listOf("trustworthy", "dependable")),
        "Accesible" to Triple("Easy to talk to or reach.", "The supervisor is very approachable if you have questions.", listOf("easy to talk to", "friendly")),
        "Discreto" to Triple("Doesn't draw attention and keeps secrets.", "He was discreet with the client's information.", listOf("quiet", "private")),
        "Sagaz" to Triple("Quick to understand things.", "He was shrewd to find the fault in minutes.", listOf("smart", "sharp")),
        "Audaz" to Triple("Brave enough to try new things.", "It was a bold idea to run the cable through the elevator shaft.", listOf("brave", "daring")),
        "Cortés" to Triple("Polite and kind.", "He was courteous when asking the client to wait.", listOf("polite", "nice")),
        "Paciente" to Triple("Waits or explains without losing their cool.", "He was patient explaining the system to the guard.", listOf("calm")),
        "Perseverante" to Triple("Keeps trying until it works.", "He was persistent until the camera connected.", listOf("stubborn")),
        "Humilde" to Triple("Doesn't brag and accepts help.", "Despite his experience, he is very humble.", listOf("modest", "simple")),
        "Atento" to Triple("Pays attention and looks out for others.", "He was attentive to everything the client asked for.", listOf("alert")),
        "Servicial" to Triple("Always willing to help.", "The building manager was very helpful.", listOf("nice", "kind")),
        "Optimista" to Triple("Sees the good side of things.", "I'm optimistic: we'll finish the project tomorrow.", listOf("positive")),
        "Objetivo" to Triple("Based on facts, not opinions.", "My report is objective: only what I saw on site.", listOf("fair", "factual")),
        "Imparcial" to Triple("Doesn't favor anyone.", "The supervisor was impartial when splitting the work.", listOf("fair", "even")),
        "Explícito" to Triple("Said clearly, leaving no doubt.", "The instructions were explicit: don't touch the panel.", listOf("clear", "direct")),
        "Preciso" to Triple("Exact, without errors.", "I need the precise measurement of the hallway.", listOf("exact")),
        "Metódico" to Triple("Follows steps in order.", "He's methodical: first he tests, then he installs.", listOf("organized", "orderly")),
        "Flexible" to Triple("Adapts to changes.", "We need to be flexible with the client's schedule.", listOf("adaptable")),
        "Pragmático" to Triple("Looks for the most practical solution.", "He was pragmatic and used what he had in the truck.", listOf("practical")),
        "Riguroso" to Triple("Very strict and exact.", "We did a rigorous check of every connection.", listOf("strict", "careful")),
        "Fidedigno" to Triple("Believable, true.", "The information comes from a trustworthy source.", listOf("reliable")),
        "Congruente" to Triple("Matches what is said or done.", "What the report says is consistent with the photos.", listOf("matching", "adds up")),
        "Equitativo" to Triple("Shares fairly.", "We split the tasks in an equitable way.", listOf("fair", "even")),
        "Asequible" to Triple("Easy to pay for or get.", "We found a more affordable option for the client.", listOf("cheap", "budget")),
        "Colaborativo" to Triple("Works well as a team.", "It was a collaborative job between two teams.", listOf("teamwork", "together")),
        "Ágil" to Triple("Quick and smooth.", "The process was agile thanks to good planning.", listOf("fast", "quick")),
        "Conciliador" to Triple("Helps people come to an agreement.", "He was conciliatory when there was a disagreement on the team.", listOf("peacemaking", "calming")),
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
        // ---- Más palabras (alcanzan para 2 años, una cada lunes)
        Word("Meticuloso", "Que hace todo con mucho cuidado y orden.", "Fue meticuloso al etiquetar cada cable del rack.", "Meticulous", listOf("cuidadoso", "detallista")),
        Word("Proactivo", "Que se adelanta a los problemas sin esperar a que se lo pidan.", "Fui proactivo y pedí el material antes de que se acabara.", "Proactive", listOf("listo", "adelantado")),
        Word("Empático", "Que entiende cómo se sienten los demás.", "Fue empático con el cliente que estaba molesto.", "Empathetic", listOf("comprensivo")),
        Word("Puntual", "Que llega a la hora exacta.", "Siempre es puntual a las reuniones de la mañana.", "Punctual", listOf("a tiempo", "a la hora")),
        Word("Eficaz", "Que logra lo que se propone.", "La solución fue eficaz y el sistema quedó funcionando.", "Effective", listOf("que funciona", "bueno")),
        Word("Eficiente", "Que hace las cosas bien sin perder tiempo ni material.", "Fue eficiente: terminó con menos cable del calculado.", "Efficient", listOf("rápido", "sin perder tiempo")),
        Word("Íntegro", "Honesto y recto, que hace lo correcto.", "Es un compañero íntegro, siempre dice la verdad.", "Upright", listOf("honesto", "derecho")),
        Word("Ameritar", "Merecer o necesitar algo.", "La falla amerita una revisión completa.", "Warrant", listOf("necesitar", "merecer")),
        Word("Exhaustivo", "Que revisa todo, sin dejar nada.", "Hicimos una prueba exhaustiva de las cámaras.", "Exhaustive", listOf("completo", "de todo")),
        Word("Imprescindible", "Que no puede faltar.", "El probador de cable es imprescindible en la camioneta.", "Essential", listOf("necesario", "importante")),
        Word("Factible", "Que se puede hacer.", "Es factible terminar hoy si llega el material.", "Feasible", listOf("posible", "se puede")),
        Word("Viable", "Que tiene posibilidades de funcionar.", "La ruta por el techo es la opción más viable.", "Viable", listOf("posible", "que sirve")),
        Word("Óptimo", "Lo mejor posible.", "La cámara quedó en el ángulo óptimo.", "Optimal", listOf("el mejor", "perfecto")),
        Word("Fluido", "Que pasa sin trabas, de forma continua.", "La comunicación con el equipo fue fluida.", "Smooth", listOf("sin problemas", "bien")),
        Word("Constructivo", "Que ayuda a mejorar.", "Le di una crítica constructiva sobre el reporte.", "Constructive", listOf("que ayuda", "bueno")),
        Word("Receptivo", "Que escucha y acepta ideas nuevas.", "El supervisor estuvo receptivo a mi propuesta.", "Receptive", listOf("abierto")),
        Word("Tajante", "Muy firme, sin dejar dudas.", "Fue tajante: nadie entra sin casco.", "Firm", listOf("firme", "claro")),
        Word("Sobrio", "Sencillo y elegante, sin exagerar.", "El cliente quería un acabado sobrio en la recepción.", "Understated", listOf("sencillo", "simple")),
        Word("Sustancial", "Importante, de mucho peso.", "Hubo una mejora sustancial en la señal.", "Substantial", listOf("grande", "importante")),
        Word("Notable", "Que se nota y llama la atención.", "El cambio fue notable después de la instalación.", "Remarkable", listOf("grande", "que se nota")),
        Word("Fehaciente", "Que prueba algo sin dejar duda.", "Tenemos pruebas fehacientes de que el panel funciona.", "Conclusive", listOf("seguro", "que lo prueba")),
        Word("Inminente", "Que va a pasar muy pronto.", "La inspección es inminente, hay que terminar hoy.", "Imminent", listOf("ya viene", "pronto")),
        Word("Paulatino", "Que pasa poco a poco.", "El cambio al sistema nuevo será paulatino.", "Gradual", listOf("poco a poco", "despacio")),
        Word("Inherente", "Que viene con algo, que es parte de él.", "El riesgo es inherente al trabajo en altura.", "Inherent", listOf("parte de", "natural")),
        Word("Primordial", "Lo más importante, lo primero.", "La seguridad es primordial en cada sitio.", "Paramount", listOf("lo más importante", "principal")),
        Word("Gestionar", "Encargarse de hacer que algo pase.", "Voy a gestionar el permiso para entrar al cuarto eléctrico.", "Arrange", listOf("conseguir", "arreglar")),
        Word("Agilizar", "Hacer que algo vaya más rápido.", "Para agilizar, cada uno toma un piso.", "Expedite", listOf("apurar", "acelerar")),
        Word("Corroborar", "Confirmar que algo es cierto.", "Voy a corroborar la dirección IP antes de configurar.", "Verify", listOf("confirmar", "checar")),
        Word("Solventar", "Resolver un problema.", "Logramos solventar la falla del lector de tarjetas.", "Resolve", listOf("arreglar", "resolver")),
        Word("Optimizar", "Hacer que algo funcione lo mejor posible.", "Quiero optimizar la ruta del cable para ahorrar material.", "Optimize", listOf("mejorar")),
        Word("Priorizar", "Decidir qué va primero.", "Hay que priorizar las puertas de la entrada principal.", "Prioritize", listOf("poner primero")),
        Word("Coordinar", "Organizar a varias personas para trabajar juntas.", "Voy a coordinar con el electricista para mañana.", "Coordinate", listOf("organizar", "ponerse de acuerdo")),
        Word("Delegar", "Pasarle una tarea a otra persona.", "Voy a delegar el etiquetado a mi compañero.", "Delegate", listOf("pasar", "encargar")),
        Word("Supervisar", "Vigilar que algo se haga bien.", "Me tocó supervisar la instalación del segundo piso.", "Oversee", listOf("revisar", "checar")),
        Word("Documentar", "Dejar por escrito o con fotos lo que se hizo.", "Hay que documentar cada cambio en el ticket.", "Document", listOf("anotar", "apuntar")),
        Word("Anticipar", "Adelantarse a lo que va a pasar.", "Anticipé que faltarían conectores y traje más.", "Anticipate", listOf("adelantarse", "prever")),
        Word("Enfatizar", "Darle importancia especial a algo al decirlo.", "Quiero enfatizar que el panel debe quedar cerrado.", "Emphasize", listOf("recalcar", "subrayar")),
        Word("Puntualizar", "Aclarar un detalle importante.", "Quiero puntualizar que el cambio fue a pedido del cliente.", "Clarify", listOf("aclarar")),
        Word("Concretar", "Hacer que algo quede definido o se cumpla.", "Logramos concretar la fecha de la instalación.", "Finalize", listOf("cerrar", "definir")),
        Word("Sobrellevar", "Aguantar una situación difícil.", "Supimos sobrellevar el retraso del material.", "Cope with", listOf("aguantar", "soportar")),
        Word("Afianzar", "Hacer algo más firme o seguro.", "Hay que afianzar el soporte de la cámara.", "Secure", listOf("asegurar", "apretar")),
        Word("Agradecido", "Que da las gracias y lo valora.", "Estoy agradecido por la ayuda de hoy.", "Grateful", listOf("dar gracias", "contento")),
        Word("Comprometido", "Que cumple con lo que promete.", "Es un técnico muy comprometido con su trabajo.", "Committed", listOf("responsable", "cumplido")),
        Word("Confiable", "En quien se puede confiar.", "Es un compañero confiable, siempre termina a tiempo.", "Reliable", listOf("de confianza", "seguro")),
        Word("Accesible", "Fácil de tratar o de alcanzar.", "El supervisor es muy accesible si tienes dudas.", "Approachable", listOf("fácil de hablar", "buena onda")),
        Word("Discreto", "Que no llama la atención y guarda secretos.", "Fue discreto con la información del cliente.", "Discreet", listOf("callado", "reservado")),
        Word("Sagaz", "Listo para darse cuenta de las cosas rápido.", "Fue sagaz al encontrar la falla en minutos.", "Shrewd", listOf("listo", "vivo")),
        Word("Audaz", "Valiente para intentar cosas nuevas.", "Fue una idea audaz pasar el cable por el elevador.", "Bold", listOf("valiente", "atrevido")),
        Word("Cortés", "Educado y amable en el trato.", "Fue cortés al pedirle al cliente que esperara.", "Courteous", listOf("educado", "amable")),
        Word("Paciente", "Que espera o explica sin desesperarse.", "Fue paciente explicándole el sistema al guardia.", "Patient", listOf("tranquilo", "calmado")),
        Word("Perseverante", "Que sigue intentando hasta lograrlo.", "Fue perseverante hasta que la cámara conectó.", "Persistent", listOf("terco", "que no se rinde")),
        Word("Humilde", "Que no presume y acepta ayuda.", "A pesar de su experiencia, es muy humilde.", "Humble", listOf("sencillo", "modesto")),
        Word("Atento", "Que pone atención y está pendiente de los demás.", "Estuvo atento a todo lo que pidió el cliente.", "Attentive", listOf("pendiente", "al tanto")),
        Word("Servicial", "Que siempre está dispuesto a ayudar.", "El encargado del edificio fue muy servicial.", "Helpful", listOf("que ayuda", "buena gente")),
        Word("Optimista", "Que ve el lado bueno de las cosas.", "Soy optimista: mañana terminamos el proyecto.", "Optimistic", listOf("positivo")),
        Word("Objetivo", "Que se basa en hechos, no en opiniones.", "Mi reporte es objetivo: solo lo que vi en el sitio.", "Objective", listOf("justo", "imparcial")),
        Word("Imparcial", "Que no favorece a nadie.", "El supervisor fue imparcial al repartir el trabajo.", "Impartial", listOf("justo", "parejo")),
        Word("Explícito", "Dicho de forma clara, sin dejar dudas.", "Las instrucciones fueron explícitas: no tocar el panel.", "Explicit", listOf("claro", "directo")),
        Word("Preciso", "Exacto, sin errores.", "Necesito la medida precisa del pasillo.", "Precise", listOf("exacto", "justo")),
        Word("Metódico", "Que sigue pasos en orden.", "Es metódico: primero prueba, después instala.", "Methodical", listOf("ordenado", "organizado")),
        Word("Flexible", "Que se adapta a los cambios.", "Hay que ser flexibles con el horario del cliente.", "Flexible", listOf("que se adapta")),
        Word("Pragmático", "Que busca la solución más práctica.", "Fue pragmático y usó lo que tenía en la camioneta.", "Pragmatic", listOf("práctico")),
        Word("Riguroso", "Muy estricto y exacto.", "Hicimos un control riguroso de cada conexión.", "Rigorous", listOf("estricto", "exigente")),
        Word("Fidedigno", "Que se puede creer, que es verdad.", "La información viene de una fuente fidedigna.", "Trustworthy", listOf("confiable", "seguro")),
        Word("Congruente", "Que va de acuerdo con lo que dice o hace.", "Lo que dice el reporte es congruente con las fotos.", "Consistent", listOf("que cuadra", "que tiene sentido")),
        Word("Equitativo", "Que reparte de forma justa.", "Hicimos un reparto equitativo de las tareas.", "Equitable", listOf("justo", "parejo")),
        Word("Asequible", "Que se puede pagar o conseguir fácil.", "Encontramos una opción más asequible para el cliente.", "Affordable", listOf("barato", "económico")),
        Word("Colaborativo", "Que trabaja bien en equipo.", "Fue un trabajo colaborativo entre dos equipos.", "Collaborative", listOf("en equipo", "juntos")),
        Word("Ágil", "Rápido y con soltura.", "El proceso fue ágil gracias a la buena planeación.", "Agile", listOf("rápido", "veloz")),
        Word("Conciliador", "Que ayuda a que la gente se ponga de acuerdo.", "Fue conciliador cuando hubo un desacuerdo en el equipo.", "Conciliatory", listOf("que calma", "que pone paz")),
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
