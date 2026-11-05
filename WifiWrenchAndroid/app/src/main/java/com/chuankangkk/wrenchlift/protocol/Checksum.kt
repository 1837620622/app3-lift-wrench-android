package com.chuankangkk.wrenchlift.protocol

object Checksum {
    fun calculate(data: ByteArray, start: Int = 0, endExclusive: Int = data.size): Int {
        require(start in 0..data.size)
        require(endExclusive in start..data.size)
        var sum = 0
        for (index in start until endExclusive) {
            sum = (sum + (data[index].toInt() and 0xFF)) and 0xFF
        }
        return sum
    }

    fun isValid(frame: ByteArray): Boolean {
        if (frame.size < WrenchPacket.MIN_FRAME_SIZE) return false
        return calculate(frame, 0, frame.lastIndex) == (frame.last().toInt() and 0xFF)
    }
}
