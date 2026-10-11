package app.aaps.ui.compose.wizardDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.foodManagement.FoodECarbsSwitch
import app.aaps.ui.compose.foodManagement.FoodECarbsTiming
import app.aaps.ui.compose.foodManagement.FoodUnknownNutrients
import app.aaps.ui.compose.foodManagement.methodLabel

internal data class FoodECarbsCallbacks(
    val portionGrams: (Double) -> Unit = {},
    val enabled: (Boolean) -> Unit = {},
    val rule: (FoodECarbsRule) -> Unit = {},
    val grams: (Int?) -> Unit = {}
)

/** Apply the stored food rule, with portion and overrides for this meal only. */
@Composable
internal fun FoodECarbsCard(state: WizardDialogUiState, actions: FoodECarbsCallbacks) {
    val food = state.foodECarbs ?: return
    // A new meal starts without a weight. That is not a mistake yet, so the problem text waits until the
    // user has entered a weight, and the weight field gets the focus instead.
    var portionEntered by rememberSaveable { mutableStateOf(false) }
    val portionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (food.portionGrams == 0.0) portionFocus.requestFocus()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            Text(food.food.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(UiStrings.food_nutrients_basis))
            NumberInputRow(
                labelRef = UiStrings.food_portion_grams, value = food.portionGrams,
                onValueChange = {
                    portionEntered = true
                    actions.portionGrams(it)
                }, valueRange = 0.0..FoodECarbsState.MAX_PORTION_GRAMS,
                step = 10.0, decimalPlaces = 1, unitLabel = CoreUiStrings.shortgramm,
                modifier = Modifier.focusRequester(portionFocus)
            )
            Text(stringResource(UiStrings.food_ecarbs_servings_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(UiStrings.food_ecarbs_macros,
                food.carbsGrams,
                if (food.food.protein == null) "—" else NumberFormat.DECIMAL_1.format(food.proteinGrams),
                if (food.food.fat == null) "—" else NumberFormat.DECIMAL_1.format(food.fatGrams)))
            // Without a saved formula there is nothing to switch on, so the switch is not shown
            if (food.food.eCarbsRule == null) {
                Text(stringResource(UiStrings.food_ecarbs_no_rule))
            } else {
                FoodECarbsSwitch(UiStrings.food_ecarbs_enable, food.enabled, food.enabled || (food.canUseRule && food.rule.isValid), actions.enabled)
                if (!food.canUseRule) Text(stringResource(UiStrings.food_ecarbs_missing_macros))
            }
            if (food.enabled) {
                val plan = state.carbPlan()
                FoodUnknownNutrients(food.food, food.rule)
                food.rule.manualGramsPer100g?.let {
                    Text(stringResource(UiStrings.food_ecarbs_saved_manual, NumberFormat.DECIMAL_1.format(it)))
                }
                Text(stringResource(UiStrings.food_ecarbs_saved_method, methodLabel(food.rule.method)))
                Text(stringResource(UiStrings.food_ecarbs_calculated, food.calculatedGrams))
                if (food.rule.carbsNowPercent < 100)
                    Text(stringResource(UiStrings.food_ecarbs_carbs_split, plan.carbs, food.rule.carbsNowPercent, state.carbs - plan.carbs))
                NumberInputRow(
                    labelRef = UiStrings.food_ecarbs_amount, value = food.eCarbsGrams.toDouble(),
                    onValueChange = { actions.grams(it.toInt()) }, valueRange = 0.0..state.maxCarbs.toDouble(),
                    step = 1.0, unitLabel = CoreUiStrings.shortgramm
                )
                if (food.overrideGrams != null) TextButton(onClick = { actions.grams(null) }) {
                    Text(stringResource(UiStrings.food_ecarbs_use_calculated))
                }
                FoodECarbsTiming(food.rule, actions.rule)
                Text(stringResource(UiStrings.food_ecarbs_preview, plan.carbs, plan.eCarbs,
                    food.rule.delayMinutes, food.rule.durationHours))
                Text(stringResource(UiStrings.food_ecarbs_profile_notice), style = MaterialTheme.typography.bodySmall)
            }
            if (!state.foodInputsValid && (portionEntered || food.portionGrams > 0.0)) Text(
                stringResource(UiStrings.food_ecarbs_invalid, FoodECarbsRule.DURATION_HOURS.first, FoodECarbsRule.DURATION_HOURS.last),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
