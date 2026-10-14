package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule

/** Grams of [amount] of [unit], or null when the unit is not a weight: no density or piece weight is assumed. */
fun weightInGrams(amount: Double, unit: String): Double? = when (unit.trim().lowercase()) {
    "g", "gram", "grams" -> amount
    "kg"                 -> amount * 1000.0
    else                 -> null
}?.takeIf { it.isFinite() }

/** A blank value means unknown, so 0 must be entered for none. */
val FD.proteinKnown: Boolean get() = protein?.let { it >= 0 } == true
val FD.fatKnown: Boolean get() = fat?.let { it >= 0 } == true

fun FD.canUse(rule: FoodECarbsRule): Boolean = rule.canUse(proteinKnown, fatKnown)

fun Double.formatAmount(): String = if (this % 1.0 == 0.0) toLong().toString() else NumberFormat.UP_TO_1_DECIMAL.format(this)

/** Not rounded and not localized, so the edit field reads back the same number. */
fun Double.toEditText(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
