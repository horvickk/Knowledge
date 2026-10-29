package app.erudit.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.erudit.ui.theme.AppTheme

val ScreenPadding = 20.dp

@Composable
fun TopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = title.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/**
 * Экран-список с верхней панелью. Без заголовка, кнопки «назад» и действий панель не нужна:
 * список начинается сразу под строкой состояния, а отступ сверху прокручивается вместе с ним.
 */
@Composable
fun ListScreen(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(start = ScreenPadding, end = ScreenPadding, bottom = 32.dp),
    content: LazyListScope.() -> Unit,
) {
    val bare = title == null && onBack == null && actions == null
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (bare) Spacer(Modifier.windowInsetsPadding(WindowInsets.statusBars)) else TopBar(title, onBack, actions ?: {})
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (bare) item { Spacer(Modifier.height(20.dp)) }
            content()
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AppTheme.extra.muted,
        modifier = modifier.padding(top = 28.dp, bottom = 10.dp),
    )
}

@Composable
fun Tile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val border = BorderStroke(1.dp, AppTheme.extra.line)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun ThinProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = AppTheme.extra.line,
    height: Dp = 4.dp,
) {
    // Пружина, а не tween: при частых обновлениях (загрузка, таймер) tween начинается заново с каждым
    // новым значением и почти не сдвигается, а пружина сохраняет скорость и идёт вровень с цифрами.
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessMediumLow), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(height))
                .background(color),
        )
    }
}

@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    stroke: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = AppTheme.extra.line,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val arcSize = Size(this.size.width - s, this.size.height - s)
            val topLeft = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(s))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = AppTheme.extra.line,
            disabledContentColor = AppTheme.extra.faint,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DomainBadge(icon: String, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(domainIcon(icon), contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Tile(modifier = modifier, padding = 14.dp) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
    }
}

/** Строка-ссылка: иконка, заголовок, подзаголовок и стрелка. */
@Composable
fun NavRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (enabled) MaterialTheme.colorScheme.onBackground else AppTheme.extra.faint,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted)
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AppTheme.extra.faint,
                )
            }
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(AppTheme.extra.line))
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = AppTheme.extra.muted) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = AppTheme.extra.muted,
        modifier = modifier.padding(vertical = 24.dp),
    )
}

/** Склонение существительного после числа: 1 урок, 2 урока, 5 уроков. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$n $word"
}
