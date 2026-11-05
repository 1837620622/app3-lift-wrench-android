package com.chuankangkk.wrenchlift.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chuankangkk.wrenchlift.R
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.MeasurementViewModel
import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WrenchLiftApp(viewModel: MeasurementViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    App3FieldTheme {
        WorkerWorkbench(
            state = state,
            onConnect = viewModel::connectTcp,
            onDisconnect = viewModel::disconnect,
            onExport = viewModel::exportCurrentCsv,
            onExportSession = viewModel::exportSessionCsv,
            onLoadMoreHistory = viewModel::loadMoreHistory,
            onNewRecord = viewModel::newRecord,
            onHostChange = viewModel::updateHost,
            onPortChange = viewModel::updatePort,
            onSlabChange = viewModel::updateSlabNo,
            onPointChange = viewModel::updatePointNo,
            onWifiSettings = viewModel::openWifiSettings,
            onWifiNameChange = viewModel::updateTargetWifiName,
            debugContent = {
        BigScreen(
            state = state,
            onHostChange = viewModel::updateHost,
            onPortChange = viewModel::updatePort,
            onSlabNoChange = viewModel::updateSlabNo,
            onPointNoChange = viewModel::updatePointNo,
            onOpenWifiSettings = viewModel::openWifiSettings,
            onConnect = viewModel::connectTcp,
            onDisconnect = viewModel::disconnect,
            onEnableRemote = viewModel::enableRemoteControl,
            onDisableRemote = viewModel::disableRemoteControl,
            onStartForward = viewModel::startForward,
            onStartReverse = viewModel::startReverse,
            onStop = viewModel::stopWrench,
            onHeartbeat = viewModel::sendHeartbeat,
            onParameterModeChange = viewModel::updateParameterMode,
            onParameterTargetTorqueChange = viewModel::updateParameterTargetTorque,
            onParameterTorqueLowerChange = viewModel::updateParameterTorqueLower,
            onParameterTorqueUpperChange = viewModel::updateParameterTorqueUpper,
            onParameterTargetAngleChange = viewModel::updateParameterTargetAngle,
            onParameterAngleUpperChange = viewModel::updateParameterAngleUpper,
            onParameterAngleLowerChange = viewModel::updateParameterAngleLower,
            onWriteParameterSettings = viewModel::writeParameterSettings,
            onSave = viewModel::saveCurrentRecord,
            onExportCsv = viewModel::exportCurrentCsv,
            onConfirmEffectivePoint = viewModel::confirmEffectivePoint,
            onRemoveEffectivePoint = viewModel::removeLatestEffectivePoint,
        )
            },
        )
    }
}

