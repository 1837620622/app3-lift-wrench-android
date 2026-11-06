package com.chuankangkk.wrenchlift.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 旧版（第 3 版）数据库升级到第 4 版：原有会话与过程点不丢，通信记录表可用。
 * 建表语句与 Room 生成的 v3 表结构一致（MeasurementEntity / TorquePointEntity 在 v3→v4 之间未变）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WrenchDatabaseMigrationTest {
    private val name = "migration_v3_test.sqlite"

    @Test fun v3DatabaseUpgradesWithoutLosingSessionsAndGainsFrameEvents() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `measurement_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectName` TEXT NOT NULL, `lineName` TEXT NOT NULL, `sectionName` TEXT NOT NULL, `slabNo` TEXT NOT NULL, `pointNo` TEXT NOT NULL, `operatorName` TEXT NOT NULL, `deviceSn` TEXT NOT NULL, `startTime` INTEGER NOT NULL, `endTime` INTEGER, `connectionMode` TEXT NOT NULL, `resultStatus` TEXT NOT NULL, `finalTorqueNm` REAL, `finalAngleDeg` REAL, `finalForceKn` REAL, `finalPressureMpa` REAL, `finalDisplacementMm` REAL, `wrenchResultType` TEXT, `wrenchResultCode` INTEGER, `wrenchResultLabel` TEXT, `wrenchEmployeeId` TEXT, `wrenchBoltNo` INTEGER, `wrenchMode` INTEGER, `targetTorqueNm` REAL, `actualTorqueNm` REAL, `targetAngleDeg` REAL, `actualAngleDeg` REAL, `torqueUpperNm` REAL, `torqueLowerNm` REAL, `angleUpperDeg` REAL, `angleLowerDeg` REAL, `wrenchResultRawHex` TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `torque_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `timestampMillis` INTEGER NOT NULL, `boltNo` INTEGER NOT NULL, `torqueNm` REAL NOT NULL, `angleRaw` INTEGER NOT NULL, `angleDeg` REAL NOT NULL, `forceKn` REAL, `pressureMpa` REAL, `displacementMm` REAL NOT NULL, `isEffectivePoint` INTEGER NOT NULL, `rawHex` TEXT NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `measurement_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_torque_points_sessionId` ON `torque_points` (`sessionId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_torque_points_timestampMillis` ON `torque_points` (`timestampMillis`)")
            db.execSQL("INSERT INTO measurement_sessions (projectName, lineName, sectionName, slabNo, pointNo, operatorName, deviceSn, startTime, connectionMode, resultStatus) VALUES ('', '', '', '', '', '', '2026110501', 1000, 'TCP', '合格')")
            db.execSQL("INSERT INTO torque_points (sessionId, timestampMillis, boltNo, torqueNm, angleRaw, angleDeg, displacementMm, isEffectivePoint, rawHex) VALUES (1, 1000, 1, 12.5, 3, 3.0, 0.0, 0, 'C5 C5 01 12')")
            db.version = 3
        }

        val database = Room.databaseBuilder(context, WrenchDatabase::class.java, name)
            .addMigrations(WrenchDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            // 打开即触发迁移；表结构不符时 Room 会在这里抛错。
            database.openHelper.writableDatabase
            val dao = database.measurementDao()
            assertNotNull("原有会话保留", dao.getSession(1))
            assertEquals("原有过程点保留", 1L, dao.lastPointId(1))
            dao.insertFrameEvents(
                listOf(FrameEventEntity(sessionId = 1, timestampMillis = 2000, direction = "收", functionCode = 0x15, summary = "结果", rawHex = "C5 C5 01 15")),
            )
            assertEquals("通信记录可写入", 1L, dao.lastFrameEventId(1))
            assertEquals(1L, dao.frameEventsPage(1, 0, 10).single().id)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
