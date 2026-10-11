package app.aaps.database.persistence

import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.database.AppRepository
import app.aaps.database.transactions.MigrateFoodECarbsTransaction
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Runs before food reads/uploads/downloads. Keep the old preference as a recovery copy. */
@SingleIn(AppScope::class)
@Inject
class FoodECarbsMigration(private val repository: AppRepository, private val preferences: () -> Preferences) {
    private val mutex = Mutex()
    private var complete = false

    suspend fun migrate() = mutex.withLock {
        if (complete) return@withLock
        val rules = legacyRules(preferences().get(StringNonKey.FoodECarbsSettings))
        if (rules.isNotEmpty()) repository.runTransactionForResultSuspend(MigrateFoodECarbsTransaction(rules))
        complete = true
    }

    companion object {
        internal fun legacyRules(json: String): Map<Long, String> {
            val legacy = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return emptyMap()
            return legacy.entries.mapNotNull { (key, value) ->
                val id = key.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val rule = value as? JsonObject ?: return@mapNotNull null
                id to JsonObject(rule + mapOf("version" to JsonPrimitive(1), "enabled" to JsonPrimitive(true))).toString()
            }.toMap()
        }
    }
}
