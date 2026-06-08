package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
                showStatusChips = !narrow,
            )
            FocusMetricStrip(state = state, compact = short)
            ChartMetricSelector(
                selected = selectedMetric,
                onSelected = onMetricSelected,
                compact = short,
                modifier = Modifier.fillMaxWidth(),
            )
            FocusedCurveWall(
                state = state,
                primaryMetric = selectedMetric,
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
    modifier: Modifier = Modifier,
) {
    EngineeringTrendChart(
        points = state.recentPoints,
        metric = primaryMetric,
        emphasized = true,
        modifier = modifier.fillMaxWidth(),
    )
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
            modifier = Modifier.height(48.dp),
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
                modifier = Modifier.weight(1f).height(48.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FocusMetricStrip(state: MeasurementState, compact: Boolean) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.panel, RoundedCornerShape(6.dp))
            .padding(if (compact) 6.dp else 8.dp),
    ) {
        if (maxWidth < 560.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusStripValue("扭矩", "%.1f".format(state.currentTorqueNm), "Nm", compact, Modifier.weight(1f))
                    FocusStripValue("转角", "%.1f".format(state.currentAngleDeg), "°", compact, Modifier.weight(1f))
                    FocusStripValue("顶升力", state.currentForceKn?.let { "%.2f".format(it) } ?: "--", "kN", compact, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusStripValue("压力", state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--", "MPa", compact, Modifier.weight(1f))
                    FocusStripValue("位移", state.displacementText(), "mm", compact, Modifier.weight(1f))
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusStripValue("扭矩", "%.1f".format(state.currentTorqueNm), "Nm", compact, Modifier.weight(1f))
                FocusStripValue("转角", "%.1f".format(state.currentAngleDeg), "°", compact, Modifier.weight(1f))
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
            enabled = state.connectionState.isOnline,
        )
        ActionButton("保存记录", onSave, Modifier.weight(1f), AppColors.warning)
        ActionButton(
            "确认平衡点",
            onConfirmEffectivePoint,
            Modifier.weight(1f),
            AppColors.running,
            enabled = state.recentPoints.isNotEmpty(),
        )
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
