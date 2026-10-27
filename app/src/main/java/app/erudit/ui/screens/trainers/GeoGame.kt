package app.erudit.ui.screens.trainers

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.erudit.trainers.Countries
import app.erudit.trainers.Country
import app.erudit.trainers.GeoScore
import app.erudit.trainers.Trainer
import app.erudit.ui.components.AnswerFeedback
import app.erudit.ui.components.AnswerField
import app.erudit.ui.components.PrimaryButton
import app.erudit.ui.components.ScreenPadding
import app.erudit.ui.components.SecondaryButton
import app.erudit.ui.components.StatTile
import app.erudit.ui.components.ThinProgress
import app.erudit.ui.components.TopBar
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.screens.MiniBars
import app.erudit.ui.screens.TrainerXp
import app.erudit.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private enum class GeoPhase { INTRO, PLAY, RESULT }

/** Страна, на которой ошиблись, и что ввёл пользователь. */
private data class GeoMiss(val country: Country, val typed: String)

private data class GeoResult(val count: Int, val correct: Int, val seconds: Int, val misses: List<GeoMiss>, val previousBest: Int?) {
    val score: Int get() = GeoScore.encode(correct, seconds)
    val xp: Int get() = (correct / 2).coerceAtMost(60)
    val record: Boolean get() = previousBest == null || score > previousBest
}

/** Тренажёры «Флаги мира» и «Столицы мира»: число стран выбирает пользователь, рекорд — для каждого числа свой. */
@Composable
fun GeoTrainerScreen(trainer: Trainer, onBack: () -> Unit) {
    val flags = trainer.id == "flags"
    val study = LocalContainer.current.study
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(GeoPhase.INTRO) }
    var countText by remember { mutableStateOf("20") }
    var edited by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<GeoResult?>(null) }
    var gained by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        val last = study.counters.first()[GeoScore.lastCountKey(trainer.id)]?.toInt()
        if (last != null && !edited) countText = last.toString()
    }
    val count = countText.toIntOrNull()?.takeIf { it in 1..Countries.all.size }
    val history by remember(count) {
        study.trainerHistory(GeoScore.key(trainer.id, count ?: 0), 20)
    }.collectAsState(initial = emptyList())
    val best = history.maxOfOrNull { it.score }

    val start = {
        if (count != null) {
            round++
            phase = GeoPhase.PLAY
            scope.launch { study.rememberValue(GeoScore.lastCountKey(trainer.id), count.toLong()) }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(title = if (phase == GeoPhase.INTRO) null else trainer.title, onBack = onBack)
        when (phase) {
            GeoPhase.INTRO -> GeoIntro(
                trainer = trainer,
                countText = countText,
                onCountChange = {
                    edited = true
                    countText = it.filter(Char::isDigit).take(3)
                },
                count = count,
                best = best,
                scores = history.map { GeoScore.correct(it.score) }.reversed(),
                onStart = start,
            )
            GeoPhase.PLAY -> androidx.compose.runtime.key(round) {
                val n = count ?: 20
                val previous = best
                GeoPlay(flags, Countries.all.take(n)) { correct, seconds, misses ->
                    val r = GeoResult(n, correct, seconds, misses, previous)
                    result = r
                    gained = null
                    phase = GeoPhase.RESULT
                    scope.launch {
                        // Опыт — раз в день на тренажёр, какое бы число стран ни выбиралось.
                        gained = study.recordTrainer(GeoScore.key(trainer.id, n), r.score, xp = r.xp, xpGroup = trainer.id)
                    }
                }
            }
            GeoPhase.RESULT -> GeoResultView(
                flags = flags,
                result = result!!,
                gained = gained,
                onAgain = { round++; phase = GeoPhase.PLAY },
                onChangeCount = { phase = GeoPhase.INTRO },
                onDone = onBack,
            )
        }
    }
}

