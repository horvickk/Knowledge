package app.erudit.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.erudit.data.content.Content
import app.erudit.data.prefs.Settings
import app.erudit.domain.Unlocks
import app.erudit.ui.nav.LocalContainer

@Composable
fun rememberSettings(): Settings {
    val settings by LocalContainer.current.settings.settings.collectAsState(initial = Settings(onboarded = true))
    return settings
}

@Composable
fun rememberUnlocks(content: Content): Unlocks {
    val container = LocalContainer.current
    val completed by container.study.completedLessons.collectAsState(initial = emptySet())
    val passed by container.study.passedExams.collectAsState(initial = emptySet())
    val settings = rememberSettings()
    return remember(content, completed, passed, settings.sequential) {
        Unlocks(content, completed, passed, settings.sequential)
    }
}
