package com.chuankangkk.wrenchlift.ui.worker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.Freshness
import com.chuankangkk.wrenchlift.measurement.LinkPhase
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.MotionCommand
import com.chuankangkk.wrenchlift.measurement.RemoteGate
import com.chuankangkk.wrenchlift.measurement.TorqueAngleView
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
    val view = remember(state.recentPoints) { TorqueAngleWindow.view(state.recentPoints) }
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
        // 系统栏可见 / 分屏 / 大字体时的矮屏：压缩顶栏与提示条，运动提示移到提示条，按钮最小 40dp，保证 6 个控制键都在屏内。
        val short = maxHeight < 340.dp
        val tiny = maxHeight < 300.dp
        // 以 480dp 高的横屏手机为基准，平板/大屏按高度等比放大主读数（相对关系不变）。
        val readoutScale = (maxHeight.value / 480f).coerceIn(1f, 1.8f)
        Column(Modifier.fillMaxSize().padding(horizontal = WrenchDimens.screenEdge)) {
            WorkbenchTopBar(state, actions, onBack = requestLeave, short = short)
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(vertical = if (compact) 4.dp else 8.dp),
                horizontalArrangement = Arrangement.spacedBy(WrenchDimens.gap),
            ) {
                BoxWithConstraints(Modifier.weight(0.24f).fillMaxHeight()) {
                    // 读数柱越高，数字越大（宽度不够时 FitNumber 再缩），保证主读数占柱高的主要部分。
                    val columnScale = (maxHeight.value / 260f).coerceIn(1f, 2.4f).coerceAtLeast(readoutScale)
                    ReadoutPanel(state, view, nowMillis, columnScale, compact, Modifier.fillMaxSize(), short = short)
                }
                ChartPanel(state, view, compact, Modifier.weight(0.51f).fillMaxHeight(), tiny = tiny)
                ControlPanel(
                    state = state,
                    actions = actions,
                    compact = compact,
                    short = short,
                    onForward = { confirmMotion = MotionCommand.FORWARD },
                    onReverse = { confirmMotion = MotionCommand.REVERSE },
                    onParams = { showParams = true },
                    modifier = Modifier.weight(0.25f).fillMaxHeight(),
                )
            }
            if (!tiny) BottomStrip(state, short, Modifier.padding(bottom = if (short) 2.dp else 6.dp)) else Spacer(Modifier.height(4.dp))
        }
        ParameterSheet(
            visible = showParams,
            state = state,
            onDismiss = { showParams = false },
            onSave = { draft ->
                actions.writeParameters(draft)
                showParams = false
            },
            onSavePulse = { draft ->
                actions.writePulseParameters(draft)
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

/** 顶栏：分格显示 板号 · 测点 · SN · 电量 · 通信 · 远程，最右「返回」（与产品方工作台渲染一致）。 */
@Composable
private fun WorkbenchTopBar(state: MeasurementState, actions: WorkerActions, onBack: () -> Unit, short: Boolean = false) {
    val p = palette
    val battery = state.batteryPercent
    val linkAction: (() -> Unit)? = when (state.linkPhase) {
        LinkPhase.FAILED, LinkPhase.DROPPED, LinkPhase.IDLE -> actions.connect
        LinkPhase.NO_WIFI -> actions.openWifiSettings
        else -> null
    }
    val barHeight = if (short) WrenchDimens.touch else WrenchDimens.topBarHeight
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = if (short) 2.dp else 6.dp)
            .heightIn(min = barHeight)
            .clip(WrenchDimens.panelShape)
            .background(p.panel)
            .border(1.dp, p.hairline, WrenchDimens.panelShape)
            .testTag("top_bar"),
    ) {
        // 窄屏或大字体：值用最短说法、SN 缩字号，保证 7 格都看得见不被截断。
        val tight = maxWidth / LocalDensity.current.fontScale < 760.dp
        Row(Modifier.fillMaxWidth().heightIn(min = barHeight).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusCell("板号", state.session.slabNo, Modifier.weight(1.05f), emphasize = true, tight = tight, dense = short)
            CellDivider()
            StatusCell("测点", state.session.pointNo, Modifier.weight(0.8f), emphasize = true, tight = tight, dense = short)
            CellDivider()
            StatusCell("SN", state.deviceSn.takeIf { it.isNotBlank() && it != "未知设备" } ?: "--", Modifier.weight(if (tight) 1.15f else 1.2f), tight = tight, small = tight, dense = short)
            CellDivider()
            StatusCell(
                "电量", battery?.let { "$it%" } ?: "--", Modifier.weight(if (tight) 0.75f else 0.8f), tight = tight, dense = short,
                valueColor = if (battery != null && battery < 20) p.danger else p.textPrimary,
                icon = if (tight) null else { { LineIcon(WrenchIcon.Battery, if (battery != null && battery < 20) p.danger else p.textSecondary, size = 16.dp, level = (battery ?: 0) / 100f) } },
            )
            CellDivider()
            StatusCell(
                "通信", if (tight) WorkerText.linkTiny(state) else WorkerText.linkShort(state), Modifier.weight(if (tight) 1.1f else 1.45f).testTag("link_status"),
                valueColor = linkColor(state.linkPhase), dot = linkColor(state.linkPhase), onClick = linkAction, tight = tight, dense = short,
            )
            CellDivider()
            StatusCell(
                "远程", if (state.remoteEnabled) "已启用" else "未启用", Modifier.weight(if (tight) 0.95f else 0.9f).testTag("remote_status"),
                valueColor = if (state.remoteEnabled) p.accent else p.warn, dot = if (state.remoteEnabled) p.accent else p.warn, tight = tight, dense = short,
            )
            Box(Modifier.padding(start = 4.dp)) {
                BackChip(onBack, Modifier.testTag("back"), tight = tight)
            }
        }
    }
}

