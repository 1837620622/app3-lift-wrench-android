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
import androidx.compose.ui.geometry.Rect
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
import com.chuankangkk.wrenchlift.measurement.ChartSegment
import com.chuankangkk.wrenchlift.measurement.TorqueAngleView
import com.chuankangkk.wrenchlift.measurement.WorkerText
import com.chuankangkk.wrenchlift.measurement.analysis.LoadingAnalysis
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
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

    fun label(value: Double, step: Double): String = "%.${decimalsFor(step)}f".format(Locale.US, value)

    /**
     * 步长需要几位小数：1/2/5×10ⁿ 为 max(0, −n)，2.5×10ⁿ 多一位（0.25 需要两位，2.5 需要一位）。
     * 旧实现把所有非整数步长都写成一位小数，斜率轴步长 0.25 时 0.25 会标成 0.3（Java 半数进位），0.75 标成 0.8。
     */
    internal fun decimalsFor(step: Double): Int {
        if (!(step > 0.0) || !step.isFinite()) return 0
        val exp = floor(log10(step) + 1e-9).toInt()
        val mantissa = step / 10.0.pow(exp)
        val halfStep = abs(mantissa - 2.5) < 1e-6
        return max(0, -exp + if (halfStep) 1 else 0)
    }
}

/**
 * 主图两种关系，横轴都是转角（切换时转折候选的位置对得上）：
 * - 扭矩–转角：过程曲线本身；
 * - 斜率–转角：当前运行段有效加载段的局部斜率 dT/dθ（N·m/°）。曲线「变陡/变缓」在这里是一个台阶，
 *   找平衡点（临界分离）时先看这里斜率在哪里明显变化，再回扭矩–转角核对。
 * 原「扭矩–序号 / 转角–序号」只是按接收顺序排点，对找转折没有帮助，已从主屏移除（导出改为按接收时间作图）。
 */
enum class ChartMode(val tab: String, val xTitle: String, val yTitle: String, private val unit: String) {
    TORQUE_ANGLE("扭矩–转角", "转角 °", "扭矩 N·m", " N·m"),
    SLOPE_ANGLE("斜率–转角", "转角 °", "斜率 N·m/°", " N·m/°"),
    ;

    /** 末点标签 / 读点文字里的纵轴值。 */
    fun yText(value: Double): String = WorkerText.readout(value, true) + unit
}

