package app.aaps.database.persistence

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodECarbsMigrationTest {
    @Test
    fun migrationPreservesAllLegacyRuleFieldsAndIgnoresInvalidFoodKeys() {
        val rules = FoodECarbsMigration.legacyRules("""{"7":{"method":"FPU","durationHours":3,"manualGramsPer100g":7.5,"future":"keep"},"bad":{},"0":{},"8":false}""")
        assertEquals(setOf(7L), rules.keys)
        val rule = Json.parseToJsonElement(rules.getValue(7)).jsonObject
        assertEquals(JsonPrimitive(1), rule["version"])
        assertEquals(JsonPrimitive(true), rule["enabled"])
        assertEquals(JsonPrimitive(7.5), rule["manualGramsPer100g"])
        assertEquals(JsonPrimitive("keep"), rule["future"])
    }

    @Test
    fun corruptPreferencesDoNotReplaceAnyFoodData() {
        listOf("", "not json", "[]").forEach { assertTrue(FoodECarbsMigration.legacyRules(it).isEmpty()) }
    }
}
