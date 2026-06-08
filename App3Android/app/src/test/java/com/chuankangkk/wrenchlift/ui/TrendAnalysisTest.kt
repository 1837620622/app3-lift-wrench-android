package com.chuankangkk.wrenchlift.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendAnalysisTest {
    @Test
    fun increasingSeriesIsReportedAsRising() {
        val values = List(10) { index ->
            index.toDouble() to index * 12.0
        }

        val result = analyzeTrend(values)

        assertEquals("持续上升", result.label)
        assertFalse(result.isStable)
    }

    @Test
    fun plateauSeriesIsReportedAsStable() {
        val values = buildList {
            repeat(8) { index -> add(index.toDouble() to index * 20.0) }
            repeat(12) { index -> add((index + 8).toDouble() to (160.0 + index * 0.2)) }
        }

        val result = analyzeTrend(values)

        assertEquals("平衡平台", result.label)
        assertTrue(result.isStable)
    }
}
