package app.erudit.domain

import app.erudit.data.content.Content
import app.erudit.data.content.Exam
import app.erudit.data.content.Lesson
import app.erudit.data.content.Question
import app.erudit.data.db.AppDao
import app.erudit.data.db.Card
import app.erudit.data.db.Counter
import app.erudit.data.db.DailyActivity
import app.erudit.data.db.ExamAttempt
import app.erudit.data.db.LessonProgress
import app.erudit.data.db.TrainerScore
import app.erudit.data.db.UnlockedAchievement
import app.erudit.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime

data class ExamOutcome(val passed: Boolean, val percent: Int, val xp: Int, val firstPass: Boolean, val attemptId: Long = 0)

data class DayProgress(val xpToday: Int, val goal: Int, val streak: Int) {
    val goalReached: Boolean get() = xpToday >= goal
    val fraction: Float get() = (xpToday.toFloat() / goal).coerceIn(0f, 1f)
}

class StudyRepository(
    private val dao: AppDao,
    private val settings: SettingsRepository,
    private val content: suspend () -> Content,
) {
    private val mutex = Mutex()
    private val _unlocked = MutableSharedFlow<Achievement>(extraBufferCapacity = 8)

    /** Новые достижения — для всплывающего уведомления. */
    val unlocked: SharedFlow<Achievement> = _unlocked

    fun today(): Long = LocalDate.now().toEpochDay()

    val completedLessons: Flow<Set<String>> = dao.lessonProgress().map { list -> list.mapTo(HashSet()) { it.lessonId } }
    val passedExams: Flow<Set<String>> = dao.passedExamIds().map { it.toSet() }
    val activity: Flow<List<DailyActivity>> = dao.allActivity()
    val totalXp: Flow<Int> = activity.map { list -> list.sumOf { it.xp } }
    val levelInfo: Flow<Levels.Info> = totalXp.map { Levels.info(it) }
    val bookmarks = dao.bookmarks()
    val examAttempts = dao.examAttempts()
    val lessonAccuracy = dao.lessonAccuracy()
    val trainerBests = dao.trainerBests()
    val achievements = dao.achievements()
    val counters: Flow<Map<String, Long>> = dao.counters().map { list -> list.associate { it.key to it.value } }
    val cardCount = dao.cardCount()
    val matureCount = dao.matureCount()
    val mistakeCount = dao.mistakeCount()
    val lastCompletedLesson = dao.lastCompletedLesson()

    fun dueCount(): Flow<Int> = dao.dueCount(today())

    val dayProgress: Flow<DayProgress> = combine(activity, settings.settings) { list, s ->
        val today = today()
        DayProgress(
            xpToday = list.firstOrNull { it.day == today }?.xp ?: 0,
            goal = s.dailyGoal,
            streak = streak(list, s.dailyGoal, today),
        )
    }

    fun trainerHistory(trainer: String, limit: Int = 30) = dao.trainerHistory(trainer, limit)

    suspend fun isBookmarked(lessonId: String): Boolean = dao.bookmarks().first().any { it.lessonId == lessonId }

    suspend fun setBookmark(lessonId: String, on: Boolean) {
        if (on) dao.addBookmark(app.erudit.data.db.Bookmark(lessonId, System.currentTimeMillis()))
        else dao.removeBookmark(lessonId)
    }

    suspend fun cardsFor(ids: List<String>): Map<String, Card> =
        ids.chunked(500).flatMap { dao.cards(it) }.associateBy { it.questionId }

    suspend fun dueCards(limit: Int): List<Card> = dao.dueCards(today(), limit)
    suspend fun mistakeCards(limit: Int): List<Card> = dao.mistakeCards(limit)

    /** Ответ на вопрос в любом режиме: обновляет карточку и дневную активность. */
    suspend fun recordAnswer(question: Question, correct: Boolean, isReview: Boolean) = mutex.withLock {
        val now = System.currentTimeMillis()
        val today = today()
        val card = dao.card(question.id) ?: Card(question.id, question.lessonId, due = today)
        dao.upsertCards(listOf(Srs.answer(card, correct, today, now)))
        if (isReview) bumpCounter(Counters.REVIEWS, 1)
        addActivityLocked(xp = if (correct) Xp.CORRECT_ANSWER else 0, answered = 1, correct = if (correct) 1 else 0)
    }

    /** Завершение урока: все его вопросы попадают в интервальные повторения. */
    suspend fun completeLesson(lesson: Lesson, quizCorrect: Int, quizTotal: Int): Int {
        val xp = mutex.withLock {
            val existing = dao.lessonProgress(lesson.id)
            val first = existing == null
            dao.upsertLessonProgress(
                LessonProgress(
                    lessonId = lesson.id,
                    completedAt = existing?.completedAt ?: System.currentTimeMillis(),
                    quizCorrect = maxOf(quizCorrect, existing?.quizCorrect ?: 0),
                    quizTotal = quizTotal,
                ),
            )
            val known = cardsFor(lesson.questions.map { it.id })
            val tomorrow = today() + 1
            val fresh = lesson.questions.filter { it.id !in known }.map { Card(it.id, it.lessonId, due = tomorrow) }
            if (fresh.isNotEmpty()) dao.upsertCards(fresh)
            val gained = if (first) Xp.LESSON else 0
            addActivityLocked(xp = gained, lessons = if (first) 1 else 0)
            gained
        }
        checkAchievements()
        return xp
    }

    suspend fun recordExam(exam: Exam, correct: Int, total: Int, durationSec: Int): ExamOutcome {
        val outcome = mutex.withLock {
            val percent = if (total == 0) 0 else correct * 100 / total
            val passed = percent >= exam.passPercent
            val firstPass = passed && dao.passCount(exam.id) == 0
            val id = dao.insertExamAttempt(ExamAttempt(0, exam.id, correct, total, passed, System.currentTimeMillis(), durationSec))
            val bonus = if (firstPass) passBonus(exam) else 0
            if (bonus > 0) addActivityLocked(xp = bonus)
            if (passed && correct == total) bumpCounter(Counters.PERFECT_EXAMS, 1)
            ExamOutcome(passed, percent, bonus + correct * Xp.CORRECT_ANSWER, firstPass, id)
        }
        checkAchievements()
        return outcome
    }

    /**
     * Пересчёт попытки, когда пользователь засчитал свои ответы в разборе ошибок: верных стало
     * [correct]. Если экзамен от этого впервые сдан, начисляется бонус; верных может только
     * прибавиться, поэтому сданный экзамен не становится несданным.
     */
    suspend fun reviseExam(attemptId: Long, exam: Exam, correct: Int): ExamOutcome {
        val outcome = mutex.withLock {
            val old = dao.examAttempt(attemptId) ?: return@withLock null
            val total = old.total
            val percent = if (total == 0) 0 else correct * 100 / total
            val passed = old.passed || percent >= exam.passPercent
            dao.updateExamAttempt(attemptId, correct, passed)
            val firstPass = passed && dao.passCountExcept(exam.id, attemptId) == 0
            if (firstPass && !old.passed) addActivityLocked(xp = passBonus(exam))
            if (passed && correct == total && !(old.passed && old.correct == total)) bumpCounter(Counters.PERFECT_EXAMS, 1)
            ExamOutcome(passed, percent, (if (firstPass) passBonus(exam) else 0) + correct * Xp.CORRECT_ANSWER, firstPass, attemptId)
        }
        checkAchievements()
        return outcome ?: ExamOutcome(false, 0, 0, false, attemptId)
    }

    private fun passBonus(exam: Exam): Int = when (exam.kind) {
        Exam.Kind.STAGE -> Xp.STAGE_EXAM
        Exam.Kind.COURSE -> Xp.COURSE_EXAM
        Exam.Kind.DOMAIN -> Xp.DOMAIN_EXAM
    }

    /**
     * Результат тренажёра. [records] — рекорды для достижений: сохраняется максимум
     * (для таблицы Шульте — минимум времени, см. [lowerIsBetter]).
     */
    suspend fun recordTrainer(
        trainer: String,
        score: Int,
        xp: Int,
        records: Map<String, Long> = emptyMap(),
        lowerIsBetter: Set<String> = emptySet(),
    ) {
        mutex.withLock {
            dao.insertTrainerScore(TrainerScore(0, trainer, score, System.currentTimeMillis()))
            for ((key, value) in records) {
                val old = dao.counter(key)
                val better = when {
                    old == null || old == 0L -> true
                    key in lowerIsBetter -> value < old
                    else -> value > old
                }
                if (better) dao.setCounter(Counter(key, value))
            }
            addActivityLocked(xp = xp)
        }
        checkAchievements()
    }

    /** Запоминает значение без сравнения с прежним — например, последний выбор пользователя. */
    suspend fun rememberValue(key: String, value: Long) = mutex.withLock { dao.setCounter(Counter(key, value)) }

    suspend fun recordBlitz(correct: Int) {
        mutex.withLock {
            val old = dao.counter(Counters.BLITZ_BEST) ?: 0
            if (correct > old) dao.setCounter(Counter(Counters.BLITZ_BEST, correct.toLong()))
        }
        checkAchievements()
    }

    private suspend fun bumpCounter(key: String, by: Long) {
        dao.setCounter(Counter(key, (dao.counter(key) ?: 0) + by))
    }

    private suspend fun addActivityLocked(xp: Int = 0, answered: Int = 0, correct: Int = 0, lessons: Int = 0) {
        val day = today()
        val current = dao.activity(day) ?: DailyActivity(day)
        dao.upsertActivity(
            current.copy(
                xp = current.xp + xp,
                answered = current.answered + answered,
                correct = current.correct + correct,
                lessons = current.lessons + lessons,
            ),
        )
        val hour = LocalTime.now().hour
        if (hour == 23 || hour < 4) dao.setCounter(Counter(Counters.NIGHT_OWL, 1))
        if (hour in 4..6) dao.setCounter(Counter(Counters.EARLY_BIRD, 1))

        val goal = settings.settings.first().dailyGoal
        val s = streak(dao.allActivity().first(), goal, day)
        if (s > (dao.counter(Counters.BEST_STREAK) ?: 0)) dao.setCounter(Counter(Counters.BEST_STREAK, s.toLong()))
    }

    suspend fun checkAchievements(): List<Achievement> {
        val snapshot = snapshot()
        val have = dao.achievementsNow().mapTo(HashSet()) { it.id }
        val fresh = Achievements.all.filter { it.id !in have && it.check(snapshot) }
        val now = System.currentTimeMillis()
        for (a in fresh) {
            dao.unlock(UnlockedAchievement(a.id, now))
            _unlocked.tryEmit(a)
        }
        return fresh
    }

    private suspend fun snapshot(): StatsSnapshot {
        val c = content()
        val lessons = completedLessons.first()
        val passed = passedExams.first()
        val activity = dao.allActivity().first()
        val goal = settings.settings.first().dailyGoal
        return StatsSnapshot(
            lessons = lessons.size,
            examsPassed = passed.size,
            coursesDone = c.coursesById.values.count { it.examId in passed },
            domainsDone = c.domains.count { it.examId in passed },
            domainsTouched = lessons.mapNotNull { c.lessonsById[it]?.domainId }.toSet().size,
            totalDomains = c.domains.size,
            streak = streak(activity, goal, today()),
            totalCorrect = activity.sumOf { it.correct },
            level = Levels.levelFor(activity.sumOf { it.xp }),
            counters = counters.first(),
        )
    }

    companion object {
        /** Серия: подряд идущие дни с выполненной целью. Сегодняшний день не обрывает серию, пока он не закончился. */
        fun streak(activity: List<DailyActivity>, goal: Int, today: Long): Int {
            val done = activity.filter { it.xp >= goal }.mapTo(HashSet()) { it.day }
            var day = if (today in done) today else today - 1
            var count = 0
            while (day in done) {
                count++
                day--
            }
            return count
        }
    }
}
