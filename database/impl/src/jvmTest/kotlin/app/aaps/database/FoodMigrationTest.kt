package app.aaps.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.aaps.database.di.JvmAppDatabaseBuilder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

class FoodMigrationTest {
    @Test
    fun `upgrading a real version 36 database keeps foods and stores the new local fields`() = runTest {
        val directory = createTempDirectory("aaps-food-migration")
        val fileName = "$directory/aaps.db"
        val schema = Json.parseToJsonElement(File("src/androidDeviceTest/assets/app.aaps.database.AppDatabase/36.json").readText())
            .jsonObject.getValue("database").jsonObject
        BundledSQLiteDriver().open(fileName).use { connection ->
            schema.getValue("entities").jsonArray.forEach { entry ->
                val entity = entry.jsonObject
                val name = entity.getValue("tableName").jsonPrimitive.content
                connection.execSQL(entity.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                entity["indices"]?.jsonArray?.forEach { index ->
                    connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                }
            }
            schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
            connection.execSQL("PRAGMA user_version = 36")
            connection.execSQL("INSERT INTO foods (id, version, dateCreated, isValid, name, portion, carbs, fat, protein, unit) VALUES (7, 0, 1000, 1, 'Meal', 100, 25, 5, 10, 'g')")
        }
        val builder = JvmAppDatabaseBuilder()
        val rule = """{"method":"FPU","durationHours":3}"""
        try {
            val database = builder.provideAppDatabase(fileName)
            try {
                val food = database.foodDao.getFoodData().single()
                assertThat(food.id).isEqualTo(7)
                assertThat(food.carbs).isEqualTo(25)
                assertThat(food.fat).isEqualTo(5)
                assertThat(food.eCarbsRuleJson).isNull()
                assertThat(food.isLocallyEdited).isFalse()
                database.foodDao.updateExistingEntry(food.copy(eCarbsRuleJson = rule, isLocallyEdited = true))
            } finally { database.close() }
            val reopened = builder.provideAppDatabase(fileName)
            try {
                val food = reopened.foodDao.getFoodData().single()
                assertThat(food.eCarbsRuleJson).isEqualTo(rule)
                assertThat(food.isLocallyEdited).isTrue()
            } finally { reopened.close() }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
