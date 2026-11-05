package com.chuankangkk.wrenchlift.protocol

class WrenchPacketParser {
    private var buffer = ByteArray(0)

    fun append(chunk: ByteArray): List<WrenchPacket> {
        if (chunk.isEmpty()) return emptyList()
        buffer += chunk
        if (buffer.size > MAX_BUFFER_SIZE) {
            buffer = buffer.takeLast(WrenchPacket.MIN_FRAME_SIZE - 1).toByteArray()
        }

        val packets = mutableListOf<WrenchPacket>()
        var index = 0
        while (index <= buffer.size - WrenchPacket.MIN_FRAME_SIZE) {
            if (!isHeaderAt(index)) {
                index += 1
                continue
            }

            val payloadLength = buffer[index + 6].toInt() and 0xFF
            val totalLength = WrenchPacket.MIN_FRAME_SIZE + payloadLength
            if (index + totalLength > buffer.size) break

            val raw = buffer.copyOfRange(index, index + totalLength)
            if (Checksum.isValid(raw)) {
                packets += WrenchPacket(
                    address = raw[2].toInt() and 0xFF,
                    functionCode = raw[3].toInt() and 0xFF,
                    reservedHigh = raw[4].toInt() and 0xFF,
                    reservedLow = raw[5].toInt() and 0xFF,
                    payload = raw.copyOfRange(7, raw.lastIndex),
                    raw = raw,
                    checksum = raw.last().toInt() and 0xFF,
                )
                index += totalLength
            } else {
                index += 1
            }
        }

        buffer = buffer.copyOfRange(index, buffer.size)
        return packets
    }

    fun reset() {
        buffer = ByteArray(0)
    }

    private fun isHeaderAt(index: Int): Boolean {
        val first = buffer[index].toInt() and 0xFF
        val second = buffer[index + 1].toInt() and 0xFF
        return (first == WrenchPacket.HEADER_FIRST && second == WrenchPacket.HEADER_SECOND) ||
            (first == WrenchPacket.ALT_HEADER_FIRST && second == WrenchPacket.ALT_HEADER_SECOND)
    }

    companion object {
        private const val MAX_BUFFER_SIZE = 4096
    }
}
