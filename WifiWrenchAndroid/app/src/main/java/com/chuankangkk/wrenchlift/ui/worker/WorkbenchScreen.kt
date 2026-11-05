package com.chuankangkk.wrenchlift.ui.worker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.Freshness
import com.chuankangkk.wrenchlift.measurement.LinkPhase
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.MotionCommand
import com.chuankangkk.wrenchlift.measurement.RemoteGate
import com.chuankangkk.wrenchlift.measurement.TorqueAngleWindow
import com.chuankangkk.wrenchlift.measurement.WorkerText
import com.chuankangkk.wrenchlift.measurement.hasWrenchMeasurementData
import kotlinx.coroutines.delay

/**
 * 正式工人工作台（设计规范 §4 单屏分区）：A 顶栏 · B 左读数 · C 中主图 · D 右控制柱 · 单行提示条。
 * 主台无纵向滚动；参数走右侧侧滑；返回 = 结束会话并回到测点/记录（不等于停止扳手）。
 */
@Composable
fun WorkbenchScreen(
    state: MeasurementState,
    actions: WorkerActions,
    onLeave: () -> Unit,
    nowMillis: Long = rememberNowMillis(),
) {
    val p = palette
    var confirmMotion by remember { mutableStateOf<MotionCommand?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showParams by rememberSaveable { mutableStateOf(false) }
    val sessionActive = state.connectionState.isOnline || state.linkPhase in setOf(LinkPhase.PROBING, LinkPhase.READY, LinkPhase.WAITING_REPLY, LinkPhase.DROPPED, LinkPhase.FAILED, LinkPhase.NO_WIFI)
    val requestLeave = { if (sessionActive) confirmLeave = true else onLeave() }
    BackHandler(enabled = !showParams) { requestLeave() }

    LaunchedEffect(state.controlMessage) {
        if (state.controlMessage.isNotBlank()) {
            delay(6_000)
            actions.clearControlMessage()
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(p.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val compact = maxHeight < 430.dp
        // 以 480dp 高的横屏手机为基准，平板/大屏按高度等比放大主读数（相对关系不变）。
        val readoutScale = (maxHeight.value / 480f).coerceIn(1f, 1.8f)
        Column(Modifier.fillMaxSize().padding(horizontal = WrenchDimens.screenEdge)) {
            WorkbenchTopBar(state, actions, onBack = requestLeave)
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = if (compact) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(WrenchDimens.gap),
            ) {
                BoxWithConstraints(Modifier.weight(0.24f).fillMaxHeight()) {
                    // 读数柱越高，数字越大（宽度不够时 FitNumber 再缩），保证主读数占柱高的主要部分。
                    val columnScale = (maxHeight.value / 260f).coerceIn(1f, 2.4f).coerceAtLeast(readoutScale)
                    ReadoutPanel(state, nowMillis, columnScale, compact, Modifier.fillMaxSize())
                }
                ChartPanel(state, nowMillis, compact, Modifier.weight(0.51f).fillMaxHeight())
                ControlPanel(
                    state = state,
                    actions = actions,
                    compact = compact,
                    onForward = { confirmMotion = MotionCommand.FORWARD },
                    onReverse = { confirmMotion = MotionCommand.REVERSE },
                    onParams = { showParams = true },
                    modifier = Modifier.weight(0.25f).fillMaxHeight(),
                )
            }
            BottomStrip(state, Modifier.padding(bottom = 6.dp))
        }
        ParameterSheet(
            visible = showParams,
            state = state,
            onDismiss = { showParams = false },
            onSave = { draft ->
                actions.writeParameters(draft)
                showParams = false
            },
        )
    }

    confirmMotion?.let { direction ->
        val forward = direction == MotionCommand.FORWARD
        WrenchDialog(
            title = if (forward) "确认正转？" else "确认反转？",
            message = if (forward) "确认以正转方向驱动扳手？请确认现场安全。" else "确认以反转方向驱动扳手？请确认现场安全。",
            confirmText = if (forward) "正转" else "反转",
            onConfirm = {
                confirmMotion = null
                if (forward) actions.forward() else actions.reverse()
            },
            onDismiss = { confirmMotion = null },
            modifier = Modifier.testTag("confirm_motion"),
        )
    }

    if (confirmLeave) {
        val mayBeMoving = state.remoteEnabled || state.motion == MotionCommand.FORWARD || state.motion == MotionCommand.REVERSE
        WrenchDialog(
            title = "结束会话？",
            message = "结束仅断开本机会话与采集，不会自动停止扳手。是否仍要结束？",
            confirmText = if (mayBeMoving) "直接结束" else "结束",
            confirmTone = ButtonTone.Neutral,
            onConfirm = {
                confirmLeave = false
                actions.endSession(false)
                onLeave()
            },
            onDismiss = { confirmLeave = false },
            extra = if (mayBeMoving && state.connectionState.isOnline) {
                {
                    ControlButton("先停止扳手再结束", WrenchIcon.Stop, onClick = {
                        confirmLeave = false
                        actions.endSession(true)
                        onLeave()
                    }, tone = ButtonTone.Danger, height = WrenchDimens.touch)
                }
            } else {
                null
            },
            modifier = Modifier.testTag("confirm_leave"),
        )
    }

    ExportDialogs(state, actions)
}

@Composable
fun rememberNowMillis(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

// ---------------- A 顶栏 ----------------

@Composable
private fun WorkbenchTopBar(state: MeasurementState, actions: WorkerActions, onBack: () -> Unit) {
    val p = palette
    Row(
        Modifier.fillMaxWidth().heightIn(min = WrenchDimens.topBarHeight).testTag("top_bar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack, modifier = Modifier.testTag("back"))
        Column(Modifier.padding(start = 4.dp).weight(1f)) {
            Text(
                "板号 ${state.session.slabNo} · 测点 ${state.session.pointNo}",
                style = WrenchType.bodyStrong, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(WorkerText.sn(state), style = WrenchType.captionStyle, color = p.textSecondary, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                val battery = state.batteryPercent
                LineIcon(WrenchIcon.Battery, if (battery != null && battery < 20) p.danger else p.textSecondary, size = 16.dp, level = (battery ?: 0) / 100f)
                Spacer(Modifier.width(4.dp))
                Text(WorkerText.battery(state), style = WrenchType.captionStyle, color = if (battery != null && battery < 20) p.danger else p.textSecondary, maxLines = 1)
            }
        }
        val linkColor = linkColor(state.linkPhase)
        val linkAction: (() -> Unit)? = when (state.linkPhase) {
            LinkPhase.FAILED, LinkPhase.DROPPED, LinkPhase.IDLE -> actions.connect
            LinkPhase.NO_WIFI -> actions.openWifiSettings
            else -> null
        }
        StatusPill(WorkerText.linkShort(state), linkColor, icon = WrenchIcon.Wifi, onClick = linkAction, modifier = Modifier.testTag("link_status"))
        StatusPill(
            WorkerText.remote(state),
            if (state.remoteEnabled) p.accent else p.warn,
            modifier = Modifier.testTag("remote_status"),
        )
    }
}

@Composable
fun linkColor(phase: LinkPhase): Color {
    val p = palette
    return when (phase) {
        LinkPhase.READY -> p.ok
        LinkPhase.WAITING_REPLY, LinkPhase.PROBING -> p.warn
        LinkPhase.FAILED, LinkPhase.DROPPED, LinkPhase.NO_WIFI -> p.danger
        LinkPhase.IDLE -> p.textSecondary
    }
}

// ---------------- B 左读数 ----------------

@Composable
private fun ReadoutPanel(state: MeasurementState, nowMillis: Long, scale: Float, compact: Boolean, modifier: Modifier) {
    val p = palette
    val hasData = state.hasWrenchMeasurementData()
    val freshness = WorkerText.freshness(state, nowMillis)
    val valueColor = if (freshness == Freshness.Live || freshness is Freshness.Delayed) p.textPrimary else if (hasData) p.textSecondary else p.textTertiary
    Panel(modifier.testTag("readouts"), padding = PaddingValues(horizontal = 12.dp, vertical = if (compact) 8.dp else 12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            ReadoutValue("扭矩", "N·m", WorkerText.readout(state.currentTorqueNm, hasData), WrenchType.display * scale, valueColor, "readout_torque")
            Hairline()
            ReadoutValue("转角", "°", WorkerText.readout(state.currentAngleDeg, hasData), WrenchType.title * scale, valueColor, "readout_angle")
        }
        Hairline(Modifier.padding(vertical = 6.dp))
        FreshnessLine(freshness, hasData)
        val result = state.lastResult
        val resultText = when {
            result != null -> "结果：${result.status.label}" + (result.actualTorqueNm?.let { " · ${WorkerText.readout(it, true)} N·m" } ?: "")
            state.lastPulseResult != null -> "结果：${state.lastPulseResult.status.label}"
            else -> "结果：暂无"
        }
        Text(resultText, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!compact) Text("分离：待分析", style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textTertiary, maxLines = 1)
    }
}

@Composable
private fun ReadoutValue(label: String, unit: String, value: String, size: androidx.compose.ui.unit.TextUnit, color: Color, tag: String) {
    val p = palette
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = WrenchType.headlineStyle, color = p.textSecondary)
            Spacer(Modifier.width(6.dp))
            Text(unit, style = WrenchType.bodyStyle.copy(fontSize = 15.sp), color = p.textTertiary, modifier = Modifier.padding(bottom = 1.dp))
        }
        FitNumber(value, size, color, Modifier.fillMaxWidth())
    }
}

@Composable
private fun FreshnessLine(freshness: Freshness, hasData: Boolean) {
    val p = palette
    val (text, color) = when (freshness) {
        Freshness.Live -> "实时更新" to p.ok
        is Freshness.Delayed -> "数据延迟 ${freshness.seconds} 秒" to p.warn
        Freshness.Retained -> "已断开 · 保留值" to p.textSecondary
        Freshness.Waiting -> (if (hasData) "保留值" else "等待数据") to p.textTertiary
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("freshness")) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = color, maxLines = 1)
    }
}

