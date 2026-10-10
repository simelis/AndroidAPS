package app.aaps.ui.compose.foodManagement

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.compose.wizardDialog.FoodECarbsSettings
import app.aaps.ui.compose.wizardDialog.FoodECarbsSettingsStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Inject
class FoodManagementViewModel(
    private val persistenceLayer: PersistenceLayer,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodManagementUiState())
    val uiState: StateFlow<FoodManagementUiState> = _uiState.asStateFlow()

    /** Derived: foods filtered by current search query and category/subcategory filters */
    val filteredFoods: StateFlow<List<FD>> = uiState
        .map { s ->
            s.foods.filter { food ->
                (s.searchQuery.isBlank() || food.name.contains(s.searchQuery, ignoreCase = true))
                    && (s.filterCategory == null || food.category == s.filterCategory)
                    && (s.filterSubCategory == null || food.subCategory == s.filterSubCategory)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Derived: subcategories available for the currently selected category filter */
    val filteredSubCategories: StateFlow<List<String>> = uiState
        .map { s ->
            val foods = s.foods
            val cat = s.filterCategory
            val relevant = if (cat != null) foods.filter { it.category == cat } else foods
            relevant.mapNotNull { it.subCategory }.filter { it.isNotBlank() }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadData()
        observeChanges()
    }

    @OptIn(FlowPreview::class)
    private fun observeChanges() {
        persistenceLayer.observeChanges<FD>()
            .debounce(1000L)
            .onEach { loadData() }
            .launchIn(viewModelScope)
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                val foods = persistenceLayer.getFoods()
                val categories = foods.mapNotNull { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                val subCategories = foods.mapNotNull { it.subCategory }.filter { it.isNotBlank() }.distinct().sorted()
                val savedRules = preferences.get(StringNonKey.FoodECarbsSettings)
                val rules = foods.associate { it.id to FoodECarbsSettingsStore.load(savedRules, it.id) }.filterValues { it.isValid }
                _uiState.update {
                    it.copy(
                        foods = foods,
                        foodRules = rules,
                        allCategories = categories,
                        allSubCategories = subCategories,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to load foods", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setFilterCategory(category: String?) {
        _uiState.update { it.copy(filterCategory = category, filterSubCategory = null) }
    }

    fun setFilterSubCategory(subCategory: String?) {
        _uiState.update { it.copy(filterSubCategory = subCategory) }
    }

    fun openEditor(food: FD? = null) {
        val nutrition = food?.nutrientsPer100g()
        val settings = food?.let { FoodECarbsSettingsStore.load(preferences.get(StringNonKey.FoodECarbsSettings), it.id) }
            ?: FoodECarbsSettings()
        _uiState.update {
            it.copy(
                editorFood = food, editorName = food?.name ?: "",
                editorCategory = food?.category ?: "", editorSubCategory = food?.subCategory ?: "",
                editorCarbs = nutrition?.carbs?.roundToInt()?.toString() ?: "",
                editorFat = nutrition?.fat?.roundToInt()?.toString() ?: "",
                editorProtein = nutrition?.protein?.roundToInt()?.toString() ?: "",
                editorEnergy = nutrition?.energy?.roundToInt()?.toString() ?: "",
                editorNeedsWeight = food != null && nutrition == null, editorOriginalWeight = "",
                editorECarbsSettings = settings, editorUseECarbs = settings.isValid,
                editorSaveAttempted = false, editorSaving = false, editorSaveFailed = false, showEditor = true
            )
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(showEditor = false) }
    }

    fun updateEditorName(v: String) = _uiState.update { it.copy(editorName = v) }
    fun updateEditorCategory(v: String) = _uiState.update { it.copy(editorCategory = v) }
    fun updateEditorSubCategory(v: String) = _uiState.update { it.copy(editorSubCategory = v) }
    fun updateEditorCarbs(v: String) = _uiState.update { it.copy(editorCarbs = v) }
    fun updateEditorFat(v: String) = _uiState.update { it.copy(editorFat = v, editorECarbsSettings = it.editorECarbsSettings.copy(manualGramsPer100g = null)) }
    fun updateEditorProtein(v: String) = _uiState.update { it.copy(editorProtein = v, editorECarbsSettings = it.editorECarbsSettings.copy(manualGramsPer100g = null)) }
    fun updateEditorEnergy(v: String) = _uiState.update { it.copy(editorEnergy = v) }
    fun updateEditorECarbsEnabled(v: Boolean) = _uiState.update { it.copy(editorUseECarbs = v) }
    fun updateEditorECarbsSettings(v: FoodECarbsSettings) = _uiState.update { it.copy(editorECarbsSettings = v) }

    fun updateEditorOriginalWeight(v: String) {
        _uiState.update { state ->
            val weight = v.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
            val nutrition = weight?.let { state.editorFood?.copy(portion = it, unit = "g")?.nutrientsPer100g() }
            state.copy(
                editorOriginalWeight = v,
                editorCarbs = nutrition?.carbs?.roundToInt()?.toString() ?: "",
                editorFat = nutrition?.fat?.roundToInt()?.toString() ?: "",
                editorProtein = nutrition?.protein?.roundToInt()?.toString() ?: "",
                editorEnergy = nutrition?.energy?.roundToInt()?.toString() ?: "",
                editorECarbsSettings = state.editorECarbsSettings.copy(manualGramsPer100g = null)
            )
        }
    }

    fun saveFood() {
        _uiState.update { it.copy(editorSaveAttempted = true) }
        val state = uiState.value
        val name = state.editorName.trim()
        if (!state.editorCanSave || state.editorSaving) return

        val food = FD(
            id = state.editorFood?.id ?: 0,
            version = state.editorFood?.version ?: 0,
            dateCreated = state.editorFood?.dateCreated ?: -1,
            isValid = true,
            referenceId = state.editorFood?.referenceId,
            ids = state.editorFood?.ids ?: IDs(),
            name = name,
            category = state.editorCategory.trim().ifBlank { null },
            subCategory = state.editorSubCategory.trim().ifBlank { null },
            portion = 100.0,
            unit = "g",
            carbs = state.editorCarbs.toInt(),
            fat = state.editorFat.toIntOrNull(),
            protein = state.editorProtein.toIntOrNull(),
            energy = state.editorEnergy.toIntOrNull(),
            gi = state.editorFood?.gi,
        )

        _uiState.update { it.copy(editorSaving = true, editorSaveFailed = false) }
        viewModelScope.launch {
            try {
                val result = persistenceLayer.insertOrUpdateFood(food)
                val savedFood = result.all().firstOrNull() ?: food.takeIf { it.id > 0 }
                check(savedFood != null && savedFood.id > 0) { "Saved food has no database ID" }
                _uiState.update { it.copy(editorFood = savedFood) }
                val json = preferences.get(StringNonKey.FoodECarbsSettings)
                preferences.put(StringNonKey.FoodECarbsSettings, if (state.editorUseECarbs)
                    FoodECarbsSettingsStore.save(json, savedFood.id, state.editorECarbsSettings)
                else FoodECarbsSettingsStore.remove(json, savedFood.id))
                _uiState.update { it.copy(showEditor = false) }
                loadData()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to save food", e)
                _uiState.update { it.copy(editorSaveFailed = true) }
            } finally {
                _uiState.update { it.copy(editorSaving = false) }
            }
        }
    }

    fun deleteFood(food: FD) {
        viewModelScope.launch {
            try {
                persistenceLayer.invalidateFood(food.id, Action.FOOD_REMOVED, Sources.Food)
                _uiState.update {
                    it.copy(
                        foods = it.foods.filter { f -> f.id != food.id },
                        undoFood = food
                    )
                }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to delete food", e)
            }
        }
    }

    fun deleteAndCloseEditor() {
        val food = uiState.value.editorFood ?: return
        deleteFood(food)
        closeEditor()
    }

    fun undoDelete() {
        val food = uiState.value.undoFood ?: return
        viewModelScope.launch {
            try {
                persistenceLayer.insertOrUpdateFood(food.copy(isValid = true))
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to undo food delete", e)
            } finally {
                _uiState.update { it.copy(undoFood = null) }
            }
        }
    }

    fun clearUndo() {
        _uiState.update { it.copy(undoFood = null) }
    }
}

@Immutable
data class FoodManagementUiState(
    val foods: List<FD> = emptyList(),
    val foodRules: Map<Long, FoodECarbsSettings> = emptyMap(),
    val isLoading: Boolean = true,
    // Filters
    val searchQuery: String = "",
    val filterCategory: String? = null,
    val filterSubCategory: String? = null,
    val allCategories: List<String> = emptyList(),
    val allSubCategories: List<String> = emptyList(),
    // Editor
    val showEditor: Boolean = false,
    val editorFood: FD? = null,
    val editorName: String = "",
    val editorCategory: String = "",
    val editorSubCategory: String = "",
    val editorNeedsWeight: Boolean = false,
    val editorOriginalWeight: String = "",
    val editorCarbs: String = "",
    val editorFat: String = "",
    val editorProtein: String = "",
    val editorEnergy: String = "",
    val editorSaveAttempted: Boolean = false,
    val editorSaving: Boolean = false,
    val editorSaveFailed: Boolean = false,
    val editorUseECarbs: Boolean = false,
    val editorECarbsSettings: FoodECarbsSettings = FoodECarbsSettings(),
    // Undo
    val undoFood: FD? = null,
) {
    val editorPreview: FD get() = FD(name = editorName, portion = 100.0, carbs = editorCarbs.toIntOrNull() ?: 0,
        fat = editorFat.toIntOrNull(), protein = editorProtein.toIntOrNull())

    val editorCanSave: Boolean get() {
        fun nutrient(value: String, required: Boolean = false): Boolean =
            (!required && value.isBlank()) || value.toIntOrNull()?.let { it in 0..100 } == true
        val weightValid = !editorNeedsWeight || editorOriginalWeight.toDoubleOrNull()?.let { it.isFinite() && it > 0 } == true
        return editorName.isNotBlank() && weightValid && nutrient(editorCarbs, required = true) &&
            nutrient(editorFat) && nutrient(editorProtein) &&
            (editorEnergy.isBlank() || editorEnergy.toIntOrNull()?.let { it >= 0 } == true) &&
            (!editorUseECarbs || (editorECarbsSettings.isValid && editorFat.toIntOrNull() != null && editorProtein.toIntOrNull() != null))
    }
}
