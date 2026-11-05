package com.chuankangkk.wrenchlift.ui.worker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 设计规范 §7.2：线性图标，2dp 描边、圆角端；语义优先。 */
enum class WrenchIcon { Back, Wifi, Battery, Remote, RotateForward, RotateReverse, Stop, Sliders, Export, Settings, Records, Refresh, Share }

@Composable
fun LineIcon(icon: WrenchIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, level: Float = 1f) {
    Canvas(modifier.size(size)) {
        val stroke = Stroke(width = 2.dp.toPx() * (this.size.minDimension / 20.dp.toPx()).coerceIn(0.8f, 1.4f), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val u = this.size.minDimension / 20f // 20 单位网格
        when (icon) {
            WrenchIcon.Back -> path(tint, stroke) { moveTo(12.5f * u, 4f * u); lineTo(6.5f * u, 10f * u); lineTo(12.5f * u, 16f * u) }
            WrenchIcon.Wifi -> wifi(tint, stroke, u)
            WrenchIcon.Battery -> battery(tint, stroke, u, level)
            WrenchIcon.Remote -> remote(tint, stroke, u)
            WrenchIcon.RotateForward -> rotate(tint, stroke, u)
            WrenchIcon.RotateReverse -> scale(-1f, 1f) { rotate(tint, stroke, u) }
            WrenchIcon.Stop -> drawRoundRect(tint, Offset(5f * u, 5f * u), Size(10f * u, 10f * u), CornerRadius(2f * u))
            WrenchIcon.Sliders -> sliders(tint, stroke, u)
            WrenchIcon.Export -> export(tint, stroke, u)
            WrenchIcon.Settings -> settings(tint, stroke, u)
            WrenchIcon.Records -> records(tint, stroke, u)
            WrenchIcon.Refresh -> rotate(tint, stroke, u)
            WrenchIcon.Share -> share(tint, stroke, u)
        }
    }
}

private fun DrawScope.path(color: Color, stroke: Stroke, block: Path.() -> Unit) {
    drawPath(Path().apply(block), color, style = stroke)
}

private fun DrawScope.wifi(color: Color, stroke: Stroke, u: Float) {
    val center = Offset(10f * u, 15.5f * u)
    listOf(3.6f, 7.2f, 10.8f).forEach { r ->
        drawArc(color, startAngle = 225f, sweepAngle = 90f, useCenter = false,
            topLeft = Offset(center.x - r * u, center.y - r * u), size = Size(2 * r * u, 2 * r * u), style = stroke)
    }
    drawCircle(color, radius = 1.3f * u, center = center)
}

private fun DrawScope.battery(color: Color, stroke: Stroke, u: Float, level: Float) {
    drawRoundRect(color, Offset(2f * u, 6f * u), Size(14f * u, 8f * u), CornerRadius(2f * u), style = stroke)
    drawLine(color, Offset(18f * u, 8.5f * u), Offset(18f * u, 11.5f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
    val fill = (level.coerceIn(0f, 1f) * 10f * u)
    if (fill > 0.5f) drawRoundRect(color, Offset(4f * u, 8f * u), Size(fill, 4f * u), CornerRadius(1f * u))
}

/** 远程：中心点 + 两侧信号弧。 */
private fun DrawScope.remote(color: Color, stroke: Stroke, u: Float) {
    val c = Offset(10f * u, 10f * u)
    drawCircle(color, radius = 1.8f * u, center = c)
    listOf(5f, 8.5f).forEach { r ->
        drawArc(color, 140f, 80f, false, Offset(c.x - r * u, c.y - r * u), Size(2 * r * u, 2 * r * u), style = stroke)
        drawArc(color, -40f, 80f, false, Offset(c.x - r * u, c.y - r * u), Size(2 * r * u, 2 * r * u), style = stroke)
    }
}

/** 顺时针旋转箭头：弧从 3 点钟顺时针扫到 12 点钟，箭头在顶端朝右。 */
private fun DrawScope.rotate(color: Color, stroke: Stroke, u: Float) {
    val r = 6.5f * u
    val c = Offset(10f * u, 10.5f * u)
    drawArc(color, startAngle = 0f, sweepAngle = 270f, useCenter = false,
        topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r), style = stroke)
    val tip = Offset(c.x + 0.6f * u, c.y - r)
    path(color, stroke) {
        moveTo(tip.x - 3f * u, tip.y - 3f * u); lineTo(tip.x, tip.y); lineTo(tip.x - 3f * u, tip.y + 3f * u)
    }
}

private fun DrawScope.sliders(color: Color, stroke: Stroke, u: Float) {
    listOf(5f to 7f, 10f to 13f, 15f to 9f).forEach { (x, knob) ->
        drawLine(color, Offset(x * u, 3.5f * u), Offset(x * u, 16.5f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawCircle(color, radius = 2.2f * u, center = Offset(x * u, knob * u))
    }
}

private fun DrawScope.export(color: Color, stroke: Stroke, u: Float) {
    path(color, stroke) { moveTo(3.5f * u, 11.5f * u); lineTo(3.5f * u, 16.5f * u); lineTo(16.5f * u, 16.5f * u); lineTo(16.5f * u, 11.5f * u) }
    drawLine(color, Offset(10f * u, 3f * u), Offset(10f * u, 12.5f * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
    path(color, stroke) { moveTo(6.5f * u, 6.5f * u); lineTo(10f * u, 3f * u); lineTo(13.5f * u, 6.5f * u) }
}

private fun DrawScope.settings(color: Color, stroke: Stroke, u: Float) {
    val c = Offset(10f * u, 10f * u)
    drawCircle(color, radius = 2.6f * u, center = c, style = stroke)
    drawCircle(color, radius = 6.2f * u, center = c, style = stroke)
    // 四条短齿（水平/竖直/对角用简单端点，避免三角函数）。
    val teeth = listOf(
        Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f),
        Offset(-0.7071f, -0.7071f), Offset(0.7071f, 0.7071f), Offset(-0.7071f, 0.7071f), Offset(0.7071f, -0.7071f),
    )
    teeth.forEach { d ->
        drawLine(color, Offset(c.x + d.x * 6.2f * u, c.y + d.y * 6.2f * u), Offset(c.x + d.x * 8.4f * u, c.y + d.y * 8.4f * u),
            strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

private fun DrawScope.records(color: Color, stroke: Stroke, u: Float) {
    listOf(5f, 10f, 15f).forEach { y ->
        drawCircle(color, radius = 1.2f * u, center = Offset(4f * u, y * u))
        drawLine(color, Offset(7.5f * u, y * u), Offset(16.5f * u, y * u), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

private fun DrawScope.share(color: Color, stroke: Stroke, u: Float) {
    val a = Offset(14.5f * u, 4.5f * u)
    val b = Offset(5.5f * u, 10f * u)
    val c = Offset(14.5f * u, 15.5f * u)
    drawLine(color, a, b, strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawLine(color, b, c, strokeWidth = stroke.width, cap = StrokeCap.Round)
    listOf(a, b, c).forEach { drawCircle(color, radius = 2.3f * u, center = it) }
}