// ---------------- C 中主图 ----------------

@Composable
private fun ChartPanel(state: MeasurementState, nowMillis: Long, compact: Boolean, modifier: Modifier) {
    val p = palette
    val view = remember(state.recentPoints) { TorqueAngleWindow.view(state.recentPoints) }
    val (emptyTitle, emptyHint) = when {
        state.connectionState.isOnline && !state.remoteEnabled -> "等待扳手数据" to "开远程并转动后自动画线"
        state.connectionState.isOnline -> "等待扳手数据" to "扳手转动后自动画线"
        else -> WorkerText.link(state) to (WorkerText.linkHint(state) ?: "连上扳手后自动画线")
    }
    Panel(modifier, padding = PaddingValues(start = 10.dp, end = 10.dp, top = if (compact) 6.dp else 10.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("扭矩–转角", style = WrenchType.headlineStyle, color = p.textPrimary)
            Spacer(Modifier.weight(1f))
            val caption = buildString {
                append("已入库 ${state.persistedPointCount} 点")
                if (!view.isEmpty && (view.sampled || view.windowed)) append(" · 显示窗口")
            }
            if (caption.isNotEmpty()) Text(caption, style = WrenchType.captionStyle, color = p.textSecondary, maxLines = 1)
        }
        LiveTorqueChart(
            view = view,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp),
            emptyTitle = emptyTitle,
            emptyHint = emptyHint,
        )
    }
}

