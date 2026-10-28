package app.erudit.ui.screens.trainers

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.erudit.domain.AnswerMatcher
import app.erudit.domain.Counters
import app.erudit.trainers.MentalMath
import app.erudit.trainers.NBack
import app.erudit.trainers.Series
import app.erudit.ui.components.AnswerFeedback
import app.erudit.ui.components.AnswerField
import app.erudit.ui.components.OptionHints
import app.erudit.ui.components.PrimaryButton
import app.erudit.ui.components.ScreenPadding
import app.erudit.ui.components.ThinProgress
import app.erudit.ui.markdown.Markdown
import app.erudit.ui.nav.rememberContent
import app.erudit.ui.screens.TrainerOutcome
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.Serif
import kotlinx.coroutines.delay

// ——— Общие элементы ———

@Composable
private fun GameColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun Keypad(onDigit: (Int) -> Unit, onDelete: () -> Unit, onSubmit: (() -> Unit)? = null) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d -> KeyButton(Modifier.weight(1f), onClick = { onDigit(d) }) { KeyText("$d") } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton(Modifier.weight(1f), onClick = onDelete) {
                Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Стереть")
            }
            KeyButton(Modifier.weight(1f), onClick = { onDigit(0) }) { KeyText("0") }
            if (onSubmit != null) {
                KeyButton(Modifier.weight(1f), onClick = onSubmit, filled = true) {
                    Icon(Icons.Outlined.Check, contentDescription = "Ответить")
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KeyText(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default, fontWeight = FontWeight.Medium))
}

