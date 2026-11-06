package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.connection.ConnectionState
import com.chuankangkk.wrenchlift.ui.worker.ChartAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerTextTest {
    private val online = MeasurementState(connectionState = ConnectionState.Connected("192.168.4.1", 7888), linkPhase = LinkPhase.READY)

    @Test fun remoteGateMatchesSpecStateMachine() {
        assertFalse(RemoteGate.canMove(online))
        assertTrue(RemoteGate.canMove(online.copy(remoteEnabled = true)))
        assertTrue(RemoteGate.canSendStop(online))
        assertFalse(RemoteGate.canMove(MeasurementState(remoteEnabled = true)))
    }

    @Test fun workerFacingTextIsPlainChineseWithoutPortsOrCodes() {
        val states = LinkPhase.entries.map { MeasurementState(linkPhase = it) } + listOf(online, online.copy(remoteEnabled = true, motion = MotionCommand.FORWARD))
        states.forEach { state ->
            listOf(WorkerText.link(state), WorkerText.linkShort(state), WorkerText.remote(state), WorkerText.motion(state), WorkerText.linkHint(state).orEmpty())
                .forEach { text ->
                    listOf("7888", "192.168", "TCP", "端口", "0x", "IP").forEach { banned -> assertFalse("$text 含 $banned", text.contains(banned)) }
                }
        }
        assertEquals("已连上扳手", WorkerText.link(online))
        assertEquals("先开远程，才能转动", WorkerText.motion(online))
        assertEquals("可以转动", WorkerText.motion(online.copy(remoteEnabled = true)))
        assertEquals("已停止", WorkerText.motion(online.copy(remoteEnabled = true, motion = MotionCommand.STOPPED)))
    }

    @Test fun droppedLinkWhileMovingWarnsToStopOnWrench() {
        val dropped = MeasurementState(linkPhase = LinkPhase.DROPPED, motion = MotionCommand.FORWARD)
        assertTrue(WorkerText.motion(dropped).contains("在扳手上停止"))
    }

    @Test fun freshnessIsSeparateFromHeartbeat() {
        val now = 100_000L
        assertEquals(Freshness.Waiting, WorkerText.freshness(online.copy(lastFrameReceiveTime = now), now))
        assertEquals(Freshness.Live, WorkerText.freshness(online.copy(lastCurvePointReceiveTime = now - 1_000), now))
        assertEquals(Freshness.Delayed(9), WorkerText.freshness(online.copy(lastCurvePointReceiveTime = now - 9_000), now))
        assertEquals(Freshness.Retained, WorkerText.freshness(MeasurementState(lastCurvePointReceiveTime = now), now))
    }

    @Test fun readoutDropsMeaninglessDecimals() {
        assertEquals("182", WorkerText.readout(182.0, true))
        assertEquals("12.5", WorkerText.readout(12.5, true))
        assertEquals("--", WorkerText.readout(182.0, false))
    }

    @Test fun niceAxisTicks() {
        val ticks = ChartAxis.niceTicks(0.0, 377.0, 5)
        assertEquals(100.0, ticks.step, 0.0)
        assertEquals(listOf(0.0, 100.0, 200.0, 300.0, 400.0), ticks.values)
        val small = ChartAxis.niceTicks(3.0, 13.0, 5)
        assertEquals(2.0, small.step, 0.0)
        assertEquals("2.5", ChartAxis.label(2.5, 0.5))
        assertEquals("200", ChartAxis.label(200.0, 50.0))
        // 回归：步长 0.25 / 0.05 时标签必须有足够小数位，不能把 0.25 标成 0.2、0.75 标成 0.8。
        val slope = ChartAxis.niceTicks(0.0, 1.2, 5)
        assertEquals(0.25, slope.step, 1e-12)
        assertEquals(listOf("0.00", "0.25", "0.50", "0.75", "1.00", "1.25"), slope.values.map { ChartAxis.label(it, slope.step) })
        assertEquals("0.05", ChartAxis.label(0.05, 0.05))
        assertEquals("0.025", ChartAxis.label(0.025, 0.025))
        assertEquals(0, ChartAxis.decimalsFor(10.0))
        assertEquals(1, ChartAxis.decimalsFor(2.5))
        assertEquals(2, ChartAxis.decimalsFor(0.25))
        assertEquals(2, ChartAxis.decimalsFor(0.01))
    }
}
