package app.erudit.trainers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

data class Trainer(
    val id: String,
    val title: String,
    val kind: String,
    val description: String,
    val icon: ImageVector,
    val scoreUnit: String,
    /** Для таблицы Шульте рекорд — минимальное время. */
    val lowerIsBetter: Boolean = false,
    /** Пользователь сам выбирает число вопросов, и рекорд для каждого числа свой (флаги, столицы). */
    val perCount: Boolean = false,
) {
    fun formatScore(score: Int): String = if (lowerIsBetter) "%.1f с".format(score / 10f) else "$score $scoreUnit"
}

object Trainers {
    val all = listOf(
        Trainer(
            "math", "Устный счёт", "Счёт",
            "Минута на как можно больше примеров. Каждые три верных ответа подряд повышают сложность: от сложения до умножения двузначных чисел и процентов.",
            Icons.Outlined.Calculate, "примеров",
        ),
        Trainer(
            "series", "Числовые ряды", "Логика",
            "Найдите закономерность и продолжите ряд. Десять задач: арифметические и геометрические прогрессии, квадраты, Фибоначчи, чередования.",
            Icons.Outlined.Timeline, "из 10",
        ),
        Trainer(
            "logic", "Логические задачи", "Логика",
            "Задачи на рассуждение: силлогизмы, рыцари и лжецы, переливания, взвешивания, задачи на порядок и вероятность.",
            Icons.Outlined.Extension, "из 10",
        ),
        Trainer(
            "span", "Запомни цифры", "Память",
            "Цифры появляются по одной — повторите последовательность. С каждым успехом она становится длиннее. Средний результат взрослого — 7±2.",
            Icons.Outlined.Pin, "цифр",
        ),
        Trainer(
            "nback", "N-back", "Память",
            "Квадрат загорается в разных клетках. Нажимайте «Совпадает», если позиция та же, что N шагов назад. Классический тренажёр рабочей памяти.",
            Icons.Outlined.Memory, "%",
        ),
        Trainer(
            "schulte", "Таблица Шульте", "Внимание",
            "Найдите числа от 1 до 25 по порядку как можно быстрее. Старайтесь смотреть в центр и охватывать таблицу периферийным зрением.",
            Icons.Outlined.GridOn, "с", lowerIsBetter = true,
        ),
        Trainer(
            "stroop", "Тест Струпа", "Внимание",
            "Назовите цвет, которым написано слово, а не само слово. 45 секунд на тренировку управляющего контроля внимания.",
            Icons.Outlined.Palette, "очков",
        ),
        Trainer(
            "flags", "Флаги мира", "География",
            "Узнайте страну по флагу и введите её название. Выберите, сколько стран взять: берутся самые известные, так что с 20 начинать легко, а все 194 — вызов для знатоков.",
            Icons.Outlined.Flag, "стран", perCount = true,
        ),
        Trainer(
            "capitals", "Столицы мира", "География",
            "Назовите столицу страны. Выберите, сколько стран взять: сначала идут самые известные. Для каждого числа стран — свой рекорд.",
            Icons.Outlined.LocationCity, "стран", perCount = true,
        ),
    )

    val byId = all.associateBy { it.id }
}

/** Счёт тренажёров с выбором числа стран: больше верных лучше, при равенстве — быстрее. */
object GeoScore {
    private const val TIME_SLOTS = 100_000

    fun encode(correct: Int, seconds: Int): Int = correct * TIME_SLOTS + (TIME_SLOTS - 1 - seconds.coerceIn(0, TIME_SLOTS - 1))
    fun correct(score: Int): Int = score / TIME_SLOTS
    fun seconds(score: Int): Int = TIME_SLOTS - 1 - score % TIME_SLOTS

    /** Ключ, под которым хранятся результаты для заданного числа стран. */
    fun key(trainer: String, count: Int): String = "$trainer:$count"

    /** Ключ последнего выбранного числа стран. */
    fun lastCountKey(trainer: String): String = "${trainer}_count"

    fun format(score: Int, count: Int): String = "${correct(score)} из $count · ${time(seconds(score))}"

    fun time(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
}
