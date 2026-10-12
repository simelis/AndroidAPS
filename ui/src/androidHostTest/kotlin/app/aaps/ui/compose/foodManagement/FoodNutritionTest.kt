package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.ui.compose.wizardDialog.FoodECarbsState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FoodNutritionTest {
    private val meal = FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, fat = 10, protein = 20)
    private val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)

    @Test
    fun `the weight of an amount is known only for weight units`() {
        assertThat(weightInGrams(200.0, "g")).isEqualTo(200.0)
        assertThat(weightInGrams(0.2, " KG ")).isEqualTo(200.0)
        listOf("pieces", "cups", "ml").forEach { assertThat(weightInGrams(2.0, it)).isNull() }
        assertThat(weightInGrams(Double.MAX_VALUE, "kg")).isNull()
    }

    @Test
    fun `amounts scale original precision and round only the final amounts`() {
        val portion = FoodECarbsState(meal, amount = 150.0, enabled = true, rule = rule)
        assertThat(portion.carbsGrams).isEqualTo(38)
        assertThat(portion.proteinGrams).isEqualTo(15.0)
        assertThat(portion.fatGrams).isEqualTo(7.5)
        assertThat(portion.eCarbsGrams).isEqualTo(13)
        assertThat(portion.copy(amount = 0.0).isValid(100)).isFalse()
        // Any unit works the same way: the amount eaten against the saved amount
        assertThat(portion.copy(food = meal.copy(unit = "ml")).carbsGrams).isEqualTo(38)
    }

    @Test
    fun `a fixed amount is for the saved amount and scales like the nutrients`() {
        val portion = FoodECarbsState(meal, amount = 150.0, enabled = true, rule = rule.copy(manualGrams = 10.5))
        // 10.5 g for 200 g, so 150 g gives 7.875 g
        assertThat(portion.eCarbsGrams).isEqualTo(8)
        assertThat(portion.copy(amount = 400.0).eCarbsGrams).isEqualTo(21)
    }

    @Test
    fun `unrepresentable imported amounts cannot produce a valid meal or crash carbohydrate rounding`() {
        val imported = meal.copy(portion = Double.MIN_VALUE, carbs = 0)
        val portion = FoodECarbsState(imported, amount = 100.0, enabled = true, rule = rule)
        assertThat(portion.carbsGrams).isEqualTo(0)
        assertThat(portion.isValid(100)).isFalse()
    }

    @Test
    fun `weight units are recognised and nutrients can be unknown`() {
        listOf("g", " Grams ", "gram", "kg").forEach { assertThat(FD.isWeightUnit(it)).isTrue() }
        listOf("ml", "pieces").forEach { assertThat(FD.isWeightUnit(it)).isFalse() }
        assertThat(meal.proteinKnown && meal.fatKnown).isTrue()
        assertThat(meal.copy(fat = null).fatKnown).isFalse()
        assertThat(meal.copy(protein = -1).proteinKnown).isFalse()
        assertThat(100.0.formatAmount()).isEqualTo("100")
        assertThat(0.5.formatAmount()).isEqualTo("0.5")
    }
}
