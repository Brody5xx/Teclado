package com.luisell.bestkeyboard

/**
 * "Modo código": lenguajes, palabras para completar y teclas especiales.
 * En los fragmentos, "¦" marca dónde queda el cursor (ej. "print(¦)" deja el cursor entre los paréntesis).
 */
object CodeWords {
    const val CURSOR = '¦'

    val LANGS = listOf("Python", "JavaScript", "Kotlin", "Java", "HTML", "C++")

    private val PY = listOf(
        "print(¦)", "input(¦)", "len(¦)", "range(¦)", "int(¦)", "str(¦)", "float(¦)", "list(¦)", "dict(¦)",
        "def ¦():", "for i in range(¦):", "for ¦ in :", "while ¦:", "if ¦:", "elif ¦:", "else:", "return ¦",
        "import ¦", "from ¦ import ", "class ¦:", "try:", "except ¦:", "finally:", "with ¦ as f:",
        "True", "False", "None", "and", "or", "not", "in", "is", "lambda ¦:", "break", "continue", "pass",
        "append(¦)", "split(¦)", "join(¦)", "lower()", "upper()", "strip()", "format(¦)", "keys()", "values()",
        "items()", "open(¦)", "self", "def __init__(self¦):", "sorted(¦)", "sum(¦)", "max(¦)", "min(¦)", "abs(¦)",
        "round(¦)", "type(¦)", "isinstance(¦)", "enumerate(¦)", "zip(¦)", "random", "randint(¦)", "global ",
    )
    private val JS = listOf(
        "console.log(¦);", "function ¦() {\n}", "const ", "let ", "var ", "if (¦) {\n}", "else {\n¦\n}",
        "for (let i = 0; i < ¦; i++) {\n}", "for (const ¦ of ) {\n}", "while (¦) {\n}", "return ¦;",
        "document.getElementById(\"¦\")", "document.querySelector(\"¦\")", "addEventListener(\"¦\", () => {\n})",
        "true", "false", "null", "undefined", "async ", "await ", "() => {¦}", "length", "push(¦)", "map(¦)",
        "filter(¦)", "forEach(¦)", "JSON.stringify(¦)", "JSON.parse(¦)", "fetch(¦)", "alert(¦)", "class ¦ {\n}",
        "import ¦ from \"\";", "export ", "try {\n¦\n} catch (e) {\n}", "setTimeout(() => {¦}, 1000);",
    )
    private val KT = listOf(
        "println(¦)", "print(¦)", "fun ¦() {\n}", "val ", "var ", "if (¦) {\n}", "else {\n¦\n}", "when (¦) {\n}",
        "for (¦ in ) {\n}", "for (i in 0 until ¦) {\n}", "while (¦) {\n}", "return ¦", "class ¦ {\n}",
        "data class ¦()", "listOf(¦)", "mutableListOf(¦)", "mapOf(¦)", "setOf(¦)", "null", "true", "false",
        "String", "Int", "Boolean", "Double", "List<¦>", "override fun ", "private ", "import ", "readLine()",
        "toInt()", "toString()", "length", "size", "forEach { ¦ }", "map { ¦ }", "filter { ¦ }", "it", "this",
    )
    private val JAVA = listOf(
        "System.out.println(¦);", "public ", "private ", "static ", "void ", "int ", "String ", "boolean ",
        "double ", "class ¦ {\n}", "public static void main(String[] args) {\n¦\n}", "if (¦) {\n}",
        "else {\n¦\n}", "for (int i = 0; i < ¦; i++) {\n}", "while (¦) {\n}", "return ¦;", "new ", "null",
        "true", "false", "import ", "Scanner sc = new Scanner(System.in);", "ArrayList<¦>", "length()",
        "size()", "equals(¦)", "try {\n¦\n} catch (Exception e) {\n}", "this", "extends ", "implements ",
    )
    private val HTML = listOf(
        "<div>¦</div>", "<p>¦</p>", "<h1>¦</h1>", "<h2>¦</h2>", "<a href=\"¦\"></a>", "<img src=\"¦\" alt=\"\">",
        "<button>¦</button>", "<input type=\"¦\">", "<span>¦</span>", "<ul>\n<li>¦</li>\n</ul>", "<li>¦</li>",
        "<script>\n¦\n</script>", "<style>\n¦\n</style>", "class=\"¦\"", "id=\"¦\"", "style=\"¦\"",
        "<!DOCTYPE html>", "<html>\n¦\n</html>", "<head>\n¦\n</head>", "<body>\n¦\n</body>", "<title>¦</title>",
        "<form>\n¦\n</form>", "<br>", "<table>\n¦\n</table>", "<meta charset=\"UTF-8\">",
    )
    private val CPP = listOf(
        "#include <¦>", "#include <iostream>", "using namespace std;", "int main() {\n¦\n    return 0;\n}",
        "std::cout << ¦ << std::endl;", "cout << ¦ << endl;", "cin >> ¦;", "int ", "double ", "string ",
        "bool ", "void ", "if (¦) {\n}", "else {\n¦\n}", "for (int i = 0; i < ¦; i++) {\n}", "while (¦) {\n}",
        "return ¦;", "class ¦ {\n};", "vector<¦>", "true", "false", "nullptr", "const ", "auto ",
    )

    private fun words(lang: String) = when (lang) {
        "JavaScript" -> JS
        "Kotlin" -> KT
        "Java" -> JAVA
        "HTML" -> HTML
        "C++" -> CPP
        else -> PY
    }

    /** Lo que se ve en la barra (sin la marca del cursor y en una sola línea). */
    fun label(snippet: String): String {
        val one = snippet.replace(CURSOR.toString(), "").replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        return if (one.length > 22) one.take(21) + "…" else one
    }

    /** Hasta 3 fragmentos que empiezan con lo que escribiste ([typed] = la palabra antes del cursor). */
    fun complete(lang: String, typed: String): List<String> {
        if (typed.isEmpty()) return emptyList()
        val t = typed.lowercase()
        val all = words(lang)
        val starts = all.filter { w ->
            val key = w.trimStart('<', '#').lowercase()
            key.startsWith(t) && label(w).length > typed.length
        }
        return starts.take(3)
    }

    /** ¿La línea abre un bloque? (Python ":" / los demás "{") — la siguiente línea va con sangría. */
    fun opensBlock(lang: String, line: String): Boolean {
        val t = line.trimEnd()
        if (t.isEmpty()) return false
        return if (lang == "Python") t.endsWith(":") else t.endsWith("{") || t.endsWith("(") || t.endsWith("[") ||
            (lang == "HTML" && Regex("<([a-zA-Z][a-zA-Z0-9]*)[^>/]*>$").containsMatchIn(t) && !t.endsWith("/>"))
    }
}