@Composable
private fun GeoIntro(
    trainer: Trainer,
    countText: String,
    onCountChange: (String) -> Unit,
    count: Int?,
    best: Int?,
    scores: List<Int>,
    onStart: () -> Unit,
) {
    val extra = AppTheme.extra
    val total = Countries.all.size
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
    ) {
        Text(trainer.kind.uppercase(), style = MaterialTheme.typography.labelSmall, color = extra.muted)
        Spacer(Modifier.height(10.dp))
        Text(trainer.title, style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(14.dp))
        Text(trainer.description, style = MaterialTheme.typography.bodyLarge, color = extra.muted)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                if (best != null && count != null) "${GeoScore.correct(best)} из $count" else "—",
                if (best != null && count != null) "рекорд · ${GeoScore.time(GeoScore.seconds(best))}" else "рекорд",
                Modifier.weight(1f),
            )
            StatTile("${scores.size}", "тренировок", Modifier.weight(1f))
        }
        if (scores.size >= 2) {
            Spacer(Modifier.height(24.dp))
            Text("Последние результаты для ${count ?: "—"} стран", style = MaterialTheme.typography.labelMedium, color = extra.muted)
            Spacer(Modifier.height(10.dp))
            MiniBars(scores)
        }
        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(32.dp))
        Text("Сколько стран", style = MaterialTheme.typography.labelMedium, color = extra.muted)
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (count == null && countText.isNotEmpty()) extra.error else extra.line),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = countText,
                    onValueChange = onCountChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onStart() }),
                    modifier = Modifier.weight(1f),
                )
                Text("из $total", style = MaterialTheme.typography.bodyMedium, color = extra.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 20, 50, 100, total).forEach { n ->
                val selected = count == n
                Surface(
                    onClick = { onCountChange(n.toString()) },
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    border = if (selected) null else BorderStroke(1.dp, extra.line),
                ) {
                    Text(
                        if (n == total) "все" else "$n",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (count == null && countText.isNotEmpty()) "Введите число от 1 до $total" else "Берутся самые известные страны, порядок — случайный",
            style = MaterialTheme.typography.bodySmall,
            color = if (count == null && countText.isNotEmpty()) extra.error else extra.muted,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Начать", onClick = onStart, enabled = count != null)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GeoPlay(flags: Boolean, pool: List<Country>, onFinish: (correct: Int, seconds: Int, misses: List<GeoMiss>) -> Unit) {
    val order = remember { pool.shuffled() }
    var index by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    var overridden by remember { mutableStateOf(false) }
    var correct by remember { mutableIntStateOf(0) }
    val misses = remember { mutableStateListOf<GeoMiss>() }
    val startedAt = remember { System.currentTimeMillis() }
    var seconds by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    val extra = AppTheme.extra
    val country = order[index]

    LaunchedEffect(Unit) {
        while (true) {
            seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
            delay(500)
        }
    }
    LaunchedEffect(verdict) { if (verdict != null) scroll.animateScrollTo(scroll.maxValue) }

    val submit = {
        if (verdict == null && input.isNotBlank()) {
            input = input.trim()
            val ok = if (flags) {
                Countries.matches(input, country.names, Countries.all.filter { it != country }.flatMap { it.names })
            } else {
                Countries.matches(input, country.acceptedCapitals, Countries.all.filter { it != country }.flatMap { it.acceptedCapitals })
            }
            if (ok) correct++ else misses += GeoMiss(country, input)
            verdict = ok
        }
    }
    val next = {
        if (verdict != null) {
            if (index + 1 >= order.size) {
                onFinish(correct, ((System.currentTimeMillis() - startedAt) / 1000).toInt(), misses.toList())
            } else {
                index++
                input = ""
                verdict = null
                overridden = false
            }
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = ScreenPadding),
        ) {
            ThinProgress(index.toFloat() / order.size, height = 3.dp)
            Spacer(Modifier.height(8.dp))
            Row {
                Text("Страна ${index + 1} из ${order.size}", style = MaterialTheme.typography.labelMedium, color = extra.muted, modifier = Modifier.weight(1f))
                Text("Верно: $correct · ${GeoScore.time(seconds)}", style = MaterialTheme.typography.labelMedium, color = extra.muted)
            }
            Spacer(Modifier.height(24.dp))
            if (flags) {
                FlagImage(country.code, height = 170.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(24.dp))
                Text("Чей это флаг?", style = MaterialTheme.typography.titleLarge)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FlagImage(country.code, height = 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Столица страны", style = MaterialTheme.typography.labelMedium, color = extra.muted)
                }
                Spacer(Modifier.height(12.dp))
                Text(country.name, style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(20.dp))
            androidx.compose.runtime.key(index) {
                AnswerField(
                    value = input,
                    onValueChange = { input = it },
                    verdict = verdict,
                    onSubmit = submit,
                    onNext = next,
                    placeholder = if (flags) "Название страны" else "Столица",
                )
            }
            if (verdict != null) {
                AnswerFeedback(
                    correct = verdict == true,
                    overridden = overridden,
                    rightAnswer = if (flags) country.name else country.capital,
                    explanation = if (flags) "Столица — ${country.capital}." else null,
                    onOverride = {
                        verdict = true
                        overridden = true
                        correct++
                        misses.removeAll { it.country == country }
                    },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp)) {
            if (verdict == null) {
                PrimaryButton("Ответить", onClick = submit, enabled = input.isNotBlank())
            } else {
                PrimaryButton(if (index + 1 >= order.size) "Завершить" else "Далее", onClick = next)
            }
        }
    }
}

@Composable
private fun GeoResultView(
    flags: Boolean,
    result: GeoResult,
    gained: Int?,
    onAgain: () -> Unit,
    onChangeCount: () -> Unit,
    onDone: () -> Unit,
) {
    val extra = AppTheme.extra
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = ScreenPadding),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (result.record) {
                    Text("НОВЫЙ РЕКОРД", style = MaterialTheme.typography.labelSmall, color = extra.warm)
                    Spacer(Modifier.height(10.dp))
                }
                Text("${result.correct} из ${result.count}", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(10.dp))
                Text(
                    "За ${GeoScore.time(result.seconds)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = extra.muted,
                )
                Spacer(Modifier.height(4.dp))
                TrainerXp(result.xp, gained)
                if (!result.record && result.previousBest != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Рекорд для ${result.count} стран: ${GeoScore.format(result.previousBest, result.count)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = extra.muted,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(28.dp))
                PrimaryButton("Ещё раз", onClick = onAgain)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Другое число стран", onClick = onChangeCount)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Готово", onClick = onDone)
            }
        }
        if (result.misses.isNotEmpty()) {
            item {
                Text(
                    "Ошибки",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 36.dp, bottom = 8.dp),
                )
            }
            items(result.misses) { m ->
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    FlagImage(m.country.code, height = 28.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (flags) m.country.name else "${m.country.name}: ${m.country.capital}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text("Ваш ответ: ${m.typed}", style = MaterialTheme.typography.bodySmall, color = extra.muted)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(extra.line))
            }
        }
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(32.dp)) }
    }
}

/** Флаг из assets. Ширина подстраивается под пропорции флага, но не шире доступного места. */
@Composable
fun FlagImage(code: String, height: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(code) {
        runCatching { context.assets.open("flags/$code.png").use { BitmapFactory.decodeStream(it) } }.getOrNull()
    } ?: return
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val ratio = bitmap.width.toFloat() / bitmap.height
    val shape = RoundedCornerShape(if (height > 60.dp) 6.dp else 3.dp)
    BoxWithConstraints(modifier) {
        val width = min(maxWidth, height * ratio)
        val bordered = Modifier
            .size(width, width / ratio)
            .clip(shape)
            .let { if (code == "np") it else it.border(1.dp, AppTheme.extra.line, shape) }
        Image(image, contentDescription = "Флаг", contentScale = ContentScale.Fit, modifier = bordered)
    }
}
