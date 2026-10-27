package app.erudit.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.erudit.domain.AnswerMatcher
import app.erudit.domain.ExamOutcome
import app.erudit.domain.SessionBuilder
import app.erudit.domain.Xp
import app.erudit.ui.components.AcceptButton
import app.erudit.ui.components.AnswerFeedback
import app.erudit.ui.components.AnswerField
import app.erudit.ui.components.OptionHints
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

enum class SessionKind(val key: String) {
    LESSON("lesson"), PRACTICE("practice"), REVIEW("review"), MISTAKES("mistakes"), BLITZ("blitz"), EXAM("exam");

    companion object {
        fun of(key: String) = entries.first { it.key == key }
    }
}

/** Вопрос сессии. Варианты показываются подсказкой, только если без них вопрос не имеет смысла. */
data class SessionItem(
    val question: Question,
    val order: List<Int>,
    val showOptions: Boolean = AnswerMatcher.needsOptions(question),
)

/**
 * Ответ, введённый с клавиатуры: [typed] пустой — вопрос остался без ответа.
 * [accepted] — пользователь сам засчитал ответ как верный.
 */
data class SessionAnswer(val item: SessionItem, val typed: String, val correct: Boolean, val accepted: Boolean = false)

data class SessionResult(
    val correct: Int,
    val total: Int,
    val xp: Int,
    val exam: Exam? = null,
    val passed: Boolean? = null,
    /** Неверные ответы; засчитанные пользователем в разборе остаются в списке с отметкой. */
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
    val input: String = "",
    /** null — ответ ещё не проверен. */
    val verdict: Boolean? = null,
    /** Ответ не совпал, но пользователь засчитал его сам. */
    val overridden: Boolean = false,
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

    /** Сколько ответов уже записано в статистику: ответ записывается, когда пользователь идёт дальше. */
    private var recorded = 0

    /** Запись ответов идёт по очереди; итог сессии ждёт её, чтобы показать начисленный опыт. */
    private var recording: Job? = null
    private var earned = AtomicInteger()

    /** Попытка экзамена в базе — её пересчитывают, когда пользователь засчитывает ответы. */
    private var attemptId = 0L
    private var examAnswersSaved = false
    private val revising = Mutex()

    init {
        start()
    }

    fun start() {
        timer?.cancel()
        saveExamAnswers()
        requeued.clear()
        recorded = 0
        earned = AtomicInteger()
        attemptId = 0
        examAnswersSaved = false
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
                    // Ответ печатается, поэтому в блиц идут вопросы с короткими ответами.
                    val short = { q: Question -> !AnswerMatcher.needsOptions(q) && AnswerMatcher.tokens(q.options[q.correct]).size <= 3 }
                    val done = study.completedLessons.first()
                    timeLimit = BLITZ_SECONDS
                    withContext(Dispatchers.Default) {
                        val learned = content.lessons.filter { it.id in done }.flatMap { it.questions }.filter(short)
                        (if (learned.size >= 20) learned else content.lessons.flatMap { it.questions }.filter(short)).shuffled().take(80)
                    }
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

    fun onInput(text: String) {
        if (_state.value.verdict == null) _state.update { it.copy(input = text) }
    }

    fun submit() {
        val s = _state.value
        val item = s.current ?: return
        if (s.verdict != null || s.result != null || s.finishing || s.input.isBlank()) return
        val typed = s.input.trim()
        val correct = AnswerMatcher.check(typed, item.question, item.order)
        if (kind == SessionKind.EXAM) {
            // На экзамене без подсказок: ответ принят — сразу следующий вопрос, разбор в конце.
            _state.update { it.copy(answers = it.answers + SessionAnswer(item, typed, correct)) }
            if (s.index + 1 >= s.items.size) finish()
            else _state.update { it.copy(index = it.index + 1, input = "") }
            return
        }
        var items = s.items
        if (kind == SessionKind.REVIEW && !correct && requeued.add(item.question.id)) {
            items = items + SessionItem(item.question, SessionBuilder.optionOrder(item.question))
        }
        _state.update { it.copy(input = typed, verdict = correct, answers = it.answers + SessionAnswer(item, typed, correct), items = items) }
        if (kind == SessionKind.BLITZ && correct) {
            viewModelScope.launch {
                delay(450)
                val now = _state.value
                if (now.index == s.index && now.verdict == true) next()
            }
        }
    }

    /** Ответ не совпал с эталоном, но пользователь уверен, что ответил верно, — засчитываем. */
    fun acceptAnyway() {
        val s = _state.value
        if (s.verdict != false || s.result != null || s.finishing) return
        val last = s.answers.lastOrNull() ?: return
        val id = last.item.question.id
        var items = s.items
        if (kind == SessionKind.REVIEW && id in requeued) {
            val again = items.indexOfLast { it.question.id == id }
            if (again > s.index) {
                items = items.filterIndexed { i, _ -> i != again }
                requeued.remove(id)
            }
        }
        _state.update {
            it.copy(verdict = true, overridden = true, items = items, answers = it.answers.dropLast(1) + last.copy(correct = true))
        }
    }

    fun next() {
        val s = _state.value
        if (s.result != null || s.finishing || s.verdict == null) return
        recordPending()
        if (s.index + 1 >= s.items.size) finish()
        else _state.update { it.copy(index = it.index + 1, input = "", verdict = null, overridden = false) }
    }

    /**
     * Записывает в статистику ответы, после которых пользователь пошёл дальше: до этого ответ
     * ещё можно засчитать вручную. Пишем в фоне приложения, чтобы запись пережила закрытие экрана.
     */
    private fun recordPending() {
        if (kind == SessionKind.EXAM) return
        val answers = _state.value.answers
        val pending = answers.drop(recorded)
        recorded = answers.size
        if (pending.isEmpty()) return
        val previous = recording
        val sink = earned
        recording = container.scope.launch {
            previous?.join()
            pending.forEach { sink.addAndGet(study.recordAnswer(it.item.question, it.correct, isReview = kind == SessionKind.REVIEW)) }
        }
    }

    private fun finish() {
        val s = _state.value
        if (s.result != null || s.finishing) return
        timer?.cancel()
        recordPending()
        _state.update { it.copy(finishing = true) }
        viewModelScope.launch {
            val answers = s.answers
            val correct = answers.count { it.correct }
            recording?.join()
            val xp = earned.get()
            val result = when (kind) {
                SessionKind.EXAM -> {
                    val e = exam!!
                    // В интервальные повторения ответы попадут после разбора (saveExamAnswers),
                    // чтобы засчитанные пользователем ответы не записались как ошибки.
                    val total = s.items.size
                    val answeredIds = answers.map { it.item.question.id }.toSet()
                    val unanswered = s.items.filter { it.question.id !in answeredIds }.map { SessionAnswer(it, "", false) }
                    val duration = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                    val outcome = study.recordExam(e, correct, total, duration)
                    attemptId = outcome.attemptId
                    SessionResult(
                        correct = correct,
                        total = total,
                        xp = outcome.xp + study.answerXp(answers.map { it.item.question to it.correct }),
                        exam = e,
                        passed = outcome.passed,
                        mistakes = answers.filter { !it.correct } + unanswered,
                        note = examNote(e, outcome),
                    )
                }
                SessionKind.LESSON -> {
                    val content = container.awaitContent()
                    val lesson = content.lessonsById.getValue(arg)
                    val bonus = study.completeLesson(lesson, correct, answers.size)
                    SessionResult(
                        correct, answers.size, xp + bonus,
                        mistakes = answers.filter { !it.correct },
                        note = if (bonus > 0) "Урок пройден. Все его вопросы добавлены в интервальные повторения." else xpNote(correct, xp),
                    )
                }
                SessionKind.BLITZ -> {
                    study.recordBlitz(correct)
                    SessionResult(correct, answers.size, xp, mistakes = answers.filter { !it.correct }, note = xpNote(correct, xp))
                }
                else -> {
                    // Повторённые карточки считаются один раз: по последней попытке.
                    val last = answers.associateBy { it.item.question.id }.values
                    SessionResult(
                        last.count { it.correct }, last.size, xp,
                        mistakes = answers.filter { !it.correct }.distinctBy { it.item.question.id },
                        note = xpNote(correct, xp),
                    )
                }
            }
            study.checkAchievements()
            _state.update { it.copy(result = result) }
        }
    }

    fun abandon() {
        timer?.cancel()
        recordPending()
    }

    /**
     * В разборе экзамена пользователь засчитал ответ на вопрос [questionId] как верный:
     * пересчитываем процент, сдачу и бонус, обновляем попытку в базе.
     */
    fun acceptExamAnswer(questionId: String) {
        val e = exam ?: return
        val s = _state.value
        val result = s.result ?: return
        val i = s.answers.indexOfFirst { it.item.question.id == questionId && !it.correct && it.typed.isNotBlank() }
        if (i < 0 || examAnswersSaved) return
        val answers = s.answers.toMutableList().also { it[i] = it[i].copy(correct = true, accepted = true) }
        val correct = answers.count { it.correct }
        _state.update {
            it.copy(
                answers = answers,
                result = result.copy(
                    correct = correct,
                    // Сдачу видно сразу, бонус и пометка придут из базы.
                    passed = result.passed == true || correct * 100 >= e.passPercent * result.total,
                    mistakes = result.mistakes.map { m ->
                        if (m.item.question.id == questionId) m.copy(correct = true, accepted = true) else m
                    },
                ),
            )
        }
        viewModelScope.launch {
            revising.withLock {
                val now = _state.value.answers
                val latest = now.count { it.correct }
                val outcome = study.reviseExam(attemptId, e, latest)
                val answerXp = study.answerXp(now.map { it.item.question to it.correct })
                _state.update { st ->
                    // Пока шёл пересчёт, пользователь мог засчитать ещё ответ — тогда ждём следующего.
                    if (st.answers.count { it.correct } != latest) st
                    else st.copy(result = st.result?.copy(xp = outcome.xp + answerXp, passed = outcome.passed, note = examNote(e, outcome)))
                }
            }
        }
    }

    /** Ответы экзамена с учётом засчитанных — в интервальные повторения, один раз. */
    private fun saveExamAnswers() {
        if (kind != SessionKind.EXAM || examAnswersSaved || _state.value.result == null) return
        examAnswersSaved = true
        val answers = _state.value.answers
        container.scope.launch {
            answers.forEach { study.recordAnswer(it.item.question, it.correct, isReview = false) }
        }
    }

    override fun onCleared() {
        saveExamAnswers()
        super.onCleared()
    }

    /** Пояснение, если опыта меньше, чем верных ответов: за уже известные вопросы опыт не начисляется. */
    private fun xpNote(correct: Int, xp: Int): String? =
        if (xp < correct * Xp.CORRECT_ANSWER) {
            "Опыт начисляется за первый верный ответ на вопрос и за повторения, срок которых наступил. За уже известные вопросы его нет."
        } else {
            null
        }

    private fun examNote(e: Exam, outcome: ExamOutcome): String = when {
        outcome.firstPass -> "Экзамен сдан впервые: +${outcome.xp} опыта"
        outcome.passed -> "Экзамен пересдан"
        else -> "Нужно не меньше ${e.passPercent}% — повторите материал и попробуйте снова"
    }

    companion object {
        const val BLITZ_SECONDS = 90
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
            state.result != null -> ResultView(
                state,
                onClose = onClose,
                onRetry = vm::start,
                onOpenLesson = onOpenLesson,
                onAccept = vm::acceptExamAnswer,
            )
            state.finishing -> Loading()
            state.items.isEmpty() -> EmptySession(state.title, state.emptyMessage ?: "Вопросов нет.", onClose)
            else -> QuestionView(
                state,
                onClose = { if (inProgress) confirmExit = true else onClose() },
                onInput = vm::onInput,
                onSubmit = vm::submit,
                onNext = vm::next,
                onAccept = vm::acceptAnyway,
            )
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

/** Снимок вопроса для анимации перехода: уходящий вопрос дорисовывается со своим ответом и вердиктом. */
private data class QuestionFrame(
    val index: Int,
    val item: SessionItem,
    val input: String,
    val verdict: Boolean?,
    val overridden: Boolean,
)

@Composable
private fun QuestionView(
    state: SessionState,
    onClose: () -> Unit,
    onInput: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onAccept: () -> Unit,
) {
    val item = state.current ?: return
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))) {
        SessionHeader(state, onClose)
        AnimatedContent(
            targetState = QuestionFrame(state.index, item, state.input, state.verdict, state.overridden),
            contentKey = { it.index },
            transitionSpec = { (slideInHorizontally { it / 6 } + fadeIn()) togetherWith fadeOut() },
            label = "question",
            modifier = Modifier.weight(1f),
        ) { frame ->
            val current = frame.item
            val active = frame.index == state.index
            val verdict = frame.verdict
            val scroll = rememberScrollState()
            LaunchedEffect(verdict) { if (verdict != null) scroll.animateScrollTo(scroll.maxValue) }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
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
                Spacer(Modifier.height(24.dp))
                if (current.showOptions) {
                    OptionHints(current.question.options, current.order)
                    Spacer(Modifier.height(16.dp))
                }
                AnswerField(
                    value = frame.input,
                    onValueChange = onInput,
                    verdict = verdict,
                    onSubmit = onSubmit,
                    onNext = onNext,
                    autoFocus = active,
                )
                AnimatedVisibility(verdict != null, enter = fadeIn(), exit = fadeOut()) {
                    val q = current.question
                    AnswerFeedback(
                        correct = verdict == true,
                        overridden = frame.overridden,
                        rightAnswer = q.options[q.correct],
                        explanation = if (state.kind == SessionKind.BLITZ) null else q.explanation,
                        onOverride = onAccept,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
        Box(Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp)) {
            if (state.verdict == null) {
                PrimaryButton("Ответить", onClick = onSubmit, enabled = state.input.isNotBlank())
            } else {
                PrimaryButton(if (state.index + 1 >= state.items.size) "Завершить" else "Далее", onClick = onNext)
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
private fun ResultView(
    state: SessionState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onOpenLesson: (String) -> Unit,
    onAccept: (questionId: String) -> Unit,
) {
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
            val exam = state.kind == SessionKind.EXAM
            val left = result.mistakes.count { !it.correct }
            item {
                Column(Modifier.padding(top = 40.dp, bottom = 12.dp)) {
                    Text(
                        if (left > 0) "Разбор ошибок · ${plural(left, "вопрос", "вопроса", "вопросов")}" else "Разбор ошибок",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (exam && result.mistakes.any { it.typed.isNotBlank() }) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Если вы ответили верно, но другими словами, нажмите «Я ответил правильно» — результат экзамена пересчитается.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = extra.muted,
                        )
                    }
                }
            }
            items(result.mistakes) { m ->
                val q = m.item.question
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text(Markdown.inline(q.text), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (m.typed.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                if (m.accepted) Icons.Outlined.Check else Icons.Outlined.Close,
                                null,
                                tint = if (m.accepted) extra.success else extra.error,
                                modifier = Modifier.size(18.dp).padding(top = 2.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (m.accepted) "${m.typed} — засчитано" else m.typed,
                                style = MaterialTheme.typography.bodyMedium,
                                color = extra.muted,
                            )
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
                    if (exam && !m.correct && m.typed.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        AcceptButton(onClick = { onAccept(q.id) })
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
