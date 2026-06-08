package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.MeasuredPoint
import com.chuankangkk.wrenchlift.measurement.WarningLevel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ChartMetric(
    val label: String,
    val unit: String,
) {
    TORQUE("扭矩-转角", "Nm"),
    FORCE("顶升力-转角", "kN"),
    PRESSURE("浮置板压力-转角", "MPa"),
}

data class TrendSummary(
    val label: String,
    val detail: String,
    val isStable: Boolean,
)

@Composable
fun EngineeringTrendChart(
    points: List<MeasuredPoint>,
    metric: ChartMetric,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val values = points.mapNotNull { point ->
        metric.valueOf(point)?.takeIf { it.isFinite() }?.let { value ->
            point.sourcePoint.angleDeg to value
        }
    }
    val latestWarning = points.lastOrNull()?.warningLevel ?: WarningLevel.NORMAL
    val color = when {
        metric == ChartMetric.FORCE && latestWarning != WarningLevel.NORMAL -> latestWarning.color()
        metric == ChartMetric.FORCE -> AppColors.warning
        metric == ChartMetric.PRESSURE -> AppColors.info
        else -> AppColors.signal
    }
    val trend = analyzeTrend(values)
    val scale = values.chartScale()
    val sampleText = "采样 ${values.size} 点"
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(onClick = onClick)
    }

    BoxWithConstraints(
        modifier = modifier
            .then(interactionModifier)
            .background(AppColors.panelInset, RoundedCornerShape(6.dp))
            .padding(horizontal = if (emphasized) 16.dp else 10.dp, vertical = if (emphasized) 12.dp else 8.dp),
    ) {
        val compactHeight = maxHeight < 170.dp
        val titleSize = when {
            emphasized && maxWidth > 760.dp -> 23.sp
            emphasized -> 20.sp
            else -> 15.sp
        }
        val valueSize = when {
            emphasized && maxWidth > 760.dp -> 34.sp
            emphasized -> 28.sp
            else -> 18.sp
        }
        val metaSize = if (emphasized) 13.sp else 11.sp
        val axisSize = if (emphasized) 12.sp else 10.sp

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 48f || size.height <= 48f) return@Canvas

            val headerReserved = when {
                compactHeight -> size.height * 0.38f
                emphasized -> min(70.dp.toPx(), size.height * 0.25f)
                else -> min(48.dp.toPx(), size.height * 0.30f)
            }
            val footerReserved = if (compactHeight) 15.dp.toPx() else 26.dp.toPx()
            val left = if (emphasized) 50.dp.toPx() else 36.dp.toPx()
            val right = (size.width - 12.dp.toPx()).coerceAtLeast(left + 1f)
            val top = headerReserved.coerceAtLeast(12.dp.toPx())
            val bottom = (size.height - footerReserved).coerceAtLeast(top + 1f)
            val plotWidth = (right - left).coerceAtLeast(1f)
            val plotHeight = (bottom - top).coerceAtLeast(1f)

            repeat(5) { row ->
                val y = top + plotHeight * row / 4f
                drawLine(
                    AppColors.grid.copy(alpha = if (row == 4) 0.9f else 0.58f),
                    Offset(left, y),
                    Offset(right, y),
                    strokeWidth = if (row == 4) 1.4.dp.toPx() else 1.dp.toPx(),
                )
            }
            repeat(7) { column ->
                val x = left + plotWidth * column / 6f
                drawLine(
                    AppColors.grid.copy(alpha = 0.48f),
                    Offset(x, top),
                    Offset(x, bottom),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            drawLine(
                AppColors.axis.copy(alpha = 0.82f),
                Offset(left, top),
                Offset(left, bottom),
                strokeWidth = 1.4.dp.toPx(),
            )
            drawLine(
                AppColors.axis.copy(alpha = 0.82f),
                Offset(left, bottom),
                Offset(right, bottom),
                strokeWidth = 1.4.dp.toPx(),
            )

            if (values.size >= 2 && scale != null) {
                fun xFor(angle: Double): Float =
                    (left + ((angle - scale.minAngle) / (scale.maxAngle - scale.minAngle)).toFloat() * plotWidth)
                        .takeIf { it.isFinite() }
                        ?.coerceIn(left, right)
                        ?: left

                fun yFor(value: Double): Float =
                    (bottom - ((value - scale.paddedMin) / scale.valueRange).toFloat() * plotHeight)
                        .takeIf { it.isFinite() }
                        ?.coerceIn(top, bottom)
                        ?: bottom

                val linePath = Path()
                values.forEachIndexed { index, point ->
                    val x = xFor(point.first)
                    val y = yFor(point.second)
                    if (index == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }

                val areaPath = Path().apply {
                    moveTo(xFor(values.first().first), bottom)
                    values.forEach { point -> lineTo(xFor(point.first), yFor(point.second)) }
                    lineTo(xFor(values.last().first), bottom)
                    close()
                }
                drawPath(areaPath, color.copy(alpha = 0.11f))
                drawPath(
                    linePath,
                    color,
                    style = Stroke(if (emphasized) 4.dp.toPx() else 3.dp.toPx()),
                )

                val latest = values.last()
                val latestCenter = Offset(xFor(latest.first), yFor(latest.second))
                drawCircle(color.copy(alpha = 0.18f), radius = 11.dp.toPx(), center = latestCenter)
                drawCircle(color, radius = if (emphasized) 6.dp.toPx() else 4.5.dp.toPx(), center = latestCenter)

                if (trend.isStable) {
                    drawLine(
                        color.copy(alpha = 0.78f),
                        Offset(left, yFor(latest.second)),
                        Offset(right, yFor(latest.second)),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = metric.label,
                    color = AppColors.textPrimary,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compactHeight || emphasized) {
                    Text(
                        text = "${trend.label} · ${trend.detail} · $sampleText",
                        color = if (trend.isStable) AppColors.running else AppColors.textSecondary,
                        fontSize = metaSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = values.lastOrNull()?.second.formatMetric(metric),
                color = color,
                fontSize = valueSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }

        if (scale != null && !compactHeight) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (emphasized) 76.dp else 50.dp,
                        bottom = if (emphasized) 30.dp else 24.dp,
                    ),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = scale.paddedMax.formatAxis(metric),
                    color = AppColors.textSecondary,
                    fontSize = axisSize,
                    maxLines = 1,
                )
                Text(
                    text = scale.paddedMin.formatAxis(metric),
                    color = AppColors.textSecondary,
                    fontSize = axisSize,
                    maxLines = 1,
                )
            }
        }

        if (values.size < 2) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = metric.emptyText(),
                    color = AppColors.textPrimary,
                    fontSize = if (emphasized) 16.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (emphasized) {
                    Text(
                        text = "连接扳手并开始动作后自动绘制",
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                values.firstOrNull()?.first?.let { "%.0f°".format(it) } ?: "--°",
                color = AppColors.textSecondary,
                fontSize = if (emphasized) 13.sp else 11.sp,
            )
            Text(
                text = "转角 ° · ${metric.unit}",
                color = AppColors.textSecondary,
                fontSize = if (emphasized) 13.sp else 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                values.lastOrNull()?.first?.let { "%.0f°".format(it) } ?: "--°",
                color = AppColors.textSecondary,
                fontSize = if (emphasized) 13.sp else 11.sp,
            )
        }
    }
}

