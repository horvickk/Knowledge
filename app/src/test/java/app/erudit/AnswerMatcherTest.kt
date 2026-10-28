package app.erudit

import app.erudit.data.content.ContentLoader
import app.erudit.data.content.ContentSource
import app.erudit.data.content.Question
import app.erudit.domain.AnswerMatcher
import app.erudit.domain.RussianStemmer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnswerMatcherTest {
    private fun q(correct: String, vararg wrong: String, text: String = "Вопрос?") =
        Question("t", "l", text, listOf(correct) + wrong, 0, null)

    private fun ok(input: String, q: Question) = assertTrue("«$input» должен быть верным для ${q.options}", AnswerMatcher.check(input, q))
    private fun no(input: String, q: Question) = assertFalse("«$input» не должен быть верным для ${q.options}", AnswerMatcher.check(input, q))

    @Test
    fun stemmer() {
        assertEquals("австр", RussianStemmer.stem("Австрия"))
        assertEquals("австрал", RussianStemmer.stem("Австралия"))
        assertEquals(RussianStemmer.stem("козу"), RussianStemmer.stem("коза"))
        assertEquals(RussianStemmer.stem("пианистом"), RussianStemmer.stem("пианист"))
        assertEquals(RussianStemmer.stem("отрицание"), RussianStemmer.stem("отрицания"))
        assertEquals(RussianStemmer.stem("Кузнецов"), RussianStemmer.stem("кузнец"))
    }

    @Test
    fun exactAndTypos() {
        val q = q("Козу", "Волка", "Капусту", "Всех вместе")
        ok("козу", q)
        ok("Коза", q)
        ok("  КОЗУ. ", q)
        no("волка", q)
        no("капуста", q)
        val long = q("Флорентийская камерата", "Венская школа", "Могучая кучка")
        ok("флорентийская камерота", long)
        ok("Камерата", long)
    }

    @Test
    fun numbers() {
        val q = q("Не более 40 часов в неделю", "60 часов в неделю", "12 часов в день", "Сколько решит работодатель")
        ok("40", q)
        ok("сорок часов", q)
        ok("не больше 40 часов", q)
        no("60 часов", q)
        no("50 часов в неделю", q)
        val word = q("Четыре", "Одна", "Две", "Десять")
        ok("4", word)
        ok("четыре", word)
        no("2", word)
        val years = q("246 лет и 210 дней", "10 дней", "Один год")
        ok("246 лет и 210 дней", years)
        no("246 лет и 211 дней", years)
        val temp = q("При +4 °C", "При 0 °C", "При 100 °C", "При −4 °C")
        ok("4", temp)
        ok("+4", temp)
        no("-4", temp)
        no("0", temp)
        val thousands = q("Около 200 тысяч слов", "Около 5 тысяч слов", "Около миллиона")
        ok("200 000", thousands)
        ok("двести тысяч", thousands)
        val roman = q("В XIX веке", "В XVII веке", "В XX веке")
        ok("19 век", roman)
        no("17 век", roman)
    }

    @Test
    fun partialAndDistinctive() {
        val bizet = q("Жорж Бизе", "Джузеппе Верди", "Вольфганг Амадей Моцарт", "Джакомо Пуччини")
        ok("Бизе", bizet)
        no("Верди", bizet)
        val smullyan = q("Фокусником и пианистом", "Космонавтом", "Футболистом", "Пиратом")
        ok("пианистом", smullyan)
        ok("фокусник", smullyan)
        no("фокусником и футболистом", smullyan)
        val quartet = q("Две скрипки, альт и виолончель", "Четыре скрипки", "Скрипка, флейта, фортепиано и арфа")
        ok("2 скрипки альт и виолончель", quartet)
        no("четыре скрипки", quartet)
        val logic = q(
            "Ложь проходит через вопрос дважды, и двойное отрицание даёт правду",
            "Лжецы иногда говорят правду", "Рыцари иногда лгут", "Вопрос запрещено задавать",
        )
        ok("двойное отрицание даёт правду", logic)
        no("лжецы говорят правду", logic)
    }

    @Test
    fun negationAndYesNo() {
        val q = q("Тенор", "Баритон", "Бас", "Контральто")
        ok("тенор", q)
        no("не тенор", q)
        no("тенор или бас", q)
        val yesNo = q("Нет: его возводили задолго до прихода кельтов", "Да, в I веке до н. э.", "Да, по приказу Артура", "Его построили римляне")
        ok("нет", yesNo)
        no("да", yesNo)
        val truth = Question("t", "l", "?", listOf("Верно", "Неверно"), 1, null)
        ok("неверно", truth)
        ok("не верно", truth)
        no("верно", truth)
    }

    @Test
    fun optionsShownForChoiceQuestions() {
        val stress = Question("t", "l", "Какой вариант правильный?", listOf("ТОрты", "ТортЫ", "ТортА", "ТОрта"), 0, null)
        assertTrue(AnswerMatcher.needsOptions(stress))
        ok("ТОрты", stress)
        no("ТортЫ", stress)
        no("торты", stress)
        val order = listOf(2, 0, 1, 3)
        assertTrue(AnswerMatcher.check("б", stress, order))
        assertFalse(AnswerMatcher.check("а", stress, order))
        val plain = q("Козу", "Волка", text = "Кого перевезти первым?")
        assertFalse(AnswerMatcher.needsOptions(plain))
        no("а", plain)
        // «Какое из…» и вопросы, помеченные в контенте как выбор, без вариантов не имеют смысла.
        assertTrue(AnswerMatcher.needsOptions(q("3/8", "1/3", text = "Какая из дробей записывается конечной десятичной дробью?")))
        val divisible = Question("t", "l", "Какое число делится на 9?", listOf("7 362", "7 361", "4 444"), 0, null, choice = true)
        assertTrue(AnswerMatcher.needsOptions(divisible))
        assertTrue(AnswerMatcher.check("а", divisible, listOf(0, 1, 2)))
        ok("7362", divisible)
    }

    @Test
    fun formulas() {
        val q = q("2ᵖ − 1", "2ᵖ + 1", "p² + 1", "p! + 1")
        ok("2^p-1", q)
        ok("2ᵖ − 1", q)
        no("2^p+1", q)
        val root = q("√2", "2", "1,5", "√3")
        ok("√2", root)
        ok("корень из 2", root)
        no("2", root)
        no("1.5", root)
    }

    /** Дословный верный ответ засчитывается, дословный неверный — нет, для всех вопросов контента. */
    @Test
    fun everyQuestionAcceptsItsAnswer() {
        val root = File("src/main/assets")
        val content = ContentLoader.load(object : ContentSource {
            override fun list(path: String): List<String> = File(root, path).list()?.toList().orEmpty()
            override fun read(path: String): String = File(root, path).readText()
        })
        val problems = mutableListOf<String>()
        val questions = content.lessonQuestions + content.logicPuzzles
        for (question in questions) {
            val right = question.options[question.correct]
            if (!AnswerMatcher.check(right, question)) problems += "не принят верный: ${question.id} «$right»"
            val loose = right.lowercase().replace(Regex("(?<!\\d)[,.]|[,.](?!\\d)|[!?«»]"), "")
            if (!AnswerMatcher.needsOptions(question) && !AnswerMatcher.check(loose, question)) {
                problems += "не принят верный без регистра и знаков: ${question.id} «$right»"
            }
            question.options.forEachIndexed { i, o ->
                if (i != question.correct && AnswerMatcher.check(o, question)) problems += "принят неверный: ${question.id} «$o» (верно «$right»)"
            }
        }
        assertTrue(problems.take(60).joinToString("\n") + "\nвсего: ${problems.size}", problems.isEmpty())
    }
}
