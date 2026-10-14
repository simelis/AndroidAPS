package app.aaps.database.transactions

import app.aaps.database.entities.Food

/**
 * Sync the Foods from NS
 */
class SyncNsFoodTransaction(
    private val foods: List<Food>,
    private val lastUploadedId: Long
) : Transaction<SyncNsFoodTransaction.TransactionResult>() {

    override suspend fun run(): TransactionResult {
        val result = TransactionResult()

        for (food in foods) {
            val current: Food? =
                food.interfaceIDs.nightscoutId?.let {
                    database.foodDao.findByNSId(it)
                }

            if (current != null && current.isLocallyEdited && (database.foodDao.getLastChangeId(current.id) ?: current.id) > lastUploadedId) {
                // Edited in AAPS and not uploaded yet: the NS copy is older, keep ours
                result.skipped.add(current)
                continue
            }
            when {
                // Deleted in NS. A delete may carry only the id, so copy nothing
                !food.isValid      -> if (current?.isValid == true) {
                    current.isValid = false
                    current.isLocallyEdited = false
                    database.foodDao.updateExistingEntry(current)
                    result.invalidated.add(current)
                }

                // nsId exists, update if different
                current != null    -> if (!current.contentEqualsTo(food)) {
                    if (current.isValid) result.updated.add(current)
                    current.copyFrom(food)
                    current.isLocallyEdited = false
                    database.foodDao.updateExistingEntry(current)
                }

                // not known nsId, add
                else               -> {
                    database.foodDao.insertNewEntry(food)
                    result.inserted.add(food)
                }
            }
        }
        return result

    }

    class TransactionResult {

        val updated = mutableListOf<Food>()
        val inserted = mutableListOf<Food>()
        val invalidated = mutableListOf<Food>()
        val skipped = mutableListOf<Food>()
    }
}
