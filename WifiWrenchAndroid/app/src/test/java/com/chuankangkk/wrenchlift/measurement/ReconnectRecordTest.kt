package com.chuankangkk.wrenchlift.measurement

import android.app.Application
import androidx.lifecycle.ViewModelStore
import com.chuankangkk.wrenchlift.connection.ScriptedWrenchServer
import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import com.chuankangkk.wrenchlift.protocol.WrenchPacketBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/** 断线后自动重连：同一台扳手的采集应续在同一条记录里，曲线不清空。 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReconnectRecordTest {
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

    private fun torqueFrame(points: Int, start: Int): WrenchPacket {
        val payload = ByteArray(12 + 4 * points)
        for (i in 0 until points) {
            payload[13 + i * 4] = (start + i).toByte()
            payload[15 + i * 4] = (start + i).toByte()
        }
        return WrenchPacketBuilder.build(WrenchPacket.FUNC_TORQUE_ANGLE, payload)
    }

    @Test fun reconnectAfterDropContinuesSameRecordAndKeepsCurve() {
        ScriptedWrenchServer().use { wrench ->
            app.getSharedPreferences("connection_preferences", 0).edit()
                .putString(MeasurementViewModel.PREF_LAST_GOOD_HOST, wrench.endpoint.host)
                .putString(MeasurementViewModel.PREF_LAST_GOOD_PORT, wrench.endpoint.port.toString())
                .commit()
            val vm = MeasurementViewModel(app, autoConnectOnWifi = false, handshakeTimeoutMillis = 1_500).also {
                store.put("vm", it)
            }
            vm.connectTcp()
            awaitCondition(message = "握手") { vm.state.value.linkPhase == LinkPhase.READY }
            repeat(3) { wrench.send(torqueFrame(25, start = 10)) }
            awaitCondition(message = "首批 75 点入库") { vm.state.value.persistedPointCount == 75L }
            val firstRecord = vm.state.value.currentSessionId
            assertTrue("应已建立记录", firstRecord != null)

            // Wi‑Fi 抖动：只断开客户端，扳手仍在；App 应自动重连。
            wrench.dropClient()
            awaitCondition(message = "断开") { vm.state.value.linkPhase == LinkPhase.DROPPED }
            awaitCondition(message = "自动重连") { vm.state.value.linkPhase == LinkPhase.READY }

            wrench.send(torqueFrame(25, start = 50))
            awaitCondition(message = "重连后继续入库到 100 点") { vm.state.value.persistedPointCount == 100L }
            // 同一条记录：记录号不变，曲线不被清空。
            assertEquals(firstRecord, vm.state.value.currentSessionId)
            assertEquals(100, vm.state.value.recentPoints.size)
            assertEquals(10, vm.state.value.recentPoints.first().sourcePoint.torqueNm.toInt())
            vm.endSession()
            awaitCondition(message = "会话结束") { vm.state.value.linkPhase == LinkPhase.IDLE }
        }
    }
}
