package app.erudit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lesson_progress")
data class LessonProgress(
    @PrimaryKey val lessonId: String,
    val completedAt: Long,
    val quizCorrect: Int,
    val quizTotal: Int,
)

@Entity(tableName = "bookmark")
data class Bookmark(
    @PrimaryKey val lessonId: String,
    val createdAt: Long,
)

/** Карточка интервального повторения — одна на вопрос. */
@Entity(tableName = "card", indices = [Index("due"), Index("lessonId")])
data class Card(
    @PrimaryKey val questionId: String,
    val lessonId: String,
    val reps: Int = 0,
    val intervalDays: Int = 0,
    val ease: Double = 2.5,
    /** День следующего показа, epochDay. */
    val due: Long = 0,
    val lapses: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val lastAnswered: Long = 0,
    val lastCorrect: Boolean = true,
)

@Entity(tableName = "exam_attempt", indices = [Index("examId")])
data class ExamAttempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: String,
    val correct: Int,
    val total: Int,
    val passed: Boolean,
    val timestamp: Long,
    val durationSec: Int,
)

@Entity(tableName = "daily_activity")
data class DailyActivity(
    @PrimaryKey val day: Long,
    val xp: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
    val lessons: Int = 0,
)

@Entity(tableName = "trainer_score", indices = [Index("trainer")])
data class TrainerScore(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainer: String,
    val score: Int,
    val timestamp: Long,
)

@Entity(tableName = "achievement")
data class UnlockedAchievement(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
)

@Entity(tableName = "counter")
data class Counter(
    @PrimaryKey val key: String,
    val value: Long,
)
