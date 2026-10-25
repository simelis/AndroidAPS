package app.aaps.ui.compose.foodManagement

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule

@Preview(showBackground = true)
@Composable
internal fun FoodECarbsRuleEditorFpuPreview() {
    MaterialTheme {
        FoodECarbsRuleEditor(
            food = FD(name = "Pizza", portion = 100.0, carbs = 30, protein = 12, fat = null),
            enabled = true,
            rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3, carbsNowPercent = 80),
            onEnabledChange = {},
            onRuleChange = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun FoodECarbsRuleEditorOffPreview() {
    MaterialTheme {
        FoodECarbsRuleEditor(
            food = FD(name = "Bread", portion = 100.0, carbs = 50, protein = 8, fat = 2),
            enabled = false,
            rule = FoodECarbsRule(),
            onEnabledChange = {},
            onRuleChange = {}
        )
    }
}
