package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.WarningLevel
import kotlin.math.roundToInt

@Composable
fun RealtimeDashboard(
    state: MeasurementState,
    onOpenChart: (ChartMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    SurfacePanel(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 470.dp || maxWidth < 760.dp
            val padding = if (compact) 10.dp else 16.dp
            val gap = if (compact) 8.dp else 12.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                WorkbenchHero(
                    state = state,
                    onOpenChart = onOpenChart,
                    compact = compact,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                ReadoutBand(state = state, compact = compact)
                if (!compact) {
                    WrenchSettingSyncBand(state)
                }
                if (!compact) {
                    DataStrip(state)
                }
                StatusLine(state = state, compact = compact)
            }
        }
    }
}

@Composable
private fun WorkbenchHero(
    state: MeasurementState,
    onOpenChart: (ChartMetric) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = AppColors.panelInset,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.grid.copy(alpha = 0.48f)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(if (compact) 12.dp else 18.dp)) {
            val narrow = maxWidth < 620.dp
            val trend = state.fieldTrend()
            val forceText = state.currentForceKn?.let { "%.2f".format(it) } ?: "--"
            val forceCaption = if (state.currentForceKn == null) {
                "未标定，不输出正式顶升力"
            } else {
                "${state.warningLevel.labelText()} · ${trend.label}"
            }
            if (narrow) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    WorkbenchPrimaryReadout(
                        value = forceText,
                        caption = forceCaption,
                        compact = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    WorkbenchCurveActions(
                        state = state,
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
                        value = forceText,
                        caption = forceCaption,
                        compact = compact,
                        modifier = Modifier.weight(1.25f).fillMaxWidth(),
                    )
                    WorkbenchCurveActions(
                        state = state,
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
    value: String,
    caption: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
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
            fontSize = if (compact) 24.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = AppColors.warning,
                fontSize = if (compact) 54.sp else 80.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
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
    }
}

@Composable
private fun WorkbenchCurveActions(
    state: MeasurementState,
    onOpenChart: (ChartMetric) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        ActionButton(
            label = "进入曲线大屏",
            onClick = { onOpenChart(ChartMetric.FORCE) },
            modifier = Modifier.fillMaxWidth(),
            color = AppColors.info,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                label = "扭矩曲线",
                onClick = { onOpenChart(ChartMetric.TORQUE) },
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                label = "压力曲线",
                onClick = { onOpenChart(ChartMetric.PRESSURE) },
                modifier = Modifier.weight(1f),
            )
        }
        if (!compact) {
            Text(
                "主界面只做现场读数；全部曲线在独立页面查看。",
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        WorkbenchHealthRow(state = state, compact = compact)
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
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(7.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WrenchSettingSyncBand(state: MeasurementState) {
    val result = state.lastResult
    val pulse = state.lastPulseResult
    val targetTorque = result?.targetTorqueNm ?: pulse?.targetTorqueNm
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
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.grid.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 5.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricTile(
                label = "扭矩",
                value = "%.1f".format(state.currentTorqueNm),
                unit = "Nm",
                color = AppColors.signal,
                compact = compact,
                modifier = Modifier.weight(1f),
            )
            MetricSeparator(compact)
            MetricTile(
                label = "转角",
                value = "%.1f".format(state.currentAngleDeg),
                unit = "°",
                color = AppColors.signal,
                compact = compact,
                modifier = Modifier.weight(1f),
            )
            MetricSeparator(compact)
            MetricTile(
                label = "顶升力",
                value = state.currentForceKn?.let { "%.2f".format(it) } ?: "--",
                unit = "kN",
                color = if (state.warningLevel == WarningLevel.NORMAL) AppColors.warning else state.warningLevel.color(),
                compact = compact,
                featured = true,
                modifier = Modifier.weight(if (compact) 1f else 1.35f),
            )
            MetricSeparator(compact)
            MetricTile(
                label = "压力",
                value = state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--",
                unit = "MPa",
                color = AppColors.signal,
                compact = compact,
                modifier = Modifier.weight(1f),
            )
            MetricSeparator(compact)
            MetricTile(
                label = "位移",
                value = state.currentDisplacementMm.takeIf { it > 0.0 }?.let { "%.2f".format(it) } ?: "--",
                unit = "mm",
                color = AppColors.signal,
                compact = compact,
                modifier = Modifier.weight(1f),
            )
        }
    }
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
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, state.warningLevel.color().copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 7.dp else 10.dp),
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
                    "螺栓 ${state.currentBoltNo} · 帧 ${state.receivedFrameCount} · 平衡点 ${state.effectivePointCount} · ${state.warningMessage}"
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
                modifier = Modifier.weight(1f, fill = false),
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
            .background(AppColors.panelInset, RoundedCornerShape(8.dp))
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
