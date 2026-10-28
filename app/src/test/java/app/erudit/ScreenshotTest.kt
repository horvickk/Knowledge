package app.erudit

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.erudit.ui.components.UpdateCard
import app.erudit.update.ChangeNote
import app.erudit.update.DownloadState
import app.erudit.update.Release
import app.erudit.update.UpdateInfo
import java.time.LocalDate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import android.graphics.Canvas
import android.os.Looper
import org.robolectric.Shadows
import androidx.test.core.app.ApplicationProvider
import app.erudit.data.db.Card
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
        var updateDownload by mutableStateOf<DownloadState>(DownloadState.None)
        val update = UpdateInfo(
            current = 56,
            releases = listOf(
                Release(
                    58, LocalDate.of(2026, 10, 28),
                    listOf(
                        ChangeNote("Обновления внутри приложения", listOf("При запуске приложение сообщает о новой сборке и показывает, что в ней нового.", "APK скачивается и устанавливается прямо из окна обновления.")),
                    ),
                    "", 7_457_377,
                ),
                Release(
                    57, LocalDate.of(2026, 10, 27),
                    listOf(
                        ChangeNote("Опыт только за новое, проверка урока по всем вопросам", listOf("Тренажёр приносит опыт раз в день по лучшему результату дня.", "Проверка после урока включает все вопросы урока.")),
                    ),
                    "", 7_400_000,
                ),
            ),
        )

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
                            "flags" -> TrainerScreen("flags") {}
                            "capitals" -> TrainerScreen("capitals") {}
                            "logic" -> TrainerScreen("logic") {}
                            "exam" -> ExamIntroScreen(firstCourse.stages.first().examId, {}, {})
                            "exam-session" -> SessionScreen("exam", firstCourse.stages.first().examId, {}, {})
                            "update" -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                                UpdateCard(update, updateDownload, {}, {}, {}, {}, Modifier.padding(20.dp))
                            }
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
        // Урок второй ступени до экзамена первой: читать можно, проверки нет — вместо неё подсказка.
        screen = "lesson"
        shot("05-lesson")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("пока закрыта", substring = true))
        compose.onNodeWithText("пока закрыта", substring = true).assertExists()
        assertTrue("у закрытого урока нет проверки", compose.onAllNodesWithText("Проверить себя").fetchSemanticsNodes().isEmpty())

        // Ввод ответа: неверный ответ и кнопка «Я ответил правильно».
        screen = "session"
        shot("12-session-empty")
        compose.onNode(hasSetTextAction()).performTextInput("что-то не то")
        compose.onNodeWithText("Ответить").performClick()
        shot("13-session-wrong")
        compose.onNodeWithText("Я ответил правильно").performClick()
        shot("14-session-accepted")
        // Переход к следующему вопросу: уходящий вопрос не должен мигать вердиктом «Неверно».
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Далее").performClick()
        repeat(6) {
            compose.mainClock.advanceTimeBy(50)
            assertTrue(
                "во время перехода виден «Неверно»",
                compose.onAllNodesWithText("Неверно").fetchSemanticsNodes().isEmpty(),
            )
        }
        compose.mainClock.autoAdvance = true

        // Экзамен: без подсказок, разбор в конце, засчитанные ответы пересчитывают результат.
        screen = "exam-session"
        shot("24-exam-question")
        repeat(15) {
            compose.onNode(hasSetTextAction()).performTextInput("не помню")
            compose.onNodeWithText("Ответить").performClick()
        }
        shot("25-exam-failed")
        val list = compose.onNode(hasScrollAction())
        repeat(12) {
            list.performScrollToNode(hasText("Я ответил правильно"))
            compose.onAllNodesWithText("Я ответил правильно").onFirst().performClick()
        }
        list.performScrollToIndex(0)
        // Пересчёт в базе идёт в фоне: ждём, пока придёт пометка о первой сдаче.
        repeat(100) {
            if (compose.onAllNodesWithText("Экзамен сдан впервые", substring = true).fetchSemanticsNodes().isNotEmpty()) return@repeat
            Thread.sleep(40)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
        shot("26-exam-revised")
        compose.onNodeWithText("Экзамен сдан").assertExists()
        compose.onNodeWithText("Экзамен сдан впервые", substring = true).assertExists()
        val attempt = runBlocking { container.database.dao().examAttempts().first().first() }
        assertTrue("попытка пересчитана: $attempt", attempt.passed && attempt.correct == 12)

        screen = "flags"
        shot("15-flags-intro")
        compose.onNodeWithText("Начать").performClick()
        shot("16-flags-play")
        compose.onNode(hasSetTextAction()).performTextInput("Австрия")
        compose.onNodeWithText("Ответить").performClick()
        shot("17-flags-answered")
        screen = "capitals"
        shot("18-capitals-intro")
        compose.onNodeWithText("Начать").performClick()
        shot("19-capitals-play")

        theme = ThemeMode.DARK
        screen = "home"
        shot("20-home-dark")
        screen = "flags"
        compose.onNodeWithText("Начать").performClick()
        shot("23-flags-dark")
        screen = "lesson"
        shot("21-lesson-dark")
        screen = "course"
        shot("22-course-dark")
        screen = "update"
        shot("27-update-dark")
        theme = ThemeMode.LIGHT
        shot("28-update")
        updateDownload = DownloadState.Downloading(0.42f)
        shot("29-update-downloading")

        // Опыт не начисляется дважды за одно и то же.
        runBlocking {
            val study = container.study
            val known = lessons.first().questions.filterIndexed { i, _ -> i % 4 != 0 }
            assertEquals("повторные верные ответы", 0, known.sumOf { study.recordAnswer(it, true, isReview = false) })
            assertEquals("урок пройден повторно", 0, study.completeLesson(lessons.first(), 8, 8))
            val first = study.recordTrainer("stroop", 20, xp = 10)
            assertEquals(10, first)
            assertEquals("тренажёр второй раз за день", 0, study.recordTrainer("stroop", 18, xp = 9))
            assertEquals("только прибавка к лучшему за день", 4, study.recordTrainer("stroop", 28, xp = 14))

            // Карточки заменённых вопросов удаляются, остальные остаются.
            val dao = container.database.dao()
            dao.upsertCards(listOf(Card("нет-такого-урока#1", "нет-такого-урока", due = 0)))
            val before = dao.knownQuestionIds().size
            study.pruneOrphanCards(content)
            assertEquals(before - 1, dao.knownQuestionIds().size)
        }
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
