package com.chuankangkk.wrenchlift.measurement.analysis

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 单个运行段的加载分析：给「平衡点（临界分离）」研究用的**转折候选**和**局部斜率**，不是结论。
 *
 * 依据 docs/项目背景与开发要求.md §11.2：
 * 1. 先取有效加载段：从扭矩首次达到峰值的 [START_FRACTION] 起，到峰值扭矩点止（去掉空行程和卸载）；
 * 2. 按转角分箱（0x12 转角为整数度，一个角度上常有多个点）；
 * 3. 连续两段直线 `T(x) = β0 + m1·x + (m2−m1)·max(0, x−c)` 遍历候选 c，两侧各留足样本与转角跨度；
 * 4. 与单直线比较残差平方和，改善不够、前后斜率差别不大、转角回退、候选贴边、转折后扭矩不再上升时
 *    一律返回「未识别」并写明原因，不强行给点。
 *
 * 阈值均为开发默认值，**未经试验标定，不是工程标准**；候选只能写「候选 / 未验证」，不能写成平衡点。
 */
object LoadingAnalysis {
    /** 有效加载段起点：扭矩首次 ≥ 峰值 × 该比例（且 ≥ 1 N·m）。 */
    const val START_FRACTION = 0.05
    const val MIN_POINTS = 20
    const val MIN_BINS = 10
    const val MIN_SPAN_DEG = 8.0
    /** 候选两侧至少各占有效转角跨度的比例。 */
    const val SIDE_FRACTION = 0.15
    const val MIN_SIDE_BINS = 4
    /** 两段直线相对单直线的残差平方和至少下降这么多。 */
    const val MIN_IMPROVEMENT = 0.5
    /** 前后斜率相对变化至少这么多。 */
    const val MIN_SLOPE_CHANGE = 0.3
    /** 加载段内转角回退超过此值（反转/打滑/重置）不判定。 */
    const val MAX_BACKTRACK_DEG = 3.0
    /** 分箱数上限：跨度很大时自动放宽箱宽，保证实时计算量可控。 */
    const val MAX_BINS = 400

    data class SlopePoint(val angleDeg: Double, val slope: Double)

    sealed interface Outcome

    /** 转折候选（未验证）。斜率单位 N·m/°。 */
    data class Candidate(
        val angleDeg: Double,
        val torqueNm: Double,
        val slopeBefore: Double,
        val slopeAfter: Double,
        /** 残差平方和相对单直线的下降比例，0..1。 */
        val improvement: Double,
        /** 两段直线在有效加载段两端的值，便于画拟合线。 */
        val fitStart: Pair<Double, Double>,
        val fitEnd: Pair<Double, Double>,
    ) : Outcome

    data class NotFound(val reason: String) : Outcome

    /** 分箱后的有效加载数据：箱内平均转角、平均扭矩、点数，以及该箱的局部斜率（端点箱为 null）。 */
    data class BinPoint(val angleDeg: Double, val torqueNm: Double, val count: Int, val slope: Double?)

    /** 两段直线在候选位置 c 的拟合结果：残差改善（0..1）与前后斜率。 */
    data class ScanPoint(val angleDeg: Double, val improvement: Double, val slopeBefore: Double, val slopeAfter: Double)

    /**
     * 两段直线在全部候选位置里残差最小的那个（不论是否过阈值）；被判「未识别」时也给出，方便调阈值。
     * [b0] + [slopeBefore]·x + ([slopeAfter] − [slopeBefore])·max(0, x − [angleDeg])。
     */
    data class BestHinge(val angleDeg: Double, val b0: Double, val slopeBefore: Double, val slopeAfter: Double, val improvement: Double) {
        fun at(x: Double) = b0 + slopeBefore * x + (slopeAfter - slopeBefore) * max(0.0, x - angleDeg)
    }

    /** 导出 / 调试用的计算过程（实时界面不需要，默认不生成）。 */
    data class Detail(
        val bins: List<BinPoint>,
        /** 单直线拟合 T = 截距 + 斜率·x。 */
        val lineIntercept: Double?,
        val lineSlope: Double?,
        val scan: List<ScanPoint>,
        val best: BestHinge?,
    )

