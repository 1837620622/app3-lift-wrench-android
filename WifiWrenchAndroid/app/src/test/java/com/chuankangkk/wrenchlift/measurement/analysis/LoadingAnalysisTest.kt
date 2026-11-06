package com.chuankangkk.wrenchlift.measurement.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

class LoadingAnalysisTest {
    /** 0x12 转角是整数度：每度上若干个点，扭矩为整数 N·m（与协议一致）。 */
    private fun run(
        maxAngle: Int,
        perDegree: Int = 4,
        noise: Double = 2.0,
        seed: Int = 7,
        torqueAt: (Double) -> Double,
    ): Pair<List<Double>, List<Double>> {
        val random = Random(seed)
        val angles = ArrayList<Double>()
        val torques = ArrayList<Double>()
        for (step in 0..maxAngle * perDegree) {
            val exact = step.toDouble() / perDegree
            angles += exact.toInt().toDouble()
            torques += (torqueAt(exact) + (random.nextDouble() * 2 - 1) * noise).roundToInt().coerceAtLeast(0).toDouble()
        }
        return angles to torques
    }

    private fun knee(x: Double, at: Double = 30.0, before: Double = 10.0, after: Double = 3.0) =
        if (x < at) before * x else before * at + after * (x - at)

    @Test fun clearSlopeChangeGivesCandidateNearTheKnee() {
        val (a, t) = run(80) { knee(it) }
        val result = LoadingAnalysis.analyze(a, t)
        val c = result.candidate!!
        assertEquals(30.0, c.angleDeg, 2.0)
        assertEquals(10.0, c.slopeBefore, 1.0)
        assertEquals(3.0, c.slopeAfter, 1.0)
        assertEquals(300.0, c.torqueNm, 20.0)
        assertTrue("残差改善应明显", c.improvement > 0.8)
        // 局部斜率：拐点前约 10、拐点后约 3。
        val early = result.slopes.filter { it.angleDeg in 8.0..20.0 }.map { it.slope }.average()
        val late = result.slopes.filter { it.angleDeg in 45.0..70.0 }.map { it.slope }.average()
        assertEquals(10.0, early, 1.5)
        assertEquals(3.0, late, 1.0)
    }

    @Test fun stiffeningCurveIsAlsoReportedAsCandidate() {
        val (a, t) = run(60) { knee(it, at = 25.0, before = 2.0, after = 9.0) }
        val c = LoadingAnalysis.analyze(a, t).candidate
        assertNotNull(c)
        assertEquals(25.0, c!!.angleDeg, 2.0)
        assertTrue(c.slopeAfter > c.slopeBefore)
    }

    @Test fun straightLineIsNotForcedIntoACandidate() {
        val (a, t) = run(80, noise = 4.0) { 6.0 * it }
        val result = LoadingAnalysis.analyze(a, t)
        assertNull(result.candidate)
        val reason = (result.outcome as LoadingAnalysis.NotFound).reason
        assertTrue(reason, reason.startsWith("单直线已能解释"))
        assertTrue(result.slopes.isNotEmpty())
        assertEquals(6.0, result.slopes.map { it.slope }.average(), 0.5)
    }

    @Test fun freeTravelBeforeContactIsTrimmedSoTheContactKneeIsNotTheCandidate() {
        // 0–12° 空行程（扭矩≈0），12° 接触后 10 N·m/°，40° 处变缓到 3 N·m/°。
        val (a, t) = run(90, noise = 1.0) { x -> if (x < 12.0) 0.0 else knee(x - 12.0, at = 28.0) }
        val result = LoadingAnalysis.analyze(a, t)
        val c = result.candidate
        assertNotNull((result.outcome as? LoadingAnalysis.NotFound)?.reason, c)
        assertEquals(40.0, c!!.angleDeg, 2.5)
        assertTrue(result.loadStartDeg!! >= 11.0)
    }

