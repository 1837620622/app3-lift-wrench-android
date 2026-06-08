package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.MeasurementState

@Composable
fun ConnectionPanel(
    state: MeasurementState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSlabNoChange: (String) -> Unit,
    onPointNoChange: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEnableRemote: () -> Unit,
    onDisableRemote: () -> Unit,
    onStartForward: () -> Unit,
    onStartReverse: () -> Unit,
    onStop: () -> Unit,
    onHeartbeat: () -> Unit,
    onResultAck: () -> Unit,
    onTimeSync: () -> Unit,
    onSave: () -> Unit,
    onConfirmEffectivePoint: () -> Unit,
) {
    var pendingDangerAction by remember { mutableStateOf<DangerAction?>(null) }
    val controlsEnabled = state.connectionState.isOnline
    pendingDangerAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDangerAction = null },
            title = { Text(action.title) },
            text = {
                Text(
                    text = action.message,
                    color = AppColors.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDangerAction = null
                        action.run()
                    },
                ) {
                    Text(action.confirmLabel, color = action.color, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDangerAction = null }) {
                    Text("取消")
                }
            },
            containerColor = AppColors.panelStrong,
            titleContentColor = AppColors.textPrimary,
            textContentColor = AppColors.textSecondary,
        )
    }

    ScrollColumn {
        SidebarStatus(state)

        SidebarGroup("一 连接扳手") {
            ActionButton(
                if (controlsEnabled) "重新连接" else "连接扳手",
                onConnect,
                Modifier.fillMaxWidth(),
                if (controlsEnabled) AppColors.info else AppColors.running,
            )
            SecondaryButton("断开连接", onDisconnect, Modifier.fillMaxWidth(), enabled = controlsEnabled)
        }

        SidebarGroup("二 扳手操作") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("手机控制", onEnableRemote, Modifier.weight(1f), AppColors.running, enabled = controlsEnabled)
                ActionButton("关闭控制", onDisableRemote, Modifier.weight(1f), AppColors.info, enabled = controlsEnabled)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton(
                    "正向顶升",
                    onClick = {
                        pendingDangerAction = DangerAction(
                            title = "确认正向顶升",
                            message = "手机将向扳手发送正向顶升指令。请确认人员、夹具、钢弹簧点位和板缝状态安全。",
                            confirmLabel = "确认顶升",
                            color = AppColors.running,
                            run = onStartForward,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    color = AppColors.running,
                    enabled = controlsEnabled,
                )
                ActionButton(
                    "反向回退",
                    onClick = {
                        pendingDangerAction = DangerAction(
                            title = "确认反向回退",
                            message = "手机将向扳手发送反向回退指令。请确认当前点位允许回退，且不会造成支撑失稳。",
                            confirmLabel = "确认回退",
                            color = AppColors.info,
                            run = onStartReverse,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    color = AppColors.info,
                    enabled = controlsEnabled,
                )
            }
            ActionButton("停止扳手", onStop, Modifier.fillMaxWidth(), AppColors.danger, enabled = controlsEnabled)
        }

        SidebarGroup("三 记录与平衡点") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ActionButton("保存记录", onSave, Modifier.weight(1f), AppColors.warning)
                ActionButton(
                    "确认平衡点",
                    onConfirmEffectivePoint,
                    Modifier.weight(1f),
                    AppColors.running,
                    enabled = state.recentPoints.isNotEmpty(),
                )
            }
            Text(
                text = state.saveStatus,
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SidebarGroup("四 工点信息") {
            OutlinedTextField(
                value = state.host,
                onValueChange = onHostChange,
                label = { Text("设备 IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = onPortChange,
                label = { Text("TCP 端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.session.slabNo,
                    onValueChange = onSlabNoChange,
                    label = { Text("板号") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.session.pointNo,
                    onValueChange = onPointNoChange,
                    label = { Text("点位") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SidebarGroup("五 设备状态") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton("检查连接", onHeartbeat, Modifier.weight(1f))
                SecondaryButton("结果自动确认", onResultAck, Modifier.weight(1f))
            }
            SecondaryButton("自动校准时间", onTimeSync, Modifier.fillMaxWidth())
            InfoLine("当前 Wi-Fi", state.currentWifiName)
            InfoLine("本机 IP", state.localIp)
            InfoLine("默认网关", state.gatewayIp)
            InfoLine("扳手网段", if (state.inWrenchSubnet) "已在 192.168.4.x" else "未进入 192.168.4.x")
            InfoLine("发现端口", state.discoveredEndpoint)
            InfoLine("设备端口", state.devicePortReachable.reachabilityText("未测试端口"))
            InfoLine("网络结果", state.networkDiagnosticSummary)
            InfoLine("设备 SN", state.deviceSn.ifBlank { state.session.deviceSn })
            InfoLine("最近消息", state.lastProtocolSummary)
            InfoLine("结果状态", state.resultStatus)
            InfoLine("平衡点数", "${state.effectivePointCount}")
            Text(
                text = state.lastRawHex.ifBlank { "尚未接收原始帧" },
                color = AppColors.running,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private data class DangerAction(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val color: androidx.compose.ui.graphics.Color,
    val run: () -> Unit,
)

@Composable
private fun SidebarStatus(state: MeasurementState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SidebarMetric("连接", state.connectionState.label, if (state.connectionState.isOnline) AppColors.running else AppColors.info, Modifier.weight(1f))
        SidebarMetric("点位", state.session.pointNo, AppColors.warning, Modifier.weight(1f))
        SidebarMetric("帧数", "${state.receivedFrameCount}", AppColors.running, Modifier.weight(1f))
    }
}

@Composable
private fun SidebarMetric(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(0.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SidebarGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle(title)
        content()
        HorizontalDivider(
            modifier = Modifier.padding(top = 2.dp),
            thickness = 1.dp,
            color = AppColors.grid.copy(alpha = 0.45f),
        )
    }
}

@Composable
fun SectionTitle(label: String) {
    Text(
        text = label,
        color = AppColors.textPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = AppColors.textSecondary, fontSize = 12.sp)
        Text(
            value,
            color = AppColors.textPrimary,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Boolean?.reachabilityText(unknown: String): String = when (this) {
    true -> "可连接"
    false -> "未连通"
    null -> unknown
}