private data class ChartScale(
    val minAngle: Double,
    val maxAngle: Double,
    val paddedMin: Double,
    val paddedMax: Double,
) {
    val valueRange: Double = (paddedMax - paddedMin).coerceAtLeast(1e-6)
}

private fun List<Pair<Double, Double>>.chartScale(): ChartScale? {
    if (size < 2) return null
    val minAngle = minOf { it.first }
    val maxAngle = max(maxOf { it.first }, minAngle + 1.0)
    val minValue = minOf { it.second }
    val maxValue = maxOf { it.second }
    val rawSpan = maxValue - minValue
    val valueSpan = if (abs(rawSpan) > 1e-9) rawSpan else max(abs(maxValue) * 0.1, 0.1)
    return ChartScale(
        minAngle = minAngle,
        maxAngle = maxAngle,
        paddedMin = min(0.0, minValue - valueSpan * 0.1),
        paddedMax = maxValue + valueSpan * 0.14,
    )
}

fun analyzeTrend(values: List<Pair<Double, Double>>): TrendSummary {
    if (values.size < 4) {
        return TrendSummary("等待采集", "连接扳手后绘制曲线", false)
    }
    val recent = values.takeLast(10)
    val firstValue = recent.first().second
    val latestValue = recent.last().second
    val valueRange = recent.maxOf { it.second } - recent.minOf { it.second }
    val relativeRange = valueRange / max(abs(latestValue), 1.0)
    val totalRise = latestValue - values.first().second
    val recentRise = latestValue - firstValue
    val stable = values.size >= 12 && relativeRange <= 0.035 && totalRise > 0.0
    val slowing = values.size >= 8 &&
        recentRise >= 0.0 &&
        recentRise <= max(abs(totalRise) * 0.16, 0.4)

    return when {
        stable -> TrendSummary("平衡平台", "变化已趋稳，可等待现场确认", true)
        slowing -> TrendSummary("接近平衡", "增速放缓，继续观察", false)
        totalRise > 0.0 -> TrendSummary("持续上升", "载荷正在建立", false)
        else -> TrendSummary("趋势核查", "数据未形成稳定上升", false)
    }
}

private fun ChartMetric.valueOf(point: MeasuredPoint): Double? = when (this) {
    ChartMetric.TORQUE -> point.sourcePoint.torqueNm
    ChartMetric.FORCE -> point.forceKn
    ChartMetric.PRESSURE -> point.pressureMpa
}

private fun ChartMetric.emptyText(): String = when (this) {
    ChartMetric.TORQUE -> "等待扳手过程数据"
    ChartMetric.FORCE -> "未标定：暂不输出正式顶升力"
    ChartMetric.PRESSURE -> "未设置面积：暂不输出正式压力"
}

private fun Double?.formatMetric(metric: ChartMetric): String {
    if (this == null) return "-- ${metric.unit}"
    val value = when (metric) {
        ChartMetric.PRESSURE -> "%.3f".format(this)
        else -> "%.2f".format(this)
    }
    return "$value ${metric.unit}"
}

private fun Double.formatAxis(metric: ChartMetric): String = when (metric) {
    ChartMetric.PRESSURE -> "%.3f".format(this)
    ChartMetric.TORQUE -> if (abs(this) >= 100.0) "%.0f".format(this) else "%.1f".format(this)
    ChartMetric.FORCE -> if (abs(this) >= 100.0) "%.0f".format(this) else "%.1f".format(this)
}
