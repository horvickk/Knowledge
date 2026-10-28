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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Shuffle
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
import androidx.compose.ui.unit.dp
import app.erudit.domain.Counters
import app.erudit.trainers.Trainers
import app.erudit.ui.components.DomainBadge
import app.erudit.ui.components.IconBadge
import app.erudit.ui.components.ListScreen
import app.erudit.ui.components.Loading
import app.erudit.ui.components.NavRow
import app.erudit.ui.components.PrimaryButton
import app.erudit.ui.components.SectionLabel
import app.erudit.ui.components.Tile
import app.erudit.ui.components.plural
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.nav.rememberContent
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.domainColor

@Composable
fun PracticeScreen(
    onReview: () -> Unit,
    onPractice: (String) -> Unit,
    onMistakes: () -> Unit,
    onBlitz: () -> Unit,
    onTrainer: (String) -> Unit,
) {
    val content = rememberContent() ?: return Loading()
    val study = LocalContainer.current.study
    val unlocks = rememberUnlocks(content)
    val due by remember { study.dueCount() }.collectAsStateWithLifecycle(initialValue = 0)
    val cards by study.cardCount.collectAsState(initial = 0)
    val mature by study.matureCount.collectAsState(initial = 0)
    val mistakes by study.mistakeCount.collectAsState(initial = 0)
    val counters by study.counters.collectAsState(initial = emptyMap())
    val bests by study.trainerBests.collectAsState(initial = emptyList())

    ListScreen {
        item {
            Text("Практика", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "Повторение по кривой забывания, свободная практика и тренажёры мышления.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.extra.muted,
            )
        }
        item {
            Spacer(Modifier.height(24.dp))
            Tile(Modifier.fillMaxWidth(), padding = 18.dp) {
                Text("Интервальное повторение", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (cards == 0) "Карточки появятся после первого пройденного урока."
                    else "В колоде ${plural(cards, "карточка", "карточки", "карточек")}, из них прочно усвоено $mature.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.extra.muted,
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    if (due > 0) "Повторить · $due" else "Повторений нет",
                    onClick = onReview,
                    enabled = due > 0,
                )
            }
        }
        item {
            SectionLabel("Режимы")
            NavRow(
                title = "Смешанная тренировка",
                subtitle = "Вопросы из всего пройденного, слабые — чаще",
                onClick = { onPractice("all") },
                leading = { IconBadge(Icons.Outlined.Shuffle, MaterialTheme.colorScheme.primary) },
            )
            NavRow(
                title = "Работа над ошибками",
                subtitle = if (mistakes > 0) plural(mistakes, "вопрос", "вопроса", "вопросов") + " с последней ошибкой" else "Ошибок пока нет",
                onClick = onMistakes,
                enabled = mistakes > 0,
                leading = { IconBadge(Icons.Outlined.ErrorOutline, AppTheme.extra.error) },
            )
            NavRow(
                title = "Блиц",
                subtitle = "90 секунд, короткие ответы · рекорд ${counters[Counters.BLITZ_BEST] ?: 0}",
                onClick = onBlitz,
                leading = { IconBadge(Icons.Outlined.Bolt, AppTheme.extra.warm) },
            )
        }
        item { SectionLabel("Тренажёры интеллекта") }
        Trainers.all.chunked(2).forEach { pair ->
            item(key = "tr-" + pair.first().id) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                    pair.forEach { t ->
                        val best = bests.firstOrNull { it.trainer == t.id }
                        Tile(onClick = { onTrainer(t.id) }, modifier = Modifier.weight(1f), padding = 14.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(t.icon, null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.weight(1f))
                                Text(t.kind, style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.muted)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(t.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                when {
                                    t.perCount -> "Свой рекорд для каждого числа стран"
                                    best != null -> "Рекорд: " + t.formatScore(if (t.lowerIsBetter) best.worst else best.best)
                                    else -> "Не пройден"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AppTheme.extra.muted,
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item { SectionLabel("По областям") }
        content.domains.forEach { d ->
            item(key = "pd-" + d.id) {
                val done = d.lessons.count { it.id in unlocks.completed }
                NavRow(
                    title = d.title,
                    subtitle = if (done == 0) "Уроки ещё не пройдены — вопросы из всей области" else "Пройдено уроков: $done",
                    onClick = { onPractice(d.id) },
                    leading = { DomainBadge(d.icon, domainColor(d.color), size = 36.dp) },
                )
            }
        }
        item { Spacer(Modifier.width(1.dp)) }
    }
}
