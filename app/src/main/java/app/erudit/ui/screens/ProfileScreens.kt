package app.erudit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.erudit.data.db.DailyActivity
import app.erudit.domain.AchievementGroup
import app.erudit.domain.Achievements
import app.erudit.domain.Counters
import app.erudit.domain.Levels
import app.erudit.domain.StudyRepository
import app.erudit.ui.components.Divider
import app.erudit.ui.components.DomainBadge
import app.erudit.ui.components.EmptyNote
import app.erudit.ui.components.IconBadge
import app.erudit.ui.components.ListScreen
import app.erudit.ui.components.Loading
import app.erudit.ui.components.NavRow
import app.erudit.ui.components.ProgressRing
import app.erudit.ui.components.SectionLabel
import app.erudit.ui.components.StatTile
import app.erudit.ui.components.ThinProgress
import app.erudit.ui.components.Tile
import app.erudit.ui.components.plural
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.nav.rememberContent
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.domainColor
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ProfileScreen(
    onAchievements: () -> Unit,
    onExams: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    onDomain: (String) -> Unit,
) {
    val content = rememberContent() ?: return Loading()
    val study = LocalContainer.current.study
    val settings = rememberSettings()
    val unlocks = rememberUnlocks(content)
    val level by study.levelInfo.collectAsState(initial = Levels.info(0))
    val activity by study.activity.collectAsState(initial = emptyList())
    val counters by study.counters.collectAsState(initial = emptyMap())
    val unlocked by study.achievements.collectAsState(initial = emptyList())
    val accuracy by study.lessonAccuracy.collectAsState(initial = emptyList())
    val cards by study.cardCount.collectAsState(initial = 0)

    val today = LocalDate.now().toEpochDay()
    val streak = StudyRepository.streak(activity, settings::goalOn, today)
    val bestStreak = maxOf(streak.toLong(), counters[Counters.BEST_STREAK] ?: 0)
    val answered = activity.sumOf { it.answered }
    val correct = activity.sumOf { it.correct }

    ListScreen {
        item {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(level.fraction, size = 84.dp, stroke = 6.dp) {
                    Text("${level.level}", style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(level.title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${level.xp} опыта · до уровня ${level.level + 1} — ${level.toNext}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.extra.muted,
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(plural(streak, "день", "дня", "дней"), "серия · рекорд $bestStreak", Modifier.weight(1f))
                StatTile("${unlocks.completed.size}", "уроков из ${content.lessons.size}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(if (answered == 0) "—" else "${correct * 100 / answered}%", "точность · $answered ответов", Modifier.weight(1f))
                StatTile("${unlocks.passed.size}", "экзаменов сдано", Modifier.weight(1f))
            }
        }
        item {
            SectionLabel("Активность за две недели")
            Tile(Modifier.fillMaxWidth()) {
                ActivityChart(activity, settings::goalOn, today)
            }
        }
        item {
            SectionLabel("Достижения · ${unlocked.size} из ${Achievements.all.size}")
            NavRow(
                title = "Все достижения",
                subtitle = unlocked.maxByOrNull { it.unlockedAt }?.let { "Последнее: " + (Achievements.byId[it.id]?.title ?: "") }
                    ?: "Первое — за первый урок",
                onClick = onAchievements,
                leading = { IconBadge(Icons.Outlined.EmojiEvents, AppTheme.extra.warm) },
            )
            NavRow(
                title = "История экзаменов",
                subtitle = "Все попытки и результаты",
                onClick = onExams,
                leading = { IconBadge(Icons.Outlined.School, MaterialTheme.colorScheme.primary) },
            )
            NavRow(
                title = "Закладки",
                subtitle = "Сохранённые уроки",
                onClick = onBookmarks,
                leading = { IconBadge(Icons.Outlined.Bookmarks, MaterialTheme.colorScheme.primary) },
            )
            NavRow(
                title = "Настройки",
                subtitle = "Цель дня, напоминания, тема",
                onClick = onSettings,
                leading = { IconBadge(Icons.Outlined.Settings, AppTheme.extra.muted) },
            )
        }
        item { SectionLabel("Прогресс по областям") }
        items(content.domains, key = { it.id }) { d ->
            val color = domainColor(d.color)
            val ids = d.lessons.mapTo(HashSet()) { it.id }
            val acc = accuracy.filter { it.lessonId in ids }
            val total = acc.sumOf { it.correct + it.wrong }
            val ok = acc.sumOf { it.correct }
            androidx.compose.material3.Surface(onClick = { onDomain(d.id) }, color = androidx.compose.ui.graphics.Color.Transparent) {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    DomainBadge(d.icon, color, size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(d.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (total > 0) "точность ${ok * 100 / total}%" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.extra.muted,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        ThinProgress(unlocks.domainProgress(d), color = color, height = 3.dp)
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "В колоде повторений ${plural(cards, "карточка", "карточки", "карточек")}",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.extra.faint,
            )
        }
    }
}

@Composable
private fun ActivityChart(activity: List<DailyActivity>, goalOn: (Long) -> Int, today: Long) {
    val days = (today - 13..today).toList()
    val byDay = activity.associateBy { it.day }
    val values = days.map { byDay[it]?.xp ?: 0 }
    // Каждый день сравнивается с целью, которая действовала в тот день.
    val met = days.map { (byDay[it]?.xp ?: 0) >= goalOn(it) }
    val max = maxOf(values.maxOrNull() ?: 0, goalOn(today), 1)
    val extra = AppTheme.extra
    val primary = MaterialTheme.colorScheme.primary
    val locale = Locale.forLanguageTag("ru")
    Column {
        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { i, v ->
                val f = (v.toFloat() / max).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(if (v == 0) 0.03f else f.coerceAtLeast(0.05f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            when {
                                met[i] -> primary
                                v > 0 -> primary.copy(alpha = 0.35f)
                                else -> extra.line
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            days.forEach { d ->
                val date = LocalDate.ofEpochDay(d)
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == today) MaterialTheme.colorScheme.onSurface else extra.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val week = values.takeLast(7).sum()
        Text(
            "За 7 дней: $week опыта · дней с выполненной целью: ${met.takeLast(7).count { it }}",
            style = MaterialTheme.typography.bodySmall,
            color = extra.muted,
        )
    }
}

@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val study = LocalContainer.current.study
    val unlocked by study.achievements.collectAsState(initial = emptyList())
    val byId = remember(unlocked) { unlocked.associateBy { it.id } }
    ListScreen(title = "Достижения", onBack = onBack) {
        item {
            Text(
                "Открыто ${unlocked.size} из ${Achievements.all.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.extra.muted,
            )
        }
        AchievementGroup.entries.forEach { group ->
            item(key = group.name) { SectionLabel(group.title) }
            items(Achievements.all.filter { it.group == group }, key = { it.id }) { a ->
                val got = byId[a.id]
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(
                        if (got != null) Icons.Outlined.EmojiEvents else Icons.Outlined.Lock,
                        if (got != null) AppTheme.extra.warm else AppTheme.extra.faint,
                        size = 40.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            a.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (got != null) MaterialTheme.colorScheme.onBackground else AppTheme.extra.muted,
                        )
                        Text(a.description, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                    }
                    if (got != null) {
                        Text(formatDate(got.unlockedAt).substringBefore(','), style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.faint)
                    }
                }
                Divider()
            }
        }
    }
}

@Composable
fun BookmarksScreen(onBack: () -> Unit, onLesson: (String) -> Unit) {
    val content = rememberContent() ?: return Loading()
    val bookmarks by LocalContainer.current.study.bookmarks.collectAsState(initial = emptyList())
    val lessons = bookmarks.mapNotNull { content.lessonsById[it.lessonId] }
    ListScreen(title = "Закладки", onBack = onBack) {
        if (lessons.isEmpty()) {
            item { EmptyNote("Сохраняйте уроки значком закладки в правом верхнем углу урока.") }
        }
        items(lessons, key = { it.id }) { lesson ->
            val domain = content.domainOf(lesson)
            NavRow(
                title = lesson.title,
                subtitle = "${domain.title} · ${content.courseOf(lesson).title}",
                onClick = { onLesson(lesson.id) },
                leading = { DomainBadge(domain.icon, domainColor(domain.color), size = 36.dp) },
            )
            Divider()
        }
        item { Spacer(Modifier.size(1.dp)) }
    }
}
