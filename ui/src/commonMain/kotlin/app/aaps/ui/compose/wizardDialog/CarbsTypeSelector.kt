package app.aaps.ui.compose.wizardDialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcBread
import app.aaps.core.ui.compose.icons.IcCake
import app.aaps.core.ui.compose.icons.IcPizza
import app.aaps.core.ui.compose.icons.IcPluginFood
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import kotlinx.coroutines.launch

/**
 * How the carbs are split: bread (all now), dumplings or pizza (part now, eCarbs added), or the
 * eCarbs formula of the food the wizard was opened from. They exclude each other, so they are one
 * choice. The food choice is shown only for a food with a saved formula, and is disabled while the
 * food lacks a nutrient the formula needs (the food card says which).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CarbsTypeSelector(
    state: WizardDialogUiState,
    onCarbsTypeChange: (CarbsType) -> Unit,
    onFoodFormulaSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val food = state.foodECarbs?.takeIf { it.food.eCarbsRule != null }
    val foodSelected = food?.enabled == true
    val count = CarbsType.entries.size + if (food != null) 1 else 0
    Row(modifier = modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
            CarbsType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = !foodSelected && state.carbsType == type,
                    onClick = { onCarbsTypeChange(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, count),
                    icon = {}
                ) {
                    Icon(
                        imageVector = when (type) {
                            CarbsType.BREAD -> IcBread
                            CarbsType.CAKE  -> IcCake
                            CarbsType.PIZZA -> IcPizza
                        },
                        // Was the enum name ("BREAD"), untranslated and saying
                        // nothing about what the choice does to the carbs.
                        contentDescription = carbsTypeEffect(type),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (food != null) SegmentedButton(
                selected = foodSelected,
                onClick = onFoodFormulaSelect,
                shape = SegmentedButtonDefaults.itemShape(count - 1, count),
                enabled = foodSelected || (food.canUseRule && food.rule.isValid),
                icon = {}
            ) {
                Icon(
                    imageVector = IcPluginFood,
                    contentDescription = stringResource(UiStrings.food_ecarbs_enable),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        val tooltipState = rememberTooltipState()
        val scope = rememberCoroutineScope()
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = {
                PlainTooltip {
                    Column {
                        Text(
                            text = when {
                                food != null && foodSelected       -> food.food.name
                                state.carbsType == CarbsType.BREAD -> stringResource(CoreUiStrings.carbs_type_bread)
                                state.carbsType == CarbsType.CAKE  -> stringResource(CoreUiStrings.carbs_type_cake)
                                else                             -> stringResource(CoreUiStrings.carbs_type_pizza)
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (foodSelected) foodFormulaEffect(state) else carbsTypeEffect(state.carbsType),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            state = tooltipState
        ) {
            IconButton(onClick = { scope.launch { tooltipState.show() } }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(CoreUiStrings.carousel_show_card),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * How much of the meal counts in the bolus now and how much comes later as eCarbs, for the bread,
 * dumplings and pizza choices as well as the food formula. Nothing is shown when all carbs count now.
 */
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

/** What a carbs type does to the entered carbs, said on its button and shown in the info tooltip. */
@Composable
internal fun carbsTypeEffect(type: CarbsType): String =
    if (type == CarbsType.BREAD) stringResource(UiStrings.wizard_carbs_type_bread_desc)
    else stringResource(
        UiStrings.wizard_carbs_type_desc,
        100 - type.carbsPercent,
        type.eCarbsPercent,
        type.eCarbsDelayMinutes,
        type.eCarbsDurationHours
    )

/** The same for the food formula, with the grams of this meal because they come from the food. */
@Composable
private fun foodFormulaEffect(state: WizardDialogUiState): String {
    val plan = state.carbPlan()
    val rule = state.foodECarbs?.rule ?: return ""
    return stringResource(UiStrings.food_ecarbs_type_desc, 100 - rule.carbsNowPercent, plan.eCarbs, plan.delayMinutes, plan.durationHours)
}
