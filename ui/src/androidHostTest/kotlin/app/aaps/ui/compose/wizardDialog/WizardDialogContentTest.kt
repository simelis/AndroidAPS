package app.aaps.ui.compose.wizardDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
import app.aaps.core.interfaces.R as InterfacesR
import app.aaps.core.ui.R as CoreUiR
import app.aaps.ui.R as UiR

/** Robolectric composable test for [WizardDialogContent]. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val decimalFormatter: DecimalFormatter = mock()

    private lateinit var closeLabel: String
    private lateinit var calculationTitle: String
    private lateinit var recordOnlyBanner: SemanticsMatcher

    /** Screen reader name of the plus button of the BG input. It exists only while the BG input is open. */
    private lateinit var bgPlusButton: String

    /** Result extras for 71% and a +1 U correction, built from the same templates as the screen. */
    private lateinit var percentageAndCorrection: String

    @Before
    fun setUp() {
        // The app registers this at start. Without it every UiStrings text shows its key name.
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        val ctx: Context = RuntimeEnvironment.getApplication()
        closeLabel = ctx.getString(CoreUiR.string.close)
        calculationTitle = ctx.getString(CoreUiR.string.wizard_calculation)
        // The banner is the only node with this pane title.
        recordOnlyBanner = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, ctx.getString(InterfacesR.string.bolus_recorded_only))
        bgPlusButton = ctx.getString(
            CoreUiR.string.a11y_plus_button_description,
            ctx.getString(CoreUiR.string.wizard_bg_label),
            ctx.getString(CoreUiR.string.value_with_unit, "1", UNITS)
        )
        percentageAndCorrection = ctx.getString(
            CoreUiR.string.value_in_parentheses,
            ctx.getString(
                UiR.string.wizard_percentage_and_correction,
                ctx.getString(CoreUiR.string.format_percent, 71),
                ctx.getString(CoreUiR.string.format_insulin_units_signed, 1.0)
            )
        )
    }

    @Test
    fun rendersAndFiresNavigateBack() {
        var back = false
        setWizard(uiState = { WizardDialogUiState() }, onNavigateBack = { back = true })
        compose.onNodeWithContentDescription(closeLabel).assertIsDisplayed()
        compose.onNodeWithContentDescription(closeLabel).performClick()
        assertThat(back).isTrue()
    }

    /** The first frames are drawn from the default state, which has no BG. That must not open the BG input. */
    @Test
    fun bgInputStaysClosedWhenTheLoadedBgIsFresh() {
        var state by mutableStateOf(WizardDialogUiState())
        setWizard(uiState = { state })
        compose.runOnIdle { state = loaded(bgAgeMinutes = 2) }
        compose.onNodeWithContentDescription(bgPlusButton).assertDoesNotExist()
    }

    @Test
    fun bgInputOpensByItselfWhenThereIsNoBg() {
        setWizard(uiState = { loaded(hasBgData = false) })
        compose.onNodeWithContentDescription(bgPlusButton).assertExists()
    }

    /** Only a tap on "Change" may scroll to the BG input. When it opens by itself, the top of the screen stays. */
    @Test
    fun bgInputThatOpensByItselfDoesNotScroll() {
        setWizard(uiState = { loaded(hasBgData = false) })
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText(calculationTitle).assertIsDisplayed()
    }

    /** Percentage and correction are one text from the templates, not "(", values and ")" side by side. */
    @Test
    fun resultShowsPercentageAndCorrectionInOneText() {
        setWizard(uiState = { loaded().copy(hasResult = true, totalInsulin = 2.0, percentage = 71, directCorrection = 1.0) })
        compose.onNodeWithText(percentageAndCorrection).assertIsDisplayed()
    }

    @Test
    fun recordOnlyBannerStaysOnScreenWhenTheUserScrollsDown() {
        setWizard(uiState = { loaded(bgAgeMinutes = 2, forcedRecordOnly = true) })
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        compose.onNode(recordOnlyBanner).assertIsDisplayed()
    }

    private val fpu = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)
    private val meal = FD(id = 7, name = "Meal", portion = 100.0, carbs = 25, protein = 10, fat = 5, eCarbsRule = fpu)

    private fun mealState(rule: FoodECarbsRule = fpu, enabled: Boolean = true) =
        loaded().copy(carbs = 50, foods = listOf(FoodECarbsState(meal.copy(eCarbsRule = rule), amount = 200.0, enabled = enabled, rule = rule)))

    @Test
    fun theFoodECarbsAreARowOfTheInputsWithTheirValue() {
        setWizard(uiState = { mealState() })

        // 200 g: (4 x 20 + 9 x 10) / 10 = 17 g
        compose.onNodeWithText(string(UiR.string.food_ecarbs_row_value, 17, 60, 3)).assertExists()
        compose.onNodeWithText(string(UiR.string.food_ecarbs_delay)).assertDoesNotExist()
    }

    @Test
    fun changeOpensTheCarbsNowTheECarbsAmountAndTiming() {
        setWizard(uiState = { mealState() })

        compose.onNodeWithText(string(UiR.string.food_ecarbs_row_value, 17, 60, 3)).performScrollTo()
        compose.onNode(hasText(string(CoreUiR.string.change)) and hasAnySibling(hasText(string(UiR.string.food_ecarbs_label)))).performClick()

        compose.onNodeWithText(string(UiR.string.food_ecarbs_carbs_now)).assertExists()
        compose.onNodeWithText(string(UiR.string.food_ecarbs_delay)).assertExists()
    }

    @Test
    fun theSplitOfTheMealIsShownUnderTheCarbsField() {
        setWizard(uiState = { mealState(rule = fpu.copy(carbsNowPercent = 80)) })

        // 80 % of 50 g is 40 g now; the other 10 g join the 17 g from protein and fat
        compose.onNodeWithText(string(UiR.string.food_ecarbs_row_value, 27, 60, 3)).assertExists()
        compose.onNodeWithText(string(UiR.string.wizard_carbs_split, 40, 27)).assertExists()
    }

    @Test
    fun pizzaShowsItsSplitToo() {
        setWizard(uiState = { loaded().copy(carbs = 50, carbsType = CarbsType.PIZZA) })
        // Pizza: 80 % now, 35 % added as eCarbs
        compose.onNodeWithText(string(UiR.string.wizard_carbs_split, 40, 17)).assertExists()
    }

    @Test
    fun breadShowsNoSplit() {
        setWizard(uiState = { loaded().copy(carbs = 50) })
        compose.onNodeWithText(string(UiR.string.wizard_carbs_split, 50, 0)).assertDoesNotExist()
    }

    @Test
    fun withTheFormulaOffThereIsNoECarbsRow() {
        setWizard(uiState = { mealState(enabled = false) })

        compose.onNodeWithText(string(UiR.string.food_ecarbs_label)).assertDoesNotExist()
    }

    @Test
    fun theFoodFormulaIsAFourthChoiceNextToBreadDumplingsAndPizza() {
        var selected = false
        setWizard(uiState = { loaded().copy(foods = listOf(FoodECarbsState(meal, rule = fpu))) }, foodActions = FoodECarbsCallbacks(enabled = { selected = it }))
        compose.onNodeWithContentDescription(string(UiR.string.food_ecarbs_enable)).performScrollTo().assertIsNotSelected().performClick()
        assertThat(selected).isTrue()
    }

    @Test
    fun missingMacrosDisableTheFoodChoiceAndExplainWhatToEnter() {
        setWizard(uiState = { loaded().copy(foods = listOf(FoodECarbsState(meal.copy(fat = null, protein = null), rule = fpu))) })
        compose.onNodeWithContentDescription(string(UiR.string.food_ecarbs_enable)).assertIsNotEnabled()
        compose.onNodeWithText(string(UiR.string.food_ecarbs_missing_macros)).assertExists()
    }

    @Test
    fun aFoodWithoutASavedFormulaShowsNoFoodChoice() {
        setWizard(uiState = { loaded().copy(foods = listOf(FoodECarbsState(meal.copy(eCarbsRule = null)))) })
        compose.onNodeWithContentDescription(string(UiR.string.food_ecarbs_enable)).assertDoesNotExist()
        compose.onNodeWithText(string(UiR.string.food_ecarbs_no_rule)).assertExists()
        compose.onNodeWithText(string(UiR.string.food_ecarbs_missing_macros)).assertDoesNotExist()
    }

    @Test
    fun fpuWithOneUnknownNutrientCanBeUsedAndSaysItCountsAsZero() {
        var selected = false
        setWizard(uiState = { loaded().copy(foods = listOf(FoodECarbsState(meal.copy(fat = null), rule = fpu))) }, foodActions = FoodECarbsCallbacks(enabled = { selected = it }))
        compose.onNodeWithText(string(UiR.string.food_ecarbs_missing_macros)).assertDoesNotExist()
        compose.onNodeWithContentDescription(string(UiR.string.food_ecarbs_enable)).performScrollTo().performClick()
        assertThat(selected).isTrue()
    }

    @Test
    fun enabledFoodModeCanBeTurnedOffWhileTimingIsInvalid() {
        var chosen: CarbsType? = null
        setWizard(
            uiState = { loaded().copy(foods = listOf(FoodECarbsState(meal, enabled = true, rule = FoodECarbsRule(durationHours = 0)))) },
            onCarbsTypeChange = { chosen = it }
        )
        compose.onNodeWithContentDescription(string(UiR.string.food_ecarbs_enable)).assertIsSelected()
        // No format arguments: the text has a literal %
        compose.onNodeWithContentDescription(RuntimeEnvironment.getApplication().getString(UiR.string.wizard_carbs_type_bread_desc)).performScrollTo().assertIsNotSelected().performClick()
        assertThat(chosen).isEqualTo(CarbsType.BREAD)
    }

    private fun string(id: Int, vararg args: Any): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun loaded(hasBgData: Boolean = true, bgAgeMinutes: Int = 0, forcedRecordOnly: Boolean = false) =
        WizardDialogUiState(
            initialized = true,
            bg = if (hasBgData) 120.0 else 0.0,
            maxCarbs = 100,
            maxBolus = 10.0,
            hasBgData = hasBgData,
            bgAgeMinutes = bgAgeMinutes,
            forcedRecordOnly = forcedRecordOnly
        )

    @Test
    fun addFoodOpensTheFoodList() {
        var opened = false
        setWizard(uiState = { loaded() }, foodActions = FoodECarbsCallbacks(add = { opened = true }))

        compose.onNodeWithText(string(UiR.string.food_add)).performScrollTo().performClick()

        assertThat(opened).isTrue()
    }

    @Test
    fun eachFoodOfTheMealCanBeRemoved() {
        var removed: Int? = null
        setWizard(uiState = { mealState() }, foodActions = FoodECarbsCallbacks(remove = { removed = it }))

        compose.onNodeWithContentDescription(string(UiR.string.food_remove, "Meal")).performScrollTo().performClick()

        assertThat(removed).isEqualTo(0)
    }

    /** [uiState] is read inside the composition, so a test can change the state after the first frame. */
    private fun setWizard(
        uiState: () -> WizardDialogUiState,
        onNavigateBack: () -> Unit = {},
        onCarbsTypeChange: (CarbsType) -> Unit = {},
        foodActions: FoodECarbsCallbacks = FoodECarbsCallbacks()
    ) {
        compose.setContent {
            MaterialTheme {
                WizardDialogContent(
                    uiState = uiState(),
                    decimalFormatter = decimalFormatter,
                    unitsLabel = UNITS,
                    onBgChange = {},
                    onCarbsChange = {},
                    onAddCarbs = {},
                    onCarbsTypeChange = onCarbsTypeChange,
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
                    onSettingsClick = {},
                    foodActions = foodActions
                )
            }
        }
    }

    private companion object {

        const val UNITS = "mg/dl"
    }
}
