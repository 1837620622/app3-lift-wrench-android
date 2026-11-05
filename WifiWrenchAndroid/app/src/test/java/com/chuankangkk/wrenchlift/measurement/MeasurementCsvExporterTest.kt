package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint
import com.chuankangkk.wrenchlift.protocol.ResultFrame
import com.chuankangkk.wrenchlift.protocol.ResultKind
import com.chuankangkk.wrenchlift.protocol.ResultStatus
import com.chuankangkk.wrenchlift.protocol.PulseResultFrame
import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import com.chuankangkk.wrenchlift.connection.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementCsvExporterTest {
    @Test
    fun fileNameUsesSanitizedSlabPointAndTimestamp() {
        val state = MeasurementState(
            session = MeasurementSession(
                slabNo = "FZB/001",
                pointNo = "P:03",
            ),
        )

        val fileName = MeasurementCsvExporter.buildFileName(
            state = state,
            nowMillis = 1_700_000_000_000,
        )

        assertEquals("WifiWrench_FZB_001_P_03_20231115_061320.csv", fileName)
    }

    @Test
    fun csvExportsCurrentPointsWithBomChineseHeadersAndRawHex() {
        val state = MeasurementState(
            session = MeasurementSession(
                projectName = "地铁,浮置板",
                lineName = "一号线",
                sectionName = "左线",
                slabNo = "FZB-018",
                pointNo = "P03",
                operatorName = "张\"三",
                deviceSn = "SESSION-SN",
            ),
            host = "192.168.4.1",
            port = "7888",
            employeeId = "EMP001",
            deviceSn = "WRENCH-SN",
            currentWifiName = "WRENCH_AP",
            localIp = "192.168.4.23",
            gatewayIp = "192.168.4.1",
            currentBoltNo = 7,
            connectionState = ConnectionState.Connected("192.168.4.1", 7888),
            lastCurvePointReceiveTime = 1_700_000_001_234,
            resultStatus = "合格",
            warningLevel = WarningLevel.WATCH,
            warningMessage = "接近平衡",
            lastPacketFunction = WrenchPacket.FUNC_TORQUE_ANGLE,
            recentPoints = listOf(
                MeasuredPoint(
                    sourcePoint = TorqueAnglePoint(
                        torqueNm = 185.25,
                        angleRaw = 92,
                        angleDeg = 92.0,
                        timestampMillis = 1_700_000_001_234,
                    ),
                    boltNo = 7,
                    forceKn = 21.3456,
                    pressureMpa = 1.7788,
                    displacementMm = 1.2778,
                    warningLevel = WarningLevel.WATCH,
                    rawHex = "C5 C5 01 12 FF FF 10 00",
                ),
            ),
        )

        val csv = MeasurementCsvExporter.buildCsv(state)

        assertTrue(csv.startsWith("\uFEFF项目,线路,区间"))
        assertTrue(csv.contains("结果状态,数据来源,当前WiFi"))
        assertTrue(csv.contains("\"地铁,浮置板\""))
        assertTrue(csv.contains("\"张\"\"三\""))
        assertTrue(csv.contains("WRENCH-SN"))
        assertTrue(csv.contains("合格,实时回传,WRENCH_AP"))
        assertTrue(csv.contains("扭矩转角过程"))
        assertTrue(csv.contains("185.2500,92.0000,21.3456,1.7788,1.2778"))
        assertTrue(csv.contains("C5 C5 01 12 FF FF 10 00"))
    }

    @Test
    fun csvRejectsEmptyStateWithoutMeasurementData() {
        val state = MeasurementState(
            currentTorqueNm = 88.0,
            currentAngleDeg = 12.0,
            lastRawHex = "C5 C5 FF 26 FF FF",
            lastPacketFunction = WrenchPacket.FUNC_REPLY_SN,
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            MeasurementCsvExporter.buildCsv(state)
        }

        assertTrue(error.message?.contains("没有真实扳手测量数据") == true)
    }

    @Test
    fun csvExportsSummaryRowWhenFinalResultExists() {
        val state = MeasurementState(
            currentTorqueNm = 88.0,
            currentAngleDeg = 12.0,
            lastRawHex = "C5 C5 FF 15 FF FF",
            lastPacketFunction = WrenchPacket.FUNC_RESULT,
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

        val lines = MeasurementCsvExporter.buildCsv(state).trim().lines()

        assertEquals(2, lines.size)
        assertTrue(lines[1].contains(",0,0,"))
        assertTrue(lines[1].contains("88.0000,12.0000"))
        assertTrue(lines[1].contains("最终结果"))
        assertTrue(lines[1].contains("C5 C5 FF 15 FF FF"))
    }

    @Test
    fun csvRejectsIncompleteFinalResultWithoutActualValues() {
        val state = MeasurementState(
            lastPacketFunction = WrenchPacket.FUNC_RESULT,
            lastResult = ResultFrame(
                status = ResultStatus(0x00, ResultKind.OK, "合格"),
                employeeId = "EMP001",
                boltNo = 3,
                year = null,
                month = null,
                day = null,
                hour = null,
                minute = null,
                second = null,
                mode = 2,
                targetTorqueNm = 100.0,
                actualTorqueNm = null,
                targetAngleDeg = 20.0,
                actualAngleDeg = null,
                torqueUpperNm = null,
                torqueLowerNm = null,
                angleUpperDeg = null,
                angleLowerDeg = null,
                rawHex = "C5 C5 FF 15 FF FF",
            ),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            MeasurementCsvExporter.buildCsv(state)
        }

        assertTrue(error.message?.contains("没有真实扳手测量数据") == true)
    }

    @Test
    fun csvExportsSummaryRowWhenPulseResultHasActualValues() {
        val state = MeasurementState(
            currentTorqueNm = 12.5,
            currentAngleDeg = 30.0,
            lastRawHex = "C5 C5 FF 55 FF FF",
            lastPacketFunction = WrenchPacket.FUNC_PULSE_RESULT,
            lastPulseResult = PulseResultFrame(
                status = ResultStatus(0x00, ResultKind.OK, "合格"),
                employeeId = "EMP001",
                boltNo = 5,
                year = 2026,
                month = 6,
                day = 9,
                hour = 9,
                minute = 10,
                second = 0,
                mode = 2,
                targetTorqueNm = 15.0,
                actualTorqueNm = 12.5,
                actualAngleDeg = 30.0,
                rawHex = "C5 C5 FF 55 FF FF",
            ),
        )

        val lines = MeasurementCsvExporter.buildCsv(state).trim().lines()

        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("12.5000,30.0000"))
        assertTrue(lines[1].contains("特殊模式结果"))
        assertTrue(lines[1].contains("C5 C5 FF 55 FF FF"))
    }
}
