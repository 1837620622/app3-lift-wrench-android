package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.data.MeasurementEntity
import com.chuankangkk.wrenchlift.measurement.MeasuredPoint
import com.chuankangkk.wrenchlift.measurement.MeasurementState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(state: MeasurementState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionTitle("通讯调试日志")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(AppColors.panelInset, RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.logLines.forEach { line ->
                Text(
                    text = line,
                    color = AppColors.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        EvidenceBlock(state)
        HistoryRecordBlock(state.recentSessions)
        RecentPointBlock(state.recentPoints)
        Text(
            text = "最近点 ${state.recentPoints.size}/50 · 扭矩 ${"%.1f".format(state.currentTorqueNm)} Nm · 角度 ${"%.1f".format(state.currentAngleDeg)}° · 顶升力 ${state.currentForceKn?.let { "%.2f".format(it) } ?: "--"} kN · 压力 ${state.currentPressureMpa?.let { "%.3f".format(it) } ?: "--"} MPa",
            color = AppColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EvidenceBlock(state: MeasurementState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.panelInset, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("本次采集证据", color = AppColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        EvidenceLine("连接端点", "${state.host}:${state.port}")
        EvidenceLine("自动发现", state.discoveredEndpoint)
        EvidenceLine("设备 SN", state.deviceSn.ifBlank { state.session.deviceSn.ifBlank { "--" } })
        EvidenceLine("当前 Wi-Fi", state.currentWifiName)
        EvidenceLine("本机 IP", state.localIp)
        EvidenceLine("网络诊断", state.networkDiagnosticSummary)
        EvidenceLine("最近发送", "${state.lastCommandLabel} ${state.lastSentHex}".trim())
        EvidenceLine("最近回传", state.lastRawHex.ifBlank { "尚未收到原始帧" })
        WrenchResultEvidence(state)
    }
}

@Composable
private fun WrenchResultEvidence(state: MeasurementState) {
    val result = state.lastResult ?: return
    EvidenceLine("扳手结果", result.status.label)
    EvidenceLine("目标/实际扭矩", "${result.targetTorqueNm.formatEvidence()} / ${result.actualTorqueNm.formatEvidence()} Nm")
    EvidenceLine("目标/实际角度", "${result.targetAngleDeg.formatEvidence()} / ${result.actualAngleDeg.formatEvidence()} °")
    EvidenceLine("扭矩上下限", "${result.torqueLowerNm.formatEvidence()} - ${result.torqueUpperNm.formatEvidence()} Nm")
    EvidenceLine("角度上下限", "${result.angleLowerDeg.formatEvidence()} - ${result.angleUpperDeg.formatEvidence()} °")
    EvidenceLine("目标顶升力", if (result.targetTorqueNm == null) "--" else "待标定后由目标扭矩换算")
}

@Composable
private fun HistoryRecordBlock(sessions: List<MeasurementEntity>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.panelInset, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("最近运行记录", color = AppColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (sessions.isEmpty()) {
            Text("尚无本机历史记录；连接扳手并收到结果后会自动写入。", color = AppColors.textSecondary, fontSize = 12.sp)
            return@Column
        }
        sessions.take(5).forEach { session ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.panel, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "${session.startTime.formatRecordTime()} · ${session.slabNo}/${session.pointNo} · ${session.resultStatus}",
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PointValue("目标扭矩", session.targetTorqueNm.formatEvidence(), Modifier.weight(1f))
                    PointValue("实际扭矩", session.actualTorqueNm.formatEvidence(), Modifier.weight(1f))
                    PointValue("目标角度", session.targetAngleDeg.formatEvidence(), Modifier.weight(1f))
                    PointValue("实际角度", session.actualAngleDeg.formatEvidence(), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RecentPointBlock(points: List<MeasuredPoint>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.panelInset, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("最近解析点", color = AppColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (points.isEmpty()) {
            Text("等待扳手发送扭矩转角过程数据", color = AppColors.textSecondary, fontSize = 12.sp)
            return@Column
        }
        points.takeLast(6).forEach { point ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PointValue("螺栓", point.boltNo.toString(), Modifier.weight(0.8f))
                PointValue("扭矩", "%.1f".format(point.sourcePoint.torqueNm), Modifier.weight(1f))
                PointValue("角度", "%.1f".format(point.sourcePoint.angleDeg), Modifier.weight(1f))
                PointValue("顶升力", point.forceKn?.let { "%.2f".format(it) } ?: "--", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EvidenceLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = AppColors.textSecondary, fontSize = 11.sp)
        Text(
            value,
            color = AppColors.textPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PointValue(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = AppColors.textSecondary, fontSize = 10.sp, maxLines = 1)
        Text(value, color = AppColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private fun Double?.formatEvidence(): String = this?.let { "%.1f".format(it) } ?: "--"

private fun Long.formatRecordTime(): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(this))
