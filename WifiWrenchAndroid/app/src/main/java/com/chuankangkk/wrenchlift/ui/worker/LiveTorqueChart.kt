package com.chuankangkk.wrenchlift.ui.worker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.ChartPoint
import com.chuankangkk.wrenchlift.measurement.TorqueAngleView
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** 「好看」的坐标刻度：步长取 1/2/2.5/5×10ⁿ。纯函数，便于单测。 */
object ChartAxis {
    data class Ticks(val min: Double, val max: Double, val step: Double, val values: List<Double>)

    fun niceTicks(rawMin: Double, rawMax: Double, target: Int = 5): Ticks {
        val low = if (rawMin.isFinite()) rawMin else 0.0
        var high = if (rawMax.isFinite()) rawMax else 1.0
        if (high - low < 1e-9) high = low + 1.0
        val rough = (high - low) / max(1, target)
        val magnitude = 10.0.pow(floor(log10(rough)))
        val residual = rough / magnitude
        val nice = when {
            residual <= 1.0 -> 1.0
            residual <= 2.0 -> 2.0
            residual <= 2.5 -> 2.5
            residual <= 5.0 -> 5.0
            else -> 10.0
        } * magnitude
        val start = floor(low / nice) * nice
        val end = ceil(high / nice) * nice
        val values = mutableListOf<Double>()
        var value = start
        var guard = 0
        while (value <= end + nice * 1e-6 && guard < 50) {
            values += if (abs(value) < nice * 1e-9) 0.0 else value
            value += nice
            guard += 1
        }
        return Ticks(start, end, nice, values)
    }

    fun label(value: Double, step: Double): String =
        if (step >= 1.0 || abs(step - Math.rint(step)) < 1e-9) "%.0f".format(Locale.US, value) else "%.1f".format(Locale.US, value)
}

/**
 * 主图：扭矩–转角。横轴按当前运行段转角跨度自适应；旧段淡色；段间不连线；拖动/点按看局部点值。
 */
