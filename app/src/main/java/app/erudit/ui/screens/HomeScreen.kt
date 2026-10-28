package app.erudit.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.erudit.domain.DayProgress
import app.erudit.domain.Levels
import app.erudit.trainers.Trainers
import app.erudit.ui.components.DomainBadge
import app.erudit.ui.components.IconBadge
import app.erudit.ui.components.ListScreen
import app.erudit.ui.components.Loading
import app.erudit.ui.components.PrimaryButton
import app.erudit.ui.components.ProgressRing
import app.erudit.ui.components.SectionLabel
import app.erudit.ui.components.ThinProgress
import app.erudit.ui.components.Tile
import app.erudit.ui.components.plural
import app.erudit.ui.markdown.Markdown
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.nav.rememberContent
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.Serif
import app.erudit.ui.theme.domainColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onLesson: (String) -> Unit,
    onReview: () -> Unit,
    onExam: (String) -> Unit,
    onTrainer: (String) -> Unit,
    onPractice: (String) -> Unit,
    onCatalog: () -> Unit,
) {
    val content = rememberContent() ?: return Loading()
    val study = LocalContainer.current.study
    val unlocks = rememberUnlocks(content)
    val day by study.dayProgress.collectAsStateWithLifecycle(initialValue = DayProgress(0, 40, 0))
    val level by study.levelInfo.collectAsState(initial = Levels.info(0))
    val due by remember { study.dueCount() }.collectAsStateWithLifecycle(initialValue = 0)
    val last by study.lastCompletedLesson.collectAsState(initial = null)
    val accuracy by study.lessonAccuracy.collectAsState(initial = emptyList())

    val next = remember(unlocks, last) { unlocks.continueLesson(last?.lessonId) }
    val nextCourse = next?.let { content.coursesById[it.courseId] }
    val lastCourse = last?.lessonId?.let { content.lessonsById[it] }?.let { content.coursesById[it.courseId] }
    val pendingExam = lastCourse?.let { unlocks.pendingStageExam(it) }

    ListScreen {
        item {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru")))
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.extra.muted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Уровень ${level.level}", style = MaterialTheme.typography.titleSmall)
                    Text(level.title, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                }
            }
        }

        item {
            Spacer(Modifier.height(24.dp))
            Tile(Modifier.fillMaxWidth(), padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(
                        day.fraction,
                        size = 72.dp,
                        stroke = 6.dp,
                        color = if (day.goalReached) AppTheme.extra.success else MaterialTheme.colorScheme.primary,
                    ) {
                        Text("${(day.fraction * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (day.goalReached) "Цель дня выполнена" else "Цель дня",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${day.xpToday} из ${day.goal} опыта",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.extra.muted,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocalFireDepartment,
                                contentDescription = null,
                                tint = if (day.streak > 0) AppTheme.extra.warm else AppTheme.extra.faint,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (day.streak > 0) "Серия: ${plural(day.streak, "день", "дня", "дней")}" else "Начните серию сегодня",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = if (day.streak > 0) AppTheme.extra.warm else AppTheme.extra.muted,
                            )
                        }
                    }
                }
            }
        }

        if (next != null && nextCourse != null) {
            val domain = content.domainsById.getValue(next.domainId)
            item {
                val color = domainColor(domain.color)
                SectionLabel(if (last == null) "Начать" else "Продолжить")
                Tile(onClick = { onLesson(next.id) }, modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DomainBadge(domain.icon, color, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(nextCourse.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
                            Text("Ступень ${next.stageIndex} · ${next.minutes} мин", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(next.title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(14.dp))
                    ThinProgress(unlocks.courseProgress(nextCourse), color = color)
                }
            }
        }

        if (pendingExam != null) {
            item {
                Spacer(Modifier.height(12.dp))
                Tile(onClick = { onExam(pendingExam.examId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Outlined.School, MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Пора на экзамен", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${lastCourse.title} · ступень ${pendingExam.index}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.extra.muted,
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionLabel("Закрепление")
            Tile(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Outlined.Replay, if (due > 0) AppTheme.extra.warm else AppTheme.extra.muted)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Повторение", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (due > 0) "${plural(due, "карточка ждёт", "карточки ждут", "карточек ждут")} повторения"
                            else "На сегодня всё повторено",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.extra.muted,
                        )
                    }
                }
            }
        }

        val weak = accuracy
            .filter { it.correct + it.wrong >= 4 && content.lessonsById.containsKey(it.lessonId) }
            .map { it to it.correct.toFloat() / (it.correct + it.wrong) }
            .filter { it.second < 0.7f }
            .sortedBy { it.second }
            .take(3)
        if (weak.isNotEmpty()) {
            item { SectionLabel("Слабые места") }
            weak.forEach { (acc, ratio) ->
                val lesson = content.lessonsById.getValue(acc.lessonId)
                item(key = "weak-" + lesson.id) {
                    app.erudit.ui.components.NavRow(
                        title = lesson.title,
                        subtitle = "Точность ${(ratio * 100).toInt()}% · потренироваться",
                        onClick = { onPractice(lesson.id) },
                    )
                }
            }
        }

        val fact = content.facts.takeIf { it.isNotEmpty() }?.let { it[(LocalDate.now().toEpochDay() % it.size).toInt()] }
        if (fact != null) {
            item {
                SectionLabel("Факт дня")
                val color = domainColor(content.domainOf(fact.first).color)
                Tile(onClick = { onLesson(fact.first.id) }, modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                    Text(
                        Markdown.inline(fact.second),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(fact.first.title, style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
        }

        item {
            SectionLabel("Тренажёры")
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Trainers.all.forEach { t ->
                    Tile(onClick = { onTrainer(t.id) }, modifier = Modifier.width(156.dp), padding = 14.dp) {
                        Icon(t.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(18.dp))
                        Text(t.title, style = MaterialTheme.typography.titleSmall)
                        Text(t.kind, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                    }
                }
            }
        }

        if (next == null) {
            item {
                Spacer(Modifier.height(24.dp))
                PrimaryButton("Открыть каталог", onClick = onCatalog)
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Доброе утро"
    in 12..17 -> "Добрый день"
    in 18..22 -> "Добрый вечер"
    else -> "Доброй ночи"
}
