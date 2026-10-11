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

/**
 * Going back from the food eCarbs build (database version 37) to this fallback (36).
 *
 * The database is made from that build's own Room schema export, so it is the real version 37 file
 * and not a guess of it.
 */
class FoodBuildDowngradeTest {

    @Test
    fun `a version 37 database from the food build keeps all foods when opened here`() = runTest {
        val directory = createTempDirectory("aaps-food-downgrade")
        val fileName = "$directory/aaps.db"
        val schema = Json.parseToJsonElement(File("src/jvmTest/resources/food-ecarbs-build-schema-37.json").readText())
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
            connection.execSQL("PRAGMA user_version = 37")
            connection.execSQL(
                "INSERT INTO foods (id, version, dateCreated, isValid, name, portion, carbs, fat, protein, unit, eCarbsRuleJson, isLocallyEdited) " +
                    "VALUES (7, 1, 1000, 1, 'Pizza', 100, 30, 10, 12, 'g', '{\"method\":\"FPU\",\"durationHours\":3}', 1)"
            )
            connection.execSQL(
                "INSERT INTO foods (id, version, dateCreated, isValid, name, portion, carbs, unit, isLocallyEdited) " +
                    "VALUES (8, 0, 1000, 1, 'Apple', 100, 12, 'g', 0)"
            )
        }

        val builder = JvmAppDatabaseBuilder()
        try {
            // Room checks the tables against this build's schema after the migration, so a column
            // left behind would fail here
            val database = builder.provideAppDatabase(fileName)
            try {
                val foods = database.foodDao.getFoodData().associateBy { it.id }
                assertThat(foods.keys).containsExactly(7L, 8L)
                assertThat(foods.getValue(7).name).isEqualTo("Pizza")
                assertThat(foods.getValue(7).carbs).isEqualTo(30)
                assertThat(foods.getValue(7).fat).isEqualTo(10)
                assertThat(foods.getValue(8).name).isEqualTo("Apple")
            } finally {
                database.close()
            }
            // And it opens again as a normal version 36 database
            val reopened = builder.provideAppDatabase(fileName)
            try {
                assertThat(reopened.foodDao.getFoodData()).hasSize(2)
            } finally {
                reopened.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
