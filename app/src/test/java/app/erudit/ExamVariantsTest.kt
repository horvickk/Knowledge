package app.erudit

import app.erudit.data.content.ContentParser
import app.erudit.data.content.Exam
import app.erudit.domain.SessionBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExamVariantsTest {
    private val quiz = """
        ? Сколько будет 2/3 : 4/9?
        + 3/2
        - 8/27
        = Делим — значит, умножаем на перевёрнутую дробь.
        ?! Сколько будет 3/4 : 9/8?
        + 2/3
        - 27/32
        = 3/4 × 8/9 = 2/3.
        ??! Какое из чисел равно 5/6 : 5/12?
        + 2
        - 1/2
        = 5/6 × 12/5 = 2.
        ? В честь кого назван алгоритм?
        + Аль-Хорезми
        - Евклида
        = Слово — от имени аль-Хорезми.
    """.trimIndent().lines()

    @Test
    fun variantsAttachToPreviousQuestion() {
        val questions = ContentParser.parseQuestions("l", quiz)
        assertEquals("варианты — не отдельные вопросы урока", 2, questions.size)
        val (task, fact) = questions
        assertEquals(listOf("Сколько будет 3/4 : 9/8?", "Какое из чисел равно 5/6 : 5/12?"), task.examVariants.map { it.text })
        assertFalse(task.examVariants[0].choice)
        assertTrue("«??! » — вариант-выбор", task.examVariants[1].choice)
        assertEquals("l", task.examVariants[0].lessonId)
        assertTrue(fact.examVariants.isEmpty())
    }

    @Test
    fun examAsksVariantsInsteadOfLessonTasks() {
        val pool = ContentParser.parseQuestions("l", quiz)
        val exam = Exam("e", Exam.Kind.STAGE, "", "", questionCount = 2, secondsPerQuestion = 40, passPercent = 80, pool = pool)
        repeat(20) { seed ->
            val asked = SessionBuilder.exam(exam, Random(seed)).map { it.text }
            assertFalse("вопрос урока с вариантами на экзамене не задаётся", "Сколько будет 2/3 : 4/9?" in asked)
            assertTrue("факт задаётся как есть", "В честь кого назван алгоритм?" in asked)
            assertEquals(2, asked.size)
        }
    }

    @Test
    fun variantWithoutQuestionIsRejected() {
        val result = runCatching { ContentParser.parseQuestions("l", listOf("?! Сирота", "+ да", "- нет")) }
        assertTrue(result.exceptionOrNull() is ContentParser.ParseException)
    }
}
