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
 * Going back from the food eCarbs builds to this fallback (36): branch v4.0-beta1 (version 37) and
 * branch v4.0-dev (version 37 or 38).
 *
 * The database is made from that build's own Room schema export, so it is the real file and not a
 * guess of it. v4.0-beta1 had two at the same version 37: early test versions had two food columns,
 * the later one also has `byWeight`.
 */
class FoodBuildDowngradeTest {

    @Test
    fun `a version 37 database from an early food build keeps all foods when opened here`() =
        downgradeKeepsFoods("food-ecarbs-build-schema-37.json", 37, BETA_EARLY)

    @Test
    fun `a version 37 database from the current food build keeps all foods when opened here`() =
        downgradeKeepsFoods("food-ecarbs-build-schema-37-by-weight.json", 37, BETA_BY_WEIGHT)

    /** The step back must not need an update when the food build adds another food column. */
    @Test
    fun `a food column this fallback does not know is dropped too`() =
        downgradeKeepsFoods("food-ecarbs-build-schema-37-by-weight.json", 37, BETA_BY_WEIGHT, futureColumn = true)

    @Test
    fun `a version 37 database from the dev build keeps all foods when opened here`() =
        downgradeKeepsFoods("food-dev-build-schema-37.json", 37, DEV_37)

    @Test
    fun `a version 38 database from the dev build keeps all foods when opened here`() =
        downgradeKeepsFoods("food-dev-build-schema-38.json", 38, DEV_38)

    /**
     * Inserts three foods into a database made from [schemaFile] at [version]: a pizza, an apple, and an
     * older version of the pizza kept as history. [extra] gives, for each food column the build added,
     * its SQL value for those three rows; some of them are NOT NULL without a default.
     */
    private fun downgradeKeepsFoods(schemaFile: String, version: Int, extra: Map<String, List<String>>, futureColumn: Boolean = false) = runTest {
        val directory = createTempDirectory("aaps-food-downgrade")
        val fileName = "$directory/aaps.db"
        val extraColumns = extra.keys.joinToString("") { ", $it" }
        fun extraValues(row: Int) = extra.values.joinToString("") { ", ${it[row]}" }
        val schema = Json.parseToJsonElement(File("src/jvmTest/resources/$schemaFile").readText())
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
            connection.execSQL("PRAGMA user_version = $version")
            if (futureColumn) connection.execSQL("ALTER TABLE foods ADD COLUMN `somethingNew` TEXT")
            connection.execSQL(
                "INSERT INTO foods (id, version, dateCreated, isValid, name, portion, carbs, fat, protein, unit$extraColumns) " +
                    "VALUES (7, 1, 1000, 1, 'Pizza', 100, 30, 10, 12, 'g'${extraValues(0)})"
            )
            connection.execSQL(
                "INSERT INTO foods (id, version, dateCreated, isValid, name, portion, carbs, unit$extraColumns) " +
                    "VALUES (8, 0, 1000, 1, 'Apple', 1, 12, 'piece'${extraValues(1)})"
            )
            // An older version of the pizza, kept as history: it points at the current row
            connection.execSQL(
                "INSERT INTO foods (id, version, dateCreated, isValid, referenceId, name, portion, carbs, unit$extraColumns) " +
                    "VALUES (9, 0, 900, 1, 7, 'Pizza', 100, 25, 'g'${extraValues(2)})"
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
                assertThat(database.foodDao.findById(9)?.referenceId).isEqualTo(7L)
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

    private companion object {

        const val RULE = "'{\"method\":\"FPU\",\"durationHours\":3}'"
        val BETA_EARLY = linkedMapOf(
            "eCarbsRuleJson" to listOf(RULE, "NULL", "NULL"),
            "isLocallyEdited" to listOf("1", "0", "0")
        )
        val BETA_BY_WEIGHT = linkedMapOf(
            "eCarbsRuleJson" to listOf(RULE, "NULL", "NULL"),
            "isLocallyEdited" to listOf("1", "0", "0"),
            "byWeight" to listOf("1", "0", "1")
        )
        val DEV_37 = linkedMapOf(
            "isLocallyEdited" to listOf("1", "0", "0")
        )
        val DEV_38 = linkedMapOf(
            "isLocallyEdited" to listOf("1", "0", "0"),
            "eCarbsRuleJson" to listOf(RULE, "NULL", "NULL"),
            "askAmount" to listOf("1", "0", "1")
        )
    }
}
