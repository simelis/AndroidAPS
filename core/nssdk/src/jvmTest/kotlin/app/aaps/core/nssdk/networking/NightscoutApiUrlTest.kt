package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.remotemodel.RemoteFood
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The same URL expectations as `NsSdkUrlContractTest`, asserted against the **Ktor** endpoint class
 * directly, before it is wired into `NSAndroidClientImpl`.
 *
 * Running it separately means the riskiest single question - does Ktor keep the `$` in `date$gt`, or
 * turn it into `%24`? - is answered before the swap rather than during it. A percent-encoded operator
 * is not an error to Nightscout: it is an unknown filter, which is ignored, and the server answers
 * **200 with unfiltered rows**.
 *
 * Every expected string here is copied from the Retrofit contract test, which is green against the
 * live stack. When both files agree, the URL half of the port is done.
 */
class NightscoutApiUrlTest {

    private lateinit var server: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var api: NightscoutApi

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = NsKtorClient.build(logging = false, logger = { })
        api = NightscoutApi(httpClient, NsUrl.toBaseUrl(server.url("/").toString().trimEnd('/')))
    }

    @AfterEach
    fun tearDown() {
        httpClient.close()
        server.close()
    }

    private suspend fun pathOf(body: String = """{"result":[]}""", call: suspend () -> Unit): String {
        server.enqueue(MockResponse.Builder().code(200).body(body).build())
        runCatching { call() }
        return server.takeRequest().target
    }

    // ---------------------------------------------------------------- the $ operators

    @Test
    fun `entries keep their dollar operators and static parts`() = runTest {
        assertThat(pathOf { api.getSgvs() })
            .isEqualTo("/api/v3/entries?sort\$desc=date&type=sgv")

        assertThat(pathOf { api.getSgvsNewerThan(1700000000000, 250) })
            .isEqualTo("/api/v3/entries?sort=date&date\$gt=1700000000000&limit=250")

        assertThat(pathOf { api.getSgvsModifiedSince(1700000000000, 250) })
            .isEqualTo("/api/v3/entries/history/1700000000000?limit=250")
    }

    @Test
    fun `treatments keep their dollar operators and static parts`() = runTest {
        assertThat(pathOf { api.getTreatmentsNewerThan("2024-01-01T00:00:00.000Z", 250) })
            .isEqualTo("/api/v3/treatments?sort=created_at&created_at\$gt=2024-01-01T00:00:00.000Z&limit=250")

        assertThat(pathOf { api.getTreatmentsModifiedSince(1700000000000, 250) })
            .isEqualTo("/api/v3/treatments/history/1700000000000?limit=250")
    }

    @Test
    fun `getLastProfile keeps sort and limit`() = runTest {
        assertThat(pathOf { api.getLastProfile() })
            .isEqualTo("/api/v3/profile?sort\$desc=date&limit=1")
    }

    @Test
    fun `getProfileModifiedSince keeps the default limit`() = runTest {
        assertThat(pathOf { api.getProfileModifiedSince(1700000000000) })
            .isEqualTo("/api/v3/profile/history/1700000000000?limit=10")
    }

    // ---------------------------------------------------------------- settings

    @Test
    fun `settings endpoints`() = runTest {
        assertThat(pathOf(body = """{"result":{}}""") { api.getSetting("aaps") })
            .isEqualTo("/api/v3/settings/aaps")

        assertThat(pathOf { api.getSettingsModifiedSince(1700000000000, 100) })
            .isEqualTo("/api/v3/settings/history/1700000000000?limit=100")

        assertThat(pathOf { api.searchSettings(100) })
            .isEqualTo("/api/v3/settings?limit=100")
    }

    /** Absence of `permanent` is what makes the delete a soft delete. */
    @Test
    fun `a soft delete sends no permanent parameter`() = runTest {
        val path = pathOf(body = """{}""") { api.deleteSetting("aaps_x", null) }

        assertThat(path).isEqualTo("/api/v3/settings/aaps_x")
        assertThat(path).doesNotContain("permanent")
    }

    @Test
    fun `a permanent delete sends permanent true`() = runTest {
        assertThat(pathOf(body = """{}""") { api.deleteSetting("aaps_x", true) })
            .isEqualTo("/api/v3/settings/aaps_x?permanent=true")
    }

    // ---------------------------------------------------------------- writes

    @Test
    fun `write endpoints target the right paths`() = runTest {
        assertThat(pathOf(body = """{}""") { api.createSetting(JsonObject(emptyMap())) })
            .isEqualTo("/api/v3/settings")

        assertThat(pathOf(body = """{}""") { api.updateSetting(JsonObject(emptyMap()), "aaps") })
            .isEqualTo("/api/v3/settings/aaps")

        assertThat(pathOf(body = """{}""") { api.createProfile(JsonObject(emptyMap())) })
            .isEqualTo("/api/v3/profile")
    }

    /** An identifier with characters that would otherwise split the path must stay one segment. */
    @Test
    fun `an identifier is percent encoded into a single path segment`() = runTest {
        val path = pathOf(body = """{"result":{}}""") { api.getSetting("a/b c") }

        assertThat(path).startsWith("/api/v3/settings/")
        assertThat(path.removePrefix("/api/v3/settings/")).isEqualTo("a%2Fb%20c")
    }

    @Test
    fun `food patches address exactly one encoded identifier and omit immutable fields`() = runTest {
        val rule = Json.parseToJsonElement("""{"version":1,"enabled":true,"manualGramsPer100g":7.5}""")
        val food = RemoteFood(name = "Apple", portion = 100.0, carbs = 12, identifier = "x", isValid = true, isReadOnly = false,
            date = 123456789L, app = "AAPS", aapsFoodECarbs = rule)
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        assertThat(api.updateFood(food, "a/b c").isSuccessful).isTrue()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.target).isEqualTo("/api/v3/food/a%2Fb%20c")
        val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
        assertThat(body["aapsFoodECarbs"]).isEqualTo(rule)
        assertThat(body["fat"]).isEqualTo(JsonNull)
        listOf("date", "identifier", "app", "device", "isValid", "srvModified").forEach { assertThat(body).doesNotContainKey(it) }
    }

    @Test
    fun `food deletes address one identifier and empty identifiers never send a request`() = runTest {
        val food = RemoteFood(name = "Apple", portion = 100.0, carbs = 12, identifier = "x", isValid = true, isReadOnly = false)
        assertThat(runCatching { api.updateFood(food, " ") }.isFailure).isTrue()
        assertThat(runCatching { api.deleteFood("") }.isFailure).isTrue()
        assertThat(server.requestCount).isEqualTo(0)
        server.enqueue(MockResponse.Builder().code(200).body("{}").build())
        api.deleteFood("a/b")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.target).isEqualTo("/api/v3/food/a%2Fb")
    }
}
