package app.erudit.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.erudit.AppContainer
import app.erudit.data.content.Exam
import app.erudit.data.content.Question
import app.erudit.domain.SessionBuilder
import app.erudit.domain.Xp
import app.erudit.ui.components.PrimaryButton
import app.erudit.ui.components.ProgressRing
import app.erudit.ui.components.ScreenPadding
import app.erudit.ui.components.SecondaryButton
import app.erudit.ui.components.ThinProgress
import app.erudit.ui.components.Loading
import app.erudit.ui.components.plural
import app.erudit.ui.markdown.Markdown
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.Serif
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionKind(val key: String) {
    LESSON("lesson"), PRACTICE("practice"), REVIEW("review"), MISTAKES("mistakes"), BLITZ("blitz"), EXAM("exam");

    val instantFeedback: Boolean get() = this != EXAM

    companion object {
        fun of(key: String) = entries.first { it.key == key }
    }
}

data class SessionItem(val question: Question, val order: List<Int>)
data class SessionAnswer(val item: SessionItem, val chosen: Int, val correct: Boolean)

data class SessionResult(
    val correct: Int,
    val total: Int,
    val xp: Int,
    val exam: Exam? = null,
    val passed: Boolean? = null,
    val mistakes: List<SessionAnswer> = emptyList(),
    val note: String? = null,
) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}

data class SessionState(
    val loading: Boolean = true,
    val kind: SessionKind = SessionKind.PRACTICE,
    val title: String = "",
    val items: List<SessionItem> = emptyList(),
    val index: Int = 0,
    val chosen: Int? = null,
    val answers: List<SessionAnswer> = emptyList(),
    val timeLeft: Int? = null,
    val timeLimit: Int? = null,
    val result: SessionResult? = null,
    val finishing: Boolean = false,
    val emptyMessage: String? = null,
) {
    val current: SessionItem? get() = items.getOrNull(index)
    val correctCount: Int get() = answers.count { it.correct }
}

