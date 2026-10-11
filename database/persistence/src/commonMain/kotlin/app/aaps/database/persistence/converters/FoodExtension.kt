package app.aaps.database.persistence.converters

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.database.entities.Food
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val ruleJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** A rule that cannot be read or is not valid is treated as no rule, never as a guessed one. */
private fun String.toECarbsRule(): FoodECarbsRule? =
    try {
        ruleJson.decodeFromString<FoodECarbsRule>(this).takeIf { it.isValid }
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

fun Food.fromDb(): FD =
    FD(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        name = this.name,
        category = this.category,
        subCategory = this.subCategory,
        portion = this.portion,
        carbs = this.carbs,
        fat = this.fat,
        protein = this.protein,
        energy = this.energy,
        unit = this.unit,
        gi = this.gi,
        eCarbsRule = this.eCarbsRuleJson?.toECarbsRule(),
        ids = this.interfaceIDs.fromDb()
    )

fun FD.toDb(): Food =
    Food(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        name = this.name,
        category = this.category,
        subCategory = this.subCategory,
        portion = this.portion,
        carbs = this.carbs,
        fat = this.fat,
        protein = this.protein,
        energy = this.energy,
        unit = this.unit,
        gi = this.gi,
        eCarbsRuleJson = this.eCarbsRule?.let { ruleJson.encodeToString(it) },
        interfaceIDs_backing = this.ids.toDb()
    )
