package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.FoodDao
import app.aaps.database.entities.Food
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncNsFoodTransactionTest {

    private lateinit var database: DelegatedAppDatabase
    private lateinit var foodDao: FoodDao

    @BeforeEach
    fun setup() {
        foodDao = mock()
        database = mock()
        whenever(database.foodDao).thenReturn(foodDao)
    }

    @Test
    fun `inserts new food when nsId not found`() = runTest {
        val food = createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 15)

        whenever(foodDao.findByNSId("ns-123")).thenReturn(null)

        val transaction = SyncNsFoodTransaction(listOf(food))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(1)
        assertThat(result.inserted[0]).isEqualTo(food)
        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(foodDao).insertNewEntry(food)
    }

    @Test
    fun `updates food when content changes`() = runTest {
        val food = createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 20)
        val existing = createFood(id = 1, nsId = "ns-123", name = "Apple", carbs = 15)

        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsFoodTransaction(listOf(food))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).hasSize(1)
        assertThat(result.updated[0]).isEqualTo(existing)
        assertThat(result.inserted).isEmpty()
        assertThat(result.invalidated).isEmpty()

        verify(foodDao).updateExistingEntry(existing)
        verify(foodDao, never()).insertNewEntry(any())
    }

    @Test
    fun `invalidates food when valid becomes invalid`() = runTest {
        val food = createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 15, isValid = false)
        val existing = createFood(id = 1, nsId = "ns-123", name = "Apple", carbs = 15, isValid = true)

        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsFoodTransaction(listOf(food))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.invalidated).hasSize(1)
        assertThat(result.invalidated[0]).isEqualTo(existing)
        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isEmpty()

        verify(foodDao).updateExistingEntry(existing)
    }

    @Test
    fun `does not update when content is same`() = runTest {
        val food = createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 15)
        val existing = createFood(id = 1, nsId = "ns-123", name = "Apple", carbs = 15)

        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)

        val transaction = SyncNsFoodTransaction(listOf(food))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.inserted).isEmpty()

        verify(foodDao, never()).updateExistingEntry(any())
        verify(foodDao, never()).insertNewEntry(any())
    }

    @Test
    fun `older food documents preserve local rules and queue a repair upload even with unchanged nutrients`() = runTest {
        val metadata = """{"version":1,"enabled":true,"method":"FPU","delayMinutes":60,"durationHours":3,"manualGramsPer100g":7.5}"""
        val existing = createFood(7, "ns-123", "Meal", 25).copy(aapsFoodECarbs = metadata)
        val incoming = createFood(0, "ns-123", "Meal", 25)
        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)
        val transaction = SyncNsFoodTransaction(listOf(incoming)).apply { database = this@SyncNsFoodTransactionTest.database }
        val result = transaction.run()
        assertThat(result.updated).hasSize(1)
        assertThat(existing.id).isEqualTo(7)
        assertThat(existing.aapsFoodECarbs).isEqualTo(metadata)
        verify(foodDao).updateExistingEntry(existing)
    }

    @Test
    fun `remote nutrient edits preserve an omitted rule and explicit disabling replaces it`() = runTest {
        val existing = createFood(7, "ns-123", "Meal", 25).copy(aapsFoodECarbs = """{"version":1,"enabled":true}""")
        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)
        SyncNsFoodTransaction(listOf(createFood(0, "ns-123", "Renamed meal", 30)))
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()
        assertThat(existing.carbs).isEqualTo(30)
        assertThat(existing.name).isEqualTo("Renamed meal")
        assertThat(existing.aapsFoodECarbs).contains("\"enabled\":true")
        val disabled = """{"version":1,"enabled":false}"""
        SyncNsFoodTransaction(listOf(createFood(0, "ns-123", "Renamed meal", 30).copy(aapsFoodECarbs = disabled)))
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()
        assertThat(existing.aapsFoodECarbs).isEqualTo(disabled)
        assertThat(existing.id).isEqualTo(7)
    }

    @Test
    fun `a rule imported on a fresh database travels with its newly assigned food ID`() = runTest {
        val metadata = """{"version":99,"enabled":true,"futureRule":{"factor":7.5}}"""
        val incoming = createFood(0, "ns-123", "Meal", 25).copy(aapsFoodECarbs = metadata)
        whenever(foodDao.insertNewEntry(any())).thenAnswer { invocation -> (invocation.arguments[0] as Food).id = 91; 91L }
        val result = SyncNsFoodTransaction(listOf(incoming)).apply { database = this@SyncNsFoodTransactionTest.database }.run()
        assertThat(result.inserted.single().id).isEqualTo(91)
        assertThat(result.inserted.single().aapsFoodECarbs).isEqualTo(metadata)
    }

    @Test
    fun `stale remote rules cannot overwrite a newer local adjustment but newer remote changes apply`() = runTest {
        val newer = """{"version":1,"enabled":true,"manualGramsPer100g":7.5,"updatedAt":2000}"""
        val older = """{"version":1,"enabled":true,"manualGramsPer100g":5,"updatedAt":1000}"""
        val existing = createFood(7, "ns-123", "Meal", 25).copy(aapsFoodECarbs = newer)
        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)
        SyncNsFoodTransaction(listOf(createFood(0, "ns-123", "Meal", 25).copy(aapsFoodECarbs = older)))
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()
        assertThat(existing.aapsFoodECarbs).isEqualTo(newer)
        verify(foodDao).updateExistingEntry(existing)
        val latestDisable = """{"version":1,"enabled":false,"updatedAt":3000}"""
        SyncNsFoodTransaction(listOf(createFood(0, "ns-123", "Meal", 25).copy(aapsFoodECarbs = latestDisable)))
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()
        assertThat(existing.aapsFoodECarbs).isEqualTo(latestDisable)
    }

    @Test
    fun `handles multiple foods`() = runTest {
        val food1 = createFood(id = 0, nsId = "ns-1", name = "Apple", carbs = 15)
        val food2 = createFood(id = 0, nsId = "ns-2", name = "Banana", carbs = 25)

        whenever(foodDao.findByNSId("ns-1")).thenReturn(null)
        whenever(foodDao.findByNSId("ns-2")).thenReturn(null)

        val transaction = SyncNsFoodTransaction(listOf(food1, food2))
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)
    }

    private fun createFood(
        id: Long,
        nsId: String,
        name: String,
        carbs: Int,
        isValid: Boolean = true
    ): Food = Food(
        name = name,
        category = "Test",
        subCategory = "Test",
        portion = 100.0,
        carbs = carbs,
        fat = 0,
        protein = 0,
        energy = 0,
        unit = "g",
        gi = null,
        isValid = isValid,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = nsId),
    ).also { it.id = id }
}
