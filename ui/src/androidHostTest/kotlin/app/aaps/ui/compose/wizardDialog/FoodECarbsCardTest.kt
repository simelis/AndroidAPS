package app.aaps.ui.compose.wizardDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.UiStringIds
import app.aaps.ui.compose.foodManagement.FoodECarbsRuleEditor
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.ui.R as UiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FoodECarbsCardTest {
    @get:Rule val compose = createComposeRule()
    private val meal = FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10)

    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

    @Test
    fun foodModeStartsOffAndCanBeExplicitlyEnabled() {
        val context = RuntimeEnvironment.getApplication()
        var enabled = false
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(WizardDialogUiState(maxCarbs = 100, foodECarbs = FoodECarbsState(meal,
                    rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))),
                    FoodECarbsCallbacks(enabled = { enabled = it }))
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_method)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(UiR.string.food_ecarbs_enable)).assertIsOff().performClick()
        assertThat(enabled).isTrue()
    }

    @Test
    fun missingMacrosDisableTheSwitchAndExplainWhatToEnter() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(WizardDialogUiState(maxCarbs = 100, foodECarbs = FoodECarbsState(meal.copy(fat = null))), FoodECarbsCallbacks())
            }
        }
        compose.onNodeWithContentDescription(context.getString(UiR.string.food_ecarbs_enable)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_missing_macros)).assertExists()
    }

    @Test
    fun enabledFoodModeShowsTheFormulaResultAndSchedulingPreview() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(
                    WizardDialogUiState(carbs = 50, maxCarbs = 100, foodECarbs = FoodECarbsState(
                        meal, portionGrams = 200.0, enabled = true,
                        rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)
                    )),
                    FoodECarbsCallbacks()
                )
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_calculated, 17)).assertExists()
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_preview, 50, 17, 60, 3)).assertExists()
    }

    @Test
    fun enabledFoodModeCanBeTurnedOffWhileTimingIsInvalid() {
        val context = RuntimeEnvironment.getApplication()
        var enabled = true
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(WizardDialogUiState(maxCarbs = 100, foodECarbs = FoodECarbsState(meal,
                    enabled = true, rule = FoodECarbsRule(durationHours = 0))),
                    FoodECarbsCallbacks(enabled = { enabled = it }))
            }
        }
        compose.onNodeWithContentDescription(context.getString(UiR.string.food_ecarbs_enable)).performClick()
        assertThat(enabled).isFalse()
    }

    @Test
    fun foodEditorShowsThePer100GramFormulaAndCanResetAManualEquivalent() {
        val context = RuntimeEnvironment.getApplication()
        val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3, manualGramsPer100g = 9.5)
        var updated = rule
        compose.setContent {
            MaterialTheme {
                FoodECarbsRuleEditor(meal.copy(portion = 100.0, carbs = 25, protein = 10, fat = 5), true, rule, {}, { updated = it })
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_per100_calculated, "8.5")).assertExists()
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_use_calculated)).performClick()
        assertThat(updated.manualGramsPer100g).isNull()
    }
}
