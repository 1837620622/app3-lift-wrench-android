package com.chuankangkk.wrenchlift.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class TorqueAngleDecoderTest {
    @Test
    fun manualTorqueAngleExampleParsesFivePoints() {
        val packet = WrenchPacketParser()
            .append(HexUtils.hexToBytes(WrenchPacketParserTest.EXAMPLE_0X12))
            .single()

        val frame = TorqueAngleDecoder.decode(packet, nowMillis = 1000L)

        assertEquals("0000000000", frame.employeeId)
        assertEquals(1, frame.boltNo)
        assertEquals(5, frame.points.size)
        assertEquals(
            listOf(
                0.0 to 0.0,
                16.0 to 2.0,
                49.0 to 6.0,
                66.0 to 10.0,
                85.0 to 17.0,
            ),
            frame.points.map { it.torqueNm to it.angleDeg },
        )
        assertEquals(listOf(1000L, 1001L, 1002L, 1003L, 1004L), frame.points.map { it.timestampMillis })
    }
}
