package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint

class CalibrationForceCalculator(
    private val torqueToForcePoints: List<Pair<Double, Double>>,
) : ForceCalculator {
    override fun calculate(point: TorqueAnglePoint, history: List<TorqueAnglePoint>): ForceCalculationResult {
        if (torqueToForcePoints.size < 2) {
            return ForceCalculationResult(
                forceKn = null,
                status = "未标定",
                warningLevel = WarningLevel.WATCH,
                message = "缺少实测标定曲线，不输出正式顶升力",
            )
        }
        val sorted = torqueToForcePoints.sortedBy { it.first }
        val lower = sorted.lastOrNull { it.first <= point.torqueNm } ?: sorted.first()
        val upper = sorted.firstOrNull { it.first >= point.torqueNm } ?: sorted.last()
        val force = if (lower.first == upper.first) {
            lower.second
        } else {
            val ratio = (point.torqueNm - lower.first) / (upper.first - lower.first)
            lower.second + ratio * (upper.second - lower.second)
        }
        return ForceCalculationResult(
            forceKn = force,
            status = "标定曲线",
            warningLevel = WarningLevel.NORMAL,
            message = "按实测标定曲线插值",
            pressureMpa = EngineeringCalculator.pressureMpa(force),
        )
    }
}
