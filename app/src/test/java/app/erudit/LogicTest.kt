package app.erudit

import app.erudit.data.db.Card
import app.erudit.data.db.DailyActivity
import app.erudit.domain.Levels
import app.erudit.domain.Srs
import app.erudit.domain.StudyRepository
import app.erudit.trainers.MentalMath
import app.erudit.trainers.NBack
import app.erudit.trainers.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LogicTest {
    @Test
    fun srsGrowsIntervalsAndResetsOnError() {
        val r = Random(1)
        var card = Card("q", "l", due = 0)
        card = Srs.answer(card, true, today = 0, now = 0, random = r)
        assertEquals(1, card.intervalDays)
        card = Srs.answer(card, true, today = card.due, now = 0, random = r)
        assertEquals(3, card.intervalDays)
        val before = card.intervalDays
        card = Srs.answer(card, true, today = card.due, now = 0, random = r)
        assertTrue(card.intervalDays > before)
        card = Srs.answer(card, false, today = card.due, now = 0, random = r)
        assertEquals(0, card.reps)
        assertEquals(1, card.lapses)
        assertEquals(1, card.intervalDays)
    }

    @Test
    fun earlyAnswerDoesNotStretchInterval() {
        val card = Card("q", "l", reps = 3, intervalDays = 10, due = 20)
        val after = Srs.answer(card, true, today = 12, now = 0)
        assertEquals(10, after.intervalDays)
        assertEquals(20, after.due)
        assertEquals(1, after.correct)
    }

    @Test
    fun levelsAreMonotonic() {
        assertEquals(1, Levels.levelFor(0))
        assertEquals(2, Levels.levelFor(100))
        for (l in 1..50) assertTrue(Levels.xpForLevel(l + 1) > Levels.xpForLevel(l))
        assertEquals(10, Levels.levelFor(Levels.xpForLevel(10)))
    }

    @Test
    fun streakCountsConsecutiveGoalDays() {
        val a = listOf(DailyActivity(10, xp = 50), DailyActivity(9, xp = 45), DailyActivity(8, xp = 10), DailyActivity(7, xp = 80))
        assertEquals(2, StudyRepository.streak(a, 40, today = 10))
        assertEquals(2, StudyRepository.streak(a, 40, today = 11))
        assertEquals(0, StudyRepository.streak(a, 40, today = 12))
    }

    @Test
    fun generatorsProduceValidProblems() {
        val r = Random(7)
        repeat(2000) {
            val level = it % 10 + 1
            val p = MentalMath.generate(level, r)
            assertTrue(p.answer >= 0)
        }
        repeat(2000) {
            val s = Series.generate(it % 10, r)
            assertTrue(s.shown.size >= 5)
        }
        val seq = NBack.generate(2, 22, r)
        assertEquals(22, seq.size)
        assertTrue(seq.all { it in 0..8 })
    }
}
