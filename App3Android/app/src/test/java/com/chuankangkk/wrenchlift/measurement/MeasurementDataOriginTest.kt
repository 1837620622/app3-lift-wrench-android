package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.connection.ConnectionState
import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint
import org.junit.Assert.assertEquals
import org.junit.Test

class MeasurementDataOriginTest {
    @Test
    fun waitingWhenNoFrameAndNoPoint() {
        assertEquals(MeasurementDataOrigin.Waiting, MeasurementState().dataOrigin())
    }

    @Test
    fun framesOnlyWhenDeviceFrameArrivesBeforeProcessPoint() {
        val state = MeasurementState(receivedFrameCount = 1)

        assertEquals(MeasurementDataOrigin.FramesOnly, state.dataOrigin())
    }

    @Test
    fun loadedPointsWhenHistoryHasPointsButNoLiveFrame() {
        val state = MeasurementState(recentPoints = listOf(samplePoint()))

        assertEquals(MeasurementDataOrigin.LoadedPoints, state.dataOrigin())
    }

    @Test
    fun retainedRealPointsWhenDisconnectedAfterReceivingProcessPoint() {
        val state = MeasurementState(
            receivedFrameCount = 2,
            lastCurvePointReceiveTime = 1_700_000_000_100,
            recentPoints = listOf(samplePoint()),
        )

        assertEquals(MeasurementDataOrigin.RetainedRealPoints, state.dataOrigin())
    }

    @Test
    fun livePointsOnlyWhenConnectedAndProcessPointExists() {
        val state = MeasurementState(
            connectionState = ConnectionState.Connected("192.168.4.1", 7888),
            receivedFrameCount = 2,
            lastCurvePointReceiveTime = 1_700_000_000_100,
            recentPoints = listOf(samplePoint()),
        )

        assertEquals(MeasurementDataOrigin.LivePoints, state.dataOrigin())
    }

    @Test
    fun heartbeatAfterLoadedHistoryDoesNotBecomeLiveCurve() {
        val state = MeasurementState(
            receivedFrameCount = 2,
            lastFrameReceiveTime = 1_700_000_000_200,
            recentPoints = listOf(samplePoint()),
        )

        assertEquals(MeasurementDataOrigin.LoadedPoints, state.dataOrigin())
    }

    @Test
    fun measurementGateExplainsFramesWithoutActualMeasurement() {
        val state = MeasurementState(receivedFrameCount = 2)

        assertEquals("已收设备帧，等待真实扭矩/角度", state.measurementGateStatus())
    }

    @Test
    fun measurementGateKeepsSaveStatusWhenMeasurementExists() {
        val state = MeasurementState(
            saveStatus = "CSV 已导出：下载/App3Exports/demo.csv",
            recentPoints = listOf(samplePoint()),
        )

        assertEquals("CSV 已导出：下载/App3Exports/demo.csv", state.measurementGateStatus())
    }

    private fun samplePoint(): MeasuredPoint = MeasuredPoint(
        sourcePoint = TorqueAnglePoint(
            torqueNm = 120.0,
            angleRaw = 45,
            angleDeg = 45.0,
            timestampMillis = 1_700_000_000_000,
        ),
        boltNo = 3,
        forceKn = null,
        pressureMpa = null,
        displacementMm = 0.0,
        warningLevel = WarningLevel.NORMAL,
        rawHex = "C5 C5 01 12 FF FF",
    )
}
