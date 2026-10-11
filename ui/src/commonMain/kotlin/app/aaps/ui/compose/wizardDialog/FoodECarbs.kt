package app.aaps.ui.compose.wizardDialog

import app.aaps.core.data.model.FD
import app.aaps.ui.compose.foodManagement.portionWeightGrams
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt
import kotlin.time.Clock

enum class FoodECarbsMethod { CUSTOM, FPU, MODIFIED_FPU }

/** Calculation choices, not a treatment recommendation. No conversion or timing is selected by default. */
data class FoodECarbsSettings(
    val method: FoodECarbsMethod = FoodECarbsMethod.CUSTOM,
    val proteinPercent: Int = 0,
    val fatPercent: Int = 0,
    val delayMinutes: Int = 0,
    val durationHours: Int = 0,
    val manualGramsPer100g: Double? = null
) {
    val isValid: Boolean
        get() = proteinPercent in 0..100 && fatPercent in 0..100 && delayMinutes in 0..720 && durationHours in 1..10 &&
            (manualGramsPer100g == null || (manualGramsPer100g.isFinite() && manualGramsPer100g in 0.0..100.0))

    fun equivalentCarbs(proteinGrams: Double, fatGrams: Double): Int = equivalentGrams(proteinGrams, fatGrams).roundToInt()

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
}

data class WizardCarbPlan(val carbs: Int, val eCarbs: Int, val delayMinutes: Int, val durationHours: Int)

/** Food mode replaces the old percentage preset; it must never add both estimates for the same meal. */
fun WizardDialogUiState.carbPlan(): WizardCarbPlan {
    val food = foodECarbs
    return if (food?.enabled == true) {
        WizardCarbPlan(carbs, food.eCarbsGrams, food.settings.delayMinutes, food.settings.durationHours)
    } else {
        WizardCarbPlan(carbs * carbsType.carbsPercent / 100, carbs * carbsType.eCarbsPercent / 100, carbsType.eCarbsDelayMinutes, carbsType.eCarbsDurationHours)
    }
}

val WizardDialogUiState.foodInputsValid: Boolean
    get() = !foodUnavailable && (foodECarbs?.let { it.isValid(maxCarbs) && carbs in 0..maxCarbs } ?: true)

data class FoodECarbsState(
    val food: FD,
    val portionGrams: Double = 0.0,
    val enabled: Boolean = false,
    val settings: FoodECarbsSettings = FoodECarbsSettings(),
    val overrideGrams: Int? = null
) {
    val hasWeight: Boolean get() = food.portionWeightGrams != null
    private val scale: Double get() = food.portionWeightGrams?.let { portionGrams / it } ?: 0.0
    val hasMacros: Boolean get() = food.protein?.let { it >= 0 } == true && food.fat?.let { it >= 0 } == true
    val proteinGrams: Double get() = (food.protein ?: 0) * scale
    val fatGrams: Double get() = (food.fat ?: 0) * scale
    val carbsGrams: Int get() = (food.carbs * scale).takeIf { it.isFinite() }?.roundToInt() ?: 0
    val calculatedGrams: Int get() = (settings.manualGramsPer100g?.let { it * portionGrams / 100.0 }
        ?: settings.equivalentGrams(proteinGrams, fatGrams)).roundToInt()
    val eCarbsGrams: Int get() = if (enabled) overrideGrams ?: calculatedGrams else 0

    fun isValid(maxCarbs: Int): Boolean =
        hasWeight && portionGrams.isFinite() && portionGrams > 0.0 && portionGrams <= 10000.0 && food.carbs >= 0 &&
            scale.isFinite() && (food.carbs * scale).isFinite() && proteinGrams.isFinite() && fatGrams.isFinite() &&
            (!enabled || (hasMacros && settings.isValid && eCarbsGrams in 0..maxCarbs))
}

/** Versioned food extension; portion weight and one-meal overrides are never saved. */
object FoodECarbsSettingsStore {
    private fun read(json: String): JsonObject = try {
        Json.parseToJsonElement(json) as? JsonObject ?: JsonObject(emptyMap())
    } catch (_: IllegalArgumentException) {
        JsonObject(emptyMap())
    }

    fun loadMetadata(json: String?): FoodECarbsSettings {
        val rule = json?.let { read(it) } ?: return FoodECarbsSettings()
        if ((rule["version"] as? JsonPrimitive)?.intOrNull != 1 || (rule["enabled"] as? JsonPrimitive)?.booleanOrNull != true)
            return FoodECarbsSettings()
        return load(JsonObject(mapOf("1" to rule)).toString(), 1)
    }

    fun metadata(settings: FoodECarbsSettings?, previous: String? = null, updatedAt: Long = Clock.System.now().toEpochMilliseconds()): String {
        val original = previous?.let { read(it) }
        val fields = original?.toMutableMap() ?: mutableMapOf()
        if (settings != null) {
            val rule = read(save("{}", 1, settings))["1"] as JsonObject
            fields.putAll(rule)
            if (settings.manualGramsPer100g == null) fields.remove("manualGramsPer100g")
        }
        fields["version"] = JsonPrimitive(1)
        fields["enabled"] = JsonPrimitive(settings != null)
        if (JsonObject(fields) != original) {
            val priorTime = (original?.get("updatedAt") as? JsonPrimitive)?.longOrNull ?: 0L
            fields["updatedAt"] = JsonPrimitive(maxOf(updatedAt, if (priorTime == Long.MAX_VALUE) priorTime else priorTime + 1))
        }
        return JsonObject(fields).toString()
    }

    fun load(json: String, foodId: Long): FoodECarbsSettings {
        val obj = read(json)[foodId.toString()] as? JsonObject ?: return FoodECarbsSettings()
        return try {
            val manual = obj["manualGramsPer100g"]?.let { it.jsonPrimitive.doubleOrNull ?: return FoodECarbsSettings() }
            FoodECarbsSettings(
                method = FoodECarbsMethod.valueOf(obj["method"]?.jsonPrimitive?.content ?: "CUSTOM"),
                proteinPercent = obj["proteinPercent"]?.jsonPrimitive?.intOrNull ?: 0,
                fatPercent = obj["fatPercent"]?.jsonPrimitive?.intOrNull ?: 0,
                delayMinutes = obj["delayMinutes"]?.jsonPrimitive?.intOrNull ?: 0,
                durationHours = obj["durationHours"]?.jsonPrimitive?.intOrNull ?: 0,
                manualGramsPer100g = manual
            ).takeIf { it.isValid } ?: FoodECarbsSettings()
        } catch (_: IllegalArgumentException) {
            FoodECarbsSettings()
        }
    }

    fun save(json: String, foodId: Long, settings: FoodECarbsSettings): String {
        require(foodId > 0 && settings.isValid)
        return JsonObject(read(json).toMutableMap().apply {
            put(foodId.toString(), buildJsonObject {
                put("method", settings.method.name)
                put("proteinPercent", settings.proteinPercent)
                put("fatPercent", settings.fatPercent)
                put("delayMinutes", settings.delayMinutes)
                put("durationHours", settings.durationHours)
                settings.manualGramsPer100g?.let { put("manualGramsPer100g", it) }
            })
        }).toString()
    }

    fun remove(json: String, foodId: Long): String = JsonObject(read(json).toMutableMap().apply { remove(foodId.toString()) }).toString()
}
