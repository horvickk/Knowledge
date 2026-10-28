package app.erudit.trainers

import kotlin.math.roundToInt
import kotlin.random.Random

data class MathProblem(val text: String, val answer: Int)

/** Устный счёт: уровень 1–10, сложность растёт с уровнем. */
object MentalMath {
    const val MAX_LEVEL = 10

    fun generate(level: Int, r: Random = Random.Default): MathProblem = when (level.coerceIn(1, MAX_LEVEL)) {
        1 -> add(r.nextInt(2, 20), r.nextInt(2, 20))
        2 -> if (r.nextBoolean()) add(r.nextInt(10, 50), r.nextInt(5, 50)) else sub(r.nextInt(20, 60), r.nextInt(2, 20))
        3 -> mul(r.nextInt(2, 10), r.nextInt(2, 10))
        4 -> if (r.nextBoolean()) add(r.nextInt(20, 100), r.nextInt(20, 100)) else sub(r.nextInt(50, 100), r.nextInt(11, 50))
        5 -> div(r.nextInt(2, 10), r.nextInt(2, 13), r)
        6 -> mul(r.nextInt(12, 100), r.nextInt(3, 10))
        7 -> if (r.nextBoolean()) add(r.nextInt(100, 1000), r.nextInt(20, 100)) else sub(r.nextInt(200, 1000), r.nextInt(20, 200))
        8 -> when (r.nextInt(3)) {
            0 -> r.nextInt(11, 26).let { MathProblem("$it²", it * it) }
            1 -> percent(r)
            else -> div(r.nextInt(11, 20), r.nextInt(3, 10), r)
        }
        9 -> mul(r.nextInt(11, 40), r.nextInt(11, 30))
        else -> {
            val a = r.nextInt(3, 13)
            val b = r.nextInt(3, 13)
            val c = r.nextInt(10, 100)
            if (r.nextBoolean()) MathProblem("$a × $b + $c", a * b + c) else MathProblem("$c + $a × $b − $a", c + a * b - a)
        }
    }

    private fun add(a: Int, b: Int) = MathProblem("$a + $b", a + b)
    private fun sub(a: Int, b: Int) = if (a >= b) MathProblem("$a − $b", a - b) else MathProblem("$b − $a", b - a)
    private fun mul(a: Int, b: Int) = MathProblem("$a × $b", a * b)

    private fun div(divisor: Int, quotient: Int, r: Random): MathProblem {
        val d = divisor.coerceAtLeast(2)
        return if (r.nextBoolean()) MathProblem("${d * quotient} ÷ $d", quotient) else MathProblem("${d * quotient} ÷ $quotient", d)
    }

    private fun percent(r: Random): MathProblem {
        val p = listOf(10, 20, 25, 50, 75).random(r)
        val base = when (p) {
            25, 75 -> r.nextInt(2, 40) * 4
            else -> r.nextInt(2, 50) * 10
        }
        return MathProblem("$p% от $base", base * p / 100)
    }
}

data class NumberSeries(val shown: List<Long>, val answer: Long, val rule: String)

/** Числовые ряды: 12 типов закономерностей, сложность растёт с номером задачи. */
object Series {
    fun generate(difficulty: Int, r: Random = Random.Default): NumberSeries {
        val pool = when {
            difficulty < 3 -> listOf(0, 1, 10)
            difficulty < 6 -> listOf(1, 2, 3, 4, 9, 11)
            else -> listOf(2, 4, 5, 6, 7, 8, 9, 11)
        }
        return make(pool.random(r), r)
    }