@Composable
fun LiveTorqueChart(view: TorqueAngleView, modifier: Modifier = Modifier, emptyTitle: String, emptyHint: String) {
    val p = palette
    val measurer = rememberTextMeasurer()
    var cursor by remember { mutableStateOf<ChartPoint?>(null) }
    var cursorStamp by remember { mutableStateOf(0L) }
    LaunchedEffect(cursorStamp) {
        if (cursor != null) {
            delay(4_000)
            cursor = null
        }
    }
    val xTicks = ChartAxis.niceTicks(view.minAngle, view.maxAngle, 6)
    val yTicks = ChartAxis.niceTicks(view.minTorque, view.maxTorque * 1.08, 5)
    val tickStyle = TextStyle(fontSize = 11.sp, color = p.textSecondary, fontFeatureSettings = "tnum")
    val axisTitleStyle = TextStyle(fontSize = 12.sp, color = p.textSecondary)
    val cursorStyle = TextStyle(fontSize = 13.sp, color = p.textPrimary, fontFeatureSettings = "tnum")
    val current = view.segments.lastOrNull { it.isCurrent }?.points.orEmpty()
    val plotBounds = remember { floatArrayOf(0f, 1f) }

    Box(modifier.testTag("torque_chart").semantics { contentDescription = "扭矩–转角实时曲线" }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(view) {
                    detectTapGestures { offset -> cursor = nearest(current, offset.x, plotBounds, xTicks); cursorStamp = System.nanoTime() }
                }
                .pointerInput(view) {
                    detectDragGestures { change, _ -> cursor = nearest(current, change.position.x, plotBounds, xTicks); cursorStamp = System.nanoTime() }
                },
        ) {
            val yLabelWidth = yTicks.values.maxOf { measurer.measure(ChartAxis.label(it, yTicks.step), tickStyle).size.width }.toFloat()
            val tickHeight = measurer.measure("0", tickStyle).size.height.toFloat()
            val left = yLabelWidth + 8.dp.toPx()
            val top = tickHeight + 10.dp.toPx() // 顶部留给纵轴名
            val right = size.width - 8.dp.toPx()
            val bottom = size.height - tickHeight - 8.dp.toPx() - tickHeight - 4.dp.toPx()
            if (right <= left + 10f || bottom <= top + 10f) return@Canvas
            val plotW = right - left
            plotBounds[0] = left
            plotBounds[1] = right
            val plotH = bottom - top
            fun x(angle: Double) = (left + ((angle - xTicks.min) / (xTicks.max - xTicks.min)) * plotW).toFloat()
            fun y(torque: Double) = (bottom - ((torque - yTicks.min) / (yTicks.max - yTicks.min)) * plotH).toFloat()

            // 网格与刻度（发丝线）。没有数据时只画网格，不显示无意义的刻度值。
            yTicks.values.forEach { value ->
                val yy = y(value)
                drawLine(p.chartGrid, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
                if (!view.isEmpty) {
                    val label = measurer.measure(ChartAxis.label(value, yTicks.step), tickStyle)
                    drawText(label, topLeft = Offset(left - 6.dp.toPx() - label.size.width, yy - label.size.height / 2f))
                }
            }
            xTicks.values.forEach { value ->
                val xx = x(value)
                drawLine(p.chartGrid, Offset(xx, top), Offset(xx, bottom), strokeWidth = 1.dp.toPx())
                if (!view.isEmpty) {
                    val label = measurer.measure(ChartAxis.label(value, xTicks.step), tickStyle)
                    drawText(label, topLeft = Offset((xx - label.size.width / 2f).coerceIn(left - label.size.width / 2f, size.width - label.size.width.toFloat()), bottom + 4.dp.toPx()))
                }
            }
            drawLine(p.chartAxis, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
            drawLine(p.chartAxis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
            val yTitle = measurer.measure("扭矩 N·m", axisTitleStyle)
            drawText(yTitle, topLeft = Offset(left - yLabelWidth.coerceAtMost(left), 0f))
            val xTitle = measurer.measure("转角 °", axisTitleStyle)
            drawText(xTitle, topLeft = Offset(right - xTitle.size.width, size.height - xTitle.size.height))

            // 曲线：旧段淡色细线，当前段强调色；段与段之间不连线。
            view.segments.forEach { segment ->
                if (segment.points.isEmpty()) return@forEach
                val path = Path()
                segment.points.forEachIndexed { index, point ->
                    val px = x(point.angle).coerceIn(left, right)
                    val py = y(point.torque).coerceIn(top, bottom)
                    if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                drawPath(
                    path,
                    if (segment.isCurrent) p.chartLine else p.chartLineOld,
                    style = Stroke(width = if (segment.isCurrent) 2.5.dp.toPx() else 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            view.latest?.let { latest ->
                val c = Offset(x(latest.angle).coerceIn(left, right), y(latest.torque).coerceIn(top, bottom))
                drawCircle(p.chartLine.copy(alpha = 0.18f), radius = 9.dp.toPx(), center = c)
                drawCircle(p.chartLine, radius = 4.dp.toPx(), center = c)
            }

            cursor?.let { point ->
                val cx = x(point.angle).coerceIn(left, right)
                val cy = y(point.torque).coerceIn(top, bottom)
                drawLine(p.textSecondary, Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                drawCircle(p.panel, radius = 5.dp.toPx(), center = Offset(cx, cy))
                drawCircle(p.textPrimary, radius = 5.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                val text = measurer.measure("转角 ${"%.0f".format(Locale.US, point.angle)}° · 扭矩 ${"%.0f".format(Locale.US, point.torque)} N·m", cursorStyle)
                val pad = 6.dp.toPx()
                val boxW = text.size.width + pad * 2
                val boxH = text.size.height + pad * 2
                val bx = (cx + 10.dp.toPx()).let { if (it + boxW > right) cx - 10.dp.toPx() - boxW else it }.coerceAtLeast(left)
                val by = top + 4.dp.toPx()
                drawRoundRect(p.panel, Offset(bx, by), Size(boxW, boxH), CornerRadius(6.dp.toPx()))
                drawRoundRect(p.hairline, Offset(bx, by), Size(boxW, boxH), CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                drawText(text, topLeft = Offset(bx + pad, by + pad))
            }
        }
        if (view.isEmpty) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emptyTitle, style = WrenchType.bodyStrong.copy(fontSize = 16.sp), color = p.textPrimary)
                Text(emptyHint, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary)
            }
        }
    }
}

private fun nearest(points: List<ChartPoint>, touchX: Float, bounds: FloatArray, ticks: ChartAxis.Ticks): ChartPoint? {
    val width = bounds[1] - bounds[0]
    if (points.isEmpty() || width <= 0f) return null
    val fraction = ((touchX - bounds[0]) / width).coerceIn(0f, 1f)
    val angle = ticks.min + fraction * (ticks.max - ticks.min)
    return points.minByOrNull { abs(it.angle - angle) }
}
