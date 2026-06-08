package com.chuankangkk.wrenchlift.connection

import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface WrenchConnection {
    val connectionState: StateFlow<ConnectionState>
    val incomingPackets: Flow<WrenchPacket>
    suspend fun connect(host: String, port: Int)
    suspend fun disconnect()
    suspend fun send(packet: WrenchPacket)
}
