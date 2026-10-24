package app.aaps.ui.compose.wizardDialog

import androidx.lifecycle.SavedStateHandle
import app.aaps.core.data.iob.CobInfo
import app.aaps.core.data.model.CarbsType
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.data.model.BolusWizardData
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
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
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
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
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
        Dispatchers.setMain(mainDispatcher)
        savedStateHandle = SavedStateHandle()
        sut = createViewModel()
    }

    private fun createViewModel() = WizardDialogViewModel(
            savedStateHandle, bolusWizardProvider, constraintChecker, profileFunction, profileUtil,
            profileRepository, activePlugin, ch, iobCobCalculator, persistenceLayer, preferences, config,
            rh, dateUtil, decimalFormatter, aapsLogger, runningModeGuard, automation, wizardExecutor, rxBus,
            CoroutineScope(UnconfinedTestDispatcher())
        )

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `updateNotes and toggleAlarm update the state`() {
        sut.updateNotes("wizard note")
        sut.toggleAlarm(true)

        assertThat(sut.uiState.value.notes).isEqualTo("wizard note")
        assertThat(sut.uiState.value.alarmChecked).isTrue()
    }

    private suspend fun stubFoodInitialization(food: FD, rule: FoodECarbsRule? = null, otherFoods: List<FD> = emptyList(), cob: Double = 0.0) {
        savedStateHandle["foodId"] = food.id.toString()
        whenever(persistenceLayer.getFoods()).thenReturn(listOf(food.copy(eCarbsRule = rule ?: food.eCarbsRule)) + otherFoods)
        whenever(iobCobCalculator.getCobInfo(any())).thenReturn(CobInfo(NOW, cob, 0.0))
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
        assertThat(state.foods.single().rule).isEqualTo(rule)
        assertThat(state.foods.single().enabled).isTrue()
        assertThat(state.eCarbs).isEqualTo(0)
        assertThat(state.foodInputsValid).isFalse()
        sut.updateFoodAmount(0, 300.0)
        assertThat(sut.uiState.value.carbs).isEqualTo(75)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(26)
    }

    @Test
    fun `a usual amount starts at the saved amount and scales carbs and eCarbs by the amount eaten`() = runTest(mainDispatcher.scheduler) {
        val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3)
        stubFoodInitialization(FD(id = 7, name = "Two slices", portion = 2.0, unit = "pieces", carbs = 50, protein = 20, fat = 10, askAmount = false), rule)
        advanceUntilIdle()
        val food = sut.uiState.value.foods.single()
        assertThat(food.amount).isEqualTo(2.0)
        assertThat(food.enabled).isTrue()
        assertThat(sut.uiState.value.carbs).isEqualTo(50)
        assertThat(sut.uiState.value.notes).isEqualTo("Two slices")
        assertThat(sut.uiState.value.foodInputsValid).isTrue()

        // 4 pieces is twice the saved 2: (4 × 40 + 9 × 20) / 10 = 34
        sut.updateFoodAmount(0, 4.0)
        assertThat(sut.uiState.value.carbs).isEqualTo(100)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(34)
    }

    @Test
    fun `an amount change clears a one meal eCarbs amount and keeps all macros in scale`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10), FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        advanceUntilIdle()
        sut.updateFoodAmount(0, 200.0)
        sut.updateMealECarbsGrams(5)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(5)
        sut.updateFoodAmount(0, 300.0)
        assertThat(sut.uiState.value.carbs).isEqualTo(75)
        assertThat(sut.uiState.value.foods.single().proteinGrams).isEqualTo(30.0)
        assertThat(sut.uiState.value.foods.single().fatGrams).isEqualTo(15.0)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(26)
        // The timing for this meal keeps a hand amount
        sut.updateMealECarbsGrams(5)
        sut.updateMealTiming(90, 4)
        assertThat(sut.uiState.value.carbPlan()).isEqualTo(WizardCarbPlan(75, 5, 90, 4))
        sut.updateMealECarbsGrams(null)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(26)
    }

    @Test
    fun `changing the carbs counted now for one meal keeps a hand eCarbs amount`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10), FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        advanceUntilIdle()
        sut.updateFoodAmount(0, 200.0)
        sut.updateMealECarbsGrams(5)
        sut.updateMealCarbsNow(80)
        assertThat(sut.uiState.value.mealAdjust.eCarbsGrams).isEqualTo(5)
        // 80 % of 50 g is 40 g now; the other 10 g join the 5 g set by hand
        assertThat(sut.uiState.value.carbPlan()).isEqualTo(WizardCarbPlan(40, 15, 0, 3))
    }

    @Test
    fun `foods added to the meal add their carbs and keep carbs typed by hand`() = runTest(mainDispatcher.scheduler) {
        val juice = FD(id = 9, name = "Juice", portion = 200.0, carbs = 20, protein = 0, fat = 0, unit = "ml", askAmount = false)
        stubFoodInitialization(
            FD(id = 7, name = "Pizza", portion = 200.0, carbs = 50, protein = 20, fat = 10, askAmount = false),
            FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3, carbsNowPercent = 80),
            otherFoods = listOf(juice)
        )
        advanceUntilIdle()
        // 5 g of bread typed by hand
        sut.updateCarbs(55)
        // Picked in the food list, which gives the id only
        sut.addFoodById(juice.id)
        advanceUntilIdle()
        assertThat(sut.uiState.value.foods.map { it.food.name }).containsExactly("Pizza", "Juice").inOrder()
        assertThat(sut.uiState.value.carbs).isEqualTo(75)
        assertThat(sut.uiState.value.foodMode).isTrue()
        // A changed amount moves the meal total by the difference
        sut.updateFoodAmount(1, 400.0)
        assertThat(sut.uiState.value.carbs).isEqualTo(95)
        sut.removeFood(0)
        assertThat(sut.uiState.value.carbs).isEqualTo(45)
        assertThat(sut.uiState.value.foodMode).isFalse()
    }

    @Test
    fun `a food added while pizza is chosen keeps pizza`() = runTest(mainDispatcher.scheduler) {
        val stew = FD(id = 10, name = "Stew", portion = 200.0, carbs = 30, protein = 20, fat = 10, askAmount = false,
            eCarbsRule = FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        stubFoodInitialization(FD(id = 7, name = "Bread", portion = 100.0, carbs = 50, askAmount = false), otherFoods = listOf(stew))
        advanceUntilIdle()
        sut.updateCarbsType(CarbsType.PIZZA)
        sut.addFood(stew)
        assertThat(sut.uiState.value.carbsType).isEqualTo(CarbsType.PIZZA)
        assertThat(sut.uiState.value.foodMode).isFalse()
    }

    @Test
    fun `the COB hint shows when COB is off with carbs on board and bolus IOB`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Dessert", portion = 100.0, carbs = 30, askAmount = false), cob = 35.0)
        whenever(iobCobCalculator.calculateIobFromBolus()).thenReturn(IobTotal(NOW).also { it.iob = 2.4 })
        sut = createViewModel()
        advanceUntilIdle()
        assertThat(sut.uiState.value.showCobHint).isTrue()
        sut.toggleCOB(true)
        assertThat(sut.uiState.value.showCobHint).isFalse()
    }

    @Test
    fun `switching between food mode and the presets never combines both calculations`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 60, protein = 20, fat = 10), FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        advanceUntilIdle()
        sut.updateFoodAmount(0, 200.0)
        sut.updateCarbsType(CarbsType.PIZZA)
        assertThat(sut.uiState.value.carbPlan().carbs).isEqualTo(48)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(21)
        sut.toggleFoodECarbs(true)
        assertThat(sut.uiState.value.carbPlan().carbs).isEqualTo(60)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(17)
        sut.updateCarbsType(CarbsType.CAKE)
        assertThat(sut.uiState.value.foods.single().enabled).isFalse()
        assertThat(sut.uiState.value.carbPlan().carbs).isEqualTo(54)
        assertThat(sut.uiState.value.carbPlan().eCarbs).isEqualTo(12)
    }

    @Test
    fun `missing protein fat cannot enable food mode`() = runTest(mainDispatcher.scheduler) {
        val rule = FoodECarbsRule(method = FoodECarbsMethod.CUSTOM, proteinPercent = 50, durationHours = 3)
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = null, fat = 10), rule)
        advanceUntilIdle()
        assertThat(sut.uiState.value.foods.single().rule.isValid).isTrue()
        sut.toggleFoodECarbs(true)
        assertThat(sut.uiState.value.foods.single().enabled).isFalse()
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
        whenever(rh.gs(eq(UiStrings.food_history_note), anyVararg())).thenReturn("Meal: 300 g")
        whenever(rh.gs(eq(UiStrings.food_history_meal_ecarbs), anyVararg())).thenReturn("Meal summary")
        whenever(rh.gs(UiStrings.food_history_ecarbs_label)).thenReturn("eCarbs")
        advanceUntilIdle()
        sut.updateCarbsType(CarbsType.PIZZA)
        sut.updateFoodAmount(0, 300.0)
        advanceUntilIdle()
        // Pizza: only the split goes in the note, not the name of the choice
        verify(rh, atLeastOnce()).gs(UiStrings.food_history_note, "Meal", "300", "g")
        verify(rh, atLeastOnce()).gs(UiStrings.food_history_meal_ecarbs, "Meal: 300 g", "eCarbs", 80, 75, 26, 60, 3)
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
        verify(rh, atLeastOnce()).gs(UiStrings.food_history_meal_ecarbs, "Meal: 300 g", "FPU", 100, 75, 26, 60, 3)
        assertThat(inputs.notes).isEqualTo("Meal summary")
    }

    @Test
    fun `wizard meal edits never overwrite the stored food rule`() = runTest(mainDispatcher.scheduler) {
        stubFoodInitialization(FD(id = 7, name = "Meal", portion = 200.0, carbs = 50, protein = 20, fat = 10), FoodECarbsRule(method = FoodECarbsMethod.FPU, durationHours = 3))
        advanceUntilIdle()
        sut.updateFoodAmount(0, 300.0)
        sut.updateMealECarbsGrams(5)
        sut.updateMealCarbsNow(70)
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
    }
}
