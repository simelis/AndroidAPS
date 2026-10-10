package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.ui.compose.wizardDialog.FoodECarbsMethod
import app.aaps.ui.compose.wizardDialog.FoodECarbsSettings
import app.aaps.ui.compose.wizardDialog.FoodECarbsSettingsStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.never

@OptIn(ExperimentalCoroutinesApi::class)
internal class FoodManagementViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: FoodManagementViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} loadData() launch (no advanceUntilIdle), so construction
        // stays clean and we test the synchronous update methods against the default state. The observeChanges()
        // cold flow is still built synchronously during init, so it must be stubbed non-null.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(persistenceLayer.observeChanges(FD::class)).thenReturn(emptyFlow())
        whenever(preferences.get(StringNonKey.FoodECarbsSettings)).thenReturn("{}")
        sut = FoodManagementViewModel(persistenceLayer, aapsLogger, preferences)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is loading with empty filters`() {
        val state = sut.uiState.value
        assertThat(state.isLoading).isTrue()
        assertThat(state.searchQuery).isEmpty()
        assertThat(state.filterCategory).isNull()
        assertThat(state.filterSubCategory).isNull()
        assertThat(state.showEditor).isFalse()
    }

    @Test
    fun `setSearchQuery updates the state`() {
        sut.setSearchQuery("apple")

        assertThat(sut.uiState.value.searchQuery).isEqualTo("apple")
    }

    @Test
    fun `setFilterCategory sets category and clears subcategory`() {
        sut.setFilterSubCategory("Sub")
        sut.setFilterCategory("Fruit")

        val state = sut.uiState.value
        assertThat(state.filterCategory).isEqualTo("Fruit")
        assertThat(state.filterSubCategory).isNull()
    }

    @Test
    fun `setFilterSubCategory updates the state`() {
        sut.setFilterSubCategory("Berries")

        assertThat(sut.uiState.value.filterSubCategory).isEqualTo("Berries")
    }

    @Test
    fun `editor update setters update the state`() {
        sut.updateEditorName("Banana")
        sut.updateEditorCategory("Fruit")
        sut.updateEditorSubCategory("Tropical")
        sut.updateEditorCarbs("27")
        sut.updateEditorFat("0")
        sut.updateEditorProtein("1")
        sut.updateEditorEnergy("105")

        val state = sut.uiState.value
        assertThat(state.editorName).isEqualTo("Banana")
        assertThat(state.editorCategory).isEqualTo("Fruit")
        assertThat(state.editorSubCategory).isEqualTo("Tropical")
        assertThat(state.editorCarbs).isEqualTo("27")
        assertThat(state.editorFat).isEqualTo("0")
        assertThat(state.editorProtein).isEqualTo("1")
        assertThat(state.editorEnergy).isEqualTo("105")
    }

    @Test
    fun `openEditor with null resets editor fields to defaults and shows editor`() {
        sut.updateEditorName("stale")
        sut.openEditor(null)

        val state = sut.uiState.value
        assertThat(state.showEditor).isTrue()
        assertThat(state.editorFood).isNull()
        assertThat(state.editorName).isEmpty()
        assertThat(state.editorPreview.portion).isEqualTo(100.0)
        assertThat(state.editorPreview.unit).isEqualTo("g")
        assertThat(state.editorSaveAttempted).isFalse()
    }

    @Test
    fun `closeEditor hides the editor`() {
        sut.openEditor(null)
        sut.closeEditor()

        assertThat(sut.uiState.value.showEditor).isFalse()
    }

    @Test
    fun `clearUndo clears the undo food`() {
        sut.clearUndo()

        assertThat(sut.uiState.value.undoFood).isNull()
    }

    @Test
    fun `editing an existing gram portion converts all nutrients to a 100 gram basis`() {
        val food = FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10, energy = 1000)
        sut.openEditor(food)
        assertThat(sut.uiState.value.editorCarbs).isEqualTo("25")
        assertThat(sut.uiState.value.editorProtein).isEqualTo("10")
        assertThat(sut.uiState.value.editorFat).isEqualTo("5")
        assertThat(sut.uiState.value.editorEnergy).isEqualTo("500")
        assertThat(sut.uiState.value.editorCanSave).isTrue()
        assertThat(food.portion).isEqualTo(200.0)
    }

    @Test
    fun `a non mass portion needs an explicit weight and is never assumed to weigh 100 grams`() {
        sut.openEditor(FD(id = 7, name = "Meal", portion = 2.0, unit = "pieces", carbs = 50, protein = 20, fat = 10))
        assertThat(sut.uiState.value.editorNeedsWeight).isTrue()
        assertThat(sut.uiState.value.editorCanSave).isFalse()
        sut.updateEditorOriginalWeight("200")
        assertThat(sut.uiState.value.editorCarbs).isEqualTo("25")
        assertThat(sut.uiState.value.editorProtein).isEqualTo("10")
        assertThat(sut.uiState.value.editorCanSave).isTrue()
        sut.updateEditorOriginalWeight("NaN")
        assertThat(sut.uiState.value.editorCanSave).isFalse()
    }

    @Test
    fun `saving a new food stores its 100 gram nutrients and manual rule under the assigned ID`() = runTest {
        whenever(persistenceLayer.getFoods()).thenReturn(emptyList())
        val inserted = FD(id = 19, name = "Meal", portion = 100.0, carbs = 25, fat = 5, protein = 10)
        val result = PersistenceLayer.TransactionResult<FD>().apply { this.inserted.add(inserted) }
        whenever(persistenceLayer.insertOrUpdateFood(any())).thenReturn(result)
        sut.openEditor()
        sut.updateEditorName("Meal")
        sut.updateEditorCarbs("25")
        sut.updateEditorFat("5")
        sut.updateEditorProtein("10")
        val rule = FoodECarbsSettings(method = FoodECarbsMethod.FPU, durationHours = 3, manualGramsPer100g = 7.5)
        sut.updateEditorECarbsSettings(rule)
        sut.updateEditorECarbsEnabled(true)
        sut.saveFood()
        advanceUntilIdle()
        val saved = argumentCaptor<FD>()
        verify(persistenceLayer).insertOrUpdateFood(saved.capture())
        assertThat(saved.firstValue.portion).isEqualTo(100.0)
        assertThat(saved.firstValue.unit).isEqualTo("g")
        assertThat(saved.firstValue.carbs).isEqualTo(25)
        val json = argumentCaptor<String>()
        verify(preferences).put(eq(StringNonKey.FoodECarbsSettings), json.capture())
        assertThat(FoodECarbsSettingsStore.load(json.firstValue, 19)).isEqualTo(rule)
        assertThat(sut.uiState.value.showEditor).isFalse()
    }

    @Test
    fun `invalid nutrients or incomplete enabled rules cannot save`() = runTest {
        sut.openEditor()
        sut.updateEditorName("Meal")
        sut.updateEditorCarbs("-1")
        sut.saveFood()
        sut.updateEditorCarbs("20")
        sut.updateEditorECarbsEnabled(true)
        sut.saveFood()
        advanceUntilIdle()
        verify(persistenceLayer, never()).insertOrUpdateFood(any())
        assertThat(sut.uiState.value.showEditor).isTrue()
    }

    @Test
    fun `changing protein or fat discards the saved manual equivalent preview`() {
        sut.updateEditorECarbsSettings(FoodECarbsSettings(durationHours = 3, manualGramsPer100g = 5.0))
        sut.updateEditorProtein("20")
        assertThat(sut.uiState.value.editorECarbsSettings.manualGramsPer100g).isNull()
    }
}
