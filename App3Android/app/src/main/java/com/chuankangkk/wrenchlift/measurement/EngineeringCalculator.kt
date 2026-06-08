package com.chuankangkk.wrenchlift.measurement

object EngineeringCalculator {
    const val DEFAULT_EFFECTIVE_AREA_MM2 = 12000.0
    const val DEFAULT_SCREW_LEAD_MM = 5.0
    const val DEFAULT_GEAR_RATIO = 1.0
    const val DEFAULT_TRANSMISSION_EFFICIENCY = 0.82
    const val DEFAULT_ZERO_OFFSET_MM = 0.0

    /**
     * 按理想丝杆功率平衡关系计算轴向力：
     * T * 2π * η * i = F * p。
     *
     * 该公式本身量纲闭合，但默认导程、效率、传动比只用于公式自检。
     * 正式顶升力必须用厂家结构参数和现场标定结果替换默认值。
     */
    fun forceKnFromTorque(
        torqueNm: Double,
        screwLeadMm: Double = DEFAULT_SCREW_LEAD_MM,
        efficiency: Double = DEFAULT_TRANSMISSION_EFFICIENCY,
        gearRatio: Double = DEFAULT_GEAR_RATIO,
    ): Double {
        require(screwLeadMm > 0.0) { "丝杆导程必须大于 0" }
        require(efficiency in 0.0..1.0) { "传动效率必须在 0 到 1 之间" }
        require(gearRatio > 0.0) { "传动比必须大于 0" }
        return 2.0 * Math.PI * efficiency * gearRatio * torqueNm / screwLeadMm
    }

    fun pressureMpa(
        forceKn: Double?,
        effectiveAreaMm2: Double = DEFAULT_EFFECTIVE_AREA_MM2,
    ): Double? {
        require(effectiveAreaMm2 > 0.0) { "有效受压面积必须大于 0" }
        return forceKn?.let { it * 1000.0 / effectiveAreaMm2 }
    }

    fun displacementMm(
        angleDeg: Double,
        screwLeadMm: Double = DEFAULT_SCREW_LEAD_MM,
        gearRatio: Double = DEFAULT_GEAR_RATIO,
        zeroOffsetMm: Double = DEFAULT_ZERO_OFFSET_MM,
    ): Double {
        require(screwLeadMm > 0.0) { "丝杆导程必须大于 0" }
        require(gearRatio > 0.0) { "传动比必须大于 0" }
        return angleDeg / 360.0 * screwLeadMm / gearRatio + zeroOffsetMm
    }
}