class SessionViewModel(
    private val container: AppContainer,
    private val kind: SessionKind,
    private val arg: String,
) : ViewModel() {
    private val _state = MutableStateFlow(SessionState(kind = kind))
    val state: StateFlow<SessionState> = _state
    private val study = container.study
    private var timer: Job? = null
    private var startedAt = 0L
    private var exam: Exam? = null
    private val requeued = HashSet<String>()

    init {
        start()
    }

    fun start() {
        timer?.cancel()
        requeued.clear()
        _state.value = SessionState(kind = kind)
        viewModelScope.launch {
            val content = container.awaitContent()
            val size = container.settings.settings.first().sessionSize
            var title = ""
            var empty: String? = null
            var timeLimit: Int? = null
            val questions: List<Question> = when (kind) {
                SessionKind.LESSON -> {
                    val lesson = content.lessonsById.getValue(arg)
                    title = lesson.title
                    SessionBuilder.lessonCheck(lesson)
                }
                SessionKind.PRACTICE -> {
                    title = SessionBuilder.scopeTitle(content, arg)
                    val lessons = SessionBuilder.lessonsInScope(content, arg)
                    val done = study.completedLessons.first()
                    val studied = lessons.filter { it.id in done }
                    val pool = (studied.ifEmpty { if (arg == "all") emptyList() else lessons }).flatMap { it.questions }
                    if (pool.isEmpty()) empty = "Пройдите хотя бы один урок — и здесь появятся вопросы для тренировки."
                    SessionBuilder.practice(pool, study.cardsFor(pool.map { it.id }), size)
                }
                SessionKind.REVIEW -> {
                    title = "Повторение"
                    val cards = study.dueCards(limit = maxOf(size, 20))
                    val qs = cards.mapNotNull { content.questionsById[it.questionId] }
                    if (qs.isEmpty()) empty = "На сегодня повторений нет. Карточки появляются после уроков и возвращаются по графику забывания."
                    qs.shuffled()
                }
                SessionKind.MISTAKES -> {
                    title = "Работа над ошибками"
                    val qs = study.mistakeCards(size).mapNotNull { content.questionsById[it.questionId] }
                    if (qs.isEmpty()) empty = "Ошибок нет — так держать."
                    qs.shuffled()
                }
                SessionKind.BLITZ -> {
                    title = "Блиц"
                    val done = study.completedLessons.first()
                    val learned = content.lessons.filter { it.id in done }.flatMap { it.questions }
                    timeLimit = 60
                    (if (learned.size >= 20) learned else content.lessons.flatMap { it.questions }).shuffled().take(80)
                }
                SessionKind.EXAM -> {
                    val e = content.exam(arg) ?: error("Нет экзамена $arg")
                    exam = e
                    title = e.title
                    timeLimit = e.timeLimitSec
                    SessionBuilder.exam(e)
                }
            }
            _state.value = SessionState(
                loading = false,
                kind = kind,
                title = title,
                items = questions.map { SessionItem(it, SessionBuilder.optionOrder(it)) },
                timeLeft = timeLimit,
                timeLimit = timeLimit,
                emptyMessage = empty,
            )
            startedAt = System.currentTimeMillis()
            if (timeLimit != null && questions.isNotEmpty()) startTimer()
        }
    }

    private fun startTimer() {
        timer = viewModelScope.launch {
            while (true) {
                delay(1000)
                val s = _state.value
                if (s.result != null || s.finishing) break
                val left = (s.timeLeft ?: break) - 1
                _state.update { it.copy(timeLeft = left) }
                if (left <= 0) {
                    finish()
                    break
                }
            }
        }
    }

    fun choose(option: Int) {
        val s = _state.value
        val item = s.current ?: return
        if (s.chosen != null || s.result != null || s.finishing) return
        val correct = option == item.question.correct
        val answer = SessionAnswer(item, option, correct)
        if (kind == SessionKind.EXAM) {
            _state.update { it.copy(chosen = option, answers = it.answers + answer) }
            viewModelScope.launch {
                delay(220)
                next()
            }
            return
        }
        var items = s.items
        if (kind == SessionKind.REVIEW && !correct && requeued.add(item.question.id)) {
            items = items + SessionItem(item.question, SessionBuilder.optionOrder(item.question))
        }
        _state.update { it.copy(chosen = option, answers = it.answers + answer, items = items) }
        viewModelScope.launch {
            study.recordAnswer(item.question, correct, isReview = kind == SessionKind.REVIEW)
        }
        if (kind == SessionKind.BLITZ) {
            viewModelScope.launch {
                delay(if (correct) 350 else 900)
                next()
            }
        }
    }

    fun next() {
        val s = _state.value
        if (s.result != null || s.finishing) return
        if (s.index + 1 >= s.items.size) finish()
        else _state.update { it.copy(index = it.index + 1, chosen = null) }
    }

    private fun finish() {
        val s = _state.value
        if (s.result != null || s.finishing) return
        timer?.cancel()
        _state.update { it.copy(finishing = true) }
        viewModelScope.launch {
            val answers = s.answers
            val correct = answers.count { it.correct }
            val result = when (kind) {
                SessionKind.EXAM -> {
                    val e = exam!!
                    answers.forEach { study.recordAnswer(it.item.question, it.correct, isReview = false) }
                    val total = s.items.size
                    val answeredIds = answers.map { it.item.question.id }.toSet()
                    val unanswered = s.items.filter { it.question.id !in answeredIds }.map { SessionAnswer(it, -1, false) }
                    val duration = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                    val outcome = study.recordExam(e, correct, total, duration)
                    SessionResult(
                        correct = correct,
                        total = total,
                        xp = outcome.xp,
                        exam = e,
                        passed = outcome.passed,
                        mistakes = answers.filter { !it.correct } + unanswered,
                        note = when {
                            outcome.firstPass -> "Экзамен сдан впервые: +${outcome.xp} опыта"
                            outcome.passed -> "Экзамен пересдан"
                            else -> "Нужно не меньше ${e.passPercent}% — повторите материал и попробуйте снова"
                        },
                    )
                }
                SessionKind.LESSON -> {
                    val content = container.awaitContent()
                    val lesson = content.lessonsById.getValue(arg)
                    val bonus = study.completeLesson(lesson, correct, answers.size)
                    SessionResult(
                        correct, answers.size, correct * Xp.CORRECT_ANSWER + bonus,
                        mistakes = answers.filter { !it.correct },
                        note = "Урок пройден. Все его вопросы добавлены в интервальные повторения.",
                    )
                }
                SessionKind.BLITZ -> {
                    study.recordBlitz(correct)
                    SessionResult(correct, answers.size, correct * Xp.CORRECT_ANSWER, mistakes = answers.filter { !it.correct })
                }
                else -> {
                    // Повторённые карточки считаются один раз: по последней попытке.
                    val last = answers.associateBy { it.item.question.id }.values
                    SessionResult(
                        last.count { it.correct }, last.size, correct * Xp.CORRECT_ANSWER,
                        mistakes = answers.filter { !it.correct }.distinctBy { it.item.question.id },
                    )
                }
            }
            study.checkAchievements()
            _state.update { it.copy(result = result) }
        }
    }

    fun abandon() {
        timer?.cancel()
    }
}

