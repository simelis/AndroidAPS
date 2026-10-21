package app.aaps.ui.compose.wizardDialog

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FoodECarbsTest {
    private val meal = FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10)
    private val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)
    private val juice = FD(id = 9, name = "Juice", portion = 200.0, carbs = 20, protein = 0, fat = 0, unit = "ml")

    private fun state(vararg foods: FoodECarbsState, carbs: Int = foods.sumOf { it.carbsGrams }, adjust: MealAdjust = MealAdjust()) =
        WizardDialogUiState(carbs = carbs, maxCarbs = 100, foods = foods.toList(), mealAdjust = adjust)

    @Test
    fun `servings scale every macro before rounding the final equivalent`() {
        val state = FoodECarbsState(meal, amount = 300.0, enabled = true, rule = rule)
        assertThat(state.carbsGrams).isEqualTo(75)
        assertThat(state.proteinGrams).isEqualTo(30.0)
        assertThat(state.fatGrams).isEqualTo(15.0)
        assertThat(state.calculatedGrams).isEqualTo(26)
    }

    @Test
    fun `a usual amount starts at the saved amount and scales by the amount eaten, a fixed amount too`() {
        val slice = FD(id = 8, name = "Slice", portion = 30.0, unit = "g", carbs = 15, protein = 10, fat = 10, askAmount = false)
        assertThat(FoodECarbsState(slice).amount).isEqualTo(30.0)
        assertThat(FoodECarbsState(slice).carbsGrams).isEqualTo(15)
        val state = FoodECarbsState(slice, amount = 60.0, enabled = true, rule = rule)
        assertThat(state.carbsGrams).isEqualTo(30)
        // (4 × 20 + 9 × 20) / 10 = 26
        assertThat(state.calculatedGrams).isEqualTo(26)
        assertThat(state.isValid(100)).isTrue()
        assertThat(state.copy(rule = rule.copy(manualGrams = 5.0)).calculatedGrams).isEqualTo(10)
        assertThat(state.copy(amount = 0.0).isValid(100)).isFalse()
        assertThat(state.copy(amount = state.maxAmount + 1).isValid(100)).isFalse()
        assertThat(state.amountStep).isEqualTo(10.0)
        assertThat(FoodECarbsState(slice.copy(portion = 3.0, unit = "pieces")).amountStep).isEqualTo(0.5)
    }

    @Test
    fun `a food asked every time starts without an amount, and without its formula it gives no eCarbs`() {
        val food = FoodECarbsState(meal, rule = rule)
        assertThat(food.enabled).isFalse()
        assertThat(food.amount).isEqualTo(0.0)
        assertThat(food.hasAmount).isFalse()
        assertThat(state(food.copy(amount = 200.0)).foodsECarbsGrams).isEqualTo(0)
    }

    @Test
    fun `a food starts with its saved formula on when it has what the formula needs`() {
        assertThat(FoodECarbsState.of(meal.copy(eCarbsRule = rule)).enabled).isTrue()
        assertThat(FoodECarbsState.of(meal).enabled).isFalse()
        assertThat(FoodECarbsState.of(meal.copy(protein = null, fat = null, eCarbsRule = rule)).enabled).isFalse()
    }

    @Test
    fun `food calculation replaces pizza split rather than adding another estimate`() {
        val state = WizardDialogUiState(carbs = 50, carbsType = CarbsType.PIZZA, maxCarbs = 100,
            foods = listOf(FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule)))
        assertThat(state.carbPlan()).isEqualTo(WizardCarbPlan(50, 17, 60, 3))
        assertThat(state.copy(foods = state.foods.map { it.copy(enabled = false) }).carbPlan())
            .isEqualTo(WizardCarbPlan(40, 17, 60, 3))
    }

    @Test
    fun `a food without carbs can still have eCarbs`() {
        val food = FoodECarbsState(meal.copy(carbs = 0), amount = 200.0, enabled = true, rule = rule)
        assertThat(food.isValid(100)).isTrue()
        assertThat(food.carbsGrams).isEqualTo(0)
        assertThat(food.calculatedGrams).isEqualTo(17)
    }

    @Test
    fun `FPU counts one unknown nutrient as zero but needs at least one`() {
        // Fat unknown: (4 x 20) / 10 = 8 g
        val noFat = FoodECarbsState(meal.copy(fat = null), amount = 200.0, enabled = true, rule = rule)
        assertThat(noFat.isValid(100)).isTrue()
        assertThat(noFat.calculatedGrams).isEqualTo(8)
        assertThat(FoodECarbsState(meal.copy(protein = null), amount = 200.0, enabled = true, rule = rule).isValid(100)).isTrue()
        assertThat(FoodECarbsState(meal.copy(protein = null, fat = null), amount = 200.0, enabled = true, rule = rule).isValid(100)).isFalse()
        assertThat(FoodECarbsState(meal.copy(protein = -1, fat = -1), amount = 200.0, enabled = true, rule = rule).isValid(100)).isFalse()
    }

    @Test
    fun `a fixed amount needs no protein or fat`() {
        // 5 g for the saved 200 g, and 400 g are eaten
        val food = FoodECarbsState(meal.copy(protein = null, fat = null), amount = 400.0, enabled = true,
            rule = rule.copy(manualGrams = 5.0))
        assertThat(food.isValid(100)).isTrue()
        assertThat(food.calculatedGrams).isEqualTo(10)
    }

    @Test
    fun `carbs held back from the bolus move to the eCarbs and keep the meal total`() {
        val state = state(FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule.copy(carbsNowPercent = 80)))
        assertThat(state.carbPlan()).isEqualTo(WizardCarbPlan(40, 27, 60, 3))
        assertThat(state.foodInputsValid).isTrue()
        // 45 g + 10 g moved is over the 50 g limit
        assertThat(state.copy(maxCarbs = 50, mealAdjust = MealAdjust(eCarbsGrams = 40)).foodInputsValid).isTrue()
        assertThat(state.copy(maxCarbs = 50, mealAdjust = MealAdjust(eCarbsGrams = 45)).foodInputsValid).isFalse()
    }

    @Test
    fun `food eCarbs require explicit valid timing and stay within the configured limit`() {
        val food = FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule)
        assertThat(food.isValid(16)).isFalse()
        assertThat(food.isValid(17)).isTrue()
        assertThat(food.copy(rule = rule.copy(durationHours = 0)).isValid(100)).isFalse()
        assertThat(food.copy(rule = rule.copy(durationHours = 11)).isValid(100)).isFalse()
        assertThat(food.copy(rule = rule.copy(delayMinutes = 721)).isValid(100)).isFalse()
        assertThat(food.copy(amount = Double.NaN).isValid(100)).isFalse()
        assertThat(food.copy(amount = Double.POSITIVE_INFINITY).isValid(100)).isFalse()
        assertThat(state(food, adjust = MealAdjust(eCarbsGrams = -1)).foodInputsValid).isFalse()
        assertThat(state(food, adjust = MealAdjust(durationHours = 0)).foodInputsValid).isFalse()
        assertThat(state(food, adjust = MealAdjust(carbsNowPercent = 49)).foodInputsValid).isFalse()
    }

    @Test
    fun `immediate carbs and unavailable foods also block food submission`() {
        val state = WizardDialogUiState(carbs = 101, maxCarbs = 100, foods = listOf(FoodECarbsState(meal)))
        assertThat(state.foodInputsValid).isFalse()
        assertThat(state.copy(carbs = 50, foodUnavailable = true).foodInputsValid).isFalse()
    }

    // ---------------------------------------------------------------------------------------------
    // A meal of several foods
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `the meal adds the carbs and eCarbs of every food`() {
        val pizza = FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule.copy(carbsNowPercent = 80))
        val drink = FoodECarbsState(juice, amount = 200.0)
        val state = state(pizza, drink)
        // 50 g at 80 % and 20 g at 100 %: (50 x 80 + 20 x 100) / 70 = 85.7 %
        assertThat(state.carbs).isEqualTo(70)
        assertThat(state.foodsCarbsNowPercent).isWithin(0.01).of(85.71)
        // 70 x 85.7 % = 60 g now, with no rounding error; the other 10 g join the 17 g from protein and fat
        assertThat(state.carbPlan()).isEqualTo(WizardCarbPlan(60, 27, 60, 3))
        assertThat(state.foodInputsValid).isTrue()
    }

    @Test
    fun `one eCarbs entry runs from the earliest delay to the latest end`() {
        val pizza = FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule.copy(delayMinutes = 60, durationHours = 3))
        val stew = FoodECarbsState(meal.copy(id = 10, name = "Stew"), amount = 200.0, enabled = true, rule = rule.copy(delayMinutes = 30, durationHours = 5))
        // 30 min to 5 h 30 min after carb time
        assertThat(state(pizza, stew).foodsTiming).isEqualTo(30 to 5)
        val long = FoodECarbsState(meal.copy(id = 11, name = "Long"), amount = 200.0, enabled = true, rule = rule.copy(delayMinutes = 0, durationHours = 10))
        // Never longer than a formula allows
        assertThat(state(pizza.copy(rule = rule.copy(delayMinutes = 120, durationHours = 10)), long).foodsTiming).isEqualTo(0 to 10)
    }

    @Test
    fun `carbs typed by hand get the split of the meal`() {
        val pizza = FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule.copy(carbsNowPercent = 80))
        // 10 g more than the food: 60 x 80 % = 48 g now
        assertThat(state(pizza, carbs = 60).carbPlan()).isEqualTo(WizardCarbPlan(48, 29, 60, 3))
    }

    @Test
    fun `the changes for this meal win over the foods`() {
        val pizza = FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule.copy(carbsNowPercent = 80))
        val state = state(pizza, FoodECarbsState(juice, amount = 200.0),
            adjust = MealAdjust(carbsNowPercent = 100, eCarbsGrams = 5, delayMinutes = 15, durationHours = 2))
        assertThat(state.carbPlan()).isEqualTo(WizardCarbPlan(70, 5, 15, 2))
    }

    @Test
    fun `foods without a formula count all their carbs now and no eCarbs`() {
        val state = state(FoodECarbsState(juice, amount = 200.0), FoodECarbsState(juice.copy(id = 12, name = "Bread"), amount = 100.0))
        assertThat(state.foodMode).isFalse()
        assertThat(state.carbPlan()).isEqualTo(WizardCarbPlan(30, 0, 0, 0))
    }
}
