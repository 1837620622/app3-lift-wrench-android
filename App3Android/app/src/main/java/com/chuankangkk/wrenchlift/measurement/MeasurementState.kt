package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.connection.ConnectionState
import com.chuankangkk.wrenchlift.data.MeasurementEntity
import com.chuankangkk.wrenchlift.protocol.PulseResultFrame
import com.chuankangkk.wrenchlift.protocol.ResultFrame
import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint

data class MeasuredPoint(
    val sourcePoint: TorqueAnglePoint,
    val boltNo: Int,
    val forceKn: Double?,
    val pressureMpa: Double? = null,
    val displacementMm: Double = 0.0,
    val warningLevel: WarningLevel,
    val rawHex: String,
)

data class MeasurementState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val host: String = "192.168.4.1",
    val port: String = "7888",
    val session: MeasurementSession = MeasurementSession(),
    val currentBoltNo: Int = 0,
    val currentTorqueNm: Double = 0.0,
    val currentAngleRaw: Int = 0,
    val currentAngleDeg: Double = 0.0,
    val currentForceKn: Double? = null,
    val currentPressureMpa: Double? = null,
    val currentDisplacementMm: Double = 0.0,
    val employeeId: String = "--",
    val batteryPercent: Int? = null,
    val deviceSn: String = "",
    val currentWifiName: String = "--",
    val localIp: String = "--",
    val gatewayIp: String = "--",
    val inWrenchSubnet: Boolean = false,
    val deviceIpReachable: Boolean? = null,
    val devicePortReachable: Boolean? = null,
    val gatewayPortReachable: Boolean? = null,
    val discoveredEndpoint: String = "--",
    val networkDiagnosticSummary: String = "尚未诊断网络",
    val lastFrameReceiveTime: Long? = null,
    val receivedFrameCount: Int = 0,
    val lastPacketFunction: Int? = null,
    val lastCommandLabel: String = "尚未发送命令",
    val lastSentHex: String = "",
    val saveStatus: String = "尚未保存",
    val resultStatus: String = "等待结果",
    val lastResult: ResultFrame? = null,
    val lastPulseResult: PulseResultFrame? = null,
    val lastProtocolSummary: String = "等待设备数据",
    val warningLevel: WarningLevel = WarningLevel.NORMAL,
    val warningMessage: String = "等待采集",
    val recentPoints: List<MeasuredPoint> = emptyList(),
    val recentSessions: List<MeasurementEntity> = emptyList(),
    val effectivePointCount: Int = 0,
    val lastRawHex: String = "",
    val logLines: List<String> = listOf("系统就绪，请先连接扳手 Wi-Fi，再检查连接。"),
)
