package app.erudit.domain

import app.erudit.data.content.Question
import kotlin.math.max
import kotlin.math.min

/**
 * Проверка ответа, введённого с клавиатуры.
 *
 * Ответ сравнивается не только с верным вариантом, но и с неверными: слово засчитывается,
 * если оно есть в верном ответе и его нет среди ошибочных. Так «Бизе» верно для «Жорж Бизе»,
 * «сорок часов» — для «Не более 40 часов в неделю», а «60 часов» — нет. Опечатки и формы слов
 * прощаются; пересказ своими словами пользователь может засчитать сам кнопкой
 * «Я ответил правильно».
 */
object AnswerMatcher {

    /** Буквы, которыми подписаны варианты, когда вопрос без них не имеет смысла. */
    val letters = listOf("А", "Б", "В", "Г", "Д", "Е")

    private val optionPhrases = Regex(
        "из перечисленн|какой вариант|какой из вариантов|как правильно (сказать|поставить|написать)",
        RegexOption.IGNORE_CASE,
    )

    /** Вопрос ссылается на варианты («что из перечисленного…») или они различаются только регистром. */
    fun needsOptions(q: Question): Boolean =
        optionPhrases.containsMatchIn(q.text) || q.options.map { compact(it) }.toSet().size < q.options.size

    /**
     * Верен ли ответ [input] на вопрос [q]. [order] — порядок показа вариантов: нужен, если они
     * видны и пользователь ввёл букву варианта.
     */
    fun check(input: String, q: Question, order: List<Int> = q.options.indices.toList()): Boolean {
        val text = input.trim()
        if (text.isEmpty()) return false
        if (needsOptions(q)) {
            letterChoice(text, order)?.let { return it == q.correct }
            // Варианты, различающиеся только ударением: сравниваем с учётом регистра.
            val exact = q.options.indexOfFirst { it.trim() == text }
            if (exact >= 0) return exact == q.correct
        }
        return matches(text, q.options, q.correct)
    }

    /** Буква варианта «А»…«Е» (или латинская a…f) → индекс варианта. */
    fun letterChoice(text: String, order: List<Int>): Int? {
        val t = text.trim().trimEnd('.', ')').lowercase()
        if (t.length != 1) return null
        val i = "абвгде".indexOf(t[0]).takeIf { it >= 0 } ?: "abcdef".indexOf(t[0]).takeIf { it >= 0 } ?: return null
        return order.getOrNull(i)
    }

    fun matches(input: String, options: List<String>, correct: Int): Boolean {
        val wrong = options.indices.filter { it != correct }.map { options[it] }

        // 1. Почти дословное совпадение строки (с допуском на опечатку, если в ответе есть буквы).
        val ci = compact(input)
        val dc = levenshtein(ci, compact(options[correct]))
        val dw = wrong.minOfOrNull { levenshtein(ci, compact(it)) } ?: Int.MAX_VALUE
        if (dc == 0 && dw > 0) return true
        val sameNumbers = ci.filter { it.isDigit() } == compact(options[correct]).filter { it.isDigit() }
        if (ci.any { it.isLetter() } && sameNumbers && dc <= typoTolerance(ci.length) && dc < dw) return true

        val user = tokens(input)
        if (user.isEmpty()) return false
        val right = tokens(options[correct]).ifEmpty { tokens(options[correct], dropStopWords = false) }
        val wrongTokens = wrong.map { tokens(it) }
        val others = wrongTokens.flatten()

        // 2. «Да» или «нет», если только верный вариант начинается с этого слова.
        val first = user.first()
        if ((first == "да" || first == "нет") && user.size <= 2 && right.firstOrNull() == first &&
            wrongTokens.none { it.firstOrNull() == first }
        ) return true

        // Отрицание, которого нет в верном ответе, меняет смысл.
        if (user.any { it in negations && it !in right }) return false

        val inRight = user.map { u -> right.any { tokenMatch(u, it) } }
        val inWrong = user.map { u -> others.any { tokenMatch(u, it) } }
        if (user.indices.any { inWrong[it] && !inRight[it] }) return false
        // Число, которого нет в верном ответе, — ошибка, даже если остальное совпало.
        if (user.indices.any { isNumber(user[it]) && !inRight[it] }) return false

        val recall = recall(user, right)
        // Совпавшее число говорит о точности больше, чем слово, и весит вдвое.
        val distinctive = user.indices.filter { inRight[it] && !inWrong[it] }.sumOf { if (isNumber(user[it])) 2L else 1L }

        // 3. Все слова ответа взяты из верного варианта, и хотя бы одно отличает его от остальных.
        if (inRight.all { it } && distinctive >= 1 && (recall >= 0.34 || distinctive >= 2)) return true
        // 4. Названа большая часть верного ответа, и он подходит лучше любого неверного.
        return recall >= 0.6 && wrongTokens.all { recall(user, it) < recall }
    }

