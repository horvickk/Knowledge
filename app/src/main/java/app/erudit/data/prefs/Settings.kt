package app.erudit.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val textScale: Float = 1f,
    val dailyGoal: Int = 40,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val sequential: Boolean = true,
    val onboarded: Boolean = false,
    val sessionSize: Int = 15,
    val checkUpdates: Boolean = true,
    /** «Позже» в окне обновления: сборка и день, до конца которого окно не показывается. */
    val updateSnoozeBuild: Int = 0,
    val updateSnoozeDay: Long = 0,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val textScale = floatPreferencesKey("text_scale")
        val dailyGoal = intPreferencesKey("daily_goal")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderHour = intPreferencesKey("reminder_hour")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val sequential = booleanPreferencesKey("sequential")
        val onboarded = booleanPreferencesKey("onboarded")
        val sessionSize = intPreferencesKey("session_size")
        val checkUpdates = booleanPreferencesKey("check_updates")
        val updateSnoozeBuild = intPreferencesKey("update_snooze_build")
        val updateSnoozeDay = longPreferencesKey("update_snooze_day")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            textScale = p[Keys.textScale] ?: 1f,
            dailyGoal = p[Keys.dailyGoal] ?: 40,
            reminderEnabled = p[Keys.reminderEnabled] ?: false,
            reminderHour = p[Keys.reminderHour] ?: 19,
            reminderMinute = p[Keys.reminderMinute] ?: 0,
            sequential = p[Keys.sequential] ?: true,
            onboarded = p[Keys.onboarded] ?: false,
            sessionSize = p[Keys.sessionSize] ?: 15,
            checkUpdates = p[Keys.checkUpdates] ?: true,
            updateSnoozeBuild = p[Keys.updateSnoozeBuild] ?: 0,
            updateSnoozeDay = p[Keys.updateSnoozeDay] ?: 0,
        )
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    suspend fun setTheme(mode: ThemeMode) = set(Keys.theme, mode.name)
    suspend fun setTextScale(scale: Float) = set(Keys.textScale, scale)
    suspend fun setDailyGoal(goal: Int) = set(Keys.dailyGoal, goal)
    suspend fun setReminderEnabled(enabled: Boolean) = set(Keys.reminderEnabled, enabled)
    suspend fun setSequential(enabled: Boolean) = set(Keys.sequential, enabled)
    suspend fun setOnboarded() = set(Keys.onboarded, true)
    suspend fun setSessionSize(size: Int) = set(Keys.sessionSize, size)
    suspend fun setCheckUpdates(enabled: Boolean) = set(Keys.checkUpdates, enabled)

    suspend fun snoozeUpdate(build: Int, day: Long) {
        context.dataStore.edit {
            it[Keys.updateSnoozeBuild] = build
            it[Keys.updateSnoozeDay] = day
        }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        context.dataStore.edit {
            it[Keys.reminderHour] = hour
            it[Keys.reminderMinute] = minute
        }
    }
}