    data class Result(
        val outcome: Outcome,
        /** 有效加载段内的局部斜率（按转角升序）。 */
        val slopes: List<SlopePoint>,
        val pointCount: Int,
        val peakTorqueNm: Double?,
        val angleAtPeakDeg: Double?,
        val loadStartDeg: Double?,
        val loadEndDeg: Double?,
        /** 仅 `analyze(..., withDetail = true)` 时有。 */
        val detail: Detail? = null,
    ) {
        val candidate: Candidate? get() = outcome as? Candidate

        companion object {
            fun none(reason: String, count: Int = 0) = Result(NotFound(reason), emptyList(), count, null, null, null, null)
        }
    }

    fun analyze(angles: List<Double>, torques: List<Double>, withDetail: Boolean = false): Result =
        analyze(angles.toDoubleArray(), torques.toDoubleArray(), min(angles.size, torques.size), withDetail)

    fun analyze(angle: DoubleArray, torque: DoubleArray, count: Int = min(angle.size, torque.size), withDetail: Boolean = false): Result {
        val n = count
        if (n < MIN_POINTS) return Result.none("点数不足（$n 点）", n)
        var peak = 0
        for (i in 1 until n) if (torque[i] > torque[peak]) peak = i
        val peakTorque = torque[peak]
        if (!(peakTorque > 0.0) || !peakTorque.isFinite()) return Result.none("扭矩未上升", n)
        val threshold = max(peakTorque * START_FRACTION, 1.0)
        var start = 0
        while (start < peak && torque[start] < threshold) start++
        val effective = peak - start + 1

        var maxSoFar = angle[start]
        var backtrack = 0.0
        for (i in start..peak) {
            maxSoFar = max(maxSoFar, angle[i])
            backtrack = max(backtrack, maxSoFar - angle[i])
        }
        val loadStart = angle[start]
        val loadEnd = maxSoFar
        var detail: Detail? = null
        fun result(outcome: Outcome, slopes: List<SlopePoint> = emptyList()) =
            Result(outcome, slopes, n, peakTorque, angle[peak], loadStart, loadEnd, detail)

        if (effective < MIN_POINTS) return result(NotFound("有效加载段点数不足（$effective 点）"))
        if (backtrack > MAX_BACKTRACK_DEG) return result(NotFound("加载段内转角回退 ${fmt(backtrack)}°（反转/打滑），不判定"))
        val bins = bin(angle, torque, start, peak)
        val span = bins.x.last() - bins.x.first()
        if (bins.size < MIN_BINS || span < MIN_SPAN_DEG) return result(NotFound("有效加载转角跨度太小（${fmt(span)}°）"))
        val slopes = localSlopes(bins, span)

        val single = fitLine(bins)
        val sse1 = single.sse + bins.within
        fun binPoints(): List<BinPoint> {
            val byAngle = slopes.associate { it.angleDeg to it.slope }
            return List(bins.size) { BinPoint(bins.x[it], bins.y[it], bins.w[it].toInt(), byAngle[bins.x[it]]) }
        }
        if (sse1 <= 1e-12 * max(1.0, bins.sumSquares)) {
            if (withDetail) detail = Detail(binPoints(), single.b0, single.b1, emptyList(), null)
            return result(NotFound("单直线已能解释"), slopes)
        }

        val side = max(SIDE_FRACTION * span, 0.0)
        var best: Hinge? = null
        val scan = if (withDetail) ArrayList<ScanPoint>(bins.size) else null
        for (k in MIN_SIDE_BINS until bins.size - MIN_SIDE_BINS) {
            val c = bins.x[k]
            if (c - bins.x.first() < side || bins.x.last() - c < side) continue
            val fit = fitHinge(bins, c) ?: continue
            scan?.add(ScanPoint(c, (1.0 - (fit.sse + bins.within) / sse1).coerceIn(0.0, 1.0), fit.b1, fit.b1 + fit.b2))
            if (best == null || fit.sse < best.sse) best = fit
        }
        if (withDetail) {
            val bestHinge = best?.let { BestHinge(it.c, it.b0, it.b1, it.b1 + it.b2, (1.0 - (it.sse + bins.within) / sse1).coerceIn(0.0, 1.0)) }
            detail = Detail(binPoints(), single.b0, single.b1, scan.orEmpty(), bestHinge)
        }
        val hinge = best ?: return result(NotFound("候选位置太靠两端"), slopes)
        val improvement = (1.0 - (hinge.sse + bins.within) / sse1).coerceIn(0.0, 1.0)
        val m1 = hinge.b1
        val m2 = hinge.b1 + hinge.b2
        val change = abs(m2 - m1) / max(max(abs(m1), abs(m2)), 1e-9)
        return when {
            improvement < MIN_IMPROVEMENT || change < MIN_SLOPE_CHANGE ->
                result(NotFound("单直线已能解释（残差改善 ${percent(improvement)}）"), slopes)
            m2 <= 0.0 -> result(NotFound("转折后扭矩不再上升（疑似停转/打滑）"), slopes)
            m1 <= 0.0 -> result(NotFound("转折前扭矩未上升（疑似空行程）"), slopes)
            else -> {
                val x0 = bins.x.first()
                val x1 = bins.x.last()
                fun fit(x: Double) = hinge.b0 + hinge.b1 * x + hinge.b2 * max(0.0, x - hinge.c)
                result(
                    Candidate(
                        angleDeg = hinge.c,
                        torqueNm = fit(hinge.c),
                        slopeBefore = m1,
                        slopeAfter = m2,
                        improvement = improvement,
                        fitStart = x0 to fit(x0),
                        fitEnd = x1 to fit(x1),
                    ),
                    slopes,
                )
            }
        }
    }

