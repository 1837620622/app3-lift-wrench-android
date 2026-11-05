package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.protocol.ResultFrame
import com.chuankangkk.wrenchlift.protocol.ResultKind
import com.chuankangkk.wrenchlift.protocol.ResultStatus
import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementReadoutFormatterTest {
    @Test
    fun hidesZeroValuesBeforeRealMeasurementArrives() {
        val state = MeasurementState(
            currentTorqueNm = 0.0,
            currentAngleDeg = 0.0,
            receivedFrameCount = 0,
        )

        assertEquals("--", state.torqueReadoutText())
        assertEquals("--", state.angleReadoutText())
    }

    @Test
    fun hidesZeroValuesWhenOnlyDeviceFramesArrive() {
        val state = MeasurementState(
            currentTorqueNm = 0.0,
            currentAngleDeg = 0.0,
            receivedFrameCount = 3,
        )

        assertEquals("--", state.torqueReadoutText())
        assertEquals("--", state.angleReadoutText())
    }

    @Test
    fun showsValuesAfterProcessPointArrives() {
        val state = MeasurementState(
            currentTorqueNm = 185.25,
            currentAngleDeg = 92.0,
            recentPoints = listOf(samplePoint()),
        )

        assertEquals("185.3", state.torqueReadoutText())
        assertEquals("92.0", state.angleReadoutText())
    }

    @Test
    fun showsValuesAfterCompleteResultFrameArrives() {
        val state = MeasurementState(
            currentTorqueNm = 88.0,
            currentAngleDeg = 12.0,
            lastResult = ResultFrame(
                status = ResultStatus(0x00, ResultKind.OK, "合格"),
                employeeId = "EMP001",
                boltNo = 3,
                year = 2026,
                month = 6,
                day = 9,
                hour = 8,
                minute = 50,
                second = 0,
                mode = 2,
                targetTorqueNm = 100.0,
                actualTorqueNm = 88.0,
                targetAngleDeg = 20.0,
                actualAngleDeg = 12.0,
                torqueUpperNm = 110.0,
                torqueLowerNm = 80.0,
                angleUpperDeg = 25.0,
                angleLowerDeg = 5.0,
                rawHex = "C5 C5 FF 15 FF FF",
            ),
        )

        assertEquals("88.0", state.torqueReadoutText())
        assertEquals("12.0", state.angleReadoutText())
    }

    @Test
    fun exportReadinessExplainsWaitingState() {
        val state = MeasurementState()

        assertEquals("暂不能导出：等待真实扳手测量数据", state.exportReadinessText())
    }

    @Test
    fun exportReadinessExplainsFramesWithoutMeasurement() {
        val state = MeasurementState(receivedFrameCount = 3)

        assertEquals("暂不能导出：已收设备帧，等待真实扭矩/角度", state.exportReadinessText())
    }

    @Test
    fun exportReadinessExplainsRealProcessPoints() {
        val state = MeasurementState(recentPoints = listOf(samplePoint(), samplePoint()))

        assertEquals("可导出：2 个真实曲线点和原始 HEX", state.exportReadinessText())
    }

    @Test
    fun exportReadinessExplainsCompleteResultFrame() {
        val state = MeasurementState(lastResult = completeResultFrame())

        assertEquals("可导出：普通最终结果和原始 HEX", state.exportReadinessText())
    }

    @Test
    fun saveExportEvidenceUsesReadinessWhenDataExistsButNotSaved() {
        val state = MeasurementState(recentPoints = listOf(samplePoint()))

        assertEquals("可导出：1 个真实曲线点和原始 HEX", state.saveExportEvidenceText())
    }

    @Test
    fun saveExportEvidenceKeepsActualExportResult() {
        val state = MeasurementState(
            saveStatus = "CSV 已导出：下载/App3Exports/App3.csv",
            recentPoints = listOf(samplePoint()),
        )

        assertEquals("CSV 已导出：下载/App3Exports/App3.csv", state.saveExportEvidenceText())
    }

    @Test
    fun balancePointActionsAreDisabledWithoutRealPointsOrMarkers() {
        val state = MeasurementState()

        assertFalse(state.canConfirmEffectivePoint())
        assertFalse(state.canRemoveEffectivePoint())
    }

    @Test
    fun balancePointActionsReflectPointAndMarkerState() {
        val state = MeasurementState(
            recentPoints = listOf(samplePoint()),
            effectivePointCount = 1,
        )

        assertTrue(state.canConfirmEffectivePoint())
        assertTrue(state.canRemoveEffectivePoint())
    }

    private fun samplePoint(): MeasuredPoint = MeasuredPoint(
        sourcePoint = TorqueAnglePoint(
            torqueNm = 185.25,
            angleRaw = 92,
            angleDeg = 92.0,
            timestampMillis = 1_700_000_001_234,
        ),
        boltNo = 7,
        forceKn = null,
        pressureMpa = null,
        displacementMm = 0.0,
        warningLevel = WarningLevel.NORMAL,
        rawHex = "C5 C5 01 12 FF FF 10 00",
    )

    private fun completeResultFrame(): ResultFrame = ResultFrame(
        status = ResultStatus(0x00, ResultKind.OK, "合格"),
        employeeId = "EMP001",
        boltNo = 3,
        year = 2026,
        month = 6,
        day = 9,
        hour = 8,
        minute = 50,
        second = 0,
        mode = 2,
        targetTorqueNm = 100.0,
        actualTorqueNm = 88.0,
        targetAngleDeg = 20.0,
        actualAngleDeg = 12.0,
        torqueUpperNm = 110.0,
        torqueLowerNm = 80.0,
        angleUpperDeg = 25.0,
        angleLowerDeg = 5.0,
        rawHex = "C5 C5 FF 15 FF FF",
    )
}
