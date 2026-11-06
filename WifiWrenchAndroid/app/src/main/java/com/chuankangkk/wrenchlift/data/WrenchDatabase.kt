package com.chuankangkk.wrenchlift.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MeasurementEntity::class, TorquePointEntity::class, FrameEventEntity::class],
    version = 4,
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }

        /** 第 4 版：新增通信记录表（只增表，不动已有数据）。 */
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `frame_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, " +
                        "`timestampMillis` INTEGER NOT NULL, `direction` TEXT NOT NULL, `functionCode` INTEGER, `summary` TEXT NOT NULL, " +
                        "`rawHex` TEXT NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `measurement_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_frame_events_sessionId` ON `frame_events` (`sessionId`)")
            }
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
