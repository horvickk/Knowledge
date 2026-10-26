package app.erudit.domain

object Xp {
    const val CORRECT_ANSWER = 2
    const val LESSON = 15
    const val STAGE_EXAM = 50
    const val COURSE_EXAM = 150
    const val DOMAIN_EXAM = 400
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
