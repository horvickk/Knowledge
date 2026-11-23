package app.erudit

import app.erudit.data.content.Content
import app.erudit.data.content.Course
import app.erudit.data.content.Domain
import app.erudit.data.content.Lesson
import app.erudit.data.content.Question
import app.erudit.data.content.Stage
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewQuestionsTest {
    @Test
    fun oldestDueVariantRepresentsItsTopicBeforeApplyingLimit() {
        fun question(id: String) = Question(id, "lesson", id, listOf("1", "2"), 0, null)
        val variants = listOf(question("exam1"), question("exam2"))
        val base = question("base").copy(examVariants = variants)
        val others = (1..40).map { question("other$it") }
        val lesson = Lesson("lesson", "course", "domain", 1, "", 1, "", listOf(base) + others)
        val course = Course("course", "domain", "", "", listOf(Stage(1, "course", "", listOf(lesson))))
        val content = Content(listOf(Domain("domain", "", "", 0L, "", listOf(course))), emptyList())
        val due = listOf("removed", "exam2", "base", "exam1") + others.map { it.id }

        for (limit in listOf(10, 30, 50)) {
            assertEquals((listOf(variants.last()) + others).take(limit), content.reviewQuestions(due, limit))
        }
        assertEquals(listOf(base), content.reviewQuestions(listOf("base", "exam1"), 30))
        assertEquals(listOf(variants.first()), content.reviewQuestions(listOf("exam1"), 30))
    }
}
