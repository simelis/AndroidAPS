package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD

/** Existing portion-based foods are converted for display/calculation without rewriting the database. */
val FD.portionWeightGrams: Double?
    get() {
        if (!portion.isFinite() || portion <= 0.0) return null
        return when (unit.trim().lowercase()) {
            "g", "gram", "grams" -> portion
            "kg"                 -> (portion * 1000.0).takeIf { it.isFinite() }
            else                 -> null // Never assume a density or the weight of a piece/cup.
        }
    }

data class FoodNutrition(val carbs: Double, val fat: Double?, val protein: Double?, val energy: Double?)

fun FD.nutrientsPer100g(): FoodNutrition? = portionWeightGrams?.let { weight ->
    val scale = 100.0 / weight
    FoodNutrition(carbs * scale, fat?.times(scale), protein?.times(scale), energy?.times(scale)).takeIf {
        it.carbs.isFinite() && it.fat?.isFinite() != false && it.protein?.isFinite() != false && it.energy?.isFinite() != false
    }
}