@Composable
private fun StatusCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = palette.textPrimary,
    emphasize: Boolean = false,
    dot: Color? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    tight: Boolean = false,
    small: Boolean = false,
    dense: Boolean = false,
) {
    val p = palette
    Column(
        modifier
            .heightIn(min = WrenchDimens.touch)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = if (tight) 5.dp else 8.dp, vertical = if (dense) 1.dp else 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, style = WrenchType.captionStyle.copy(lineHeight = if (dense) 13.sp else 16.sp), color = p.textSecondary, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(if (tight) 6.dp else 8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(if (tight) 3.dp else 5.dp))
            }
            if (icon != null) {
                icon()
                Spacer(Modifier.width(4.dp))
            }
            val base = when {
                small -> WrenchType.bodyStrong.copy(fontSize = 13.sp)
                emphasize -> WrenchType.bodyStrong.copy(fontSize = 16.sp)
                else -> WrenchType.bodyStrong
            }
            Text(value, style = base.copy(fontFeatureSettings = "tnum", lineHeight = if (dense) 18.sp else base.lineHeight), color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CellDivider() {
    Box(Modifier.width(1.dp).height(26.dp).background(palette.hairline))
}

@Composable
private fun BackChip(onBack: () -> Unit, modifier: Modifier = Modifier, tight: Boolean = false) {
    val p = palette
    Row(
        modifier
            .heightIn(min = WrenchDimens.touch)
            .clip(WrenchDimens.buttonShape)
            .border(1.dp, p.hairline, WrenchDimens.buttonShape)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(start = if (tight) 4.dp else 8.dp, end = if (tight) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LineIcon(WrenchIcon.Back, p.textPrimary, size = 20.dp)
        Spacer(Modifier.width(2.dp))
        Text("返回", style = WrenchType.buttonStyle, color = p.textPrimary)
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
private fun ReadoutPanel(state: MeasurementState, view: TorqueAngleView, nowMillis: Long, scale: Float, compact: Boolean, modifier: Modifier, short: Boolean = false) {
    val p = palette
    val hasData = state.hasWrenchMeasurementData()
    val freshness = WorkerText.freshness(state, nowMillis)
    val live = freshness == Freshness.Live || freshness is Freshness.Delayed
    val torqueColor = when {
        live -> p.accent
        hasData -> p.accent.copy(alpha = 0.6f)
        else -> p.textTertiary
    }
    val angleColor = if (live) p.textPrimary else if (hasData) p.textSecondary else p.textTertiary
    Panel(modifier.testTag("readouts"), padding = PaddingValues(horizontal = 14.dp, vertical = if (short) 6.dp else if (compact) 8.dp else 12.dp)) {
        Text("扭矩 / 转角", style = WrenchType.headlineStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = p.textPrimary, maxLines = 1)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // 两个大数字按本区高度再算一次上限：宽度、高度都放得下才用，数字绝不被下方内容压住。
            val fontScale = LocalDensity.current.fontScale
            val showPeak = maxHeight > 130.dp
            val captionDp = 18f * fontScale * (if (showPeak) 2 else 1)
            val budget = (maxHeight.value - captionDp - 14f).coerceAtLeast(40f)
            val ratio = WrenchType.title.value / WrenchType.display.value
            val heightCapSp = budget / (fontScale * 1.18f * (1f + ratio))
            val displaySize = minOf(WrenchType.display.value * scale, heightCapSp).coerceAtLeast(22f).sp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Column(Modifier.testTag("readout_torque")) {
                    FitNumberWithUnit(WorkerText.readout(state.currentTorqueNm, hasData), "N·m", displaySize, torqueColor, Modifier.fillMaxWidth())
                    if (showPeak) {
                        val peak = view.currentPeak?.torque
                        Text(
                            if (hasData && peak != null) "峰值 ${WorkerText.readout(peak, true)} N·m" else "扭矩 N·m",
                            style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary, maxLines = 1,
                        )
                    }
                }
                Hairline()
                Column(Modifier.testTag("readout_angle")) {
                    Text("转角", style = WrenchType.captionStyle.copy(fontSize = 13.sp, lineHeight = 15.sp), color = p.textSecondary)
                    FitNumberWithUnit(WorkerText.readout(state.currentAngleDeg, hasData), "°", displaySize * ratio, angleColor, Modifier.fillMaxWidth())
                }
            }
        }
        Hairline(Modifier.padding(vertical = if (short) 3.dp else 6.dp))
        FreshnessLine(freshness, hasData)
        val result = state.lastResult
        val resultText = when {
            result != null -> "结果：${result.status.label}" + (result.actualTorqueNm?.let { " · ${WorkerText.readout(it, true)} N·m" } ?: "")
            state.lastPulseResult != null -> "结果：${state.lastPulseResult.status.label}"
            else -> "结果：暂无"
        }
        if (!short) Text(resultText, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!compact) Text("分离：待分析", style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textTertiary, maxLines = 1)
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
private fun ChartPanel(state: MeasurementState, view: TorqueAngleView, compact: Boolean, modifier: Modifier, tiny: Boolean = false) {
    val p = palette
    val (emptyTitle, emptyHint) = when {
        state.connectionState.isOnline && !state.remoteEnabled -> "等待扳手数据" to "开远程并转动后自动画线"
        state.connectionState.isOnline -> "等待扳手数据" to "扳手转动后自动画线"
        else -> WorkerText.link(state) to (WorkerText.linkHint(state) ?: "连上扳手后自动画线")
    }
    Panel(modifier, padding = PaddingValues(start = 8.dp, end = 10.dp, top = if (compact) 6.dp else 10.dp, bottom = 6.dp)) {
        val stored = buildString {
            append("已入库 ${state.persistedPointCount} 点")
            if (tiny) append(" · 全量见导出") else if (!view.isEmpty && (view.sampled || view.windowed)) append(" · 显示窗口")
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val title = @Composable { m: Modifier ->
                Text("扭矩–转角", style = WrenchType.headlineStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold), color = p.textPrimary, maxLines = 1, modifier = m)
            }
            if (maxWidth / LocalDensity.current.fontScale >= 420.dp) {
                Text(stored, style = WrenchType.captionStyle, color = p.textSecondary, maxLines = 1, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
                title(Modifier.align(Alignment.Center))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    title(Modifier.padding(start = 4.dp))
                    Spacer(Modifier.weight(1f))
                    Text(stored, style = WrenchType.captionStyle, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        LiveTorqueChart(
            view = view,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp),
            emptyTitle = emptyTitle,
            emptyHint = emptyHint,
            targetTorqueNm = state.lastResult?.targetTorqueNm?.takeIf { it > 0 },
        )
    }
}

// ---------------- D 右控制柱 ----------------

@Composable
private fun ControlPanel(
    state: MeasurementState,
    actions: WorkerActions,
    compact: Boolean,
    short: Boolean = false,
    onForward: () -> Unit,
    onReverse: () -> Unit,
    onParams: () -> Unit,
    modifier: Modifier,
) {
    val p = palette
    val canMove = RemoteGate.canMove(state)
    val online = state.connectionState.isOnline
    val gap: Dp = if (short) 6.dp else if (compact) 8.dp else WrenchDimens.controlGap
    val deviceError = WorkerText.deviceError(state)
    val motionText = deviceError ?: WorkerText.motion(state)
    val motionColor = when {
        deviceError != null -> p.danger
        !online -> p.textTertiary
        !state.remoteEnabled -> p.warn
        state.motion == MotionCommand.FORWARD || state.motion == MotionCommand.REVERSE -> p.accent
        else -> p.ok
    }
    val forwardActive = state.motion == MotionCommand.FORWARD && canMove
    val reverseActive = state.motion == MotionCommand.REVERSE && canMove
    Panel(modifier.testTag("controls"), padding = PaddingValues(if (short) 6.dp else if (compact) 8.dp else 10.dp)) {
        // 「远程控制」表头条：整行可点，开关在右（0x11）。
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (state.remoteEnabled) p.accentSoft else p.panelSunken),
        ) {
            val narrow = maxWidth < 200.dp
            ToggleRow(
                title = if (narrow) "远程" else "远程控制",
                checked = state.remoteEnabled,
                onCheckedChange = { wanted -> if (wanted) actions.enableRemote() else actions.disableRemote() },
                enabled = RemoteGate.canToggleRemote(state),
                icon = if (narrow) null else WrenchIcon.Remote,
                titleColor = if (state.remoteEnabled) p.accent else p.textPrimary,
                modifier = Modifier.fillMaxWidth().testTag("remote_toggle"),
            )
        }
        if (!short) {
            AnimatedContent(motionText, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "motion") { text ->
                Text(
                    text, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = motionColor, maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 3.dp).testTag("motion_status"),
                )
            }
        } else {
            Spacer(Modifier.height(4.dp))
        }
        // 按控制柱实际可用高度决定：一列大按钮（够高）或两两并排（矮屏 / 大字体）；按钮随高度放大，绝不裁切。
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val avail = maxHeight
            val singleMin = WrenchDimens.motionButton * 2 + WrenchDimens.stopButton + WrenchDimens.touch * 2 + gap * 4
            val exportEnabled = !state.exportInProgress && state.hasWrenchMeasurementData()
            if (avail >= singleMin) {
                val unit = ((avail - gap * 4) / 4.85f).coerceIn(WrenchDimens.motionButton, 84.dp)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)) {
                    ControlButton("正转", WrenchIcon.RotateForward, onForward, Modifier.fillMaxWidth().testTag("forward"), ButtonTone.Accent, enabled = canMove, filled = forwardActive, height = unit)
                    ControlButton("反转", WrenchIcon.RotateReverse, onReverse, Modifier.fillMaxWidth().testTag("reverse"), ButtonTone.Accent, enabled = canMove, filled = reverseActive, height = unit)
                    ControlButton("停止扳手", WrenchIcon.Stop, actions.stop, Modifier.fillMaxWidth().testTag("stop"), ButtonTone.Danger, enabled = true, filled = true,
                        height = (unit * 1.15f).coerceAtLeast(WrenchDimens.stopButton))
                    ControlButton("参数", WrenchIcon.Sliders, onParams, Modifier.fillMaxWidth().testTag("params"), height = (unit * 0.85f).coerceAtLeast(WrenchDimens.touch))
                    ControlButton(if (state.exportInProgress) "导出中…" else "导出 Excel", WrenchIcon.Export, actions.export, Modifier.fillMaxWidth().testTag("export"),
                        enabled = exportEnabled, height = (unit * 0.85f).coerceAtLeast(WrenchDimens.touch))
                }
            } else {
                // Material 按钮最小触控 48dp：按 48 起算，够高再放大到 52。
                val minUnit = WrenchDimens.touch
                val unit = ((avail - gap * 2) / 3.02f).coerceIn(minUnit, WrenchDimens.motionButton)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap, Alignment.CenterVertically)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        ControlButton("正转", null, onForward, Modifier.weight(1f).testTag("forward"), ButtonTone.Accent, enabled = canMove, filled = forwardActive, height = unit)
                        ControlButton("反转", null, onReverse, Modifier.weight(1f).testTag("reverse"), ButtonTone.Accent, enabled = canMove, filled = reverseActive, height = unit)
                    }
                    ControlButton("停止扳手", WrenchIcon.Stop, actions.stop, Modifier.fillMaxWidth().testTag("stop"), ButtonTone.Danger, enabled = true, filled = true, height = unit * 1.08f)
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        ControlButton("参数", null, onParams, Modifier.weight(1f).testTag("params"), height = (unit * 0.92f).coerceAtLeast(minUnit))
                        ControlButton(if (state.exportInProgress) "导出中" else "导出", null, actions.export, Modifier.weight(1f).testTag("export"),
                            enabled = exportEnabled, height = (unit * 0.92f).coerceAtLeast(minUnit))
                    }
                }
            }
        }
    }
}

// ---------------- 单行提示条 ----------------

@Composable
private fun BottomStrip(state: MeasurementState, short: Boolean, modifier: Modifier = Modifier) {
    val p = palette
    Row(modifier.fillMaxWidth().height(if (short) 22.dp else 28.dp).testTag("bottom_strip"), verticalAlignment = Alignment.CenterVertically) {
        Text(
            WorkerText.WINDOW_NOTE,
            style = WrenchType.captionStyle, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // 矮屏时控制柱不放运动提示，这里补上（报错优先）。
        val message = state.controlMessage.ifBlank {
            if (short) WorkerText.deviceError(state) ?: WorkerText.motion(state) else "结束会话不会停止扳手"
        }
        Text(
            message, style = WrenchType.captionStyle.copy(fontSize = 13.sp),
            color = if (state.controlMessage.isBlank()) p.textTertiary else p.textPrimary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).testTag("control_message"),
        )
    }
}
