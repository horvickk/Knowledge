package app.erudit.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import app.erudit.AppContainer
import app.erudit.data.content.Content

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer не задан") }

@Composable
fun rememberContent(): Content? {
    val content by LocalContainer.current.content.collectAsState()
    return content
}

/** Маршруты приложения. Аргументы кодируются: в id встречаются точки и двоеточия. */
object Routes {
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val PRACTICE = "practice"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val BOOKMARKS = "bookmarks"
    const val ACHIEVEMENTS = "achievements"
    const val SETTINGS = "settings"
    const val EXAMS = "exams"
    const val ONBOARDING = "onboarding"

    fun domain(id: String) = "domain/${Uri.encode(id)}"
    fun course(id: String) = "course/${Uri.encode(id)}"
    fun lesson(id: String) = "lesson/${Uri.encode(id)}"
    fun session(kind: String, arg: String = "-") = "session/$kind/${Uri.encode(arg)}"
    fun exam(examId: String) = "examintro/${Uri.encode(examId)}"
    fun trainer(id: String) = "trainer/$id"
}

fun NavController.go(route: String) = navigate(route) { launchSingleTop = true }
