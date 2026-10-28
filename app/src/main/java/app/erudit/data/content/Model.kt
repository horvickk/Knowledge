package app.erudit.data.content

data class Domain(
    val id: String,
    val title: String,
    val icon: String,
    val color: Long,
    val description: String,
    val courses: List<Course>,
) {
    val lessons: List<Lesson> get() = courses.flatMap { it.lessons }
    val examId: String get() = "$id:final"
}

data class Course(
    val id: String,
    val domainId: String,
    val title: String,
    val description: String,
    val stages: List<Stage>,
) {
    val lessons: List<Lesson> get() = stages.flatMap { it.lessons }
    val examId: String get() = "$id:final"
}

data class Stage(
    val index: Int,
    val courseId: String,
    val title: String,
    val lessons: List<Lesson>,
) {
    val id: String get() = "$courseId:s$index"
    val examId: String get() = id
}

data class Lesson(
    val id: String,
    val courseId: String,
    val domainId: String,
    val stageIndex: Int,
    val title: String,
    val minutes: Int,
    val body: String,
    val questions: List<Question>,
)

data class Question(
    val id: String,
    val lessonId: String,
    val text: String,
    val options: List<String>,
    val correct: Int,
    val explanation: String?,
    /** Вопрос-выбор («?? » в контенте): без вариантов он не имеет смысла, и они всегда показываются. */
    val choice: Boolean = false,
    /**
     * Экзаменационные варианты задачи («?! » в контенте): та же суть на другом примере.
     * Экзамен задаёт один из них вместо вопроса урока, чтобы проверить понимание, а не память на ответ.
     */
    val examVariants: List<Question> = emptyList(),
) {
    /** Вопросы «верно/неверно» не перемешиваются, чтобы порядок ответов был привычным. */
    val keepOrder: Boolean
        get() = options.size == 2 && options.any { it.equals("Верно", true) } && options.any { it.equals("Неверно", true) }
}

/** Экзамен — проверка ступени, итог курса или большой экзамен области. */
data class Exam(
    val id: String,
    val kind: Kind,
    val title: String,
    val subtitle: String,
    val questionCount: Int,
    val secondsPerQuestion: Int,
    val passPercent: Int,
    val pool: List<Question>,
) {
    enum class Kind { STAGE, COURSE, DOMAIN }

    val timeLimitSec: Int get() = questionCount * secondsPerQuestion
}

class Content(
    val domains: List<Domain>,
    val logicPuzzles: List<Question>,
) {
    val domainsById: Map<String, Domain> = domains.associateBy { it.id }
    val coursesById: Map<String, Course> = domains.flatMap { it.courses }.associateBy { it.id }
    val lessons: List<Lesson> = domains.flatMap { it.lessons }
    val lessonsById: Map<String, Lesson> = lessons.associateBy { it.id }

    /** Вопросы уроков вместе с экзаменационными вариантами — всё, на что могут ссылаться карточки. */
    val lessonQuestions: List<Question> = lessons.flatMap { l -> l.questions.flatMap { listOf(it) + it.examVariants } }
    val questionsById: Map<String, Question> = (lessonQuestions + logicPuzzles).associateBy { it.id }
    val stagesById: Map<String, Stage> =
        domains.flatMap { d -> d.courses.flatMap { it.stages } }.associateBy { it.id }

    /** Короткие выдержки для «факта дня»: блоки `!>` и `>` из уроков. */
    val facts: List<Pair<Lesson, String>> by lazy {
        lessons.flatMap { lesson ->
            lesson.body.lineSequence()
                .filter { it.startsWith("!> ") }
                .map { lesson to it.removePrefix("!> ").trim() }
                .toList()
        }
    }

    fun domainOf(lesson: Lesson): Domain = domainsById.getValue(lesson.domainId)
    fun courseOf(lesson: Lesson): Course = coursesById.getValue(lesson.courseId)

    fun exam(id: String): Exam? {
        val (ownerId, suffix) = id.split(":").let { if (it.size == 2) it[0] to it[1] else return null }
        if (suffix.startsWith("s")) {
            val stage = stagesById[id] ?: return null
            val course = coursesById.getValue(stage.courseId)
            val pool = stage.lessons.flatMap { it.questions }
            return Exam(
                id = id,
                kind = Exam.Kind.STAGE,
                title = "Экзамен: ${stage.title.lowercase()}",
                subtitle = course.title,
                questionCount = minOf(15, pool.size),
                secondsPerQuestion = 40,
                passPercent = 80,
                pool = pool,
            )
        }
        coursesById[ownerId]?.let { course ->
            val pool = course.lessons.flatMap { it.questions }
            return Exam(
                id = id,
                kind = Exam.Kind.COURSE,
                title = "Итоговый экзамен",
                subtitle = course.title,
                questionCount = minOf(25, pool.size),
                secondsPerQuestion = 40,
                passPercent = 80,
                pool = pool,
            )
        }
        domainsById[ownerId]?.let { domain ->
            val pool = domain.lessons.flatMap { it.questions }
            return Exam(
                id = id,
                kind = Exam.Kind.DOMAIN,
                title = "Большой экзамен",
                subtitle = domain.title,
                questionCount = minOf(40, pool.size),
                secondsPerQuestion = 35,
                passPercent = 80,
                pool = pool,
            )
        }
        return null
    }

    /** Все экзамены области в порядке прохождения — для истории и статистики. */
    fun examsOf(domain: Domain): List<String> =
        domain.courses.flatMap { c -> c.stages.map { it.examId } + c.examId } + domain.examId
}
