package app.aaps.ui.compose.foodManagement

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.ui.CoreUiStrings
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Inject
class FoodManagementViewModel(
    private val persistenceLayer: PersistenceLayer,
    private val clientControlDispatcher: ClientControlActionDispatcher,
    private val config: Config,
    private val rh: TextResolver,
    private val aapsLogger: AAPSLogger,
) : ViewModel() {

    // One id for each edit, the same while the user tries to save it again (see ClientControlActionDispatcher.Command.FoodChange)
    private var editorRequestId = newRequestId()

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
                _uiState.update {
                    it.copy(
                        foods = foods,
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
        editorRequestId = newRequestId()
        if (food != null) {
            _uiState.update {
                it.copy(
                    editorFood = food,
                    editorName = food.name,
                    editorCategory = food.category ?: "",
                    editorSubCategory = food.subCategory ?: "",
                    editorPortion = food.portion.toString(),
                    editorUnit = food.unit,
                    editorCarbs = food.carbs.toString(),
                    editorFat = food.fat?.toString() ?: "",
                    editorProtein = food.protein?.toString() ?: "",
                    editorEnergy = food.energy?.toString() ?: "",
                    editorSaveAttempted = false,
                    showEditor = true
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    editorFood = null,
                    editorName = "",
                    editorCategory = "",
                    editorSubCategory = "",
                    editorPortion = "",
                    editorUnit = "g",
                    editorCarbs = "",
                    editorFat = "",
                    editorProtein = "",
                    editorEnergy = "",
                    editorSaveAttempted = false,
                    showEditor = true
                )
            }
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(showEditor = false) }
    }

    fun updateEditorName(v: String) = _uiState.update { it.copy(editorName = v) }
    fun updateEditorCategory(v: String) = _uiState.update { it.copy(editorCategory = v) }
    fun updateEditorSubCategory(v: String) = _uiState.update { it.copy(editorSubCategory = v) }
    fun updateEditorPortion(v: String) = _uiState.update { it.copy(editorPortion = v) }
    fun updateEditorUnit(v: String) = _uiState.update { it.copy(editorUnit = v) }
    fun updateEditorCarbs(v: String) = _uiState.update { it.copy(editorCarbs = v) }
    fun updateEditorFat(v: String) = _uiState.update { it.copy(editorFat = v) }
    fun updateEditorProtein(v: String) = _uiState.update { it.copy(editorProtein = v) }
    fun updateEditorEnergy(v: String) = _uiState.update { it.copy(editorEnergy = v) }

    fun saveFood() {
        _uiState.update { it.copy(editorSaveAttempted = true) }
        val state = uiState.value
        val name = state.editorName.trim()
        if (name.isBlank()) return

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
            portion = state.editorPortion.toDoubleOrNull() ?: 0.0,
            unit = state.editorUnit.trim().ifBlank { "g" },
            carbs = state.editorCarbs.toIntOrNull() ?: 0,
            fat = state.editorFat.toIntOrNull(),
            protein = state.editorProtein.toIntOrNull(),
            energy = state.editorEnergy.toIntOrNull(),
        )

        viewModelScope.launch {
            try {
                // On a client the editor stays open when the master did not take it, so the input is not lost
                if (store(food, editorRequestId)) _uiState.update { it.copy(showEditor = false) }
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Failed to save food", e)
            }
        }
    }

    fun deleteFood(food: FD) {
        viewModelScope.launch {
            try {
                if (store(food.copy(isValid = false), newRequestId()))
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
                store(food.copy(isValid = true), newRequestId())
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

    /**
     * Stores [food], or deletes it when it is not valid. A master stores it here. A client stores nothing: it sends
     * the change to the master, which stores and uploads it, and the food comes back here through Nightscout. Saving
     * it here too would make two foods, because only the Nightscout id tells them apart. Returns true when it was
     * stored.
     */
    private suspend fun store(food: FD, requestId: String): Boolean {
        // Only this client has a food without a Nightscout id (made here by an older build): its delete stays here
        val localOnly = config.AAPSCLIENT && food.id != 0L && food.ids.nightscoutId == null
        if (localOnly && !food.isValid) {
            persistenceLayer.invalidateFood(food.id, Action.FOOD_REMOVED, Sources.Food)
            return true
        }
        val label = rh.gs(if (food.isValid) CoreUiStrings.clientcontrol_action_save_food else CoreUiStrings.clientcontrol_action_delete_food)
        val result = clientControlDispatcher.execute(ClientControlActionDispatcher.Command.FoodChange(requestId, food), label) {
            if (food.isValid) persistenceLayer.insertOrUpdateFood(food)
            else persistenceLayer.invalidateFood(food.id, Action.FOOD_REMOVED, Sources.Food)
            ActionProgress.Applied
        }
        if (result !is ActionProgress.Applied) return false
        // The master added it as a new food, which comes back with a Nightscout id: the old copy would be a second one
        if (localOnly) persistenceLayer.invalidateFood(food.id, Action.FOOD_REMOVED, Sources.Food)
        return true
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newRequestId(): String = Uuid.random().toString()
}

@Immutable
data class FoodManagementUiState(
    val foods: List<FD> = emptyList(),
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
    val editorPortion: String = "",
    val editorUnit: String = "g",
    val editorCarbs: String = "",
    val editorFat: String = "",
    val editorProtein: String = "",
    val editorEnergy: String = "",
    val editorSaveAttempted: Boolean = false,
    // Undo
    val undoFood: FD? = null,
)
