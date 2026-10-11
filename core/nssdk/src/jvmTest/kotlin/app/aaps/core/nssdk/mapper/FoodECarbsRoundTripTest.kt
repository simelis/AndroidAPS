package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.nssdk.nsSdkJson
import app.aaps.core.nssdk.remotemodel.RemoteFood
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

class FoodECarbsRoundTripTest {
    @Test
    fun `upload download preserves the complete namespaced rule including unknown future fields`() {
        val rule = Json.parseToJsonElement("""{"version":1,"enabled":true,"method":"CUSTOM","proteinPercent":25,"fatPercent":10,"delayMinutes":60,"durationHours":3,"manualGramsPer100g":7.5,"future":{"x":true}}""")
        val original = NSFood(date = 1700000000000, identifier = "ns-id", isValid = true, name = "Meal", portion = 100.0,
            carbs = 25, fat = 5, protein = 10, aapsFoodECarbs = rule)
        val upload = nsSdkJson.encodeToString(RemoteFood.serializer(), original.toRemoteFood())
        assertThat(Json.parseToJsonElement(upload).jsonObject["aapsFoodECarbs"]).isEqualTo(rule)
        assertThat(upload.toNSFood()?.aapsFoodECarbs).isEqualTo(rule)
    }

    @Test
    fun `legacy and malformed extensions do not prevent parsing the standard food`() {
        listOf("", ",\"aapsFoodECarbs\":null", ",\"aapsFoodECarbs\":false", ",\"aapsFoodECarbs\":{\"version\":99}").forEach { extra ->
            val food = """{"type":"food","name":"Meal","portion":100,"carbs":25$extra}""".toNSFood()
            assertThat(food?.carbs).isEqualTo(25)
        }
    }
}
