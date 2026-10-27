package app.erudit.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.erudit.ui.components.IconBadge
import app.erudit.ui.components.PrimaryButton
import app.erudit.ui.components.ScreenPadding
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val settings = LocalContainer.current.settings
    val scope = rememberCoroutineScope()
    val goals = listOf("Легко" to 20, "Обычно" to 40, "Серьёзно" to 60, "Интенсивно" to 100)
    var selected by remember { mutableIntStateOf(1) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
    ) {
        Spacer(Modifier.height(56.dp))
        Text("Эрудит", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Большая библиотека знаний: от математики и физики до истории, искусства и философии. Каждая тема — от основ к глубине.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppTheme.extra.muted,
        )
        Spacer(Modifier.height(36.dp))
        Feature(Icons.AutoMirrored.Outlined.MenuBook, "Читайте уроки", "Короткие тексты по 5–10 минут с ключевыми идеями и фактами.")
        Feature(Icons.Outlined.School, "Сдавайте экзамены", "Ступень за ступенью: экзамен открывает следующий уровень.")
        Feature(Icons.Outlined.Replay, "Повторяйте вовремя", "Интервальные повторения возвращают вопросы, когда вы начинаете их забывать.")
        Feature(Icons.Outlined.Psychology, "Тренируйте мышление", "Логика, устный счёт, память и внимание.")
        Spacer(Modifier.height(28.dp))
        Text("Сколько заниматься в день?", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        ChoiceChips(goals.mapIndexed { i, (label, xp) -> "$label · $xp" to (i == selected) }) { selected = it }
        Text(
            "Это цель в очках опыта. 40 — примерно новый урок и тренажёр или 10–15 минут.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.extra.muted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.weight(1f, fill = true))
        Spacer(Modifier.height(36.dp))
        PrimaryButton("Начать", onClick = {
            scope.launch {
                settings.setDailyGoal(goals[selected].second)
                settings.setOnboarded()
                onDone()
            }
        })
        Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(20.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = AppTheme.extra.muted)
        }
    }
}
