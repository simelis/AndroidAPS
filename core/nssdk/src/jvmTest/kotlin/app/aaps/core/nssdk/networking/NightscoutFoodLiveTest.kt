package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.NSAndroidClientImpl
import app.aaps.core.nssdk.localmodel.food.NSFood
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

/**
 * Food sync against a real Nightscout. Skipped unless `NS_TEST_URL` and `NS_TEST_TOKEN` are set,
 * so normal test runs never touch a server. Use a throwaway Nightscout only: this writes foods.
 *
 * `NS_TEST_API_SECRET` is optional. With it, a food is also made through API v1, the way the
 * Nightscout web editor makes them.
 *
 * Two clients play AAPS and AAPSClient on one Nightscout.
 */
@EnabledIfEnvironmentVariable(named = "NS_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "NS_TEST_TOKEN", matches = ".+")
class NightscoutFoodLiveTest {

    private val url = System.getenv("NS_TEST_URL").trimEnd('/')
    private val token = System.getenv("NS_TEST_TOKEN")
    private fun client() = NSAndroidClientImpl(url, token, logging = false, logger = { }, dispatcher = Dispatchers.IO)
    private val master = client()
    private val follower = client()

    private val fpu = Json.parseToJsonElement("""{"method":"FPU","proteinPercent":0,"fatPercent":0,"delayMinutes":60,"durationHours":3,"manualGramsPer100g":null}""")
    private val runId = System.currentTimeMillis()

    private fun food(name: String, carbs: Int, identifier: String? = null, isValid: Boolean = true, rule: JsonElement? = null) =
        NSFood(date = System.currentTimeMillis(), identifier = identifier, isValid = isValid, name = name, portion = 100.0, carbs = carbs,
               fat = 10, protein = 20, unit = "g", aapsECarbsRule = rule)

    /** Newest food change time on the server, the same cursor LoadFoodsRunner starts from. */
    private suspend fun NSAndroidClientImpl.newestFood() = getLastModified().collections.food

    private suspend fun NSAndroidClientImpl.changesSince(from: Long) = getFoodsModifiedSince(from, 500).values
    private suspend fun NSAndroidClientImpl.listed(identifier: String) = getFoods(1000).values.firstOrNull { it.identifier == identifier }

    @Test
    fun `a food and its rule sync both ways, deletes reach the other device and a restore after a delete works`() = runBlocking {
        val start = master.newestFood()

        // AAPS adds a food with a rule
        val created = master.createFood(food("Pizza $runId", 30, rule = fpu))
        assertThat(created.response).isEqualTo(201)
        val id = created.identifier!!

        // AAPSClient sees it, rule included, both in the list and in the history
        assertThat(follower.listed(id)!!.aapsECarbsRule).isEqualTo(fpu)
        assertThat(follower.changesSince(start).single { it.identifier == id }.aapsECarbsRule).isEqualTo(fpu)

        // AAPSClient edits it and removes the rule
        val afterCreate = master.newestFood()
        assertThat(follower.updateFood(food("Pizza $runId", 35, identifier = id)).response).isEqualTo(200)
        val edited = master.changesSince(afterCreate).single { it.identifier == id }
        assertThat(edited.carbs).isEqualTo(35)
        assertThat(edited.aapsECarbsRule).isNull()

        // AAPS deletes it. The list hides it, only the history tells AAPSClient
        val afterEdit = master.newestFood()
        assertThat(master.updateFood(food("Pizza $runId", 35, identifier = id, isValid = false)).response).isEqualTo(200)
        assertThat(follower.listed(id)).isNull()
        assertThat(follower.changesSince(afterEdit).single { it.identifier == id }.isValid).isFalse()

        // AAPSClient, not knowing yet, edits it: Nightscout answers 410 Gone, and adding it again works
        assertThat(follower.updateFood(food("Pizza $runId", 40, identifier = id)).response).isEqualTo(410)
        val again = follower.createFood(food("Pizza $runId", 40, rule = fpu))
        assertThat(again.response).isEqualTo(201)
        assertThat(again.identifier).isNotEqualTo(id)
        assertThat(master.listed(again.identifier!!)!!.carbs).isEqualTo(40)
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "NS_TEST_API_SECRET", matches = ".+")
    fun `a food made in the Nightscout web editor can be edited and given a rule`() = runBlocking {
        val id = createWithApiV1("Bread $runId")
        val start = master.newestFood()

        // AAPS sees the web-editor food only in the full list, by its Mongo _id
        val listed = master.listed(id)!!
        assertThat(listed.carbs).isEqualTo(45)

        // It has no date and no app, so a PATCH must not send them - this is what used to fail
        assertThat(master.updateFood(listed.copy(carbs = 50, aapsECarbsRule = fpu)).response).isEqualTo(200)
        val changed = follower.changesSince(start).single { it.identifier == id }
        assertThat(changed.carbs).isEqualTo(50)
        assertThat(changed.aapsECarbsRule).isEqualTo(fpu)

        // A rule removed in AAPS is removed on the server, not kept
        assertThat(master.updateFood(listed.copy(carbs = 50, aapsECarbsRule = null)).response).isEqualTo(200)
        assertThat(follower.listed(id)!!.aapsECarbsRule).isAnyOf(null, JsonNull)
    }

    /** POST /api/v1/food with the API secret, like the Nightscout web editor. Returns the new _id. */
    private fun createWithApiV1(name: String): String {
        val secret = MessageDigest.getInstance("SHA-1").digest(System.getenv("NS_TEST_API_SECRET").toByteArray())
            .joinToString("") { "%02x".format(it) }
        val body = """{"type":"food","name":"$name","category":"","subcategory":"","unit":"g","portion":100,"carbs":45,"fat":0,"protein":0}"""
        val request = HttpRequest.newBuilder(URI("$url/api/v1/food"))
            .header("api-secret", secret).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build()
        // HTTP/1.1, because Node does not accept the HTTP/2 upgrade the JDK client tries on a POST
        val response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isEqualTo(200)
        return Json.parseToJsonElement(response.body()).jsonArray.single().jsonObject.getValue("_id").jsonPrimitive.content
    }
}
