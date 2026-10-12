package app.aaps.ui.compose.wizardDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.ui.UiStringIds
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR
import app.aaps.ui.R as UiR

/** Robolectric composable test for [WizardDialogContent]: renders, fires navigate-back (Close), and opens the BG field only for a real old or missing reading. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val decimalFormatter: DecimalFormatter = mock()

    private lateinit var context: Context
    private lateinit var closeLabel: String
    private lateinit var changeLabel: String
    private lateinit var bgLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        context = ctx
        closeLabel = ctx.getString(CoreUiR.string.close)
        changeLabel = ctx.getString(CoreUiR.string.change)
        bgLabel = ctx.getString(CoreUiR.string.wizard_bg_label)
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
    }

    private fun setWizard(state: () -> WizardDialogUiState, onNavigateBack: () -> Unit = {}) {
        compose.setContent {
            MaterialTheme {
                WizardDialogContent(
                    uiState = state(),
                    decimalFormatter = decimalFormatter,
                    unitsLabel = "mg/dl",
                    onBgChange = {},
                    onCarbsChange = {},
                    onAddCarbs = {},
                    onCarbsTypeChange = {},
                    onPercentageChange = {},
                    onDirectCorrectionChange = {},
                    onCarbTimeChange = {},
                    onNotesChange = {},
                    onProfileSelect = {},
                    onBgToggle = {},
                    onTTToggle = {},
                    onTrendToggle = {},
                    onIOBToggle = {},
                    onCOBToggle = {},
                    onAlarmToggle = {},
                    onCalculationExpandToggle = {},
                    onNavigateBack = onNavigateBack,
                    onConfirmClick = {},
                    onSettingsClick = {}
                )
            }
        }
    }

    @Test
    fun rendersAndFiresNavigateBack() {
        var back = false
        setWizard(state = { WizardDialogUiState() }, onNavigateBack = { back = true })
        compose.onNodeWithContentDescription(closeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(back).isTrue()
    }

    /**
     * The first frame is drawn before the view model has loaded, so it has no BG yet. That must not
     * open the BG field: opening it also scrolls the screen down, away from the food card at the top.
     */
    @Test
    fun aFreshBgStaysClosedWhenTheFirstFrameIsDrawnBeforeLoading() {
        var state by mutableStateOf(WizardDialogUiState())
        setWizard(state = { state })

        compose.runOnIdle { state = WizardDialogUiState(isLoaded = true, hasBgData = true, bg = 120.0, bgAgeMinutes = 2) }
        compose.waitForIdle()

        compose.onNodeWithText(bgLabel).assertDoesNotExist()
    }

    private val fpu = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)
    private val meal = FD(id = 7, name = "Meal", portion = 100.0, carbs = 25, protein = 10, fat = 5, eCarbsRule = fpu)

    private fun mealState(rule: FoodECarbsRule = fpu, enabled: Boolean = true) =
        WizardDialogUiState(isLoaded = true, hasBgData = true, bgAgeMinutes = 2, carbs = 50, maxCarbs = 100,
            foodECarbs = FoodECarbsState(meal.copy(eCarbsRule = rule), amount = 200.0, enabled = enabled, rule = rule))

    @Test
    fun theFoodECarbsAreARowOfTheInputsWithTheirValue() {
        setWizard(state = { mealState() })

        // 200 g: (4 x 20 + 9 x 10) / 10 = 17 g
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_row_value, 17, 60, 3)).assertExists()
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_delay)).assertDoesNotExist()
    }

    @Test
    fun changeOpensTheECarbsAmountAndTiming() {
        setWizard(state = { mealState() })

        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_row_value, 17, 60, 3)).performScrollTo()
        compose.onNode(hasText(changeLabel) and hasAnySibling(hasText(context.getString(UiR.string.food_ecarbs_label)))).performClick()

        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_delay)).assertExists()
    }

    @Test
    fun theSplitOfTheMealIsShownUnderTheCarbsField() {
        setWizard(state = { mealState(rule = fpu.copy(carbsNowPercent = 80)) })

        // 80 % of 50 g is 40 g now; the other 10 g join the 17 g from protein and fat
        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_row_value, 27, 60, 3)).assertExists()
        compose.onNodeWithText(context.getString(UiR.string.wizard_carbs_split, 40, 27)).assertExists()
    }

    @Test
    fun pizzaShowsItsSplitToo() {
        setWizard(state = { WizardDialogUiState(isLoaded = true, carbs = 50, maxCarbs = 100, carbsType = CarbsType.PIZZA) })
        // Pizza: 80 % now, 35 % added as eCarbs
        compose.onNodeWithText(context.getString(UiR.string.wizard_carbs_split, 40, 17)).assertExists()
    }

    @Test
    fun breadShowsNoSplit() {
        setWizard(state = { WizardDialogUiState(isLoaded = true, carbs = 50, maxCarbs = 100) })
        compose.onNodeWithText(context.getString(UiR.string.wizard_carbs_split, 50, 0)).assertDoesNotExist()
    }

    @Test
    fun withTheFormulaOffThereIsNoECarbsRow() {
        setWizard(state = { mealState(enabled = false) })

        compose.onNodeWithText(context.getString(UiR.string.food_ecarbs_label)).assertDoesNotExist()
    }

    @Test
    fun aMissingBgOpensTheBgFieldOnceLoaded() {
        setWizard(state = { WizardDialogUiState(isLoaded = true, hasBgData = false) })

        compose.onNodeWithText(bgLabel).assertExists()
    }
}
