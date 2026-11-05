package com.chuankangkk.wrenchlift.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.chuankangkk.wrenchlift.measurement.MeasurementState

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
