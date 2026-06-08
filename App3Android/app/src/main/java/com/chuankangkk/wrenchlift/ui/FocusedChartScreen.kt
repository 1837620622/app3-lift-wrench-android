package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.WarningLevel

@Composable
fun FocusedChartScreen(
    state: MeasurementState,
    selectedMetric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
    onBack: () -> Unit,
    onStopWrench: () -> Unit,
    onSave: () -> Unit,
    onConfirmEffectivePoint: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(AppColors.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        val narrow = maxWidth < 760.dp
        val short = maxHeight < 430.dp
        val gap = if (short) 8.dp else 12.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (short) 12.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            FocusHeader(
                state = state,
                selectedMetric = selectedMetric,
                onBack = onBack,
                compact = short,
            )
            FocusMetricStrip(state = state)
            if (!short) {
                ChartMetricSelector(
                    selected = selectedMetric,
                    onSelected = onMetricSelected,
                    compact = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FocusedCurveWall(
                state = state,
                primaryMetric = selectedMetric,
                onMetricSelected = onMetricSelected,
                compact = short,
                narrow = narrow,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            FocusActionBar(
                state = state,
                onStopWrench = onStopWrench,
                onSave = onSave,
                onConfirmEffectivePoint = onConfirmEffectivePoint,
                compact = short,
            )
        }
    }
}

@Composable
private fun FocusedCurveWall(
    state: MeasurementState,
    primaryMetric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
    compact: Boolean,
    narrow: Boolean,
    modifier: Modifier = Modifier,
) {
    val gap = if (compact) 8.dp else 12.dp
    val secondaryMetrics = ChartMetric.entries.filter { it != primaryMetric }

    if (narrow) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            EngineeringTrendChart(
                points = state.recentPoints,
                metric = primaryMetric,
                emphasized = true,
                modifier = Modifier.weight(1.35f).fillMaxWidth(),
            )
            secondaryMetrics.forEach { metric ->
                EngineeringTrendChart(
                    points = state.recentPoints,
                    metric = metric,
                    emphasized = false,
                    onClick = { onMetricSelected(metric) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            EngineeringTrendChart(
                points = state.recentPoints,
                metric = primaryMetric,
                emphasized = true,
                modifier = Modifier.weight(1.75f).fillMaxHeight(),
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                secondaryMetrics.forEach { metric ->
                    EngineeringTrendChart(
                        points = state.recentPoints,
                        metric = metric,
                        emphasized = false,
                        onClick = { onMetricSelected(metric) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                if (!compact) {
                    FocusCurveStatusCard(
                        state = state,
                        modifier = Modifier.weight(0.82f).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusCurveStatusCard(state: MeasurementState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(AppColors.panel, RoundedCornerShape(6.dp))
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "现场判断",
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
            )
            Text(
                "${state.warningLevel.labelText()} · ${state.fieldTrend().label}",
                color = state.warningLevel.color(),
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                state.warningMessage,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FocusHeader(
    state: MeasurementState,
    selectedMetric: ChartMetric,
    onBack: () -> Unit,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.height(if (compact) 42.dp else 48.dp),
            shape = RoundedCornerShape(6.dp),
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

@Composable
fun ChartMetricSelector(
    selected: ChartMetric,
    onSelected: (ChartMetric) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ChartMetric.entries.forEach { metric ->
            val checked = metric == selected
            Button(
                onClick = { onSelected(metric) },
                modifier = Modifier.weight(1f).height(if (compact) 38.dp else 44.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (checked) AppColors.info else AppColors.panelRaised,
                    contentColor = AppColors.textPrimary,
                ),
            ) {
                Text(
                    text = metric.label.substringBefore("-"),
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun FocusMetricRail(
    state: MeasurementState,
    selectedMetric: ChartMetric,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(AppColors.panel, RoundedCornerShape(6.dp))
            .padding(if (compact) 12.dp else 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        FocusPrimaryMetric(state, selectedMetric, compact)
        FocusRailValue("扭矩", "%.1f".format(state.currentTorqueNm), "Nm", selectedMetric == ChartMetric.TORQUE, compact)
        FocusRailValue("转角", "%.1f".format(state.currentAngleDeg), "°", false, compact)
        FocusRailValue("顶升力", state.currentForceKn?.let { "%.2f".format(it) } ?: "--", "kN", selectedMetric == ChartMetric.FORCE, compact)
        FocusRailValue("压力", state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--", "MPa", selectedMetric == ChartMetric.PRESSURE, compact)
        FocusRailValue("位移", state.displacementText(), "mm", false, compact)
    }
}

@Composable
private fun FocusPrimaryMetric(state: MeasurementState, selectedMetric: ChartMetric, compact: Boolean) {
    val value = when (selectedMetric) {
        ChartMetric.TORQUE -> "%.1f".format(state.currentTorqueNm)
        ChartMetric.FORCE -> state.currentForceKn?.let { "%.2f".format(it) } ?: "--"
        ChartMetric.PRESSURE -> state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--"
    }
    val color = when {
        selectedMetric == ChartMetric.FORCE && state.warningLevel != WarningLevel.NORMAL -> state.warningLevel.color()
        selectedMetric == ChartMetric.FORCE -> AppColors.warning
        selectedMetric == ChartMetric.PRESSURE -> AppColors.info
        else -> AppColors.signal
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("当前值", color = AppColors.textSecondary, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = color,
                fontSize = if (compact) 38.sp else 52.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                selectedMetric.unit,
                color = AppColors.textSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(start = 5.dp, bottom = 7.dp),
            )
        }
    }
}

@Composable
private fun FocusRailValue(
    label: String,
    value: String,
    unit: String,
    highlighted: Boolean,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, color = AppColors.textSecondary, fontSize = if (compact) 12.sp else 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                color = if (highlighted) AppColors.textPrimary else AppColors.signal,
                fontSize = if (compact) 19.sp else 23.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                unit,
                color = AppColors.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 3.dp, bottom = 2.dp),
            )
        }
    }
}

@Composable
private fun FocusMetricStrip(state: MeasurementState) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().background(AppColors.panel, RoundedCornerShape(6.dp)).padding(8.dp),
    ) {
        if (maxWidth < 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusStripValue("扭矩", "%.1f".format(state.currentTorqueNm), "Nm", Modifier.weight(1f))
                    FocusStripValue("转角", "%.1f".format(state.currentAngleDeg), "°", Modifier.weight(1f))
                    FocusStripValue("顶升力", state.currentForceKn?.let { "%.2f".format(it) } ?: "--", "kN", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusStripValue("压力", state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--", "MPa", Modifier.weight(1f))
                    FocusStripValue("位移", state.displacementText(), "mm", Modifier.weight(1f))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusStripValue("扭矩", "%.1f".format(state.currentTorqueNm), "Nm", Modifier.weight(1f))
                FocusStripValue("转角", "%.1f".format(state.currentAngleDeg), "°", Modifier.weight(1f))
                FocusStripValue("顶升力", state.currentForceKn?.let { "%.2f".format(it) } ?: "--", "kN", Modifier.weight(1f))
                FocusStripValue("压力", state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--", "MPa", Modifier.weight(1f))
                FocusStripValue("位移", state.displacementText(), "mm", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FocusStripValue(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp)
        Text(
            "$value $unit",
            color = AppColors.textPrimary,
            fontSize = 16.sp,
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
    onConfirmEffectivePoint: () -> Unit,
    compact: Boolean,
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
        )
        ActionButton("保存记录", onSave, Modifier.weight(1f), AppColors.warning)
        ActionButton("确认平衡点", onConfirmEffectivePoint, Modifier.weight(1f), AppColors.running)
        if (!compact) {
            Text(
                text = state.warningMessage,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.5f).align(Alignment.CenterVertically),
            )
        }
    }
}