    private fun make(type: Int, r: Random): NumberSeries {
        val n = 5
        return when (type) {
            0 -> {
                val a = r.nextInt(1, 30); val d = r.nextInt(2, 12)
                arithmetic(a.toLong(), d.toLong(), n, "Каждый следующий член больше на $d")
            }
            10 -> {
                val d = r.nextInt(3, 15); val a = d * n + r.nextInt(5, 60)
                arithmetic(a.toLong(), -d.toLong(), n, "Каждый следующий член меньше на $d")
            }
            1 -> {
                val a = r.nextInt(1, 6).toLong(); val q = r.nextInt(2, 4).toLong()
                val terms = generateSequence(a) { it * q }.take(n + 1).toList()
                NumberSeries(terms.take(n), terms[n], "Каждый член умножается на $q")
            }
            2 -> {
                val start = r.nextInt(1, 8); val k = r.nextInt(0, 4)
                val terms = (start..start + n).map { (it * it + k).toLong() }
                NumberSeries(terms.take(n), terms[n], if (k == 0) "Квадраты чисел подряд" else "Квадраты чисел плюс $k")
            }
            3 -> {
                var a = r.nextInt(1, 6).toLong(); var b = r.nextInt(a.toInt(), 9).toLong()
                val terms = mutableListOf(a, b)
                while (terms.size < n + 1) { val c = a + b; terms += c; a = b; b = c }
                NumberSeries(terms.take(n), terms[n], "Каждый член — сумма двух предыдущих")
            }
            4 -> {
                val a = r.nextInt(1, 20).toLong(); val d0 = r.nextInt(1, 5).toLong()
                val terms = mutableListOf(a)
                var d = d0
                while (terms.size < n + 1) { terms += terms.last() + d; d++ }
                NumberSeries(terms.take(n), terms[n], "Разности растут на 1: +$d0, +${d0 + 1}, +${d0 + 2}…")
            }
            5 -> {
                val add = r.nextInt(1, 6).toLong(); val mul = r.nextInt(2, 4).toLong()
                val terms = mutableListOf(r.nextInt(1, 6).toLong())
                var i = 0
                while (terms.size < n + 1) { terms += if (i % 2 == 0) terms.last() + add else terms.last() * mul; i++ }
                NumberSeries(terms.take(n), terms[n], "Чередуются действия: +$add и ×$mul")
            }
            6 -> {
                val a1 = r.nextInt(1, 10).toLong(); val d1 = r.nextInt(2, 6).toLong()
                val a2 = r.nextInt(20, 40).toLong(); val d2 = r.nextInt(1, 5).toLong()
                val terms = (0 until n + 2).map { i -> if (i % 2 == 0) a1 + d1 * (i / 2) else a2 - d2 * (i / 2) }
                NumberSeries(terms.take(n + 1), terms[n + 1], "Два чередующихся ряда: +$d1 через один и −$d2 через один")
            }
            7 -> {
                val primes = listOf(2L, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53)
                val s = r.nextInt(0, primes.size - n - 1)
                NumberSeries(primes.subList(s, s + n), primes[s + n], "Простые числа подряд")
            }
            8 -> {
                val k = r.nextInt(1, 4).toLong(); val m = r.nextInt(2, 4).toLong()
                val terms = generateSequence(r.nextInt(1, 5).toLong()) { it * m + k }.take(n + 1).toList()
                NumberSeries(terms.take(n), terms[n], "Каждый член: умножить на $m и прибавить $k")
            }
            9 -> {
                val s = r.nextInt(1, 6)
                val terms = (s..s + n).map { (it * (it + 1) / 2).toLong() }
                NumberSeries(terms.take(n), terms[n], "Треугольные числа: n(n+1)/2")
            }
            else -> {
                val s = r.nextInt(1, 7)
                val terms = (s..s + n).map { (it * (it + 1)).toLong() }
                NumberSeries(terms.take(n), terms[n], "Произведения соседних чисел: n·(n+1)")
            }
        }
    }

    private fun arithmetic(a: Long, d: Long, n: Int, rule: String): NumberSeries {
        val terms = (0..n).map { a + d * it }
        return NumberSeries(terms.take(n), terms[n], rule)
    }
}

/** Последовательность позиций для N-back: примерно треть — совпадения. */
object NBack {
    fun generate(n: Int, trials: Int, r: Random = Random.Default): List<Int> {
        val seq = mutableListOf<Int>()
        repeat(trials) { i ->
            seq += if (i >= n && r.nextDouble() < 0.33) {
                seq[i - n]
            } else {
                var p: Int
                do { p = r.nextInt(9) } while (i >= n && p == seq[i - n])
                p
            }
        }
        return seq
    }

    /**
     * Сбалансированная точность, %: поровну доля найденных совпадений и доля верно пропущенных шагов.
     * Не нажимать ничего — 50%, как и нажимать всё подряд; при подсчёте по всем шагам бездействие
     * давало 70–80%, потому что совпадений лишь около трети.
     */
    fun accuracy(sequence: List<Int>, n: Int, responses: Collection<Int>): Int {
        val scored = n until sequence.size
        val targets = scored.filter { sequence[it] == sequence[it - n] }.toSet()
        val others = scored.count { it !in targets }
        val hits = responses.count { it in targets }
        val falseAlarms = responses.count { it in scored && it !in targets }
        val hitRate = if (targets.isEmpty()) 1.0 else hits.toDouble() / targets.size
        val rejectRate = if (others == 0) 1.0 else (others - falseAlarms).toDouble() / others
        return (50 * (hitRate + rejectRate)).roundToInt().coerceIn(0, 100)
    }

    /** Опыт только за результат выше случайного (50%): до 10 за каждый уровень N. */
    fun xp(accuracy: Int, n: Int): Int = (accuracy - 50).coerceAtLeast(0) / 5 * n
}
