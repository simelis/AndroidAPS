package app.aaps.database.transactions

import app.aaps.database.DelegatedAppDatabase
import app.aaps.database.daos.FoodDao
import app.aaps.database.entities.Food
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MigrateFoodECarbsTransactionTest {
    @Test
    fun `legacy migration updates only current foods without metadata and never resurrects disabled rules`() = runTest {
        val dao = mock<FoodDao>()
        val database = mock<DelegatedAppDatabase>()
        whenever(database.foodDao).thenReturn(dao)
        val legacy = Food(id = 7, dateCreated = 1000, name = "Meal", portion = 100.0, carbs = 25)
        val disabled = legacy.copy(id = 8, aapsFoodECarbs = """{"version":1,"enabled":false}""")
        val historic = legacy.copy(id = 9, referenceId = 7)
        whenever(dao.findById(7)).thenReturn(legacy)
        whenever(dao.findById(8)).thenReturn(disabled)
        whenever(dao.findById(9)).thenReturn(historic)
        val rule = """{"version":1,"enabled":true,"durationHours":3}"""
        MigrateFoodECarbsTransaction(mapOf(7L to rule, 8L to rule, 9L to rule, 10L to rule)).apply { this.database = database }.run()
        assertThat(legacy.aapsFoodECarbs).contains("\"updatedAt\":1000")
        assertThat(legacy.aapsFoodECarbs).contains("\"durationHours\":3")
        verify(dao).updateExistingEntry(legacy)
        verify(dao, never()).updateExistingEntry(disabled)
        verify(dao, never()).updateExistingEntry(historic)
    }
}
