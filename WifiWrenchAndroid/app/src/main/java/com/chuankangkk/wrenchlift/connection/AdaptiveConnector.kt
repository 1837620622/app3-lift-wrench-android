package com.chuankangkk.wrenchlift.connection

import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import com.chuankangkk.wrenchlift.protocol.WrenchPacketBuilder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** 自适应探测结果。工人只看到「已连上扳手 / 连接失败」，细节只进高级诊断。 */
sealed interface ProbeOutcome {
    val tried: List<WrenchEndpoint>

    /** TCP 建链且收到合法 C 协议帧（握手成功），可写入 last-good。 */
    data class Ready(
        val endpoint: WrenchEndpoint,
        val connection: WrenchConnection,
        val firstFrame: WrenchPacket,
        override val tried: List<WrenchEndpoint>,
    ) : ProbeOutcome

    /** 只有 TCP 能连、扳手还没回话：保持连接等待，首个合法帧到达后才算握手成功。 */
    data class TcpOnly(
        val endpoint: WrenchEndpoint,
        val connection: WrenchConnection,
        override val tried: List<WrenchEndpoint>,
    ) : ProbeOutcome

    data class Failed(override val tried: List<WrenchEndpoint>) : ProbeOutcome
}

/**
 * 按 NET-07 顺序逐个尝试端点：TCP 建链后发送心跳 0x33 + 查 SN 0x25，
 * 在限定时间内收到任何合法 C 协议帧（校验通过）才算握手成功，不以 TCP SYN 成功为准。
 */
class AdaptiveConnector(
    private val connectionFactory: () -> WrenchConnection,
    private val handshakeTimeoutMillis: Long = 1_800L,
    private val onProgress: (WrenchEndpoint) -> Unit = {},
) {
    suspend fun probe(
        primary: List<WrenchEndpoint>,
        candidates: suspend (tried: Set<WrenchEndpoint>) -> List<WrenchEndpoint>,
        subnetScan: suspend (tried: Set<WrenchEndpoint>) -> List<WrenchEndpoint>,
        attach: suspend (WrenchConnection) -> Unit = {},
    ): ProbeOutcome {
        val tried = LinkedHashSet<WrenchEndpoint>()
        val tcpOnly = mutableListOf<WrenchEndpoint>()

        suspend fun attempt(endpoint: WrenchEndpoint): ProbeOutcome.Ready? {
            if (!tried.add(endpoint)) return null
            onProgress(endpoint)
            val connection = connectionFactory()
            attach(connection)
            connection.connect(endpoint.host, endpoint.port)
            if (connection.connectionState.value !is ConnectionState.Connected) {
                connection.disconnect()
                return null
            }
            val frame = handshake(connection, handshakeTimeoutMillis)
            if (frame != null) return ProbeOutcome.Ready(endpoint, connection, frame, tried.toList())
            tcpOnly += endpoint
            connection.disconnect()
            return null
        }

        for (endpoint in primary) attempt(endpoint)?.let { return it }
        for (endpoint in candidates(tried.toSet())) attempt(endpoint)?.let { return it }
        for (endpoint in subnetScan(tried.toSet())) attempt(endpoint)?.let { return it }

        // 没有端点回话：退回到优先级最高、TCP 能连上的端点，保持连接等待扳手首帧。
        for (endpoint in tcpOnly) {
            val connection = connectionFactory()
            attach(connection)
            connection.connect(endpoint.host, endpoint.port)
            if (connection.connectionState.value is ConnectionState.Connected) {
                return ProbeOutcome.TcpOnly(endpoint, connection, tried.toList())
            }
            connection.disconnect()
        }
        return ProbeOutcome.Failed(tried.toList())
    }

    companion object {
        /** 先订阅再发送，避免应答在订阅前到达被漏掉。返回首个合法帧，超时返回 null。 */
        suspend fun handshake(connection: WrenchConnection, timeoutMillis: Long): WrenchPacket? = coroutineScope {
            val firstFrame = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(timeoutMillis) { connection.incomingPackets.first() }
            }
            val sent = runCatching {
                connection.send(WrenchPacketBuilder.heartbeat())
                connection.send(WrenchPacketBuilder.querySn())
            }.isSuccess
            if (!sent) {
                firstFrame.cancel()
                null
            } else {
                firstFrame.await()
            }
        }
    }
}
