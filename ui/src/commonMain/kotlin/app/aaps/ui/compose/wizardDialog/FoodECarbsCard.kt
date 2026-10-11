package app.aaps.ui.compose.wizardDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.rememberBringIntoViewOnExpand
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.foodManagement.FoodECarbsSwitch
import app.aaps.ui.compose.foodManagement.FoodECarbsTiming
import app.aaps.ui.compose.foodManagement.FoodUnknownNutrients
import app.aaps.ui.compose.foodManagement.fatKnown
import app.aaps.ui.compose.foodManagement.methodLabel
import app.aaps.ui.compose.foodManagement.proteinKnown
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class FoodECarbsCallbacks(
    val portionGrams: (Double) -> Unit = {},
    val enabled: (Boolean) -> Unit = {},
    val rule: (FoodECarbsRule) -> Unit = {},
    val grams: (Int?) -> Unit = {}
)

/**
 * Apply the stored food formula, with portion and overrides for this meal only.
 *
 * Kept short on purpose: the weight, one nutrient line and the switch. Background text is in the
 * info tooltip, and the eCarbs amount and timing are a row of the wizard inputs ([FoodECarbsRow]).
 */
@Composable
internal fun FoodECarbsCard(state: WizardDialogUiState, actions: FoodECarbsCallbacks, modifier: Modifier = Modifier) {
    val food = state.foodECarbs ?: return
    // A new meal starts without a weight. That is not a mistake yet, so the problem text waits until the
    // user has entered a weight, and the weight field gets the focus instead.
    var portionEntered by rememberSaveable { mutableStateOf(false) }
    val portionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (food.portionGrams == 0.0) portionFocus.requestFocus()
    }
    val hasWeight = food.portionGrams > 0.0
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(food.food.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FoodInfoButton(food)
            }
            NumberInputRow(
                labelRef = UiStrings.food_portion_grams, value = food.portionGrams,
                onValueChange = {
                    portionEntered = true
                    actions.portionGrams(it)
                }, valueRange = 0.0..FoodECarbsState.MAX_PORTION_GRAMS,
                step = 10.0, unitLabel = CoreUiStrings.shortgramm, compact = true,
                modifier = Modifier.focusRequester(portionFocus)
            )
            // Only real numbers: before a weight is entered every amount would be 0
            if (hasWeight) {
                val unknown = stringResource(UiStrings.food_value_unknown)
                Text(stringResource(UiStrings.food_ecarbs_macros,
                    food.carbsGrams,
                    if (food.food.proteinKnown) food.proteinGrams.roundToInt().toString() else unknown,
                    if (food.food.fatKnown) food.fatGrams.roundToInt().toString() else unknown))
            }
            // Without a saved formula there is nothing to switch on, so the switch is not shown
            if (food.food.eCarbsRule == null) {
                Text(stringResource(UiStrings.food_ecarbs_no_rule), style = MaterialTheme.typography.bodySmall)
            } else {
                FoodECarbsSwitch(UiStrings.food_ecarbs_enable, food.enabled, food.enabled || (food.canUseRule && food.rule.isValid), actions.enabled)
                if (!food.canUseRule) Text(stringResource(UiStrings.food_ecarbs_missing_macros), style = MaterialTheme.typography.bodySmall)
            }
            // The eCarbs amount and timing are a row of the wizard inputs, next to carb time (FoodECarbsRow)
            if (food.enabled && hasWeight) FoodUnknownNutrients(food.food, food.rule)
            if (!state.foodInputsValid && (portionEntered || hasWeight)) Text(
                stringResource(UiStrings.food_ecarbs_invalid, FoodECarbsRule.DURATION_HOURS.first, FoodECarbsRule.DURATION_HOURS.last),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * The eCarbs of the food formula as one row of the wizard inputs, like carb time and percentage:
 * the value, and a Change button that opens the amount and timing for this meal.
 */
@Composable
internal fun FoodECarbsRow(state: WizardDialogUiState, actions: FoodECarbsCallbacks, modifier: Modifier = Modifier) {
    val food = state.foodECarbs?.takeIf { it.enabled } ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val plan = state.carbPlan()
    val moved = state.carbs - plan.carbs
    Column(modifier = modifier.bringIntoViewRequester(rememberBringIntoViewOnExpand(expanded))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(UiStrings.food_ecarbs_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    stringResource(UiStrings.food_ecarbs_row_value, plan.eCarbs, plan.delayMinutes, plan.durationHours),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (!expanded) FilledTonalButton(onClick = { expanded = true }) {
                Text(stringResource(CoreUiStrings.change))
            }
        }
        if (moved > 0) Text(
            stringResource(UiStrings.food_ecarbs_moved, plan.carbs, moved),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                NumberInputRow(
                    labelRef = UiStrings.food_ecarbs_amount, value = food.eCarbsGrams.toDouble(),
                    onValueChange = { actions.grams(it.toInt()) }, valueRange = 0.0..state.maxCarbs.toDouble(),
                    step = 1.0, unitLabel = CoreUiStrings.shortgramm
                )
                if (food.overrideGrams != null) TextButton(onClick = { actions.grams(null) }) {
                    Text(stringResource(UiStrings.food_ecarbs_use_calculated))
                }
                FoodECarbsTiming(food.rule, actions.rule)
            }
        }
    }
}

/** Background for this card that does not need to be on screen all the time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodInfoButton(food: FoodECarbsState) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val rule = food.food.eCarbsRule
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
        tooltip = {
            PlainTooltip {
                Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
                    Text(stringResource(UiStrings.food_ecarbs_servings_hint))
                    if (rule != null) {
                        val manual = rule.manualGramsPer100g
                        Text(
                            if (manual != null) stringResource(UiStrings.food_ecarbs_saved_manual, NumberFormat.DECIMAL_1.format(manual))
                            else stringResource(UiStrings.food_ecarbs_saved_method, methodLabel(rule.method))
                        )
                        Text(stringResource(UiStrings.food_ecarbs_how_to_change))
                    }
                }
            }
        },
        state = tooltipState
    ) {
        IconButton(onClick = { scope.launch { tooltipState.show() } }) {
            Icon(Icons.Outlined.Info, contentDescription = stringResource(UiStrings.food_card_info))
        }
    }
}
