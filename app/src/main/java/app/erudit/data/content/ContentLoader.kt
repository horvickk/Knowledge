package app.erudit.data.content

/** Источник файлов: assets на устройстве или папка на диске в тестах. */
interface ContentSource {
    fun list(path: String): List<String>
    fun read(path: String): String
}

object ContentLoader {
    const val ROOT = "content"
    private const val DOMAIN_FILE = "domain.txt"
    private const val LOGIC_FILE = "logic.txt"

    fun load(source: ContentSource): Content {
        val domainDirs = children(source, ROOT).filter { !it.contains('.') }.sorted()
        val domains = domainDirs.map { dir ->
            val domainId = ContentParser.keyOf(dir)
            val courses = children(source, "$ROOT/$dir")
                .filter { it.endsWith(".txt") && it != DOMAIN_FILE }
                .sorted()
                .map { file ->
                    ContentParser.parseCourse(domainId, ContentParser.keyOf(file), source.read("$ROOT/$dir/$file"))
                }
            ContentParser.parseDomain(domainId, source.read("$ROOT/$dir/$DOMAIN_FILE"), courses)
        }.filter { it.courses.isNotEmpty() }

        val logic = if (LOGIC_FILE in children(source, ROOT)) {
            ContentParser.parseQuestions("logic", source.read("$ROOT/$LOGIC_FILE").lines())
        } else {
            emptyList()
        }
        return Content(domains, logic)
    }

    /** Непосредственные потомки папки; некоторые реализации AssetManager отдают вложенные пути целиком. */
    private fun children(source: ContentSource, path: String): List<String> =
        source.list(path).map { it.substringBefore('/') }.distinct()
}
