package com.chuankangkk.wrenchlift.protocol

data class ResultFrame(
    val status: ResultStatus,
    val employeeId: String,
    val boltNo: Int,
    val year: Int?,
    val month: Int?,
    val day: Int?,
    val hour: Int?,
    val minute: Int?,
    val second: Int?,
    val mode: Int?,
    val targetTorqueNm: Double?,
    val actualTorqueNm: Double?,
    val targetAngleDeg: Double?,
    val actualAngleDeg: Double?,
    val torqueUpperNm: Double?,
    val torqueLowerNm: Double?,
    val angleUpperDeg: Double?,
    val angleLowerDeg: Double?,
    val rawHex: String,
)

enum class ResultKind {
    OK,
    NG,
    UNKNOWN,
}

data class ResultStatus(
    val code: Int?,
    val kind: ResultKind,
    val label: String,
)

object ResultFrameDecoder {
    fun decode(packet: WrenchPacket): ResultFrame {
        require(packet.functionCode == WrenchPacket.FUNC_RESULT) { "不是 0x15 运行结果帧" }
        val payload = packet.payload
        val status = mapStatus(packet.reservedHigh)

        return ResultFrame(
            status = status,
            employeeId = safeAscii(payload, 0, 10),
            boltNo = safeU16(payload, 10) ?: 0,
            year = safeU16(payload, 12),
            month = safeByte(payload, 14),
            day = safeByte(payload, 15),
            hour = safeByte(payload, 16),
            minute = safeByte(payload, 17),
            second = safeByte(payload, 18),
            mode = safeByte(payload, 19),
            targetTorqueNm = safeU16(payload, 20)?.toDouble(),
            actualTorqueNm = safeU16(payload, 22)?.toDouble(),
            targetAngleDeg = safeU16(payload, 24)?.toDouble(),
            actualAngleDeg = safeU16(payload, 26)?.toDouble(),
            torqueUpperNm = safeU16(payload, 28)?.toDouble(),
            torqueLowerNm = safeU16(payload, 30)?.toDouble(),
            angleUpperDeg = safeU16(payload, 32)?.toDouble(),
            angleLowerDeg = safeU16(payload, 34)?.toDouble(),
            rawHex = packet.rawHex(),
        )
    }

    fun mapStatus(code: Int): ResultStatus = when (code and 0xFF) {
        0x00 -> ResultStatus(code, ResultKind.OK, "合格")
        0x01 -> ResultStatus(code, ResultKind.OK, "扭矩到达")
        0x02 -> ResultStatus(code, ResultKind.OK, "位置到达")
        0x10 -> ResultStatus(code, ResultKind.NG, "不合格")
        0x11 -> ResultStatus(code, ResultKind.NG, "扭矩偏低")
        0x12 -> ResultStatus(code, ResultKind.NG, "扭矩偏高")
        0x13 -> ResultStatus(code, ResultKind.NG, "疑似打滑")
        0x14 -> ResultStatus(code, ResultKind.NG, "角度偏小")
        0x15 -> ResultStatus(code, ResultKind.NG, "角度偏大")
        0x16 -> ResultStatus(code, ResultKind.NG, "重复拧紧")
        else -> ResultStatus(code, ResultKind.UNKNOWN, "未知状态")
    }

    private fun safeByte(data: ByteArray, offset: Int): Int? =
        data.getOrNull(offset)?.let { it.toInt() and 0xFF }

    private fun safeU16(data: ByteArray, offset: Int): Int? =
        if (offset + 1 < data.size) readU16(data, offset) else null

    private fun safeAscii(data: ByteArray, offset: Int, length: Int): String {
        if (offset >= data.size) return ""
        val end = minOf(offset + length, data.size)
        return data.copyOfRange(offset, end).decodeToString().trimEnd('\u0000', ' ')
    }
}
