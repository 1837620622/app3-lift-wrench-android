package com.chuankangkk.wrenchlift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.BuildConfig
import com.chuankangkk.wrenchlift.connection.ConnectionState
import com.chuankangkk.wrenchlift.measurement.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** 主页面只处理现场测量；协议控制与原始帧留在调试版的独立入口。 */
@Composable
fun WorkerWorkbench(
    state: MeasurementState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onExport: () -> Unit,
    onExportSession: (Long) -> Unit,
    onLoadMoreHistory: () -> Unit,
    onNewRecord: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSlabChange: (String) -> Unit,
    onPointChange: (String) -> Unit,
    onWifiSettings: () -> Unit,
    debugContent: @Composable () -> Unit,
    onWifiNameChange: (String) -> Unit = {},
) {
    var page by rememberSaveable { mutableStateOf("measurement") }
    var confirmNew by remember { mutableStateOf(false) }
    BackHandler(enabled = page != "measurement") { page = "measurement" }
    val online = state.connectionState.isOnline
    val connecting = state.connectionState is ConnectionState.Connecting
    var clockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(online) {
        while (online) {
            clockMillis = System.currentTimeMillis()
            delay(1000)
        }
    }
    val ageSeconds = state.lastCurvePointReceiveTime?.let { ((clockMillis - it).coerceAtLeast(0) / 1000) }
    val fresh = online && ageSeconds != null && ageSeconds <= 5
    val connectionColor by animateColorAsState(if (fresh) AppColors.running else AppColors.textSecondary, label = "connectionStatus")
    Surface(color = AppColors.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != "measurement") TextButton(onClick = { page = "measurement" }) { Text("返回测量") }
                Text(when (page) {
                    "settings" -> "连接与测点"
                    "history" -> "测量记录"
                    "debug" -> "调试工具"
                    else -> "顶升测量"
                }, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 18.sp)
                if (page == "measurement") {
                    TextButton(onClick = { page = "history" }, modifier = Modifier.testTag("history")) { Text("记录") }
                    TextButton(onClick = { page = "settings" }, modifier = Modifier.testTag("settings")) { Text("设置") }
                    if (BuildConfig.DEBUG) TextButton(onClick = { page = "debug" }, modifier = Modifier.testTag("debug")) { Text("调试") }
                }
            }
            HorizontalDivider()
            when (page) {
                "settings" -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("先连接设备 Wi-Fi，再连接设备。名称只用于引导，协议与通信端口单独设置。")
                    OutlinedTextField(state.targetWifiName, onWifiNameChange, label = { Text("目标 Wi-Fi 名称（可自定义）") }, placeholder = { Text("填写设备铭牌上的名称") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("wifi_name"))
                    Text("当前 Wi-Fi：${state.currentWifiName}", color = AppColors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(state.host, onHostChange, label = { Text("设备 IP") }, singleLine = true, enabled = !online && !connecting, modifier = Modifier.weight(2f))
                        OutlinedTextField(state.port, onPortChange, label = { Text("端口") }, singleLine = true, enabled = !online && !connecting, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(state.session.slabNo, onSlabChange, label = { Text("板号") }, singleLine = true, enabled = !online, modifier = Modifier.weight(1f))
                        OutlinedTextField(state.session.pointNo, onPointChange, label = { Text("测点") }, singleLine = true, enabled = !online, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onWifiSettings) { Text("打开 Wi-Fi 设置") }
                        Button(onClick = if (online) onDisconnect else onConnect, enabled = !connecting) { Text(if (online) "断开设备" else "连接扳手") }
                    }
                    Text(state.networkDiagnosticSummary, color = AppColors.textSecondary)
                    Text("软件版本 ${BuildConfig.VERSION_NAME} · 构建 ${BuildConfig.VERSION_CODE}", color = AppColors.textSecondary)
                }
                "history" -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                    item { Text("记录保存在本机。每条 CSV 导出全部采集点，曲线显示窗口不影响导出。", color = AppColors.textSecondary) }
                    if (state.recentSessions.isEmpty()) item { Text("暂无测量记录，连接扳手后开始采集。") }
                    items(state.recentSessions, key = { it.id }) { session ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${session.slabNo} / ${session.pointNo}", fontWeight = FontWeight.SemiBold)
                                    Text("${SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(session.startTime))} · ${session.resultStatus}", color = AppColors.textSecondary, fontSize = 12.sp)
                                }
                                OutlinedButton(onClick = { onExportSession(session.id) }, enabled = !state.exportInProgress) { Text("导出 CSV") }
                            }
                            HorizontalDivider(Modifier.padding(top = 8.dp))
                        }
                    }
                    item { TextButton(onClick = onLoadMoreHistory) { Text("加载更多记录") } }
                    item { Text(state.saveStatus, color = AppColors.textSecondary) }
                }
                "debug" -> if (BuildConfig.DEBUG) Box(Modifier.weight(1f).fillMaxWidth()) { debugContent() }
                else -> {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(state.connectionState.label, color = connectionColor, fontWeight = FontWeight.Bold)
                        Text("${state.session.slabNo} / ${state.session.pointNo}", modifier = Modifier.weight(1f), maxLines = 1)
                        Button(onClick = if (online) onDisconnect else onConnect, enabled = !connecting, modifier = Modifier.testTag("connect")) { Text(if (online) "结束采集" else if (connecting) "正在连接" else "连接并采集") }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(bottom = 8.dp)) {
                        val compactReadout = maxHeight < 190.dp
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.widthIn(min = 120.dp, max = 180.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                                Readout("扭矩", state.torqueReadoutText(), "N·m", compactReadout)
                                Readout("转角", state.angleReadoutText(), "°", compactReadout)
                            }
                            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("分离点：待数据分析", color = AppColors.textSecondary, fontSize = 12.sp, maxLines = 1)
                                EngineeringTrendChart(state.recentPoints, ChartMetric.TORQUE, modifier = Modifier.weight(1f).fillMaxWidth().testTag("torque_chart"))
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(when {
                                fresh -> "实时回传 · 已入库 ${state.persistedPointCount} 点"
                                online && ageSeconds != null -> "过程数据暂停 · ${ageSeconds} 秒前更新"
                                online -> "已连接 · 等待过程数据"
                                state.recentPoints.isNotEmpty() -> "采集已停止 · 已保留数据"
                                state.targetWifiName.isNotBlank() -> "请连接 Wi-Fi：${state.targetWifiName}"
                                else -> "先连接设备 Wi-Fi，再开始采集"
                            }, fontSize = 13.sp, maxLines = 2)
                            Text("v${BuildConfig.VERSION_NAME}", fontSize = 10.sp, color = AppColors.textMuted)
                        }
                        TextButton(onClick = { confirmNew = true }, enabled = !online && !connecting && !state.exportInProgress, modifier = Modifier.testTag("new_record")) { Text("新建记录") }
                        Button(onClick = onExport, enabled = state.hasWrenchMeasurementData() && !state.exportInProgress, modifier = Modifier.testTag("export")) { Text(if (state.exportInProgress) "导出中" else "导出 CSV") }
                    }
                    if (state.saveStatus != "尚未保存") Text(state.saveStatus, fontSize = 12.sp, color = AppColors.textSecondary, maxLines = 2, modifier = Modifier.padding(bottom = 4.dp))
                    (state.connectionState as? ConnectionState.Error)?.let { Text(it.message, color = AppColors.danger, fontSize = 12.sp, maxLines = 2) }
                }
            }
        }
    }
    if (confirmNew) AlertDialog(onDismissRequest = { confirmNew = false }, title = { Text("新建测量记录？") }, text = { Text("当前数据保留在记录中。清空显示后，可在设置中填写新测点，再连接采集。") }, confirmButton = { TextButton(onClick = { confirmNew = false; onNewRecord() }) { Text("新建") } }, dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("取消") } })
}

@Composable
private fun Readout(label: String, value: String, unit: String, compact: Boolean) {
    Column {
        Text("$label $unit", color = AppColors.textSecondary, fontSize = if (compact) 12.sp else 14.sp)
        Text(value, fontSize = if (compact) 26.sp else 36.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
