package app.erudit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LessonProgress::class,
        Bookmark::class,
        Card::class,
        ExamAttempt::class,
        DailyActivity::class,
        TrainerScore::class,
        UnlockedAchievement::class,
        Counter::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "erudit.db").build()
    }
}