    /** 转角分箱：箱宽 1°，跨度大时放宽到不超过 [MAX_BINS] 个箱。按转角升序。 */
    internal class Bins(val x: DoubleArray, val y: DoubleArray, val w: DoubleArray, val within: Double, val sumSquares: Double) {
        val size: Int get() = x.size
    }

    internal fun bin(angle: DoubleArray, torque: DoubleArray, from: Int, to: Int): Bins {
        var lo = Double.POSITIVE_INFINITY
        var hi = Double.NEGATIVE_INFINITY
        for (i in from..to) { lo = min(lo, angle[i]); hi = max(hi, angle[i]) }
        val width = max(1.0, (hi - lo) / MAX_BINS)
        val sums = HashMap<Long, DoubleArray>()
        var sumSquares = 0.0
        for (i in from..to) {
            val key = floor((angle[i] - lo) / width + 0.5).toLong()
            val acc = sums.getOrPut(key) { DoubleArray(4) }
            acc[0] += angle[i]; acc[1] += torque[i]; acc[2] += torque[i] * torque[i]; acc[3] += 1.0
            sumSquares += torque[i] * torque[i]
        }
        val keys = sums.keys.sorted()
        val x = DoubleArray(keys.size)
        val y = DoubleArray(keys.size)
        val w = DoubleArray(keys.size)
        var within = 0.0
        keys.forEachIndexed { index, key ->
            val acc = sums.getValue(key)
            x[index] = acc[0] / acc[3]
            y[index] = acc[1] / acc[3]
            w[index] = acc[3]
            within += max(0.0, acc[2] - acc[1] * acc[1] / acc[3])
        }
        return Bins(x, y, w, within, sumSquares)
    }

    private class Line(val b0: Double, val b1: Double, val sse: Double)

    private fun fitLine(bins: Bins): Line {
        var sw = 0.0; var sx = 0.0; var sy = 0.0
        for (i in 0 until bins.size) { sw += bins.w[i]; sx += bins.w[i] * bins.x[i]; sy += bins.w[i] * bins.y[i] }
        val mx = sx / sw
        val my = sy / sw
        var sxx = 0.0; var sxy = 0.0
        for (i in 0 until bins.size) { val dx = bins.x[i] - mx; sxx += bins.w[i] * dx * dx; sxy += bins.w[i] * dx * (bins.y[i] - my) }
        val b1 = if (sxx > 0.0) sxy / sxx else 0.0
        val b0 = my - b1 * mx
        var sse = 0.0
        for (i in 0 until bins.size) { val r = bins.y[i] - b0 - b1 * bins.x[i]; sse += bins.w[i] * r * r }
        return Line(b0, b1, sse)
    }

