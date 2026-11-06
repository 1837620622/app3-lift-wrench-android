package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.measurement.analysis.LoadingAnalysis
import com.chuankangkk.wrenchlift.measurement.analysis.WorkingCondition
import kotlin.math.max
import kotlin.math.min

/**
 * 实时曲线显示窗口（只影响界面）。数据库与导出始终是全量，不受这里的上限、分段、抽样影响。
 *
 * 规则：
 * - 内存显示缓冲最多 [MAX_BUFFER_POINTS] 点（约十几分钟连续回传），只用于画图；
 * - 按「运行段」切分（[isNewRun]，与导出同一口径）：螺栓号变化、转角突然回落或回到 0 附近（新一次运行）、
 *   或接收间隔超过 [GAP_MILLIS] 时断开，段与段之间不连线；
 * - 段内接收间隔超过 [DASH_GAP_MILLIS]（0x12 约每秒一帧，漏了一帧以上）记为「数据间断」，画虚线连接，
 *   与「转折候选」的标记完全不同；
 * - 当前段另做加载分析（[LoadingAnalysis]）：转折候选与局部斜率，只作参考、未验证；
 * - 当前段另做工况划分（[WorkingCondition]）：界面只显示末点工况与疑似打滑次数，明细在导出；
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
    const val DASH_GAP_MILLIS = 2_500L
    /** 转角从 ≥ [RESTART_FROM_DEG] 一步回到 ≤ [RESTART_AT_DEG]：扳手新一次运行从 0 起算。 */
    const val RESTART_AT_DEG = 1.0
    const val RESTART_FROM_DEG = 5.0

    /** 运行段边界（实时图、导出图表、导出「运行段」同一口径）。 */
    fun isNewRun(prevBolt: Int, prevAngle: Double, prevMillis: Long, bolt: Int, angle: Double, millis: Long): Boolean =
        bolt != prevBolt ||
            angle < prevAngle - RESET_DROP_DEG ||
            (angle <= RESTART_AT_DEG && prevAngle >= RESTART_FROM_DEG) ||
            millis - prevMillis > GAP_MILLIS

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
            val newRun = isNewRun(
                previous.boltNo, previous.sourcePoint.angleDeg, previous.sourcePoint.timestampMillis,
                point.boltNo, point.sourcePoint.angleDeg, point.sourcePoint.timestampMillis,
            )
            if (newRun) {
                result += current
                current = mutableListOf()
            }
            current += point
        }
        result += current
        return result
    }

    /**
     * @param firstSeq 缓冲区第一个点在本记录里的序号（从 1 起），与导出「过程数据」的「序号」列一致。
     */
    fun view(points: List<MeasuredPoint>, maxDrawPointsPerSegment: Int = 600, firstSeq: Long = 1): TorqueAngleView {
        val all = segments(points)
        if (all.isEmpty()) return TorqueAngleView.EMPTY
        val kept = all.takeLast(MAX_SEGMENTS)
        // 各段在缓冲区里是连续切片：由此算出每个点的记录内序号。
        val keptStarts = ArrayList<Int>(kept.size)
        var cursor = points.size - kept.sumOf { it.size }
        kept.forEach { keptStarts += cursor; cursor += it.size }
        val latestSegment = kept.last()
        val latestStart = keptStarts.last()
        val latest = latestSegment.last().toChartPoint(firstSeq + latestStart + latestSegment.lastIndex)

        val angleMax = latestSegment.maxOf { it.sourcePoint.angleDeg }
        val unionMin = kept.minOf { segment -> segment.minOf { it.sourcePoint.angleDeg } }
        val unionMax = max(angleMax, kept.maxOf { segment -> segment.maxOf { it.sourcePoint.angleDeg } })
        val clippedMin = max(unionMin, unionMax - MAX_VISIBLE_SPAN_DEG)
        val windowed = all.size > kept.size || clippedMin > unionMin || points.size >= MAX_BUFFER_POINTS

        val drawn = kept.mapIndexedNotNull { index, segment ->
            val start = keptStarts[index]
            val inRange = segment.withIndex()
                .filter { it.value.sourcePoint.angleDeg >= clippedMin }
                .map { it.value.toChartPoint(firstSeq + start + it.index) }
            if (inRange.isEmpty()) null else withGaps(inRange, maxDrawPointsPerSegment).let { (drawnPoints, gaps) ->
                ChartSegment(
                    points = drawnPoints,
                    isCurrent = index == kept.lastIndex,
                    sourceCount = inRange.size,
                    gapStarts = gaps,
                )
            }
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
            currentPeak = latestSegment.withIndex().maxBy { it.value.sourcePoint.torqueNm }
                .let { it.value.toChartPoint(firstSeq + latestStart + it.index) },
            currentPointCount = latestSegment.size,
            analysis = LoadingAnalysis.analyze(
                DoubleArray(latestSegment.size) { latestSegment[it].sourcePoint.angleDeg },
                DoubleArray(latestSegment.size) { latestSegment[it].sourcePoint.torqueNm },
            ),
            condition = WorkingCondition.classify(
                DoubleArray(latestSegment.size) { latestSegment[it].sourcePoint.angleDeg },
                DoubleArray(latestSegment.size) { latestSegment[it].sourcePoint.torqueNm },
                LongArray(latestSegment.size) { latestSegment[it].sourcePoint.timestampMillis },
            ),
        )
    }

    /**
     * 段内按接收间隔切成连续片，各片分别抽样后拼接；返回拼接后每个「间断后首点」的下标。
     * 先切再抽样：抽样后相邻两点的时间差不能当成间断（点多时一个桶就可能跨几秒）。
     */
    internal fun withGaps(points: List<ChartPoint>, maxPoints: Int): Pair<List<ChartPoint>, List<Int>> {
        val pieces = ArrayList<List<ChartPoint>>()
        var start = 0
        for (i in 1 until points.size) {
            if (points[i].receivedAtMillis - points[i - 1].receivedAtMillis > DASH_GAP_MILLIS) {
                pieces += points.subList(start, i)
                start = i
            }
        }
        pieces += points.subList(start, points.size)
        if (pieces.size == 1) return decimate(points, maxPoints) to emptyList()
        val out = ArrayList<ChartPoint>(min(points.size, maxPoints + pieces.size * 2))
        val gaps = ArrayList<Int>(pieces.size - 1)
        pieces.forEachIndexed { index, piece ->
            if (index > 0) gaps += out.size
            val budget = max(4, (maxPoints.toLong() * piece.size / points.size).toInt())
            out += decimate(piece, budget)
        }
        return out to gaps
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

    /** 记录序号优先用点上带的 [MeasuredPoint.seq]（结果帧也计入点数，缓冲推算会偏）；没有时才用 [fallbackSeq]。 */
    private fun MeasuredPoint.toChartPoint(fallbackSeq: Long) = ChartPoint(
        angle = sourcePoint.angleDeg,
        torque = sourcePoint.torqueNm,
        receivedAtMillis = sourcePoint.timestampMillis,
        seq = if (seq > 0L) seq else fallbackSeq,
    )
}

/** @property seq 记录内序号（从 1 起），与导出「序号」列一致。 */
data class ChartPoint(val angle: Double, val torque: Double, val receivedAtMillis: Long, val seq: Long = 0)

/** @property gapStarts [points] 里「数据间断后首点」的下标：与前一点之间超过 [TorqueAngleWindow.DASH_GAP_MILLIS] 没收到数据。 */
data class ChartSegment(
    val points: List<ChartPoint>,
    val isCurrent: Boolean,
    val sourceCount: Int,
    val gapStarts: List<Int> = emptyList(),
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
    /** 当前运行段的加载分析（转折候选 + 局部斜率），只作参考、未验证。 */
    val analysis: LoadingAnalysis.Result? = null,
    /** 当前运行段的工况划分（开发默认阈值，待真机标定）。 */
    val condition: WorkingCondition.Result? = null,
) {
    val isEmpty: Boolean get() = latest == null

    companion object {
        val EMPTY = TorqueAngleView(emptyList(), 0.0, MIN_SPAN, 0.0, 1.0, 0, 0, null, windowed = false, sampled = false)
        private const val MIN_SPAN = TorqueAngleWindow.MIN_SPAN_DEG
    }
}
