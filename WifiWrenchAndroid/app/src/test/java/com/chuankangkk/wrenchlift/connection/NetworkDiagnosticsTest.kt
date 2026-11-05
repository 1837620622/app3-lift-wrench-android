package com.chuankangkk.wrenchlift.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDiagnosticsTest {
    @Test
    fun ssidTextIsCleanedForFieldDisplay() {
        assertEquals("500banshou", NetworkDiagnostics.cleanSsid("\"500banshou\""))
        assertEquals("--", NetworkDiagnostics.cleanSsid("<unknown ssid>"))
        assertEquals("--", NetworkDiagnostics.cleanSsid(""))
        assertEquals("--", NetworkDiagnostics.cleanSsid(null))
    }

    @Test
    fun wrenchSubnetRequiresClientAddress() {
        assertTrue(NetworkDiagnostics.isWrenchSubnet("192.168.4.150"))
        assertFalse(NetworkDiagnostics.isWrenchSubnet("192.168.4.1"))
        assertFalse(NetworkDiagnostics.isWrenchSubnet("10.211.55.3"))
        assertFalse(NetworkDiagnostics.isWrenchSubnet("--"))
    }

    @Test
    fun discoveryCandidatesPreferUserTargetGatewayAndDefaultAp() {
        assertEquals(
            listOf("192.168.4.99", "192.168.4.254", "192.168.4.1"),
            NetworkDiagnostics.candidateHosts(
                localIp = "192.168.4.150",
                gatewayIp = "192.168.4.254",
                targetHost = "192.168.4.99",
            ),
        )
    }

    @Test
    fun protocolPortsDoNotTreatHttpConfigAsDataPort() {
        assertEquals(
            listOf(1234, 7888, 8899, 9000, 10001),
            NetworkDiagnostics.candidateProtocolPorts(1234),
        )
        assertFalse(NetworkDiagnostics.candidateProtocolPorts(7888).contains(80))
        assertFalse(NetworkDiagnostics.candidateProtocolPorts(7888).contains(8080))
    }

    @Test
    fun subnetScanIsLimitedToWrenchNetwork() {
        assertEquals("192.168.4", NetworkDiagnostics.subnetPrefix("192.168.4.150"))
        assertEquals(null, NetworkDiagnostics.subnetPrefix("192.168.5.150"))
        assertEquals(null, NetworkDiagnostics.subnetPrefix("10.211.55.3"))
    }
}
