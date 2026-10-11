package app.aaps.database.transactions

import app.aaps.database.entities.Food

/**
 * Sync the Foods from NS. Rows edited in AAPS are skipped, see [Food.isLocallyEdited].
 */
class SyncNsFoodTransaction(private val foods: List<Food>) : Transaction<SyncNsFoodTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()

        for (food in foods) {
            val current: Food? =
                food.interfaceIDs.nightscoutId?.let {
                    database.foodDao.findByNSId(it)
                }

            if (current?.isLocallyEdited == true) {
                // Edited or deleted in AAPS. Food edits are not uploaded, so Nightscout has an older copy: keep ours
                continue
            }
            if (current != null) {
                // nsId exists, update if different
                if (!current.contentEqualsTo(food)) {
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

    class TransactionResult {

        val updated = mutableListOf<Food>()
        val inserted = mutableListOf<Food>()
        val invalidated = mutableListOf<Food>()
    }
}