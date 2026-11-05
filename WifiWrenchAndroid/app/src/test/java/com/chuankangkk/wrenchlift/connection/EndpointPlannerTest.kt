package com.chuankangkk.wrenchlift.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPlannerTest {
    @Test fun firstSessionTriesFactoryDefaultFirst() {
        assertEquals(listOf(WrenchEndpoint("192.168.4.1", 7888)), EndpointPlanner.primary(lastGood = null))
    }

    @Test fun cachedEndpointGoesBeforeDefaultWithoutDuplicates() {
        val cached = WrenchEndpoint("192.168.4.1", 8899)
        assertEquals(listOf(cached, WrenchEndpoint.DEFAULT), EndpointPlanner.primary(cached))
        assertEquals(listOf(WrenchEndpoint.DEFAULT), EndpointPlanner.primary(WrenchEndpoint.DEFAULT))
    }

    @Test fun candidatesCoverGatewayManualAndKnownPortsButSkipPrimary() {
        val candidates = EndpointPlanner.candidates(
            lastGood = null,
            manual = WrenchEndpoint("192.168.4.20", 9100),
            gatewayIp = "192.168.4.254",
            localIp = "192.168.4.150",
        )
        assertEquals(WrenchEndpoint("192.168.4.20", 9100), candidates.first())
        assertFalse(WrenchEndpoint.DEFAULT in candidates)
        assertTrue(WrenchEndpoint("192.168.4.1", 8899) in candidates)
        assertTrue(WrenchEndpoint("192.168.4.254", 7888) in candidates)
        assertTrue(WrenchEndpoint("192.168.4.254", 10001) in candidates)
        assertFalse(candidates.any { it.host == "192.168.4.150" })
        assertEquals(candidates.size, candidates.distinct().size)
        assertFalse(candidates.any { it.port == 80 || it.port == 8080 })
    }

    @Test fun parseRejectsBlankAndOutOfRange() {
        assertNull(WrenchEndpoint.parse("", "7888"))
        assertNull(WrenchEndpoint.parse("192.168.4.1", "70000"))
        assertNull(WrenchEndpoint.parse("--", "7888"))
        assertEquals(WrenchEndpoint("192.168.4.1", 7888), WrenchEndpoint.parse(" 192.168.4.1 ", "7888"))
    }
}
