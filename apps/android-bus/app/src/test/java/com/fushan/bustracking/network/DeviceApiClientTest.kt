package com.fushan.bustracking.network

import android.app.Application
import com.fushan.bustracking.data.PendingBoardingEventEntity
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DeviceApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DeviceApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DeviceApiClient(
            apiBaseUrl = server.url("/").toString(),
            deviceApiKey = "test-api-key",
            hardwareSerial = "TEST-SERIAL-01",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun permissionSnapshot_parsesRoutesAndEmployeeRouteAssignments() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "version": 42,
                  "bus": {"id": "bus-1", "code": "BUS-01", "name": "Morning Bus"},
                  "routes": [
                    {"id": "route-1", "code": "R01", "name": "North"},
                    {"id": "route-2", "code": "R02", "name": "South"}
                  ],
                  "employees": [
                    {
                      "id": "employee-1",
                      "employeeNo": "E001",
                      "name": "Alice",
                      "department": "QA",
                      "cardSn": "  CARD-01  ",
                      "routeIds": ["route-1", "route-2"]
                    }
                  ],
                  "stops": []
                }
                """.trimIndent(),
            ),
        )

        val snapshot = client.permissionSnapshot()

        val request = server.takeRequest()
        assertRequestIdentity(request.headers["Authorization"], request.headers["X-Device-Hardware-Serial"])
        assertEquals("GET", request.method)
        assertEquals("/permissions", request.path)
        assertEquals(42L, snapshot.version)
        assertEquals("bus-1", snapshot.busId)
        assertEquals(listOf("route-1", "route-2"), snapshot.routes.map { it.id })
        assertEquals("CARD-01", snapshot.employees.single().cardSn)
        assertEquals("QA", snapshot.employees.single().department)
        assertEquals("route-1,route-2", snapshot.employees.single().routeIds)
    }

    @Test
    fun uploadEvents_serializesRouteLocationAndNullableSnapshots() {
        server.enqueue(MockResponse().setBody("""{"acceptedIds":["event-1"],"rejected":[]}"""))
        val event = PendingBoardingEventEntity(
            id = "event-1",
            cardSn = "CARD-01",
            employeeId = null,
            result = "DENIED_NO_PERMISSION",
            scannedAt = 1_700_000_000_000,
            permissionVersion = 42,
            employeeNoSnapshot = null,
            employeeNameSnapshot = null,
            employeeDepartmentSnapshot = null,
            latitude = 25.033,
            longitude = 121.5654,
            locationRecordedAt = 1_700_000_000_500,
            locationSource = "CURRENT",
            accuracyMeters = 4.5f,
            routeIds = "route-1,route-2",
        )

        val response = client.uploadEvents(listOf(event))

        val request = server.takeRequest()
        assertRequestIdentity(request.headers["Authorization"], request.headers["X-Device-Hardware-Serial"])
        assertEquals("POST", request.method)
        assertEquals("/boarding-events/batch", request.path)
        assertEquals(listOf("event-1"), response.accepted)
        val payload = JSONObject(request.body.readUtf8()).getJSONArray("events").getJSONObject(0)
        assertEquals("2023-11-14T22:13:20Z", payload.getString("scannedAt"))
        assertEquals("2023-11-14T22:13:20.500Z", payload.getString("locationRecordedAt"))
        assertEquals(listOf("route-1", "route-2"), List(2) { payload.getJSONArray("routeIds").getString(it) })
        assertTrue(payload.isNull("employeeId"))
        assertTrue(payload.isNull("employeeName"))
        assertEquals(25.033, payload.getDouble("latitude"), 0.000001)
    }

    @Test
    fun acknowledgeConfiguration_postsVersionAndAuthenticationHeaders() {
        server.enqueue(MockResponse().setResponseCode(204))

        client.acknowledgeConfiguration(77)

        val request = server.takeRequest()
        assertRequestIdentity(request.headers["Authorization"], request.headers["X-Device-Hardware-Serial"])
        assertEquals("POST", request.method)
        assertEquals("/configuration-ack", request.path)
        assertEquals(77L, JSONObject(request.body.readUtf8()).getLong("version"))
    }

    private fun assertRequestIdentity(authorization: String?, hardwareSerial: String?) {
        assertEquals("Bearer test-api-key", authorization)
        assertEquals("TEST-SERIAL-01", hardwareSerial)
    }
}
