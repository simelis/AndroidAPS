package app.aaps.database.daos

import androidx.room.Dao
import androidx.room.Query
import app.aaps.database.entities.Food
import app.aaps.database.entities.TABLE_FOODS

@Dao
internal interface FoodDao : TraceableDao<Food> {

    @Query("SELECT * FROM $TABLE_FOODS WHERE id = :id")
    override suspend fun findById(id: Long): Food?

    @Query("DELETE FROM $TABLE_FOODS")
    override suspend fun deleteAllEntries()

    @Query("DELETE FROM $TABLE_FOODS WHERE dateCreated < :than")
    override suspend fun deleteOlderThan(than: Long): Int

    @Query("DELETE FROM $TABLE_FOODS WHERE referenceId IS NOT NULL")
    override suspend fun deleteTrackedChanges(): Int

    @Query("SELECT id FROM $TABLE_FOODS ORDER BY id DESC limit 1")
    suspend fun getLastId(): Long?

    @Query("SELECT * FROM $TABLE_FOODS WHERE nightscoutId = :nsId AND referenceId IS NULL")
    suspend fun findByNSId(nsId: String): Food?

    @Query("SELECT * FROM $TABLE_FOODS WHERE isValid = 1 AND referenceId IS NULL ORDER BY id DESC")
    suspend fun getFoodData(): List<Food>

    // for WS we need 1 record only
    @Query("SELECT * FROM $TABLE_FOODS WHERE id > :id ORDER BY id ASC limit 1")
    suspend fun getNextModifiedOrNewAfter(id: Long): Food?

    /** Id of the newest change of this food: the row itself, or its newest history row. */
    @Query("SELECT MAX(id) FROM $TABLE_FOODS WHERE id = :id OR referenceId = :id")
    suspend fun getLastChangeId(id: Long): Long?

    @Query("SELECT * FROM $TABLE_FOODS WHERE id = :referenceId")
    suspend fun getCurrentFromHistoric(referenceId: Long): Food?

    /**
     * Clears `isLocallyEdited` of every food whose newest change has an id up to [uploadedUpTo], the
     * food upload cursor. Such a food is in Nightscout, so a newer Nightscout version may replace it.
     * Only the flag changes: no history row is made, so nothing is uploaded again.
     */
    @Query(
        "UPDATE $TABLE_FOODS SET isLocallyEdited = 0 WHERE isLocallyEdited = 1 AND referenceId IS NULL AND id <= :uploadedUpTo " +
            "AND NOT EXISTS (SELECT 1 FROM $TABLE_FOODS AS newer WHERE newer.referenceId = $TABLE_FOODS.id AND newer.id > :uploadedUpTo)"
    )
    suspend fun clearUploadedEdits(uploadedUpTo: Long): Int
}