package com.chuankangkk.wrenchlift.measurement

import com.chuankangkk.wrenchlift.protocol.TorqueAnglePoint

interface ForceCalculator {
    fun calculate(point: TorqueAnglePoint, history: List<TorqueAnglePoint>): ForceCalculationResult
}

data class ForceCalculationResult(
    val forceKn: Double?,
    val status: String,
    val warningLevel: WarningLevel,
    val message: String,
    val pressureMpa: Double? = null,
)

enum class WarningLevel {
    NORMAL,
    WATCH,
    WARNING,
    ERROR,
}
