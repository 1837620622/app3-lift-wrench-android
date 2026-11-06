package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TorqueAngleWindowTest {
    private fun point(angle: Double, torque: Double, bolt: Int = 1, t: Long = 0L) =
        MeasuredPoint(TorqueAnglePoint(torque, angle.toInt(), angle, t), bolt, null, warningLevel = WarningLevel.WATCH, rawHex = "")

    @Test fun chartPointsCarryRecordSequenceMatchingExportRows() {
        // 缓冲区装着本记录第 1001～1080 点：两段运行（第二段转角回落），序号与导出「序号」列一致，抽样后也不变。
        val first = List(50) { point(it * 4.0, it * 3.0, t = it * 100L) }
        val second = List(30) { point(it * 2.0, it * 5.0 + 1, t = 6_000L + it * 100L) }
        val view = TorqueAngleWindow.view(first + second, firstSeq = 1_001)
        val old = view.segments.first { !it.isCurrent }.points
        val current = view.segments.first { it.isCurrent }.points
        assertEquals(1_001L, old.first().seq)
        assertEquals(1_050L, old.last().seq)
        assertEquals(1_051L, current.first().seq)
        assertEquals(1_080L, current.last().seq)
        assertEquals(1_080L, view.latest!!.seq)
        assertEquals(1_080L, view.currentPeak!!.seq) // 第二段扭矩单调增，峰值是末点
        val sampled = TorqueAngleWindow.view(List(2_000) { point(it * 0.1, (it % 97).toDouble()) }, maxDrawPointsPerSegment = 100, firstSeq = 1)
        val pts = sampled.segments.single().points
        assertTrue(pts.zipWithNext().all { (a, b) -> b.seq > a.seq })
        pts.forEach { assertEquals("抽样点序号与角度对应", it.seq - 1, Math.round(it.angle * 10)) }
    }

    @Test fun displayBufferIsNoLongerFiftyPointsButStaysBounded() {
        val many = List(5_000) { point(it.toDouble(), it.toDouble()) }
        val buffer = TorqueAngleWindow.append(emptyList(), many.take(120))
        assertEquals(120, buffer.size)
        val capped = TorqueAngleWindow.append(buffer, many)
        assertEquals(TorqueAngleWindow.MAX_BUFFER_POINTS, capped.size)
        assertEquals(4_999.0, capped.last().sourcePoint.torqueNm, 0.0)
    }

    @Test fun axisFollowsAngleSpanOfCurrentRun() {
        val run = List(200) { point(10.0 + it * 0.5, it * 2.0) }
        val view = TorqueAngleWindow.view(run)
        assertEquals(10.0, view.minAngle, 1e-9)
        assertEquals(109.5, view.maxAngle, 1e-9)
        assertEquals(200, view.shownPointCount)
        assertFalse(view.windowed)
    }

    @Test fun tinySpanGetsMinimumTenDegreeWindow() {
        val view = TorqueAngleWindow.view(listOf(point(3.0, 5.0), point(4.0, 6.0)))
        assertEquals(TorqueAngleWindow.MIN_SPAN_DEG, view.maxAngle - view.minAngle, 1e-9)
        assertTrue(view.minAngle >= 0.0)
    }

    @Test fun newRunStartsNewSegmentAndSegmentsAreNotJoined() {
        val first = List(50) { point(it * 4.0, it * 3.0, bolt = 1, t = it * 100L) }
        val second = List(30) { point(it * 2.0, it * 5.0, bolt = 1, t = 6_000L + it * 100L) } // 转角回落 → 新一次运行
        val segments = TorqueAngleWindow.segments(first + second)
        assertEquals(2, segments.size)
        val view = TorqueAngleWindow.view(first + second)
        assertEquals(2, view.segments.size)
        assertTrue(view.segments.last().isCurrent)
        assertFalse(view.segments.first().isCurrent)
    }

    @Test fun boltChangeAndLongGapSplitRuns() {
        val points = listOf(point(1.0, 1.0, 1, 0), point(2.0, 2.0, 2, 100), point(3.0, 3.0, 2, 20_000))
        assertEquals(3, TorqueAngleWindow.segments(points).size)
    }

    @Test fun veryLongRunShowsTrailingSpanOnly() {
        val run = List(2_000) { point(it * 1.0, it.toDouble()) }
        val view = TorqueAngleWindow.view(run)
        assertTrue(view.windowed)
        assertEquals(1_999.0 - TorqueAngleWindow.MAX_VISIBLE_SPAN_DEG, view.minAngle, 1e-9)
    }

    @Test fun decimationKeepsPeaksAndLastPoint() {
        val points = List(10_000) { ChartPoint(it.toDouble(), if (it == 4_321) 999.0 else (it % 50).toDouble(), 0L) }
        val reduced = TorqueAngleWindow.decimate(points, 600)
        assertTrue(reduced.size <= 601)
        assertTrue(reduced.any { it.torque == 999.0 })
        assertEquals(points.last(), reduced.last())
        assertEquals(reduced.sortedBy { it.angle }, reduced) // 保持原顺序
    }
}
