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

        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 48f || size.height <= 48f) return@Canvas

            val headerReserved = when {
                compactHeight -> size.height * 0.35f
                emphasized -> min(54.dp.toPx(), size.height * 0.22f)
                else -> min(34.dp.toPx(), size.height * 0.28f)
            }
            val footerReserved = if (compactHeight) 11.dp.toPx() else 20.dp.toPx()
            val left = if (emphasized) 34.dp.toPx() else 20.dp.toPx()
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

            if (values.size >= 2) {
                val minAngle = values.minOf { it.first }
                val maxAngle = max(values.maxOf { it.first }, minAngle + 1.0)
                val minValue = values.minOf { it.second }
                val maxValue = values.maxOf { it.second }
                val rawSpan = maxValue - minValue
                val valueSpan = if (abs(rawSpan) > 1e-9) rawSpan else max(abs(maxValue) * 0.1, 0.1)
                val paddedMin = min(0.0, minValue - valueSpan * 0.1)
                val paddedMax = maxValue + valueSpan * 0.14
                val valueRange = (paddedMax - paddedMin).coerceAtLeast(1e-6)

                fun xFor(angle: Double): Float =
                    (left + ((angle - minAngle) / (maxAngle - minAngle)).toFloat() * plotWidth)
                        .takeIf { it.isFinite() }
                        ?.coerceIn(left, right)
                        ?: left

                fun yFor(value: Double): Float =
                    (bottom - ((value - paddedMin) / valueRange).toFloat() * plotHeight)
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
                        text = "${trend.label} · ${trend.detail}",
                        color = if (trend.isStable) AppColors.running else AppColors.textSecondary,
                        fontSize = if (emphasized) 13.sp else 11.sp,
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

        if (values.size < 2) {
            Text(
                text = metric.emptyText(),
                color = AppColors.textSecondary,
                fontSize = if (emphasized) 16.sp else 13.sp,
                modifier = Modifier.align(Alignment.Center),
            )
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
                values.lastOrNull()?.first?.let { "%.0f°".format(it) } ?: "--°",
                color = AppColors.textSecondary,
                fontSize = if (emphasized) 13.sp else 11.sp,
            )
        }
    }
}

fun analyzeTrend(values: List<Pair<Double, Double>>): TrendSummary {
    if (values.size < 4) {
        return TrendSummary("等待趋势", "采样点不足", false)
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
    ChartMetric.TORQUE -> "等待扭矩与转角数据"
    ChartMetric.FORCE -> "未标定时不输出正式顶升力"
    ChartMetric.PRESSURE -> "未设置有效面积时不输出正式压力"
}

private fun Double?.formatMetric(metric: ChartMetric): String {
    if (this == null) return "-- ${metric.unit}"
    val value = when (metric) {
        ChartMetric.PRESSURE -> "%.3f".format(this)
        else -> "%.2f".format(this)
    }
    return "$value ${metric.unit}"
}
