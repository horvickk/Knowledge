package app.erudit.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.erudit.BuildConfig
import app.erudit.data.prefs.ThemeMode
import app.erudit.notify.Reminders
import app.erudit.ui.components.Divider
import app.erudit.ui.components.ListScreen
import app.erudit.ui.components.SectionLabel
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.nav.rememberContent
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.Serif
import kotlinx.coroutines.launch

@Composable
fun ChoiceChips(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, (label, selected) ->
            Surface(
                onClick = { onSelect(i) },
                shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                border = if (selected) null else BorderStroke(1.dp, AppTheme.extra.line),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedBorderColor = AppTheme.extra.line,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.settings
    val settings = rememberSettings()
    val content = rememberContent()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var scale by remember(settings.textScale) { mutableFloatStateOf(settings.textScale) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            repo.setReminderEnabled(granted)
            if (granted) Reminders.schedule(context, settings.reminderHour, settings.reminderMinute)
        }
    }

    ListScreen(title = "Настройки", onBack = onBack) {
        item {
            SectionLabel("Оформление")
            ChoiceChips(
                listOf(
                    "Как в системе" to (settings.theme == ThemeMode.SYSTEM),
                    "Светлая" to (settings.theme == ThemeMode.LIGHT),
                    "Тёмная" to (settings.theme == ThemeMode.DARK),
                ),
            ) { i -> scope.launch { repo.setTheme(ThemeMode.entries[i]) } }
            Spacer(Modifier.height(20.dp))
            Text("Размер текста в уроках", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Знание — сила, а чтение — путь к нему.",
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, fontSize = MaterialTheme.typography.bodyLarge.fontSize * scale),
                color = AppTheme.extra.muted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Slider(
                value = scale,
                onValueChange = { scale = it },
                onValueChangeFinished = { scope.launch { repo.setTextScale(scale) } },
                valueRange = 0.85f..1.35f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = AppTheme.extra.line,
                ),
            )
        }
        item {
            SectionLabel("Цель дня, опыт")
            val goals = listOf(20, 40, 60, 100, 150)
            ChoiceChips(goals.map { "$it" to (settings.dailyGoal == it) }) { i -> scope.launch { repo.setDailyGoal(goals[i]) } }
            Text(
                "Урок даёт 15 опыта, каждый верный ответ — 2, экзамены — бонус.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.extra.muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            SectionLabel("Длина тренировки")
            val sizes = listOf(10, 15, 20, 30)
            ChoiceChips(sizes.map { "$it вопросов" to (settings.sessionSize == it) }) { i -> scope.launch { repo.setSessionSize(sizes[i]) } }
        }
        item {
            SectionLabel("Обучение")
            SwitchRow(
                "Последовательные ступени",
                "Следующая ступень открывается после экзамена предыдущей. Выключите, чтобы изучать что угодно в любом порядке.",
                settings.sequential,
            ) { scope.launch { repo.setSequential(it) } }
        }
        item {
            SectionLabel("Напоминания")
            SwitchRow(
                "Ежедневное напоминание",
                "Придёт, только если цель дня ещё не выполнена",
                settings.reminderEnabled,
            ) { on ->
                if (on && Build.VERSION.SDK_INT >= 33) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    scope.launch {
                        repo.setReminderEnabled(on)
                        if (on) Reminders.schedule(context, settings.reminderHour, settings.reminderMinute) else Reminders.cancel(context)
                    }
                }
            }
            if (settings.reminderEnabled) {
                Surface(onClick = { pickTime = true }, color = androidx.compose.ui.graphics.Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Text("Время", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(
                            "%02d:%02d".format(settings.reminderHour, settings.reminderMinute),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        item {
            SectionLabel("Данные")
            Surface(onClick = { confirmReset = true }, color = androidx.compose.ui.graphics.Color.Transparent) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text("Сбросить прогресс", style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.error)
                    Text("Удалит уроки, карточки, экзамены и достижения", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                }
            }
            Divider()
        }
        item {
            SectionLabel("О приложении")
            Text("Эрудит ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            if (content != null) {
                Text(
                    "Областей: ${content.domains.size} · курсов: ${content.coursesById.size} · уроков: ${content.lessons.size} · " +
                        "вопросов: ${content.lessons.sumOf { it.questions.size }} · логических задач: ${content.logicPuzzles.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.extra.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                "Весь контент работает офлайн. Шрифт Literata — SIL Open Font License.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.extra.muted,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить весь прогресс?") },
            text = { Text("Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { container.resetProgress() }
                }) { Text("Сбросить", color = AppTheme.extra.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (pickTime) {
        val state = rememberTimePickerState(settings.reminderHour, settings.reminderMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    pickTime = false
                    scope.launch {
                        repo.setReminderTime(state.hour, state.minute)
                        Reminders.schedule(context, state.hour, state.minute)
                    }
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Отмена") } },
            text = { TimePicker(state = state) },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}
