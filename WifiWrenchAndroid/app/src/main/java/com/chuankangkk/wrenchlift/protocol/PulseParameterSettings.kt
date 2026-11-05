package com.chuankangkk.wrenchlift.protocol

import java.time.LocalDateTime
import kotlin.math.roundToInt

/**
 * 0x50（RC283F 驱动）脉冲 / 离合 / 冲击扳手参数（最新协议.docx §12）。
 * 只有确认现场是该类扳手时才在界面开放；定扭矩扳手走 0x10。
 */
data class Rc283fPulseSettings(
    val productId: String = "0000000000",
    val stationName: String = "11111111111111111111",
    val employeeId: String = "2222222222",
    val toolSerial: String = "0000000000",
    val boltModel: String = "0000000000",
    /** MS1 扭矩模式 0x00；MP 定点硬连接 0x01；MP 定点软连接 0x02。 */
    val mode: Int,
    /** N·m，协议按 0.1 N·m 写入（0-65535 表示 0-6553.5 N·m）。 */
    val targetTorqueNm: Double,
    /** 50-350，仅定点硬/软连接有效，扭矩模式填 0。 */
    val k1: Int = 0,
    /** 1-80。 */
    val k2: Int = 0,
    /** 1-3000。 */
    val k3: Int = 0,
    /** 0-100 %。 */
    val boltStatusPercent: Int = 0,
    val errorPrecision: Int = 0,
    val boltCount: Int = 0,
    val dateTime: LocalDateTime = LocalDateTime.now(),
) {
    init {
        require(mode in 0x00..0x02) { "RC283F 参数模式只支持 0x00/0x01/0x02" }
        require(torqueToProtocol(targetTorqueNm) in 0..0xFFFF) { "目标扭矩超出协议范围（0-6553.5 N·m）" }
        if (mode == MODE_MS1_TORQUE) {
            require(k1 == 0 && k2 == 0 && k3 == 0) { "扭矩模式下 K1/K2/K3 须填 0" }
        } else {
            require(k1 in 50..350) { "K1 范围 50-350" }
            require(k2 in 1..80) { "K2 范围 1-80" }
            require(k3 in 1..3000) { "K3 范围 1-3000" }
        }
        require(boltStatusPercent in 0..100) { "螺栓状态范围 0-100%" }
        require(errorPrecision in 0..0xFF) { "误差精度超出 1 字节" }
        require(boltCount in 0..0xFFFF) { "螺栓个数超出范围" }
    }

    companion object {
        const val MODE_MS1_TORQUE = 0x00
        const val MODE_MP_HARD = 0x01
        const val MODE_MP_SOFT = 0x02
    }
}

/**
 * 0x51（RCH 驱动）脉冲 / 离合 / 冲击扳手参数（最新协议.docx §13）。
 */
data class RchPulseSettings(
    val productId: String = "0000000000",
    val stationName: String = "11111111111111111111",
    val employeeId: String = "2222222222",
    val toolSerial: String = "0000000000",
    val boltModel: String = "0000000000",
    /** MP 定点硬连接 0x01；MP 定点软连接 0x02；MP 任意参数设定 0x03。 */
    val mode: Int,
    /** 1-10，仅定点硬/软连接有效。 */
    val presetIndex: Int = 1,
    /** 50-300，仅任意参数设定模式有效。 */
    val k1: Int = 0,
    /** 1-80（1 字节）。 */
    val k2: Int = 0,
    /** 1-20（1 字节）。 */
    val k3: Int = 0,
    /** 仅任意参数设定模式有效；协议未写缩放，按原始 2 字节整数写入，真机确认后再定单位。 */
    val setTorqueRaw: Int = 0,
    val boltStatusPercent: Int = 0,
    val errorPrecision: Int = 0,
    val boltCount: Int = 0,
    val dateTime: LocalDateTime = LocalDateTime.now(),
) {
    init {
        require(mode in 0x01..0x03) { "RCH 参数模式只支持 0x01/0x02/0x03" }
        if (mode == MODE_MP_ANY) {
            require(k1 in 50..300) { "K1 范围 50-300" }
            require(k2 in 1..80) { "K2 范围 1-80" }
            require(k3 in 1..20) { "K3 范围 1-20" }
            require(setTorqueRaw in 0..0xFFFF) { "设定扭矩超出 2 字节" }
        } else {
            require(presetIndex in 1..10) { "定点模式参数序号范围 1-10" }
        }
        require(boltStatusPercent in 0..100) { "螺栓状态范围 0-100%" }
        require(errorPrecision in 0..0xFF) { "误差精度超出 1 字节" }
        require(boltCount in 0..0xFFFF) { "螺栓个数超出范围" }
    }

    companion object {
        const val MODE_MP_HARD = 0x01
        const val MODE_MP_SOFT = 0x02
        const val MODE_MP_ANY = 0x03
    }
}

/** 0x50/0x55 扭矩口径：0.1 N·m（与 0x10/0x12 的整数 N·m 不同，禁止混用）。 */
fun torqueToProtocol(torqueNm: Double): Int = (torqueNm * 10.0).roundToInt()
