package com.chuankangkk.wrenchlift.ui.worker

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
 * 测点 / 记录页：工作台「返回」的去处。左边填板号测点开始新测量；右边是本机记录，可逐条导出 Excel。
 * 板号、测点只是现场记录字段，不是通信参数（NET-05）。
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
    var slab by rememberSaveable { mutableStateOf(state.session.slabNo) }
    var point by rememberSaveable { mutableStateOf(state.session.pointNo) }
    var retryId by remember { mutableLongStateOf(-1L) }
    Column(Modifier.fillMaxSize().background(p.canvas).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = WrenchDimens.screenEdge).testTag("records")) {
        PageTopBar("测点与记录", onBack = null) {
            StatusPill(WorkerText.linkShort(state), linkColor(state.linkPhase), icon = WrenchIcon.Wifi, onClick = onOpenGuide)
            Spacer(Modifier.width(8.dp))
            IconAction(WrenchIcon.Settings, "设置", onOpenSettings, Modifier.testTag("open_settings"))
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(WrenchDimens.gap)) {
            Panel(Modifier.weight(0.38f).fillMaxHeight(), padding = PaddingValues(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("新测点", style = WrenchType.headlineStyle.copy(fontSize = 19.sp), color = p.textPrimary)
                    Text("同一测点重复试验会各存一条记录", style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WrenchTextField(slab, { slab = it.take(32) }, "板号", Modifier.weight(1f).testTag("slab_input"))
                        WrenchTextField(point, { point = it.take(32) }, "测点", Modifier.weight(1f).testTag("point_input"))
                    }
                }
                ControlButton(
                    "开始测量", null,
                    onClick = {
                        actions.startMeasurement(slab, point)
                        onStart()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("start_measurement"),
                    tone = ButtonTone.Accent, height = 56.dp,
                    enabled = !state.connectionState.isOnline,
                )
                Text("开始后自动连接扳手", style = WrenchType.captionStyle, color = p.textTertiary, modifier = Modifier.padding(top = 4.dp))
            }
            Panel(Modifier.weight(0.62f).fillMaxHeight(), padding = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本机记录", style = WrenchType.headlineStyle.copy(fontSize = 19.sp), color = p.textPrimary, modifier = Modifier.weight(1f))
                    Text("每条导出全部过程点（Excel 含图表）", style = WrenchType.captionStyle, color = p.textSecondary)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("record_list"), contentPadding = PaddingValues(vertical = 6.dp)) {
                    if (state.recentSessions.isEmpty()) {
                        item { Text("还没有记录。填好测点后点「开始测量」。", style = WrenchType.bodyStyle, color = p.textSecondary, modifier = Modifier.padding(vertical = 16.dp)) }
                    }
                    items(state.recentSessions, key = { it.id }) { record ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${record.slabNo} · ${record.pointNo}", style = WrenchType.bodyStrong, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${recordTime(record.startTime)} · ${record.resultStatus}" + (record.actualTorqueNm?.let { " · ${WorkerText.readout(it, true)} N·m" } ?: ""),
                                    style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
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

private fun recordTime(millis: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(millis))