@Composable
fun SessionScreen(kindKey: String, arg: String, onClose: () -> Unit, onOpenLesson: (String) -> Unit) {
    val container = LocalContainer.current
    val kind = SessionKind.of(kindKey)
    val vm: SessionViewModel = viewModel(key = "$kindKey/$arg") { SessionViewModel(container, kind, arg) }
    val state by vm.state.collectAsState()
    var confirmExit by remember { mutableStateOf(false) }

    val inProgress = state.result == null && !state.finishing && state.answers.isNotEmpty()
    BackHandler(enabled = inProgress) { confirmExit = true }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(if (kind == SessionKind.EXAM) "Прервать экзамен?" else "Завершить сессию?") },
            text = {
                Text(
                    if (kind == SessionKind.EXAM) "Попытка не будет засчитана."
                    else "Ответы уже сохранены, прогресс не потеряется.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmExit = false; vm.abandon(); onClose() }) { Text("Выйти") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Продолжить") } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.loading -> Loading()
            state.result != null -> ResultView(state, onClose = onClose, onRetry = vm::start, onOpenLesson = onOpenLesson)
            state.finishing -> Loading()
            state.items.isEmpty() -> EmptySession(state.title, state.emptyMessage ?: "Вопросов нет.", onClose)
            else -> QuestionView(state, onClose = { if (inProgress) confirmExit = true else onClose() }, onChoose = vm::choose, onNext = vm::next)
        }
    }
}

@Composable
private fun SessionHeader(state: SessionState, onClose: () -> Unit) {
    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Закрыть") }
            Text(
                state.title,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.extra.muted,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            val left = state.timeLeft
            if (left != null) {
                val urgent = left <= 10
                Text(
                    "%d:%02d".format(left / 60, left % 60),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (urgent) AppTheme.extra.error else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 12.dp),
                )
            } else {
                Text(
                    "${state.index + 1} / ${state.items.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.extra.muted,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
        val fraction = if (state.kind == SessionKind.BLITZ) {
            (state.timeLeft ?: 0).toFloat() / (state.timeLimit ?: 1)
        } else {
            state.index.toFloat() / state.items.size.coerceAtLeast(1)
        }
        ThinProgress(fraction, modifier = Modifier.padding(horizontal = 12.dp), height = 3.dp)
    }
}

@Composable
private fun QuestionView(state: SessionState, onClose: () -> Unit, onChoose: (Int) -> Unit, onNext: () -> Unit) {
    val item = state.current ?: return
    Column(Modifier.fillMaxSize()) {
        SessionHeader(state, onClose)
        AnimatedContent(
            targetState = state.index,
            transitionSpec = { (slideInHorizontally { it / 6 } + fadeIn()) togetherWith fadeOut() },
            label = "question",
            modifier = Modifier.weight(1f),
        ) { index ->
            val current = state.items.getOrNull(index) ?: item
            val chosen = if (index == state.index) state.chosen else null
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding, vertical = 24.dp),
            ) {
                if (state.kind == SessionKind.BLITZ) {
                    Text(
                        "Верно: ${state.correctCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppTheme.extra.muted,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    Markdown.inline(current.question.text),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 30.sp),
                )
                Spacer(Modifier.height(28.dp))
                val reveal = chosen != null && state.kind.instantFeedback
                current.order.forEach { optionIndex ->
                    OptionTile(
                        text = current.question.options[optionIndex],
                        state = when {
                            !reveal && chosen == optionIndex -> OptionState.SELECTED
                            !reveal -> OptionState.IDLE
                            optionIndex == current.question.correct -> OptionState.CORRECT
                            optionIndex == chosen -> OptionState.WRONG
                            else -> OptionState.DIMMED
                        },
                        enabled = chosen == null,
                        onClick = { onChoose(optionIndex) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                AnimatedVisibility(reveal && state.kind != SessionKind.BLITZ, enter = fadeIn(), exit = fadeOut()) {
                    val correct = chosen == current.question.correct
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(
                            if (correct) "Верно" else "Неверно",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (correct) AppTheme.extra.success else AppTheme.extra.error,
                        )
                        current.question.explanation?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                Markdown.inline(it),
                                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif),
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }
        }
        if (state.chosen != null && state.kind.instantFeedback && state.kind != SessionKind.BLITZ) {
            Box(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = ScreenPadding, vertical = 16.dp),
            ) {
                PrimaryButton(if (state.index + 1 >= state.items.size) "Завершить" else "Далее", onClick = onNext)
            }
        } else {
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(86.dp))
        }
    }
}

enum class OptionState { IDLE, SELECTED, CORRECT, WRONG, DIMMED }

@Composable
fun OptionTile(text: String, state: OptionState, enabled: Boolean, onClick: () -> Unit) {
    val extra = AppTheme.extra
    val (border, bg, fg) = when (state) {
        OptionState.IDLE -> Triple(extra.line, MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)
        OptionState.SELECTED -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
        OptionState.CORRECT -> Triple(extra.success, extra.successSoft, MaterialTheme.colorScheme.onSurface)
        OptionState.WRONG -> Triple(extra.error, extra.errorSoft, MaterialTheme.colorScheme.onSurface)
        OptionState.DIMMED -> Triple(extra.line, MaterialTheme.colorScheme.surface, extra.faint)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = bg,
        border = BorderStroke(if (state == OptionState.IDLE || state == OptionState.DIMMED) 1.dp else 1.5.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Markdown.inline(text), style = MaterialTheme.typography.bodyLarge, color = fg, modifier = Modifier.weight(1f))
            when (state) {
                OptionState.CORRECT -> Icon(Icons.Outlined.Check, null, tint = extra.success, modifier = Modifier.size(20.dp))
                OptionState.WRONG -> Icon(Icons.Outlined.Close, null, tint = extra.error, modifier = Modifier.size(20.dp))
                else -> Unit
            }
        }
    }
}

