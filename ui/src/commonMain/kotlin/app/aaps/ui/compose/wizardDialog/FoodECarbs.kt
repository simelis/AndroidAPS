package app.aaps.ui.compose.wizardDialog

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.ui.compose.foodManagement.canUse
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
 * One meal of a food, scaled by [amount] in the food's own unit against the amount saved with it
 * ([FD.portion]). The amount starts empty when the food asks for it every time ([FD.askAmount]), so it
 * is weighed or counted, and at the saved amount otherwise. Nothing here is saved.
 */
data class FoodECarbsState(
    val food: FD,
    val amount: Double = if (food.askAmount) 0.0 else food.portion,
    val enabled: Boolean = false,
    val rule: FoodECarbsRule = FoodECarbsRule(),
    val overrideGrams: Int? = null
) {

    private val scale: Double get() = if (food.portion > 0.0) amount / food.portion else 0.0
    /** False when the food lacks a nutrient the rule needs. */
    val canUseRule: Boolean get() = food.canUse(rule)
    val proteinGrams: Double get() = (food.protein ?: 0) * scale
    val fatGrams: Double get() = (food.fat ?: 0) * scale
    val carbsGrams: Int get() = (food.carbs * scale).takeIf { it.isFinite() }?.roundToInt() ?: 0
    /** A fixed amount is per saved amount of the food, so it scales like the nutrients. */
    val calculatedGrams: Int get() = (rule.manualGrams?.let { it * scale } ?: rule.equivalentGrams(proteinGrams, fatGrams)).roundToInt()
    val eCarbsGrams: Int get() = if (enabled) overrideGrams ?: calculatedGrams else 0
    val hasAmount: Boolean get() = amount > 0.0
    /** Largest amount: [MAX_PORTIONS] times the saved amount. */
    val maxAmount: Double get() = food.portion * MAX_PORTIONS
    /** Half steps for counts like pieces, steps of 10 for amounts like 100 g or 250 ml. */
    val amountStep: Double get() = if (food.portion < 20.0) 0.5 else 10.0

    fun isValid(maxCarbs: Int): Boolean =
        food.portion.isFinite() && food.portion > 0.0 && amount.isFinite() && amount > 0.0 && amount <= maxAmount &&
            food.carbs >= 0 && scale.isFinite() && (food.carbs * scale).isFinite() && proteinGrams.isFinite() && fatGrams.isFinite() &&
            (!enabled || (canUseRule && rule.isValid && eCarbsGrams in 0..maxCarbs))

    companion object {

        /** How many times its saved amount a meal of a food may be. */
        const val MAX_PORTIONS = 20.0
    }
}
