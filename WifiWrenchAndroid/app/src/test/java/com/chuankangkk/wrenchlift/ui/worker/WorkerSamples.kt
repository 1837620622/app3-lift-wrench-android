package com.chuankangkk.wrenchlift.ui.worker

import com.chuankangkk.wrenchlift.connection.ConnectionState
import com.chuankangkk.wrenchlift.connection.WrenchEndpoint
import com.chuankangkk.wrenchlift.data.MeasurementEntity
import com.chuankangkk.wrenchlift.measurement.LinkPhase
import com.chuankangkk.wrenchlift.measurement.MeasuredPoint
import com.chuankangkk.wrenchlift.measurement.MeasurementSession
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.MotionCommand
import com.chuankangkk.wrenchlift.measurement.WarningLevel
import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint

/** 仅测试：界面验收用的合成状态（不进入运行数据源）。 */
object WorkerSamples {
    private const val T0 = 1_762_300_000_000L

    /** 空行程 → 接触后承载 → 斜率变化的扭矩–转角形态（分段多项式，非真实现场数据）。 */
    fun loadingCurve(count: Int = 420, bolt: Int = 1, startMillis: Long = T0): List<MeasuredPoint> = List(count) { i ->
        val angle = i * 0.9
        val torque = when {
            angle < 40 -> 4 + angle * 0.12
            angle < 220 -> 8.8 + (angle - 40) * 1.55
            else -> 287.8 + (angle - 220) * 0.42 - (angle - 220) * (angle - 220) * 0.0004
        }
        MeasuredPoint(
            TorqueAnglePoint(torque.toInt().toDouble(), angle.toInt(), angle.toInt().toDouble(), startMillis + (i / 12) * 1000L),
            bolt, null, warningLevel = WarningLevel.WATCH, rawHex = "C5 C5 01 12",
        )
    }

    fun idle(phase: LinkPhase = LinkPhase.PROBING) = MeasurementState(
        linkPhase = phase,
        connectionState = if (phase == LinkPhase.PROBING) ConnectionState.Connecting else ConnectionState.Disconnected,
        session = MeasurementSession(slabNo = "FZB-012", pointNo = "P03"),
        targetWifiName = "WRENCH-500",
        currentWifiName = if (phase == LinkPhase.NO_WIFI) "--" else "WRENCH-500",
    )

    fun live(remote: Boolean, motion: MotionCommand = MotionCommand.NONE, nowMillis: Long = System.currentTimeMillis()): MeasurementState {
        val points = loadingCurve()
        val last = points.last()
        return MeasurementState(
            linkPhase = LinkPhase.READY,
            connectionState = ConnectionState.Connected("192.168.4.1", 7888),
            activeEndpoint = WrenchEndpoint.DEFAULT,
            session = MeasurementSession(slabNo = "FZB-012", pointNo = "P03", deviceSn = "2026110501"),
            deviceSn = "2026110501",
            batteryPercent = 82,
            remoteEnabled = remote,
            motion = motion,
            recentPoints = points,
            currentTorqueNm = last.sourcePoint.torqueNm,
            currentAngleDeg = last.sourcePoint.angleDeg,
            lastCurvePointReceiveTime = nowMillis - 400,
            lastFrameReceiveTime = nowMillis - 400,
            persistedPointCount = 2_505,
            currentSessionId = 7,
            receivedFrameCount = 40,
        )
    }

    fun result() = com.chuankangkk.wrenchlift.protocol.ResultFrameDecoder.decode(
        com.chuankangkk.wrenchlift.protocol.TestPacketFactory.resultFrame(0x00, "0000000000", 1, 343, 377),
    )

    fun sessions(): List<MeasurementEntity> = List(6) { i ->
        MeasurementEntity(
            id = 20L - i, projectName = "钢弹簧浮置板顶升", lineName = "1号线", sectionName = "A-B", slabNo = "FZB-0${12 - i / 2}",
            pointNo = "P0${3 - i % 3}", operatorName = "现场操作员", deviceSn = "2026110501", startTime = T0 - i * 3_600_000L,
            endTime = T0 - i * 3_600_000L + 600_000L, connectionMode = "TCP", resultStatus = if (i % 3 == 1) "扭矩偏低" else "合格",
            finalTorqueNm = 180.0, finalAngleDeg = 88.0, finalForceKn = null, finalPressureMpa = null, finalDisplacementMm = null,
            wrenchResultType = "普通结果", wrenchResultCode = 0, wrenchResultLabel = "合格", wrenchEmployeeId = null, wrenchBoltNo = 1,
            wrenchMode = 2, targetTorqueNm = 180.0, actualTorqueNm = 176.0 + i, targetAngleDeg = 90.0, actualAngleDeg = 88.0,
            torqueUpperNm = 220.0, torqueLowerNm = 150.0, angleUpperDeg = 360.0, angleLowerDeg = 1.0, wrenchResultRawHex = null,
        )
    }
}
