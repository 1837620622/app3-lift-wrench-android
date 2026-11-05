package com.chuankangkk.wrenchlift.ui.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.BuildConfig
import com.chuankangkk.wrenchlift.measurement.MeasurementState

/** 设置（次级入口）：Wi‑Fi 名称引导、导出选项、配色、高级诊断、关于。端口只在高级诊断里出现（NET-03）。 */
@Composable
fun SettingsScreen(
    state: MeasurementState,
    actions: WorkerActions,
    onBack: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenDebug: (() -> Unit)?,
) {
    val p = palette
    Column(Modifier.fillMaxSize().background(p.canvas).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = WrenchDimens.screenEdge).testTag("settings")) {
        PageTopBar("设置", onBack = onBack)
        Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(WrenchDimens.gap)) {
            Panel(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(16.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("扳手 Wi‑Fi")
                    WrenchTextField(state.targetWifiName, actions.setWifiName, "扳手 Wi‑Fi 名称（只用于提示）", Modifier.fillMaxWidth().testTag("wifi_name"), placeholder = "见扳手铭牌")
                    Text("换扳手时改这里即可；连接靠自动查找，不需要填地址。", style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary)
                    Hairline()
                    SectionLabel("导出")
                    ToggleRow("同时导出 CSV", state.exportCsvToo, actions.setExportCsvToo, subtitle = "Excel（含图表）为主，CSV 可选", modifier = Modifier.fillMaxWidth().testTag("csv_toggle"))
                    Hairline()
                    SectionLabel("界面")
                    ToggleRow("深色界面", state.darkTheme, actions.setDarkTheme, subtitle = "室内或平板可用；强光现场建议浅色", modifier = Modifier.fillMaxWidth().testTag("dark_toggle"))
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel("维护")
                    NavRow("高级诊断", "查看通信端点与探测记录（维护人员）", onOpenDiagnostics, Modifier.testTag("open_diagnostics"))
                    if (onOpenDebug != null) NavRow("调试工具", "仅调试版", onOpenDebug, Modifier.testTag("open_debug"))
                    Hairline()
                    SectionLabel("关于")
                    Text("WiFi 扳手 ${BuildConfig.VERSION_NAME}", style = WrenchType.bodyStrong, color = p.textPrimary)
                    Text("隔振器顶升调频采集", style = WrenchType.bodyStyle, color = p.textSecondary)
                    Text("著作权：传康Kk（GitHub 1837620622）· 免费供应", style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary)
                    Text("未标定不输出正式力值；分离点 / 平衡点待分析。", style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textTertiary)
                }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = palette
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(WrenchDimens.buttonShape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = WrenchType.buttonStyle, color = p.textPrimary)
            Text(subtitle, style = WrenchType.captionStyle, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", style = WrenchType.headlineStyle.copy(fontSize = 22.sp), color = p.textTertiary)
    }
}

/** 高级诊断：维护人员查看/修改 host:port、探测记录。不在工人主流程里。 */
@Composable
fun DiagnosticsScreen(state: MeasurementState, actions: WorkerActions, onBack: () -> Unit) {
    val p = palette
    val online = state.connectionState.isOnline
    Column(Modifier.fillMaxSize().background(p.canvas).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = WrenchDimens.screenEdge).testTag("diagnostics")) {
        PageTopBar("高级诊断", onBack = onBack)
        Row(Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(WrenchDimens.gap)) {
            Panel(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(16.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionLabel("网络")
                    InfoLine("当前 Wi‑Fi", state.currentWifiName)
                    InfoLine("本机 IP", state.localIp)
                    InfoLine("网关", state.gatewayIp)
                    InfoLine("扳手网段", if (state.inWrenchSubnet) "是（192.168.4.x）" else "否 / 未知")
                    Hairline(Modifier.padding(vertical = 4.dp))
                    SectionLabel("自适应探测")
                    InfoLine("默认端点", "192.168.4.1:7888（先试）")
                    InfoLine("上次成功", state.lastGoodEndpoint?.toString() ?: "--")
                    InfoLine("本次端点", state.activeEndpoint?.toString() ?: "--")
                    InfoLine("握手", if (state.probeReport.handshake) "已收到 C 协议帧" else "未确认")
                    InfoLine("探测", state.probeReport.summary)
                    if (state.probeReport.tried.isNotEmpty()) InfoLine("试过", state.probeReport.tried.joinToString("、"))
                    InfoLine("诊断", state.networkDiagnosticSummary)
                }
            }
            Panel(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("手动端点（仅维护时用）")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WrenchTextField(state.host, actions.setHost, "设备地址", Modifier.weight(2f).testTag("diag_host"), enabled = !online)
                        WrenchTextField(state.port, actions.setPort, "端口", Modifier.weight(1f).testTag("diag_port"), enabled = !online, numeric = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ControlButton("按此地址连接", null, actions.connectManual, Modifier.weight(1f), ButtonTone.Accent, enabled = !online, height = WrenchDimens.touch)
                        ControlButton("检查网络", null, actions.runDiagnostics, Modifier.weight(1f), height = WrenchDimens.touch)
                    }
                    Text("手动端点失败后仍会自动探测；成功握手的端点会记住，下次优先。", style = WrenchType.captionStyle, color = p.textSecondary)
                    Hairline()
                    SectionLabel("最近日志")
                    state.logLines.takeLast(10).reversed().forEach {
                        Text(it, style = WrenchType.captionStyle, color = p.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val p = palette
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textSecondary, modifier = Modifier.weight(0.35f))
        Text(value, style = WrenchType.captionStyle.copy(fontSize = 13.sp), color = p.textPrimary, modifier = Modifier.weight(0.65f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
