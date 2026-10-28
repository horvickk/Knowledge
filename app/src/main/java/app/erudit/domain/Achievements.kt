package app.erudit.domain

/** Снимок статистики, по которому проверяются достижения. */
data class StatsSnapshot(
    val lessons: Int = 0,
    val examsPassed: Int = 0,
    val coursesDone: Int = 0,
    val domainsDone: Int = 0,
    val domainsTouched: Int = 0,
    val totalDomains: Int = 0,
    val streak: Int = 0,
    val totalCorrect: Int = 0,
    val level: Int = 1,
    val counters: Map<String, Long> = emptyMap(),
) {
    fun counter(key: String): Long = counters[key] ?: 0
}

object Counters {
    const val REVIEWS = "reviews"
    const val PERFECT_EXAMS = "perfect_exams"
    const val NIGHT_OWL = "night_owl"
    const val EARLY_BIRD = "early_bird"
    const val BLITZ_BEST = "blitz_best"
    const val MATH_BEST = "math_best"
    const val SPAN_BEST = "span_best"
    const val SCHULTE_BEST = "schulte_best_ds"
    const val NBACK_LEVEL = "nback_level"
    const val STROOP_BEST = "stroop_best"
    const val SEQ_PERFECT = "seq_perfect"
    const val LOGIC_PERFECT = "logic_perfect"
    const val BEST_STREAK = "best_streak"

    /** Опыт, начисленный тренажёру за сегодня (см. [Xp.trainerGain]). */
    fun trainerXp(trainer: String) = "trainer_xp:$trainer"
}

