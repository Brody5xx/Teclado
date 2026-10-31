package com.luisell.bestkeyboard

import java.text.Normalizer

/**
 * Buscar emojis por nombre (en español o inglés) y sugerir un emoji según lo que escribes:
 * "casa" → 🏠, "jajaja" → 😂, "te quiero" → ❤️, "estoy cansado" → 😴.
 */
object EmojiSearch {

    /** Palabras y frases de emociones (van primero que el diccionario de emojis). */
    private val FEELINGS: Map<String, String> = buildMap {
        fun add(emoji: String, vararg words: String) { for (w in words) put(w, emoji) }
        add("😂", "jaja", "jajaja", "jajajaja", "jeje", "jejeje", "haha", "hahaha", "lol", "lmao", "me muero de risa", "que risa")
        add("😆", "xd", "jsjs", "jsjsjs")
        add("❤️", "te quiero", "te amo", "love you", "i love you", "mi amor", "amor", "love", "te quiero mucho")
        add("😍", "hermoso", "hermosa", "precioso", "preciosa", "beautiful", "lindo", "linda", "guapa", "guapo")
        add("😊", "feliz", "contento", "contenta", "alegre", "happy", "glad", "que bueno", "me alegro")
        add("😢", "triste", "sad", "llorar", "lloro", "llorando", "crying", "que pena", "me duele")
        add("😠", "enojado", "enojada", "molesto", "molesta", "angry", "mad", "furioso", "furiosa", "enfadado")
        add("😴", "cansado", "cansada", "sueno", "tired", "sleepy", "dormir", "a dormir", "sleep")
        add("🙏", "gracias", "thanks", "thank you", "muchas gracias", "thank you so much", "por favor", "please", "dios")
        add("👌", "ok", "okay", "vale", "listo", "perfecto", "perfect", "de acuerdo", "dale")
        add("👍", "si", "yes", "claro", "of course", "bien", "good", "excelente", "great", "sure")
        add("☀️", "buenos dias", "good morning", "buen dia", "sol", "sunny")
        add("🌙", "buenas noches", "good night", "goodnight", "noche")
        add("🎂", "cumpleanos", "feliz cumpleanos", "birthday", "happy birthday", "cumple")
        add("🎉", "felicidades", "felicitaciones", "congrats", "congratulations", "fiesta", "party", "celebrar")
        add("🤤", "hambre", "hungry", "tengo hambre", "delicioso", "delicious", "rico", "yummy")
        add("😘", "beso", "besos", "kiss", "kisses", "besitos")
        add("🤗", "abrazo", "abrazos", "hug", "hugs")
        add("😔", "lo siento", "sorry", "perdon", "disculpa", "perdoname", "i'm sorry", "im sorry")
        add("😱", "miedo", "scared", "afraid", "que miedo", "terror", "omg", "dios mio")
        add("😮", "wow", "increible", "amazing", "no puede ser", "en serio", "really")
        add("🔥", "fuego", "fire", "lit", "brutal", "genial")
        add("🥶", "frio", "cold", "que frio", "freezing")
        add("🥵", "calor", "hot", "que calor")
        add("🤒", "enfermo", "enferma", "sick", "fiebre", "fever")
        add("🤔", "pensando", "hmm", "mmm", "thinking", "no se", "quizas", "maybe", "tal vez")
        add("💪", "fuerza", "strong", "tu puedes", "you got this", "animo", "gym", "vamos")
        add("💼", "trabajo", "work", "job", "oficina", "office", "chamba")
        add("💰", "dinero", "money", "pago", "pay", "cash", "plata", "sueldo")
        add("🥳", "vacaciones", "vacation", "finally", "por fin", "yay")
        add("😎", "cool", "chido", "chevere", "tranquilo")
        add("🙄", "aja", "whatever", "como sea", "ugh")
        add("😅", "oops", "ups", "uy", "jeje perdon")
        add("👋", "hola", "hello", "hi", "hey", "adios", "bye", "chao", "nos vemos", "see you")
    }

    private var index: HashMap<String, MutableList<String>>? = null
    private var mainName: HashMap<String, String>? = null

    fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()

    private fun buildIndex(): Boolean {
        if (index != null) return true
        val cats = EmojiData.categories ?: return false
        val idx = HashMap<String, MutableList<String>>(8000)
        val main = HashMap<String, String>(4000)
        for (c in cats) for (e in c) {
            for (list in listOf(e.es, e.en)) {
                for ((i, k) in list.withIndex()) {
                    idx.getOrPut(k) { ArrayList(2) }.add(e.text)
                    if (i == 0 && k !in main) main[k] = e.text
                }
            }
        }
        index = idx
        mainName = main
        return true
    }

    /** Busca emojis por palabras (en español o inglés). */
    fun search(query: String, limit: Int = 80): List<String> {
        val cats = EmojiData.categories ?: return emptyList()
        val q = norm(query)
        if (q.isEmpty()) return emptyList()
        val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val scored = ArrayList<Pair<Int, String>>()
        var order = 0
        for (c in cats) for (e in c) {
            order++
            val kws = e.es + e.en
            if (kws.isEmpty()) continue
            var total = 0
            var ok = true
            for (w in words) {
                var best = 0
                for ((i, k) in kws.withIndex()) {
                    val first = i == 0 || i == e.es.size
                    val sc = when {
                        k == q && first -> 100
                        k == w -> if (first) 90 else 75
                        k.startsWith(w) -> if (first) 60 else 45
                        k.split(' ').any { it.startsWith(w) } -> 30
                        else -> 0
                    }
                    if (sc > best) best = sc
                }
                if (best == 0) { ok = false; break }
                total += best
            }
            if (ok) scored.add(Pair(total * 10000 - order, e.text))
        }
        FEELINGS[q]?.let { f -> scored.add(Pair(Int.MAX_VALUE, f)) }
        return scored.sortedByDescending { it.first }.map { it.second }.distinct().take(limit)
    }

    /**
     * Un emoji para lo que acabas de escribir (o null). [before] = el texto antes del cursor.
     * Mira primero frases de 3 y 2 palabras ("te quiero mucho"), luego la última palabra.
     */
    fun suggest(before: CharSequence): String? {
        if (!buildIndex()) return null
        val words = norm(before.takeLast(60).toString())
            .split(Regex("[^a-z0-9ñ']+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        for (n in 3 downTo 2) {
            if (words.size >= n) FEELINGS[words.takeLast(n).joinToString(" ")]?.let { return it }
        }
        val w = words.last()
        if (w.length < 2) return null
        // Risas largas: "jajajajaja", "jejeje", "jsjsjs"
        if (Regex("^(j+[aeiou]+){2,}j*$").matches(w) || Regex("^(js)+j?$").matches(w)) return "😂"
        FEELINGS[w]?.let { return it }
        val candidates = listOfNotNull(w, w.removeSuffix("s").takeIf { it != w && it.length > 2 },
            w.removeSuffix("es").takeIf { it != w && it.length > 2 },
            if (w.endsWith("a")) w.dropLast(1) + "o" else null)
        val main = mainName ?: return null
        val idx = index ?: return null
        for (c in candidates) main[c]?.let { return it }
        // Palabras clave que apuntan a pocos emojis (las muy generales, como "cara", no sirven)
        for (c in candidates) {
            val l = idx[c] ?: continue
            if (c.length >= 3 && l.size in 1..6) return l[0]
        }
        return null
    }
}
