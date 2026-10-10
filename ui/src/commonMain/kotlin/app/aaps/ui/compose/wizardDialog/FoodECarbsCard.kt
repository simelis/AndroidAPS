package app.aaps.ui.compose.wizardDialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.FD
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

internal data class FoodECarbsCallbacks(
    val portionGrams: (Double) -> Unit = {},
    val enabled: (Boolean) -> Unit = {},
    val settings: (FoodECarbsSettings) -> Unit = {},
    val grams: (Int?) -> Unit = {}
)

/** Apply the stored food rule, with portion and overrides for this meal only. */
@Composable
internal fun FoodECarbsCard(state: WizardDialogUiState, actions: FoodECarbsCallbacks) {
    val food = state.foodECarbs ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(food.food.name, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(UiStrings.food_nutrients_basis))
            if (!food.hasWeight) Text(stringResource(UiStrings.food_weight_required), color = MaterialTheme.colorScheme.error)
            NumberInputRow(
                labelRef = UiStrings.food_portion_grams, value = food.portionGrams,
                onValueChange = actions.portionGrams, valueRange = 0.0..10000.0,
                step = 10.0, decimalPlaces = 1, unitLabel = TextRef.Literal("g")
            )
            Text(stringResource(UiStrings.food_ecarbs_servings_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(UiStrings.food_ecarbs_macros,
                food.carbsGrams,
                if (food.food.protein == null) "—" else NumberFormat.DECIMAL_1.format(food.proteinGrams),
                if (food.food.fat == null) "—" else NumberFormat.DECIMAL_1.format(food.fatGrams)))
            FoodECarbsSwitch(UiStrings.food_ecarbs_enable, food.enabled, food.enabled || (food.hasMacros && food.settings.isValid), actions.enabled)
            if (!food.settings.isValid) Text(stringResource(UiStrings.food_ecarbs_configure_food))
            if (!food.hasMacros) Text(stringResource(UiStrings.food_ecarbs_missing_macros))
            if (food.enabled) {
                food.settings.manualGramsPer100g?.let {
                    Text(stringResource(UiStrings.food_ecarbs_saved_manual, NumberFormat.DECIMAL_1.format(it)))
                }
                Text(stringResource(UiStrings.food_ecarbs_saved_method, methodLabel(food.settings.method)))
                Text(stringResource(UiStrings.food_ecarbs_calculated, food.calculatedGrams))
                NumberInputRow(
                    labelRef = UiStrings.food_ecarbs_amount, value = food.eCarbsGrams.toDouble(),
                    onValueChange = { actions.grams(it.toInt()) }, valueRange = 0.0..state.maxCarbs.toDouble(),
                    step = 1.0, unitLabel = TextRef.Literal("g")
                )
                if (food.overrideGrams != null) TextButton(onClick = { actions.grams(null) }) {
                    Text(stringResource(UiStrings.food_ecarbs_use_calculated))
                }
                FoodECarbsTiming(food.settings, actions.settings)
                Text(stringResource(UiStrings.food_ecarbs_preview, state.carbs, food.eCarbsGrams,
                    food.settings.delayMinutes, food.settings.durationHours))
                Text(stringResource(UiStrings.food_ecarbs_profile_notice), style = MaterialTheme.typography.bodySmall)
            }
            if (!state.foodInputsValid) Text(stringResource(UiStrings.food_ecarbs_invalid), color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Reusable rule edited and saved with the food, including a manual equivalent per 100 g. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodECarbsRuleEditor(
    food: FD, enabled: Boolean, settings: FoodECarbsSettings,
    onEnabledChange: (Boolean) -> Unit, onSettingsChange: (FoodECarbsSettings) -> Unit
) {
    val hasMacros = FoodECarbsState(food).hasMacros
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FoodECarbsSwitch(UiStrings.food_ecarbs_rule_enable, enabled, hasMacros || enabled, onEnabledChange)
        if (!hasMacros) Text(stringResource(UiStrings.food_ecarbs_missing_macros))
        if (enabled) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = methodLabel(settings.method), onValueChange = {}, readOnly = true,
                    label = { Text(stringResource(UiStrings.food_ecarbs_method)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    FoodECarbsMethod.entries.forEach { method ->
                        DropdownMenuItem(text = { Text(methodLabel(method)) }, onClick = {
                            expanded = false
                            onSettingsChange(settings.copy(method = method, manualGramsPer100g = null))
                        })
                    }
                }
            }
            Text(stringResource(when (settings.method) {
                FoodECarbsMethod.CUSTOM       -> UiStrings.food_ecarbs_custom_description
                FoodECarbsMethod.FPU          -> UiStrings.food_ecarbs_fpu_description
                FoodECarbsMethod.MODIFIED_FPU -> UiStrings.food_ecarbs_modified_description
            }), style = MaterialTheme.typography.bodySmall)
            if (settings.method == FoodECarbsMethod.CUSTOM) {
                NumberInputRow(
                    labelRef = UiStrings.food_ecarbs_protein_factor, value = settings.proteinPercent.toDouble(),
                    onValueChange = { onSettingsChange(settings.copy(proteinPercent = it.toInt(), manualGramsPer100g = null)) },
                    valueRange = 0.0..100.0, step = 1.0, unitLabel = TextRef.Literal("%")
                )
                NumberInputRow(
                    labelRef = UiStrings.food_ecarbs_fat_factor, value = settings.fatPercent.toDouble(),
                    onValueChange = { onSettingsChange(settings.copy(fatPercent = it.toInt(), manualGramsPer100g = null)) },
                    valueRange = 0.0..100.0, step = 1.0, unitLabel = TextRef.Literal("%")
                )
            }
            val calculated = settings.equivalentGrams((food.protein ?: 0).toDouble(), (food.fat ?: 0).toDouble())
            Text(stringResource(UiStrings.food_ecarbs_per100_calculated, NumberFormat.DECIMAL_1.format(calculated)))
            NumberInputRow(
                labelRef = UiStrings.food_ecarbs_per100_amount, value = settings.manualGramsPer100g ?: calculated,
                onValueChange = { onSettingsChange(settings.copy(manualGramsPer100g = it)) },
                valueRange = 0.0..100.0, step = 0.1, decimalPlaces = 1, unitLabel = TextRef.Literal("g")
            )
            if (settings.manualGramsPer100g != null) TextButton(onClick = { onSettingsChange(settings.copy(manualGramsPer100g = null)) }) {
                Text(stringResource(UiStrings.food_ecarbs_use_calculated))
            }
            FoodECarbsTiming(settings, onSettingsChange)
            Text(stringResource(UiStrings.food_ecarbs_rule_hint), style = MaterialTheme.typography.bodySmall)
            if (!settings.isValid) Text(stringResource(UiStrings.food_ecarbs_rule_invalid), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun FoodECarbsSwitch(label: TextRef, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val text = stringResource(label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = text })
    }
}

@Composable
private fun FoodECarbsTiming(settings: FoodECarbsSettings, onChange: (FoodECarbsSettings) -> Unit) {
    NumberInputRow(
        labelRef = UiStrings.food_ecarbs_delay, value = settings.delayMinutes.toDouble(),
        onValueChange = { onChange(settings.copy(delayMinutes = it.toInt())) },
        valueRange = 0.0..720.0, step = 15.0, unitLabel = CoreUiStrings.units_min
    )
    NumberInputRow(
        labelRef = UiStrings.food_ecarbs_duration, value = settings.durationHours.toDouble(),
        onValueChange = { onChange(settings.copy(durationHours = it.toInt())) },
        valueRange = 0.0..10.0, step = 1.0, unitLabel = TextRef.Literal("h")
    )
}

@Composable
private fun methodLabel(method: FoodECarbsMethod): String = stringResource(when (method) {
    FoodECarbsMethod.CUSTOM       -> UiStrings.food_ecarbs_custom
    FoodECarbsMethod.FPU          -> UiStrings.food_ecarbs_fpu
    FoodECarbsMethod.MODIFIED_FPU -> UiStrings.food_ecarbs_modified_fpu
})