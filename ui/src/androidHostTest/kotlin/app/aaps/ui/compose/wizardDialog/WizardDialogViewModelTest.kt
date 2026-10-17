package app.aaps.ui.compose.wizardDialog

import androidx.lifecycle.SavedStateHandle
import app.aaps.core.data.model.BolusWizardData
import app.aaps.core.data.model.CarbsType
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bolus.WizardExecutor
import app.aaps.core.interfaces.clientcontrol.ActionProgress
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.wizard.BolusWizard
import app.aaps.ui.UiStrings
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.mockingDetails
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class WizardDialogViewModelTest {

    @Mock private lateinit var constraintChecker: ConstraintsChecker
    @Mock private lateinit var profileFunction: ProfileFunction
    @Mock private lateinit var profileUtil: ProfileUtil
    @Mock private lateinit var profileRepository: ProfileRepository
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var ch: ConcentrationHelper
    @Mock private lateinit var iobCobCalculator: IobCobCalculator
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    @Mock private lateinit var preferences: Preferences
    @Mock private lateinit var config: Config
    @Mock private lateinit var rh: ResourceHelper
    @Mock private lateinit var dateUtil: DateUtil
    @Mock private lateinit var decimalFormatter: DecimalFormatter
    @Mock private lateinit var aapsLogger: AAPSLogger
    @Mock private lateinit var runningModeGuard: RunningModeGuard
    @Mock private lateinit var automation: Automation
    @Mock private lateinit var wizardExecutor: WizardExecutor
    @Mock private lateinit var rxBus: RxBus
    @Mock private lateinit var commandQueue: CommandQueue

    private val smbBeingDelivered = MutableStateFlow(false)
    private val bolusWizardProvider: () -> BolusWizard = mock()

    private lateinit var sut: WizardDialogViewModel
    private lateinit var savedStateHandle: SavedStateHandle

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // init { viewModelScope.launch { initialize() } } is deferred by StandardTestDispatcher, so construction
        // touches no collaborators and the internal BolusWizard stays null; the pure state flips below don't need it.
        // The scheduler is shared with runTest below, so the first advanceUntilIdle() in a test body runs
        // initialize() before the refresh queued after it, and drives everything the view model launches.
        // Stubbed before construction: init { } queues initialize() on the shared scheduler, so it runs
        // before any test body. With no profile store it returns straight away, which is all these tests need.
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(null))
        whenever(commandQueue.smbBeingDelivered).thenReturn(smbBeingDelivered)
        whenever(rxBus.toFlow(EventAutosensCalculationFinished::class)).thenReturn(MutableSharedFlow())
        Dispatchers.setMain(mainDispatcher)
        savedStateHandle = SavedStateHandle()
        sut = createViewModel()
    }

    private fun createViewModel() = WizardDialogViewModel(
            savedStateHandle, bolusWizardProvider, constraintChecker, profileFunction, profileUtil,
            profileRepository, activePlugin, ch, iobCobCalculator, persistenceLayer, preferences, config,
            rh, dateUtil, decimalFormatter, aapsLogger, runningModeGuard, automation, wizardExecutor, rxBus,
            CoroutineScope(UnconfinedTestDispatcher()), commandQueue
        )

    // The BG field is sent to the master with the other inputs. Filled once when the wizard opened, it dosed on a BG
    // from minutes ago when the user came back to the wizard later.
    @Test
    fun `the BG field follows new readings until the user edits it`() {
        val opened = WizardDialogUiState(bg = 6.1, bgAgeMinutes = 4, hasBgData = true)

        val followed = opened.withNewReading(bg = 7.3, ageMinutes = 0)
        assertThat(followed.bg).isEqualTo(7.3)
        assertThat(followed.bgAgeMinutes).isEqualTo(0)

        val edited = opened.copy(bg = 5.0, bgEdited = true)
        assertThat(edited.withNewReading(bg = 7.3, ageMinutes = 0)).isEqualTo(edited)
    }

    @Test
    fun `only a changed BG counts as edited`() {
        val shown = sut.uiState.value.bg
        sut.updateBg(shown)
        assertThat(sut.uiState.value.bgEdited).isFalse()

        sut.updateBg(shown + 1.0)
        assertThat(sut.uiState.value.bgEdited).isTrue()
    }

    // #5381: the master calculates the dose only after the SMB has finished, with the SMB in IOB.
    @Test
    fun `OK waits while an SMB is being delivered`() {
        mainDispatcher.scheduler.advanceUntilIdle()
        smbBeingDelivered.value = true
        mainDispatcher.scheduler.advanceUntilIdle()
        assertThat(sut.uiState.value.smbBeingDelivered).isTrue()

        val withInsulin = sut.uiState.value.copy(totalInsulin = 1.5)
        assertThat(withInsulin.waitsForSmb).isTrue()
        assertThat(withInsulin.copy(forcedRecordOnly = true).waitsForSmb).isFalse()
        assertThat(withInsulin.copy(totalInsulin = 0.0).waitsForSmb).isFalse()

        smbBeingDelivered.value = false
        mainDispatcher.scheduler.advanceUntilIdle()
        assertThat(sut.uiState.value.smbBeingDelivered).isFalse()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /**
     * #5415: the confirmation and the record-only path read the alarm and the note from the wizard of the last
     * calculation. Changed as the last step, they did not reach it: the alarm rang although it was turned off,
     * or did not ring although it was turned on, and the note was not saved.
     */
    @Test
    fun `a changed alarm or note reaches the wizard`() {
        val wizards = givenWizardsThatCalculate()

        sut.updateCarbs(40)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertThat(calculation(wizards.last())[USE_ALARM]).isEqualTo(false)

        sut.toggleAlarm(true)
        mainDispatcher.scheduler.advanceUntilIdle()
        assertThat(calculation(wizards.last())[USE_ALARM]).isEqualTo(true)

        sut.updateNotes("pen")
        mainDispatcher.scheduler.advanceUntilIdle()
        assertThat(calculation(wizards.last())[NOTES]).isEqualTo("pen")
    }

    /**
     * #5415, seen on a phone: a note typed fast starts a calculation for every letter, and they can end in another
     * order. "test5415" was saved as "test54", because an older calculation ended last and its wizard was kept.
     */
    @Test
    fun `an older calculation that ends last does not replace a newer one`() {
        val wizards = givenWizardsThatCalculate()
        var first = true
        persistenceLayer.stub {
            onBlocking { getTemporaryTargetActiveAt(any()) } doSuspendableAnswer {
                if (first) {
                    first = false
                    delay(1_000)
                }
                null
            }
        }

        sut.updateNotes("test54")
        mainDispatcher.scheduler.runCurrent() // its calculation starts and waits for the slow read
        sut.updateNotes("test5415")
        mainDispatcher.scheduler.advanceUntilIdle() // the newer one ends first, then the older one

        sut.getConfirmationSummary()
        val used = wizards.single { wizard -> mockingDetails(wizard).invocations.any { it.method.name == "buildConfirmationLines" } }
        assertThat(calculation(used)[NOTES]).isEqualTo("test5415")
    }

    /** Every calculation gets its own wizard, whose `doCalc` returns itself. Returns them in the order made. */
    private fun givenWizardsThatCalculate(): List<BolusWizard> {
        // Only after initialize() has run with no profile store, so it does not need everything a real start does
        mainDispatcher.scheduler.advanceUntilIdle()
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(mock<ProfileStore>()))
        profileFunction.stub {
            onBlocking { getProfile() } doReturn mock<EffectiveProfile>()
            onBlocking { getProfileName() } doReturn "Test"
        }
        whenever(constraintChecker.applyCarbsConstraints(any())).thenAnswer { it.arguments[0] }
        val wizards = mutableListOf<BolusWizard>()
        whenever(bolusWizardProvider.invoke()).thenAnswer {
            mock<BolusWizard>(defaultAnswer = { invocation ->
                if (invocation.method.name == "doCalc") invocation.mock else RETURNS_DEFAULTS.answer(invocation)
            }).also { wizard ->
                whenever(wizard.data).thenReturn(mock<BolusWizardData>())
                wizards += wizard
            }
        }
        return wizards
    }

    /** The arguments of the `doCalc` of [wizard]. */
    private fun calculation(wizard: BolusWizard): Array<Any?> =
        mockingDetails(wizard).invocations.single { it.method.name == "doCalc" }.arguments

    @Test
    fun `updateNotes and toggleAlarm update the state`() {
        sut.updateNotes("wizard note")
        sut.toggleAlarm(true)

        assertThat(sut.uiState.value.notes).isEqualTo("wizard note")
        assertThat(sut.uiState.value.alarmChecked).isTrue()
    }

    // #5327: eating later turns the "time to eat" alarm on, as in 3.x. A manual untick is kept while the minutes change.
    @Test
    fun `eating later ticks the alarm, and an untick stays while the time changes`() {
        assertThat(sut.uiState.value.alarmChecked).isFalse()

        sut.updateCarbTime(15)
        assertThat(sut.uiState.value.alarmChecked).isTrue()

        sut.toggleAlarm(false)
        sut.updateCarbTime(20)
        assertThat(sut.uiState.value.alarmChecked).isFalse()

        // Back to now and later again: a new decision, so it is ticked again.
        sut.updateCarbTime(0)
        sut.updateCarbTime(10)
        assertThat(sut.uiState.value.alarmChecked).isTrue()
    }

    @Test
    fun `eating earlier does not tick the alarm`() {
        sut.updateCarbTime(-15)

        assertThat(sut.uiState.value.alarmChecked).isFalse()
    }

    private suspend fun stubFoodInitialization(food: FD, rule: FoodECarbsRule? = null) {
        savedStateHandle["foodId"] = food.id.toString()
        whenever(persistenceLayer.getFoods()).thenReturn(listOf(food.copy(eCarbsRule = rule ?: food.eCarbsRule)))
        val store = mock<ProfileStore>()
        whenever(store.getProfileList()).thenReturn(arrayListOf<CharSequence>("Test"))
        whenever(profileRepository.profile).thenReturn(MutableStateFlow(store))
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
        whenever(constraintChecker.getMaxCarbsAllowed()).thenReturn(ConstraintObject(100, aapsLogger))
        whenever(constraintChecker.getMaxBolusAllowed()).thenReturn(ConstraintObject(10.0, aapsLogger))
        val pump = mock<PumpWithConcentration>()
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(pump.pumpDescription).thenReturn(PumpDescription())
        whenever(iobCobCalculator.ads).thenReturn(mock<AutosensDataStore>())
        whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(NOW))
        whenever(iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended()).thenReturn(IobTotal(NOW))
        whenever(preferences.get(IntKey.OverviewBolusPercentage)).thenReturn(100)
        whenever(rh.gs(CoreUiStrings.active)).thenReturn("Active")
        // Created after the stubs, so init sees the food and the profile store
        sut = createViewModel()
    }

    @Test
    fun `food navigation applies a saved rule and waits for the amount eaten`() = runTest(mainDispatcher.scheduler) {
        val meal = FD(id = 7, name = "Pizza & salad? #1", portion = 200.0, carbs = 50, protein = 20, fat = 10)
        val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)
        stubFoodInitialization(meal, rule)
        advanceUntilIdle()
        val state = sut.uiState.value
        assertThat(state.notes).isEqualTo(meal.name)
        assertThat(state.carbs).isEqualTo(0)
        assertThat(state.foodECarbs!!.rule).isEqualTo(rule)
        assertThat(state.foodECarbs!!.enabled).isTrue()
        assertThat(state.eCarbs).isEqualTo(0)
        assertThat(state.foodInputsValid).isFalse()
        sut.updateFoodAmount(300.0)
        assertThat(sut.uiState.value.carbs).isEqualTo(75)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(26)
    }

    @Test
    fun `a usual amount starts at the saved amount and scales carbs and eCarbs by the amount eaten`() = runTest(mainDispatcher.scheduler) {
        val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3)
        stubFoodInitialization(FD(id = 7, name = "Two slices", portion = 2.0, unit = "pieces", carbs = 50, protein = 20, fat = 10, askAmount = false), rule)
        advanceUntilIdle()
        val food = sut.uiState.value.foodECarbs!!
        assertThat(food.amount).isEqualTo(2.0)
        assertThat(food.enabled).isTrue()
        assertThat(sut.uiState.value.carbs).isEqualTo(50)
        assertThat(sut.uiState.value.notes).isEqualTo("Two slices")
        assertThat(sut.uiState.value.foodInputsValid).isTrue()

        // 4 pieces is twice the saved 2: (4 × 40 + 9 × 20) / 10 = 34
        sut.updateFoodAmount(4.0)
        assertThat(sut.uiState.value.carbs).isEqualTo(100)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(34)
    }

    @Test
    fun `serving and method changes clear a one meal override and keep all macros in scale`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10), FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        advanceUntilIdle()
        sut.updateFoodAmount(200.0)
        sut.updateFoodECarbsGrams(5)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(5)
        sut.updateFoodAmount(300.0)
        assertThat(sut.uiState.value.carbs).isEqualTo(75)
        assertThat(sut.uiState.value.foodECarbs!!.proteinGrams).isEqualTo(30.0)
        assertThat(sut.uiState.value.foodECarbs!!.fatGrams).isEqualTo(15.0)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(26)
        sut.updateFoodECarbsGrams(5)
        sut.updateFoodECarbsRule(FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 90, durationHours = 4))
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(5)
        assertThat(sut.uiState.value.foodECarbs!!.overrideGrams).isEqualTo(5)
        sut.updateFoodECarbsRule(FoodECarbsRule(method = FoodECarbsMethod.MODIFIED_FPU, durationHours = 3))
        assertThat(sut.uiState.value.foodECarbs!!.overrideGrams).isNull()
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(13)
    }

    @Test
    fun `changing the carbs counted now for one meal keeps a hand eCarbs amount`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10), FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        advanceUntilIdle()
        sut.updateFoodAmount(200.0)
        sut.updateFoodECarbsGrams(5)
        sut.updateFoodECarbsRule(sut.uiState.value.foodECarbs!!.rule.copy(carbsNowPercent = 80))
        assertThat(sut.uiState.value.foodECarbs!!.overrideGrams).isEqualTo(5)
        // 80 % of 50 g is 40 g now; the other 10 g join the 5 g set by hand
        assertThat(sut.uiState.value.carbPlan()).isEqualTo(WizardCarbPlan(40, 15, 0, 3))
    }

    @Test
    fun `switching between food mode and the presets never combines both calculations`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 60, protein = 20, fat = 10), FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        advanceUntilIdle()
        sut.updateFoodAmount(200.0)
        sut.updateCarbsType(CarbsType.PIZZA)
        assertThat(sut.uiState.value.carbPlan().carbs).isEqualTo(48)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(21)
        sut.toggleFoodECarbs(true)
        assertThat(sut.uiState.value.carbPlan().carbs).isEqualTo(60)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(17)
        sut.updateCarbsType(CarbsType.CAKE)
        assertThat(sut.uiState.value.foodECarbs!!.enabled).isFalse()
        assertThat(sut.uiState.value.carbPlan().carbs).isEqualTo(54)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(12)
    }

    @Test
    fun `missing protein fat cannot enable food mode`() = runTest(mainDispatcher.scheduler) {
        val rule = FoodECarbsRule(method = FoodECarbsMethod.CUSTOM, proteinPercent = 50, durationHours = 3)
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = null, fat = 10), rule)
        advanceUntilIdle()
        assertThat(sut.uiState.value.foodECarbs!!.rule.isValid).isTrue()
        sut.toggleFoodECarbs(true)
        assertThat(sut.uiState.value.foodECarbs!!.enabled).isFalse()
    }

    @Test
    fun `food meal submission sends scaled carbs and eCarbs with carb time added once`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(
            FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10),
            FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)
        )
        whenever(profileFunction.getProfile()).thenReturn(mock<EffectiveProfile>())
        whenever(profileFunction.getProfileName()).thenReturn("Test")
        whenever(constraintChecker.applyCarbsConstraints(any())).thenAnswer { it.arguments[0] }
        val wizard = mock<BolusWizard>()
        val result = mock<BolusWizardData>()
        whenever(result.calculatedTotalInsulin).thenReturn(1.0)
        whenever(wizard.data).thenReturn(result)
        whenever(bolusWizardProvider.invoke()).thenReturn(wizard)
        whenever(wizard.doCalc(any(), any(), anyOrNull(), any(), any(), any(), any(), any(), any(), any(),
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(wizard)
        whenever(rh.gs(CoreUiStrings.clientcontrol_action_deliver_bolus)).thenReturn("Deliver bolus")
        whenever(wizardExecutor.prepare(any(), any())).thenReturn(ActionProgress.Sending)
        whenever(rh.gs(UiStrings.food_ecarbs_fpu)).thenReturn("FPU")
        whenever(rh.gs(eq(UiStrings.food_history_note_ecarbs), anyVararg())).thenReturn("Meal summary")
        whenever(rh.gs(UiStrings.food_history_ecarbs_label)).thenReturn("eCarbs")
        advanceUntilIdle()
        sut.updateCarbsType(CarbsType.PIZZA)
        sut.updateFoodAmount(300.0)
        advanceUntilIdle()
        // Pizza: only the split goes in the note, not the name of the choice
        verify(rh, atLeastOnce()).gs(UiStrings.food_history_note_ecarbs, "Meal", "300", "g", "eCarbs", 80, 75, 26, 60, 3)
        sut.toggleFoodECarbs(true)
        sut.updateCarbTime(15)
        advanceUntilIdle()
        sut.deliverManualWizard()
        advanceUntilIdle()

        val source = argumentCaptor<WizardExecutor.WizardSource>()
        verify(wizardExecutor).prepare(source.capture(), any())
        val inputs = (source.firstValue as WizardExecutor.WizardSource.Manual).inputs
        assertThat(inputs.carbs).isEqualTo(75)
        assertThat(inputs.eCarbsGrams).isEqualTo(26)
        assertThat(inputs.carbTime).isEqualTo(15)
        assertThat(inputs.eCarbsDelayMinutes).isEqualTo(75)
        assertThat(inputs.eCarbsDurationHours).isEqualTo(3)
        verify(rh, atLeastOnce()).gs(UiStrings.food_history_note_ecarbs, "Meal", "300", "g", "FPU", 100, 75, 26, 60, 3)
        assertThat(inputs.notes).isEqualTo("Meal summary")
    }

    @Test
    fun `wizard meal edits never overwrite the stored food rule`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10), FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        advanceUntilIdle()
        sut.updateFoodAmount(300.0)
        sut.updateFoodECarbsGrams(5)
        verify(persistenceLayer, never()).insertOrUpdateFood(any())
    }

    @Test
    fun `deleted food is reported and cannot be submitted`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Deleted meal", portion = 200.0, carbs = 50, isValid = false))
        advanceUntilIdle()
        assertThat(sut.uiState.value.foodUnavailable).isTrue()
        assertThat(sut.uiState.value.foodInputsValid).isFalse()
        assertThat(sut.hasAction()).isFalse()
    }

    /**
     * `initialize()` and `recalculateSuspend()` both return as soon as there is no profile store, so
     * these tests reach the percentage decision without standing up the whole wizard.
     */
    private fun stubForRefresh(storedPercentage: Int) {
        whenever(preferences.get(BooleanNonKey.WizardIncludeTrend)).thenReturn(false)
        whenever(preferences.get(BooleanNonKey.WizardIncludeCob)).thenReturn(false)
        whenever(preferences.get(BooleanKey.OverviewUseBolusAdvisor)).thenReturn(false)
        whenever(preferences.get(IntKey.OverviewBolusPercentage)).thenReturn(storedPercentage)
        whenever(preferences.get(IntKey.OverviewResetBolusPercentageTime)).thenReturn(30)
        whenever(dateUtil.now()).thenReturn(NOW)
    }

    private fun lastGlucoseValueAt(timestamp: Long) =
        GV(timestamp = timestamp, raw = null, value = 100.0, trendArrow = TrendArrow.FLAT, noise = null, sourceSensor = SourceSensor.UNKNOWN)

    @Test
    fun `dismissing the settings sheet keeps the percentage the user set in the dialog`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 100)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        sut.updatePercentage(150)
        advanceUntilIdle()
        assertThat(sut.uiState.value.percentage).isEqualTo(150)

        // The sheet has no control for the per bolus percentage, so dismissing it must not touch it.
        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(150)
    }

    /**
     * The first read has nothing to compare against, so it only remembers the stored value. Without
     * this, a dismissal before the dialog finished starting up would apply the stored percentage over
     * whatever the slider held.
     */
    @Test
    fun `the first read of the stored percentage does not touch the slider`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 150)
        val before = sut.uiState.value.percentage

        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(before)
    }

    @Test
    fun `a percentage really changed in the settings sheet is applied`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 100)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        whenever(preferences.get(IntKey.OverviewBolusPercentage)).thenReturn(120)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(lastGlucoseValueAt(NOW))
        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(120)
    }

    @Test
    fun `a percentage above 100 is dropped when the last glucose value is too old`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 100)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        // Older than OverviewResetBolusPercentageTime, so the stored percentage must not be re-applied.
        whenever(preferences.get(IntKey.OverviewBolusPercentage)).thenReturn(150)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(lastGlucoseValueAt(NOW - T.mins(45).msecs()))
        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(100)
    }

    @Test
    fun `a percentage above 100 is dropped when there is no glucose value at all`() = runTest(mainDispatcher.scheduler) {
        stubForRefresh(storedPercentage = 100)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        whenever(preferences.get(IntKey.OverviewBolusPercentage)).thenReturn(150)
        whenever(persistenceLayer.getLastGlucoseValue()).thenReturn(null)
        sut.refreshAfterSettings()
        advanceUntilIdle()

        assertThat(sut.uiState.value.percentage).isEqualTo(100)
    }

    private companion object {

        const val NOW = 1_700_000_000_000L

        /** Positions of `useAlarm` and `notes` in the parameters of `BolusWizard.doCalc`. */
        const val USE_ALARM = 15
        const val NOTES = 16
    }
}