    private fun isNumber(token: String) = token.any { it.isDigit() }

    /** Доля слов варианта [option], которые есть в ответе [user]. */
    private fun recall(user: List<String>, option: List<String>): Double =
        if (option.isEmpty()) 0.0 else option.count { o -> user.any { tokenMatch(it, o) } }.toDouble() / option.size

    private fun typoTolerance(len: Int): Int = when {
        len < 5 -> 0
        len < 9 -> 1
        len < 16 -> 2
        else -> 3
    }

    private val negations = setOf("не", "нет", "ни")

    private val stopWords = setOf(
        "и", "а", "но", "или", "в", "во", "на", "с", "со", "к", "ко", "по", "о", "об", "обо", "от", "до",
        "за", "из", "изо", "у", "для", "при", "про", "под", "над", "же", "ли", "бы", "это", "этот", "эта",
        "эти", "то", "тот", "та", "те", "что", "чтобы", "как", "так", "его", "ее", "их", "он", "она",
        "оно", "они", "мы", "вы", "я", "ты", "свой", "своя", "свое", "свои", "который", "которая",
        "которое", "которые", "весь", "вся", "все", "всё", "был", "была", "было", "были", "быть", "есть",
        "около", "примерно", "только", "уже", "еще", "очень", "также", "тоже", "лишь", "ведь", "вот",
        "там", "тут", "где", "когда", "the", "a", "an", "of",
    )

    private val abbreviations = mapOf(
        "млрд" to "миллиард", "млн" to "миллион", "тыс" to "тысяч", "км" to "километр",
        "кг" to "килограмм", "см" to "сантиметр", "мм" to "миллиметр",
    )

    /** Строка без пробелов и пунктуации — для дословного сравнения и формул. */
    fun compact(text: String): String {
        var s = text.lowercase().replace('ё', 'е')
        s = s.replace("квадратный корень из", "√").replace("корень из", "√")
        s = superscripts(s)
        s = s.replace('−', '-').replace('–', '-').replace('—', '-')
            .replace('×', '*').replace('·', '*').replace('∙', '*')
            .replace(Regex("(?<=\\d),(?=\\d)"), ".")
        s = numberWordsToDigits(s)
        return s.filter { it.isLetterOrDigit() || it in "√^-+*/=<>%°π½⅓¼¾.!" }.trimEnd('.', '!')
    }

    private fun superscripts(s: String): String {
        val map = mapOf(
            '⁰' to '0', '¹' to '1', '²' to '2', '³' to '3', '⁴' to '4', '⁵' to '5', '⁶' to '6',
            '⁷' to '7', '⁸' to '8', '⁹' to '9', '⁻' to '-', '⁺' to '+', 'ᵖ' to 'p', 'ⁿ' to 'n', 'ˣ' to 'x',
        )
        val out = StringBuilder()
        var inSup = false
        for (c in s) {
            val m = map[c]
            if (m != null) {
                if (!inSup) out.append('^')
                out.append(m)
                inSup = true
            } else {
                out.append(c)
                inSup = false
            }
        }
        return out.toString()
    }

    /** Значимые слова: нижний регистр, числа цифрами, без служебных слов, основы слов. */
    fun tokens(text: String, dropStopWords: Boolean = true): List<String> {
        var s = romanToArabic(text).lowercase().replace('ё', 'е')
        s = s.replace(Regex("(?<=\\d)[  ](?=\\d{3}(\\D|$))"), "")
        s = numberWordsToDigits(s)
        val raw = tokenPattern.findAll(s).map { it.value }.toList()
        return raw.mapNotNull { t ->
            when {
                t[0].isDigit() || t[0] == '-' || t[0] == '−' -> t.replace('−', '-').replace(',', '.')
                dropStopWords && t in stopWords -> null
                else -> {
                    val w = abbreviations[t] ?: t
                    if (w.any { it in 'а'..'я' }) RussianStemmer.stem(w) else w
                }
            }
        }
    }

    /** Число (минус — только если перед ним не цифра: «1941-1945» — это два года) или слово. */
    private val tokenPattern = Regex("(?<![\\p{L}\\d])[-−]\\d+(?:[.,]\\d+)?|\\d+(?:[.,]\\d+)?|\\p{L}+")

    fun tokenMatch(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.any { it.isDigit() } || b.any { it.isDigit() }) return false
        val short = if (a.length <= b.length) a else b
        val long = if (a.length <= b.length) b else a
        if (short.length >= 6 && long.startsWith(short) && long.length - short.length <= 3) return true
        val minLen = short.length
        if (minLen < 5) return false
        return levenshtein(a, b) <= if (minLen >= 8) 2 else 1
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    // ——— Числа ———

