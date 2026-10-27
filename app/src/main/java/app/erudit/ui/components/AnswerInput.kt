package app.erudit.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.erudit.domain.AnswerMatcher
import app.erudit.ui.markdown.Markdown
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.Serif

/**
 * Поле для ответа с клавиатуры. До проверки Enter отправляет ответ, после — переходит дальше;
 * поле остаётся в фокусе, чтобы клавиатура не пряталась между вопросами.
 * [verdict]: null — ещё не проверено, true/false — верно/неверно.
 */
@Composable
fun AnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    verdict: Boolean?,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ваш ответ",
    keyboardType: KeyboardType = KeyboardType.Text,
    autoFocus: Boolean = true,
) {
    val extra = AppTheme.extra
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (autoFocus) {
            runCatching { focus.requestFocus() }
            keyboard?.show()
        }
    }
    val border by animateColorAsState(
        when (verdict) {
            true -> extra.success
            false -> extra.error
            null -> if (focused) MaterialTheme.colorScheme.primary else extra.line
        },
        label = "answerBorder",
    )
    val background = when (verdict) {
        true -> extra.successSoft
        false -> extra.errorSoft
        null -> MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = background,
        border = BorderStroke(if (verdict == null && !focused) 1.dp else 1.5.dp, border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = extra.faint)
            }
            BasicTextField(
                value = value,
                onValueChange = { v ->
                    // Enter с аппаратной клавиатуры приходит как перевод строки.
                    when {
                        '\n' in v -> if (verdict == null) onSubmit() else onNext()
                        verdict == null -> onValueChange(v)
                    }
                },
                readOnly = verdict != null,
                singleLine = false,
                maxLines = 4,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (verdict == null) onSubmit() else onNext() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

/**
 * Разбор ответа: верно или нет, правильный ответ и пояснение. Если ответ не засчитан,
 * пользователь может засчитать его сам — например, если ответил верно, но своими словами.
 */
@Composable
fun AnswerFeedback(
    correct: Boolean,
    overridden: Boolean,
    rightAnswer: String,
    explanation: String?,
    onOverride: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val extra = AppTheme.extra
    Column(modifier) {
        Text(
            when {
                overridden -> "Засчитано как верный"
                correct -> "Верно"
                else -> "Неверно"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (correct) extra.success else extra.error,
        )
        if (!correct || overridden) {
            Spacer(Modifier.height(6.dp))
            Text(
                buildAnnotatedString {
                    append("Правильный ответ: ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(Markdown.inline(rightAnswer)) }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (!correct && onOverride != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = onOverride,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, extra.line),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = extra.success, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Я ответил правильно", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        explanation?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                Markdown.inline(it),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Serif),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/** Варианты-подсказки для вопросов, которые без них не имеют смысла: ответ — буква или текст. */
@Composable
fun OptionHints(options: List<String>, order: List<Int>, modifier: Modifier = Modifier) {
    val extra = AppTheme.extra
    Column(modifier.fillMaxWidth()) {
        order.forEachIndexed { pos, index ->
            Row(Modifier.padding(vertical = 5.dp)) {
                Text(
                    AnswerMatcher.letters[pos],
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(26.dp),
                )
                Text(Markdown.inline(options[index]), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Введите букву или сам вариант", style = MaterialTheme.typography.bodySmall, color = extra.muted)
    }
}
