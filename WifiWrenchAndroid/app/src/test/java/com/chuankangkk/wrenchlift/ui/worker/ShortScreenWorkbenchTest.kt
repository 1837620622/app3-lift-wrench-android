package com.chuankangkk.wrenchlift.ui.worker

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 回归：CI 的 API 35 模拟器（640×360dp、1.3 倍字体、系统栏可见）曾把「参数/导出」挤出屏幕。
 * 这里模拟扣掉状态栏/导航栏后的可用高度，6 个控制键必须全部可见（不靠滚动、不藏菜单）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ShortScreenWorkbenchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun assertAllControlsVisible(name: String) {
        RuntimeEnvironment.setFontScale(1.3f)
        try {
            compose.setContent { WifiWrenchWorkerApp(WorkerSamples.live(remote = false), WorkerActions(), null, WorkerScreen.Workbench, immersive = false) }
            compose.waitForIdle()
            val hidden = listOf("forward", "reverse", "stop", "params", "export", "remote_toggle", "torque_chart", "readouts", "back")
                .filter { runCatching { compose.onNodeWithTag(it).assertIsDisplayed() }.isFailure }
            assertEquals("这些控件被挤出屏幕：$hidden", emptyList<String>(), hidden)
            // 「可见」还不够：每个控件必须完整落在所属面板内（不被裁一半）。
            fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val controls = bounds("controls")
            val readouts = bounds("readouts")
            val clipped = buildList {
                listOf("forward", "reverse", "stop", "params", "export", "remote_toggle").forEach { tag ->
                    val b = bounds(tag)
                    if (b.top < controls.top - 1f || b.bottom > controls.bottom + 1f) add(tag)
                }
                listOf("readout_torque", "readout_angle").forEach { tag ->
                    val b = bounds(tag)
                    if (b.top < readouts.top - 1f || b.bottom > readouts.bottom + 1f) add(tag)
                }
            }
            assertEquals("这些控件被面板裁切：$clipped", emptyList<String>(), clipped)
            System.getenv("WIFI_WRENCH_EVIDENCE_DIR")?.let { dir ->
                val view = compose.activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(android.graphics.Canvas(bitmap))
                File(dir).mkdirs()
                File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    @Test @Config(qualifiers = "w640dp-h288dp-land-xhdpi") fun statusAndBottomNavBarsVisible() = assertAllControlsVisible("short-640x288-font1.3")
    @Test @Config(qualifiers = "w640dp-h312dp-land-xhdpi") fun statusBarAndGestureNav() = assertAllControlsVisible("short-640x312-font1.3")
    @Test @Config(qualifiers = "w592dp-h336dp-land-xhdpi") fun statusBarAndSideNav() = assertAllControlsVisible("short-592x336-font1.3")
}
