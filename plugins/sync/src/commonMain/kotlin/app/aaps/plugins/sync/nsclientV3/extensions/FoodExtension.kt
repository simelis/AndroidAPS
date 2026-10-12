package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.IDs
import app.aaps.core.nssdk.localmodel.food.NSFood
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
        // Not in Nightscout yet: only a food in g or kg asks for the amount every time
        askAmount = FD.isWeightUnit(unit),
        ids = IDs(nightscoutId = identifier)
    )

fun FD.toNSFood(): NSFood =
    NSFood(
        date = Clock.System.now().toEpochMilliseconds(),
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
        identifier = ids.nightscoutId,
    )
