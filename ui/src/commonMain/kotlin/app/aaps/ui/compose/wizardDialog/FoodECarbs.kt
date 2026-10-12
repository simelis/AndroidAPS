package app.aaps.ui.compose.wizardDialog

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.ui.compose.foodManagement.canUse
import app.aaps.ui.compose.foodManagement.portionWeightGrams
import kotlin.math.roundToInt

data class WizardCarbPlan(val carbs: Int, val eCarbs: Int, val delayMinutes: Int, val durationHours: Int)

/** Food mode replaces the old percentage preset; it must never add both estimates for the same meal. */
fun WizardDialogUiState.carbPlan(): WizardCarbPlan {
    val food = foodECarbs
    return if (food?.enabled == true) {
        // The carbs held back from the bolus are moved to the eCarbs, so the meal total stays the same
        val carbsNow = food.rule.carbsNow(carbs)
        WizardCarbPlan(carbsNow, food.eCarbsGrams + carbs - carbsNow, food.rule.delayMinutes, food.rule.durationHours)
    } else {
        WizardCarbPlan(carbs * carbsType.carbsPercent / 100, carbs * carbsType.eCarbsPercent / 100, carbsType.eCarbsDelayMinutes, carbsType.eCarbsDurationHours)
    }
}

val WizardDialogUiState.foodInputsValid: Boolean
    get() = !foodUnavailable && (foodECarbs?.let { it.isValid(maxCarbs) && carbs in 0..maxCarbs && carbPlan().eCarbs in 0..maxCarbs } ?: true)

/**
 * One meal of a food. A weighed food is scaled by [portionGrams], which starts empty so the food is
 * weighed every time. Any other food is scaled by [amount], in the food's own unit, which starts at
 * the amount saved with the food (its usual amount). The amounts and the overrides here are never saved.
 */
data class FoodECarbsState(
    val food: FD,
    val portionGrams: Double = 0.0,
    val amount: Double = food.portion,
    val enabled: Boolean = false,
    val rule: FoodECarbsRule = FoodECarbsRule(),
    val overrideGrams: Int? = null
) {

    /** True when the wizard asks for the weight eaten, false when it asks for the amount in the food's unit. */
    val byWeight: Boolean get() = food.portionWeightGrams != null
    private val scale: Double get() = food.portionWeightGrams?.let { portionGrams / it } ?: (amount / food.portion)
    /** False when the food lacks a nutrient the rule needs. */
    val canUseRule: Boolean get() = food.canUse(rule)
    val proteinGrams: Double get() = (food.protein ?: 0) * scale
    val fatGrams: Double get() = (food.fat ?: 0) * scale
    val carbsGrams: Int get() = (food.carbs * scale).takeIf { it.isFinite() }?.roundToInt() ?: 0
    /** A fixed amount is per 100 g of a weighed food, or per saved amount of the others. */
    val calculatedGrams: Int get() = (rule.manualGrams?.let { it * (if (byWeight) portionGrams / 100.0 else scale) }
        ?: rule.equivalentGrams(proteinGrams, fatGrams)).roundToInt()
    val eCarbsGrams: Int get() = if (enabled) overrideGrams ?: calculatedGrams else 0
    /** Weight or amount entered. */
    val hasAmount: Boolean get() = if (byWeight) portionGrams > 0.0 else amount > 0.0
    /** Largest amount: [MAX_PORTIONS] times the saved amount. */
    val maxAmount: Double get() = food.portion * MAX_PORTIONS
    /** Half steps for counts like pieces, steps of 10 for amounts like 250 ml or a 30 g slice. */
    val amountStep: Double get() = if (food.portion < 20.0) 0.5 else 10.0

    fun isValid(maxCarbs: Int): Boolean =
        (if (byWeight) portionGrams.isFinite() && portionGrams > 0.0 && portionGrams <= MAX_PORTION_GRAMS
        else food.portion.isFinite() && food.portion > 0.0 && amount.isFinite() && amount > 0.0 && amount <= maxAmount) &&
            food.carbs >= 0 && scale.isFinite() && (food.carbs * scale).isFinite() && proteinGrams.isFinite() && fatGrams.isFinite() &&
            (!enabled || (canUseRule && rule.isValid && eCarbsGrams in 0..maxCarbs))

    companion object {

        const val MAX_PORTION_GRAMS = 10000.0
        /** How many times its saved amount a meal of a food may be. */
        const val MAX_PORTIONS = 20.0
    }
}
