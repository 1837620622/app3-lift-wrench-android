package com.chuankangkk.wrenchlift.ui.worker

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chuankangkk.wrenchlift.measurement.LinkPhase
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import kotlinx.coroutines.delay

enum class WorkerScreen { Splash, Guide, Workbench, Records, Settings, Diagnostics, Debug }

/**
 * 正式工人 App 根：启动页 → 工作台 / 连接引导 → 测点与记录 → 设置 / 高级诊断。
 * 核心启停全在工作台一屏完成，不做多页跳转。
 */
@Composable
fun WifiWrenchWorkerApp(
    state: MeasurementState,
    actions: WorkerActions,
    debugContent: (@Composable () -> Unit)?,
    startScreen: WorkerScreen = WorkerScreen.Splash,
    splashMillis: Long = SPLASH_MILLIS,
    immersive: Boolean = true,
) {
    var screen by rememberSaveable { mutableStateOf(startScreen) }
    var settingsReturn by rememberSaveable { mutableStateOf(WorkerScreen.Records) }
    ImmersiveBars(immersive)

    if (screen == WorkerScreen.Splash) {
        LaunchedEffect(Unit) {
            delay(splashMillis)
            screen = if (state.onWrenchNetwork()) WorkerScreen.Workbench else WorkerScreen.Guide
        }
    }
    // 引导页上一旦握手成功，直接进入工作台。
    LaunchedEffect(screen, state.linkPhase) {
        if (screen == WorkerScreen.Guide && (state.linkPhase == LinkPhase.READY || state.linkPhase == LinkPhase.WAITING_REPLY)) {
            screen = WorkerScreen.Workbench
        }
    }
    val openSettings = { from: WorkerScreen ->
        settingsReturn = from
        screen = WorkerScreen.Settings
    }
    BackHandler(enabled = screen == WorkerScreen.Settings || screen == WorkerScreen.Diagnostics || screen == WorkerScreen.Debug) {
        screen = when (screen) {
            WorkerScreen.Diagnostics, WorkerScreen.Debug -> WorkerScreen.Settings
            else -> settingsReturn
        }
    }

    WrenchWorkerTheme(dark = state.darkTheme) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
            label = "screen",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            when (target) {
                WorkerScreen.Splash -> SplashScreen()
                WorkerScreen.Guide -> ConnectGuideScreen(
                    state, actions,
                    onOpenRecords = { screen = WorkerScreen.Records },
                    onOpenSettings = { openSettings(WorkerScreen.Guide) },
                )
                WorkerScreen.Workbench -> WorkbenchScreen(state, actions, onLeave = { screen = WorkerScreen.Records })
                WorkerScreen.Records -> RecordsScreen(
                    state, actions,
                    onStart = { screen = WorkerScreen.Workbench },
                    onOpenSettings = { openSettings(WorkerScreen.Records) },
                    onOpenGuide = { screen = WorkerScreen.Guide },
                )
                WorkerScreen.Settings -> SettingsScreen(
                    state, actions,
                    onBack = { screen = settingsReturn },
                    onOpenDiagnostics = { screen = WorkerScreen.Diagnostics },
                    onOpenDebug = debugContent?.let { { screen = WorkerScreen.Debug } },
                )
                WorkerScreen.Diagnostics -> DiagnosticsScreen(state, actions, onBack = { screen = WorkerScreen.Settings })
                WorkerScreen.Debug -> Column(Modifier.fillMaxSize().background(palette.canvas).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    PageTopBar("调试工具（仅调试版）", onBack = { screen = WorkerScreen.Settings }, modifier = Modifier.padding(horizontal = 12.dp))
                    Box(Modifier.weight(1f)) { debugContent?.invoke() }
                }
            }
        }
    }
}

const val SPLASH_MILLIS = 1_200L

/** 工业 HMI：隐藏系统栏，边缘上滑可临时呼出。 */
@Composable
private fun ImmersiveBars(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled && controller != null) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
