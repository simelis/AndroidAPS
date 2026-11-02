package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.data.model.IDs
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientControlMessage

/** A food edited on the client → the wire message (client send side). Only what the user can change travels. */
internal fun FD.toFoodChange(requestId: String): ClientControlMessage.FoodChange = ClientControlMessage.FoodChange(
    requestId = requestId,
    nsId = ids.nightscoutId,
    isValid = isValid,
    name = name,
    category = category,
    subCategory = subCategory,
    portion = portion,
    unit = unit,
    carbs = carbs,
    fat = fat,
    protein = protein,
    energy = energy,
    gi = gi,
    askAmount = askAmount,
    eCarbsRule = eCarbsRule?.toJson()
)

/**
 * The food the master stores (master receive side): [current] with the client's values, or a new food when
 * [current] is null. The local ids stay the master's own.
 */
internal fun ClientControlMessage.FoodChange.toFood(current: FD?): FD = FD(
    id = current?.id ?: 0,
    version = current?.version ?: 0,
    dateCreated = current?.dateCreated ?: -1,
    isValid = isValid,
    referenceId = current?.referenceId,
    ids = current?.ids ?: IDs(),
    name = name,
    category = category,
    subCategory = subCategory,
    portion = portion,
    unit = unit,
    carbs = carbs,
    fat = fat,
    protein = protein,
    energy = energy,
    gi = gi,
    askAmount = askAmount,
    eCarbsRule = FoodECarbsRule.fromJson(eCarbsRule)
)
