package app.aaps.ui.compose.wizardDialog

import app.aaps.core.data.model.FD
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FoodECarbsTest {
    private val meal = FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10)
    private val settings = FoodECarbsSettings(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)

    @Test
    fun `reference methods and custom factors produce independently calculated equivalents`() {
        assertThat(settings.equivalentCarbs(20.0, 10.0)).isEqualTo(17)
        assertThat(settings.copy(method = FoodECarbsMethod.MODIFIED_FPU).equivalentCarbs(20.0, 10.0)).isEqualTo(9)
        assertThat(settings.copy(method = FoodECarbsMethod.CUSTOM, proteinPercent = 25, fatPercent = 10)
            .equivalentCarbs(20.0, 10.0)).isEqualTo(6)
    }

    @Test
    fun `servings scale every macro before rounding the final equivalent`() {
        val state = FoodECarbsState(meal, portionGrams = 300.0, enabled = true, settings = settings)
        assertThat(state.carbsGrams).isEqualTo(75)
        assertThat(state.proteinGrams).isEqualTo(30.0)
        assertThat(state.fatGrams).isEqualTo(15.0)
        assertThat(state.eCarbsGrams).isEqualTo(26)
    }

    @Test
    fun `saved settings do not store a meal portion or override`() {
        val stored = FoodECarbsSettingsStore.save("{}", meal.id, settings)
        val state = FoodECarbsState(meal, settings = FoodECarbsSettingsStore.load(stored, meal.id))
        assertThat(state.enabled).isFalse()
        assertThat(state.eCarbsGrams).isEqualTo(0)
        assertThat(state.portionGrams).isEqualTo(0.0)
        assertThat(state.overrideGrams).isNull()
    }

    @Test
    fun `food calculation replaces pizza split rather than adding another estimate`() {
        val state = WizardDialogUiState(carbs = 50, carbsType = CarbsType.PIZZA, maxCarbs = 100,
            foodECarbs = FoodECarbsState(meal, portionGrams = 200.0, enabled = true, settings = settings))
        assertThat(state.carbPlan()).isEqualTo(WizardCarbPlan(50, 17, 60, 3))
        assertThat(state.copy(foodECarbs = state.foodECarbs!!.copy(enabled = false)).carbPlan())
            .isEqualTo(WizardCarbPlan(40, 17, 60, 3))
    }

    @Test
    fun `zero carbohydrate food can have eCarbs without dividing by its carbohydrate amount`() {
        val food = FoodECarbsState(meal.copy(carbs = 0), portionGrams = 200.0, enabled = true, settings = settings)
        assertThat(food.isValid(100)).isTrue()
        assertThat(food.carbsGrams).isEqualTo(0)
        assertThat(food.eCarbsGrams).isEqualTo(17)
    }

    @Test
    fun `missing macros are not treated as known zero amounts`() {
        assertThat(FoodECarbsState(meal.copy(fat = null), portionGrams = 200.0, enabled = true, settings = settings).isValid(100)).isFalse()
        assertThat(FoodECarbsState(meal.copy(protein = null), portionGrams = 200.0, enabled = true, settings = settings).isValid(100)).isFalse()
        assertThat(FoodECarbsState(meal.copy(protein = 0, fat = 0), portionGrams = 200.0, enabled = true, settings = settings).isValid(100)).isTrue()
        assertThat(FoodECarbsState(meal.copy(fat = -1), portionGrams = 200.0, enabled = true, settings = settings).isValid(100)).isFalse()
    }

    @Test
    fun `food eCarbs require explicit valid timing and stay within the configured limit`() {
        val state = FoodECarbsState(meal, portionGrams = 200.0, enabled = true, settings = settings)
        assertThat(state.isValid(16)).isFalse()
        assertThat(state.copy(overrideGrams = 10).isValid(16)).isTrue()
        assertThat(state.copy(overrideGrams = -1).isValid(100)).isFalse()
        assertThat(state.copy(settings = settings.copy(durationHours = 0)).isValid(100)).isFalse()
        assertThat(state.copy(settings = settings.copy(durationHours = 11)).isValid(100)).isFalse()
        assertThat(state.copy(settings = settings.copy(delayMinutes = 721)).isValid(100)).isFalse()
        assertThat(state.copy(portionGrams = Double.NaN).isValid(100)).isFalse()
        assertThat(state.copy(portionGrams = Double.POSITIVE_INFINITY).isValid(100)).isFalse()
    }

    @Test
    fun `immediate carbs and unavailable foods also block food submission`() {
        val state = WizardDialogUiState(carbs = 101, maxCarbs = 100, foodECarbs = FoodECarbsState(meal))
        assertThat(state.foodInputsValid).isFalse()
        assertThat(state.copy(carbs = 50, foodUnavailable = true).foodInputsValid).isFalse()
    }

    @Test
    fun `saving one food preserves the other food settings`() {
        val other = settings.copy(method = FoodECarbsMethod.CUSTOM, proteinPercent = 10, durationHours = 5)
        val first = FoodECarbsSettingsStore.save("{}", 7, settings)
        val both = FoodECarbsSettingsStore.save(first, 8, other)
        assertThat(FoodECarbsSettingsStore.load(both, 7)).isEqualTo(settings)
        assertThat(FoodECarbsSettingsStore.load(both, 8)).isEqualTo(other)
        assertThat(FoodECarbsSettingsStore.load(both, 9)).isEqualTo(FoodECarbsSettings())
    }

    @Test
    fun `corrupt or unsupported settings revert to unconfigured custom factors`() {
        listOf("", "not json", "[]", """{"7":{"method":"FUTURE","durationHours":3}}""",
            """{"7":{"method":"FPU","durationHours":300}}""",
            """{"7":{"method":{},"durationHours":3}}""").forEach {
            assertThat(FoodECarbsSettingsStore.load(it, 7)).isEqualTo(FoodECarbsSettings())
        }
    }
}
