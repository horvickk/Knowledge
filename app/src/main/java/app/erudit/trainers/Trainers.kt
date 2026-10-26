package app.erudit.trainers

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.GridOn
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
    )

    val byId = all.associateBy { it.id }
}
