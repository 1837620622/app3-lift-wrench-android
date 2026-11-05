package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.MeasurementDataOrigin
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.angleReadoutText
import com.chuankangkk.wrenchlift.measurement.canConfirmEffectivePoint
import com.chuankangkk.wrenchlift.measurement.canRemoveEffectivePoint
import com.chuankangkk.wrenchlift.measurement.dataOrigin
import com.chuankangkk.wrenchlift.measurement.exportDestinationText
import com.chuankangkk.wrenchlift.measurement.exportReadinessText
import com.chuankangkk.wrenchlift.measurement.hasWrenchMeasurementData
import com.chuankangkk.wrenchlift.measurement.measurementGateStatus
import com.chuankangkk.wrenchlift.measurement.torqueReadoutText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FocusedChartScreen(
    state: MeasurementState,
    selectedMetric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
    onBack: () -> Unit,
    onStopWrench: () -> Unit,
    onSave: () -> Unit,
    onExportCsv: () -> Unit,
    onConfirmEffectivePoint: () -> Unit,
    onRemoveEffectivePoint: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(AppColors.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        val portrait = maxHeight > maxWidth || maxWidth < AppBreakpoints.compactWidth
        val short = maxHeight < AppBreakpoints.shortHeight
        val shortLandscape = !portrait && short
        val compact = portrait || short
        val gap = if (compact) 8.dp else 12.dp
        val portraitChartHeight = (maxHeight * 0.58f).coerceIn(360.dp, 620.dp)
        val bodyModifier = if (portrait) {
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxSize()
        }

        if (shortLandscape) {
            val sideWidth = (maxWidth * 0.30f).coerceIn(250.dp, 320.dp)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                FocusHeader(
                    state = state,
                    selectedMetric = selectedMetric,
                    onBack = onBack,
                    compact = true,
                    showStatusChips = false,
                )
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    FocusedCurveWall(
                        state = state,
                        primaryMetric = selectedMetric,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(sideWidth)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        DataOriginBand(state = state, compact = true)
                        ChartMetricSelector(
                            selected = selectedMetric,
                            onSelected = onMetricSelected,
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FocusMetricStrip(state = state, compact = true)
                        FocusActionBar(
                            state = state,
                            onStopWrench = onStopWrench,
                            onSave = onSave,
                            onExportCsv = onExportCsv,
                            onConfirmEffectivePoint = onConfirmEffectivePoint,
                            onRemoveEffectivePoint = onRemoveEffectivePoint,
                            compact = true,
                            stacked = true,
                            scrollableRail = true,
                        )
                    }
                }
            }
            return@BoxWithConstraints
        }

        Column(
            modifier = bodyModifier.padding(bottom = if (compact) 12.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            FocusHeader(
                state = state,
                selectedMetric = selectedMetric,
                onBack = onBack,
                compact = compact,
                showStatusChips = !portrait,
            )
            FocusMetricStrip(state = state, compact = short)
            DataOriginBand(state = state, compact = compact)
            ChartMetricSelector(
                selected = selectedMetric,
                onSelected = onMetricSelected,
                compact = compact,
                modifier = Modifier.fillMaxWidth(),
            )
            FocusedCurveWall(
                state = state,
                primaryMetric = selectedMetric,
                modifier = if (portrait) {
                    Modifier.fillMaxWidth().height(portraitChartHeight)
                } else {
                    Modifier.weight(1f).fillMaxWidth()
                },
            )

            FocusActionBar(
                state = state,
                onStopWrench = onStopWrench,
                onSave = onSave,
                onExportCsv = onExportCsv,
                onConfirmEffectivePoint = onConfirmEffectivePoint,
                onRemoveEffectivePoint = onRemoveEffectivePoint,
                compact = compact,
                stacked = portrait,
                scrollableRail = shortLandscape,
            )
        }
    }
}

