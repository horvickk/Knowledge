package app.erudit.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import app.erudit.data.content.Content
import app.erudit.data.content.Lesson
import app.erudit.ui.components.Divider
import app.erudit.ui.components.EmptyNote
import app.erudit.ui.components.Loading
import app.erudit.ui.components.ScreenPadding
import app.erudit.ui.nav.rememberContent
import app.erudit.ui.theme.AppTheme
import app.erudit.ui.theme.domainColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class SearchHit(val lesson: Lesson, val snippet: String?, val score: Int)

object Search {
    private fun norm(s: String) = s.lowercase().replace('ё', 'е')

    private val markup = Regex("[*`#>|!$]")

    fun run(content: Content, query: String, limit: Int = 60): List<SearchHit> {
        val words = norm(query).split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return content.lessons.mapNotNull { lesson ->
            val title = norm(lesson.title)
            val body = norm(lesson.body)
            if (!words.all { title.contains(it) || body.contains(it) }) return@mapNotNull null
            val score = words.sumOf { w -> (if (title.contains(w)) 10 else 0) + body.windowedCount(w).coerceAtMost(5) }
            val first = words.map { body.indexOf(it) }.filter { it >= 0 }.minOrNull()
            val snippet = first?.let {
                val start = (it - 60).coerceAtLeast(0)
                val end = (it + 110).coerceAtMost(lesson.body.length)
                (if (start > 0) "…" else "") + lesson.body.substring(start, end).replace('\n', ' ').replace(markup, "").trim() + "…"
            }
            SearchHit(lesson, snippet, score)
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun String.windowedCount(w: String): Int {
        var count = 0
        var i = indexOf(w)
        while (i >= 0) { count++; i = indexOf(w, i + w.length) }
        return count
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit, onLesson: (String) -> Unit) {
    val content = rememberContent() ?: return Loading()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val hits by produceState(initialValue = emptyList<SearchHit>(), query) {
        delay(150)
        value = withContext(Dispatchers.Default) { Search.run(content, query) }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад") }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text("Поиск по урокам", style = MaterialTheme.typography.bodyLarge, color = AppTheme.extra.faint)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp).focusRequester(focus),
                )
            }
        }
        Divider()
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = ScreenPadding)) {
            if (query.length >= 2 && hits.isEmpty()) {
                item { EmptyNote("Ничего не найдено. Попробуйте другое слово.") }
            }
            items(hits, key = { it.lesson.id }) { hit ->
                val domain = content.domainOf(hit.lesson)
                Surface(onClick = { onLesson(hit.lesson.id) }, color = Color.Transparent) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                        Text(
                            "${domain.title} · ${content.courseOf(hit.lesson).title}".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = domainColor(domain.color),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(hit.lesson.title, style = MaterialTheme.typography.titleMedium)
                        hit.snippet?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.extra.muted, maxLines = 3)
                        }
                    }
                }
                Divider()
            }
        }
    }
}
