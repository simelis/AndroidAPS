package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences

import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.objects.workflow.WorkOutcome

import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsclientV3.keys.NsclientLongKey
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException

/**
 * Loads foods in two ways, because Nightscout has two kinds of food:
 *
 * - Changes made through API v3 (AAPS and AAPSClient) are read from the food history every time,
 *   from [NsclientLongKey.FoodLastLoadedSrvModified] (0 the first time, so all of it). Only the
 *   history has deleted foods; the plain list hides them.
 * - Foods made or edited in the Nightscout web editor (API v1) have no change time and are not in
 *   the history. The full list is read every 5th time to find them.
 */
@Inject
class LoadFoodsRunner(

    private val aapsLogger: AAPSLogger,

    private val nsClientV3Plugin: NSClientV3Plugin,
    private val dateUtil: DateUtil,
    private val storeDataForDb: StoreDataForDb,
    private val nsIncomingDataProcessor: NsIncomingDataProcessor,
    private val nsClientRepository: NSClientRepository,
    private val preferences: Preferences
) {

    suspend fun run(): WorkOutcome {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Failure("AndroidClient is null")
        val loadedUpTo = preferences.get(NsclientLongKey.FoodLastLoadedSrvModified)

        // A failed history read must not also skip the full list below, so it has its own error handling
        var historyError: Exception? = null
        try {
            // The first time (and after a full sync) it is read from the start: the full list hides
            // deleted foods, so a delete made before that would never be seen
            if ((nsClientV3Plugin.newestDataOnServer?.collections?.food ?: Long.MAX_VALUE) > loadedUpTo)
                loadChanges(nsAndroidClient, loadedUpTo)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.message)
            historyError = error
        }

        try {
            if (nsClientV3Plugin.lastLoadedSrvModified.collections.foods++ % 5 == 0L) {
                val foods: List<NSFood> = nsAndroidClient.getFoods(1000).values
                aapsLogger.debug(LTag.NSCLIENT, "FOODS: $foods")
                nsClientRepository.addLog("◄ RCV", "${foods.size} FOODs")
                // Schedule processing of fetched data
                nsIncomingDataProcessor.processFood(foods)
                storeDataForDb.storeFoodsToDb()
            } else {
                nsClientRepository.addLog("● RCV FOOD", "full list skipped")
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.message)
            nsClientV3Plugin.lastOperationError = error.message
            return WorkOutcome.Failure(error.message ?: "error")
        }

        historyError?.let {
            nsClientV3Plugin.lastOperationError = it.message
            return WorkOutcome.Failure(it.message ?: "error")
        }
        nsClientV3Plugin.lastOperationError = null
        return WorkOutcome.Success
    }

    private suspend fun loadChanges(nsAndroidClient: NSAndroidClient, loadedUpTo: Long) {
        var from = loadedUpTo
        while (true) {
            val response = nsAndroidClient.getFoodsModifiedSince(from, NSClientV3Plugin.RECORDS_TO_LOAD)
            if (response.values.isEmpty()) return
            nsClientRepository.addLog("◄ RCV", "${response.values.size} FOOD changes from ${dateUtil.dateAndTimeAndSecondsString(from)}")
            nsIncomingDataProcessor.processFood(response.values)
            storeDataForDb.storeFoodsToDb()
            // Stop when the cursor cannot move, or the next page would repeat this one
            val next = response.lastServerModified ?: return
            if (next <= from) return
            preferences.put(NsclientLongKey.FoodLastLoadedSrvModified, next)
            from = next
            if (response.values.size < NSClientV3Plugin.RECORDS_TO_LOAD) return
        }
    }
}
