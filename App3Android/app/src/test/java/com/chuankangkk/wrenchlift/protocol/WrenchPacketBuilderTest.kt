package com.chuankangkk.wrenchlift.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