@Composable
private fun FocusedCurveWall(
    state: MeasurementState,
    primaryMetric: ChartMetric,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val origin = state.dataOrigin()
        val sourceColor = origin.color()
        EngineeringTrendChart(
            points = state.recentPoints,
            metric = primaryMetric,
            emphasized = true,
            modifier = Modifier.fillMaxSize(),
            emptyTitle = origin.chartEmptyTitle(state, primaryMetric),
            emptyDetail = origin.chartEmptyDetail(state, primaryMetric),
        )
        ChartSourceBadge(
            text = origin.chartBadgeText(state),
            color = sourceColor,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 38.dp),
        )
    }
}

@Composable
private fun ChartSourceBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = AppColors.backgroundAlt.copy(alpha = 0.90f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FocusHeader(
    state: MeasurementState,
    selectedMetric: ChartMetric,
    onBack: () -> Unit,
    compact: Boolean,
    showStatusChips: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.height(AppDimens.compactControlHeight),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, AppColors.panelLine.copy(alpha = 0.82f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textPrimary),
        ) {
            Text("返回工作台", fontSize = if (compact) 13.sp else 15.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "曲线大屏 · ${selectedMetric.label}",
                color = AppColors.textPrimary,
                fontSize = if (compact) 21.sp else 27.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Text(
                    text = "板号 ${state.session.slabNo} · 点位 ${state.session.pointNo} · 螺栓 ${state.currentBoltNo}",
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showStatusChips) {
            StatusChip(
                label = state.connectionState.label,
                color = if (state.connectionState.isOnline) AppColors.running else AppColors.info,
            )
            StatusChip(
                label = state.warningLevel.labelText(),
                color = state.warningLevel.color(),
            )
        }
    }
}

@Composable
private fun DataOriginBand(state: MeasurementState, compact: Boolean) {
    val origin = state.dataOrigin()
    val sourceLabel = origin.focusLabel
    val sourceColor = origin.color()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = sourceColor.copy(alpha = 0.10f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, sourceColor.copy(alpha = 0.38f)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(if (compact) 8.dp else 10.dp)) {
            if (maxWidth < 620.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    DataOriginValue("数据来源", sourceLabel, sourceColor, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DataOriginValue("过程点", "${state.recentPoints.size}", AppColors.teal, Modifier.weight(1f))
                        DataOriginValue("接收帧", "${state.receivedFrameCount}", AppColors.info, Modifier.weight(1f))
                        DataOriginValue("最近帧", state.lastPacketFunction?.let { functionLabel(it) } ?: "--", AppColors.amber, Modifier.weight(1f))
                    }
                    DataOriginHint(origin = origin, state = state, color = sourceColor)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DataOriginValue("数据来源", sourceLabel, sourceColor, Modifier.weight(1.25f))
                        DataOriginValue("过程点", "${state.recentPoints.size}", AppColors.teal, Modifier.weight(0.8f))
                        DataOriginValue("接收帧", "${state.receivedFrameCount}", AppColors.info, Modifier.weight(0.8f))
                        DataOriginValue("最近帧", state.lastPacketFunction?.let { functionLabel(it) } ?: "--", AppColors.amber, Modifier.weight(1f))
                        DataOriginValue(
                            "曲线时间",
                            state.lastCurvePointReceiveTime.shortTime(),
                            AppColors.signal,
                            Modifier.weight(0.95f),
                        )
                    }
                    DataOriginHint(origin = origin, state = state, color = sourceColor)
                }
            }
        }
    }
}

