package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.core.nssdk.localmodel.food.NSFood
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlin.time.Clock

fun NSFood.toFood(): FD =
    FD(
        isValid = isValid,
        name = name,
        category = category,
        subCategory = subCategory,
        portion = portion,
        carbs = carbs,
        fat = fat,
        protein = protein,
        energy = energy,
        unit = unit,
        gi = gi,
        aapsFoodECarbs = aapsFoodECarbs?.takeUnless { it == JsonNull }?.toString(),
        ids = IDs(nightscoutId = identifier)
    )

fun FD.toNSFood(): NSFood =
    NSFood(
        date = dateCreated.takeIf { it > 0 } ?: Clock.System.now().toEpochMilliseconds(),
        isValid = isValid,
        name = name,
        category = category,
        subCategory = subCategory,
        portion = portion,
        carbs = carbs,
        fat = fat,
        protein = protein,
        energy = energy,
        unit = unit,
        gi = gi,
        aapsFoodECarbs = aapsFoodECarbs?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() },
        identifier = ids.nightscoutId,
    )
