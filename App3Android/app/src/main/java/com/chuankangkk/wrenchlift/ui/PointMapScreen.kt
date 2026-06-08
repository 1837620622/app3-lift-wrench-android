package com.chuankangkk.wrenchlift.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chuankangkk.wrenchlift.measurement.MeasurementState

@Composable
fun PointMapScreen(state: MeasurementState) {
    Column(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("板端点位")
            Text("有效 ${state.effectivePointCount}", color = AppColors.teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(AppColors.panelInset, RoundedCornerShape(8.dp))
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val slabLeft = size.width * 0.12f
                val slabTop = size.height * 0.12f
                val slabWidth = size.width * 0.76f
                val slabHeight = size.height * 0.72f
                drawRoundRect(
                    color = AppColors.panelRaised,
                    topLeft = Offset(slabLeft, slabTop),
                    size = Size(slabWidth, slabHeight),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                )
                drawRoundRect(
                    color = AppColors.grid,
                    topLeft = Offset(slabLeft, slabTop),
                    size = Size(slabWidth, slabHeight),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    style = Stroke(1.4.dp.toPx()),
                )
                val positions = listOf(
                    0.22f to 0.24f,
                    0.50f to 0.22f,
                    0.78f to 0.24f,
                    0.22f to 0.50f,
                    0.78f to 0.50f,
                    0.22f to 0.76f,
                    0.50f to 0.78f,
                    0.78f to 0.76f,
                )
                positions.forEachIndexed { index, position ->
                    val pointLabel = "P%02d".format(index + 1)
                    val isCurrent = pointLabel == state.session.pointNo.uppercase()
                    val center = Offset(
                        slabLeft + slabWidth * position.first,
                        slabTop + slabHeight * position.second,
                    )
                    drawCircle(
                        color = if (isCurrent) AppColors.teal else AppColors.blue.copy(alpha = 0.34f),
                        radius = if (isCurrent) 13.dp.toPx() else 9.dp.toPx(),
                        center = center,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = if (isCurrent) 19.dp.toPx() else 14.dp.toPx(),
                        center = center,
                        style = Stroke(1.dp.toPx()),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        pointLabel,
                        center.x,
                        center.y + 4.dp.toPx(),
                        Paint().apply {
                            color = AppColors.textPrimary.toArgb()
                            textSize = if (isCurrent) 13.dp.toPx() else 10.5.dp.toPx()
                            textAlign = Paint.Align.CENTER
                            isFakeBoldText = isCurrent
                        },
                    )
                }
            }
        }
        InfoLine("当前板号", state.session.slabNo)
        InfoLine("当前点位", state.session.pointNo)
        InfoLine("提示", "确认平衡点会标记最近一条采样点，后续用于复算平衡状态。")
    }
}
