package com.luisell.bestkeyboard

/**
 * Palabras y frases sencillas → su forma más formal (sin internet), en español e inglés.
 * Se sugieren mientras escribes: "arreglé" → "reparé", "ya sirve" → "funciona correctamente".
 */
object FormalWords {

    class Match(val deleteLen: Int, val replacement: String)

    private val MAP: Map<String, String> = buildMap {
        fun add(formal: String, vararg informal: String) { for (w in informal) put(EmojiSearch.norm(w), formal) }
        // ---- Español
        add("reparé", "arregle")
        add("reparar", "arreglar")
        add("reparado", "arreglado")
        add("verificar", "checar", "chequear")
        add("verifiqué", "cheque", "chequee", "chequé")
        add("funciona correctamente", "ya sirve", "ya jala", "ya funciona", "ya quedo", "ya quedó")
        add("no funciona", "no sirve", "no jala")
        add("reemplacé", "cambie")
        add("instalé", "puse", "meti", "metí")
        add("retiré", "quite")
        add("instalé el cableado", "jale el cable", "jalé el cable", "tire el cable", "tiré el cable")
        add("encendí", "prendi")
        add("en este momento", "ahorita")
        add("de inmediato", "luego luego")
        add("más tarde", "al rato")
        add("envié", "mande")
        add("notifiqué", "avise")
        add("conversé", "platique")
        add("trabajo", "chamba")
        add("supervisor", "jefe")
        add("para", "pa")
        add("porque", "pq", "xq", "porq")
        add("también", "tmb", "tb", "tmbn")
        add("por favor", "porfa", "xfa", "pls")
        add("solamente", "nomas", "nomás", "nada mas", "nada más")
        add("presentó una falla", "se descompuso")
        add("está dañado", "esta roto", "está roto")
        add("dañado", "roto", "descompuesto")
        add("de acuerdo", "okey", "oki")
        add("quedo atento", "cualquier cosa me avisan", "cualquier cosa me dicen")
        add("buen día", "buenas")
        add("muchas gracias", "grax", "graciass")
        add("aproximadamente", "como unos", "como unas")
        add("realicé pruebas", "hice pruebas")
        add("se programó", "se programo")
        add("coordiné con", "hable con", "hablé con")
        // ---- English
        add("repaired", "fixed")
        add("repair", "fix")
        add("verified", "checked")
        add("verify", "check")
        add("is now working properly", "works now", "working now", "good now")
        add("is not working", "doesnt work", "doesn't work", "not working")
        add("failed", "broke")
        add("damaged", "broken")
        add("installed", "put in")
        add("replaced", "swapped")
        add("replace", "swap")
        add("as soon as possible", "asap")
        add("going to", "gonna")
        add("want to", "wanna")
        add("need to", "gotta")
        add("thank you", "thx", "thanks", "ty")
        add("team", "guys")
        add("equipment", "stuff")
        add("informed", "told")
        add("spoke with", "talked to")
        add("your", "ur")
        add("please", "plz", "pls")
        add("because", "cuz", "bc", "coz")
        add("before", "b4")
        add("somewhat", "kinda")
        add("tested", "tried it")
        add("ran the cable", "pulled the cable")
        add("please let me know if you need anything else", "let me know", "lmk")
    }

    private val MAX_WORDS = MAP.keys.maxOf { it.split(' ').size }

    /**
     * Busca una forma más formal para lo último que escribiste ([before] = el texto antes del cursor).
     * Sirve justo después de la palabra o con un espacio después.
     */
    fun match(before: CharSequence): Match? {
        val text = before.toString()
        val trailing = when {
            text.endsWith(" ") && !text.endsWith("  ") -> 1
            else -> 0
        }
        val core = text.substring(0, text.length - trailing)
        if (core.isEmpty() || !TextUtilLite.isWordChar(core.last())) return null
        // Inicio de cada una de las últimas palabras
        val starts = ArrayList<Int>()
        var i = core.length
        while (starts.size < MAX_WORDS && i > 0) {
            while (i > 0 && core[i - 1] == ' ') i--
            if (i == 0 || !TextUtilLite.isWordChar(core[i - 1])) break
            var j = i
            while (j > 0 && TextUtilLite.isWordChar(core[j - 1])) j--
            starts.add(j)
            i = j
            if (i > 0 && core[i - 1] != ' ') break
        }
        for (n in starts.size downTo 1) {
            val start = starts[n - 1]
            val piece = core.substring(start)
            val key = EmojiSearch.norm(piece).replace(EmojiSearch.SPACES, " ")
            val formal = MAP[key] ?: continue
            if (EmojiSearch.norm(formal) == key) continue
            val out = if (piece[0].isUpperCase()) formal.replaceFirstChar { it.uppercaseChar() } else formal
            return Match(piece.length + trailing, out + if (trailing > 0) " " else "")
        }
        return null
    }

    private object TextUtilLite {
        fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'
    }
}