@Composable
private fun EmptySession(title: String, message: String, onClose: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.muted)
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Понятно", onClick = onClose)
    }
}

@Composable
private fun ResultView(state: SessionState, onClose: () -> Unit, onRetry: () -> Unit, onOpenLesson: (String) -> Unit) {
    val result = state.result ?: return
    val extra = AppTheme.extra
    val content = LocalContainer.current.content.collectAsState().value
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ScreenPadding, vertical = 0.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val ringColor = when (result.passed) {
                    true -> extra.success
                    false -> extra.error
                    null -> MaterialTheme.colorScheme.primary
                }
                ProgressRing(result.percent / 100f, size = 132.dp, stroke = 8.dp, color = ringColor) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (state.kind == SessionKind.BLITZ) "${result.correct}" else "${result.percent}%",
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        if (state.kind == SessionKind.BLITZ) {
                            Text("верных", style = MaterialTheme.typography.bodySmall, color = extra.muted)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    when {
                        result.passed == true -> "Экзамен сдан"
                        result.passed == false -> "Пока не сдано"
                        state.kind == SessionKind.LESSON -> "Урок пройден"
                        result.percent >= 90 -> "Отлично"
                        result.percent >= 70 -> "Хороший результат"
                        else -> "Есть над чем поработать"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${result.correct} из ${result.total} верно · +${result.xp} опыта",
                    style = MaterialTheme.typography.bodyLarge,
                    color = extra.muted,
                )
                result.note?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = extra.muted)
                }
                Spacer(Modifier.height(32.dp))
                PrimaryButton("Готово", onClick = onClose)
                if (state.kind != SessionKind.LESSON) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(if (state.kind == SessionKind.EXAM) "Пересдать" else "Ещё раз", onClick = onRetry)
                }
            }
        }
        if (result.mistakes.isNotEmpty()) {
            item {
                Text(
                    "Разбор ошибок · ${plural(result.mistakes.size, "вопрос", "вопроса", "вопросов")}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 40.dp, bottom = 12.dp),
                )
            }
            items(result.mistakes) { m ->
                val q = m.item.question
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(Markdown.inline(q.text), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (m.chosen >= 0) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Close, null, tint = extra.error, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(Markdown.inline(q.options[m.chosen]), style = MaterialTheme.typography.bodyMedium, color = extra.muted)
                        }
                    } else {
                        Text("Нет ответа", style = MaterialTheme.typography.bodyMedium, color = extra.muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Check, null, tint = extra.success, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Markdown.inline(q.options[q.correct]), style = MaterialTheme.typography.bodyMedium)
                    }
                    q.explanation?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(Markdown.inline(it), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Serif), color = extra.muted)
                    }
                    val lesson = content?.lessonsById?.get(q.lessonId)
                    if (lesson != null) {
                        TextButton(onClick = { onOpenLesson(lesson.id) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("Урок: ${lesson.title}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(extra.line))
            }
        }
        item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(40.dp)) }
    }
}
