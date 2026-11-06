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

    /** 一次读到超过 4 KB（例如积压后一起到达）：先解析再限长，帧一个不丢。 */
    @Test
    fun largeChunkOverBufferLimitKeepsEveryFrame() {
        val frame = HexUtils.hexToBytes(EXAMPLE_0X12)
        val chunk = ByteArray(0).let { var all = it; repeat(150) { all += frame }; all }
        assertTrue(chunk.size > 4096)
        assertEquals(150, WrenchPacketParser().append(chunk).size)
    }

    /** 假帧头 + 大段垃圾被逐字节跳过，之后跨两块到达的真帧照样拼回。 */
    @Test
    fun garbageIsCappedButFollowingSplitFrameSurvives() {
        val parser = WrenchPacketParser()
        val frame = HexUtils.hexToBytes(EXAMPLE_0X12)
        // 一个声称 255 字节的假帧头让解析器等待，后面跟 5000 字节垃圾。
        val junk = byteArrayOf(0xC5.toByte(), 0xC5.toByte(), 0x01, 0x12, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()) + ByteArray(5_000) { 0x11 }
        assertEquals(0, parser.append(junk).size)
        assertEquals(0, parser.append(frame.copyOfRange(0, 20)).size)
        assertEquals(1, parser.append(frame.copyOfRange(20, frame.size)).size)
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