/**
 * 主图（默认扭矩–转角）。
 * - 横轴按当前运行段转角跨度自适应；两个页签共用横轴；
 * - 旧段淡色细线、段间不连线，有上一段时在横轴名一行右侧标图例；
 * - 段内超过 2.5 秒没收到数据（数据间断）画同色细虚线，与「转折候选」的紫色标记区分；
 * - 转折候选（两段直线拟合，未验证）：紫色竖虚线 + 空心菱形 + 两段拟合细虚线 + 「候选 x° · 未验证」；
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
    val candidate = view.analysis?.candidate
    // 读屏/测试可读到转折候选（画在 Canvas 里的标签没有语义）。
    val description = "${mode.tab}实时曲线" + (candidate?.let { "，转折候选 ${WorkerText.readout(it.angleDeg, true)}°（未验证）" } ?: "")
    Box(modifier.testTag("torque_chart").semantics { contentDescription = description }) {
        Crossfade(targetState = mode, animationSpec = tween(180), label = "chart_mode", modifier = Modifier.fillMaxSize()) { shown ->
            ChartCanvas(view, shown, targetTorqueNm, live)
        }
        val p = palette
        if (view.isEmpty) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(emptyTitle, style = WrenchType.bodyStrong.copy(fontSize = 17.sp), color = p.textPrimary)
                Text(emptyHint, style = WrenchType.labelStyle, color = p.textSecondary)
            }
        } else if (mode == ChartMode.SLOPE_ANGLE && slopeSeries(view).isEmpty()) {
            // 斜率需要一段像样的有效加载；不够时直说原因（分析给出的），不画误导性的线。
            Text(
                (view.analysis?.outcome as? LoadingAnalysis.NotFound)?.reason ?: "加载数据不足",
                style = WrenchType.labelStyle, color = p.textSecondary,
                modifier = Modifier.align(Alignment.Center).testTag("slope_reason"),
            )
        }
    }
}

/** 斜率–转角的数据：当前运行段的局部斜率（纵值放在 [ChartPoint.torque] 里复用画线代码）。 */
private fun slopeSeries(view: TorqueAngleView): List<ChartSegment> {
    val slopes = view.analysis?.slopes.orEmpty()
    if (slopes.size < 2) return emptyList()
    return listOf(ChartSegment(slopes.map { ChartPoint(it.angleDeg, it.slope, 0L) }, isCurrent = true, sourceCount = slopes.size))
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
    val torqueMode = mode == ChartMode.TORQUE_ANGLE
    val series = remember(view, mode) { if (torqueMode) view.segments else slopeSeries(view) }
    val candidate = view.analysis?.candidate
    val xTicks = remember(view) { ChartAxis.niceTicks(view.minAngle, view.maxAngle, 6) }
    val target = targetTorqueNm.takeIf { torqueMode }
    val yTicks = remember(view, mode, target) {
        if (torqueMode) {
            ChartAxis.niceTicks(view.minTorque, max(view.maxTorque, target ?: 0.0) * HEADROOM, 5)
        } else {
            val values = series.flatMap { s -> s.points.map { it.torque } } + listOfNotNull(candidate?.slopeBefore, candidate?.slopeAfter)
            val low = min(0.0, values.minOrNull() ?: 0.0) * HEADROOM
            val high = max(values.maxOrNull() ?: 1.0, low + 1.0) * HEADROOM
            ChartAxis.niceTicks(low, high, 5)
        }
    }
    // 坐标范围平滑过渡：数据把轴撑大时网格与曲线一起缩放，而不是跳变。
    val xMin = rememberAxisValue(xTicks.min)
    val xMax = rememberAxisValue(xTicks.max)
    val yMin = rememberAxisValue(yTicks.min)
    val yMax = rememberAxisValue(yTicks.max)

    val lineColor = if (torqueMode) p.chartLine else p.chartAngle
    val candidateColor = p.chartCandidate
    val tickStyle = TextStyle(fontSize = 12.sp, color = p.textSecondary, fontFeatureSettings = "tnum")
    val axisTitleStyle = TextStyle(fontSize = 13.sp, color = p.textSecondary, fontWeight = FontWeight.Medium)
    val legendStyle = TextStyle(fontSize = 12.sp, color = p.textSecondary)
    val tagStyle = TextStyle(fontSize = 13.sp, color = p.onAccent, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val targetStyle = TextStyle(fontSize = 12.sp, color = p.warn, fontFeatureSettings = "tnum")
    val cursorStyle = TextStyle(fontSize = 13.sp, color = p.textPrimary, fontFeatureSettings = "tnum")
    val gapStyle = TextStyle(fontSize = 11.sp, color = p.textSecondary, fontFeatureSettings = "tnum")
    val candidateStyle = TextStyle(fontSize = 12.sp, color = candidateColor, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val current = series.lastOrNull { it.isCurrent }?.points.orEmpty()
    val hasOld = torqueMode && series.any { !it.isCurrent && it.points.isNotEmpty() }
    val plotBounds = remember { floatArrayOf(0f, 1f) }
    // 末点在画布上的位置：主画布写入，呼吸光圈层读取（光圈逐帧重画时不必重画整条曲线）。
    val latestAt = remember { floatArrayOf(Float.NaN, Float.NaN) }
    // 本帧画出的各段折线与候选标签框（屏幕坐标），给末点标签避让用。
    val screenLines = remember { ArrayList<FloatArray>() }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(view, mode) {
                    detectTapGestures { offset -> cursor = nearest(current, offset.x, plotBounds, xTicks); cursorStamp = System.nanoTime() }
                }
                .pointerInput(view, mode) {
                    detectDragGestures { change, _ -> cursor = nearest(current, change.position.x, plotBounds, xTicks); cursorStamp = System.nanoTime() }
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
            val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))

            // 网格与刻度（发丝线）。没有数据时只画网格，不显示无意义的刻度值；缩放过渡中只画落在范围内的刻度。
            yTicks.values.filter { it >= y0 - epsY && it <= y1 + epsY }.forEach { value ->
                val yy = py(value)
                drawLine(if (abs(value) < 1e-9 && !torqueMode) p.chartAxis else p.chartGrid, Offset(left, yy), Offset(right, yy), strokeWidth = 1.dp.toPx())
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

            screenLines.clear()
            val gapLabels = ArrayList<Pair<androidx.compose.ui.text.TextLayoutResult, Offset>>()
            clipRect(left, top - 2.dp.toPx(), right + 2.dp.toPx(), bottom) {
                // 目标扭矩虚线（只在扳手结果帧给出目标值、且纵轴为扭矩时出现）。
                target?.let { value ->
                    val ty = py(value)
                    drawLine(p.warn.copy(alpha = 0.8f), Offset(left, ty), Offset(right, ty), strokeWidth = 1.3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
                    val label = measurer.measure("目标 ${ChartAxis.label(value, 1.0)}", targetStyle)
                    drawText(label, topLeft = Offset(left + 6.dp.toPx(), ty - label.size.height - 2.dp.toPx()))
                }
                series.forEach { segment ->
                    if (segment.points.isEmpty()) return@forEach
                    val path = Path()
                    // 填充用一条不断开的折线：段内间断处 moveTo 会让填充按子路径各自闭合，出现斜向色边。
                    val outline = Path()
                    val line = FloatArray(segment.points.size * 2)
                    val gapStarts = segment.gapStarts.toHashSet()
                    val color = if (segment.isCurrent) lineColor else p.chartLineOld
                    segment.points.forEachIndexed { index, point ->
                        val x = px(point.angle)
                        val y = py(point.torque)
                        line[index * 2] = x
                        line[index * 2 + 1] = y
                        if (index == 0 || index in gapStarts) path.moveTo(x, y) else path.lineTo(x, y)
                        if (index == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
                    }
                    screenLines += line
                    if (torqueMode && segment.isCurrent && segment.points.size >= 2 && segment.points.isMostlyIncreasing()) {
                        val fill = Path().apply {
                            addPath(outline)
                            lineTo(px(segment.points.last().angle), bottom)
                            lineTo(px(segment.points.first().angle), bottom)
                            close()
                        }
                        drawPath(fill, Brush.verticalGradient(listOf(lineColor.copy(alpha = if (p.isDark) 0.34f else 0.20f), lineColor.copy(alpha = 0f)), startY = top, endY = bottom))
                    }
                    drawPath(
                        path,
                        color,
                        style = Stroke(width = if (segment.isCurrent) 2.6.dp.toPx() else 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                    // 数据间断：这两点之间超过 2.5 秒没收到数据。用细虚线连 + 空心小圆 + 「间断 N s」（灰色），
                    // 不当作连续实测；与紫色的转折候选完全不同。转角没动时虚线很短，靠小圆和文字看出来。
                    var lastLabelRight = Float.NEGATIVE_INFINITY
                    segment.gapStarts.forEach { index ->
                        if (index <= 0 || index >= segment.points.size) return@forEach
                        val a = segment.points[index - 1]
                        val b = segment.points[index]
                        val pa = Offset(px(a.angle), py(a.torque))
                        val pb = Offset(px(b.angle), py(b.torque))
                        drawLine(color.copy(alpha = 0.75f), pa, pb, strokeWidth = 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())))
                        drawCircle(p.panel, radius = 4.dp.toPx(), center = pb)
                        drawCircle(p.textSecondary, radius = 4.dp.toPx(), center = pb, style = Stroke(1.5.dp.toPx()))
                        val seconds = ((b.receivedAtMillis - a.receivedAtMillis) / 1000.0).let { if (it >= 10) "%.0f".format(Locale.US, it) else "%.1f".format(Locale.US, it) }
                        val label = measurer.measure("间断 $seconds s", gapStyle)
                        // 在点的左上 / 右下 / 右上 / 左下里挑压线最少、且不被候选竖线穿过的位置。
                        val candidateX = candidate?.let { px(it.angleDeg) }
                        val w = label.size.width.toFloat()
                        val h = label.size.height.toFloat()
                        val gapOffset = 6.dp.toPx()
                        val spots = listOf(
                            Offset(pb.x - gapOffset - w, pb.y - gapOffset - h),
                            Offset(pb.x + gapOffset, pb.y + gapOffset),
                            Offset(pb.x + gapOffset, pb.y - gapOffset - h),
                            Offset(pb.x - gapOffset - w, pb.y + gapOffset),
                        ).map { Offset(it.x.coerceIn(left + 2.dp.toPx(), right - w - 2.dp.toPx()), it.y.coerceIn(top, bottom - h)) }
                        val spot = spots.minBy { o ->
                            val crossed = candidateX != null && candidateX in (o.x - 3.dp.toPx())..(o.x + w + 3.dp.toPx())
                            linesHitting(screenLines, o.x, o.y, o.x + w, o.y + h) * 10 + (if (crossed) 100 else 0) + spots.indexOf(o)
                        }
                        val lx = spot.x
                        val ly = spot.y
                        if (segment.isCurrent && lx > lastLabelRight + 4.dp.toPx()) {
                            gapLabels += label to Offset(lx, ly)
                            lastLabelRight = lx + label.size.width
                            screenLines += floatArrayOf(lx, ly, lx + label.size.width, ly, lx + label.size.width, ly + label.size.height, lx, ly + label.size.height, lx, ly)
                        }
                    }
                }
                candidate?.let { c ->
                    val cx = px(c.angleDeg)
                    drawLine(candidateColor.copy(alpha = 0.7f), Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.3.dp.toPx(), pathEffect = dash)
                    if (torqueMode) {
                        // 两段拟合直线（细虚线），说明候选是怎么来的。
                        val knee = Offset(cx, py(c.torqueNm))
                        drawLine(candidateColor.copy(alpha = 0.85f), Offset(px(c.fitStart.first), py(c.fitStart.second)), knee, strokeWidth = 1.2.dp.toPx(), pathEffect = dash)
                        drawLine(candidateColor.copy(alpha = 0.85f), knee, Offset(px(c.fitEnd.first), py(c.fitEnd.second)), strokeWidth = 1.2.dp.toPx(), pathEffect = dash)
                        val r = 6.dp.toPx()
                        val diamond = Path().apply {
                            moveTo(knee.x, knee.y - r); lineTo(knee.x + r, knee.y); lineTo(knee.x, knee.y + r); lineTo(knee.x - r, knee.y); close()
                        }
                        drawPath(diamond, p.panel)
                        drawPath(diamond, candidateColor, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
                    } else {
                        // 斜率图：候选前后两段的拟合斜率各画一条水平虚线并标值。
                        val start = px(c.fitStart.first)
                        val end = px(c.fitEnd.first)
                        val yBefore = py(c.slopeBefore)
                        val yAfter = py(c.slopeAfter)
                        drawLine(candidateColor, Offset(start, yBefore), Offset(cx, yBefore), strokeWidth = 1.6.dp.toPx(), pathEffect = dash)
                        drawLine(candidateColor, Offset(cx, yAfter), Offset(end, yAfter), strokeWidth = 1.6.dp.toPx(), pathEffect = dash)
                        val before = measurer.measure("前 ${WorkerText.readout(c.slopeBefore, true)}", candidateStyle)
                        val after = measurer.measure("后 ${WorkerText.readout(c.slopeAfter, true)}", candidateStyle)
                        // 数值标在水平线旁：外端 / 靠竖线一端 × 线上 / 线下，挑不压斜率曲线、不被竖线穿过的位置。
                        val gap = 4.dp.toPx()
                        fun place(label: androidx.compose.ui.text.TextLayoutResult, lineY: Float, from: Float, to: Float, outerIsLeft: Boolean): Offset {
                            val w = label.size.width.toFloat()
                            val h = label.size.height.toFloat()
                            val outerX = if (outerIsLeft) from + gap else to - w - gap
                            val innerX = if (outerIsLeft) to - w - 2 * gap else from + 2 * gap
                            // 线段太短时，标在线段延长线外侧（数据之外通常是空白）。
                            val beyondX = if (outerIsLeft) from - gap - w else to + gap
                            val spots = (listOf(outerX, innerX).flatMap { x -> listOf(Offset(x, lineY - h - gap), Offset(x, lineY + gap)) } +
                                Offset(beyondX, lineY - h / 2f))
                                .map { Offset(it.x.coerceIn(left + 2.dp.toPx(), (right - w - 2.dp.toPx()).coerceAtLeast(left)), it.y.coerceIn(top, (bottom - h).coerceAtLeast(top))) }
                            val clear = 3.dp.toPx()
                            return spots.minBy { o ->
                                val crossed = cx > o.x - gap && cx < o.x + w + gap
                                linesHitting(screenLines, o.x - clear, o.y - clear, o.x + w + clear, o.y + h + clear) * 10 + (if (crossed) 100 else 0) + spots.indexOf(o)
                            }
                        }
                        val b = place(before, yBefore, start, cx, outerIsLeft = true)
                        drawText(before, topLeft = b)
                        screenLines += rectOutline(b.x, b.y, before.size.width.toFloat(), before.size.height.toFloat())
                        val a = place(after, yAfter, cx, end, outerIsLeft = false)
                        drawText(after, topLeft = a)
                        // 记下两个数值框，候选标签与末点标签都避开它们。
                        screenLines += rectOutline(a.x, a.y, after.size.width.toFloat(), after.size.height.toFloat())
                    }
                }
            }
            // 间断文字最后画、带底色：不被候选竖线或拟合虚线穿过。
            gapLabels.forEach { (label, at) ->
                drawRoundRect(p.panel.copy(alpha = 0.9f), Offset(at.x - 2.dp.toPx(), at.y), Size(label.size.width + 4.dp.toPx(), label.size.height.toFloat()), CornerRadius(3.dp.toPx()))
                drawText(label, topLeft = at)
            }
            // 候选标签：贴着竖线，在 右上 / 左上 / 右下 / 左下 里挑压线最少、且不盖住菱形的位置；写明「未验证」。
            var chipRect: Rect? = null
            // 末点（及其光晕）也算障碍：候选标签不贴着末点（只给候选标签用，放完就拿掉）。
            val dotObstacle = view.latest?.takeIf { torqueMode }?.let { latest ->
                val dx = px(latest.angle).coerceIn(left, right)
                val dy = py(latest.torque).coerceIn(top, bottom)
                val r = 12.dp.toPx()
                rectOutline(dx - r, dy - r, 2 * r, 2 * r).also { screenLines += it }
            }
            candidate?.let { c ->
                val cx = px(c.angleDeg)
                if (cx < left - 1f || cx > right + 1f) return@let
                val text = measurer.measure("候选 ${WorkerText.readout(c.angleDeg, true)}° · 未验证", candidateStyle)
                val pad = 5.dp.toPx()
                val w = text.size.width + pad * 2
                val h = text.size.height + pad
                val gap = 4.dp.toPx()
                val knee = if (torqueMode) Offset(cx, py(c.torqueNm)) else null
                val spots = listOf(
                    Offset(cx + gap, top + 2.dp.toPx()),
                    Offset(cx - gap - w, top + 2.dp.toPx()),
                    Offset(cx + gap, bottom - h - 2.dp.toPx()),
                    Offset(cx - gap - w, bottom - h - 2.dp.toPx()),
                ).map { Offset(it.x.coerceIn(left, (right - w).coerceAtLeast(left)), it.y) }
                val chip = spots.minBy { o ->
                    val coversKnee = knee != null && knee.x in (o.x - 8.dp.toPx())..(o.x + w + 8.dp.toPx()) && knee.y in (o.y - 8.dp.toPx())..(o.y + h + 8.dp.toPx())
                    // 挤到边上后被竖线穿过的位置也尽量不用。
                    val crossesLine = cx > o.x + 1f && cx < o.x + w - 1f
                    linesHitting(screenLines, o.x, o.y, o.x + w, o.y + h) * 10 + (if (coversKnee) 1_000 else 0) + (if (crossesLine) 200 else 0) + spots.indexOf(o)
                }
                val lx = chip.x
                val ly = chip.y
                drawRoundRect(p.panel.copy(alpha = 0.92f), Offset(lx, ly), Size(w, h), CornerRadius(5.dp.toPx()))
                drawRoundRect(candidateColor.copy(alpha = 0.8f), Offset(lx, ly), Size(w, h), CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
                drawText(text, topLeft = Offset(lx + pad, ly + pad / 2f))
                screenLines += rectOutline(lx, ly, w, h)
                chipRect = Rect(lx, ly, lx + w, ly + h)
            }
            dotObstacle?.let { screenLines.remove(it) }
            latestAt[0] = Float.NaN
            view.latest?.takeIf { torqueMode }?.let { latest ->
                val c = Offset(px(latest.angle).coerceIn(left, right), py(latest.torque).coerceIn(top, bottom))
                latestAt[0] = c.x
                latestAt[1] = c.y
                if (!live) drawCircle(lineColor.copy(alpha = 0.22f), radius = 9.dp.toPx(), center = c)
                drawCircle(lineColor, radius = 4.dp.toPx(), center = c)
                // 末点数值标签：在点周围几个候选位里挑第一个不压任何曲线的（左上 → 左下 → 左侧 → 右上 → 右下 → 更低的左下），
                // 都压线就挑压得最少的；读点时收起。
                if (cursor == null) {
                    val tag = measurer.measure(mode.yText(latest.torque), tagStyle)
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
                    fun coversChip(o: Offset) = chipRect?.overlaps(Rect(o.x - clearance, o.y - clearance, o.x + w + clearance, o.y + h + clearance)) == true
                    val best = candidates.minBy { o ->
                        val hits = linesHitting(screenLines, o.x - clearance, o.y - clearance, o.x + w + clearance, o.y + h + clearance)
                        // 同样不压线时保持候选顺序；盖住末点本身或候选标签的位置直接排除。
                        val coversDot = c.x in o.x..(o.x + w) && c.y in o.y..(o.y + h)
                        hits * 10 + (if (coversDot) 1_000 else 0) + (if (coversChip(o)) 2_000 else 0) + candidates.indexOf(o)
                    }
                    // 矮图里实在放不下就不画：左侧读数已经大字显示同一个值，不能盖住「候选 · 未验证」。
                    if (!coversChip(best)) {
                        drawRoundRect(lineColor, best, Size(w, h), CornerRadius(5.dp.toPx()))
                        drawText(tag, topLeft = Offset(best.x + padX, best.y + padY))
                    }
                }
            }

            cursor?.let { point ->
                val cx = px(point.angle).coerceIn(left, right)
                val cy = py(point.torque).coerceIn(top, bottom)
                drawLine(p.textSecondary, Offset(cx, top), Offset(cx, bottom), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                drawCircle(p.panel, radius = 5.dp.toPx(), center = Offset(cx, cy))
                drawCircle(p.textPrimary, radius = 5.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
                val text = measurer.measure(cursorText(point, mode), cursorStyle)
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
        if (live && torqueMode && !view.isEmpty) LivePulse(latestAt, lineColor)
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

/** 屏幕坐标折线里与矩形相交的线段数（端点在内或与任一边相交）。 */
/** 矩形外框折线（屏幕坐标），用于标签互相避让。 */
internal fun rectOutline(x: Float, y: Float, w: Float, h: Float) = floatArrayOf(x, y, x + w, y, x + w, y + h, x, y + h, x, y)

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

/** 读点框：扭矩–转角为「#序号 · 转角 · 扭矩」（与 Excel「过程数据」同一行可对照）；斜率图为「转角 · 斜率」。 */
private fun cursorText(point: ChartPoint, mode: ChartMode): String = when (mode) {
    ChartMode.TORQUE_ANGLE -> "#${point.seq} · ${WorkerText.readout(point.angle, true)}° · ${WorkerText.readout(point.torque, true)} N·m"
    ChartMode.SLOPE_ANGLE -> "${WorkerText.readout(point.angle, true)}° · ${mode.yText(point.torque)}"
}

/** 顶升时转角基本单调递增；回退/往复时不画面积，避免填充错位。 */
private fun List<ChartPoint>.isMostlyIncreasing(): Boolean {
    if (size < 2) return false
    var back = 0
    for (i in 1 until size) if (this[i].angle < this[i - 1].angle - 0.5) back += 1
    return back <= size / 20
}

private fun nearest(points: List<ChartPoint>, touchX: Float, bounds: FloatArray, ticks: ChartAxis.Ticks): ChartPoint? {
    val width = bounds[1] - bounds[0]
    if (points.isEmpty() || width <= 0f) return null
    val fraction = ((touchX - bounds[0]) / width).coerceIn(0f, 1f)
    val x = ticks.min + fraction * (ticks.max - ticks.min)
    return points.minByOrNull { abs(it.angle - x) }
}
