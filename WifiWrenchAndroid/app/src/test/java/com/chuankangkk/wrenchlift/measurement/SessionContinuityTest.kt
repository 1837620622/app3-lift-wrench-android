package com.chuankangkk.wrenchlift.measurement

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.chuankangkk.wrenchlift.connection.ScriptedWrenchServer
import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import com.chuankangkk.wrenchlift.protocol.WrenchPacketBuilder
import com.chuankangkk.wrenchlift.protocol.WifiWrenchParameterSettings
import org.junit.Assert.assertTrue
import com.chuankangkk.wrenchlift.protocol.TestPacketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * 记录边界（断线续用、结束后新建、换扳手新建）与序号一致性。
 * 断线自动重连属于同一条记录；只有用户结束会话或换了一台扳手才新开记录。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionContinuityTest {
    private lateinit var main: ExecutorCoroutineDispatcher
    private lateinit var app: Application
    private val store = ViewModelStore()

    @Before fun setUp() {
        main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        Dispatchers.setMain(main)
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("connection_preferences", 0).edit().clear().commit()
    }

    @After fun tearDown() {
        store.clear()
        app.getSharedPreferences("connection_preferences", 0).edit().clear().commit()
        Dispatchers.resetMain()
        main.close()
    }

    private fun awaitCondition(timeoutMillis: Long = 12_000, message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("等待超时：$message")
    }

    private fun viewModelFor(server: ScriptedWrenchServer): MeasurementViewModel {
        app.getSharedPreferences("connection_preferences", 0).edit()
            .putString(MeasurementViewModel.PREF_LAST_GOOD_HOST, server.endpoint.host)
            .putString(MeasurementViewModel.PREF_LAST_GOOD_PORT, server.endpoint.port.toString())
            .commit()
        return MeasurementViewModel(app, autoConnectOnWifi = false, handshakeTimeoutMillis = 1_500).also {
            store.put("vm-${server.port}", it)
        }
    }

    private fun ScriptedWrenchServer.frames(code: Int) = received.filter { it.functionCode == code }

    private fun torqueFrame(points: Int, start: Int): WrenchPacket {
        val payload = ByteArray(12 + 4 * points)
        for (i in 0 until points) {
            payload[13 + i * 4] = (start + i).toByte()
            payload[15 + i * 4] = (start + i).toByte()
        }
        return WrenchPacketBuilder.build(WrenchPacket.FUNC_TORQUE_ANGLE, payload)
    }

    /** 同一台扳手断线后自动重连：记录号不变、点数连续、曲线保留。 */
    @Test fun reconnectAfterDropContinuesSameRecordAndKeepsCurve() {
        ScriptedWrenchServer().use { wrench ->
            val vm = viewModelFor(wrench)
            vm.connectTcp()
            awaitCondition(message = "握手") { vm.state.value.linkPhase == LinkPhase.READY }
            repeat(3) { wrench.send(torqueFrame(25, start = 10)) }
            awaitCondition(message = "首批 75 点入库") { vm.state.value.persistedPointCount == 75L }
            val firstRecord = vm.state.value.currentSessionId
            assertTrue("应已建立记录", firstRecord != null)

            wrench.dropClient()
            awaitCondition(message = "断开") { vm.state.value.linkPhase == LinkPhase.DROPPED }
            awaitCondition(message = "自动重连") { vm.state.value.linkPhase == LinkPhase.READY }

            wrench.send(torqueFrame(25, start = 50))
            awaitCondition(message = "重连后继续入库到 100 点") { vm.state.value.persistedPointCount == 100L }
            assertEquals(firstRecord, vm.state.value.currentSessionId)
            assertEquals(100, vm.state.value.recentPoints.size)
            assertEquals(10, vm.state.value.recentPoints.first().sourcePoint.torqueNm.toInt())
            vm.endSession()
            awaitCondition(message = "会话结束") { vm.state.value.linkPhase == LinkPhase.IDLE }
        }
    }

    /** 用户结束会话后再连接：必须是新记录，曲线从头开始。 */
    @Test fun connectingAfterEndingSessionStartsNewRecord() {
        ScriptedWrenchServer().use { wrench ->
            val vm = viewModelFor(wrench)
            vm.connectTcp()
            awaitCondition(message = "握手") { vm.state.value.linkPhase == LinkPhase.READY }
            wrench.send(torqueFrame(25, start = 10))
            awaitCondition(message = "25 点入库") { vm.state.value.persistedPointCount == 25L }
            val first = vm.state.value.currentSessionId
            vm.endSession()
            awaitCondition(message = "会话结束") { vm.state.value.linkPhase == LinkPhase.IDLE }

            vm.connectTcp()
            awaitCondition(message = "再次握手 linkPhase=${vm.state.value.linkPhase} conn=${vm.state.value.connectionState} probe=${vm.state.value.probeReport.summary} log=${vm.state.value.logLines.takeLast(4)}") { vm.state.value.linkPhase == LinkPhase.READY }
            wrench.send(torqueFrame(5, start = 80))
            awaitCondition(message = "新记录入库 5 点") { vm.state.value.persistedPointCount == 5L }
            assertNotEquals(first, vm.state.value.currentSessionId)
            assertEquals(5, vm.state.value.recentPoints.size)
            vm.endSession()
            awaitCondition(message = "会话结束") { vm.state.value.linkPhase == LinkPhase.IDLE }
        }
    }

    /** 换了一台扳手（SN 变化）：上一台的记录收尾，新记录只含这台的数据。 */
    @Test fun changingWrenchStartsNewRecord() {
        ScriptedWrenchServer().use { wrench ->
            val vm = viewModelFor(wrench)
            vm.connectTcp()
            awaitCondition(message = "握手") { vm.state.value.linkPhase == LinkPhase.READY }
            awaitCondition(message = "读到 SN") { vm.state.value.deviceSn.isNotBlank() }
            wrench.send(torqueFrame(25, start = 10))
            awaitCondition(message = "25 点入库") { vm.state.value.persistedPointCount == 25L }
            val first = vm.state.value.currentSessionId

            wrench.dropClient()
            awaitCondition(message = "断开") { vm.state.value.linkPhase == LinkPhase.DROPPED }
            wrench.snPayload = byteArrayOf(0x99.toByte(), 0x88.toByte(), 0x77, 0x66, 0x55)
            awaitCondition(message = "自动重连") { vm.state.value.linkPhase == LinkPhase.READY }
            awaitCondition(message = "读到新 SN") { vm.state.value.deviceSn.startsWith("9988") }

            wrench.send(torqueFrame(3, start = 60))
            awaitCondition(message = "新扳手记录入库 3 点") { vm.state.value.persistedPointCount == 3L }
            assertNotEquals(first, vm.state.value.currentSessionId)
            assertEquals(3, vm.state.value.recentPoints.size)
            vm.endSession()
            awaitCondition(message = "会话结束") { vm.state.value.linkPhase == LinkPhase.IDLE }
        }
    }

    /** 结果帧也占记录里的一个序号：曲线上的序号必须与导出「过程数据」的序号一致。 */
    @Test fun chartSequenceNumbersMatchRecordAcrossResultFrames() {
        ScriptedWrenchServer().use { wrench ->
            val vm = viewModelFor(wrench)
            vm.connectTcp()
            awaitCondition(message = "握手") { vm.state.value.linkPhase == LinkPhase.READY }
            wrench.send(torqueFrame(25, start = 10))
            awaitCondition(message = "25 点") { vm.state.value.persistedPointCount == 25L }
            wrench.send(TestPacketFactory.resultFrame(0x00, "0000000000", 1, 182, 88))
            awaitCondition(message = "结果帧计入点数") { vm.state.value.persistedPointCount == 26L }
            wrench.send(torqueFrame(25, start = 40))
            awaitCondition(message = "第二批") { vm.state.value.persistedPointCount == 51L }
            val recent = vm.state.value.recentPoints
            // 第一批序号 1..25；结果帧占 26；第二批序号 27..51。
            assertEquals(1L, recent.first().seq)
            assertEquals("第二批第一个点（扭矩 40）", 27L, recent.first { it.sourcePoint.torqueNm == 40.0 }.seq)
            assertEquals(51L, recent.last().seq)
            vm.endSession()
            awaitCondition(message = "会话结束") { vm.state.value.linkPhase == LinkPhase.IDLE }
        }
    }

    /** 写参数不编造标识：员工号沿用扳手回传的值，未知时留空。 */
    @Test fun parameterWriteDoesNotInventIdentifiers() {
        ScriptedWrenchServer().use { wrench ->
            val vm = viewModelFor(wrench)
            vm.connectTcp()
            awaitCondition(message = "握手") { vm.state.value.linkPhase == LinkPhase.READY }
            val draft = ParameterDraft(
                mode = WifiWrenchParameterSettings.MODE_M1, targetTorque = "100", torqueLower = "0", torqueUpper = "0",
                targetAngle = "0", angleUpper = "0", angleLower = "0",
            )
            vm.writeParameters(draft)
            awaitCondition(message = "写入 0x10") { wrench.frames(WrenchPacket.FUNC_PARAM_SET).isNotEmpty() }
            val first = wrench.frames(WrenchPacket.FUNC_PARAM_SET).single().payload
            assertTrue("未知员工号时标识区全零", first.copyOfRange(0, 60).all { it == 0.toByte() })

            // 扳手回传过员工号（0x12 的员工号字段）后，参数写入沿用这个值。
            val payload = ByteArray(12 + 4)
            "EMP0001234".encodeToByteArray().copyInto(payload, endIndex = 10)
            wrench.send(WrenchPacketBuilder.build(WrenchPacket.FUNC_TORQUE_ANGLE, payload))
            awaitCondition(message = "读到员工号") { vm.state.value.employeeId == "EMP0001234" }
            vm.writeParameters(draft)
            awaitCondition(message = "第二次写入 0x10") { wrench.frames(WrenchPacket.FUNC_PARAM_SET).size == 2 }
            val second = wrench.frames(WrenchPacket.FUNC_PARAM_SET)[1].payload
            assertEquals("EMP0001234", second.copyOfRange(30, 40).decodeToString().trimEnd('\u0000'))
            assertTrue("产品号不编造", second.copyOfRange(0, 10).all { it == 0.toByte() })
            vm.endSession()
            awaitCondition(message = "会话结束") { vm.state.value.linkPhase == LinkPhase.IDLE }
        }
    }
}