@Composable
private fun KeyButton(modifier: Modifier, onClick: () -> Unit, filled: Boolean = false, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (filled) null else BorderStroke(1.dp, AppTheme.extra.line),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Таймер обратного отсчёта; вызывает [onEnd] по истечении. */
@Composable
private fun rememberCountdown(seconds: Int, onEnd: () -> Unit): Float {
    var left by remember { mutableLongStateOf(seconds * 1000L) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (left > 0) {
            delay(100)
            left = (seconds * 1000L - (System.currentTimeMillis() - start)).coerceAtLeast(0)
        }
        onEnd()
    }
    return left / 1000f
}

@Composable
private fun TimerBar(left: Float, total: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ThinProgress(left / total, modifier = Modifier.weight(1f), height = 3.dp)
        Text(
            "${kotlin.math.ceil(left).toInt()} с",
            style = MaterialTheme.typography.labelMedium,
            color = if (left < 10) AppTheme.extra.error else AppTheme.extra.muted,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun AnswerDisplay(text: String, color: Color) {
    Text(
        text.ifEmpty { " " },
        style = MaterialTheme.typography.displaySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default, fontWeight = FontWeight.Medium),
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ——— Устный счёт ———

@Composable
fun MathGame(onFinish: (TrainerOutcome) -> Unit) {
    var level by remember { mutableIntStateOf(1) }
    var problem by remember { mutableStateOf(MentalMath.generate(1)) }
    var input by remember { mutableStateOf("") }
    var correct by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf<Boolean?>(null) }
    var done by remember { mutableStateOf(false) }

    val left = rememberCountdown(60) {
        if (!done) {
            done = true
            onFinish(
                TrainerOutcome(
                    score = correct,
                    xp = correct.coerceAtMost(40),
                    summary = "Верно $correct, ошибок $wrong · уровень $level из ${MentalMath.MAX_LEVEL}",
                    records = mapOf(Counters.MATH_BEST to correct.toLong()),
                ),
            )
        }
    }

    LaunchedEffect(flash) {
        if (flash != null) {
            delay(250)
            flash = null
        }
    }

    fun nextProblem() {
        input = ""
        problem = MentalMath.generate(level)
    }

    val extra = AppTheme.extra
    val displayColor by animateColorAsState(
        when (flash) {
            true -> extra.success
            false -> extra.error
            null -> MaterialTheme.colorScheme.onBackground
        },
        label = "flash",
    )

    GameColumn {
        TimerBar(left, 60)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("Верно: $correct", style = MaterialTheme.typography.labelMedium, color = extra.muted, modifier = Modifier.weight(1f))
            Text("Уровень $level", style = MaterialTheme.typography.labelMedium, color = extra.muted)
        }
        Spacer(Modifier.weight(1f))
        Text(problem.text, style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        AnswerDisplay(input, displayColor)
        Spacer(Modifier.weight(1f))
        Keypad(
            onDigit = { d ->
                if (done) return@Keypad
                input += d
                val answer = problem.answer.toString()
                if (input == answer) {
                    correct++
                    streak++
                    flash = true
                    if (streak >= 3 && level < MentalMath.MAX_LEVEL) {
                        level++
                        streak = 0
                    }
                    nextProblem()
                } else if (input.length >= answer.length) {
                    wrong++
                    streak = 0
                    flash = false
                    if (level > 1) level--
                    nextProblem()
                }
            },
            onDelete = { input = input.dropLast(1) },
        )
    }
}

// ——— Числовые ряды ———

@Composable
fun SeriesGame(onFinish: (TrainerOutcome) -> Unit) {
    val total = 10
    var index by remember { mutableIntStateOf(0) }
    var series by remember { mutableStateOf(Series.generate(0)) }
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Boolean?>(null) }
    var correct by remember { mutableIntStateOf(0) }
    val extra = AppTheme.extra

    GameColumn {
        ThinProgress(index.toFloat() / total, height = 3.dp)
        Spacer(Modifier.height(8.dp))
        Text("Задача ${index + 1} из $total", style = MaterialTheme.typography.labelMedium, color = extra.muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        Text(
            series.shown.joinToString(",  ") + ",  ?",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default, fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        AnswerDisplay(
            if (result == false) "${input.ifEmpty { "—" }} → ${series.answer}" else input,
            when (result) {
                true -> extra.success
                false -> extra.error
                null -> MaterialTheme.colorScheme.onBackground
            },
        )
        if (result != null) {
            Spacer(Modifier.height(10.dp))
            Text(series.rule, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif), color = extra.muted, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        if (result == null) {
            Keypad(
                onDigit = { d -> if (input.length < 7) input += d },
                onDelete = { input = input.dropLast(1) },
                onSubmit = {
                    if (input.isNotEmpty()) {
                        val ok = input.toLongOrNull() == series.answer
                        if (ok) correct++
                        result = ok
                    }
                },
            )
        } else {
            PrimaryButton(if (index + 1 >= total) "Завершить" else "Далее", onClick = {
                if (index + 1 >= total) {
                    onFinish(
                        TrainerOutcome(
                            score = correct,
                            xp = correct * 2,
                            summary = "Решено $correct из $total рядов",
                            records = if (correct == total) mapOf(Counters.SEQ_PERFECT to 1L) else emptyMap(),
                        ),
                    )
                } else {
                    index++
                    series = Series.generate(index)
                    input = ""
                    result = null
                }
            })
        }
    }
}

// ——— Логические задачи ———

@Composable
fun LogicGame(onFinish: (TrainerOutcome) -> Unit) {
    val content = rememberContent() ?: return
    val questions = remember { content.logicPuzzles.shuffled().take(10) }
    if (questions.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var verdict by remember { mutableStateOf<Boolean?>(null) }
    var overridden by remember { mutableStateOf(false) }
    var correct by remember { mutableIntStateOf(0) }
    val q = questions[index]
    val order = remember(index) { if (q.keepOrder) q.options.indices.toList() else q.options.indices.shuffled() }
    val showOptions = remember(index) { AnswerMatcher.needsOptions(q) }
    val scroll = rememberScrollState()
    val extra = AppTheme.extra
    LaunchedEffect(verdict) { if (verdict != null) scroll.animateScrollTo(scroll.maxValue) }

    val submit = {
        if (verdict == null && input.isNotBlank()) {
            input = input.trim()
            val ok = AnswerMatcher.check(input, q, order)
            if (ok) correct++
            verdict = ok
        }
    }
    val next = {
        if (verdict != null) {
            if (index + 1 >= questions.size) {
                onFinish(
                    TrainerOutcome(
                        score = correct,
                        xp = correct * 3,
                        summary = "Решено $correct из ${questions.size} задач",
                        records = if (correct == questions.size) mapOf(Counters.LOGIC_PERFECT to 1L) else emptyMap(),
                    ),
                )
            } else {
                index++
                input = ""
                verdict = null
                overridden = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = ScreenPadding),
        ) {
            ThinProgress(index.toFloat() / questions.size, height = 3.dp)
            Spacer(Modifier.height(8.dp))
            Text("Задача ${index + 1} из ${questions.size}", style = MaterialTheme.typography.labelMedium, color = extra.muted)
            Spacer(Modifier.height(20.dp))
            Text(Markdown.inline(q.text), style = MaterialTheme.typography.titleLarge.copy(lineHeight = 28.sp))
            Spacer(Modifier.height(24.dp))
            if (showOptions) {
                OptionHints(q.options, order)
                Spacer(Modifier.height(16.dp))
            }
            androidx.compose.runtime.key(index) {
                AnswerField(
                    value = input,
                    onValueChange = { input = it },
                    verdict = verdict,
                    onSubmit = submit,
                    onNext = next,
                )
            }
            if (verdict != null) {
                AnswerFeedback(
                    correct = verdict == true,
                    overridden = overridden,
                    rightAnswer = q.options[q.correct],
                    explanation = q.explanation,
                    onOverride = {
                        verdict = true
                        overridden = true
                        correct++
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
                PrimaryButton(if (index + 1 >= questions.size) "Завершить" else "Далее", onClick = next)
            }
        }
    }
}

// ——— Запомни цифры ———

@Composable
fun SpanGame(onFinish: (TrainerOutcome) -> Unit) {
    var length by remember { mutableIntStateOf(3) }
    var digits by remember { mutableStateOf(List(3) { (0..9).random() }) }
    var showing by remember { mutableIntStateOf(-1) }
    var phase by remember { mutableStateOf("show") }
    var input by remember { mutableStateOf("") }
    var best by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(2) }
    var lastOk by remember { mutableStateOf<Boolean?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val extra = AppTheme.extra

    LaunchedEffect(round) {
        phase = "show"
        delay(700)
        for (i in digits.indices) {
            showing = i
            delay(850)
            showing = -1
            delay(200)
        }
        phase = "input"
    }

    fun nextRound(ok: Boolean) {
        lastOk = ok
        if (ok) {
            best = maxOf(best, length)
            length++
        } else {
            lives--
        }
        if (lives <= 0) {
            onFinish(
                TrainerOutcome(
                    score = best,
                    xp = best * 2,
                    summary = if (best > 0) "Самая длинная последовательность — $best цифр" else "Попробуйте ещё раз — начните с трёх цифр",
                    records = mapOf(Counters.SPAN_BEST to best.toLong()),
                ),
            )
            return
        }
        digits = List(length) { (0..9).random() }
        input = ""
        round++
    }

    GameColumn {
        Row(Modifier.fillMaxWidth()) {
            Text("Длина: $length", style = MaterialTheme.typography.labelMedium, color = extra.muted, modifier = Modifier.weight(1f))
            Text("Попыток: $lives", style = MaterialTheme.typography.labelMedium, color = extra.muted)
        }
        Spacer(Modifier.weight(1f))
        when (phase) {
            "show" -> {
                Text(
                    if (showing >= 0) "${digits[showing]}" else " ",
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 88.sp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    when (lastOk) {
                        true -> "Верно! Теперь длиннее"
                        false -> "Не совсем. Ещё попытка"
                        null -> "Запоминайте"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (lastOk) {
                        true -> extra.success
                        false -> extra.error
                        null -> extra.muted
                    },
                )
            }
            else -> {
                Text("Введите цифры по порядку", style = MaterialTheme.typography.bodyMedium, color = extra.muted)
                Spacer(Modifier.height(16.dp))
                AnswerDisplay(input.map { it }.joinToString(" "), MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(Modifier.weight(1f))
        if (phase == "input") {
            Keypad(
                onDigit = { d ->
                    if (input.length < length) input += d
                    if (input.length == length) nextRound(input == digits.joinToString(""))
                },
                onDelete = { input = input.dropLast(1) },
            )
        } else {
            Spacer(Modifier.height(268.dp))
        }
    }
}

// ——— N-back ———

@Composable
fun NBackGame(onFinish: (TrainerOutcome) -> Unit) {
    var n by remember { mutableIntStateOf(0) }
    if (n == 0) {
        GameColumn {
            Spacer(Modifier.weight(1f))
            Text("Выберите уровень", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Нажимайте «Совпадает», когда квадрат стоит там же, где был N шагов назад.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.extra.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                (1..4).forEach { level ->
                    KeyButton(Modifier.weight(1f), onClick = { n = level }) { KeyText("$level") }
                }
            }
            Spacer(Modifier.weight(1f))
        }
        return
    }
    NBackRun(n, onFinish)
}

@Composable
private fun NBackRun(n: Int, onFinish: (TrainerOutcome) -> Unit) {
    val trials = 20 + n
    val sequence = remember { NBack.generate(n, trials) }
    var step by remember { mutableIntStateOf(-1) }
    var visible by remember { mutableStateOf(false) }
    val responses = remember { mutableStateListOf<Int>() }
    var feedback by remember { mutableStateOf<Boolean?>(null) }
    val extra = AppTheme.extra

    LaunchedEffect(Unit) {
        delay(800)
        for (i in 0 until trials) {
            step = i
            feedback = null
            visible = true
            delay(1600)
            visible = false
            delay(700)
        }
        val targets = (n until trials).filter { sequence[it] == sequence[it - n] }.toSet()
        val hits = responses.count { it in targets }
        val falseAlarms = responses.count { it !in targets }
        val accuracy = NBack.accuracy(sequence, n, responses)
        onFinish(
            TrainerOutcome(
                score = accuracy,
                xp = NBack.xp(accuracy, n),
                summary = "$n-back: совпадений найдено $hits из ${targets.size}, ложных нажатий $falseAlarms",
                records = if (accuracy >= 80) mapOf(Counters.NBACK_LEVEL to n.toLong()) else emptyMap(),
            ),
        )
    }

    GameColumn {
        ThinProgress((step + 1).toFloat() / trials, height = 3.dp)
        Spacer(Modifier.height(8.dp))
        Text("$n-back · шаг ${step + 1} из $trials", style = MaterialTheme.typography.labelMedium, color = extra.muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth(0.82f)) {
            for (row in 0 until 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (col in 0 until 3) {
                        val cell = row * 3 + col
                        val lit = visible && step >= 0 && sequence[step] == cell
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    MaterialTheme.shapes.medium,
                                ),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val color = when (feedback) {
            true -> extra.success
            false -> extra.error
            null -> MaterialTheme.colorScheme.primary
        }
        Surface(
            onClick = {
                if (step >= n && step !in responses) {
                    responses += step
                    feedback = sequence[step] == sequence[step - n]
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = MaterialTheme.shapes.medium,
            color = color,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Совпадает", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ——— Таблица Шульте ———

@Composable
fun SchulteGame(onFinish: (TrainerOutcome) -> Unit) {
    val numbers = remember { (1..25).shuffled() }
    var next by remember { mutableIntStateOf(1) }
    var errors by remember { mutableIntStateOf(0) }
    var wrongCell by remember { mutableIntStateOf(-1) }
    var rightCell by remember { mutableIntStateOf(-1) }
    val start = remember { System.currentTimeMillis() }
    var elapsed by remember { mutableLongStateOf(0L) }
    val extra = AppTheme.extra

    LaunchedEffect(Unit) {
        while (next <= 25) {
            elapsed = System.currentTimeMillis() - start
            delay(100)
        }
    }
    LaunchedEffect(wrongCell, rightCell) {
        delay(180)
        wrongCell = -1
        rightCell = -1
    }

    GameColumn {
        Row(Modifier.fillMaxWidth()) {
            Text("Найдите: $next", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("%.1f с".format(elapsed / 1000f), style = MaterialTheme.typography.titleMedium, color = extra.muted)
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (row in 0 until 5) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 5) {
                        val idx = row * 5 + col
                        val value = numbers[idx]
                        Surface(
                            onClick = {
                                if (next > 25) return@Surface
                                if (value == next) {
                                    rightCell = idx
                                    next++
                                    if (next > 25) {
                                        val ds = ((System.currentTimeMillis() - start) / 100).toInt()
                                        onFinish(
                                            TrainerOutcome(
                                                score = ds,
                                                xp = if (ds < 400) 15 else 10,
                                                summary = "Ошибок: $errors",
                                                records = mapOf(Counters.SCHULTE_BEST to ds.toLong()),
                                            ),
                                        )
                                    }
                                } else {
                                    errors++
                                    wrongCell = idx
                                }
                            },
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                            shape = MaterialTheme.shapes.small,
                            color = when (idx) {
                                wrongCell -> extra.errorSoft
                                rightCell -> extra.successSoft
                                else -> MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(1.dp, extra.line),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "$value",
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default, fontWeight = FontWeight.Medium),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ——— Тест Струпа ———

private data class StroopColor(val name: String, val light: Color, val dark: Color)

private val stroopColors = listOf(
    StroopColor("красный", Color(0xFFD23F3A), Color(0xFFF0716B)),
    StroopColor("синий", Color(0xFF2E63D1), Color(0xFF7DA3F5)),
    StroopColor("зелёный", Color(0xFF23914F), Color(0xFF5FCB8A)),
    StroopColor("жёлтый", Color(0xFFD99A00), Color(0xFFF2C444)),
)

@Composable
fun StroopGame(onFinish: (TrainerOutcome) -> Unit) {
    var word by remember { mutableIntStateOf(0) }
    var ink by remember { mutableIntStateOf(1) }
    var correct by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf<Boolean?>(null) }
    var done by remember { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue < 1.5f }
    val extra = AppTheme.extra

    fun shuffle() {
        word = stroopColors.indices.random()
        ink = if (Math.random() < 0.75) stroopColors.indices.filter { it != word }.random() else word
    }

    LaunchedEffect(Unit) { shuffle() }
    LaunchedEffect(flash) {
        if (flash != null) {
            delay(200)
            flash = null
        }
    }

    val left = rememberCountdown(45) {
        if (!done) {
            done = true
            val score = (correct - wrong).coerceAtLeast(0)
            onFinish(
                TrainerOutcome(
                    score = score,
                    xp = (score / 2).coerceAtMost(30),
                    summary = "Верно $correct, ошибок $wrong",
                    records = mapOf(Counters.STROOP_BEST to score.toLong()),
                ),
            )
        }
    }

    GameColumn {
        TimerBar(left, 45)
        Spacer(Modifier.height(8.dp))
        Text("Выберите цвет букв", style = MaterialTheme.typography.labelMedium, color = extra.muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        val c = stroopColors[ink]
        Text(
            stroopColors[word].name.uppercase(),
            style = MaterialTheme.typography.displaySmall.copy(fontSize = 46.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Default, fontWeight = FontWeight.Bold),
            color = if (dark) c.dark else c.light,
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.size(10.dp).background(
            when (flash) {
                true -> extra.success
                false -> extra.error
                null -> Color.Transparent
            },
            MaterialTheme.shapes.small,
        ))
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stroopColors.indices.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { i ->
                        KeyButton(Modifier.weight(1f), onClick = {
                            if (done) return@KeyButton
                            if (i == ink) correct++ else wrong++
                            flash = i == ink
                            shuffle()
                        }) {
                            Text(stroopColors[i].name, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
