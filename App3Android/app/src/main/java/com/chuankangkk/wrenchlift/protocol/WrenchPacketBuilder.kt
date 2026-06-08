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
