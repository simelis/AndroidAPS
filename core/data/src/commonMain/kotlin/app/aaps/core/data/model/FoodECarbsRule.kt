package app.aaps.core.data.model

import kotlinx.serialization.Serializable

enum class FoodECarbsMethod { CUSTOM, FPU, MODIFIED_FPU }

/**
 * How a food turns its protein and fat into extra carbs (eCarbs) in the bolus wizard.
 *
 * Calculation choices, not a treatment recommendation. A new rule has no factors and no duration,
 * so it is not valid until the user chooses them.
 */
@Serializable
data class FoodECarbsRule(
    val method: FoodECarbsMethod = FoodECarbsMethod.CUSTOM,
    val proteinPercent: Int = 0,
    val fatPercent: Int = 0,
    val delayMinutes: Int = 0,
    val durationHours: Int = 0,
    /** Fixed eCarbs per 100 g of food. When set, it is used instead of the formula. */
    val manualGramsPer100g: Double? = null
) {

    val isValid: Boolean
        get() = proteinPercent in FACTOR_PERCENT && fatPercent in FACTOR_PERCENT &&
            delayMinutes in DELAY_MINUTES && durationHours in DURATION_HOURS &&
            (manualGramsPer100g == null || (manualGramsPer100g.isFinite() && manualGramsPer100g in MANUAL_GRAMS_PER_100G))

    fun equivalentGrams(proteinGrams: Double, fatGrams: Double): Double {
        if (!proteinGrams.isFinite() || !fatGrams.isFinite() || proteinGrams < 0 || fatGrams < 0) return 0.0
        // Original FPU: 100 kcal = 10 g carbohydrate equivalent. Modified FPU: 200 kcal = 10 g.
        // These are insulin-equivalent estimates, not grams of glucose produced by digestion.
        // Modified-FPU reference: https://doi.org/10.1186/s12986-023-00757-w (adult MDI study).
        return when (method) {
            FoodECarbsMethod.CUSTOM       -> proteinGrams * proteinPercent / 100.0 + fatGrams * fatPercent / 100.0
            FoodECarbsMethod.FPU          -> (proteinGrams * 4.0 + fatGrams * 9.0) / 10.0
            FoodECarbsMethod.MODIFIED_FPU -> (proteinGrams * 4.0 + fatGrams * 9.0) / 20.0
        }
    }

    companion object {

        val FACTOR_PERCENT = 0..100
        val DELAY_MINUTES = 0..720
        val DURATION_HOURS = 1..10
        val MANUAL_GRAMS_PER_100G = 0.0..100.0
    }
}
