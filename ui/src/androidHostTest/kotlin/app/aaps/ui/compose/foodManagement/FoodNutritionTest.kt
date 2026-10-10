package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD
import app.aaps.ui.compose.wizardDialog.FoodECarbsMethod
import app.aaps.ui.compose.wizardDialog.FoodECarbsSettings
import app.aaps.ui.compose.wizardDialog.FoodECarbsSettingsStore
import app.aaps.ui.compose.wizardDialog.FoodECarbsState
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FoodNutritionTest {
    private val meal = FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, fat = 10, protein = 20)
    private val rule = FoodECarbsSettings(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)

    @Test
    fun `known mass portions display on a 100 gram basis without modifying the food`() {
        assertThat(meal.nutrientsPer100g()).isEqualTo(FoodNutrition(25.0, 5.0, 10.0, null))
        assertThat(meal.copy(portion = 0.2, unit = "kg").nutrientsPer100g()).isEqualTo(meal.nutrientsPer100g())
        assertThat(meal.portion).isEqualTo(200.0)
        listOf("pieces", "cups", "ml").forEach { assertThat(meal.copy(unit = it).nutrientsPer100g()).isNull() }
        assertThat(meal.copy(portion = 0.0).nutrientsPer100g()).isNull()
    }

    @Test
    fun `gram portions scale original precision and round only the final amounts`() {
        val portion = FoodECarbsState(meal, portionGrams = 150.0, enabled = true, settings = rule)
        assertThat(portion.carbsGrams).isEqualTo(38)
        assertThat(portion.proteinGrams).isEqualTo(15.0)
        assertThat(portion.fatGrams).isEqualTo(7.5)
        assertThat(portion.eCarbsGrams).isEqualTo(13)
        assertThat(portion.copy(portionGrams = 0.0).isValid(100)).isFalse()
        assertThat(portion.copy(food = meal.copy(unit = "ml")).isValid(100)).isFalse()
    }

    @Test
    fun `a stored manual equivalent scales by weight independently of the original database portion`() {
        val manual = rule.copy(manualGramsPer100g = 10.5)
        val json = FoodECarbsSettingsStore.save("{}", 7, manual)
        val stored = FoodECarbsSettingsStore.load(json, 7)
        val portion = FoodECarbsState(meal, portionGrams = 150.0, enabled = true, settings = stored)
        assertThat(portion.eCarbsGrams).isEqualTo(16)
        assertThat(portion.copy(portionGrams = 300.0).eCarbsGrams).isEqualTo(32)
        assertThat(FoodECarbsSettingsStore.load(FoodECarbsSettingsStore.remove(json, 7), 7).isValid).isFalse()
        assertThat(rule.copy(manualGramsPer100g = Double.NaN).isValid).isFalse()
    }

    @Test
    fun `unrepresentable imported weights cannot produce a valid meal or crash carbohydrate rounding`() {
        val imported = meal.copy(portion = Double.MIN_VALUE, carbs = 0)
        assertThat(imported.nutrientsPer100g()).isNull()
        val portion = FoodECarbsState(imported, portionGrams = 100.0, enabled = true, settings = rule)
        assertThat(portion.carbsGrams).isEqualTo(0)
        assertThat(portion.isValid(100)).isFalse()
    }

    @Test
    fun `malformed manual equivalents disable the saved rule instead of switching to formula dosing`() {
        val json = """{"7":{"method":"FPU","durationHours":3,"manualGramsPer100g":"invalid"}}"""
        assertThat(FoodECarbsSettingsStore.load(json, 7).isValid).isFalse()
    }
}
