package com.chuankangkk.wrenchlift.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** 最新协议.docx 中尚未覆盖的报文示例与 0x05/0x50/0x51 字段布局。 */
class ProtocolCoverageTest {
    private fun parse(hex: String): WrenchPacket = WrenchPacketParser().append(HexUtils.hexToBytes(hex)).single()

    @Test fun docxAckTimeRequestAndSnExamplesParse() {
        val ack = AckDecoder.decode(parse("C5 C5 01 06 FF FF 01 00 90"))
        assertTrue(ack.isAck)
        assertEquals(WrenchPacket.FUNC_TIME_REQUEST, parse("C5 C5 01 21 FF FF 01 01 AC").functionCode)
        assertEquals("1234567890", DeviceSnDecoder.decode(parse("C5 C5 01 26 FF FF 05 12 34 56 78 90 58")).sn)
    }

    @Test fun docxTimeSyncExampleMatchesBuilder() {
        val packet = WrenchPacketBuilder.timeSync(LocalDateTime.of(2024, 9, 30, 10, 1, 8))
        assertEquals("C5 C5 01 22 FF FF 07 07 E8 09 1E 0A 01 08 DB", packet.rawHex())
    }

    @Test fun statusFrameCarriesErrorCodeAndDirectionBit() {
        val forwardOk = DeviceStatusDecoder.decode(WrenchPacketBuilder.build(WrenchPacket.FUNC_STATUS, byteArrayOf(0x00, 0x00, 0x00, 0x00)))
        assertEquals(0, forwardOk.errorCode)
        assertEquals(false, forwardOk.reverseDirection)
        val reverseWithError = DeviceStatusDecoder.decode(WrenchPacketBuilder.build(WrenchPacket.FUNC_STATUS, byteArrayOf(0x00, 0x12, 0x00, 0x01)))
        assertEquals(0x12, reverseWithError.errorCode)
        assertEquals(true, reverseWithError.reverseDirection)
        assertEquals(null, DeviceStatusDecoder.decode(WrenchPacketBuilder.build(WrenchPacket.FUNC_STATUS, byteArrayOf())).errorCode)
    }

    @Test fun rc283fPulseParametersFollowDocxOffsetsAndTenthNewtonMetre() {
        val settings = Rc283fPulseSettings(
            mode = Rc283fPulseSettings.MODE_MP_HARD, targetTorqueNm = 123.4, k1 = 200, k2 = 40, k3 = 1500,
            boltStatusPercent = 80, errorPrecision = 5, boltCount = 12, dateTime = LocalDateTime.of(2024, 9, 30, 10, 1, 8),
        )
        val packet = WrenchPacketBuilder.pulseParameterSetRc283f(settings)
        assertEquals(WrenchPacket.FUNC_PULSE_PARAM_SET_RC283F, packet.functionCode)
        assertEquals(0x50, packet.payload.size)
        assertEquals(8 + 0x50, packet.toFrame().size)
        val p = packet.payload
        assertEquals(2024, readU16(p, 60))
        assertEquals(9, p[62].toInt())
        assertEquals(0x01, p[67].toInt())
        assertEquals(1234, readU16(p, 68)) // 123.4 N·m → 0.1 N·m 单位
        assertEquals(200, readU16(p, 70))
        assertEquals(40, readU16(p, 72))
        assertEquals(1500, readU16(p, 74))
        assertEquals(80, p[76].toInt())
        assertEquals(5, p[77].toInt())
        assertEquals(12, readU16(p, 78))
        // 组出来的帧能被本方解析器识别（校验和正确）。
        assertEquals(packet.rawHex(), parse(packet.rawHex()).rawHex())
    }

    @Test fun rc283fTorqueModeRequiresZeroK() {
        assertThrows(IllegalArgumentException::class.java) {
            Rc283fPulseSettings(mode = Rc283fPulseSettings.MODE_MS1_TORQUE, targetTorqueNm = 100.0, k1 = 60)
        }
        val ok = Rc283fPulseSettings(mode = Rc283fPulseSettings.MODE_MS1_TORQUE, targetTorqueNm = 6553.5)
        assertEquals(0xFFFF, readU16(WrenchPacketBuilder.pulseParameterSetRc283f(ok).payload, 68))
        assertThrows(IllegalArgumentException::class.java) { Rc283fPulseSettings(mode = 0, targetTorqueNm = 6553.6) }
    }

    @Test fun rchPulseParametersUseOneByteK2K3() {
        val settings = RchPulseSettings(
            mode = RchPulseSettings.MODE_MP_ANY, k1 = 120, k2 = 33, k3 = 7, setTorqueRaw = 900,
            boltStatusPercent = 100, errorPrecision = 3, boltCount = 4, dateTime = LocalDateTime.of(2024, 9, 30, 10, 1, 8),
        )
        val p = WrenchPacketBuilder.pulseParameterSetRch(settings).payload
        assertEquals(0x50, p.size)
        assertEquals(0x03, p[67].toInt())
        assertEquals(1, readU16(p, 68)) // 定点序号（任意参数模式下不生效，保留默认 1）
        assertEquals(120, readU16(p, 70))
        assertEquals(33, p[72].toInt())
        assertEquals(7, p[73].toInt())
        assertEquals(900, readU16(p, 74))
        assertEquals(100, p[76].toInt())
        assertEquals(4, readU16(p, 78))
        assertThrows(IllegalArgumentException::class.java) { RchPulseSettings(mode = RchPulseSettings.MODE_MP_HARD, presetIndex = 11) }
        assertFalse(runCatching { RchPulseSettings(mode = RchPulseSettings.MODE_MP_ANY, k1 = 301, k2 = 1, k3 = 1) }.isSuccess)
    }
}