    private class Hinge(val c: Double, val b0: Double, val b1: Double, val b2: Double, val sse: Double)

    /** 加权最小二乘：基函数 [1, x, max(0, x−c)]，3×3 正规方程高斯消元（以 c 为原点，循环内不分配对象）。 */
    private fun fitHinge(bins: Bins, c: Double): Hinge? {
        var s00 = 0.0; var s01 = 0.0; var s02 = 0.0; var s11 = 0.0; var s12 = 0.0; var s22 = 0.0
        var r0 = 0.0; var r1 = 0.0; var r2 = 0.0
        for (i in 0 until bins.size) {
            val w = bins.w[i]
            val x = bins.x[i] - c
            val h = if (x > 0.0) x else 0.0
            val wy = w * bins.y[i]
            s00 += w; s01 += w * x; s02 += w * h
            s11 += w * x * x; s12 += w * x * h; s22 += w * h * h
            r0 += wy; r1 += wy * x; r2 += wy * h
        }
        val solution = solve3(
            arrayOf(
                doubleArrayOf(s00, s01, s02, r0),
                doubleArrayOf(s01, s11, s12, r1),
                doubleArrayOf(s02, s12, s22, r2),
            ),
        ) ?: return null
        val d0 = solution[0]
        val b1 = solution[1]
        val b2 = solution[2]
        var sse = 0.0
        for (i in 0 until bins.size) {
            val x = bins.x[i] - c
            val r = bins.y[i] - (d0 + b1 * x + b2 * (if (x > 0.0) x else 0.0))
            sse += bins.w[i] * r * r
        }
        return Hinge(c, d0 - b1 * c, b1, b2, sse)
    }

    private fun solve3(m: Array<DoubleArray>): DoubleArray? {
        for (col in 0 until 3) {
            var pivot = col
            for (r in col + 1 until 3) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            if (abs(m[pivot][col]) < 1e-12) return null
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (r in 0 until 3) {
                if (r == col) continue
                val factor = m[r][col] / m[col][col]
                for (k in col until 4) m[r][k] -= factor * m[col][k]
            }
        }
        return DoubleArray(3) { m[it][3] / m[it][it] }
    }

    /**
     * 局部斜率 dT/dθ：每个箱取前后共 [window] 度内各箱做加权直线拟合；窗口取跨度的 1/8（4°–40°）。
     * 两端只有单侧数据的箱不出值，避免端点效应。
     */
    internal fun localSlopes(bins: Bins, span: Double): List<SlopePoint> {
        val window = (span / 8.0).coerceIn(4.0, 40.0)
        val half = window / 2.0
        val out = ArrayList<SlopePoint>(bins.size)
        var lo = 0
        var hi = 0
        for (k in 0 until bins.size) {
            val center = bins.x[k]
            while (bins.x[lo] < center - half) lo++
            while (hi + 1 < bins.size && bins.x[hi + 1] <= center + half) hi++
            if (lo == k || hi == k || hi - lo + 1 < 3) continue
            var sw = 0.0; var sx = 0.0; var sy = 0.0
            for (i in lo..hi) { sw += bins.w[i]; sx += bins.w[i] * bins.x[i]; sy += bins.w[i] * bins.y[i] }
            val mx = sx / sw
            val my = sy / sw
            var sxx = 0.0; var sxy = 0.0
            for (i in lo..hi) { val dx = bins.x[i] - mx; sxx += bins.w[i] * dx * dx; sxy += bins.w[i] * dx * (bins.y[i] - my) }
            if (sxx > 0.0) out += SlopePoint(center, sxy / sxx)
        }
        return out
    }

    private fun fmt(value: Double): String = if (abs(value - Math.rint(value)) < 0.05) "%.0f".format(Locale.ROOT, value) else "%.1f".format(Locale.ROOT, value)

    private fun percent(value: Double): String = "%.0f%%".format(Locale.ROOT, value * 100.0)
}
