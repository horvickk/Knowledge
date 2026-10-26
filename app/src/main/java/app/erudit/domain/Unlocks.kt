package app.erudit.domain

import app.erudit.data.content.Content
import app.erudit.data.content.Course
import app.erudit.data.content.Domain
import app.erudit.data.content.Lesson
import app.erudit.data.content.Stage

/**
 * Правила последовательного обучения: следующая ступень открывается после экзамена
 * предыдущей, итоговый экзамен — после всех ступеней. В свободном режиме открыто всё.
 */
class Unlocks(
    private val content: Content,
    val completed: Set<String>,
    val passed: Set<String>,
    private val sequential: Boolean,
) {
    fun stageOpen(stage: Stage): Boolean {
        if (!sequential || stage.index == 1) return true
        val course = content.coursesById.getValue(stage.courseId)
        return course.stages[stage.index - 2].examId in passed
    }

    fun lessonOpen(lesson: Lesson): Boolean =
        stageOpen(content.stagesById.getValue("${lesson.courseId}:s${lesson.stageIndex}"))

    fun stageExamOpen(stage: Stage): Boolean =
        stageOpen(stage) && (!sequential || stage.lessons.all { it.id in completed })

    fun courseExamOpen(course: Course): Boolean =
        !sequential || course.stages.all { it.examId in passed }

    fun domainExamOpen(domain: Domain): Boolean =
        !sequential || domain.courses.all { it.examId in passed }

    fun examOpen(examId: String): Boolean {
        content.stagesById[examId]?.let { return stageExamOpen(it) }
        val owner = examId.substringBefore(':')
        content.coursesById[owner]?.let { return courseExamOpen(it) }
        content.domainsById[owner]?.let { return domainExamOpen(it) }
        return false
    }

    fun courseProgress(course: Course): Float {
        val lessons = course.lessons
        if (lessons.isEmpty()) return 0f
        return lessons.count { it.id in completed }.toFloat() / lessons.size
    }

    fun domainProgress(domain: Domain): Float {
        val lessons = domain.lessons
        if (lessons.isEmpty()) return 0f
        return lessons.count { it.id in completed }.toFloat() / lessons.size
    }

    /** Следующий непройденный и открытый урок курса. */
    fun nextLesson(course: Course): Lesson? =
        course.lessons.firstOrNull { it.id !in completed && lessonOpen(it) }

    /** Что изучать дальше: продолжение последнего курса, затем следующий курс области. */
    fun continueLesson(lastLessonId: String?): Lesson? {
        val last = lastLessonId?.let { content.lessonsById[it] }
        if (last != null) {
            val course = content.coursesById.getValue(last.courseId)
            nextLesson(course)?.let { return it }
            val domain = content.domainsById.getValue(last.domainId)
            domain.courses.dropWhile { it.id != course.id }.drop(1).firstNotNullOfOrNull { nextLesson(it) }?.let { return it }
        }
        return content.domains.firstNotNullOfOrNull { d -> d.courses.firstNotNullOfOrNull { nextLesson(it) } }
    }

    /** Урок, после которого стоит сдать экзамен ступени: всё пройдено, экзамен не сдан. */
    fun pendingStageExam(course: Course): Stage? =
        course.stages.firstOrNull { it.examId !in passed && stageOpen(it) && it.lessons.all { l -> l.id in completed } }
}
