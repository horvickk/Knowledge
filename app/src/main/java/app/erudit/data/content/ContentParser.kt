package app.erudit.data.content

/**
 * Разбирает текстовый формат курсов.
 *
 * Файл курса:
 * ```
 * title: Название курса
 * description: Короткое описание
 *
 * @stage Основы
 * @lesson lesson-id
 * title: Название урока
 * minutes: 7
 *
 * Текст урока в упрощённом markdown…
 *
 * ? Вопрос
 * + верный ответ
 * - неверный ответ
 * = пояснение
 * ```
 */
object ContentParser {

    class ParseException(message: String) : Exception(message)

    fun parseDomain(id: String, text: String, courses: List<Course>): Domain {
        val header = parseHeader(text.lines())
        return Domain(
            id = id,
            title = header["title"] ?: throw ParseException("domain $id: нет title"),
            icon = header["icon"] ?: "book",
            color = parseColor(header["color"] ?: "#6B6862"),
            description = header["description"] ?: "",
            courses = courses,
        )
    }

    fun parseCourse(domainId: String, courseKey: String, text: String): Course {
        val courseId = "$domainId.$courseKey"
        val lines = text.lines()
        val firstDirective = lines.indexOfFirst { it.startsWith("@") }.let { if (it < 0) lines.size else it }
        val header = parseHeader(lines.subList(0, firstDirective))

        val stages = mutableListOf<Stage>()
        var stageTitle: String? = null
        var stageLessons = mutableListOf<Lesson>()
        var i = firstDirective

        fun closeStage() {
            val title = stageTitle ?: return
            stages += Stage(stages.size + 1, courseId, title, stageLessons)
            stageLessons = mutableListOf()
        }

        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("@stage") -> {
                    closeStage()
                    stageTitle = line.removePrefix("@stage").trim()
                    i++
                }
                line.startsWith("@lesson") -> {
                    if (stageTitle == null) throw ParseException("$courseId: урок до @stage")
                    val key = line.removePrefix("@lesson").trim()
                    var end = i + 1
                    while (end < lines.size && !lines[end].startsWith("@stage") && !lines[end].startsWith("@lesson")) end++
                    stageLessons += parseLesson(courseId, domainId, stages.size + 1, key, lines.subList(i + 1, end))
                    i = end
                }
                line.isBlank() || line.startsWith("//") -> i++
                else -> throw ParseException("$courseId: неожиданная строка ${i + 1}: $line")
            }
        }
        closeStage()

        return Course(
            id = courseId,
            domainId = domainId,
            title = header["title"] ?: throw ParseException("$courseId: нет title"),
            description = header["description"] ?: "",
            stages = stages,
        )
    }

    private fun parseLesson(courseId: String, domainId: String, stageIndex: Int, key: String, lines: List<String>): Lesson {
        val lessonId = "$courseId.$key"
        var i = 0
        val meta = mutableMapOf<String, String>()
        while (i < lines.size && lines[i].isNotBlank()) {
            val (k, v) = splitKeyValue(lines[i]) ?: throw ParseException("$lessonId: плохой заголовок '${lines[i]}'")
            meta[k] = v
            i++
        }
        val quizStart = (i until lines.size).firstOrNull { lines[it].startsWith("? ") } ?: lines.size
        val body = lines.subList(i, quizStart).joinToString("\n").trim()
        val questions = parseQuestions(lessonId, lines.subList(quizStart, lines.size))
        return Lesson(
            id = lessonId,
            courseId = courseId,
            domainId = domainId,
            stageIndex = stageIndex,
            title = meta["title"] ?: throw ParseException("$lessonId: нет title"),
            minutes = meta["minutes"]?.toIntOrNull() ?: estimateMinutes(body),
            body = body,
            questions = questions,
        )
    }

    fun parseQuestions(ownerId: String, lines: List<String>): List<Question> {
        val result = mutableListOf<Question>()
        var text: String? = null
        val options = mutableListOf<String>()
        var correct = -1
        var explanation: String? = null

        fun flush() {
            val q = text ?: return
            if (correct < 0) throw ParseException("$ownerId: нет верного ответа в «$q»")
            if (options.size < 2) throw ParseException("$ownerId: мало вариантов в «$q»")
            result += Question(
                id = ownerId + "#" + stableHash(q),
                lessonId = ownerId,
                text = q,
                options = options.toList(),
                correct = correct,
                explanation = explanation,
            )
            text = null
            options.clear()
            correct = -1
            explanation = null
        }

        for (raw in lines) {
            val line = raw.trimEnd()
            when {
                line.isBlank() || line.startsWith("//") -> Unit
                line.startsWith("? ") -> {
                    flush()
                    text = line.substring(2).trim()
                }
                line.startsWith("+ ") -> {
                    if (text == null) throw ParseException("$ownerId: ответ без вопроса '$line'")
                    if (correct >= 0) throw ParseException("$ownerId: два верных ответа в «$text»")
                    correct = options.size
                    options += line.substring(2).trim()
                }
                line.startsWith("- ") -> {
                    if (text == null) throw ParseException("$ownerId: ответ без вопроса '$line'")
                    options += line.substring(2).trim()
                }
                line.startsWith("= ") -> {
                    if (text == null) throw ParseException("$ownerId: пояснение без вопроса '$line'")
                    explanation = line.substring(2).trim()
                }
                else -> throw ParseException("$ownerId: неожиданная строка в вопросах '$line'")
            }
        }
        flush()
        return result
    }

    private fun parseHeader(lines: List<String>): Map<String, String> =
        lines.filter { it.isNotBlank() && !it.startsWith("//") }
            .mapNotNull { splitKeyValue(it) }
            .toMap()

    private fun splitKeyValue(line: String): Pair<String, String>? {
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        return line.substring(0, idx).trim() to line.substring(idx + 1).trim()
    }

    private fun parseColor(hex: String): Long = 0xFF000000 or hex.removePrefix("#").toLong(16)

    private fun estimateMinutes(body: String): Int =
        (body.split(Regex("\\s+")).size / 160).coerceAtLeast(2)

    /** Стабильный короткий идентификатор: не зависит от порядка вопросов в файле. */
    fun stableHash(s: String): String {
        var h = 1125899906842597L
        for (ch in s) h = 31 * h + ch.code
        return java.lang.Long.toString(h and 0x7FFFFFFFFFFFL, 36)
    }

    /** `01-math` → `math`. Числовой префикс задаёт порядок и не входит в id. */
    fun keyOf(fileName: String): String =
        fileName.substringBeforeLast('.').replace(Regex("^\\d+-"), "")
}
