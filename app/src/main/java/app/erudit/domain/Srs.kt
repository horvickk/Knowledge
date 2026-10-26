package app.erudit.domain

import app.erudit.data.db.Card
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Упрощённый SM-2 для вопросов с выбором ответа: оценка бинарная (верно/неверно).
 * Ответ на карточку, срок которой ещё не наступил, не растягивает интервал —
 * иначе свободная тренировка «накручивала» бы повторения.
 */
object Srs {
    private const val MIN_EASE = 1.3
    private const val MAX_EASE = 3.0

    fun answer(card: Card, correct: Boolean, today: Long, now: Long, random: Random = Random.Default): Card {
        val counted = card.copy(
            correct = card.correct + if (correct) 1 else 0,
            wrong = card.wrong + if (correct) 0 else 1,
            lastAnswered = now,
            lastCorrect = correct,
        )
        if (!correct) {
            return counted.copy(
                reps = 0,
                intervalDays = 1,
                lapses = card.lapses + 1,
                ease = (card.ease - 0.2).coerceAtLeast(MIN_EASE),
                due = today + 1,
            )
        }
        val isDue = card.reps == 0 || card.due <= today
        if (!isDue) return counted

        val interval = when (card.reps) {
            0 -> 1
            1 -> 3
            else -> fuzz((card.intervalDays * card.ease).roundToInt().coerceAtLeast(card.intervalDays + 1), random)
        }
        return counted.copy(
            reps = card.reps + 1,
            intervalDays = interval,
            ease = (card.ease + 0.02).coerceAtMost(MAX_EASE),
            due = today + interval,
        )
    }

    /** ±10% к длинным интервалам, чтобы повторения не собирались в один день. */
    private fun fuzz(interval: Int, random: Random): Int {
        if (interval < 5) return interval
        val delta = (interval * 0.1).roundToInt().coerceAtLeast(1)
        return interval + random.nextInt(-delta, delta + 1)
    }
}
