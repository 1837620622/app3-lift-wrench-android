package com.chuankangkk.wrenchlift.measurement

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
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
import com.chuankangkk.wrenchlift.protocol.App3ParameterSettings
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import java.io.Writer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class MeasurementViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MeasurementRepository(
        WrenchDatabase.getInstance(application).measurementDao(),
    )
    private val uncalibratedCalculator: ForceCalculator = CalibrationForceCalculator(emptyList())
    private val networkDiagnostics = NetworkDiagnostics(application)

    private val connectionPreferences = application.getSharedPreferences("connection_preferences", android.content.Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(MeasurementState(
        targetWifiName = connectionPreferences.getString("target_wifi_name", "").orEmpty(),
        host = connectionPreferences.getString("host", "192.168.4.1").orEmpty(),
        port = connectionPreferences.getString("port", "7888").orEmpty(),
    ))
    val state: StateFlow<MeasurementState> = _state.asStateFlow()

    private var connection: WrenchConnection = TcpWrenchClient()
    private var stateJob: Job? = null
    private var packetJob: Job? = null
    private var sessionId: Long? = null
    private var connectionMode: String = "未连接"
    private val sessionMutex = Mutex()
    private val measurementMutex = Mutex()
    private val historyLimit = MutableStateFlow(20)

    init {
        bindConnection(connection)
        viewModelScope.launch {
            historyLimit.collectLatest { limit ->
                repository.observeRecentSessions(limit = limit).collect { sessions ->
                    _state.update { it.copy(recentSessions = sessions) }
                }
            }
        }
    }

    fun updateHost(value: String) {
        _state.update { it.copy(host = value) }
        connectionPreferences.edit().putString("host", value).apply()
    }

    fun updatePort(value: String) {
        val port = value.filter { char -> char.isDigit() }.take(5)
        _state.update { it.copy(port = port) }
        connectionPreferences.edit().putString("port", port).apply()
    }

    fun updateTargetWifiName(value: String) {
        val name = value.take(64)
        _state.update { it.copy(targetWifiName = name) }
        connectionPreferences.edit().putString("target_wifi_name", name).apply()
    }

    fun updateSlabNo(value: String) {
        _state.update { it.copy(session = it.session.copy(slabNo = value)) }
    }

    fun updatePointNo(value: String) {
        _state.update { it.copy(session = it.session.copy(pointNo = value)) }
    }

    fun updateParameterMode(mode: Int) {
        if (mode != App3ParameterSettings.MODE_M1 && mode != App3ParameterSettings.MODE_M2) return
        _state.update {
            it.copy(
                parameterMode = mode,
                parameterTargetAngle = when {
                    mode == App3ParameterSettings.MODE_M1 -> "0"
                    it.parameterTargetAngle == "0" -> "90"
                    else -> it.parameterTargetAngle
                },
            )
        }
    }

    fun updateParameterTargetTorque(value: String) {
        _state.update { it.copy(parameterTargetTorque = value.sanitizeUIntText()) }
    }

    fun updateParameterTorqueLower(value: String) {
        _state.update { it.copy(parameterTorqueLower = value.sanitizeUIntText()) }
    }

    fun updateParameterTorqueUpper(value: String) {
        _state.update { it.copy(parameterTorqueUpper = value.sanitizeUIntText()) }
    }

    fun updateParameterTargetAngle(value: String) {
        _state.update { it.copy(parameterTargetAngle = value.sanitizeDecimalText()) }
    }

    fun updateParameterAngleUpper(value: String) {
        _state.update { it.copy(parameterAngleUpper = value.sanitizeDecimalText()) }
    }

    fun updateParameterAngleLower(value: String) {
        _state.update { it.copy(parameterAngleLower = value.sanitizeDecimalText()) }
    }

    fun connectTcp() {
        if (_state.value.connectionState.isOnline || _state.value.connectionState is ConnectionState.Connecting) return
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
                connection.connectionState.value is ConnectionState.Error &&
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

            if (connection.connectionState.value is ConnectionState.Connected) {
                sendConnectionProbe()
            } else {
                appendLog("连接未成功：${diagnostic.summary}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            connection.disconnect()
            measurementMutex.withLock {
                sessionId?.let { id ->
                    val current = _state.value
                    persistSessionSummary(id, current.lastResult?.status?.label ?: "采集结束", current.lastResult, current.lastPulseResult)
                }
            }
            appendLog("连接已断开")
        }
    }

    fun openWifiSettings() {
        runCatching {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(intent)
        }.onSuccess {
            _state.update {
                it.copy(
                    networkDiagnosticSummary = "已打开系统 Wi-Fi 设置，请手动连接扳手热点后返回 App 检查连接。",
                    lastProtocolSummary = "等待手动连接扳手 Wi-Fi",
                )
            }
            appendLog("已打开系统 Wi-Fi 设置")
        }.onFailure { throwable ->
            _state.update {
                it.copy(networkDiagnosticSummary = "无法打开系统 Wi-Fi 设置：${throwable.message ?: "未知错误"}")
            }
            appendLog("打开系统 Wi-Fi 设置失败：${throwable.message ?: "未知错误"}")
        }
    }

    fun enableRemoteControl() = sendCommand(WrenchPacketBuilder.enableRemoteControl(), "手机控制已开启")

    fun disableRemoteControl() = sendCommand(WrenchPacketBuilder.disableRemoteControl(), "手机控制已关闭")

    fun startForward() = sendCommand(WrenchPacketBuilder.startForward(), "正向顶升")

    fun startReverse() = sendCommand(WrenchPacketBuilder.startReverse(), "反向回退")

    fun stopWrench() = sendCommand(WrenchPacketBuilder.stop(), "停止扳手")

    fun writeParameterSettings() {
        val settings = buildParameterSettings() ?: return
        val statusText =
            "已发送 ${settings.modeLabel()}：目标扭矩 ${settings.targetTorqueNm} Nm，目标角度 %.1f°".format(settings.targetAngleDeg)
        sendCommand(WrenchPacketBuilder.parameterSet(settings), "写入扳手参数") {
            it.copy(
                parameterStatus = statusText,
            )
        }
    }

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

    fun saveCurrentRecord() {
        viewModelScope.launch {
            val current = _state.value
            if (!current.hasWrenchMeasurementData()) {
                val message = "尚未收到真实扳手测量数据，不能保存记录"
                _state.update { it.copy(saveStatus = message) }
                appendLog(message)
                return@launch
            }
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

    fun loadMoreHistory() { historyLimit.value = (historyLimit.value + 20).coerceAtMost(100_000) }

    fun newRecord() {
        if (_state.value.connectionState.isOnline || _state.value.connectionState is ConnectionState.Connecting) return
        viewModelScope.launch {
            packetJob?.cancelAndJoin()
            measurementMutex.withLock {
                sessionId = null
                val old = _state.value
                _state.value = MeasurementState(targetWifiName = old.targetWifiName, host = old.host, port = old.port, session = old.session,
                    recentSessions = old.recentSessions, saveStatus = "新记录已就绪，请检查测点后连接")
            }
        }
    }

    fun exportCurrentCsv() {
        val id = sessionId
        if (id == null) {
            _state.update { it.copy(saveStatus = "暂无测量记录可导出") }
            return
        }
        exportSessionCsv(id)
    }

    fun exportSessionCsv(id: Long) {
        viewModelScope.launch {
            if (_state.value.exportInProgress) return@launch
            _state.update { it.copy(exportInProgress = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    // 等当前帧完整入库后冻结上界；之后收到的点留待下一次导出。
                    val snapshot = measurementMutex.withLock {
                        val record = repository.getSession(id) ?: error("记录不存在")
                        record to (repository.lastPointId(id) ?: 0L)
                    }
                    val (record, throughId) = snapshot
                    require(throughId > 0 || record.actualTorqueNm != null) { "记录尚无测量数据" }
                    val metadata = MeasurementState(
                        session = MeasurementSession(record.projectName, record.lineName, record.sectionName,
                            record.slabNo, record.pointNo, record.operatorName, record.deviceSn),
                        deviceSn = record.deviceSn, resultStatus = record.resultStatus,
                        host = "", port = "", currentWifiName = "", localIp = "", gatewayIp = "",
                        currentTorqueNm = record.actualTorqueNm ?: record.finalTorqueNm ?: 0.0,
                        currentAngleDeg = record.actualAngleDeg ?: record.finalAngleDeg ?: 0.0,
                        currentForceKn = record.finalForceKn,
                        lastPacketFunction = if (throughId > 0) WrenchPacket.FUNC_TORQUE_ANGLE else WrenchPacket.FUNC_RESULT,
                        lastRawHex = record.wrenchResultRawHex.orEmpty(),
                    )
                    val fileName = "R${id}_" + MeasurementCsvExporter.buildFileName(metadata)
                    var rowCount = 0
                    val write: suspend (Writer) -> Unit = { writer ->
                        MeasurementCsvExporter.writeHeader(writer)
                        var cursor = 0L
                        rowCount = 0
                        while (cursor < throughId) {
                            val page = repository.pointsPage(id, cursor, throughId)
                            if (page.isEmpty()) break
                            for (point in page) {
                                val measured = MeasuredPoint(
                                    sourcePoint = TorqueAnglePoint(point.torqueNm, point.angleRaw, point.angleDeg, point.timestampMillis),
                                    boltNo = point.boltNo, forceKn = point.forceKn, pressureMpa = point.pressureMpa,
                                    displacementMm = point.displacementMm, warningLevel = WarningLevel.WATCH, rawHex = point.rawHex,
                                )
                                MeasurementCsvExporter.writePoint(writer,
                                    metadata.copy(recentPoints = listOf(measured)), ++rowCount, measured)
                            }
                            cursor = page.last().id
                        }
                        if (rowCount == 0) MeasurementCsvExporter.writeSummary(writer, metadata)
                    }
                    val application = getApplication<Application>()
                    val path = exportCsvToDownloads(application, fileName, write)
                        ?: exportCsvToAppDocuments(application, fileName, write)
                    "CSV 已导出：$path · $rowCount 个过程点"
                }
                _state.update { it.copy(saveStatus = result) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.update { it.copy(saveStatus = "CSV 导出失败：${error.message}") }
            } finally {
                _state.update { it.copy(exportInProgress = false) }
            }
        }
    }

    private suspend fun exportCsvToDownloads(application: Application, fileName: String, write: suspend (Writer) -> Unit): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = application.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/App3Exports")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            val output = resolver.openOutputStream(uri) ?: error("无法打开下载目录")
            output.bufferedWriter(Charsets.UTF_8).use { write(it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            "下载/App3Exports/$fileName"
        } catch (cancelled: CancellationException) {
            resolver.delete(uri, null, null)
            throw cancelled
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private suspend fun exportCsvToAppDocuments(application: Application, fileName: String, write: suspend (Writer) -> Unit): String {
        val documentRoot = application.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: File(application.filesDir, "Documents")
        val exportDir = File(documentRoot, "App3Exports")
        check(exportDir.isDirectory || exportDir.mkdirs()) { "无法创建导出目录" }
        val csvFile = File(exportDir, fileName)
        try {
            csvFile.bufferedWriter(Charsets.UTF_8).use { write(it) }
        } catch (error: Exception) {
            csvFile.delete()
            throw error
        }
        return "App专属目录/${csvFile.name}"
    }

    fun confirmEffectivePoint() {
        viewModelScope.launch {
            val activeSessionId = sessionId ?: return@launch
            if (!_state.value.canConfirmEffectivePoint()) {
                _state.update { it.copy(saveStatus = "没有可确认的采样点") }
                appendLog("平衡点确认被跳过：还没有真实采样点")
                return@launch
            }
            repository.markLatestPointEffective(activeSessionId)
            val count = repository.countEffectivePoints(activeSessionId)
            _state.update {
                it.copy(
                    effectivePointCount = count,
                    saveStatus = "已确认平衡点 $count",
                )
            }
            appendLog("最新采样点已标记为平衡点")
        }
    }

    fun removeLatestEffectivePoint() {
        viewModelScope.launch {
            val activeSessionId = sessionId ?: return@launch
            if (!_state.value.canRemoveEffectivePoint()) {
                _state.update { it.copy(saveStatus = "当前没有可删除的平衡点") }
                appendLog("平衡点删除被跳过：当前没有平衡点标记")
                return@launch
            }
            repository.unmarkLatestEffectivePoint(activeSessionId)
            val count = repository.countEffectivePoints(activeSessionId)
            _state.update {
                it.copy(
                    effectivePointCount = count,
                    saveStatus = if (count > 0) {
                        "已删除最近平衡点，剩余 $count"
                    } else {
                        "已删除平衡点标记，当前无平衡点"
                    },
                )
            }
            appendLog("已删除最近一个平衡点标记；原始采样点保留")
        }
    }

    private fun sendCommand(
        packet: WrenchPacket,
        label: String,
        onSuccessState: ((MeasurementState) -> MeasurementState)? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                connection.send(packet)
            }.onSuccess {
                _state.update {
                    val updated = it.copy(
                        lastCommandLabel = label,
                        lastSentHex = packet.rawHex(),
                    )
                    onSuccessState?.invoke(updated) ?: updated
                }
                appendLog("$label 已发送：${packet.rawHex()}")
            }.onFailure { throwable ->
                if (label == "写入扳手参数") {
                    _state.update { it.copy(parameterStatus = "参数发送失败：${throwable.message ?: "未知错误"}") }
                }
                appendLog("$label 发送失败：${throwable.message ?: "未知错误"}")
            }
        }
    }

    private fun buildParameterSettings(): App3ParameterSettings? {
        val current = _state.value
        val targetTorque = current.parameterTargetTorque.toIntOrNull()
        val torqueLower = current.parameterTorqueLower.toIntOrNull()
        val torqueUpper = current.parameterTorqueUpper.toIntOrNull()
        val targetAngle = if (current.parameterMode == App3ParameterSettings.MODE_M1) {
            0.0
        } else {
            current.parameterTargetAngle.toDoubleOrNull()
        }
        val angleUpper = current.parameterAngleUpper.toDoubleOrNull()
        val angleLower = current.parameterAngleLower.toDoubleOrNull()
        if (
            targetTorque == null ||
            torqueLower == null ||
            torqueUpper == null ||
            targetAngle == null ||
            angleUpper == null ||
            angleLower == null
        ) {
            _state.update { it.copy(parameterStatus = "参数不完整，请填写扭矩和角度") }
            appendLog("参数写入被拦截：存在空值或非法数字")
            return null
        }
        return runCatching {
            App3ParameterSettings(
                mode = current.parameterMode,
                targetTorqueNm = targetTorque,
                torqueLowerNm = torqueLower,
                torqueUpperNm = torqueUpper,
                targetAngleDeg = targetAngle,
                angleUpperDeg = angleUpper,
                angleLowerDeg = angleLower,
            )
        }.onFailure { throwable ->
            _state.update { it.copy(parameterStatus = throwable.message ?: "参数超出协议范围") }
            appendLog("参数写入被拦截：${throwable.message ?: "参数超出协议范围"}")
        }.getOrNull()
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
                try {
                    measurementMutex.withLock { handlePacket(packet) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    _state.update {
                        it.copy(lastProtocolSummary = "协议解析失败：${throwable.message ?: "未知错误"}")
                    }
                    appendLog("协议解析失败，已保留原始 HEX：${throwable.message ?: "未知错误"}")
                }
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
                runCatching {
                    connection.send(WrenchPacketBuilder.timeSync())
                }.onSuccess {
                    _state.update { it.copy(lastProtocolSummary = "扳手请求校时，已自动回复") }
                    appendLog("扳手请求时间校准，已自动回复")
                }.onFailure { throwable ->
                    _state.update { it.copy(lastProtocolSummary = "扳手请求校时，回复失败") }
                    appendLog("扳手请求时间校准，回复失败：${throwable.message ?: "未知错误"}")
                }
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
                lastCurvePointReceiveTime = now,
                persistedPointCount = old.persistedPointCount + newMeasuredPoints.size,
                recentPoints = merged,
                saveStatus = "已保存 ${merged.size} 个显示点，原始帧已入库",
            )
        }
    }

    private suspend fun handleResult(packet: WrenchPacket, now: Long) {
        val result = ResultFrameDecoder.decode(packet)
        val ackResult = runCatching {
            connection.send(WrenchPacketBuilder.resultAck())
        }
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
            ).withSyncedResultSettings(result)
        }
        persistSessionSummary(activeSessionId, result.status.label, result, pulseResult = null)
        val ackText = if (ackResult.isSuccess) {
            "已自动确认"
        } else {
            "确认发送失败：${ackResult.exceptionOrNull()?.message ?: "未知错误"}"
        }
        appendLog(
            "收到最终结果：${result.status.label}；目标/实际扭矩 " +
                "${result.targetTorqueNm.formatLog()}/${result.actualTorqueNm.formatLog()} Nm；" +
                "目标/实际角度 ${result.targetAngleDeg.formatLog()}/${result.actualAngleDeg.formatLog()}°；$ackText",
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
                lastCurvePointReceiveTime = now,
                persistedPointCount = old.persistedPointCount + 1,
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
        connection.close()
        super.onCleared()
    }
}

private fun Double?.formatLog(): String = this?.let { "%.1f".format(it) } ?: "--"

private fun String.sanitizeUIntText(maxLength: Int = 5): String =
    filter { it.isDigit() }.take(maxLength)

private fun String.sanitizeDecimalText(maxLength: Int = 7): String {
    val builder = StringBuilder()
    var dotSeen = false
    for (char in this) {
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !dotSeen -> {
                builder.append(char)
                dotSeen = true
            }
        }
        if (builder.length >= maxLength) break
    }
    return builder.toString()
}

private fun App3ParameterSettings.modeLabel(): String =
    if (mode == App3ParameterSettings.MODE_M1) "M1 定扭矩" else "M2 角度控制"

private fun MeasurementState.withSyncedResultSettings(result: ResultFrame): MeasurementState {
    val targetTorque = result.targetTorqueNm?.roundToInt()?.toString() ?: parameterTargetTorque
    val targetAngle = result.targetAngleDeg?.formatInput() ?: parameterTargetAngle
    val torqueUpper = result.torqueUpperNm?.roundToInt()?.toString() ?: parameterTorqueUpper
    val torqueLower = result.torqueLowerNm?.roundToInt()?.toString() ?: parameterTorqueLower
    val angleUpper = result.angleUpperDeg?.formatInput() ?: parameterAngleUpper
    val angleLower = result.angleLowerDeg?.formatInput() ?: parameterAngleLower
    val hasAnySetting =
        result.targetTorqueNm != null ||
            result.targetAngleDeg != null ||
            result.torqueUpperNm != null ||
            result.torqueLowerNm != null ||
            result.angleUpperDeg != null ||
            result.angleLowerDeg != null
    return if (hasAnySetting) {
        copy(
            parameterTargetTorque = targetTorque,
            parameterTargetAngle = targetAngle,
            parameterTorqueUpper = torqueUpper,
            parameterTorqueLower = torqueLower,
            parameterAngleUpper = angleUpper,
            parameterAngleLower = angleLower,
            parameterStatus = "已从扳手最终结果同步目标设置",
        )
    } else {
        this
    }
}

private fun Double.formatInput(): String =
    if (abs(this - roundToInt()) < 1e-9) roundToInt().toString() else "%.1f".format(this)
