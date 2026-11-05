package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint
import org.junit.Assert.*
import org.junit.Test

class CalibrationBoundaryTest {
    private fun point(torque: Double) = TorqueAnglePoint(torque, 0, 0.0, 0L)

    @Test fun outsideCalibrationRangeIsInvalidInsteadOfClamped() {
        val calculator = CalibrationForceCalculator(listOf(10.0 to 1.0, 100.0 to 10.0))
        assertNull(calculator.calculate(point(9.0), emptyList()).forceKn)
        assertNull(calculator.calculate(point(101.0), emptyList()).forceKn)
        assertEquals(5.0, calculator.calculate(point(50.0), emptyList()).forceKn!!, 1e-9)
    }

    @Test fun invalidCalibrationIsRejected() {
        val calculator = CalibrationForceCalculator(listOf(10.0 to 1.0, 10.0 to 2.0))
        assertThrows(IllegalArgumentException::class.java) { calculator.calculate(point(10.0), emptyList()) }
    }

    @Test fun frameTimeIsReceiveTimeNotInventedSamplingTime() {
        val packet = com.chuankangkk.wrenchlift.protocol.WrenchPacket(
            address = 1, functionCode = 0x12,
            payload = ByteArray(12) + byteArrayOf(0, 1, 0, 2, 0, 3, 0, 4),
        )
        val frame = com.chuankangkk.wrenchlift.protocol.TorqueAngleDecoder.decode(packet, 1000L)
        assertEquals(listOf(1000L, 1000L), frame.points.map { it.timestampMillis })
    }

    @Test fun streamingCsvPreservesMoreThanOneDisplayWindowAndEscapesFormulaText() {
        val metadata = MeasurementState(session = MeasurementSession(projectName = "=1+1"))
        val output = StringBuilder()
        MeasurementCsvExporter.writeHeader(output)
        repeat(1205) { i ->
            MeasurementCsvExporter.writePoint(output, metadata, i + 1,
                MeasuredPoint(TorqueAnglePoint(i.toDouble(), i, i.toDouble(), 1000L),
                    1, null, warningLevel = WarningLevel.WATCH, rawHex = "row-$i"))
        }
        val lines = output.lines().filter { it.isNotEmpty() }
        assertEquals(1206, lines.size)
        assertTrue(lines.last().contains("row-1204"))
        assertTrue(lines[1].startsWith("'=1+1,"))
        assertTrue(output.startsWith("\uFEFF"))
    }
}
