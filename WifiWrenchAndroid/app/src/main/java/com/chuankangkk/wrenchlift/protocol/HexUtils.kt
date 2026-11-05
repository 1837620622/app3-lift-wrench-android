package com.chuankangkk.wrenchlift.protocol

object HexUtils {
    fun hexToBytes(text: String): ByteArray {
        val compact = text.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
        require(compact.length % 2 == 0) { "十六进制字符串长度必须是偶数" }
        return ByteArray(compact.length / 2) { index ->
            compact.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}

fun ByteArray.toHexString(): String =
    joinToString(" ") { byte -> "%02X".format(byte.toInt() and 0xFF) }

fun Int.toUnsignedByte(): Byte = (this and 0xFF).toByte()
