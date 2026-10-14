package app.aaps.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

enum class FoodECarbsMethod { CUSTOM, FPU, MODIFIED_FPU }

/**
 * How a food turns its protein and fat into eCarbs in the bolus wizard.
 * A new rule has no duration, so it is not valid until the user chooses one.
 */
@Serializable
data class FoodECarbsRule(
    val method: FoodECarbsMethod = FoodECarbsMethod.CUSTOM,
    val proteinPercent: Int = 0,
    val fatPercent: Int = 0,
    val delayMinutes: Int = 0,
    val durationHours: Int = 0,
    /**
     * Fixed eCarbs for the saved amount of the food, used instead of the formula.
     * Keep the old JSON name, or saved and synced rules lose this value.
     */
    @SerialName("manualGramsPer100g") val manualGrams: Double? = null,
    /** Part of the meal carbs that the bolus counts now. The rest is added to the eCarbs. */
    val carbsNowPercent: Int = 100
) {

    val isValid: Boolean
        get() = proteinPercent in FACTOR_PERCENT && fatPercent in FACTOR_PERCENT &&
            delayMinutes in DELAY_MINUTES && durationHours in DURATION_HOURS && carbsNowPercent in CARBS_NOW_PERCENT &&
            (manualGrams == null || (manualGrams.isFinite() && manualGrams >= 0.0))

    /** FPU needs only one known nutrient: the unknown one counts as 0 g, which gives fewer eCarbs, not more. */
    fun canUse(proteinKnown: Boolean, fatKnown: Boolean): Boolean = when {
        manualGrams != null               -> true
        method == FoodECarbsMethod.CUSTOM -> (proteinPercent == 0 || proteinKnown) && (fatPercent == 0 || fatKnown)
        else                              -> proteinKnown || fatKnown
    }

    val countsProteinAndFat: Boolean
        get() = manualGrams == null && method != FoodECarbsMethod.CUSTOM

    fun carbsNow(carbs: Int): Int = carbs * carbsNowPercent / 100

    fun equivalentGrams(proteinGrams: Double, fatGrams: Double): Double {
        if (!proteinGrams.isFinite() || !fatGrams.isFinite() || proteinGrams < 0 || fatGrams < 0) return 0.0
        // Modified FPU: https://doi.org/10.1186/s12986-023-00757-w
        return when (method) {
            FoodECarbsMethod.CUSTOM       -> proteinGrams * proteinPercent / 100.0 + fatGrams * fatPercent / 100.0
            FoodECarbsMethod.FPU          -> (proteinGrams * 4.0 + fatGrams * 9.0) / 10.0
            FoodECarbsMethod.MODIFIED_FPU -> (proteinGrams * 4.0 + fatGrams * 9.0) / 20.0
        }
    }

    fun toJson(): JsonElement = json.encodeToJsonElement(serializer(), this)
    fun toJsonString(): String = json.encodeToString(serializer(), this)

    companion object {

        val FACTOR_PERCENT = 0..100
        val DELAY_MINUTES = 0..720
        val DURATION_HOURS = 1..10
        val CARBS_NOW_PERCENT = 50..100

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** A rule that cannot be read or is not valid gives null, never a guessed rule. */
        fun fromJson(element: JsonElement?): FoodECarbsRule? =
            if (element == null || element == JsonNull) null
            else readSafely { json.decodeFromJsonElement(serializer(), element) }

        /** Same as [fromJson], for a rule stored as text. */
        fun fromJsonString(text: String?): FoodECarbsRule? =
            text?.let { readSafely { json.decodeFromString(serializer(), it) } }

        private fun readSafely(read: () -> FoodECarbsRule): FoodECarbsRule? =
            try {
                read().takeIf { it.isValid }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
