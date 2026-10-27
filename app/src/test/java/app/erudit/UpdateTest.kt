package app.erudit

import app.erudit.update.ChangeNote
import app.erudit.update.Releases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset

/** Разбор релизов GitHub для окна обновления (org.json — из Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateTest {
    /** Так выглядит описание релиза, которое пишет .github/scripts/release_notes.py. */
    private val body = """
        ## Что нового

        ### Опыт только за новое
        - Верный ответ даёт опыт, только если на вопрос ещё не отвечали верно
          или подошёл срок его повторения.
        - Проверка после урока включает все вопросы урока.

        ### Контент: криминалистика
        Описание без списка
        в две строки.

        ## Сборка

        Ветка `main`, коммит abc.
        - Этот пункт к изменениям не относится.
    """.trimIndent()

    @Test
    fun notesSection() {
        assertEquals(
            listOf(
                ChangeNote(
                    "Опыт только за новое",
                    listOf(
                        "Верный ответ даёт опыт, только если на вопрос ещё не отвечали верно или подошёл срок его повторения.",
                        "Проверка после урока включает все вопросы урока.",
                    ),
                ),
                ChangeNote("Контент: криминалистика", listOf("Описание без списка", "в две строки.")),
            ),
            Releases.notes(body),
        )
        assertTrue("старые релизы без раздела", Releases.notes("Автоматическая сборка из ветки `x`.").isEmpty())
    }

    @Test
    fun parseReleases() {
        fun release(tag: String, apk: Boolean = true, draft: Boolean = false, pre: Boolean = false, notes: String = ""): String {
            val apkAsset = if (apk) """,{"name": "Erudit-1.apk", "size": 7457377, "browser_download_url": "https://example.org/$tag.apk"}""" else ""
            return """
                {
                  "tag_name": "$tag", "draft": $draft, "prerelease": $pre,
                  "published_at": "2026-10-27T23:31:35Z",
                  "body": ${org.json.JSONObject.quote(notes)},
                  "assets": [{"name": "notes.txt", "size": 10, "browser_download_url": "https://example.org/notes.txt"}$apkAsset]
                }
            """
        }
        val json = "[" + listOf(
            release("build-57"),
            release("build-59", notes = body),
            release("build-60", draft = true),
            release("build-61", pre = true),
            release("v2.0"),
            release("build-62", apk = false),
            release("build-58"),
        ).joinToString(",") + "]"

        val releases = Releases.parse(json, zone = ZoneOffset.ofHours(3))
        assertEquals(listOf(59, 58, 57), releases.map { it.build })
        val latest = releases.first()
        assertEquals("https://example.org/build-59.apk", latest.apkUrl)
        assertEquals(7457377L, latest.apkSize)
        assertEquals("дата по местному времени", LocalDate.of(2026, 10, 28), latest.date)
        assertEquals(2, latest.notes.size)
        assertTrue(releases.last().notes.isEmpty())
    }
}
