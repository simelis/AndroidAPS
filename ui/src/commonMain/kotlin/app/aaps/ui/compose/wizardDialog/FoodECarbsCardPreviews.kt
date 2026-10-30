package app.aaps.ui.compose.wizardDialog

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule

private val previewRule = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3, carbsNowPercent = 80)
private val previewFood = FD(id = 1, name = "Pizza", portion = 100.0, carbs = 30, protein = 12, fat = 10, eCarbsRule = previewRule)
private val previewDrink = FD(id = 2, name = "Juice", portion = 200.0, carbs = 20, protein = 0, fat = 0, unit = "ml")

@Preview(showBackground = true)
@Composable
internal fun FoodECarbsCardNewMealPreview() {
    MaterialTheme {
        FoodECarbsCards(
            state = WizardDialogUiState(maxCarbs = 100, foods = listOf(FoodECarbsState(previewFood, rule = previewRule, enabled = true))),
            actions = FoodECarbsCallbacks()
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun FoodECarbsCardWithWeightPreview() {
    MaterialTheme {
        FoodECarbsCards(
            state = WizardDialogUiState(
                carbs = 75, maxCarbs = 100,
                foods = listOf(FoodECarbsState(previewFood, amount = 250.0, enabled = true, rule = previewRule))
            ),
            actions = FoodECarbsCallbacks()
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun FoodECarbsCardNoFormulaPreview() {
    MaterialTheme {
        FoodECarbsCards(
            state = WizardDialogUiState(carbs = 30, maxCarbs = 100, foods = listOf(FoodECarbsState(previewFood.copy(eCarbsRule = null), amount = 100.0))),
            actions = FoodECarbsCallbacks()
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun FoodECarbsCardMealPreview() {
    MaterialTheme {
        FoodECarbsCards(
            state = WizardDialogUiState(
                carbs = 95, maxCarbs = 100,
                foods = listOf(
                    FoodECarbsState(previewFood, amount = 250.0, enabled = true, rule = previewRule),
                    FoodECarbsState(previewDrink, amount = 200.0)
                )
            ),
            actions = FoodECarbsCallbacks()
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun FoodECarbsRowPreview() {
    MaterialTheme {
        FoodECarbsRow(
            state = WizardDialogUiState(
                carbs = 75, maxCarbs = 100,
                foods = listOf(FoodECarbsState(previewFood, amount = 250.0, enabled = true, rule = previewRule))
            ),
            actions = FoodECarbsCallbacks()
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun FoodECarbsCardAmountPreview() {
    MaterialTheme {
        FoodECarbsCards(
            state = WizardDialogUiState(
                carbs = 30, maxCarbs = 100,
                foods = listOf(
                    FoodECarbsState(
                        previewFood.copy(name = "Pizza", portion = 3.0, unit = "slices", askAmount = false),
                        enabled = true, rule = previewRule
                    )
                )
            ),
            actions = FoodECarbsCallbacks()
        )
    }
}