// ---------------- D 右控制柱 ----------------

@Composable
private fun ControlPanel(
    state: MeasurementState,
    actions: WorkerActions,
    compact: Boolean,
    onForward: () -> Unit,
    onReverse: () -> Unit,
    onParams: () -> Unit,
    modifier: Modifier,
) {
    val p = palette
    val canMove = RemoteGate.canMove(state)
    val online = state.connectionState.isOnline
    val gap: Dp = if (compact) 8.dp else WrenchDimens.controlGap
    val motionText = WorkerText.motion(state)
    val motionColor = when {
        !online -> p.textTertiary
        !state.remoteEnabled -> p.warn
        state.motion == MotionCommand.FORWARD || state.motion == MotionCommand.REVERSE -> p.accent
        else -> p.ok
    }
    Panel(modifier.testTag("controls"), padding = PaddingValues(if (compact) 8.dp else 10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 200.dp
            ToggleRow(
                title = if (narrow) "远程" else "远程控制",
                checked = state.remoteEnabled,
                onCheckedChange = { wanted -> if (wanted) actions.enableRemote() else actions.disableRemote() },
                enabled = RemoteGate.canToggleRemote(state),
                icon = if (narrow) null else WrenchIcon.Remote,
                modifier = Modifier.fillMaxWidth().testTag("remote_toggle"),
            )
        }
        AnimatedContent(motionText, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "motion") { text ->
            Text(
                text, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = motionColor, maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).testTag("motion_status"),
            )
        }
        // 按控制柱实际可用高度决定：一列大按钮（够高）或两两并排（矮屏 / 大字体）；按钮随高度放大，绝不裁切。
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val avail = maxHeight
            val singleMin = WrenchDimens.motionButton * 2 + WrenchDimens.stopButton + WrenchDimens.touch * 2 + gap * 4
            if (avail >= singleMin) {
                val unit = ((avail - gap * 4) / 4.85f).coerceIn(WrenchDimens.motionButton, 84.dp)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)) {
                    ControlButton("正转", WrenchIcon.RotateForward, onForward, Modifier.fillMaxWidth().testTag("forward"), ButtonTone.Accent, enabled = canMove, filled = false, height = unit)
                    ControlButton("反转", WrenchIcon.RotateReverse, onReverse, Modifier.fillMaxWidth().testTag("reverse"), ButtonTone.Accent, enabled = canMove, filled = false, height = unit)
                    ControlButton("停止扳手", WrenchIcon.Stop, actions.stop, Modifier.fillMaxWidth().testTag("stop"), ButtonTone.Danger, enabled = true, filled = true,
                        height = (unit * 1.15f).coerceAtLeast(WrenchDimens.stopButton))
                    ControlButton("参数", WrenchIcon.Sliders, onParams, Modifier.fillMaxWidth().testTag("params"), height = (unit * 0.85f).coerceAtLeast(WrenchDimens.touch))
                    ControlButton(if (state.exportInProgress) "导出中…" else "导出 Excel", WrenchIcon.Export, actions.export, Modifier.fillMaxWidth().testTag("export"),
                        enabled = !state.exportInProgress && state.hasWrenchMeasurementData(), height = (unit * 0.85f).coerceAtLeast(WrenchDimens.touch))
                }
            } else {
                val rowGap = gap
                val unit = ((avail - rowGap * 2) / 3.1f).coerceIn(44.dp, WrenchDimens.motionButton)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(rowGap, Alignment.CenterVertically)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        ControlButton("正转", null, onForward, Modifier.weight(1f).testTag("forward"), ButtonTone.Accent, enabled = canMove, filled = false, height = unit)
                        ControlButton("反转", null, onReverse, Modifier.weight(1f).testTag("reverse"), ButtonTone.Accent, enabled = canMove, filled = false, height = unit)
                    }
                    ControlButton("停止扳手", WrenchIcon.Stop, actions.stop, Modifier.fillMaxWidth().testTag("stop"), ButtonTone.Danger, enabled = true, filled = true, height = unit * 1.1f)
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        ControlButton("参数", null, onParams, Modifier.weight(1f).testTag("params"), height = (unit * 0.92f).coerceAtLeast(44.dp))
                        ControlButton(if (state.exportInProgress) "导出中" else "导出", null, actions.export, Modifier.weight(1f).testTag("export"),
                            enabled = !state.exportInProgress && state.hasWrenchMeasurementData(), height = (unit * 0.92f).coerceAtLeast(44.dp))
                    }
                }
            }
        }
    }
}

// ---------------- 单行提示条 ----------------

@Composable
private fun BottomStrip(state: MeasurementState, modifier: Modifier = Modifier) {
    val p = palette
    Row(modifier.fillMaxWidth().height(28.dp).testTag("bottom_strip"), verticalAlignment = Alignment.CenterVertically) {
        Text(
            WorkerText.WINDOW_NOTE,
            style = WrenchType.captionStyle, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val message = state.controlMessage.ifBlank { "结束会话不会停止扳手" }
        Text(
            message, style = WrenchType.captionStyle.copy(fontSize = 13.sp),
            color = if (state.controlMessage.isBlank()) p.textTertiary else p.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).testTag("control_message"),
        )
    }
}
