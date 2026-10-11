package app.aaps.plugins.sync.nsclientV3.workers

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.utils.DateUtil

import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.objects.workflow.WorkOutcome

import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.NsIncomingDataProcessor
import dev.zacsweers.metro.Inject

/**
 * Loads foods in two ways, because Nightscout has two kinds of food:
 *
 * - Changes made through API v3 (AAPS and AAPSClient) are read from the food history every time,
 *   from the `food` cursor. Only the history has deleted foods; the plain list hides them.
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
    private val nsClientRepository: NSClientRepository
) {

    suspend fun run(): WorkOutcome {
        val nsAndroidClient = nsClientV3Plugin.nsAndroidClient ?: return WorkOutcome.Failure("AndroidClient is null")
        val collections = nsClientV3Plugin.lastLoadedSrvModified.collections

        try {
            // The history needs a starting point, so it begins after the first full read
            if (collections.food != 0L && (nsClientV3Plugin.newestDataOnServer?.collections?.food ?: Long.MAX_VALUE) > collections.food)
                loadChanges(nsAndroidClient)

            if (collections.foods++ % 5 == 0L) {
                val foods: List<NSFood> = nsAndroidClient.getFoods(1000).values
                aapsLogger.debug(LTag.NSCLIENT, "FOODS: $foods")
                nsClientRepository.addLog("◄ RCV", "${foods.size} FOODs")
                // Schedule processing of fetched data
                nsIncomingDataProcessor.processFood(foods)
                storeDataForDb.storeFoodsToDb()
                // Start the history from the server's own time, not this phone's clock
                val newestOnServer = nsClientV3Plugin.newestDataOnServer?.collections?.food ?: 0L
                if (collections.food == 0L && newestOnServer > 0L) {
                    collections.food = newestOnServer
                    nsClientV3Plugin.storeLastLoadedSrvModified()
                }
            } else {
                nsClientRepository.addLog("● RCV FOOD", "full list skipped")
            }
        } catch (error: Exception) {
            aapsLogger.error("Error: ", error)
            nsClientRepository.addLog("◄ ERROR", error.message)
            nsClientV3Plugin.lastOperationError = error.message
            return WorkOutcome.Failure(error.message ?: "error")
        }

        nsClientV3Plugin.lastOperationError = null
        return WorkOutcome.Success
    }

    private suspend fun loadChanges(nsAndroidClient: NSAndroidClient) {
        val collections = nsClientV3Plugin.lastLoadedSrvModified.collections
        while (true) {
            val from = collections.food
            val response = nsAndroidClient.getFoodsModifiedSince(from, NSClientV3Plugin.RECORDS_TO_LOAD)
            if (response.values.isEmpty()) return
            nsClientRepository.addLog("◄ RCV", "${response.values.size} FOOD changes from ${dateUtil.dateAndTimeAndSecondsString(from)}")
            nsIncomingDataProcessor.processFood(response.values)
            storeDataForDb.storeFoodsToDb()
            // Stop when the cursor cannot move, or the next page would repeat this one
            val next = response.lastServerModified ?: return
            if (next <= from) return
            collections.food = next
            nsClientV3Plugin.storeLastLoadedSrvModified()
            if (response.values.size < NSClientV3Plugin.RECORDS_TO_LOAD) return
        }
    }
}
