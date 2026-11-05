package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.chuankangkk.wrenchlift.connection.ConnectionState
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.canConfirmEffectivePoint
import com.chuankangkk.wrenchlift.measurement.canRemoveEffectivePoint
import com.chuankangkk.wrenchlift.measurement.exportDestinationText
import com.chuankangkk.wrenchlift.measurement.exportReadinessText
import com.chuankangkk.wrenchlift.measurement.hasWrenchMeasurementData
import com.chuankangkk.wrenchlift.measurement.measurementGateStatus
import com.chuankangkk.wrenchlift.protocol.WifiWrenchParameterSettings

@Composable
fun ConnectionPanel(
    state: MeasurementState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSlabNoChange: (String) -> Unit,
    onPointNoChange: (String) -> Unit,
    onOpenWifiSettings: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEnableRemote: () -> Unit,
    onDisableRemote: () -> Unit,
    onStartForward: () -> Unit,
    onStartReverse: () -> Unit,
    onStop: () -> Unit,
    onHeartbeat: () -> Unit,
    onParameterModeChange: (Int) -> Unit,
    onParameterTargetTorqueChange: (String) -> Unit,
    onParameterTorqueLowerChange: (String) -> Unit,
    onParameterTorqueUpperChange: (String) -> Unit,
    onParameterTargetAngleChange: (String) -> Unit,
    onParameterAngleUpperChange: (String) -> Unit,
    onParameterAngleLowerChange: (String) -> Unit,
    onWriteParameterSettings: () -> Unit,
    onSave: () -> Unit,
    onExportCsv: () -> Unit,
    onConfirmEffectivePoint: () -> Unit,
    onRemoveEffectivePoint: () -> Unit,
) {
    var pendingDangerAction by remember { mutableStateOf<DangerAction?>(null) }
    var showAdvancedParameters by remember { mutableStateOf(false) }
    val controlsEnabled = state.connectionState.isOnline
    val isConnecting = state.connectionState is ConnectionState.Connecting
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
            shape = MaterialTheme.shapes.large,
        )
    }

    ScrollColumn {
        SidebarStatus(state)

        SidebarGroup("一 连接扳手") {
            ActionButton(
                when {
                    isConnecting -> "连接中"
                    controlsEnabled -> "重新连接"
                    else -> "连接扳手"
                },
                onConnect,
                Modifier.fillMaxWidth(),
                if (controlsEnabled) AppColors.info else AppColors.running,
                enabled = !isConnecting,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                SecondaryButton("Wi-Fi 设置", onOpenWifiSettings, Modifier.widthIn(min = 128.dp))
                SecondaryButton("断开连接", onDisconnect, Modifier.widthIn(min = 128.dp), enabled = controlsEnabled)
            }
        }

        SidebarGroup("二 扳手操作") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                ActionButton("手机控制", onEnableRemote, Modifier.widthIn(min = 130.dp), AppColors.running, enabled = controlsEnabled)
                ActionButton("关闭控制", onDisableRemote, Modifier.widthIn(min = 130.dp), AppColors.info, enabled = controlsEnabled)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
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
                    modifier = Modifier.widthIn(min = 130.dp),
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
                    modifier = Modifier.widthIn(min = 130.dp),
                    color = AppColors.info,
                    enabled = controlsEnabled,
                )
            }
            ActionButton("停止扳手", onStop, Modifier.fillMaxWidth(), AppColors.danger, enabled = controlsEnabled)
        }

        SidebarGroup("三 目标设置") {
            SecondaryButton(
                if (showAdvancedParameters) "收起目标设置" else "展开目标设置",
                onClick = { showAdvancedParameters = !showAdvancedParameters },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = state.parameterStatus,
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAdvancedParameters) {
                ParameterSettingsBlock(
                    state = state,
                    controlsEnabled = controlsEnabled,
                    onModeChange = onParameterModeChange,
                    onTargetTorqueChange = onParameterTargetTorqueChange,
                    onTorqueLowerChange = onParameterTorqueLowerChange,
                    onTorqueUpperChange = onParameterTorqueUpperChange,
                    onTargetAngleChange = onParameterTargetAngleChange,
                    onAngleUpperChange = onParameterAngleUpperChange,
                    onAngleLowerChange = onParameterAngleLowerChange,
                    onRequestWrite = {
                        pendingDangerAction = DangerAction(
                            title = "确认写入目标设置",
                            message = state.parameterConfirmMessage(),
                            confirmLabel = "确认写入",
                            color = AppColors.warning,
                            run = onWriteParameterSettings,
                        )
                    },
                )
            }
        }

        SidebarGroup("四 记录与参考点") {
            val hasMeasurementData = state.hasWrenchMeasurementData()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                ActionButton("保存记录", onSave, Modifier.widthIn(min = 128.dp), AppColors.warning, enabled = hasMeasurementData)
                ActionButton("导出 CSV", onExportCsv, Modifier.widthIn(min = 128.dp), AppColors.info, enabled = hasMeasurementData)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                ActionButton(
                    "标记参考点",
                    onConfirmEffectivePoint,
                    Modifier.widthIn(min = 130.dp),
                    AppColors.running,
                    enabled = state.canConfirmEffectivePoint(),
                )
                ActionButton(
                    "删除参考点",
                    onRemoveEffectivePoint,
                    Modifier.widthIn(min = 130.dp),
                    AppColors.danger,
                    enabled = state.canRemoveEffectivePoint(),
                )
            }
            Text(
                text = if (hasMeasurementData) {
                    "${state.exportReadinessText()}\n${state.exportDestinationText()}"
                } else {
                    state.measurementGateStatus()
                },
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SidebarGroup("五 工点信息") {
            OutlinedTextField(
                value = state.host,
                onValueChange = onHostChange,
                label = { Text("设备 IP") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.port,
                onValueChange = onPortChange,
                label = { Text("TCP 端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.session.slabNo,
                    onValueChange = onSlabNoChange,
                    label = { Text("板号") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.session.pointNo,
                    onValueChange = onPointNoChange,
                    label = { Text("点位") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SidebarGroup("六 设备状态") {
            SecondaryButton("检查连接", onHeartbeat, Modifier.fillMaxWidth())
            InfoLine("结果确认", "收到最终结果后自动回复")
            InfoLine("时间校准", "扳手请求校时时自动回复")
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
            InfoLine("参考点标记", "${state.effectivePointCount}")
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
private fun ParameterSettingsBlock(
    state: MeasurementState,
    controlsEnabled: Boolean,
    onModeChange: (Int) -> Unit,
    onTargetTorqueChange: (String) -> Unit,
    onTorqueLowerChange: (String) -> Unit,
    onTorqueUpperChange: (String) -> Unit,
    onTargetAngleChange: (String) -> Unit,
    onAngleUpperChange: (String) -> Unit,
    onAngleLowerChange: (String) -> Unit,
    onRequestWrite: () -> Unit,
) {
    val m1Selected = state.parameterMode == WifiWrenchParameterSettings.MODE_M1
    val m2Selected = state.parameterMode == WifiWrenchParameterSettings.MODE_M2
    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (m1Selected) {
                ActionButton("定扭矩", { onModeChange(WifiWrenchParameterSettings.MODE_M1) }, Modifier.weight(1f), AppColors.info)
            } else {
                SecondaryButton("定扭矩", { onModeChange(WifiWrenchParameterSettings.MODE_M1) }, Modifier.weight(1f))
            }
            if (m2Selected) {
                ActionButton("角度控制", { onModeChange(WifiWrenchParameterSettings.MODE_M2) }, Modifier.weight(1f), AppColors.info)
            } else {
                SecondaryButton("角度控制", { onModeChange(WifiWrenchParameterSettings.MODE_M2) }, Modifier.weight(1f))
            }
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val narrowFields = maxWidth < 300.dp
            if (narrowFields) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ParameterField(state.parameterTargetTorque, onTargetTorqueChange, "目标扭矩 Nm", Modifier.fillMaxWidth())
                    ParameterField(state.parameterTargetAngle, onTargetAngleChange, "目标角度 °", Modifier.fillMaxWidth(), enabled = m2Selected, decimal = true)
                    ParameterField(state.parameterTorqueLower, onTorqueLowerChange, "扭矩下限", Modifier.fillMaxWidth())
                    ParameterField(state.parameterTorqueUpper, onTorqueUpperChange, "扭矩上限", Modifier.fillMaxWidth())
                    ParameterField(state.parameterAngleLower, onAngleLowerChange, "角度下限", Modifier.fillMaxWidth(), decimal = true)
                    ParameterField(state.parameterAngleUpper, onAngleUpperChange, "角度上限", Modifier.fillMaxWidth(), decimal = true)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ParameterField(state.parameterTargetTorque, onTargetTorqueChange, "目标扭矩 Nm", Modifier.weight(1f))
                        ParameterField(state.parameterTargetAngle, onTargetAngleChange, "目标角度 °", Modifier.weight(1f), enabled = m2Selected, decimal = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ParameterField(state.parameterTorqueLower, onTorqueLowerChange, "扭矩下限", Modifier.weight(1f))
                        ParameterField(state.parameterTorqueUpper, onTorqueUpperChange, "扭矩上限", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ParameterField(state.parameterAngleLower, onAngleLowerChange, "角度下限", Modifier.weight(1f), decimal = true)
                        ParameterField(state.parameterAngleUpper, onAngleUpperChange, "角度上限", Modifier.weight(1f), decimal = true)
                    }
                }
            }
        }
        Text(
            text = "目标设置会改变扳手动作参数，现场确认夹具、人员和点位后再写入。",
            color = AppColors.warning,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        ActionButton(
            label = "写入目标设置",
            onClick = onRequestWrite,
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.warning,
            enabled = controlsEnabled && state.parameterInputComplete(),
        )
    }
}

@Composable
private fun ParameterField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

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
            color = AppColors.panelLine.copy(alpha = 0.42f),
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

private fun MeasurementState.parameterInputComplete(): Boolean =
    parameterTargetTorque.isNotBlank() &&
        parameterTorqueLower.isNotBlank() &&
        parameterTorqueUpper.isNotBlank() &&
        parameterAngleUpper.isNotBlank() &&
        parameterAngleLower.isNotBlank() &&
        (parameterMode == WifiWrenchParameterSettings.MODE_M1 || parameterTargetAngle.isNotBlank())

private fun MeasurementState.parameterConfirmMessage(): String {
    val modeText = if (parameterMode == WifiWrenchParameterSettings.MODE_M1) "定扭矩" else "角度控制"
    val targetAngle = if (parameterMode == WifiWrenchParameterSettings.MODE_M1) "0" else parameterTargetAngle
    return "将向扳手写入 $modeText 参数：目标扭矩 $parameterTargetTorque Nm，目标角度 $targetAngle°，扭矩范围 $parameterTorqueLower-$parameterTorqueUpper Nm，角度范围 $parameterAngleLower-$parameterAngleUpper°。请确认现场安全后再写入。"
}
