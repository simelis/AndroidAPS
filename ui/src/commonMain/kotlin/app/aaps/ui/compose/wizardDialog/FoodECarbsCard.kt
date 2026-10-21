package app.aaps.ui.compose.wizardDialog

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.icons.IcPluginFood
import app.aaps.core.ui.compose.rememberBringIntoViewOnExpand
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.foodManagement.FoodECarbsTiming
import app.aaps.ui.compose.foodManagement.FoodUnknownNutrients
import app.aaps.ui.compose.foodManagement.InfoTooltipButton
import app.aaps.ui.compose.foodManagement.fatKnown
import app.aaps.ui.compose.foodManagement.formatAmount
import app.aaps.ui.compose.foodManagement.methodLabel
import app.aaps.ui.compose.foodManagement.proteinKnown
import app.aaps.ui.compose.foodManagement.toDoubleRange
import kotlin.math.roundToInt

internal data class FoodECarbsCallbacks(
    val amount: (index: Int, amount: Double) -> Unit = { _, _ -> },
    val enabled: (Boolean) -> Unit = {},
    val add: (FD) -> Unit = {},
    val remove: (index: Int) -> Unit = {},
    val carbsNow: (Int?) -> Unit = {},
    val grams: (Int?) -> Unit = {},
    val timing: (delayMinutes: Int, durationHours: Int) -> Unit = { _, _ -> }
)

/** One card per food of the meal. */
@Composable
internal fun FoodECarbsCards(state: WizardDialogUiState, actions: FoodECarbsCallbacks) {
    state.foods.forEachIndexed { index, food ->
        // Keyed by the food, so a removed card does not hand its "amount entered" to the next one
        key(food.food.id, index) {
            FoodECarbsCard(state, index, food, actions)
        }
    }
}

