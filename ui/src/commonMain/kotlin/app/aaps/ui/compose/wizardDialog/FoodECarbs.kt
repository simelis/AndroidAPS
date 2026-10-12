package app.aaps.ui.compose.wizardDialog

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.ui.compose.foodManagement.canUse
import app.aaps.ui.compose.foodManagement.savedAmountECarbs
import kotlin.math.roundToInt

data class WizardCarbPlan(val carbs: Int, val eCarbs: Int, val delayMinutes: Int, val durationHours: Int)

/** Food mode replaces the carbs type split: never add both estimates for the same meal. */
fun WizardDialogUiState.carbPlan(): WizardCarbPlan {
    val food = foodECarbs
    return if (food?.enabled == true) {
        // Carbs held back from the bolus move to the eCarbs, so the meal total stays the same
        val carbsNow = food.rule.carbsNow(carbs)
        WizardCarbPlan(carbsNow, food.eCarbsGrams + carbs - carbsNow, food.rule.delayMinutes, food.rule.durationHours)
    } else {
        WizardCarbPlan(carbs * carbsType.carbsPercent / 100, carbs * carbsType.eCarbsPercent / 100, carbsType.eCarbsDelayMinutes, carbsType.eCarbsDurationHours)
    }
}

val WizardDialogUiState.foodInputsValid: Boolean
    get() = !foodUnavailable && (foodECarbs?.let { it.isValid(maxCarbs) && carbs in 0..maxCarbs && carbPlan().eCarbs in 0..maxCarbs } ?: true)

/** One meal of a food. [amount] is in the food's own unit and is scaled against [FD.portion]. */
data class FoodECarbsState(
    val food: FD,
    val amount: Double = if (food.askAmount) 0.0 else food.portion,
    val enabled: Boolean = false,
    val rule: FoodECarbsRule = FoodECarbsRule(),
    val overrideGrams: Int? = null
) {

    private val scale: Double get() = if (food.portion > 0.0) amount / food.portion else 0.0
    val canUseRule: Boolean get() = food.canUse(rule)
    val proteinGrams: Double get() = (food.protein ?: 0) * scale
    val fatGrams: Double get() = (food.fat ?: 0) * scale
    val carbsGrams: Int get() = (food.carbs * scale).takeIf { it.isFinite() }?.roundToInt() ?: 0
    val calculatedGrams: Int get() = (food.savedAmountECarbs(rule) * scale).roundToInt()
    val eCarbsGrams: Int get() = if (enabled) overrideGrams ?: calculatedGrams else 0
    val hasAmount: Boolean get() = amount > 0.0
    val maxAmount: Double get() = food.portion * MAX_PORTIONS
    val amountStep: Double get() = if (food.portion < 20.0) 0.5 else 10.0

    fun isValid(maxCarbs: Int): Boolean =
        food.portion.isFinite() && food.portion > 0.0 && amount.isFinite() && amount > 0.0 && amount <= maxAmount &&
            food.carbs >= 0 && scale.isFinite() && (food.carbs * scale).isFinite() && proteinGrams.isFinite() && fatGrams.isFinite() &&
            (!enabled || (canUseRule && rule.isValid && eCarbsGrams in 0..maxCarbs))

    companion object {

        const val MAX_PORTIONS = 20.0
    }
}
