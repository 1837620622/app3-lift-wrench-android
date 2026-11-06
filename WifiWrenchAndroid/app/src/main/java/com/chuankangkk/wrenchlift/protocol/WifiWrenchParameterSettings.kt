package com.chuankangkk.wrenchlift.protocol

import java.time.LocalDateTime
import kotlin.math.roundToInt

data class WifiWrenchParameterSettings(
    val productId: String = "0000000000",
    val stationName: String = "11111111111111111111",
    val employeeId: String = "2222222222",
    val toolSerial: String = "0000000000",
    val controllerSerial: String = "3333333333",
    val mode: Int,
    val targetTorqueNm: Int,
    val torqueLowerNm: Int,
    val torqueUpperNm: Int,
    val targetAngleDeg: Double,
    val angleUpperDeg: Double,
    val angleLowerDeg: Double,
    val dateTime: LocalDateTime = LocalDateTime.now(),
) {
    init {
        require(mode == MODE_M1 || mode == MODE_M2) { "参数模式只支持 M1 或 M2" }
        require(targetTorqueNm in 0..65535) { "目标扭矩超出协议范围" }
        require(torqueLowerNm in 0..65535) { "扭矩下限超出协议范围" }
        require(torqueUpperNm in 0..65535) { "扭矩上限超出协议范围" }
        require(angleToProtocol(targetAngleDeg) in 0..65535) { "目标角度超出协议范围" }
        require(angleToProtocol(angleUpperDeg) in 0..65535) { "角度上限超出协议范围" }
        require(angleToProtocol(angleLowerDeg) in 0..65535) { "角度下限超出协议范围" }
        // 协议：上下限填 0 表示不受该项控制；两项都填时下限不能大于上限，否则扳手一启动就会停机。
        require(torqueLowerNm == 0 || torqueUpperNm == 0 || torqueLowerNm <= torqueUpperNm) { "扭矩下限不能大于上限" }
        require(angleLowerDeg <= 0.0 || angleUpperDeg <= 0.0 || angleLowerDeg <= angleUpperDeg) { "角度下限不能大于上限" }
    }

    companion object {
        const val MODE_M1 = 1
        const val MODE_M2 = 2
        const val PAYLOAD_SIZE = 0x50

        fun angleToProtocol(angleDeg: Double): Int = (angleDeg * 10.0).roundToInt()
    }
}
