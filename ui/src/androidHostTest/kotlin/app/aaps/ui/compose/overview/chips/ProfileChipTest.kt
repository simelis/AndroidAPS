package app.aaps.ui.compose.overview.chips

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ProfileChipTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsProfileName() {
        compose.setContent {
            MaterialTheme {
                ProfileChip(profileName = "Default 5.6", isModified = false, progress = 0f, onClick = {})
            }
        }

        compose.onNodeWithText("Default 5.6").assertIsDisplayed()
    }

    @Test
    fun longName_onNarrowChip_keepsPercentageAndTimeLeftInsideTheChip() {
        compose.setContent {
            MaterialTheme {
                ProfileChip(
                    profileName = "Darbadienis su ilgu pavadinimu",
                    suffix = "(90%)",
                    remaining = "(1h 56')",
                    isModified = true,
                    progress = 0.5f,
                    onClick = {},
                    modifier = Modifier
                        .width(180.dp)
                        .testTag("profileChip")
                )
            }
        }

        // The chip merges its texts into one node, so the texts are found in the unmerged tree
        val chip = compose.onNodeWithTag("profileChip").getUnclippedBoundsInRoot()
        val name = compose.onNodeWithText("Darbadienis su ilgu pavadinimu", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val suffix = compose.onNodeWithText("(90%)", useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
        val remaining = compose.onNodeWithText("(1h 56')", useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertThat(name.right).isAtMost(suffix.left)
        assertThat(suffix.right).isAtMost(remaining.left)
        assertThat(remaining.right).isAtMost(chip.right)
        assertThat(remaining.bottom).isAtMost(chip.bottom)
    }

    @Test
    fun click_firesCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                ProfileChip(
                    profileName = "Default 5.6",
                    isModified = true,
                    progress = 0.6f,
                    onClick = { clicks++ },
                    modifier = Modifier.testTag("profileChip")
                )
            }
        }

        compose.onNodeWithTag("profileChip").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun disabled_doesNotFireCallback() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                ProfileChip(
                    profileName = "Default 5.6",
                    isModified = false,
                    progress = 0f,
                    onClick = { clicks++ },
                    enabled = false,
                    modifier = Modifier.testTag("profileChip")
                )
            }
        }

        compose.onNodeWithTag("profileChip").performClick()

        assertThat(clicks).isEqualTo(0)
    }
}
