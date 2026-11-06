package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.chuankangkk.wrenchlift.measurement.WarningLevel
import com.chuankangkk.wrenchlift.measurement.angleReadoutText
import com.chuankangkk.wrenchlift.measurement.dataOrigin
import com.chuankangkk.wrenchlift.measurement.exportDestinationText
import com.chuankangkk.wrenchlift.measurement.exportReadinessText
import com.chuankangkk.wrenchlift.measurement.hasWrenchMeasurementData
import com.chuankangkk.wrenchlift.measurement.measurementGateStatus
import com.chuankangkk.wrenchlift.measurement.saveExportEvidenceText
import com.chuankangkk.wrenchlift.measurement.torqueReadoutText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RealtimeDashboard(
    state: MeasurementState,
    onExportCsv: () -> Unit,
    onOpenChart: (ChartMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfacePanel(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < AppBreakpoints.compactHeight || maxWidth < AppBreakpoints.mediumWidth
            val scrollContent = compact
            val padding = if (compact) 10.dp else 16.dp
            val gap = if (compact) 8.dp else 12.dp
            val columnModifier = if (scrollContent) {
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
            } else {
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            }
            if (scrollContent) {
                val heroHeight = (maxHeight * 0.72f).coerceIn(430.dp, 560.dp)
                Column(
                    modifier = columnModifier,
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    WorkbenchHero(
                        state = state,
                        onExportCsv = onExportCsv,
                        onOpenChart = onOpenChart,
                        compact = true,
                        modifier = Modifier.fillMaxWidth().height(heroHeight),
                    )
                    ReadoutBand(state = state, compact = true)
                    FieldEvidenceBand(state = state, compact = true)
                    StatusLine(state = state, compact = true)
                }
            } else {
                Column(
                    modifier = columnModifier,
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    WorkbenchHero(
                        state = state,
                        onExportCsv = onExportCsv,
                        onOpenChart = onOpenChart,
                        compact = false,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    ReadoutBand(state = state, compact = false)
                    FieldEvidenceBand(state = state, compact = false)
                    WrenchSettingSyncBand(state)
                    DataStrip(state)
                    StatusLine(state = state, compact = false)
                }
            }
        }
    }
}

@Composable
private fun WorkbenchHero(
    state: MeasurementState,
    onExportCsv: () -> Unit,
    onOpenChart: (ChartMetric) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = AppColors.panelInset,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, AppColors.panelLine.copy(alpha = 0.55f)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 18.dp)) {
            val narrow = maxWidth < 620.dp
            val trend = state.fieldTrend()
            val forceText = state.currentForceKn?.let { "%.2f".format(it) } ?: "--"
            val forceCaption = if (state.currentForceKn == null) {
                if (state.recentPoints.isEmpty()) {
                    "等待扳手负载数据；未标定不显示正式力"
                } else {
                    "已有扭矩/转角；未标定不显示正式力"
                }
            } else {
                "${state.warningLevel.labelText()} · ${trend.label}"
            }
            if (narrow) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    WorkbenchPrimaryReadout(
                        state = state,
                        value = forceText,
                        caption = forceCaption,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    WorkbenchCurveActions(
                        state = state,
                        onExportCsv = onExportCsv,
                        onOpenChart = onOpenChart,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WorkbenchPrimaryReadout(
                        state = state,
                        value = forceText,
                        caption = forceCaption,
                        compact = compact,
                        modifier = Modifier.weight(1.25f).fillMaxWidth(),
                    )
                    WorkbenchCurveActions(
                        state = state,
                        onExportCsv = onExportCsv,
                        onOpenChart = onOpenChart,
                        compact = compact,
                        modifier = Modifier.weight(0.85f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkbenchPrimaryReadout(
    state: MeasurementState,
    value: String,
    caption: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val veryNarrow = maxWidth < 300.dp
        val valueSize = when {
            veryNarrow -> 40.sp
            compact -> 50.sp
            else -> 80.sp
        }
        val titleSize = when {
            veryNarrow -> 20.sp
            compact -> 24.sp
            else -> 34.sp
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp),
        ) {
            Text(
                "现场主读数",
                color = AppColors.textSecondary,
                fontSize = if (compact) 13.sp else 15.sp,
                maxLines = 1,
            )
            Text(
                "顶升力",
                color = AppColors.textPrimary,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    color = AppColors.warning,
                    fontSize = valueSize,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "kN",
                    color = AppColors.textSecondary,
                    fontSize = if (compact) 16.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, bottom = if (compact) 10.dp else 16.dp),
                )
            }
            Text(
                caption,
                color = AppColors.textSecondary,
                fontSize = if (compact) 13.sp else 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                FieldStepRail(state = state)
            }
        }
    }
}

@Composable
private fun FieldStepRail(state: MeasurementState) {
    val hasMeasurementData = state.hasWrenchMeasurementData()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WorkStepChip(
            label = "Wi-Fi",
            value = when {
                state.inWrenchSubnet -> "扳手网段"
                state.currentWifiName != "--" -> "已连接"
                else -> "未确认"
            },
            color = if (state.inWrenchSubnet) AppColors.running else AppColors.info,
            modifier = Modifier.weight(1f),
        )
        WorkStepChip(
            label = "TCP",
            value = if (state.connectionState.isOnline) "已连接" else state.connectionState.label,
            color = if (state.connectionState.isOnline) AppColors.running else AppColors.warning,
            modifier = Modifier.weight(1f),
        )
        WorkStepChip(
            label = "曲线点",
            value = when {
                state.recentPoints.isNotEmpty() -> "${state.recentPoints.size} 点"
                state.receivedFrameCount > 0 -> "等过程点"
                else -> "无数据"
            },
            color = if (state.recentPoints.isNotEmpty()) AppColors.teal else AppColors.amber,
            modifier = Modifier.weight(1f),
        )
        WorkStepChip(
            label = "导出",
            value = when {
                !hasMeasurementData -> "等数据"
                state.saveStatus.startsWith("CSV 已导出") -> "已完成"
                state.saveStatus.contains("失败") -> "失败"
                else -> "未导出"
            },
            color = when {
                !hasMeasurementData -> AppColors.amber
                state.saveStatus.startsWith("CSV 已导出") -> AppColors.running
                state.saveStatus.contains("失败") -> AppColors.danger
                else -> AppColors.info
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WorkStepChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WorkbenchCurveActions(
    state: MeasurementState,
    onExportCsv: () -> Unit,
    onOpenChart: (ChartMetric) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val defaultMetric = if (state.currentForceKn == null) ChartMetric.TORQUE else ChartMetric.FORCE
    val hasMeasurementData = state.hasWrenchMeasurementData()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        ActionButton(
            label = "打开曲线大屏",
            onClick = { onOpenChart(defaultMetric) },
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.info,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                label = "顶升力",
                onClick = { onOpenChart(ChartMetric.FORCE) },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                label = "扭矩",
                onClick = { onOpenChart(ChartMetric.TORQUE) },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                label = "压力",
                onClick = { onOpenChart(ChartMetric.PRESSURE) },
                modifier = Modifier.weight(1f),
            )
        }
        SecondaryButton(
            label = "导出 CSV",
            onClick = onExportCsv,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasMeasurementData,
        )
        Text(
            text = if (hasMeasurementData) state.exportReadinessText() else state.measurementGateStatus(),
            color = if (hasMeasurementData) AppColors.running else AppColors.textSecondary,
            fontSize = if (compact) 12.sp else 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (!compact) {
            Text(
                text = state.exportDestinationText(),
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!compact) {
            WorkbenchHealthRow(state = state, compact = false)
        }
    }
}

@Composable
private fun WorkbenchHealthRow(state: MeasurementState, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WorkbenchHealthChip(
                label = "连接",
                value = state.connectionState.label,
                color = if (state.connectionState.isOnline) AppColors.running else AppColors.info,
                modifier = Modifier.weight(1f),
            )
            WorkbenchHealthChip(
                label = "帧数",
                value = "${state.receivedFrameCount}",
                color = AppColors.teal,
                modifier = Modifier.weight(1f),
            )
            if (!compact) {
                WorkbenchHealthChip(
                    label = "点数",
                    value = "${state.recentPoints.size}",
                    color = AppColors.amber,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WorkbenchHealthChip(
                    label = "设备",
                    value = state.deviceSn.ifBlank { "--" },
                    color = AppColors.info,
                    modifier = Modifier.weight(1f),
                )
                WorkbenchHealthChip(
                    label = "地址",
                    value = "${state.host}:${state.port}",
                    color = AppColors.signal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WorkbenchHealthChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FieldEvidenceBand(state: MeasurementState, compact: Boolean) {
    val origin = state.dataOrigin()
    val sourceLabel = origin.dashboardLabel
    val sourceColor = origin.color()
    val saveStatus = state.saveExportEvidenceText()
    Surface(
        color = AppColors.panelInset,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AppColors.panelLine.copy(alpha = 0.42f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 7.dp else 9.dp),
        ) {
            if (maxWidth < 560.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        EvidenceCell("来源", sourceLabel, sourceColor, Modifier.weight(1.1f), compact)
                        EvidenceCell("帧数", "${state.receivedFrameCount}", AppColors.teal, Modifier.weight(0.8f), compact)
                        EvidenceCell("点数", "${state.recentPoints.size}", AppColors.amber, Modifier.weight(0.8f), compact)
                    }
                    EvidenceCell("保存/导出", saveStatus, AppColors.info, Modifier.fillMaxWidth(), compact)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EvidenceCell("来源", sourceLabel, sourceColor, Modifier.weight(1f), compact)
                    EvidenceCell("端点", "${state.host}:${state.port}", AppColors.signal, Modifier.weight(1f), compact)
                    EvidenceCell("最近帧", state.lastPacketFunction?.let { functionLabel(it) } ?: "--", AppColors.amber, Modifier.weight(1f), compact)
                    EvidenceCell("曲线时间", state.lastCurvePointReceiveTime.shortTime(), AppColors.teal, Modifier.weight(0.9f), compact)
                    EvidenceCell("保存/导出", saveStatus, AppColors.info, Modifier.weight(1.5f), compact)
                }
            }
        }
    }
}

private fun MeasurementDataOrigin.color(): Color = when (this) {
    MeasurementDataOrigin.LivePoints -> AppColors.running
    MeasurementDataOrigin.RetainedRealPoints -> AppColors.warning
    MeasurementDataOrigin.LoadedPoints -> AppColors.info
    MeasurementDataOrigin.FramesOnly -> AppColors.warning
    MeasurementDataOrigin.Waiting -> AppColors.info
}

private fun Long?.shortTime(): String =
    this?.let { SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(it)) } ?: "--"

@Composable
private fun EvidenceCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    Column(
        modifier = modifier
            .background(color.copy(alpha = 0.08f), MaterialTheme.shapes.small)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            color = color,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WrenchSettingSyncBand(state: MeasurementState) {
    val result = state.lastResult
    val pulse = state.lastPulseResult
    // M2 结果帧的 28-29 是预设扭矩实际值，不是目标扭矩设定。
    val targetTorque = result?.settingTorqueNm ?: pulse?.targetTorqueNm
    val actualTorque = result?.actualTorqueNm ?: pulse?.actualTorqueNm
    val targetAngle = result?.targetAngleDeg
    val actualAngle = result?.actualAngleDeg ?: pulse?.actualAngleDeg
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SmallValue(
            "目标扭矩",
            targetTorque?.let { "${it.shortText()} Nm" } ?: "--",
            AppColors.info,
            Modifier.weight(1f),
        )
        SmallValue(
            "实际扭矩",
            actualTorque?.let { "${it.shortText()} Nm" } ?: "--",
            AppColors.signal,
            Modifier.weight(1f),
        )
        SmallValue(
            "目标角度",
            targetAngle?.let { "${it.shortText()}°" } ?: "--",
            AppColors.info,
            Modifier.weight(1f),
        )
        SmallValue(
            "实际角度",
            actualAngle?.let { "${it.shortText()}°" } ?: "--",
            AppColors.signal,
            Modifier.weight(1f),
        )
        SmallValue(
            "扳手结果",
            state.resultStatus,
            state.warningLevel.color(),
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReadoutBand(state: MeasurementState, compact: Boolean) {
    Surface(
        color = AppColors.panelInset,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AppColors.panelLine.copy(alpha = 0.42f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = if (compact) 4.dp else 8.dp,
                vertical = if (compact) 5.dp else 8.dp,
            ),
        ) {
            val narrow = maxWidth < 620.dp
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TorqueMetricTile(state, compact = true, modifier = Modifier.weight(1f))
                        AngleMetricTile(state, compact = true, modifier = Modifier.weight(1f))
                        ForceMetricTile(state, compact = true, modifier = Modifier.weight(1.2f))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PressureMetricTile(state, compact = true, modifier = Modifier.weight(1f))
                        DisplacementMetricTile(state, compact = true, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TorqueMetricTile(state, compact = compact, modifier = Modifier.weight(1f))
                    MetricSeparator(compact)
                    AngleMetricTile(state, compact = compact, modifier = Modifier.weight(1f))
                    MetricSeparator(compact)
                    ForceMetricTile(state, compact = compact, modifier = Modifier.weight(if (compact) 1f else 1.35f))
                    MetricSeparator(compact)
                    PressureMetricTile(state, compact = compact, modifier = Modifier.weight(1f))
                    MetricSeparator(compact)
                    DisplacementMetricTile(state, compact = compact, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TorqueMetricTile(state: MeasurementState, compact: Boolean, modifier: Modifier = Modifier) {
    MetricTile(
        label = "扭矩",
        value = state.torqueReadoutText(),
        unit = "Nm",
        color = AppColors.signal,
        compact = compact,
        modifier = modifier,
    )
}

@Composable
private fun AngleMetricTile(state: MeasurementState, compact: Boolean, modifier: Modifier = Modifier) {
    MetricTile(
        label = "转角",
        value = state.angleReadoutText(),
        unit = "°",
        color = AppColors.signal,
        compact = compact,
        modifier = modifier,
    )
}

@Composable
private fun ForceMetricTile(state: MeasurementState, compact: Boolean, modifier: Modifier = Modifier) {
    MetricTile(
        label = "顶升力",
        value = state.currentForceKn?.let { "%.2f".format(it) } ?: "--",
        unit = "kN",
        color = if (state.warningLevel == WarningLevel.NORMAL) AppColors.warning else state.warningLevel.color(),
        compact = compact,
        featured = true,
        modifier = modifier,
    )
}

@Composable
private fun PressureMetricTile(state: MeasurementState, compact: Boolean, modifier: Modifier = Modifier) {
    MetricTile(
        label = "压力",
        value = state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--",
        unit = "MPa",
        color = AppColors.signal,
        compact = compact,
        modifier = modifier,
    )
}

@Composable
private fun DisplacementMetricTile(state: MeasurementState, compact: Boolean, modifier: Modifier = Modifier) {
    MetricTile(
        label = "位移",
        value = state.currentDisplacementMm.takeIf { it > 0.0 }?.let { "%.2f".format(it) } ?: "--",
        unit = "mm",
        color = AppColors.signal,
        compact = compact,
        modifier = modifier,
    )
}

@Composable
private fun MetricSeparator(compact: Boolean) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(if (compact) 42.dp else 58.dp)
            .background(AppColors.grid.copy(alpha = 0.55f)),
    )
}

@Composable
private fun StatusLine(state: MeasurementState, compact: Boolean) {
    val trend = state.fieldTrend()
    Surface(
        color = state.warningLevel.color().copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, state.warningLevel.color().copy(alpha = 0.38f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 7.dp else 10.dp),
        ) {
            if (maxWidth < AppBreakpoints.compactWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "${state.warningLevel.labelText()} · ${trend.label}",
                        color = AppColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "螺栓 ${state.currentBoltNo} · 帧 ${state.receivedFrameCount} · ${state.warningMessage}",
                        color = AppColors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${state.warningLevel.labelText()} · ${state.resultStatus} · ${trend.label}",
                        color = AppColors.textPrimary,
                        fontSize = if (compact) 16.sp else 21.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.34f),
                    )
                    Text(
                        text = if (compact) {
                            "螺栓 ${state.currentBoltNo} · 帧 ${state.receivedFrameCount} · 参考点 ${state.effectivePointCount} · ${state.warningMessage}"
                        } else {
                            state.warningMessage
                        },
                        color = AppColors.textSecondary,
                        fontSize = if (compact) 12.sp else 15.sp,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.66f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    unit: String,
    color: Color,
    compact: Boolean,
    featured: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val valueSize = when {
        compact -> 28.sp
        featured -> 46.sp
        else -> 36.sp
    }
    val labelSize = when {
        compact -> 11.sp
        featured -> 14.sp
        else -> 13.sp
    }
    Column(
        modifier = modifier
            .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = if (compact) 2.dp else 4.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 4.dp),
    ) {
        Text(label, color = AppColors.textSecondary, fontSize = labelSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = color,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = unit,
                color = AppColors.textSecondary,
                fontSize = if (compact) 10.sp else 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 3.dp, bottom = if (compact) 3.dp else 5.dp),
            )
        }
    }
}

@Composable
private fun DataStrip(state: MeasurementState) {
    val trend = state.fieldTrend()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SmallValue("螺栓", "${state.currentBoltNo}", AppColors.teal, Modifier.weight(1f))
        SmallValue("趋势", trend.label, if (trend.isStable) AppColors.running else AppColors.warning, Modifier.weight(1f))
        SmallValue("电量", state.batteryPercent?.let { "$it%" } ?: "--", AppColors.amber, Modifier.weight(1f))
        SmallValue("接收帧", "${state.receivedFrameCount}", AppColors.teal, Modifier.weight(1f))
        SmallValue("最近消息", state.lastPacketFunction?.let { functionLabel(it) } ?: "--", state.warningLevel.color(), Modifier.weight(1f))
    }
}

@Composable
private fun SmallValue(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AppColors.panelInset, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = AppColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun WarningLevel.color(): Color = when (this) {
    WarningLevel.NORMAL -> AppColors.running
    WarningLevel.WATCH -> AppColors.warning
    WarningLevel.WARNING -> Color(0xFFFF8A45)
    WarningLevel.ERROR -> AppColors.danger
}

fun WarningLevel.labelText(): String = when (this) {
    WarningLevel.NORMAL -> "正常"
    WarningLevel.WATCH -> "观察"
    WarningLevel.WARNING -> "预警"
    WarningLevel.ERROR -> "危险"
}

fun MeasurementState.fieldTrend(): TrendSummary {
    val forceValues = recentPoints.mapNotNull { point ->
        point.forceKn?.takeIf { it.isFinite() }?.let { point.sourcePoint.angleDeg to it }
    }
    if (forceValues.size >= 4) return analyzeTrend(forceValues)

    val torqueValues = recentPoints.map { point ->
        point.sourcePoint.angleDeg to point.sourcePoint.torqueNm
    }
    return analyzeTrend(torqueValues)
}

fun Double.shortText(): String = if (this >= 10) {
    roundToInt().toString()
} else {
    "%.1f".format(this)
}
