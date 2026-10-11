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

/** One meal of a gram-based food. Portion weight and the overrides here are never saved. */
data class FoodECarbsState(
    val food: FD,
    val portionGrams: Double = 0.0,
    val enabled: Boolean = false,
    val rule: FoodECarbsRule = FoodECarbsRule(),
    val overrideGrams: Int? = null
) {

    private val scale: Double get() = food.portionWeightGrams?.let { portionGrams / it } ?: 0.0
    /** False when the food lacks a nutrient the rule needs. */
    val canUseRule: Boolean get() = food.canUse(rule)
    val proteinGrams: Double get() = (food.protein ?: 0) * scale
    val fatGrams: Double get() = (food.fat ?: 0) * scale
    val carbsGrams: Int get() = (food.carbs * scale).takeIf { it.isFinite() }?.roundToInt() ?: 0
    val calculatedGrams: Int get() = (rule.manualGramsPer100g?.let { it * portionGrams / 100.0 }
        ?: rule.equivalentGrams(proteinGrams, fatGrams)).roundToInt()
    val eCarbsGrams: Int get() = if (enabled) overrideGrams ?: calculatedGrams else 0

    fun isValid(maxCarbs: Int): Boolean =
        food.portionWeightGrams != null && portionGrams.isFinite() && portionGrams > 0.0 && portionGrams <= MAX_PORTION_GRAMS &&
            food.carbs >= 0 && scale.isFinite() && (food.carbs * scale).isFinite() && proteinGrams.isFinite() && fatGrams.isFinite() &&
            (!enabled || (canUseRule && rule.isValid && eCarbsGrams in 0..maxCarbs))

    companion object {

        const val MAX_PORTION_GRAMS = 10000.0
    }
}
