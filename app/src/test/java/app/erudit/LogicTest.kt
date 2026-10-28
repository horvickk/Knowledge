package app.erudit

import app.erudit.data.db.Card
import app.erudit.data.db.DailyActivity
import app.erudit.data.prefs.GoalHistory
import app.erudit.data.prefs.Settings
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
    fun goalChangeDoesNotRewriteStreak() {
        // Цель 40, а с 10-го дня — 100: прошлые дни по-прежнему сверяются с 40.
        val history = GoalHistory.change(emptyList(), old = 40, goal = 100, today = 10)
        val settings = Settings(dailyGoal = 100, goalHistory = history)
        assertEquals(40, settings.goalOn(9))
        assertEquals(100, settings.goalOn(10))
        val a = listOf(DailyActivity(7, xp = 50), DailyActivity(8, xp = 45), DailyActivity(9, xp = 60), DailyActivity(10, xp = 30))
        assertEquals("сегодня цель не выполнена, но серия не оборвана", 3, StudyRepository.streak(a, settings::goalOn, today = 10))
        assertEquals(1, StudyRepository.streak(a + DailyActivity(11, xp = 120), settings::goalOn, today = 11))

        // Снижение цели не засчитывает задним числом дни, когда прежняя цель не была выполнена.
        val lowered = Settings(dailyGoal = 20, goalHistory = GoalHistory.change(emptyList(), old = 100, goal = 20, today = 10))
        val b = listOf(DailyActivity(8, xp = 30), DailyActivity(9, xp = 30), DailyActivity(10, xp = 30))
        assertEquals(1, StudyRepository.streak(b, lowered::goalOn, today = 10))

        // Повторная смена в тот же день заменяет запись, история хранится в строке.
        val twice = GoalHistory.change(history, old = 100, goal = 60, today = 10)
        assertEquals(listOf(Long.MIN_VALUE to 40, 10L to 60), twice)
        assertEquals(twice, GoalHistory.parse(GoalHistory.format(twice)))
        assertEquals(40, Settings(dailyGoal = 40).goalOn(123))
    }

    @Test
    fun nBackRewardsOnlyAboveChance() {
        val r = Random(3)
        repeat(50) {
            val n = it % 4 + 1
            val seq = NBack.generate(n, 20 + n, r)
            val targets = (n until seq.size).filter { i -> seq[i] == seq[i - n] }
            val idle = NBack.accuracy(seq, n, emptyList())
            assertEquals("ничего не нажимать — 50%", 50, idle)
            assertEquals(0, NBack.xp(idle, n))
            assertEquals("нажимать всё подряд — тоже 50%", 50, NBack.accuracy(seq, n, (0 until seq.size).toList()))
            val perfect = NBack.accuracy(seq, n, targets)
            assertEquals(100, perfect)
            assertEquals(10 * n, NBack.xp(perfect, n))
        }
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
