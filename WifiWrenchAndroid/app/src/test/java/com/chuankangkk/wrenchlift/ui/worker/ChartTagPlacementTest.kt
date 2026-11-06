package com.chuankangkk.wrenchlift.ui.worker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 末点数值标签避让曲线用的几何判断。 */
class ChartTagPlacementTest {
    @Test fun segmentCrossingRectIsDetectedEvenWhenBothEndsAreOutside() {
        // 长斜线穿过矩形中部（采样稀疏时一段线可能很长）。
        assertTrue(segmentHitsRect(0f, 0f, 100f, 100f, 40f, 40f, 60f, 60f))
        assertTrue(segmentHitsRect(0f, 50f, 100f, 50f, 40f, 40f, 60f, 60f))
    }

    @Test fun segmentBesideRectIsNotAHit() {
        assertFalse(segmentHitsRect(0f, 0f, 100f, 0f, 40f, 40f, 60f, 60f))
        assertFalse(segmentHitsRect(0f, 100f, 30f, 70f, 40f, 40f, 60f, 60f))
        assertFalse(segmentHitsRect(70f, 0f, 70f, 100f, 40f, 40f, 60f, 60f))
    }

    @Test fun countsEachSegmentOfEveryLine() {
        val rising = floatArrayOf(0f, 100f, 50f, 50f, 100f, 0f)
        val flat = floatArrayOf(0f, 90f, 100f, 90f)
        // 折点 (50,50) 落在矩形里：前后两段都算压线。
        assertEquals(2, linesHitting(listOf(rising, flat), 40f, 40f, 60f, 60f))
        assertEquals(2, linesHitting(listOf(rising, flat), 0f, 85f, 5f, 95f))
        assertEquals(0, linesHitting(listOf(rising, flat), 80f, 60f, 95f, 80f))
    }
}

/** 平板整体放大、手机不动。 */
class LargeScreenScaleTest {
    @Test fun phonesKeepNativeScale() {
        assertEquals(1f, largeScreenScale(914, 411), 0f)
        assertEquals(1f, largeScreenScale(800, 360), 0f)
        assertEquals(1f, largeScreenScale(640, 288), 0f)
    }

    @Test fun tabletsScaleUpButNoMoreThan130Percent() {
        assertEquals(1.3f, largeScreenScale(1280, 800), 0.001f)
        assertEquals(1f, largeScreenScale(960, 600), 0.001f)
        val s = largeScreenScale(1024, 600)
        assertTrue(s in 1f..1.3f)
    }
}
