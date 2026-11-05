package com.chuankangkk.wrenchlift.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.chuankangkk.wrenchlift.connection.ConnectionState
import com.chuankangkk.wrenchlift.measurement.LinkPhase
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.ui.worker.SplashScreen
import com.chuankangkk.wrenchlift.ui.worker.WifiWrenchWorkerApp
import com.chuankangkk.wrenchlift.ui.worker.WorkerActions
import com.chuankangkk.wrenchlift.ui.worker.WorkerScreen
import com.chuankangkk.wrenchlift.ui.worker.WrenchWorkerTheme

// ---- 正式工人页预览（仅调试源码；不含任何过程数据，曲线区显示等待态） ----

@Preview(name = "工人工作台 640×360", widthDp = 640, heightDp = 360, showBackground = true)
@Composable
private fun WorkerWorkbenchPhonePreview() {
    WifiWrenchWorkerApp(
        state = MeasurementState(linkPhase = LinkPhase.READY, connectionState = ConnectionState.Connected("192.168.4.1", 7888), batteryPercent = 82),
        actions = WorkerActions(), debugContent = null, startScreen = WorkerScreen.Workbench, immersive = false,
    )
}

@Preview(name = "工人工作台 平板 1280×800", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
private fun WorkerWorkbenchTabletPreview() {
    WifiWrenchWorkerApp(
        state = MeasurementState(linkPhase = LinkPhase.PROBING, connectionState = ConnectionState.Connecting),
        actions = WorkerActions(), debugContent = null, startScreen = WorkerScreen.Workbench, immersive = false,
    )
}

@Preview(name = "连接引导", widthDp = 853, heightDp = 480, showBackground = true)
@Composable
private fun WorkerGuidePreview() {
    WifiWrenchWorkerApp(
        state = MeasurementState(linkPhase = LinkPhase.NO_WIFI, targetWifiName = "WRENCH-500"),
        actions = WorkerActions(), debugContent = null, startScreen = WorkerScreen.Guide, immersive = false,
    )
}

@Preview(name = "启动页", widthDp = 853, heightDp = 480)
@Composable
private fun WorkerSplashPreview() {
    WrenchWorkerTheme(dark = true) { SplashScreen(animate = false) }
}

@Preview(
    name = "现场工作台 横屏",
    widthDp = 1500,
    heightDp = 900,
    showBackground = true,
    backgroundColor = 0xFF202224,
)
@Composable
private fun BigScreenLandscapePreview() {
    BigScreenPreviewBody(state = previewWaitingState())
}

@Preview(
    name = "现场工作台 竖屏",
    widthDp = 430,
    heightDp = 920,
    showBackground = true,
    backgroundColor = 0xFF202224,
)
@Composable
private fun BigScreenPortraitPreview() {
    BigScreenPreviewBody(state = previewWaitingState())
}

@Preview(
    name = "现场工作台 等待真实数据",
    widthDp = 430,
    heightDp = 920,
    showBackground = true,
    backgroundColor = 0xFF202224,
)
@Composable
private fun BigScreenWaitingPreview() {
    BigScreenPreviewBody(state = previewWaitingState())
}

@Preview(
    name = "曲线大屏 横屏手机",
    widthDp = 900,
    heightDp = 430,
    showBackground = true,
    backgroundColor = 0xFF151A1D,
)
@Composable
private fun FocusedChartLandscapePreview() {
    FocusedChartPreviewBody(
        state = previewWaitingState(),
        selectedMetric = ChartMetric.TORQUE,
    )
}

@Preview(
    name = "曲线大屏 竖屏手机",
    widthDp = 430,
    heightDp = 920,
    showBackground = true,
    backgroundColor = 0xFF151A1D,
)
@Composable
private fun FocusedChartPortraitPreview() {
    FocusedChartPreviewBody(
        state = previewWaitingState(),
        selectedMetric = ChartMetric.TORQUE,
    )
}

@Preview(
    name = "曲线大屏 等待真实数据",
    widthDp = 430,
    heightDp = 920,
    showBackground = true,
    backgroundColor = 0xFF151A1D,
)
@Composable
private fun FocusedChartWaitingPreview() {
    FocusedChartPreviewBody(
        state = previewWaitingState(),
        selectedMetric = ChartMetric.TORQUE,
    )
}

@Composable
private fun BigScreenPreviewBody(state: MeasurementState) {
    WifiWrenchFieldTheme {
        BigScreen(
            state = state,
            onHostChange = {},
            onPortChange = {},
            onSlabNoChange = {},
            onPointNoChange = {},
            onOpenWifiSettings = {},
            onConnect = {},
            onDisconnect = {},
            onEnableRemote = {},
            onDisableRemote = {},
            onStartForward = {},
            onStartReverse = {},
            onStop = {},
            onHeartbeat = {},
            onParameterModeChange = {},
            onParameterTargetTorqueChange = {},
            onParameterTorqueLowerChange = {},
            onParameterTorqueUpperChange = {},
            onParameterTargetAngleChange = {},
            onParameterAngleUpperChange = {},
            onParameterAngleLowerChange = {},
            onWriteParameterSettings = {},
            onSave = {},
            onExportCsv = {},
            onConfirmEffectivePoint = {},
            onRemoveEffectivePoint = {},
        )
    }
}

@Composable
private fun FocusedChartPreviewBody(state: MeasurementState, selectedMetric: ChartMetric) {
    WifiWrenchFieldTheme {
        FocusedChartScreen(
            state = state,
            selectedMetric = selectedMetric,
            onMetricSelected = {},
            onBack = {},
            onStopWrench = {},
            onSave = {},
            onExportCsv = {},
            onConfirmEffectivePoint = {},
            onRemoveEffectivePoint = {},
        )
    }
}

private fun previewWaitingState(): MeasurementState = MeasurementState(
    currentWifiName = "--",
    saveStatus = "尚未导出",
    networkDiagnosticSummary = "请先连接扳手 Wi-Fi，再检查 TCP 端口",
    warningMessage = "等待真实扳手 TCP 数据",
)
