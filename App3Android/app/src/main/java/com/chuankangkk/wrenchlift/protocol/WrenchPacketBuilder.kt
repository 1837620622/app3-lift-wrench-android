package com.chuankangkk.wrenchlift.protocol

import java.time.LocalDateTime

object WrenchPacketBuilder {
    fun build(
        functionCode: Int,
        payload: ByteArray,
        address: Int = WrenchPacket.DEFAULT_ADDRESS,
        reservedHigh: Int = 0xFF,
        reservedLow: Int = 0xFF,
    ): WrenchPacket {
        val raw = buildRaw(functionCode, payload, address, reservedHigh, reservedLow)
        return WrenchPacket(
            address = address,
            functionCode = functionCode,
            payload = payload.copyOf(),
            reservedHigh = reservedHigh,
            reservedLow = reservedLow,
            raw = raw,
            checksum = raw.last().toInt() and 0xFF,
        )
    }

    fun buildRaw(
        functionCode: Int,
        payload: ByteArray,
        address: Int = WrenchPacket.DEFAULT_ADDRESS,
        reservedHigh: Int = 0xFF,
        reservedLow: Int = 0xFF,
    ): ByteArray {
        require(payload.size <= 0xFF) { "单帧数据内容长度不能超过 255 字节" }
        val frame = ByteArray(WrenchPacket.MIN_FRAME_SIZE + payload.size)
        frame[0] = WrenchPacket.HEADER_FIRST.toUnsignedByte()
        frame[1] = WrenchPacket.HEADER_SECOND.toUnsignedByte()
        frame[2] = address.toUnsignedByte()
        frame[3] = functionCode.toUnsignedByte()
        frame[4] = reservedHigh.toUnsignedByte()
        frame[5] = reservedLow.toUnsignedByte()
        frame[6] = payload.size.toUnsignedByte()
        payload.copyInto(frame, destinationOffset = 7)
        frame[frame.lastIndex] = Checksum.calculate(frame, 0, frame.lastIndex).toUnsignedByte()
        return frame
    }

    fun enableRemoteControl(address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket =
        build(WrenchPacket.FUNC_REMOTE_CONTROL, byteArrayOf(0x01), address)

    fun disableRemoteControl(address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket =
        build(WrenchPacket.FUNC_REMOTE_CONTROL, byteArrayOf(0x00), address)

    fun startForward(address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket =
        build(WrenchPacket.FUNC_STOP_RUN, byteArrayOf(0x01), address)

    fun startReverse(address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket =
        build(WrenchPacket.FUNC_STOP_RUN, byteArrayOf(0x02), address)

    fun stop(address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket =
        build(WrenchPacket.FUNC_STOP_RUN, byteArrayOf(0x00), address)

    fun parameterSet(settings: App3ParameterSettings, address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket {
        val payload = ByteArray(App3ParameterSettings.PAYLOAD_SIZE)
        writeAscii(payload, 0, 10, settings.productId)
        writeAscii(payload, 10, 20, settings.stationName)
        writeAscii(payload, 30, 10, settings.employeeId)
        writeAscii(payload, 40, 10, settings.toolSerial)
        writeAscii(payload, 50, 10, settings.controllerSerial)
        writeU16(payload, 60, settings.dateTime.year)
        payload[62] = settings.dateTime.monthValue.toUnsignedByte()
        payload[63] = settings.dateTime.dayOfMonth.toUnsignedByte()
        payload[64] = settings.dateTime.hour.toUnsignedByte()
        payload[65] = settings.dateTime.minute.toUnsignedByte()
        payload[66] = settings.dateTime.second.toUnsignedByte()
        payload[67] = settings.mode.toUnsignedByte()
        writeU16(payload, 68, settings.targetTorqueNm)
        writeU16(payload, 70, settings.torqueLowerNm)
        writeU16(payload, 72, settings.torqueUpperNm)
        writeU16(payload, 74, App3ParameterSettings.angleToProtocol(settings.targetAngleDeg))
        writeU16(payload, 76, App3ParameterSettings.angleToProtocol(settings.angleUpperDeg))
        writeU16(payload, 78, App3ParameterSettings.angleToProtocol(settings.angleLowerDeg))
        return build(WrenchPacket.FUNC_PARAM_SET, payload, address)
    }

    fun heartbeat(address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket =
        build(WrenchPacket.FUNC_HEARTBEAT, byteArrayOf(0x00), address)

    fun resultAck(address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket =
        build(WrenchPacket.FUNC_RESULT_CONFIRM, byteArrayOf(0x00), address)

    fun querySn(address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket =
        build(WrenchPacket.FUNC_QUERY_SN, byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00), address)

    fun timeSync(dateTime: LocalDateTime = LocalDateTime.now(), address: Int = WrenchPacket.DEFAULT_ADDRESS): WrenchPacket {
        val payload = byteArrayOf(
            ((dateTime.year ushr 8) and 0xFF).toByte(),
            (dateTime.year and 0xFF).toByte(),
            dateTime.monthValue.toUnsignedByte(),
            dateTime.dayOfMonth.toUnsignedByte(),
            dateTime.hour.toUnsignedByte(),
            dateTime.minute.toUnsignedByte(),
            dateTime.second.toUnsignedByte(),
        )
        return build(WrenchPacket.FUNC_TIME_SYNC, payload, address)
    }

}

private fun writeAscii(target: ByteArray, offset: Int, length: Int, value: String) {
    val bytes = value.encodeToByteArray()
    val count = minOf(length, bytes.size)
    bytes.copyInto(target, destinationOffset = offset, startIndex = 0, endIndex = count)
}

private fun writeU16(target: ByteArray, offset: Int, value: Int) {
    require(value in 0..0xFFFF) { "U16 字段超出范围" }
    target[offset] = ((value ushr 8) and 0xFF).toByte()
    target[offset + 1] = (value and 0xFF).toByte()
}
