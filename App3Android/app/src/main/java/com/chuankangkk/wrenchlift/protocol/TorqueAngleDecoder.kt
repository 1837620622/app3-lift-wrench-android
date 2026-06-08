package com.chuankangkk.wrenchlift.protocol

data class TorqueAngleFrame(
    val employeeId: String,
    val boltNo: Int,
    val points: List<TorqueAnglePoint>,
    val rawHex: String,
)

data class TorqueAnglePoint(
    val torqueNm: Double,
    val angleRaw: Int,
    val angleDeg: Double,
    val timestampMillis: Long,
)

object TorqueAngleDecoder {
    fun decode(packet: WrenchPacket, nowMillis: Long = System.currentTimeMillis()): TorqueAngleFrame {
        require(packet.functionCode == WrenchPacket.FUNC_TORQUE_ANGLE) { "不是 0x12 扭矩角度帧" }
        val payload = packet.payload
        require(payload.size >= 12) { "0x12 数据长度至少需要 12 字节" }
        require((payload.size - 12) % 4 == 0) { "0x12 点位数据长度必须是 4 字节整数倍" }

        val employeeId = fixedAscii(payload, 0, 10)
        val boltNo = readU16(payload, 10)
        val points = mutableListOf<TorqueAnglePoint>()
        var offset = 12
        var index = 0
        while (offset + 3 < payload.size) {
            val torqueRaw = readU16(payload, offset)
            val angleRaw = readU16(payload, offset + 2)
            points += TorqueAnglePoint(
                torqueNm = torqueRaw.toDouble(),
                angleRaw = angleRaw,
                // 说明书和旧版实测均按整数角度解析。若厂家后续确认需除以 10，只需替换此处显示换算。
                angleDeg = angleRaw.toDouble(),
                timestampMillis = nowMillis + index,
            )
            offset += 4
            index += 1
        }
        return TorqueAngleFrame(employeeId, boltNo, points, packet.rawHex())
    }
}

internal fun readU16(data: ByteArray, offset: Int): Int =
    ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

internal fun fixedAscii(data: ByteArray, offset: Int, length: Int): String =
    data.copyOfRange(offset, offset + length)
        .decodeToString()
        .trimEnd('\u0000', ' ')
