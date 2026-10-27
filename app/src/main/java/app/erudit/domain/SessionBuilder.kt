package app.erudit.domain

import app.erudit.data.content.Content
import app.erudit.data.content.Exam
import app.erudit.data.content.Lesson
import app.erudit.data.content.Question
import app.erudit.data.db.Card
import kotlin.random.Random

/** Подбор вопросов для разных режимов. */
object SessionBuilder {
    /** Проверка после урока — все его вопросы в случайном порядке. */
    fun lessonCheck(lesson: Lesson, random: Random = Random.Default): List<Question> =
        lesson.questions.shuffled(random)

    /** Все уроки в области видимости: область, курс, ступень, урок или всё («all»). */
    fun lessonsInScope(content: Content, scope: String): List<Lesson> = when {
        scope == "all" -> content.lessons
        content.domainsById.containsKey(scope) -> content.domainsById.getValue(scope).lessons
        content.coursesById.containsKey(scope) -> content.coursesById.getValue(scope).lessons
        content.stagesById.containsKey(scope) -> content.stagesById.getValue(scope).lessons
        content.lessonsById.containsKey(scope) -> listOf(content.lessonsById.getValue(scope))
        else -> emptyList()
    }

    fun scopeTitle(content: Content, scope: String): String = when {
        scope == "all" -> "Всё изученное"
        content.domainsById.containsKey(scope) -> content.domainsById.getValue(scope).title
        content.coursesById.containsKey(scope) -> content.coursesById.getValue(scope).title
        content.stagesById.containsKey(scope) -> content.stagesById.getValue(scope).let {
            "${content.coursesById.getValue(it.courseId).title} · ${it.title}"
        }
        content.lessonsById.containsKey(scope) -> content.lessonsById.getValue(scope).title
        else -> "Тренировка"
    }

    /**
     * Тренировка: сначала вопросы, на которые чаще ошибались или которых ещё не видели,
     * с долей случайности, чтобы сессии не повторялись.
     */
    fun practice(pool: List<Question>, cards: Map<String, Card>, size: Int, random: Random = Random.Default): List<Question> =
        pool.map { q ->
            val c = cards[q.id]
            val priority = when {
                c == null -> 0.9
                else -> (c.wrong + 1.0) / (c.correct + c.wrong + 2.0) + if (!c.lastCorrect) 0.5 else 0.0
            }
            q to priority + random.nextDouble() * 0.6
        }
            .sortedByDescending { it.second }
            .take(size)
            .map { it.first }
            .shuffled(random)

    /** Экзамен: вопросы равномерно со всех уроков пула. */
    fun exam(exam: Exam, random: Random = Random.Default): List<Question> {
        val groups = exam.pool.groupBy { it.lessonId }.values.map { it.shuffled(random).toMutableList() }.shuffled(random)
        val result = mutableListOf<Question>()
        var round = 0
        while (result.size < exam.questionCount && groups.any { it.size > round }) {
            for (g in groups) {
                if (result.size >= exam.questionCount) break
                g.getOrNull(round)?.let { result += it }
            }
            round++
        }
        return result.shuffled(random)
    }

    fun optionOrder(q: Question, random: Random = Random.Default): List<Int> =
        if (q.keepOrder) q.options.indices.toList() else q.options.indices.shuffled(random)
}
