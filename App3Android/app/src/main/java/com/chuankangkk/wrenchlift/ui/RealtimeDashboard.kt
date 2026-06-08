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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var selectedMetricIndex by rememberSaveable { mutableIntStateOf(ChartMetric.FORCE.ordinal) }
    val selectedMetric = ChartMetric.entries[selectedMetricIndex]
    SurfacePanel(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val dashboardWidth = maxWidth
            val compact = maxHeight < 470.dp || maxWidth < 760.dp
            val padding = if (compact) 10.dp else 16.dp
            val gap = if (compact) 8.dp else 12.dp
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                if (dashboardWidth < 590.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        ChartMetricSelector(
                            selected = selectedMetric,
                            onSelected = { selectedMetricIndex = it.ordinal },
                            compact = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ActionButton(
                            label = "放大曲线",
                            onClick = { onOpenChart(selectedMetric) },
                            modifier = Modifier.fillMaxWidth(),
                            color = AppColors.info,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ChartMetricSelector(
                            selected = selectedMetric,
                            onSelected = { selectedMetricIndex = it.ordinal },
                            compact = compact,
                            modifier = Modifier.weight(1f),
                        )
                        ActionButton(
                            label = "放大曲线",
                            onClick = { onOpenChart(selectedMetric) },
                            modifier = Modifier.width(if (compact) 112.dp else 132.dp),
                            color = AppColors.info,
                        )
                    }
                }
                EngineeringTrendChart(
                    points = state.recentPoints,
                    metric = selectedMetric,
                    onClick = { onOpenChart(selectedMetric) },
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

private fun MeasurementState.fieldTrend(): TrendSummary {
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
