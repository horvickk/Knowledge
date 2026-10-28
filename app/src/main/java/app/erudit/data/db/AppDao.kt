package app.erudit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class TrainerBest(val trainer: String, val best: Int, val worst: Int, val sessions: Int)

data class LessonAccuracy(val lessonId: String, val correct: Int, val wrong: Int)

@Dao
interface AppDao {
    // Уроки
    @Query("SELECT * FROM lesson_progress")
    fun lessonProgress(): Flow<List<LessonProgress>>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :id")
    suspend fun lessonProgress(id: String): LessonProgress?

    @Upsert
    suspend fun upsertLessonProgress(p: LessonProgress)

    @Query("SELECT * FROM lesson_progress ORDER BY completedAt DESC LIMIT 1")
    fun lastCompletedLesson(): Flow<LessonProgress?>

    // Закладки
    @Query("SELECT * FROM bookmark ORDER BY createdAt DESC")
    fun bookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(b: Bookmark)

    @Query("DELETE FROM bookmark WHERE lessonId = :id")
    suspend fun removeBookmark(id: String)

    // Карточки
    @Query("SELECT * FROM card WHERE questionId IN (:ids)")
    suspend fun cards(ids: List<String>): List<Card>

    @Query("SELECT * FROM card WHERE questionId = :id")
    suspend fun card(id: String): Card?

    @Upsert
    suspend fun upsertCards(cards: List<Card>)

    @Query("SELECT * FROM card WHERE due <= :today ORDER BY due, lastAnswered LIMIT :limit")
    suspend fun dueCards(today: Long, limit: Int): List<Card>

    @Query("SELECT COUNT(*) FROM card WHERE due <= :today")
    fun dueCount(today: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM card")
    fun cardCount(): Flow<Int>

    @Query("SELECT * FROM card WHERE lastCorrect = 0 ORDER BY lastAnswered DESC LIMIT :limit")
    suspend fun mistakeCards(limit: Int): List<Card>

    @Query("SELECT COUNT(*) FROM card WHERE lastCorrect = 0")
    fun mistakeCount(): Flow<Int>

    @Query("SELECT questionId FROM card")
    suspend fun knownQuestionIds(): List<String>

    @Query("DELETE FROM card WHERE questionId IN (:ids)")
    suspend fun deleteCards(ids: List<String>)

    @Query("SELECT lessonId, SUM(correct) AS correct, SUM(wrong) AS wrong FROM card GROUP BY lessonId")
    fun lessonAccuracy(): Flow<List<LessonAccuracy>>

    @Query("SELECT COUNT(*) FROM card WHERE intervalDays >= 21")
    fun matureCount(): Flow<Int>

    // Экзамены
    @Insert
    suspend fun insertExamAttempt(a: ExamAttempt): Long

    @Query("SELECT * FROM exam_attempt WHERE id = :id")
    suspend fun examAttempt(id: Long): ExamAttempt?

    @Query("UPDATE exam_attempt SET correct = :correct, passed = :passed WHERE id = :id")
    suspend fun updateExamAttempt(id: Long, correct: Int, passed: Boolean)

    @Query("SELECT COUNT(*) FROM exam_attempt WHERE examId = :examId AND passed = 1 AND id != :except")
    suspend fun passCountExcept(examId: String, except: Long): Int

    @Query("SELECT * FROM exam_attempt ORDER BY timestamp DESC")
    fun examAttempts(): Flow<List<ExamAttempt>>

    @Query("SELECT DISTINCT examId FROM exam_attempt WHERE passed = 1")
    fun passedExamIds(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM exam_attempt WHERE examId = :examId AND passed = 1")
    suspend fun passCount(examId: String): Int

    // Активность
    @Query("SELECT * FROM daily_activity WHERE day = :day")
    suspend fun activity(day: Long): DailyActivity?

    @Upsert
    suspend fun upsertActivity(a: DailyActivity)

    @Query("SELECT * FROM daily_activity ORDER BY day")
    fun allActivity(): Flow<List<DailyActivity>>

    // Тренажёры
    @Insert
    suspend fun insertTrainerScore(s: TrainerScore)

    @Query("SELECT trainer, MAX(score) AS best, MIN(score) AS worst, COUNT(*) AS sessions FROM trainer_score GROUP BY trainer")
    fun trainerBests(): Flow<List<TrainerBest>>

    @Query("SELECT * FROM trainer_score WHERE trainer = :trainer ORDER BY timestamp DESC LIMIT :limit")
    fun trainerHistory(trainer: String, limit: Int): Flow<List<TrainerScore>>

    // Достижения
    @Query("SELECT * FROM achievement")
    fun achievements(): Flow<List<UnlockedAchievement>>

    @Query("SELECT * FROM achievement")
    suspend fun achievementsNow(): List<UnlockedAchievement>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun unlock(a: UnlockedAchievement)

    // Счётчики
    @Query("SELECT value FROM counter WHERE `key` = :key")
    suspend fun counter(key: String): Long?

    @Upsert
    suspend fun setCounter(c: Counter)

    @Query("SELECT * FROM counter")
    fun counters(): Flow<List<Counter>>
}
