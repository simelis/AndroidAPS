package app.aaps.ui.compose.foodManagement

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsRule

/**
 * Weight in grams of [amount] of [unit], or null when the unit is not a weight (pieces, ml, ...).
 * AAPS never assumes a density or the weight of a piece or a cup.
 */
fun weightInGrams(amount: Double, unit: String): Double? = when (unit.trim().lowercase()) {
    "g", "gram", "grams" -> amount
    "kg"                 -> amount * 1000.0
    else                 -> null
}?.takeIf { it.isFinite() }

/** A blank value means unknown, so 0 must be entered for none. */
val FD.proteinKnown: Boolean get() = protein?.let { it >= 0 } == true
val FD.fatKnown: Boolean get() = fat?.let { it >= 0 } == true

/** True when this food has the nutrients that [rule] uses. */
fun FD.canUse(rule: FoodECarbsRule): Boolean = rule.canUse(proteinKnown, fatKnown)

/** An amount like 100 or 0.5 for display, without a needless ".0". */
fun Double.formatAmount(): String = if (this % 1.0 == 0.0) toLong().toString() else NumberFormat.UP_TO_1_DECIMAL.format(this)

/** An amount for an edit field: always with a dot and not rounded, so it reads back as the same number. */
fun Double.toEditText(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
