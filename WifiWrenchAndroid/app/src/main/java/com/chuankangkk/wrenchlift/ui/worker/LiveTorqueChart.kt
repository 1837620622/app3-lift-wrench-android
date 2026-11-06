package com.chuankangkk.wrenchlift.ui.worker

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.ChartPoint
import com.chuankangkk.wrenchlift.measurement.TorqueAngleView
import com.chuankangkk.wrenchlift.measurement.WorkerText
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

/** 主图三种关系：横轴「转角」或「序号」（与导出「过程数据」序号列一致），纵轴「扭矩」或「转角」。 */
enum class ChartMode(val tab: String, val xTitle: String, val yTitle: String) {
    TORQUE_ANGLE("扭矩–转角", "转角 °", "扭矩 N·m"),
    TORQUE_SEQ("扭矩–序号", "序号", "扭矩 N·m"),
    ANGLE_SEQ("转角–序号", "序号", "转角 °"),
    ;

    val yIsTorque: Boolean get() = this != ANGLE_SEQ

    fun x(point: ChartPoint): Double = if (this == TORQUE_ANGLE) point.angle else point.seq.toDouble()

    fun y(point: ChartPoint): Double = if (yIsTorque) point.torque else point.angle

    /** 末点标签 / 读点文字里的纵轴值。 */
    fun yText(value: Double): String = WorkerText.readout(value, true) + if (yIsTorque) " N·m" else "°"
}

/**
 * 主图（默认扭矩–转角）。
 * - 扭矩–转角：横轴按当前运行段转角跨度自适应；
 * - 扭矩–序号 / 转角–序号：横轴为记录内序号，与 Excel「过程数据」逐行对应；
 * - 旧段淡色细线、段间不连线，有上一段时在横轴名一行右侧标图例；
 * - 当前段下方渐变填充、末点数值标签；纵轴为扭矩且有结果帧目标值时画目标虚线；
 * - 动效克制：坐标范围变化时 260ms 平滑缩放；切换关系图淡入淡出；实时更新时末点光圈缓慢呼吸；
 * - 点按/拖动看局部点值（含序号），4 秒后自动收起。
 */
@Composable
fun LiveTorqueChart(
    view: TorqueAngleView,
    modifier: Modifier = Modifier,
    emptyTitle: String,
    emptyHint: String,
    targetTorqueNm: Double? = null,
    mode: ChartMode = ChartMode.TORQUE_ANGLE,
    live: Boolean = false,
) {
    Box(modifier.testTag("torque_chart").semantics { contentDescription = "${mode.tab}实时曲线" }) {
        Crossfade(targetState = mode, animationSpec = tween(180), label = "chart_mode", modifier = Modifier.fillMaxSize()) { shown ->
            ChartCanvas(view, shown, targetTorqueNm, live)
        }
        if (view.isEmpty) {
            val p = palette
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emptyTitle, style = WrenchType.bodyStrong.copy(fontSize = 17.sp), color = p.textPrimary)
                Text(emptyHint, style = WrenchType.labelStyle, color = p.textSecondary)
            }
        }
    }
}

