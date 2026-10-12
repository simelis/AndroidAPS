package app.aaps.ui.compose.wizardDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
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
    private val fpu = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)
    private val meal = FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10, eCarbsRule = fpu)

    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

    @Test
    fun aFoodAskedEveryTimeFocusesTheAmountAndShowsNoProblemYet() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(WizardDialogUiState(maxCarbs = 100, foodECarbs = FoodECarbsState(meal)), FoodECarbsCallbacks())
            }
        }
        compose.onNode(isFocused()).assert(hasText(context.getString(UiR.string.food_amount_eaten)))
        compose.onNodeWithText(invalidText(context)).assertDoesNotExist()
    }

    @Test
    fun theProblemShowsOnceAWrongAmountIsEntered() {
        val context = RuntimeEnvironment.getApplication()
        var portion: Double? = null
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(WizardDialogUiState(maxCarbs = 100, foodECarbs = FoodECarbsState(meal)),
                    FoodECarbsCallbacks(amount = { portion = it }))
            }
        }
        compose.onNode(isFocused()).performTextReplacement("0")
        compose.onNode(isFocused()).performImeAction()

        assertThat(portion).isEqualTo(0.0)
        compose.onNodeWithText(invalidText(context)).assertExists()
    }

    private fun invalidText(context: Context) =
        context.getString(UiR.string.food_ecarbs_invalid, FoodECarbsRule.DURATION_HOURS.first, FoodECarbsRule.DURATION_HOURS.last)

    @Test
    fun enabledFpuWithUnknownFatShowsTheZeroNote() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(WizardDialogUiState(carbs = 50, maxCarbs = 100, foodECarbs = FoodECarbsState(meal.copy(fat = null),
                    amount = 200.0, enabled = true, rule = fpu)), FoodECarbsCallbacks())
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_fat_unknown)).assertExists()
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_protein_unknown)).assertDoesNotExist()
    }

    @Test
    fun enabledFoodModeShowsTheMealNutrientsAndKeepsTheBackgroundInTheTooltip() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(
                    WizardDialogUiState(carbs = 50, maxCarbs = 100, foodECarbs = FoodECarbsState(
                        meal, amount = 200.0, enabled = true, rule = fpu
                    )),
                    FoodECarbsCallbacks()
                )
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_macros, 50, "20", "10")).assertExists()
        val howToChange = context.getString(UiR.string.food_ecarbs_how_to_change)
        compose.onNodeWithText(howToChange).assertDoesNotExist()

        compose.onNodeWithContentDescription(context.getString(UiR.string.food_card_info)).performClick()
        compose.onNodeWithText(howToChange).assertExists()
    }

    @Test
    fun beforeAnAmountIsEnteredNoNutrientsAreShown() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(WizardDialogUiState(maxCarbs = 100, foodECarbs = FoodECarbsState(meal, enabled = true, rule = fpu)), FoodECarbsCallbacks())
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_macros, 0, "0", "0")).assertDoesNotExist()
    }

    @Test
    fun foodEditorShowsTheFormulaForTheSavedAmountAndCanResetAFixedAmount() {
        val context = RuntimeEnvironment.getApplication()
        val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3, manualGrams = 9.5)
        var updated = rule
        compose.setContent {
            MaterialTheme {
                FoodECarbsRuleEditor(meal.copy(portion = 100.0, carbs = 25, protein = 10, fat = 5), true, rule, {}, { updated = it })
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_amount_calculated, "100", "g", "8.5")).assertExists()
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_amount_fixed, "100", "g")).assertExists()
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_use_calculated)).performClick()
        assertThat(updated.manualGrams).isNull()
    }

    @Test
    fun foodEditorExplainsWhenTheFormulaNeedsANutrientTheFoodLacks() {
        val context = RuntimeEnvironment.getApplication()
        val rule = FoodECarbsRule(method = FoodECarbsMethod.CUSTOM, fatPercent = 50, durationHours = 3)
        compose.setContent {
            MaterialTheme {
                FoodECarbsRuleEditor(meal.copy(portion = 100.0, fat = null), true, rule, {}, {})
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_missing_macros)).assertExists()
    }

    @Test
    fun aUsualAmountStartsFilledInAndInTheFoodsOwnUnit() {
        val context = RuntimeEnvironment.getApplication()
        val slice = meal.copy(portion = 3.0, unit = "slices", askAmount = false)
        compose.setContent {
            MaterialTheme {
                FoodECarbsCard(WizardDialogUiState(carbs = 50, maxCarbs = 100, foodECarbs = FoodECarbsState(slice, rule = fpu, enabled = true)),
                    FoodECarbsCallbacks())
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_amount_eaten)).assertExists()
        compose.onNodeWithText("slices").assertExists()
        compose.onNode(isFocused()).assertDoesNotExist()
        compose.onNodeWithText(invalidText(context)).assertDoesNotExist()
    }

    @Test
    fun formulaEditorKeepsTheExplanationsBehindInfoButtons() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                FoodECarbsRuleEditor(meal.copy(portion = 100.0), true, fpu, {}, {})
            }
        }
        val fpuText = context.getString(UiR.string.food_ecarbs_fpu_description)
        compose.onNodeWithText(fpuText).assertDoesNotExist()
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_carbs_now_hint)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_rule_hint)).assertDoesNotExist()
        val infoButtons = compose.onAllNodesWithContentDescription(context.getString(UiR.string.food_more_info))
        infoButtons.assertCountEquals(3)
        infoButtons[1].performClick()
        compose.onNodeWithText(fpuText).assertExists()
    }

    @Test
    fun aFixedAmountIsForTheSavedAmountInAnyUnit() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                FoodECarbsRuleEditor(meal.copy(portion = 3.0, unit = "slices"), true, fpu, {}, {})
            }
        }
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_amount_fixed, "3", "slices")).assertExists()
    }

    @Test
    fun foodEditorSwitchIsAvailableWithoutProteinAndFat() {
        val context = RuntimeEnvironment.getApplication()
        var enabled = false
        compose.setContent {
            MaterialTheme {
                FoodECarbsRuleEditor(meal.copy(portion = 100.0, fat = null, protein = null), false, FoodECarbsRule(), { enabled = it }, {})
            }
        }
        compose.onNodeWithContentDescription(context.getString(UiR.string.food_ecarbs_rule_enable)).performClick()
        assertThat(enabled).isTrue()
    }
}
