package com.chuankangkk.wrenchlift.protocol

data class WrenchPacket(
    val address: Int,
    val functionCode: Int,
    val payload: ByteArray,
    val reservedHigh: Int = 0xFF,
    val reservedLow: Int = 0xFF,
    val raw: ByteArray = byteArrayOf(),
    val checksum: Int = -1,
) {
    fun toFrame(): ByteArray {
        if (raw.isNotEmpty()) return raw.copyOf()
        return WrenchPacketBuilder.buildRaw(
            functionCode = functionCode,
            payload = payload,
            address = address,
            reservedHigh = reservedHigh,
            reservedLow = reservedLow,
        )
    }

    fun rawHex(): String = toFrame().toHexString()

    companion object {
        const val HEADER_FIRST = 0xC5
        const val HEADER_SECOND = 0xC5
        const val ALT_HEADER_FIRST = 0xA5
        const val ALT_HEADER_SECOND = 0xA5
        const val DEFAULT_ADDRESS = 0x01
        const val MIN_FRAME_SIZE = 8

        const val FUNC_STOP_RUN = 0x01
        const val FUNC_STATUS = 0x05
        const val FUNC_ACK = 0x06
        const val FUNC_PARAM_SET = 0x10
        const val FUNC_BATTERY = 0x04
        const val FUNC_REMOTE_CONTROL = 0x11
        const val FUNC_TORQUE_ANGLE = 0x12
        const val FUNC_RESULT = 0x15
        const val FUNC_RESULT_CONFIRM = 0x17
        const val FUNC_TIME_REQUEST = 0x21
        const val FUNC_TIME_SYNC = 0x22
        const val FUNC_NUT_RESET = 0x23
        const val FUNC_QUERY_SN = 0x25
        const val FUNC_REPLY_SN = 0x26
        const val FUNC_HEARTBEAT = 0x33
        const val FUNC_GPS = 0x44
        const val FUNC_PULSE_PARAM_SET_RC283F = 0x50
        const val FUNC_PULSE_PARAM_SET_RCH = 0x51
        const val FUNC_PULSE_RESULT = 0x55
    }
}
