package app.aaps.ui.compose.wizardDialog

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FoodECarbsTest {
    private val meal = FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10)
    private val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)

    @Test
    fun `servings scale every macro before rounding the final equivalent`() {
        val state = FoodECarbsState(meal, amount = 300.0, enabled = true, rule = rule)
        assertThat(state.carbsGrams).isEqualTo(75)
        assertThat(state.proteinGrams).isEqualTo(30.0)
        assertThat(state.fatGrams).isEqualTo(15.0)
        assertThat(state.eCarbsGrams).isEqualTo(26)
    }

    @Test
    fun `a usual amount starts at the saved amount and scales by the amount eaten, a fixed amount too`() {
        val slice = FD(id = 8, name = "Slice", portion = 30.0, unit = "g", carbs = 15, protein = 10, fat = 10, askAmount = false)
        // Starts at the saved amount, so a usual meal is taken as it is
        assertThat(FoodECarbsState(slice).amount).isEqualTo(30.0)
        assertThat(FoodECarbsState(slice).carbsGrams).isEqualTo(15)
        val state = FoodECarbsState(slice, amount = 60.0, enabled = true, rule = rule)
        assertThat(state.carbsGrams).isEqualTo(30)
        // (4 × 20 + 9 × 20) / 10 = 26
        assertThat(state.eCarbsGrams).isEqualTo(26)
        assertThat(state.isValid(100)).isTrue()
        assertThat(state.copy(rule = rule.copy(manualGrams = 5.0)).eCarbsGrams).isEqualTo(10)
        assertThat(state.copy(amount = 0.0).isValid(100)).isFalse()
        assertThat(state.copy(amount = state.maxAmount + 1).isValid(100)).isFalse()
        assertThat(state.amountStep).isEqualTo(10.0)
        assertThat(FoodECarbsState(slice.copy(portion = 3.0, unit = "pieces")).amountStep).isEqualTo(0.5)
    }

    @Test
    fun `a food asked every time starts without an amount or an override`() {
        val state = FoodECarbsState(meal, rule = rule)
        assertThat(state.enabled).isFalse()
        assertThat(state.eCarbsGrams).isEqualTo(0)
        assertThat(state.amount).isEqualTo(0.0)
        assertThat(state.hasAmount).isFalse()
        assertThat(state.overrideGrams).isNull()
    }

    @Test
    fun `food calculation replaces pizza split rather than adding another estimate`() {
        val state = WizardDialogUiState(carbs = 50, carbsType = CarbsType.PIZZA, maxCarbs = 100,
            foodECarbs = FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule))
        assertThat(state.carbPlan()).isEqualTo(WizardCarbPlan(50, 17, 60, 3))
        assertThat(state.copy(foodECarbs = state.foodECarbs!!.copy(enabled = false)).carbPlan())
            .isEqualTo(WizardCarbPlan(40, 17, 60, 3))
    }

    @Test
    fun `a food without carbs can still have eCarbs`() {
        val food = FoodECarbsState(meal.copy(carbs = 0), amount = 200.0, enabled = true, rule = rule)
        assertThat(food.isValid(100)).isTrue()
        assertThat(food.carbsGrams).isEqualTo(0)
        assertThat(food.eCarbsGrams).isEqualTo(17)
    }

    @Test
    fun `FPU counts one unknown nutrient as zero but needs at least one`() {
        // Fat unknown: only the 20 g protein count, (4 x 20) / 10 = 8 g
        val noFat = FoodECarbsState(meal.copy(fat = null), amount = 200.0, enabled = true, rule = rule)
        assertThat(noFat.isValid(100)).isTrue()
        assertThat(noFat.eCarbsGrams).isEqualTo(8)
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
        assertThat(food.eCarbsGrams).isEqualTo(10)
    }

    @Test
    fun `carbs held back from the bolus move to the eCarbs and keep the meal total`() {
        val state = WizardDialogUiState(carbs = 50, maxCarbs = 100,
            foodECarbs = FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule.copy(carbsNowPercent = 80)))
        assertThat(state.carbPlan()).isEqualTo(WizardCarbPlan(40, 27, 60, 3))
        assertThat(state.foodInputsValid).isTrue()
        // The moved carbs count against the limit too: 45 g + 10 g moved is over a 50 g limit
        val food = state.foodECarbs!!
        assertThat(state.copy(maxCarbs = 50, foodECarbs = food.copy(overrideGrams = 40)).foodInputsValid).isTrue()
        assertThat(state.copy(maxCarbs = 50, foodECarbs = food.copy(overrideGrams = 45)).foodInputsValid).isFalse()
    }

    @Test
    fun `food eCarbs require explicit valid timing and stay within the configured limit`() {
        val state = FoodECarbsState(meal, amount = 200.0, enabled = true, rule = rule)
        assertThat(state.isValid(16)).isFalse()
        assertThat(state.copy(overrideGrams = 10).isValid(16)).isTrue()
        assertThat(state.copy(overrideGrams = -1).isValid(100)).isFalse()
        assertThat(state.copy(rule = rule.copy(durationHours = 0)).isValid(100)).isFalse()
        assertThat(state.copy(rule = rule.copy(durationHours = 11)).isValid(100)).isFalse()
        assertThat(state.copy(rule = rule.copy(delayMinutes = 721)).isValid(100)).isFalse()
        assertThat(state.copy(amount = Double.NaN).isValid(100)).isFalse()
        assertThat(state.copy(amount = Double.POSITIVE_INFINITY).isValid(100)).isFalse()
    }

    @Test
    fun `immediate carbs and unavailable foods also block food submission`() {
        val state = WizardDialogUiState(carbs = 101, maxCarbs = 100, foodECarbs = FoodECarbsState(meal))
        assertThat(state.foodInputsValid).isFalse()
        assertThat(state.copy(carbs = 50, foodUnavailable = true).foodInputsValid).isFalse()
    }
}
