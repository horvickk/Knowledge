package app.erudit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.erudit.data.content.Course
import app.erudit.data.content.Stage
import app.erudit.domain.Unlocks
import app.erudit.ui.components.Divider
import app.erudit.ui.components.DomainBadge
import app.erudit.ui.components.IconBadge
import app.erudit.ui.components.ListScreen
import app.erudit.ui.components.Loading
import app.erudit.ui.components.NavRow
import app.erudit.ui.components.Pill
import app.erudit.ui.components.SectionLabel
import app.erudit.ui.components.SecondaryButton
import app.erudit.ui.components.ThinProgress
import app.erudit.ui.components.Tile
import app.erudit.ui.components.plural
import app.erudit.ui.nav.rememberContent
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.domainColor

@Composable
fun CatalogScreen(onDomain: (String) -> Unit, onSearch: () -> Unit) {
    val content = rememberContent() ?: return Loading()
    val unlocks = rememberUnlocks(content)
    val totalLessons = content.lessons.size
    val totalQuestions = content.lessons.sumOf { it.questions.size }

    ListScreen(
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, contentDescription = "Поиск") }
        },
    ) {
        item {
            Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                Text("Курсы", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${plural(content.domains.size, "область", "области", "областей")} · " +
                        "${plural(totalLessons, "урок", "урока", "уроков")} · " +
                        plural(totalQuestions, "вопрос", "вопроса", "вопросов"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.extra.muted,
                )
            }
        }
        items(content.domains, key = { it.id }) { domain ->
            val color = domainColor(domain.color)
            val progress = unlocks.domainProgress(domain)
            Tile(
                onClick = { onDomain(domain.id) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DomainBadge(domain.icon, color)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(domain.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${plural(domain.courses.size, "курс", "курса", "курсов")} · ${plural(domain.lessons.size, "урок", "урока", "уроков")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.extra.muted,
                        )
                    }
                    if (domain.examId in unlocks.passed) {
                        Icon(Icons.Outlined.WorkspacePremium, contentDescription = "Область освоена", tint = color)
                    }
                }
                if (progress > 0f) {
                    Spacer(Modifier.height(14.dp))
                    ThinProgress(progress, color = color)
                }
            }
        }
    }
}

