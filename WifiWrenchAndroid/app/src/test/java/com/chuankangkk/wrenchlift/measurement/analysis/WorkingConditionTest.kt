package com.chuankangkk.wrenchlift.measurement.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkingConditionTest {
    private val base = 1_762_300_000_000L

    /** 1 Hz 接收（每点 1000 ms），除非 [gapAt] 处额外加 [gapMillis]。 */
    private fun classify(
        angle: DoubleArray,
        torque: DoubleArray,
        gapAt: Int = -1,
        gapMillis: Long = 0,
    ): WorkingCondition.Result {
        val millis = LongArray(angle.size) { i -> base + i * 1_000L + if (i >= gapAt && gapAt >= 0) gapMillis else 0L }
        return WorkingCondition.classify(angle, torque, millis)
    }

    /** 空程 10 点（转角 0→18，扭矩 0）→ 加载 10 点（转角 18→28，扭矩 10→100）→ 停转保压 10 点 → 卸载 10 点（扭矩 100→10）。 */
    private fun tightening(): Pair<DoubleArray, DoubleArray> {
        val angle = DoubleArray(40)
        val torque = DoubleArray(40)
        for (i in 0 until 10) { angle[i] = i * 2.0; torque[i] = 0.0 }
        for (i in 10 until 20) { angle[i] = 18.0 + (i - 9); torque[i] = (i - 9) * 10.0 }
        for (i in 20 until 30) { angle[i] = 28.0; torque[i] = 100.0 }
        for (i in 30 until 40) { angle[i] = 28.0; torque[i] = 100.0 - (i - 29) * 10.0 }
        return angle to torque
    }

    @Test fun normalTighteningGoesFreeTravelLoadingStallUnloading() {
        val (angle, torque) = tightening()
        val result = classify(angle, torque)
        val phases = result.segments.map { it.phase }
        assertTrue("空程在前：$phases", phases.indexOf(WorkingCondition.Phase.FREE_TRAVEL) == 0)
        val load = phases.indexOf(WorkingCondition.Phase.LOADING)
        val stall = phases.indexOf(WorkingCondition.Phase.STALL)
        val unload = phases.indexOf(WorkingCondition.Phase.UNLOADING)
        assertTrue("加载在空程之后：$phases", load > 0)
        assertTrue("停转保压在加载之后：$phases", stall > load)
        assertTrue("卸载在保压之后：$phases", unload > stall)
        assertTrue("空程转角 ≈ 18°：${result.angleIn(WorkingCondition.Phase.FREE_TRAVEL)}", result.angleIn(WorkingCondition.Phase.FREE_TRAVEL) >= 16.0)
        assertTrue("没有打滑：${result.events}", result.count(WorkingCondition.EventKind.SLIP) == 0)
        assertTrue("不是空转", !result.noLoad)
        assertEquals(100.0, result.peakTorque, 1e-9)
        assertEquals(5.0, result.engageTorque, 1e-9)
    }

    @Test fun phaseAtFollowsSegmentOrder() {
        val (angle, torque) = tightening()
        val result = classify(angle, torque)
        assertEquals(WorkingCondition.Phase.FREE_TRAVEL, result.phaseAt(0))
        assertEquals(WorkingCondition.Phase.STALL, result.phaseAt(25))
        assertEquals(WorkingCondition.Phase.UNLOADING, result.phaseAt(38))
        assertNull(result.phaseAt(40))
    }

    @Test fun torqueDropWhileStillTurningIsSlipEvent() {
        val (angle, torque) = tightening()
        // 第 20 点：扭矩从 100 掉到 40，转角仍在走（不回退）→ 疑似打滑。
        for (i in 20 until 40) { torque[i] = 40.0; angle[i] = 28.0 + (i - 19) }
        val result = classify(angle, torque)
        val slips = result.events.filter { it.kind == WorkingCondition.EventKind.SLIP }
        assertEquals(1, slips.size)
        assertEquals(20, slips.single().index)
        assertTrue("降幅约 60%：${slips.single().value}", slips.single().value in 55.0..65.0)
        assertTrue("判读提示打滑：${result.verdict}", result.verdict.contains("疑似打滑 1 次"))
    }

    @Test fun sustainedBackTurnIsBacktrackPhaseAndOneEvent() {
        val (angle, torque) = tightening()
        // 第 20 点起转角每秒倒回 1°，连续 10 点，之后停住。
        for (i in 20 until 30) angle[i] = 28.0 - (i - 19)
        for (i in 30 until 40) angle[i] = 18.0
        val result = classify(angle, torque)
        val back = result.events.filter { it.kind == WorkingCondition.EventKind.BACKTRACK }
        assertEquals("连续回退只记一次", 1, back.size)
        assertEquals(20, back.single().index)
        assertEquals(1.0, back.single().value, 1e-9)
        assertTrue("回退阶段存在：${result.segments.map { it.phase }}", result.segments.any { it.phase == WorkingCondition.Phase.BACKOFF })
    }

    @Test fun dataGapIsEventWithSeconds() {
        val (angle, torque) = tightening()
        val result = classify(angle, torque, gapAt = 10, gapMillis = 4_000)
        val gaps = result.events.filter { it.kind == WorkingCondition.EventKind.GAP }
        assertEquals(1, gaps.size)
        assertEquals(10, gaps.single().index)
        assertEquals(5.0, gaps.single().value, 1e-9)
    }

    @Test fun rotatingWithoutLoadIsNoLoadNotLoading() {
        val angle = DoubleArray(30) { it * 2.0 }
        val torque = DoubleArray(30) { 1.0 + (it % 2) * 0.5 }
        val result = classify(angle, torque)
        assertTrue(result.noLoad)
        assertTrue(result.segments.all { it.phase == WorkingCondition.Phase.NO_LOAD })
        assertTrue(result.verdict.contains("空转"))
    }

    @Test fun idleIsNotCountedAsFreeTravel() {
        val angle = DoubleArray(20) { 5.0 }
        val torque = DoubleArray(20) { 0.0 }
        val result = classify(angle, torque)
        assertEquals(WorkingCondition.Phase.IDLE, result.current)
        assertEquals(0.0, result.angleIn(WorkingCondition.Phase.FREE_TRAVEL), 0.0)
    }

    @Test fun spinningOffAfterLoadingIsFreeSpin() {
        val (angle, torque) = tightening()
        // 加载后扭矩直接降到 0 但仍在转：脱开空转（同时也是一次扭矩骤降，计为疑似打滑）。
        for (i in 20 until 40) { torque[i] = 0.0; angle[i] = 28.0 + (i - 19) * 1.0 }
        val result = classify(angle, torque)
        assertTrue(result.segments.any { it.phase == WorkingCondition.Phase.FREE_SPIN })
        assertTrue(result.segments.none { it.phase == WorkingCondition.Phase.FREE_TRAVEL && it.start > 10 })
    }

    @Test fun shortPhasesAreMergedSoFlickerDoesNotBecomeSegments() {
        val (angle, torque) = tightening()
        // 停转段中间塞一个单点扭矩抖动（不足以判定卸载）：不应切出独立的小段。
        torque[25] = 95.0
        val result = classify(angle, torque)
        assertTrue("没有 1–2 点的碎段：${result.segments.map { it.points }}", result.segments.dropLast(1).all { it.points >= 3 })
    }

    @Test fun emptyInputIsEmptyResult() {
        val result = WorkingCondition.classify(DoubleArray(0), DoubleArray(0), LongArray(0))
        assertTrue(result.segments.isEmpty())
        assertEquals("无数据", result.verdict)
        assertNull(result.current)
    }
}
