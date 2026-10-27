package app.erudit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.erudit.ui.components.Loading
import app.erudit.ui.components.PrimaryButton
import app.erudit.ui.components.ScreenPadding
import app.erudit.ui.components.SecondaryButton
import app.erudit.ui.components.ThinProgress
import app.erudit.ui.components.plural
import app.erudit.ui.components.TopBar
import app.erudit.ui.markdown.Markdown
import app.erudit.ui.markdown.MarkdownBlock
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.nav.rememberContent
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.domainColor
import kotlinx.coroutines.launch

@Composable
fun LessonScreen(lessonId: String, onBack: () -> Unit, onCheck: (String) -> Unit, onOpenLesson: (String) -> Unit) {
    val content = rememberContent() ?: return Loading()
    val lesson = content.lessonsById[lessonId] ?: return Loading()
    val course = content.courseOf(lesson)
    val domain = content.domainOf(lesson)
    val stage = course.stages[lesson.stageIndex - 1]
    val study = LocalContainer.current.study
    val unlocks = rememberUnlocks(content)
    val completed = lesson.id in unlocks.completed
    val accent = domainColor(domain.color)
    val blocks = remember(lesson.id) { Markdown.parse(lesson.body) }
    val scope = rememberCoroutineScope()

    val bookmarks by study.bookmarks.collectAsState(initial = emptyList())
    val bookmarked = bookmarks.any { it.lessonId == lesson.id }

    val listState = rememberLazyListState()
    val progress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) 0f
            else {
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (last >= total - 1) 1f else listState.firstVisibleItemIndex.toFloat() / total
            }
        }
    }

    // Следующий урок курса — после завершения текущего.
    val next = remember(lesson.id, unlocks) {
        val lessons = course.lessons
        lessons.getOrNull(lessons.indexOf(lesson) + 1)?.takeIf { unlocks.lessonOpen(it) }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(
            title = null,
            onBack = onBack,
            actions = {
                IconButton(onClick = { scope.launch { study.setBookmark(lesson.id, !bookmarked) } }) {
                    Icon(
                        if (bookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Закладка",
                        tint = if (bookmarked) accent else MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
        )
        ThinProgress(progress, color = accent, track = MaterialTheme.colorScheme.background, height = 2.dp)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = ScreenPadding),
        ) {
            item {
                Column(Modifier.padding(top = 16.dp, bottom = 12.dp)) {
                    Text(
                        "${domain.title} · ${course.title}".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(lesson.title, style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Ступень ${stage.index}: ${stage.title} · ${lesson.minutes} мин",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.extra.muted,
                    )
                }
            }
            itemsIndexed(blocks) { _, block -> MarkdownBlock(block, accent) }
            item {
                Column(
                    Modifier
                        .padding(top = 40.dp, bottom = 24.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    if (completed) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = AppTheme.extra.success, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Урок пройден", style = MaterialTheme.typography.titleSmall, color = AppTheme.extra.success)
                        }
                        if (next != null) {
                            PrimaryButton("Следующий урок", onClick = { onOpenLesson(next.id) })
                            Spacer(Modifier.height(10.dp))
                        }
                        SecondaryButton("Проверить себя ещё раз", onClick = { onCheck(lesson.id) })
                    } else {
                        Text(
                            "Закрепите прочитанное: ${plural(lesson.questions.size, "вопрос", "вопроса", "вопросов")} по уроку.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.extra.muted,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                        PrimaryButton("Проверить себя", onClick = { onCheck(lesson.id) })
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
