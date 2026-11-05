package com.chuankangkk.wrenchlift.ui.worker

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.R
import com.chuankangkk.wrenchlift.measurement.LinkPhase
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import com.chuankangkk.wrenchlift.measurement.WorkerText

/**
 * 启动页（§5.5）：深炭底 + T 形手把 Wi‑Fi 扭矩扳手 + 三行中文。无端口、无按钮、无说明书式文案。
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier, animate: Boolean = true) {
    val fade = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) fade.animateTo(1f, tween(420)) }
    BoxWithConstraints(modifier.fillMaxSize().background(SplashColors.background).testTag("splash")) {
        // 构图对齐产品方启动页稿：上方主副标题，中间 T 形手把扳手，底部署名。
        val imageHeight = maxHeight * 0.50f
        val titleSize = (maxHeight.value * 0.095f).coerceIn(34f, 64f).sp
        Column(
            Modifier.fillMaxSize().alpha(fade.value).windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.55f))
            Text("WiFi 扳手", color = SplashColors.title, fontSize = titleSize, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            Text("隔振器顶升调频采集", color = SplashColors.subtitle, fontSize = (titleSize.value * 0.45f).sp, letterSpacing = 3.sp)
            Spacer(Modifier.weight(0.25f))
            Image(
                painter = painterResource(R.drawable.wifi_wrench_splash_t_handle),
                contentDescription = "T 形手把 Wi‑Fi 扭矩扳手",
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(imageHeight),
            )
            Spacer(Modifier.weight(0.6f))
            Text("传康Kk · 免费供应", color = SplashColors.footer, fontSize = 14.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

/** 启动页结束后的去向（SPL-04）：已在扳手 Wi‑Fi → 工作台；否则 → 连接引导（无端口）。 */
fun MeasurementState.onWrenchNetwork(): Boolean {
    val ssidMatches = targetWifiName.isNotBlank() && currentWifiName.equals(targetWifiName, ignoreCase = true)
    return linkPhase == LinkPhase.READY || linkPhase == LinkPhase.WAITING_REPLY || inWrenchSubnet || ssidMatches
}

/**
 * 连接引导（NET-04）：只提示去系统连扳手 Wi‑Fi、名称说明与连接状态；没有端口栏。
 */
@Composable
fun ConnectGuideScreen(
    state: MeasurementState,
    actions: WorkerActions,
    onOpenRecords: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val p = palette
    Column(
        Modifier.fillMaxSize().background(p.canvas).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = WrenchDimens.screenEdge).testTag("guide"),
    ) {
        PageTopBar("连接扳手", onBack = null) {
            IconAction(WrenchIcon.Records, "测点与记录", onOpenRecords)
            Spacer(Modifier.width(8.dp))
            IconAction(WrenchIcon.Settings, "设置", onOpenSettings)
        }
        Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(WrenchDimens.gap)) {
            Panel(Modifier.weight(0.42f).fillMaxHeight(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Box(Modifier.fillMaxSize().background(SplashColors.background), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.wifi_wrench_splash_t_handle),
                        contentDescription = "T 形手把 Wi‑Fi 扭矩扳手",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                }
            }
            Panel(Modifier.weight(0.58f).fillMaxHeight(), padding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                    GuideStep(1, "打开系统设置，连上扳手的 Wi‑Fi", state.targetWifiName.takeIf { it.isNotBlank() }?.let { "名称：$it" } ?: "名称见扳手铭牌")
                    GuideStep(2, "回到这里，会自动连上扳手", "不用做任何设置")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(WorkerText.link(state), linkColor(state.linkPhase), icon = WrenchIcon.Wifi, modifier = Modifier.testTag("guide_status"))
                        WorkerText.linkHint(state)?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(it, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary)
                        }
                    }
                    Text("当前 Wi‑Fi：${state.currentWifiName}", style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textTertiary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlButton("去连接 Wi‑Fi", WrenchIcon.Wifi, actions.openWifiSettings, Modifier.weight(1f).testTag("open_wifi"), ButtonTone.Accent, height = 56.dp)
                    ControlButton(
                        if (state.linkPhase == LinkPhase.PROBING) "正在连接…" else "重新连接",
                        WrenchIcon.Refresh, actions.connect, Modifier.weight(1f).testTag("retry_connect"),
                        enabled = state.linkPhase != LinkPhase.PROBING, height = 56.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, title: String, detail: String) {
    val p = palette
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
            Text("$number", style = WrenchType.bodyStrong, color = p.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = WrenchType.bodyStrong.copy(fontSize = 17.sp, lineHeight = 23.sp), color = p.textPrimary)
            Text(detail, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary, textAlign = TextAlign.Start)
        }
    }
}
