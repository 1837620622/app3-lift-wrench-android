package com.chuankangkk.wrenchlift.protocol

import java.time.LocalDateTime

object TestPacketFactory {
    fun resultFrame(
        statusCode: Int,
        employeeId: String,
        boltNo: Int,
        actualTorqueNm: Int,
        actualAngleDeg: Int,
        address: Int = WrenchPacket.DEFAULT_ADDRESS,
    ): WrenchPacket {
        val payload = ByteArray(0x24)
        val employeeBytes = employeeId.padEnd(10, '0').take(10).encodeToByteArray()
        employeeBytes.copyInto(payload, endIndex = minOf(10, employeeBytes.size))
        writeU16(payload, 10, boltNo)
        val now = LocalDateTime.now()
        writeU16(payload, 12, now.year)
        payload[14] = now.monthValue.toUnsignedByte()
        payload[15] = now.dayOfMonth.toUnsignedByte()
        payload[16] = now.hour.toUnsignedByte()
        payload[17] = now.minute.toUnsignedByte()
        payload[18] = now.second.toUnsignedByte()
        payload[19] = 0x01
        writeU16(payload, 20, 180)
        writeU16(payload, 22, actualTorqueNm)
        writeU16(payload, 24, 45)
        writeU16(payload, 26, actualAngleDeg)
        writeU16(payload, 28, 220)
        writeU16(payload, 30, 120)
        writeU16(payload, 32, 80)
        writeU16(payload, 34, 5)
        return WrenchPacketBuilder.build(
            functionCode = WrenchPacket.FUNC_RESULT,
            payload = payload,
            address = address,
            reservedHigh = statusCode,
            reservedLow = 0xFF,
        )
    }

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 8) and 0xFF).toByte()
        target[offset + 1] = (value and 0xFF).toByte()
    }
}

private fun Int.toUnsignedByte(): Byte = (this and 0xFF).toByte()
