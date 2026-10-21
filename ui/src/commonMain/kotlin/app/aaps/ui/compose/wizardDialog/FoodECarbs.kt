package app.aaps.ui.compose.wizardDialog

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.ui.compose.foodManagement.canUse
import app.aaps.ui.compose.foodManagement.savedAmountECarbs
import kotlin.math.roundToInt

data class WizardCarbPlan(val carbs: Int, val eCarbs: Int, val delayMinutes: Int, val durationHours: Int)

/**
 * Changes for this meal only, made in the eCarbs row's Change panel. Null keeps the value the foods give.
 * Nothing here is saved with a food.
 */
data class MealAdjust(
    val carbsNowPercent: Int? = null,
    /** The protein and fat eCarbs of the whole meal, without the carbs held back from the bolus. */
    val eCarbsGrams: Int? = null,
    val delayMinutes: Int? = null,
    val durationHours: Int? = null
)

/** True while the food formulas are the carbs choice, instead of bread, dumplings or pizza. */
val WizardDialogUiState.foodMode: Boolean get() = foods.any { it.enabled }

/** The foods whose formula is used. */
private val WizardDialogUiState.formulaFoods: List<FoodECarbsState> get() = foods.filter { it.enabled }

/**
 * Part of the meal carbs that the bolus counts now, from the foods: each food's own percent, weighted
 * by its carbs. A food without a formula counts all its carbs now. For one food this is its percent.
 */
val WizardDialogUiState.foodsCarbsNowPercent: Double get() = foodsCarbsNowShare.let { (part, whole) -> part * 100.0 / whole }

/** [foodsCarbsNowPercent] as a fraction of whole numbers, so the grams counted now have no rounding error. */
private val WizardDialogUiState.foodsCarbsNowShare: Pair<Long, Long>
    get() {
        val withCarbs = foods.filter { it.carbsGrams > 0 }
        val total = withCarbs.sumOf { it.carbsGrams.toLong() }
        if (total > 0) return withCarbs.sumOf { it.carbsGrams.toLong() * (if (it.enabled) it.rule.carbsNowPercent else 100) } to total * 100
        // No food has carbs, only carbs typed by hand: the plain average of the formulas
        val percents = formulaFoods.map { it.rule.carbsNowPercent.toLong() }
        return if (percents.isEmpty()) 1L to 1L else percents.sum() to percents.size * 100L
    }

private val WizardDialogUiState.mealCarbsNowShare: Pair<Long, Long>
    get() = mealAdjust.carbsNowPercent?.let { it.toLong() to 100L } ?: foodsCarbsNowShare

val WizardDialogUiState.mealCarbsNowPercent: Double get() = mealCarbsNowShare.let { (part, whole) -> part * 100.0 / whole }

/** The protein and fat eCarbs that the formulas of the foods give for the amounts eaten. */
val WizardDialogUiState.foodsECarbsGrams: Int get() = formulaFoods.sumOf { it.calculatedGrams }

val WizardDialogUiState.mealECarbsGrams: Int get() = mealAdjust.eCarbsGrams ?: foodsECarbsGrams

/**
 * One eCarbs entry for the whole meal: from the earliest delay of the foods to the latest end, at most
 * the longest duration a formula allows.
 */
val WizardDialogUiState.foodsTiming: Pair<Int, Int>
    get() {
        val rules = formulaFoods.map { it.rule }
        if (rules.isEmpty()) return 0 to 0
        val start = rules.minOf { it.delayMinutes }
        val end = rules.maxOf { it.delayMinutes + it.durationHours * 60 }
        return start to ((end - start + 59) / 60).coerceAtMost(FoodECarbsRule.DURATION_HOURS.last)
    }

val WizardDialogUiState.mealDelayMinutes: Int get() = mealAdjust.delayMinutes ?: foodsTiming.first
val WizardDialogUiState.mealDurationHours: Int get() = mealAdjust.durationHours ?: foodsTiming.second

/** Food mode replaces the carbs type split: never add both estimates for the same meal. */
fun WizardDialogUiState.carbPlan(): WizardCarbPlan =
    if (foodMode) {
        // Carbs held back from the bolus move to the eCarbs, so the meal total stays the same
        val (part, whole) = mealCarbsNowShare
        val carbsNow = (carbs * part / whole).toInt()
        WizardCarbPlan(carbsNow, mealECarbsGrams + carbs - carbsNow, mealDelayMinutes, mealDurationHours)
    } else {
        WizardCarbPlan(carbs * carbsType.carbsPercent / 100, carbs * carbsType.eCarbsPercent / 100, carbsType.eCarbsDelayMinutes, carbsType.eCarbsDurationHours)
    }

private val WizardDialogUiState.mealAdjustValid: Boolean
    get() = (mealAdjust.carbsNowPercent?.let { it in FoodECarbsRule.CARBS_NOW_PERCENT } ?: true) &&
        (mealAdjust.eCarbsGrams?.let { it in 0..maxCarbs } ?: true) &&
        mealDelayMinutes in FoodECarbsRule.DELAY_MINUTES &&
        mealDurationHours in FoodECarbsRule.DURATION_HOURS

val WizardDialogUiState.foodInputsValid: Boolean
    get() = !foodUnavailable && (foods.isEmpty() ||
        (foods.all { it.isValid(maxCarbs) } && carbs in 0..maxCarbs && carbPlan().eCarbs in 0..maxCarbs && (!foodMode || mealAdjustValid)))

/** One food of a meal. [amount] is in the food's own unit and is scaled against [FD.portion]. */
data class FoodECarbsState(
    val food: FD,
    val amount: Double = if (food.askAmount) 0.0 else food.portion,
    val enabled: Boolean = false,
    val rule: FoodECarbsRule = FoodECarbsRule()
) {

    private val scale: Double get() = if (food.portion > 0.0) amount / food.portion else 0.0
    val canUseRule: Boolean get() = food.canUse(rule)
    val proteinGrams: Double get() = (food.protein ?: 0) * scale
    val fatGrams: Double get() = (food.fat ?: 0) * scale
    val carbsGrams: Int get() = (food.carbs * scale).takeIf { it.isFinite() }?.roundToInt() ?: 0
    val calculatedGrams: Int get() = (food.savedAmountECarbs(rule) * scale).roundToInt()
    val hasAmount: Boolean get() = amount > 0.0
    val maxAmount: Double get() = food.portion * MAX_PORTIONS
    val amountStep: Double get() = if (food.portion < 20.0) 0.5 else 10.0

    fun isValid(maxCarbs: Int): Boolean =
        food.portion.isFinite() && food.portion > 0.0 && amount.isFinite() && amount > 0.0 && amount <= maxAmount &&
            food.carbs >= 0 && scale.isFinite() && (food.carbs * scale).isFinite() && proteinGrams.isFinite() && fatGrams.isFinite() &&
            (!enabled || (canUseRule && rule.isValid && calculatedGrams in 0..maxCarbs))

    companion object {

        const val MAX_PORTIONS = 20.0

        /** One table, not a shopping list: more foods than this are a typing mistake. */
        const val MAX_FOODS = 10

        /** A food as the wizard starts it: its saved formula on, when the food has what the formula needs. */
        fun of(food: FD): FoodECarbsState {
            val rule = food.eCarbsRule
            val state = FoodECarbsState(food, rule = rule ?: FoodECarbsRule())
            return state.copy(enabled = rule != null && state.canUseRule)
        }
    }
}
