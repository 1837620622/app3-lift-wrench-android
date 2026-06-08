package com.chuankangkk.wrenchlift.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WrenchPacketParserTest {
    @Test
    fun checksumMatchesManualExample() {
        val frame = HexUtils.hexToBytes(EXAMPLE_0X12)
        assertEquals(0x97, Checksum.calculate(frame, 0, frame.lastIndex))
        assertTrue(Checksum.isValid(frame))
    }

    @Test
    fun splitPacketIsParsedAfterSecondChunk() {
        val frame = HexUtils.hexToBytes(EXAMPLE_0X12)
        val parser = WrenchPacketParser()

        assertEquals(0, parser.append(frame.copyOfRange(0, 11)).size)
        val packets = parser.append(frame.copyOfRange(11, frame.size))

        assertEquals(1, packets.size)
        assertEquals(WrenchPacket.FUNC_TORQUE_ANGLE, packets.single().functionCode)
    }

    @Test
    fun stickyPacketsAreParsedTogether() {
        val frame = HexUtils.hexToBytes(EXAMPLE_0X12)
        val packets = WrenchPacketParser().append(frame + frame)

        assertEquals(2, packets.size)
        assertEquals(listOf(0x12, 0x12), packets.map { it.functionCode })
    }

    @Test
    fun badChecksumIsDroppedAndNextFrameSurvives() {
        val good = HexUtils.hexToBytes(EXAMPLE_0X12)
        val bad = good.copyOf().also { it[it.lastIndex] = 0x00 }
        val packets = WrenchPacketParser().append(bad + good)

        assertEquals(1, packets.size)
        assertEquals(good.toHexString(), packets.single().rawHex())
    }

    @Test
    fun alternateA5HeaderIsAcceptedForFieldCompatibility() {
        val raw = HexUtils.hexToBytes("A5 A5 FF 04 FF FF 01 64 B0")
        val packet = WrenchPacketParser().append(raw).single()

        assertEquals(WrenchPacket.FUNC_BATTERY, packet.functionCode)
        assertEquals(100, BatteryDecoder.decodePercent(packet))
    }

    companion object {
        const val EXAMPLE_0X12 =
            "C5 C5 01 12 FF FF 20 " +
                "30 30 30 30 30 30 30 30 30 30 " +
                "00 01 " +
                "00 00 00 00 " +
                "00 10 00 02 " +
                "00 31 00 06 " +
                "00 42 00 0A " +
                "00 55 00 11 " +
                "97"
    }
}
