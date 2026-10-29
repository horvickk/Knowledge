package app.erudit.ui.markdown

import java.util.regex.Matcher
import java.util.regex.Pattern

/** Кусок текста с формулами: обычный текст или математический элемент, который рисуется особо. */
sealed interface MathSeg {
    /** Обычный текст; разметка уроков (`**`, `*`, `` ` ``) в нём обрабатывается при отрисовке. */
    data class Plain(val text: String) : MathSeg

    /** Дробь, рисуется двухэтажной. [raw] — исходная запись, например «1/2». */
    data class Frac(val num: String, val den: String, val raw: String) : MathSeg

    /** Корень с чертой над подкоренным выражением; [sign] — «√», «∛» или «∜». */
    data class Root(val sign: Char, val radicand: String, val raw: String) : MathSeg

    /** Верхний индекс из записи «a^(1/2)» или «e^(iπ)». */
    data class Sup(val text: String) : MathSeg

    /** Нижний индекс из записи «T_хол». */
    data class Sub(val text: String) : MathSeg
}

/**
 * Находит в тексте формулы, которые стоит нарисовать по-математически. Дроби распознаются только из
 * «математических» частей: чисел, латинских и греческих букв, корней и выражений в скобках. Поэтому
 * «км/ч», «A/B-тест», «TCP/IP» и «50/30/20» остаются текстом. Деление с пробелами («96 / 4») — тоже
 * текст: в цепочке вычислений неясно, что именно делится. Запись «\/» — косая черта без дроби.
 */
object MathParser {
    private const val SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹⁻ⁿᵖ"
    private const val SUB = "₀₁₂₃₄₅₆₇₈₉"
    private const val NUM = "(?:\\d{1,3}(?:[  ]\\d{3})+|\\d+(?:,\\d+)?)"
    private const val LET = "[A-Za-zα-ωπφ]"
    private const val GROUP = "\\((?:[^()\\n]|\\([^()\\n]*\\))*\\)"
    private const val ROOT = "[√∛∜](?:$GROUP|$NUM|$LET)"
    private const val ATOM = "(?:$ROOT|$GROUP|$NUM[πa-z]?|$LET)[$SUP$SUB]*"

    private val frac = Pattern.compile("(?<![\\p{L}\\p{N}/.,$SUP$SUB)])($ATOM)/($ATOM)(?![\\p{L}\\p{N}/\\-($SUP])")
    private val root = Pattern.compile("([√∛∜])($GROUP|$NUM|$LET)")
    private val caret = Pattern.compile("\\^($GROUP|[0-9A-Za-zα-ωπ⁻-]+)")
    private val sub = Pattern.compile("(?<=\\p{L})_([\\p{L}\\p{N}]+)")

    fun parse(text: String, fractions: Boolean = true): List<MathSeg> {
        val out = mutableListOf<MathSeg>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) out += MathSeg.Plain(plain.toString())
            plain.clear()
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && text.startsWith("\\/", i)) {
                plain.append('/')
                i += 2
                continue
            }
            val seg: MathSeg?
            val end: Int
            val m = if (fractions && mayStartFraction(text, i)) match(frac, text, i) else null
            if (m != null) {
                seg = MathSeg.Frac(unwrap(m.group(1)), unwrap(m.group(2)), m.group())
                end = m.end()
            } else {
                val r = if (c in "√∛∜") match(root, text, i) else null
                val s = if (c == '^') match(caret, text, i) else null
                val u = if (c == '_') match(sub, text, i) else null
                when {
                    r != null -> { seg = MathSeg.Root(c, unwrap(r.group(2)), r.group()); end = r.end() }
                    s != null -> { seg = MathSeg.Sup(unwrap(s.group(1)).replace('/', '⁄')); end = s.end() }
                    u != null -> { seg = MathSeg.Sub(u.group(1)); end = u.end() }
                    else -> { seg = null; end = i + 1 }
                }
            }
            if (seg == null) {
                plain.append(c)
            } else {
                flush()
                out += seg
            }
            i = end
        }
        flush()
        return out
    }

    /** Быстрая проверка перед регулярным выражением: дробь начинается с числа, буквы формулы, скобки или корня. */
    private fun mayStartFraction(text: String, i: Int): Boolean {
        val c = text[i]
        val prev = text.getOrNull(i - 1)
        if (prev != null && prev.isLetterOrDigit()) return false
        return c.isDigit() || c in 'a'..'z' || c in 'A'..'Z' || c in 'α'..'ω' || c == '(' || c in "√∛∜"
    }

    /** Совпадение, начинающееся ровно в [start]; ретроспективные проверки видят текст до него. */
    private fun match(p: Pattern, text: String, start: Int): Matcher? {
        val m = p.matcher(text).region(start, text.length).useTransparentBounds(true).useAnchoringBounds(false)
        return if (m.lookingAt()) m else null
    }

    /** «(1 + √5)» → «1 + √5»: скобки нужны в записи, а на рисунке их заменяют черта дроби или корня. */
    private fun unwrap(s: String): String =
        if (s.length >= 2 && s.first() == '(' && s.last() == ')' && closes(s)) s.substring(1, s.length - 1) else s

    /** Закрывает ли последняя скобка именно первую: у «(a)(b)» внешних скобок нет. */
    private fun closes(s: String): Boolean {
        var depth = 0
        s.forEachIndexed { i, ch ->
            if (ch == '(') depth++
            if (ch == ')') {
                depth--
                if (depth == 0 && i != s.lastIndex) return false
            }
        }
        return true
    }
}
