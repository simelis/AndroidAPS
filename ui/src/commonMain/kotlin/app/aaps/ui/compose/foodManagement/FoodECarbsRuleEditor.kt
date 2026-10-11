package app.aaps.ui.compose.foodManagement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.NumberInputRow
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

/** Reusable rule edited and saved with the food, including a manual equivalent per 100 g. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoodECarbsRuleEditor(
    food: FD, enabled: Boolean, rule: FoodECarbsRule,
    onEnabledChange: (Boolean) -> Unit, onRuleChange: (FoodECarbsRule) -> Unit
) {
    val hasMacros = food.hasProteinAndFat
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        FoodECarbsSwitch(UiStrings.food_ecarbs_rule_enable, enabled, hasMacros || enabled, onEnabledChange)
        if (!hasMacros) Text(stringResource(UiStrings.food_ecarbs_missing_macros))
        if (enabled) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = methodLabel(rule.method), onValueChange = {}, readOnly = true,
                    label = { Text(stringResource(UiStrings.food_ecarbs_method)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    FoodECarbsMethod.entries.forEach { method ->
                        DropdownMenuItem(text = { Text(methodLabel(method)) }, onClick = {
                            expanded = false
                            onRuleChange(rule.copy(method = method, manualGramsPer100g = null))
                        })
                    }
                }
            }
            Text(stringResource(when (rule.method) {
                FoodECarbsMethod.CUSTOM       -> UiStrings.food_ecarbs_custom_description
                FoodECarbsMethod.FPU          -> UiStrings.food_ecarbs_fpu_description
                FoodECarbsMethod.MODIFIED_FPU -> UiStrings.food_ecarbs_modified_description
            }), style = MaterialTheme.typography.bodySmall)
            if (rule.method == FoodECarbsMethod.CUSTOM) {
                NumberInputRow(
                    labelRef = UiStrings.food_ecarbs_protein_factor, value = rule.proteinPercent.toDouble(),
                    onValueChange = { onRuleChange(rule.copy(proteinPercent = it.toInt(), manualGramsPer100g = null)) },
                    valueRange = FoodECarbsRule.FACTOR_PERCENT.toDoubleRange(), step = 1.0, unitLabel = CoreUiStrings.shortpercent
                )
                NumberInputRow(
                    labelRef = UiStrings.food_ecarbs_fat_factor, value = rule.fatPercent.toDouble(),
                    onValueChange = { onRuleChange(rule.copy(fatPercent = it.toInt(), manualGramsPer100g = null)) },
                    valueRange = FoodECarbsRule.FACTOR_PERCENT.toDoubleRange(), step = 1.0, unitLabel = CoreUiStrings.shortpercent
                )
            }
            val calculated = rule.equivalentGrams((food.protein ?: 0).toDouble(), (food.fat ?: 0).toDouble())
            Text(stringResource(UiStrings.food_ecarbs_per100_calculated, NumberFormat.DECIMAL_1.format(calculated)))
            NumberInputRow(
                labelRef = UiStrings.food_ecarbs_per100_amount, value = rule.manualGramsPer100g ?: calculated,
                onValueChange = { onRuleChange(rule.copy(manualGramsPer100g = it)) },
                valueRange = FoodECarbsRule.MANUAL_GRAMS_PER_100G, step = 0.1, decimalPlaces = 1, unitLabel = CoreUiStrings.shortgramm
            )
            if (rule.manualGramsPer100g != null) TextButton(onClick = { onRuleChange(rule.copy(manualGramsPer100g = null)) }) {
                Text(stringResource(UiStrings.food_ecarbs_use_calculated))
            }
            FoodECarbsTiming(rule, onRuleChange)
            Text(stringResource(UiStrings.food_ecarbs_rule_hint), style = MaterialTheme.typography.bodySmall)
            if (!rule.isValid) Text(
                stringResource(UiStrings.food_ecarbs_rule_invalid, FoodECarbsRule.DURATION_HOURS.first, FoodECarbsRule.DURATION_HOURS.last),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
internal fun FoodECarbsSwitch(label: TextRef, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val text = stringResource(label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)) {
        Text(text, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = text })
    }
}

/** Duration starts at 0 so a new rule has no duration until the user chooses one. */
@Composable
internal fun FoodECarbsTiming(rule: FoodECarbsRule, onChange: (FoodECarbsRule) -> Unit) {
    NumberInputRow(
        labelRef = UiStrings.food_ecarbs_delay, value = rule.delayMinutes.toDouble(),
        onValueChange = { onChange(rule.copy(delayMinutes = it.toInt())) },
        valueRange = FoodECarbsRule.DELAY_MINUTES.toDoubleRange(), step = 15.0, unitLabel = CoreUiStrings.units_min
    )
    NumberInputRow(
        labelRef = UiStrings.food_ecarbs_duration, value = rule.durationHours.toDouble(),
        onValueChange = { onChange(rule.copy(durationHours = it.toInt())) },
        valueRange = 0.0..FoodECarbsRule.DURATION_HOURS.last.toDouble(), step = 1.0, unitLabel = CoreUiStrings.units_hours
    )
}

@Composable
internal fun methodLabel(method: FoodECarbsMethod): String = stringResource(when (method) {
    FoodECarbsMethod.CUSTOM       -> UiStrings.food_ecarbs_custom
    FoodECarbsMethod.FPU          -> UiStrings.food_ecarbs_fpu
    FoodECarbsMethod.MODIFIED_FPU -> UiStrings.food_ecarbs_modified_fpu
})

private fun IntRange.toDoubleRange(): ClosedFloatingPointRange<Double> = first.toDouble()..last.toDouble()
