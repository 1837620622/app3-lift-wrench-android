package com.chuankangkk.wrenchlift.measurement

enum class MeasurementDataOrigin(
    val dashboardLabel: String,
    val focusLabel: String,
) {
    LivePoints("实时回传", "实时回传"),
    RetainedRealPoints("上次真实点", "上次真实数据"),
    LoadedPoints("已载入记录", "已载入记录"),
    FramesOnly("已收设备帧", "已收帧，等待过程点"),
    Waiting("等待扳手数据", "等待真实扳手数据"),
}

fun MeasurementState.dataOrigin(): MeasurementDataOrigin = when {
    recentPoints.isNotEmpty() && lastCurvePointReceiveTime != null && connectionState.isOnline ->
        MeasurementDataOrigin.LivePoints
    recentPoints.isNotEmpty() && lastCurvePointReceiveTime != null -> MeasurementDataOrigin.RetainedRealPoints
    recentPoints.isNotEmpty() -> MeasurementDataOrigin.LoadedPoints
    receivedFrameCount > 0 -> MeasurementDataOrigin.FramesOnly
    else -> MeasurementDataOrigin.Waiting
}
