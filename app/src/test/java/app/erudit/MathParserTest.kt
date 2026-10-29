package app.erudit

import app.erudit.data.content.ContentLoader
import app.erudit.data.content.ContentSource
import app.erudit.ui.markdown.MathParser
import app.erudit.ui.markdown.MathSeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MathParserTest {
    private fun fractions(text: String) = MathParser.parse(text).filterIsInstance<MathSeg.Frac>()

    @Test
    fun rootsPowersAndIndices() {
        assertEquals(listOf(MathSeg.Root('√', "49", "√49"), MathSeg.Plain(" = 7")), MathParser.parse("√49 = 7"))
        assertEquals(listOf(MathSeg.Root('∛', "27", "∛27"), MathSeg.Plain(" = 3")), MathParser.parse("∛27 = 3"))
        assertEquals(MathSeg.Root('√', "13² − 5²", "√(13² − 5²)"), MathParser.parse("√(13² − 5²)").single())
        assertEquals(
            listOf(MathSeg.Root('√', "a", "√a"), MathSeg.Plain(" = a"), MathSeg.Sup("1⁄2")),
            MathParser.parse("√a = a^(1/2)"),
        )
        assertEquals(listOf(MathSeg.Plain("e"), MathSeg.Sup("iπ"), MathSeg.Plain(" + 1 = 0")), MathParser.parse("e^(iπ) + 1 = 0"))
        assertEquals(listOf(MathSeg.Plain("η = 1 − T"), MathSeg.Sub("хол")), MathParser.parse("η = 1 − T_хол"))
    }

    @Test
    fun fractionsAndWhatStaysText() {
        assertEquals(MathSeg.Frac("1", "2", "1/2"), fractions("Интуиция говорит 1/2.").single())
        assertEquals(MathSeg.Frac("1 + √5", "2", "(1 + √5)/2"), fractions("корень (1 + √5)/2 ≈ 1,618").single())
        assertEquals(MathSeg.Frac("√3", "2", "√3/2"), fractions("| 30° | 1/2 | √3/2 |")[1])
        assertEquals(MathSeg.Frac("1", "299 792 458", "1/299 792 458"), fractions("за 1/299 792 458 секунды").single())
        assertEquals(MathSeg.Frac("−b ± √D", "2a", "(−b ± √D)/(2a)"), fractions("x = (−b ± √D)/(2a)").single())
        // Дробь под корнем: корень разбирается целиком, дробь внутри — при отрисовке подкоренного выражения.
        assertEquals(MathSeg.Root('√', "l/g", "√(l/g)"), MathParser.parse("√(l/g)").single())
        listOf(
            "72 км/ч", "9,8 м/с²", "A/B-тест", "протоколы TCP/IP", "правило 50/30/20", "комета 2I/Борисов",
            "НОК = 96 / 4 = 24", "принцип Парето (80\\/20)", "да/нет", "dy/dx",
        ).forEach { assertTrue("не дробь: $it", fractions(it).isEmpty()) }
        assertEquals("(80/20)", MathParser.parse("(80\\/20)").filterIsInstance<MathSeg.Plain>().joinToString("") { it.text })
    }

    /** Во всём контенте дробью не становятся слова и единицы — кроме формул, где части нарочно взяты в скобки. */
    @Test
    fun contentFractionsAreMath() {
        val root = File("src/main/assets")
        val content = ContentLoader.load(object : ContentSource {
            override fun list(path: String): List<String> = File(root, path).list()?.toList().orEmpty()
            override fun read(path: String): String = File(root, path).readText()
        })
        val texts = content.lessons.map { it.body } +
            content.lessonQuestions.flatMap { q -> listOf(q.text) + q.options + listOfNotNull(q.explanation) }
        val found = texts.flatMap { fractions(it) }
        assertTrue("дробей мало: ${found.size}", found.size > 300)
        val cyrillic = Regex("[а-яё]", RegexOption.IGNORE_CASE)
        val suspicious = found.filter { (cyrillic.containsMatchIn(it.num) || cyrillic.containsMatchIn(it.den)) && !it.raw.startsWith("(") }
        assertTrue("слова и единицы приняты за дробь: $suspicious", suspicious.isEmpty())
    }
}
