package com.chuankangkk.wrenchlift.ui.worker

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.WorkerText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 记录页：工作台「返回」的去处。只有一台扳手：左边是扳手状态与「开始测量」（自动连接、建新记录，不填任何字段）；
 * 右边是本机记录，可逐条导出 Excel（全量过程点 + 原生图表）。
 */
@Composable
fun RecordsScreen(
    state: MeasurementState,
    actions: WorkerActions,
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    val p = palette
    var retryId by remember { mutableLongStateOf(-1L) }
    val online = state.connectionState.isOnline
    Column(Modifier.fillMaxSize().background(p.canvas).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = WrenchDimens.screenEdge).testTag("records")) {
        PageTopBar("记录", onBack = null) {
            StatusPill(WorkerText.linkShort(state), linkColor(state.linkPhase), icon = WrenchIcon.Wifi, onClick = onOpenGuide)
            Spacer(Modifier.width(8.dp))
            IconAction(WrenchIcon.Settings, "设置", onOpenSettings, Modifier.testTag("open_settings"))
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(WrenchDimens.gap)) {
            Panel(Modifier.weight(0.36f).fillMaxHeight().testTag("wrench_card"), padding = PaddingValues(16.dp)) {
                Text("扳手", style = WrenchType.headlineStyle.copy(fontSize = 19.sp), color = p.textPrimary)
                Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
                    InfoRow("通信", WorkerText.link(state), linkColor(state.linkPhase), dot = true)
                    Hairline()
                    InfoRow("SN", state.deviceSn.takeIf { it.isNotBlank() && it != "未知设备" } ?: "--")
                    Hairline()
                    InfoRow("电量", state.batteryPercent?.let { "$it%" } ?: "--")
                    Hairline()
                    InfoRow("类型", state.wrenchModel.label)
                }
                ControlButton(
                    if (online) "回到工作台" else "开始测量", null,
                    onClick = {
                        if (!online) actions.startMeasurement()
                        onStart()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("start_measurement"),
                    tone = ButtonTone.Accent, height = 56.dp,
                )
            }
            Panel(Modifier.weight(0.64f).fillMaxHeight(), padding = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 32.dp)) {
                    Text("本机记录", style = WrenchType.headlineStyle.copy(fontSize = 19.sp), color = p.textPrimary, modifier = Modifier.weight(1f))
                    if (state.recentSessions.isNotEmpty()) {
                        Text("${state.recentSessions.size} 条", style = WrenchType.labelStyle, color = p.textSecondary)
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("record_list"), contentPadding = PaddingValues(vertical = 6.dp)) {
                    if (state.recentSessions.isEmpty()) {
                        item { Text("还没有记录", style = WrenchType.bodyStyle, color = p.textSecondary, modifier = Modifier.padding(vertical = 16.dp)) }
                    }
                    items(state.recentSessions, key = { it.id }) { record ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "R${record.id} · ${recordTime(record.startTime)}",
                                    style = WrenchType.bodyStrong.copy(fontSize = 16.sp, fontFeatureSettings = "tnum"), color = p.textPrimary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    record.resultStatus + (record.actualTorqueNm?.let { " · ${WorkerText.readout(it, true)} N·m" } ?: ""),
                                    style = WrenchType.labelStyle, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            ControlButton("导出", WrenchIcon.Export, {
                                retryId = record.id
                                actions.exportSession(record.id)
                            }, enabled = !state.exportInProgress, height = WrenchDimens.touch, modifier = Modifier.testTag("export_${record.id}"))
                        }
                        Hairline()
                    }
                    if (state.recentSessions.size >= 20) {
                        item {
                            TextButton(onClick = actions.loadMoreHistory, modifier = Modifier.heightIn(min = WrenchDimens.touch)) {
                                Text("加载更多记录", style = WrenchType.buttonStyle, color = p.accent)
                            }
                        }
                    }
                }
            }
        }
    }
    ExportDialogs(state, actions, onRetry = { if (retryId > 0) actions.exportSession(retryId) else actions.export() })
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = palette.textPrimary, dot: Boolean = false) {
    val p = palette
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = WrenchType.labelStyle.copy(fontSize = 14.sp), color = p.textSecondary, modifier = Modifier.width(52.dp))
        if (dot) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(valueColor))
            Spacer(Modifier.width(6.dp))
        }
        Text(value, style = WrenchType.valueStyle, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun recordTime(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(millis))
