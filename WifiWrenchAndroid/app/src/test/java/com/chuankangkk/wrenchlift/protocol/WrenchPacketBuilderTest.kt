package com.chuankangkk.wrenchlift.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WrenchPacketBuilderTest {
    @Test
    fun heartbeatFrameMatchesFieldProbe() {
        val packet = WrenchPacketBuilder.heartbeat()

        assertEquals("C5 C5 01 33 FF FF 01 00 BD", packet.rawHex())
        assertTrue(Checksum.isValid(packet.toFrame()))
    }

    @Test
    fun resultAckFrameMatchesProtocolProbe() {
        val packet = WrenchPacketBuilder.resultAck()

        assertEquals("C5 C5 01 17 FF FF 01 00 A1", packet.rawHex())
        assertTrue(Checksum.isValid(packet.toFrame()))
    }

    @Test
    fun querySnFrameMatchesFieldProbe() {
        val packet = WrenchPacketBuilder.querySn()

        assertEquals("C5 C5 01 25 FF FF 05 00 00 00 00 00 B3", packet.rawHex())
        assertTrue(Checksum.isValid(packet.toFrame()))
    }

    @Test
    fun remoteAndRunCommandsUseExpectedPayloads() {
        assertEquals("C5 C5 01 11 FF FF 01 01 9C", WrenchPacketBuilder.enableRemoteControl().rawHex())
        assertEquals("C5 C5 01 11 FF FF 01 00 9B", WrenchPacketBuilder.disableRemoteControl().rawHex())
        assertEquals("C5 C5 01 01 FF FF 01 01 8C", WrenchPacketBuilder.startForward().rawHex())
        assertEquals("C5 C5 01 01 FF FF 01 02 8D", WrenchPacketBuilder.startReverse().rawHex())
        assertEquals("C5 C5 01 01 FF FF 01 00 8B", WrenchPacketBuilder.stop().rawHex())
    }

    @Test
    fun m1ParameterSetMatchesManualExample() {
        val packet = WrenchPacketBuilder.parameterSet(
            WifiWrenchParameterSettings(
                productId = "0000000000",
                stationName = "11111111111111111111",
                employeeId = "2222222222",
                toolSerial = "0000000000",
                controllerSerial = "3333333333",
                dateTime = LocalDateTime.of(2024, 9, 30, 10, 1, 8),
                mode = WifiWrenchParameterSettings.MODE_M1,
                targetTorqueNm = 500,
                torqueLowerNm = 450,
                torqueUpperNm = 550,
                targetAngleDeg = 0.0,
                angleUpperDeg = 360.0,
                angleLowerDeg = 1.0,
            ),
        )

        assertEquals(
            "C5 C5 01 10 FF FF 50 30 30 30 30 30 30 30 30 30 30 " +
                "31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 " +
                "32 32 32 32 32 32 32 32 32 32 " +
                "30 30 30 30 30 30 30 30 30 30 " +
                "33 33 33 33 33 33 33 33 33 33 " +
                "07 E8 09 1E 0A 01 08 01 01 F4 01 C2 02 26 00 00 0E 10 00 0A A1",
            packet.rawHex(),
        )
        assertTrue(Checksum.isValid(packet.toFrame()))
    }

    @Test
    fun m2ParameterSetMatchesManualExample() {
        val packet = WrenchPacketBuilder.parameterSet(
            WifiWrenchParameterSettings(
                productId = "0000000000",
                stationName = "11111111111111111111",
                employeeId = "2222222222",
                toolSerial = "0000000000",
                controllerSerial = "3333333333",
                dateTime = LocalDateTime.of(2024, 9, 30, 10, 1, 8),
                mode = WifiWrenchParameterSettings.MODE_M2,
                targetTorqueNm = 180,
                torqueLowerNm = 200,
                torqueUpperNm = 500,
                targetAngleDeg = 90.0,
                angleUpperDeg = 360.0,
                angleLowerDeg = 1.0,
            ),
        )

        assertEquals(
            "C5 C5 01 10 FF FF 50 30 30 30 30 30 30 30 30 30 30 " +
                "31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 31 " +
                "32 32 32 32 32 32 32 32 32 32 " +
                "30 30 30 30 30 30 30 30 30 30 " +
                "33 33 33 33 33 33 33 33 33 33 " +
                "07 E8 09 1E 0A 01 08 02 00 B4 00 C8 01 F4 03 84 0E 10 00 0A BA",
            packet.rawHex(),
        )
        assertTrue(Checksum.isValid(packet.toFrame()))
    }
    /** 协议：上下限填 0 表示不受控；两项都填时下限大于上限会让扳手一启动就停机，App 侧拦截。 */
    @Test
    fun parameterLimitsMustBeOrderedUnlessZero() {
        fun settings(tl: Int, tu: Int, al: Double, au: Double) = WifiWrenchParameterSettings(
            mode = WifiWrenchParameterSettings.MODE_M2, targetTorqueNm = 180, torqueLowerNm = tl, torqueUpperNm = tu,
            targetAngleDeg = 90.0, angleUpperDeg = au, angleLowerDeg = al,
        )
        settings(200, 500, 1.0, 360.0)
        settings(0, 500, 0.0, 360.0)
        settings(200, 0, 1.0, 0.0)
        val torque = runCatching { settings(500, 200, 1.0, 360.0) }.exceptionOrNull()
        assertEquals("扭矩下限不能大于上限", torque?.message)
        val angle = runCatching { settings(200, 500, 360.0, 1.0) }.exceptionOrNull()
        assertEquals("角度下限不能大于上限", angle?.message)
    }
}