    @Test fun unloadingAfterPeakIsIgnored() {
        val (a, t) = run(70) { knee(it) }
        // 峰值后扳手停转、扭矩回落：转角不变、扭矩降到 0。
        val angles = a + List(30) { 70.0 }
        val torques = t + List(30) { i -> (t.last() * (1 - i / 29.0)).coerceAtLeast(0.0) }
        val result = LoadingAnalysis.analyze(angles, torques)
        assertEquals(30.0, result.candidate!!.angleDeg, 2.0)
        assertEquals(70.0, result.loadEndDeg!!, 0.0)
    }

    @Test fun tooFewPointsOrTooSmallSpanIsNotIdentified() {
        val few = LoadingAnalysis.analyze(List(10) { it.toDouble() }, List(10) { it * 5.0 })
        assertTrue((few.outcome as LoadingAnalysis.NotFound).reason.startsWith("点数不足"))
        val (a, t) = run(5, perDegree = 10) { 20.0 * it }
        val narrow = LoadingAnalysis.analyze(a, t)
        assertTrue((narrow.outcome as LoadingAnalysis.NotFound).reason.startsWith("有效加载转角跨度太小"))
    }

    @Test fun angleBacktrackInsideLoadingIsNotJudged() {
        val (a, t) = run(60) { knee(it) }
        val angles = a.toMutableList()
        val mid = angles.size / 2
        for (i in mid until mid + 8) angles[i] = angles[i] - 6.0
        val result = LoadingAnalysis.analyze(angles, t)
        assertTrue((result.outcome as LoadingAnalysis.NotFound).reason.startsWith("加载段内转角回退"))
    }

    @Test fun torqueThatNeverRisesIsNotIdentified() {
        val result = LoadingAnalysis.analyze(List(40) { it.toDouble() }, List(40) { 0.0 })
        assertEquals("扭矩未上升", (result.outcome as LoadingAnalysis.NotFound).reason)
    }

    @Test fun detailIsOnlyBuiltOnRequestAndMatchesTheCandidate() {
        val (a, t) = run(80) { knee(it) }
        assertNull("实时界面不生成明细", LoadingAnalysis.analyze(a, t).detail)
        val result = LoadingAnalysis.analyze(a, t, withDetail = true)
        val detail = result.detail!!
        val c = result.candidate!!
        // 整数度 → 每度一箱；有效加载约 2..80°。
        assertTrue("bins ${detail.bins.size}", detail.bins.size in 70..81)
        assertEquals(detail.bins.size, detail.bins.count { it.count > 0 })
        assertTrue("端点箱无斜率", detail.bins.first().slope == null && detail.bins.last().slope == null)
        // 扫描里残差改善最大的位置就是候选，且与 best 一致。
        val top = detail.scan.maxBy { it.improvement }
        assertEquals(c.angleDeg, top.angleDeg, 0.0)
        assertEquals(c.angleDeg, detail.best!!.angleDeg, 0.0)
        assertEquals(c.improvement, detail.best!!.improvement, 1e-9)
        assertEquals(c.torqueNm, detail.best!!.at(c.angleDeg), 1e-9)
        // 单直线斜率在两段之间。
        assertTrue(detail.lineSlope!! in 3.0..10.0)
    }

    @Test fun rejectedRunStillReportsBestHingeForTuning() {
        val (a, t) = run(80, noise = 4.0) { 6.0 * it }
        val result = LoadingAnalysis.analyze(a, t, withDetail = true)
        assertNull(result.candidate)
        val best = result.detail!!.best!!
        assertTrue("直线上最佳两段改善很小：${best.improvement}", best.improvement < LoadingAnalysis.MIN_IMPROVEMENT)
        assertTrue(result.detail!!.scan.isNotEmpty())
    }

    @Test fun largeSpanIsBinnedSoWorkStaysBounded() {
        val (a, t) = run(3_000, perDegree = 2) { knee(it, at = 1_200.0, before = 0.5, after = 0.15) }
        val started = System.nanoTime()
        val result = LoadingAnalysis.analyze(a, t)
        val millis = (System.nanoTime() - started) / 1e6
        assertEquals(1_200.0, result.candidate!!.angleDeg, 20.0)
        assertTrue("slopes ${result.slopes.size}", result.slopes.size <= LoadingAnalysis.MAX_BINS + 1)
        assertTrue("耗时 $millis ms", millis < 2_000)
    }
}
