package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.clientcontrol.FailureReason
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class FoodManagementViewModelTest {

    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var config: Config

    private val dispatcher = FakeDispatcher()
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var sut: FoodManagementViewModel

    /** On a client it records what is sent and answers [clientAnswer]; on a master it runs the local part, like the real one. */
    private inner class FakeDispatcher : ClientControlActionDispatcher {

        val sent = mutableListOf<ClientControlActionDispatcher.Command>()
        var clientAnswer: ActionProgress = ActionProgress.Applied

        override fun dispatch(command: ClientControlActionDispatcher.Command): Flow<ActionProgress> = emptyFlow()
        override suspend fun run(command: ClientControlActionDispatcher.Command, label: String): ActionProgress = clientAnswer
        override suspend fun execute(command: ClientControlActionDispatcher.Command, label: String, localExecute: suspend () -> ActionProgress): ActionProgress =
            if (config.AAPSCLIENT) clientAnswer.also { sent += command } else localExecute()
    }

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} loadData() launch (no advanceUntilIdle), so construction
        // stays clean and we test the synchronous update methods against the default state. The observeChanges()
        // cold flow is still built synchronously during init, so it must be stubbed non-null.
        Dispatchers.setMain(testDispatcher)
        whenever(persistenceLayer.observeChanges(FD::class)).thenReturn(emptyFlow())
        sut = FoodManagementViewModel(persistenceLayer, dispatcher, config, generatedTextResolver(), aapsLogger)
    }

    private val apple = FD(id = 7, ids = IDs(nightscoutId = "ns-apple"), name = "Apple", portion = 100.0, carbs = 12)

    private fun asClient() = whenever(config.AAPSCLIENT).thenReturn(true)

    private fun typeNewFood() {
        sut.openEditor(null)
        sut.updateEditorName("Pear")
        sut.updateEditorCarbs("20")
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
        sut.updateEditorPortion("120")
        sut.updateEditorUnit("g")
        sut.updateEditorCarbs("27")
        sut.updateEditorFat("0")
        sut.updateEditorProtein("1")
        sut.updateEditorEnergy("105")

        val state = sut.uiState.value
        assertThat(state.editorName).isEqualTo("Banana")
        assertThat(state.editorCategory).isEqualTo("Fruit")
        assertThat(state.editorSubCategory).isEqualTo("Tropical")
        assertThat(state.editorPortion).isEqualTo("120")
        assertThat(state.editorUnit).isEqualTo("g")
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
        assertThat(state.editorUnit).isEqualTo("g")
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
    fun `the master stores a saved food itself`() = runTest(testDispatcher) {
        typeNewFood()
        sut.saveFood()
        advanceUntilIdle()

        verify(persistenceLayer).insertOrUpdateFood(argThat { name == "Pear" && carbs == 20 })
        assertThat(sut.uiState.value.showEditor).isFalse()
    }

    /** Stored here too, it would come back from the master as a second food: only the Nightscout id tells them apart. */
    @Test
    fun `a client sends a saved food to the master and stores nothing`() = runTest(testDispatcher) {
        asClient()
        typeNewFood()
        sut.saveFood()
        advanceUntilIdle()

        val sent = dispatcher.sent.single() as ClientControlActionDispatcher.Command.FoodChange
        assertThat(sent.food.name).isEqualTo("Pear")
        assertThat(sent.food.ids.nightscoutId).isNull()
        verify(persistenceLayer, never()).insertOrUpdateFood(any())
        assertThat(sut.uiState.value.showEditor).isFalse()
    }

    /** The master did not take it: the editor stays open, so the input is not lost, and a new try is the same request. */
    @Test
    fun `a client keeps the editor open and the request id when the master did not take it`() = runTest(testDispatcher) {
        asClient()
        dispatcher.clientAnswer = ActionProgress.Unconfirmed(FailureReason.NoReply)
        typeNewFood()
        sut.saveFood()
        advanceUntilIdle()
        sut.saveFood()
        advanceUntilIdle()

        assertThat(sut.uiState.value.showEditor).isTrue()
        val ids = dispatcher.sent.map { (it as ClientControlActionDispatcher.Command.FoodChange).requestId }
        assertThat(ids).hasSize(2)
        assertThat(ids.distinct()).hasSize(1)
    }

    @Test
    fun `each edit gets its own request id`() = runTest(testDispatcher) {
        asClient()
        typeNewFood()
        sut.saveFood()
        advanceUntilIdle()
        typeNewFood()
        sut.saveFood()
        advanceUntilIdle()

        assertThat(dispatcher.sent.map { (it as ClientControlActionDispatcher.Command.FoodChange).requestId }.distinct()).hasSize(2)
    }

    @Test
    fun `a client sends a delete to the master and offers undo`() = runTest(testDispatcher) {
        asClient()
        sut.deleteFood(apple)
        advanceUntilIdle()

        val sent = dispatcher.sent.single() as ClientControlActionDispatcher.Command.FoodChange
        assertThat(sent.food.isValid).isFalse()
        assertThat(sent.food.ids.nightscoutId).isEqualTo("ns-apple")
        verify(persistenceLayer, never()).invalidateFood(any(), any(), any())
        assertThat(sut.uiState.value.undoFood).isEqualTo(apple)
    }

    @Test
    fun `a delete the master did not take offers no undo`() = runTest(testDispatcher) {
        asClient()
        dispatcher.clientAnswer = ActionProgress.Rejected(FailureReason.NotReachable)
        sut.deleteFood(apple)
        advanceUntilIdle()

        assertThat(sut.uiState.value.undoFood).isNull()
    }

    /** A food made on this client by an older build is not in Nightscout: the master cannot find it, so it is deleted here. */
    @Test
    fun `a client deletes its own food without a Nightscout id here`() = runTest(testDispatcher) {
        asClient()
        sut.deleteFood(apple.copy(ids = IDs()))
        advanceUntilIdle()

        assertThat(dispatcher.sent).isEmpty()
        verify(persistenceLayer).invalidateFood(7L, Action.FOOD_REMOVED, Sources.Food)
    }

    /** The master adds it as a new food, which comes back with a Nightscout id: the old copy here would be a second one. */
    @Test
    fun `a client sends its own food without a Nightscout id as a new one and drops its copy`() = runTest(testDispatcher) {
        asClient()
        sut.openEditor(apple.copy(ids = IDs()))
        sut.saveFood()
        advanceUntilIdle()

        val sent = dispatcher.sent.single() as ClientControlActionDispatcher.Command.FoodChange
        assertThat(sent.food.ids.nightscoutId).isNull()
        verify(persistenceLayer).invalidateFood(7L, Action.FOOD_REMOVED, Sources.Food)
    }
}