enum class AchievementGroup(val title: String) {
    STUDY("Учёба"),
    EXAMS("Экзамены"),
    HABIT("Привычка"),
    MIND("Интеллект"),
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val group: AchievementGroup,
    val check: (StatsSnapshot) -> Boolean,
)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("first_lesson", "Первый шаг", "Пройти первый урок", AchievementGroup.STUDY) { it.lessons >= 1 },
        Achievement("lessons_10", "Читатель", "Пройти 10 уроков", AchievementGroup.STUDY) { it.lessons >= 10 },
        Achievement("lessons_50", "Книжный червь", "Пройти 50 уроков", AchievementGroup.STUDY) { it.lessons >= 50 },
        Achievement("lessons_150", "Библиотекарь", "Пройти 150 уроков", AchievementGroup.STUDY) { it.lessons >= 150 },
        Achievement("lessons_300", "Ходячая энциклопедия", "Пройти 300 уроков", AchievementGroup.STUDY) { it.lessons >= 300 },
        Achievement("domains_5", "Разносторонний", "Изучать уроки в 5 разных областях", AchievementGroup.STUDY) { it.domainsTouched >= 5 },
        Achievement("domains_all", "Полимат", "Пройти хотя бы один урок в каждой области", AchievementGroup.STUDY) {
            it.totalDomains > 0 && it.domainsTouched >= it.totalDomains
        },
        Achievement("correct_100", "Сто верных", "Дать 100 правильных ответов", AchievementGroup.STUDY) { it.totalCorrect >= 100 },
        Achievement("correct_1000", "Тысяча верных", "Дать 1000 правильных ответов", AchievementGroup.STUDY) { it.totalCorrect >= 1000 },
        Achievement("correct_5000", "Пять тысяч", "Дать 5000 правильных ответов", AchievementGroup.STUDY) { it.totalCorrect >= 5000 },
        Achievement("level_10", "Знаток", "Достичь 10-го уровня", AchievementGroup.STUDY) { it.level >= 10 },
        Achievement("level_20", "Мыслитель", "Достичь 20-го уровня", AchievementGroup.STUDY) { it.level >= 20 },

        Achievement("first_exam", "Экзамен сдан", "Сдать первый экзамен", AchievementGroup.EXAMS) { it.examsPassed >= 1 },
        Achievement("exams_10", "Отличник", "Сдать 10 разных экзаменов", AchievementGroup.EXAMS) { it.examsPassed >= 10 },
        Achievement("exams_30", "Экзаменатор", "Сдать 30 разных экзаменов", AchievementGroup.EXAMS) { it.examsPassed >= 30 },
        Achievement("perfect_exam", "Без единой ошибки", "Сдать экзамен на 100%", AchievementGroup.EXAMS) { it.counter(Counters.PERFECT_EXAMS) >= 1 },
        Achievement("course_done", "Курс окончен", "Сдать итоговый экзамен курса", AchievementGroup.EXAMS) { it.coursesDone >= 1 },
        Achievement("courses_5", "Широкий профиль", "Окончить 5 курсов", AchievementGroup.EXAMS) { it.coursesDone >= 5 },
        Achievement("domain_done", "Магистр", "Сдать большой экзамен области", AchievementGroup.EXAMS) { it.domainsDone >= 1 },

        Achievement("streak_3", "Разгон", "Выполнять цель дня 3 дня подряд", AchievementGroup.HABIT) { it.counter(Counters.BEST_STREAK) >= 3 },
        Achievement("streak_7", "Неделя знаний", "Серия из 7 дней", AchievementGroup.HABIT) { it.counter(Counters.BEST_STREAK) >= 7 },
        Achievement("streak_30", "Привычка", "Серия из 30 дней", AchievementGroup.HABIT) { it.counter(Counters.BEST_STREAK) >= 30 },
        Achievement("streak_100", "Сотня", "Серия из 100 дней", AchievementGroup.HABIT) { it.counter(Counters.BEST_STREAK) >= 100 },
        Achievement("reviews_100", "Память крепнет", "Сделать 100 повторений", AchievementGroup.HABIT) { it.counter(Counters.REVIEWS) >= 100 },
        Achievement("reviews_1000", "Железная память", "Сделать 1000 повторений", AchievementGroup.HABIT) { it.counter(Counters.REVIEWS) >= 1000 },
        Achievement("night_owl", "Сова", "Заниматься после 23:00", AchievementGroup.HABIT) { it.counter(Counters.NIGHT_OWL) >= 1 },
        Achievement("early_bird", "Жаворонок", "Заниматься до 7:00", AchievementGroup.HABIT) { it.counter(Counters.EARLY_BIRD) >= 1 },

        Achievement("blitz_20", "Молния", "20 верных ответов в блице", AchievementGroup.MIND) { it.counter(Counters.BLITZ_BEST) >= 20 },
        Achievement("math_25", "Калькулятор", "Решить 25 примеров за минуту", AchievementGroup.MIND) { it.counter(Counters.MATH_BEST) >= 25 },
        Achievement("span_9", "Феноменальная память", "Запомнить 9 цифр подряд", AchievementGroup.MIND) { it.counter(Counters.SPAN_BEST) >= 9 },
        Achievement("schulte_25", "Острый взгляд", "Пройти таблицу Шульте быстрее 25 секунд", AchievementGroup.MIND) {
            it.counter(Counters.SCHULTE_BEST) in 1..250
        },
        Achievement("nback_3", "Рабочая память", "Пройти 3-back с точностью от 80%", AchievementGroup.MIND) { it.counter(Counters.NBACK_LEVEL) >= 3 },
        Achievement("stroop_30", "Внимательный", "30 очков в тесте Струпа: верные ответы минус ошибки", AchievementGroup.MIND) { it.counter(Counters.STROOP_BEST) >= 30 },
        Achievement("seq_10", "Чувство закономерности", "Решить все 10 числовых рядов", AchievementGroup.MIND) { it.counter(Counters.SEQ_PERFECT) >= 1 },
        Achievement("logic_10", "Логик", "Решить все 10 логических задач", AchievementGroup.MIND) { it.counter(Counters.LOGIC_PERFECT) >= 1 },
    )

    val byId: Map<String, Achievement> = all.associateBy { it.id }
}