    private val units = buildMap {
        fun put(value: Long, vararg forms: String) = forms.forEach { put(it, value) }
        put(0, "ноль", "нуль", "ноля", "нуля")
        put(1, "один", "одна", "одно", "одного", "одной", "одну", "одним", "одном", "одному")
        put(2, "два", "две", "двух", "двум", "двумя")
        put(3, "три", "трех", "трем", "тремя")
        put(4, "четыре", "четырех", "четырем", "четырьмя")
        put(5, "пять", "пяти", "пятью")
        put(6, "шесть", "шести", "шестью")
        put(7, "семь", "семи")
        put(8, "восемь", "восьми", "восемью")
        put(9, "девять", "девяти", "девятью")
        put(10, "десять", "десяти", "десятью")
        put(11, "одиннадцать", "одиннадцати")
        put(12, "двенадцать", "двенадцати")
        put(13, "тринадцать", "тринадцати")
        put(14, "четырнадцать", "четырнадцати")
        put(15, "пятнадцать", "пятнадцати")
        put(16, "шестнадцать", "шестнадцати")
        put(17, "семнадцать", "семнадцати")
        put(18, "восемнадцать", "восемнадцати")
        put(19, "девятнадцать", "девятнадцати")
        put(20, "двадцать", "двадцати")
        put(30, "тридцать", "тридцати")
        put(40, "сорок", "сорока")
        put(50, "пятьдесят", "пятидесяти")
        put(60, "шестьдесят", "шестидесяти")
        put(70, "семьдесят", "семидесяти")
        put(80, "восемьдесят", "восьмидесяти")
        put(90, "девяносто", "девяноста")
        put(100, "сто", "ста")
        put(200, "двести", "двухсот")
        put(300, "триста", "трехсот")
        put(400, "четыреста", "четырехсот")
        put(500, "пятьсот", "пятисот")
        put(600, "шестьсот", "шестисот")
        put(700, "семьсот", "семисот")
        put(800, "восемьсот", "восьмисот")
        put(900, "девятьсот", "девятисот")
    }

    private val multipliers = buildMap {
        listOf("тысяча", "тысячи", "тысяч", "тысячу", "тысячей", "тысячам").forEach { put(it, 1_000L) }
        listOf("миллион", "миллиона", "миллионов", "миллионам").forEach { put(it, 1_000_000L) }
        listOf("миллиард", "миллиарда", "миллиардов").forEach { put(it, 1_000_000_000L) }
    }

    private val digitsWithMultiplier =
        Regex("(\\d+(?:[.,]\\d+)?)\\s*(тысяч\\p{L}*|тыс\\.?|миллион\\p{L}*|млн|миллиард\\p{L}*|млрд)(?!\\p{L})")

    /**
     * «Двадцать пять» → «25», «две тысячи» и «2 тысячи» → «2000», «2,4 млн» → «2400000».
     * Слово «миллион» без числа перед ним не трогаем.
     */
    fun numberWordsToDigits(input: String): String {
        val text = digitsWithMultiplier.replace(input) { m ->
            val word = m.groupValues[2]
            val factor = when {
                word.startsWith("тыс") -> 1_000.0
                word.startsWith("миллион") || word == "млн" -> 1_000_000.0
                else -> 1_000_000_000.0
            }
            val value = m.groupValues[1].replace(',', '.').toDouble() * factor
            if (value == Math.floor(value)) value.toLong().toString() else value.toString()
        }
        val words = Regex("\\p{L}+|[^\\p{L}]+").findAll(text).map { it.value }.toList()
        val out = StringBuilder()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            if (w in units) {
                var total = 0L
                var current = 0L
                var j = i
                var last = i
                while (j < words.size) {
                    val t = words[j]
                    when {
                        t.isBlank() -> {}
                        t in units -> { current += units.getValue(t); last = j }
                        t in multipliers -> {
                            total += max(current, 1) * multipliers.getValue(t)
                            current = 0
                            last = j
                        }
                        else -> break
                    }
                    j++
                }
                out.append(total + current)
                i = last + 1
            } else {
                out.append(w)
                i++
            }
        }
        return out.toString()
    }

    private val roman = Regex("\\b(?=[MDCLXVI])M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})\\b")
    private val romanLower = Regex("\\b(?=[xvi])(xc|xl|l?x{0,3})(ix|iv|v?i{0,3})\\b")

    /** Римские числа: заглавные — всегда, строчные — если состоят из i, v, x, l и длиннее одной буквы. */
    fun romanToArabic(text: String): String {
        val upper = roman.replace(text) { m -> if (m.value.isEmpty()) m.value else romanValue(m.value).toString() }
        return romanLower.replace(upper) { m ->
            if (m.value.length < 2) m.value else romanValue(m.value.uppercase()).toString()
        }
    }

    private fun romanValue(s: String): Int {
        val v = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var total = 0
        for (i in s.indices) {
            val cur = v.getValue(s[i])
            val next = if (i + 1 < s.length) v.getValue(s[i + 1]) else 0
            total += if (cur < next) -cur else cur
        }
        return total
    }
}

