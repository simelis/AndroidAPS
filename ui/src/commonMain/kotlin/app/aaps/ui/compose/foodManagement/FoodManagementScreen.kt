package app.aaps.ui.compose.foodManagement

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSearchField
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.clearFocusOnTap
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FoodManagementScreen(
    viewModel: FoodManagementViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToWizard: (foodId: Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredFoods by viewModel.filteredFoods.collectAsStateWithLifecycle()
    val filteredSubCategories by viewModel.filteredSubCategories.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current
    val undoLabel = stringResource(CoreUiStrings.undo)
    val deletedLabel = stringResource(CoreUiStrings.food_deleted)

    // Show undo snackbar when a food is deleted
    LaunchedEffect(state.undoFood) {
        state.undoFood?.let {
            val result = snackbarHostState.showSnackbar(
                message = deletedLabel,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            } else {
                viewModel.clearUndo()
            }
        }
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    AapsSearchField(
                        query = state.searchQuery,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiStrings.back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openEditor() }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(CoreUiStrings.add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Filter chips. Favourites comes first and is always there, so it can be found before any are marked.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AapsSpacing.extraLarge),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                FilterChip(
                    selected = state.showFavouritesOnly,
                    onClick = { viewModel.setShowFavouritesOnly(!state.showFavouritesOnly) },
                    label = { Text(stringResource(UiStrings.food_favourites)) },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                )
                state.allCategories.forEach { cat ->
                    FilterChip(
                        selected = state.filterCategory == cat,
                        onClick = {
                            viewModel.setFilterCategory(if (state.filterCategory == cat) null else cat)
                        },
                        label = { Text(cat) }
                    )
                }
            }
            if (filteredSubCategories.isNotEmpty() && state.filterCategory != null) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AapsSpacing.extraLarge),
                    horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
                ) {
                    filteredSubCategories.forEach { sub ->
                        FilterChip(
                            selected = state.filterSubCategory == sub,
                            onClick = {
                                viewModel.setFilterSubCategory(if (state.filterSubCategory == sub) null else sub)
                            },
                            label = { Text(sub) }
                        )
                    }
                }
            }

            // Food list
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredFoods.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(if (state.showFavouritesOnly && state.favouriteIds.none { id -> state.foods.any { it.id == id } })
                            UiStrings.food_favourites_empty else CoreUiStrings.no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = AapsSpacing.fabClearance),
                    verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
                ) {
                    items(filteredFoods, key = { it.id }) { food ->
                        FoodItem(
                            food = food,
                            isFavourite = food.id in state.favouriteIds,
                            onToggleFavourite = { viewModel.toggleFavourite(food) },
                            onEdit = { viewModel.openEditor(food) },
                            onCalculate = { onNavigateToWizard(food.id) }
                        )
                    }
                }
            }
        }

        // Editor bottom sheet
        if (state.showEditor) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val focusManager = LocalFocusManager.current
            ModalBottomSheet(
                onDismissRequest = { focusManager.clearFocus(); viewModel.closeEditor() },
                sheetState = sheetState,
            ) {
                FoodEditorContent(
                    state = state,
                    onNameChange = viewModel::updateEditorName,
                    onCategoryChange = viewModel::updateEditorCategory,
                    onSubCategoryChange = viewModel::updateEditorSubCategory,
                    onCarbsChange = viewModel::updateEditorCarbs,
                    onFatChange = viewModel::updateEditorFat,
                    onProteinChange = viewModel::updateEditorProtein,
                    onEnergyChange = viewModel::updateEditorEnergy,
                    onSave = { focusManager.clearFocus(); viewModel.saveFood() },
                    onCancel = { focusManager.clearFocus(); viewModel.closeEditor() },
                    onDelete = if (state.editorFood != null) {
                        { focusManager.clearFocus(); viewModel.deleteAndCloseEditor() }
                    } else null,
                    isEditing = state.editorFood != null,
                    onPortionChange = viewModel::updateEditorPortion,
                    onUnitChange = viewModel::updateEditorUnit,
                    onAskAmountChange = viewModel::updateEditorAskAmount,
                    onECarbsEnabledChange = viewModel::updateEditorECarbsEnabled,
                    onECarbsRuleChange = viewModel::updateEditorECarbsRule,
                    onFavouriteChange = viewModel::updateEditorFavourite
                )
            }
        }
    }
}