@Composable
private fun DataOriginHint(origin: MeasurementDataOrigin, state: MeasurementState, color: Color) {
    Text(
        text = when (origin) {
            MeasurementDataOrigin.LivePoints -> "正在显示真实扳手曲线点；最近曲线时间 ${state.lastCurvePointReceiveTime.shortTime()}。"
            MeasurementDataOrigin.RetainedRealPoints -> "当前显示上次真实扳手数据；TCP 未连接，不是正在实时回传。"
            MeasurementDataOrigin.LoadedPoints -> "当前是已载入记录，不是实时空转数据；重新连接扳手后会切到真实回传。"
            MeasurementDataOrigin.FramesOnly -> "已收到设备帧，但尚未收到可绘制的扭矩-转角过程点。"
            MeasurementDataOrigin.Waiting -> "等待真实扳手 TCP 数据；不显示本地生成曲线。"
        },
        color = color,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun MeasurementDataOrigin.color(): Color = when (this) {
    MeasurementDataOrigin.LivePoints -> AppColors.running
    MeasurementDataOrigin.RetainedRealPoints -> AppColors.warning
    MeasurementDataOrigin.LoadedPoints -> AppColors.info
    MeasurementDataOrigin.FramesOnly -> AppColors.warning
    MeasurementDataOrigin.Waiting -> AppColors.info
}

private fun MeasurementDataOrigin.chartBadgeText(state: MeasurementState): String = when (this) {
    MeasurementDataOrigin.LivePoints -> "实时回传 · ${state.recentPoints.size} 点"
    MeasurementDataOrigin.RetainedRealPoints -> "上次真实数据 · ${state.recentPoints.size} 点"
    MeasurementDataOrigin.LoadedPoints -> "已载入记录 · ${state.recentPoints.size} 点"
    MeasurementDataOrigin.FramesOnly -> "已收设备帧 · 等过程点"
    MeasurementDataOrigin.Waiting -> "等待真实数据"
}

private fun MeasurementDataOrigin.chartEmptyTitle(state: MeasurementState, metric: ChartMetric): String? = when {
    state.recentPoints.isNotEmpty() && metric == ChartMetric.FORCE -> "未标定：暂不输出正式顶升力"
    state.recentPoints.isNotEmpty() && metric == ChartMetric.PRESSURE -> "未设置面积：暂不输出正式压力"
    this == MeasurementDataOrigin.FramesOnly -> "已收设备帧，等待扭矩转角过程点"
    this == MeasurementDataOrigin.Waiting -> "等待真实扳手 TCP 数据"
    else -> null
}

private fun MeasurementDataOrigin.chartEmptyDetail(state: MeasurementState, metric: ChartMetric): String? = when {
    state.recentPoints.isNotEmpty() && metric != ChartMetric.TORQUE -> "扭矩/转角已入库；力和压力需先完成现场标定"
    this == MeasurementDataOrigin.FramesOnly -> "心跳、编号或电量帧不能绘制曲线；开始真实动作后等待 0x12 过程帧"
    this == MeasurementDataOrigin.Waiting -> "连接扳手 Wi-Fi 和 TCP 后，真实回传会自动绘制"
    else -> null
}

@Composable
private fun DataOriginValue(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            color = color,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ChartMetricSelector(
    selected: ChartMetric,
    onSelected: (ChartMetric) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = AppColors.panelInset,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AppColors.panelLine.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.space4),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.space4),
        ) {
            ChartMetric.entries.forEach { metric ->
                val checked = metric == selected
                Button(
                    onClick = { onSelected(metric) },
                    modifier = Modifier.weight(1f).height(AppDimens.compactControlHeight),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (checked) AppColors.info else Color.Transparent,
                        contentColor = if (checked) Color(0xFF071118) else AppColors.textSecondary,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = if (checked) 1.dp else 0.dp),
                ) {
                    Text(
                        text = metric.label.substringBefore("-"),
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusMetricStrip(state: MeasurementState, compact: Boolean) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.panel, MaterialTheme.shapes.medium)
            .padding(if (compact) 6.dp else 8.dp),
    ) {
        if (maxWidth < 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusStripValue("扭矩", state.torqueReadoutText(), "Nm", compact, Modifier.weight(1f))
                    FocusStripValue("转角", state.angleReadoutText(), "°", compact, Modifier.weight(1f))
                    FocusStripValue("顶升力", state.currentForceKn?.let { "%.2f".format(it) } ?: "--", "kN", compact, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusStripValue("压力", state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--", "MPa", compact, Modifier.weight(1f))
                    FocusStripValue("位移", state.displacementText(), "mm", compact, Modifier.weight(1f))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusStripValue("扭矩", state.torqueReadoutText(), "Nm", compact, Modifier.weight(1f))
                FocusStripValue("转角", state.angleReadoutText(), "°", compact, Modifier.weight(1f))
                FocusStripValue("顶升力", state.currentForceKn?.let { "%.2f".format(it) } ?: "--", "kN", compact, Modifier.weight(1f))
                FocusStripValue("压力", state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--", "MPa", compact, Modifier.weight(1f))
                FocusStripValue("位移", state.displacementText(), "mm", compact, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FocusStripValue(label: String, value: String, unit: String, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp)
        Text(
            "$value $unit",
            color = AppColors.textPrimary,
            fontSize = if (compact) 14.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun MeasurementState.displacementText(): String =
    if (currentDisplacementMm > 0.0) {
        "%.2f".format(currentDisplacementMm)
    } else {
        "--"
    }

@Composable
private fun FocusActionBar(
    state: MeasurementState,
    onStopWrench: () -> Unit,
    onSave: () -> Unit,
    onExportCsv: () -> Unit,
    onConfirmEffectivePoint: () -> Unit,
    onRemoveEffectivePoint: () -> Unit,
    compact: Boolean,
    stacked: Boolean,
    scrollableRail: Boolean,
) {
    val hasMeasurementData = state.hasWrenchMeasurementData()
    if (stacked) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                label = "停止扳手",
                onClick = onStopWrench,
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.danger,
                enabled = state.connectionState.isOnline,
            )
            if (scrollableRail) {
                ScrollActionRail(
                    buttons = listOf(
                        RailButtonSpec("保存记录", onSave, AppColors.warning, hasMeasurementData),
                        RailButtonSpec("导出 CSV", onExportCsv, AppColors.info, hasMeasurementData),
                    ),
                )
                ScrollActionRail(
                    buttons = listOf(
                        RailButtonSpec("标记参考点", onConfirmEffectivePoint, AppColors.running, state.canConfirmEffectivePoint()),
                        RailButtonSpec("删除参考点", onRemoveEffectivePoint, AppColors.danger, state.canRemoveEffectivePoint()),
                    ),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton("保存记录", onSave, Modifier.weight(1f), AppColors.warning, enabled = hasMeasurementData)
                    ActionButton("导出 CSV", onExportCsv, Modifier.weight(1f), AppColors.info, enabled = hasMeasurementData)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ActionButton(
                        "标记参考点",
                        onConfirmEffectivePoint,
                        Modifier.weight(1f),
                        AppColors.running,
                        enabled = state.canConfirmEffectivePoint(),
                    )
                    ActionButton(
                        "删除参考点",
                        onRemoveEffectivePoint,
                        Modifier.weight(1f),
                        AppColors.danger,
                        enabled = state.canRemoveEffectivePoint(),
                    )
                }
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
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(
                label = "停止扳手",
                onClick = onStopWrench,
                modifier = Modifier.weight(1f),
                color = AppColors.danger,
                enabled = state.connectionState.isOnline,
            )
            ActionButton("保存记录", onSave, Modifier.weight(1f), AppColors.warning, enabled = hasMeasurementData)
            ActionButton("导出 CSV", onExportCsv, Modifier.weight(1f), AppColors.info, enabled = hasMeasurementData)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ActionButton(
                "标记参考点",
                onConfirmEffectivePoint,
                Modifier.weight(1f),
                AppColors.running,
                enabled = state.canConfirmEffectivePoint(),
            )
            ActionButton(
                "删除参考点",
                onRemoveEffectivePoint,
                Modifier.weight(1f),
                AppColors.danger,
                enabled = state.canRemoveEffectivePoint(),
            )
            if (!compact) {
                Text(
                    text = if (hasMeasurementData) {
                        "${state.exportReadinessText()} · ${state.exportDestinationText()}"
                    } else {
                        state.measurementGateStatus()
                    },
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.5f).align(Alignment.CenterVertically),
                )
            }
        }
    }
}

private data class RailButtonSpec(
    val label: String,
    val onClick: () -> Unit,
    val color: androidx.compose.ui.graphics.Color,
    val enabled: Boolean,
)

@Composable
private fun ScrollActionRail(buttons: List<RailButtonSpec>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        buttons.forEach { button ->
            ActionButton(
                label = button.label,
                onClick = button.onClick,
                modifier = Modifier.widthIn(min = 132.dp),
                color = button.color,
                enabled = button.enabled,
            )
        }
    }
}

private fun Long?.shortTime(): String =
    this?.let { SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(it)) } ?: "--"
