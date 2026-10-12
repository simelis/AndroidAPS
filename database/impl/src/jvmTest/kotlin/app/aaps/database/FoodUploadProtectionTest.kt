package app.aaps.database

import app.aaps.database.di.JvmAppDatabaseBuilder
import app.aaps.database.entities.Food
import app.aaps.database.entities.embedments.InterfaceIDs
import app.aaps.database.transactions.InsertOrUpdateFoodTransaction
import app.aaps.database.transactions.SyncNsFoodTransaction
import app.aaps.database.transactions.Transaction
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

/** A food edited in AAPS follows Nightscout again once its newest change is uploaded, also after a full sync. */
class FoodUploadProtectionTest {

    private val directory = createTempDirectory("aaps-food-protection")
    private val database = JvmAppDatabaseBuilder().provideAppDatabase("$directory/aaps.db")

    @AfterTest
    fun tearDown() {
        database.close()
        directory.toFile().deleteRecursively()
    }

    @Test
    fun `an uploaded edit takes a newer Nightscout value after a full sync`() = runTest {
        val id = editedFood()
        val lastChange = database.foodDao.getLastChangeId(id)!!

        assertThat(database.foodDao.clearUploadedEdits(lastChange)).isEqualTo(1)
        run(SyncNsFoodTransaction(listOf(fromNs(carbs = 30)), lastUploadedId = 0))

        assertThat(database.foodDao.findById(id)!!.carbs).isEqualTo(30)
    }

    @Test
    fun `an edit that is not uploaded yet stays protected`() = runTest {
        val id = editedFood()
        val lastChange = database.foodDao.getLastChangeId(id)!!

        assertThat(database.foodDao.clearUploadedEdits(lastChange - 1)).isEqualTo(0)
        run(SyncNsFoodTransaction(listOf(fromNs(carbs = 30)), lastUploadedId = 0))

        val food = database.foodDao.findById(id)!!
        assertThat(food.carbs).isEqualTo(25)
        assertThat(food.isLocallyEdited).isTrue()
    }

    @Test
    fun `clearing the flag makes no new change to upload`() = runTest {
        val id = editedFood()
        val lastId = database.foodDao.getLastId()

        database.foodDao.clearUploadedEdits(database.foodDao.getLastChangeId(id)!!)

        assertThat(database.foodDao.getLastId()).isEqualTo(lastId)
        assertThat(database.foodDao.findById(id)!!.isLocallyEdited).isFalse()
    }

    private suspend fun editedFood(): Long {
        run(SyncNsFoodTransaction(listOf(fromNs(carbs = 20)), lastUploadedId = 0))
        val food = database.foodDao.findByNSId(NS_ID)!!
        run(InsertOrUpdateFoodTransaction(food.copy(carbs = 25)))
        return food.id
    }

    private fun fromNs(carbs: Int) =
        Food(name = "Meal", portion = 100.0, carbs = carbs, unit = "g", interfaceIDs_backing = InterfaceIDs(nightscoutId = NS_ID))

    private suspend fun <T> run(transaction: Transaction<T>): T {
        transaction.database = DelegatedAppDatabase(mutableListOf(), database)
        return transaction.run()
    }

    private companion object {

        const val NS_ID = "ns-food"
    }
}
