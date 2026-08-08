package com.fushan.bustracking.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DeviceStateEntity::class,
        PermissionEntity::class,
        PendingGpsEntity::class,
        PendingBoardingEventEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class BusDatabase : RoomDatabase() {
    abstract fun deviceState(): DeviceStateDao
    abstract fun permissions(): PermissionDao
    abstract fun gps(): GpsDao
    abstract fun boardingEvents(): BoardingEventDao

    companion object {
        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE device_state ADD COLUMN route_ids TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE device_state ADD COLUMN route_names TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE permission_cache ADD COLUMN department TEXT NOT NULL DEFAULT 'Unassigned'")
                db.execSQL("ALTER TABLE permission_cache ADD COLUMN route_ids TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN event_type TEXT NOT NULL DEFAULT 'BOARDING'")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN employee_no_snapshot TEXT")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN employee_name_snapshot TEXT")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN employee_department_snapshot TEXT")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN latitude REAL")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN longitude REAL")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN location_recorded_at INTEGER")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN location_source TEXT NOT NULL DEFAULT 'UNAVAILABLE'")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN accuracy_meters REAL")
                db.execSQL("ALTER TABLE pending_boarding_event ADD COLUMN route_ids TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: BusDatabase? = null

        fun get(context: Context): BusDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BusDatabase::class.java,
                    "bus-tracking.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
