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
 * One meal of a food. A weighed food is scaled by [portionGrams], a food counted in portions by
 * [portions]. The amounts and the overrides here are never saved.
 */
data class FoodECarbsState(
    val food: FD,
    val portionGrams: Double = 0.0,
    val portions: Double = 1.0,
    val enabled: Boolean = false,
    val rule: FoodECarbsRule = FoodECarbsRule(),
    val overrideGrams: Int? = null
) {

    /** True when the wizard asks for the weight eaten, false when it asks for the number of portions. */
    val byWeight: Boolean get() = food.portionWeightGrams != null
    private val scale: Double get() = food.portionWeightGrams?.let { portionGrams / it } ?: portions
    /** False when the food lacks a nutrient the rule needs. */
    val canUseRule: Boolean get() = food.canUse(rule)
    val proteinGrams: Double get() = (food.protein ?: 0) * scale
    val fatGrams: Double get() = (food.fat ?: 0) * scale
    val carbsGrams: Int get() = (food.carbs * scale).takeIf { it.isFinite() }?.roundToInt() ?: 0
    /** A fixed amount is per 100 g of a weighed food, or per portion. */
    val calculatedGrams: Int get() = (rule.manualGrams?.let { it * (if (byWeight) portionGrams / 100.0 else portions) }
        ?: rule.equivalentGrams(proteinGrams, fatGrams)).roundToInt()
    val eCarbsGrams: Int get() = if (enabled) overrideGrams ?: calculatedGrams else 0
    /** Weight or number of portions entered. */
    val hasAmount: Boolean get() = if (byWeight) portionGrams > 0.0 else portions > 0.0

    fun isValid(maxCarbs: Int): Boolean =
        (if (byWeight) portionGrams.isFinite() && portionGrams > 0.0 && portionGrams <= MAX_PORTION_GRAMS
        else portions.isFinite() && portions > 0.0 && portions <= MAX_PORTIONS) &&
            food.carbs >= 0 && scale.isFinite() && (food.carbs * scale).isFinite() && proteinGrams.isFinite() && fatGrams.isFinite() &&
            (!enabled || (canUseRule && rule.isValid && eCarbsGrams in 0..maxCarbs))

    companion object {

        const val MAX_PORTION_GRAMS = 10000.0
        const val MAX_PORTIONS = 20.0
        const val PORTIONS_STEP = 0.5
    }
}