/** Стеммер Портера для русского языка (алгоритм Snowball) — отбрасывает окончания. */
object RussianStemmer {
    private const val VOWELS = "аеиоуыэюя"

    private val gerund1 = listOf("вшись", "вши", "в")
    private val gerund2 = listOf("ившись", "ывшись", "ивши", "ывши", "ив", "ыв")
    private val reflexive = listOf("ся", "сь")
    private val adjective = listOf(
        "ими", "ыми", "его", "ого", "ему", "ому", "ее", "ие", "ые", "ое", "ей", "ий", "ый", "ой", "ем",
        "им", "ым", "ом", "их", "ых", "ую", "юю", "ая", "яя", "ою", "ею",
    )
    private val participle1 = listOf("ем", "нн", "вш", "ющ", "щ")
    private val participle2 = listOf("ивш", "ывш", "ующ")
    private val verb1 = listOf("ете", "йте", "ешь", "нно", "ла", "на", "ли", "ем", "ло", "но", "ет", "ют", "ны", "ть", "й", "л", "н")
    private val verb2 = listOf(
        "ейте", "уйте", "ила", "ыла", "ена", "ите", "или", "ыли", "ило", "ыло", "ено", "ует", "уют", "ены",
        "ить", "ыть", "ишь", "ей", "уй", "ил", "ыл", "им", "ым", "ен", "ят", "ит", "ыт", "ую", "ю",
    )
    private val noun = listOf(
        "иями", "ями", "ами", "ией", "иям", "ием", "иях", "ев", "ов", "ие", "ье", "еи", "ии", "ей", "ой",
        "ий", "ям", "ем", "ам", "ом", "ах", "ях", "ию", "ью", "ия", "ья", "а", "е", "и", "й", "о", "у",
        "ы", "ь", "ю", "я",
    )

    fun stem(input: String): String {
        val word = input.lowercase().replace('ё', 'е')
        val rv = word.indexOfFirst { it in VOWELS }.let { if (it < 0) return word else it + 1 }
        val r2 = r1(word, r1(word, 0))
        var w = word

        fun endsInRv(e: String) = w.endsWith(e) && w.length - e.length >= rv
        fun removeFrom(list: List<String>, afterAYa: Boolean): Boolean {
            for (e in list.sortedByDescending { it.length }) {
                if (!endsInRv(e)) continue
                if (afterAYa) {
                    val p = w.length - e.length - 1
                    if (p < rv || w[p] != 'а' && w[p] != 'я') continue
                }
                w = w.dropLast(e.length)
                return true
            }
            return false
        }

        // Шаг 1.
        if (!(removeFrom(gerund2, false) || removeFrom(gerund1, true))) {
            removeFrom(reflexive, false)
            if (removeFrom(adjective, false)) {
                if (!removeFrom(participle2, false)) removeFrom(participle1, true)
            } else if (!(removeFrom(verb2, false) || removeFrom(verb1, true))) {
                removeFrom(noun, false)
            }
        }
        // Шаг 2.
        if (endsInRv("и")) w = w.dropLast(1)
        // Шаг 3: словообразовательный суффикс в R2.
        for (e in listOf("ость", "ост")) {
            if (w.endsWith(e) && w.length - e.length >= r2) {
                w = w.dropLast(e.length)
                break
            }
        }
        // Шаг 4.
        when {
            endsInRv("ейше") || endsInRv("ейш") -> {
                w = w.dropLast(if (w.endsWith("ейше")) 4 else 3)
                if (w.endsWith("нн")) w = w.dropLast(1)
            }
            endsInRv("нн") -> w = w.dropLast(1)
            endsInRv("ь") -> w = w.dropLast(1)
        }
        return w.ifEmpty { word }
    }

    /** Начало области R1 (или R2, если передать начало R1): после первой согласной, следующей за гласной. */
    private fun r1(w: String, from: Int): Int {
        var i = from
        while (i < w.length && w[i] !in VOWELS) i++
        while (i < w.length && w[i] in VOWELS) i++
        return if (i < w.length) i + 1 else w.length
    }
}
