package ru.scalejournal.util

object Bmi {

    fun value(weightKg: Double, heightCm: Int): Double? {
        if (weightKg <= 0 || heightCm !in 50..250) return null
        val meters = heightCm / 100.0
        return weightKg / (meters * meters)
    }

    fun category(bmi: Double): String = when {
        bmi < 18.5 -> "недостаточный вес"
        bmi < 25.0 -> "норма"
        bmi < 30.0 -> "избыточный вес"
        else -> "ожирение"
    }
}
