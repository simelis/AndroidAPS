package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule

/**
 * Weight of the stored portion of a weighed food, or null for a food counted in portions. A weighed
 * food stored with another portion (like 30 g from Nightscout) is converted for display and
 * calculation without rewriting the database.
 */
val FD.portionWeightGrams: Double?
    get() {
        if (!byWeight || !portion.isFinite() || portion <= 0.0) return null
        return when (unit.trim().lowercase()) {
            "g", "gram", "grams" -> portion
            "kg"                 -> (portion * 1000.0).takeIf { it.isFinite() }
            else                 -> null // Never assume a density or the weight of a piece/cup.
        }
    }

/** A blank value means unknown, so 0 must be entered for none. */
val FD.proteinKnown: Boolean get() = protein?.let { it >= 0 } == true
val FD.fatKnown: Boolean get() = fat?.let { it >= 0 } == true

/** True when this food has the nutrients that [rule] uses. */
fun FD.canUse(rule: FoodECarbsRule): Boolean = rule.canUse(proteinKnown, fatKnown)

data class FoodNutrition(val carbs: Double, val fat: Double?, val protein: Double?, val energy: Double?)

fun FD.nutrientsPer100g(): FoodNutrition? = portionWeightGrams?.let { weight ->
    val scale = 100.0 / weight
    FoodNutrition(carbs * scale, fat?.times(scale), protein?.times(scale), energy?.times(scale)).takeIf {
        it.carbs.isFinite() && it.fat?.isFinite() != false && it.protein?.isFinite() != false && it.energy?.isFinite() != false
    }
}
