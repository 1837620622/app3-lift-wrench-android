package com.chuankangkk.wrenchlift.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MeasurementEntity::class, TorquePointEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class WrenchDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao

    companion object {
        @Volatile
        private var instance: WrenchDatabase? = null

        fun getInstance(context: Context): WrenchDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WrenchDatabase::class.java,
                    "wrench_lift.sqlite",
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN wrenchResultType TEXT")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN wrenchResultCode INTEGER")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN wrenchResultLabel TEXT")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN wrenchEmployeeId TEXT")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN wrenchBoltNo INTEGER")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN wrenchMode INTEGER")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN targetTorqueNm REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN actualTorqueNm REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN targetAngleDeg REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN actualAngleDeg REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN torqueUpperNm REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN torqueLowerNm REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN angleUpperDeg REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN angleLowerDeg REAL")
                db.execSQL("ALTER TABLE measurement_sessions ADD COLUMN wrenchResultRawHex TEXT")
            }
        }
    }
}
