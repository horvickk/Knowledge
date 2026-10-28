package app.erudit

import app.erudit.data.content.ContentLoader
import app.erudit.data.content.ContentSource
import app.erudit.data.db.Card
import app.erudit.domain.SessionBuilder
import app.erudit.domain.Srs
import app.erudit.domain.Xp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class XpTest {
    private val today = 20_000L

    /** Цепочка ответов на один вопрос, как её видит репозиторий: опыт считается по карточке до ответа. */
    private fun answer(card: Card?, correct: Boolean, day: Long = today): Pair<Int, Card> {
        val xp = if (correct) Xp.forAnswer(card, day) else 0
        return xp to Srs.answer(card ?: Card("q", "l", due = day), correct, day, now = day * 86_400_000)
    }

    @Test
    fun firstCorrectAnswerGivesXpOnlyOnce() {
        val (first, card) = answer(null, correct = true)
        assertEquals(Xp.CORRECT_ANSWER, first)
        val (again, card2) = answer(card, correct = true)
        assertEquals("тот же вопрос в тот же день", 0, again)
        assertEquals("и ещё раз", 0, answer(card2, correct = true).first)
    }

    @Test
    fun wrongThenRightGivesXpOnceForNewQuestion() {
        val (_, wrong) = answer(null, correct = false)
        val (fixed, card) = answer(wrong, correct = true)
        assertEquals("первый верный ответ — новое знание", Xp.CORRECT_ANSWER, fixed)
        val (_, wrongAgain) = answer(card, correct = false)
        assertEquals("ошибиться нарочно и исправиться — опыта нет", 0, answer(wrongAgain, correct = true).first)
    }

    @Test
    fun dueReviewGivesXp() {
        val (_, card) = answer(null, correct = true)
        // Карточку можно повторить, когда подошёл срок.
        val (review, reviewed) = answer(card, correct = true, day = card.due)
        assertEquals(Xp.CORRECT_ANSWER, review)
        assertEquals("после повторения срок снова в будущем", 0, answer(reviewed, correct = true, day = card.due).first)
    }

    @Test
    fun cardFromCompletedLessonGivesXpOnFirstCorrectAnswer() {
        val fresh = Card("q", "l", due = today + 1)
        assertEquals(Xp.CORRECT_ANSWER, Xp.forAnswer(fresh, today))
    }

    @Test
    fun trainerGivesXpOncePerDayByBestResult() {
        val (first, s1) = Xp.trainerGain(null, today, 10)
        assertEquals(10, first)
        val (worse, s2) = Xp.trainerGain(s1, today, 6)
        assertEquals("результат хуже лучшего за день", 0, worse)
        val (better, s3) = Xp.trainerGain(s2, today, 15)
        assertEquals("только прибавка к лучшему результату дня", 5, better)
        val (same, _) = Xp.trainerGain(s3, today, 15)
        assertEquals(0, same)
        val (nextDay, _) = Xp.trainerGain(s3, today + 1, 6)
        assertEquals("на следующий день — снова", 6, nextDay)
    }

    @Test
    fun blitzUsesOnlyCompletedLessons() {
        val root = File("src/main/assets")
        val content = ContentLoader.load(object : ContentSource {
            override fun list(path: String): List<String> = File(root, path).list()?.toList().orEmpty()
            override fun read(path: String): String = File(root, path).readText()
        })
        assertTrue("новичку блиц не из чего собрать", SessionBuilder.blitzPool(content, emptySet()).isEmpty())
        val done = content.lessons.take(3).map { it.id }.toSet()
        val pool = SessionBuilder.blitzPool(content, done)
        assertTrue("три урока дают блиц", pool.size >= SessionBuilder.BLITZ_MIN)
        assertTrue(pool.all { it.lessonId in done })
    }

    @Test
    fun lessonCheckHasAllQuestions() {
        val root = File("src/main/assets")
        val content = ContentLoader.load(object : ContentSource {
            override fun list(path: String): List<String> = File(root, path).list()?.toList().orEmpty()
            override fun read(path: String): String = File(root, path).readText()
        })
        content.lessons.forEach { lesson ->
            assertEquals(lesson.id, lesson.questions.map { it.id }.toSet(), SessionBuilder.lessonCheck(lesson).map { it.id }.toSet())
        }
    }
}
