package app.erudit

import app.erudit.data.content.Content
import app.erudit.data.content.ContentLoader
import app.erudit.data.content.ContentSource
import app.erudit.ui.markdown.Markdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Проверяет весь учебный контент из assets: формат, вопросы, уникальность. */
class ContentValidationTest {
    private val content: Content by lazy {
        val root = File("src/main/assets")
        ContentLoader.load(object : ContentSource {
            override fun list(path: String): List<String> = File(root, path).list()?.toList().orEmpty()
            override fun read(path: String): String = File(root, path).readText()
        })
    }

    @Test
    fun contentLoads() {
        assertTrue("нет областей", content.domains.isNotEmpty())
        println(
            "Областей: ${content.domains.size}, курсов: ${content.coursesById.size}, уроков: ${content.lessons.size}, " +
                "вопросов: ${content.lessons.sumOf { it.questions.size }}, логических задач: ${content.logicPuzzles.size}",
        )
    }

    @Test
    fun idsAreUnique() {
        val lessonIds = content.lessons.map { it.id }
        assertEquals("повторяются id уроков", lessonIds.size, lessonIds.toSet().size)
        val questionIds = content.lessons.flatMap { it.questions } .map { it.id } + content.logicPuzzles.map { it.id }
        val dup = questionIds.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue("повторяются вопросы: $dup", dup.isEmpty())
    }

    @Test
    fun structureIsComplete() {
        val problems = mutableListOf<String>()
        for (d in content.domains) {
            if (d.description.isBlank()) problems += "${d.id}: нет описания"
            for (c in d.courses) {
                if (c.description.isBlank()) problems += "${c.id}: нет описания"
                if (c.stages.isEmpty()) problems += "${c.id}: нет ступеней"
                for (s in c.stages) if (s.lessons.isEmpty()) problems += "${s.id}: пустая ступень"
            }
        }
        for (l in content.lessons) {
            if (l.questions.size < 6) problems += "${l.id}: вопросов ${l.questions.size} (< 6)"
            val words = l.body.split(Regex("\\s+")).size
            if (words < 250) problems += "${l.id}: слишком короткий урок ($words слов)"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun questionsAreWellFormed() {
        val problems = mutableListOf<String>()
        for (q in content.lessons.flatMap { it.questions } + content.logicPuzzles) {
            if (q.options.size !in 2..5) problems += "${q.id}: вариантов ${q.options.size}"
            if (q.options.toSet().size != q.options.size) problems += "${q.id}: повторяются варианты «${q.text}»"
            if (q.explanation.isNullOrBlank()) problems += "${q.id}: нет пояснения «${q.text}»"
            if (q.options.any { it.isBlank() }) problems += "${q.id}: пустой вариант"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun markdownParses() {
        for (l in content.lessons) {
            val blocks = Markdown.parse(l.body)
            assertTrue("${l.id}: пустой текст", blocks.isNotEmpty())
        }
    }

    @Test
    fun examsResolve() {
        for (d in content.domains) {
            for (id in content.examsOf(d)) {
                val exam = content.exam(id)
                assertTrue("экзамен $id не найден", exam != null)
                assertTrue("в экзамене $id мало вопросов", exam!!.questionCount >= 5)
            }
        }
    }
}
