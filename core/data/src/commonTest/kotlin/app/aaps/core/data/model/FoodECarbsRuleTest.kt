package app.aaps.core.data.model

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        assertFalse(rule.copy(manualGrams = Double.NaN).isValid)
        assertFalse(rule.copy(manualGrams = 100.1).isValid)
        assertTrue(rule.copy(manualGrams = 7.5).isValid)
        assertFalse(rule.copy(carbsNowPercent = 49).isValid)
        assertFalse(rule.copy(carbsNowPercent = 101).isValid)
        assertTrue(rule.copy(carbsNowPercent = 50).isValid)
    }

    @Test
    fun aRuleNeedsOnlyTheNutrientsItUses() {
        // A fixed amount needs neither
        assertTrue(rule.copy(manualGrams = 5.0).canUse(proteinKnown = false, fatKnown = false))
        // FPU needs at least one; the unknown one counts as 0 g
        assertTrue(rule.canUse(proteinKnown = true, fatKnown = false))
        assertTrue(rule.canUse(proteinKnown = false, fatKnown = true))
        assertFalse(rule.canUse(proteinKnown = false, fatKnown = false))
        // Custom needs only the nutrients with a factor above 0
        val fatOnly = rule.copy(method = FoodECarbsMethod.CUSTOM, fatPercent = 30)
        assertTrue(fatOnly.canUse(proteinKnown = false, fatKnown = true))
        assertFalse(fatOnly.canUse(proteinKnown = true, fatKnown = false))
        assertTrue(rule.copy(method = FoodECarbsMethod.CUSTOM).canUse(proteinKnown = false, fatKnown = false))
    }

    @Test
    fun carbsNowKeepsThePercentAndRoundsDown() {
        assertEquals(50, rule.carbsNow(50))
        assertEquals(40, rule.copy(carbsNowPercent = 80).carbsNow(50))
        // 80 % of 47 g is 37.6 g: the bolus counts 37 g, so the 10 g left go to the eCarbs
        assertEquals(37, rule.copy(carbsNowPercent = 80).carbsNow(47))
    }

    @Test
    fun jsonRoundTripKeepsTheRule() {
        val manual = rule.copy(manualGrams = 7.5)
        assertEquals(manual, FoodECarbsRule.fromJson(manual.toJson()))
        assertEquals(manual, FoodECarbsRule.fromJsonString(manual.toJsonString()))
    }

    @Test
    fun unreadableOrInvalidJsonIsNoRule() {
        assertNull(FoodECarbsRule.fromJson(null))
        assertNull(FoodECarbsRule.fromJson(JsonNull))
        assertNull(FoodECarbsRule.fromJson(JsonPrimitive("FPU")))
        assertNull(FoodECarbsRule.fromJsonString("not json"))
        assertNull(FoodECarbsRule.fromJsonString("{\"method\":\"UNKNOWN\",\"durationHours\":3}"))
        assertNull(FoodECarbsRule.fromJsonString("{\"durationHours\":0}"))
        // Unknown fields from a newer AAPS are ignored
        assertEquals(FoodECarbsRule(durationHours = 3), FoodECarbsRule.fromJsonString("{\"durationHours\":3,\"newField\":1}"))
        // A rule saved before the carbs-now field existed counts all carbs now
        assertEquals(100, FoodECarbsRule.fromJsonString("{\"method\":\"FPU\",\"durationHours\":3}")?.carbsNowPercent)
    }
}
