package com.chuankangkk.wrenchlift.connection

import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveConnectorTest {
    private val connector = AdaptiveConnector(
        connectionFactory = { TcpWrenchClient(connectTimeoutMillis = 800, heartbeatIntervalMillis = 60_000) },
        handshakeTimeoutMillis = 700,
    )

    @Test fun defaultEndpointIsTriedFirstAndHandshakeNeedsRealProtocolFrame() = runBlocking {
        ScriptedWrenchServer().use { wrench ->
            val outcome = connector.probe(
                primary = listOf(wrench.endpoint),
                candidates = { error("首个端点握手成功后不应再找候选") },
                subnetScan = { error("不应扫子网") },
            )
            assertTrue(outcome is ProbeOutcome.Ready)
            outcome as ProbeOutcome.Ready
            assertEquals(wrench.endpoint, outcome.endpoint)
            assertEquals(WrenchPacket.FUNC_REPLY_SN, outcome.firstFrame.functionCode)
            // 握手内容：心跳 0x33 + 查 SN 0x25。
            assertEquals(listOf(WrenchPacket.FUNC_HEARTBEAT, WrenchPacket.FUNC_QUERY_SN), wrench.functionCodes().take(2))
            outcome.connection.disconnect()
        }
    }

    @Test fun changedPortIsFoundThroughCandidatesWhenDefaultRefuses() = runBlocking {
        val closedDefault = ScriptedWrenchServer.closedEndpoint()
        ScriptedWrenchServer().use { moved ->
            var candidateCalls = 0
            val outcome = connector.probe(
                primary = listOf(closedDefault),
                candidates = { tried ->
                    candidateCalls += 1
                    assertTrue(closedDefault in tried)
                    listOf(moved.endpoint)
                },
                subnetScan = { emptyList() },
            )
            assertEquals(1, candidateCalls)
            assertEquals(moved.endpoint, (outcome as ProbeOutcome.Ready).endpoint)
            assertEquals(listOf(closedDefault, moved.endpoint), outcome.tried)
            outcome.connection.disconnect()
        }
    }

    @Test fun lastGoodIsReusedBeforeDefault() = runBlocking {
        ScriptedWrenchServer().use { cached ->
            ScriptedWrenchServer().use { default ->
                val outcome = connector.probe(
                    primary = EndpointPlanner.primary(cached.endpoint).map { if (it == WrenchEndpoint.DEFAULT) default.endpoint else it },
                    candidates = { emptyList() },
                    subnetScan = { emptyList() },
                )
                assertEquals(cached.endpoint, (outcome as ProbeOutcome.Ready).endpoint)
                assertEquals(0, default.accepted.get())
                outcome.connection.disconnect()
            }
        }
    }

    @Test fun silentTcpPortIsNotTreatedAsHandshakeWhenARealWrenchAnswersLater() = runBlocking {
        ScriptedWrenchServer(replies = false).use { silent ->
            ScriptedWrenchServer().use { wrench ->
                val outcome = connector.probe(
                    primary = listOf(silent.endpoint),
                    candidates = { emptyList() },
                    subnetScan = { listOf(wrench.endpoint) },
                )
                assertEquals(wrench.endpoint, (outcome as ProbeOutcome.Ready).endpoint)
                outcome.connection.disconnect()
            }
        }
    }

    @Test fun onlySilentPortFallsBackToWaitingForFirstFrame() = runBlocking {
        ScriptedWrenchServer(replies = false).use { silent ->
            val outcome = connector.probe(
                primary = listOf(ScriptedWrenchServer.closedEndpoint(), silent.endpoint),
                candidates = { emptyList() },
                subnetScan = { emptyList() },
            )
            assertTrue(outcome is ProbeOutcome.TcpOnly)
            assertEquals(silent.endpoint, (outcome as ProbeOutcome.TcpOnly).endpoint)
            assertTrue(outcome.connection.connectionState.value.isOnline)
            outcome.connection.disconnect()
        }
    }

    @Test fun nothingReachableFails() = runBlocking {
        val outcome = connector.probe(
            primary = listOf(ScriptedWrenchServer.closedEndpoint()),
            candidates = { emptyList() },
            subnetScan = { emptyList() },
        )
        assertTrue(outcome is ProbeOutcome.Failed)
        assertFalse(outcome.tried.isEmpty())
    }
}
