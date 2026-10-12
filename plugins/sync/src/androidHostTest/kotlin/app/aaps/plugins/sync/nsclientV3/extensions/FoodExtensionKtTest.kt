package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.FD
import app.aaps.core.data.model.FoodECarbsMethod
import app.aaps.core.data.model.FoodECarbsRule
import app.aaps.core.data.model.IDs
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.plugins.sync.extensions.contentEqualsTo
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

internal class FoodExtensionKtTest {

    @Test
    fun toFood() {
        val food = FD(
            isValid = true,
            name = "name",
            category = "category",
            subCategory = "subcategory",
            portion = 2.0,
            carbs = 20,
            fat = 21,
            protein = 22,
            energy = 23,
            unit = "g",
            gi = 25,
            eCarbsRule = FoodECarbsRule(method = FoodECarbsMethod.FPU, delayMinutes = 60, durationHours = 3, manualGrams = 7.5),
            ids = IDs(
                nightscoutId = "nightscoutId"
            )
        )

        val food2 = (food.toNSFood().convertToRemoteAndBack() as NSFood).toFood()
        assertThat(food.contentEqualsTo(food2)).isTrue()
        assertThat(food.ids.contentEqualsTo(food2.ids)).isTrue()
        assertThat(food2.eCarbsRule).isEqualTo(food.eCarbsRule)
    }

    @Test
    fun `a missing, null or unreadable rule from Nightscout is no rule`() {
        val food = FD(name = "name", portion = 100.0, carbs = 20, ids = IDs(nightscoutId = "id"))
        assertThat((food.toNSFood().convertToRemoteAndBack() as NSFood).toFood().eCarbsRule).isNull()
        assertThat(food.toNSFood().copy(aapsECarbsRule = JsonNull).toFood().eCarbsRule).isNull()
        assertThat(food.toNSFood().copy(aapsECarbsRule = JsonPrimitive("FPU")).toFood().eCarbsRule).isNull()
    }

    @Test
    fun `asking for the amount is synced, and a food that does not say is asked every time when its unit is a weight`() {
        val slice = FD(name = "Slice", portion = 30.0, unit = "g", carbs = 15, askAmount = false, ids = IDs(nightscoutId = "id"))
        val back = (slice.toNSFood().convertToRemoteAndBack() as NSFood).toFood()
        assertThat(back.askAmount).isFalse()
        assertThat(back.contentEqualsTo(slice)).isTrue()
        // Like a food from the Nightscout web editor
        assertThat(slice.toNSFood().copy(aapsAskAmount = null).toFood().askAmount).isTrue()
        assertThat(slice.copy(unit = "piece").toNSFood().copy(aapsAskAmount = null).toFood().askAmount).isFalse()
    }
}