@Composable
fun DomainScreen(domainId: String, onBack: () -> Unit, onCourse: (String) -> Unit, onExam: (String) -> Unit, onPractice: (String) -> Unit) {
    val content = rememberContent() ?: return Loading()
    val domain = content.domainsById[domainId] ?: return Loading()
    val unlocks = rememberUnlocks(content)
    val color = domainColor(domain.color)

    ListScreen(onBack = onBack) {
        item {
            Column(Modifier.padding(top = 4.dp)) {
                DomainBadge(domain.icon, color, size = 56.dp)
                Spacer(Modifier.height(18.dp))
                Text(domain.title, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(10.dp))
                Text(domain.description, style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.muted)
                Spacer(Modifier.height(18.dp))
                ThinProgress(unlocks.domainProgress(domain), color = color)
                Spacer(Modifier.height(6.dp))
                val done = domain.lessons.count { it.id in unlocks.completed }
                Text("Пройдено $done из ${domain.lessons.size}", style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
            }
        }
        item { SectionLabel("Курсы") }
        items(domain.courses, key = { it.id }) { course ->
            CourseRow(course, unlocks, color) { onCourse(course.id) }
            Divider()
        }
        item {
            SectionLabel("Закрепление")
            SecondaryButton("Тренировка по области", onClick = { onPractice(domain.id) })
            Spacer(Modifier.height(10.dp))
            ExamRow(
                title = "Большой экзамен",
                subtitle = "40 вопросов по всем курсам области",
                passed = domain.examId in unlocks.passed,
                open = unlocks.domainExamOpen(domain),
                lockedHint = "Откроется после итоговых экзаменов всех курсов",
                color = color,
                onClick = { onExam(domain.examId) },
            )
        }
    }
}

@Composable
private fun CourseRow(course: Course, unlocks: Unlocks, color: Color, onClick: () -> Unit) {
    val progress = unlocks.courseProgress(course)
    val finished = course.examId in unlocks.passed
    NavRow(
        title = course.title,
        subtitle = buildString {
            append(plural(course.lessons.size, "урок", "урока", "уроков"))
            append(" · ")
            append(plural(course.stages.size, "ступень", "ступени", "ступеней"))
            if (progress > 0f) append(" · ${(progress * 100).toInt()}%")
        },
        onClick = onClick,
        leading = {
            ProgressDot(progress, finished, color)
        },
    )
}

@Composable
private fun ProgressDot(progress: Float, finished: Boolean, color: Color) {
    app.erudit.ui.components.ProgressRing(
        fraction = if (finished) 1f else progress,
        size = 32.dp,
        stroke = 3.dp,
        color = color,
    ) {
        if (finished) Icon(Icons.Outlined.School, null, tint = color, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun CourseScreen(
    courseId: String,
    onBack: () -> Unit,
    onLesson: (String) -> Unit,
    onExam: (String) -> Unit,
    onPractice: (String) -> Unit,
) {
    val content = rememberContent() ?: return Loading()
    val course = content.coursesById[courseId] ?: return Loading()
    val domain = content.domainsById.getValue(course.domainId)
    val unlocks = rememberUnlocks(content)
    val color = domainColor(domain.color)
    val next = unlocks.nextLesson(course)

    ListScreen(onBack = onBack) {
        item {
            Column(Modifier.padding(top = 4.dp)) {
                Text(domain.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
                Spacer(Modifier.height(10.dp))
                Text(course.title, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(10.dp))
                Text(course.description, style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.muted)
                Spacer(Modifier.height(18.dp))
                ThinProgress(unlocks.courseProgress(course), color = color)
                if (next != null) {
                    Spacer(Modifier.height(20.dp))
                    app.erudit.ui.components.PrimaryButton(
                        if (unlocks.courseProgress(course) == 0f) "Начать курс" else "Продолжить: ${next.title}",
                        onClick = { onLesson(next.id) },
                    )
                }
            }
        }
        course.stages.forEach { stage ->
            item(key = stage.id) { StageHeader(stage, unlocks, color) }
            items(stage.lessons, key = { it.id }) { lesson ->
                val open = unlocks.lessonOpen(lesson)
                val done = lesson.id in unlocks.completed
                NavRow(
                    title = lesson.title,
                    subtitle = "${lesson.minutes} мин · ${plural(lesson.questions.size, "вопрос", "вопроса", "вопросов")}",
                    onClick = { onLesson(lesson.id) },
                    enabled = open,
                    leading = {
                        Icon(
                            when {
                                !open -> Icons.Outlined.Lock
                                done -> Icons.Outlined.CheckCircle
                                else -> Icons.Outlined.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = when {
                                !open -> AppTheme.extra.faint
                                done -> color
                                else -> AppTheme.extra.line
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    },
                )
            }
            item(key = stage.id + "-exam") {
                Spacer(Modifier.height(8.dp))
                ExamRow(
                    title = "Экзамен ступени",
                    subtitle = "${minOf(15, stage.lessons.sumOf { it.questions.size })} вопросов · проходной балл 80%",
                    passed = stage.examId in unlocks.passed,
                    open = unlocks.stageExamOpen(stage),
                    lockedHint = if (unlocks.stageOpen(stage)) "Пройдите все уроки ступени" else "Сначала сдайте предыдущую ступень",
                    color = color,
                    onClick = { onExam(stage.examId) },
                )
            }
        }
        item {
            SectionLabel("Итог курса")
            ExamRow(
                title = "Итоговый экзамен",
                subtitle = "25 вопросов по всему курсу",
                passed = course.examId in unlocks.passed,
                open = unlocks.courseExamOpen(course),
                lockedHint = "Откроется после экзаменов всех ступеней",
                color = color,
                onClick = { onExam(course.examId) },
            )
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Тренировка по курсу", onClick = { onPractice(course.id) })
        }
    }
}

@Composable
private fun StageHeader(stage: Stage, unlocks: Unlocks, color: Color) {
    val done = stage.lessons.count { it.id in unlocks.completed }
    Column(Modifier.padding(top = 32.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "СТУПЕНЬ ${stage.index}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
            Spacer(Modifier.width(10.dp))
            Text("$done/${stage.lessons.size}", style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.faint)
            if (stage.examId in unlocks.passed) {
                Spacer(Modifier.width(10.dp))
                Pill("сдано", color = AppTheme.extra.success)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stage.title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun ExamRow(
    title: String,
    subtitle: String,
    passed: Boolean,
    open: Boolean,
    lockedHint: String,
    color: Color,
    onClick: () -> Unit,
) {
    Tile(onClick = if (open || passed) onClick else null, modifier = Modifier.fillMaxWidth(), padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
            IconBadge(
                when {
                    passed -> Icons.Outlined.WorkspacePremium
                    open -> Icons.Outlined.School
                    else -> Icons.Outlined.Lock
                },
                if (open || passed) color else AppTheme.extra.faint,
                size = 38.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    if (open || passed) subtitle else lockedHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.extra.muted,
                )
            }
            if (passed) Pill("сдан", color = AppTheme.extra.success)
        }
    }
}
