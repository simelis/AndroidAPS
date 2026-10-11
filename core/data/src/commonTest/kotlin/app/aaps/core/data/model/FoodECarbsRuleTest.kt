package app.aaps.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FoodECarbsRuleTest {

    private val rule = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3)

    @Test
    fun referenceMethodsAndCustomFactorsGiveTheirOwnEquivalents() {
        assertEquals(17.0, rule.equivalentGrams(20.0, 10.0))
        assertEquals(8.5, rule.copy(method = FoodECarbsMethod.MODIFIED_FPU).equivalentGrams(20.0, 10.0))
        assertEquals(6.0, rule.copy(method = FoodECarbsMethod.CUSTOM, proteinPercent = 25, fatPercent = 10).equivalentGrams(20.0, 10.0))
    }

    @Test
    fun negativeOrNotFiniteAmountsGiveNoEquivalent() {
        assertEquals(0.0, rule.equivalentGrams(-1.0, 10.0))
        assertEquals(0.0, rule.equivalentGrams(20.0, Double.NaN))
    }

    @Test
    fun newRuleIsNotValidUntilADurationIsChosen() {
        assertFalse(FoodECarbsRule().isValid)
        assertTrue(rule.isValid)
    }

    @Test
    fun valuesOutsideTheLimitsAreNotValid() {
        assertFalse(rule.copy(durationHours = 11).isValid)
        assertFalse(rule.copy(delayMinutes = -1).isValid)
        assertFalse(rule.copy(delayMinutes = 721).isValid)
        assertFalse(rule.copy(proteinPercent = 101).isValid)
        assertFalse(rule.copy(manualGramsPer100g = Double.NaN).isValid)
        assertFalse(rule.copy(manualGramsPer100g = 100.1).isValid)
        assertTrue(rule.copy(manualGramsPer100g = 7.5).isValid)
    }
}
