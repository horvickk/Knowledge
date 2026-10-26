package app.erudit.ui.nav

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.erudit.domain.Achievement
import app.erudit.ui.components.Divider
import app.erudit.ui.screens.AchievementsScreen
import app.erudit.ui.screens.BookmarksScreen
import app.erudit.ui.screens.CatalogScreen
import app.erudit.ui.screens.CourseScreen
import app.erudit.ui.screens.DomainScreen
import app.erudit.ui.screens.ExamHistoryScreen
import app.erudit.ui.screens.ExamIntroScreen
import app.erudit.ui.screens.HomeScreen
import app.erudit.ui.screens.LessonScreen
import app.erudit.ui.screens.OnboardingScreen
import app.erudit.ui.screens.PracticeScreen
import app.erudit.ui.screens.ProfileScreen
import app.erudit.ui.screens.SearchScreen
import app.erudit.ui.screens.SessionScreen
import app.erudit.ui.screens.SettingsScreen
import app.erudit.ui.screens.TrainerScreen
import app.erudit.ui.theme.AppTheme
import kotlinx.coroutines.delay

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Сегодня", Icons.Outlined.Home),
    Tab(Routes.CATALOG, "Курсы", Icons.Outlined.LocalLibrary),
    Tab(Routes.PRACTICE, "Практика", Icons.Outlined.FitnessCenter),
    Tab(Routes.PROFILE, "Профиль", Icons.Outlined.Person),
)

@Composable
fun AppNav(onboarded: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }
    val container = LocalContainer.current

    var toast by remember { mutableStateOf<Achievement?>(null) }
    LaunchedEffect(Unit) {
        container.study.unlocked.collect { a ->
            toast = a
            delay(3200)
            if (toast == a) toast = null
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                Graph(nav, if (onboarded) Routes.HOME else Routes.ONBOARDING)
            }
            if (showBar) BottomBar(nav, route)
        }
        AnimatedVisibility(
            visible = toast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val a = toast
            if (a != null) AchievementToast(a)
        }
    }
}

@Composable
private fun AchievementToast(a: Achievement) {
    Surface(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = AppTheme.extra.warm)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Достижение: ${a.title}", style = MaterialTheme.typography.titleSmall)
                Text(a.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    Column {
        Divider()
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars,
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = route == tab.route,
                    onClick = {
                        nav.navigate(tab.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        unselectedIconColor = AppTheme.extra.faint,
                        unselectedTextColor = AppTheme.extra.faint,
                        indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Graph(nav: NavHostController, start: String) {
    val back: () -> Unit = { nav.popBackStack() }
    val openLesson: (String) -> Unit = { nav.go(Routes.lesson(it)) }
    val openExam: (String) -> Unit = { nav.go(Routes.exam(it)) }
    val openTrainer: (String) -> Unit = { nav.go(Routes.trainer(it)) }
    val practice: (String) -> Unit = { nav.go(Routes.session("practice", it)) }

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onDone = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onLesson = openLesson,
                onReview = { nav.go(Routes.session("review")) },
                onExam = openExam,
                onTrainer = openTrainer,
                onPractice = practice,
                onCatalog = { nav.navigate(Routes.CATALOG) { launchSingleTop = true } },
            )
        }
        composable(Routes.CATALOG) {
            CatalogScreen(onDomain = { nav.go(Routes.domain(it)) }, onSearch = { nav.go(Routes.SEARCH) })
        }
        composable(Routes.PRACTICE) {
            PracticeScreen(
                onReview = { nav.go(Routes.session("review")) },
                onPractice = practice,
                onMistakes = { nav.go(Routes.session("mistakes")) },
                onBlitz = { nav.go(Routes.session("blitz")) },
                onTrainer = openTrainer,
            )
        }
        composable(Routes.PROFILE) {
            ProfileScreen(
                onAchievements = { nav.go(Routes.ACHIEVEMENTS) },
                onExams = { nav.go(Routes.EXAMS) },
                onBookmarks = { nav.go(Routes.BOOKMARKS) },
                onSettings = { nav.go(Routes.SETTINGS) },
                onDomain = { nav.go(Routes.domain(it)) },
            )
        }
        composable("domain/{id}") { e ->
            DomainScreen(
                domainId = e.arg("id"),
                onBack = back,
                onCourse = { nav.go(Routes.course(it)) },
                onExam = openExam,
                onPractice = practice,
            )
        }
        composable("course/{id}") { e ->
            CourseScreen(courseId = e.arg("id"), onBack = back, onLesson = openLesson, onExam = openExam, onPractice = practice)
        }
        composable("lesson/{id}") { e ->
            LessonScreen(
                lessonId = e.arg("id"),
                onBack = back,
                onCheck = { nav.go(Routes.session("lesson", it)) },
                onOpenLesson = { id ->
                    nav.navigate(Routes.lesson(id)) { popUpTo("lesson/{id}") { inclusive = true } }
                },
            )
        }
        composable("examintro/{id}") { e ->
            val id = e.arg("id")
            ExamIntroScreen(examId = id, onBack = back, onStart = {
                nav.navigate(Routes.session("exam", id))
            })
        }
        composable("session/{kind}/{arg}") { e ->
            SessionScreen(
                kindKey = e.arguments?.getString("kind") ?: "practice",
                arg = e.arg("arg"),
                onClose = back,
                onOpenLesson = { id -> nav.navigate(Routes.lesson(id)) { popUpTo("session/{kind}/{arg}") { inclusive = true } } },
            )
        }
        composable("trainer/{id}") { e -> TrainerScreen(trainerId = e.arg("id"), onBack = back) }
        composable(Routes.SEARCH) { SearchScreen(onBack = back, onLesson = openLesson) }
        composable(Routes.BOOKMARKS) { BookmarksScreen(onBack = back, onLesson = openLesson) }
        composable(Routes.ACHIEVEMENTS) { AchievementsScreen(onBack = back) }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
        composable(Routes.EXAMS) { ExamHistoryScreen(onBack = back, onExam = openExam) }
    }
}

private fun androidx.navigation.NavBackStackEntry.arg(name: String): String =
    Uri.decode(arguments?.getString(name).orEmpty())
