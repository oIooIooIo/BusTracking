package com.fushan.bustracking.data

import android.content.Context
import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BusDatabaseMigrationTest {
    private lateinit var context: Context
    private lateinit var database: BusDatabase
    private val databaseName = "bus-database-migration-test"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration1To2_preservesRowsAndAddsRouteLocationDefaults() = runBlocking {
        createVersionOneDatabase()

        database = Room.databaseBuilder(context, BusDatabase::class.java, databaseName)
            .addMigrations(BusDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        val state = database.deviceState().get()!!
        val permission = database.permissions().find("CARD-01")!!
        val event = database.boardingEvents().pending(10).single()

        assertEquals(7L, state.sequenceCounter)
        assertEquals(12L, state.permissionVersion)
        assertEquals("", state.routeIds)
        assertEquals("", state.routeNames)
        assertEquals("Alice", permission.employeeName)
        assertEquals("Unassigned", permission.department)
        assertEquals("", permission.routeIds)
        assertEquals("BOARDING", event.eventType)
        assertEquals("UNAVAILABLE", event.locationSource)
        assertEquals("", event.routeIds)
        assertNull(event.latitude)
        assertNull(event.locationRecordedAt)
    }

    private fun createVersionOneDatabase() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS device_state (
                            id INTEGER NOT NULL,
                            sequence_counter INTEGER NOT NULL,
                            permission_version INTEGER,
                            bus_id TEXT,
                            bus_code TEXT,
                            bus_name TEXT,
                            permission_synced_at INTEGER,
                            dropped_gps_count INTEGER NOT NULL,
                            PRIMARY KEY(id)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS permission_cache (
                            card_sn TEXT NOT NULL,
                            employee_id TEXT NOT NULL,
                            employee_no TEXT NOT NULL,
                            employee_name TEXT NOT NULL,
                            PRIMARY KEY(card_sn)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS pending_gps (
                            sequence_no INTEGER NOT NULL,
                            recorded_at INTEGER NOT NULL,
                            latitude REAL NOT NULL,
                            longitude REAL NOT NULL,
                            accuracy_meters REAL,
                            sync_status TEXT NOT NULL,
                            retry_count INTEGER NOT NULL,
                            last_attempt_at INTEGER,
                            error_code TEXT,
                            PRIMARY KEY(sequence_no)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS pending_boarding_event (
                            id TEXT NOT NULL,
                            card_sn TEXT NOT NULL,
                            employee_id TEXT,
                            result TEXT NOT NULL,
                            scanned_at INTEGER NOT NULL,
                            permission_version INTEGER,
                            sync_status TEXT NOT NULL,
                            retry_count INTEGER NOT NULL,
                            last_attempt_at INTEGER,
                            error_code TEXT,
                            PRIMARY KEY(id)
                        )
                        """.trimIndent(),
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        helper.writableDatabase.apply {
            execSQL(
                """
                INSERT INTO device_state (
                    id, sequence_counter, permission_version, bus_id, bus_code, bus_name,
                    permission_synced_at, dropped_gps_count
                ) VALUES (1, 7, 12, 'bus-1', 'BUS-01', 'Morning Bus', 1000, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO permission_cache (card_sn, employee_id, employee_no, employee_name)
                VALUES ('CARD-01', 'employee-1', 'E001', 'Alice')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO pending_boarding_event (
                    id, card_sn, employee_id, result, scanned_at, permission_version,
                    sync_status, retry_count, last_attempt_at, error_code
                ) VALUES (
                    'event-1', 'CARD-01', 'employee-1', 'ALLOWED', 1700000000000, 12,
                    'PENDING', 0, NULL, NULL
                )
                """.trimIndent(),
            )
        }
        helper.close()
    }
}
