package app.aaps.database.transactions

import app.aaps.database.entities.Food
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Sync the Foods from NS
 */
class SyncNsFoodTransaction(private val foods: List<Food>) : Transaction<SyncNsFoodTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()

        for (food in foods) {
            val current: Food? =
                food.interfaceIDs.nightscoutId?.let {
                    database.foodDao.findByNSId(it)
                }

            if (current != null) {
                val localRule = current.aapsFoodECarbs
                val keepLocalRule = localRule != null && (food.aapsFoodECarbs == null || ruleTimestamp(localRule) > ruleTimestamp(food.aapsFoodECarbs))
                val repairExtension = food.isValid && keepLocalRule && localRule != food.aapsFoodECarbs
                // Older clients/editors omit our extension. Omission must not erase an existing rule.
                // Disabling is an explicit JSON value, so it still replaces the previous rule.
                if (keepLocalRule) food.aapsFoodECarbs = localRule
                // nsId exists, update if different
                if (!current.contentEqualsTo(food) || repairExtension) {
                    if (food.isValid && current.isValid) result.updated.add(current)
                    else if (!food.isValid && current.isValid) result.invalidated.add(current)
                    current.copyFrom(food)
                    database.foodDao.updateExistingEntry(current)
                }
            } else {
                // not known nsId, add
                database.foodDao.insertNewEntry(food)
                result.inserted.add(food)
            }
        }
        return result

    }

    private fun ruleTimestamp(rule: String?): Long = runCatching {
        val obj = rule?.let { Json.parseToJsonElement(it) } as? JsonObject
        (obj?.get("updatedAt") as? JsonPrimitive)?.longOrNull ?: 0L
    }.getOrDefault(0L)

    class TransactionResult {

        val updated = mutableListOf<Food>()
        val inserted = mutableListOf<Food>()
        val invalidated = mutableListOf<Food>()
    }
}
