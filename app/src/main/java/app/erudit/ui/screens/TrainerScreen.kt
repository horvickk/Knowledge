package app.erudit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.erudit.trainers.Trainer
import app.erudit.trainers.Trainers
import app.erudit.ui.components.PrimaryButton
import app.erudit.ui.components.ScreenPadding
import app.erudit.ui.components.SecondaryButton
import app.erudit.ui.components.StatTile
import app.erudit.ui.components.TopBar
import app.erudit.ui.components.plural
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.screens.trainers.GeoTrainerScreen
import app.erudit.ui.screens.trainers.LogicGame
import app.erudit.ui.screens.trainers.MathGame
import app.erudit.ui.screens.trainers.NBackGame
import app.erudit.ui.screens.trainers.SchulteGame
import app.erudit.ui.screens.trainers.SeriesGame
import app.erudit.ui.screens.trainers.SpanGame
import app.erudit.ui.screens.trainers.StroopGame
import app.erudit.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Итог тренировки, который игра передаёт наверх. */
data class TrainerOutcome(
    val score: Int,
    val xp: Int,
    val summary: String,
    val records: Map<String, Long> = emptyMap(),
)

private enum class Phase { INTRO, PLAY, RESULT }

@Composable
fun TrainerScreen(trainerId: String, onBack: () -> Unit) {
    val trainer = Trainers.byId[trainerId] ?: return
    if (trainer.perCount) return GeoTrainerScreen(trainer, onBack)
    val container = LocalContainer.current
    val study = container.study
    var phase by remember { mutableStateOf(Phase.INTRO) }
    var outcome by remember { mutableStateOf<TrainerOutcome?>(null) }
    var previousBest by remember { mutableStateOf<Int?>(null) }
    var gained by remember { mutableStateOf<Int?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val history by remember { study.trainerHistory(trainer.id, 20) }.collectAsState(initial = emptyList())
    // Рекорд и число тренировок — по всей истории, а не по последним 20 результатам из графика.
    val stats by remember { study.trainerBests }.collectAsState(initial = emptyList())
    val mine = stats.firstOrNull { it.trainer == trainer.id }
    val best = if (trainer.lowerIsBetter) mine?.worst else mine?.best

    val finish: (TrainerOutcome) -> Unit = { o ->
        outcome = o
        // Рекорд сравнивается с тем, что было до этой тренировки: после записи она сама станет лучшей.
        previousBest = best
        gained = null
        phase = Phase.RESULT
        // Запись — в фоне приложения: «Готово» сразу после тренировки не должно её отменять.
        container.scope.launch {
            val g = study.recordTrainer(
                trainer.id, o.score, o.xp, o.records,
                lowerIsBetter = if (trainer.lowerIsBetter) o.records.keys else emptySet(),
            )
            withContext(Dispatchers.Main) { gained = g }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = if (phase == Phase.INTRO) null else trainer.title, onBack = onBack)
        when (phase) {
            Phase.INTRO -> TrainerIntro(trainer, best, mine?.sessions ?: 0, history.map { it.score }.reversed()) {
                round++
                phase = Phase.PLAY
            }
            Phase.PLAY -> Box(Modifier.fillMaxSize()) {
                androidx.compose.runtime.key(round) {
                    when (trainer.id) {
                        "math" -> MathGame(finish)
                        "series" -> SeriesGame(finish)
                        "logic" -> LogicGame(finish)
                        "span" -> SpanGame(finish)
                        "nback" -> NBackGame(finish)
                        "schulte" -> SchulteGame(finish)
                        "stroop" -> StroopGame(finish)
                    }
                }
            }
            Phase.RESULT -> TrainerResult(
                trainer = trainer,
                outcome = outcome!!,
                gained = gained,
                previousBest = previousBest,
                onAgain = { round++; phase = Phase.PLAY },
                onDone = onBack,
            )
        }
    }
}

@Composable
private fun TrainerIntro(trainer: Trainer, best: Int?, sessions: Int, scores: List<Int>, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
    ) {
        Text(trainer.kind.uppercase(), style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.muted)
        Spacer(Modifier.height(10.dp))
        Text(trainer.title, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(14.dp))
        Text(trainer.description, style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.muted)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(best?.let { trainer.formatScore(it) } ?: "—", "рекорд", Modifier.weight(1f))
            StatTile("$sessions", plural(sessions, "тренировка", "тренировки", "тренировок").substringAfter(' '), Modifier.weight(1f))
        }
        if (scores.size >= 2) {
            Spacer(Modifier.height(24.dp))
            Text("Последние результаты", style = MaterialTheme.typography.labelMedium, color = AppTheme.extra.muted)
            Spacer(Modifier.height(10.dp))
            MiniBars(scores, invert = trainer.lowerIsBetter)
        }
        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Начать", onClick = onStart)
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(20.dp))
    }
}

/**
 * Опыт за тренировку. Тренажёр приносит опыт раз в день — по лучшему результату дня, поэтому
 * начисленное [gained] бывает меньше заработанного [xp]; null — запись ещё не завершилась.
 */
@Composable
fun TrainerXp(xp: Int, gained: Int?, modifier: Modifier = Modifier) {
    val muted = AppTheme.extra.muted
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (gained == null) " " else "+$gained опыта", style = MaterialTheme.typography.bodyMedium, color = muted)
        if (gained != null && gained < xp) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (gained == 0) "Опыт за этот тренажёр сегодня уже получен. Превзойдите лучший результат дня — и получите разницу."
                else "Начислена разница с лучшим результатом дня: тренажёр приносит опыт раз в день.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun MiniBars(values: List<Int>, invert: Boolean = false, modifier: Modifier = Modifier) {
    val max = values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val min = values.minOrNull() ?: 0
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier.fillMaxWidth().height(72.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        values.takeLast(20).forEach { v ->
            val f = if (invert) {
                // Меньше — лучше: самый быстрый результат — самый высокий столбик.
                if (max == min) 1f else 0.25f + 0.75f * (max - v).toFloat() / (max - min)
            } else {
                (v.toFloat() / max).coerceAtLeast(0.04f)
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
private fun TrainerResult(
    trainer: Trainer,
    outcome: TrainerOutcome,
    gained: Int?,
    previousBest: Int?,
    onAgain: () -> Unit,
    onDone: () -> Unit,
) {
    val record = previousBest == null ||
        (if (trainer.lowerIsBetter) outcome.score < previousBest else outcome.score > previousBest)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        if (record) {
            Text("НОВЫЙ РЕКОРД", style = MaterialTheme.typography.labelSmall, color = AppTheme.extra.warm)
            Spacer(Modifier.height(10.dp))
        }
        Text(trainer.formatScore(outcome.score), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(10.dp))
        Text(outcome.summary, style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.muted)
        Spacer(Modifier.height(6.dp))
        TrainerXp(outcome.xp, gained)
        Spacer(Modifier.weight(1f))
        PrimaryButton("Ещё раз", onClick = onAgain)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("Готово", onClick = onDone)
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(20.dp))
    }
}
