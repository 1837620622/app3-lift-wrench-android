package com.chuankangkk.wrenchlift.ui

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.chuankangkk.wrenchlift.measurement.*
import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkerWorkbenchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mainPageKeepsControlsVisibleAndHasNoDeviceRunButton() {
        var connectClicks = 0
        show(MeasurementState(), onConnect = { connectClicks++ })
        compose.onNodeWithTag("connect").assertIsDisplayed().performClick()
        compose.onNodeWithTag("export").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag("new_record").assertIsDisplayed()
        compose.onNodeWithTag("torque_chart").assertIsDisplayed()
        compose.onNodeWithText("正转启动").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, connectClicks) }
        screenshot("worker-empty")
    }

    @Test fun settingsAndHistoryReturnToMeasurement() {
        show(MeasurementState())
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithText("设备 IP").assertIsDisplayed()
        compose.onNodeWithText("返回测量").performClick()
        compose.onNodeWithTag("history").performClick()
        compose.onNodeWithText("暂无测量记录，连接扳手后开始采集。").assertIsDisplayed()
        compose.onNodeWithText("返回测量").performClick()
        compose.onNodeWithTag("torque_chart").assertIsDisplayed()
    }

    @Test fun realDataPresentationCanExportAndChartDoesNotClaimConfirmedBalance() {
        var exports = 0
        val points = List(50) { i -> MeasuredPoint(TorqueAnglePoint(i * 2.0, i, i.toDouble(), 1000L), 1, null, warningLevel = WarningLevel.WATCH, rawHex = "test-frame") }
        show(MeasurementState(recentPoints = points, currentTorqueNm = 98.0, currentAngleDeg = 49.0, persistedPointCount = 2505), onExport = { exports++ })
        compose.onNodeWithTag("export").assertIsEnabled().performClick()
        compose.onNodeWithText("分离点：待数据分析").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, exports) }
        screenshot("worker-data")
    }

    private fun show(state: MeasurementState, onConnect: () -> Unit = {}, onExport: () -> Unit = {}) {
        compose.setContent {
            App3FieldTheme {
                WorkerWorkbench(state, onConnect, {}, onExport, {}, {}, {}, {}, {}, {}, {}, {}, debugContent = {})
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        InstrumentationRegistry.getInstrumentation().targetContext.openFileOutput("$name.png", 0).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
