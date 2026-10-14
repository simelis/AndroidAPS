package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
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
import org.mockito.kotlin.times

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
        whenever(preferences.get(StringNonKey.FoodLastECarbsRule)).thenReturn("")
        whenever(preferences.get(StringNonKey.FoodFavourites)).thenReturn("")
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
    fun `a food is edited as saved, with its own amount and nutrients`() {
        sut.openEditor(FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10, energy = 1000, askAmount = false))
        assertThat(sut.uiState.value.editorPortion).isEqualTo("200")
        assertThat(sut.uiState.value.editorUnit).isEqualTo("g")
        assertThat(sut.uiState.value.editorCarbs).isEqualTo("50")
        assertThat(sut.uiState.value.editorEnergy).isEqualTo("1000")
        assertThat(sut.uiState.value.editorAskAmount).isFalse()
        assertThat(sut.uiState.value.editorCanSave).isTrue()
    }

    @Test
    fun `a new food starts as 100 g, asked for every time`() {
        sut.openEditor()
        assertThat(sut.uiState.value.editorPortion).isEqualTo("100")
        assertThat(sut.uiState.value.editorUnit).isEqualTo("g")
        assertThat(sut.uiState.value.editorAskAmount).isTrue()
    }

    @Test
    fun `foods in pieces keep their unit and nutrients and need no weight`() {
        sut.openEditor(FD(id = 7, name = "Meal", portion = 2.0, unit = "pieces", carbs = 50, protein = 20, fat = 10))
        assertThat(sut.uiState.value.editorPortion).isEqualTo("2")
        assertThat(sut.uiState.value.editorUnit).isEqualTo("pieces")
        assertThat(sut.uiState.value.editorCarbs).isEqualTo("50")
        assertThat(sut.uiState.value.editorProtein).isEqualTo("20")
        assertThat(sut.uiState.value.editorCanSave).isTrue()
        sut.updateEditorPortion("NaN")
        assertThat(sut.uiState.value.editorCanSave).isFalse()
    }

    @Test
    fun `an amount with decimals opens unchanged and can be saved on a comma locale`() {
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("lt"))
        try {
            sut.openEditor(FD(id = 7, name = "Meal", portion = 2.25, unit = "pieces", carbs = 50))
            assertThat(sut.uiState.value.editorPortion).isEqualTo("2.25")
            assertThat(sut.uiState.value.editorCanSave).isTrue()
        } finally {
            Locale.setDefault(locale)
        }
    }

    @Test
    fun `a new amount or unit keeps the nutrients and clears only a fixed eCarbs amount`() {
        val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3, manualGrams = 5.0)
        sut.openEditor(FD(id = 7, name = "Meal", portion = 2.0, unit = "pieces", carbs = 50, protein = 20, fat = 10, eCarbsRule = rule))

        sut.updateEditorPortion("3")
        assertThat(sut.uiState.value.editorCarbs).isEqualTo("50")
        assertThat(sut.uiState.value.editorECarbsRule.manualGrams).isNull()
        assertThat(sut.uiState.value.editorECarbsRule.method).isEqualTo(FoodECarbsMethod.FPU)

        sut.updateEditorECarbsRule(rule)
        sut.updateEditorUnit("slices")
        assertThat(sut.uiState.value.editorECarbsRule.manualGrams).isNull()
    }

    @Test
    fun `no nutrient may weigh more than a food in grams or kg`() {
        sut.openEditor(FD(id = 7, name = "Slice", portion = 25.0, carbs = 20, protein = 2, fat = 1))
        assertThat(sut.uiState.value.editorCanSave).isTrue()
        sut.updateEditorCarbs("30")
        assertThat(sut.uiState.value.editorCanSave).isFalse()
        sut.updateEditorUnit("kg")
        assertThat(sut.uiState.value.editorCanSave).isTrue()
        sut.updateEditorUnit("pieces")
        sut.updateEditorCarbs("300")
        assertThat(sut.uiState.value.editorCanSave).isTrue()
    }

    @Test
    fun `a usual amount in grams is saved as entered, with the amount not asked every time`() = runTest {
        whenever(persistenceLayer.insertOrUpdateFood(any())).thenReturn(PersistenceLayer.TransactionResult())
        sut.openEditor()
        sut.updateEditorAskAmount(false)
        sut.updateEditorName("Slice")
        sut.updateEditorPortion("30")
        sut.updateEditorUnit("g")
        sut.updateEditorCarbs("15")
        sut.updateEditorProtein("12")
        sut.updateEditorFat("5")
        sut.updateEditorECarbsEnabled(true)
        sut.updateEditorECarbsRule(FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        assertThat(sut.uiState.value.editorCanSave).isTrue()
        sut.saveFood()
        advanceUntilIdle()
        val saved = argumentCaptor<FD>()
        verify(persistenceLayer).insertOrUpdateFood(saved.capture())
        assertThat(saved.firstValue.askAmount).isFalse()
        assertThat(saved.firstValue.portion).isEqualTo(30.0)
        assertThat(saved.firstValue.unit).isEqualTo("g")
        assertThat(saved.firstValue.eCarbsRule?.method).isEqualTo(FoodECarbsMethod.FPU)
    }

    @Test
    fun `saving a food in pieces keeps its unit and carbs`() = runTest {
        val food = FD(id = 7, name = "Meal", portion = 2.0, unit = "pieces", carbs = 150, protein = 20, fat = 10)
        whenever(persistenceLayer.getFoods()).thenReturn(listOf(food))
        whenever(persistenceLayer.insertOrUpdateFood(any())).thenReturn(PersistenceLayer.TransactionResult())
        sut.openEditor(food)
        sut.saveFood()
        advanceUntilIdle()
        val saved = argumentCaptor<FD>()
        verify(persistenceLayer).insertOrUpdateFood(saved.capture())
        assertThat(saved.firstValue.portion).isEqualTo(2.0)
        assertThat(saved.firstValue.unit).isEqualTo("pieces")
        assertThat(saved.firstValue.carbs).isEqualTo(150)
    }

    @Test
    fun `disabling a saved rule removes it and unrelated edits keep it`() = runTest {
        val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3)
        val food = FD(id = 7, name = "Meal", portion = 100.0, carbs = 25, fat = 5, protein = 10, eCarbsRule = rule)
        whenever(persistenceLayer.getFoods()).thenReturn(listOf(food))
        whenever(persistenceLayer.insertOrUpdateFood(any())).thenReturn(PersistenceLayer.TransactionResult())
        sut.openEditor(food)
        sut.updateEditorName("Renamed meal")
        sut.saveFood()
        advanceUntilIdle()
        sut.openEditor(food)
        sut.updateEditorECarbsEnabled(false)
        sut.saveFood()
        advanceUntilIdle()
        val saved = argumentCaptor<FD>()
        verify(persistenceLayer, times(2)).insertOrUpdateFood(saved.capture())
        assertThat(saved.allValues[0].eCarbsRule).isEqualTo(rule)
        assertThat(saved.allValues[1].eCarbsRule).isNull()
    }

    @Test
    fun `saving a new food stores its nutrients and fixed amount under the assigned ID`() = runTest {
        whenever(persistenceLayer.getFoods()).thenReturn(emptyList())
        val inserted = FD(id = 19, name = "Meal", portion = 100.0, carbs = 25, fat = 5, protein = 10)
        val result = PersistenceLayer.TransactionResult<FD>().apply { this.inserted.add(inserted) }
        whenever(persistenceLayer.insertOrUpdateFood(any())).thenReturn(result)
        sut.openEditor()
        sut.updateEditorName("Meal")
        sut.updateEditorCarbs("25")
        sut.updateEditorFat("5")
        sut.updateEditorProtein("10")
        val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3, manualGrams = 7.5)
        sut.updateEditorECarbsRule(rule)
        sut.updateEditorECarbsEnabled(true)
        sut.saveFood()
        advanceUntilIdle()
        val saved = argumentCaptor<FD>()
        verify(persistenceLayer).insertOrUpdateFood(saved.capture())
        assertThat(saved.firstValue.portion).isEqualTo(100.0)
        assertThat(saved.firstValue.unit).isEqualTo("g")
        assertThat(saved.firstValue.carbs).isEqualTo(25)
        assertThat(saved.firstValue.eCarbsRule).isEqualTo(rule)
        assertThat(sut.uiState.value.showEditor).isFalse()
        verify(preferences).put(StringNonKey.FoodLastECarbsRule, rule.copy(manualGrams = null).toJsonString())
    }

    @Test
    fun `favourites are saved and the favourites filter shows only them`() = runTest {
        val apple = FD(id = 1, name = "Apple", portion = 100.0, carbs = 12)
        val bread = FD(id = 2, name = "Bread", portion = 100.0, carbs = 50)
        whenever(persistenceLayer.getFoods()).thenReturn(listOf(apple, bread))
        sut = FoodManagementViewModel(persistenceLayer, aapsLogger, preferences)
        backgroundScope.launch { sut.filteredFoods.collect {} }
        advanceUntilIdle()

        sut.toggleFavourite(bread)
        sut.setShowFavouritesOnly(true)
        advanceUntilIdle()

        verify(preferences).put(StringNonKey.FoodFavourites, "2")
        verify(preferences).put(BooleanNonKey.FoodShowFavouritesOnly, true)
        assertThat(sut.filteredFoods.value).containsExactly(bread)

        sut.toggleFavourite(bread)
        advanceUntilIdle()
        verify(preferences).put(StringNonKey.FoodFavourites, "")
        assertThat(sut.filteredFoods.value).isEmpty()
    }

    @Test
    fun `the editor star marks a new food as favourite when it is saved`() = runTest {
        whenever(persistenceLayer.getFoods()).thenReturn(emptyList())
        val inserted = FD(id = 19, name = "Soup", portion = 100.0, carbs = 5)
        whenever(persistenceLayer.insertOrUpdateFood(any())).thenReturn(PersistenceLayer.TransactionResult<FD>().apply { this.inserted.add(inserted) })
        sut.openEditor()
        sut.updateEditorName("Soup")
        sut.updateEditorCarbs("5")
        sut.updateEditorFavourite(true)
        verify(preferences, never()).put(eq(StringNonKey.FoodFavourites), any<String>())

        sut.saveFood()
        advanceUntilIdle()

        verify(preferences).put(StringNonKey.FoodFavourites, "19")
        assertThat(sut.uiState.value.favouriteIds).containsExactly(19L)
    }

    @Test
    fun `the editor star shows and removes an existing favourite, and Cancel keeps it`() = runTest {
        whenever(preferences.get(StringNonKey.FoodFavourites)).thenReturn("7")
        sut = FoodManagementViewModel(persistenceLayer, aapsLogger, preferences)
        val food = FD(id = 7, name = "Meal", portion = 100.0, carbs = 25)
        whenever(persistenceLayer.getFoods()).thenReturn(listOf(food))
        whenever(persistenceLayer.insertOrUpdateFood(any())).thenReturn(PersistenceLayer.TransactionResult())

        sut.openEditor(food)
        assertThat(sut.uiState.value.editorFavourite).isTrue()
        sut.updateEditorFavourite(false)
        sut.closeEditor()
        assertThat(sut.uiState.value.favouriteIds).containsExactly(7L)

        sut.openEditor(food)
        sut.updateEditorFavourite(false)
        sut.saveFood()
        advanceUntilIdle()
        verify(preferences).put(StringNonKey.FoodFavourites, "")
        assertThat(sut.uiState.value.favouriteIds).isEmpty()
    }

    @Test
    fun `saved favourites and the filter choice are read on start`() {
        whenever(preferences.get(StringNonKey.FoodFavourites)).thenReturn("3, 7,bad")
        whenever(preferences.get(BooleanNonKey.FoodShowFavouritesOnly)).thenReturn(true)
        sut = FoodManagementViewModel(persistenceLayer, aapsLogger, preferences)
        assertThat(sut.uiState.value.favouriteIds).containsExactly(3L, 7L)
        assertThat(sut.uiState.value.showFavouritesOnly).isTrue()
    }

    @Test
    fun `a food without a formula starts from the last saved formula but leaves it off`() {
        val last = FoodECarbsRule(method = FoodECarbsMethod.MODIFIED_FPU, delayMinutes = 60, durationHours = 4, carbsNowPercent = 80)
        whenever(preferences.get(StringNonKey.FoodLastECarbsRule)).thenReturn(last.toJsonString())
        sut.openEditor(FD(id = 3, name = "Bread", portion = 100.0, carbs = 50, fat = 2, protein = 8))
        assertThat(sut.uiState.value.editorECarbsRule).isEqualTo(last)
        assertThat(sut.uiState.value.editorUseECarbs).isFalse()
    }

    @Test
    fun `a food with its own formula keeps it`() {
        val own = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3)
        val last = FoodECarbsRule(method = FoodECarbsMethod.MODIFIED_FPU, durationHours = 4)
        whenever(preferences.get(StringNonKey.FoodLastECarbsRule)).thenReturn(last.toJsonString())
        sut.openEditor(FD(id = 3, name = "Meal", portion = 100.0, carbs = 25, fat = 5, protein = 10, eCarbsRule = own))
        assertThat(sut.uiState.value.editorECarbsRule).isEqualTo(own)
    }

    @Test
    fun `a fixed amount saves without protein and fat`() {
        sut.openEditor()
        sut.updateEditorName("Sauce")
        sut.updateEditorCarbs("10")
        sut.updateEditorECarbsRule(FoodECarbsRule(durationHours = 3, manualGrams = 5.0))
        sut.updateEditorECarbsEnabled(true)
        assertThat(sut.uiState.value.editorCanSave).isTrue()
        sut.updateEditorECarbsRule(FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        assertThat(sut.uiState.value.editorCanSave).isFalse()
        sut.updateEditorFat("5")
        assertThat(sut.uiState.value.editorCanSave).isTrue()
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
    fun `changing protein or fat clears the fixed amount`() {
        sut.updateEditorECarbsRule(FoodECarbsRule(durationHours = 3, manualGrams = 5.0))
        sut.updateEditorProtein("20")
        assertThat(sut.uiState.value.editorECarbsRule.manualGrams).isNull()
    }
}
