package app.erudit.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.erudit.data.content.Content
import app.erudit.data.content.Exam
import app.erudit.ui.components.Divider
import app.erudit.ui.components.EmptyNote
import app.erudit.ui.components.ListScreen
import app.erudit.ui.components.Loading
import app.erudit.ui.components.Pill
import app.erudit.ui.components.PrimaryButton
import app.erudit.ui.components.SectionLabel
import app.erudit.ui.components.StatTile
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.nav.rememberContent
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.domainColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))

fun formatDate(millis: Long): String =
    dateFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** Область, к которой относится экзамен — для цвета и подписи. */
fun examDomainId(content: Content, examId: String): String? {
    val owner = examId.substringBefore(':')
    return content.domainsById[owner]?.id ?: content.coursesById[owner]?.domainId
}

@Composable
fun ExamIntroScreen(examId: String, onBack: () -> Unit, onStart: () -> Unit) {
    val content = rememberContent() ?: return Loading()
    val exam = content.exam(examId) ?: return Loading()
    val unlocks = rememberUnlocks(content)
    val attempts by LocalContainer.current.study.examAttempts.collectAsState(initial = emptyList())
    val mine = attempts.filter { it.examId == examId }
    val best = mine.maxOfOrNull { if (it.total == 0) 0 else it.correct * 100 / it.total }
    val color = examDomainId(content, examId)?.let { domainColor(content.domainsById.getValue(it).color) }
        ?: MaterialTheme.colorScheme.primary
    val open = unlocks.examOpen(examId) || examId in unlocks.passed

    ListScreen(onBack = onBack) {
        item {
            Column(Modifier.padding(top = 4.dp)) {
                Text(exam.subtitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
                Spacer(Modifier.height(10.dp))
                Text(exam.title, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(
                    when (exam.kind) {
                        Exam.Kind.STAGE -> "Проверка знаний по всем урокам ступени. После сдачи откроется следующая ступень."
                        Exam.Kind.COURSE -> "Итоговая проверка по всему курсу. Сдача экзамена — свидетельство, что курс освоен."
                        Exam.Kind.DOMAIN -> "Самая серьёзная проверка: вопросы из всех курсов области."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppTheme.extra.muted,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("${exam.questionCount}", "вопросов", Modifier.weight(1f))
                    StatTile("${exam.timeLimitSec / 60} мин", "на всё", Modifier.weight(1f))
                    StatTile("${exam.passPercent}%", "чтобы сдать", Modifier.weight(1f))
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    "Подсказок во время экзамена нет — разбор ошибок будет в конце. Ответы влияют на интервальные повторения.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.extra.muted,
                )
                Spacer(Modifier.height(28.dp))
                PrimaryButton(if (mine.isEmpty()) "Начать экзамен" else "Пройти ещё раз", onClick = onStart, enabled = open && exam.pool.isNotEmpty())
                if (!open) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Экзамен пока закрыт. Последовательное обучение можно отключить в настройках.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.extra.muted,
                    )
                }
            }
        }
        if (mine.isNotEmpty()) {
            item { SectionLabel("Попытки · лучший результат $best%") }
            items(mine) { a -> AttemptRow(formatDate(a.timestamp), a.correct, a.total, a.passed) }
        }
    }
}

@Composable
private fun AttemptRow(date: String, correct: Int, total: Int, passed: Boolean, title: String? = null) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (title != null) Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(date, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
            }
            Text(
                "$correct/$total",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 12.dp),
            )
            Pill(if (passed) "сдан" else "не сдан", color = if (passed) AppTheme.extra.success else AppTheme.extra.error)
        }
        Divider()
    }
}

@Composable
fun ExamHistoryScreen(onBack: () -> Unit, onExam: (String) -> Unit) {
    val content = rememberContent() ?: return Loading()
    val attempts by LocalContainer.current.study.examAttempts.collectAsState(initial = emptyList())
    ListScreen(title = "Экзамены", onBack = onBack) {
        if (attempts.isEmpty()) {
            item { EmptyNote("Вы ещё не сдавали экзамены. Они есть в конце каждой ступени и курса.") }
        }
        items(attempts, key = { it.id }) { a ->
            val exam = content.exam(a.examId)
            androidx.compose.material3.Surface(
                onClick = { onExam(a.examId) },
                color = androidx.compose.ui.graphics.Color.Transparent,
            ) {
                AttemptRow(
                    date = formatDate(a.timestamp),
                    correct = a.correct,
                    total = a.total,
                    passed = a.passed,
                    title = exam?.let { "${it.subtitle}: ${it.title.lowercase()}" } ?: a.examId,
                )
            }
        }
    }
}
