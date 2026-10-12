package app.aaps.ui.compose.foodManagement

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.UiStringIds
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR
import app.aaps.ui.R as UiR

/** Robolectric composable test for [FoodEditorContent]: renders the editor fields + save/cancel actions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FoodEditorContentTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var saveLabel: String
    private lateinit var cancelLabel: String

    @Before
    fun setUp() {
        TextRefIdRegistry.register("ui") { name -> UiStringIds.idOf(name) }
        val ctx: Context = RuntimeEnvironment.getApplication()
        saveLabel = ctx.getString(CoreUiR.string.save)
        cancelLabel = ctx.getString(CoreUiR.string.cancel)
    }

    @Test
    fun rendersEditorWithSaveAndCancel() {
        compose.setContent {
            MaterialTheme {
                FoodEditorContent(
                    state = FoodManagementUiState(),
                    onNameChange = {},
                    onCategoryChange = {},
                    onSubCategoryChange = {},
                    onCarbsChange = {},
                    onFatChange = {},
                    onProteinChange = {},
                    onEnergyChange = {},
                    onSave = {},
                    onCancel = {},
                    onDelete = null,
                    isEditing = false
                )
            }
        }
        compose.onNodeWithText(saveLabel).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(cancelLabel).assertIsDisplayed()
    }

    @Test
    fun theAmountAndUnitAreAlwaysThereAndAskingEveryTimeIsASwitch() {
        val ctx: Context = RuntimeEnvironment.getApplication()
        var state by mutableStateOf(FoodManagementUiState())
        var askAmount: Boolean? = null
        compose.setContent {
            MaterialTheme {
                FoodEditorContent(
                    state = state, onNameChange = {}, onCategoryChange = {}, onSubCategoryChange = {}, onCarbsChange = {},
                    onFatChange = {}, onProteinChange = {}, onEnergyChange = {}, onSave = {}, onCancel = {}, onDelete = null,
                    isEditing = false, onAskAmountChange = { askAmount = it }
                )
            }
        }
        compose.onNodeWithText(ctx.getString(UiR.string.food_amount)).assertExists()
        compose.onNodeWithText(ctx.getString(CoreUiR.string.unit_label)).assertExists()
        compose.onNodeWithText(ctx.getString(UiR.string.food_ask_amount_info)).assertDoesNotExist()
        compose.onNodeWithContentDescription(ctx.getString(UiR.string.food_ask_amount)).assertIsOn().performClick()
        assertThat(askAmount).isFalse()

        // 30 g carbs in a 25 g slice is a typo
        val tooHeavy = ctx.getString(UiR.string.food_nutrient_more_than_food, "25", "g")
        compose.onNodeWithText(tooHeavy).assertDoesNotExist()
        state = FoodManagementUiState(editorPortion = "25", editorUnit = "g", editorCarbs = "30")
        compose.onNodeWithText(tooHeavy).assertExists()
    }
}
