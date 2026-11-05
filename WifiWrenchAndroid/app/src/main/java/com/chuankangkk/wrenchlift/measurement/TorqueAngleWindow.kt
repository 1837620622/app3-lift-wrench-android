package com.chuankangkk.wrenchlift.measurement

import kotlin.math.max
import kotlin.math.min

/**
 * 实时曲线显示窗口（只影响界面）。数据库与导出始终是全量，不受这里的上限、分段、抽样影响。
 *
 * 规则：
 * - 内存显示缓冲最多 [MAX_BUFFER_POINTS] 点（约十几分钟连续回传），只用于画图；
 * - 按「运行段」切分：螺栓号变化、转角突然回落（新一次运行）、或接收间隔过长时断开，段与段之间不连线；
 * - 横轴按当前段转角跨度自适应；跨度超过 [MAX_VISIBLE_SPAN_DEG] 时只看最近这一段跨度；
 * - 点太多时按序号分桶保留每桶最小/最大扭矩，保住峰谷形态，不插值、不造点。
 */
object TorqueAngleWindow {
    const val MAX_BUFFER_POINTS = 4_000
    const val RESET_DROP_DEG = 45.0
    const val GAP_MILLIS = 10_000L
    const val MIN_SPAN_DEG = 10.0
    const val MAX_VISIBLE_SPAN_DEG = 1_080.0
    const val MAX_SEGMENTS = 2

    fun append(buffer: List<MeasuredPoint>, added: List<MeasuredPoint>): List<MeasuredPoint> {
        if (added.isEmpty()) return buffer
        val merged = buffer + added
        return if (merged.size > MAX_BUFFER_POINTS) merged.takeLast(MAX_BUFFER_POINTS) else merged
    }

    fun segments(points: List<MeasuredPoint>): List<List<MeasuredPoint>> {
        if (points.isEmpty()) return emptyList()
        val result = mutableListOf<MutableList<MeasuredPoint>>()
        var current = mutableListOf(points.first())
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val point = points[index]
            val newRun = point.boltNo != previous.boltNo ||
                point.sourcePoint.angleDeg < previous.sourcePoint.angleDeg - RESET_DROP_DEG ||
                point.sourcePoint.timestampMillis - previous.sourcePoint.timestampMillis > GAP_MILLIS
            if (newRun) {
                result += current
                current = mutableListOf()
            }
            current += point
        }
        result += current
        return result
    }

    fun view(points: List<MeasuredPoint>, maxDrawPointsPerSegment: Int = 600): TorqueAngleView {
        val all = segments(points)
        if (all.isEmpty()) return TorqueAngleView.EMPTY
        val kept = all.takeLast(MAX_SEGMENTS)
        val latestSegment = kept.last()
        val latest = latestSegment.last().toChartPoint()

        val angleMax = latestSegment.maxOf { it.sourcePoint.angleDeg }
        val unionMin = kept.minOf { segment -> segment.minOf { it.sourcePoint.angleDeg } }
        val unionMax = max(angleMax, kept.maxOf { segment -> segment.maxOf { it.sourcePoint.angleDeg } })
        val clippedMin = max(unionMin, unionMax - MAX_VISIBLE_SPAN_DEG)
        val windowed = all.size > kept.size || clippedMin > unionMin || points.size >= MAX_BUFFER_POINTS

        val drawn = kept.mapIndexedNotNull { index, segment ->
            val inRange = segment
                .filter { it.sourcePoint.angleDeg >= clippedMin }
                .map { it.toChartPoint() }
            if (inRange.isEmpty()) null else ChartSegment(
                points = decimate(inRange, maxDrawPointsPerSegment),
                isCurrent = index == kept.lastIndex,
                sourceCount = inRange.size,
            )
        }
        val visible = drawn.flatMap { it.points }
        var minAngle = visible.minOfOrNull { it.angle } ?: latest.angle
        var maxAngle = visible.maxOfOrNull { it.angle } ?: latest.angle
        if (maxAngle - minAngle < MIN_SPAN_DEG) {
            // 刚起转时跨度很小：给最小 10° 视野，非负角度不把轴拉到负数。
            val nonNegative = minAngle >= 0.0
            minAngle = (maxAngle + minAngle) / 2.0 - MIN_SPAN_DEG / 2.0
            if (nonNegative && minAngle < 0.0) minAngle = 0.0
            maxAngle = minAngle + MIN_SPAN_DEG
        }
        val minTorque = min(0.0, visible.minOfOrNull { it.torque } ?: 0.0)
        val maxTorque = max(visible.maxOfOrNull { it.torque } ?: 0.0, minTorque + 1.0)
        return TorqueAngleView(
            segments = drawn,
            minAngle = minAngle,
            maxAngle = maxAngle,
            minTorque = minTorque,
            maxTorque = maxTorque,
            shownPointCount = drawn.sumOf { it.sourceCount },
            bufferedPointCount = points.size,
            latest = latest,
            windowed = windowed,
            sampled = drawn.any { it.points.size < it.sourceCount },
            currentPeak = latestSegment.maxBy { it.sourcePoint.torqueNm }.toChartPoint(),
            currentPointCount = latestSegment.size,
        )
    }

    /** 按序号分桶，每桶保留最小与最大扭矩点（保持原顺序）；末点始终保留。 */
    fun decimate(points: List<ChartPoint>, maxPoints: Int): List<ChartPoint> {
        if (points.size <= maxPoints || maxPoints < 4) return points
        val bucketCount = maxPoints / 2
        val bucketSize = points.size.toDouble() / bucketCount
        val result = ArrayList<ChartPoint>(maxPoints + 1)
        for (bucket in 0 until bucketCount) {
            val start = (bucket * bucketSize).toInt()
            val end = min(points.size, ((bucket + 1) * bucketSize).toInt()).coerceAtLeast(start + 1)
            var minIndex = start
            var maxIndex = start
            for (i in start until end) {
                if (points[i].torque < points[minIndex].torque) minIndex = i
                if (points[i].torque > points[maxIndex].torque) maxIndex = i
            }
            if (minIndex == maxIndex) {
                result += points[minIndex]
            } else {
                result += points[min(minIndex, maxIndex)]
                result += points[max(minIndex, maxIndex)]
            }
        }
        if (result.last() != points.last()) result += points.last()
        return result
    }

    private fun MeasuredPoint.toChartPoint() = ChartPoint(
        angle = sourcePoint.angleDeg,
        torque = sourcePoint.torqueNm,
        receivedAtMillis = sourcePoint.timestampMillis,
    )
}

data class ChartPoint(val angle: Double, val torque: Double, val receivedAtMillis: Long)

data class ChartSegment(
    val points: List<ChartPoint>,
    val isCurrent: Boolean,
    val sourceCount: Int,
)

data class TorqueAngleView(
    val segments: List<ChartSegment>,
    val minAngle: Double,
    val maxAngle: Double,
    val minTorque: Double,
    val maxTorque: Double,
    val shownPointCount: Int,
    val bufferedPointCount: Int,
    val latest: ChartPoint?,
    val windowed: Boolean,
    val sampled: Boolean,
    /** 当前运行段（未抽样）的峰值扭矩点。 */
    val currentPeak: ChartPoint? = null,
    val currentPointCount: Int = 0,
) {
    val isEmpty: Boolean get() = latest == null

    companion object {
        val EMPTY = TorqueAngleView(emptyList(), 0.0, MIN_SPAN, 0.0, 1.0, 0, 0, null, windowed = false, sampled = false)
        private const val MIN_SPAN = TorqueAngleWindow.MIN_SPAN_DEG
    }
}
