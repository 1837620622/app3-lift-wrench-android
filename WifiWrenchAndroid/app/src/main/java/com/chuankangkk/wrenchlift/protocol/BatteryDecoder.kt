package com.chuankangkk.wrenchlift.protocol

object BatteryDecoder {
    fun decodePercent(packet: WrenchPacket): Int? {
        if (packet.functionCode != WrenchPacket.FUNC_BATTERY || packet.payload.isEmpty()) return null
        return (packet.payload[0].toInt() and 0xFF).coerceIn(0, 100)
    }
}
