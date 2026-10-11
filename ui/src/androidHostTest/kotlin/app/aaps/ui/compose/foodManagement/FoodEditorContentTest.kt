package app.aaps.ui.compose.foodManagement

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import app.aaps.core.interfaces.resources.TextRefIdRegistry
import app.aaps.ui.UiStringIds
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

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
}
