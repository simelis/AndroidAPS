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

        val transaction = SyncNsFoodTransaction(listOf(food), lastUploadedId = 0)
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

        val transaction = SyncNsFoodTransaction(listOf(food), lastUploadedId = 0)
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

        val transaction = SyncNsFoodTransaction(listOf(food), lastUploadedId = 0)
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

        val transaction = SyncNsFoodTransaction(listOf(food), lastUploadedId = 0)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.updated).isEmpty()
        assertThat(result.invalidated).isEmpty()
        assertThat(result.inserted).isEmpty()

        verify(foodDao, never()).updateExistingEntry(any())
        verify(foodDao, never()).insertNewEntry(any())
    }

    @Test
    fun `food edited in AAPS is not overwritten by Nightscout before it is uploaded`() = runTest {
        val existing = createFood(id = 7, nsId = "ns-123", name = "Local meal", carbs = 25)
            .apply { isLocallyEdited = true }
        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)

        val result = SyncNsFoodTransaction(listOf(createFood(id = 0, nsId = "ns-123", name = "Meal", carbs = 50)), lastUploadedId = 0)
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()

        assertThat(existing.name).isEqualTo("Local meal")
        assertThat(existing.carbs).isEqualTo(25)
        assertThat(result.updated).isEmpty()
        assertThat(result.inserted).isEmpty()
        verify(foodDao, never()).updateExistingEntry(any())
        verify(foodDao, never()).insertNewEntry(any())
    }

    @Test
    fun `food deleted in AAPS is not restored or deleted again by Nightscout`() = runTest {
        val existing = createFood(id = 7, nsId = "ns-123", name = "Apple", carbs = 15, isValid = false)
            .apply { isLocallyEdited = true }
        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)

        SyncNsFoodTransaction(listOf(createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 15)), lastUploadedId = 0)
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()

        assertThat(existing.isValid).isFalse()
        verify(foodDao, never()).updateExistingEntry(any())
    }

    @Test
    fun `food edited in AAPS follows Nightscout again once its newest change is uploaded`() = runTest {
        val existing = createFood(id = 7, nsId = "ns-123", name = "Meal", carbs = 25).apply { isLocallyEdited = true }
        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)
        whenever(foodDao.getLastChangeId(7)).thenReturn(40)

        SyncNsFoodTransaction(listOf(createFood(id = 0, nsId = "ns-123", name = "Meal", carbs = 30)), lastUploadedId = 39)
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()
        assertThat(existing.carbs).isEqualTo(25)

        val result = SyncNsFoodTransaction(listOf(createFood(id = 0, nsId = "ns-123", name = "Meal", carbs = 30)), lastUploadedId = 40)
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()
        assertThat(existing.carbs).isEqualTo(30)
        assertThat(existing.isLocallyEdited).isFalse()
        assertThat(result.updated).containsExactly(existing)
    }

    @Test
    fun `a delete with only an id marks the food deleted and copies nothing`() = runTest {
        val existing = createFood(id = 7, nsId = "ns-123", name = "Apple", carbs = 15)
        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)
        val deleteOnly = Food(name = "", portion = 0.0, carbs = 0, isValid = false, interfaceIDs_backing = InterfaceIDs(nightscoutId = "ns-123"))

        val result = SyncNsFoodTransaction(listOf(deleteOnly), lastUploadedId = 0).apply { database = this@SyncNsFoodTransactionTest.database }.run()

        assertThat(existing.isValid).isFalse()
        assertThat(existing.name).isEqualTo("Apple")
        assertThat(existing.carbs).isEqualTo(15)
        assertThat(result.invalidated).containsExactly(existing)
    }

    @Test
    fun `a delete of a food this device never had is not stored`() = runTest {
        whenever(foodDao.findByNSId("ns-123")).thenReturn(null)
        val result = SyncNsFoodTransaction(listOf(createFood(id = 0, nsId = "ns-123", name = "Apple", carbs = 15, isValid = false)), lastUploadedId = 0)
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()
        assertThat(result.inserted).isEmpty()
        verify(foodDao, never()).insertNewEntry(any())
    }

    @Test
    fun `handles multiple foods`() = runTest {
        val food1 = createFood(id = 0, nsId = "ns-1", name = "Apple", carbs = 15)
        val food2 = createFood(id = 0, nsId = "ns-2", name = "Banana", carbs = 25)

        whenever(foodDao.findByNSId("ns-1")).thenReturn(null)
        whenever(foodDao.findByNSId("ns-2")).thenReturn(null)

        val transaction = SyncNsFoodTransaction(listOf(food1, food2), lastUploadedId = 0)
        transaction.database = database
        val result = transaction.run()

        assertThat(result.inserted).hasSize(2)
    }

    @Test
    fun `a download keeps the eCarbs formula and the amount setting of this phone`() = runTest {
        val rule = """{"method":"FPU","durationHours":3}"""
        val existing = createFood(id = 1, nsId = "ns-123", name = "Meal", carbs = 25).apply { eCarbsRuleJson = rule; askAmount = false }
        whenever(foodDao.findByNSId("ns-123")).thenReturn(existing)

        SyncNsFoodTransaction(listOf(createFood(id = 0, nsId = "ns-123", name = "Meal", carbs = 25)))
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()
        verify(foodDao, never()).updateExistingEntry(any())

        SyncNsFoodTransaction(listOf(createFood(id = 0, nsId = "ns-123", name = "Meal", carbs = 30)))
            .apply { database = this@SyncNsFoodTransactionTest.database }.run()
        assertThat(existing.carbs).isEqualTo(30)
        assertThat(existing.eCarbsRuleJson).isEqualTo(rule)
        assertThat(existing.askAmount).isFalse()
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