@Composable
private fun ChartCanvas(view: TorqueAngleView, mode: ChartMode, targetTorqueNm: Double?, live: Boolean) {
    val p = palette
    val measurer = rememberTextMeasurer()
    var cursor by remember { mutableStateOf<ChartPoint?>(null) }
    var cursorStamp by remember { mutableLongStateOf(0L) }
    LaunchedEffect(cursorStamp) {
        if (cursor != null) {
            delay(4_000)
            cursor = null
        }
    }
    val visible = remember(view) { view.segments.flatMap { it.points } }
    val xTicks = remember(view, mode) {
        if (mode == ChartMode.TORQUE_ANGLE || visible.isEmpty()) {
            ChartAxis.niceTicks(view.minAngle, view.maxAngle, 6)
        } else {
            val first = visible.minOf { it.seq }.toDouble()
            val last = visible.maxOf { it.seq }.toDouble()
            ChartAxis.niceTicks(first, maxOf(last, first + MIN_SEQ_SPAN), 6)
        }
    }
    val target = targetTorqueNm.takeIf { mode.yIsTorque }
    val yTicks = remember(view, mode, target) {
        if (mode.yIsTorque) {
            ChartAxis.niceTicks(view.minTorque, max(view.maxTorque, target ?: 0.0) * HEADROOM, 5)
        } else {
            // 转角为协议无符号量：纵轴从 0 起，与导出图一致。
            val low = minOf(0.0, visible.minOfOrNull { it.angle } ?: 0.0)
            val high = max(visible.maxOfOrNull { it.angle } ?: 1.0, low + MIN_ANGLE_SPAN) * HEADROOM
            ChartAxis.niceTicks(low, high, 5)
        }
    }
    // 坐标范围平滑过渡：数据把轴撑大时网格与曲线一起缩放，而不是跳变。
    val xMin = rememberAxisValue(xTicks.min)
    val xMax = rememberAxisValue(xTicks.max)
    val yMin = rememberAxisValue(yTicks.min)
    val yMax = rememberAxisValue(yTicks.max)

    val lineColor = if (mode.yIsTorque) p.chartLine else p.chartAngle
    val tickStyle = TextStyle(fontSize = 12.sp, color = p.textSecondary, fontFeatureSettings = "tnum")
    val axisTitleStyle = TextStyle(fontSize = 13.sp, color = p.textSecondary, fontWeight = FontWeight.Medium)
    val legendStyle = TextStyle(fontSize = 12.sp, color = p.textSecondary)
    val tagStyle = TextStyle(fontSize = 13.sp, color = p.onAccent, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val targetStyle = TextStyle(fontSize = 12.sp, color = p.warn, fontFeatureSettings = "tnum")
    val cursorStyle = TextStyle(fontSize = 13.sp, color = p.textPrimary, fontFeatureSettings = "tnum")
    val current = view.segments.lastOrNull { it.isCurrent }?.points.orEmpty()
    val hasOld = view.segments.any { !it.isCurrent && it.points.isNotEmpty() }
    val plotBounds = remember { floatArrayOf(0f, 1f) }
    // 末点在画布上的位置：主画布写入，呼吸光圈层读取（光圈逐帧重画时不必重画整条曲线）。
    val latestAt = remember { floatArrayOf(Float.NaN, Float.NaN) }
    // 本帧画出的各段折线（屏幕坐标），给末点标签避让用。
    val screenLines = remember { ArrayList<FloatArray>() }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(view, mode) {
                    detectTapGestures { offset -> cursor = nearest(current, mode, offset.x, plotBounds, xTicks); cursorStamp = System.nanoTime() }
                }
                .pointerInput(view, mode) {
                    detectDragGestures { change, _ -> cursor = nearest(current, mode, change.position.x, plotBounds, xTicks); cursorStamp = System.nanoTime() }
                },
        ) {
            val x0 = xMin.value.toDouble()
            val x1 = xMax.value.toDouble().let { if (it - x0 < 1e-9) x0 + 1.0 else it }
            val y0 = yMin.value.toDouble()
            val y1 = yMax.value.toDouble().let { if (it - y0 < 1e-9) y0 + 1.0 else it }
            val yLabelWidth = yTicks.values.maxOf { measurer.measure(ChartAxis.label(it, yTicks.step), tickStyle).size.width }.toFloat()
            val tickHeight = measurer.measure("0", tickStyle).size.height.toFloat()
            val yTitle = measurer.measure(mode.yTitle, axisTitleStyle)
            val xTitle = measurer.measure(mode.xTitle, axisTitleStyle)
            // 最右刻度值居中会探出绘图区：右边留出半个末刻度宽，保证不被面板裁掉。
            val lastXLabel = measurer.measure(ChartAxis.label(xTicks.values.last(), xTicks.step), tickStyle).size.width.toFloat()
            // 纵轴名竖排在最左（与产品方渲染一致），其右是刻度值，再右是绘图区。
            val left = yTitle.size.height + 6.dp.toPx() + yLabelWidth + 8.dp.toPx()
            val top = tickHeight / 2f + 6.dp.toPx()
            val right = size.width - max(10.dp.toPx(), lastXLabel / 2f + 2.dp.toPx())
            val bottom = size.height - tickHeight - 6.dp.toPx() - xTitle.size.height - 4.dp.toPx()
            if (right <= left + 10f || bottom <= top + 10f) return@Canvas
            val plotW = right - left
            val plotH = bottom - top
            plotBounds[0] = left
            plotBounds[1] = right
            fun px(value: Double) = (left + ((value - x0) / (x1 - x0)) * plotW).toFloat()
            fun py(value: Double) = (bottom - ((value - y0) / (y1 - y0)) * plotH).toFloat()
            val epsX = (x1 - x0) * 1e-6
            val epsY = (y1 - y0) * 1e-6

            // 网格与刻度（发丝线）。没有数据时只画网格，不显示无意义的刻度值；缩放过渡中只画落在范围内的刻度。
            yTicks.values.filter { it >= y0 - epsY && it <= y1 + epsY }.forEach { value ->
                val yy = py(value)
                drawLine(p.chartGrid, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
                if (!view.isEmpty) {
                    val label = measurer.measure(ChartAxis.label(value, yTicks.step), tickStyle)
                    drawText(label, topLeft = Offset(left - 8.dp.toPx() - label.size.width, yy - label.size.height / 2f))
                }
            }
            // 横轴刻度值过密时隔一个标一个（网格线照画），避免数字挤在一起。
            val widest = xTicks.values.maxOf { measurer.measure(ChartAxis.label(it, xTicks.step), tickStyle).size.width }
            val spacing = plotW * (xTicks.step / (x1 - x0)).toFloat()
            val labelEvery = if (widest + 8.dp.toPx() > spacing) 2 else 1
            xTicks.values.forEachIndexed { index, value ->
                if (value < x0 - epsX || value > x1 + epsX) return@forEachIndexed
                val xx = px(value)
                drawLine(p.chartGrid, Offset(xx, top), Offset(xx, bottom), strokeWidth = 1.dp.toPx())
                if (!view.isEmpty && index % labelEvery == 0) {
                    val label = measurer.measure(ChartAxis.label(value, xTicks.step), tickStyle)
                    val lx = (xx - label.size.width / 2f).coerceIn(left - label.size.width / 2f, size.width - label.size.width.toFloat())
                    drawText(label, topLeft = Offset(lx, bottom + 5.dp.toPx()))
                }
            }
            drawLine(p.chartAxis, Offset(left, top), Offset(left, bottom), strokeWidth = 1.2.dp.toPx())
            drawLine(p.chartAxis, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.2.dp.toPx())
            val midY = top + plotH / 2f
            rotate(-90f, pivot = Offset(yTitle.size.height / 2f, midY)) {
                drawText(yTitle, topLeft = Offset(yTitle.size.height / 2f - yTitle.size.width / 2f, midY - yTitle.size.height / 2f))
            }
            val titleRow = size.height - xTitle.size.height
            val xTitleLeft = left + plotW / 2f - xTitle.size.width / 2f
            drawText(xTitle, topLeft = Offset(xTitleLeft, titleRow))

            // 图例：只在有上一段时出现，放在横轴名同一行的右端（绘图区外，不压曲线）；放不下就不画。
            if (hasOld) {
                val now = measurer.measure("本段", legendStyle)
                val old = measurer.measure("上一段", legendStyle)
                val swatch = 14.dp.toPx()
                val gap = 5.dp.toPx()
                val between = 12.dp.toPx()
                val total = swatch + gap + now.size.width + between + swatch + gap + old.size.width
                val startX = right - total
                if (startX > xTitleLeft + xTitle.size.width + 12.dp.toPx()) {
                    val cy = titleRow + xTitle.size.height / 2f
                    var cx = startX
                    drawLine(lineColor, Offset(cx, cy), Offset(cx + swatch, cy), strokeWidth = 2.6.dp.toPx(), cap = StrokeCap.Round)
                    cx += swatch + gap
                    drawText(now, topLeft = Offset(cx, cy - now.size.height / 2f))
                    cx += now.size.width + between
                    drawLine(p.chartLineOld, Offset(cx, cy), Offset(cx + swatch, cy), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    cx += swatch + gap
                    drawText(old, topLeft = Offset(cx, cy - old.size.height / 2f))
                }
            }

            clipRect(left, top - 2.dp.toPx(), right + 2.dp.toPx(), bottom) {
                // 目标扭矩虚线（只在扳手结果帧给出目标值、且纵轴为扭矩时出现）。
                target?.let { value ->
                    val ty = py(value)
                    drawLine(p.warn.copy(alpha = 0.8f), Offset(left, ty), Offset(right, ty), strokeWidth = 1.3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                    val label = measurer.measure("目标 ${ChartAxis.label(value, 1.0)}", targetStyle)
                    drawText(label, topLeft = Offset(left + 6.dp.toPx(), ty - label.size.height - 2.dp.toPx()))
                }
                screenLines.clear()
                view.segments.forEach { segment ->
                    if (segment.points.isEmpty()) return@forEach
                    val path = Path()
                    val line = FloatArray(segment.points.size * 2)
                    segment.points.forEachIndexed { index, point ->
                        val x = px(mode.x(point))
                        val y = py(mode.y(point))
                        line[index * 2] = x
                        line[index * 2 + 1] = y
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    screenLines += line
                    val increasing = mode != ChartMode.TORQUE_ANGLE || segment.points.isMostlyIncreasing()
                    if (segment.isCurrent && segment.points.size >= 2 && increasing) {
                        val fill = Path().apply {
                            addPath(path)
                            lineTo(px(mode.x(segment.points.last())), bottom)
                            lineTo(px(mode.x(segment.points.first())), bottom)
                            close()
                        }
                        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = if (p.isDark) 0.34f else 0.20f), lineColor.copy(alpha = 0f)), startY = top, endY = bottom))
                    }
                    drawPath(
                        path,
                        if (segment.isCurrent) lineColor else p.chartLineOld,
                        style = Stroke(width = if (segment.isCurrent) 2.6.dp.toPx() else 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
            latestAt[0] = Float.NaN
            view.latest?.let { latest ->
                val c = Offset(px(mode.x(latest)).coerceIn(left, right), py(mode.y(latest)).coerceIn(top, bottom))
                latestAt[0] = c.x
                latestAt[1] = c.y
                if (!live) drawCircle(lineColor.copy(alpha = 0.22f), radius = 9.dp.toPx(), center = c)
                drawCircle(lineColor, radius = 4.dp.toPx(), center = c)
                // 末点数值标签：在点周围几个候选位里挑第一个不压任何曲线的（左上 → 左下 → 左侧 → 右上 → 右下 → 更低的左下），
                // 都压线就挑压得最少的；读点时收起。
                if (cursor == null) {
                    val tag = measurer.measure(mode.yText(mode.y(latest)), tagStyle)
                    val padX = 6.dp.toPx()
                    val padY = 3.dp.toPx()
                    val w = tag.size.width + padX * 2
                    val h = tag.size.height + padY * 2
                    val offset = 10.dp.toPx()
                    val minX = left + 2.dp.toPx()
                    val candidates = listOf(
                        Offset(c.x - w - offset, c.y - h - offset),
                        Offset(c.x - w - offset, c.y + offset),
                        Offset(c.x - w - offset * 1.6f, c.y - h / 2f),
                        Offset(c.x + offset, c.y - h - offset),
                        Offset(c.x + offset, c.y + offset),
                        Offset(c.x - w - offset, c.y + offset * 2f + h),
                    ).map { Offset(it.x.coerceIn(minX, (right - w).coerceAtLeast(minX)), it.y.coerceIn(top, (bottom - h).coerceAtLeast(top))) }
                    val clearance = 3.dp.toPx()
                    val best = candidates.minBy { o ->
                        val hits = linesHitting(screenLines, o.x - clearance, o.y - clearance, o.x + w + clearance, o.y + h + clearance)
                        // 同样不压线时保持候选顺序；盖住末点本身的位置直接排除。
                        val coversDot = c.x in o.x..(o.x + w) && c.y in o.y..(o.y + h)
                        hits * 10 + (if (coversDot) 1_000 else 0) + candidates.indexOf(o)
                    }
                    drawRoundRect(lineColor, best, Size(w, h), CornerRadius(5.dp.toPx()))
                    drawText(tag, topLeft = Offset(best.x + padX, best.y + padY))
                }
            }

            cursor?.let { point ->
                val cx = px(mode.x(point)).coerceIn(left, right)
                val cy = py(mode.y(point)).coerceIn(top, bottom)
                drawLine(p.textSecondary, Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                drawCircle(p.panel, radius = 5.dp.toPx(), center = Offset(cx, cy))
                drawCircle(p.textPrimary, radius = 5.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                val text = measurer.measure(cursorText(point), cursorStyle)
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
        if (live && !view.isEmpty) LivePulse(latestAt, lineColor)
    }
}

/** 实时更新时末点光圈缓慢呼吸（1.4s 一周期，低透明度）：一眼看出数据在走；断开/延迟时静止。 */
@Composable
private fun LivePulse(latestAt: FloatArray, color: Color) {
    val transition = rememberInfiniteTransition(label = "live")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Canvas(Modifier.fillMaxSize()) {
        val x = latestAt[0]
        val y = latestAt[1]
        if (x.isNaN() || y.isNaN()) return@Canvas
        drawCircle(color.copy(alpha = 0.30f - 0.18f * phase), radius = (8f + 6f * phase).dp.toPx(), center = Offset(x, y))
    }
}

/** 坐标轴端点：首次直接到位，之后变化走 260ms 缓动。 */
@Composable
private fun rememberAxisValue(target: Double): Animatable<Float, AnimationVector1D> {
    val value = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) { value.animateTo(target.toFloat(), tween(260, easing = FastOutSlowInEasing)) }
    return value
}

private const val HEADROOM = 1.05
private const val MIN_SEQ_SPAN = 20.0
private const val MIN_ANGLE_SPAN = 10.0

/** 读点框：序号 + 转角 + 扭矩，与 Excel「过程数据」同一行可对照。 */
/** 屏幕坐标折线里与矩形相交的线段数（端点在内或与任一边相交）。 */
internal fun linesHitting(lines: List<FloatArray>, l: Float, t: Float, r: Float, b: Float): Int {
    var hits = 0
    lines.forEach { line ->
        var i = 0
        while (i + 3 < line.size) {
            if (segmentHitsRect(line[i], line[i + 1], line[i + 2], line[i + 3], l, t, r, b)) hits++
            i += 2
        }
        if (line.size == 2 && line[0] in l..r && line[1] in t..b) hits++
    }
    return hits
}

/** Liang–Barsky 裁剪：线段与轴对齐矩形是否有交。 */
internal fun segmentHitsRect(x0: Float, y0: Float, x1: Float, y1: Float, l: Float, t: Float, r: Float, b: Float): Boolean {
    val dx = x1 - x0
    val dy = y1 - y0
    var u0 = 0f
    var u1 = 1f
    val p = floatArrayOf(-dx, dx, -dy, dy)
    val q = floatArrayOf(x0 - l, r - x0, y0 - t, b - y0)
    for (k in 0 until 4) {
        if (p[k] == 0f) {
            if (q[k] < 0f) return false
        } else {
            val u = q[k] / p[k]
            if (p[k] < 0f) { if (u > u1) return false; if (u > u0) u0 = u } else { if (u < u0) return false; if (u < u1) u1 = u }
        }
    }
    return true
}

private fun cursorText(point: ChartPoint): String =
    "#${point.seq} · ${WorkerText.readout(point.angle, true)}° · ${WorkerText.readout(point.torque, true)} N·m"

/** 顶升时转角基本单调递增；回退/往复时不画面积，避免填充错位。 */
private fun List<ChartPoint>.isMostlyIncreasing(): Boolean {
    if (size < 2) return false
    var back = 0
    for (i in 1 until size) if (this[i].angle < this[i - 1].angle - 0.5) back += 1
    return back <= size / 20
}

private fun nearest(points: List<ChartPoint>, mode: ChartMode, touchX: Float, bounds: FloatArray, ticks: ChartAxis.Ticks): ChartPoint? {
    val width = bounds[1] - bounds[0]
    if (points.isEmpty() || width <= 0f) return null
    val fraction = ((touchX - bounds[0]) / width).coerceIn(0f, 1f)
    val x = ticks.min + fraction * (ticks.max - ticks.min)
    return points.minByOrNull { abs(mode.x(it) - x) }
}
