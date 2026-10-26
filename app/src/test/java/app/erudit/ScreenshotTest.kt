package app.erudit

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import android.os.Looper
import org.robolectric.Shadows
import androidx.test.core.app.ApplicationProvider
import app.erudit.data.prefs.ThemeMode
import app.erudit.ui.nav.LocalContainer
import app.erudit.ui.screens.CatalogScreen
import app.erudit.ui.screens.CourseScreen
import app.erudit.ui.screens.DomainScreen
import app.erudit.ui.screens.ExamIntroScreen
import app.erudit.ui.screens.HomeScreen
import app.erudit.ui.screens.LessonScreen
import app.erudit.ui.screens.OnboardingScreen
import app.erudit.ui.screens.PracticeScreen
import app.erudit.ui.screens.ProfileScreen
import app.erudit.ui.screens.SessionScreen
import app.erudit.ui.screens.TrainerScreen
import app.erudit.ui.theme.EruditTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Рендерит основные экраны в PNG (build/screenshots) для визуальной проверки дизайна.
 * Запуск: ./gradlew :app:testDebugUnitTest --tests app.erudit.ScreenshotTest
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = File("build/screenshots").apply { mkdirs() }

    @Test
    fun screens() {
        val app = ApplicationProvider.getApplicationContext<EruditApp>()
        val container = app.container
        val content = runBlocking { container.awaitContent() }
        val firstCourse = content.domains.first().courses.first()
        val lessons = firstCourse.lessons

        var screen by mutableStateOf("onboarding")
        var theme by mutableStateOf(ThemeMode.LIGHT)

        compose.setContent {
            CompositionLocalProvider(LocalContainer provides container) {
                EruditTheme(mode = theme) {
                    Box(Modifier.fillMaxSize()) {
                        when (screen) {
                            "onboarding" -> OnboardingScreen {}
                            "home" -> HomeScreen({}, {}, {}, {}, {}, {})
                            "catalog" -> CatalogScreen({}, {})
                            "domain" -> DomainScreen(content.domains.first().id, {}, {}, {}, {})
                            "course" -> CourseScreen(firstCourse.id, {}, {}, {}, {})
                            "lesson" -> LessonScreen(lessons[3].id, {}, {}, {})
                            "session" -> SessionScreen("practice", firstCourse.id, {}, {})
                            "practice" -> PracticeScreen({}, {}, {}, {}, {})
                            "profile" -> ProfileScreen({}, {}, {}, {}, {})
                            "trainer" -> TrainerScreen("math") {}
                            "exam" -> ExamIntroScreen(firstCourse.stages.first().examId, {}, {})
                        }
                    }
                }
            }
        }
        shot("01-onboarding")

        runBlocking {
            val study = container.study
            lessons.take(3).forEach { l ->
                l.questions.forEachIndexed { i, q -> study.recordAnswer(q, i % 4 != 0, isReview = false) }
                study.completeLesson(l, 4, 5)
            }
        }

        listOf("home", "catalog", "domain", "course", "lesson", "session", "practice", "profile", "trainer", "exam")
            .forEachIndexed { i, s ->
                screen = s
                shot("%02d-%s".format(i + 2, s))
            }
        theme = ThemeMode.DARK
        screen = "home"
        shot("20-home-dark")
        screen = "lesson"
        shot("21-lesson-dark")
        screen = "course"
        shot("22-course-dark")
    }

    private fun shot(name: String) {
        // Фоновые запросы Room завершаются в своём потоке и возвращаются в главный looper.
        repeat(20) {
            Thread.sleep(40)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
        }
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
