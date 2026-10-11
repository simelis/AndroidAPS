package app.aaps.ui.compose.wizardDialog

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.core.interfaces.utils.DecimalFormatter
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

/** Robolectric composable test for [WizardDialogContent]: renders, fires navigate-back (Close), and opens the BG field only for a real old or missing reading. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WizardDialogContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val decimalFormatter: DecimalFormatter = mock()

    private lateinit var closeLabel: String
    private lateinit var bgLabel: String

    @Before
    fun setUp() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        closeLabel = ctx.getString(CoreUiR.string.close)
        bgLabel = ctx.getString(CoreUiR.string.wizard_bg_label)
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

    @Test
    fun aMissingBgOpensTheBgFieldOnceLoaded() {
        setWizard(state = { WizardDialogUiState(isLoaded = true, hasBgData = false) })

        compose.onNodeWithText(bgLabel).assertExists()
    }
}
