package com.company.bustracking.data

import android.content.Context
import android.app.Application
import android.location.Location
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.company.bustracking.tracking.ScanLocationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LocalStoreTest {
    private lateinit var database: BusDatabase
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BusDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = LocalStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replacePermissions_enablesAllowedAndDeniedCardDecisions() = runBlocking {
        store.replacePermissions(snapshot())

        val allowed = store.checkCard("CARD-01")
        val denied = store.checkCard("UNKNOWN")
        val state = database.deviceState().get()

        assertEquals("ALLOWED", allowed.result)
        assertEquals("employee-1", allowed.employee?.employeeId)
        assertEquals(42L, allowed.permissionVersion)
        assertEquals("route-1", allowed.routeIds)
        assertEquals("DENIED_NO_PERMISSION", denied.result)
        assertEquals("route-1,route-2", denied.routeIds)
        assertEquals("route-1,route-2", state?.routeIds)
        assertEquals("North, South", state?.routeNames)
        assertEquals(1, database.permissions().count())
    }

    @Test
    fun checkCard_withoutAssignedRoutes_reportsAuthDataNotReady() = runBlocking {
        store.replacePermissions(snapshot(routes = emptyList()))

        val decision = store.checkCard("CARD-01")

        assertEquals("AUTH_DATA_NOT_READY", decision.result)
        assertNull(decision.employee)
        assertNull(decision.permissionVersion)
        assertEquals("", decision.routeIds)
    }

    @Test
    fun recordBoarding_capturesEmployeeRouteAndLocationSnapshot() = runBlocking {
        store.replacePermissions(snapshot())
        val decision = store.checkCard("CARD-01")
        val location = Location("test").apply {
            latitude = 25.033
            longitude = 121.5654
            time = 1_700_000_000_500
            accuracy = 4.5f
        }

        val id = store.recordBoarding(
            cardSn = "CARD-01",
            decision = decision,
            scannedAt = 1_700_000_000_000,
            capture = ScanLocationProvider.Capture(location, "CURRENT"),
        )

        val event = database.boardingEvents().pending(10).single()
        assertNotNull(id)
        assertEquals(id, event.id)
        assertEquals("employee-1", event.employeeId)
        assertEquals("E001", event.employeeNoSnapshot)
        assertEquals("Alice", event.employeeNameSnapshot)
        assertEquals("QA", event.employeeDepartmentSnapshot)
        assertEquals("route-1", event.routeIds)
        assertEquals(25.033, event.latitude!!, 0.000001)
        assertEquals(121.5654, event.longitude!!, 0.000001)
        assertEquals(1_700_000_000_500, event.locationRecordedAt)
        assertEquals("CURRENT", event.locationSource)
        assertEquals(4.5f, event.accuracyMeters)
    }

    private fun snapshot(
        routes: List<LocalStore.RouteData> = listOf(
            LocalStore.RouteData("route-1", "R01", "North"),
            LocalStore.RouteData("route-2", "R02", "South"),
        ),
    ) = LocalStore.PermissionSnapshotData(
        version = 42,
        busId = "bus-1",
        busCode = "BUS-01",
        busName = "Morning Bus",
        routes = routes,
        employees = listOf(
            PermissionEntity(
                cardSn = "CARD-01",
                employeeId = "employee-1",
                employeeNo = "E001",
                employeeName = "Alice",
                department = "QA",
                routeIds = "route-1",
            ),
        ),
    )
}
