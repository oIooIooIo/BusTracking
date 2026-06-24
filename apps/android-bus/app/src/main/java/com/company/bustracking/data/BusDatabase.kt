package com.company.bustracking.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceStateEntity::class,
        PermissionEntity::class,
        PendingGpsEntity::class,
        PendingBoardingEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class BusDatabase : RoomDatabase() {
    abstract fun deviceState(): DeviceStateDao
    abstract fun permissions(): PermissionDao
    abstract fun gps(): GpsDao
    abstract fun boardingEvents(): BoardingEventDao

    companion object {
        @Volatile
        private var instance: BusDatabase? = null

        fun get(context: Context): BusDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BusDatabase::class.java,
                    "bus-tracking.db",
                ).build().also { instance = it }
            }
    }
}
