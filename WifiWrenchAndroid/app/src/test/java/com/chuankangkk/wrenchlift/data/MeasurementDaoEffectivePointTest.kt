package com.chuankangkk.wrenchlift.data

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeasurementDaoEffectivePointTest {
    private lateinit var database: WrenchDatabase
    private lateinit var dao: MeasurementDao

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication().applicationContext
        database = Room.inMemoryDatabaseBuilder(context, WrenchDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.measurementDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun unmarkLatestEffectivePoint_keepsRawPointsAndOnlyRemovesLatestMarker() = runTest {
        val sessionId = dao.insertSession(testSession())
        dao.insertTorquePoint(testPoint(sessionId, timestamp = 100L, rawHex = "C5 C5 01"))
        dao.insertTorquePoint(testPoint(sessionId, timestamp = 200L, rawHex = "C5 C5 02"))

        dao.markLatestPointEffective(sessionId)
        assertEquals(1, dao.countEffectivePoints(sessionId))

        dao.insertTorquePoint(testPoint(sessionId, timestamp = 300L, rawHex = "C5 C5 03"))
        dao.markLatestPointEffective(sessionId)
        assertEquals(2, dao.countEffectivePoints(sessionId))

        dao.unmarkLatestEffectivePoint(sessionId)

        val points = dao.observePoints(sessionId).first()
        assertEquals(1, dao.countEffectivePoints(sessionId))
        assertEquals(3, points.size)
        assertEquals(listOf("C5 C5 01", "C5 C5 02", "C5 C5 03"), points.map { it.rawHex })
        assertFalse(points.first { it.timestampMillis == 100L }.isEffectivePoint)
        assertTrue(points.first { it.timestampMillis == 200L }.isEffectivePoint)
        assertFalse(points.first { it.timestampMillis == 300L }.isEffectivePoint)
    }

    @Test
    fun snapshotPagesIncludeEveryPointAndExcludeLaterArrivals() = runTest {
        val sessionId = dao.insertSession(testSession())
        repeat(2505) { index ->
            dao.insertTorquePoint(testPoint(sessionId, timestamp = 5000L - index, rawHex = "row-$index"))
        }
        val upper = dao.lastPointId(sessionId)!!
        dao.insertTorquePoint(testPoint(sessionId, timestamp = 1L, rawHex = "later"))
        var cursor = 0L
        val exported = mutableListOf<TorquePointEntity>()
        while (cursor < upper) {
            val page = dao.pointsPage(sessionId, cursor, upper)
            assertTrue(page.size <= 1000)
            exported += page
            cursor = page.last().id
        }
        assertEquals(2505, exported.size)
        assertEquals((0..2504).map { "row-$it" }, exported.map { it.rawHex })
        assertEquals(2506, dao.observePoints(sessionId).first().size)
    }

    private fun testSession(): MeasurementEntity =
        MeasurementEntity(
            projectName = "WiFi扳手",
            lineName = "测试线路",
            sectionName = "测试区间",
            slabNo = "S1",
            pointNo = "P1",
            operatorName = "测试员",
            deviceSn = "SN001",
            startTime = 1L,
            endTime = null,
            connectionMode = "TCP",
            resultStatus = "采集中",
            finalTorqueNm = null,
            finalAngleDeg = null,
            finalForceKn = null,
            finalPressureMpa = null,
            finalDisplacementMm = null,
            wrenchResultType = null,
            wrenchResultCode = null,
            wrenchResultLabel = null,
            wrenchEmployeeId = null,
            wrenchBoltNo = null,
            wrenchMode = null,
            targetTorqueNm = null,
            actualTorqueNm = null,
            targetAngleDeg = null,
            actualAngleDeg = null,
            torqueUpperNm = null,
            torqueLowerNm = null,
            angleUpperDeg = null,
            angleLowerDeg = null,
            wrenchResultRawHex = null,
        )

    private fun testPoint(sessionId: Long, timestamp: Long, rawHex: String): TorquePointEntity =
        TorquePointEntity(
            sessionId = sessionId,
            timestampMillis = timestamp,
            boltNo = 1,
            torqueNm = timestamp / 10.0,
            angleRaw = timestamp.toInt(),
            angleDeg = timestamp / 2.0,
            forceKn = null,
            pressureMpa = null,
            displacementMm = 0.0,
            isEffectivePoint = false,
            rawHex = rawHex,
        )
}