@Composable
private fun FoodItem(
    food: FD,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onEdit: () -> Unit,
    onCalculate: () -> Unit,
) {
    val basis = stringResource(UiStrings.food_portion_amount, food.portion.formatAmount(), food.unit)
    val rule = food.eCarbsRule
    ElevatedCard(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AapsSpacing.extraLarge)
            .animateContentSize(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AapsSpacing.extraLarge, top = AapsSpacing.large, bottom = AapsSpacing.large, end = AapsSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = food.name,
                    style = MaterialTheme.typography.titleSmall
                )
                val category = food.category
                val subCategory = food.subCategory
                Text(
                    text = when {
                        category == null    -> basis
                        subCategory == null -> stringResource(UiStrings.food_details_category, basis, category)
                        else                -> stringResource(UiStrings.food_details_subcategory, basis, category, subCategory)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val unknown = stringResource(UiStrings.food_value_unknown)
                fun grams(value: Int?): String = value?.toString() ?: unknown
                Text(
                    text = stringResource(InterfacesStrings.format_carbs, food.carbs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                val fat = grams(food.fat)
                val protein = grams(food.protein)
                val energy = food.energy?.takeIf { it > 0 }
                if (rule != null && food.canUse(rule)) {
                    // For the saved amount shown on the left, like the nutrients
                    val eCarbs = rule.manualGrams ?: rule.equivalentGrams((food.protein ?: 0).toDouble(), (food.fat ?: 0).toDouble())
                    Text(stringResource(UiStrings.food_ecarbs_list, NumberFormat.UP_TO_1_DECIMAL.format(eCarbs)), style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    text = if (energy == null) stringResource(UiStrings.food_fat_protein, fat, protein)
                    else stringResource(UiStrings.food_fat_protein_energy, fat, protein, energy.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Named by the food like the calculator button below, so a screen reader says "Chicken salad, Favourite, on".
            IconToggleButton(
                checked = isFavourite,
                onCheckedChange = { onToggleFavourite() },
                modifier = Modifier.semantics { contentDescription = food.name }
            ) {
                Icon(
                    imageVector = if (isFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(UiStrings.food_favourite),
                    modifier = Modifier.size(AapsSpacing.chipIconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Name the food, because every row has this same button. Without it a screen reader
            // says "Bolus wizard" once per row with nothing to tell them apart, and picking the
            // wrong one opens the calculator already filled with the wrong carbs.
            //
            // The food name goes on the IconButton and the verb stays on the Icon: a
            // contentDescription on a merging node is inserted BEFORE its children rather than
            // replacing them, so this reads "Chicken salad, Bolus wizard".
            IconButton(
                onClick = onCalculate,
                modifier = Modifier.semantics { contentDescription = food.name }
            ) {
                Icon(
                    imageVector = IcCalculator,
                    contentDescription = stringResource(CoreUiStrings.boluswizard),
                    modifier = Modifier.size(AapsSpacing.chipIconSize),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun FoodEditorContent(
    state: FoodManagementUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSubCategoryChange: (String) -> Unit,
    onCarbsChange: (String) -> Unit,
    onFatChange: (String) -> Unit,
    onProteinChange: (String) -> Unit,
    onEnergyChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)?,
    isEditing: Boolean,
    onPortionChange: (String) -> Unit = {},
    onUnitChange: (String) -> Unit = {},
    onAskAmountChange: (Boolean) -> Unit = {},
    onECarbsEnabledChange: (Boolean) -> Unit = {},
    onECarbsRuleChange: (FoodECarbsRule) -> Unit = {},
    onFavouriteChange: (Boolean) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .clearFocusOnTap(focusManager)
            .padding(horizontal = AapsSpacing.xxLarge)
            .padding(bottom = AapsSpacing.xxLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (isEditing) CoreUiStrings.edit_food else CoreUiStrings.add_food),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            // Applied on Save, like the rest of the form: a new food has no ID before that
            IconToggleButton(checked = state.editorFavourite, onCheckedChange = onFavouriteChange) {
                Icon(
                    imageVector = if (state.editorFavourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = stringResource(UiStrings.food_favourite),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        OutlinedTextField(
            value = state.editorName,
            onValueChange = onNameChange,
            label = { Text(stringResource(CoreUiStrings.name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = state.editorSaveAttempted && state.editorName.isBlank()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            OutlinedTextField(
                value = state.editorCategory,
                onValueChange = onCategoryChange,
                label = { Text(stringResource(CoreUiStrings.category)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = state.editorSubCategory,
                onValueChange = onSubCategoryChange,
                label = { Text(stringResource(CoreUiStrings.subcategory)) },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        // The nutrients below are for this amount: 100 g from a label, 3 pieces, 250 ml, ...
        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            OutlinedTextField(
                value = state.editorPortion, onValueChange = onPortionChange,
                label = { Text(stringResource(UiStrings.food_amount)) }, modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            OutlinedTextField(
                value = state.editorUnit, onValueChange = onUnitChange,
                label = { Text(stringResource(CoreUiStrings.unit_label)) }, modifier = Modifier.weight(1f), singleLine = true
            )
        }
        FoodECarbsSwitch(UiStrings.food_ask_amount, state.editorAskAmount, true, onAskAmountChange) {
            InfoTooltipButton(stringResource(UiStrings.food_more_info)) { Text(stringResource(UiStrings.food_ask_amount_info)) }
        }

        OutlinedTextField(
            value = state.editorCarbs,
            onValueChange = onCarbsChange,
            label = { Text(stringResource(CoreUiStrings.label_carbs_g)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            OutlinedTextField(
                value = state.editorFat,
                onValueChange = onFatChange,
                label = { Text(stringResource(CoreUiStrings.label_fat_g)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = state.editorProtein,
                onValueChange = onProteinChange,
                label = { Text(stringResource(CoreUiStrings.label_protein_g)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = state.editorEnergy,
                onValueChange = onEnergyChange,
                label = { Text(stringResource(CoreUiStrings.label_energy_kj)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        // A typo like 30 g carbs in a 25 g slice: no nutrient can weigh more than the food
        val weight = state.editorWeightGrams
        if (weight != null && listOf(state.editorCarbs, state.editorFat, state.editorProtein).any { (it.toIntOrNull() ?: 0) > weight }) Text(
            stringResource(UiStrings.food_nutrient_more_than_food, state.editorPortion, state.editorUnit),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
        )
        FoodECarbsRuleEditor(state.editorPreview, state.editorUseECarbs, state.editorECarbsRule, onECarbsEnabledChange, onECarbsRuleChange)
        if (state.editorSaveAttempted && !state.editorCanSave) {
            Text(stringResource(UiStrings.food_editor_invalid), color = MaterialTheme.colorScheme.error)
        }
        if (state.editorSaveFailed) Text(stringResource(UiStrings.food_save_failed), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(AapsSpacing.medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(
                        stringResource(CoreUiStrings.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Spacer(Modifier.width(AapsSpacing.extraSmall))
            }
            Row {
                TextButton(onClick = onCancel) {
                    Text(stringResource(CoreUiStrings.cancel))
                }
                Spacer(Modifier.width(AapsSpacing.medium))
                TextButton(
                    onClick = onSave,
                    enabled = !state.editorSaving && (!state.editorSaveAttempted || state.editorCanSave)
                ) {
                    Text(stringResource(CoreUiStrings.save))
                }
            }
        }
    }
}