@Composable
fun BigScreen(
    state: MeasurementState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSlabNoChange: (String) -> Unit,
    onPointNoChange: (String) -> Unit,
    onOpenWifiSettings: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEnableRemote: () -> Unit,
    onDisableRemote: () -> Unit,
    onStartForward: () -> Unit,
    onStartReverse: () -> Unit,
    onStop: () -> Unit,
    onHeartbeat: () -> Unit,
    onParameterModeChange: (Int) -> Unit,
    onParameterTargetTorqueChange: (String) -> Unit,
    onParameterTorqueLowerChange: (String) -> Unit,
    onParameterTorqueUpperChange: (String) -> Unit,
    onParameterTargetAngleChange: (String) -> Unit,
    onParameterAngleUpperChange: (String) -> Unit,
    onParameterAngleLowerChange: (String) -> Unit,
    onWriteParameterSettings: () -> Unit,
    onSave: () -> Unit,
    onExportCsv: () -> Unit,
    onConfirmEffectivePoint: () -> Unit,
    onRemoveEffectivePoint: () -> Unit,
) {
    var focusedChart by rememberSaveable { mutableStateOf(false) }
    var selectedMetricIndex by rememberSaveable { mutableIntStateOf(ChartMetric.FORCE.ordinal) }
    var sidePanelIndex by rememberSaveable { mutableIntStateOf(SidePanelMode.WORK.ordinal) }
    val selectedMetric = ChartMetric.entries[selectedMetricIndex]
    val selectedSidePanel = SidePanelMode.entries[sidePanelIndex]

    BackHandler(enabled = focusedChart) {
        focusedChart = false
    }
    ImmersiveSystemBars(enabled = focusedChart)

    if (focusedChart) {
        FocusedChartScreen(
            state = state,
            selectedMetric = selectedMetric,
            onMetricSelected = { selectedMetricIndex = it.ordinal },
            onBack = { focusedChart = false },
            onStopWrench = onStop,
            onSave = onSave,
            onExportCsv = onExportCsv,
            onConfirmEffectivePoint = onConfirmEffectivePoint,
            onRemoveEffectivePoint = onRemoveEffectivePoint,
        )
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .background(AppColors.background)
    ) {
        EngineeringBackground()
        FieldBackdrop()
        val availableHeight = maxHeight
        val compact = maxWidth < AppBreakpoints.expandedWidth || maxHeight < AppBreakpoints.comfortableHeight
        val stacked = maxWidth < AppBreakpoints.compactWidth
        val outerPadding = if (compact) 10.dp else 18.dp
        val gap = if (compact) 10.dp else 14.dp
        val leftWidth = if (compact) {
            (maxWidth * 0.29f).coerceIn(220.dp, 300.dp)
        } else {
            310.dp
        }
        val rightWidth = 340.dp

        if (stacked) {
            val dashboardHeight = (maxHeight * 0.72f).coerceIn(520.dp, 720.dp)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = outerPadding,
                        top = outerPadding,
                        end = outerPadding,
                        bottom = outerPadding + 18.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                HeaderBar(state, compact = true)
                RealtimeDashboard(
                    state = state,
                    onExportCsv = onExportCsv,
                    onOpenChart = { metric ->
                        selectedMetricIndex = metric.ordinal
                        focusedChart = true
                    },
                    modifier = Modifier.fillMaxWidth().height(dashboardHeight),
                )
                SurfacePanel(modifier = Modifier.fillMaxWidth().height(650.dp)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SidePanelTabs(
                            selected = selectedSidePanel,
                            onSelected = { sidePanelIndex = it.ordinal },
                            includeWork = true,
                        )
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            SidePanelContent(
                                mode = selectedSidePanel,
                                state = state,
                                onHostChange = onHostChange,
                                onPortChange = onPortChange,
                                onSlabNoChange = onSlabNoChange,
                                onPointNoChange = onPointNoChange,
                                onOpenWifiSettings = onOpenWifiSettings,
                                onConnect = onConnect,
                                onDisconnect = onDisconnect,
                                onEnableRemote = onEnableRemote,
                                onDisableRemote = onDisableRemote,
                                onStartForward = onStartForward,
                                onStartReverse = onStartReverse,
                                onStop = onStop,
                                onHeartbeat = onHeartbeat,
                                onParameterModeChange = onParameterModeChange,
                                onParameterTargetTorqueChange = onParameterTargetTorqueChange,
                                onParameterTorqueLowerChange = onParameterTorqueLowerChange,
                                onParameterTorqueUpperChange = onParameterTorqueUpperChange,
                                onParameterTargetAngleChange = onParameterTargetAngleChange,
                                onParameterAngleUpperChange = onParameterAngleUpperChange,
                                onParameterAngleLowerChange = onParameterAngleLowerChange,
                                onWriteParameterSettings = onWriteParameterSettings,
                                onSave = onSave,
                                onExportCsv = onExportCsv,
                                onConfirmEffectivePoint = onConfirmEffectivePoint,
                                onRemoveEffectivePoint = onRemoveEffectivePoint,
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = outerPadding,
                        top = outerPadding,
                        end = outerPadding,
                        bottom = outerPadding + if (compact) 12.dp else 8.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                HeaderBar(state, compact = compact)
                if (!compact && availableHeight >= AppBreakpoints.tallHeight) {
                    CriticalBanner(state)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    SurfacePanel(modifier = Modifier.fillMaxHeight().width(leftWidth)) {
                        if (compact) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                SidePanelTabs(
                                    selected = selectedSidePanel,
                                    onSelected = { sidePanelIndex = it.ordinal },
                                    includeWork = true,
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    SidePanelContent(
                                        mode = selectedSidePanel,
                                        state = state,
                                        onHostChange = onHostChange,
                                        onPortChange = onPortChange,
                                        onSlabNoChange = onSlabNoChange,
                                        onPointNoChange = onPointNoChange,
                                        onOpenWifiSettings = onOpenWifiSettings,
                                        onConnect = onConnect,
                                        onDisconnect = onDisconnect,
                                        onEnableRemote = onEnableRemote,
                                        onDisableRemote = onDisableRemote,
                                        onStartForward = onStartForward,
                                        onStartReverse = onStartReverse,
                                        onStop = onStop,
                                        onHeartbeat = onHeartbeat,
                                        onParameterModeChange = onParameterModeChange,
                                        onParameterTargetTorqueChange = onParameterTargetTorqueChange,
                                        onParameterTorqueLowerChange = onParameterTorqueLowerChange,
                                        onParameterTorqueUpperChange = onParameterTorqueUpperChange,
                                        onParameterTargetAngleChange = onParameterTargetAngleChange,
                                        onParameterAngleUpperChange = onParameterAngleUpperChange,
                                        onParameterAngleLowerChange = onParameterAngleLowerChange,
                                        onWriteParameterSettings = onWriteParameterSettings,
                                        onSave = onSave,
                                        onExportCsv = onExportCsv,
                                        onConfirmEffectivePoint = onConfirmEffectivePoint,
                                        onRemoveEffectivePoint = onRemoveEffectivePoint,
                                    )
                                }
                            }
                        } else {
                            ConnectionPanel(
                                state = state,
                                onHostChange = onHostChange,
                                onPortChange = onPortChange,
                                onSlabNoChange = onSlabNoChange,
                                onPointNoChange = onPointNoChange,
                                onOpenWifiSettings = onOpenWifiSettings,
                                onConnect = onConnect,
                                onDisconnect = onDisconnect,
                                onEnableRemote = onEnableRemote,
                                onDisableRemote = onDisableRemote,
                                onStartForward = onStartForward,
                                onStartReverse = onStartReverse,
                                onStop = onStop,
                                onHeartbeat = onHeartbeat,
                                onParameterModeChange = onParameterModeChange,
                                onParameterTargetTorqueChange = onParameterTargetTorqueChange,
                                onParameterTorqueLowerChange = onParameterTorqueLowerChange,
                                onParameterTorqueUpperChange = onParameterTorqueUpperChange,
                                onParameterTargetAngleChange = onParameterTargetAngleChange,
                                onParameterAngleUpperChange = onParameterAngleUpperChange,
                                onParameterAngleLowerChange = onParameterAngleLowerChange,
                                onWriteParameterSettings = onWriteParameterSettings,
                                onSave = onSave,
                                onExportCsv = onExportCsv,
                                onConfirmEffectivePoint = onConfirmEffectivePoint,
                                onRemoveEffectivePoint = onRemoveEffectivePoint,
                            )
                        }
                    }
                    RealtimeDashboard(
                        state = state,
                        onExportCsv = onExportCsv,
                        onOpenChart = { metric ->
                            selectedMetricIndex = metric.ordinal
                            focusedChart = true
                        },
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                    if (!compact) {
                        SurfacePanel(modifier = Modifier.fillMaxHeight().width(rightWidth)) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                SidePanelTabs(
                                    selected = if (selectedSidePanel == SidePanelMode.WORK) SidePanelMode.PROTOCOL else selectedSidePanel,
                                    onSelected = { sidePanelIndex = it.ordinal },
                                    includeWork = false,
                                )
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    when (selectedSidePanel) {
                                        SidePanelMode.WORK, SidePanelMode.PROTOCOL -> ProtocolPanel(state)
                                        SidePanelMode.POINTS -> PointMapScreen(state)
                                        SidePanelMode.LOGS -> HistoryScreen(state)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SidePanelMode(val label: String) {
    WORK("作业"),
    PROTOCOL("设备"),
    POINTS("点位"),
    LOGS("日志"),
}

@Composable
private fun SidePanelTabs(
    selected: SidePanelMode,
    onSelected: (SidePanelMode) -> Unit,
    includeWork: Boolean,
) {
    val modes = SidePanelMode.entries.filter { includeWork || it != SidePanelMode.WORK }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(AppDimens.space8),
        color = AppColors.panelInset,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, AppColors.panelLine.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppDimens.space4),
            horizontalArrangement = Arrangement.spacedBy(AppDimens.space4),
        ) {
            modes.forEach { mode ->
                val checked = mode == selected
                Button(
                    onClick = { onSelected(mode) },
                    modifier = Modifier.weight(1f).height(AppDimens.compactControlHeight),
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (checked) AppColors.info else Color.Transparent,
                        contentColor = if (checked) Color(0xFF071118) else AppColors.textSecondary,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (checked) 1.dp else 0.dp,
                        pressedElevation = 0.dp,
                    ),
                ) {
                    Text(
                        mode.label,
                        fontSize = 13.sp,
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SidePanelContent(
    mode: SidePanelMode,
    state: MeasurementState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSlabNoChange: (String) -> Unit,
    onPointNoChange: (String) -> Unit,
    onOpenWifiSettings: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEnableRemote: () -> Unit,
    onDisableRemote: () -> Unit,
    onStartForward: () -> Unit,
    onStartReverse: () -> Unit,
    onStop: () -> Unit,
    onHeartbeat: () -> Unit,
    onParameterModeChange: (Int) -> Unit,
    onParameterTargetTorqueChange: (String) -> Unit,
    onParameterTorqueLowerChange: (String) -> Unit,
    onParameterTorqueUpperChange: (String) -> Unit,
    onParameterTargetAngleChange: (String) -> Unit,
    onParameterAngleUpperChange: (String) -> Unit,
    onParameterAngleLowerChange: (String) -> Unit,
    onWriteParameterSettings: () -> Unit,
    onSave: () -> Unit,
    onExportCsv: () -> Unit,
    onConfirmEffectivePoint: () -> Unit,
    onRemoveEffectivePoint: () -> Unit,
) {
    when (mode) {
        SidePanelMode.WORK -> ConnectionPanel(
            state = state,
            onHostChange = onHostChange,
            onPortChange = onPortChange,
            onSlabNoChange = onSlabNoChange,
            onPointNoChange = onPointNoChange,
            onOpenWifiSettings = onOpenWifiSettings,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onEnableRemote = onEnableRemote,
            onDisableRemote = onDisableRemote,
            onStartForward = onStartForward,
            onStartReverse = onStartReverse,
            onStop = onStop,
            onHeartbeat = onHeartbeat,
            onParameterModeChange = onParameterModeChange,
            onParameterTargetTorqueChange = onParameterTargetTorqueChange,
            onParameterTorqueLowerChange = onParameterTorqueLowerChange,
            onParameterTorqueUpperChange = onParameterTorqueUpperChange,
            onParameterTargetAngleChange = onParameterTargetAngleChange,
            onParameterAngleUpperChange = onParameterAngleUpperChange,
            onParameterAngleLowerChange = onParameterAngleLowerChange,
            onWriteParameterSettings = onWriteParameterSettings,
            onSave = onSave,
            onExportCsv = onExportCsv,
            onConfirmEffectivePoint = onConfirmEffectivePoint,
            onRemoveEffectivePoint = onRemoveEffectivePoint,
        )
        SidePanelMode.PROTOCOL -> ProtocolPanel(state)
        SidePanelMode.POINTS -> PointMapScreen(state)
        SidePanelMode.LOGS -> HistoryScreen(state)
    }
}

@Composable
private fun ImmersiveSystemBars(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        val activity = view.context.findActivity()
        val controller = activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, view)
        }
        if (enabled) {
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (enabled) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun EngineeringBackground() {
    val fineStepDp = 28.dp
    val gridColor = AppColors.grid
    val axisColor = AppColors.axis
    val backgroundColor = AppColors.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {
                    drawRect(backgroundColor)
                    val fineStep = fineStepDp.toPx()
                    val majorStep = fineStep * 4f
                    var x = 0f
                    while (x <= size.width) {
                        val color = if ((x / majorStep).toInt().toFloat() == x / majorStep) {
                            gridColor.copy(alpha = 0.22f)
                        } else {
                            gridColor.copy(alpha = 0.08f)
                        }
                        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        x += fineStep
                    }
                    var y = 0f
                    while (y <= size.height) {
                        val color = if ((y / majorStep).toInt().toFloat() == y / majorStep) {
                            gridColor.copy(alpha = 0.22f)
                        } else {
                            gridColor.copy(alpha = 0.08f)
                        }
                        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        y += fineStep
                    }
                    drawLine(
                        gridColor.copy(alpha = 0.24f),
                        Offset(size.width * 0.08f, size.height * 0.88f),
                        Offset(size.width * 0.92f, size.height * 0.88f),
                        strokeWidth = 2.dp.toPx(),
                    )
                    drawLine(
                        axisColor.copy(alpha = 0.24f),
                        Offset(size.width * 0.08f, size.height * 0.91f),
                        Offset(size.width * 0.92f, size.height * 0.91f),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
            }
    )
}

@Composable
private fun FieldBackdrop() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth < 900.dp || maxHeight < 620.dp) return@BoxWithConstraints
        Image(
            painter = painterResource(id = R.drawable.app3_field_backdrop),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.10f),
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterStart,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            AppColors.background.copy(alpha = 0.34f),
                            Color.Transparent,
                            AppColors.background.copy(alpha = 0.76f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun HeaderBar(state: MeasurementState, compact: Boolean) {
    Surface(
        color = AppColors.panelStrong,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        border = BorderStroke(1.dp, AppColors.panelLine.copy(alpha = 0.42f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(AppColors.panelStrong, Color(0xFF334044), AppColors.panelStrong),
                    ),
                )
                .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 9.dp else 14.dp),
        ) {
            val narrow = maxWidth < 640.dp
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeaderTitleBlock(state = state, compact = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatusChip(label = state.connectionState.label, color = if (state.connectionState.isOnline) AppColors.teal else AppColors.blue)
                        StatusChip(label = "电池 ${state.batteryPercent?.let { "$it%" } ?: "--"}", color = AppColors.blue)
                        StatusChip(label = "帧 ${state.receivedFrameCount}", color = AppColors.teal)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HeaderTitleBlock(state = state, compact = compact, modifier = Modifier.weight(1f))
                    StatusChip(label = state.connectionState.label, color = if (state.connectionState.isOnline) AppColors.teal else AppColors.blue)
                    StatusChip(label = "电池 ${state.batteryPercent?.let { "$it%" } ?: "--"}", color = AppColors.blue)
                    if (!compact) {
                        StatusChip(label = "帧数 ${state.receivedFrameCount}", color = AppColors.teal)
                        StatusChip(label = "最后帧 ${state.lastFrameReceiveTime.formatTimeOrDash()}", color = AppColors.amber)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderTitleBlock(state: MeasurementState, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "WiFi 扭矩扳手顶升现场工作台",
            color = AppColors.textPrimary,
            fontSize = if (compact) 21.sp else 27.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "板号 ${state.session.slabNo} · 点位 ${state.session.pointNo} · 螺栓 ${state.currentBoltNo} · 工号 ${state.employeeId}",
            color = AppColors.textSecondary,
            fontSize = if (compact) 12.sp else 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CriticalBanner(state: MeasurementState) {
    val color = state.warningLevel.color()
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.2.dp, color.copy(alpha = 0.58f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(modifier = Modifier.weight(0.22f)) {
                Text("临界点预警", color = AppColors.textSecondary, fontSize = 13.sp)
                Text(
                    state.warningLevel.labelText(),
                    color = color,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
            }
            Column(modifier = Modifier.weight(0.56f)) {
                Text(
                    state.warningMessage,
                    color = AppColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "临界力还没有现场标定，现在只做趋势提醒；正式验收前需要录入标定参数。",
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.22f)) {
                Text("当前计算力", color = AppColors.textSecondary, fontSize = 13.sp)
                Text(
                    state.currentForceKn?.let { "%.2f kN".format(it) } ?: "未标定",
                    color = color,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ProtocolPanel(state: MeasurementState) {
    ScrollColumn {
        SectionTitle("设备通讯明细")
        ProtocolStatusGrid(state)
        SectionTitle("手机发给扳手")
        ProtocolHexBlock(
            title = state.lastCommandLabel,
            hex = state.lastSentHex.ifBlank { "尚未发送命令" },
            color = AppColors.amber,
        )
        SectionTitle("扳手回传数据")
        ProtocolHexBlock(
            title = state.lastPacketFunction?.let { "最近回传：${functionLabel(it)}" } ?: "尚未接收",
            hex = state.lastRawHex.ifBlank { "等待设备二进制帧" },
            color = AppColors.teal,
        )
        ResultValueList(state)
    }
}

@Composable
private fun ProtocolStatusGrid(state: MeasurementState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MiniMetric("过程数据", "曲线采样点", "${state.recentPoints.size}/50", AppColors.teal, Modifier.weight(1f))
            MiniMetric("最终结果", "扳手回传", state.resultStatus, state.warningLevel.color(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            MiniMetric("电池", "剩余电量", state.batteryPercent?.let { "$it%" } ?: "--", AppColors.blue, Modifier.weight(1f))
            MiniMetric("设备 SN", "扳手编号", state.deviceSn.ifBlank { "--" }, AppColors.amber, Modifier.weight(1f))
        }
        MiniMetric("最近消息", "自动解析结果", state.lastProtocolSummary, AppColors.info, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MiniMetric(label: String, caption: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(AppColors.panelInset, MaterialTheme.shapes.medium)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(caption, color = AppColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = AppColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProtocolHexBlock(title: String, hex: String, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.panelInset, MaterialTheme.shapes.medium)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(hex, color = AppColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ResultValueList(state: MeasurementState) {
    val result = state.lastResult
    val pulse = state.lastPulseResult
    SectionTitle("最终结果明细")
    InfoLine("工号", result?.employeeId?.ifBlank { state.employeeId } ?: state.employeeId)
    InfoLine("螺栓号", "${result?.boltNo ?: state.currentBoltNo}")
    InfoLine("目标/实际扭矩", "${result?.targetTorqueNm.formatDash()} / ${result?.actualTorqueNm.formatDash()} Nm")
    InfoLine("目标/实际角度", "${result?.targetAngleDeg.formatDash()} / ${result?.actualAngleDeg.formatDash()} °")
    InfoLine("扭矩上下限", "${result?.torqueLowerNm.formatDash()} - ${result?.torqueUpperNm.formatDash()} Nm")
    InfoLine("角度上下限", "${result?.angleLowerDeg.formatDash()} - ${result?.angleUpperDeg.formatDash()} °")
    InfoLine("目标顶升力", if (result?.targetTorqueNm == null) "--" else "待标定后换算")
    if (pulse != null) {
        SectionTitle("特殊模式结果")
        InfoLine("状态", pulse.status.label)
        InfoLine("扭矩", "${pulse.targetTorqueNm.formatDash()} / ${pulse.actualTorqueNm.formatDash()} Nm")
        InfoLine("角度", "${pulse.actualAngleDeg.formatDash()} °")
    }
}

@Composable
fun SurfacePanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = AppColors.panel,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, AppColors.panelLine.copy(alpha = 0.44f)),
        content = content,
    )
}

@Composable
fun StatusChip(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f)),
    ) {
        Text(
            text = label,
            color = AppColors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppColors.teal,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(AppDimens.controlHeight),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color(0xFF07120F),
            disabledContainerColor = AppColors.panelRaised,
            disabledContentColor = AppColors.textSecondary,
        ),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(AppDimens.compactControlHeight),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, AppColors.panelLine.copy(alpha = if (enabled) 0.85f else 0.35f)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = AppColors.textPrimary,
            disabledContentColor = AppColors.textMuted,
        ),
    ) {
        Text(label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ScrollColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = AppDimens.space16, top = AppDimens.space16, end = AppDimens.space16, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = { content() },
    )
}

private fun Long?.formatTimeOrDash(): String {
    if (this == null) return "--"
    return SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(this))
}

private fun Double?.formatDash(): String = this?.let { "%.1f".format(it) } ?: "--"

internal fun functionLabel(functionCode: Int): String = when (functionCode) {
    WrenchPacket.FUNC_STOP_RUN -> "扳手动作"
    WrenchPacket.FUNC_STATUS -> "设备状态"
    WrenchPacket.FUNC_ACK -> "设备应答"
    WrenchPacket.FUNC_BATTERY -> "电量回传"
    WrenchPacket.FUNC_REMOTE_CONTROL -> "手机控制"
    WrenchPacket.FUNC_PARAM_SET -> "参数设置"
    WrenchPacket.FUNC_TORQUE_ANGLE -> "扭矩转角过程"
    WrenchPacket.FUNC_RESULT -> "最终结果"
    WrenchPacket.FUNC_RESULT_CONFIRM -> "结果确认"
    WrenchPacket.FUNC_TIME_REQUEST -> "请求校时"
    WrenchPacket.FUNC_TIME_SYNC -> "时间校准"
    WrenchPacket.FUNC_NUT_RESET -> "螺母计数"
    WrenchPacket.FUNC_QUERY_SN -> "查询编号"
    WrenchPacket.FUNC_REPLY_SN -> "设备编号"
    WrenchPacket.FUNC_HEARTBEAT -> "连接保活"
    WrenchPacket.FUNC_GPS -> "位置数据"
    WrenchPacket.FUNC_PULSE_PARAM_SET_RC283F,
    WrenchPacket.FUNC_PULSE_PARAM_SET_RCH -> "特殊模式参数"
    WrenchPacket.FUNC_PULSE_RESULT -> "特殊模式结果"
    else -> "未识别数据"
}
