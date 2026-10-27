package app.erudit.domain

import app.erudit.data.db.Card

/**
 * Опыт начисляется только за новое: за первый верный ответ на вопрос, за повторение, срок
 * которого наступил, за первое прохождение урока и сдачу экзамена, а в тренажёрах — за лучший
 * результат дня. Повторное прохождение того же самого опыта не даёт.
 */
object Xp {
    const val CORRECT_ANSWER = 2
    const val LESSON = 15
    const val STAGE_EXAM = 50
    const val COURSE_EXAM = 150
    const val DOMAIN_EXAM = 400

    /**
     * Опыт за верный ответ: вопрос ещё ни разу не был отвечен верно или подошёл срок его
     * повторения. Любой ответ переносит срок карточки на будущее, поэтому повторно в тот же
     * день опыт за вопрос не начисляется.
     */
    fun forAnswer(card: Card?, today: Long): Int = when {
        card == null || card.correct == 0 -> CORRECT_ANSWER
        card.due <= today -> CORRECT_ANSWER
        else -> 0
    }

    private const val DAY_SLOT = 100_000L

    /**
     * Тренажёр даёт опыт раз в день — по лучшей тренировке дня: новая тренировка добавляет
     * только разницу с уже начисленным сегодня. [stored] — сохранённое значение
     * (день и начисленный за него опыт), возвращаются прибавка и новое значение для хранения.
     */
    fun trainerGain(stored: Long?, today: Long, xp: Int): Pair<Int, Long> {
        val already = if (stored != null && stored / DAY_SLOT == today) (stored % DAY_SLOT).toInt() else 0
        val total = maxOf(already, xp.coerceIn(0, (DAY_SLOT - 1).toInt()))
        return (total - already) to today * DAY_SLOT + total
    }
}

object Levels {
    /** Суммарный опыт, нужный для достижения уровня [level] (уровень 1 — с нуля). */
    fun xpForLevel(level: Int): Int {
        val n = level - 1
        return 40 * n * n + 60 * n
    }

    fun levelFor(xp: Int): Int {
        var level = 1
        while (xpForLevel(level + 1) <= xp) level++
        return level
    }

    fun title(level: Int): String = when {
        level < 5 -> "Новичок"
        level < 10 -> "Любознательный"
        level < 15 -> "Знаток"
        level < 20 -> "Эрудит"
        level < 30 -> "Мыслитель"
        level < 40 -> "Мудрец"
        else -> "Полимат"
    }

    data class Info(val level: Int, val title: String, val xp: Int, val levelStart: Int, val levelEnd: Int) {
        val fraction: Float get() = ((xp - levelStart).toFloat() / (levelEnd - levelStart)).coerceIn(0f, 1f)
        val toNext: Int get() = levelEnd - xp
    }

    fun info(xp: Int): Info {
        val level = levelFor(xp)
        return Info(level, title(level), xp, xpForLevel(level), xpForLevel(level + 1))
    }
}
