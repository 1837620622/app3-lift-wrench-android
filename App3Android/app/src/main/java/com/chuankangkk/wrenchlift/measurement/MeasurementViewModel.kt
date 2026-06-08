package com.chuankangkk.wrenchlift.measurement

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chuankangkk.wrenchlift.connection.ConnectionState
import com.chuankangkk.wrenchlift.connection.NetworkDiagnosticResult
import com.chuankangkk.wrenchlift.connection.NetworkDiagnostics
import com.chuankangkk.wrenchlift.connection.TcpWrenchClient
import com.chuankangkk.wrenchlift.connection.WrenchConnection
import com.chuankangkk.wrenchlift.data.MeasurementRepository
import com.chuankangkk.wrenchlift.data.TorquePointEntity
import com.chuankangkk.wrenchlift.data.WrenchDatabase
import com.chuankangkk.wrenchlift.protocol.AckDecoder
import com.chuankangkk.wrenchlift.protocol.BatteryDecoder
import com.chuankangkk.wrenchlift.protocol.DeviceSnDecoder
import com.chuankangkk.wrenchlift.protocol.DeviceStatusDecoder
import com.chuankangkk.wrenchlift.protocol.GpsDecoder
import com.chuankangkk.wrenchlift.protocol.NutResetDecoder
import com.chuankangkk.wrenchlift.protocol.PulseResultFrameDecoder
import com.chuankangkk.wrenchlift.protocol.PulseResultFrame
import com.chuankangkk.wrenchlift.protocol.ResultFrameDecoder
import com.chuankangkk.wrenchlift.protocol.ResultFrame
import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint
import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import com.chuankangkk.wrenchlift.protocol.WrenchPacketBuilder
import com.chuankangkk.wrenchlift.protocol.TorqueAngleDecoder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

class MeasurementViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MeasurementRepository(
        WrenchDatabase.getInstance(application).measurementDao(),
    )
    private val uncalibratedCalculator: ForceCalculator = CalibrationForceCalculator(emptyList())
    private val networkDiagnostics = NetworkDiagnostics(application)

    private val _state = MutableStateFlow(MeasurementState())
    val state: StateFlow<MeasurementState> = _state.asStateFlow()

    private var connection: WrenchConnection = TcpWrenchClient()
    private var stateJob: Job? = null
    private var packetJob: Job? = null
    private var sessionId: Long? = null
    private var connectionMode: String = "未连接"
    private val sessionMutex = Mutex()

    init {
        bindConnection(connection)
        viewModelScope.launch {
            repository.observeRecentSessions(limit = 12).collect { sessions ->
                _state.update { it.copy(recentSessions = sessions) }
            }
        }
    }

    fun updateHost(value: String) {
        _state.update { it.copy(host = value) }
    }

    fun updatePort(value: String) {
        _state.update { it.copy(port = value.filter { char -> char.isDigit() }.take(5)) }
    }

    fun updateSlabNo(value: String) {
        _state.update { it.copy(session = it.session.copy(slabNo = value)) }
    }

    fun updatePointNo(value: String) {
        _state.update { it.copy(session = it.session.copy(pointNo = value)) }
    }

    fun connectTcp() {
        viewModelScope.launch {
            val current = _state.value
            val portNumber = current.port.toIntOrNull() ?: 7888
            val requestedHost = current.host.ifBlank { "192.168.4.1" }
            val diagnostic = diagnoseNetwork(requestedHost, portNumber, checkPorts = true)
            var activeHost = diagnostic.suggestedHost.ifBlank { requestedHost }
            var activePort = diagnostic.suggestedPort
            connectionMode = "TCP"
            switchConnection(TcpWrenchClient())
            ensureSession("TCP")
            _state.update { it.copy(host = activeHost, port = activePort.toString()) }
            appendLog("开始连接 $activeHost:$activePort")
            connection.connect(activeHost, activePort)

            if (
                _state.value.connectionState is ConnectionState.Error &&
                activeHost == "192.168.4.1" &&
                diagnostic.gatewayIp != "--" &&
                diagnostic.gatewayIp != activeHost
            ) {
                activeHost = diagnostic.gatewayIp
                appendLog("默认地址失败，尝试默认网关 $activeHost:$activePort")
                switchConnection(TcpWrenchClient())
                _state.update { it.copy(host = activeHost) }
                connection.connect(activeHost, activePort)
            }

            if (_state.value.connectionState is ConnectionState.Connected) {
                sendConnectionProbe()
            } else {
                appendLog("连接未成功：${diagnostic.summary}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connection.disconnect()
            appendLog("连接已断开")
        }
    }

    fun enableRemoteControl() = sendCommand(WrenchPacketBuilder.enableRemoteControl(), "手机控制已开启")

    fun disableRemoteControl() = sendCommand(WrenchPacketBuilder.disableRemoteControl(), "手机控制已关闭")

    fun startForward() = sendCommand(WrenchPacketBuilder.startForward(), "正向顶升")

    fun startReverse() = sendCommand(WrenchPacketBuilder.startReverse(), "反向回退")

    fun stopWrench() = sendCommand(WrenchPacketBuilder.stop(), "停止扳手")

    fun sendHeartbeat() {
        viewModelScope.launch {
            val current = _state.value
            val portNumber = current.port.toIntOrNull() ?: 7888
            diagnoseNetwork(current.host.ifBlank { "192.168.4.1" }, portNumber, checkPorts = true)
            if (_state.value.connectionState is ConnectionState.Connected) {
                sendCommand(WrenchPacketBuilder.heartbeat(), "检查连接")
            }
        }
    }

    fun sendResultAck() {
        appendLog("结果确认会在扳手回传最终结果后自动处理，不能手动乱发")
    }

    fun sendTimeSync() {
        appendLog("时间校准会在扳手主动请求时自动回复，不能手动乱发")
    }

    fun saveCurrentRecord() {
        viewModelScope.launch {
            val current = _state.value
            val activeSessionId = sessionId ?: ensureSession(connectionMode)
            persistSessionSummary(
                activeSessionId = activeSessionId,
                resultStatus = current.resultStatus,
                result = current.lastResult,
                pulseResult = current.lastPulseResult,
            )
            _state.update { it.copy(saveStatus = "当前记录已写入本机数据库") }
            appendLog("会话摘要已保存")
        }
    }

    fun confirmEffectivePoint() {
        viewModelScope.launch {
            val activeSessionId = sessionId ?: return@launch
            repository.markLatestPointEffective(activeSessionId)
            _state.update {
                it.copy(
                    effectivePointCount = it.effectivePointCount + 1,
                    saveStatus = "已确认平衡点 ${it.effectivePointCount + 1}",
                )
            }
            appendLog("最新采样点已标记为平衡点")
        }
    }

    private fun sendCommand(packet: WrenchPacket, label: String) {
        viewModelScope.launch {
            runCatching {
                connection.send(packet)
            }.onSuccess {
                _state.update {
                    it.copy(
                        lastCommandLabel = label,
                        lastSentHex = packet.rawHex(),
                    )
                }
                appendLog("$label 已发送：${packet.rawHex()}")
            }.onFailure { throwable ->
                appendLog("$label 发送失败：${throwable.message ?: "未知错误"}")
            }
        }
    }

    private suspend fun sendConnectionProbe() {
        val heartbeat = WrenchPacketBuilder.heartbeat()
        val querySn = WrenchPacketBuilder.querySn()
        runCatching {
            connection.send(heartbeat)
            connection.send(querySn)
        }.onSuccess {
            _state.update {
                it.copy(
                    lastCommandLabel = "连接检查和设备编号查询",
                    lastSentHex = querySn.rawHex(),
                    lastProtocolSummary = "已查询设备编号",
                )
            }
            appendLog("已发送连接检查和设备编号查询")
        }.onFailure { throwable ->
            appendLog("连接检查失败：${throwable.message ?: "未知错误"}")
        }
    }

    private suspend fun diagnoseNetwork(
        host: String,
        port: Int,
        checkPorts: Boolean,
    ): NetworkDiagnosticResult {
        val result = networkDiagnostics.diagnose(host, port, checkPorts)
        _state.update {
            it.copy(
                currentWifiName = result.wifiName,
                localIp = result.localIp,
                gatewayIp = result.gatewayIp,
                inWrenchSubnet = result.inWrenchSubnet,
                deviceIpReachable = result.deviceIpReachable,
                devicePortReachable = result.devicePortReachable,
                gatewayPortReachable = result.gatewayPortReachable,
                discoveredEndpoint = result.discoveredEndpoint,
                networkDiagnosticSummary = result.summary,
                lastProtocolSummary = result.summary,
                host = if (checkPorts && result.suggestedHost != host) result.suggestedHost else it.host,
                port = if (checkPorts && result.suggestedPort != port) result.suggestedPort.toString() else it.port,
            )
        }
        appendLog("网络诊断：${result.summary}")
        return result
    }

    private suspend fun switchConnection(newConnection: WrenchConnection) {
        stateJob?.cancel()
        packetJob?.cancel()
        connection.disconnect()
        connection = newConnection
        bindConnection(newConnection)
    }

    private fun bindConnection(target: WrenchConnection) {
        stateJob = viewModelScope.launch {
            target.connectionState.collect { connectionState ->
                _state.update { it.copy(connectionState = connectionState) }
            }
        }
        packetJob = viewModelScope.launch {
            target.incomingPackets.collect { packet ->
                handlePacket(packet)
            }
        }
    }

    private suspend fun handlePacket(packet: WrenchPacket) {
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                receivedFrameCount = it.receivedFrameCount + 1,
                lastPacketFunction = packet.functionCode,
                lastFrameReceiveTime = now,
                lastRawHex = packet.rawHex(),
                lastProtocolSummary = "收到设备数据",
            )
        }
        when (packet.functionCode) {
            WrenchPacket.FUNC_ACK -> {
                val ack = AckDecoder.decode(packet)
                _state.update { it.copy(lastProtocolSummary = "设备应答 ${ack.label}") }
                appendLog("收到设备应答：${ack.label}")
            }
            WrenchPacket.FUNC_TORQUE_ANGLE -> handleTorqueAngle(packet, now)
            WrenchPacket.FUNC_RESULT -> handleResult(packet, now)
            WrenchPacket.FUNC_TIME_REQUEST -> {
                connection.send(WrenchPacketBuilder.timeSync())
                _state.update { it.copy(lastProtocolSummary = "扳手请求校时，已自动回复") }
                appendLog("扳手请求时间校准，已自动回复")
            }
            WrenchPacket.FUNC_REPLY_SN -> {
                val sn = DeviceSnDecoder.decode(packet)
                _state.update {
                    it.copy(
                        deviceSn = sn.sn,
                        session = it.session.copy(deviceSn = sn.sn),
                        lastProtocolSummary = "设备 SN ${sn.sn}",
                    )
                }
                appendLog("收到设备编号：${sn.sn}")
            }
            WrenchPacket.FUNC_BATTERY -> {
                BatteryDecoder.decodePercent(packet)?.let { percent ->
                    _state.update {
                        it.copy(
                            batteryPercent = percent,
                            lastProtocolSummary = "电量 $percent%",
                        )
                    }
                    appendLog("收到电池电量 $percent%")
                }
            }
            WrenchPacket.FUNC_STATUS -> {
                val status = DeviceStatusDecoder.decode(packet)
                _state.update { it.copy(lastProtocolSummary = "设备状态 ${status.summary}") }
                appendLog("收到设备状态：${status.summary}")
            }
            WrenchPacket.FUNC_NUT_RESET -> {
                val nutReset = NutResetDecoder.decode(packet)
                _state.update { it.copy(lastProtocolSummary = nutReset.label) }
                appendLog("收到螺母计数：${nutReset.label}")
            }
            WrenchPacket.FUNC_GPS -> {
                val gps = GpsDecoder.decode(packet)
                val summary = if (gps.valid) "GPS 有效 ${gps.text}" else "GPS 无效 ${gps.text}"
                _state.update { it.copy(lastProtocolSummary = summary) }
                appendLog("收到位置数据：$summary")
            }
            WrenchPacket.FUNC_PULSE_RESULT -> handlePulseResult(packet, now)
            else -> appendLog("收到未识别设备数据，原始内容已保存")
        }
    }

    private suspend fun handleTorqueAngle(packet: WrenchPacket, now: Long) {
        val decoded = TorqueAngleDecoder.decode(packet, nowMillis = now)
        val activeSessionId = sessionId ?: ensureSession(connectionMode)
        val calculator = uncalibratedCalculator
        val newMeasuredPoints = mutableListOf<MeasuredPoint>()
        val historyBefore = _state.value.recentPoints.map { it.sourcePoint }.toMutableList()
        var latestCalculation: ForceCalculationResult? = null

        decoded.points.forEach { point ->
            val result = calculator.calculate(point, historyBefore)
            val displacementMm = 0.0
            val measuredPoint = MeasuredPoint(
                sourcePoint = point,
                boltNo = decoded.boltNo,
                forceKn = result.forceKn,
                pressureMpa = result.pressureMpa,
                displacementMm = displacementMm,
                warningLevel = result.warningLevel,
                rawHex = decoded.rawHex,
            )
            latestCalculation = result
            historyBefore += point
            newMeasuredPoints += measuredPoint
            repository.savePoint(
                TorquePointEntity(
                    sessionId = activeSessionId,
                    timestampMillis = point.timestampMillis,
                    boltNo = decoded.boltNo,
                    torqueNm = point.torqueNm,
                    angleRaw = point.angleRaw,
                    angleDeg = point.angleDeg,
                    forceKn = result.forceKn,
                    pressureMpa = result.pressureMpa,
                    displacementMm = displacementMm,
                    isEffectivePoint = false,
                    rawHex = decoded.rawHex,
                ),
            )
        }

        val latest = newMeasuredPoints.lastOrNull() ?: return
        val latestResult = latestCalculation ?: return
        _state.update { old ->
            val merged = (old.recentPoints + newMeasuredPoints).takeLast(50)
            old.copy(
                currentBoltNo = decoded.boltNo,
                employeeId = decoded.employeeId.ifBlank { "--" },
                currentTorqueNm = latest.sourcePoint.torqueNm,
                currentAngleRaw = latest.sourcePoint.angleRaw,
                currentAngleDeg = latest.sourcePoint.angleDeg,
                currentForceKn = latest.forceKn,
                currentPressureMpa = latest.pressureMpa,
                currentDisplacementMm = latest.displacementMm,
                warningLevel = latest.warningLevel,
                warningMessage = latestResult.message,
                lastFrameReceiveTime = now,
                recentPoints = merged,
                saveStatus = "已保存 ${merged.size} 个显示点，原始帧已入库",
            )
        }
    }

    private suspend fun handleResult(packet: WrenchPacket, now: Long) {
        val result = ResultFrameDecoder.decode(packet)
        connection.send(WrenchPacketBuilder.resultAck())
        val activeSessionId = sessionId ?: ensureSession(connectionMode)
        recordResultMeasurementPoint(
            activeSessionId = activeSessionId,
            boltNo = result.boltNo,
            torqueNm = result.actualTorqueNm,
            angleDeg = result.actualAngleDeg,
            now = now,
            rawHex = result.rawHex,
        )
        _state.update {
            it.copy(
                resultStatus = result.status.label,
                currentBoltNo = result.boltNo,
                employeeId = result.employeeId.ifBlank { it.employeeId },
                currentTorqueNm = result.actualTorqueNm ?: it.currentTorqueNm,
                currentAngleDeg = result.actualAngleDeg ?: it.currentAngleDeg,
                currentAngleRaw = result.actualAngleDeg?.roundToInt() ?: it.currentAngleRaw,
                lastFrameReceiveTime = now,
                lastResult = result,
                lastProtocolSummary = "扳手设置已同步：目标扭矩 ${result.targetTorqueNm.formatLog()} Nm，实际扭矩 ${result.actualTorqueNm.formatLog()} Nm",
            )
        }
        persistSessionSummary(activeSessionId, result.status.label, result, pulseResult = null)
        appendLog(
            "收到最终结果：${result.status.label}；目标/实际扭矩 " +
                "${result.targetTorqueNm.formatLog()}/${result.actualTorqueNm.formatLog()} Nm；" +
                "目标/实际角度 ${result.targetAngleDeg.formatLog()}/${result.actualAngleDeg.formatLog()}°；已自动确认",
        )
    }

    private suspend fun handlePulseResult(packet: WrenchPacket, now: Long) {
        val result = PulseResultFrameDecoder.decode(packet)
        val activeSessionId = sessionId ?: ensureSession(connectionMode)
        recordResultMeasurementPoint(
            activeSessionId = activeSessionId,
            boltNo = result.boltNo,
            torqueNm = result.actualTorqueNm,
            angleDeg = result.actualAngleDeg,
            now = now,
            rawHex = result.rawHex,
        )
        _state.update {
            it.copy(
                resultStatus = result.status.label,
                currentBoltNo = result.boltNo,
                employeeId = result.employeeId.ifBlank { it.employeeId },
                currentTorqueNm = result.actualTorqueNm ?: it.currentTorqueNm,
                currentAngleDeg = result.actualAngleDeg ?: it.currentAngleDeg,
                currentAngleRaw = result.actualAngleDeg?.roundToInt() ?: it.currentAngleRaw,
                lastFrameReceiveTime = now,
                lastPulseResult = result,
                lastProtocolSummary = "扳手脉冲结果已同步：目标扭矩 ${result.targetTorqueNm.formatLog()} Nm",
            )
        }
        persistSessionSummary(activeSessionId, result.status.label, result = null, pulseResult = result)
        appendLog(
            "收到特殊模式结果：${result.status.label}；目标/实际扭矩 " +
                "${result.targetTorqueNm.formatLog()}/${result.actualTorqueNm.formatLog()} Nm；" +
                "实际角度 ${result.actualAngleDeg.formatLog()}°",
        )
    }

    private suspend fun recordResultMeasurementPoint(
        activeSessionId: Long,
        boltNo: Int,
        torqueNm: Double?,
        angleDeg: Double?,
        now: Long,
        rawHex: String,
    ) {
        if (torqueNm == null || angleDeg == null) return
        val sourcePoint = TorqueAnglePoint(
            torqueNm = torqueNm,
            angleRaw = angleDeg.roundToInt(),
            angleDeg = angleDeg,
            timestampMillis = now,
        )
        val result = uncalibratedCalculator.calculate(
            sourcePoint,
            _state.value.recentPoints.map { it.sourcePoint },
        )
        val measuredPoint = MeasuredPoint(
            sourcePoint = sourcePoint,
            boltNo = boltNo,
            forceKn = result.forceKn,
            pressureMpa = result.pressureMpa,
            displacementMm = 0.0,
            warningLevel = result.warningLevel,
            rawHex = rawHex,
        )
        repository.savePoint(
            TorquePointEntity(
                sessionId = activeSessionId,
                timestampMillis = sourcePoint.timestampMillis,
                boltNo = boltNo,
                torqueNm = sourcePoint.torqueNm,
                angleRaw = sourcePoint.angleRaw,
                angleDeg = sourcePoint.angleDeg,
                forceKn = result.forceKn,
                pressureMpa = result.pressureMpa,
                displacementMm = measuredPoint.displacementMm,
                isEffectivePoint = false,
                rawHex = rawHex,
            ),
        )
        _state.update { old ->
            val merged = (old.recentPoints + measuredPoint).takeLast(50)
            old.copy(
                currentBoltNo = boltNo,
                currentTorqueNm = sourcePoint.torqueNm,
                currentAngleRaw = sourcePoint.angleRaw,
                currentAngleDeg = sourcePoint.angleDeg,
                currentForceKn = result.forceKn,
                currentPressureMpa = result.pressureMpa,
                currentDisplacementMm = measuredPoint.displacementMm,
                warningLevel = result.warningLevel,
                warningMessage = result.message,
                recentPoints = merged,
                saveStatus = "已保存扳手结果点，原始结果帧已入库",
            )
        }
    }

    private suspend fun persistSessionSummary(
        activeSessionId: Long,
        resultStatus: String,
        result: ResultFrame?,
        pulseResult: PulseResultFrame?,
    ) {
        val current = _state.value
        repository.updateSessionSummary(
            sessionId = activeSessionId,
            resultStatus = resultStatus,
            finalTorqueNm = result?.actualTorqueNm ?: pulseResult?.actualTorqueNm ?: current.currentTorqueNm,
            finalAngleDeg = result?.actualAngleDeg ?: pulseResult?.actualAngleDeg ?: current.currentAngleDeg,
            finalForceKn = current.currentForceKn,
            finalPressureMpa = current.currentPressureMpa,
            finalDisplacementMm = current.currentDisplacementMm,
            wrenchResultType = when {
                result != null -> "普通结果"
                pulseResult != null -> "脉冲结果"
                else -> null
            },
            wrenchResultCode = result?.status?.code ?: pulseResult?.status?.code,
            wrenchResultLabel = result?.status?.label ?: pulseResult?.status?.label,
            wrenchEmployeeId = result?.employeeId ?: pulseResult?.employeeId,
            wrenchBoltNo = result?.boltNo ?: pulseResult?.boltNo,
            wrenchMode = result?.mode ?: pulseResult?.mode,
            targetTorqueNm = result?.targetTorqueNm ?: pulseResult?.targetTorqueNm,
            actualTorqueNm = result?.actualTorqueNm ?: pulseResult?.actualTorqueNm,
            targetAngleDeg = result?.targetAngleDeg,
            actualAngleDeg = result?.actualAngleDeg ?: pulseResult?.actualAngleDeg,
            torqueUpperNm = result?.torqueUpperNm,
            torqueLowerNm = result?.torqueLowerNm,
            angleUpperDeg = result?.angleUpperDeg,
            angleLowerDeg = result?.angleLowerDeg,
            wrenchResultRawHex = result?.rawHex ?: pulseResult?.rawHex,
        )
    }

    private suspend fun ensureSession(mode: String): Long = sessionMutex.withLock {
        sessionId?.let { return it }
        val current = _state.value.session
        val id = repository.createSession(
            projectName = current.projectName,
            lineName = current.lineName,
            sectionName = current.sectionName,
            slabNo = current.slabNo,
            pointNo = current.pointNo,
            operatorName = current.operatorName,
            deviceSn = current.deviceSn,
            connectionMode = mode,
        )
        sessionId = id
        return id
    }

    private fun appendLog(message: String) {
        _state.update {
            it.copy(logLines = (it.logLines + message).takeLast(12))
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            connection.disconnect()
        }
    }
}

private fun Double?.formatLog(): String = this?.let { "%.1f".format(it) } ?: "--"
