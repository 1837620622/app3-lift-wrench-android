package com.chuankangkk.wrenchlift.ui.worker

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chuankangkk.wrenchlift.measurement.LinkPhase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 回归（第五轮细节排版复查）：矮屏 640×288dp + 1.3 倍字体。
 * - 连接引导：说明区只在自己的区域里滚动，不画到「去连接 Wi‑Fi / 重新连接」上面；
 * - 第 1 步标题在逗号后断行，「扳手」不被劈开；
 * - 矮屏读数区的报错用短说法「报错 18」，不被省略成半句；
 * - 参数侧滑：目标 / 下限 / 上限 三个框完整落在侧滑里（单位写在分组标题，不再当后缀挤进输入框）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w640dp-h288dp-land-xhdpi")
class GuideAndSheetLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun largeFont() {
        RuntimeEnvironment.setFontScale(1.3f)
    }

    @After fun resetFont() {
        RuntimeEnvironment.setFontScale(1f)
    }

    @Test fun guideInfoScrollsAboveButtonsInsteadOfOverlappingThem() {
        compose.setContent {
            WifiWrenchWorkerApp(WorkerSamples.idle(LinkPhase.NO_WIFI), WorkerActions(), null, WorkerScreen.Guide, immersive = false)
        }
        compose.waitForIdle()
        val info = compose.onNodeWithTag("guide_info", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val open = compose.onNodeWithTag("open_wifi", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("说明区压到了按钮上：说明区 $info，按钮 $open", info.bottom <= open.top + 1f)
        compose.onNodeWithTag("open_wifi").assertIsDisplayed()
        compose.onNodeWithTag("retry_connect").assertIsDisplayed()
    }

    @Test fun guideTitleBreaksAfterCommaSoWrenchNameStaysWhole() {
        compose.setContent {
            WifiWrenchWorkerApp(WorkerSamples.idle(LinkPhase.NO_WIFI), WorkerActions(), null, WorkerScreen.Guide, immersive = false)
        }
        compose.waitForIdle()
        // 源码里的 Wi‑Fi 用的是不换行连字符 U+2011，这里保持一致。
        compose.onNodeWithText("打开系统设置，\n连上扳手的 Wi‑Fi").assertIsDisplayed()
    }

    @Test fun tinyReadoutShowsShortErrorWithoutTruncatingTheSentence() {
        compose.setContent {
            WifiWrenchWorkerApp(
                WorkerSamples.live(remote = true).copy(deviceErrorCode = 18),
                WorkerActions(), null, WorkerScreen.Workbench, immersive = false,
            )
        }
        compose.waitForIdle()
        compose.onNodeWithTag("control_message").assertTextEquals("报错 18")
    }

    @Test fun parameterFieldsStayInsideTheSheetAtShortHeight() {
        compose.setContent {
            WifiWrenchWorkerApp(WorkerSamples.live(remote = true), WorkerActions(), null, WorkerScreen.Workbench, immersive = false)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("params").performClick()
        compose.waitForIdle()
        val sheet = compose.onNodeWithTag("parameter_sheet", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("扭矩（N·m）").assertIsDisplayed()
        listOf("param_target_torque", "param_torque_lower", "param_torque_upper").forEach { tag ->
            val box = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(
                "$tag 超出侧滑：$box 不在 $sheet 内",
                box.left >= sheet.left - 1f && box.right <= sheet.right + 1f && box.bottom <= sheet.bottom + 1f,
            )
        }
    }
}
