package com.chuankangkk.wrenchlift.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 协议 0x15：M1 / M2 同一位置字段含义不同，设定值只在 M1 里读得到。 */
class ResultFrameModeTest {
    private fun frame(mode: Int, status: Int = 0xFF): WrenchPacket {
        val payload = List(10) { 0x30 } + listOf(0x00, 0x01) + listOf(0x07, 0xE8, 0x09, 0x1E, 0x0A, 0x01, 0x08) + listOf(mode) +
            listOf(0x00, 0xB5, 0x01, 0xE5, 0x00, 0x2D, 0x00, 0x2C, 0x01, 0xF4, 0x01, 0x90, 0x00, 0x64, 0x00, 0x01)
        val body = listOf(0xC5, 0xC5, 0x01, 0x15, status, 0xFF, payload.size) + payload
        val bytes = (body + (body.sum() and 0xFF)).map { it.toByte() }.toByteArray()
        return WrenchPacketParser().append(bytes).single()
    }

    @Test fun m2TorqueFieldsAreActualValuesNotSettings() {
        val result = ResultFrameDecoder.decode(frame(mode = 0x02))
        assertTrue(result.isM2)
        assertNull("M2 没有目标扭矩设定值", result.settingTorqueNm)
        assertEquals("预设扭矩实际值", result.torqueField1Name)
        assertEquals(181.0, result.targetTorqueNm!!, 0.0)
        assertEquals("最终扭矩实际值", result.torqueField2Name)
        assertEquals(485.0, result.actualTorqueNm!!, 0.0)
        assertEquals("目标角度设置值", result.angleField1Name)
        assertEquals(45.0, result.targetAngleDeg!!, 0.0)
        assertEquals("最终扭矩", result.torqueLimitName)
        assertEquals("M2 扭矩+角度模式", result.modeText)
    }

    @Test fun m1TorqueFieldsAreSettings() {
        val result = ResultFrameDecoder.decode(frame(mode = 0x01))
        assertTrue(!result.isM2)
        assertEquals(181.0, result.settingTorqueNm!!, 0.0)
        assertEquals("目标扭矩", result.torqueField1Name)
        assertEquals("目标扭矩实际值", result.torqueField2Name)
        assertEquals("M1 扭矩模式", result.modeText)
    }

    @Test fun unknownModeIsLabelledNotGuessed() {
        val result = ResultFrameDecoder.decode(frame(mode = 0x09))
        assertEquals("模式 0x09（协议未定义）", result.modeText)
    }

    @Test fun statusCodesMapToOkAndNgKinds() {
        assertEquals(ResultKind.NG, ResultFrameDecoder.mapStatus(0x13).kind)
        assertEquals("疑似打滑", ResultFrameDecoder.mapStatus(0x13).label)
        assertEquals(ResultKind.OK, ResultFrameDecoder.mapStatus(0x01).kind)
        assertEquals(ResultKind.UNKNOWN, ResultFrameDecoder.mapStatus(0x77).kind)
    }
}
