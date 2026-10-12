package app.aaps.core.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

enum class FoodECarbsMethod { CUSTOM, FPU, MODIFIED_FPU }

/**
 * How a food turns its protein and fat into extra carbs (eCarbs) in the bolus wizard.
 *
 * Calculation choices, not a treatment recommendation. A new rule has no factors and no duration,
 * so it is not valid until the user chooses them.
 */
@Serializable
data class FoodECarbsRule(
    val method: FoodECarbsMethod = FoodECarbsMethod.CUSTOM,
    val proteinPercent: Int = 0,
    val fatPercent: Int = 0,
    val delayMinutes: Int = 0,
    val durationHours: Int = 0,
    /**
     * Fixed eCarbs per 100 g of a weighed food, or per portion of a food counted in portions. When set,
     * it is used instead of the formula. The JSON name is kept from when every rule food was weighed.
     */
    @SerialName("manualGramsPer100g") val manualGrams: Double? = null,
    /**
     * Part of the meal carbs that the bolus counts now. The rest is not dropped: it is added to the
     * eCarbs, like the pizza option of the wizard does.
     */
    val carbsNowPercent: Int = 100
) {

    val isValid: Boolean
        get() = proteinPercent in FACTOR_PERCENT && fatPercent in FACTOR_PERCENT &&
            delayMinutes in DELAY_MINUTES && durationHours in DURATION_HOURS && carbsNowPercent in CARBS_NOW_PERCENT &&
            (manualGrams == null || (manualGrams.isFinite() && manualGrams in MANUAL_GRAMS))

    /**
     * True when a food with these known nutrients can use this rule. A fixed amount needs neither and a
     * custom rule needs only the nutrients it counts. FPU needs at least one: the unknown one counts as
     * 0 g, which gives fewer eCarbs, not more.
     */
    fun canUse(proteinKnown: Boolean, fatKnown: Boolean): Boolean = when {
        manualGrams != null        -> true
        method == FoodECarbsMethod.CUSTOM -> (proteinPercent == 0 || proteinKnown) && (fatPercent == 0 || fatKnown)
        else                              -> proteinKnown || fatKnown
    }

    /** True when the formula counts both protein and fat, so an unknown one is counted as 0 g. */
    val countsProteinAndFat: Boolean
        get() = manualGrams == null && method != FoodECarbsMethod.CUSTOM

    /** Splits the meal [carbs] into the carbs counted now and the carbs moved to the eCarbs. */
    fun carbsNow(carbs: Int): Int = carbs * carbsNowPercent / 100

    fun equivalentGrams(proteinGrams: Double, fatGrams: Double): Double {
        if (!proteinGrams.isFinite() || !fatGrams.isFinite() || proteinGrams < 0 || fatGrams < 0) return 0.0
        // Original FPU: 100 kcal = 10 g carbohydrate equivalent. Modified FPU: 200 kcal = 10 g.
        // These are insulin-equivalent estimates, not grams of glucose produced by digestion.
        // Modified-FPU reference: https://doi.org/10.1186/s12986-023-00757-w (adult MDI study).
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
        val MANUAL_GRAMS = 0.0..100.0
        val CARBS_NOW_PERCENT = 50..100

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** A rule that cannot be read or is not valid is treated as no rule, never as a guessed one. */
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