@Composable
private fun FoodECarbsCard(state: WizardDialogUiState, index: Int, food: FoodECarbsState, actions: FoodECarbsCallbacks) {
    // An empty amount is not a mistake yet: show the problem only after an amount is entered
    var amountEntered by rememberSaveable { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!food.hasAmount) amountFocus.requestFocus()
    }
    val hasAmount = food.hasAmount
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(food.food.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FoodInfoButton(food)
                IconButton(onClick = { actions.remove(index) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(UiStrings.food_remove, food.food.name))
                }
            }
            NumberInputRow(
                labelRef = UiStrings.food_amount_eaten, value = food.amount,
                onValueChange = {
                    amountEntered = true
                    actions.amount(index, it)
                }, valueRange = 0.0..food.maxAmount, step = food.amountStep,
                decimalPlaces = if (food.amountStep < 1.0) 1 else 0, unitLabel = TextRef.Literal(food.food.unit), compact = true,
                modifier = Modifier.focusRequester(amountFocus)
            )
            if (hasAmount) {
                val unknown = stringResource(UiStrings.food_value_unknown)
                Text(stringResource(UiStrings.food_ecarbs_macros,
                    food.carbsGrams,
                    if (food.food.proteinKnown) food.proteinGrams.roundToInt().toString() else unknown,
                    if (food.food.fatKnown) food.fatGrams.roundToInt().toString() else unknown))
            }
            if (food.food.eCarbsRule == null) Text(stringResource(UiStrings.food_ecarbs_no_rule), style = MaterialTheme.typography.bodySmall)
            else if (!food.canUseRule) Text(stringResource(UiStrings.food_ecarbs_missing_macros), style = MaterialTheme.typography.bodySmall)
            if (food.enabled && hasAmount) FoodUnknownNutrients(food.food, food.rule)
            // Once for the meal, under the last food
            if (index == state.foods.lastIndex && !state.foodInputsValid && (amountEntered || hasAmount)) Text(
                stringResource(UiStrings.food_ecarbs_invalid, FoodECarbsRule.DURATION_HOURS.first, FoodECarbsRule.DURATION_HOURS.last),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Opens the list of foods. A meal of several foods is added one food at a time. */
@Composable
internal fun AddFoodButton(state: WizardDialogUiState, onAdd: (FD) -> Unit, modifier: Modifier = Modifier) {
    if (state.availableFoods.isEmpty() || state.foods.size >= FoodECarbsState.MAX_FOODS) return
    var showPicker by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, modifier = modifier) {
        Icon(IcPluginFood, contentDescription = null, modifier = Modifier.padding(end = AapsSpacing.medium))
        Text(stringResource(UiStrings.food_add))
    }
    if (showPicker) FoodPickerSheet(
        foods = state.availableFoods,
        onPick = {
            showPicker = false
            onAdd(it)
        },
        onDismiss = { showPicker = false }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodPickerSheet(foods: List<FD>, onPick: (FD) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(foods, query) {
        val text = query.trim()
        if (text.isEmpty()) foods else foods.filter { it.name.contains(text, ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.padding(horizontal = AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
            Text(stringResource(UiStrings.food_add_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(UiStrings.food_add_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (shown.isEmpty()) Text(stringResource(UiStrings.food_add_none), style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn {
            items(shown, key = { it.id }) { food ->
                ListItem(
                    headlineContent = { Text(food.name) },
                    supportingContent = { Text(stringResource(UiStrings.food_portion_amount, food.portion.formatAmount(), food.unit)) },
                    trailingContent = { Text(stringResource(InterfacesStrings.format_carbs, food.carbs)) },
                    modifier = Modifier.clickable { onPick(food) }
                )
            }
        }
    }
}

@Composable
internal fun FoodECarbsRow(state: WizardDialogUiState, actions: FoodECarbsCallbacks, modifier: Modifier = Modifier) {
    if (!state.foodMode) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val plan = state.carbPlan()
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
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberInputRow(
                        labelRef = UiStrings.food_ecarbs_carbs_now, value = state.mealCarbsNowPercent.roundToInt().toDouble(),
                        onValueChange = { actions.carbsNow(it.toInt()) },
                        valueRange = FoodECarbsRule.CARBS_NOW_PERCENT.toDoubleRange(), step = 5.0, unitLabel = CoreUiStrings.shortpercent,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.foods.size > 1) InfoTooltipButton(stringResource(UiStrings.food_card_info)) {
                        Text(stringResource(UiStrings.food_meal_carbs_now_hint))
                    }
                }
                NumberInputRow(
                    labelRef = UiStrings.food_ecarbs_amount, value = state.mealECarbsGrams.toDouble(),
                    onValueChange = { actions.grams(it.toInt()) }, valueRange = 0.0..state.maxCarbs.toDouble(),
                    step = 1.0, unitLabel = CoreUiStrings.shortgramm
                )
                if (state.mealAdjust.eCarbsGrams != null) TextButton(onClick = { actions.grams(null) }) {
                    Text(stringResource(UiStrings.food_ecarbs_use_calculated))
                }
                FoodECarbsTiming(FoodECarbsRule(delayMinutes = state.mealDelayMinutes, durationHours = state.mealDurationHours)) {
                    actions.timing(it.delayMinutes, it.durationHours)
                }
            }
        }
    }
}

@Composable
private fun FoodInfoButton(food: FoodECarbsState) {
    val rule = food.food.eCarbsRule
    InfoTooltipButton(stringResource(UiStrings.food_card_info)) {
        Text(stringResource(UiStrings.food_ecarbs_amount_hint))
        if (rule != null) {
            val manual = rule.manualGrams
            Text(
                if (manual == null) stringResource(UiStrings.food_ecarbs_saved_method, methodLabel(rule.method))
                else stringResource(UiStrings.food_ecarbs_saved_manual_amount, NumberFormat.DECIMAL_1.format(manual), food.food.portion.formatAmount(), food.food.unit)
            )
            Text(stringResource(UiStrings.food_ecarbs_how_to_change))
        }
    }
}

@Composable
internal fun CarbsSplitLine(state: WizardDialogUiState, modifier: Modifier = Modifier) {
    val plan = state.carbPlan()
    if (plan.carbs == state.carbs && plan.eCarbs == 0) return
    Text(
        stringResource(UiStrings.wizard_carbs_split, plan.carbs, plan.eCarbs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
internal fun foodFormulaEffect(state: WizardDialogUiState): String {
    val plan = state.carbPlan()
    return stringResource(UiStrings.food_ecarbs_type_desc, 100 - state.mealCarbsNowPercent.roundToInt(), plan.eCarbs, plan.delayMinutes, plan.durationHours)
}
