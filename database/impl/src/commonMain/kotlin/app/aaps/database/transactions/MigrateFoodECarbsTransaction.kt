package app.aaps.database.transactions

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Copy the former device-local rules into current food rows without replacing synced metadata. */
class MigrateFoodECarbsTransaction(private val rules: Map<Long, String>) : Transaction<Unit>() {
    override suspend fun run() {
        for ((id, rule) in rules) {
            val food = database.foodDao.findById(id) ?: continue
            if (food.referenceId != null || food.aapsFoodECarbs != null) continue
            val fields = Json.parseToJsonElement(rule) as JsonObject
            // Saving a legacy rule also saved this food, so its row timestamp is the edit time.
            food.aapsFoodECarbs = JsonObject(fields + ("updatedAt" to JsonPrimitive(food.dateCreated.coerceAtLeast(0)))).toString()
            // The normal update records history and queues the migrated rule for upload.
            database.foodDao.updateExistingEntry(food)
        }
    }
}
